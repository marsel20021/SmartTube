package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;

/**
 * Обработка кнопки WLED в плеере.
 * Короткое нажатие включает/выключает подсветку, долгое открывает настройки.
 * Состояние хранится в SharedPreferences под ключом "ambilight_enabled".
 */
@SuppressWarnings("deprecation")
public class AmbilightControllerUi extends BasePlayerController {
    private static final String KEY_ENABLED = "ambilight_enabled";
    // Модуль common не видит классы модуля tv, поэтому активити вызываем по имени
    private static final String SETTINGS_ACTIVITY =
            "com.liskovsoft.smartyoutubetv2.tv.ui.playback.ambilight.WledSettingsActivity";

    private int mWledActionId = 0;

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (!isWledButton(buttonId)) {
            return;
        }

        // Плеер передаёт состояние кнопки ДО нажатия, новое состояние выставляем сами
        boolean enable = buttonState == PlayerUI.BUTTON_OFF;

        SharedPreferences prefs = getPrefs();
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_ENABLED, enable).apply();
        }

        applyButtonState(enable);
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        Context context = getContext();

        if (isWledButton(buttonId) && context != null) {
            Intent intent = new Intent();
            intent.setClassName(context, SETTINGS_ACTIVITY);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    // Синхронизируем вид кнопки с настройкой: при создании плеера и после возврата из настроек
    @Override
    public void onEngineInitialized() {
        syncButtonWithPrefs();
    }

    @Override
    public void onVideoLoaded(Video item) {
        syncButtonWithPrefs();
    }

    @Override
    public void onViewResumed() {
        syncButtonWithPrefs();
    }

    private void syncButtonWithPrefs() {
        applyButtonState(isEnabledInPrefs());
    }

    private void applyButtonState(boolean enabled) {
        int id = getWledActionId();

        if (getPlayer() != null && id != 0) {
            getPlayer().setButtonState(id, enabled ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        }
    }

    private boolean isEnabledInPrefs() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return true;
        }

        Object value = prefs.getAll().get(KEY_ENABLED);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return true;   // как в AmbilightSettings: по умолчанию включено
    }

    private boolean isWledButton(int buttonId) {
        int id = getWledActionId();
        return id != 0 && buttonId == id;
    }

    /** ID кнопки живёт в модуле tv, поэтому ищем его по имени (и запоминаем). */
    private int getWledActionId() {
        if (mWledActionId == 0) {
            Context context = getContext();
            if (context != null) {
                mWledActionId = context.getResources().getIdentifier("action_wled", "id", context.getPackageName());
            }
        }
        return mWledActionId;
    }

    private SharedPreferences getPrefs() {
        Context context = getContext();
        return context != null ? android.preference.PreferenceManager.getDefaultSharedPreferences(context) : null;
    }
}