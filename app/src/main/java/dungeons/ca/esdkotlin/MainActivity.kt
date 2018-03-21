package dungeons.ca.esdkotlin

import android.Manifest
import android.app.Activity
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
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import dungeons.ca.esdkotlin.ServiceManager.ServiceBinder



/** Created by Gurtok on 3/11/2018 */

// Stubbing out the port for now.
class MainActivity : Activity(){

  /** Identify logcat messages. */
  private val logTag = "MainActivity"
  /** Do NOT record more than once every 50 milliseconds. Default value is 250ms. */
  private val MIN_SENSOR_REFRESH = 50
  /** Persistent access to the apps database to avoid creating multiple db objects. */
  var databaseHelper: DatabaseHelper = DatabaseHelper()
  /** The backend service that runs data collection and uploading. */
  var serviceManager: ServiceManager = ServiceManager()
  /** If the UI thread is active, it should be bound to the service manager. */
  var isBound = false
  /** Refresh time in milliseconds. Default = 250ms. */
  var sensorRefreshTime: Int = 250

  private val gpsPermissionArray = Array(2){
    Manifest.permission.ACCESS_FINE_LOCATION
    Manifest.permission.ACCESS_COARSE_LOCATION  }

  private val audioPermissionArray = Array(1){ Manifest.permission.RECORD_AUDIO }

  /** This scheduled thread will run the UI screen updates. */
  var updateTimer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  /** The current database population. Probably does not need to be a long, research how sql deals with IDs. */
  var sensorTV: TextView = TextView(null)
  var documentsTV: TextView = TextView(null)
  var gpsTV: TextView = TextView(null)
  var errorsTV: TextView = TextView(null)
  var audioTV: TextView = TextView(null)
  var databaseTV: TextView = TextView(null)
  /** Number of sensor readings this session */
  var sensorReadings: Int = 0
  var documentsIndexed: Int = 0
  var databasePopulation: Int = 0
  var gpsReadings: Int = 0
  var uploadErrors: Int = 0
  var audioReadings: Int = 0


  private var updateRunnable: Runnable = Runnable {
    run{
      runOnUiThread( { updateScreen() } )
    }
  }

