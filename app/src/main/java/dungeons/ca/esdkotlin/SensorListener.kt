package dungeons.ca.esdkotlin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.BatteryManager
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Created by Gurtok on 3/11/2018.
  */


open class SensorListener(context: Context ,passedDbHelper: DatabaseHelper, serviceManger: ServiceManager ): Thread(), android.hardware.SensorEventListener{

  /** Use this to identify this classes log messages. */
  private val logTag = "SensorListener"
  /** The controlling service manager. */
  private val serviceManager: ServiceManager = serviceManger
  /** Main activity context. */
  private val passedContext: Context = context
  /** Gives access to the local database via a helper class. */
  private val dbHelper = passedDbHelper
  /** The audio runnable is executed on this thread. */
  private val audioThread: ExecutorService = Executors.newSingleThreadExecutor()

  // Date / Time variables.
  /** A static reference to the custom date format. */
  private val logDateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ", Locale.US)
  /** Timers, the schema is defined else where. */
  private var startTime: Long = 0
  /** Used to get access to GPS. */
  private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
// Sensor variables.
  private var lastUpdate: Long = 0
  /** If we are currently logging PHONE sensor data. */
  private var sensorLogging = false
  /** Instance of sensorMessageHandler Manager. */
  private val mSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  /** Each loop, data wrapper to upload to Elastic. */
  private var joSensorData: JSONObject = JSONObject()
  /** Array to hold sensorMessageHandler references. */
  private var usableSensorList: MutableList<Int> = MutableList(0,{0})
  /** Refresh time in milliseconds. Default = 250ms. */
  private var sensorRefreshTime: Int = 250
  /** If listeners are active. */
  private var sensorsRegistered = false

// GPS variables.
  /** Battery level in percentages. */
  private var batteryLevel: Double = 0.0
  /** Helper class to organize gps data. */
  private var gpsLogger: GPSLogger = GPSLogger()
  /** Control for telling if we have already registered the gps listeners. */
  private var gpsRegistered = false
// AUDIO variables.
  /** Helper class for obtaining audio data. */
  private var audioRunnable = AudioRunnable()
  /** Control variable to make sure we only create one audio logger. */
  private var audioRegistered = false

  /** Listener for battery updates. */
  private var batteryReceiver = object: BroadcastReceiver(){
    override fun onReceive( context:Context, intent:Intent)
    {
      val batteryData = intent . getIntExtra (BatteryManager.EXTRA_LEVEL, -1)
      val batteryScale = intent . getIntExtra (BatteryManager.EXTRA_SCALE, -1)
      if (batteryData > 0 && batteryScale > 0) {
        batteryLevel = batteryData.toDouble()
      }
    }
  }

  fun stopThread(){
    this.interrupt()
  }


  /**
   * This is the main recording loop. One reading per sensorMessageHandler per loop.
   * Update timestamp in sensorMessageHandler data structure.
   * Store the logging start time with each document.
   * Store the duration of the sensorMessageHandler log with each document.
   * Dump gps data into document if it's ready.
   * Put battery status percentage into the Json.
   * @param event A reference to the event object.
   */
  override fun onSensorChanged( event:SensorEvent ) {
    if (!interrupted() && System.currentTimeMillis() > lastUpdate + sensorRefreshTime && sensorsRegistered) {
      // ^^ Make sure we generate docs at an adjustable rate.
      // 250ms is the default setting.

      // Reset our flags to update the service manager about the type of sensor readings.
      var gpsReading = false
      var audioReading = false

      var sensorName: String
      var sensorHierarchyName: kotlin.collections.List<String>
      try {
        joSensorData.put("@timestamp", logDateFormat.format(Date(System.currentTimeMillis())))
        joSensorData.put("start_time", logDateFormat.format(Date(startTime)))
        joSensorData.put("log_duration_seconds", (System.currentTimeMillis() - startTime) / 1000)
        if (gpsRegistered && gpsLogger.gpsHasData) {
          joSensorData = gpsLogger.getGpsData(joSensorData)
          gpsReading = true
        }
        if (audioRegistered && audioRunnable.hasData) {
          joSensorData = audioRunnable.getAudioData(joSensorData)
          audioReading = true
        }
        if (batteryLevel > 0) {
          joSensorData.put("battery_percentage", batteryLevel)
        }
        for ( cursor in event.values ) {
          if (!cursor.isNaN() && cursor < Long.MAX_VALUE && cursor > Long.MIN_VALUE) {
            sensorHierarchyName = event.sensor.stringType.split("\\.")
            sensorName = if (sensorHierarchyName.isEmpty() )  event.sensor.stringType else sensorHierarchyName[sensorHierarchyName.size - 1]
            joSensorData.put(sensorName, cursor)
          }
        }
        dbHelper.jsonToDatabase(joSensorData)
        serviceManager.sensorSuccess(gpsReading, audioReading)
        lastUpdate = System.currentTimeMillis()
      } catch (JsonEx: JSONException ) {
        Log.e(logTag, JsonEx.message + " || " + JsonEx.cause)
      }
    }
  }

// Phone Sensors

