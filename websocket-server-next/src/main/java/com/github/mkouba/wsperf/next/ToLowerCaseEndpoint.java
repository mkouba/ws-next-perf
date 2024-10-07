package com.github.mkouba.wsperf.next;

import io.quarkus.websockets.next.InboundProcessingMode;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
//import io.smallrye.mutiny.Uni;

@WebSocket(path = "to-lower-case", inboundProcessingMode = InboundProcessingMode.CONCURRENT)
public class ToLowerCaseEndpoint {

    @OnTextMessage
    //Uni<String> convert(String message) {
    String convert(String message) {
        //return Uni.createFrom().item(message.toLowerCase());
        return message.toLowerCase();
    }

}