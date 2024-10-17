This is a basic Android application for displaying live GNSS data received via
BLE from a Flysight 2. The code is pretty awful (my first Android project), but
perhaps useful. It was created from the basic [Create your first Android
app](https://developer.android.com/codelabs/basic-android-kotlin-compose-first-app)
tutorial.

You might be able to open or import this project directly into Android studio,
but the only files that were modified from the tutorial are below and can
probably just be copied into a new project if that doesn't work.

```
main/app/src/main/java/com/example/myapplication/MainActivity.kt
app/src/main/AndroidManifest.xml
```

Notes:

- Start Scanning button looks for a device named "FlySight"
  (`bluetoothLeScanner.startScan` -> ). Once that is found, two buttons appear:
    - "Pair": Pair the flysight
        - This seems to handle pairing correctly (via `createBond`, but does
          not read `CRS_RX_UUID` per Michael's instructions, so there might be
          a better way to do this.
    - "Test GATT": enable GNSS notifications and start displaying data
        1. Use `connectGatt` to connect to the flysight
        2. `onConnectionStateChange`, `discoverServices` to get the supported characteristics
        3. `onServicesDiscovered`, `requestMtu` to change the MTU. Default in
           android is 23 bytes (IIRC), so only 20 bytes of GNSS data would be
           received. I think setting this to 31 (28 data bytes + 3 overhead)
           would probably work instead of current 256
        4. `onMtuChanged`, `enableGnssNotifications`
            - `gatt.setCharacteristicNotification` is not sufficient to get
              notifications, the descriptor must be updated too with
              `gatt.writeDescriptor(desc,
              BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)`
        5. At this point, data will start coming in on
           `onCharacteristicChanged` callback.
- Click `Start Scanning` at any point to disconnect from current device and
  re-scan for a device named "FlySight"
- If receiving GNSS data, click "Test GATT" to disconnect from the GATT server
  and stop notifications.
