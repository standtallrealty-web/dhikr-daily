package in.standtall.adhan;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final SharedPreferences cp = getSharedPreferences("crash", Context.MODE_PRIVATE);
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    e.printStackTrace(new java.io.PrintWriter(sw));
                    cp.edit().putString("last", sw.toString()).commit();
                } catch (Throwable x) { }
                if (old != null) old.uncaughtException(t, e);
            }
        });
    }
}
