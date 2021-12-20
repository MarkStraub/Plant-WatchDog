/*
   BLE setup and callbacks are based on
       https://github.com/tamberg/mse-tsm-mobcom/blob/master/06/Arduino/nRF52840Sense_UartBlePeripheral/nRF52840Sense_UartBlePeripheral.ino
       licensed under MIT License
   SHT31 setup and value reading is based on
       https://github.com/tamberg/mse-tsm-mobcom/blob/master/06/Arduino/nRF52840Sense_HygrometerBlePeripheral/nRF52840Sense_HygrometerBlePeripheral.ino
       licensed under MIT Licens
   NeoPixel setup and usage based on
       https://github.com/adafruit/Adafruit_NeoPixel
       licensed under GNU Lesser General Public License
*/

#include "Adafruit_TinyUSB.h"; // Fix https://github.com/adafruit/Adafruit_nRF52_Arduino/issues/653
#include "Adafruit_SHT31.h"
#include <Adafruit_NeoPixel.h>
#include <bluefruit.h>

#define MOI_PIN   14 // pin of the moisture sensor
#define MOIS_DRY 575 // measured value air
#define MOIS_WET 350 // measured value water

#define PIX_PIN    9 // pin of the NeoPixel led strip
#define NUMPIXELS 10 // number of NeoPixels on the strip

BLEBas batteryService;

// Service UUID:  AAD50001-DE89-4B63-9486-975DAFAAAEBC
// Temperature:   AAD50002-DE89-4B63-9486-975DAFAAAEBC
// Humidity:      AAD50003-DE89-4B63-9486-975DAFAAAEBC
// Moistur:       AAD50004-DE89-4B63-9486-975DAFAAAEBC
uint8_t const plantWatchDogServiceUuid[]            = { 0xBC, 0xAE, 0xAA, 0xAF, 0x5D, 0x97, 0x86, 0x94, 0x63, 0x4B, 0x89, 0xDE, 0x01, 0x00, 0xD5, 0xAA };
uint8_t const temperatureNotifyCharacteristicUuid[] = { 0xBC, 0xAE, 0xAA, 0xAF, 0x5D, 0x97, 0x86, 0x94, 0x63, 0x4B, 0x89, 0xDE, 0x02, 0x00, 0xD5, 0xAA };
uint8_t const humidityNotifyCharacteristicUuid[]    = { 0xBC, 0xAE, 0xAA, 0xAF, 0x5D, 0x97, 0x86, 0x94, 0x63, 0x4B, 0x89, 0xDE, 0x03, 0x00, 0xD5, 0xAA };
uint8_t const moistureNotifyCharacteristicUuid[]    = { 0xBC, 0xAE, 0xAA, 0xAF, 0x5D, 0x97, 0x86, 0x94, 0x63, 0x4B, 0x89, 0xDE, 0x04, 0x00, 0xD5, 0xAA };

// Temperature Min: AAD50005-DE89-4B63-9486-975DAFAAAEBC
// Temperature Max: AAD50006-DE89-4B63-9486-975DAFAAAEBC
// Humidity Min:    AAD50007-DE89-4B63-9486-975DAFAAAEBC
// Humidity Max:    AAD50008-DE89-4B63-9486-975DAFAAAEBC
// Moistur Min:     AAD50009-DE89-4B63-9486-975DAFAAAEBC
// Moistur Max:     AAD500A0-DE89-4B63-9486-975DAFAAAEBC
uint8_t const tempWriteMinCharacteristicUuid[] = { 0xBC, 0xAE, 0xAA, 0xAF, 0x5D, 0x97, 0x86, 0x94, 0x63, 0x4B, 0x89, 0xDE, 0x05, 0x00, 0xD5, 0xAA };
uint8_t const tempWriteMaxCharacteristicUuid[] = { 0xBC, 0xAE, 0xAA, 0xAF, 0x5D, 0x97, 0x86, 0x94, 0x63, 0x4B, 0x89, 0xDE, 0x06, 0x00, 0xD5, 0xAA };
uint8_t const humiWriteMinCharacteristicUuid[] = { 0xBC, 0xAE, 0xAA, 0xAF, 0x5D, 0x97, 0x86, 0x94, 0x63, 0x4B, 0x89, 0xDE, 0x07, 0x00, 0xD5, 0xAA };
uint8_t const humiWriteMaxCharacteristicUuid[] = { 0xBC, 0xAE, 0xAA, 0xAF, 0x5D, 0x97, 0x86, 0x94, 0x63, 0x4B, 0x89, 0xDE, 0x08, 0x00, 0xD5, 0xAA };
uint8_t const moisWriteMinCharacteristicUuid[] = { 0xBC, 0xAE, 0xAA, 0xAF, 0x5D, 0x97, 0x86, 0x94, 0x63, 0x4B, 0x89, 0xDE, 0x09, 0x00, 0xD5, 0xAA };
uint8_t const moisWriteMaxCharacteristicUuid[] = { 0xBC, 0xAE, 0xAA, 0xAF, 0x5D, 0x97, 0x86, 0x94, 0x63, 0x4B, 0x89, 0xDE, 0xA0, 0x00, 0xD5, 0xAA };

