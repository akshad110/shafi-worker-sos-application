package com.example.shafi.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.graphics.drawable.DrawableWrapper;

import androidx.core.content.ContextCompat;

import com.example.shafi.R;

import java.util.Locale;

public final class LetterMark {

    private LetterMark() {
    }

    public static String letter(String name) {
        if (name == null) return "S";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "S";
        int point = trimmed.codePointAt(0);
        return new String(Character.toChars(point)).toUpperCase(Locale.US);
    }

    public static Drawable icon(Context context, String name) {
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.max(1, Math.round(24f * density));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(ContextCompat.getColor(context, R.color.avatar));
        float radius = size / 2f;
        canvas.drawCircle(radius, radius, radius, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(size * 0.52f);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(letter(name), radius, radius - (metrics.ascent + metrics.descent) / 2f, paint);
        return new Untinted(new BitmapDrawable(context.getResources(), bitmap));
    }

    private static final class Untinted extends DrawableWrapper {
        Untinted(Drawable drawable) {
            super(drawable);
        }

        @Override
        public void setTintList(ColorStateList tint) {
        }

        @Override
        public void setTint(int tintColor) {
        }

        @Override
        public void setTintMode(PorterDuff.Mode tintMode) {
        }

        @Override
        public ConstantState getConstantState() {
            return null;
        }
    }
}
