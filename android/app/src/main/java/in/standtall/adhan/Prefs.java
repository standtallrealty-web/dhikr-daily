package in.standtall.adhan;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final double PUNE_LAT = 18.5204, PUNE_LON = 73.8567;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("adhan", Context.MODE_PRIVATE);
    }
    static boolean master(Context c) { return sp(c).getBoolean("master", true); }
    static void setMaster(Context c, boolean v) { sp(c).edit().putBoolean("master", v).apply(); }
    static boolean prayerOn(Context c, int i) { return sp(c).getBoolean("p" + i, true); }
    static void setPrayerOn(Context c, int i, boolean v) { sp(c).edit().putBoolean("p" + i, v).apply(); }
    static String sched(Context c) { return sp(c).getString("sched", ""); }
    static void setSched(Context c, String json) { sp(c).edit().putString("sched", json).apply(); }
    static boolean hanafi(Context c) { return sp(c).getBoolean("hanafi", false); }
    static void setHanafi(Context c, boolean v) { sp(c).edit().putBoolean("hanafi", v).apply(); }
    static double lat(Context c) { return Double.longBitsToDouble(sp(c).getLong("lat", Double.doubleToLongBits(PUNE_LAT))); }
    static double lon(Context c) { return Double.longBitsToDouble(sp(c).getLong("lon", Double.doubleToLongBits(PUNE_LON))); }
    static boolean customLoc(Context c) { return sp(c).getBoolean("custom", false); }
    static void setLoc(Context c, double lat, double lon) {
        sp(c).edit().putLong("lat", Double.doubleToLongBits(lat)).putLong("lon", Double.doubleToLongBits(lon)).putBoolean("custom", true).apply();
    }
    static void resetLoc(Context c) { sp(c).edit().putBoolean("custom", false).remove("lat").remove("lon").apply(); }
}
