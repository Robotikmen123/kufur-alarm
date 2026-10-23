package com.kufuralarm;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ekranın tamamını 5 saniye kırmızıya boyayan, kapatılamayan uyarı. */
public final class AlertOverlay {
    public static final int SECONDS = 5;

    private static View view;
    private static int remaining;
    private static TextView countdown;
    private static final Handler H = new Handler(Looper.getMainLooper());

    private AlertOverlay() {}

    public static boolean canShow(Context c) {
        return Settings.canDrawOverlays(c);
    }

    public static void show(final Context ctx, int todayCount) {
        if (view != null) {           // açıkken yeni küfür: süre baştan
            remaining = SECONDS;
            return;
        }
        if (!canShow(ctx)) return;
        final Context app = ctx.getApplicationContext();
        final WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);

        LinearLayout root = new LinearLayout(app);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(255, 0, 0));
        // Dokunmaları yut: alttaki uygulamaya hiçbir şey geçmesin.
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) { return true; }
        });
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        TextView title = text(app, "⚠ KÜFÜR\nALGILANDI ⚠", 44, true);
        countdown = text(app, String.valueOf(SECONDS), 96, true);
        TextView sub = text(app, "Bugün: " + todayCount + ". küfür", 22, false);
        sub.setTextColor(Color.rgb(255, 214, 214));
        root.addView(title);
        root.addView(countdown);
        root.addView(sub);

        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        int big = Math.max(dm.widthPixels, dm.heightPixels) + 400;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = -200;
        lp.y = -200;
        lp.width = big;
        lp.height = big;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            lp.setFitInsetsTypes(0);
        }
        // İçerik ekranın görünen ortasında dursun.
        root.setPadding(200, 200, big - 200 - dm.widthPixels, big - 200 - dm.heightPixels);

        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            return;
        }
        view = root;
        remaining = SECONDS;
        H.post(new Runnable() {
            @Override public void run() {
                if (view == null) return;
                if (remaining <= 0) {
                    try { wm.removeView(view); } catch (Exception ignored) {}
                    view = null;
                    return;
                }
                countdown.setText(String.valueOf(remaining));
                remaining--;
                H.postDelayed(this, 1000);
            }
        });
    }

    private static TextView text(Context c, String s, int sp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, 20, 0, 20);
        return t;
    }
}
