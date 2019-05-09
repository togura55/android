package com.wacom.ogura.tsuyoshi.wdcontroller;


import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    // Constants（Bluetooth LE Gatt UUID）
    // Private Service
//    private static final UUID UUID_SERVICE_PRIVATE         = UUID.fromString( "FF6B1160-8FE6-11E7-ABC4-CEC278B6B50A" );
//    private static final UUID UUID_SERVICE_PRIVATE = UUID.fromString("34B1CF4D-1069-4AD6-89B6-E161D79BE4D2"); // for WdP1.1

    //    private static final UUID UUID_CHARACTERISTIC_PRIVATE1 = UUID.fromString("FF6B1426-8FE6-11E7-ABC4-CEC278B6B50A");
//    private static final UUID UUID_CHARACTERISTIC_PRIVATE2 = UUID.fromString("FF6B1548-8FE6-11E7-ABC4-CEC278B6B50A");
    // for Notification
    private static final UUID UUID_NOTIFY = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Constants
    private static final int REQUEST_ENABLE_BLUETOOTH = 1; // Bluetooth機能の有効化要求時の識別コード
    private static final int REQUEST_CONNECT_DEVICE = 2; // デバイス接続要求時の識別コード
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;  // Android 6(sdk>23) Bluetooth Scan

    private String CommandState;        // a current state of which command is just sent to Pub
    private final String CMD_GETCONFIG = "getconfig";
    private final String CMD_SETCONFIG = "setconfig";  // setconfig,aaa,bbb,ccc
    private final String CMD_GETVERSION = "getversion";
    private final String CMD_START = "start";       // Publisher state
    private final String CMD_STOP = "stop";         // Publisher state
    private final String CMD_SUSPEND = "suspend";   // Publisher state
    private final String CMD_RESUME = "resume";     // Publisher state
    private final String CMD_RESTART = "restart";
    private final String CMD_POWEROFF = "poweroff";
    private final String CMD_GETLOGS = "getlogs";
    private final String CMD_GETBARCODE = "getbarcode";
    private final String CMD_GETSTATUS = "getstatus";

    private final String RES_ACK = "ack";
    private final String RES_NAK = "nak";

    private int State;      // a current state of the WdC, set one of the STATE_XXX value
    private final int STATE_NEUTRAL = 0; // initial state
    private final int STATE_READY = 1;   // Scan BLE completed
    private final int STATE_ACTIVE = 2;  // under handling BLE device

    //        public int PublisherCurrentState;
    private final int PUBLISHER_STATE_DISCONNECTED = -1;
    private final int PUBLISHER_STATE_NEUTRAL = 0;
    private final int PUBLISHER_STATE_ACTIVE = 1;
    private final int PUBLISHER_STATE_IDLE = 2;

    // member valuables
    private BluetoothAdapter mBluetoothAdapter;    // BluetoothAdapter : Bluetooth処理で必要
    private String mDeviceAddress = "";    // デバイスアドレス
    private BluetoothGatt mBluetoothGatt = null;    // Gattサービスの検索、キャラスタリスティックの読み書き
    private BluetoothDevice mBluetoothDevice = null;

    private String mWdpVersion;
    private String mWidth;
    private String mHeight;
    private String mPointSize;
    private String mDeviceName;
    private String mESN;
    private String mBattery;
    private String mFirmwareVersion;       // added 1.1
    private String mDeviceType;
    private String mTransferMode;
    private String mBarcode;               // added 1.1
    private String mServerIpAddress;
    private String mServerPortNumberBase;
    private int mDeviceState  = PUBLISHER_STATE_DISCONNECTED;
    private String mClientIpAddress;    // added 1.0.2

    // GUI items
    private Button mButton_Connect;    // Connect button
    private Button mButton_Disconnect;    // Disconnect button
    private Button mButton_getVersion;
    private Button mButton_getConfig;
    private Button mButton_setConfig;
    private Button mButton_deviceStart;
    private Button mButton_deviceStop;
    private Button mButton_deviceSuspend;
    private Button mButton_deviceResume;
    private Button mButton_devicePowerOff;
    private Button mButton_deviceRestart;

    private EditText mEditText_DeviceName;
    private EditText mEditText_IpAddress;
    private EditText mEditText_PortNumber;

    private TextView mTextView_WdpVersion;

    // ---- RfComm ------------------------
    static final String TAG = "BT_TEST1";
    BluetoothAdapter bluetoothAdapter;

    TextView btStatusTextView;
    TextView tempTextView;

    BTClientThread btClientThread;

    void ShowToaster(final String s){
        if (s != null) {
            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            final String s;

            switch (msg.what) {
                case Constants.MESSAGE_GETCONFIG:
                    mEditText_DeviceName.setText(mDeviceName);
                    mEditText_IpAddress.setText(mServerIpAddress);
                    mEditText_PortNumber.setText(mServerPortNumberBase);
                    break;
                case Constants.MESSAGE_SETCONFIG:  // setconfig,aaa,bbb,ccc
                    ShowToaster((String) msg.obj);
                    break;
                case Constants.MESSAGE_GETVERSION:
                    mTextView_WdpVersion.setText(mWdpVersion);
                    break;
                case Constants.MESSAGE_START:       // Publisher state
                    ShowToaster((String) msg.obj);
                    break;
                case Constants.MESSAGE_STOP:         // Publisher state
                    ShowToaster((String) msg.obj);
                    break;
                case Constants.MESSAGE_SUSPEND:   // Publisher state
                    ShowToaster((String) msg.obj);
                    break;
                case Constants.MESSAGE_RESUME:     // Publisher state
                    ShowToaster((String) msg.obj);
                    break;
                case Constants.MESSAGE_RESTART:
                    ShowToaster((String) msg.obj);
                    break;
                case Constants.MESSAGE_POWEROFF:
                    ShowToaster((String) msg.obj);
                    break;
                case Constants.MESSAGE_GETLOGS:
                    break;
                case Constants.MESSAGE_GETBARCODE:
                    break;
                case Constants.MESSAGE_GETSTATUS:
                    break;

                case Constants.MESSAGE_UPDATEUI:
                    UpdateUi((int) msg.obj);
                    break;

                case Constants.MESSAGE_BT:
                    s = (String) msg.obj;
                    if (s != null) {
                        //                       btStatusTextView.setText(s);
//                        MainActivity.this.runOnUiThread(new Runnable() {
//                            public void run() {
//                                Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show();
//                            }
//                        });
                    }
                    break;
//                case Constants.MESSAGE_TEMP:
//                    s = (String) msg.obj;
//                    if (s != null) {
////                        tempTextView.setText(s);
//                    }
//                    break;
            }
        }
    };


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
//        outState.putString(Constants.STATE_TEMP, tempTextView.getText().toString());
    }

    public class BTClientThread extends Thread {

        DataInputStream dataInputStream;
        DataOutputStream dataOutputStream;
        BluetoothSocket bluetoothSocket;

        private String BtCommand;

        BTClientThread(String command) {
            BtCommand = command;
        }

        public void run() {
            byte[] incomingBuff = new byte[64];

//            if (mBluetoothDevice == null) {
//                Log.d(TAG, "No device found.");
//                return;
//            }

            try {

                // Create the socket
                bluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(
                        Constants.BT_UUID);

                while (true) {
                    if (Thread.interrupted()) {
                        break;
                    }

                    try {
                        // Connect to the created socket
                        bluetoothSocket.connect();

                        // send message to handler
                        handler.obtainMessage(
                                Constants.MESSAGE_BT,
                                R.string.connect + " " + mBluetoothDevice.getName())
                                .sendToTarget();

                        OutputStream os = bluetoothSocket.getOutputStream();
                        dataInputStream = new DataInputStream(bluetoothSocket.getInputStream());
                        dataOutputStream = new DataOutputStream(bluetoothSocket.getOutputStream());

                        // Send Command and receive response

                        if (BtCommand.length() > 0) {

                            int size = BtCommand.length();
                            dataOutputStream.writeInt(size);

                            byte[] buf = BtCommand.getBytes("UTF-8");
                            dataOutputStream.write(buf, 0, buf.length);

                            // Read Response
                            int incomingBytes = dataInputStream.read(incomingBuff);
                            int nHeader = 4;
                            byte[] buff = new byte[incomingBytes - nHeader];
                            System.arraycopy(incomingBuff, nHeader, buff, 0, incomingBytes - nHeader);
                            String s = new String(buff, StandardCharsets.UTF_8);

                            ResponseDispatcher(BtCommand, s);

                            break;
                        }
                    } catch (
                            IOException e) {
                        e.printStackTrace();
                    }

                    // 閉じる
                    if (bluetoothSocket != null) {
                        try {
                            bluetoothSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        bluetoothSocket = null;
                    }

                    handler.obtainMessage(
                            Constants.MESSAGE_BT,
                            "DISCONNECTED - Exit BTClientThread")
                            .sendToTarget();
                }
            } catch (
                    IOException e) {
                e.printStackTrace();
            }
        }
    }

    void ResponseDispatcher(String command, String response) {
        try {
            Boolean updateUi = false;

            switch (command) {
                case CMD_GETCONFIG:
                    // decode
 //                   List<String> list = SplitArgument(",", response);
                    List<String> list = Arrays.asList(response.split(","));
                    if (list.size() < 13)   // ToDo: should be set by enum
                    {
                        // error, resend?
                        throw new IOException("GetConfig returns the smaller number of parameters.");
                    } else {
                        int i = -1;
                        mWidth = list.get(++i);
                        mHeight = list.get(++i);
                        mPointSize = list.get(++i);
                        mDeviceName = list.get(++i);
                        mESN = list.get(++i);
                        mBattery = list.get(++i);
                        mFirmwareVersion = list.get(++i);       // added 1.1
                        mDeviceType = list.get(++i);
                        mTransferMode = list.get(++i);
                        mBarcode = list.get(++i);               // added 1.1
                        mServerIpAddress = list.get(++i);
                        mServerPortNumberBase = list.get(++i);
                        mDeviceState = Integer.parseInt(list.get(++i));
                        mClientIpAddress = list.get(++i);    // added 1.0.2
                    }

                    handler.obtainMessage(
                            Constants.MESSAGE_GETCONFIG, response)
                            .sendToTarget();
                    break;
                case CMD_SETCONFIG:  // setconfig,aaa,bbb,ccc
                    break;
                case CMD_GETVERSION:
                    mWdpVersion = response;
                    handler.obtainMessage(
                            Constants.MESSAGE_GETVERSION, response)
                            .sendToTarget();
                    break;
                case CMD_START:       // Publisher state
                    mDeviceState = PUBLISHER_STATE_ACTIVE;
                    updateUi = true;
                    break;
                case CMD_STOP:         // Publisher state
                    mDeviceState = PUBLISHER_STATE_NEUTRAL;
                    updateUi = true;
                    break;
                case CMD_SUSPEND:   // Publisher state
                    mDeviceState = PUBLISHER_STATE_IDLE;
                    updateUi = true;
                    break;
                case CMD_RESUME:     // Publisher state
                    mDeviceState = PUBLISHER_STATE_ACTIVE;
                    updateUi = true;
                    break;
                case CMD_RESTART:
                    mDeviceState = PUBLISHER_STATE_DISCONNECTED;
                    updateUi = true;
                    break;
                case CMD_POWEROFF:
                    mDeviceState = PUBLISHER_STATE_DISCONNECTED;
                    updateUi = true;
                    break;
                case CMD_GETLOGS:
                    break;
                case CMD_GETBARCODE:
                    break;
                case CMD_GETSTATUS:
                    if (response != RES_NAK) {
                        mDeviceState = Integer.parseInt(response);
                        updateUi = true;
                    }
                    break;
                default:
                    break;
            }

            if (updateUi)
                handler.obtainMessage(
                        Constants.MESSAGE_UPDATEUI, mDeviceState)
                        .sendToTarget();

        }catch(IOException ex) {
            ex.printStackTrace();
        }
    }

    void UpdateUi(int state){
        switch (state){
            case PUBLISHER_STATE_DISCONNECTED:
                mButton_Connect.setEnabled(false);    // Connect button
                mButton_Disconnect.setEnabled(false);    // Disconnect button
                mButton_getVersion.setEnabled(false);
                mButton_getConfig.setEnabled(false);
                mButton_setConfig.setEnabled(false);
                mButton_deviceStart.setEnabled(false);
                mButton_deviceStop.setEnabled(false);
                mButton_deviceSuspend.setEnabled(false);
                mButton_deviceResume.setEnabled(false);
                mButton_devicePowerOff.setEnabled(false);
                mButton_deviceRestart.setEnabled(false);
                break;
            case PUBLISHER_STATE_NEUTRAL:
                mButton_getVersion.setEnabled(true);
                mButton_getConfig.setEnabled(true);
                mButton_setConfig.setEnabled(true);

                mButton_deviceStart.setEnabled(true);
                mButton_deviceStop.setEnabled(false);
                mButton_deviceSuspend.setEnabled(false);
                mButton_deviceResume.setEnabled(false);
                mButton_devicePowerOff.setEnabled(true);
                mButton_deviceRestart.setEnabled(true);
                break;
            case PUBLISHER_STATE_ACTIVE:
                mButton_getVersion.setEnabled(true);
                mButton_getConfig.setEnabled(true);
                mButton_setConfig.setEnabled(true);

                mButton_deviceStart.setEnabled(false);
                mButton_deviceStop.setEnabled(true);
                mButton_deviceSuspend.setEnabled(true);
                mButton_deviceResume.setEnabled(false);
                mButton_devicePowerOff.setEnabled(true);
                mButton_deviceRestart.setEnabled(true);
                break;
            case PUBLISHER_STATE_IDLE:
                mButton_getVersion.setEnabled(true);
                mButton_getConfig.setEnabled(true);
                mButton_setConfig.setEnabled(true);

                mButton_deviceStart.setEnabled(false);
                mButton_deviceStop.setEnabled(true);
                mButton_deviceSuspend.setEnabled(false);
                mButton_deviceResume.setEnabled(true);
                mButton_devicePowerOff.setEnabled(true);
                mButton_deviceRestart.setEnabled(true);
                break;
            default:
                break;
        }
    }
    // ----- End of RfComm -----------------


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // just for the demo
//        mDeviceName = "Wacom Clipboard";
//        mServerIpAddress = "192.168.0.7";
//        mServerPortNumberBase = "1337";

        // GUI items
        mButton_Connect = findViewById(R.id.button_connect);
        mButton_Connect.setOnClickListener(this);
        mButton_Disconnect = findViewById(R.id.button_disconnect);
        mButton_Disconnect.setOnClickListener(this);
        mButton_getVersion = findViewById(R.id.button_getVersion);
        mButton_getVersion.setOnClickListener(this);
        mButton_getConfig = findViewById(R.id.button_getConfig);
        mButton_getConfig.setOnClickListener(this);
        mButton_setConfig = findViewById(R.id.button_setConfig);
        mButton_setConfig.setOnClickListener(this);
        mButton_deviceStart = findViewById(R.id.button_deviceStart);
        mButton_deviceStart.setOnClickListener(this);
        mButton_deviceStop = findViewById(R.id.button_deviceStop);
        mButton_deviceStop.setOnClickListener(this);
        mButton_deviceSuspend = findViewById(R.id.button_deviceSuspend);
        mButton_deviceSuspend.setOnClickListener(this);
        mButton_deviceResume = findViewById(R.id.button_deviceResume);
        mButton_deviceResume.setOnClickListener(this);
        mButton_devicePowerOff = findViewById(R.id.button_devicePowerOff);
        mButton_devicePowerOff.setOnClickListener(this);
        mButton_deviceRestart = findViewById(R.id.button_deviceRestart);
        mButton_deviceRestart.setOnClickListener(this);

        mEditText_DeviceName = findViewById(R.id.edit_deviceName);
        mEditText_IpAddress = findViewById(R.id.edit_ipAddress);
        mEditText_PortNumber = findViewById(R.id.edit_portNumber);

        mTextView_WdpVersion = findViewById(R.id.textview_wdpversion);

        UpdateUi(PUBLISHER_STATE_DISCONNECTED);

        // Android端末がBLEをサポートしてるかの確認
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_is_not_supported, Toast.LENGTH_SHORT).show();
            finish();    // アプリ終了宣言
            return;
        }

        // Android M Permission check
        // Resolving for the Android6.0(sdk>23) Bluetooth Scan problem
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
            }
        }

        // Bluetoothアダプタの取得

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (null == mBluetoothAdapter) {    // Android端末がBluetoothをサポートしていない
            Toast.makeText(this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT).show();
            finish();    // アプリ終了宣言
            return;
        }

        // ----- RfComm -----

        Log.d(TAG, "onCreate");

        //       setContentView(R.layout.activity_main);

        // Find Views
        //   btStatusTextView = (TextView) findViewById(R.id.btStatusTextView);
        //  tempTextView = (TextView) findViewById(R.id.tempTextView);


