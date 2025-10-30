# Bella-Queue Worker接入

该文档旨在为希望接入Bella-Queue成为Worker能力点的用户提供详细的接入指引。

## 一. 自部署推理模型接入

### 1.1 接入流程

1. **部署模型**：部署推理模型并记录部署信息
2. **创建应用**：
    - 调用Bella-Queue创建queue
    - 创建应用、部署组、启动部署组、创建路由
    - 记录应用信息到数据库
3. **注册模型**：向系统注册已部署的模型
4. **注册渠道**：调用OpenApi注册包含queue信息的渠道（mode=route）

### 1.1.1 queue注册

由接入方维护queue名字的生成规则，bella-queue只做基础规则约束。

```bash
curl -X POST "https://${openApiBase}/v1/queue/register" \
-H "Content-Type: application/json" \
-H "Authorization: Bearer ${openApiKey}" \
-d '{
"queue": "my-task-queue",
"endpoint": "/v1/chat/completions"
}'
```

#### 参数说明:

- `queue`: 队列名称，只能包含字母、数字、下划线、分隔符，长度不超过64字符。全局唯一。
- `endpoint`: 处理任务的能力点

### 1.1.2 渠道注册

```bash
  curl -X POST "http://${openApiBase}/console/metadata/channel" \
    -H "Content-Type: application/json" \
    -d '{
      "entityType": "channel",
      "entityCode": "openai-gpt4",
      "dataDestination": "production", 
      "priority": "high",
      "protocol": "openai",
      "supplier": "openai",
      "url": "https://your-model-service.com/v1",
      "channelInfo": "{\"model\":\"gpt-4\"}",
      "priceInfo": "{\"input\":0.03,\"output\":0.06}",
      "trialEnabled": 1,
      "ownerType": "system",
      "ownerCode": "admin",
      "ownerName": "Administrator", 
      "visibility": "public",
      "queueMode": 2,
      "queueName": "queueName"
    }'
```

#### 参数说明:

- `queueMode`: 队列模式，取值来自QueueMode枚举
    - `0` (QueueMode.NONE): 无模式，不支持pull或route
    - `1` (QueueMode.PULL): 支持拉取模式，Worker主动拉取任务
    - `2` (QueueMode.ROUTE): 支持路由模式，渠道绑定到指定的queueName，任务会被路由到对应的队列处理
    - `3` (QueueMode.BOTH): 同时支持拉取和路由模式
- `queueName`: 处理任务的能力点

### 1.2 实现Worker

Worker实现的完整流程如下：

#### 步骤1：获取EventBus配置

Worker节点向 Bella-Queue 发起请求：

- **接口**: `GET /v1/queue/eventbus`
- **作用**: 获取 EventBus 的地址和 topic 信息，用于后续事件通知
- **返回**: eventbus 地址 和 topic 信息

#### 步骤2：拉取待处理任务

Worker节点继续请求：

- **接口**: `POST /v1/queue/take`
- **作用**: 从任务队列中拉取待处理的任务
- **返回**: 任务列表，包含每个任务的 instanceId

#### 步骤3：执行任务

Worker节点根据任务类型执行任务：

- 支持 blocking/streaming 模式 或 batch/callback 模式

#### 步骤4：任务处理后的通知方式（根据任务类型不同）

##### 4a. 对于 blocking/streaming 模式任务：

**发送 Progress 事件（实时进度）**

- Worker使用 topic + instanceId 构造 StreamKey
- 通过 EventBus 实时发送任务进度事件

**发送 Completion 事件（任务完成）**

- 任务完成后，通过 EventBus 发送完成事件，包含任务结果
- 上游业务系统可订阅该事件获取结果

##### 4b. 对于 batch/callback 模式任务：

**提交任务结果**

- **接口**: `POST /v1/queue/{taskId}/complete`
- **作用**: 提交任务处理结果
- **返回**: 确认信息（如 taskId）
- 若配置了 HTTP 回调，系统还会触发回调通知上游业务系统

### 1.2.1 获取eventbus配置

获取EventBus连接配置，用户在线任务（blocking, streaming）执行进度、结果通信。

```bash
curl -X GET "https://${openApiBase}/v1/queue/eventbus" \
-H "Authorization: Bearer ${openApiKey}"
```

#### 响应示例:

