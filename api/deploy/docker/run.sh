#!/bin/bash
# Docker Compose 容器内启动脚本

set -e

echo "=== Bella Queue 启动脚本 ==="

# 检查 Java 环境
if [ -z "$JAVA_HOME" ]; then
    # 尝试查找 Java
    if command -v java >/dev/null 2>&1; then
        JAVA_CMD="java"
        echo "使用系统 Java: $(java -version 2>&1 | head -n1)"
    else
        echo "错误: 未找到 Java 环境"
        echo "请设置 JAVA_HOME 环境变量或安装 Java"
        exit 1
    fi
else
    JAVA_CMD="$JAVA_HOME/bin/java"
    echo "使用 JAVA_HOME: $JAVA_HOME"
fi

# 设置默认的内存参数
if [ -z "$JAVA_XMS" ]; then
    export JAVA_XMS="2048m"
fi

if [ -z "$JAVA_XMX" ]; then
    export JAVA_XMX="2048m"
fi

if [ -z "$JAVA_METASPACE_SIZE" ]; then
    export JAVA_METASPACE_SIZE="512m"
fi

if [ -z "$JAVA_MAX_METASPACE_SIZE" ]; then
    export JAVA_MAX_METASPACE_SIZE="512m"
fi

echo "内存配置: Xms=${JAVA_XMS}, Xmx=${JAVA_XMX}"

# 基础 JVM 参数
JAVA_OPTS="-server"
JAVA_OPTS="${JAVA_OPTS} -Xms${JAVA_XMS} -Xmx${JAVA_XMX}"
JAVA_OPTS="${JAVA_OPTS} -XX:MetaspaceSize=${JAVA_METASPACE_SIZE}"
JAVA_OPTS="${JAVA_OPTS} -XX:MaxMetaspaceSize=${JAVA_MAX_METASPACE_SIZE}"

# GC 配置
JAVA_OPTS="${JAVA_OPTS} -XX:+UseG1GC"

# 设置应用相关的环境变量
if [ -z "$APP_ROOT_DIR" ]; then
    export APP_ROOT_DIR="$(pwd)"
fi

if [ -z "$APP_LOG_DIR" ]; then
    export APP_LOG_DIR="${APP_ROOT_DIR}/logs"
    mkdir -p "${APP_LOG_DIR}"
fi

if [ -z "$APP_TEMP_DIR" ]; then
    export APP_TEMP_DIR="${APP_ROOT_DIR}/tmp"
    mkdir -p "${APP_TEMP_DIR}"
fi

# 查找应用 JAR 文件
if [ -n "$JAR_FILE" ]; then
    # 如果指定了 JAR 文件路径
    if [ ! -f "$JAR_FILE" ]; then
        echo "错误: 指定的 JAR 文件不存在: $JAR_FILE"
        exit 1
    fi
else
    # 自动查找 JAR 文件
    if [ -d "lib" ]; then
        JAR_FILE=$(find lib -name "*.jar" | head -n1)
    elif [ -d "target" ]; then
        JAR_FILE=$(find target -name "*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | head -n1)
    else
        JAR_FILE=$(find . -maxdepth 2 -name "*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | head -n1)
    fi

    if [ -z "$JAR_FILE" ]; then
        echo "错误: 未找到应用 JAR 文件"
        echo "请指定 JAR_FILE 环境变量或确保 JAR 文件在 lib/ 或 target/ 目录中"
        exit 1
    fi
fi

echo "应用 JAR: $JAR_FILE"

# 加载环境配置
SCRIPT_DIR="$(dirname "$0")"
if [ -f "${SCRIPT_DIR}/setenv.sh" ]; then
    echo "加载环境配置..."
    source "${SCRIPT_DIR}/setenv.sh"
else
    echo "警告: 未找到 setenv.sh，使用默认配置"
fi

# 构建最终的启动命令
if [ -n "$USER_OPTS" ]; then
    JAVA_OPTS="${JAVA_OPTS} ${USER_OPTS}"
fi

# 应用程序参数
APP_ARGS=""
if [ -n "$APP_ARGUMENTS" ]; then
    APP_ARGS="$APP_ARGUMENTS"
fi

# 显示启动信息
echo ""
echo "启动参数:"
echo "  Java 命令: $JAVA_CMD"
echo "  应用目录: $APP_ROOT_DIR"
echo "  日志目录: $APP_LOG_DIR"
echo "  临时目录: $APP_TEMP_DIR"
echo ""

# 启动应用
echo "启动 Bella Queue..."
echo "完整命令: $JAVA_CMD $JAVA_OPTS -jar $JAR_FILE $APP_ARGS"
echo ""

# 使用 exec 替换当前进程，确保信号传递正确
exec $JAVA_CMD $JAVA_OPTS -jar "$JAR_FILE" $APP_ARGS
