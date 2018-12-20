package de.db.aim;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;

public class CloudService extends Service {

    private static final String TAG = CloudService.class.getSimpleName();

    private CloudBinder mBinder = new CloudBinder();
    private AudioEncoderService mService;
    private boolean mBound = false;
    private MqttAndroidClient mMqttClient;

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            AudioEncoderService.AudioEncoderBinder binder = (AudioEncoderService.AudioEncoderBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (getString(R.string.pref_client_id_prefix_key).equals(key)) {
                Log.i(TAG, "An encoder preference has been changed: " + key);
                setupService();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Intent intent = new Intent(this, AudioEncoderService.class);
        Log.d(TAG,"Binding AudioEncoderService");
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        sharedPreferences().registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        setupService();
    }

    @Override
    public void onDestroy() {
        sharedPreferences().unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        Log.d(TAG,"Unbinding AudioEncoderService");
        unbindService(mConnection);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound");
        return mBinder;
    }

    void setupService() {
        String clientId = stringPreferenceValue(R.string.pref_client_id_prefix_key) + "_" + Build.SERIAL;
        Log.i(TAG, "MQTT client id is " + clientId);
    }

    private String stringPreferenceValue(int key) {
        return sharedPreferences().getString(getString(key), "");
    }

    private int integerPreferenceValue(int key) {
        return Integer.parseInt(sharedPreferences().getString(getString(key), ""));
    }

    private SharedPreferences sharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    class CloudBinder extends Binder {
        CloudService getService() {
            // Return this instance of LocalService so clients can call public methods
            return CloudService.this;
        }
    }
}
