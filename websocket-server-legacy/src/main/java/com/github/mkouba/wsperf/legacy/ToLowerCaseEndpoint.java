package com.github.mkouba.wsperf.legacy;

import jakarta.websocket.OnMessage;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/to-lower-case")
public class ToLowerCaseEndpoint {

    @OnMessage
    String convert(String message) {
        return message.toLowerCase();
    }
}