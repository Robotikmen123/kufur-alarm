package com.kufuralarm;

import android.app.Application;
import android.content.Context;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Çökmeleri kaydeder, uygulama tekrar açılınca ekranda gösterilir. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                saveCrash(App.this, e);
                if (def != null) def.uncaughtException(t, e);
            }
        });
    }

    static void saveCrash(Context c, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        if (s.length() > 1500) s = s.substring(0, 1500);
        ListenService.prefs(c).edit().putString("crash", s).commit();
    }
}
