package in.standtall.adhan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int idx = i.getIntExtra(Scheduler.EXTRA_IDX, 0);
        long at = i.getLongExtra(Scheduler.EXTRA_AT, 0);
        boolean test = i.getBooleanExtra(Scheduler.EXTRA_TEST, false);
        boolean fresh = at == 0 || System.currentTimeMillis() - at < 10 * 60 * 1000L;
        if (fresh && (test || Prefs.master(c))) {
            Intent s = new Intent(c, AdhanService.class);
            s.setAction(AdhanService.ACT_PLAY);
            s.putExtra(Scheduler.EXTRA_IDX, idx);
            c.startForegroundService(s);
        }
        Scheduler.scheduleAll(c);
    }
}
