package com.wacom.ogura.tsuyoshi.wdcontroller;


import android.Manifest;
import android.annotation.SuppressLint;
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
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
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
    private static final int REQUEST_ENABLE_BLUETOOTH = 1; // ID code when using the activate Bluetooth function
    private static final int REQUEST_CONNECT_DEVICE = 2; // ID code for using request of the device connection
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
    private final int STATE_START = 0; // initial state, just the ScanAndConnect completed
    private final int STATE_NEUTRAL = 1; // neutral state
    private final int STATE_READY = 2;   // Scan BLE completed
    private final int STATE_ACTIVE = 3;  // under handling BLE device

    //        public int PublisherCurrentState;
    private final int PUBLISHER_STATE_DISCONNECTED = -1;
    private final int PUBLISHER_STATE_START = 0;
    private final int PUBLISHER_STATE_NEUTRAL = 1;
    private final int PUBLISHER_STATE_ACTIVE = 2;
    private final int PUBLISHER_STATE_IDLE = 3;

    // member valuables
    private BluetoothAdapter mBluetoothAdapter;    // BluetoothAdapter : need at the Bluetooth process
    private String mDeviceAddress = "";
    private BluetoothGatt mBluetoothGatt = null;    // Gatt service search, r/w the characteristic
    private BluetoothDevice mBluetoothDevice = null;

    private DataInputStream mDataInputStream = null;
    private DataOutputStream mDataOutputStream = null;
    private BluetoothSocket mBluetoothSocket = null;
    private Boolean mBTSocketConnected = false;

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
    private int mDeviceState = PUBLISHER_STATE_DISCONNECTED;
    private ArrayList mLogsList = new ArrayList<>();

    // GUI items
    private Button mButton_Disconnect;    // Disconnect button
    private Button mButton_getVersion;
    private Button mButton_getConfig;
    private Button mButton_setConfig;
    private Button mButton_deviceStart;
    private Button mButton_deviceSuspend;
    private Button mButton_devicePowerOff;
    private Button mButton_deviceRestart;
    private Button mButton_getLogs;
    private Button mButton_clearLogs;
    private Button mButton_saveLogs;

    private ListView mListView_Logs;
    private ArrayAdapter mAdapterLogs;

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

    void ShowToaster(final String s) {
        if (s != null) {
            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @SuppressLint("HandlerLeak")
    final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            final String s;

            switch (msg.what) {
                case Constants.MESSAGE_GETCONFIG:
                    mEditText_DeviceName.setText(mDeviceName);
                    mEditText_IpAddress.setText(mServerIpAddress);
                    mEditText_PortNumber.setText(mServerPortNumberBase);
                    mButton_getConfig.setEnabled(true);
                    break;
                case Constants.MESSAGE_SETCONFIG:  // setconfig,aaa,bbb,ccc
                    ShowToaster((String) msg.obj);
                    mButton_setConfig.setEnabled(true);
                    break;
                case Constants.MESSAGE_GETVERSION:
                    mTextView_WdpVersion.setText(mWdpVersion);
                    mButton_getVersion.setEnabled(true);
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
                    mLogsList.add((String) msg.obj);
                    mAdapterLogs.notifyDataSetChanged();
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

        private String BtCommand;
        private DataInputStream dataInputStream;
        private DataOutputStream dataOutputStream;

        BTClientThread(String command, DataOutputStream output, DataInputStream input) {
            BtCommand = command;
            dataOutputStream = output;
            dataInputStream = input;
        }

        public void run() {
            byte[] incomingBuff = new byte[256]; // ToDo: need to be set buffer size dynamically

//            if (mBluetoothDevice == null) {
//                Log.d(TAG, "No device found.");
//                return;
//            }

            while (true) {
                if (Thread.interrupted()) {
                    break;
                }

                try {
                    // Send Command and receive response
                    if (BtCommand.length() > 0) {

                        int size = BtCommand.length();
                        dataOutputStream.writeInt(size);

                        byte[] buf = BtCommand.getBytes(StandardCharsets.UTF_8);
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
            }

        }
    }

    void ResponseDispatcher(String commandPacket, String response) {
        boolean updateUi = false;
        String command;

        try {
            List<String> commandList = Arrays.asList(commandPacket.split(","));
            if (commandList.size() > 1){
                command = commandList.get(0);
            }
            else {
                command = commandPacket;
            }

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
                        // added 1.0.2
                        String mClientIpAddress = list.get(++i);
                    }

                    handler.obtainMessage(
                            Constants.MESSAGE_GETCONFIG, response)
                            .sendToTarget();
                    break;
                case CMD_SETCONFIG:  // setconfig,aaa,bbb,ccc

                    handler.obtainMessage(
                            Constants.MESSAGE_SETCONFIG, response)
                            .sendToTarget();
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
                    handler.obtainMessage(
                            Constants.MESSAGE_GETLOGS, response)
                            .sendToTarget();
                    updateUi = true;
                    break;
                case CMD_GETBARCODE:
                    // do something
                    break;
                case CMD_GETSTATUS:
                    if (!response.equals(RES_NAK)) {
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

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    void UpdateUi(int state) {
        switch (state) {
            case PUBLISHER_STATE_DISCONNECTED:
            case PUBLISHER_STATE_START:
                mButton_Disconnect.setEnabled(false);    // Disconnect button
                mButton_Disconnect.setText(getString(R.string.disconnect));
                mButton_getVersion.setEnabled(false);
                mButton_getConfig.setEnabled(false);
                mButton_setConfig.setEnabled(false);
                mButton_deviceStart.setEnabled(false);
                mButton_deviceStart.setText(getString(R.string.deviceStart));
                mButton_deviceSuspend.setEnabled(false);
                mButton_deviceSuspend.setText(getString(R.string.deviceSuspend));
                mButton_devicePowerOff.setEnabled(false);
                mButton_deviceRestart.setEnabled(false);
                mButton_getLogs.setEnabled(false);
                mButton_clearLogs.setEnabled(false);
                mButton_saveLogs.setEnabled(false);
                mEditText_DeviceName.setText(""); // set empty
                mEditText_IpAddress.setText("");
                mEditText_PortNumber.setText("");
                break;
            case PUBLISHER_STATE_NEUTRAL:
                mButton_Disconnect.setEnabled(true);
                mButton_Disconnect.setText(getString(R.string.disconnect));
                mButton_getVersion.setEnabled(true);
                mButton_getConfig.setEnabled(true);
                mButton_setConfig.setEnabled(true);
                mButton_deviceStart.setEnabled(true);
                mButton_deviceStart.setText(getString(R.string.deviceStart));
                mButton_deviceSuspend.setEnabled(false);
                mButton_deviceSuspend.setText(getString(R.string.deviceSuspend));
                mButton_devicePowerOff.setEnabled(true);
                mButton_deviceRestart.setEnabled(true);
                mButton_getLogs.setEnabled(true);
                mButton_clearLogs.setEnabled(true);
                mButton_saveLogs.setEnabled(true);
                break;
            case PUBLISHER_STATE_ACTIVE:
                mButton_Disconnect.setEnabled(true);
                mButton_Disconnect.setText(getString(R.string.disconnect));
                mButton_getVersion.setEnabled(true);
                mButton_getConfig.setEnabled(true);
                mButton_setConfig.setEnabled(true);
                mButton_deviceStart.setEnabled(true);
                mButton_deviceStart.setText(getString(R.string.deviceStop));
                mButton_deviceSuspend.setEnabled(true);
                mButton_deviceSuspend.setText(getString(R.string.deviceSuspend));
                mButton_devicePowerOff.setEnabled(true);
                mButton_deviceRestart.setEnabled(true);
                break;
            case PUBLISHER_STATE_IDLE:
                mButton_Disconnect.setEnabled(true);
                mButton_Disconnect.setText(getString(R.string.disconnect));
                mButton_getVersion.setEnabled(true);
                mButton_getConfig.setEnabled(true);
                mButton_setConfig.setEnabled(true);
                mButton_deviceStart.setEnabled(true);
                mButton_deviceStart.setText(getString(R.string.deviceStop));
                mButton_deviceSuspend.setEnabled(true);
                mButton_deviceSuspend.setText(getString(R.string.deviceResume));
                mButton_devicePowerOff.setEnabled(true);
                mButton_deviceRestart.setEnabled(true);
                break;
            default:
                break;
        }
    }
    // ----- End of RfComm -----------------

    // filer
    InputFilter inputFilter = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            if (source.toString().matches("^[0-9a-zA-Z@¥.¥_¥¥-]+$")) {
                return source;
            } else {
                return "";
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // GUI items
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
        mButton_deviceSuspend = findViewById(R.id.button_deviceSuspend);
        mButton_deviceSuspend.setOnClickListener(this);
        mButton_devicePowerOff = findViewById(R.id.button_devicePowerOff);
        mButton_devicePowerOff.setOnClickListener(this);
        mButton_deviceRestart = findViewById(R.id.button_deviceRestart);
        mButton_deviceRestart.setOnClickListener(this);
        mButton_getLogs = findViewById(R.id.button_getLogs);
        mButton_getLogs.setOnClickListener(this);
        mButton_clearLogs = findViewById(R.id.button_clearLogs);
        mButton_clearLogs.setOnClickListener(this);
        mButton_saveLogs = findViewById(R.id.button_saveLogs);
        mButton_saveLogs.setOnClickListener(this);

        // ListViewにArrayAdapterを設定する
        // リスト項目とListViewを対応付けるArrayAdapterを用意する
        mAdapterLogs = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mLogsList);
        mListView_Logs = (ListView)findViewById(R.id.listview_deviceLogs);
        mListView_Logs.setAdapter(mAdapterLogs);

        mEditText_DeviceName = findViewById(R.id.edit_deviceName);
        mEditText_IpAddress = findViewById(R.id.edit_ipAddress);
        mEditText_PortNumber = findViewById(R.id.edit_portNumber);

//        // create a filter array
//        InputFilter[] filters = new InputFilter[] { inputFilter };
//
//        mEditText_IpAddress.setFilters(filters);
//        mEditText_PortNumber.setFilters(filters);

        mTextView_WdpVersion = findViewById(R.id.textview_wdpversion);

        UpdateUi(PUBLISHER_STATE_DISCONNECTED);

        // Investigate Android device supports BLE or not
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_is_not_supported, Toast.LENGTH_SHORT).show();
            finish();    // declare app is finished
            return;
        }

        // Android M Permission check
        // Resolving for the Android6.0(sdk>23) Bluetooth Scan problem
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
            }
        }

        // Get the Bluetooth adapter

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (null == mBluetoothAdapter) {    // Android device is not supported Bluetooth
            Toast.makeText(this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT).show();
            finish();    // declare the app is finished
            return;
        }

        // ----- RfComm -----

        Log.d(TAG, "onCreate");


        // Initialize Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.d(TAG, "This device doesn't support Bluetooth.");
        }
        // ---- End of RfComm -----
    }

    private void SendCommand(String command, String params) {
        connect();

        CommandState = command;
        String CommandParams;
        if (params == null)
            CommandParams = command;
        else
            CommandParams = command + "," + params;

        btClientThread = new BTClientThread(CommandParams, mDataOutputStream, mDataInputStream);
        btClientThread.start();
    }

    @Override
    public void onClick(View v) {
//        Toast.makeText(this, "onClick is invoked.", Toast.LENGTH_SHORT).show();

        if (mButton_Disconnect.getId() == v.getId()) {
            mButton_Disconnect.setEnabled(false);    // gray-out button for preventing double-tap
            disconnect();            // disconnect
            return;
        }
        if (mButton_getVersion.getId() == v.getId()) {
            mButton_getVersion.setEnabled(false);    // gray-out for preventing double-tap
            SendCommand(CMD_GETVERSION, null);
            return;
        }
        if (mButton_getConfig.getId() == v.getId()) {
            mButton_getConfig.setEnabled(false);    //
            SendCommand(CMD_GETCONFIG, null);
        }
        if (mButton_setConfig.getId() == v.getId()) {
            mButton_setConfig.setEnabled(false);    //
            String params = mEditText_DeviceName.getText() + "," +
                    mEditText_IpAddress.getText() + "," +
                    mEditText_PortNumber.getText();
            SendCommand(CMD_SETCONFIG, params);
        }
        if (mButton_deviceStart.getId() == v.getId()) {
            mButton_deviceStart.setEnabled(false);    //

            String s = getString(R.string.deviceStart);
            String c = CMD_START;
            if(mDeviceState == PUBLISHER_STATE_NEUTRAL) {
                s = getString(R.string.deviceStop);
                c = CMD_START;
            }
            else if (mDeviceState == PUBLISHER_STATE_ACTIVE || mDeviceState == PUBLISHER_STATE_IDLE) {
                s = getString(R.string.deviceStart);
                c = CMD_STOP;
            }
            mButton_deviceStart.setText(s);
            SendCommand(c, null);
        }
        if (mButton_deviceSuspend.getId() == v.getId()) {
            mButton_deviceSuspend.setEnabled(false);    //

            String s = getString(R.string.deviceSuspend);
            String c = CMD_SUSPEND;
            if(mDeviceState == PUBLISHER_STATE_ACTIVE ) {
                s = getString(R.string.deviceResume);
                c = CMD_SUSPEND;
            }
            else if (mDeviceState == PUBLISHER_STATE_IDLE) {
                s = getString(R.string.deviceSuspend);
                c = CMD_RESUME;
            }
            mButton_deviceSuspend.setText(s);
            SendCommand(c, null);
        }
        if (mButton_devicePowerOff.getId() == v.getId()) {
            mButton_devicePowerOff.setEnabled(false);    //
            SendCommand(CMD_POWEROFF, null);
        }
        if (mButton_deviceRestart.getId() == v.getId()) {
            mButton_deviceRestart.setEnabled(false);    //
            SendCommand(CMD_RESTART, null);
        }

        // Logs area
        if (mButton_getLogs.getId() == v.getId()) {
            mButton_getLogs.setEnabled(false);    //
            SendCommand(CMD_GETLOGS, null);
        }
        if (mButton_clearLogs.getId() == v.getId()) {
//            mButton_clearLogs.setEnabled(false);
            this.mAdapterLogs.clear();  // clear all
            this.mAdapterLogs.notifyDataSetChanged(); // notify
        }
        if (mButton_saveLogs.getId() == v.getId()) {
//            mButton_saveLogs.setEnabled(false);    //
            // do something
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

    // cases of displaying the 1st time, return from the other page, or resume from the pause
    @Override
    protected void onResume() {
        super.onResume();

        // request to enable the BT function on Android device
        requestBluetoothFeature();

        mDeviceName = "";
        mServerIpAddress = "";
        mServerPortNumberBase = "";
        mWdpVersion = "";

        // Settings for GUI items
        mEditText_DeviceName.setText(mDeviceName);
        mEditText_IpAddress.setText(mServerIpAddress);
        mEditText_PortNumber.setText(mServerPortNumberBase);
        mTextView_WdpVersion.setText(mWdpVersion);

        // Try GetStatus if the device address is not empty
        //   Is WdP device？
        //    Yes, Read DeviceState value of WdP
        if (!mDeviceAddress.equals("")) {
            SendCommand(CMD_GETSTATUS, null);
        }
    }

    // In the case of going to the background by transition to another activity
    @Override
    protected void onPause() {
        super.onPause();

        disconnect();
    }

    // Just before the activity finish
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mBluetoothGatt) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    // Request to enable the Bluetooth function of the Android device
    private void requestBluetoothFeature() {
        if (mBluetoothAdapter.isEnabled()) {
            return;
        }
        // When device's Bluetooth function is disabled, request to activate (open a dialog)
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
    }

    // Result of operation for the feature activation
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH: // Request to enable Bluetooth
                if (Activity.RESULT_CANCELED == resultCode) {    // rejected
                    Toast.makeText(this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT).show();
                    finish();    // declare finish the app
                    return;
                }
                break;
            case REQUEST_CONNECT_DEVICE: // request to access the device
                String strDeviceName;
                if (Activity.RESULT_OK == resultCode) {
                    // Get the information from the device list activity
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

    // Option menu creation procedure
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    // Item selection procedure of option menu
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

    // Connection
    private void connect() {
        if (mDeviceAddress.equals("")) {    // Nothing to do when DeviceAddress is empty
            return;
        }

        if (mBluetoothDevice != null) {
            return;
        }
//        if (null != mBluetoothGatt) {    // Already connected or under the connection in case of mBluetoothGatt is null
//            return;
//        }

        // 接続
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);

        try {
            // Create the socket
            if (mBluetoothSocket == null)
                mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(
                        Constants.BT_UUID);

            // Connect to the created socket
            if (!mBTSocketConnected) {
                mBluetoothSocket.connect();
                mBTSocketConnected = true;
                // send message to handler
                handler.obtainMessage(
                        Constants.MESSAGE_BT,
                        R.string.connect + " " + mBluetoothDevice.getName())
                        .sendToTarget();

                //                        OutputStream os = bluetoothSocket.getOutputStream();
                mDataInputStream = new DataInputStream(mBluetoothSocket.getInputStream());
                mDataOutputStream = new DataOutputStream(mBluetoothSocket.getOutputStream());
            }
        } catch (
                IOException e) {
            e.printStackTrace();
        }
    }

    // Disconnection
    private void disconnect() {
        // ---- RfComm ------
        if (btClientThread != null) {
            btClientThread.interrupt();
            btClientThread = null;
        }

        // Close BluetoothSocket
        if (mBTSocketConnected) {
            if (mBluetoothSocket != null) {
                try {
                    mDataInputStream.close();
                    mDataOutputStream.close();
                    mBluetoothSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mDataInputStream = null;
                mDataOutputStream = null;
                mBluetoothSocket = null;

                mBTSocketConnected = false;

                handler.obtainMessage(
                        Constants.MESSAGE_BT,
                        "DISCONNECTED - Close BluetoothSocket")
                        .sendToTarget();
            }

            if (mBluetoothDevice != null)
                mBluetoothDevice = null;
        }
        // ------------------

        // set the state and update UI
        mDeviceState = PUBLISHER_STATE_DISCONNECTED;
        handler.obtainMessage(
                Constants.MESSAGE_UPDATEUI, mDeviceState)
                .sendToTarget();
    }

//    // Read the GATT characteristic
//    private void readCharacteristic(UUID uuid_service, UUID uuid_characteristic) {
//        if (null == mBluetoothGatt) {
//            return;
//        }
//        BluetoothGattCharacteristic bleChar = mBluetoothGatt.getService(uuid_service).getCharacteristic(uuid_characteristic);
//        mBluetoothGatt.readCharacteristic(bleChar);
//    }
//
//    // Setting the GATT characteristic notification
//    private void setCharacteristicNotification(UUID uuid_service, UUID uuid_characteristic, boolean enable) {
//        if (null == mBluetoothGatt) {
//            return;
//        }
//        BluetoothGattCharacteristic bleChar = mBluetoothGatt.getService(uuid_service).getCharacteristic(uuid_characteristic);
//        mBluetoothGatt.setCharacteristicNotification(bleChar, enable);
//        BluetoothGattDescriptor descriptor = bleChar.getDescriptor(UUID_NOTIFY);
//        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//        mBluetoothGatt.writeDescriptor(descriptor);
//    }
//
//    // Write the GATT characteristic
//    private void writeCharacteristic(UUID uuid_service, UUID uuid_characteristic, String string) {
//        if (null == mBluetoothGatt) {
//            return;
//        }
//        BluetoothGattCharacteristic bleChar = mBluetoothGatt.getService(uuid_service).getCharacteristic(uuid_characteristic);
//        bleChar.setValue(string);
//        mBluetoothGatt.writeCharacteristic(bleChar);
//    }
}
