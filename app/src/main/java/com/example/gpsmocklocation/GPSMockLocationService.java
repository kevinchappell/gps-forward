package com.example.gpsmocklocation;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GPSMockLocationService extends Service {
    public static final String ACTION_SERVICE_STATUS = "com.example.gpsmocklocation.SERVICE_STATUS";
    public static final String EXTRA_IS_RUNNING = "IS_RUNNING";
    private static final String TAG = "GPSMockLocationService";
    private static final String GPS_DEVICE = "/dev/ttyACM1";
    private LocationManager locationManager;
    private Thread gpsThread;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("GPSMockLocationService", "onCreate called");
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            isRunning = true;
            gpsThread = new Thread(new GPSReaderRunnable());
            gpsThread.start();

            Log.d("GPSMockLocationService", "GPS thread started");


            // Broadcast that the service is running
            Intent broadcastIntent = new Intent(ACTION_SERVICE_STATUS);
            broadcastIntent.putExtra(EXTRA_IS_RUNNING, true);
            sendBroadcast(broadcastIntent);
        } else {
            Log.d("GPSMockLocationService", "Service already running");
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (gpsThread != null) {
            gpsThread.interrupt();
        }

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

    private class GPSReaderRunnable implements Runnable {
        private final Pattern GPRMC_PATTERN = Pattern.compile(
            "\\$GPRMC,(\\d{6}\\.\\d{2}),A,(\\d{4}\\.\\d{5}),(N|S),(\\d{5}\\.\\d{5}),(E|W),.*");

        @Override
        public void run() {
            Log.d("GPSMockLocationService", "onStartCommand called");
            try {
                Process process = Runtime.getRuntime().exec("su");
                OutputStream os = process.getOutputStream();
                os.write(("cat " + GPS_DEVICE + "\n").getBytes());
                os.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while (isRunning && (line = reader.readLine()) != null) {
                    Matcher matcher = GPRMC_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        double latitude = parseCoordinate(matcher.group(2), matcher.group(3));
                        double longitude = parseCoordinate(matcher.group(4), matcher.group(5));
                        setMockLocation(latitude, longitude);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading GPS data", e);
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
