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
import android.widget.*
import dungeons.ca.esdkotlin.ServiceManager.ServiceBinder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit


/** Created by Gurtok on 3/11/2018 */

// Stubbing out the port for now.
class MainActivity : Activity(){

  /** Identify logcat messages. */
  private val logTag = "MainActivity"
  /** Do NOT record more than once every 50 milliseconds. Default value is 250ms. */
  private val minSensorRefresh = 50

  /** If the UI thread is active, it should be bound to the service manager. */
  var isBound = false
  /** Refresh time in milliseconds. Default = 250ms. */
  var sensorRefreshTime: Int = 250

  private val gpsRequestPermissionCode = 123123
  private val audioRequestPermissionCode = 456456

  private var gpsFinePermission = false
  private var audioPermission = false

  private val gpsPermissionArray = Array(1){ Manifest.permission.ACCESS_FINE_LOCATION}

  private val audioPermissionArray = Array(1){ Manifest.permission.RECORD_AUDIO }

  /** This scheduled thread will run the UI screen updates. */
  private var updateTimer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

  /** The current database population. Probably does not need to be a long, research how sql deals with IDs. */
  private lateinit var sensorTV: TextView
  private lateinit var documentsTV: TextView
  private lateinit var gpsTV: TextView
  private lateinit var errorsTV: TextView
  private lateinit var audioTV: TextView
  private lateinit var databaseTV: TextView

  /** Number of sensor readings this session */
  private var sensorReadings: Int = 0
  private var documentsIndexed: Int = 0
  private var databasePopulation: Int = 0
  private var gpsReadings: Int = 0
  private var uploadErrors: Int = 0
  private var audioReadings: Int = 0

  private lateinit var startIntent:Intent


  /** The backend service that runs data collection and uploading. */
  private var serviceManager = ServiceManager()



  private var serviceManagerConnection = object: ServiceConnection {
    override fun onServiceConnected(name: ComponentName, service: IBinder) {
      Log.i(logTag, "We are bound to the service manager!")
      serviceManager = (service as ServiceBinder).getService()
      isBound = true
    }
    override fun onServiceDisconnected(p0: ComponentName?) {
      isBound = false
    }
  }

  private var updateRunnable = Runnable {
    run{
      runOnUiThread( { updateScreen() } )
    }
  }



  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    sensorTV = findViewById(R.id.sensor_tv)
    documentsTV = findViewById(R.id.documents_tv)
    gpsTV = findViewById(R.id.gps_TV)
    errorsTV = findViewById(R.id.errors_TV)
    audioTV = findViewById(R.id.audioCount)
    databaseTV = findViewById(R.id.databaseCount)

    buildButtonLogic()

    startIntent  = Intent(this, serviceManager::class.java)
    startService( startIntent )

    Log.i(logTag, "Started Main Activity!")
  }

  override fun onStart() {
    super.onStart()
    Log.i(logTag, "onStart!!")
    bindService( startIntent, serviceManagerConnection, Context.BIND_AUTO_CREATE )
  }

  /** Call for updates, then update the display. */
  private fun updateScreen(){
    if(isBound) {
      val dataBundle:Bundle = serviceManager.updateUiData()
      sensorReadings = dataBundle.getInt("sensorReadings" )
      documentsIndexed = dataBundle.getInt("documentsIndexed" )
      gpsReadings = dataBundle.getInt("gpsReadings" )
      uploadErrors = dataBundle.getInt("uploadErrors" )
      audioReadings = dataBundle.getInt("audioReadings" )
      databasePopulation = dataBundle.getLong("databasePopulation" ).toInt()
    }

    sensorTV.text = sensorReadings.toString()
    documentsTV.text = documentsIndexed.toString()
    gpsTV.text = gpsReadings.toString()
    errorsTV.text = uploadErrors.toString()
    audioTV.text = audioReadings.toString()
    databaseTV.text = databasePopulation.toString()

    val seekbarText = findViewById<TextView>(R.id.TickText)
    seekbarText.text = String.format("${getString(R.string.Collection_Interval)} $sensorRefreshTime ${getString(R.string.milliseconds)}" )
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
      if(isBound){
        // If gps button is turned ON.
        if (isChecked) {
          // Make sure we have permission each time the button is turned on.
          ActivityCompat.requestPermissions(this, gpsPermissionArray, gpsRequestPermissionCode )
          if(gpsPermission()){
            serviceManager.setGpsPower( true )
          }else {
            gpsCheckBox.toggle()
            Toast.makeText(applicationContext, "GPS access denied.", Toast.LENGTH_SHORT).show()
          }
        }else{
          serviceManager.setGpsPower( false )
        }
      }
    }

    // Radio button to indicate if we should be sampling audio data from MIC.
    val audioCheckBox = findViewById<CheckBox>(R.id.audioCheckBox)
    audioCheckBox.setOnCheckedChangeListener{ _, isChecked ->
      if (isBound){
        // If audio button is turned ON.
        if (isChecked) {
          ActivityCompat.requestPermissions(this, audioPermissionArray, audioRequestPermissionCode)
          if( audioPermission() ){
            serviceManager.setAudioPower( true )
          }else{
            audioCheckBox.toggle()
            Toast.makeText(applicationContext, "Audio access denied.", Toast.LENGTH_SHORT).show()
          }
        }else{
          serviceManager.setAudioPower( false )
        }
      }
    }

    // Seekbar used to modify our MASTER sampling rate!
    val seekbar = findViewById<SeekBar>(R.id.seekBar)
    val seekbarText = findViewById<TextView>(R.id.TickText)
    seekbar.setOnSeekBarChangeListener( object: SeekBar.OnSeekBarChangeListener{

      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if ( progress * 10 < minSensorRefresh ) {
          Toast.makeText(applicationContext, "Minimum sensor refresh is 50 ms", Toast.LENGTH_SHORT).show()
          sensorRefreshTime = minSensorRefresh
          seekBar?.progress = 5
        } else {
          sensorRefreshTime = progress * 10
          if(isBound)
            serviceManager.setSensorRefreshTime(sensorRefreshTime)
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
    gpsFinePermission = (ContextCompat.checkSelfPermission(this, gpsPermissionArray[0]) == PackageManager.PERMISSION_GRANTED)
    return gpsFinePermission
  }

  /**
   * Prompt user for GPS access.
   * Write this result to shared preferences.
   * @return True if we asked for permission and it was granted.
   */
  private fun audioPermission(): Boolean{
    audioPermission = (ContextCompat.checkSelfPermission(this, audioPermissionArray[0]) == PackageManager.PERMISSION_GRANTED)
    return audioPermission
  }


  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
    if (permissions != null) {
      when (requestCode) {
        gpsRequestPermissionCode -> {
          gpsFinePermission = (grantResults?.get(0) == 0)
        }
         audioRequestPermissionCode -> {
           audioPermission = (grantResults?.get(0) == 0)
         }
      }
    }
  }

  /** If our activity is paused, we need to indicate to the service manager via a static variable. */
  override fun onPause() {
    super.onPause()
    unbindService(serviceManagerConnection)
  }

  /**
   * When the activity starts or resumes, we start the upload process immediately.
   */
  override fun onResume() {
    bindService( startIntent, serviceManagerConnection, Context.BIND_AUTO_CREATE )
    updateTimer.scheduleAtFixedRate(updateRunnable, 1000, 500, TimeUnit.MILLISECONDS )

    super.onResume()
  }

  /** If the user exits the application. */
  override fun onDestroy() {
    if(isBound)
      serviceManager.stopServiceThread()

    updateTimer.shutdown()
    super.onDestroy()
  }









}






