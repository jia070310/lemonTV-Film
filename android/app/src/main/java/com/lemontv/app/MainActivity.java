package com.lemontv.app;

import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        applyTaskIconFromSplashAsset();

        // Hide status bar and enable fullscreen for TV experience
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    // TaskDescription.Builder exists from API 29 (Q) onward only; P (28) must use the legacy 3-arg constructor.
    private void applyTaskIconFromSplashAsset() {
        try {
            Drawable d = ContextCompat.getDrawable(this, R.drawable.lom);
            if (d == null) {
                return;
            }
            String label = getString(R.string.title_activity_main);
            int accent = ContextCompat.getColor(this, R.color.splash_screen_background);
            ActivityManager.TaskDescription desc;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                desc = new ActivityManager.TaskDescription.Builder()
                    .setLabel(label)
                    .setIcon(R.drawable.lom)
                    .build();
            } else {
                Bitmap icon = drawableToBitmap(d);
                if (icon == null) {
                    return;
                }
                desc = new ActivityManager.TaskDescription(label, icon, accent);
            }
            setTaskDescription(desc);
        } catch (Throwable ignored) {
            // NoClassDefFoundError (e.g. mis-guarded APIs) is not a RuntimeException
        }
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bd = (BitmapDrawable) drawable;
            if (bd.getBitmap() != null) {
                return bd.getBitmap();
            }
        }
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        if (w <= 0 || h <= 0) {
            w = h = 256;
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, w, h);
        drawable.draw(canvas);
        return bitmap;
    }
}
