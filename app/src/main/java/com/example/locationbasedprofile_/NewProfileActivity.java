package com.example.locationbasedprofile_;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

@SuppressLint("Registered")
public class NewProfileActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnCameraIdleListener {

    GoogleMap map;
    CameraPosition currentLocation;
    FusedLocationProviderClient mFusedLocationClient;
    Button manageProfileBtn;
    ImageButton myLocationBtn;
    SeekBar soundBar;
    EditText profileName;
    TextView sound, pageTitle;
    LatLng mapPosition;

    int MAX_PROFILE_NO = 10;
    int PERMISSION_ID = 44;
    int maxSoundLevel = 7, currentSoundLevel = 3;
    int receivedNoOfProfiles, receivedProfileIndex, receivedSoundLevel;
    double receivedLatitude, receivedLongitude;
    double[] receivedLatitudes = new double[MAX_PROFILE_NO];
    double[] receivedLongitudes = new double[MAX_PROFILE_NO];
    String operation, sentProfileName, profileData, receivedProfileName, line;
    String[] receivedProfileNames = new String[MAX_PROFILE_NO];
    String[] modifiedProfileData = new String[4];

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_profile);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        pageTitle = findViewById(R.id.newProfilePage);
        myLocationBtn = findViewById(R.id.myLocationButton);
        manageProfileBtn = findViewById(R.id.manageProfile);
        profileName = findViewById(R.id.profileName_EditText);

        Bundle bundle = getIntent().getExtras();

        if(bundle != null) {

            operation = bundle.getString("OPERATION");

            // from modify profile
            pageTitle.setText(operation + " Profile");
            manageProfileBtn.setText(operation);

            receivedProfileIndex = bundle.getInt("PROFILE_INDEX");
            receivedProfileName = bundle.getString("PROFILE_NAME");
            profileName.setText(receivedProfileName);
            //----LATITUDE and LONGITUDE are received in onMapReady method
            //receivedLatitude = bundle.getDouble("LATITUDE");
            //receivedLongitude = bundle.getDouble("LONGITUDE");
            receivedSoundLevel = bundle.getInt("SOUND_LEVEL", 3);
            currentSoundLevel = receivedSoundLevel;

            // from add new profile
            receivedProfileNames = bundle.getStringArray("PROFILE_NAMES");
            receivedLatitudes = bundle.getDoubleArray("PROFILE_LATITUDES");
            receivedLongitudes = bundle.getDoubleArray("PROFILE_LONGITUDES");
            receivedNoOfProfiles = bundle.getInt("NO_OF_PROFILES");
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
        }

        SupportMapFragment mapFragment;
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        soundBar = findViewById(R.id.soundBar);
        soundBar.setMax(maxSoundLevel);
        soundBar.setProgress(currentSoundLevel);
        sound = findViewById(R.id.soundLeveltextView);
        sound.setText("Sound level: " + currentSoundLevel);

        soundBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSoundLevel = progress;
                sound.setText("Sound level: " + currentSoundLevel);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        myLocationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getLastLocation("MyLocation", 0, 0);
            }
        });

        manageProfileBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                int isSameProfileName = 0;
                int isSameLocation = 0;

                //replace("\"", "")
                String profileNameWithoutComma = profileName.getText().toString().replace(",", "");

                if(operation.equals("Add")) {
                    if (receivedNoOfProfiles != 0) {
                        // check if profile already exists with the provided name
                        // OR
                        // the ~chosen location
                        for (int i = 0; i < receivedNoOfProfiles; i++) {
                            if (receivedProfileNames[i].equals(profileNameWithoutComma)) {
                                isSameProfileName = 1;
                                break;
                            }
                            if ((receivedLatitudes[i] < currentLocation.target.latitude + 0.0003
                                && receivedLatitudes[i] > currentLocation.target.latitude - 0.0003)
                                && (receivedLongitudes[i] < currentLocation.target.longitude + 0.00045
                                && receivedLongitudes[i] > currentLocation.target.longitude - 0.00045)) {
                                    isSameLocation = 1;
                            }
                        }
                        if (isSameLocation == 0 && isSameProfileName == 0 && !profileNameWithoutComma.equals("")) {
                            addProfile();
                        } else {
                            if(isSameLocation != 0)
                                Toast.makeText(NewProfileActivity.this, "A profile with approximately same location already exists", Toast.LENGTH_LONG).show();
                            if(profileNameWithoutComma.equals(""))
                                Toast.makeText(NewProfileActivity.this, "Enter a profile name", Toast.LENGTH_LONG).show();
                            if(isSameProfileName != 0)
                                Toast.makeText(NewProfileActivity.this, "A profile with the same name already exists", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        addProfile();
                    }
                }
                else {
                    modifyProfile();
                }
            }
        });
    }

    public void modifyProfile() {
        profileName = findViewById(R.id.profileName_EditText);
        sentProfileName = profileName.getText().toString().replace(",", ""); //replace("\"", "")
        String sentLatitude = String.format ("%.9f", currentLocation.target.latitude);
        String sentLongitude = String.format ("%.9f", currentLocation.target.longitude);

        modifiedProfileData[0] = sentProfileName;
        modifiedProfileData[1] = sentLatitude;
        modifiedProfileData[2] = sentLongitude;
        modifiedProfileData[3] = String.valueOf(soundBar.getProgress());

        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.putExtra("MODIFIED_PROFILE_DATA", modifiedProfileData);
        setResult(RESULT_OK, mainIntent);
        finish();
    }

    public void addProfile() {
        writeFile();
        Intent mainIntent = new Intent(this, MainActivity.class);
        setResult(RESULT_OK, mainIntent);
        finish();
    }

    private void writeFile() {
        profileName = findViewById(R.id.profileName_EditText);
        sentProfileName = profileName.getText().toString().replace(",", ""); //replace("\"", "")
        String sentLatitude = String.format ("%.9f", currentLocation.target.latitude);
        String sentLongitude = String.format ("%.9f", currentLocation.target.longitude);

        profileData = sentProfileName + "," + sentLatitude + "," + sentLongitude + "," + soundBar.getProgress();

        String FILENAME = "data.txt";
        Context context = getApplicationContext();
        BufferedWriter bufferedWriter = null;

        try {
            File file = new File(context.getExternalFilesDir(null).getAbsolutePath(), FILENAME);
            FileWriter fileWriter = new FileWriter(file, true);
            bufferedWriter = new BufferedWriter(fileWriter);

            bufferedWriter.write(profileData);
            bufferedWriter.newLine();
            bufferedWriter.flush();

            Toast.makeText(this, "Profile added", Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e){
            e.printStackTrace();
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
        } catch (IOException e){
            e.printStackTrace();
            Toast.makeText(this, "Error saving", Toast.LENGTH_SHORT).show();
        } finally {
            if (bufferedWriter != null) try {
                bufferedWriter.close();
            } catch (IOException ioe2) {
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        Bundle bundle = getIntent().getExtras();

        operation = bundle.getString("OPERATION");
        receivedLatitude = bundle.getDouble("LATITUDE", 0);
        receivedLongitude = bundle.getDouble("LONGITUDE", 0);

        map.setOnCameraIdleListener(this);

        getLastLocation(operation, receivedLatitude, receivedLongitude);
    }

    @SuppressLint("MissingPermission")
    private void getLastLocation(final String operationMap, final double receivedLatitude, final double receivedLongitude) {
        if (checkPermissions()) {
            if (isLocationEnabled()) {
                mFusedLocationClient.getLastLocation()
                        .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location lastLocation) {
                                if (lastLocation == null) {
                                    requestNewLocationData();
                                } else {
                                    //map.clear(); // clear old location marker in google map

                                    if (operationMap.equals("Modify")) {
                                        LatLng userLatLng_new = new LatLng(receivedLatitude, receivedLongitude);
                                        moveCamera(userLatLng_new);
                                    }
                                    if (operationMap.equals("Add") || operationMap.equals("MyLocation")) {
                                        LatLng userLatLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                                        moveCamera(userLatLng);
                                    }
                                }
                            }
                        });
            } else {
                Toast.makeText(this, "Turn on location", Toast.LENGTH_LONG).show();
            }
        } else {
            requestPermissions();
        }
        // getting user last location to set the default location marker on the map
        //lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    public void moveCamera(LatLng userLatLng){
        mapPosition = userLatLng;
        if(operation.equals("MyLocation"))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(mapPosition, 11)); //current zoom level, if user has already changed it?
        else
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(mapPosition, 11));
        //map.addMarker(new MarkerOptions().position(mapPosition).title(""));
    }

    @SuppressLint("MissingPermission")
    private void requestNewLocationData(){

        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(0);
        mLocationRequest.setFastestInterval(0);
        mLocationRequest.setNumUpdates(1);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mFusedLocationClient.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                Looper.myLooper()
        );

    }

    private LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            Location mLastLocation = locationResult.getLastLocation();
            mapPosition = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            // latTextView.setText(mLastLocation.getLatitude()+"");
            // lonTextView.setText(mLastLocation.getLongitude()+"");
        }
    };

    private boolean checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }


    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER);
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSION_ID
        );
    }

    @Override
    public void onCameraMoveStarted(int i) {

    }

    @Override
    public void onCameraIdle() {
        currentLocation = map.getCameraPosition();

        TextView locationText = findViewById(R.id.locationTextView);
        double latitude = currentLocation.target.latitude;
        double longitude = currentLocation.target.longitude;
        mapPosition = new LatLng(latitude, longitude);
        map.clear();
        map.addMarker(new MarkerOptions().position(mapPosition).title(""));

        //locationText.setText("Location: " + latitude + ", " + longitude);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_ID) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation(operation, receivedLatitude, receivedLongitude);
            }
        }
    }

    @Override
    public void onResume(){
        super.onResume();
        if (checkPermissions()) {
            getLastLocation(operation, receivedLatitude, receivedLongitude);
        }

    }
}
