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


import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.util.Log
import com.google.android.gms.common.api.CommonStatusCodes
import dungeons.ca.esdkotlin.ui.camera.BarcodeMainActivity

/** Created by Gurtok on 3/11/2018. */

/**
 * A fragment to contain the QR code activity.
 */
class QRcodeFragment : PreferenceFragment(){

  /** Identify logcat messages. */
  private val logTag = "Preference_Frag"
  /** Integer to ID the QR activity result. */
  val QR_REQUEST_CODE = 1232131213
  /** Reference to the applications shared preferences. */
  private var sharedPreferences: SharedPreferences();

  /**
   * Base creation method.
   * @param savedInstanceState - Not used.
   */
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context)
    addPreferencesFromResource(R.xml.preferences)
    setupQRButton()
  }

  /** Initializes the QR code button in the settings list. */
  private fun setupQRButton() {
    val qrPreference = this.preferenceManager.findPreference("qr_code")
    qrPreference.setOnPreferenceClickListener { _: Preference ->
      val qrIntent = Intent(context, BarcodeMainActivity::class.java)
      startActivityForResult(qrIntent, QR_REQUEST_CODE)
    }
  }

  /**
   * This method runs when the fragment returns an intent.
   * @param resultCode - ID of data.
   * @param data       - Intent containing the new host string.
   */
  override fun onActivityResult( requestCode: Int, resultCode: Int, data: Intent) {
    Log.e(logTag, "Received results from QR reader.")
    if (resultCode == CommonStatusCodes.SUCCESS) {
      Log.e(logTag, "Received SUCCESS CODE")
      val hostString = data.getStringExtra("hostString")
      if (!hostString.isBlank()) {
        sharedPreferences.edit().putString("host", hostString).apply()
        onCreate(this.arguments)
      }
      }
    }

}
