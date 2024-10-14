package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("tag", "started")

        // Initialize Bluetooth
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        setContent {
            BLEScanScreen()
        }
    }

    @Composable
    fun BLEScanScreen() {
        var isScanning by remember { mutableStateOf(false) }
        var scanCb by remember {
            mutableStateOf(MyScanCallback({ cb ->
                Log.i("tag", "onFinish -> stopScan")
                stopScan(cb)
                isScanning = false
            }))
        }
        var gatt by remember {
            mutableStateOf<BluetoothGatt?>(null)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                if (isScanning) {
                    stopScan(scanCb)
                    isScanning = false
                } else {
                    if (gatt != null) {
                        gatt!!.disconnect()
                        gatt = null
                    }
                    if (scanCb.result != null) {
                        scanCb.result = null
                    }
                    if (checkPermissions()) {
                        Log.i("tag", "startScan()")
                        startScan(scanCb)
                        isScanning = true
                    } else {
                        requestPermissions()
                    }
                }
            }) {
                Text(if (isScanning) "Stop Scanning" else "Start Scanning")
            }

            if (!isScanning && scanCb.result != null) {
                Row {
                    Button({
                        pairDevice(scanCb.result?.device!!)
                    }) {
                        Text("Pair")
                    }
                    Button({
                        gatt = setupGatt(scanCb.result!!.device!!, gatt)
                    }) {
                        Text("Test GATT")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Device: ${scanCb.result?.device?.name ?: "Unknown"} - ${scanCb.result?.device?.address ?: "Unknown"}")
            }
        }
    }

    private fun setupGatt(dev: BluetoothDevice, gatt: BluetoothGatt?): BluetoothGatt? {
        if (gatt == null) {
            return dev.connectGatt(this, false, MyBluetoothGattCallback())
        } else {
            gatt.disconnect()
            return null
        }
    }

    private fun pairDevice(dev: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("tag", "missing BLUETOOTH_CONNECT permission")
            return
        }
        dev.createBond()
    }

    private fun startScan(scanCb: MyScanCallback) {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("tag", "missing BLUETOOTH_SCAN permission")
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

        Log.i("tag", "in startScan")

        bluetoothLeScanner.startScan(scanCb)
    }

    private fun stopScan(scanCb: MyScanCallback) {
        Log.i("tag", "stopScan()")
        bluetoothLeScanner.stopScan(scanCb)
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ), 1
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, you can start scanning
        } else {
            // Handle permission denial
        }
    }
}

class MyScanCallback(onFinish: (MyScanCallback) -> Unit) : ScanCallback() {
    var result: ScanResult? = null
    val onFinish = onFinish

    private fun isMatch(result: ScanResult): Boolean {
        if (result.device.name == "FlySight") {
            var s = String()
            val mDat = result.scanRecord?.manufacturerSpecificData;
            if (mDat == null) {
                s += "mDat=${mDat}\n"
            } else {
                s += "mDat.size()=${mDat.size()}\n"
                for (index in 0..<mDat.size()) {
                    val value = mDat.valueAt(index)
                    s += "${index}: key=${mDat.keyAt(index)} val=["
                    for (byte in value) {
                        s += "${byte},"
                    }
                    s += "]\n"
                }
            }

            Log.i("tag", "found: ${s}")
            this.result = result
            onFinish(this)
            return true
        }

        return false
    }

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        super.onScanResult(callbackType, result)
        Log.i("tag", "found ${result.device.name} : ${result.device.address}")

        if (this.result != null) {
            return
        }

        isMatch(result)
    }

    override fun onBatchScanResults(results: List<ScanResult>) {
        super.onBatchScanResults(results)

        if (this.result != null) {
            return
        }

        for (result in results) {
            Log.i("tag", "found ${result.device.name} : ${result.device.address}")
            if (isMatch(result)) {
                break;
            }
            if (result.device.name == "FlySight") {
                Log.i("tag", "found")
                this.result = result
                onFinish(this)
                break;
            }
        }
    }

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        Log.e("tag", "scan failed ${errorCode}")
    }
}

class MyBluetoothGattCallback : BluetoothGattCallback() {
    val GNSS_PV_UUID = UUID.fromString("00000000-8e22-4541-9d4c-21edae82ed19");

    //val CRS_TX_UUID = UUID.fromString("00000001-8e22-4541-9d4c-21edae82ed19")
    val CRS_RX_UUID = UUID.fromString("00000002-8e22-4541-9d4c-21edae82ed19");

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)

        Log.i("tag", "state change $status $newState")

        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                Log.i("tag", "connected")
                gatt.discoverServices()
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                Log.i("tag", "disconnected")
                // Device disconnected
                gatt.close() // Always close the connection
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)

        Log.i("tag", "services discovered $status")

        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Services discovered, you can now read/write characteristics
            val services = gatt.services
            // Example: Reading a characteristic
            services.forEach { service ->
                Log.i("tag", "service ${service.uuid.toString()}")
                for (char in service.characteristics) {
                    Log.i("tag", "char: ${char.uuid.toString()}")
                }


                var char = service.getCharacteristic(GNSS_PV_UUID)
                if (char != null) {
                    var desc = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (desc == null) {
                        Log.e("tag", "desc is null")
                        /*
                        desc = BluetoothGattDescriptor(
                            GNSS_PV_UUID, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
                        )
                        char.addDescriptor(desc)
                        */
                    }

                    if (gatt.setCharacteristicNotification(char, true)) {
                        Log.i("tag", "notification enabled")
                    } else {
                        Log.e("tag", "notification could not be enabled")
                    }
                    val wr_status = gatt.writeDescriptor(
                        desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                    Log.i("tag", "gnss write status: $wr_status")

                }
            }
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        super.onCharacteristicRead(gatt, characteristic, value, status)
        var valueString = "{"
        for (byte in value) {
            valueString += "${byte.toString(radix = 16)},"
        }
        valueString += "}"
        Log.i("tag", "char read $status: ${valueString}")
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
    ) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        Log.i("tag", "char write $status")
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
    ) {
        super.onCharacteristicChanged(gatt, characteristic, value)
        Log.i("tag", "char changed: ${value}")
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        super.onDescriptorWrite(gatt, descriptor, status)
        Log.i("tag", "desc write $status")
    }
}