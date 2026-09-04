package com.termux.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.data.DataUtils;
import com.termux.shared.interact.ShareUtils;

/** Routes an external "open target" deep link to the requested target. */
public class LinkRouterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleOpenTargetLink(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleOpenTargetLink(intent);
    }

    //CWE 926
    //SOURCE
    private void handleOpenTargetLink(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;

        Uri data = intent.getData();
        if (data == null) return;

        String target = data.getQueryParameter("target");
        if (target == null) return;

        String resolvedTarget = DataUtils.getDefaultIfNull(target, "");
        ShareUtils.shareText(this, "callExternal", resolvedTarget);
    }
}