  /** Use this method to control if we should be recording sensor data or not. */
  fun setSensorPower( power: Boolean) {
    sensorLogging = power
    if (power && !sensorsRegistered) {
      registerSensorListeners()
    }
    if (!power && sensorsRegistered) {
      unregisterSensorListeners()
    }
  }

  /**
   * A control method for collection intervals.
   */
  fun setSensorRefreshTime( updatedRefresh: Int) {
    sensorRefreshTime = updatedRefresh
  }

  /** Method to register listeners upon logging. */
  private fun registerSensorListeners() {
    startTime = System.currentTimeMillis()
    lastUpdate = startTime
    parseSensors()
    // Register each sensorMessageHandler to this activity.
    for (cursorInt in usableSensorList ) {
      mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(cursorInt),
          SensorManager.SENSOR_DELAY_NORMAL, null)
    }
    val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    passedContext.registerReceiver(this.batteryReceiver, batteryFilter, null, null)
    sensorsRegistered = true
  }

  /** Unregister listeners. */
  private fun unregisterSensorListeners() {
    if (sensorsRegistered) {
      passedContext.unregisterReceiver(this.batteryReceiver)
      mSensorManager.unregisterListener(this)
      setGpsPower(false)
      setAudioPower(false)
    }
    sensorsRegistered = false
  }

  /** Generate a list of on-board phone sensors. */
  private fun parseSensors() {
    try {
      val deviceSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL).toTypedArray()
      usableSensorList = kotlin.collections.mutableListOf()
      for (sensor in deviceSensors) {
        // Use this to filter out trigger(One-shot) sensors, which are dealt with differently.
        if (sensor.reportingMode != Sensor.REPORTING_MODE_ONE_SHOT) {
          usableSensorList.add(sensor.type)
        }
      }
    } catch (nullPtrEx: NullPointerException) {
      Log.e(logTag, "Sensor list is returned null.")
    }
  }

// GPS

  /** Control method to enable/disable gps recording. */
  fun setGpsPower( power: Boolean ) {
    //Log.e( logTag, "Set gps power: " + power );
    if (power && sensorLogging && !gpsRegistered) { registerGpsSensors() }
    if (!power && sensorLogging && gpsRegistered) { unRegisterGpsSensors() }
  }


  /** Register gps sensors to enable recording. */
  private fun registerGpsSensors() {


    try {
      locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, sensorRefreshTime - 10L, 0f, gpsLogger)
      locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, sensorRefreshTime - 10L, 0f, gpsLogger)
      Log.i(logTag, "GPS listeners registered.")
      gpsRegistered = true
    } catch ( secEx: SecurityException) {
      Log.e(logTag, "Failure turning gps on/off. Cause: " + secEx.message)
      secEx.printStackTrace()
    } catch (runTimeEx: RuntimeException) {
      Log.e(logTag, "StackTrace: ")
      runTimeEx.printStackTrace()
    }

  }

  /** Unregister gps sensors. */
  private fun unRegisterGpsSensors() {
    locationManager.removeUpdates(gpsLogger)
    gpsRegistered = false
    Log.i(logTag, "GPS unregistered.")
  }

  //AUDIO

  /** Set audio recording on/off. */
  fun setAudioPower( power:Boolean) {
    //Log.e( logTag, "Set audio power: " + power );
    if (power && sensorLogging && !audioRegistered) {
      registerAudioSensors()
    }
    if (!power && sensorLogging  && audioRegistered) {
      unregisterAudioSensors()
    }
  }

  /** Register audio recording thread. */
  private fun registerAudioSensors() {
    audioThread.submit(audioRunnable)
    audioRegistered = true
    Log.i(logTag, "Registered audio sensors.")

  }

  /** Stop audio recording thread. */
  private fun unregisterAudioSensors() {
    audioRunnable.setStopAudioThread()
    audioRegistered = false
    Log.i(logTag, "Unregistered audio sensors.")
  }


  override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
  }

}