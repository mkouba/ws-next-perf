#!/bin/bash
if [ -z "$QUARKUS_VERSIONS" ]; then
    QUARKUS_VERSIONS="3.15.1 3.16.3 3.17.0.CR1 999-SNAPSHOT"
fi

if [ -z "$WARMUP_CLIENTS" ]; then
    WARMUP_CLIENTS=500
fi

if [ -z "$TEST_CLIENTS" ]; then
    TEST_CLIENTS=5000
fi

if [ -z "$TIMEOUT" ]; then
    TIMEOUT=60
fi

# Use "-flame" to generate flame graphs with async profiler
# If async profiler is used then the following runtime variables must be set:
# sysctl kernel.perf_event_paranoid=1
# sysctl kernel.kptr_restrict=0
if [ -z "$ASYNC_PROFILER_PATH" ]; then
    ASYNC_PROFILER_PATH="/opt/java/async-profiler-3.0-linux-x64"
fi

contains_arg() {
    local search="$1"
    shift
    local found=false

    for arg in "$@"; do
        if [[ "$arg" == "$search" ]]; then
            found=true
            break
        fi
    done

    if [[ "$found" == true ]]; then
        return 0
    else
        return 1
    fi
}

wait_for_start() {
    counter=1
    while [ "$(jps | grep quarkus-run)" == "" ]
    do
      sleep 1
      echo "Waiting ${counter}s for the server to start.."
      let counter++
    done
    echo "Server started"
}

wait_for_stop() {
    counter=1
    while [ "$(jps | grep quarkus-run)" != "" ]
    do
      sleep 1
      echo "Waiting ${counter}s for the server to stop.."
      let counter++
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
    # additional time to start the http server
    sleep 2

    # run test client
    cd ../test-client
    # run warmup phase
    java -Dnumber.of.clients=$WARMUP_CLIENTS -Dtimeout=$TIMEOUT -jar target/quarkus-app/quarkus-run.jar

    # start async profiler if needed
    if contains_arg "-flame" "$@"; then
        # Get the server PID
        PID="$(jps | grep quarkus-run | cut -d ' ' -f 1)"
        $ASYNC_PROFILER_PATH/bin/asprof start $PID
        echo "Async profiler started for server PID: $PID"
    fi

    # run benchmark phase
    java -Dnumber.of.clients=$TEST_CLIENTS -Dtimeout=$TIMEOUT -jar target/quarkus-app/quarkus-run.jar

    # go to project root
    cd ../

    # stop async profiler if needed
    if contains_arg "-flame" "$@"; then
        # Get the server PID
        PID="$(jps | grep quarkus-run | cut -d ' ' -f 1)"
        FILE="flame_$i.html"
        $ASYNC_PROFILER_PATH/bin/asprof stop -f $FILE -o flamegraph $PID
        echo "Async profiler stopped for server PID: $PID, output file: $FILE"
    fi

    # wait for the server to stop
    wait_for_stop

    echo "Testing $i finished..."
done

cd test-client
mvn package -Dquarkus.package.main-class=summary
java -jar target/quarkus-app/quarkus-run.jar target/results


