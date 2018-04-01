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

import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.util.Log

import org.json.JSONException
import org.json.JSONObject

/**
 * GPS Logger - Location Listener implementation.
 */
class GPSLogger: LocationListener {

  /** */
  private var gpsProvider = ""
  private var gpsUpdates = 0
  private var gpsLat = 0.0
  private var gpsLong = 0.0
  private var gpsLatitude = 0.0
  private var lastGpsLat = 0.0
  private var lastGpsLong = 0.0

  private var gpsAlt = 0.0
  private var gpsLongStart = 0.0
  private var gpsDistanceMetres = 0.0
  private var gpsDistanceFeet = 0.0
  private var gpsTotalDistance = 0.0
  private var gpsTotalDistanceKM = 0.0
  private var gpsTotalDistanceMiles = 0.0
  private var gpsAccuracy = 0f
  private var gpsBearing = 0f
  private var gpsSpeed = 0f
  private var gpsSpeedKMH = 0f
  private var gpsSpeedMPH = 0f
  private var gpsAcceleration = 0f
  private var gpsAccelerationKMH = 0f
  private var gpsAccelerationMPH = 0f
  private var lastSpeed = 0f


  var gpsHasData = false

  /**
   * Method to record gps.
   * @param location Current location.
   */
  override fun onLocationChanged( location: Location) {
    gpsLat = location.latitude
    gpsLong = location.longitude
    gpsAlt = location.altitude
    gpsAccuracy = location.accuracy
    gpsBearing = location.bearing
    gpsProvider = location.provider
    gpsSpeed = location.speed

    // Store the lat/long for the first reading we got
    if( gpsUpdates == 0 ){
      gpsLatitude = gpsLat
      gpsLongStart = gpsLong
      lastSpeed = gpsSpeed
      lastGpsLat = gpsLat
      lastGpsLong = gpsLong
    }
    // Metre per second is not ideal. Adding km/hr and mph as well
    gpsSpeedKMH = gpsSpeed * 3.6f
    gpsSpeedMPH = gpsSpeed * 2.23694f
    // Calculate acceleration
    gpsAcceleration = gpsSpeed - lastSpeed
    gpsAccelerationKMH = gpsAcceleration * 3.6f
    gpsAccelerationMPH = gpsAcceleration * 2.23694f
    lastSpeed = gpsSpeed

    // Calculate distance
    val currentLocation = Location("current")
    currentLocation.latitude = gpsLat
    currentLocation.longitude = gpsLong

    val lastLocation = Location("last")
    lastLocation.latitude = lastGpsLat
    lastLocation.longitude = lastGpsLong

    gpsDistanceMetres = currentLocation.distanceTo(lastLocation).toDouble()
    gpsDistanceFeet = gpsDistanceMetres * 3.28084
    lastGpsLat = gpsLat
    lastGpsLong = gpsLong

    // Track total distance
    gpsTotalDistance += gpsDistanceMetres
    gpsTotalDistanceKM = gpsTotalDistance * 0.001
    gpsTotalDistanceMiles = gpsTotalDistance * 0.000621371

    // We're live!
    gpsHasData = true
    gpsUpdates++

  }


  /** Required over ride. Not used. */
  override fun onStatusChanged( provider: String, status: Int, extras: Bundle){}

  /** Required over ride. Not used. */
  override fun onProviderEnabled( provider: String){}

  /** Required over ride. Not used. */
  override fun onProviderDisabled( provider: String){}

  /**
   * Take the passed json object, add the collected gps data.
   * @param passedJson A reference to the SensorRunnable json file that will be uploaded.
   * @return The json that now included the gps data.
   */
  fun getGpsData(passedJson: JSONObject  ): JSONObject{
    try{
      // Function to update the joSensorData list.
      passedJson.put("location", String.format("%s,%s", gpsLat, gpsLong))
      passedJson.put("start_location", String.format("%s,%s",gpsLatitude, gpsLongStart ))
      passedJson.put("altitude", gpsAlt)
      passedJson.put("accuracy", gpsAccuracy)
      passedJson.put("bearing", gpsBearing)
      passedJson.put("gps_provider", gpsProvider)
      passedJson.put("speed", gpsSpeed)
      passedJson.put("speed_kmh", gpsSpeedKMH)
      passedJson.put("speed_mph", gpsSpeedMPH)
      passedJson.put("gps_updates", gpsUpdates)
      passedJson.put("acceleration", gpsAcceleration)
      passedJson.put("acceleration_kmh", gpsAccelerationKMH)
      passedJson.put("acceleration_mph", gpsAccelerationMPH)
      passedJson.put("distance_metres", gpsDistanceMetres)
      passedJson.put("distance_feet", gpsDistanceFeet)
      passedJson.put("total_distance_metres", gpsTotalDistance)
      passedJson.put("total_distance_km", gpsTotalDistanceKM)
      passedJson.put("total_distance_miles", gpsTotalDistanceMiles)
    }catch( JsonEx: JSONException ){
      Log.e( "GPSLogger", "Error creating Json. " )
      return passedJson
    }
    //Log.e(logTag, "Getting gps data!!");
    return passedJson
  }


}
