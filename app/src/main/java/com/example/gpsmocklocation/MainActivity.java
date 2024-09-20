package com.example.gpsmocklocation;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

public class MainActivity extends Activity {
    private Button startButton;
    private Button stopButton;
    private BroadcastReceiver serviceStatusReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("MainActivity", "Starting GPSMockLocationService");
                startService(new Intent(MainActivity.this, GPSMockLocationService.class));
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("MainActivity", "Stopping GPSMockLocationService");
                stopService(new Intent(MainActivity.this, GPSMockLocationService.class));
            }
        });

        // Create the BroadcastReceiver
        serviceStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean isRunning = intent.getBooleanExtra(GPSMockLocationService.EXTRA_IS_RUNNING, false);
                updateButtonColor(isRunning);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(GPSMockLocationService.ACTION_SERVICE_STATUS);
        registerReceiver(serviceStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the BroadcastReceiver
        unregisterReceiver(serviceStatusReceiver);
    }

    private void updateButtonColor(boolean isRunning) {
        if (isRunning) {
            startButton.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
        } else {
            startButton.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        }
    }
}
