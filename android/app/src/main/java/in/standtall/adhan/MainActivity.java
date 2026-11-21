package in.standtall.adhan;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Date;

public class MainActivity extends Activity {
    private LinearLayout root;
    private boolean building;

    private static String trace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private void showError(String title, final String err) {
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

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final android.content.SharedPreferences cp = getSharedPreferences("crash", Context.MODE_PRIVATE);
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                cp.edit().putString("last", trace(e)).commit();
                if (old != null) old.uncaughtException(t, e);
            }
        });
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
        Scheduler.scheduleAll(this);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (root != null) build();
        } catch (Throwable e) {
            showError("Error. Please send a screenshot of this to Claude:", trace(e));
        }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(s);
        x.setAllCaps(false);
        x.setOnClickListener(l);
        return x;
    }

    private String fmt(long ms) { return DateFormat.getTimeFormat(this).format(new Date(ms)); }

    private void build() {
        building = true;
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#F7F1E3"));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(28));
        sv.addView(root);

        root.addView(text("Dhikr Adhan", 26, true, Color.parseColor("#256F5B")));
        root.addView(text("Version 1.3 · Powered by Stand Tall Realty", 12, false, Color.parseColor("#7A6A3A")));

        Switch master = new Switch(this);
        master.setText("Play adhan at prayer time");
        master.setTextSize(18);
        master.setChecked(Prefs.master(this));
        master.setPadding(0, dp(18), 0, dp(6));
        master.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (building) return;
                Prefs.setMaster(MainActivity.this, on);
                Scheduler.scheduleAll(MainActivity.this);
                build();
            }
        });
        root.addView(master);

        long[] nx = Scheduler.next(this);
        String st = !Prefs.master(this) ? "Adhan is OFF."
                : nx == null ? "No prayer is switched on."
                : "Next: " + PrayerTimes.NAMES[(int) nx[0]] + " at " + fmt(nx[1]) + ". It will play by itself, even if the phone is locked.";
        root.addView(text(st, 14, false, Color.parseColor("#256F5B")));

        root.addView(text("Today", 16, true, Color.parseColor("#333333")));
        ((LinearLayout.LayoutParams) root.getChildAt(root.getChildCount() - 1).getLayoutParams()).topMargin = dp(18);
        long[] today = Scheduler.day(this, 0);
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            Switch s = new Switch(this);
            s.setText(PrayerTimes.NAMES[i] + "   " + fmt(today[i]));
            s.setTextSize(17);
            s.setChecked(Prefs.prayerOn(this, i));
            s.setPadding(0, dp(8), 0, dp(8));
            s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                    if (building) return;
                    Prefs.setPrayerOn(MainActivity.this, idx, on);
                    Scheduler.scheduleAll(MainActivity.this);
                    build();
                }
            });
            root.addView(s);
        }

        Switch han = new Switch(this);
        han.setText("Hanafi Asr (shadow 2x). Off = standard");
        han.setTextSize(15);
        han.setChecked(Prefs.hanafi(this));
        han.setPadding(0, dp(12), 0, dp(6));
        han.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (building) return;
                Prefs.setHanafi(MainActivity.this, on);
                Scheduler.scheduleAll(MainActivity.this);
                build();
            }
        });
        root.addView(han);

        String loc = Prefs.customLoc(this)
                ? String.format("Location: %.3f, %.3f (your phone)", Prefs.lat(this), Prefs.lon(this))
                : "Location: Pune";
        root.addView(text(loc, 14, false, Color.parseColor("#333333")));
        ((LinearLayout.LayoutParams) root.getChildAt(root.getChildCount() - 1).getLayoutParams()).topMargin = dp(10);
        root.addView(button("Use my phone's location", new View.OnClickListener() {
            @Override public void onClick(View v) { useLocation(); }
        }));
        if (Prefs.customLoc(this)) {
            root.addView(button("Use Pune", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Prefs.resetLoc(MainActivity.this);
                    Scheduler.scheduleAll(MainActivity.this);
                    build();
                }
            }));
        }

        root.addView(text("Test", 16, true, Color.parseColor("#333333")));
        ((LinearLayout.LayoutParams) root.getChildAt(root.getChildCount() - 1).getLayoutParams()).topMargin = dp(18);
        root.addView(button("▶ Play adhan now", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent s = new Intent(MainActivity.this, AdhanService.class);
                s.setAction(AdhanService.ACT_PLAY);
                s.putExtra(Scheduler.EXTRA_IDX, 1);
                startForegroundService(s);
            }
        }));
        root.addView(button("⏱ Test in 1 minute (lock your phone)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Scheduler.scheduleTest(MainActivity.this, 1, 60000);
                Toast.makeText(MainActivity.this, "Adhan will play in 1 minute. Lock your phone now.", Toast.LENGTH_LONG).show();
            }
        }));

        root.addView(text("For best results", 16, true, Color.parseColor("#333333")));
        ((LinearLayout.LayoutParams) root.getChildAt(root.getChildCount() - 1).getLayoutParams()).topMargin = dp(18);
        root.addView(button("Allow this app to run in the background", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            }
        }));
        TextView foot = text("To stop the adhan: tap STOP in the notification, or press the side button.\n"
                + "Adhan plays at full alarm volume, even in silent mode.\n"
                + "Times match the Dhikr Daily app (Karachi method).", 13, false, Color.parseColor("#555555"));
        foot.setPadding(0, dp(16), 0, 0);
        root.addView(foot);

        setContentView(sv);
        building = false;
    }

    private void useLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 2);
            return;
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best == null) {
                Toast.makeText(this, "Location not available. Open Google Maps once, then try again.", Toast.LENGTH_LONG).show();
                return;
            }
            Prefs.setLoc(this, best.getLatitude(), best.getLongitude());
            Scheduler.scheduleAll(this);
            build();
        } catch (Exception e) {
            Toast.makeText(this, "Could not read the location.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 2 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) useLocation();
    }
}
