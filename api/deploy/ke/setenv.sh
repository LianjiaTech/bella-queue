#!/bin/bash

SH_APOLLO_SERVER="${APOLLO_CONFIG_SERVER}"

if [ "x$ENVTYPE" = "xpreview" ]; then
  export SPRING_PROFILES_ACTIVE='preview'
  export APOLLO_SERVER="prev.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"PREV"}
  export APOLLO_CLUSTER=${APOLLO_CLUSTER:-"default"}
elif [ "x$ENVTYPE" = "xdocker" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
  export APOLLO_SERVER="test.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"TEST"}
  export APOLLO_CLUSTER="${APOLLO_CLUSTER:-default}"
elif [ "x$ENVTYPE" = "xtest" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
  export APOLLO_SERVER="test.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"TEST"}
  export APOLLO_CLUSTER=${APOLLO_CLUSTER:-"default"}
elif [ "x$ENVTYPE" = "xprod" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
  export APOLLO_SERVER="prod.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"PROD"}
  export APOLLO_CLUSTER=${APOLLO_CLUSTER:-"default"}
  export HAWK_SERVER_HOST="${HAWK_CONFIG_SERVER}"
  export HAWK_SERVER_SOCKET_PORT="8101"
elif [ "x$ENVTYPE" = "xdev" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
  export APOLLO_SERVER="dev.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"DEV"}
  export APOLLO_CLUSTER=${APOLLO_CLUSTER:-"default"}
elif [ -n "${ENVTYPE}" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
  export APOLLO_SERVER="test.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"TEST"}
  export APOLLO_CLUSTER=${APOLLO_CLUSTER:-"default"}
else
  export SPRING_PROFILES_ACTIVE='prod'
  export APOLLO_SERVER="prod.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"PROD"}
  export APOLLO_CLUSTER=${APOLLO_CLUSTER:-"default"}
  export HAWK_SERVER_HOST="${HAWK_CONFIG_SERVER}"
  export HAWK_SERVER_SOCKET_PORT="8101"
fi

USER_OPTS=""
USER_OPTS="$USER_OPTS -Xlog:gc*:${MATRIX_APPLOGS_DIR}/gc.log:time,tags"
USER_OPTS="$USER_OPTS -XX:AutoBoxCacheMax=20000 -Djava.security.egd=file:/dev/./urandom"
USER_OPTS="$USER_OPTS -XX:+PrintCommandLineFlags -XX:-OmitStackTraceInFastThrow"
USER_OPTS="$USER_OPTS -Djava.net.preferIPv4Stack=true -Djava.awt.headless=true -Dfile.encoding=UTF-8"
USER_OPTS="$USER_OPTS -Droot.path=${MATRIX_CODE_DIR}"
USER_OPTS="$USER_OPTS -Dlogging.path=${MATRIX_APPLOGS_DIR}"
USER_OPTS="$USER_OPTS -Djava.io.tmpdir=${MATRIX_CACHE_DIR}"
USER_OPTS="$USER_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}"
USER_OPTS="$USER_OPTS -Dapollo.meta=http://${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
USER_OPTS="$USER_OPTS -Dapollo.env=${APOLLO_ENV}"
USER_OPTS="$USER_OPTS -Dapollo.cacheDir=${MATRIX_PRIVDATA_DIR}"
USER_OPTS="$USER_OPTS -Dapollo.cluster=${APOLLO_CLUSTER}"
USER_OPTS="$USER_OPTS -Dapollo.enabled=true"

if [ -n "${HAWK_SERVER_HOST}" ]; then
  USER_OPTS="$USER_OPTS -Dhawk.server.host=${HAWK_SERVER_HOST}"
  USER_OPTS="$USER_OPTS -Dhawk.server.socket-port=${HAWK_SERVER_SOCKET_PORT}"
fi