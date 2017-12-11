package com.example.bledevicesearch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.method.HideReturnsTransformationMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, GPS_Interface {

    private static final int REQUEST_CODE_OPEN_GPS = 1;
    private static final int REQUEST_CODE_PERMISSION_LOCATION = 2;
    private static final String TAG = MainActivity.class.getSimpleName();

    private GPSPresenter gps_presenter;

    private RelativeLayout layout;

    private List<Device> mDeviceList = new ArrayList<>();
    private DevicesAdapter adapter;

    BluetoothAdapter mBluetoothAdapter;

    private boolean mScanning;

    private Handler mHandler;
    private static final long SCAN_PERIOD = 15000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mHandler = new Handler();

        RecyclerView recyclerView = findViewById(R.id.recycle_view);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new DevicesAdapter(mDeviceList);
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(this,DividerItemDecoration.VERTICAL));

        //当Android版本大于等于6.0时才会加载布局，注册广播接收器
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gps_presenter = new GPSPresenter(this, this);
            layout = findViewById(R.id.location_layout);
            TextView enableButton = findViewById(R.id.btn_location_enable);
            TextView moreButton = findViewById(R.id.btn_location_more);
            enableButton.setOnClickListener(this);
            moreButton.setOnClickListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Android版本大于等于6.0，显示提示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!gps_presenter.gpsIsOpen(this)) {
                layout.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        adapter.clear();
        scanLeDevice(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.action_stop).setVisible(false);
            menu.findItem(R.id.action_scan).setVisible(true);
        } else {
            menu.findItem(R.id.action_stop).setVisible(true);
            menu.findItem(R.id.action_scan).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 处理动作按钮的点击事件
        switch (item.getItemId()) {
            case R.id.action_scan:
                adapter.clear();
                checkPermissions();
                return true;
            case R.id.action_stop:
                scanLeDevice(false);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    stopScan();
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            startScan();
        } else {
            mScanning = false;
            stopScan();
        }
        invalidateOptionsMenu();
    }

    /**
     * 定位服务开关事件的回调函数
     *
     * @param gpsOpen
     */
    @Override
    public void gpsSwitchState(boolean gpsOpen) {
        if (gpsOpen) {
            layout.setVisibility(View.GONE);
        } else {
            layout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_location_enable:
                //跳转到开启定位服务界面
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(intent, REQUEST_CODE_OPEN_GPS);
                break;
            case R.id.btn_location_more:
                new AlertDialog.Builder(this)
                        .setTitle("定位服务")
                        .setMessage("您的手机可能需要开启定位服务才能搜索到助听器。如果您确定身边有助听器设备，却无法搜索到设备，请开启定位服务。")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .show();
                break;
            default:
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //释放资源
        if (gps_presenter != null) {
            gps_presenter.onDestroy();
        }
    }

    /**
     * 蓝牙开关和授权检测
     */
    private void checkPermissions() {
        //没加对设备是否支持BLE的判断是当前大部分手机都支持
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "请先打开蓝牙", Toast.LENGTH_LONG).show();
            return;
        }
        //当Android版本大于等于6.0且定位权限未授权时，申请权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
        } else {
            scanLeDevice(true);
        }
    }

    /**
     * 申请授权
     */
    private void requestLocationPermission() {
        //之前选择过拒绝但没有勾选不再提示，解释授权原因
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("权限申请")
                    .setMessage("从Android 6.0之后，想要扫描低功率蓝牙设备，应用需要拥有访问设备位置的权限。这是因为Bluetooth beacons蓝牙信标，可用于确定手机和用户的位置。但本应用不会使用到您的位置信息，开启此权限只是为了扫描到蓝牙设备。")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_PERMISSION_LOCATION);
                        }
                    })
                    .setNegativeButton("Cancle", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_PERMISSION_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSION_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
            } else {
                Snackbar.make(findViewById(R.id.root), "扫描失败，缺少权限", Snackbar.LENGTH_INDEFINITE)
                        .setAction("settings", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent intent = new Intent();
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                                intent.setData(Uri.fromParts("package", getPackageName(), null));
                                startActivity(intent);
                            }
                        })
                        .setDuration(2000)
                        .show();
            }
        }
    }

    private void startScan() {
        Log.d(TAG, "开始扫描");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //4.4之前
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            //4.4及之后
            mBluetoothAdapter.getBluetoothLeScanner().startScan(mScanCallback);
        }
    }

    private void stopScan() {
        Log.d(TAG, "结束扫描");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //4.4之前
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        } else {
            //4.4及之后
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
        }
    }

    /**
     * 新接口
     * ScanCallback 是蓝牙扫描返回结果的回调，可以通过回调获取扫描结果。
     */
    private ScanCallback mScanCallback = new ScanCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d(TAG, "found device name : " + result.getDevice().getName() + " address : " + result.getDevice().getAddress());
            String name = result.getDevice().getName() == null ? "N/A": result.getDevice().getName();
            Device device = new Device(name, result.getDevice().getAddress());
            adapter.addDevice(device);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            //在此返回一个包含所有扫描结果的列表集，包括以往扫描到的结果。
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            //扫描失败后的处理。
        }
    };

    private boolean includeDevice(Device device) {
        for (Device d : mDeviceList) {
            if (device.getAddress().equals(d.getAddress())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 旧接口
     */
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.d(TAG, "found device name : " + device.getName() + " address : " + device.getAddress());
            String name = device.getName().equals("null") ? "N/A": device.getName();
            Device de = new Device(name, device.getAddress());
            adapter.addDevice(de);
        }
    };
}
