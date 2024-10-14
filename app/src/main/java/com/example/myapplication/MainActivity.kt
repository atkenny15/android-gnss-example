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

        var gnssText by remember { mutableStateOf(String()) }
        val MAX_LINES = 30
        val lines = ArrayDeque<String>()
        val updateText = { text: String ->
            lines.addFirst(text)
            while (lines.size > MAX_LINES) {
                lines.removeLast()
            }
            gnssText = lines.joinToString("\n")
        }

        val scanCb by remember {
            mutableStateOf(MyScanCallback({ cb ->
                Log.i("tag", "onFinish -> stopScan")
                stopScan(cb)
                isScanning = false
            }, updateText))
        }
        var gatt by remember {
            mutableStateOf<BluetoothGatt?>(null)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
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
                        updateText("startScan()")
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
                    Spacer(modifier = Modifier.width(16.dp))
                    Button({
                        gatt = setupGatt(scanCb.result!!.device!!, gatt, updateText)
                    }) {
                        Text("Test GATT")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Device: ${scanCb.result?.device?.name ?: "Unknown"} - ${scanCb.result?.device?.address ?: "Unknown"}")
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(gnssText)
        }
    }

    private fun setupGatt(
        dev: BluetoothDevice, gatt: BluetoothGatt?, updateGnssText: (String) -> Unit
    ): BluetoothGatt? {
        if (gatt == null) {
            return dev.connectGatt(this, false, MyBluetoothGattCallback(updateGnssText))
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

class MyScanCallback(onFinish: (MyScanCallback) -> Unit, updateText: (String) -> Unit) :
    ScanCallback() {
    var result: ScanResult? = null
    val onFinish = onFinish
    val updateText = updateText

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

            updateText("found: ${s}")
            Log.i("tag", "found: ${s}")
            this.result = result
            onFinish(this)
            return true
        }

        return false
    }

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        super.onScanResult(callbackType, result)
        updateText("found ${result.device.name} : ${result.device.address}")
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
            updateText("found ${result.device.name} : ${result.device.address}")
            Log.i("tag", "found ${result.device.name} : ${result.device.address}")
            if (isMatch(result)) {
                break;
            }
            if (result.device.name == "FlySight") {
                updateText("found flysight")
                Log.i("tag", "found flysight")
                this.result = result
                onFinish(this)
                break;
            }
        }
    }

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        updateText("scan failed ${errorCode}")
        Log.e("tag", "scan failed ${errorCode}")
    }
}

class MyBluetoothGattCallback(updateText: (String) -> Unit) : BluetoothGattCallback() {
    private val updateText = updateText;
    private val GNSS_PV_UUID = UUID.fromString("00000000-8e22-4541-9d4c-21edae82ed19");
    private val lines = ArrayDeque<String>()
    private val MAX_LINES = 20;
    //val CRS_TX_UUID = UUID.fromString("00000001-8e22-4541-9d4c-21edae82ed19")
    //val CRS_RX_UUID = UUID.fromString("00000002-8e22-4541-9d4c-21edae82ed19");

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        updateText("state change $status $newState")
        Log.i("tag", "state change $status $newState")
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                updateText("connected")
                Log.i("tag", "connected")
                gatt.discoverServices()
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                updateText("disconnected")
                Log.i("tag", "disconnected")
                // Device disconnected
                gatt.close() // Always close the connection
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)
        updateText("services discovered $status")
        Log.i("tag", "services discovered $status")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (gatt.requestMtu(256)) {
                updateText("requestMtu successful")
                Log.i("tag", "requestMtu successful")
            } else {
                updateText("requestMtu unsuccessful")
                Log.e("tag", "requestMtu unsuccessful")
            }
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
        updateText("MTU changed $mtu $status")
        Log.i("tag", "MTU changed $mtu $status")
        if (gatt != null && status == BluetoothGatt.GATT_SUCCESS) {
            updateText("enable gnss notifications")
            Log.i("tag", "enable gnss notifications")
            enableGnssNotifications(gatt, GNSS_PV_UUID, updateText)
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        super.onCharacteristicRead(gatt, characteristic, value, status)
        logBytes("char read $status", value)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
    ) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        updateText("char write $status")
        Log.i("tag", "char write $status")
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
    ) {
        super.onCharacteristicChanged(gatt, characteristic, value)
        logBytes("char ${characteristic.uuid} changed", value)
        if (characteristic.uuid == GNSS_PV_UUID) {
            if (value.size != 28) {
                updateText("Got ${value.size} bytes but expected 28 for GNSS characteristic")
                Log.e("tag", "Got ${value.size} bytes but expected 28 for GNSS characteristic")
            } else {
                val data = GnssData(value)
                updateText(data.toString())
                Log.i("tag", "data: ${data}")
            }
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int
    ) {
        super.onDescriptorWrite(gatt, descriptor, status)
        updateText("desc write $status")
        Log.i("tag", "desc write $status")
    }
}

