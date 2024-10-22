package com.github.mkouba.wsperf;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;

@QuarkusMain(name = "test")
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

    @ConfigProperty(name = "timeout", defaultValue = "60")
    long timeout;

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
        int numberOfClientMessages = numberOfClients * numberOfMessages;
        CountDownLatch receivedMessagesLatch = new CountDownLatch(numberOfClientMessages);
        CountDownLatch sendMessagesLatch = new CountDownLatch(numberOfClientMessages);
        String payload = "FOO";
        AtomicReference<String> quarkusVersion = new AtomicReference<>();

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
                                if (s.startsWith("_")) {
                                    quarkusVersion.compareAndSet(null, s.substring(1));
                                } else {
                                    if (!s.equals(payload.toLowerCase())) {
                                        Log.errorf("Received invalid message from the server: %s", s);
                                    }
                                    receivedMessagesLatch.countDown();
                                    long received = receivedMessagesLatch.getCount();
                                    if (received % (numberOfClientMessages / 10) == 0) {
                                        Log.infof("%s messages received", numberOfClientMessages - received);
                                    }
                                }
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
                    sendMessagesLatch.countDown();
                    long sent = sendMessagesLatch.getCount();
                    if (sent % (numberOfClientMessages / 10) == 0) {
                        Log.infof("%s messages sent", numberOfClientMessages - sent);
                    }
                });
            }
            if (messageInterval.isPresent()) {
                Log.infof("Message %s sent, wait for %s ms", i + 1, messageInterval.get().toMillis());
                Thread.sleep(messageInterval.get().toMillis());
            }
        }

        if (!sendMessagesLatch.await(timeout, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Unable to send all messages in time...");
        }

        boolean failed = false;
        if (success.sum() == numberOfClientMessages) {
            Log.infof("%s messages sent to each connected client", numberOfMessages);
            if (!receivedMessagesLatch.await(timeout, TimeUnit.SECONDS)) {
                Log.warnf("Incorrect number of replies received: %s", receivedMessagesLatch.getCount());
                failed = true;
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

        long timeTaken = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        if (failed) {
            Log.warnf("Failed in %s ms", timeTaken);
            return 1;
        }

        Log.infof("Finished in %s ms", timeTaken);

        LocalDateTime timestamp = LocalDateTime.now();
        JsonObject res = new JsonObject();
        res.put("timestamp", timestamp.toString());
        res.put("timeTaken", timeTaken);
        res.put("quarkusVersion", quarkusVersion.get());
        res.put("numberOfClients", numberOfClients);
        res.put("numberOfMessages", numberOfMessages);

        File resultsDir = new File("target/results");
        Files.createDirectories(resultsDir.toPath());
        Files.writeString(new File(resultsDir, quarkusVersion.get() + ".json").toPath(), res.toString());

        return 0;
    }

}
