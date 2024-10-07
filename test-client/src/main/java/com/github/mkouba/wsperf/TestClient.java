package com.github.mkouba.wsperf;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import jakarta.inject.Inject;

@QuarkusMain
public class TestClient implements QuarkusApplication {

    @ConfigProperty(name = "number.of.clients", defaultValue = "1000")
    int numberOfClients;

    @ConfigProperty(name = "number.of.messages", defaultValue = "1000")
    int numberOfMessages;

    @ConfigProperty(name = "server.host", defaultValue = "localhost")
    String serverHost;

    @ConfigProperty(name = "server.port", defaultValue = "8080")
    int serverPort;

    @ConfigProperty(name = "server.path", defaultValue = "/to-lower-case")
    String serverPath;

    @ConfigProperty(name = "message-interval")
    Optional<Duration> messageInterval;

    @Inject
    Vertx vertx;

    @Override
    public int run(String... args) throws Exception {
        long start = System.nanoTime();
        Log.infof(
                "Test client started [number-of-clients: %s, number-of-messages: %s, server.host: %s, server.port: %s, server.path: %s]",
                numberOfClients, numberOfMessages, serverHost, serverPort, serverPath);

        List<WebSocket> clients = new CopyOnWriteArrayList<>();
        CountDownLatch receivedMessagesLatch = new CountDownLatch(numberOfClients * numberOfMessages);
        String payload = "FOO";
        long timeout = 60l;

        // Connect all clients
        CountDownLatch connectedLatch = new CountDownLatch(numberOfClients);
        for (int i = 0; i < numberOfClients; i++) {
            WebSocketClient client = vertx.createWebSocketClient();
            WebSocketConnectOptions connectOptions = new WebSocketConnectOptions().setHost(serverHost)
                    .setPort(serverPort).setURI(serverPath).setAllowOriginHeader(false);
            client.connect(connectOptions)
                    .onComplete(r -> {
                        if (r.succeeded()) {
                            WebSocket ws = r.result();
                            ws.textMessageHandler(s -> {
                                if (!s.equals(payload.toLowerCase())) {
                                    Log.errorf("Received invalid message from the server: %s", s);
                                }
                                receivedMessagesLatch.countDown();
                            });
                            clients.add(ws);
                            connectedLatch.countDown();
                        } else {
                            throw new IllegalStateException(r.cause());
                        }
                    });
        }

        if (!connectedLatch.await(timeout, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Unable to connect all clients...");
        }
        Log.infof("%s clients connected", numberOfClients);

        // Send messages
        CountDownLatch messagesLatch = new CountDownLatch(numberOfClients * numberOfMessages);
        LongAdder success = new LongAdder();
        LongAdder failure = new LongAdder();
        for (int i = 0; i < numberOfMessages; i++) {
            for (WebSocket ws : clients) {
                ws.writeTextMessage(payload, r -> {
                    if (r.succeeded()) {
                        success.increment();
                    } else {
                        failure.increment();
                        Log.error("Error sending a message to " + ws, r.cause());
                    }
                    messagesLatch.countDown();
                });
            }
            if (messageInterval.isPresent()) {
                Log.infof("Message %s sent, wait for %s ms", i + 1, messageInterval.get().toMillis());
                Thread.sleep(messageInterval.get().toMillis());
            }
        }

        if (!messagesLatch.await(timeout, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Unable to send all messages in time...");
        }

        if (success.sum() == numberOfClients * numberOfMessages) {
            Log.infof("%s messages sent to each connected client", numberOfMessages);
            if (!receivedMessagesLatch.await(timeout, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Number of replies received: " + receivedMessagesLatch.getCount());
            }
        } else {
            Log.warnf("%s failures when sending %s messages with %s clients", failure.sum(), numberOfMessages,
                    numberOfClients);
        }

        // Close all clients
        Log.infof("Closing %s clients", numberOfClients);
        CountDownLatch closedLatch = new CountDownLatch(numberOfClients);
        for (WebSocket ws : clients) {
            ws.close().onComplete(r -> {
                if (r.succeeded()) {
                    closedLatch.countDown();
                } else {
                    throw new IllegalStateException(r.cause());
                }
            });
        }
        clients.clear();
        if (!closedLatch.await(timeout, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Unable to close all clients...");
        }

        Log.infof("Finished in %s ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return 0;
    }

}