fun logBytes(string: String, value: ByteArray) {
    var valueString = "size=${value.size} {"
    for (byte in value) {
        valueString += "${byte.toString(radix = 16)},"
    }
    valueString += "}"
    Log.i("tag", "$string: ${valueString}")
}

class GnssData {
    val iTow: UInt;
    val lon: Int;
    val lat: Int;
    val hMsl: Int;
    val velN: Int;
    val velE: Int;
    val velD: Int;

    constructor(bytes: ByteArray) {
        iTow = getInt(bytes, 0).toUInt()
        lon = getInt(bytes, 4)
        lat = getInt(bytes, 8)
        hMsl = getInt(bytes, 12)
        velN = getInt(bytes, 16)
        velE = getInt(bytes, 20)
        velD = getInt(bytes, 24)
    }

    override fun toString(): String {
        return String.format(
            "%.03f, %.07f, %.07f, %.03f, %.03f, %.03f, %.03f",
            iTow.toDouble() / 1e3,
            lon.toDouble() / 1e7,
            lat.toDouble() / 1e7,
            hMsl.toDouble() / 1e3,
            velN.toDouble() / 1e3,
            velE.toDouble() / 1e3,
            velD.toDouble() / 1e3
        )
    }
}

fun getInt(bytes: ByteArray, offset: Int): Int {
    var ret: UInt = 0U
    for (i in 0..3) {
        ret = ret or (bytes[offset + i].toUByte().toUInt() shl (i * 8))
    }
    return ret.toInt()
}

fun enableGnssNotifications(gatt: BluetoothGatt, uuid: UUID, updateText: (String) -> Unit) {
    val services = gatt.services
    services.forEach { service ->
        updateText("service ${service.uuid.toString()}")
        Log.i("tag", "service ${service.uuid.toString()}")
        for (char in service.characteristics) {
            updateText("char: ${char.uuid.toString()}")
            Log.i("tag", "char: ${char.uuid.toString()}")
        }

        var char = service.getCharacteristic(uuid)
        if (char != null) {
            if (char.descriptors.isEmpty()) {
                updateText("no descriptors for GNSS_PV_UUID")
                Log.e("tag", "no descriptors for GNSS_PV_UUID")
            } else {
                if (gatt.setCharacteristicNotification(char, true)) {
                    updateText("notification enabled")
                    Log.i("tag", "notification enabled")
                } else {
                    updateText("notification could not be enabled")
                    Log.e("tag", "notification could not be enabled")
                }
                var desc = char.descriptors[0]
                val wr_status = gatt.writeDescriptor(
                    desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
                if (wr_status == 0) {
                    updateText("gnss write status: $wr_status")
                    Log.i("tag", "gnss write status: $wr_status")
                } else {
                    updateText("gnss write status: $wr_status")
                    Log.e("tag", "gnss write status: $wr_status")
                }
            }
        }
    }
}
