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

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class ServiceManager extends Service {
  /** Identify logcat messages. */
  private static final String logTag = "EsdServiceManager";
  /** Creates a new binder to this service. */
  private final IBinder serviceManagerBinder = new ServiceBinder();
  /** New instance of the sensor thread. */
  private SensorListener sensorListener;
  /** This thread pool is the working pool. Use this to execute the sensor runnable and Uploads. */
  private final ExecutorService workingThreadPool = Executors.newFixedThreadPool(2);
  /**
   * This thread pool handles the timer in which we control this service.
   * Timer that controls if/when we should be uploading data to the server.
   */
  private final ScheduledExecutorService timerPool = Executors.newScheduledThreadPool(2);
  /** Number of sensor readings this session. */
  private int sensorReadings = 0;
  /** Number of audio readings this session. */
  private int audioReadings = 0;
  /** Number of gps locations recorded this session */
  private int gpsReadings = 0;
  /** Number of documents indexed to Elastic this session. */
  private int documentsIndexed = 0;
  /** Number of data uploaded failures this session. */
  private int uploadErrors = 0;
  /** True if we are currently reading sensor data. */
  private boolean logging = false;
  /** Toggle, if we should be recording AUDIO sensor data. */
  private boolean audioLogging = false;
  /** Toggle, if we should be recording GPS data. */
  private boolean gpsLogging = false;
  /** Uploads controls the data flow between the local database and Elastic server. */
  private Uploads uploads;
  /** This is the runnable we will use to check network connectivity once every 30 min. */
  private final Runnable uploadRunnable = new Runnable() {
    @Override
    public void run() {
      if (!uploads.isWorking()) {
        workingThreadPool.submit(uploads);
      } else if (uploads.isWorking()) {
        Log.e(logTag, "Uploading already in progress.");
      } else {
        Log.e(logTag, "Failed to submit uploads runnable to thread pool!");
      }
    }
  };

  /** Applications' single instance of DatabaseHelper. Pass this to underlying functions. */
  private DatabaseHelper dbHelper;
  /** Toggle, if this service is currently running. Used by the main activity. */
  private boolean serviceActive = false;
  /** Time of the last sensor recording. Used to shut down unused resources. */
  private long lastSuccessfulSensorTime;
  /** If we go more than an a half hour without recording any sensor data, shut down this thread. */
  private final Runnable serviceTimeoutRunnable = new Runnable() {
    @Override
    public void run() {
      // Last sensor result plus 1/2 hour in milliseconds is greater than the current time.
      boolean timeCheck = lastSuccessfulSensorTime + (1000 * 60 * 30) > System.currentTimeMillis();
      if (!logging && !uploads.isWorking() && !timeCheck) {
        Log.e(logTag, "Shutting down service. Not logging!");
        stopServiceThread();
      }
    }
  };

  /**
   * Default constructor:
   * Instantiate the class broadcast receiver and messageFilters.
   * Register receiver to make sure we can communicate with the other threads.
   */
  @Override
  public void onCreate() {
    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    dbHelper = new DatabaseHelper(this);
    sensorListener = new SensorListener(getBaseContext(), dbHelper, this);
    uploads = new Uploads(sharedPrefs, this, dbHelper);
  }

  /** Return a reference to this instance of the service. */
  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return serviceManagerBinder;
  }

  /** Stop the service manager as a whole. */
  void stopServiceThread() {
    stopLogging();
    stopSelf();
  }

  /**
   * Runs when the mainActivity executes this service.
   * @param intent  - Not used.
   * @param flags   - Not used.
   * @param startId - Name of mainActivity.
   * @return START_STICKY will make sure the OS restarts this process if it has to trim memory.
   */
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    //Log.e(logTag, "ESD -- On Start Command." );
    if (!serviceActive) {
      lastSuccessfulSensorTime = System.currentTimeMillis();
      /* Use SensorRunnable class to start the logging process. */
      workingThreadPool.submit(sensorListener);
      /* Create an instance of Uploads, and submit to the thread pool to begin execution. */
      /* Schedule periodic checks for internet connectivity. */
      timerPool.scheduleAtFixedRate(uploadRunnable, 5, 60, TimeUnit.SECONDS);
      /* Schedule periodic checks for service shutdown due to inactivity. */
      timerPool.scheduleAtFixedRate(serviceTimeoutRunnable, 60, 60, TimeUnit.MINUTES);
      /* Send a message to the main thread to indicate the manager service has been initialized. */
      serviceActive = true;
      Log.i(logTag, "Started service manager.");
    }
    // If the service is shut down, do not restart it automatically.
    return Service.START_NOT_STICKY;
  }

  /** This method uses the passed UI handler to relay messages if/when the activity is running. */
  Bundle updateUiData() {
    Bundle outBundle = new Bundle();
    outBundle.putInt("sensorReadings", sensorReadings);
    outBundle.putInt("gpsReadings", gpsReadings);
    outBundle.putInt("audioReadings", audioReadings);
    outBundle.putInt("documentsIndexed", documentsIndexed);
    outBundle.putInt("uploadErrors", uploadErrors);
    outBundle.putLong("databasePopulation", dbHelper.databaseEntries());
    return outBundle;
  }

  /**
   * Used by the sensor thread to report success back here.
   * Will only be called when successful phone data is read.
   * @param gpsReading   - Boolean GPS sensor data.
   * @param audioReading - Boolean AUDIO sensor data.
   */
  public void sensorSuccess(boolean gpsReading, boolean audioReading) {
    sensorReadings++;
    if (gpsReading)
      gpsReadings++;
    if (audioReading)
      audioReadings++;
  }

  /**
   * Used by the upload thread to report success back here.
   * @param result - Boolean true if successful.
   * @param count  - Integer, how many records were indexed.
   */
  public void uploadSuccess(boolean result, int count) {
    if (result)
      documentsIndexed += count;
    else
      uploadErrors++;
  }

  /**
   * Control method to enable/disable gps recording.
   * @param power - Boolean true if we need to record sensor data.
   */
  void setGpsPower(boolean power) {
    gpsLogging = power;
    sensorListener.setGpsPower(power);
  }

  /**
   * Control method to change the sensor refresh rate.
   * @param updatedRefresh - Integer in milliseconds. 99 < updatedRefresh < 1000
   */
  void setSensorRefreshTime(int updatedRefresh) {
    sensorListener.setSensorRefreshTime(updatedRefresh);
  }

  /**
   * Control method to enable/disable audio recording.
   * @param power - Boolean true if we need to record sensor data.
   */
  void setAudioPower(boolean power) {
    audioLogging = power;
    sensorListener.setAudioPower(power);
  }

  /**
   * Start logging method:
   * Send logging requests to the sensor thread receiver.
   * 1. SENSOR toggle.
   * 2. GPS toggle.
   * 3. AUDIO toggle.
   */
  public void startLogging() {
    logging = true;
    sensorListener.setSensorPower(true);
    sensorListener.setGpsPower(gpsLogging);
    sensorListener.setAudioPower(audioLogging);
  }

  /** Stop the sensor listener. */
  public void stopLogging() {
    logging = false;
    sensorListener.setSensorPower(false);
  }

  /**
   * This runs when the service either shuts itself down or the OS trims memory.
   * 1. Stop sensor thread.
   * 2. Stop upload thread.
   */
  @Override
  public void onDestroy() {
    sensorListener.stopThread();
    uploads.stopUploading();
    super.onDestroy();
  }

  /** A class to supply a binder to the UI thread. */
  class ServiceBinder extends Binder {
    EsdServiceManager getService() {
      return EsdServiceManager.this;
    }
  }


}


