package com.swiftmail.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "swiftmail_alerts";
    private static final int REQ_NOTIF_PERMISSION = 1001;

    // MQTT Client
    private MqttAndroidClient mqttClient;
    private static final String MQTT_BROKER_URI = "tcp://test.mosquitto.org:1883";
    private static final String MQTT_TOPIC = "swiftmail/#";
    private static final int MQTT_QOS = 1;
    private static final String MQTT_LOG_TAG = "MQTT";

    // Planned sensor thresholds (ready for MQTT)
    private static final int LUX_OPEN_THRESHOLD = 18;        // Door open if > 18
    private static final int PROX_NEW_MAIL_THRESHOLD = 8000; // New mail if > 8000

    private TextView tvDoorStatusValue;
    private TextView tvMailStatus;
    private TextView tvLastDeliveryValue;

    private boolean isDoorOpen = false;
    private boolean hasNewMail = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDoorStatusValue = findViewById(R.id.tv_door_status_value);
        tvMailStatus = findViewById(R.id.tv_mail_status);
        tvLastDeliveryValue = findViewById(R.id.tv_last_delivery_value);

        Button btnToggleDoor = findViewById(R.id.btn_toggle_door);
        Button btnUpdateMail = findViewById(R.id.btn_update_mail);

        // Notification setup
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();

        // Initial UI render
        refreshUI();

        // MQTT setup
        setupMqtt();

        // Buttons = REFRESH ONLY (MQTT-ready)
        btnToggleDoor.setOnClickListener(v ->
            // TODO (MQTT): Request latest door sensor value (Placerholder currently re-render the last known value)
            refreshUI()
        );

        btnUpdateMail.setOnClickListener(v ->
            // TODO (MQTT): Request latest mail sensor value (Placerholder currently re-render the last known value)
            refreshUI()
        );
    }

    /* ============================
       MQTT SETUP
       ============================ */
    private void setupMqtt() {
        String clientId = "SwiftMail-" + System.currentTimeMillis();

        // Initialize MQTT client
        mqttClient = new MqttAndroidClient(this, MQTT_BROKER_URI, clientId);

        // Attach single callback before connecting
        mqttClient.setCallback(new MqttCallback() {

            @Override
            public void connectionLost(Throwable cause) {
                android.util.Log.e("MQTT", "Connection lost", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload()).trim();
                Log.i(MQTT_LOG_TAG, "Message arrived | Topic: " + topic + " | Payload: " + payload);

                try {
                    runOnUiThread(() -> {
                        if (topic.equals("swiftmail/raw/lux")) {
                            try {
                                float luxValue = Float.parseFloat(payload);
                                Log.i(MQTT_LOG_TAG, "Detected LUX value: " + luxValue);
                                onSensorUpdateLux(luxValue);
                            } catch (NumberFormatException e) {
                                Log.e(MQTT_LOG_TAG, "Lux payload is not a valid float", e);
                            }
                        } else if (topic.equals("swiftmail/raw/proximity")) {
                            try {
                                int proxValue = Integer.parseInt(payload);
                                Log.i(MQTT_LOG_TAG, "Detected PROXIMITY value: " + proxValue);
                                onSensorUpdateProximity(proxValue);
                            } catch (NumberFormatException e) {
                                Log.e(MQTT_LOG_TAG, "Proximity payload is not an integer", e);
                            }
                        }
                    });

                } catch (Exception e) {
                    Log.e(MQTT_LOG_TAG, "Error processing MQTT message", e);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used for subscriber-only app
            }
        });

        // Connect options
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);

        // Connect and subscribe
        try {
            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    android.util.Log.i("MQTT", "MQTT connected successfully.");

                    // Subscribe to topic after connection
                    try {
                        mqttClient.subscribe(MQTT_TOPIC, 1, null, new IMqttActionListener() {
                            @Override
                            public void onSuccess(IMqttToken asyncActionToken) {
                                android.util.Log.i("MQTT", "Subscribed to topic: " + MQTT_TOPIC);
                            }

                            @Override
                            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                android.util.Log.e("MQTT", "Subscription failed", exception);
                            }
                        });
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    android.util.Log.e("MQTT", "MQTT connection failed", exception);
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /* ============================
       SENSOR ENTRY POINTS (MQTT)
       ============================ */

    // Called when LUX sensor is connected
    public void onSensorUpdateLux(float luxValue) {
        boolean doorOpenFromLux = luxValue > LUX_OPEN_THRESHOLD;
        applyDoorState(doorOpenFromLux);
    }

    // Called when proximity sensor value is connected
    public void onSensorUpdateProximity(int proximityValue) {
        boolean newMailFromProx = proximityValue > PROX_NEW_MAIL_THRESHOLD;
        applyMailState(newMailFromProx);
    }

    /* ============================
       STATE HANDLING
       ============================ */

    private void applyDoorState(boolean newDoorOpen) {
        boolean changed = (newDoorOpen != isDoorOpen);
        isDoorOpen = newDoorOpen;

        updateDoorStatus();

        // Notify ONLY on real state change
        if (changed) {
            String doorState = isDoorOpen
                    ? getString(R.string.door_open)
                    : getString(R.string.door_closed);

            showNotification(
                    "Door status changed",
                    "Mailbox door is now: " + doorState,
                    2001
            );
        }
    }

    private void applyMailState(boolean newHasMail) {
        boolean becameNewMail = (!hasNewMail && newHasMail);
        hasNewMail = newHasMail;

        updateMailStatus();

        // Update timestamp ONLY when new mail arrives
        if (becameNewMail) {
            tvLastDeliveryValue.setText(getCurrentDateTime());

            showNotification(
                    "New mail delivered",
                    "You have received new mail.",
                    2002
            );
        }
    }

    /* ============================
       UI UPDATES
       ============================ */

    private void refreshUI() {
        updateDoorStatus();
        updateMailStatus();
    }

    private void updateDoorStatus() {
        tvDoorStatusValue.setText(
                isDoorOpen
                        ? getString(R.string.door_open)
                        : getString(R.string.door_closed)
        );
    }

    private void updateMailStatus() {
        tvMailStatus.setText(
                hasNewMail
                        ? getString(R.string.mail_new)
                        : getString(R.string.mail_none)
        );
    }

    /* ============================
       DATE + TIME
       ============================ */

    private String getCurrentDateTime() {
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());
    }

    /* ============================
       NOTIFICATIONS
       ============================ */

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Swift Mail Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for door and mail events");

            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIF_PERMISSION
                );
            }
        }
    }

    private void showNotification(String title, String message, int id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(id, builder.build());
    }
}