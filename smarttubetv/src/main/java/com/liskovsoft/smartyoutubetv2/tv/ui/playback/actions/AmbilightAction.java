package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Кнопка WLED в плеере: красная иконка = подсветка выключена, зелёная = включена.
 * Иконка одна (action_wled.png), цвет подставляется программно.
 */
public class AmbilightAction extends TwoStateAction {
    private static final int COLOR_OFF = 0xFFE53935;   // красный
    private static final int COLOR_ON = 0xFF43A047;    // зелёный

    public AmbilightAction(Context context) {
        super(context, R.id.action_wled, R.drawable.action_wled);

        Drawable icon = ContextCompat.getDrawable(context, R.drawable.action_wled);
        if (icon instanceof BitmapDrawable) {
            BitmapDrawable base = (BitmapDrawable) icon;
            Drawable[] drawables = new Drawable[2];
            drawables[INDEX_OFF] = ActionHelpers.createDrawable(context, base, COLOR_OFF);
            drawables[INDEX_ON] = ActionHelpers.createDrawable(context, base, COLOR_ON);
            setDrawables(drawables);
        }

        String[] labels = new String[2];
        labels[INDEX_OFF] = "WLED: Выкл";
        labels[INDEX_ON] = "WLED: Вкл";
        setLabels(labels);
    }
}