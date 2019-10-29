package com.mskrzynski.voicecontrolpc;

import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class UDPBroadcast implements Runnable {

    static List<String> listaAdresowIP = new ArrayList<>();

    @Override
    public void run() {
        try {
            int ilosc_pakietow_udp = 0;

            //resetowanie listy adresów
            listaAdresowIP.clear();

            //otworzenie socketu na losowym porcie
            DatagramSocket socketUDP = new DatagramSocket();
            socketUDP.setBroadcast(true);

            //Wiadomość do serwera
            byte[] wiadomoscUDP = "VC_REQUEST".getBytes();

            // Wyszukaj wszystkie możliwe interfejsy
            Enumeration interfejsy = NetworkInterface.getNetworkInterfaces();
            while (interfejsy.hasMoreElements()) {

                NetworkInterface interfejs = (NetworkInterface) interfejsy.nextElement();

                // Nie rozgłaszaj do interfejsu loopback
                if (interfejs.isLoopback() || !interfejs.isUp()) continue;

                // Zdobądź adres rozgłoszeniowy znalezionego interfejsu
                for (InterfaceAddress interfejsAdres : interfejs.getInterfaceAddresses()) {
                    InetAddress adresBroadcast = interfejsAdres.getBroadcast();
                    if (adresBroadcast == null) continue;

                    // Wysyłaj 50 pakietów rozgłoszeniowych na znaleziony adres rozgłoszeniowy
                    try {
                        while(ilosc_pakietow_udp <= 50) {
                            ilosc_pakietow_udp++;
                            DatagramPacket sendPacket = new DatagramPacket(wiadomoscUDP, wiadomoscUDP.length, adresBroadcast, 8163);
                            socketUDP.send(sendPacket);

                            // Oczekiwanie na odpowiedź
                            // Mały pakiet aby zminimalizować jego utratę
                            byte[] odebranyBufor = new byte[15];
                            DatagramPacket pakiet = new DatagramPacket(odebranyBufor, odebranyBufor.length);
                            socketUDP.receive(pakiet);

                            // Sprawdzamy czy odpowiedź jest prawidłowa
                            String message = new String(pakiet.getData()).trim();
                            if (message.equals("VC_RESPONSE")) {

                                //Zapisujemy adres IP znalezionych serwerów oraz ich hostname'y
                                String adresIP = String.valueOf((pakiet.getAddress().getHostAddress()));
                                String hostname = String.valueOf(pakiet.getAddress().getHostName());
                                listaAdresowIP.add(adresIP);
                                listaAdresowIP.add(hostname);

                                Log.i("UDPBroadcast", "adding to listaAdresowIP: " + adresIP + " " + hostname);
                            }
                        }
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }

            // Zamykamy port
            socketUDP.close();
        }
        catch (SocketException e) {
            e.printStackTrace();
        }

        // Tymczasowa zmiana listy serwerów na LinkedHashSet w celu usunięcia duplikatów przy zachowaniu kolejności
        if(!listaAdresowIP.isEmpty()) {
            Set<String> listaAdresowIPSet = new LinkedHashSet<>(listaAdresowIP);
            listaAdresowIP.clear();
            listaAdresowIP.addAll(listaAdresowIPSet);
        }
    }
}
