package com.wacom.ogura.tsuyoshi.wdcontroller;


import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import java.nio.ByteBuffer;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener
{
    // Constants（Bluetooth LE Gatt UUID）
    // Private Service
    private static final UUID UUID_SERVICE_PRIVATE         = UUID.fromString( "FF6B1160-8FE6-11E7-ABC4-CEC278B6B50A" );
    private static final UUID UUID_CHARACTERISTIC_PRIVATE1 = UUID.fromString( "FF6B1426-8FE6-11E7-ABC4-CEC278B6B50A" );
    private static final UUID UUID_CHARACTERISTIC_PRIVATE2 = UUID.fromString( "FF6B1548-8FE6-11E7-ABC4-CEC278B6B50A" );
    // for Notification
    private static final UUID UUID_NOTIFY                  = UUID.fromString( "00002902-0000-1000-8000-00805f9b34fb" );

    // Constants
    private static final int    REQUEST_ENABLEBLUETOOTH = 1; // Bluetooth機能の有効化要求時の識別コード
    private static final int    REQUEST_CONNECTDEVICE   = 2; // デバイス接続要求時の識別コード
    private static final int    PERMISSION_REQUEST_COARSE_LOCATION = 1;  // Android 6(sdk>23) Bluetooth Scan

    // member valuables
    private BluetoothAdapter mBluetoothAdapter;    // BluetoothAdapter : Bluetooth処理で必要
    private String        mDeviceAddress = "";    // デバイスアドレス
    private BluetoothGatt mBluetoothGatt = null;    // Gattサービスの検索、キャラスタリスティックの読み書き

    private String mDefaultDeviceName = "Wacom Clipboard";
    private String mDefaultIpAddress = "192.168.0.7";
    private String mDefaultPortNumber = "1337";

    // GUI items
    private Button mButton_Connect;    // Connect button
    private Button mButton_Disconnect;    // Disconnect button
    private Button   mButton_ReadChara1;    // キャラクタリスティック１の読み込みボタン
    private Button   mButton_ReadChara2;    // キャラクタリスティック２の読み込みボタン
    private CheckBox mCheckBox_NotifyChara1;    // キャラクタリスティック１の変更通知ON/OFFチェックボックス
    private Button   mButton_WriteHello;        // キャラクタリスティック２への「Hello」書き込みボタン
    private Button   mButton_WriteWorld;        // キャラクタリスティック２への「World」書き込みボタン

    private EditText mEditText_DeviceName;
    private EditText mEditText_IpAddress;
    private EditText mEditText_PortNumber;

    // ---- RFCOMM ------------------------
    static final String TAG = "BTTEST1";
    BluetoothAdapter bluetoothAdapter;

    TextView btStatusTextView;
    TextView tempTextView;

    BTClientThread btClientThread;

    final Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {

            String s;

            switch(msg.what){
                case Constants.MESSAGE_BT:
                    s = (String) msg.obj;
                    if(s != null){
                        btStatusTextView.setText(s);
                    }
                    break;
                case Constants.MESSAGE_TEMP:
                    s = (String) msg.obj;
                    if(s != null){
                        tempTextView.setText(s);
                    }
                    break;
            }
        }
    };


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(Constants.STATE_TEMP, tempTextView.getText().toString());
    }

    public class BTClientThread extends Thread {

        InputStream inputStream;
        OutputStream outputStrem;
        BluetoothSocket bluetoothSocket;

        public void run() {

            byte[] incomingBuff = new byte[64];

            BluetoothDevice bluetoothDevice = null;
            Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
            for(BluetoothDevice device : devices){
                if(device.getName().equals(Constants.BT_DEVICE)) {
                    bluetoothDevice = device;
                    break;
                }
            }

            if(bluetoothDevice == null){
                Log.d(TAG, "No device found.");
                return;
            }

            try {

                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(
                        Constants.BT_UUID);

                while(true) {

                    if(Thread.interrupted()){
                        break;
                    }

                    try {
                        bluetoothSocket.connect();

                        handler.obtainMessage(
                                Constants.MESSAGE_BT,
                                "CONNECTED " + bluetoothDevice.getName())
                                .sendToTarget();

                        inputStream = bluetoothSocket.getInputStream();
                        outputStrem = bluetoothSocket.getOutputStream();

                        while (true) {

                            if (Thread.interrupted()) {
                                break;
                            }

                            // Send Command
                            String command = "GET:TEMP";
                            outputStrem.write(command.getBytes());
                            // Read Response
                            int incomingBytes = inputStream.read(incomingBuff);
                            byte[] buff = new byte[incomingBytes];
                            System.arraycopy(incomingBuff, 0, buff, 0, incomingBytes);
                            String s = new String(buff, StandardCharsets.UTF_8);

                            // Show Result to UI
                            handler.obtainMessage(
                                    Constants.MESSAGE_TEMP,
                                    s)
                                    .sendToTarget();

                            // Update again in a few seconds
                            Thread.sleep(3000);
                        }

                    } catch (IOException e) {
                        // connect will throw IOException immediately
                        // when it's disconnected.
                        Log.d(TAG, e.getMessage());
                    }

                    handler.obtainMessage(
                            Constants.MESSAGE_BT,
                            "DISCONNECTED")
                            .sendToTarget();

                    // Re-try after 3 sec
                    Thread.sleep(3 * 1000);
                }

            }catch (InterruptedException e){
                e.printStackTrace();
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            if(bluetoothSocket != null){
                try {
                    bluetoothSocket.close();
                } catch (IOException e) {}
                bluetoothSocket = null;
            }

            handler.obtainMessage(
                    Constants.MESSAGE_BT,
                    "DISCONNECTED - Exit BTClientThread")
                    .sendToTarget();
        }
    }
    // ----- End of RFCOMM -----------------

    // BluetoothGattコールバックオブジェクト
    private final BluetoothGattCallback mGattcallback = new BluetoothGattCallback()
    {
        // 接続状態変更（connectGatt()の結果として呼ばれる。）
        @Override
        public void onConnectionStateChange( BluetoothGatt gatt, int status, int newState )
        {
            if( BluetoothGatt.GATT_SUCCESS != status )
            {
                return;
            }

            if( BluetoothProfile.STATE_CONNECTED == newState )
            {    // 接続完了
                mBluetoothGatt.discoverServices();    // サービス検索
                runOnUiThread( new Runnable()
                {
                    public void run()
                    {
                        // GUIアイテムの有効無効の設定
                        // 切断ボタンを有効にする
                        mButton_Disconnect.setEnabled( true );
                    }
                } );
                return;
            }
            if( BluetoothProfile.STATE_DISCONNECTED == newState )
            {    // 切断完了（接続可能範囲から外れて切断された）
                // 接続可能範囲に入ったら自動接続するために、mBluetoothGatt.connect()を呼び出す。
                mBluetoothGatt.connect();
                runOnUiThread( new Runnable()
                {
                    public void run()
                    {
                        // GUIアイテムの有効無効の設定
                        // 読み込みボタンを無効にする（通知チェックボックスはチェック状態を維持。通知ONで切断した場合、再接続時に通知は再開するので）
//                        mButton_ReadChara1.setEnabled( false );
//                        mButton_ReadChara2.setEnabled( false );
//                        mCheckBox_NotifyChara1.setEnabled( false );
//                        mButton_WriteHello.setEnabled( false );
//                        mButton_WriteWorld.setEnabled( false );
                    }
                } );
                return;
            }
        }

        // サービス検索が完了したときの処理（mBluetoothGatt.discoverServices()の結果として呼ばれる。）
        @Override
        public void onServicesDiscovered( BluetoothGatt gatt, int status )
        {
            if( BluetoothGatt.GATT_SUCCESS != status )
            {
                return;
            }

            // 発見されたサービスのループ
            for( BluetoothGattService service : gatt.getServices() )
            {
                // サービスごとに個別の処理
                if( ( null == service ) || ( null == service.getUuid() ) )
                {
                    continue;
                }
                if( UUID_SERVICE_PRIVATE.equals( service.getUuid() ) )
                {    // プライベートサービス
                    runOnUiThread( new Runnable()
                    {
                        public void run()
                        {
                            // GUIアイテムの有効無効の設定
                            mButton_ReadChara1.setEnabled( true );
                            mButton_ReadChara2.setEnabled( true );
                            mCheckBox_NotifyChara1.setEnabled( true );
                            mButton_WriteHello.setEnabled( true );
                            mButton_WriteWorld.setEnabled( true );
                        }
                    } );
                    continue;
                }
            }
        }

        // キャラクタリスティックが読み込まれたときの処理
        @Override
        public void onCharacteristicRead( BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status )
        {
            if( BluetoothGatt.GATT_SUCCESS != status )
            {
                return;
            }
            // キャラクタリスティックごとに個別の処理
            if( UUID_CHARACTERISTIC_PRIVATE1.equals( characteristic.getUuid() ) )
            {    // キャラクタリスティック１：データサイズは、2バイト（数値を想定。0～65,535）
                byte[]       byteChara = characteristic.getValue();
                ByteBuffer   bb        = ByteBuffer.wrap( byteChara );
                final String strChara  = String.valueOf( bb.getShort() );
                runOnUiThread( new Runnable()
                {
                    public void run()
                    {
                        // GUIアイテムへの反映
 //                       ( (TextView)findViewById( R.id.textview_readchara1 ) ).setText( strChara );
                    }
                } );
                return;
            }
            if( UUID_CHARACTERISTIC_PRIVATE2.equals( characteristic.getUuid() ) )
            {    // キャラクタリスティック２：データサイズは、8バイト（文字列を想定。半角文字8文字）
                final String strChara = characteristic.getStringValue( 0 );
                runOnUiThread( new Runnable()
                {
                    public void run()
                    {
                        // GUIアイテムへの反映
        //                ( (TextView)findViewById( R.id.textview_readchara2 ) ).setText( strChara );
                    }
                } );
                return;
            }
        }

        // キャラクタリスティック変更が通知されたときの処理
        @Override
        public void onCharacteristicChanged( BluetoothGatt gatt, BluetoothGattCharacteristic characteristic )
        {
            // キャラクタリスティックごとに個別の処理
            if( UUID_CHARACTERISTIC_PRIVATE1.equals( characteristic.getUuid() ) )
            {    // キャラクタリスティック１：データサイズは、2バイト（数値を想定。0～65,535）
                byte[]       byteChara = characteristic.getValue();
                ByteBuffer   bb        = ByteBuffer.wrap( byteChara );
                final String strChara  = String.valueOf( bb.getShort() );
                runOnUiThread( new Runnable()
                {
                    public void run()
                    {
                        // GUIアイテムへの反映
       //                 ( (TextView)findViewById( R.id.textview_notifychara1 ) ).setText( strChara );
                    }
                } );
                return;
            }
        }

        // キャラクタリスティックが書き込まれたときの処理
        @Override
        public void onCharacteristicWrite( BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status )
        {
            if( BluetoothGatt.GATT_SUCCESS != status )
            {
                return;
            }
            // キャラクタリスティックごとに個別の処理
            if( UUID_CHARACTERISTIC_PRIVATE2.equals( characteristic.getUuid() ) )
            {    // キャラクタリスティック２：データサイズは、8バイト（文字列を想定。半角文字8文字）
                runOnUiThread( new Runnable()
                {
                    public void run()
                    {
                        // GUIアイテムの有効無効の設定
                        // 書き込みボタンを有効にする
                        mButton_WriteHello.setEnabled( true );
                        mButton_WriteWorld.setEnabled( true );
                    }
                } );
                return;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // GUI items
        mButton_Connect = (Button)findViewById( R.id.button_connect );
        mButton_Connect.setOnClickListener( this );
        mButton_Disconnect = (Button)findViewById( R.id.button_disconnect );
        mButton_Disconnect.setOnClickListener( this );
      //  mButton_ReadChara1 = (Button)findViewById( R.id.button_readchara1 );
      //  mButton_ReadChara1.setOnClickListener( this );
      //  mButton_ReadChara2 = (Button)findViewById( R.id.button_readchara2 );
      //  mButton_ReadChara2.setOnClickListener( this );
      //  mCheckBox_NotifyChara1 = (CheckBox)findViewById( R.id.checkbox_notifychara1 );
      //  mCheckBox_NotifyChara1.setOnClickListener( this );
      //  mButton_WriteHello = (Button)findViewById( R.id.button_writehello );
      //  mButton_WriteHello.setOnClickListener( this );
      //  mButton_WriteWorld = (Button)findViewById( R.id.button_writeworld );
      //  mButton_WriteWorld.setOnClickListener( this );

        mEditText_DeviceName = (EditText)findViewById( R.id.edit_deviceName );
        mEditText_IpAddress = (EditText)findViewById( R.id.edit_ipAddress );
        mEditText_PortNumber = (EditText)findViewById( R.id.edit_portNumber );

        // Android端末がBLEをサポートしてるかの確認
        if( !getPackageManager().hasSystemFeature( PackageManager.FEATURE_BLUETOOTH_LE ) )
        {
            Toast.makeText( this, R.string.ble_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
            return;
        }

        // Android M Permission check
        // Resolving for the Android6.0(sdk>23) Bluetooth Scan problem
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
            }
        }

        // Bluetoothアダプタの取得
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService( Context.BLUETOOTH_SERVICE );
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if( null == mBluetoothAdapter )
        {    // Android端末がBluetoothをサポートしていない
            Toast.makeText( this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
            return;
        }

        // ----- RFCOMM -----
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);

        // Find Views
     //   btStatusTextView = (TextView) findViewById(R.id.btStatusTextView);
      //  tempTextView = (TextView) findViewById(R.id.tempTextView);

        if(savedInstanceState != null){
            String temp = savedInstanceState.getString(Constants.STATE_TEMP);
            tempTextView.setText(temp);
        }

        // Initialize Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if( bluetoothAdapter == null ){
            Log.d(TAG, "This device doesn't support Bluetooth.");
        }

        // ---- End of RFCOMM -----
    }

    @Override
    public void onClick( View v )
    {
        if( mButton_Connect.getId() == v.getId() )
        {
            mButton_Connect.setEnabled( false );    // 接続ボタンの無効化（連打対策）
            connect();            // connect
            return;
        }
        if( mButton_Disconnect.getId() == v.getId() )
        {
            mButton_Disconnect.setEnabled( false );    // 切断ボタンの無効化（連打対策）
            disconnect();            // disconnect
            return;
        }
//        if( mButton_ReadChara1.getId() == v.getId() )
//        {
//            readCharacteristic( UUID_SERVICE_PRIVATE, UUID_CHARACTERISTIC_PRIVATE1 );
//            return;
//        }
//        if( mButton_ReadChara2.getId() == v.getId() )
//        {
//            readCharacteristic( UUID_SERVICE_PRIVATE, UUID_CHARACTERISTIC_PRIVATE2 );
//            return;
//        }
//        if( mCheckBox_NotifyChara1.getId() == v.getId() )
//        {
//            setCharacteristicNotification( UUID_SERVICE_PRIVATE, UUID_CHARACTERISTIC_PRIVATE1, mCheckBox_NotifyChara1.isChecked() );
//            return;
//        }
//        if( mButton_WriteHello.getId() == v.getId() )
//        {
//            mButton_WriteHello.setEnabled( false );    // 書き込みボタンの無効化（連打対策）
//            mButton_WriteWorld.setEnabled( false );    // 書き込みボタンの無効化（連打対策）
//            writeCharacteristic( UUID_SERVICE_PRIVATE, UUID_CHARACTERISTIC_PRIVATE2, "Hello" );
//            return;
//        }
//        if( mButton_WriteWorld.getId() == v.getId() )
//        {
//            mButton_WriteHello.setEnabled( false );    // 書き込みボタンの無効化（連打対策）
//            mButton_WriteWorld.setEnabled( false );    // 書き込みボタンの無効化（連打対策）
//            writeCharacteristic( UUID_SERVICE_PRIVATE, UUID_CHARACTERISTIC_PRIVATE2, "World" );
//            return;
//        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {
                    Toast.makeText(this, R.string.bluetooth_scan_need_permission, Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    // 初回表示時、および、ポーズからの復帰時
    @Override
    protected void onResume()
    {
        super.onResume();

        // --- RFCOMM ------
        btClientThread = new BTClientThread();
        btClientThread.start();
        // ---- End of RFCOMM -----


        // Android端末のBluetooth機能の有効化要求
        requestBluetoothFeature();

        // GUIアイテムの有効無効の設定
        mButton_Connect.setEnabled( false );
        mButton_Disconnect.setEnabled( false );
//        mButton_ReadChara1.setEnabled( false );
//        mButton_ReadChara2.setEnabled( false );
//        mCheckBox_NotifyChara1.setChecked( false );
//        mCheckBox_NotifyChara1.setEnabled( false );
//        mButton_WriteHello.setEnabled( false );
//        mButton_WriteWorld.setEnabled( false );

        mEditText_DeviceName.setText(mDefaultDeviceName);
        mEditText_IpAddress.setText(mDefaultIpAddress);
        mEditText_PortNumber.setText(mDefaultPortNumber);


        // デバイスアドレスが空でなければ、接続ボタンを有効にする。
        if( !mDeviceAddress.equals( "" ) )
        {
            mButton_Connect.setEnabled( true );
        }

        // 接続ボタンを押す
        mButton_Connect.callOnClick();
    }

    // 別のアクティビティ（か別のアプリ）に移行したことで、バックグラウンドに追いやられた時
    @Override
    protected void onPause()
    {
        super.onPause();

        // ---- RFCOMM -----
        if(btClientThread != null){
            btClientThread.interrupt();
            btClientThread = null;
        }
        // --- End of RFCOMM ----

        // 切断
        disconnect();
    }

    // アクティビティの終了直前
    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        if( null != mBluetoothGatt )
        {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    // Android端末のBluetooth機能の有効化要求
    private void requestBluetoothFeature()
    {
        if( mBluetoothAdapter.isEnabled() )
        {
            return;
        }
        // デバイスのBluetooth機能が有効になっていないときは、有効化要求（ダイアログ表示）
        Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
        startActivityForResult( enableBtIntent, REQUEST_ENABLEBLUETOOTH );
    }


    // 機能の有効化ダイアログの操作結果
    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data )
    {
        switch( requestCode )
        {
            case REQUEST_ENABLEBLUETOOTH: // Bluetooth有効化要求
                if( Activity.RESULT_CANCELED == resultCode )
                {    // 有効にされなかった
                    Toast.makeText( this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT ).show();
                    finish();    // アプリ終了宣言
                    return;
                }
                break;
            case REQUEST_CONNECTDEVICE: // デバイス接続要求
                String strDeviceName;
                if( Activity.RESULT_OK == resultCode )
                {
                    // デバイスリストアクティビティからの情報の取得
                    strDeviceName = data.getStringExtra( DeviceListActivity.EXTRAS_DEVICE_NAME );
                    mDeviceAddress = data.getStringExtra( DeviceListActivity.EXTRAS_DEVICE_ADDRESS );
                }
                else
                {
                    strDeviceName = "";
                    mDeviceAddress = "";
                }
                ( (TextView)findViewById( R.id.textview_devicename ) ).setText( strDeviceName );
                ( (TextView)findViewById( R.id.textview_deviceaddress ) ).setText( mDeviceAddress );
     //           ( (TextView)findViewById( R.id.textview_readchara1 ) ).setText( "" );
     //           ( (TextView)findViewById( R.id.textview_readchara2 ) ).setText( "" );
     //           ( (TextView)findViewById( R.id.textview_notifychara1 ) ).setText( "" );
                break;
        }
        super.onActivityResult( requestCode, resultCode, data );
    }

    // オプションメニュー作成時の処理
    @Override
    public boolean onCreateOptionsMenu( Menu menu )
    {
        getMenuInflater().inflate( R.menu.activity_main, menu );
        return true;
    }

    // オプションメニューのアイテム選択時の処理
    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        switch( item.getItemId() )
        {
            case R.id.menuitem_search:
                Intent devicelistactivityIntent = new Intent( this, DeviceListActivity.class );
                startActivityForResult( devicelistactivityIntent, REQUEST_CONNECTDEVICE );
                return true;
        }
        return false;
    }

    // 接続
    private void connect()
    {
        if( mDeviceAddress.equals( "" ) )
        {    // DeviceAddressが空の場合は処理しない
            return;
        }

        if( null != mBluetoothGatt )
        {    // mBluetoothGattがnullでないなら接続済みか、接続中。
            return;
        }

        // 接続
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice( mDeviceAddress );
        mBluetoothGatt = device.connectGatt( this, false, mGattcallback );
    }

    // 切断
    private void disconnect()
    {
        if( null == mBluetoothGatt )
        {
            return;
        }

        // 切断
        //   mBluetoothGatt.disconnect()ではなく、mBluetoothGatt.close()しオブジェクトを解放する。
        //   理由：「ユーザーの意思による切断」と「接続範囲から外れた切断」を区別するため。
        //   ①「ユーザーの意思による切断」は、mBluetoothGattオブジェクトを解放する。再接続は、オブジェクト構築から。
        //   ②「接続可能範囲から外れた切断」は、内部処理でmBluetoothGatt.disconnect()処理が実施される。
        //     切断時のコールバックでmBluetoothGatt.connect()を呼んでおくと、接続可能範囲に入ったら自動接続する。
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        // GUIアイテムの有効無効の設定
        // 接続ボタンのみ有効にする
        mButton_Connect.setEnabled( true );
        mButton_Disconnect.setEnabled( false );
//        mButton_ReadChara1.setEnabled( false );
//        mButton_ReadChara2.setEnabled( false );
//        mCheckBox_NotifyChara1.setChecked( false );
//        mCheckBox_NotifyChara1.setEnabled( false );
//        mButton_WriteHello.setEnabled( false );
//        mButton_WriteWorld.setEnabled( false );
    }

    // キャラクタリスティックの読み込み
    private void readCharacteristic( UUID uuid_service, UUID uuid_characteristic )
    {
        if( null == mBluetoothGatt )
        {
            return;
        }
        BluetoothGattCharacteristic blechar = mBluetoothGatt.getService( uuid_service ).getCharacteristic( uuid_characteristic );
        mBluetoothGatt.readCharacteristic( blechar );
    }

    // キャラクタリスティック通知の設定
    private void setCharacteristicNotification( UUID uuid_service, UUID uuid_characteristic, boolean enable )
    {
        if( null == mBluetoothGatt )
        {
            return;
        }
        BluetoothGattCharacteristic blechar = mBluetoothGatt.getService( uuid_service ).getCharacteristic( uuid_characteristic );
        mBluetoothGatt.setCharacteristicNotification( blechar, enable );
        BluetoothGattDescriptor descriptor = blechar.getDescriptor( UUID_NOTIFY );
        descriptor.setValue( BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE );
        mBluetoothGatt.writeDescriptor( descriptor );
    }

    // キャラクタリスティックの書き込み
    private void writeCharacteristic( UUID uuid_service, UUID uuid_characteristic, String string )
    {
        if( null == mBluetoothGatt )
        {
            return;
        }
        BluetoothGattCharacteristic blechar = mBluetoothGatt.getService( uuid_service ).getCharacteristic( uuid_characteristic );
        blechar.setValue( string );
        mBluetoothGatt.writeCharacteristic( blechar );
    }
}
