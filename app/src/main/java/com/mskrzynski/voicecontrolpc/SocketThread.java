package com.mskrzynski.voicecontrolpc;

import android.os.Build;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

class SocketThread implements Runnable{

    static Socket socket;
    private String ipAddress;
    private int port;
    static DataOutputStream dos;
    static DataInputStream dis;
    static boolean error_occured;

    SocketThread(String ipAddress, int port){
        this.ipAddress = ipAddress;
        this.port = port;
    }

    @Override
    public void run() {
        error_occured = false;
        InetAddress serverAddr = null;

        try {
            serverAddr = InetAddress.getByName(ipAddress);
        }
        catch (UnknownHostException e) {
            e.printStackTrace();
        }

        try {
            socket = new Socket(serverAddr, port);
            socket.setKeepAlive(true);
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            String deviceName = String.format("%s %s\n", Build.MANUFACTURER, Build.MODEL);
            dos.write(deviceName.getBytes());
        }
        catch (IOException e) {
            error_occured = true;
            e.printStackTrace();
        }
    }
}
