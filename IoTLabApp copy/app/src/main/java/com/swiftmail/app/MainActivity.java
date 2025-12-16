package com.swiftmail.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

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

        // Initial UI text
        updateDoorStatus();
        updateMailStatus();

        // Door button
        btnToggleDoor.setOnClickListener(v -> {
            isDoorOpen = !isDoorOpen;
            updateDoorStatus();
        });

        // Mail update button
        btnUpdateMail.setOnClickListener(v -> {
            // Toggle mail status for testing
            hasNewMail = !hasNewMail;

            // Update "Mail status"
            updateMailStatus();

            // Only update "Last delivery" when there is new mail
            if (hasNewMail) {
                tvLastDeliveryValue.setText(getCurrentTime());
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
}