package com.trongvq.smswebhub;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.trongvq.smswebhub.data.DataHandler;
import com.trongvq.smswebhub.service.SmsWebService;

import java.util.List;

import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {
    private final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE_PERMISSION_SMS = 0x00FE;
    private static final int REQUEST_CODE_SETTINGS = 0x00FD;
    private final String[] wantedPermissions = {
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.INTERNET
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        BottomNavigationView navView = findViewById(R.id.nav_view);
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupWithNavController(navView, navController);

        // check permission
        if (!EasyPermissions.hasPermissions(this, wantedPermissions)) {
            EasyPermissions.requestPermissions(this, "This app needs some permissions!", REQUEST_CODE_PERMISSION_SMS, wantedPermissions);
        }

        // check activation
        if (!DataHandler.getInstance().isActivated()) {
            DataHandler.getInstance().setTextWebHubStatus(getString(R.string.not_activated));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        Log.d(TAG, "onPermissionsGranted:" + requestCode + ":" + perms.size());
        Toast.makeText(this, "Permissions are granted!\nClose this app and run it again to start background service!", Toast.LENGTH_LONG).show();
        stopService(new Intent(getApplicationContext(), SmsWebService.class));
        finish();
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        Log.d(TAG, "onPermissionsDenied:" + requestCode + ":" + perms.size());
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(this)
                    .setTitle("Permissions Required")
                    .setPositiveButton("Settings")
                    .setNegativeButton("Cancel")
                    .setRequestCode(REQUEST_CODE_SETTINGS)
                    .build()
                    .show();
        }
    }
}