// Service & Characteristics
BLEService plantWatchDogService = BLEService(plantWatchDogServiceUuid);
BLECharacteristic tempNotifyCharacteristic = BLECharacteristic(temperatureNotifyCharacteristicUuid);
BLECharacteristic humiNotifyCharacteristic = BLECharacteristic(humidityNotifyCharacteristicUuid);
BLECharacteristic moisNotifyCharacteristic = BLECharacteristic(moistureNotifyCharacteristicUuid);
BLECharacteristic tempMinCharacteristic    = BLECharacteristic(tempWriteMinCharacteristicUuid);
BLECharacteristic tempMaxCharacteristic    = BLECharacteristic(tempWriteMaxCharacteristicUuid);
BLECharacteristic humiMinCharacteristic    = BLECharacteristic(humiWriteMinCharacteristicUuid);
BLECharacteristic humiMaxCharacteristic    = BLECharacteristic(humiWriteMaxCharacteristicUuid);
BLECharacteristic moisMinCharacteristic    = BLECharacteristic(moisWriteMinCharacteristicUuid);
BLECharacteristic moisMaxCharacteristic    = BLECharacteristic(moisWriteMaxCharacteristicUuid);

uint16_t mtu;                                                       // maximum transmission unit

Adafruit_SHT31 sht31 = Adafruit_SHT31();                            // humidity sensor

Adafruit_NeoPixel pixels(NUMPIXELS, PIX_PIN, NEO_GRB + NEO_KHZ800); // NeoPixel

// NeoPixel colors for different measurements
int tempColor = pixels.Color(255, 0, 0);
int humiColor = pixels.Color(0, 0, 255);
int moisColor = pixels.Color(0, 255, 0);
int off = pixels.Color(0, 0, 0);

// initial sensor values
int tempVal = 0;
int humiVal = 0;
int moisVal = 0;

// configured min & max values of the measurements
int tempMinVal = -1000;
int tempMaxVal = 6000;
int humiMinVal = 0;
int humiMaxVal = 10000;
int moisMinVal = 0;
int moisMaxVal = 10000;

// first color mode
// 0 = temp; 1 = humi; 2 = mois;
int colorMode = 0;

// helper array for sensor data
uint8_t sensorData[2];

// loop counter for delay
int loopCounter = 0;

// converts a uint8_t of length 2 into an integer
int readIntegerFromUintArray(uint8_t* data, uint16_t len) {
  if (len == 2) {
    int d1 = data[0] << 0;
    int d2 = data[1] << 8;
    return d1 + d2;
  }
  Serial.println("Invalid data received.");
  return 0;
}

// called whenever temperature configuration data is written and updates the configuration field
void tempWriteCallback(uint16_t connHandle, BLECharacteristic* characteristic, uint8_t* data, uint16_t len) {
  if (characteristic->uuid == tempMinCharacteristic.uuid) {
    tempMinVal = readIntegerFromUintArray(data, len);
    Serial.print("tempMinVal: ");
    Serial.println(tempMinVal);
  } else if (characteristic->uuid == tempMaxCharacteristic.uuid) {
    tempMaxVal = readIntegerFromUintArray(data, len);
    Serial.print("tempMaxVal: ");
    Serial.println(tempMaxVal);
  } else {
    Serial.println("Unknown temperature UUID.");
  }
}

// called whenever humidity configuration data is written and updates the configuration field
void humiWriteCallback(uint16_t connHandle, BLECharacteristic* characteristic, uint8_t* data, uint16_t len) {
  if (characteristic->uuid == humiMinCharacteristic.uuid) {
    humiMinVal = readIntegerFromUintArray(data, len);
    Serial.print("humiMinVal: ");
    Serial.println(humiMinVal);
  } else if (characteristic->uuid == humiMaxCharacteristic.uuid) {
    humiMaxVal = readIntegerFromUintArray(data, len);
    Serial.print("humiMaxVal: ");
    Serial.println(humiMaxVal);
  } else {
    Serial.println("Unknown humidity UUID.");
  }
}

