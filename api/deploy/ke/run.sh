#!/bin/bash

set -e

FLUENT_LOGS_ENABLED=${FAST_ENABLED:=false}
if [ -n "${SERVICE_ID}" ] && [ -n "${SERVICE_GROUP}" ] && [ -n "${POD_NAME}" ] && [ -n "${POD_NAMESPACE}" ] && [ -n "${CONTAINER_NAME}" ] && [ "${FLUENT_LOGS_ENABLED}" == "true"  ];then
  echo "--- 开启天眼日志采集..."
  export FLUENT_APPLOGS_DIR=/applogs/${SERVICE_ID}_${SERVICE_GROUP}_${POD_NAME}_${POD_NAMESPACE}_${CONTAINER_NAME}
  export FLUENT_ACCESSLOGS_DIR=/accesslogs/${SERVICE_ID}_${SERVICE_GROUP}_${POD_NAME}_${POD_NAMESPACE}_${CONTAINER_NAME}
  mkdir -p ${FLUENT_APPLOGS_DIR}
  mkdir -p ${FLUENT_ACCESSLOGS_DIR}
  rm -rf /data0/www/applogs /data0/www/logs
  ln -s ${FLUENT_APPLOGS_DIR} /data0/www/applogs
  ln -s ${FLUENT_ACCESSLOGS_DIR} /data0/www/logs
fi


if [ "$JAVA_HOME" = "" ]; then
    echo "Error: JAVA_HOME is not set."
    exit 1
fi

JAVA_COMMENT="$JAVA_HOME/bin/java"

if [ "$Xmn" = "" ]; then
    export Xmn="1024m"
fi

if [ "$Xms" = "" ]; then
    export Xms="4096m"
fi

if [ "$Xmx" = "" ]; then
    export Xmx="4096m"
fi

if [ "$MetaspaceSize" = "" ]; then
    export MetaspaceSize="512m"
fi

if [ "$MaxMetaspaceSize" = "" ]; then
    export MaxMetaspaceSize="512m"
fi

if [ "$CMSINIT" = "" ]; then
    export CMSINIT="70"
fi
#jvm 参数
JAVA_OPTS="$JAVA_OPTS -server -Xms${Xms} -Xmx${Xmx}"
JAVA_OPTS="$JAVA_OPTS -XX:MetaspaceSize=${MetaspaceSize} -XX:MaxMetaspaceSize=${MaxMetaspaceSize}"
JAVA_OPTS="$JAVA_OPTS -Dlogging.path=${MATRIX_APPLOGS_DIR}"
JAVA_OPTS="$JAVA_OPTS -Dsun.net.inetaddr.ttl=1 -Dsun.net.inetaddr.negative.ttl=1"
# GC日志已在setenv.sh中通过-Xlog配置
Xmn_OPTS="-Xmn${Xmn}"
GC_OPTS="$GC_OPTS -XX:+UseG1GC"

# 测试、预览开启DEBUG功能，生产开启JMX
if [ "$ENVTYPE" != "" ]; then
    JAVA_OPTS="$JAVA_OPTS -Djavax.net.debug=ssl -Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=$DEBUGPORT"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=$JMXPORT -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=$HOSTNAME"
else
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=$JMXPORT -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=$HOSTNAME"
fi

#待启动jar
JARFILE=`find "$MATRIX_CODE_DIR/lib" -name *.jar`

#加载个性化配置
if [ -f "${MATRIX_CODE_DIR}/bin/setenv.sh" ];then
    echo ${MATRIX_CODE_DIR}/bin/setenv.sh
    source "${MATRIX_CODE_DIR}/bin/setenv.sh"
fi

if [[ "${GC_OPTS}" =~ UseG1GC ]];then
    if [[ "${USER_OPTS}" != "" ]]; then
        if [[ "${USER_ARGS}" != "" ]];then
            echo "$JAVA_COMMENT $JAVA_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE ${USER_ARGS}"
            exec $JAVA_COMMENT $JAVA_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE ${USER_ARGS}
        else
            echo "$JAVA_COMMENT $JAVA_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE"
            exec $JAVA_COMMENT $JAVA_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE
        fi
    else
        echo "$JAVA_COMMENT $JAVA_OPTS $GC_OPTS -jar $JARFILE ${USER_ARGS}"
        exec $JAVA_COMMENT $JAVA_OPTS $GC_OPTS -jar $JARFILE ${USER_ARGS}
    fi
else
    if [[ "${USER_OPTS}" != "" ]]; then
        if [[ "${USER_ARGS}" != "" ]];then
            echo "$JAVA_COMMENT $JAVA_OPTS $Xmn_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE ${USER_ARGS}"
            exec $JAVA_COMMENT $JAVA_OPTS $Xmn_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE ${USER_ARGS}
        else
            echo "$JAVA_COMMENT $JAVA_OPTS $Xmn_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE"
            exec $JAVA_COMMENT $JAVA_OPTS $Xmn_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE
        fi
    else
        echo "$JAVA_COMMENT $JAVA_OPTS $Xmn_OPTS $GC_OPTS -jar $JARFILE ${USER_ARGS}"
        exec $JAVA_COMMENT $JAVA_OPTS $Xmn_OPTS $GC_OPTS -jar $JARFILE ${USER_ARGS}
    fi
fi