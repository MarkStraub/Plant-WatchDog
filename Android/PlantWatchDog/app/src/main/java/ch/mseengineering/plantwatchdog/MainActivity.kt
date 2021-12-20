package ch.mseengineering.plantwatchdog

import android.Manifest
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import ch.mseengineering.plantwatchdog.databinding.ActivityMainBinding
import ch.mseengineering.plantwatchdog.services.Firestore
import java.util.*
import kotlin.collections.ArrayList

/*
Bluetooth connection and data exchange based on
    https://github.com/tamberg/mse-tsm-mobcom/blob/master/07/Android/MyBleCentralApp/app/src/main/java/org/tamberg/myblecentralapp/MainActivity.java
    licensed under MIT
 */

class MainActivity : AppCompatActivity() {

    private val SCAN_PERIOD_MS: Long = 10000
    private val watchDogServiceUuid =
        ParcelUuid(UUID.fromString("AAD50001-DE89-4B63-9486-975DAFAAAEBC"))
    private val tempCharUuid = UUID.fromString("AAD50002-DE89-4B63-9486-975DAFAAAEBC")
    private val humiCharUuid = UUID.fromString("AAD50003-DE89-4B63-9486-975DAFAAAEBC")
    private val moisCharUuid = UUID.fromString("AAD50004-DE89-4B63-9486-975DAFAAAEBC")
    private val tempWriteMinCharUuid = UUID.fromString("AAD50005-DE89-4B63-9486-975DAFAAAEBC")
    private val tempWriteMaxCharUuid = UUID.fromString("AAD50006-DE89-4B63-9486-975DAFAAAEBC")
    private val humiWriteMinCharUuid = UUID.fromString("AAD50007-DE89-4B63-9486-975DAFAAAEBC")
    private val humiWriteMaxCharUuid = UUID.fromString("AAD50008-DE89-4B63-9486-975DAFAAAEBC")
    private val moisWriteMinCharUuid = UUID.fromString("AAD50009-DE89-4B63-9486-975DAFAAAEBC")
    private val moisWriteMaxCharUuid = UUID.fromString("AAD500A0-DE89-4B63-9486-975DAFAAAEBC")

    private lateinit var firestore: Firestore

    // callback for the watchdog scanning process
    private lateinit var watchDogScanCallback: WatchDogScanCallback

    // callback for the watchdog connection processs
    private lateinit var watchDogConnectionCallback: WatchDogConnectionCallback
    private lateinit var mGatt: BluetoothGatt
    private lateinit var mScanner: BluetoothLeScanner
    private val mHandler = Handler(Looper.getMainLooper())

    // queue for writing gatt descriptors
    private val mDescriptorWriteQueue: Queue<BluetoothGattDescriptor> = LinkedList()

    // queue for writing values to the device
    private val mBleCharacteristicQueue: Queue<BluetoothGattCharacteristic> = LinkedList()

    // list of found bluetooth devices in the scanning process
    private val devices: MutableList<BluetoothDevice> = ArrayList()

    private lateinit var binding: ActivityMainBinding

    // the connection status
    var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = Firestore()

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // passing each menu id as a set of ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    // starts the scanning process and updates the available watch dog list
    fun getWatchDogDeviceList(watchDogScanCallback: WatchDogScanCallback) {
        this.watchDogScanCallback = watchDogScanCallback
        // ensure that the device has bluetooth
        if (this.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            val bleManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bleAdapter = bleManager.adapter
            // check if bluetooth is enabled
            if (bleAdapter.isEnabled) {
                mScanner = bleAdapter.bluetoothLeScanner
                val permission = Manifest.permission.ACCESS_FINE_LOCATION
                val checkResult = ActivityCompat.checkSelfPermission(this, permission)
                // check if bluetooth permission is granted
                // if the permission is not granted, create and launch the permission intent
                if (checkResult != PackageManager.PERMISSION_GRANTED) {
                    permissionResultAction.launch(permission)
                } else {
                    // if permission is granted, start the scanning process
                    scanForWatchDogs()
                }
            } else {
                // if bluetooth is not enabled, start an enable intent
                val enableBleIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (enableBleIntent.resolveActivity(this.packageManager) != null) {
                    getBleEnableAction.launch(enableBleIntent)
                }
            }
        } else {
            Toast.makeText(this, R.string.no_bluetooth, Toast.LENGTH_LONG).show()
            watchDogScanCallback.onReject()
        }
    }

