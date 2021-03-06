package e.kerry.kerobotnrf;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;


import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewDebug;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.w3c.dom.Text;

import static e.kerry.kerobotnrf.UartService.ACK_Value;
import static e.kerry.kerobotnrf.UartService.floatToByteArray;

public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener {
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;

    // 05.20.2020
    private static boolean gyroSwitch = false;

    TextView mRemoteRssiVal;
    RadioGroup mRg;
    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
    private ListView messageListView;
    private ArrayAdapter<String> listAdapter;
    private Button btnConnectDisconnect,btnSend;
    private EditText edtMessage;

    private Button b_Fwrd;
    private Button b_Right;
    private Button b_Stop;
    private Button b_Rvrse;
    private Button b_Left;
    private Button b_Ack;
    private Button btnGryoscopre;
    private Button btnJoystickView;

    // 2020.05.14 adding gyroscope
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private SensorEventListener rotationVectorEventListenser;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        messageListView = (ListView) findViewById(R.id.listMessage);
        listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
        messageListView.setAdapter(listAdapter);
        messageListView.setDivider(null);
        btnConnectDisconnect=(Button) findViewById(R.id.btn_select);
        btnSend=(Button) findViewById(R.id.sendButton);
        edtMessage = (EditText) findViewById(R.id.sendText);

        b_Fwrd = findViewById(R.id.b_Fwrd);
        b_Right = findViewById(R.id.b_Right);
        b_Stop = findViewById(R.id.b_Stop);
        b_Rvrse = findViewById(R.id.b_Rvrse);
        b_Left = findViewById(R.id.b_Left);
        b_Ack = findViewById(R.id.b_Ack);
        btnGryoscopre = findViewById(R.id.btnGryoscopre); // 05.20.2020 TaWei added
        btnJoystickView = findViewById(R.id.btnJoystickView); // 06.04.2020 TaWei added
        setButtonColors();
        b_Fwrd.setVisibility(View.INVISIBLE);
        b_Right.setVisibility(View.INVISIBLE);
        b_Stop.setVisibility(View.INVISIBLE);
        b_Rvrse.setVisibility(View.INVISIBLE);
        b_Left.setVisibility(View.INVISIBLE);
        b_Ack.setVisibility(View.INVISIBLE);
        btnGryoscopre.setVisibility(View.INVISIBLE);
        btnJoystickView.setVisibility(View.INVISIBLE);


        // Initialize the UartService Class
        service_init();

        // 2020.05.14 Ta-Wei edited
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        rotationVectorEventListenser = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                String message = "gyroON"; // change on artemis to receive gyroscope value
                byte[] value;
                if (gyroSwitch) {
                    float[] rotationMatrix = new float[16];
                    SensorManager.getRotationMatrixFromVector(
                            rotationMatrix, sensorEvent.values);
                    // Remap coordinate system
                    float[] remappedRotationMatrix = new float[16];
                    SensorManager.remapCoordinateSystem(rotationMatrix,
                            SensorManager.AXIS_X,
                            SensorManager.AXIS_Z,
                            remappedRotationMatrix);

                    // Convert to orientations
                    float[] orientations = new float[3];
                    SensorManager.getOrientation(remappedRotationMatrix, orientations);

                    for (int i = 0; i < 3; i++) {
                        orientations[i] = (float) (Math.toDegrees(orientations[i]));
                        // orientation[0] range from -90 to 90      flip the phone on the longer side
                        // orientation[1] range from -180 to 180    flip the phone on the shorter side
                        // orientation[2] range from -180 to 180    rotate the phone from left to right or right to left
                    }

                    if (orientations[0] > 0) {
                        getWindow().getDecorView().setBackgroundColor(Color.YELLOW);
                    } else if (orientations[0] < 0) {
                        getWindow().getDecorView().setBackgroundColor(Color.BLUE);
                    }

                    // value = ByteBuffer.allocate(4).putFloat(orientations[0]).array();;
                    // value = floatToByteArray(orientations[0]);
                    message = String.format(Locale.getDefault(), "%3.3f", orientations[0]);
                    value = message.getBytes();
                    mService.writeRXCharacteristic(value);

                    // Update the log with time stamp
                    String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                    listAdapter.add("[" + currentDateTimeString + "] TX: " + message);
                    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                    edtMessage.setText("");
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };
        // sensorManager.registerListener(rotationVectorEventListenser, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(rotationVectorEventListenser, rotationVectorSensor, 500000); // 500000 microseconds 2 message per second

        // 06.04.2020 Ta-Wei edited
        // JoystickView from https://github.com/efficientisoceles/JoystickView
        btnJoystickView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent newIntent = new Intent(getApplicationContext(), JoystickActivity.class);
                startActivity(newIntent);
            }
        });

        // Handle Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBtAdapter.isEnabled()) { //Request BlueTooth enable from user cuz not enabled
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else { //BlueTooth enabled so Start the DeviceListActivity Class to choose device
                    if (btnConnectDisconnect.getText().equals("Connect")){ //Button label is Connect

                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices

                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else { //Button label is Disconnect
                        //Disconnect button pressed
                        if (mDevice!=null)
                        {
                            mService.disconnect(); //UartService disconnect function
                            //Turn off command buttons
                            b_Fwrd.setVisibility(View.INVISIBLE);
                            b_Right.setVisibility(View.INVISIBLE);
                            b_Stop.setVisibility(View.INVISIBLE);
                            b_Rvrse.setVisibility(View.INVISIBLE);
                            b_Left.setVisibility(View.INVISIBLE);
                            btnGryoscopre.setVisibility(View.INVISIBLE);
                            btnJoystickView.setVisibility(View.INVISIBLE);
                        }
                    }
                }
            }
        });
        // Handle Send button
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText = (EditText) findViewById(R.id.sendText);
                String message = editText.getText().toString();
                byte[] value;
                try {
                    //send data to service ... converts utf-8 to decimal
                    value = message.getBytes("UTF-8");
                    mService.writeRXCharacteristic(value);//KHE
                    //Update the log with time stamp
                    String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                    listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
                    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                    edtMessage.setText("");
                } catch (UnsupportedEncodingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        });

        // Handle stop button click
        b_Stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "0";
                byte[] value;
                try {
                    setButtonColors();
                    //send data to service ... converts utf-8 to decimal
                    value = message.getBytes("UTF-8");
                    mService.writeRXCharacteristic(value);//KHE
                    //Update the log with time stamp
                    String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                    listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
                    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                    edtMessage.setText("");
                    //ViewCompat.setBackgroundTintList(b_Stop, ContextCompat.getColorStateList(getApplicationContext()
                    //        , android.R.color.holo_red_light));
                    ViewCompat.setBackgroundTintList(b_Stop, ContextCompat.getColorStateList(getApplicationContext()
                            ,R.color.colorStop));
                } catch (UnsupportedEncodingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        });

        b_Fwrd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "1";
                byte[] value;
                try {
                    //send data to service ... converts utf-8 to decimal
                    value = message.getBytes("UTF-8");
                    mService.writeRXCharacteristic(value);//KHE
                    //Update the log with time stamp
                    String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                    listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
                    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                    edtMessage.setText("");
                    setButtonColors();
                    ViewCompat.setBackgroundTintList(b_Fwrd, ContextCompat.getColorStateList(getApplicationContext()
                            ,R.color.colorGo));
                } catch (UnsupportedEncodingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        });

        b_Right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "2";
                byte[] value;
                try {
                    //send data to service ... converts utf-8 to decimal
                    value = message.getBytes("UTF-8");
                    mService.writeRXCharacteristic(value);//KHE
                    //Update the log with time stamp
                    String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                    listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
                    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                    edtMessage.setText("");
                    setButtonColors();
                    ViewCompat.setBackgroundTintList(b_Right, ContextCompat.getColorStateList(getApplicationContext()
                            ,R.color.colorGo));
                } catch (UnsupportedEncodingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        });
        b_Rvrse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "3";
                byte[] value;
                try {
                    //send data to service ... converts utf-8 to decimal
                    value = message.getBytes("UTF-8");
                    mService.writeRXCharacteristic(value);//KHE
                    //Update the log with time stamp
                    String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                    listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
                    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                    edtMessage.setText("");
                    setButtonColors();
                    ViewCompat.setBackgroundTintList(b_Rvrse, ContextCompat.getColorStateList(getApplicationContext()
                            ,R.color.colorGo));
                } catch (UnsupportedEncodingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        });
        b_Left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "4";
                byte[] value;
                try {
                    //send data to service ... converts utf-8 to decimal
                    value = message.getBytes("UTF-8");
                    mService.writeRXCharacteristic(value);//KHE


                    //Update the log with time stamp
                    String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                    listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
                    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                    edtMessage.setText("");
                    setButtonColors();
                    ViewCompat.setBackgroundTintList(b_Left, ContextCompat.getColorStateList(getApplicationContext()
                            ,R.color.colorGo));
                } catch (UnsupportedEncodingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        });

        b_Ack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "ACK = 05 00 00 20 00 8d ef 02 d2";
                mService.writeRXCharacteristic(ACK_Value);//KHE
                //Update the log with time stamp
                String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
                messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
            }
        });

        btnGryoscopre.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String gyro_OnOff = (String) btnGryoscopre.getText();
                if (gyro_OnOff.equals("Gyro OFF")) {
                    gyroSwitch = true;
                    btnGryoscopre.setText("Gyro ON");
                } else if (gyro_OnOff.equals("Gyro ON")) {
                    gyroSwitch = false;
                    btnGryoscopre.setText("Gyro OFF");
                    getWindow().getDecorView().setBackgroundColor(Color.BLACK);
                    sendMessage("Stop");
                }
            }
        });

    } //End OnCreate

    private void sendMessage (String message) {
        try {
            byte [] value = message.getBytes("UTF-8");
            mService.writeRXCharacteristic(value);
            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
            listAdapter.add("["+currentDateTimeString+"] TX: "+ message);
            messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void setButtonColors(){

        ViewCompat.setBackgroundTintList(btnConnectDisconnect, ContextCompat.getColorStateList(getApplicationContext()
                ,R.color.colorPrimary));
        ViewCompat.setBackgroundTintList(b_Fwrd, ContextCompat.getColorStateList(getApplicationContext()
                ,R.color.colorAccent));
        ViewCompat.setBackgroundTintList(b_Right, ContextCompat.getColorStateList(getApplicationContext()
                ,R.color.colorAccent));
        ViewCompat.setBackgroundTintList(b_Rvrse, ContextCompat.getColorStateList(getApplicationContext()
                ,R.color.colorAccent));
        ViewCompat.setBackgroundTintList(b_Stop, ContextCompat.getColorStateList(getApplicationContext()
                ,R.color.colorAccent));
        ViewCompat.setBackgroundTintList(b_Left, ContextCompat.getColorStateList(getApplicationContext()
                ,R.color.colorAccent));
        ViewCompat.setBackgroundTintList(btnGryoscopre, ContextCompat.getColorStateList(getApplicationContext()
                , R.color.colorAccent));
        ViewCompat.setBackgroundTintList(btnJoystickView, ContextCompat.getColorStateList(getApplicationContext()
                , R.color.colorAccent));
    }

    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
        }
    };

    private Handler mHandler = new Handler() {
        @Override

        //Handler events that received from UART service - Haven't seen this get called KHE
        public void handleMessage(Message msg) {
            Log.d(TAG, "KHE - Event received from UART service");
        }
    };

    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
            //***********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "KHE UART_CONNECT_MSG");
                        btnConnectDisconnect.setText("Disconnect");
                        edtMessage.setEnabled(true);
                        btnSend.setEnabled(true);
                        ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - ready");
                        listAdapter.add("["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
                        messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                        mState = UART_PROFILE_CONNECTED;
                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "KHE UART_DISCONNECT_MSG");
                        btnConnectDisconnect.setText("Connect");
                        edtMessage.setEnabled(false);
                        btnSend.setEnabled(false);
                        ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
                        listAdapter.add("["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());
                        mState = UART_PROFILE_DISCONNECTED;
                        mService.close();
                        b_Fwrd.setVisibility(View.INVISIBLE);
                        b_Right.setVisibility(View.INVISIBLE);
                        b_Stop.setVisibility(View.INVISIBLE);
                        b_Rvrse.setVisibility(View.INVISIBLE);
                        b_Left.setVisibility(View.INVISIBLE);
                        b_Ack.setVisibility(View.INVISIBLE);
                        btnGryoscopre.setVisibility(View.INVISIBLE);
                        btnJoystickView.setVisibility(View.INVISIBLE);
                    }
                });
            }


            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {

                Log.d(TAG, "KHE - GATT SERVICES DISCOVERED");
                String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                listAdapter.add("["+currentDateTimeString+"] TX: Setting TX NOTIFY");
                messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                //######### ENABLE TX and ACK NOTIFICATION ############ after slight delay
                setTxAckNotifications(100);
            }
            //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
                //BREAKPOINT NEXT LINE
                String s_Message = "";
                final byte[] rcvdValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                if(rcvdValue != null)
                { //charachteristic = ACK = 013
                    if(rcvdValue.length < 10 ) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                try {
                                    if (rcvdValue.length > 1) { //Got some message other than from robot command echo
                                        StringBuilder sb = new StringBuilder(rcvdValue.length * 2);
                                        for (byte b : rcvdValue)
                                            sb.append(String.format("%02x", b & 0xff));
                                        String text = sb.toString();
                                        Log.d(TAG, "KHE Data Value Rcvd" + text);
                                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                                        listAdapter.add("[" + currentDateTimeString + "] RX: " + text);
                                        if( text .equals("656e71")) { //Hex for e=65 n=6e q=71
                                            // Do something here if you want to respond to an Enq
                                            mService.writeRXCharacteristic(ACK_Value);//KHE
                                        }
                                    } else { //Only one byte so command from robot
                                        String text = new String(rcvdValue, StandardCharsets.UTF_8);
                                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                                        listAdapter.add("[" + currentDateTimeString + "] RX: " + text);
                                    }
                                    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);

                                } catch (Exception e) {
                                    Log.e(TAG, e.toString());
                                }
                            }
                        });
                    }//endif rcvdVale < 10
                    if (rcvdValue.length > 9) { //Was receiving 10 Byte message at one point - not any more
                        Log.d(TAG, "KHE => Got 10 byte txValue"); //Char = TX = 12
                        mService.writeRXCharacteristic(ACK_Value);
                    }//endif rcvdValue.length > 9
                }//endif rcvdValue != null
            }//endif action = data_available
            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
            }
        }
    };

    private void setTxAckNotifications(final long l_MSecs){ //added delay after some loading problems
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (mService.enableTXNotification())
                {
                    Log.d(TAG, "KHE - Tx Notification Set - Call AckNotification");
                    String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                    listAdapter.add("["+currentDateTimeString+"] TX:  TX NOTIFY SET Calling ACK");
                    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                    setAckNotification(1000);
                }else
                {
                    setTxAckNotifications(l_MSecs + 100); //Try again
                }
            }
        };
        Handler handler = new Handler();
        handler.postDelayed(runnable, l_MSecs);
    }

    private void setAckNotification(final long l_MSecs){
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "KHE - Trying to set Ack Notification");
                if (mService.enableAckNotification())
                {
                    String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                    listAdapter.add("["+currentDateTimeString+"] TX: Setting ACK NOTIFY");
                    messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                    Log.d(TAG, "KHE - Ack Notification Set");
                    b_Fwrd.setVisibility(View.VISIBLE);
                    b_Right.setVisibility(View.VISIBLE);
                    b_Stop.setVisibility(View.VISIBLE);
                    b_Rvrse.setVisibility(View.VISIBLE);
                    b_Left.setVisibility(View.VISIBLE);
                    b_Ack.setVisibility(View.VISIBLE);
                    btnGryoscopre.setVisibility(View.VISIBLE);
                    btnJoystickView.setVisibility(View.VISIBLE);
                }
            }
        };
        Handler handler = new Handler();
        handler.postDelayed(runnable, l_MSecs);
    }

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }
    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;

    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
                    mService.connect(deviceAddress);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onBackPressed() {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("nRFUART's running in background.\n             Disconnect to exit");
        }
        else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.popup_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
        }
    }


}
