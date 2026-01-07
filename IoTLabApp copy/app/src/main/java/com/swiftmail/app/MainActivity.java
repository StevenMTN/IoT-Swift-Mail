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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "swiftmail_alerts";
    private static final int REQ_NOTIF_PERMISSION = 1001;

    // Planned sensor thresholds (ready for MQTT)
    private static final int LUX_OPEN_THRESHOLD = 18;        // Door open if > 18
    private static final int PROX_NEW_MAIL_THRESHOLD = 8000; // New mail if > 8000

    // MQTT configuration
    private static final String MQTT_BROKER = "tcp://test.mosquitto.org:1883";
    private static final String MQTT_TOPIC = "swiftmail/sensors";

    private MqttAndroidClient mqttClient;

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
        btnToggleDoor.setOnClickListener(v -> {
            // TODO (MQTT): Request latest door sensor value
        });

        btnUpdateMail.setOnClickListener(v -> {
            // TODO (MQTT): Request latest mail sensor value
        });
    }

    /* ============================
       MQTT SETUP
       ============================ */

    private void setupMqtt() {
        String clientId = "SwiftMail-" + System.currentTimeMillis();
        // The constructor for the replacement Paho MQTT library (hannesa2) requires an additional Ack argument
        mqttClient = new MqttAndroidClient(this, MQTT_BROKER, clientId, Ack.AUTO_ACK);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                handleIncomingMessage(message.toString());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        mqttClient.connect(options, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                mqttClient.subscribe(MQTT_TOPIC, 0);
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                exception.printStackTrace();
            }
        });
    }

    private void handleIncomingMessage(String payload) {
        // Expected format: lux.proximity
        String[] values = payload.split("\\.");

        if (values.length == 2) {
            try {
                int lux = (int) Float.parseFloat(values[0]);
                int proximity = Integer.parseInt(values[1]);

                runOnUiThread(() -> {
                    onSensorUpdateLux(lux);
                    onSensorUpdateProximity(proximity);
                });

            } catch (NumberFormatException ignored) {
            }
        }
    }

    /* ============================
       SENSOR ENTRY POINTS (MQTT)
       ============================ */

    // Called when LUX sensor is connected
    public void onSensorUpdateLux(int luxValue) {
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