    // handle enable intent
    private val getBleEnableAction =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // if the intent result is successful, restart the scanning process
            if (it.resultCode == RESULT_OK) {
                getWatchDogDeviceList(watchDogScanCallback)
            } else {
                Toast.makeText(this, R.string.bluetooth_not_enabled, Toast.LENGTH_LONG).show()
                watchDogScanCallback.onReject()
            }
        }

    // handle permission intent
    private val permissionResultAction =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // if the permission was granted, restart the scanning process
            if (it) {
                getWatchDogDeviceList(watchDogScanCallback)
            } else {
                Toast.makeText(this, R.string.fine_location_denied, Toast.LENGTH_LONG)
                watchDogScanCallback.onReject()
            }
        }

    // scann for available bluetooth devices which offer the watch dog service
    private fun scanForWatchDogs() {
        val filters: MutableList<ScanFilter> = ArrayList()
        filters.add(ScanFilter.Builder().setServiceUuid(watchDogServiceUuid).build())
        val settings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        // search for SCAN_PERIOD_MS milliseconds before stop scanning and returning devices
        mHandler.postDelayed({
            mScanner.stopScan(mScanCallback)
            watchDogScanCallback.onSuccess(devices)
        }, SCAN_PERIOD_MS)
        mScanner.startScan(filters, settings, mScanCallback)
    }

    // handle device scanning result
    private val mScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            // add the device if it has not already been added
            if (!devices.contains(result.device)) {
                devices.add(result.device)
            }
        }
    }

    // establish a connection with the selected bluetooth device
    fun connect(device: BluetoothDevice, watchDogConnectionCallback: WatchDogConnectionCallback) {
        // store the callback so it can be executed later on
        this.watchDogConnectionCallback = watchDogConnectionCallback
        device.connectGatt(this, false, mGattCallback)
    }

    // handle the gatt callback callbacks
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            // if a connection was established, start service discovery and set the gatt variable
            // else change the connection state to false and execute the onConnectionChanged() callback
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt!!.discoverServices()
                mGatt = gatt!!
            } else {
                isConnected = false
                watchDogConnectionCallback.onConnectionChanged()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            // if the connection is established, update the connection state and execute the
            // onConnectionChanged() callback
            if (status == GATT_SUCCESS && gatt != null) {
                isConnected = true
                watchDogConnectionCallback.onConnectionChanged()
                // send the current range settings to the device before starting to listen
                // to the devices measurement notifications
                sendSettingsOnConnect { subscribeToNotifications() }
            }
        }

        // whenever data is received from the device, write it to the database
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            val uuid = characteristic!!.uuid
            val formatType = BluetoothGattCharacteristic.FORMAT_UINT16
            // read the value according to the format
            val value = characteristic.getIntValue(formatType, 0)
            if (value != null) {
                // convert the read value to a double
                val measurement: Double = if (value == 0) 0.0 else value / 100.0
                when (uuid) {
                    tempCharUuid -> firestore.setTemperatureData(measurement)
                    humiCharUuid -> firestore.setHumidityData(measurement)
                    moisCharUuid -> firestore.setMoistureData(measurement)
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (status == GATT_SUCCESS) {
                val queueHead = mDescriptorWriteQueue.poll()
                // will call the function as long as there is anything in the queue
                // gatt?.writeDescriptor() will execute onDescriptorWrite()
                if (queueHead != null) gatt?.writeDescriptor(queueHead)
            }
        }
    }

    // subscribe to all temperature, humidity and moisture notifications
    private fun subscribeToNotifications() {
        val service = mGatt.getService(watchDogServiceUuid.uuid)
        val tempCharacteristic = service.getCharacteristic(tempCharUuid)
        val tempRegistered = mGatt.setCharacteristicNotification(tempCharacteristic, true)
        if (tempRegistered) {
            val uuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            val descriptor: BluetoothGattDescriptor = tempCharacteristic.getDescriptor(uuid)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            mDescriptorWriteQueue.add(descriptor)
        }
        val humiCharacteristic = service.getCharacteristic(humiCharUuid)
        val humiRegistered = mGatt.setCharacteristicNotification(humiCharacteristic, true)
        if (humiRegistered) {
            val uuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            val descriptor: BluetoothGattDescriptor = humiCharacteristic.getDescriptor(uuid)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            mDescriptorWriteQueue.add(descriptor)
        }
        val moisCharacteristic = service.getCharacteristic(moisCharUuid)
        val moisRegistered = mGatt.setCharacteristicNotification(moisCharacteristic, true)
        if (moisRegistered) {
            val uuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            val descriptor: BluetoothGattDescriptor = moisCharacteristic.getDescriptor(uuid)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            mDescriptorWriteQueue.add(descriptor)
        }

        // write the first descriptor and trigger the write queue
        val queueHead = mDescriptorWriteQueue.poll()
        if (queueHead != null) mGatt.writeDescriptor(queueHead)
    }

    // add temperature slider values to the write queue
    fun addTemperatureToWriteQueue(values: List<Float>) {
        addWriteCharacteristicToQueue(tempWriteMinCharUuid, values[0])
        addWriteCharacteristicToQueue(tempWriteMaxCharUuid, values[1])
    }

    // add humidity slider values to the write queue
    fun addHumidityToWriteQueue(values: List<Float>) {
        addWriteCharacteristicToQueue(humiWriteMinCharUuid, values[0])
        addWriteCharacteristicToQueue(humiWriteMaxCharUuid, values[1])
    }

    // add temperature slider values to the write queue
    fun addMoistureToWriteQueue(values: List<Float>) {
        addWriteCharacteristicToQueue(moisWriteMinCharUuid, values[0])
        addWriteCharacteristicToQueue(moisWriteMaxCharUuid, values[1])
    }

    // helper function which creates and adds a write characteristic to the queue
    private fun addWriteCharacteristicToQueue(writeUuid: UUID, value: Float) {
        if (isConnected) {
            val service = mGatt.getService(watchDogServiceUuid.uuid)
            if (service != null) {
                val characteristic = service.getCharacteristic(writeUuid)
                if (characteristic != null) {
                    characteristic.setValue(
                        (value * 100).toInt(),
                        BluetoothGattCharacteristic.FORMAT_UINT16,
                        0
                    )
                    mBleCharacteristicQueue.add(characteristic)
                }
            }
        }
    }

    // sends the current range settings from the database to the watch dog when connected
    private fun sendSettingsOnConnect(myCallback: () -> Unit) {
        firestore.getTemperatureSettings { temp ->
            if (temp != null && temp.count() == 2) addTemperatureToWriteQueue(temp)
            firestore.getHumiditySettings { humi ->
                if (humi != null && humi.count() == 2) addHumidityToWriteQueue(humi)
                firestore.getMoistureSettings { mois ->
                    // if all values contain data, add moisture to the queue and start the worker
                    // else the db does not contain any values and write to the device is needed
                    if (mois != null && mois.count() == 2) {
                        addMoistureToWriteQueue(mois)
                        startWriteQueueWorker(myCallback)
                    } else {
                        myCallback()
                    }
                }
            }
        }
    }

    // start writing the write characteristics in the queue to the device with delay
    // this fixes the problem of writing to many characteristics in a short amount of time
    fun startWriteQueueWorker(myCallback: () -> Unit) {
        val queueHead = mBleCharacteristicQueue.poll()
        if (queueHead != null) {
            mHandler.postDelayed({
                mGatt.writeCharacteristic(queueHead)
                startWriteQueueWorker(myCallback)
            }, 200)
        } else {
            myCallback()
        }
    }

    // disconnect the device
    fun disconnect() {
        mGatt.disconnect()
        mGatt.close()
        isConnected = false
    }

    interface WatchDogScanCallback {
        fun onSuccess(bleDevices: (List<BluetoothDevice>))
        fun onReject()
    }

    interface WatchDogConnectionCallback {
        fun onConnectionChanged()
    }
}