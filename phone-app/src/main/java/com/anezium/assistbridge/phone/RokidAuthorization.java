package com.anezium.assistbridge.phone;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

final class RokidAuthorization {
    static final int REQUEST_CODE = 8301;

    private static final String TAG = "AssistBridgeAuth";
    private static final String GLOBAL_PACKAGE = "com.rokid.sprite.global.aiapp";
    private static final String AUTH_ACTIVITY = "com.rokid.sprite.aiapp.externalapp.auth.AuthorizationActivity";
    private static final String AUTH_ACTION = "com.rokid.sprite.aiapp.externalapp.AUTHORIZATION";
    private static final String EXTRA_AUTH_RESULT = "auth_result";
    private static final String EXTRA_AUTH_TOKEN = "auth_token";
    private static final int AUTH_RESULT_SUCCESS = 2001;

    private RokidAuthorization() {
    }

    static boolean isHiRokidInstalled(Activity activity) {
        try {
            activity.getPackageManager().getPackageInfo(GLOBAL_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    static boolean request(Activity activity) {
        if (!isHiRokidInstalled(activity)) {
            return false;
        }

        Intent explicitIntent = new Intent().setComponent(new ComponentName(GLOBAL_PACKAGE, AUTH_ACTIVITY));
        try {
            activity.startActivityForResult(explicitIntent, REQUEST_CODE);
            return true;
        } catch (RuntimeException explicitFailure) {
            Log.w(TAG, "explicit auth failed, trying action fallback", explicitFailure);
        }

        try {
            Intent fallback = new Intent(AUTH_ACTION).setPackage(GLOBAL_PACKAGE);
            activity.startActivityForResult(fallback, REQUEST_CODE);
            return true;
        } catch (RuntimeException fallbackFailure) {
            Log.w(TAG, "auth fallback failed", fallbackFailure);
            return false;
        }
    }

    static String parseToken(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return "";
        }

        int result = data.getIntExtra(EXTRA_AUTH_RESULT, -1);
        if (result != AUTH_RESULT_SUCCESS) {
            return "";
        }
        String token = data.getStringExtra(EXTRA_AUTH_TOKEN);
        return token == null ? "" : token;
    }
}
