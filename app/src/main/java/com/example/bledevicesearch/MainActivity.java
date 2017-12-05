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
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
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
    private TextView enableButton;
    private TextView moreButton;

    private List<Device> mDeviceList;
    private RecyclerView recyclerView;
    private DevicesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDeviceList = new ArrayList<>();

        recyclerView = findViewById(R.id.recycle_view);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new DevicesAdapter(mDeviceList);
        recyclerView.setAdapter(adapter);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gps_presenter = new GPSPresenter(this, this);

            layout = findViewById(R.id.location_layout);
            enableButton = findViewById(R.id.btn_location_enable);
            moreButton = findViewById(R.id.btn_location_more);
            enableButton.setOnClickListener(this);
            moreButton.setOnClickListener(this);
            findViewById(R.id.scan_btn).setOnClickListener(this);
        }


    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!gps_presenter.gpsIsOpen(this)) {
                layout.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void gpsSwitchState(boolean gpsOpen) {
        if (gpsOpen) {
            layout.setVisibility(View.GONE);
        } else {
            layout.setVisibility(View.VISIBLE);
        }
    }

    private boolean checkGPSIsOpen() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
            return false;
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.scan_btn:
                checkPermissions();
                break;
            case R.id.btn_location_enable:
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

    private void checkPermissions() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "请先打开蓝牙", Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestLocationPermission();
            } else {
                startScan();
            }
        }
    }

    private void requestLocationPermission() {
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
        BluetoothAdapter bleAdapter = BluetoothAdapter.getDefaultAdapter();
        if (Build.VERSION.SDK_INT < 21) {
            bleAdapter.startLeScan(mLeScanCallback);
        } else {
            bleAdapter.getBluetoothLeScanner().startScan(mScanCallback);
        }


    }

    //ScanCallback 是蓝牙扫描返回结果的回调，可以通过回调获取扫描结果。
    private ScanCallback mScanCallback = new ScanCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d(TAG, "found device name : " + result.getDevice().getName() + " address : " + result.getDevice().getAddress());
            //当发现一个外设时回调此方法，但本人在实际使用过程当中发现一些问题，
            //此方法在一次扫描过程当中只会返回一台设备，也就是如果scan有结果返回后，
            //就会一直返回被第一次扫描到的那个设备，无论等多久都一样，所以本人怀疑
            //如果要使用此方法的话，可能需要间歇性多次调用startScan才能发现多个设备。
            //但是不是这样，各位可以自己去试一试，因为本人在开发过程中依然使用了
            //上面第二种过时的方法。ScanResult 可以获得扫描到的设备，可以保存到设备列表成员变量当中方便后续操作。
            Device device = new Device(result.getDevice().getName(), result.getDevice().getAddress());
            if (!includeDevice(device)) {
                mDeviceList.add(device);
                adapter.notifyItemInserted(mDeviceList.size() - 1);
            }
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

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.d(TAG, "found device name : " + device.getName() + " address : " + device.getAddress());
            //成功扫描到设备后，在这里获得bluetoothDevice。可以放进设备列表成员变量当中方便后续操作。
            //也可以发广播通知activity发现了新设备，更新活动设备列表的显示等。
            //这里需要注意一点，在onLeScan当中不能执行耗时操作，不宜执行复杂运算操作，切记，
            //下面即将提到的onScanResult，onBatchScanResults同理。
        }
    };
}
