package com.github.mkouba.wsperf;

import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.pause;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.repeat;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.ws;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

public class ToLowerCaseSimulation extends Simulation {

        static int numberOfClients = Integer.parseInt(System.getProperty("number.of.clients", "1000"));

        static int numberOfMessages = Integer.parseInt(System.getProperty("number.of.messages", "1000"));

        static int rampTime = Integer.parseInt(System.getProperty("ramp.time", "15"));

        static String serverHost = System.getProperty("server.host", "localhost");

        static int serverPort = Integer.parseInt(System.getProperty("server.port", "8080"));

        static String serverPath = System.getProperty("server.path", "/to-lower-case");

        HttpProtocolBuilder httpProtocol = http
                        .baseUrl("http://" + serverHost + ":" + serverPort)
                        .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .doNotTrackHeader("1")
                        .acceptLanguageHeader("en-US,en;q=0.5")
                        .acceptEncodingHeader("gzip, deflate")
                        .userAgentHeader("Gatling2")
                        .wsBaseUrl("ws://" + serverHost + ":" + serverPort);

        ScenarioBuilder scn = scenario("WebSocket")
                        .exec(
                                        // exec(session -> session.set("id", "Gatling" + session.userId())),
                                        ws("Connect WS").connect(serverPath),
                                        pause(1),
                                        repeat(numberOfMessages, "i").on(
                                                        ws("Convert Foo")
                                                                        .sendText("FOO")
                                                                        .await(30).on(
                                                                                        ws.checkTextMessage(
                                                                                                        "Check result")
                                                                                                        .check(bodyString()
                                                                                                                        .is("foo")))),
                                        ws("Close WS").close());

        {
                setUp(
                                scn.injectOpen(rampUsers(numberOfClients).during(rampTime))).protocols(httpProtocol);
        }

}
