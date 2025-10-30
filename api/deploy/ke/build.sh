#!/bin/sh
# 构建命令为 sh build.sh ARGS_DEV, 可传入ARGS_DEV作为用户变量 如 -Dmaven.test.skip=true

ARGS_DEV="$@"

set -e

# 切换到api目录执行构建
cd "$(dirname "$0")/../.."

rm -rf release/
mvn clean package ${ARGS_DEV}

mkdir -p release/{bin,lib}
chmod +x deploy/ke/setenv.sh && cp deploy/ke/setenv.sh release/bin/
chmod +x deploy/ke/run.sh && cp deploy/ke/run.sh release/bin/
cp target/*.jar release/lib/
tar czvf release.tar.gz release
