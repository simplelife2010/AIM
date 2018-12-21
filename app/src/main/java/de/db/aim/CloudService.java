package de.db.aim;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;

public class CloudService extends Service implements AudioEncoderListener {

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
            mService.registerAudioEncoderListener(CloudService.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService.unregisterAudioEncoderListener(CloudService.this);
            mBound = false;
        }
    };

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (getString(R.string.pref_mqtt_client_id_prefix_key).equals(key) ||
                    getString(R.string.pref_mqtt_server_uri_key).equals(key) ||
                    getString(R.string.pref_topic_level_principal_key).equals(key) ||
                    getString(R.string.pref_topic_level_application_key).equals(key) ||
                    getString(R.string.pref_topic_level_component_audio_key).equals(key)) {
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

    @Override
    public void onNewEncodedAudioFrame(long timestamp, String audioPathName) {
        Log.d(TAG, "Received encoded audio filename " + audioPathName + " with timestamp " + timestamp);
        new AudioPublisherTask().execute(new AudioPublisherTaskParams(mMqttClient, getTopic(), timestamp, new String(audioPathName)));
    }

    private String getTopic() {
        return stringPreferenceValue(R.string.pref_topic_level_principal_key) + "/" +
                Build.SERIAL + "/" +
                stringPreferenceValue(R.string.pref_topic_level_application_key) + "/" +
                stringPreferenceValue(R.string.pref_topic_level_component_audio_key);
    }

    void setupService() {
        String clientId = stringPreferenceValue(R.string.pref_mqtt_client_id_prefix_key) + "_" + Build.SERIAL;
        Log.i(TAG, "MQTT client id is " + clientId);
        mMqttClient = new MqttAndroidClient(getApplicationContext(), stringPreferenceValue(R.string.pref_mqtt_server_uri_key), clientId);
        Log.d(TAG, "Created MQTT Client");
        mMqttClient.setCallback(new MqttCallbackImpl());
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setAutomaticReconnect(true);
        connectOptions.setCleanSession(false);
        connectOptions.setConnectionTimeout(30);
        connectOptions.setKeepAliveInterval(60);
        connectOptions.setMaxInflight(10);
        connectOptions.setMqttVersion(MqttConnectOptions.MQTT_VERSION_DEFAULT);

        try {
            IMqttToken token = mMqttClient.connect(connectOptions, null, new IMqttConnectActionListener());
            Log.d(TAG, "MQTT connect token: " + token.toString());
        } catch (MqttException e) {
            e.printStackTrace();
        }
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

    private class MqttCallbackImpl implements MqttCallbackExtended {

        private final String TAG = MqttCallbackImpl.class.getSimpleName();

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            if (reconnect) {
                Log.d(TAG, "Reconnect to " + serverURI + " complete");
            } else {
                Log.d(TAG, "Connect to " + serverURI + " complete");
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            Log.d(TAG, "Connection lost: " + cause.toString());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            Log.d(TAG, "Message arrived at topic " + topic);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            Log.d(TAG, "Message delivery complete: " + token.toString());
        }
    }

    private class IMqttConnectActionListener implements IMqttActionListener {

        private final String TAG = IMqttConnectActionListener.class.getSimpleName();

        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
            Log.d(TAG, "MQTT connect action successful");
            DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
            disconnectedBufferOptions.setBufferEnabled(true);
            disconnectedBufferOptions.setBufferSize(100);
            disconnectedBufferOptions.setPersistBuffer(false);
            disconnectedBufferOptions.setDeleteOldestMessages(false);
            mMqttClient.setBufferOpts(disconnectedBufferOptions);
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
            Log.d(TAG, "MQTT connect action unsuccessful: "  + exception.toString());
        }
    }

    private class AudioPublisherTaskParams {
        MqttAndroidClient mqttClient;
        String topic;
        long timestamp;
        String pathName;

        AudioPublisherTaskParams(MqttAndroidClient mqttClient, String topic, long timestamp, String pathName) {
            this.mqttClient = mqttClient;
            this.topic = topic;
            this.timestamp = timestamp;
            this.pathName = pathName;
        }
    }

    private static class AudioPublisherTask extends AsyncTask<AudioPublisherTaskParams, Void, Void> {

        private static final String TAG = AudioPublisherTask.class.getSimpleName();

        @Override
        protected Void doInBackground(AudioPublisherTaskParams... audioPublisherTaskParams) {
            Log.d(TAG, "AudioPublisherTask starting to publish " +
                    audioPublisherTaskParams[0].pathName +
                    " with timestamp " +
                    audioPublisherTaskParams[0].timestamp +
                    " on topic " +
                    audioPublisherTaskParams[0].topic);
            publishAudioFile(audioPublisherTaskParams[0].mqttClient,
                    audioPublisherTaskParams[0].topic,
                    audioPublisherTaskParams[0].timestamp,
                    audioPublisherTaskParams[0].pathName);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.d(TAG, "AudioPublisherTask finished");
        }

        private void publishAudioFile(MqttAndroidClient mqttClient, String topic, long timestamp, String pathName) {
            try {
                Log.d(TAG, "Publishing audio file");
                MqttMessage message = new MqttMessage();
                message.setPayload(getPayload(timestamp, pathName));
                message.setQos(1);
                message.setRetained(false);
                mqttClient.publish(topic, message);
            } catch (MqttException e) {
                Log.e(TAG, "Could not publish message: " + e.toString());
            }
        }

        private byte[] getPayload(long timestamp, String pathName) {
            return new String("Test-Payload").getBytes(StandardCharsets.UTF_8);
        }
    }
}
