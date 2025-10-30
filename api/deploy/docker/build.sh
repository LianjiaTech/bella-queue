#!/bin/bash
# Docker Compose 部署构建脚本
# 使用方法: ./build.sh [Maven参数]
# 例如: ./build.sh -Dmaven.test.skip=true

ARGS="$*"

set -e

# 切换到 API 项目根目录
cd "$(dirname "$0")/../.."

echo "=== Bella Queue Docker Compose 构建 ==="
echo "构建参数: ${ARGS}"

# 清理之前的构建产物
rm -rf release/

# 执行 Maven 构建
echo "执行 Maven 构建..."
mvn clean package ${ARGS}

# 创建发布目录
mkdir -p release/{bin,lib}

# 复制脚本文件 - 使用 docker 目录下的脚本
echo "复制部署脚本..."
chmod +x deploy/docker/setenv.sh && cp deploy/docker/setenv.sh release/bin/
chmod +x deploy/docker/run.sh && cp deploy/docker/run.sh release/bin/

# 复制应用 JAR 包
echo "复制应用文件..."
cp target/*.jar release/lib/

echo "✅ Docker Compose 构建完成！"
echo ""
echo "构建产物结构:"
echo "  release/"
echo "  ├── bin/"
echo "  │   ├── setenv.sh    # 环境配置"
echo "  │   └── run.sh       # 启动脚本"
echo "  └── lib/"
echo "      └── bella-queue-*.jar  # 应用程序"
echo ""
echo "使用方法:"
echo "  ./start.sh --build   # 构建并启动完整服务栈"
