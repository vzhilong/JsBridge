package com.vincent.jsbridge;

public interface BridgeHandler {
    void handler(String data, BridgeResponseCallback callback);
}