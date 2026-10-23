package com.kufuralarm;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private TextView status, count, info;
    private Button toggle, overlayBtn, micBtn, batteryBtn;

    private final BroadcastReceiver upd = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(48, 80, 48, 48);
        l.setBackgroundColor(Color.rgb(27, 27, 31));

        TextView title = tv("🛑 Küfür Alarmı", 28, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        l.addView(title);
        status = tv("", 15, Color.rgb(187, 187, 187));
        l.addView(status);
        count = tv("", 20, Color.rgb(255, 92, 92));
        count.setTypeface(null, Typeface.BOLD);
        l.addView(count);
        info = tv("", 14, Color.rgb(230, 230, 230));
        l.addView(info);

        micBtn = btn("1) Mikrofon ve bildirim izni ver", new View.OnClickListener() {
            @Override public void onClick(View v) { askRuntimePerms(); }
        });
        overlayBtn = btn("2) Diğer uygulamaların üstünde göster izni", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        batteryBtn = btn("3) Pil kısıtlamasını kaldır (arka planda kapanmasın)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            }
        });
        toggle = btn("", new View.OnClickListener() {
            @Override public void onClick(View v) { toggleService(); }
        });
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        Button test = btn("Uyarıyı test et", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!AlertOverlay.canShow(MainActivity.this)) {
                    toast("Önce 2. izni ver");
                    return;
                }
                AlertOverlay.show(MainActivity.this, ListenService.todayCount(MainActivity.this));
            }
        });

        CheckBox mute = new CheckBox(this);
        mute.setText("Bip sesi hâlâ geliyorsa medya sesini de kıs");
        mute.setTextColor(Color.rgb(187, 187, 187));
        mute.setChecked(ListenService.prefs(this).getBoolean("mute_music", false));
        mute.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                ListenService.prefs(MainActivity.this).edit().putBoolean("mute_music", on).apply();
                toast("Servisi yeniden başlatınca geçerli olur");
            }
        });

        l.addView(micBtn);
        l.addView(overlayBtn);
        l.addView(batteryBtn);
        l.addView(toggle);
        l.addView(test);
        l.addView(mute);
        l.addView(tv("\nNot: Konuşmalar Google'ın konuşma tanıma servisiyle yazıya çevrilir. "
                + "İnternet gerekir.", 12, Color.rgb(120, 120, 120)));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.rgb(27, 27, 31));
        sv.addView(l);
        setContentView(sv);

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Bu telefonda konuşma tanıma yok. Google uygulamasını kur/güncelle.");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter("com.kufuralarm.UPDATE");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(upd, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(upd, f);
        }
        refresh();
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(upd); } catch (Exception ignored) {}
    }

    private boolean hasMic() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean batteryFree() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void refresh() {
        boolean mic = hasMic(), ov = AlertOverlay.canShow(this), bat = batteryFree();
        micBtn.setText((mic ? "✅ " : "1) ") + "Mikrofon ve bildirim izni");
        overlayBtn.setText((ov ? "✅ " : "2) ") + "Diğer uygulamaların üstünde göster");
        batteryBtn.setText((bat ? "✅ " : "3) ") + "Pil kısıtlamasını kaldır");
        boolean run = ListenService.running;
        toggle.setText(run ? "⏹ Dinlemeyi durdur" : "▶ Dinlemeyi başlat");
        toggle.setBackgroundColor(run ? Color.rgb(90, 90, 96) : Color.rgb(220, 30, 30));
        status.setText(run ? "Servis açık" : "Kapalı");
        android.content.SharedPreferences p = ListenService.prefs(this);
        String st = p.getString("status", "");
        String cr = p.getString("crash", "");
        String txt = run ? st : "";
        if (cr.length() > 0) txt += "\n\nSon çökme (ekran görüntüsü atabilirsin):\n" + cr;
        info.setText(txt);
        count.setText("Bugünkü küfür sayısı: " + ListenService.todayCount(this));
    }

    private void askRuntimePerms() {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
        requestPermissions(p.toArray(new String[0]), 1);
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        refresh();
    }

    private void toggleService() {
        Intent i = new Intent(this, ListenService.class);
        if (ListenService.running) {
            stopService(i);
            ListenService.running = false;
        } else {
            if (!hasMic()) { toast("Önce mikrofon iznini ver"); askRuntimePerms(); return; }
            if (!AlertOverlay.canShow(this)) { toast("Önce 'üstte göster' iznini ver"); return; }
            ListenService.prefs(this).edit().remove("crash").putString("status", "Başlatılıyor...").apply();
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                ListenService.running = true;
            } catch (Throwable e) {
                App.saveCrash(this, e);
            }
        }
        status.postDelayed(new Runnable() { @Override public void run() { refresh(); } }, 1500);
    }

    private TextView tv(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, 12, 0, 12);
        return t;
    }

    private Button btn(String s, View.OnClickListener c) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 16, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