// called whenever moisture configuration data is written and updates the configuration field
void moisWriteCallback(uint16_t connHandle, BLECharacteristic* characteristic, uint8_t* data, uint16_t len) {
  if (characteristic->uuid == moisMinCharacteristic.uuid) {
    moisMinVal = readIntegerFromUintArray(data, len);
    Serial.print("moisMinVal: ");
    Serial.println(moisMinVal);
  } else if (characteristic->uuid == moisMaxCharacteristic.uuid) {
    moisMaxVal = readIntegerFromUintArray(data, len);
    Serial.print("moisMaxVal: ");
    Serial.println(moisMaxVal);
  } else {
    Serial.println("Unknown moisture UUID.");
  }
}

// turn off all NeoPixels
void turnOffPixels() {
  setColorPixels(off, -1); // turn off all pixels
}

// called when device disconnected
// pixels are turned off and  values are reset
void disconnectedCallback(uint16_t connHandle, uint8_t reason) {
  Serial.print(connHandle);
  Serial.print(" disconnected, reason = ");
  Serial.println(reason); // see https://github.com/adafruit/Adafruit_nRF52_Arduino
  turnOffPixels();
  tempVal = 0;
  humiVal = 0;
  moisVal = 0;
}

void setupBluefruit() {
  Bluefruit.begin();
  Bluefruit.setName("PlantWatchDog");
  Bluefruit.Periph.setDisconnectCallback(disconnectedCallback);
  mtu = Bluefruit.getMaxMtu(BLE_GAP_ROLE_PERIPH);
}

void setupBateryService() {
  batteryService.begin();
  batteryService.write(100);
}

void setupPlantWatchDogService() {
  plantWatchDogService.begin();

  tempNotifyCharacteristic.setProperties(CHR_PROPS_NOTIFY);
  tempNotifyCharacteristic.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  tempNotifyCharacteristic.setMaxLen(mtu);
  tempNotifyCharacteristic.begin();

  humiNotifyCharacteristic.setProperties(CHR_PROPS_NOTIFY);
  humiNotifyCharacteristic.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  humiNotifyCharacteristic.setMaxLen(mtu);
  humiNotifyCharacteristic.begin();

  moisNotifyCharacteristic.setProperties(CHR_PROPS_NOTIFY);
  moisNotifyCharacteristic.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  moisNotifyCharacteristic.setMaxLen(mtu);
  moisNotifyCharacteristic.begin();

  tempMinCharacteristic.setProperties(CHR_PROPS_WRITE | CHR_PROPS_WRITE_WO_RESP);
  tempMinCharacteristic.setPermission(SECMODE_NO_ACCESS, SECMODE_OPEN);
  tempMinCharacteristic.setMaxLen(mtu);
  tempMinCharacteristic.setWriteCallback(tempWriteCallback, true);
  tempMinCharacteristic.begin();

  tempMaxCharacteristic.setProperties(CHR_PROPS_WRITE | CHR_PROPS_WRITE_WO_RESP);
  tempMaxCharacteristic.setPermission(SECMODE_NO_ACCESS, SECMODE_OPEN);
  tempMaxCharacteristic.setMaxLen(mtu);
  tempMaxCharacteristic.setWriteCallback(tempWriteCallback, true);
  tempMaxCharacteristic.begin();

  humiMinCharacteristic.setProperties(CHR_PROPS_WRITE | CHR_PROPS_WRITE_WO_RESP);
  humiMinCharacteristic.setPermission(SECMODE_NO_ACCESS, SECMODE_OPEN);
  humiMinCharacteristic.setMaxLen(mtu);
  humiMinCharacteristic.setWriteCallback(humiWriteCallback, true);
  humiMinCharacteristic.begin();

  humiMaxCharacteristic.setProperties(CHR_PROPS_WRITE | CHR_PROPS_WRITE_WO_RESP);
  humiMaxCharacteristic.setPermission(SECMODE_NO_ACCESS, SECMODE_OPEN);
  humiMaxCharacteristic.setMaxLen(mtu);
  humiMaxCharacteristic.setWriteCallback(humiWriteCallback, true);
  humiMaxCharacteristic.begin();

  moisMinCharacteristic.setProperties(CHR_PROPS_WRITE | CHR_PROPS_WRITE_WO_RESP);
  moisMinCharacteristic.setPermission(SECMODE_NO_ACCESS, SECMODE_OPEN);
  moisMinCharacteristic.setMaxLen(mtu);
  moisMinCharacteristic.setWriteCallback(moisWriteCallback, true);
  moisMinCharacteristic.begin();

  moisMaxCharacteristic.setProperties(CHR_PROPS_WRITE | CHR_PROPS_WRITE_WO_RESP);
  moisMaxCharacteristic.setPermission(SECMODE_NO_ACCESS, SECMODE_OPEN);
  moisMaxCharacteristic.setMaxLen(mtu);
  moisMaxCharacteristic.setWriteCallback(moisWriteCallback, true);
  moisMaxCharacteristic.begin();
}

