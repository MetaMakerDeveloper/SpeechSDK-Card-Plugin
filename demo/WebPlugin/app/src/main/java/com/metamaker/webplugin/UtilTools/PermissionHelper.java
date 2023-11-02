package com.metamaker.webplugin.UtilTools;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionHelper {
    public static final int REQUEST_CODE = 5;

    // 定义所需权限
    private static final String[] permission = new String[]{
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.READ_CONTACTS
    };

    // 判断指定权限是否全部授权
    public static boolean isPermissionGranted(Activity activity) {
        if (Build.VERSION.SDK_INT >= 23) {
            for (int i = 0; i < permission.length; i++) {
                int checkPermission = ContextCompat.checkSelfPermission(activity, permission[i]);
                if (checkPermission != PackageManager.PERMISSION_GRANTED)
                    return false;
            }
            return true;
        }

        return true;
    }

    public static boolean checkPermission(Activity activity) {
        if (isPermissionGranted(activity)) {
            return true;
        } else {
            ActivityCompat.requestPermissions(activity, permission, REQUEST_CODE);
            return false;
        }
    }
}
