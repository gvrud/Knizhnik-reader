package com.example.reader;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

public final class UiTools {

    private UiTools() {
    }

    public static Context fixFontScale(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = 1.0f;
        if (Build.VERSION.SDK_INT >= 17) {
            return base.createConfigurationContext(config);
        }
        Resources res = base.getResources();
        res.updateConfiguration(config, res.getDisplayMetrics());
        return base;
    }
}