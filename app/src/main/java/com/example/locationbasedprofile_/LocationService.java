package com.example.locationbasedprofile_;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.SensorEventListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static com.example.locationbasedprofile_.App.CHANNEL_ID;

public class LocationService extends Service  {

    int MAX_PROFILE_NO = 10;
    int activeProfileIndex = MAX_PROFILE_NO, previousProIndexForNotification = MAX_PROFILE_NO;
    double latFromService, lonFromService;
    double latBorderMinus, latBorderPlus, lonBorderMinus, lonBorderPlus;
    boolean firstTime;
    String activeProfileName = "No active profile";
    String previousProNameForNotification = "No active profile";
    FusedLocationProviderClient fusedLocationProviderClient;
    LocationCallback locationCallback;
    Location lastLocation;
    PendingIntent pendingIntent;
    Notification notification;
    Intent intent;

    @Override
    public void onCreate() {
        super.onCreate();
        firstTime = true;
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                lastLocation = locationResult.getLastLocation();

                checkLocationResult();
            }
        };
        startForeground(1, getNotification(activeProfileName));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        requestNewLocationData();
        return START_STICKY;
    }

    private void requestNewLocationData(){

        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(2000); // Checks location every other second

        fusedLocationProviderClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.myLooper()
        );
    }

    private Notification getNotification(String activeProfileName_) {
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);

        notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Active sound profile:")
                .setContentText(activeProfileName_ + "")
                .setSmallIcon(R.drawable.app_icon)
                .setContentIntent(pendingIntent).build();

        previousProIndexForNotification = activeProfileIndex;
        previousProNameForNotification = activeProfileName;

        return notification;
    }

    // Sends notification IF there is a change in the active profile
    private void updateNotification() {
        Notification notification = getNotification(activeProfileName);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(1, notification);
    }

    // If user is at a location where a profile is set to,
    // broadcast is sent to the MainActivity and said profile is set as the active profile
    private void checkLocationResult(){
        if (lastLocation == null) {
            requestNewLocationData();
        } else {
            latFromService = lastLocation.getLatitude();
            lonFromService = lastLocation.getLongitude();

            intent = new Intent("ACT_LOC");
            if (isProfileFound()) {
                intent.putExtra("PROFILE_POSITION_FROM_SERVICE", activeProfileIndex);
            }
            else {
                intent.putExtra("PROFILE_POSITION_FROM_SERVICE", MAX_PROFILE_NO);
                activeProfileName = "No active profile";
            }

            if(!previousProNameForNotification.equals(activeProfileName)) {
                updateNotification();
                sendBroadcast(intent);
            }
        }
    }

    // Checks if the phone is at a location that matches any of the profiles
    public boolean isProfileFound() {
        int lineNumber = 0;
        String FILENAME = "data.txt";
        Context context = getApplicationContext();

        try {
            File file = new File(context.getExternalFilesDir(null).getAbsolutePath(), FILENAME);
            FileInputStream fileInputStream = new FileInputStream(file);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            String line = bufferedReader.readLine();

            while (line != null && lineNumber < MAX_PROFILE_NO) {
                String[] eachLine = line.split(",");

                latBorderMinus = Double.parseDouble(eachLine[1]) - 0.0004;
                latBorderPlus = Double.parseDouble(eachLine[1]) + 0.0004;
                lonBorderMinus = Double.parseDouble(eachLine[2]) - 0.0006;
                lonBorderPlus = Double.parseDouble(eachLine[2]) + 0.0006;

                if ((latBorderMinus < latFromService  && latFromService < latBorderPlus)
                        && (lonBorderMinus < lonFromService && lonFromService < lonBorderPlus) ){
                    activeProfileIndex = lineNumber;
                    activeProfileName = eachLine[0];
                    return true;
                }
                lineNumber++;
                line = bufferedReader.readLine();
            }
        } catch (FileNotFoundException e){
            e.printStackTrace();
        } catch (IOException e){
            e.printStackTrace();
        } finally {
        }
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        //throw new UnsupportedOperationException("Not yet implemented");
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
    }
}
