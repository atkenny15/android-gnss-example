package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
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
        var scanCb by remember { mutableStateOf(MyScanCallback()) }

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
                        startScan (scanCb) { results ->
                            if (results[0].device.name == "FlySight") {
                                stopScan(scanCb)
                                isScanning = false
                            }
                        }
                        isScanning = true
                    } else {
                        requestPermissions()
                    }
                }
            }) {
                Text(if (isScanning) "Stop Scanning" else "Start Scanning")
            }

            if (!scanCb.onResults.isEmpty()) {
                Button({}) {
                    Text("Pair")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(scanCb.onResults) { result ->
                    Text("Device: ${result.device.name ?: "Unknown"} - ${result.device.address}")
                }
            }
        }
    }

    private fun startScan(scanCb: MyScanCallback, onResults: (List<ScanResult>) -> Unit) {
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

class MyScanCallback : ScanCallback() {
    val onResults = listOf<ScanResult>()

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        super.onScanResult(callbackType, result)
        Log.i("tag", "found ${result.device.name} : ${result.device.address}")
        onResults(listOf(result))  // Add more logic for handling results as needed
    }

    override fun onBatchScanResults(results: List<ScanResult>) {
        super.onBatchScanResults(results)
        for (result in results) {
            Log.i("tag", "found ${result.device.name} : ${result.device.address}")
        }
        onResults(results)
    }

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        Log.e("tag", "scan failed ${errorCode}")
    }
}