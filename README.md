# GROUP 4_10 SOURCE CODE

## IoT Smart Mail-Delivery Box Alert System - ‘Swift Mail’

Smart Mailbox Android App (MQTT + Raspberry Pi)

SwiftMail is an Android application that monitors a smart mailbox using MQTT.
Features
- Door open / closed detection (lux sensor)
- New mail detection (proximity sensor)
- Push notifications on state changes
- MQTT-based communication
- Real-time UI updates

Sensor data is published by a Raspberry Pi (IoT device) and consumed by the Android app in real time.

# System Architecture -
[Raspberry Pi]
  ├── Lux Sensor       → swiftmail/raw/lux
  ├── Proximity Sensor → swiftmail/raw/proximity
  └── MQTT Client
           ▲
           │
     [MQTT Broker (test.mosquitto.org:1883)]
           │
           ▼
[Android App - SwiftMail]

The app subscribes to sensor data and updates UI + notifications automatically.

## MQTT Configuration -
The public broker used in this project: tcp://test.mosquitto.org:1883
Subscribed Topics: swiftmail/raw/sensors	

Sensor Logic & Configuration
Lux → Door Status
lux > 18 → Door OPEN
lux ≤ 18 → Door CLOSED

Proximity → Mail Status
proximity > 8000 → New mail detected

# Main Component of the application -
MainActivity.java

## Responsible for:
- MQTT connection & subscription
- Receiving sensor values
- Updating UI
- Triggering notifications
- Update recent status via buttons

## Data Flow:
1. App receives message in messageArrived()
2. Values are parsed and routed to:
    - onSensorUpdateLux()
    - onSensorUpdateProximity()
3. UI updates and notifications are triggered if state changes

## UI Elements:
Door Status	- Displays OPEN / CLOSED
Mail Status	- Displays NEW MAIL / NO MAIL
Last Delivery - Timestamp of last mail
Refresh Buttons	- Refresh the most recent sensor updates

## Notifications:
Notifications are sent only on real state changes in:
- Door opens or closes
- New mail arrives

## Testing:
1. Start MQTT broker (connected to test.mosquitto.org:1883)
2. Start Raspberry Pi publisher (Python script: swiftmail_script.py)
3. Launch Android app
4. Verify Logcat:
    - Message arrived | Topic: swiftmail/raw/lux | Payload: 23.5
    - Message arrived | Topic: swiftmail/raw/proximity | Payload: 9000

5. Verify UI and notifications update correctly
