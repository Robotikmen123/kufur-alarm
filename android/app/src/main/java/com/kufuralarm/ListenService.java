package com.kufuralarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Arka planda sürekli dinler, küfür duyunca kırmızı uyarıyı açar. */
public class ListenService extends Service implements RecognitionListener {
    public static final String ACTION_STOP = "com.kufuralarm.STOP";
    private static final String CH = "dinleme";
    private static final int NOTIF_ID = 1;

    public static volatile boolean running = false;

    private final Handler h = new Handler(Looper.getMainLooper());
    private SpeechRecognizer sr;
    private Intent recIntent;
    private AudioManager am;
    private String lastHitText = "";
    private boolean stopping = false;

    // ---- sayaç -------------------------------------------------------------
    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static int todayCount(Context c) {
        return prefs(c).getInt("c_" + today(), 0);
    }

    static int increment(Context c) {
        SharedPreferences p = prefs(c);
        int n = p.getInt("c_" + today(), 0) + 1;
        p.edit().putInt("c_" + today(), n).apply();
        return n;
    }

    /** Uygulama ekranında görünen durum satırı. */
    static void report(Context c, String msg) {
        prefs(c).edit().putString("status", msg).apply();
        c.sendBroadcast(new Intent("com.kufuralarm.UPDATE").setPackage(c.getPackageName()));
    }

    static String errName(int e) {
        switch (e) {
            case 1: return "ağ zaman aşımı";
            case 2: return "ağ hatası";
            case 3: return "ses kaydı hatası";
            case 4: return "sunucu hatası";
            case 5: return "istemci hatası";
            case 6: return "konuşma yok";
            case 7: return "eşleşme yok";
            case 8: return "tanıyıcı meşgul";
            case 9: return "izin yetersiz";
            case 10: return "çok fazla istek";
            case 11: return "sunucu bağlantısı koptu";
            case 12: return "dil desteklenmiyor";
            case 13: return "dil indirilmemiş";
            default: return "hata " + e;
        }
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("kufur", MODE_PRIVATE);
    }

    // ---- servis ------------------------------------------------------------
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForegroundCompat();
        } catch (Throwable e) {
            report(this, "Servis başlatılamadı: " + e);
            App.saveCrash(this, e);
            running = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            am = (AudioManager) getSystemService(AUDIO_SERVICE);
            muteBeeps(true);
            initRecognizer();
            startListening();
            report(this, "Dinleniyor 🎙️");
        }
        return START_NOT_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Dinleme",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, ListenService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        Notification n = b.setContentTitle("Küfür Alarmı dinliyor 🎙️")
                .setContentText("Bugün: " + todayCount(this) + " küfür")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Durdur", stop).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void initRecognizer() {
        if (sr != null) {
            try { sr.destroy(); } catch (Exception ignored) {}
        }
        sr = null;
        // Önce Google'ın tanıyıcısını dene (Türkçe'de en iyisi), yoksa varsayılan.
        android.content.ComponentName g = new android.content.ComponentName(
                "com.google.android.googlequicksearchbox",
                "com.google.android.voicesearch.serviceapi.GoogleRecognitionService");
        try {
            getPackageManager().getServiceInfo(g, 0);
            sr = SpeechRecognizer.createSpeechRecognizer(this, g);
        } catch (Exception ignored) {}
        if (sr == null) sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(this);
        recIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        recIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR");
        recIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        // Profanity filtresini kapatmayı dene (destekleyen cihazlarda).
        recIntent.putExtra("android.speech.extra.PROFANITY_FILTER", false);
        recIntent.putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 15000L);
        recIntent.putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 1500L);
        recIntent.putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1500L);
    }

    private void startListening() {
        if (stopping || sr == null) return;
        lastHitText = "";
        try {
            sr.startListening(recIntent);
        } catch (Exception e) {
            restart(1000);
        }
    }

    private void restart(long delay) {
        if (stopping) return;
        h.removeCallbacksAndMessages(null);
        h.postDelayed(new Runnable() {
            @Override public void run() {
                try { sr.cancel(); } catch (Exception ignored) {}
                startListening();
            }
        }, delay);
    }

    /** Her yeniden başlatmada çıkan "bip" sesini kıs. */
    private void muteBeeps(boolean mute) {
        if (am == null) return;
        int dir = mute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE;
        int[] streams = {AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM};
        for (int s : streams) {
            try { am.adjustStreamVolume(s, dir, 0); } catch (Exception ignored) {}
        }
        if (prefs(this).getBoolean("mute_music", false)) {
            try { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0); } catch (Exception ignored) {}
        }
    }

    private void check(Bundle b) {
        if (b == null) return;
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return;
        report(this, "Duydu: " + list.get(0));
        // İlk (en olası) sonuç + alternatifler: herhangi birinde küfür varsa yakala.
        for (String text : list) {
            List<String> hits = KufurDetector.find(text);
            if (!hits.isEmpty()) {
                // Aynı cümlenin kısmi/tam sonuçları için iki kez sayma.
                String key = hits.toString();
                if (key.equals(lastHitText)) return;
                lastHitText = key;
                int n = increment(this);
                AlertOverlay.show(this, n);
                startForegroundCompat(); // bildirimdeki sayacı güncelle
                sendBroadcast(new Intent("com.kufuralarm.UPDATE").setPackage(getPackageName()));
                return;
            }
        }
    }

    // ---- RecognitionListener ----------------------------------------------
    @Override public void onPartialResults(Bundle b) { check(b); }

    @Override public void onResults(Bundle b) {
        check(b);
        restart(50);
    }

    @Override public void onError(int error) {
        if (error != 6 && error != 7) report(this, "Tanıyıcı: " + errName(error) + " (tekrar deneniyor)");
        long d = 100;
        if (error == 9) d = 5000;
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) d = 500;
        if (error == SpeechRecognizer.ERROR_NETWORK
                || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_SERVER) d = 3000;
        if (error == SpeechRecognizer.ERROR_CLIENT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            initRecognizer();
        }
        restart(d);
    }

    @Override public void onReadyForSpeech(Bundle p) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float v) {}
    @Override public void onBufferReceived(byte[] b) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onEvent(int t, Bundle p) {}

    @Override
    public void onDestroy() {
        stopping = true;
        running = false;
        h.removeCallbacksAndMessages(null);
        if (sr != null) {
            try { sr.destroy(); } catch (Exception ignored) {}
        }
        muteBeeps(false);
        sendBroadcast(new Intent("com.kufuralarm.UPDATE").setPackage(getPackageName()));
        super.onDestroy();
    }
}
