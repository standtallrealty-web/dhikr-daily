package in.standtall.adhan;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.format.DateFormat;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Date;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final String HOST = "dhikr-daily.standtallrealty.workers.dev";
    static final String URL = "https://" + HOST + "/";
    static final String VERSION = "2.0";

    private WebView wv;
    private PermissionRequest pendingWebPerm;
    private GeolocationPermissions.Callback geoCb;
    private String geoOrigin;
    private ValueCallback<Uri[]> fileCb;
    private SpeechRecognizer sr;
    private String pendingSrLang;
    private boolean beepMuted;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void showError(String title, String err) {
        ScrollView sv = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(30), dp(14), dp(20));
        TextView h = new TextView(this);
        h.setText(title);
        h.setTextSize(18);
        h.setTextColor(Color.RED);
        TextView e = new TextView(this);
        e.setText(err.length() > 2500 ? err.substring(0, 2500) : err);
        e.setTextSize(11);
        e.setTextIsSelectable(true);
        l.addView(h);
        l.addView(e);
        sv.addView(l);
        setContentView(sv);
    }

    private static String trace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final android.content.SharedPreferences cp = getSharedPreferences("crash", Context.MODE_PRIVATE);
        String last = cp.getString("last", null);
        if (last != null) {
            cp.edit().remove("last").commit();
            showError("The app crashed last time. Please send a screenshot of this to Claude:", last);
            return;
        }
        try {
            init();
        } catch (Throwable e) {
            showError("Startup error. Please send a screenshot of this to Claude:", trace(e));
        }
    }

    private void init() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        try { Scheduler.scheduleAll(this); } catch (Exception e) { }

        getWindow().setStatusBarColor(Color.parseColor("#F8F4EA"));
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setNavigationBarColor(Color.parseColor("#FFFDF8"));

        wv = new WebView(this);
        wv.setBackgroundColor(Color.parseColor("#F8F4EA"));
        setContentView(wv);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " DhikrDailyApp/" + VERSION);
        wv.addJavascriptInterface(new Bridge(), "AndroidAdhan");
        wv.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
        });
        wv.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(new Runnable() { @Override public void run() {
                    boolean audio = false;
                    for (String r : req.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) audio = true;
                    if (!audio) { req.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        req.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        pendingWebPerm = req;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 4);
                    }
                }});
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    cb.invoke(origin, true, false);
                } else {
                    geoCb = cb;
                    geoOrigin = origin;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 5);
                }
            }
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try { startActivityForResult(p.createIntent(), 9); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });
        wv.loadUrl(URL);
    }

    @Override
    protected void onActivityResult(int code, int res, Intent data) {
        super.onActivityResult(code, res, data);
        if (code == 9 && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (code == 3) {
            if (ok && pendingSrLang != null) doSr(pendingSrLang);
            else { js("error", "{\"e\":\"not-allowed\"}"); js("end", "{}"); }
            pendingSrLang = null;
        } else if (code == 4 && pendingWebPerm != null) {
            if (ok) pendingWebPerm.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE}); else pendingWebPerm.deny();
            pendingWebPerm = null;
        } else if (code == 5 && geoCb != null) {
            geoCb.invoke(geoOrigin, ok, false);
            geoCb = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wv.canGoBack()) wv.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        destroySr();
        super.onDestroy();
    }

    // ---------- speech recognition (used by voice counting) ----------
    private void js(final String type, final String json) {
        runOnUiThread(new Runnable() { @Override public void run() {
            if (wv != null) wv.evaluateJavascript("window.__srCb&&window.__srCb('" + type + "'," + JSONObject.quote(json) + ")", null);
        }});
    }

    private void muteBeep(boolean mute) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (mute && !beepMuted) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                beepMuted = true;
            } else if (!mute && beepMuted) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
                beepMuted = false;
            }
        } catch (Exception e) { }
    }

    private void destroySr() {
        try { if (sr != null) { sr.setRecognitionListener(null); sr.cancel(); sr.destroy(); } } catch (Exception e) { }
        sr = null;
        muteBeep(false);
    }

    private void doSr(String lang) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            js("error", "{\"e\":\"service-not-allowed\"}");
            js("end", "{}");
            return;
        }
        destroySr();
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(android.os.Bundle p) { js("start", "{}"); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float v) { }
            @Override public void onBufferReceived(byte[] b) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int t, android.os.Bundle p) { }
            @Override public void onPartialResults(android.os.Bundle b) {
                ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (l != null && l.size() > 0) {
                    try { js("partial", new JSONObject().put("t", l.get(0)).toString()); } catch (Exception e) { }
                }
            }
            @Override public void onResults(android.os.Bundle b) {
                ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                float[] c = b.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                if (l != null && l.size() > 0) {
                    try { js("final", new JSONObject().put("t", l.get(0)).put("c", c != null && c.length > 0 ? c[0] : 0).toString()); } catch (Exception e) { }
                }
                js("end", "{}");
            }
            @Override public void onError(int e) {
                String er = null;
                if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) er = "not-allowed";
                else if (e == SpeechRecognizer.ERROR_NETWORK || e == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) er = "network";
                else if (e == SpeechRecognizer.ERROR_AUDIO) er = "audio-capture";
                if (er != null) js("error", "{\"e\":\"" + er + "\"}");
                js("end", "{}");
            }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        muteBeep(true);
        sr.startListening(i);
    }

    // ---------- bridge used by the Dhikr Daily screens ----------
    private class Bridge {
        @JavascriptInterface
        public String version() { return VERSION; }

        /** JSON: {on:bool, p:[0/1 x5], t:[[ms x5] x days]} computed by the app with the user's own settings */
        @JavascriptInterface
        public void setSchedule(String json) {
            try {
                JSONObject o = new JSONObject(json);
                Prefs.setMaster(MainActivity.this, o.optBoolean("on", true));
                Prefs.setSched(MainActivity.this, json);
                Scheduler.scheduleAll(MainActivity.this);
            } catch (Exception e) { }
        }

        @JavascriptInterface
        public String status() {
            try {
                long[] n = Scheduler.next(MainActivity.this);
                JSONObject o = new JSONObject();
                o.put("on", Prefs.master(MainActivity.this));
                if (n != null) o.put("next", PrayerTimes.NAMES[(int) n[0]] + " at " + DateFormat.getTimeFormat(MainActivity.this).format(new Date(n[1])));
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void testNow() {
            runOnUiThread(new Runnable() { @Override public void run() {
                Intent s = new Intent(MainActivity.this, AdhanService.class);
                s.setAction(AdhanService.ACT_PLAY);
                s.putExtra(Scheduler.EXTRA_IDX, 1);
                startForegroundService(s);
            }});
        }

        @JavascriptInterface
        public void testIn(final int seconds) {
            runOnUiThread(new Runnable() { @Override public void run() {
                Scheduler.scheduleTest(MainActivity.this, 1, seconds * 1000L);
            }});
        }

        @JavascriptInterface
        public void openBattery() {
            runOnUiThread(new Runnable() { @Override public void run() {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception e2) { }
                }
            }});
        }

        @JavascriptInterface
        public void notify(String title, String body) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel("general", "Reminders", NotificationManager.IMPORTANCE_DEFAULT));
                Intent open = new Intent(MainActivity.this, MainActivity.class);
                android.app.PendingIntent pi = android.app.PendingIntent.getActivity(MainActivity.this, 0, open,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
                android.app.Notification n = new android.app.Notification.Builder(MainActivity.this, "general")
                        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
                        .setContentIntent(pi).setAutoCancel(true).build();
                nm.notify((int) (System.currentTimeMillis() % 100000), n);
            } catch (Exception e) { }
        }

        @JavascriptInterface
        public void vibrate(final int ms) {
            try {
                android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v == null) return;
                int d = Math.max(10, Math.min(ms, 500));
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createOneShot(d, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                else v.vibrate(d);
            } catch (Exception e) { }
        }

        @JavascriptInterface
        public void keepAwake(final boolean on) {
            runOnUiThread(new Runnable() { @Override public void run() {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }});
        }

        @JavascriptInterface
        public void srStart(final String lang) {
            runOnUiThread(new Runnable() { @Override public void run() {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    pendingSrLang = lang;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
                } else doSr(lang);
            }});
        }

        @JavascriptInterface
        public void srStop() {
            runOnUiThread(new Runnable() { @Override public void run() { destroySr(); }});
        }
    }
}
