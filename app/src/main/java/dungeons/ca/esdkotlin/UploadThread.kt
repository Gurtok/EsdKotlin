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

import android.content.SharedPreferences
import android.util.Log
import java.io.IOException
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.PasswordAuthentication
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/**
 * A class to start a thread upload the database to Kibana.
 * @author Gurtok.
 * @version First version of upload Async thread.
 */
class UploadThread( passedPreferences: SharedPreferences,
         /** A reference to the service manager, used to update data counts. */
         private var serviceManager: ServiceManager,
         /** A reference to the database helper the service manager controls. */
         private var dbHelper: DatabaseHelper): Runnable {

  /** Static variable for the indexer thread to communicate success or failure of an index attempt. */
  private var uploadSuccess = false
  /** Control variable to indicate if we should stop uploading to elastic. */
  private var stopUploadThread = false
  /** Name of the elastic index to ID the data. */
  private var esIndex = ""
  /** Name of the elastic index TYPE. */
  private var esType = ""

  private var esHost = ""
  private var esSSL = false
  private var esPort = ""
  private var dateTheIndexName = false

  private var esUsername = ""
  private var esPassword = ""

  /** ID for logcat. */
  private val logTag = "Uploads"
  /** A reference to the apps stored preferences. */
  private var sharedPreferences = passedPreferences
  /** The thread class to do the actual uploading of data with. */
  private var esIndexer: Indexer
  /** Control variable to indicate if this runnable is currently uploading data. */
  private var working = false

  /** Default Constructor using the application context. */
  init{
    sharedPreferences = passedPreferences
    esIndexer = Indexer( this )
  }

  /** Main class entry. The data we need has already been updated. So just go nuts. */
  override fun run() {
    working = true
    Log.e(logTag, "Started upload thread.")
    stopUploadThread = false
    startUploading()
    Log.e(logTag, "Stopped upload thread.")
    working = false
  }

  /** A method to indicate if this thread is currently working. */
  fun isWorking(): Boolean{
    return working
  }

  fun setIndexingSuccess( result: Boolean){
    uploadSuccess = result
  }

  /** Control method to halt the whole thread. */
  fun stopUploading() {
    stopUploadThread = true
  }

  private fun updateConfigurationFromPreferences(){

    esHost = sharedPreferences.getString("host", "localhost")
    esPort = sharedPreferences.getString("port", "9200")
    esSSL = sharedPreferences.getBoolean("ssl", false)
    esIndex = sharedPreferences.getString("index", "test_index")
    esType = sharedPreferences.getString("type", "esd")
    dateTheIndexName = sharedPreferences.getBoolean("index_date", false)

    esUsername = sharedPreferences.getString("user", "")
    esPassword = sharedPreferences.getString("pass", "")

  }

  /** Main work of upload runnable is accomplished here. */
  private fun startUploading() {
    var globalUploadTimer = System.currentTimeMillis()

    /* Loop to keep uploading. */
    while (!stopUploadThread) {


      // If we have gone more than 20 seconds without an update, stop the thread.
      if(System.currentTimeMillis() > globalUploadTimer + 20000 && dbHelper.databaseEntries() < 10 ){
        stopUploadThread = true
        return
      }

      // One bulk upload per 5 seconds.
      if ( System.currentTimeMillis() > globalUploadTimer + 5000 ) {
        /* If we cannot establish a connection with the elastic server. */
        if (!checkForElasticHost()) {
          // This thread is not working.
          // We should stop the service if this is true.
          stopUploadThread = true
          Log.e("$logTag: startUpload", "No elastic host.")
          return
        }else{
          val nextString = dbHelper.getBulkString(esIndex, esType)
          // If setNextString has data.
          if( !nextString.isEmpty() ){
            esIndexer.setNextString( nextString )
          }else{
            return
          }
          try {
            uploadSuccess = false
            // Start the indexing thread, and join to wait for it to finish.
            esIndexer.start()
            esIndexer.join()
          } catch ( interEx: InterruptedException) {
            Log.e("$logTag: startUpload", "Failed to join ESI thread, possibly not running.")
          }
          if (uploadSuccess) {
            Log.e( "$logTag: startUpload", "Uploaded " + dbHelper.getDeleteCount() + " rows from DB." )
            globalUploadTimer = System.currentTimeMillis()
            serviceManager.uploadSuccess(true, dbHelper.getDeleteCount() )
            dbHelper.deleteUploadedIndices()
          }else{
            Log.e("$logTag: startUpload", "Failed index.")
            serviceManager.uploadSuccess(false, dbHelper.getDeleteCount() )
          }
        }

      }


    }

  }

  /**
   * Extract config information from sharedPreferences.
   * Tag the current date stamp on the index name if set in preferences. Credit: GlenRSmith.
   */
  private fun updateIndexerUrl( modifiedProtocol: String) {

    // Tag the current date stamp on the index name if set in preferences
    // Thanks GlenRSmith for this idea
    if (dateTheIndexName) {
      val logDate = Date(System.currentTimeMillis())
      val logDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
      val dateString = logDateFormat.format(logDate)
      esIndex = String.format("%s-%s",esIndex ,dateString )
    }

    val mappingURL = String.format("%s/%s", modifiedProtocol, esIndex)
    // Note the different URLs. Regular post ends with type. Mapping ends with index ID.
    val postingURL = String.format("%s/%s", modifiedProtocol, "_bulk")

    try {
      esIndexer.mapUrl = URL(mappingURL)
      esIndexer.postUrl = URL(postingURL)
    } catch ( malformedUrlEx: MalformedURLException) {
      Log.e("$logTag: updateIndexer", "Failed to update URLs.")
      esIndexer.mapUrl = URL("")
      esIndexer.postUrl = URL("")
    }

    esIndexer.esSSL = esSSL

  }

  /** Helper method to determine if we currently have access to an elastic server to upload to. */
  private fun checkForElasticHost(): Boolean {
    var responseCodeSuccess = false
    var responseCode = 0
    // Get new config info.
    updateConfigurationFromPreferences()

    // Required for SSL.
    if (esUsername.isNotEmpty() && esPassword.isNotEmpty() ) {
      esIndexer.esUsername = esUsername
      esIndexer.esPassword = esPassword

      Authenticator.setDefault(object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
          return PasswordAuthentication(esUsername, esPassword.toCharArray())
        }
      })
    }

    // Secured Connection
    if (esSSL) {
      // Add secured protocol to host name.
      val esUrl = URL(String.format("https://%s:%s", esHost, esPort))
      try {
        val httpsConnection = esUrl.openConnection() as HttpsURLConnection
        httpsConnection.connectTimeout = 2000
        httpsConnection.readTimeout = 2000
        httpsConnection.connect()
        responseCode = httpsConnection.responseCode
        if (responseCode in 200..299) {
          responseCodeSuccess = true
          updateIndexerUrl(esUrl.toString())
        }
        httpsConnection.disconnect()
      } catch ( ex: IOException ) {
        Log.e("$logTag: chkHost", "Failure to open connection cause. " + ex.message + " " + responseCode)
        ex.printStackTrace()
      }

    }else{ // Else NON-secured connection.
      // Add NON-secured protocol to host name.
      val esUrl = URL(String.format("http://%s:%s", esHost, esPort))

      try {
        val httpConnection = esUrl.openConnection() as HttpURLConnection
        httpConnection.connectTimeout = 2000
        httpConnection.readTimeout = 2000
        httpConnection.connect()
        responseCode = httpConnection.responseCode
        if (responseCode in 200..299) {
          responseCodeSuccess = true
          updateIndexerUrl(esUrl.toString())
        }
        httpConnection.disconnect()
      } catch (ex: IOException) {
        Log.e("$logTag: chkHost", "Failure to open connection cause. " + ex.message + " " + responseCode)
      }

    }

    // Returns TRUE if the response code was valid.
    return responseCodeSuccess
  }

}
