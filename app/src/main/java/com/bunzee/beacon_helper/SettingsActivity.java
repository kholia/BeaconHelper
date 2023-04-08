package com.bunzee.beacon_helper;

import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.DropDownPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static native void setFrequency(int frequency_);

    // Settings
    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            // Handle 'Time Delta'
            EditTextPreference tpreference = findPreference("time_delta");
            String ov = tpreference.getText();
            tpreference.setOnPreferenceChangeListener((preference, newValue) -> {
                EditTextPreference ep = (EditTextPreference) preference;
                String value = (String) newValue;
                value = value.trim();
                if (!value.equals(ov)) {
                    ep.setText(value);
                }
                Double time_delta = 0.0;
                try {
                    time_delta = Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    ep.setText("0.0"); // recover from a bad value
                }
                TimeDeltaHandling.time_delta = time_delta;
                // System.err.println(TimeDeltaHandling.time_delta);
                return false;
            });
        }
    }
}