package com.termux.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.file.TermuxFileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Imports a stored file, looked up by name in the local file store, into the app's files dir. */
public class FileImportActivity extends AppCompatActivity {

    private static final String LOG_TAG = "FileImportActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleImportFileLink(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleImportFileLink(intent);
    }

    //CWE 89
    //SOURCE
    private void handleImportFileLink(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;

        Uri data = intent.getData();
        if (data == null) return;

        String requestedFileName = data.getQueryParameter("name");
        if (requestedFileName == null) return;

        String unexpandedName = TermuxFileUtils.getUnExpandedTermuxPath(requestedFileName);
        String expandedName = TermuxFileUtils.getExpandedTermuxPath(unexpandedName);
        String fileBody = TermuxFileUtils.readStoredFileBody(this, expandedName);

        if (fileBody == null) return;

        try {
            File outDir = new File(getFilesDir(), "imported");
            outDir.mkdirs();
            File outFile = new File(outDir, "restored.txt");
            try (FileOutputStream out = new FileOutputStream(outFile)) {
                out.write(fileBody.getBytes(StandardCharsets.UTF_8));
            }
            Logger.logInfo(LOG_TAG, "Imported file body saved to " + outFile.getAbsolutePath());
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save imported file", e);
        }
    }
}
