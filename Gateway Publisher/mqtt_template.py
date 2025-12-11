# Imports for MQTT
import time
import datetime
import paho.mqtt.client as mqtt
import paho.mqtt.publish as publish

# Using decimal to round the value for lux :)
from decimal import Decimal

# Imports for sensor
import board
import busio

# Uncomment the correct sensor 
#import adafruit_vcnl4010 	# Proximity sensor
#import adafruit_tsl2591 	# High range lux sensor

 
# Initialize I2C bus and sensor.
i2c = busio.I2C(board.SCL, board.SDA)

# Uncomment your current sensor :)
#sensor = adafruit_vcnl4010.VCNL4010(i2c)	# Proximity
#sensor = adafruit_tsl2591.TSL2591(i2c)		# High range lux sensor

# Set MQTT broker and topic
broker = "SET-BROKER-HERE"	# Broker 

pub_topic = "ADD-TOPIC-HERE"  # send messages to this topic


############### MQTT section ##################

# when connecting to mqtt do this;
def on_connect(client, userdata, flags, rc):
	if rc==0:
		print("Connection established. Code: "+str(rc))
	else:
		print("Connection failed. Code: " + str(rc))
		
def on_publish(client, userdata, mid):
    print("Published: " + str(mid))
	
def on_disconnect(client, userdata, rc):
	if rc != 0:
		print ("Unexpected disonnection. Code: ", str(rc))
	else:
		print("Disconnected. Code: " + str(rc))
	
def on_log(client, userdata, level, buf):		# Message is in buf
    print("MQTT Log: " + str(buf))

	
############### Sensor section ##################	
def get_lux():
	lux = sensor.lux
	lux_value = round(Decimal(lux), 3) 	# Rounds the lux value to 3 decimals, and prints it
	print('Total light: {0} lux'.format(lux_value))
	return lux_value
	
def get_proximity():
	proximity = sensor.proximity # The higher the value, object closer to sensor
	print('Proximity: {0}'.format(proximity))
	return proximity
	
	
	
# Connect functions for MQTT
client = mqtt.Client()
client.on_connect = on_connect
client.on_disconnect = on_disconnect
client.on_publish = on_publish
client.on_log = on_log

# Connect to MQTT 
print("Attempting to connect to broker " + broker)
client.connect(broker)	# Broker address, port and keepalive (maximum period in seconds allowed between communications with the broker)
client.loop_start()


# Loop that publishes message
while True:
	data_to_send = "CALL-SENSOR-FUNCTION"	# Here, call the correct function from the sensor section depending on sensor
	client.publish(pub_topic, str(data_to_send))
	time.sleep(2.0)	# Set delay