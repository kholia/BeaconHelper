package com.bunzee.beacon_helper;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import com.bunzee.beacon_helper.databinding.ActivityMainBinding;
import com.jakewharton.processphoenix.ProcessPhoenix;

import org.joda.time.DateTime;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_RECORD_AUDIO = 0;
    private static final int PERMISSION_INTERNET = 1;
    private static final int PERMISSION_WAKE_LOCK = 2;

    private ActivityMainBinding binding;

    static boolean created = false;
    TextView tvt = null;
    TextView tv = null;

    private boolean found = false;
    private boolean sync_done = false;
    // Handler
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // https://romannurik.github.io/AndroidAssetStudio/icons-actionbar.html
    // https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html
    // sudo apt-get install gpick
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent I;
        switch (item.getItemId()) {
            case R.id.action_settings:
                // User chose the "Settings" item, show the app settings UI...
                I = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(I);
                return true;
            case R.id.action_help:
                I = new Intent(MainActivity.this, HelpActivity.class);
                startActivity(I);
                return true;
            case R.id.action_gps:
                I = new Intent(MainActivity.this, GpsActivity.class);
                startActivity(I);
                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    private static boolean crunchifyAddressReachable(String address, int port, int timeout) throws IOException {
        Socket crunchifySocket = new Socket();
        try {
            // Connects this socket to the server with a specified timeout value.
            crunchifySocket.connect(new InetSocketAddress(address, port), timeout);
            // Return true if connection successful
            return true;
        } catch (IOException exception) {
            // exception.printStackTrace();
            // Return false if connection fails
            return false;
        } finally {
            crunchifySocket.close();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getSupportActionBar();

        // https://stackoverflow.com/questions/3588682/is-it-normal-for-the-activity-oncreate-method-to-be-called-multiple-times
        if (created) {
            ProcessPhoenix.triggerRebirth(MainActivity.this);
        } else {
            created = true;
        }
        // Get required perms
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.INTERNET},
                    PERMISSION_INTERNET);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WAKE_LOCK)
                != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WAKE_LOCK},
                    PERMISSION_WAKE_LOCK);
        }

        // Note
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        // 'Time Delta'
        prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String time_delta = prefs.getString("time_delta", "0.0");
        try {
            TimeDeltaHandling.time_delta = Double.parseDouble(time_delta);
        } catch (NumberFormatException e) {
            TimeDeltaHandling.time_delta = 0.0;
            System.err.println("[-] Resetting time_delta to 0.0!");
        }
        // Example of a call to a native method
        if (tv == null)
            tv = binding.results;
        tv.setText("Looking for 'beacon' at 192.168.4.1...", TextView.BufferType.EDITABLE);
        tv.setMovementMethod(new ScrollingMovementMethod());

        // Main infinite loop executing in the background
        int delay = 1000;
        HandlerThread recordingHandlerThread = new HandlerThread("Background Worker Thread");
        recordingHandlerThread.start();
        Handler recordingHandler = new Handler(recordingHandlerThread.getLooper());

        Runnable backgroundRunnable;
        backgroundRunnable = new Runnable() {

            @Override
            public void run() {
                DateTime now = DateTime.now();
                int time_delta_seconds = (int) TimeDeltaHandling.time_delta;
                int time_delta_millis = (int) ((TimeDeltaHandling.time_delta - time_delta_seconds) * 1000);
                // System.err.printf("[Original] %d %d\n", now.getSecondOfMinute(), now.getMillisOfSecond());
                DateTime corrected_now = now.minusSeconds(time_delta_seconds);
                corrected_now = corrected_now.minusMillis(time_delta_millis);
                long epoch = corrected_now.getMillis() / 1000; /* XXX: some accuracy loss! */
                String host = "192.168.4.1";
                try {
                    found = crunchifyAddressReachable(host, 80, 2000);
                    if (found)
                        System.out.println("isReachable(host, port, timeout) Result ==> HTTP ping successful for host: " + host);
                    else
                        System.out.println("isReachable(host, port, timeout) Result ==> HTTP ping failed for host: " + host);
                } catch (Exception e) {
                    found = false;
                }
                mainHandler.post(() -> {
                    if (!sync_done) {
                        if (found)
                            tv.setText("Found 'beacon' at 192.168.4.1 ;)", TextView.BufferType.EDITABLE);
                        else
                            tv.setText("Looking for 'beacon' at 192.168.4.1. \n\nPlease connect to the 'beacon' WiFi Access Point if not already done so.", TextView.BufferType.EDITABLE);
                    }
                });

                if (found && !sync_done) {
                    try {
                        String u = "http://192.168.4.1/sync_time.cgi?epoch=" + epoch;
                        URL url = new URL(u);
                        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                        InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                        sync_done = true;
                        mainHandler.post(() -> {
                            tv.setText("Synchronized 'beacon' at 192.168.4.1 successfully! ;)", TextView.BufferType.EDITABLE);
                        });
                    } catch (IOException e) {
                        System.err.println(e);
                    }
                }
                mainHandler.removeCallbacks(this);
                recordingHandler.postDelayed(this, delay);
            }
        };
        mainHandler.removeCallbacks(backgroundRunnable);
        recordingHandler.postDelayed(backgroundRunnable, delay);
    }
}
