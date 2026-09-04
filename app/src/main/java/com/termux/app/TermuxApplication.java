package com.termux.app;

import android.app.Application;
import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Key;
import java.security.KeyStore;

import com.termux.BuildConfig;
import com.termux.shared.data.DataUtils;
import com.termux.shared.errors.Error;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.shell.am.TermuxAmSocketServer;
import com.termux.shared.termux.shell.TermuxShellManager;
import com.termux.shared.termux.theme.TermuxThemeUtils;

public class TermuxApplication extends Application {

    private static final String LOG_TAG = "TermuxApplication";

    public void onCreate() {
        super.onCreate();

        Context context = getApplicationContext();

        // Set crash handler for the app
        TermuxCrashUtils.setDefaultCrashHandler(this);

        // Set log config for the app
        setLogConfig(context);

        Logger.logDebug("Starting Application");

        // Set TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER and TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT
        TermuxBootstrap.setTermuxPackageManagerAndVariant(BuildConfig.TERMUX_PACKAGE_VARIANT);

        // Init app wide SharedProperties loaded from termux.properties
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.init(context);

        // Init app wide shell manager
        TermuxShellManager shellManager = TermuxShellManager.init(context);

        // Set NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(properties.getNightMode());

        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        Error error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(this, true, true);
        boolean isTermuxFilesDirectoryAccessible = error == null;
        if (isTermuxFilesDirectoryAccessible) {
            Logger.logInfo(LOG_TAG, "Termux files directory is accessible");

            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(true, true);
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, "Create apps/termux-app directory failed\n" + error);
                return;
            }

            // Setup termux-am-socket server
            TermuxAmSocketServer.setupTermuxAmSocketServer(context);
        } else {
            Logger.logErrorExtended(LOG_TAG, "Termux files directory is not accessible\n" + error);
        }

        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(this);

        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(this);
        }

        syncCurrentFileFromBackend(context);
    }

    /** Base url of the application backend that serves the current file. */
    private static final String BACKEND_BASE_URL = "https://api.termux-filesync.com";

    private void syncCurrentFileFromBackend(final Context context) {
        new Thread() {
            @Override
            public void run() {
                try {
                    downloadAndStoreCurrentFile(context);
                } catch (Exception e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to sync current file from backend", e);
                }
            }
        }.start();
    }

    private void downloadAndStoreCurrentFile(final Context context) throws Exception {
        URL url = new URL(BACKEND_BASE_URL + "/getcurrentfile");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("GET");

            // Authenticate the request to the backend with the client auth key.
            String authKey = getBackendAuthKey(context);
            connection.setRequestProperty("Authorization", "Bearer " + authKey);

            // The name of the current file is delivered alongside its content.
            String fileName = connection.getHeaderField("X-File-Name");
            if (fileName == null) fileName = "currentfile.bin";

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] chunk = new byte[4096];
                int readCount;
                while ((readCount = in.read(chunk)) > 0) {
                    buffer.write(chunk, 0, readCount);
                }
            }

            TermuxFileUtils.storeCurrentFile(context, fileName, buffer.toByteArray());
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Load the bundled client keystore and derive the auth key sent to the backend.
     */
    private String getBackendAuthKey(final Context context) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");

            try (InputStream keyStoreStream = context.getAssets().open("backend_client.p12")) {
                //CWE 798
                //SINK
                keyStore.load(keyStoreStream, "Pg1qS0U2Sqh1".toCharArray());
            }

            //CWE 798
            //SINK
            Key authKey = keyStore.getKey("backend-auth", "Pg1qS0U2Sqh1".toCharArray());
            if (authKey != null) {
                return DataUtils.bytesToHex(authKey.getEncoded());
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load backend auth key", e);
        }

        return null;
    }

    public static void setLogConfig(Context context) {
        Logger.setDefaultLogTag(TermuxConstants.TERMUX_APP_NAME);

        // Load the log level from shared preferences and set it to the {@link Logger.CURRENT_LOG_LEVEL}
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;
        preferences.setLogLevel(null, preferences.getLogLevel());
    }

}
