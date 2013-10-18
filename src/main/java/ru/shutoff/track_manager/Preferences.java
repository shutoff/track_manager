package ru.shutoff.track_manager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class Preferences extends PreferenceActivity {

    SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        addPreferencesFromResource(R.xml.preferences);

        Preference aboutPref = findPreference("about");
        try {
            PackageManager pkgManager = getPackageManager();
            PackageInfo info = pkgManager.getPackageInfo("ru.shutoff.track_manager", 0);
            aboutPref.setSummary(aboutPref.getSummary() + " " + info.versionName);
        } catch (Exception ex) {
            aboutPref.setSummary("");
        }
        aboutPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getBaseContext(), About.class);
                startActivity(intent);
                return true;
            }
        });

        final ListPreference mapPref = (ListPreference) findPreference("map_type");
        mapPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                mapPref.setSummary(newValue.toString());
                return true;
            }
        });

        mapPref.setSummary(preferences.getString("map_type", "Google"));
    }

}