//        if (savedInstanceState != null) {
//            String temp = savedInstanceState.getString(Constants.STATE_TEMP);
////            tempTextView.setText(temp);
//        }

        // Initialize Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.d(TAG, "This device doesn't support Bluetooth.");
        }
        // ---- End of RfComm -----
    }

    private void SendCommand(String command){
        connect();

        CommandState = command;
        btClientThread = new BTClientThread(command);
        btClientThread.start();
    }

    @Override
    public void onClick(View v) {
        Toast.makeText(this, "onClick is invoked.", Toast.LENGTH_SHORT).show();

        if (mButton_Connect.getId() == v.getId()) {
            mButton_Connect.setEnabled(false);    // [接続]ボタンの無効化（連打対策）
            connect();            // connect
            return;
        }
        if (mButton_Disconnect.getId() == v.getId()) {
            mButton_Disconnect.setEnabled(false);    // [切断]ボタンの無効化（連打対策）
            disconnect();            // disconnect
            return;
        }
        if (mButton_getVersion.getId() == v.getId()) {
            mButton_getVersion.setEnabled(false);    // 無効化（連打対策）
            SendCommand(CMD_GETVERSION);
            return;
        }
        if (mButton_getConfig.getId() == v.getId()) {
            mButton_getConfig.setEnabled(false);    // 無効化（連打対策）
            SendCommand(CMD_GETCONFIG);
        }
        if (mButton_setConfig.getId() == v.getId()) {
            mButton_setConfig.setEnabled(false);    // 無効化（連打対策）
            SendCommand(CMD_SETCONFIG);
        }
        if (mButton_deviceStart.getId() == v.getId()) {
            mButton_deviceStart.setEnabled(false);    // 無効化（連打対策）
            SendCommand(CMD_START);
        }
        if (mButton_deviceStop.getId() == v.getId()) {
            mButton_deviceStop.setEnabled(false);    // 無効化（連打対策）
            SendCommand(CMD_STOP);
        }
        if (mButton_deviceSuspend.getId() == v.getId()) {
            mButton_deviceSuspend.setEnabled(false);    // 無効化（連打対策）
            SendCommand(CMD_SUSPEND);
        }
        if (mButton_deviceResume.getId() == v.getId()) {
            mButton_deviceResume.setEnabled(false);    // 無効化（連打対策）
            SendCommand(CMD_RESUME);
        }
        if (mButton_devicePowerOff.getId() == v.getId()) {
            mButton_devicePowerOff.setEnabled(false);    // 無効化（連打対策）
            SendCommand(CMD_POWEROFF);
        }
        if (mButton_deviceRestart.getId() == v.getId()) {
            mButton_deviceRestart.setEnabled(false);    // 無効化（連打対策）
            SendCommand(CMD_RESTART);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {
                    Toast.makeText(this, R.string.bluetooth_scan_need_permission, Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    // 初回表示時、および、他画面からの戻り、ポーズからの復帰時
    @Override
    protected void onResume() {
        super.onResume();

//        // --- RfComm ------
        // for Auto connect/disconnect
//        btClientThread = new BTClientThread();
//        btClientThread.start();
//        // ---- End of RfComm -----


        // Android端末のBluetooth機能の有効化要求
        requestBluetoothFeature();

        // GUIアイテムの有効無効の設定
//        mButton_Connect.setEnabled( false );
//        mButton_Disconnect.setEnabled( false );

        mEditText_DeviceName.setText(mDeviceName);
        mEditText_IpAddress.setText(mServerIpAddress);
        mEditText_PortNumber.setText(mServerPortNumberBase);


        // Try GetStatus if the device address is not empty
        //   Is WdP device？
        //    Yes, Read DeviceState value of WdP
        if (!mDeviceAddress.equals("")) {
            SendCommand(CMD_GETSTATUS);
        }

        // [接続]ボタンを押す
        //       mButton_Connect.callOnClick();
    }

    // 別のアクティビティ（か別のアプリ）に移行したことで、バックグラウンドに追いやられた時
    @Override
    protected void onPause() {
        super.onPause();

        // ---- RfComm -----
        // Auto connect/disconnect
//        if(btClientThread != null){
//            btClientThread.interrupt();
//            btClientThread = null;
//        }
        // --- End of RfComm ----

        // 切断
        disconnect();
    }

    // アクティビティの終了直前
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mBluetoothGatt) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    // Android端末のBluetooth機能の有効化要求
    private void requestBluetoothFeature() {
        if (mBluetoothAdapter.isEnabled()) {
            return;
        }
        // デバイスのBluetooth機能が有効になっていないときは、有効化要求（ダイアログ表示）
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
    }

    // 機能の有効化ダイアログの操作結果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH: // Bluetooth有効化要求
                if (Activity.RESULT_CANCELED == resultCode) {    // 有効にされなかった
                    Toast.makeText(this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT).show();
                    finish();    // アプリ終了宣言
                    return;
                }
                break;
            case REQUEST_CONNECT_DEVICE: // デバイス接続要求
                String strDeviceName;
                if (Activity.RESULT_OK == resultCode) {
                    // デバイスリストアクティビティからの情報の取得
                    strDeviceName = data.getStringExtra(DeviceListActivity.EXTRAS_DEVICE_NAME);
                    mDeviceAddress = data.getStringExtra(DeviceListActivity.EXTRAS_DEVICE_ADDRESS);
                } else {
                    strDeviceName = "";
                    mDeviceAddress = "";
                }
                ((TextView) findViewById(R.id.textview_devicename)).setText(strDeviceName);
                ((TextView) findViewById(R.id.textview_deviceaddress)).setText(mDeviceAddress);

                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // オプションメニュー作成時の処理
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    // オプションメニューのアイテム選択時の処理
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuitem_search:
                Intent deviceListActivityIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(deviceListActivityIntent, REQUEST_CONNECT_DEVICE);
                return true;
        }
        return false;
    }

    // 接続
    private void connect() {
        if (mDeviceAddress.equals("")) {    // DeviceAddressが空の場合は処理しない
            return;
        }

        if (mBluetoothDevice != null){
            return;
        }
//        if (null != mBluetoothGatt) {    // mBluetoothGattがnullでないなら接続済みか、接続中。
//            return;
//        }

        // 接続
//        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice( mDeviceAddress );
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);

//        mBluetoothGatt = device.connectGatt( this, false, mGattCallback );

        // --- RfComm ------
//        btClientThread = new BTClientThread(CMD_GETVERSION);
//        btClientThread.start();
        // ---- End of RfComm -----
    }

    // 切断
    private void disconnect() {
        // ---- RfComm ------
        if (btClientThread != null) {
            btClientThread.interrupt();
            btClientThread = null;
        }
        // ------------------

        // commented out cause No use of GATT
//        if( null == mBluetoothGatt )
//        {
//            return;
//        }

        // 切断
        //   mBluetoothGatt.disconnect()ではなく、mBluetoothGatt.close()しオブジェクトを解放する。
        //   理由：「ユーザーの意思による切断」と「接続範囲から外れた切断」を区別するため。
        //   ①「ユーザーの意思による切断」は、mBluetoothGattオブジェクトを解放する。再接続は、オブジェクト構築から。
        //   ②「接続可能範囲から外れた切断」は、内部処理でmBluetoothGatt.disconnect()処理が実施される。
        //     切断時のコールバックでmBluetoothGatt.connect()を呼んでおくと、接続可能範囲に入ったら自動接続する。
//        mBluetoothGatt.close();
//        mBluetoothGatt = null;
        // GUIアイテムの有効無効の設定
        // [接続]ボタンのみ有効にする
        mButton_Connect.setEnabled(true);
        mButton_Disconnect.setEnabled(false);
    }

    // GATTキャラクタリスティックの読み込み
    private void readCharacteristic(UUID uuid_service, UUID uuid_characteristic) {
        if (null == mBluetoothGatt) {
            return;
        }
        BluetoothGattCharacteristic bleChar = mBluetoothGatt.getService(uuid_service).getCharacteristic(uuid_characteristic);
        mBluetoothGatt.readCharacteristic(bleChar);
    }

    // GATTキャラクタリスティック通知の設定
    private void setCharacteristicNotification(UUID uuid_service, UUID uuid_characteristic, boolean enable) {
        if (null == mBluetoothGatt) {
            return;
        }
        BluetoothGattCharacteristic bleChar = mBluetoothGatt.getService(uuid_service).getCharacteristic(uuid_characteristic);
        mBluetoothGatt.setCharacteristicNotification(bleChar, enable);
        BluetoothGattDescriptor descriptor = bleChar.getDescriptor(UUID_NOTIFY);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    // GATTキャラクタリスティックの書き込み
    private void writeCharacteristic(UUID uuid_service, UUID uuid_characteristic, String string) {
        if (null == mBluetoothGatt) {
            return;
        }
        BluetoothGattCharacteristic bleChar = mBluetoothGatt.getService(uuid_service).getCharacteristic(uuid_characteristic);
        bleChar.setValue(string);
        mBluetoothGatt.writeCharacteristic(bleChar);
    }
}
