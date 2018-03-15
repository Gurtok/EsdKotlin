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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

/** */
public class SettingsActivity extends PreferenceActivity {

  /** */
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    checkValues();
    getFragmentManager().beginTransaction().replace(android.R.id.content, new Fragment_Preference()).commit();

  }

  /** */
  private void checkValues() {
    SharedPreferences sharedPrefs =
    PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    String es_host = sharedPrefs.getString("host", "localhost");
    String es_port = sharedPrefs.getString("port", "9200");
    String es_index = sharedPrefs.getString("index", "sensor_dump");
    String es_type = sharedPrefs.getString("type", "phone_data");
    String current_values = es_host + ":" + es_port + "/"
    + es_index + "/" + es_type;
    Log.v("Preferences", current_values);
  }


}

