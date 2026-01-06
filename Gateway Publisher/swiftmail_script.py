# Imports for MQTT
import time
import datetime
import paho.mqtt.client as mqtt

# Using decimal to round the value for lux :)
from decimal import Decimal

# Imports for sensor
import board
import busio

# Sensors
import adafruit_vcnl4010     # Proximity sensor
import adafruit_tsl2591     # High range lux sensor

# Initialize I2C bus
i2c = busio.I2C(board.SCL, board.SDA)

# Initialize sensors
sensor = adafruit_tsl2591.TSL2591(i2c)     # Lux sensor
sensor2 = adafruit_vcnl4010.VCNL4010(i2c)  # Proximity sensor

# MQTT broker and topic
broker = "test.mosquitto.org"
pub_topic = "swiftmail/sensors"

# MQTT callbacks

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Connection established. Code:", rc)
    else:
        print("Connection failed. Code:", rc)

def on_publish(client, userdata, mid):
    print("Published message ID:", mid)

def on_disconnect(client, userdata, rc):
    print("Disconnected. Code:", rc)

# Sensor functions

def get_lux():
    lux = sensor.lux
    lux_value = round(Decimal(lux), 3)
    print(f"Lux: {lux_value}")
    return lux_value

def get_proximity():
    proximity = sensor2.proximity
    print(f"Proximity: {proximity}")
    return proximity

# MQTT setup

client = mqtt.Client()
client.on_connect = on_connect
client.on_disconnect = on_disconnect
client.on_publish = on_publish

print("Connecting to broker:", broker)
client.connect(broker)
client.loop_start()

# Publish loop

while True:
    lux = get_lux()
    proximity = get_proximity()

    # Combine into one message
    data_to_send = f"{lux}.{proximity}"

    print("Sending:", data_to_send)
    client.publish(pub_topic, data_to_send)

    time.sleep(2)