void startAdvertising() {
  Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
  Bluefruit.Advertising.addTxPower();
  Bluefruit.Advertising.addService(plantWatchDogService);
  Bluefruit.Advertising.addName();

  // See https://developer.apple.com/library/content/qa/qa1931/_index.html
  const int fastModeInterval = 32;  // * 0.625 ms = 20 ms
  const int slowModeInterval = 244; // * 0.625 ms = 152.5 ms
  const int fastModeTimeout = 30;   // s
  Bluefruit.Advertising.restartOnDisconnect(true);
  Bluefruit.Advertising.setInterval(fastModeInterval, slowModeInterval);
  Bluefruit.Advertising.setFastTimeout(fastModeTimeout);
  // 0 = continue advertising after fast mode, until connected
  Bluefruit.Advertising.start(0);
  Serial.println("Advertising ...");
}

void startSHT31() {
  sht31.begin(0x44);
  sht31.heater(false);
}

void startPixels() {
  pixels.begin();
  turnOffPixels(); // turn off all pixels
  pixels.setBrightness(5); // 0 - 255
}

void setup() {
  Serial.begin(115200);
  setupBluefruit();
  setupBateryService();
  setupPlantWatchDogService();
  startSHT31();
  startPixels();
  startAdvertising();
}

// writes a float to a uint8_t[] of length 2
// returns the interger representation of the float * 100
int writeFloatToUintArray(float value, uint8_t data[]) {
  int v = value * 100.0f;
  data[0] = v >> 0;
  data[1] = v >> 8;
  return v;
}

// reads and then notifies the current temperature
void notifyTemperatureIfConnected() {
  float temp = sht31.readTemperature();
  tempVal = writeFloatToUintArray(temp, sensorData);
  if (Bluefruit.connected() && tempNotifyCharacteristic.notify(sensorData, sizeof(sensorData))) {
    Serial.print("Temperature: ");
    Serial.print(temp);
    Serial.println(" *C");
  }
}

// reads and then notifies the current humidity
void notifyHumidityIfConnected() {
  float humi = sht31.readHumidity();
  humiVal = writeFloatToUintArray(humi, sensorData);
  if (Bluefruit.connected() && humiNotifyCharacteristic.notify(sensorData, sizeof(sensorData)))  {
    Serial.print("Humidity: ");
    Serial.print(humi);
    Serial.println(" %");
  }
}

// ensures that the moisture value is within the allowed range
// this method is needed because the moisture sensor is not very precise
float getAjustedMoisture(float moi) {
  return moi < MOIS_WET ? MOIS_WET : (moi > MOIS_DRY ? MOIS_DRY : moi);
}

// reads and then notifies the current moisture
void notifyMoistureIfConnected() {
  float moiAjusted = getAjustedMoisture(analogRead(MOI_PIN));
  float moiPercent = (float) map(moiAjusted, MOIS_WET, MOIS_DRY, 10000, 0) / 100;
  moisVal = writeFloatToUintArray(moiPercent, sensorData);
  if (Bluefruit.connected() && moisNotifyCharacteristic.notify(sensorData, sizeof(sensorData)))  {
    Serial.print("Moisture: ");
    Serial.print(moiPercent);
    Serial.println(" %");
  }
}

// enable the required pixels in the desired color
void setColorPixels(int color, int value) {
  for (int i = 0; i < 10; i++) {
    if (i * 10 < value) {
      pixels.setPixelColor(i, color);
    } else {
      pixels.setPixelColor(i, off);
    }
  }
  pixels.show();
}

// updates the pixels according to the current color mode
void updatePixels() {
  int ajusted = -1; // measurement needs to be ajusted to  range [0, 1, ..., 100]
  switch (colorMode) {
    case 0:
      ajusted = map(tempVal, tempMinVal, tempMaxVal, 0, 100);
      setColorPixels(tempColor, ajusted);
      colorMode++;
      break;
    case 1:
      ajusted = map(humiVal, humiMinVal, humiMaxVal, 0, 100);
      setColorPixels(humiColor, ajusted);
      colorMode++;
      break;
    case 2:
      ajusted = map(moisVal, moisMinVal, moisMaxVal, 0, 100);
      setColorPixels(moisColor, ajusted);
      colorMode = 0;
      break;
    default:
      Serial.println("Unknown color mode.");
  }
}

void loop() {
  // only execute after 5 seconds
  if (loopCounter > 49) {
    loopCounter = 0;
    notifyTemperatureIfConnected();
    delay(50); // without delay values might be lost
    notifyHumidityIfConnected();
    delay(50); // without delay values might be lost
    notifyMoistureIfConnected();
    updatePixels();
    loopCounter = 0;
  }
  loopCounter++;
  delay(100);
}
