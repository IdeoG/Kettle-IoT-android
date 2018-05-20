package com.example.ideog.kettleble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

/**
 * Created by ideog on 22.03.2018.
 */

public class BluetoothService extends Service {
    public final static String ACTION_GATT_CONNECTED = "com.ideo.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.ideo.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.ideo.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.ideo.ACTION_DATA_AVAILABLE";

    private final static String TAG = BluetoothService.class.getSimpleName();

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public float current_temperature = 0f;
    public float current = 0f;

    public BluetoothGatt mBLEGatt;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private int mConnectionState = STATE_DISCONNECTED;

    private String SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb";
    private String CHARACTER = "0000ffe1-0000-1000-8000-00805f9b34fb";

    private final IBinder mBinder = new LocalBinder();

    private int status = 0;


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBLEGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    public class LocalBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }


    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        }

        return true;
    }

    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            return false;
        }

        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBLEGatt != null) {
            if (mBLEGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            return false;
        }

        mBLEGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    public void disconnect() {
        if (mBluetoothAdapter == null || mBLEGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBLEGatt.disconnect();
    }

    public void close() {
        if (mBLEGatt == null) {
            return;
        }
        mBLEGatt.close();
        mBLEGatt = null;
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            try {
                String stringValue = new String(data, "ASCII");
                Log.i(TAG, stringValue);
                int value = Integer.valueOf(stringValue);
                switch (status) {
                    case 0:
                        if (value==255)
                            status = 1;
                        break;
                    case 1:
                        status = (value == 129) ? 2 : 0;
                        break;
                    case 2:
                        status++;
                        if (value > 1280) {
                            current_temperature = ((float) value) / 100f;
                            current = 0;
                            status = 0;
                            break;
                        }
                        current_temperature = ((float) value) / 10f;
                        break;
                    case 3:
                        status=0;
                        current = ((float) value) / 10f;
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        sendBroadcast(intent);
    }

    public void sendPacket(char keeping_temperature, char isChecked) {
        if (mBluetoothAdapter == null || mBLEGatt == null) {
            return;
        }

        BluetoothGattService mCustomService = mBLEGatt.getService(UUID.fromString(SERVICE));
        if (mCustomService == null) {
            return;
        }

        byte[] values = new byte[9];
        values[0] = (byte) 0xff;
        values[1] = (byte) 0x47;
        values[2] = (byte) 0x00;
        values[3] = (byte) keeping_temperature;
        values[4] = (byte) isChecked;
        values[5] = (byte) 0x00;
        values[6] = (byte) 0x00;
        values[7] = (byte) 0x0D;
        values[8] = (byte) 0x09;

        BluetoothGattCharacteristic mWriteCharacteristic = mCustomService.getCharacteristic(UUID.fromString(CHARACTER));
        mWriteCharacteristic.setValue(values);

        mBLEGatt.writeCharacteristic(mWriteCharacteristic);
    }

    public void setCharacteristicNotification() {
        if (mBluetoothAdapter == null || mBLEGatt == null) {
            return;
        }
        /*check if the service is available on the device*/
        BluetoothGattService mCustomService = mBLEGatt.getService(UUID.fromString(SERVICE));
        if (mCustomService == null) {
            return;
        }
        /*get the read characteristic from the service*/

        BluetoothGattCharacteristic mReadCharacteristic = mCustomService.getCharacteristic(UUID.fromString(CHARACTER));
        mBLEGatt.setCharacteristicNotification(mReadCharacteristic, true);

        BluetoothGattDescriptor clientConfig = mReadCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBLEGatt.writeDescriptor(clientConfig);

        Log.i(TAG, "Char read");
    }
}