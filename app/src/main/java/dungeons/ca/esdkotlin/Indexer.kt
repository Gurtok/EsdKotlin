/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dungeons.ca.esdkotlin

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.IOException
import java.net.*
import javax.net.ssl.HttpsURLConnection


/**
 * Elastic Search Indexer.
 * Use this thread to upload data to the elastic server.
 */
class Indexer(uploads: UploadThread)  {

    /** Used to identify which class is writing to logCat. */
    private val logTag = "eSearchIndexer"
    /** Elastic username. */
    var esUsername = ""
    /** Elastic password. */
    var esPassword = ""
    var esSSL = false
    /** The URL we use to post data to the server. */
    lateinit var postUrl: URL
    /** The URL we use to create an index and PUT a mapping schema on it. */
    lateinit var mapUrl: URL
    /** A variable to hold the JSON string to be uploaded. */
    private var uploadString = ""
    /** Reference to the calling class. */
    private var passedUploadThread = uploads
    /** Used to establish outside connection. */
    private lateinit var esUrlConnection: HttpURLConnection
    /** Variable to keep track if this instance of the indexer has submitted a map. */
    private var alreadySentMapping = false


    /** Base constructor. */
    init {

    }

    /** This run method is executed upon each index start. */
    fun run() {

        Log.e("Thread", "Thread is starting..")

        if (!alreadySentMapping) {
            Log.e("Indexer", "Sending mapping!")
            createMapping()
            alreadySentMapping = true
        } else {
            Log.e("Indexer", "Don't need to send mapping!")
        }

        if (!uploadString.isEmpty()) {
            Log.e("Indexer", "Got an upload string!")
            index(uploadString)
        } else {
            Log.e("Indexer", "Upload string is empty!!")
        }

        Log.e("Thread", "Thread is done..")

    }

    /** Send messages to Upload thread and ESD service thread to indicate result of index. */
    private fun indexSuccess(result: Boolean) {
        passedUploadThread.setIndexingSuccess(result)
    }

    /** Set the next string to be uploaded. */
    fun setNextString(uploadString: String) {
        this.uploadString = uploadString
    }

    /** Create a map and send to elastic for sensor index. */
    private fun createMapping() {
        // Connect to elastic using PUT to make elastic understand this is a mapping.
        if (connect("PUT")) {
            try {
                val dataOutputStream = DataOutputStream(esUrlConnection.outputStream)
                // Lowest json level, contains explicit typing of sensor data.
                val mappingTypes = JSONObject()
                // Type "start_location" && "location" using pre-defined typeGeoPoint. ^^
                mappingTypes.put("start_location", JSONObject().put("type", "geo_point"))
                mappingTypes.put("location", JSONObject().put("type", "geo_point"))
                // Put the two newly typed fields under properties.
                val properties = JSONObject().put("properties", mappingTypes)
                // Put the two newly typed fields under properties.
                val tagging = JSONObject().put("esd", properties)
                // File this new properties json under _mappings.
                val mappings = JSONObject().put("mappings", tagging)
                // Write out to elastic using the passed outputStream that is connected.
                dataOutputStream.writeBytes(mappings.toString())
                if (checkResponseCode()) {
                    Log.e("$logTag: createMap", "Successfully uploaded map!")
                    alreadySentMapping = true
                } else {
                    // Send message to upload thread about the failure to upload via intent.
                    Log.e("$logTag: createMap", "Failed response code check on MAPPING. " + mappings.toString())
                }
            } catch (jsonEx: JSONException) {
                Log.e("$logTag: createMap", "JSON error: " + jsonEx.toString())
            } catch (IoEx: IOException) {
                Log.e("$logTag: createMap", "Failed to write to outputStreamWriter.")
            }
            esUrlConnection.disconnect()
        }
    }

