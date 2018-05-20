package com.example.ideog.kettleble;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;
import com.github.angads25.toggle.LabeledSwitch;
import com.github.angads25.toggle.interfaces.OnToggledListener;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private String mDeviceAddress = "A4:C1:38:77:23:7C";
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothService mBLEService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBLEService = ((BluetoothService.LocalBinder) service).getService();
            if (!mBLEService.initialize()) {
                finish();
            }
            mBLEService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBLEService = null;
        }
    };
    private TextView txtTemp;
    private TextView txtCurrent;
    private ConstraintLayout layout;
    private LabeledSwitch toggle;
    private LottieAnimationView loading;
    private LottieAnimationView water;

    private final int REQUEST_ENABLE_BT = 1;
    private final int REQUEST_CODE_PERMISSION_LOCATION = 2;
    private boolean mConnected;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    private void initializeUI() {
        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        myToolbar.setTitle("Redmond kettle");
        setSupportActionBar(myToolbar);

        txtTemp = findViewById(R.id.txtTemp);
        txtCurrent = findViewById(R.id.txtCurrent);

        layout = findViewById(R.id.top_level_layout);
        layout.setVisibility(ConstraintLayout.GONE);

        loading = findViewById(R.id.loading);
        toggle = findViewById(R.id.toggle);
        water = findViewById(R.id.water);

        water.setProgress(0.5f);

        loading.setVisibility(LottieAnimationView.VISIBLE);
        loading.playAnimation();
        loading.loop(true);

        toggle.setOnToggledListener(new OnToggledListener() {
            @Override
            public void onSwitched(LabeledSwitch labeledSwitch, boolean isOn) {
                if (isOn) {
                    mBLEService.sendPacket((char) 20, (char) 1);
                } else {
                    mBLEService.sendPacket((char) 20, (char) 0);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
        Intent gattServiceIntent = new Intent(this, BluetoothService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBLEService != null) {
            mBLEService.connect(mDeviceAddress);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBLEService = null;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothService.ACTION_GATT_CONNECTED)) {
                mConnected = true;

                if (loading.isAnimating())
                    loading.cancelAnimation();

                loading.setVisibility(LottieAnimationView.GONE);
                layout.setVisibility(ConstraintLayout.VISIBLE);

                notificationEnable();
                invalidateOptionsMenu();
            } else if (action.equals(BluetoothService.ACTION_GATT_DISCONNECTED)) {

                loading.setVisibility(LottieAnimationView.VISIBLE);
                layout.setVisibility(ConstraintLayout.GONE);
                loading.playAnimation();
                loading.loop(true);

                mConnected = false;
                invalidateOptionsMenu();
            } else if (action.equals(BluetoothService.ACTION_DATA_AVAILABLE)) {
                handle_data();
            }
        }
    };

    private void handle_data() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mBLEService.current > 5.0f) {
                    if (!toggle.isOn()) {
                        water.playAnimation();
                        water.loop(true);
                        toggle.setOn(true);
                    }
                } else if (mBLEService.current == 0.0f) {
                    if (mBLEService.current_temperature > 90f)
                        toggle.setOn(false);
                    water.cancelAnimation();
                    water.setProgress(0.5f);
                }
                txtTemp.setText(String.valueOf(mBLEService.current_temperature) + " \u2103");
                txtCurrent.setText(String.valueOf(mBLEService.current + " A"));
            }
        });
    }

    private void checkPermissions() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck == 0)
            permissionCheck += ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            return;
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_PERMISSION_LOCATION);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBLEService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBLEService.disconnect();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void notificationEnable() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                mBLEService.setCharacteristicNotification();
            }
        };

        Timer timer = new Timer();
        timer.schedule(task, 2000);
    }
}
