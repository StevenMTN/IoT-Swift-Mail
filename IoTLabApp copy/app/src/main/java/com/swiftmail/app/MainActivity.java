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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "swiftmail_alerts";
    private static final int REQ_NOTIF_PERMISSION = 1001;

    private TextView tvDoorStatusValue;
    private TextView tvMailStatus;
    private TextView tvLastDeliveryValue;

    private boolean isDoorOpen = false;
    private boolean hasNewMail = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        tvDoorStatusValue = findViewById(R.id.tv_door_status_value);
        tvMailStatus = findViewById(R.id.tv_mail_status);
        tvLastDeliveryValue = findViewById(R.id.tv_last_delivery_value);

        Button btnToggleDoor = findViewById(R.id.btn_toggle_door);
        Button btnUpdateMail = findViewById(R.id.btn_update_mail);

        // Notifications setup
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();

        // Initial UI text
        updateDoorStatus();
        updateMailStatus();

        // Door button (testing)
        btnToggleDoor.setOnClickListener(v -> {
            isDoorOpen = !isDoorOpen;
            updateDoorStatus();

            // Send a notification whenever door status changes
            String doorState = isDoorOpen ? getString(R.string.door_open) : getString(R.string.door_closed);
            showNotification(
                    "Door status changed",
                    "Mailbox door is now: " + doorState,
                    2001
            );
        });

        // Mail update button (testing)
        btnUpdateMail.setOnClickListener(v -> {
            boolean wasNewMail = hasNewMail;
            hasNewMail = !hasNewMail; // Toggle mail status for testing

            updateMailStatus();

            // Only update "Last delivery" when there is new mail
            if (hasNewMail) {
                tvLastDeliveryValue.setText(getCurrentTime());
            }

            // Only notify when it becomes "new mail delivered"
            if (!wasNewMail && hasNewMail) {
                showNotification(
                        "New mail delivered",
                        "You have received new mail.",
                        2002
                );
            }
        });
    }

    // Door status UI update
    private void updateDoorStatus() {
        if (isDoorOpen) {
            tvDoorStatusValue.setText(getString(R.string.door_open));
        } else {
            tvDoorStatusValue.setText(getString(R.string.door_closed));
        }
    }

    // Mail status UI update
    private void updateMailStatus() {
        if (hasNewMail) {
            tvMailStatus.setText(getString(R.string.mail_new));
        } else {
            tvMailStatus.setText(getString(R.string.mail_none));
        }
    }

    // Time of last delivery
    private String getCurrentTime() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date());
    }

    // Create a notification channel (Android 8.0+)
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String name = "Swift Mail Alerts";
            String description = "Notifications for door and mail events";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    // Ask for POST_NOTIFICATIONS on Android 13+ (API 33+)
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIF_PERMISSION
                );
            }
        }
    }

    // Show a notification (only if permission is granted on Android 13+)
    private void showNotification(String title, String message, int notificationId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return; // Permission not granted, do nothing
            }
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher) // Simple default icon
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(notificationId, builder.build());
    }
}