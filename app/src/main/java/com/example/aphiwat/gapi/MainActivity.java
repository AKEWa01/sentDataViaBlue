package com.example.aphiwat.gapi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener, StepListener {
    //Blutooth Def
    public static final int REQUEST_ENABLE_BT = 1;
    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final int MESSAGE_READ = 0;
    public static final int MESSAGE_WRITE = 1;
    public static final int CONNECTING = 2;
    public static final int CONNECTED = 3;
    public static final int NO_SOCKET_FOUND = 4;
    public static final int STATE_MESSAGE_RECEIVED = 5;
    private static final int REQUEST_CODE = 1000;
    TextView gyro, gyro2, actna;
    Button walk, sit, sleep, run, noac;
    int countsensor = 0, level;
    Sensor humidity, senGy, heart_rate, temperature, light, pressure, geomagnetic;
    //Wifi
    WifiManager wifi;
    WifiScanReceiver wifiReceiver;
    PowerManager pm;
    PowerManager.WakeLock wl;
    boolean stopvalue = true, scanwifina = false;
    //    List<String>value = new ArrayList<String>();
    List<byte[]> valueByte = new ArrayList<byte[]>();
    String la = "0", lo = "0", time = "0";
    //    String last_heartrate_time = "0";
    int activity = 1;
    ListView lv_paired_devices;
    TextView msg_box;
    EditText writeNa;
    boolean bluTT = false;
    Button send;
    Set<BluetoothDevice> set_pairedDevices;
    ArrayAdapter adapter_paired_devices;
    BluetoothAdapter bluetoothAdapter;
    SendReceive sendReceive;
    String bluetooth_message = "00";

    JSONObject sensorSuit;

    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg_type) {
            super.handleMessage(msg_type);
            switch (msg_type.what) {
                case MESSAGE_READ:

                    byte[] readbuf = (byte[]) msg_type.obj;
                    String string_recieved = new String(readbuf);
                    //do some task based on recieved string
                    break;
                case MESSAGE_WRITE:

                    if (msg_type.obj != null) {
                        ConnectedThread connectedThread = new ConnectedThread((BluetoothSocket) msg_type.obj);
                        connectedThread.write(bluetooth_message.getBytes());

                    }
                    break;

                case CONNECTED:
                    Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                    bluTT = true;
                    break;

                case CONNECTING:
                    Toast.makeText(getApplicationContext(), "Connecting...", Toast.LENGTH_SHORT).show();
                    break;

                case NO_SOCKET_FOUND:
                    Toast.makeText(getApplicationContext(), "No socket found", Toast.LENGTH_SHORT).show();
                    break;

                case STATE_MESSAGE_RECEIVED:
                    byte[] readbuffer = (byte[]) msg_type.obj;
                    String tempMsg = new String(readbuffer, 0, msg_type.arg1);
                    msg_box.append(tempMsg);
                    break;
            }
        }
    };
    //CountSTep
    private StepDetector simpleStepDetector;
    private Sensor accel;
    //private static final String TEXT_NUM_STEPS = "Number of Steps: ";
    private int numSteps, n = 0;
    //Location Manager
    private LocationManager locationManager;
    private LocationListener listener;
    //gyro
    private SensorManager sensorManager;
    Thread de = new Thread() {
        @Override
        public void run() {
            try {

                while (true) {
                    time = String.valueOf(System.currentTimeMillis() / 1000L);
                    scanwifina = false;
                    countsensor = 0;
                    Onsensor();
                    n++;
                    while (countsensor < 11) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (stopvalue == false) {
                                        countsensor++;
                                        Onsensor();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                    if (bluTT) {
                        sendReceive.write();
                    }

                    Thread.sleep(5);
                }
            } catch (InterruptedException e) {

            }
        }
    };
    //--compass
    private float[] mGravity = new float[3];
    private float[] mGeomagnetic = new float[3];
    //end Blutooth

    private float azimuth = 0f;
    //Battery////////////////////////////////////////////////////////////////////////////
    private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctxt, Intent intent) {
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            /*if(countsensor==9)
                gyro.setText(String.valueOf(level) + "%");*/
        }
    };

    //On sensor
    private void Onsensor() {
        stopvalue = true;
        sensorManager.unregisterListener(this, accel);
        sensorManager.unregisterListener(this, senGy);
        sensorManager.unregisterListener(this, temperature);
        sensorManager.unregisterListener(this, humidity);
        sensorManager.unregisterListener(this, light);
        sensorManager.unregisterListener(this, pressure);
        sensorManager.unregisterListener(this, geomagnetic);
        ///////Step count
        if (accel != null) {
            sensorManager.registerListener(MainActivity.this, accel, SensorManager.SENSOR_DELAY_FASTEST);
        }

        if (countsensor == 0) {
            if (accel == null) {
                stopvalue = false;
            } else {
                sensorManager.registerListener(MainActivity.this, accel, SensorManager.SENSOR_DELAY_FASTEST);
            }
        } else if (countsensor == 1) {
            if (senGy == null) {
                /*String b="pushGyro("+"X : "+",Y : "
                        +",Z : " + ", uts : \"" + (System.currentTimeMillis() / 1000L)+ "\")";
                value.add(b);*/
                stopvalue = false;
            } else {
                sensorManager.registerListener(MainActivity.this, senGy, SensorManager.SENSOR_DELAY_FASTEST);
            }
        } else if (countsensor == 2) {
            stopvalue = false;
        } else if (countsensor == 3) {
            if (temperature == null) {
                //gyro.setText("No TEMp");
                //String b="pushTemperature(No sensor)";
                //value.add(b);
                stopvalue = false;
                countsensor++;
            } else {
                sensorManager.registerListener(MainActivity.this, temperature, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } else if (countsensor == 4) {
            if (humidity == null) {
                //gyro.setText("No Sensor hUMIDITY");
                //String b="pushHumidity(No sensor)";
                //value.add(b);
                stopvalue = false;
            } else {
                sensorManager.registerListener(MainActivity.this, humidity, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } else if (countsensor == 5) {
            if (light == null) {
                //gyro.setText("No light sensor");
                //String b="pushLight(No sensor)";
                //value.add(b);
                stopvalue = false;
            } else {
                sensorManager.registerListener(MainActivity.this, light, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } else if (countsensor == 6) {
            if (pressure == null) {
                //gyro.setText("No Pressure");
                //String b="pushPressure(No sensor)";
                //value.add(b);
                stopvalue = false;
            } else {
                sensorManager.registerListener(MainActivity.this, pressure, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } else if (countsensor == 7) {
            if (geomagnetic == null) {
                //gyro.setText("No magentic");
                //String b="pushCompass(No sensor)";
                //value.add(b);
                stopvalue = false;
            } else {
                sensorManager.registerListener(MainActivity.this, geomagnetic, SensorManager.SENSOR_DELAY_GAME);
                sensorManager.registerListener(MainActivity.this, accel, SensorManager.SENSOR_DELAY_GAME);
            }
        } else if (countsensor == 8) {
            //gyro.setText(String.valueOf(level) + "%");

            try {
                sensorSuit.put("real", activity);
                sensorSuit.put("bat", level);
            } catch (JSONException e) {
                e.printStackTrace();
            }

//            String aaas = "pushPredict(true:" + activity + ",uts:\"" + time + "\");";
//            value.add(aaas);
//            valueByte.add(aaas.getBytes());
//            String b = "pushBattery(battery:" + String.valueOf(level) + ",uts:\"" + time + "\");";
//            value.add(b);
//            valueByte.add(b.getBytes());
            stopvalue = false;
        } else if (countsensor == 9) {
            try {
                sensorSuit.put("point", "POINT(" + la + " " + lo + ")");
            } catch (JSONException e) {
                e.printStackTrace();
            }
//            String b = "pushGps(point:\"POINT(" + la + " " + lo + ")\",uts:\"" + time + "\");";
//            value.add(b);
//            valueByte.add(b.getBytes());
            stopvalue = false;
            //wifi.startScan();
        } else if (countsensor == 10) {
            if (accel == null) {
                stopvalue = false;
            } else {

                try {
                    sensorSuit.put("step", numSteps);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

//                String b = "pushStep(step:" + numSteps + ",uts:\"" + time + "\");";
//                value.add(b);
//                valueByte.add(b.getBytes());
                stopvalue = false;
            }
        } else if (countsensor == 11) {

            if (wifi.isWifiEnabled() == false) {
                wifi.setWifiEnabled(true);
            }

            wifi.startScan();
            scanwifina = true;
        }
        //gyro2.setText(String.valueOf(countsensor));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE: {
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {

                    }
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        sensorSuit = new JSONObject();

        //bluetooth
        adapter_paired_devices = new ArrayAdapter(getApplicationContext(), R.layout.support_simple_spinner_dropdown_item);
        initialize_bluetooth();
        start_accepting_connection();


        numSteps = 0;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        gyro = (TextView) findViewById(R.id.tgyro);
        gyro2 = (TextView) findViewById(R.id.tgyrotrue);
        actna = (TextView) findViewById(R.id.acnaja);

        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifi.setWifiEnabled(true);
        wifiReceiver = new WifiScanReceiver();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        //--Battery
        this.registerReceiver(this.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        walk = (Button) findViewById(R.id.act1);
        run = (Button) findViewById(R.id.act2);
        sit = (Button) findViewById(R.id.act3);
        sleep = (Button) findViewById(R.id.act4);
        noac = (Button) findViewById(R.id.act0);

        humidity = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        heart_rate = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        temperature = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        senGy = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        geomagnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        simpleStepDetector = new StepDetector();
        simpleStepDetector.registerListener(this);
        sensorManager.registerListener(MainActivity.this, accel, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(MainActivity.this, heart_rate, 3);

        //--Location manager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                la = String.valueOf(location.getLatitude());
                lo = String.valueOf(location.getLongitude());
                //if(countsensor==10)
                //gyro.setText("Location\n " + location.getLongitude() + " " + location.getLatitude());
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {

                Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(i);
            }3
        };
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET}
                        , 10);
            }
            return;
        }
        locationManager.requestLocationUpdates("gps", 1000, 0, listener);
        /*la=String.valueOf(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER).getLatitude());
        lo=String.valueOf(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER).getLongitude());*/
        //--Set Time
        //3 sleep 4 sit 5 walk 6 run
        noac.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                actna.setText("Activity : noActivity");
                activity = 1;
            }
        });
        walk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                actna.setText("Activity : walk");
                activity = 5;
            }
        });
        run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                actna.setText("Activity : run");
                activity = 6;
            }
        });
        sleep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                actna.setText("Activity : sleep");
                activity = 3;
            }
        });
        sit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                actna.setText("Activity : sit");
                activity = 4;
            }
        });


        de.start();

        /*BtnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                countsensor++;
                if(countsensor>11) {
                    gyro.setText("");
                    for(int i=0;i<value.size();i++){
                        gyro.append(value.get(i));
                    }
                }else {
                    Onsensor();
                }
            }
        });*/
    }//END OnCREATE

    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor senall = sensorEvent.sensor;
        gyro2.setText(String.valueOf(countsensor));
        if (senall.getType() == Sensor.TYPE_ACCELEROMETER && countsensor == 0 && stopvalue) {
            //gyro.setText("ACCE\nX : " + sensorEvent.values[0] + "\n Y : " + sensorEvent.values[1] + "\n Z : " + sensorEvent.values[2]);

            if (sensorSuit.length() != 0) {
//                Log.d("GraphQL", sensorSuit.toString());
                byte[] b = new byte[0];
                try {
                    b = sensorSuit.toString().getBytes("utf-8");
                    valueByte.add(b);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            sensorSuit = new JSONObject();


            JSONArray acc_value = new JSONArray();
            try {
                acc_value.put(sensorEvent.values[0]);
                acc_value.put(sensorEvent.values[1]);
                acc_value.put(sensorEvent.values[2]);

                sensorSuit.put("uts", time);
                sensorSuit.put("acc", acc_value);
            } catch (JSONException e) {
                e.printStackTrace();
            }

//            String a = "pushAccelerate(" + "x:" + String.valueOf(sensorEvent.values[0]) + ",y:" + String.valueOf(sensorEvent.values[1])
//                    + ",z:" + String.valueOf(sensorEvent.values[2]) + ",uts:\"" + time + "\");";
//            value.add(a);
//            valueByte.add(a.getBytes());
            stopvalue = false;
        }
        if (senall.getType() == Sensor.TYPE_GYROSCOPE && countsensor == 1 && stopvalue) {
            //gyro.setText("Gyro\nX : " + sensorEvent.values[0] + "\n Y : " + sensorEvent.values[1] + "\n Z : " + sensorEvent.values[2]);

            JSONArray gyro_value = new JSONArray();
            try {
                gyro_value.put(sensorEvent.values[0]);
                gyro_value.put(sensorEvent.values[1]);
                gyro_value.put(sensorEvent.values[2]);

                sensorSuit.put("gyro", gyro_value);
            } catch (JSONException e) {
                e.printStackTrace();
            }

//            String a = "pushGyro(" + "x:" + String.valueOf(sensorEvent.values[0]) + ",y:" + String.valueOf(sensorEvent.values[1])
//                    + ",z:" + String.valueOf(sensorEvent.values[2]) + ",uts:\"" + time + "\");";
//            value.add(a);
//            valueByte.add(a.getBytes());
            stopvalue = false;
        }
        if (senall.getType() == Sensor.TYPE_HEART_RATE) {
//            if (!time.equals(last_heartrate_time)) {

            try {
                sensorSuit.put("hr", sensorEvent.values[0]);
            } catch (JSONException e) {
                e.printStackTrace();
            }

//                String a = "pushHeartrate(heartrate:" + String.valueOf(sensorEvent.values[0]) + ",uts : \"" + time + "\");";
//                value.add(a);
//                valueByte.add(a.getBytes());
            stopvalue = false;
//                last_heartrate_time = time;
//            }
        }
        if (countsensor == 3 && senall.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE && stopvalue) {
            //gyro.setText("Temp : " + sensorEvent.values[0]);

            try {
                sensorSuit.put("temp", sensorEvent.values[0]);
            } catch (JSONException e) {
                e.printStackTrace();
            }

//            String a = "pushTemperature(temperature:" + String.valueOf(sensorEvent.values[0]) + ",uts : \"" + time + "\");";
//            value.add(a);
//            valueByte.add(a.getBytes());
            stopvalue = false;
        }
        if (countsensor == 4 && senall.getType() == Sensor.TYPE_RELATIVE_HUMIDITY && stopvalue) {
            //gyro.setText("HUMDITY : "+sensorEvent.values[0]);

            try {
                sensorSuit.put("hum", sensorEvent.values[0]);
            } catch (JSONException e) {
                e.printStackTrace();
            }

//            String a = "pushHumdity(humdity:" + String.valueOf(sensorEvent.values[0]) + ",uts:\"" + time + "\");";
//            value.add(a);
//            valueByte.add(a.getBytes());
            stopvalue = false;
        }
        if (countsensor == 5 && senall.getType() == Sensor.TYPE_LIGHT && stopvalue) {
            //gyro.setText("Light : "+sensorEvent.values[0]);

            try {
                sensorSuit.put("light", sensorEvent.values[0]);
            } catch (JSONException e) {
                e.printStackTrace();
            }

//            String a = "pushLight(light:" + String.valueOf(sensorEvent.values[0]) + ",uts:\"" + time + "\");";
//            value.add(a);
//            valueByte.add(a.getBytes());
            stopvalue = false;
        }
        if (countsensor == 6 && senall.getType() == Sensor.TYPE_PRESSURE && stopvalue) {
            //gyro.setText("Pressure : "+sensorEvent.values[0]);

            try {
                sensorSuit.put("pres", sensorEvent.values[0]);
            } catch (JSONException e) {
                e.printStackTrace();
            }

//            String a = "pushPressure(pressure:" + String.valueOf(sensorEvent.values[0]) + ",uts:\"" + time + "\");";
//            value.add(a);
//            valueByte.add(a.getBytes());
            stopvalue = false;
        }
        if (countsensor == 7 && stopvalue) {
            final float alpha = 0.97f;
            synchronized (this) {
                if (senall.getType() == Sensor.TYPE_ACCELEROMETER) {
                    mGravity[0] = alpha * mGravity[0] + (1 - alpha) * sensorEvent.values[0];
                    mGravity[1] = alpha * mGravity[1] + (1 - alpha) * sensorEvent.values[1];
                    mGravity[2] = alpha * mGravity[2] + (1 - alpha) * sensorEvent.values[2];
                }
                if (senall.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha) * sensorEvent.values[0];
                    mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha) * sensorEvent.values[1];
                    mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha) * sensorEvent.values[2];
                }
                float R[] = new float[9];
                float I[] = new float[9];
                boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
                if (success) {
                    float orientation[] = new float[3];
                    SensorManager.getOrientation(R, orientation);
                    azimuth = (float) Math.toDegrees(orientation[0]);
                    azimuth = (azimuth + 360) % 360;

                    //gyro.setText("Compass\n"+azimuth);

                    try {
                        sensorSuit.put("comp", azimuth);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

//                    String a = "pushCompass(compass:" + String.valueOf(azimuth) + ",uts:\"" + time + "\");";
//                    value.add(a);
//                    valueByte.add(a.getBytes());
                    stopvalue = false;
                }
            }
        }
        if (senall.getType() == Sensor.TYPE_ACCELEROMETER) {
            simpleStepDetector.updateAccel(
                    sensorEvent.timestamp, sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2]);
        }
    }

    @Override
    protected void onDestroy() {
//        de.stop();
        de = null;
        sendReceive = null;
        sensorManager.unregisterListener(this);
        unregisterReceiver(wifiReceiver);
        super.onDestroy();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    //
    public void step(long timeNs) {
        numSteps++;
    }

    @Override
    protected void onPause() {
        registerReceiver(
                wifiReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        );
        super.onPause();
    }

    protected void onResume() {
        registerReceiver(
                wifiReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        );
        super.onResume();
    }

    // Bluetooth
    public void start_accepting_connection() {
        //call this on button click as suited by you

        AcceptThread acceptThread = new AcceptThread();
        acceptThread.start();
        Toast.makeText(getApplicationContext(), "accepting", Toast.LENGTH_SHORT).show();
    }

    public void initialize_bluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Toast.makeText(getApplicationContext(), "Your Device doesn't support bluetooth. you can play as Single player", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            set_pairedDevices = bluetoothAdapter.getBondedDevices();

            if (set_pairedDevices.size() > 0) {

                for (BluetoothDevice device : set_pairedDevices) {
                    String deviceName = device.getName();
                    String deviceHardwareAddress = device.getAddress(); // MAC address

                    adapter_paired_devices.add(device.getName() + "\n" + device.getAddress());
                }
            }
        }
    }

    private class WifiScanReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            List<ScanResult> wifiScanList = wifi.getScanResults();
            //gyro.setText("WTF");
            if (scanwifina) {
//                for (int i = 0; i < wifiScanList.size(); i++) {
//                    //String info = ((wifiScanList.get(i)).toString());
//                    String info = "pushWifi(ssid:\"" + ((wifiScanList.get(i).SSID).toString()) + "\",bssid:\"" + ((wifiScanList.get(i).BSSID).toString()) + "\"";
//                    info += ",rssi:" + String.valueOf(wifiScanList.get(i).level) + ",capa:\"" + ((wifiScanList.get(i).capabilities).toString()) + "\",freq:" + ((wifiScanList.get(i).frequency));
//                    info += ",uts:\"" + String.valueOf(time) + "\");";
//                    valueByte.add(info.getBytes());
//                }
//                String p;
                WifiInfo wifiInfo = wifi.getConnectionInfo();
                //3 sleep 4 sit 5 walk 6 run

                stopvalue = false;
                scanwifina = false;
            }
        }
    }

    public class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("NAME", MY_UUID);
            } catch (IOException e) {
            }
            serverSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    mHandler.obtainMessage(CONNECTED).sendToTarget();
                    bluTT = true;
                    sendReceive = new SendReceive(socket);
                    sendReceive.start();
                }
            }
        }
    }


    private class ConnectedThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[2];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();

                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private class SendReceive extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inStream;
        private final OutputStream outStream;

        public SendReceive(BluetoothSocket socket) {
            bluetoothSocket = socket;
            InputStream tempIn = null;
            OutputStream tempOut = null;
            try {
                tempIn = bluetoothSocket.getInputStream();
                tempOut = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inStream = tempIn;
            outStream = tempOut;
        }

        public void run() {
            byte[] buffer = new byte[100000];
            int bytes;
            while (true) {
                try {
                    bytes = inStream.read(buffer);
                    mHandler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write() {
            int i = 0;
            while (valueByte.size() != i) {
                try {
                    outStream.write(valueByte.get(i));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                i++;
            }

//            String s = new String();
//            for (byte[] b : valueByte) {
//                s += new String(b);
//            }
//            Log.d("GraphQL", s);

//            value.clear();
            valueByte.clear();

        }
    }
}