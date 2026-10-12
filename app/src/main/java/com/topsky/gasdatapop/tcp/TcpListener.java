package com.topsky.gasdatapop.tcp;

public interface TcpListener {

    void onConnected();

    void onReConnect(String msg);

    void onDisconnected();

    void onReceived(byte[] data);

    void onError(Exception e);
}