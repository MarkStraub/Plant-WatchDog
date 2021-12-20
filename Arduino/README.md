# Plant WatchDog Arduino Program

## Prerequisites
* [Feather nRF52840 Sense](https://github.com/tamberg/mse-tsm-mobcom/wiki/Feather-nRF52840-Sense) device
* Onboard Temperature Sensor via I2C
* Onboard Humidity Sensor via I2C
* External [Grove Shield for Particle Mesh](https://www.seeedstudio.com/Grove-Shield-for-Particle-Mesh-p-4080.html)
* External [Capacitive Moisture Sensor](https://www.seeedstudio.com/Grove-Capacitive-Moisture-Sensor-Corrosion-Resistant.html) on pin 14 (Grove A0)
* External [RGB LED Stick](https://www.seeedstudio.com/Grove-RGB-LED-Stick-10-WS2813-Mini.html) on pin 9 (Grove D4)

## Adding libraries
* Arduino IDE > Sketch > Include Library > Manage Libraries ...
* Search for "Adafruit SHT31 Library" > Install
* Search for "Adafruit NeoPixel" > Install

## Uploading the program
* Connect the Arduino board via USB
* Arduino IDE > Tools > Board: Adafruit Feather nRF52840 Sense
* Arduino IDE > Tools > Port: Adafruit Feather nRF52840 Sense
* Arduino IDE > Upload