```json
{
  "url": "redis://your-redis-host:6379",
  "topic": "bella:eventbus:"
}
```

#### 响应字段说明:

- `url`: eventbus地址（默认redis）
- `topic`: EventBus主题前缀

### 1.2.2 拉取任务

从指定队列中拉取待处理的任务，支持多队列同时拉取和多种拉取策略。

```bash
curl -X POST "https://${openApiBase}/v1/queue/take" \
-H "Content-Type: application/json" \
-H "Authorization: Bearer ${openApiKey}" \
-d '{
"queues": ["my-task-queue:0", "my-task-queue:1"],
"size": 10,
"strategy": "fifo"
}'
```

#### 请求参数:

- `queues`: 队列列表，格式为"队列名:优先级"
- `size`: 拉取任务的数量上限
- `strategy`: 队列拉取策略（默认：`fifo`）
    - `fifo`: 先进先出，按照任务入队时间先后拉取，入队越早优先级越高，最多只能拉取10个。
    - `round_robin`: 轮询模式，在多个队列间轮流拉取。
    - `active_passive`: 主备模式，优先拉取主队列，主队列无任务时拉取备队列

#### 响应示例:

```json
{
  "my-task-queue:0": [
    {
      "task_id": "task-123",
      "endpoint": "/v1/chat/completions",
      "queue": "my-task-queue",
      "level": 0,
      "data": {
      },
      "status": "queued",
      "instance_id": "instance-456",
      "start_time": 1635724800000,
      "response_mode": "blocking"
    }
  ]
}
```

#### 响应字段说明:

- `task_id`: 任务唯一标识符
- `endpoint`: 任务处理能力点
- `queue`: 队列名称
- `level`: 队列优先级
- `data`: 上游提交的任务数据
- `status`: 任务状态，拉取后为"queued"
    - `waiting`: 等待调度
    - `queued`: 已入队，正在处理
    - `succeeded`: 执行成功
    - `failed`: 执行失败
    - `timeout`: 执行超时
    - `cancelled`: 已取消
- `instance_id`: 处理节点实例ID（用于blocking/streaming模式）
- `start_time`: 任务开始处理时间戳

### 1.2.3 完成任务（batch和callback类型任务）

batch和callback类型任务需要通过该接口提交任务处理结果，标记任务完成并更新任务状态。

```bash
curl -X POST "https://${openApiBase}/v1/queue/{taskId}/complete" \
-H "Content-Type: application/json" \
-H "Authorization: Bearer ${openApiKey}" \
-d '{
  "status_code":200,
  "request_id": "",
  "body":{
  }
}'
```

#### 请求参数:

- `{taskId}`: 路径参数，任务唯一标识符
- 请求体:
  ```json
  {
    "status_code": 200,
    "request_id": "", 
    "body": {}
  }
  ```
    - `status_code`: 状态码，200表示成功，其他表示失败
    - `request_id`: 请求ID，填入task_id
    - `body`: 具体响应内容

#### 响应示例:

```
task-123
```

### 1.2.4 进度报告（blocking/streaming任务）

对于blocking和streaming模式的任务，Worker需要通过Redis Stream发送进度和完成事件，以便上游能够实时接收处理进度。

#### StreamKey构造规则：

- 格式：`{eventbus.topic}{instanceId}`
    - `instanceId`：从拉取任务响应中获取的处理节点实例ID
    - 示例：`bella:eventbus:instance-456`

#### 事件类型：

- Progress事件：用于发送任务执行进度
- Completion事件：用于发送任务完成结果

#### Progress事件结构：

```json
{
  "name": "task-progress-event",
  "from": "",
  "payload": {
    "taskId": "task-123",
    "eventId": "progress-1",
    "eventName": "processing",
    "eventData": {
    }
  },
  "context": ""
}
```

#### Progress事件字段说明：

- `name`: 事件类型名称，固定为"task-progress-event"
- `from`: 本机ip:port（可为空字符串）
- `payload`: 数据
- `taskId`: 任务唯一标识符
- `eventId`: 事件Id
- `eventName`: 事件名称
- `eventData`: 进度数据(Map类型)
- `context`: 事件上下文信息（为空字符串即可）

#### Completion事件结构：

