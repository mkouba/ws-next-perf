package com.github.mkouba.wsperf.next;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Application;
import io.quarkus.runtime.Quarkus;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.InboundProcessingMode;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocket;
import jakarta.inject.Inject;

@WebSocket(path = "to-lower-case", inboundProcessingMode = InboundProcessingMode.CONCURRENT)
public class ToLowerCaseEndpoint {

    static final long WARMUP_DELAY = 10;
    static final AtomicReference<LocalDateTime> LAST_CLOSED = new AtomicReference<>(LocalDateTime.now());

    @Inject
    ToLowerCaseService service;

    @Inject
    OpenConnections connections;

    @OnOpen
    String open() {
        return "_" + Application.class.getPackage().getImplementationVersion();
    }

    @OnTextMessage
    String convert(String message) {
        return service.convert(message);
    }

    @OnClose
    void close() {
        LAST_CLOSED.set(LocalDateTime.now());
    }

    @Scheduled(identity = "auto-exit", delayed = "${test-auto-exit:5m}", every = "${test-auto-exit:5m}")
    void autoExit() {
        if (Duration.between(LAST_CLOSED.get(), LocalDateTime.now()).getSeconds() > WARMUP_DELAY
                && connections.listAll().isEmpty()) {
            Log.infof("No open connections - exit app");
            Quarkus.asyncExit();
        }
    }

}