package com.example.rssi;

import android.bluetooth.BluetoothAdapter;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class MainActivity extends AppCompatActivity {
    boolean logging = false;
    int sample = 1, first_rssi = -58;
    String beacon  = "UUID Removed For Security Reasons";
    private final BluetoothAdapter BTAdapter = BluetoothAdapter.getDefaultAdapter();
    TextView tv_display, tv_uuid, tv_time, tv_path;
    EditText distance, material;

    KalmanFilter filter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //File Initialization
        String dir = Environment.getExternalStorageDirectory().getAbsolutePath() +"/RSSI";
        File dirs = new File(dir);
        dirs.mkdirs();
        File file = new File(dir + "/rssi.txt");

        //Display
        setContentView(R.layout.activity_main);
        tv_display = findViewById(R.id.textView);
        tv_uuid = findViewById(R.id.textView2);
        distance = findViewById(R.id.editText);
        material = findViewById(R.id.editTextTextPersonName); // material name
        tv_time = findViewById(R.id.textView3);
        tv_path = findViewById(R.id.textView4);
        tv_path.setText(dir);

        View logButton = findViewById(R.id.button), calibrateButton = findViewById(R.id.button2);
        SeekBar mySlider = findViewById(R.id.seekBar);
        logButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if(!logging) {
                    logging = true;
                    int dist = Integer.valueOf(distance.getText().toString());
                    filter = new KalmanFilter(dist,1, 2, first_rssi);
                }
            }
        });

        calibrateButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view)
            {
                int dist = Integer.valueOf(distance.getText().toString());
                filter = new KalmanFilter(dist, 1, 2, first_rssi);
            }
        });

        mySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                distance.setText(String.valueOf(i+1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        final Handler handler = new Handler();
        Timer timer = new Timer(false);
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        boolean running = true;
                        BTAdapter.startLeScan(new BluetoothAdapter.LeScanCallback() {
                            @Override
                            public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                                //Check iBeacon Signature
                                int offset = 2;
                                boolean iBeacon = false;
                                while (offset <= 5) {
                                    if (((int) scanRecord[offset + 2] & 255) == 2 &&
                                            ((int) scanRecord[offset + 3] & 255) == 21) {
                                        iBeacon = true;
                                        break;
                                    }
                                    offset++;
                                }

                                if (iBeacon) {
                                    //Convert to string
                                    byte[] uuidBytes = new byte[16];
                                    System.arraycopy(scanRecord, offset + 4, uuidBytes, 0, 16);
                                    String hexString = bytes2char(uuidBytes);

                                    //Hyphenate the UUID
                                    String uuid = hexString.substring(0, 8) + "-" +
                                            hexString.substring(8, 12) + "-" +
                                            hexString.substring(12, 16) + "-" +
                                            hexString.substring(16, 20) + "-" +
                                            hexString.substring(20, 32);

                                    // if Beacon = our Gimbal Beacon by UUID
                                    if(Objects.equals(uuid, beacon)){
                                        int rvalue = rssi;                  // raw measurement value
                                        int kvalue = filter.getNewEst(rssi); // kalman filtered value
                                        int avalue = filter.getNewAvg((rssi)); // Low pass filtered value

                                        if(sample < 501 && logging){
                                            try {
                                                FileOutputStream stream = new FileOutputStream(file, true);
                                                try {
                                                    if(sample < 2)
                                                        stream.write(("\n" + material.getText().toString() + "\n").getBytes());
                                                    stream.write(
                                                            (rvalue + " " + kvalue + " "  + avalue + " " + distance.getText().toString() + " " + sample + "\n").getBytes()
                                                    );
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                } finally {
                                                    try {
                                                        stream.close();
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            } catch (FileNotFoundException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        else{
                                            if(Integer.parseInt(distance.getText().toString()) == 1)
                                                first_rssi = avalue;
                                            sample = 0;
                                            logging = false;
                                        }
                                        int finalValue = avalue;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                tv_uuid.setText(uuid);
                                                tv_display.setText(finalValue + "dB");
                                                tv_time.setText("Sample: " + sample);
                                                sample++;
                                            }
                                        });
                                    }
                                }
                            }
                        });
                    }
                });
            }
        };
        timer.schedule(timerTask,1000); // 1000 = 1 second.
    }

    static final char[] hexArr = "0123456789ABCDEF".toCharArray();
    private static String bytes2char(byte[] arr) {
        char[] result = new char[arr.length * 2];
        for ( int i = 0; i < arr.length; ++i ) {
            int x = arr[i] & 0xFF;
            result[i * 2] = hexArr[x >>> 4];
            result[i * 2 + 1] = hexArr[x & 0x0F];
        }
        return new String(result);
    }
}