    /** Send JSON data to elastic using POST. */
    private fun index(uploadString: String) {
        // Boolean return to check if we successfully connected to the elastic host.
        Log.e("Index Function", "Starting index attempt....")
        if (connect("POST")) {
            // POST our documents to elastic.
            Log.e("Index Function", "Starting POST")
            try {
                val dataOutputStream = DataOutputStream(esUrlConnection.outputStream)
                dataOutputStream.writeBytes(uploadString)
                // Check status of post operation.
                if (checkResponseCode()) {
                    Log.e("$logTag: esIndex.", "Uploaded string success!")
                    indexSuccess(true)
                } else {
                    Log.e("$logTag: esIndex.", "Uploaded string FAILURE!")
                    indexSuccess(false)
                }
            } catch (IOex: IOException) {
                // Error writing to httpConnection.
                Log.e("$logTag: esIndex.", IOex.message)
            }
            esUrlConnection.disconnect()
        } else {
            Log.e("Index Function", "Was not a POST...WTF?")
            esUrlConnection.disconnect()
        }
    }

    /** Open a connection with the server. */
    private fun connect(verb: String): Boolean {
        // Control variable to timeout the connection process if required.
        var connectFailCount = 0
        // Send authentication if required
        if (esUsername.isNotBlank() && esPassword.isNotBlank()) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(esUsername, esPassword.toCharArray())
                }
            })
        }

        while (connectFailCount == 0 || connectFailCount % 9 != 0) {
            // Establish connection.
            try {
                when (verb) {
                    "PUT" -> {
                        // Create and map the index itself.

                        esUrlConnection = if (esSSL) mapUrl.openConnection() as HttpsURLConnection
                        else mapUrl.openConnection() as HttpURLConnection

                        esUrlConnection.doOutput = true
                        esUrlConnection.doInput = true
                        esUrlConnection.setRequestProperty("Content-Type", "application/json")
                        esUrlConnection.connectTimeout = 2000
                        esUrlConnection.readTimeout = 2000
                        esUrlConnection.requestMethod = verb
                        esUrlConnection.connect()
                        return true
                    }
                    "POST" -> {
                        // For sending index data.
                        esUrlConnection = if (esSSL) postUrl.openConnection() as HttpsURLConnection
                        else postUrl.openConnection() as HttpURLConnection
                        esUrlConnection.doOutput = true
                        esUrlConnection.doInput = true
                        esUrlConnection.setRequestProperty("Content-Type", "application/json")
                        esUrlConnection.connectTimeout = 2000
                        esUrlConnection.readTimeout = 2000
                        esUrlConnection.requestMethod = verb
                        esUrlConnection.connect()
                        return true
                    }
                    else -> {
                    }
                }

            } catch (urlEx: MalformedURLException) {
                Log.e("$logTag: connect.", "Error building URL.")
                connectFailCount++
            } catch (IOex: IOException) {
                Log.e("$logTag: connect.", "Failed to connect to elastic. " + IOex.message + "  " + IOex.cause)
                connectFailCount++
            }
        }
        // If it got this far, it failed.
        return false
    }

    /** Helper class to determine if an individual indexing operation was successful. */
    private fun checkResponseCode(): Boolean {
        var responseMessage = "ResponseCode placeholder."
        var responseCode = 0
        try {
            responseMessage = esUrlConnection.responseMessage
            responseCode = esUrlConnection.responseCode
            /*
            Since we are receiving a response code after establishing a connection, elastic is simply
              telling us that we do not need to upload a mapping, as the index already exists. Thus we
                get the error code 400.
            */
            if (esUrlConnection.requestMethod == "PUT" && responseCode == 400) {
                return true
            }
            if (responseCode in 200..299) {
                return true
            } else {
                throw IOException("")
            }
        } catch (ioEx: IOException) {
            // "I expect only the finest of 200s" - Ademara
            Log.e("$logTag: response",
                    String.format("%s%s\n%s%s\n%s",
                            "Bad response code: ", responseCode,
                            "Response Message: ", responseMessage,
                            "$esUrlConnection request type: " + esUrlConnection.requestMethod)// End string.
            )
        }
        return false
    }


}
