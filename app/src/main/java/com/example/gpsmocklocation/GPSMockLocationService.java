package com.example.gpsmocklocation;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GPSMockLocationService extends Service {
    public static final String ACTION_SERVICE_STATUS = "com.example.gpsmocklocation.SERVICE_STATUS";
    public static final String EXTRA_IS_RUNNING = "IS_RUNNING";
    private static final String TAG = "GPSMockLocationService";
    private static final String ACTION_USB_PERMISSION = "com.example.gpsmocklocation.USB_PERMISSION";

    private LocationManager locationManager;
    private UsbManager usbManager;
    private UsbSerialPort port;
    private Thread gpsThread;
    private boolean isRunning = false;

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            connectToDevice(device);
                        }
                    } else {
                        Log.d(TAG, "Permission denied for device " + device);
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate called");
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            isRunning = true;
            registerReceiver(usbPermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED);

            UsbDevice device = intent.getParcelableExtra("usbDevice");
            if (device != null) {
                PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                usbManager.requestPermission(device, permissionIntent);
            } else {
                Log.e(TAG, "No USB device provided");
                stopSelf();
                return START_NOT_STICKY;
            }

            // Broadcast that the service is running
            Intent broadcastIntent = new Intent(ACTION_SERVICE_STATUS);
            broadcastIntent.putExtra(EXTRA_IS_RUNNING, true);
            sendBroadcast(broadcastIntent);
        } else {
            Log.d(TAG, "Service already running");
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (gpsThread != null) {
            gpsThread.interrupt();
        }
        if (port != null) {
            try {
                port.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing USB port", e);
            }
        }
        unregisterReceiver(usbPermissionReceiver);

        // Broadcast that the service is not running
        Intent broadcastIntent = new Intent(ACTION_SERVICE_STATUS);
        broadcastIntent.putExtra(EXTRA_IS_RUNNING, false);
        sendBroadcast(broadcastIntent);

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void connectToDevice(UsbDevice device) {
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Failed to open device");
            return;
        }

        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            Log.e(TAG, "No driver for device");
            return;
        }

        port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            gpsThread = new Thread(new GPSReaderRunnable());
            gpsThread.start();
            Log.d(TAG, "GPS thread started");
        } catch (IOException e) {
            Log.e(TAG, "Error setting up device", e);
        }
    }

    private class GPSReaderRunnable implements Runnable {
        private final Pattern GPRMC_PATTERN = Pattern.compile(
                "\\$GPRMC,(\\d{6}\\.\\d{2}),A,(\\d{4}\\.\\d{5}),(N|S),(\\d{5}\\.\\d{5}),(E|W),.*");

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            StringBuilder lineBuilder = new StringBuilder();

            while (isRunning) {
                try {
                    int len = port.read(buffer, 1000);
                    if (len > 0) {
                        String data = new String(buffer, 0, len);
                        lineBuilder.append(data);

                        int newlineIndex;
                        while ((newlineIndex = lineBuilder.indexOf("\n")) != -1) {
                            String line = lineBuilder.substring(0, newlineIndex).trim();
                            lineBuilder.delete(0, newlineIndex + 1);

                            Matcher matcher = GPRMC_PATTERN.matcher(line);
                            if (matcher.matches()) {
                                double latitude = parseCoordinate(matcher.group(2), matcher.group(3));
                                double longitude = parseCoordinate(matcher.group(4), matcher.group(5));
                                setMockLocation(latitude, longitude);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading GPS data", e);
                    break;
                }
            }
        }

        private double parseCoordinate(String coord, String direction) {
            double degrees = Double.parseDouble(coord.substring(0, 2));
            double minutes = Double.parseDouble(coord.substring(2));
            double decimal = degrees + (minutes / 60.0);
            return (direction.equals("S") || direction.equals("W")) ? -decimal : decimal;
        }

        private void setMockLocation(double latitude, double longitude) {
            Location mockLocation = new Location(LocationManager.GPS_PROVIDER);
            mockLocation.setLatitude(latitude);
            mockLocation.setLongitude(longitude);
            mockLocation.setAltitude(0);
            mockLocation.setTime(System.currentTimeMillis());
            mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            mockLocation.setAccuracy(5);

            try {
                locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation);
            } catch (SecurityException e) {
                Log.e(TAG, "No permission to set mock location", e);
            }
        }
    }
}