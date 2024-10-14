package com.github.mkouba.wsperf.legacy;

import jakarta.inject.Inject;
import jakarta.websocket.OnMessage;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/to-lower-case")
public class ToLowerCaseEndpoint {

    @Inject
    ToLowewCaseService service;

    @OnMessage
    String convert(String message) {
        return service.convert(message);
    }
}