package de.db.aim;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class CloudService extends MonitorableService implements AudioEncoderListener {

    private static final String TAG = CloudService.class.getSimpleName();

    private CloudBinder mBinder = new CloudBinder();
    private AudioEncoderService mService;
    private boolean mBound = false;
    private MqttAndroidClient mMqttClient;
    private long mLastAudioPublishTimestamp;

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
                    getString(R.string.pref_topic_level_component_audio_key).equals(key) ||
                    getString(R.string.pref_audio_publish_period_key).equals(key)){
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
        broadcastStatus("Initializing");
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
    public void onNewEncodedAudioFrame(long timestamp, String path, String filename) {
        Log.d(TAG, "Received encoded audio filename " + filename + " with timestamp " + timestamp);
        long now = System.currentTimeMillis();
        if (now >= mLastAudioPublishTimestamp + 1000 * integerPreferenceValue(R.string.pref_audio_publish_period_key)) {
            Log.d(TAG, "Audio publish period is elapsed");
            mLastAudioPublishTimestamp = now;
            new AudioPublisherTask().execute(new AudioPublisherTaskParams(mMqttClient, getTopic(), timestamp, new String(path), new String(filename)));
        }
    }

    private String getTopic() {
        return stringPreferenceValue(R.string.pref_topic_level_principal_key) + "/" +
                Build.SERIAL + "/" +
                stringPreferenceValue(R.string.pref_topic_level_application_key) + "/" +
                stringPreferenceValue(R.string.pref_topic_level_component_audio_key);
    }

    void setupService() {
        mLastAudioPublishTimestamp = System.currentTimeMillis() - 1000 * integerPreferenceValue(R.string.pref_audio_publish_period_key);
        if (mMqttClient != null) {
            try {
                Log.d(TAG, "Disconnecting MQTT client...");
                mMqttClient.disconnect();
                Thread.sleep(100);
            } catch (MqttException e) {
                Log.e(TAG, "Could not disconnect MQTT client: " + e.toString());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
            Log.d(TAG, "MQTT Connection lost");
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
        String path;
        String filename;

        AudioPublisherTaskParams(MqttAndroidClient mqttClient, String topic, long timestamp, String path, String filename) {
            this.mqttClient = mqttClient;
            this.topic = topic;
            this.timestamp = timestamp;
            this.path = path;
            this.filename = filename;
        }
    }

    private static class AudioPublisherTask extends AsyncTask<AudioPublisherTaskParams, Void, Void> {

        private static final String TAG = AudioPublisherTask.class.getSimpleName();

        @Override
        protected Void doInBackground(AudioPublisherTaskParams... audioPublisherTaskParams) {
            Log.d(TAG, "AudioPublisherTask starting to publish " +
                    audioPublisherTaskParams[0].filename +
                    " with timestamp " +
                    audioPublisherTaskParams[0].timestamp +
                    " on topic " +
                    audioPublisherTaskParams[0].topic);
            publishAudioFile(audioPublisherTaskParams[0].mqttClient,
                    audioPublisherTaskParams[0].topic,
                    audioPublisherTaskParams[0].timestamp,
                    audioPublisherTaskParams[0].path,
                    audioPublisherTaskParams[0].filename);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.d(TAG, "AudioPublisherTask finished");
        }

        private void publishAudioFile(MqttAndroidClient mqttClient, String topic, long timestamp, String path, String filename) {
            try {
                Log.d(TAG, "Publishing audio file");
                MqttMessage message = new MqttMessage();
                message.setPayload(getPayload(timestamp, path, filename));
                message.setQos(1);
                message.setRetained(false);
                mqttClient.publish(topic, message);
            } catch (MqttException e) {
                Log.e(TAG, "Could not publish message: " + e.toString());
            }
        }

        private byte[] getPayload(long timestamp, String path, String filename) {
            Payload payload = new Payload();
            payload.setTimestamp(timestamp);
            File file = new File(path + "/" + filename);
            try {
                byte[] bytes = loadFile(file);
                String encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT);
                payload.addMetric("path", path);
                payload.addMetric("filename", filename);
                payload.addMetric("channels", new Integer(1));
                payload.addMetric("sample_rate", new Integer(44100));
                payload.addMetric("sample_size", new Integer(16));
                payload.addMetric("compressed_audio_data", encoded);
            } catch (IOException e) {
                Log.e(TAG, "Could not read audio file");
            }
            return payload.toJson().getBytes(StandardCharsets.UTF_8);
        }

        private byte[] loadFile(File file) throws IOException {
            InputStream is = new FileInputStream(file);

            long length = file.length();
            if (length > Integer.MAX_VALUE) {
                throw new RuntimeException("File is too large");
            }
            byte[] bytes = new byte[(int)length];

            int offset = 0;
            int numRead = 0;
            while (offset < bytes.length
                    && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
                offset += numRead;
            }

            if (offset < bytes.length) {
                throw new IOException("Could not completely read file "+file.getName());
            }

            is.close();
            return bytes;
        }
    }
}
