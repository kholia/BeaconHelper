package com.bunzee.beacon_helper;

import android.Manifest;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;


public class GpsActivity extends AppCompatActivity {
    private static final int PERMISSION_ACCESS_FINE_LOCATION = 4;
    boolean have_permissions = true;
    android.location.LocationListener locationListener;
    android.location.LocationManager locationManager;


    public void InitialiseLocationListener(android.content.Context context) {
        locationManager = (android.location.LocationManager)
                context.getSystemService(android.content.Context.LOCATION_SERVICE);

        locationListener = new android.location.LocationListener() {
            public void onLocationChanged(android.location.Location location) {
                if (location.getProvider().equals(android.location.LocationManager.GPS_PROVIDER)) { // This is what we want!
                    long sysTime = System.currentTimeMillis();
                    TimeDeltaHandling.time_delta = (sysTime - location.getTime()) / 1000.0;
                    TextView results = (TextView) findViewById(R.id.gps_time_sync_status);
                    System.err.printf("Time Delta is (%f)s\n", TimeDeltaHandling.time_delta);
                    results.setText(String.format("GPS time synced! Time Delta is (%f)! Please press the 'Stop Continuous Sync', and then the back button, and continue using the app as usual. Double-check this 'Time Delta' against the value shown by the ClockSync app.", TimeDeltaHandling.time_delta));
                    /* Save to 'Shared Preferences' */
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(GpsActivity.this);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("time_delta", Double.toString(TimeDeltaHandling.time_delta));
                    editor.apply();
                } else {
                    // android.util.Log.d("Location", "Time Device (" + location.getProvider() + "): " + time);
                }
            }

            public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        try {
            // locationManager.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 1000, 0, locationListener);
            locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 500, 1, locationListener);
            // Note: To Stop listening use: locationManager.removeUpdates(locationListener)
        } catch (SecurityException e) {
            System.err.println("SecurityException in GPS sync code!?");
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_ACCESS_FINE_LOCATION) {
            have_permissions = true;
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gps);

        // GPS stuff
        if (ContextCompat.checkSelfPermission(GpsActivity.this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            have_permissions = false;
            ActivityCompat.requestPermissions(GpsActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_ACCESS_FINE_LOCATION);
        }

        Button buttonStop = findViewById(R.id.btnStop);
        buttonStop.setOnClickListener(v -> {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception e) {
                System.err.println("Exception in locationManager.removeUpdates(), ignoring...");
            }
        });

        TextView results = (TextView) findViewById(R.id.gps_time_sync_status);
        results.setText(Workarounds.gps_time_sync_status);

        if (have_permissions)
            InitialiseLocationListener(GpsActivity.this);
        else {
            results.setText("Press the back button and re-click on the GPS icon again!");
        }
    }
}
