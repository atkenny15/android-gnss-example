package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
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
                Button({
                    pairDevice(scanCb.result?.device!!)
                }) {
                    Text("Pair")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Device: ${scanCb.result?.device?.name ?: "Unknown"} - ${scanCb.result?.device?.address ?: "Unknown"}")
            }
        }
    }

    private fun pairDevice(dev: BluetoothDevice) {
        dev.connectGatt(this, true, MyBluetoothGattCallback())
    }

    private fun startScan(scanCb: MyScanCallback) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
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
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            1
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, you can start scanning
        } else {
            // Handle permission denial
        }
    }
}

class MyScanCallback(onFinish: (MyScanCallback) -> Unit)  : ScanCallback() {
    var result: ScanResult? = null
    val onFinish = onFinish

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        super.onScanResult(callbackType, result)
        Log.i("tag", "found ${result.device.name} : ${result.device.address}")

        if (this.result != null) {
            return
        }

        if (result.device.name == "FlySight") {
            Log.i("tag", "found")
            this.result = result
            onFinish(this)
        }
    }

    override fun onBatchScanResults(results: List<ScanResult>) {
        super.onBatchScanResults(results)

        if (this.result != null) {
            return
        }

        for (result in results) {
            Log.i("tag", "found ${result.device.name} : ${result.device.address}")
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
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.i("tag", "state change $status $newState")

        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                Log.i("tag", "connected")
                // Successfully connected to the device
                gatt.discoverServices() // Discover services after successful connection
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                Log.i("tag", "disconnected")
                // Device disconnected
                gatt.close() // Always close the connection
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.i("tag", "services discovered $status")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Services discovered, you can now read/write characteristics
            val services = gatt.services
            // Example: Reading a characteristic
            services.forEach { service ->
                /*
                val characteristic = service.getCharacteristic(YOUR_CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    gatt.readCharacteristic(characteristic) // Read characteristic
                }
                */
            }
        }
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        Log.i("tag", "char read $status")

        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Characteristic read successfully
            val value = characteristic.value
            // Process the value as needed
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        Log.i("tag", "char write $status")

        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Characteristic written successfully
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.i("tag", "char changed")

        // Called when a characteristic's value is changed
        val value = characteristic.value
        // Process the updated value
    }
}