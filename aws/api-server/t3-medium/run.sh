#!/bin/bash

PORT=8080
WAR=coupon-api-{APP_VERSION}.jar
PID_NAME=coupon_api_${PORT}.pid

function stop_process {
    max_try=5
    pid=$1
    try_cnt=1
    echo "process id: ${pid}"
    while true; do
        if [ ${try_cnt} -ge ${max_try} ]; then
            echo "exceed the number of try to kill process"
            break;
        fi
        if [ ! -f "/proc/${pid}/stat" ]; then
            echo "process is killed: ${pid}"
            break;
        fi

        echo "try to stop process(${try_cnt}/${max_try}): ${pid}"
        kill ${pid}
        try_cnt=$((try_cnt + 1))
        sleep 1

    done
}

case "$1" in
start)
   nohup java -server \
   -Xms2048m \
   -Xmx2048m \
   -Dspring.jpa.hibernate.ddl-auto=update \
   -Dserver.tomcat.threads.max=200 \
   -Dserver.tomcat.threads.min-spare=50 \
   -Dserver.tomcat.accept-count=2000 \
   -Duser.timezone=Asia/Seoul \
   -Dspring.profiles.active=live \
   -Dspring.kafka.bootstrap-servers={인프라_서버_Private_IP}:9092,{인프라_서버_Private_IP}:9093 \
   -Dlogging.file.path=/home/ubuntu/springboot-coupon-system/logs/coupon-api \
   -Dredisson.rate-limiter.coupon-issue.tps=2700 \
   -jar ./${WAR} 1>nohup.out 2>&1 & echo $! > ${PID_NAME}

   tail -f ./nohup.out
   ;;
stop)
   pid=`cat ./${PID_NAME}`
   stop_process ${pid}
   rm ./${PID_NAME}
   ;;
restart)
   $0 stop
   $0 start
   ;;
status)
   if [ -e ./${PID_NAME} ]; then
      echo ${PID_NAME}.sh is running, pid=`cat ./${PID_NAME}`
   else
      echo ${PID_NAME} is NOT running
      exit 1
   fi
   ;;
*)
   echo "Usage: $0 {start|stop|status|restart}"
esac

exit 0