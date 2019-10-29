package com.mskrzynski.voicecontrolpc;

class Server {

    private String ip_address;
    private String hostname;

    Server(String ip, String host) {
        ip_address = ip;
        hostname = host;
    }

    String getIpAddress() {
        return ip_address;
    }

    String getHostname() {
        return hostname;
    }
}
