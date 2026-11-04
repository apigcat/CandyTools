package com.candy.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** 主题：0 跟随系统 / 1 浅色 / 2 深色。 */
final class ThemeHelper {

    static final String PREFS = "candy_tools";
    static final String KEY_THEME = "theme_mode";

    static final int SYSTEM = 0;
    static final int LIGHT = 1;
    static final int DARK = 2;

    private ThemeHelper() {
    }

    static int getMode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_THEME, SYSTEM);
    }

    static void setMode(Context context, int mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME, mode).apply();
    }

    /** 按用户选择强制 uiMode，让 values / values-night 资源生效。 */
    static Context wrap(Context base) {
        int mode = getMode(base);
        if (mode == SYSTEM) {
            return base;
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        int night = mode == DARK
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(config);
    }
}