```json
{
  "name": "task-completion-event",
  "from": "",
  "payload": {
    "taskId": "task-123",
    "result": {
      "status_code": 200,
      "request_id": "task-123",
      "body": {
      }
    }
  },
  "context": ""
}
```

#### Completion事件字段说明：

- `name`: 事件类型名称，固定为"task-completion-event"
- `from`: 本机ip:port（可为空字符串）
- `payload`: 事件负载数据
- `taskId`: 任务唯一标识符
- `result`: 任务执行结果，包含status_code（状态码）、request_id（请求ID）、body（响应内容）
- `context`: 事件上下文信息（通常为空）

## 二. 其他java栈能力点接入

### 2.1 添加依赖

```xml

<dependency>
    <groupId>top.bella</groupId>
    <artifactId>openapi-spi</artifactId>
    <version>1.1.68</version>
</dependency>
```

### 2.2 配置OpenAiService

```java

@Configuration
public class OpenAiConfig {

    @Value("${bella.openApiBase}")
    private String openApiBase;

    @Value("${bella.openopenApiKey}")
    private String openopenApiKey;

    @Bean
    public OpenAiService openAiService() {
        return new OpenAiService(openopenApiKey, openApiBase);
    }
}
```

### 2.4 实现TaskExecutor

TaskExecutor是Worker处理任务的核心接口，定义了任务提交执行和容量管理的契约。

#### 接口定义

```java
public interface TaskExecutor {
    /**
     * 提交任务处理
     * @param task 任务包装器，包含任务数据和状态管理方法
     */
    void submit(TaskWrapper task);

    /**
     * 返回当前可处理的任务数量
     * Worker可根据该参数动态调整拉取任务的数量
     * @return 剩余容量，返回0时不会拉取新任务
     */
    Integer remainingCapacity();
}
```

#### TaskWrapper方法说明：

- `getPayload()`: 获取上游提交的任务数据（Map格式）
- `markComplete(Object result)`: 标记任务完成，传入处理结果
- `markRetryLater()`: 标记任务稍后重试
- `emitProgress()`: 发送进度事件（streaming模式）

#### 实现示例

```java

@Component
public class TaskExecutorImpl implements TaskExecutor {

    @Override
    public void submit(TaskWrapper task) {
        try {
            // 获取任务数据
            Map<String, Object> data = task.getPayload();

            // 判断任务类型
            String responseMode = (String) data.get("responseMode");

            if("streaming".equals(responseMode)) {
                // Streaming任务处理
                Map<String, Object> result = processTaskWithProgress(data, task);

                // 发送进度事件
                task.emitProgress("onProgress", "onProgress", result);

                // 如果完成，标记任务完成
                task.markComplete(Map.of());
            } else {
                // 其他任务处理
                Object result = processTask(data);

                // 标记任务完成
                task.markComplete(result);
            }
        } catch (Exception e) {
            // 处理失败
            if(shouldRetry(e)) {
                task.markRetryLater();
            } else {
                task.markComplete(buildErrorResponse(e));
            }
        }
    }

    @Override
    public Integer remainingCapacity() {
        return 10;
    }
}
```

### 2.5 配置Worker调度执行器

默认提供基于定时轮训的ScheduledWorker，通过配置文件启用并配置调度参数。

#### 使用默认ScheduledWorker：

```yaml
bella:
  queue:
    worker:
      enabled: true
    scheduled:
      enabled: true
      take-strategy: fifo
      size: 10
      queues:
        - "my-task-queue:0"
        - "my-task-queue:1"
```

#### 配置参数说明：

- `enabled`: 是否启用ScheduledWorker调度器
- `queues`: 监听的队列列表，格式为"队列名:优先级"，数字越小优先级越高，会根据配置顺序拉取任务
- `take-size`: 每次拉取的最大任务数，实际数量取TaskExecutor.remainingCapacity()和task-size之间的小值
- `take-strategy`: 队列拉取策略
    - `fifo`: 先进先出，按照任务入队时间先后拉取，入队越早优先级越高，最多只能拉取10个。
    - `round_robin`: 轮询模式，在多个队列间轮流拉取。
    - `active_passive`: 主备模式，优先拉取主队列，主队列无任务时拉取备队列

#### 自定义Worker调度执行实现

如果默认的ScheduledWorker不满足需求，可以复用已实例化的Worker组件，自定义拉取任务调度执行逻辑。
