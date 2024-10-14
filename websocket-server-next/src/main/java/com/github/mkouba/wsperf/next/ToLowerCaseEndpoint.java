package com.github.mkouba.wsperf.next;

import io.quarkus.websockets.next.InboundProcessingMode;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import jakarta.inject.Inject;

@WebSocket(path = "to-lower-case", inboundProcessingMode = InboundProcessingMode.CONCURRENT)
public class ToLowerCaseEndpoint {

    @Inject
    ToLowewCaseService service;

    @OnTextMessage
    String convert(String message) {
        return service.convert(message);
    }

}