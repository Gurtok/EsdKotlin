package dungeons.ca.esdkotlin

import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import org.w3c.dom.Text

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import dungeons.ca.esdkotlin.EsdServiceManager


/**
 * Created by Gurtok on 3/11/2018.
 */
/** Identify logcat messages. */
val logTag = "MainActivity"
/** Do NOT record more than once every 50 milliseconds. Default value is 250ms. */
val MIN_SENSOR_REFRESH = 50;
/** Persistent access to the apps database to avoid creating multiple db objects. */
val databaseHelper: DatabaseHelper = DatabaseHelper()
/** The backend service that runs data collection and uploading. */
var serviceManager: EsdServiceManager = ServiceManager ;
/** If the UI thread is active, it should be bound to the service manager. */
var boolean isBound = false;
/** Refresh time in milliseconds. Default = 250ms. */
var int sensorRefreshTime = 250;
/** This scheduled thread will run the UI screen updates. */
val ScheduledExecutorService updateTimer = Executors.newSingleThreadScheduledExecutor();
/** Number of sensor readings this session */
var sensorReadings, documentsIndexed, gpsReadings, uploadErrors, audioReadings = 0;


/** The current database population. Probably does not need to be a long, research how sql deals with IDs. */
var databasePopulation: TextView = 0L
var sensorTV: TextView
var documentsTV: TextView
var gpsTV: TextView
var errorsTV: TextView
var audioTV: TextView
var databaseTV: TextView = null




// Stubbing out the port for now.

class MainActivity : Activity(){

  var updateRunnable: Runnable = Runnable {
    run{
      runOnUiThread( { updateScreen() } )
    }
  }

  private val serviceManagerConnection =  object: ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      val serviceBinder  = service as ServiceBinder
      serviceManager = serviceBinder.getService();
      isBound = true;
    }

    override fun onServiceDisconnected(p0: ComponentName?) {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
  }

  // On Create in Kotlin!!
  init {
    setContentView(R.layout.activity_main)
    buildButtonLogic()
    Log.e(logTag, "Started Main Activity!")

    sensorTV = findViewById(R.id.sensor_tv)
    documentsTV = findViewById(R.id.documents_tv)
    gpsTV = findViewById(R.id.gps_TV)
    errorsTV = findViewById(R.id.errors_TV)
    audioTV = findViewById(R.id.audioCount)
    databaseTV = findViewById(R.id.databaseCount)
  }

  private fun startServiceManager(){
    val startIntent  = Intent(this, EsdServiceManager.class.)
    startService( startIntent )
    bindService( startIntent, serviceManagerConnection, Context.BIND_AUTO_CREATE )
  }







  /** Call service manager to receive a bundle of updated data counts. */
  fun getScreenUpdates(){

  }

  /** Call for updates, then update the display. */
  fun updateScreen(){

  }

  /**
   * Go through the sensor array and light them all up
   * btnStart: Click a button, get some sensor data.
   * ibSetup: Settings screen.
   * seekBar: Adjust the collection rate of data.
   * gpsToggle: Turn gps collection on/off.
   * audioToggle: Turn audio recording on/off.
   */
  fun buildButtonLogic(){

  }

  /**
   * Prompt user for GPS access.
   * Write this result to shared preferences.
   * @return True if we asked for permission and it was granted.
   */
  fun gpsPermission(): Boolean{

    return false
  }

  /**
   * Prompt user for MICROPHONE access.
   * Write this result to shared preferences.
   * @return True if we asked for permission and it was granted.
   */
  fun audioPermission(): Boolean{

    return false
  }

  /** If our activity is paused, we need to indicate to the service manager via a static variable. */
  override fun onPause() {
    super.onPause()
    startServiceManager()
    databaseHelper = new DatabaseHelper(this)
    updateTimer.scheduleAtFixedRate(updateRunnable, 500, 500, TimeUnit.MILLISECONDS )
  }

  /**
   * When the activity starts or resumes, we start the upload process immediately.
   * If we were logging, we need to start the logging process. ( OS memory trim only )
   */
  override fun onResume() {
    super.onResume()
    startServiceManager()
    databaseHelper = new DatabaseHelper(this)
    updateTimer.scheduleAtFixedRate(updateRunnable, 500, 500, TimeUnit.MILLISECONDS )
  }

  /** If the user exits the application. */
  override fun onDestroy() {
    unbindService( serviceManagerConnection )
    serviceManager.stopServiceThread()
    updateTimer.shutdown()
    databaseHelper.close()
    super.onDestroy()
  }










}






