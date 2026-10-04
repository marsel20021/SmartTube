package com.liskovsoft.smartyoutubetv2.tv.ui.playback.ambilight;

import android.os.Bundle;
import android.text.InputType;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Экран настроек WLED Ambilight.
 * Должен быть объявлен в AndroidManifest.xml модуля smarttubetv,
 * иначе startActivity() падает с "Unable to find explicit activity class".
 */
public class WledSettingsActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Без setContentView/FrameLayout: android.R.id.content уже есть в окне.
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new WledFragment())
                    .commit();
        }
    }

    public static class WledFragment extends PreferenceFragmentCompat {
        // Поля, где нужен только числовой ввод (всё, кроме wled_ip)
        private static final String[] NUMERIC_KEYS = {
                "udp_port", "fps", "brightness", "smoothing", "gamma",
                "black_threshold", "capture_depth", "led_offset",
                "leds_top", "leds_bottom", "leds_left", "leds_right",
                "margin_x", "margin_y", "saturation"
        };

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.wled_settings, rootKey);

            for (String key : NUMERIC_KEYS) {
                EditTextPreference pref = findPreference(key);
                if (pref != null) {
                    pref.setOnBindEditTextListener(editText ->
                            editText.setInputType(InputType.TYPE_CLASS_NUMBER));
                }
            }
        }
    }
}