#!/bin/bash
# Bella Queue Docker Compose 启动脚本

set -e

show_help() {
    echo "Bella Queue Docker Compose 部署脚本"
    echo "一键启动包含 MySQL、Redis、API 的完整服务栈"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -h, --help              显示帮助信息"
    echo "  -b, --build             重新构建镜像"
    echo "  --api-port PORT         API 端口 (默认: 8080)"
    echo "  --db-name NAME          数据库名称 (默认: bella_queue)"
    echo "  --db-username USER      数据库用户名 (默认: bella_user)"
    echo "  --db-password PWD       数据库密码 (默认: 123456)"
    echo "  --db-root-password PWD  数据库root密码 (默认: 123456)"
    echo "  --redis-password PWD    Redis 密码 (默认: 123456)"
    echo "  --queue-secret-key KEY  队列加密密钥 (默认: 0123456789abcdef)"
    echo "  --batch-thread-size N   批处理拆分线程数 (默认: 1)"
    echo "  --bella-openapi-host URL    Bella OpenAPI服务地址"
    echo "  --bella-openapi-key KEY     Bella OpenAPI密钥"
    echo ""
    echo "高级参数（通过环境变量）:"
    echo "  BELLA_QUEUE_BATCH_FILE_PATH         批处理文件存储路径 (默认: /tmp/bella-queue/batch)"
    echo "  BATCH_MAX_SPLITTING     批处理最大分割数 (默认: 100)"
    echo "  BELLA_QUEUE_LOAD_BATCH_SIZE         加载批处理大小 (默认: 100)"
    echo ""
}

# 默认配置
BUILD_ARG=""
API_PORT="8080"
DB_NAME="bella_queue"
BELLA_DB_USERNAME="bella_user"
BELLA_DB_PASSWORD="123456"
DB_ROOT_PASSWORD="123456"
QUEUE_REDIS_PASSWORD="123456"
QUEUE_SECRET_KEY="0123456789abcdef"
BATCH_THREAD_SIZE="1"
BELLA_OPEN_API_BASE=""  # 必须由用户提供
BELLA_OPEN_CONSOLE_KEY=""  # 必须由用户提供
BELLA_QUEUE_BATCH_FILE_PATH="${BELLA_QUEUE_BATCH_FILE_PATH:-/tmp/bella-queue/batch}"
BATCH_MAX_SPLITTING="${BATCH_MAX_SPLITTING:-100}"
BELLA_QUEUE_LOAD_BATCH_SIZE="${BELLA_QUEUE_LOAD_BATCH_SIZE:-100}"

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -b|--build)
            BUILD_ARG="--build"
            shift
            ;;
        --api-port)
            API_PORT="$2"
            shift 2
            ;;
        --db-name)
            DB_NAME="$2"
            shift 2
            ;;
        --db-username)
            BELLA_DB_USERNAME="$2"
            shift 2
            ;;
        --db-password)
            BELLA_DB_PASSWORD="$2"
            shift 2
            ;;
        --db-root-password)
            DB_ROOT_PASSWORD="$2"
            shift 2
            ;;
        --redis-password)
            QUEUE_REDIS_PASSWORD="$2"
            shift 2
            ;;
        --queue-secret-key)
            QUEUE_SECRET_KEY="$2"
            shift 2
            ;;
        --batch-thread-size)
            BATCH_THREAD_SIZE="$2"
            shift 2
            ;;
        --threads)
            BATCH_THREAD_SIZE="$2"
            shift 2
            ;;
        --bella-openapi-host)
            BELLA_OPEN_API_BASE="$2"
            shift 2
            ;;
        --bella-openapi-key)
            BELLA_OPEN_CONSOLE_KEY="$2"
            shift 2
            ;;
        *)
            echo "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
done

echo "======================================="
echo "     Bella Queue Docker Compose"
echo "======================================="

# 验证必需参数
if [ -z "$BELLA_OPEN_API_BASE" ] || [ -z "$BELLA_OPEN_CONSOLE_KEY" ]; then
    echo "❌ 错误: 缺少必需的 Bella OpenAPI 配置"
    echo ""
    echo "Bella Queue 依赖 Bella OpenAPI 服务才能正常工作，以下参数是必需的："
    echo "  --bella-openapi-host URL     Bella OpenAPI服务地址"
    echo "  --bella-openapi-key KEY      Bella OpenAPI密钥"
    echo ""
    echo "示例："
    echo "  ./start.sh --bella-openapi-host https://your-bella-openapi-service.com --bella-openapi-key your_bella_openapi_secret_key"
    echo ""
    echo "没有这些配置，系统虽然可以启动，但以下功能将无法使用："
    echo "  • API 认证和授权"
    echo "  • 文件上传下载"
    echo "  • 批处理任务执行"
    echo "  • 队列任务路由"
    echo ""
    exit 1
fi

# 检查环境
if ! command -v docker &> /dev/null || ! command -v docker-compose &> /dev/null; then
    echo "错误: 请安装 Docker 和 docker-compose"
    exit 1
fi

# 创建目录和文件
mkdir -p data/{mysql,redis,api/{logs,cache}}

# 检查是否需要强制构建
if [ ! -d "../../release" ] && [ "$BUILD_ARG" != "--build" ]; then
    echo "⚠️  警告: 检测到这是首次启动，自动启用构建模式"
    BUILD_ARG="--build"
fi

# 设置环境变量
export API_PORT=$API_PORT
export DB_NAME=$DB_NAME
export BELLA_DB_USERNAME=$BELLA_DB_USERNAME
export BELLA_DB_PASSWORD=$BELLA_DB_PASSWORD
export DB_ROOT_PASSWORD=$DB_ROOT_PASSWORD
export QUEUE_REDIS_HOST="redis"
export QUEUE_REDIS_PORT="6379"
export QUEUE_REDIS_PASSWORD=$QUEUE_REDIS_PASSWORD
export QUEUE_SECRET_KEY=$QUEUE_SECRET_KEY
export BATCH_THREAD_SIZE=$BATCH_THREAD_SIZE
export BELLA_OPEN_API_BASE=$BELLA_OPEN_API_BASE
export BELLA_OPEN_CONSOLE_KEY=$BELLA_OPEN_CONSOLE_KEY
export BELLA_QUEUE_BATCH_FILE_PATH=$BELLA_QUEUE_BATCH_FILE_PATH
export BATCH_MAX_SPLITTING=$BATCH_MAX_SPLITTING
export BELLA_QUEUE_LOAD_BATCH_SIZE=$BELLA_QUEUE_LOAD_BATCH_SIZE

# 构建应用（如果需要）
if [ "$BUILD_ARG" = "--build" ]; then
    echo "构建应用..."
    ./build.sh -Dmaven.test.skip=true
fi

echo "启动服务..."
docker-compose up -d $BUILD_ARG

sleep 10

if docker-compose ps | grep -q "Up"; then
    echo "✅ 服务启动成功！"
    echo "  API: http://localhost:$API_PORT"
    echo "  健康检查: http://localhost:$API_PORT/actuator/health"
    echo ""
    echo "🔧 当前配置:"
    echo "  Bella OpenAPI: $BELLA_OPEN_API_BASE"
    echo "  批处理拆分线程数: $BATCH_THREAD_SIZE"
else
    echo "❌ 服务启动失败"
    exit 1
fi
