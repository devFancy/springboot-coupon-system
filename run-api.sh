#!/bin/bash

APP_NAME="Coupon API"
JAR_PATH="./coupon/coupon-api/build/libs/coupon-api-*.jar"
PID_FILE="./coupon-api.pid"
LOG_FILE="./nohup-api.out"

stop_process() {
    local pid=$1
    if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
        echo "[INFO] $APP_NAME is not running or PID file is missing."
        return
    fi

    echo "[INFO] Stopping $APP_NAME (PID: $pid)..."
    kill "$pid" 2>/dev/null
    sleep 2

    if kill -0 "$pid" 2>/dev/null; then
        echo "[WARN] Force killing $APP_NAME (PID: $pid)..."
        kill -9 "$pid"
    else
        echo "[SUCCESS] $APP_NAME stopped."
    fi
}

case "$1" in
start)
    if [ -e "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "[WARN] $APP_NAME is already running."
        exit 1
    fi

    echo "[INFO] Starting $APP_NAME..."
    nohup java -jar \
    -Dspring.profiles.active=local \
    -Dspring.application.name=coupon-api-local \
    $JAR_PATH 1>$LOG_FILE 2>&1 &

    echo $! > $PID_FILE
    echo "[SUCCESS] $APP_NAME started. (PID: $(cat "$PID_FILE"))"
    echo "[INFO] You can check the log with: tail -f $LOG_FILE"
    ;;
stop)
    if [ ! -f "$PID_FILE" ]; then
        echo "[INFO] $APP_NAME is not running (PID file not found)."
        exit 0
    fi
    stop_process "$(cat "$PID_FILE")"
    rm -f "$PID_FILE"
    ;;
restart)
    echo "[INFO] Restarting $APP_NAME..."
    $0 stop
    sleep 2
    $0 start
    ;;
status)
    if [ -e "$PID_FILE" ] && ps -p "$(cat "$PID_FILE")" > /dev/null; then
       echo "[INFO] $APP_NAME is running, PID: $(cat "$PID_FILE")"
    else
       echo "[INFO] $APP_NAME is NOT running."
    fi
    ;;
*)
    echo "Usage: $0 {start|stop|restart|status}"
esac

exit 0
