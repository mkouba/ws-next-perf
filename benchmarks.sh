#!/bin/bash
if [ -z "$QUARKUS_VERSIONS" ]; then
    QUARKUS_VERSIONS="3.15.1 999-SNAPSHOT"
fi
if [ -z "$WARMUP_CLIENTS" ]; then
    WARMUP_CLIENTS=10
fi
if [ -z "$TEST_CLIENTS" ]; then
    TEST_CLIENTS=10
fi

wait_for_start() {
    while [ "$(jps | grep quarkus-run)" == "" ]
    do
      echo "Waiting for the server to start.."
      sleep 1
    done
    echo "Server started"
}

wait_for_stop() {
    while [ "$(jps | grep quarkus-run)" != "" ]
    do
      echo "Waiting for the server to stop.."
      sleep 1
    done
    echo "Server stopped"
}

echo "====================================================="
echo "WS Next - Quarkus versions to test: $QUARKUS_VERSIONS";
echo "Warmup clients: $WARMUP_CLIENTS";
echo "Test clients: $TEST_CLIENTS";
echo "====================================================="

# build the client
cd test-client
mvn clean package
cd ../

QUARKUS_VERSIONS_ARRAY=$(echo $QUARKUS_VERSIONS);

for i in $QUARKUS_VERSIONS_ARRAY
do
    # build and run server
    cd websocket-server-next
    mvn clean package -Dquarkus.platform.version=$i
    nohup java -Dtest-auto-exit=5s -jar target/quarkus-app/quarkus-run.jar &
    
    # wait for the server to start
    wait_for_start
    sleep 2

    # run test client
    cd ../test-client
    # run warmup phase
    java -Dnumber.of.clients=$WARMUP_CLIENTS -jar target/quarkus-app/quarkus-run.jar
    # run benchmark phase
    java -Dnumber.of.clients=$TEST_CLIENTS -jar target/quarkus-app/quarkus-run.jar

    # wait for the server to stop
    wait_for_stop

    echo "Testing $i finished..."
    cd ../
done

cd test-client
mvn package -Dquarkus.package.main-class=summary
java -jar target/quarkus-app/quarkus-run.jar target/results


