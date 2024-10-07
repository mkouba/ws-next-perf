package com.github.mkouba.wsperf.next;

import io.quarkus.websockets.next.InboundProcessingMode;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;

@WebSocket(path = "to-lower-case", inboundProcessingMode = InboundProcessingMode.CONCURRENT)
public class ToLowerCaseEndpoint {

    @OnTextMessage
    String convert(String message) {
        return message.toLowerCase();
    }

}