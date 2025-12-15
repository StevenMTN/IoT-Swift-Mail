# Imports for MQTT
import time
import datetime
import paho.mqtt.client as mqtt
import paho.mqtt.publish as publish
import json  # Added for JSON data formatting

# Using decimal to round the value for lux :)
from decimal import Decimal

# Imports for sensors
import board
import busio

# Import both sensors
import adafruit_tsl2591  # Proximity sensor (for distance measurement)
import adafruit_vcnl4010 # Lux sensor (for light detection)

# Initialize I2C bus
i2c = busio.I2C(board.SCL, board.SDA)

# Initialize both sensors
proximity_sensor = adafruit_vcnl4010.VCNL4010(i2c)  # Distance sensor
proximity_sensor.measurement_timing_budget = 20000  # 20ms timing budget

lux_sensor = adafruit_tsl2591.TSL2591(i2c)  # Light sensor
lux_sensor.gain = adafruit_tsl2591.GAIN_MED  
lux_sensor.integration_time = adafruit_tsl2591.INTEGRATIONTIME_200MS  

# Mailbox configuration
DEVICE_ID = "mailbox_01"  # Change this for each mailbox
PROXIMITY_MAIL_THRESHOLD_MM = 2000  # Distance in mm - adjust based on your setup
LUX_OPEN_THRESHOLD = 500  # Lux value - adjust based on your environment
PROXIMITY_TIMEOUT = 1000  # Max valid reading distance in mm

# Mailbox state tracking
mailbox_state = {
    "has_mail": False,
    "is_open": False,
    "last_mail_detected": None,
    "last_opened": None
}

# Set MQTT broker and topic
broker = "test.mosquitto.org"  # Broker address
pub_topic = f"mailbox/{DEVICE_ID}/sensors"  # Dynamic topic with device ID

############### MQTT section ##################
# when connecting to mqtt do this;
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Connection established. Code: " + str(rc))
    else:
        print("Connection failed. Code: " + str(rc))

def on_publish(client, userdata, mid):
    print("Published message ID: " + str(mid))

def on_disconnect(client, userdata, rc):
    if rc != 0:
        print("Unexpected disconnection. Code: ", str(rc))
    else:
        print("Disconnected. Code: " + str(rc))

def on_log(client, userdata, level, buf):  # Message is in buf
    print("MQTT Log: " + str(buf))

############### Sensor section ##################
def get_lux():
    try:
        lux = lux_sensor.lux
        if lux is not None:
            lux_value = round(float(lux), 2)
            print(f'Light level: {lux_value} lux')
            return lux_value
        return None
    except Exception as e:
        print(f"Error reading lux sensor: {e}")
        return None

def get_proximity():
    """Read VCNL4010 proximity (relative value)"""
    try:
        proximity = proximity_sensor.proximity
        print(f'Proximity value: {proximity}')
        return proximity
    except Exception as e:
        print(f"Error reading proximity sensor: {e}")
        return None

def determine_mailbox_state(proximity_data, lux_data):
    """Determine mailbox state based on sensor readings"""
    global mailbox_state
    state_changed = False
    
    # Create a copy of current state for comparison
    new_state = mailbox_state.copy()
    
    # Check for mail using proximity sensor
    if proximity_data is not None:
        has_mail_now = proximity_data > PROXIMITY_MAIL_THRESHOLD_MM
        
        if has_mail_now != mailbox_state["has_mail"]:
            new_state["has_mail"] = has_mail_now
            if has_mail_now:
                new_state["last_mail_detected"] = datetime.datetime.now().isoformat()
                print(f"MAIL DETECTED! Distance: {proximity_data}mm")
            else:
                print("Mail removed or taken")
            state_changed = True
    
    # Check if mailbox is open using lux sensor
    if lux_data is not None:
        is_open_now = lux_data > LUX_OPEN_THRESHOLD
        
        if is_open_now != mailbox_state["is_open"]:
            new_state["is_open"] = is_open_now
            if is_open_now:
                new_state["last_opened"] = datetime.datetime.now().isoformat()
                print(f"MAILBOX OPENED! Light level: {lux_data} lux")
            else:
                print("Mailbox closed")
            state_changed = True
    
    # Update global state
    mailbox_state.update(new_state)
    
    return state_changed

def collect_sensor_data():
    """Collect data from both sensors and package for MQTT"""
    # Read sensor data
    proximity = get_proximity()
    lux = get_lux()
    
    # Determine mailbox state
    state_changed = determine_mailbox_state(proximity, lux)
    
    # Prepare data payload
    data_to_send = {
        "device_id": DEVICE_ID,
        "timestamp": datetime.datetime.now().isoformat(),
        "proximity_mm": proximity,
        "lux": float(lux) if lux is not None else None,
        "has_mail": mailbox_state["has_mail"],
        "is_open": mailbox_state["is_open"],
        "state_changed": state_changed,
        "last_mail_detected": mailbox_state["last_mail_detected"],
        "last_opened": mailbox_state["last_opened"],
        "thresholds": {
            "proximity_mail_threshold_mm": PROXIMITY_MAIL_THRESHOLD_MM,
            "lux_open_threshold": LUX_OPEN_THRESHOLD
        }
    }
    
    return data_to_send

# Connect functions for MQTT
client = mqtt.Client(client_id=DEVICE_ID)
client.on_connect = on_connect
client.on_disconnect = on_disconnect
client.on_publish = on_publish
client.on_log = on_log

# Connect to MQTT 
print("Attempting to connect to broker " + broker)
try:
    client.connect(broker, keepalive=60)  # Broker address and keepalive
    client.loop_start()
    print(f"Connected! Monitoring mailbox {DEVICE_ID}")
    print(f"Proximity threshold: {PROXIMITY_MAIL_THRESHOLD_MM}mm (below = mail)")
    print(f"Lux threshold: {LUX_OPEN_THRESHOLD} (above = open)")
    print("-" * 40)
except Exception as e:
    print(f"Failed to connect to broker: {e}")
    exit(1)

# Loop that publishes message
try:
    while True:
        # Collect sensor data with mailbox state
        sensor_data = collect_sensor_data()
        
        # Convert to JSON string for MQTT
        data_json = json.dumps(sensor_data, default=str)
        
        # Publish to MQTT
        client.publish(pub_topic, data_json, qos=1)
        
        # Print status
        status = f"Mail: {'YES' if mailbox_state['has_mail'] else 'NO'}, "
        status += f"Open: {'YES' if mailbox_state['is_open'] else 'NO'}"
        print(status)
        
        time.sleep(2.0)  # Set delay between readings
        
except KeyboardInterrupt:
    print("\nStopping mailbox monitor...")
    client.loop_stop()
    client.disconnect()
    print("Disconnected from MQTT broker")
except Exception as e:
    print(f"Error in main loop: {e}")
    client.loop_stop()
    client.disconnect()