  private var serviceManagerConnection =  object: ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      val serviceBinder  = service as ServiceBinder
      serviceManager = serviceBinder.getService()
      isBound = true
    }

    override fun onServiceDisconnected(p0: ComponentName?) {
      isBound = false
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
  private fun getScreenUpdates(){
    if( isBound ){
      val dataBundle:Bundle = serviceManager.updateUiData()
      sensorReadings = dataBundle.getInt("sensorReadings" )
      documentsIndexed = dataBundle.getInt("documentsIndexed" )
      gpsReadings = dataBundle.getInt("gpsReadings" )
      uploadErrors = dataBundle.getInt("uploadErrors" )
      audioReadings = dataBundle.getInt("audioReadings" )
      databasePopulation = dataBundle.getLong("databasePopulation" ).toInt()
    }
  }

  /** Call for updates, then update the display. */
  private fun updateScreen(){
    getScreenUpdates()
    sensorTV.text = sensorReadings.toString()
    documentsTV.text = documentsIndexed.toString()
    gpsTV.text = gpsReadings.toString()
    errorsTV.text = uploadErrors.toString()
    audioTV.text = audioReadings.toString()
    databaseTV.text = databasePopulation.toString()

  }

  /**
   * Go through the sensor array and light them all up
   * btnStart: Click a button, get some sensor data.
   * ibSetup: Settings screen.
   * seekBar: Adjust the collection rate of data.
   * gpsToggle: Turn gps collection on/off.
   * audioToggle: Turn audio recording on/off.
   */
  private fun buildButtonLogic(){

    // Main start button
    val startButton = findViewById<ToggleButton>(R.id.toggleStart)
    startButton.setOnCheckedChangeListener { _, isChecked ->
      if (isBound) {
        if (isChecked) {
          Log.e(logTag, "Start button ON !")
          startButton.setBackgroundResource(R.drawable.main_button_shape_on)
          serviceManager.startLogging()
        } else {
          Log.e(logTag, "Start button OFF !")
          startButton.setBackgroundResource(R.drawable.main_button_shape_off)
          serviceManager.stopLogging()
        }
      }
    }

    // 3 Dot button to access settings.
    val settingsButton = findViewById<ImageButton>(R.id.settings)
    settingsButton.setOnClickListener{ _ ->
      startActivity( Intent( baseContext, SettingsActivity::class.java))
    }

    // Radio button to indicate if we should be sampling gps location data.
    val gpsCheckBox = findViewById<CheckBox>(R.id.gpsCheckBox)
    gpsCheckBox.setOnCheckedChangeListener{ _, isChecked ->
      // If gps button is turned ON.
      if (!gpsPermission() && isChecked) {
        gpsCheckBox.toggle()
        Toast.makeText(applicationContext, "GPS access denied.", Toast.LENGTH_SHORT).show()
      } else {
        serviceManager.setGpsPower( isChecked )
      }
    }

    // Radio button to indicate if we should be sampling audio data from MIC.
    val audioCheckBox = findViewById<CheckBox>(R.id.audioCheckBox)
    audioCheckBox.setOnCheckedChangeListener{ _, isChecked ->
      // If audio button is turned ON.
      if (!audioPermission() && isChecked) {
        audioCheckBox.toggle()
        Toast.makeText(applicationContext, "Audio access denied.", Toast.LENGTH_SHORT).show()
      } else {
        serviceManager.setAudioPower( isChecked )
      }
    }

    // Seekbar used to modify our MASTER sampling rate!
    val seekbar = findViewById<SeekBar>(R.id.seekBar)
    val seekbarText = findViewById<TextView>(R.id.TickText)
    seekbar.setOnSeekBarChangeListener( object: SeekBar.OnSeekBarChangeListener{
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if ( progress * 10 < MIN_SENSOR_REFRESH ) {
          Toast.makeText(applicationContext, "Minimum sensor refresh is 50 ms", Toast.LENGTH_SHORT).show()
          sensorRefreshTime = MIN_SENSOR_REFRESH
          seekBar?.progress = 5
        } else {
          sensorRefreshTime = progress * 10
        }
        seekbarText.text = String.format("%s %s %s",getString(R.string.Collection_Interval), sensorRefreshTime, getString(R.string.milliseconds) )
      }

    override fun onStartTrackingTouch(p0: SeekBar?) {} // Intentionally BLANK
    override fun onStopTrackingTouch(p0: SeekBar?)  {} // Intentionally BLANK
    })
  }

  /**
   * Prompt user for GPS access.
   * Write this result to shared preferences.
   * @return True if we asked for permission and it was granted.
   */
  private fun gpsPermission(): Boolean{

    ActivityCompat.requestPermissions(this, gpsPermissionArray, MODE_PRIVATE )

    val gpsPermissionCoarse = (ContextCompat.checkSelfPermission(this, Manifest.permission.
        ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)

    val gpsPermissionFine = (ContextCompat.checkSelfPermission(this, android.Manifest.permission.
        ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)

    return (gpsPermissionFine || gpsPermissionCoarse)
  }

  /**
   * Prompt user for MICROPHONE access.
   * Write this result to shared preferences.
   * @return True if we asked for permission and it was granted.
   */
  private fun audioPermission(): Boolean{

    ActivityCompat.requestPermissions(this, audioPermissionArray, 1)

    return (ContextCompat.checkSelfPermission(this,
        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
  }

  /** If our activity is paused, we need to indicate to the service manager via a static variable. */
  override fun onPause() {
    databaseHelper.close()
    super.onPause()
  }

  /**
   * When the activity starts or resumes, we start the upload process immediately.
   * If we were logging, we need to start the logging process. ( OS memory trim only )
   */
  override fun onResume() {
    super.onResume()
    startServiceManager()
    databaseHelper = DatabaseHelper(this)
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






