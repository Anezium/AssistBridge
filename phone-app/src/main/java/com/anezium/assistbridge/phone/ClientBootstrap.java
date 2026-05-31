package com.anezium.assistbridge.phone;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import com.example.cxrglobal.CXRLink;
import com.example.cxrglobal.callbacks.IGlassAppCbk;
import com.anezium.assistbridge.protocol.AssistBridgeProtocol;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class ClientBootstrap {
    private static final String TAG = "AssistBridgeBootstrap";

    private final Context context;
    private final CXRLink link;

    ClientBootstrap(Context context, CXRLink link) {
        this.context = context.getApplicationContext();
        this.link = link;
    }

    String ensureRunning() {
        boolean installed = queryInstalled();
        File apk = extractAssetApk();
        ClientAssetInfo assetInfo = apk == null ? null : clientAssetInfo(apk);
        boolean shouldInstall = !installed || bundledClientChanged(assetInfo);
        boolean installedFromBundle = false;

        if (shouldInstall) {
            if (apk == null) {
                return "glasses asset missing";
            }
            Log.i(TAG, "installing bundled glasses app " + (assetInfo == null ? apk.getName() : assetInfo.label()));
            if (!installApk(apk)) {
                return "glasses install failed";
            }
            if (assetInfo != null) {
                rememberInstalledClient(assetInfo);
            }
            installedFromBundle = true;
        }

        if (startClient()) {
            return installedFromBundle ? "glasses app installed/updated" : "glasses app running";
        }
        return "glasses start failed";
    }

    private boolean queryInstalled() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(false);
        link.appIsInstalled(new NoopGlassAppCbk() {
            @Override
            public void onQueryAppResult(boolean installed) {
                result.set(installed);
                latch.countDown();
            }
        });
        return await(latch, 5000L, "query") && result.get();
    }

    private boolean installApk(File apk) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(false);
        link.appUploadAndInstall(apk.getAbsolutePath(), new NoopGlassAppCbk() {
            @Override
            public void onInstallAppResult(boolean success) {
                result.set(success);
                latch.countDown();
            }
        });
        return await(latch, 90000L, "install") && result.get();
    }

    private boolean startClient() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(false);
        link.appStart(AssistBridgeProtocol.CLIENT_MAIN_ACTIVITY, new NoopGlassAppCbk() {
            @Override
            public void onOpenAppResult(boolean success) {
                result.set(success);
                latch.countDown();
            }
        });
        return await(latch, 8000L, "start") && result.get();
    }

    private File extractAssetApk() {
        File dest = new File(context.getFilesDir(), AssistBridgeProtocol.CLIENT_ASSET_NAME);
        try (InputStream input = context.getAssets().open(AssistBridgeProtocol.CLIENT_ASSET_NAME);
             FileOutputStream output = new FileOutputStream(dest)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            return dest;
        } catch (IOException exception) {
            Log.w(TAG, "asset extraction failed: " + exception.getMessage());
            return null;
        }
    }

    private ClientAssetInfo clientAssetInfo(File apk) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (packageInfo == null) {
                return null;
            }
            String versionName = packageInfo.versionName == null ? "0.0.0" : packageInfo.versionName;
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? packageInfo.getLongVersionCode()
                    : packageInfo.versionCode;
            return new ClientAssetInfo(versionName, versionCode, sha256(apk));
        } catch (RuntimeException exception) {
            Log.w(TAG, "asset package read failed: " + exception.getMessage());
            return null;
        }
    }

    private boolean bundledClientChanged(ClientAssetInfo assetInfo) {
        if (assetInfo == null) {
            return false;
        }
        String last = context.getSharedPreferences(PhonePrefs.PREFS, Context.MODE_PRIVATE)
                .getString(PhonePrefs.CLIENT_APK_FINGERPRINT, null);
        return !assetInfo.fingerprint().equals(last);
    }

    private void rememberInstalledClient(ClientAssetInfo assetInfo) {
        context.getSharedPreferences(PhonePrefs.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PhonePrefs.CLIENT_APK_FINGERPRINT, assetInfo.fingerprint())
                .apply();
    }

    private String sha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.toURI().toURL().openStream()) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            Log.w(TAG, "sha256 failed: " + exception.getMessage());
            return "";
        }
    }

    private boolean await(CountDownLatch latch, long timeoutMs, String label) {
        try {
            boolean ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!ok) {
                Log.w(TAG, label + " timed out");
            }
            return ok;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static class NoopGlassAppCbk implements IGlassAppCbk {
        @Override
        public void onInstallAppResult(boolean success) {
        }

        @Override
        public void onUnInstallAppResult(boolean success) {
        }

        @Override
        public void onOpenAppResult(boolean success) {
        }

        @Override
        public void onStopAppResult(boolean success) {
        }

        @Override
        public void onGlassAppResume(boolean resume) {
        }

        @Override
        public void onQueryAppResult(boolean installed) {
        }
    }

    private static final class ClientAssetInfo {
        final String versionName;
        final long versionCode;
        final String sha256;

        ClientAssetInfo(String versionName, long versionCode, String sha256) {
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.sha256 = sha256 == null ? "" : sha256;
        }

        String fingerprint() {
            return versionCode + ":" + versionName + ":" + sha256;
        }

        String label() {
            return versionName + " (" + versionCode + ")";
        }
    }
}
