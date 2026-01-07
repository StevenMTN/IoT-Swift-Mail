import time
import datetime
import paho.mqtt.client as mqtt
from decimal import Decimal

import board
import busio
import adafruit_vcnl4010
import adafruit_tsl2591


# SENSOR THRESHOLDS
LUX_OPEN_THRESHOLD = 18
PROX_NEW_MAIL_THRESHOLD = 8000


# MQTT CONFIG
broker = "test.mosquitto.org"
BASE_TOPIC = "swiftmail"


# I2C + SENSORS
i2c = busio.I2C(board.SCL, board.SDA)
lux_sensor = adafruit_tsl2591.TSL2591(i2c)
prox_sensor = adafruit_vcnl4010.VCNL4010(i2c)

# MQTT CALLBACKS
def on_connect(client, userdata, flags, rc):
    print("Connected with result code:", rc)

def on_disconnect(client, userdata, rc):
    print("Disconnected with result code:", rc)

# MQTT CLIENT
client = mqtt.Client()
client.on_connect = on_connect
client.on_disconnect = on_disconnect

print("Connecting to broker:", broker)
client.connect(broker, 1883, 60)
client.loop_start()

# INITIAL STATE MEMORY
last_door_state = None
last_mail_state = None

# MAIN LOOP
while True:
    lux = round(Decimal(lux_sensor.lux), 2)
    proximity = prox_sensor.proximity

    # Publish raw sensor values
    client.publish(f"{BASE_TOPIC}/raw/lux", lux, retain=True)
    client.publish(f"{BASE_TOPIC}/raw/proximity", proximity, retain=True)

    # Detect door events
    door_state = "OPEN" if lux > LUX_OPEN_THRESHOLD else "CLOSED"

    if door_state != last_door_state:
        print("Door event:", door_state)
        client.publish(
            f"{BASE_TOPIC}/status/door",
            door_state,
            retain=True
        )
        last_door_state = door_state

    # Detect mail events
    mail_state = "NEW_MAIL" if proximity > PROX_NEW_MAIL_THRESHOLD else "NO_MAIL"

    if mail_state != last_mail_state:
        print("Mail event:", mail_state)
        client.publish(
            f"{BASE_TOPIC}/status/mail",
            mail_state,
            retain=True
        )
        last_mail_state = mail_state

    # Pinging if the device is alive (heartbeat)
    timestamp = datetime.datetime.now().isoformat()
    client.publish(
        f"{BASE_TOPIC}/heartbeat",
        timestamp,
        retain=True
    )

    time.sleep(2)