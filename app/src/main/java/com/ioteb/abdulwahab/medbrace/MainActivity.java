package com.ioteb.abdulwahab.medbrace;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    TextView cityField, detailsField, currentTemperatureField, humidity_field, pressure_field, updatedField;
    EditText editTextAddress, editTextPort;
    Button buttonConnect;
    TextView  textViewRx;   //TextView textViewState, textViewRx;

    String temprSend = "00.00";
    private LineChart mChart;

    //Hotspot
    TextView infoIp, infoPort;
    TextView textViewState, textViewPrompt;
    static final int UdpServerPORT = 1060;
    UdpServerThread udpServerThread;
    private WifiManager wifiManager;

    public static String sentence;
    public static ArrayList<Entry> yValues;
    public static float time = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        detailsField = (TextView) findViewById(R.id.details_field);
        pressure_field = (TextView) findViewById(R.id.pressure_field);

        detailsField.setText(temprSend +"°");

        String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
        pressure_field.setText(currentDateTimeString + " :in ");

        //chart
        mChart = (LineChart) findViewById(R.id.Linechart);
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(false);

        yValues = new ArrayList<>();

        yValues.add(new Entry(0, 0f));
        //yValues.add(new Entry(10, 40f));
        //yValues.add(new Entry(20, 38f));
        //yValues.add(new Entry(30, 39f));
        //yValues.add(new Entry(40, 41f));


        LineDataSet set = new LineDataSet(yValues, "Temperature");
        set.setFillAlpha(110);
        set.setLineWidth(3f);
        set.setValueTextSize(13f);
        set.setValueTextColor(Color.WHITE);

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(set);
        LineData data = new LineData(dataSets);
        mChart.setData(data);

        //Hotspot
        //checkSystemWritePermission();
        setupHotspot();

        infoIp = (TextView) findViewById(R.id.infoip);
        infoPort = (TextView) findViewById(R.id.infoport);
        textViewState = (TextView) findViewById(R.id.state);
        textViewPrompt = (TextView) findViewById(R.id.prompt);

        infoIp.setText(getIpAddress());
        infoPort.setText(String.valueOf(UdpServerPORT));

    }

    //hotspot --------------------------------------------------
    private void setupHotspot() {

        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
        startActivity(intent);

        //Hotspot Setup..
        wifiManager = (WifiManager) this.getBaseContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(false);
        }

        WifiConfiguration wc = new WifiConfiguration();

        wc.SSID = "IoTeb";
        wc.preSharedKey = "1234567abc";
        wc.hiddenSSID = true;
        wc.status = WifiConfiguration.Status.ENABLED;
        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);

        int res = wifiManager.addNetwork(wc);
        Log.e("WifiPreference", "add Network returned " + res);
        boolean b = wifiManager.enableNetwork(res, true);
        Log.e("WifiPreference", "enableNetwork returned " + b);


        try {
            Method setWifiApMethod = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            boolean apstatus = (Boolean) setWifiApMethod.invoke(wifiManager, wc, true);

            Method isWifiApEnabledmethod = wifiManager.getClass().getMethod("isWifiApEnabled");
            while (!(Boolean) isWifiApEnabledmethod.invoke(wifiManager)) {
            }
            ;
            Method getWifiApStateMethod = wifiManager.getClass().getMethod("getWifiApState");
            int apstate = (Integer) getWifiApStateMethod.invoke(wifiManager);
            Method getWifiApConfigurationMethod = wifiManager.getClass().getMethod("getWifiApConfiguration");
            wc = (WifiConfiguration) getWifiApConfigurationMethod.invoke(wifiManager);
            Log.e("CLIENT", "\nSSID:" + wc.SSID + "\nPassword:" + wc.preSharedKey + "\n" + wc.hiddenSSID + "\n");

        } catch (Exception e) {
            Log.e(this.getClass().toString(), "", e);

        }
    }


    public Context getActivity() {
        return getBaseContext().getApplicationContext();
    }

    @Override
    protected void onStart() {
        udpServerThread = new UdpServerThread(UdpServerPORT);
        udpServerThread.start();
        super.onStart();
    }


    @Override
    protected void onDestroy() {
        if (udpServerThread != null) {
            udpServerThread.setRunning(false);
            udpServerThread = null;
        }
        super.onDestroy();

    }

    private void updateState(final String state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewState.setText(state);
            }
        });
    }

    private void updatePrompt(final String prompt) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewPrompt.append(prompt);

                detailsField.setText(sentence +"°");

                String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
                pressure_field.setText(currentDateTimeString + " :in ");

                time += 10.00 ;
                //Log.e("hhhh", );
                yValues.add(new Entry(time, Float.parseFloat(sentence)));//---------------------------------------------------

                LineDataSet set = new LineDataSet(yValues, "Tempe");
                set.setFillAlpha(110);
                set.setLineWidth(3f);
                set.setValueTextSize(13f);
                set.setValueTextColor(Color.WHITE);

                ArrayList<ILineDataSet> dataSets = new ArrayList<>();
                dataSets.add(set);
                LineData data = new LineData(dataSets);
                mChart.setData(data);
                mChart.notifyDataSetChanged();
                mChart.invalidate();
            }
        });
    }

    private class UdpServerThread extends Thread {

        int serverPort;
        DatagramSocket socket;

        boolean running;

        public UdpServerThread(int serverPort) {
            super();
            this.serverPort = serverPort;
        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        @Override
        public void run() {

            running = true;

            try {
                updateState("Starting UDP Server");
                socket = new DatagramSocket(serverPort);

                updateState("UDP Server is running");
                Log.e(TAG, "UDP Server is running");

                while (running) {


                    byte[] buf = new byte[256];

                    // receive request
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    try {
                        socket.receive(packet);     //this code block the program flow
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    sentence = new String(packet.getData());
                    //infoPort.setText(sentence.toString());//---------------------------------------
                    //sentence = sentence.replaceAll("�", "");
                    sentence = sentence.substring(0, packet.getLength());

                    Log.e(TAG, sentence.length() + " , " + packet.getLength() + " , " + sentence);

                    // send the response to the client at "address" and "port"
                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();

                    updatePrompt("Request from: " + sentence + " , " + address + ":" + port + "\n");

                    String dString = new Date().toString() + "\n"
                            + "Your address " + address.toString() + ":" + String.valueOf(port);
                    buf = dString.getBytes();
                    packet = new DatagramPacket(buf, buf.length, address, port);
                    try {
                        socket.send(packet);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

                Log.e(TAG, "UDP Server ended");

            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    //socket.close();
                    Log.e(TAG, "socket.close()");
                }
            }
        }
    }

    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += "SiteLocalAddress: "
                                + inetAddress.getHostAddress() + "\n";
                    }

                }

            }

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }

        return ip;
    }

}
