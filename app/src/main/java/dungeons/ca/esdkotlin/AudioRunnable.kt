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

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.json.JSONException

import org.json.JSONObject

open class AudioRunnable: Runnable {
  /** Identify logcat messages. */
  private val logTag = "audioRunnable"
  /** We use this to indicate to the sensor thread if we have data to send. */
  var hasData = false
  /** Use this control variable to stop the recording of audio data. */
  private var stopThread = false
  /** A reference to the current audio sample "loudness" in terms of percentage of mic capability. */
  private var amplitude = 0f
  /** A reference to the current audio sample frequency. */
  private var frequency = 0f
  /** The sampling rate of the audio recording. */
  private val sampleRate = 44100
  /** Short type array to feed to the recording API. */
  private var audioBuffer = ShortArray(0)
  /** Minimum buffer size required by AudioRecord API. */
  private var bufferSize: Int = 0

  /**
   * Default constructor.
   * Determine minimum buffer size, get data from Android audio api.
   * Set variables before executing the runnable.
   */
  init{
    // Buffer size in bytes.
    bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    // A check to make sure we are doing math on valid objects.
    if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
      bufferSize = sampleRate * 2
    }
  }

  /** Stop the audio logging thread. */
  fun setStopAudioThread() {
    stopThread = true
  }

  /** Main run method. */
  override fun run() {
    // ?????
    audioBuffer = ShortArray(bufferSize/2)
    // New instance of Android audio recording api.
    val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.DEFAULT,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )

    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
      Log.e(logTag, "AudioRecord has not been initialized properly.")
      return
    }
    stopThread = false

    while (!stopThread) {
      audioRecord.read(audioBuffer, 0, audioBuffer.size)
      var lowest = 0f
      var highest = 0f
      var zeroes = 0
      var lastValue = 0

      // Exploring the buffer. Record the highest and lowest readings
      audioBuffer.forEach { anAudioBuffer ->

        lowest = if(anAudioBuffer < lowest) anAudioBuffer.toFloat() else lowest
        highest = if(anAudioBuffer > highest) anAudioBuffer.toFloat() else highest
        // Down and coming up
        if (anAudioBuffer > 0 && lastValue < 0) {
          zeroes++
        }
        // Up and down
        if (anAudioBuffer < 0 && lastValue > 0) {
          zeroes++
        }
        lastValue = anAudioBuffer.toInt()
        // Calculate highest and lowest peak difference as a % of the max possible
        // value
        amplitude = (highest - lowest) / 65536 * 100
        // Take the count of the peaks in the time that we had based on the sample
        // rate to calculate frequency

        val seconds = audioBuffer.size / sampleRate.toFloat()
        frequency = zeroes / seconds / 2

        hasData = ( !frequency.isNaN() && !amplitude.isNaN() )


      }

    }
    audioRecord.stop()
    audioRecord.release()
    Log.i(logTag, "Audio recording stopping.")
  }

  /** Called on the sensor thread, delivers data to the sensor message handler. */
  fun getAudioData( passedJson: JSONObject): JSONObject {
    try {
      passedJson.put("frequency", frequency)
      passedJson.put("amplitude", amplitude)
    } catch (jsonEx: JSONException) {
      Log.e(logTag, "Error adding data to json. Frequency: $frequency : Amplitude: $amplitude")
    }
    hasData = false
    return passedJson
  }


}
