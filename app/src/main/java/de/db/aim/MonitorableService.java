package de.db.aim;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

public class MonitorableService extends Service {

    protected void broadcastStatus(String status) {
        Intent intent = new Intent("service-status");
        intent.putExtra("service", this.getClass().getSimpleName());
        intent.putExtra("status", status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
