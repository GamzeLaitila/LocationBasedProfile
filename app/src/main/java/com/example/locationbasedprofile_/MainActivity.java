package com.example.locationbasedprofile_;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener {

    Button btn_addNew;
    ListView profileList;
    ImageView settingsIcon;
    AudioManager audioManager;
    LocationBroadcastReceiver receiver;
    View mView;

    private final int REQUEST_PERMISSION_PHONE_STATE = 1;
    int MAX_PROFILE_NO = 10;
    int currentNoOfProfiles = 0;
    int profilePosition;
    double[] profileLatitudes = new double[MAX_PROFILE_NO];
    double[] profileLongitudes = new double[MAX_PROFILE_NO];
    String toBeAddedProfileData;
    String toastMessage, activeProfileName;
    String[] profileNames = new String[MAX_PROFILE_NO];
    String[] modifiedProfileData = new String[4];
    String[][] allLines = new String[MAX_PROFILE_NO][4];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        receiver = new LocationBroadcastReceiver();

        showStartDialog(); // AlertDialog for initial app information

        String[] PERMISSIONS = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_NOTIFICATION_POLICY
        };
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSION_PHONE_STATE);

        profileList = findViewById(R.id.profileList);
        audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        // Reads profile details from file and puts them in the ListView component
        // In this case, readFile is called with the purpose "initiate"
        readFile("initiate", 0);

        btn_addNew = findViewById(R.id.addNew);

        // Checks if MAX_PROFILE_NO is reached
        // if not, starts NewProfileActivity where profile details are asked from the user
        btn_addNew.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View v) {

                if(currentNoOfProfiles == MAX_PROFILE_NO){
                    Toast.makeText(MainActivity.this, "Max. number of profiles is " + MAX_PROFILE_NO, Toast.LENGTH_SHORT).show();
                }
                else{
                    addNewProfile();
                }
            }
        });

        settingsIcon = profileList.findViewById(R.id.listView_images);

        profileList.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                profilePosition = position;
                showPopup(view);
            }
        });
    }

    // Initial information about the application
    // Comes up every time app is launched unless "Don't show again" is checked
    private void showStartDialog()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        mView = getLayoutInflater().inflate(R.layout.dialog_app_info, null);
        CheckBox checkBox = mView.findViewById(R.id.checkBox);
        builder.setTitle("App-information")
                .setMessage("\nLocation Based Profile allows you to set specific sound levels automatically, " +
                        "depending on your current location.\n" +
                        "\nStart creating profiles by pressing Add Profile Button")
                .setView(mView)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(compoundButton.isChecked()){
                    storeDialogStatus(true);
                }else{
                    storeDialogStatus(false);
                }
            }
        });

        if(getDialogStatus()){
            dialog.hide();
        }else{
            dialog.show();
        }
    }

    // Sets conditions for AlertDialog (App-information)
    private void storeDialogStatus(boolean isChecked){
        SharedPreferences preferences = getSharedPreferences("CheckItem", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("item", isChecked);
        editor.apply();
    }

    // Sets conditions for AlertDialog (App-information)
    private boolean getDialogStatus(){
        SharedPreferences mSharedPreferences = getSharedPreferences("CheckItem", MODE_PRIVATE);
        return mSharedPreferences.getBoolean("item", false);
    }

    // Popup menu for profile modifications (Activate, Modify, Delete)
    public void showPopup (View view){
        PopupMenu popupMenu = new PopupMenu(this, view, Gravity.RIGHT);
        popupMenu.setOnMenuItemClickListener(this);
        MenuInflater inflater = popupMenu.getMenuInflater();
        inflater.inflate(R.menu.profile_menu, popupMenu.getMenu());
        popupMenu.show();
    }

    // Profile modification (Activate, Modify, Delete) actions
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch(item.getItemId()){
            case R.id.activate_item:
                activateProfile(profilePosition);
                //Toast.makeText(this, "Activate selected", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.modify_item:
                modifyProfile(profilePosition);
                //Toast.makeText(this, "Modify selected", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.delete_item:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Are you sure you want to delete the profile?").setCancelable(false)
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                deleteProfile(profilePosition);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                return true;
            default:
                return false;
        }
    }

    // Starts intent to NewProfileActivity, waits for results which is new profile's specifications
    // Current profile details are sent in the bundle so that necessary queries can be done
    // in order to make sure that a new profile with the same name or location can not be added
    public void addNewProfile(){
        Intent openProfileIntent = new Intent(getApplicationContext(), NewProfileActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString("OPERATION", "Add");
        bundle.putStringArray("PROFILE_NAMES", profileNames);
        bundle.putDoubleArray("PROFILE_LATITUDES", profileLatitudes);
        bundle.putDoubleArray("PROFILE_LONGITUDES", profileLongitudes);
        bundle.putInt("NO_OF_PROFILES", currentNoOfProfiles);
        openProfileIntent.putExtras(bundle);
        openProfileIntent.setAction(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        startActivityForResult(openProfileIntent, 1);
    }

    // When a profile's "Activate" option is chosen
    // OR
    // any registered profile meets the user's current location,
    // said profile is activated aka sound level is set accordingly
    private void activateProfile(int profilePosition) {
        audioManager.setStreamVolume(AudioManager.STREAM_RING, Integer.parseInt(allLines[profilePosition][3]), 0);
        activeProfileName = allLines[profilePosition][0];
    }

    // When a profile's "Modify" option is chosen,
    // this method starts intent to NewProfileActivity, waits for results which is the modified profile's specifications.
    // Chosen profile's details are sent in the bundle so that NewProfileActivity can put them to their places
    // as a reference to the user
    private void modifyProfile(int profileIndex) {
        Intent openProfileIntent = new Intent(getApplicationContext(), NewProfileActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString("OPERATION", "Modify");
        bundle.putInt("PROFILE_INDEX", profileIndex);
        bundle.putString("PROFILE_NAME", allLines[profileIndex][0]);
        bundle.putDouble("LATITUDE", Double.parseDouble(allLines[profileIndex][1]));
        bundle.putDouble("LONGITUDE", Double.parseDouble(allLines[profileIndex][2]));
        bundle.putInt("SOUND_LEVEL", Integer.parseInt(allLines[profileIndex][3]));
        openProfileIntent.putExtras(bundle);
        startActivityForResult(openProfileIntent, 2);
    }

    // When a profile's "Delete" option is chosen,
    // said profile's index is sent to be handled and removed from the file.
    // Contents of the file (current profiles) is then put back on the screen by printProfiles() function
    // via readFile() function
    private void deleteProfile(int profileIndex) {
        readFile("delete", profileIndex);
        writeFile("delete");
        readFile("initiate", 0);
    }

    // Receives returned data from intents.
    // If intent was started with "adding new profile" purpose, data is received via requestCode:1
    // If intent was started with "modifying a profile" purpose, data is received via requestCode:2
    // Final state of the profiles is put again on the screen by printProfiles() function
    // via readFile() function
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // result after adding profile
        if(requestCode == 1){
            if(resultCode == RESULT_OK){
                toBeAddedProfileData = data.getStringExtra("TO_BE_ADDED_PROFILE_DATA");
                writeFile("add");
                readFile("add", 0);
            }
        }
        // result after modifying profile
        if(requestCode == 2){
            if(resultCode == RESULT_OK){
                modifiedProfileData = data.getStringArrayExtra("MODIFIED_PROFILE_DATA");
                writeFile("modify");
                readFile("modify", profilePosition);
            }
        }
    }

    // Receives "data" from Service, "data" being an index of a profile.
    // If index is below MAX_PROFILE_NO, it activates the profile which the index refers to
    public class LocationBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, Intent intent) {
            if (intent.getAction().equals("ACT_LOC")) {
                profilePosition = intent.getExtras().getInt("PROFILE_POSITION_FROM_SERVICE", MAX_PROFILE_NO);

                if (profilePosition < MAX_PROFILE_NO) {
                    activateProfile(profilePosition);
                }
            }
        }
    }

    // Checks if background service is on
    public boolean isServiceRunning(Context c, Class<?> serviceClass) {
        ActivityManager activityManager = (ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

        for(ActivityManager.RunningServiceInfo runningServiceInfo : services){
            if (runningServiceInfo.service.getClassName().equals(serviceClass.getName())){
                return true;
            }
        }
        return false;
    }

    // Depending on the parameters it gets, this method reads profile details from the file
    // and sends them to the method printProfiles() to be written on the screen into ListView.
    // String -operation- can be "initiate", "add", "modify" or "delete".
    private void readFile(String operation, int index){

        int lineNumber = 0, countLinesUntil = 0;
        String FILENAME = "data.txt";
        String[] eachLine = new String[4];
        Context context = getApplicationContext();

        try {
            File file = new File(context.getExternalFilesDir(null).getAbsolutePath(), FILENAME);
            FileInputStream fileInputStream = new FileInputStream(file);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            if(operation == "initiate")
                countLinesUntil = MAX_PROFILE_NO;
            if(operation == "add")
                countLinesUntil = MAX_PROFILE_NO;
            if(operation == "modify")
                countLinesUntil = MAX_PROFILE_NO;
            if(operation == "delete")
                countLinesUntil = currentNoOfProfiles - 1;

            String line = bufferedReader.readLine();
            while (line != null && lineNumber < countLinesUntil) {

                if(operation == "delete" && lineNumber == index){
                    line = bufferedReader.readLine();
                }

                eachLine = line.split(",");

                for (int i = 0; i < 4; i++) {
                    allLines[lineNumber][i] = eachLine[i];
                }
                // skip the profile_to_be_deleted
                lineNumber++;
                line = bufferedReader.readLine();
            }
            if (operation != "delete")
                printProfiles(allLines, lineNumber);
            else
                currentNoOfProfiles = lineNumber;

            // start background service when at least 1 profile exists
            if (!isServiceRunning(getApplicationContext(), LocationService.class) && currentNoOfProfiles > 0) {
                IntentFilter filter = new IntentFilter("ACT_LOC");
                registerReceiver(receiver, filter);
                Intent serviceIntent = new Intent(this ,LocationService.class);
                startService(serviceIntent);
            }
            else if (currentNoOfProfiles == 0){
                stopService(new Intent(this, LocationService.class));
            }

        } catch (FileNotFoundException e){
            e.printStackTrace();
        } catch (IOException e){
            e.printStackTrace();
        } finally {
        }
    }

    // Writes to file current profile's states depending on the parameters it gets.
    // This function is used to either add, modify or delete a profile
    private void writeFile(String operation) {
        String FILENAME = "data.txt";
        Context context = getApplicationContext();
        BufferedWriter bufferedWriter = null;
        String line = "";

        switch (operation){
            case "add": toastMessage = "added";
                break;
            case "modify": toastMessage = "modified";
                break;
            case "delete": toastMessage = "deleted";
                break;
        }

        try {
            File file = new File(context.getExternalFilesDir(null).getAbsolutePath(), FILENAME);

            if(operation == "delete" || operation == "modify"){
                FileWriter fileWriter_clear = new FileWriter(file, false);
                bufferedWriter = new BufferedWriter(fileWriter_clear);
                bufferedWriter.write("");

                FileWriter fileWriter_writeOver = new FileWriter(file, true);
                bufferedWriter = new BufferedWriter(fileWriter_writeOver);
            }

            if(operation == "add"){
                FileWriter fileWriter = new FileWriter(file, true);
                bufferedWriter = new BufferedWriter(fileWriter);
            }

            if(operation == "delete" || operation == "modify"){
                if (operation == "modify") {
                    for (int i = 0; i < 4; i++) {
                        allLines[profilePosition][i] = modifiedProfileData[i];
                    }
                }
                for (int i = 0; i < currentNoOfProfiles; i++) {
                    line = allLines[i][0] + "," + allLines[i][1] + "," + allLines[i][2] + "," + allLines[i][3];
                    bufferedWriter.write(line);
                    bufferedWriter.newLine();
                    bufferedWriter.flush();
                }
            }
            if(operation == "add"){
                line = toBeAddedProfileData;
                bufferedWriter.write(line);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            }

            Toast.makeText(this, "Profile " + toastMessage, Toast.LENGTH_SHORT).show();

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

    // Actually prints each profile's details that it reads from file
    // Sets these profile details into the ListView
    private void printProfiles(String[][] allLines, int noOfProfiles) {

        currentNoOfProfiles = noOfProfiles;
        List<HashMap<String, String>> list = new ArrayList<>();

        String[] receivedProfileNames = new String[MAX_PROFILE_NO];
        String[] receivedSoundLevels = new String[MAX_PROFILE_NO];

        for (int i = 0; i < noOfProfiles; i++) {
            receivedProfileNames[i] = allLines[i][0];
            receivedSoundLevels[i] = allLines[i][3];

            profileNames[i] = receivedProfileNames[i];
            profileLatitudes[i] = Double.parseDouble(allLines[i][1]);
            profileLongitudes[i] = Double.parseDouble(allLines[i][2]);
        }
        for (int i = 0; i < noOfProfiles; i++) {
            HashMap<String, String > hm = new HashMap<>();
            hm.put("ProfileName", receivedProfileNames[i]);
            //hm.put("SoundLevelText", "Sound level: ");
            if(Integer.parseInt(allLines[i][3]) == 0)
                hm.put("SoundLevelIcon", Integer.toString(R.drawable.sound_level_0));
            if(Integer.parseInt(allLines[i][3]) == 1)
                hm.put("SoundLevelIcon", Integer.toString(R.drawable.sound_level_1));
            if(Integer.parseInt(allLines[i][3]) == 2)
                hm.put("SoundLevelIcon", Integer.toString(R.drawable.sound_level_2));
            if(Integer.parseInt(allLines[i][3]) == 3)
                hm.put("SoundLevelIcon", Integer.toString(R.drawable.sound_level_3));
            if(Integer.parseInt(allLines[i][3]) == 4)
                hm.put("SoundLevelIcon", Integer.toString(R.drawable.sound_level_4));
            if(Integer.parseInt(allLines[i][3]) == 5)
                hm.put("SoundLevelIcon", Integer.toString(R.drawable.sound_level_5));
            if(Integer.parseInt(allLines[i][3]) == 6)
                hm.put("SoundLevelIcon", Integer.toString(R.drawable.sound_level_6));
            if(Integer.parseInt(allLines[i][3]) == 7)
                hm.put("SoundLevelIcon", Integer.toString(R.drawable.sound_level_7));
            hm.put("SettingsIcon", Integer.toString(R.drawable.settings_icon_2));
            list.add(hm);
        }

        String[] from = {"ProfileName", "SoundLevelIcon", "SettingsIcon"};
        int[] to = {R.id.profileName, R.id.soundLevel_images, R.id.listView_images};

        SimpleAdapter simpleAdapter = new SimpleAdapter(getBaseContext(), list, R.layout.listview_items, from, to);
        profileList.setAdapter(simpleAdapter);
    }
}
