package in.standtall.adhan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;

public class AdhanService extends Service {
    static final String ACT_PLAY = "in.standtall.adhan.PLAY";
    static final String ACT_STOP = "in.standtall.adhan.STOP";
    private static final String CH = "adhan";
    private static final int NID = 77;

    private MediaPlayer mp;
    private BroadcastReceiver screenRx;
    private AudioManager am;
    private AudioFocusRequest focus;
    private int oldVol = -1;
    private long t0;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACT_STOP.equals(intent.getAction())) {
            stopAll();
            return START_NOT_STICKY;
        }
        int idx = intent == null ? 0 : intent.getIntExtra(Scheduler.EXTRA_IDX, 0);
        startAdhan(idx);
        return START_NOT_STICKY;
    }

    private void startAdhan(int idx) {
        cleanup();
        String name = PrayerTimes.NAMES[Math.max(0, Math.min(4, idx))];
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CH, "Adhan", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);

        Intent stop = new Intent(this, AdhanService.class);
        stop.setAction(ACT_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 5, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action stopAct = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause), "STOP", stopPi).build();
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Adhan — " + name)
                .setContentText("Tap STOP, or press the side button, to silence")
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(stopPi)
                .addAction(stopAct)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NID, n);
        }

        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        try {
            oldVol = am.getStreamVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        } catch (Exception e) { oldVol = -1; }

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        try {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs).build();
            am.requestAudioFocus(focus);
        } catch (Exception e) { focus = null; }

        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(attrs);
            AssetFileDescriptor afd = getResources().openRawResourceFd(idx == 0 ? R.raw.fajr : R.raw.adhan);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK);
            mp.setVolume(1f, 1f);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer p) { stopAll(); }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer p, int what, int extra) { stopAll(); return true; }
            });
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            stopAll();
            return;
        }

        t0 = System.currentTimeMillis();
        screenRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                // Side button: screen turns off (phone was on) or on (phone was locked) -> stop
                if (System.currentTimeMillis() - t0 > 2000) stopAll();
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenRx, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenRx, f);
    }

    private void cleanup() {
        try { if (screenRx != null) unregisterReceiver(screenRx); } catch (Exception e) { }
        screenRx = null;
        try { if (mp != null) { mp.stop(); } } catch (Exception e) { }
        try { if (mp != null) { mp.release(); } } catch (Exception e) { }
        mp = null;
        try { if (focus != null && am != null) am.abandonAudioFocusRequest(focus); } catch (Exception e) { }
        focus = null;
        try { if (oldVol >= 0 && am != null) am.setStreamVolume(AudioManager.STREAM_ALARM, oldVol, 0); } catch (Exception e) { }
        oldVol = -1;
    }

    private void stopAll() {
        cleanup();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }
}
