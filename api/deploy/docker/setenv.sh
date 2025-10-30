#!/bin/bash
# Docker Compose 环境配置脚本
# 此脚本设置 JVM 参数和应用程序环境变量

# 检查 Spring Profile 配置
if [ -z "$SPRING_PROFILES_ACTIVE" ]; then
    echo "环境配置: 使用默认 profile"
else
    echo "环境配置: SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE"
fi

# 初始化 JVM 参数
USER_OPTS=""

# GC 日志配置
if [ -n "$APP_LOG_DIR" ]; then
    USER_OPTS="${USER_OPTS} -Xlog:gc*:${APP_LOG_DIR}/gc.log:time,tags"
fi

# JVM 优化参数
USER_OPTS="${USER_OPTS} -XX:AutoBoxCacheMax=20000"
USER_OPTS="${USER_OPTS} -Djava.security.egd=file:/dev/./urandom"
USER_OPTS="${USER_OPTS} -XX:+PrintCommandLineFlags"
USER_OPTS="${USER_OPTS} -XX:-OmitStackTraceInFastThrow"
USER_OPTS="${USER_OPTS} -Djava.net.preferIPv4Stack=true"
USER_OPTS="${USER_OPTS} -Djava.awt.headless=true"
USER_OPTS="${USER_OPTS} -Dfile.encoding=UTF-8"

# 应用路径参数
if [ -n "$APP_ROOT_DIR" ]; then
    USER_OPTS="${USER_OPTS} -Droot.path=${APP_ROOT_DIR}"
fi

if [ -n "$APP_LOG_DIR" ]; then
    USER_OPTS="${USER_OPTS} -Dlogging.path=${APP_LOG_DIR}"
fi

if [ -n "$APP_TEMP_DIR" ]; then
    USER_OPTS="${USER_OPTS} -Djava.io.tmpdir=${APP_TEMP_DIR}"
fi

# Spring Profile 参数
USER_OPTS="${USER_OPTS} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}"

# 数据库连接参数
if [ -n "${BELLA_DB_URL}" ]; then
    USER_OPTS="${USER_OPTS} -DBELLA_DB_URL=${BELLA_DB_URL}"
fi

if [ -n "${BELLA_DB_USERNAME}" ]; then
    USER_OPTS="${USER_OPTS} -DBELLA_DB_USERNAME=${BELLA_DB_USERNAME}"
fi

if [ -n "${BELLA_DB_PASSWORD}" ]; then
    USER_OPTS="${USER_OPTS} -DBELLA_DB_PASSWORD=${BELLA_DB_PASSWORD}"
fi

# Redis 连接参数
if [ -n "${QUEUE_REDIS_HOST}" ]; then
    USER_OPTS="${USER_OPTS} -DQUEUE_REDIS_HOST=${QUEUE_REDIS_HOST}"
fi

if [ -n "${QUEUE_REDIS_PORT}" ]; then
    USER_OPTS="${USER_OPTS} -DQUEUE_REDIS_PORT=${QUEUE_REDIS_PORT}"
fi

if [ -n "${QUEUE_REDIS_PASSWORD}" ]; then
    USER_OPTS="${USER_OPTS} -DQUEUE_REDIS_PASSWORD=${QUEUE_REDIS_PASSWORD}"
fi

# 队列配置参数
if [ -n "${QUEUE_SECRET_KEY}" ]; then
    USER_OPTS="${USER_OPTS} -DQUEUE_SECRET_KEY=${QUEUE_SECRET_KEY}"
fi

if [ -n "${BATCH_THREAD_SIZE}" ]; then
    USER_OPTS="${USER_OPTS} -DBATCH_THREAD_SIZE=${BATCH_THREAD_SIZE}"
fi

# Bella OpenAPI 参数
if [ -n "${BELLA_OPEN_API_BASE}" ]; then
    USER_OPTS="${USER_OPTS} -DBELLA_OPEN_API_BASE=${BELLA_OPEN_API_BASE}"
fi

if [ -n "${BELLA_OPEN_CONSOLE_KEY}" ]; then
    USER_OPTS="${USER_OPTS} -DBELLA_OPEN_CONSOLE_KEY=${BELLA_OPEN_CONSOLE_KEY}"
fi

# 批处理相关参数
if [ -n "${BELLA_QUEUE_BATCH_FILE_PATH}" ]; then
    USER_OPTS="${USER_OPTS} -DBELLA_QUEUE_BATCH_FILE_PATH=${BELLA_QUEUE_BATCH_FILE_PATH}"
fi

if [ -n "${BATCH_MAX_SPLITTING}" ]; then
    USER_OPTS="${USER_OPTS} -DBATCH_MAX_SPLITTING=${BATCH_MAX_SPLITTING}"
fi

if [ -n "${BELLA_QUEUE_LOAD_BATCH_SIZE}" ]; then
    USER_OPTS="${USER_OPTS} -DBELLA_QUEUE_LOAD_BATCH_SIZE=${BELLA_QUEUE_LOAD_BATCH_SIZE}"
fi

echo "JVM 参数配置完成"
