package com.example.gpsmocklocation;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private Button startButton;
    private Button stopButton;
    private Spinner usbDeviceSpinner;
    private BroadcastReceiver serviceStatusReceiver;

    private UsbManager usbManager;
    private List<UsbDevice> usbDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        usbDeviceSpinner = findViewById(R.id.usbDeviceSpinner);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        usbDevices = new ArrayList<>();

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UsbDevice selectedDevice = (UsbDevice) usbDeviceSpinner.getSelectedItem();
                if (selectedDevice != null) {
                    Log.d(TAG, "Starting GPSMockLocationService");
                    Intent serviceIntent = new Intent(MainActivity.this, GPSMockLocationService.class);
                    serviceIntent.putExtra(UsbManager.EXTRA_DEVICE, selectedDevice);
                    startService(serviceIntent);
                } else {
                    Toast.makeText(MainActivity.this, "Please select a USB device", Toast.LENGTH_SHORT).show();
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Stopping GPSMockLocationService");
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

        refreshUsbDeviceList();
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onResume() {
        super.onResume();
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(GPSMockLocationService.ACTION_SERVICE_STATUS);
        registerReceiver(serviceStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        // Refresh USB device list
        refreshUsbDeviceList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the BroadcastReceiver
        unregisterReceiver(serviceStatusReceiver);
    }

    private void updateButtonColor(boolean isRunning) {
        Log.d(TAG, isRunning ? "Service is running" : "Service is not running");

        if (isRunning) {
            startButton.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
        } else {
            startButton.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    private void refreshUsbDeviceList() {
        List<UsbDevice> usbDevices = new ArrayList<>(usbManager.getDeviceList().values());
        UsbDeviceAdapter adapter = new UsbDeviceAdapter(this, usbDevices);
        usbDeviceSpinner.setAdapter(adapter);

        usbDeviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                UsbDevice selectedDevice = (UsbDevice) parent.getItemAtPosition(position);
                Log.d(TAG, "Selected USB device: " + selectedDevice.getDeviceName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
}