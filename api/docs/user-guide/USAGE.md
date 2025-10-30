# Bella-Queue使用文档

该文档旨在为希望应用AI批处理能力的用户提供接入指引。

## Batch Api接入

Batch API 是一项专为处理大规模、非即时性任务而设计的异步处理服务。它允许开发者将大量 API
请求整合到一个文件中，并提交给模型进行后台处理。该服务通过提供显著的成本节约、更高的速率限制以及明确的处理时间框架，为需要处理海量数据的应用场景提供了高效且经济的解决方案。

### 核心功能与优势

#### 异步批处理

支持异步理批量请求，更好利用后端模型能力。

#### 成本效益

显著降低大规模批处理运营成本。

### 适用场景

#### 内容生成

批量生成产品描述、文章摘要、营销文案，或对现有内容进行翻译、润色和扩展。

#### 模型评估与测试

使用大量测试用例来评估模型的性能、鲁棒性或偏见，进行大规模模型评估。

#### 文本嵌入与向量数据库构建

高效处理大规模嵌入请求，将文档转化为向量表示。

### 支持的能力

列表里为部分支持能力，其他正在陆续接入

| 端点 (Endpoint)        | 主要功能                      | 典型应用场景                     |
|----------------------|---------------------------|----------------------------|
| /v1/chat/completions | 与语言模型系列模型交互，进行文本生成、问答、对话等 | 批量生成产品描述、文章摘要、营销文案、代码注释等   |
| /v1/embeddings       | 将文本转换为数值向量（嵌入），用于衡量语义相似性  | 构建向量数据库、实现语义搜索、推荐系统、文本聚类分析 |
| /v1/workflow         | ai能力流程化编排                 | 工作流                        |

### Batch任务生命周期

| 状态          | 描述                         |
|-------------|----------------------------|
| validating  | 批处理作业已创建，系统正在验证输入文件的格式和内容。 |
| in_progress | 输入文件验证通过，系统正在处理批处理中的请求。    |
| finalizing  | 所有请求已处理完毕，系统正在整理和准备输出文件。   |
| completed   | 批处理作业已成功完成，所有结果已准备就绪，可以下载。 |
| failed      | 批处理作业在处理过程中遇到错误，未能成功完成。    |
| expired     | 批处理作业未能在时间窗口的时限内完成，已超时。    |

#### 作业过期 (expired)

作业过期是批处理 API 的一个内置保护机制，旨在防止作业无限期地运行。每个批处理作业都有一个指定时间的完成窗口

重要提示： 当作业过期时，所有尚未完成的请求将被取消，但已经完成的请求的结果仍然会被保存。开发者只需要为那些已经成功完成的请求支付费用。

#### 作业失败 (failed)

作业失败通常表示在处理过程中遇到了一个无法恢复的错误，导致作业无法继续进行。常见原因包括：

- 输入文件格式错误
- 请求参数无效
- 权限问题
- 系统内部错误

### Batch创建与管理

前置：上传符合格式条件的文件到file-api, purpose为"batch"。

#### 创建任务

```bash
curl -X POST "https://${openApiBase}/v1/batches" \
-H "Content-Type: application/json" \
-H "Authorization: Bearer ${openApiKey}" \
-H "X-BELLA-QUEUE-NAME: ${queueName}" \
-d '{
"input_file_id": "file-xxx-xxx",
"endpoint": "/v1/chat/completions",
"completion_window": "24h",
"metadata": {
}
}'
```

#### 请求头说明:

- `X-BELLA-QUEUE-NAME`: 指定任务提交到的队列名称（可选，如果不为空则直接投递到该队列）

#### 参数说明:

- `input_file_id`: 输入文件ID，文件格式为JSONL。文件id为上传到file-api的文件id
- `endpoint`: 处理能力点
- `completion_window`: 完成时间窗口 (支持m, h, d 默认24h)
- `metadata`: 自定义元数据

#### 查询任务

```bash
curl -X GET "https://${openApiBase}/v1/batches/${batch_id}" \
-H "Authorization: Bearer ${openApiKey}"
```

#### 响应示例:

```json
{
  "id": "BATCH-1-1-250902181937-0041-000001",
  "object": "batch",
  "endpoint": "/v1/chat/completions",
  "input_file_id": "file-xxx-xxx",
  "completion_window": "24h",
  "status": "completed",
  "output_file_id": "file-xxx-xxx",
  //完成详情文件id
  "error_file_id": "",
  //失败详情文件id
  "created_at": 1756837177,
  "in_progress_at": 1756837177,
  "finalizing_at": 946684800,
  "completed_at": 1756838726,
  "failed_at": 946684800,
  "expired_at": 946684800,
  "cancelling_at": 946684800,
  "cancelled_at": 946684800,
  "request_counts": {
    "total": 40000,
    // 总请求数量
    "completed": 40000,
    // 已完成请求数量
    "failed": 0
    //解析失败数量
  },
  "metadata": {
  }
}
```

#### 状态说明：

- `validating`: 验证输入文件
- `in_progress`: 处理中
- `completed`: 处理完成
- `failed`: 处理失败
- `expired`: 已过期
- `cancelled`: 已取消

#### 列表查询

```bash
curl -X GET "https://${openApiBase}/v1/batches?limit=${limit}&after=${batch_id}" \
-H "Authorization: Bearer ${openApiKey}"
```

#### 取消任务

```bash
curl -X POST "https://${openApiBase}/v1/batches/${batch_id}/cancel" \
-H "Authorization: Bearer ${openApiKey}"
```

### 输入/输出文件格式与要求

#### 输入文件

文件格式：jsonl

输入文件是 Batch API 工作的基础，它包含了所有需要处理的 API 请求。为了确保批处理任务能够成功创建和执行，输入文件必须严格遵守
规定的格式和要求。Batch API 要求输入文件必须采用 JSONL (JSON Lines) 格式，其中每一行都是一个独立的、有效的 JSON 对象。

```jsonl
{"custom_id": "request-1", "method": "POST", "url": "/v1/chat/completions", "body": {"model": "gpt-4", "messages": [{"role": "user", "content": "What is the capital of France?"}]}}
{"custom_id": "request-2", "method": "POST", "url": "/v1/chat/completions", "body": {"model": "gpt-4", "messages": [{"role": "user", "content": "Translate 'Hello' to Spanish."}]}}
```

custom_id: 业务id, 用于关联输出结果

重要限制：一个批处理任务中的所有请求都必须指向同一个端点和同一个模型，不能混合使用不同模型；单个批处理任务数量小于50000个、文件大小小于100M。

#### 输出文件

文件格式：jsonl

```json
{"id": "batch_req_123", "custom_id": "request-2", "response": {"status_code": 200, "request_id": "req_123", "body": {"id": "chatcmpl-123", "object": "chat.completion", "created": 1711652795, "model": "gpt-3.5-turbo-0125", "choices": [{"index": 0, "message": {"role": "assistant", "content": "Hello."}, "logprobs": null, "finish_reason": "stop"}], "usage": {"prompt_tokens": 22, "completion_tokens": 2, "total_tokens": 24}, "system_fingerprint": "fp_123"}}, "error": null}
```

请注意，输出行的顺序可能与输入行的顺序不一致。不要依赖顺序来处理结果，而应使用输出文件中每一行都包含的 custom_id
字段，以便将输入中的请求与输出中的结果进行映射。

#### 错误文件

文件格式：jsonl

```json
{"id": "batch_req_125", "custom_id": "request-3", "response": null, "error": {"code": "invalid_request_error", "message": "The model `gpt-3.5-turbo-9999` does not exist"}}
```

### 文件下载与处理

通过查询任务接口输出的output_file_id，error_file_id，分别到file-api上下载输出文件、错误文件。

## Task Api接入

Bella-Queue还提供基于队列的单任务提交，交由后端大模型能力处理的功能。

### 2.3.1 任务提交

```bash
curl -X POST "https://${openApiBase}/v1/queue/put" \
-H "Content-Type: application/json" \
-H "Authorization: Bearer ${openApiKey}" \
-d '{
"endpoint": "/v1/chat/completions",
"queue": "my-task-queue",
"level": 0,
"data": {
},
"response_mode": "callback",
"callback_url": "https://your-app.com/callback",
"timeout": 300
}'
```

#### 参数说明:

- `endpoint`: 能力点
- `queue`: 队列名称（可选）
- `level`: 队列优先级(可选)，数值越大优先级越低(默认0, 0: 在线；1及以上: 离线, 目前仅支持0，1)
- `data`: 任务数据
- `response_mode`: 响应模式，单任务支持三种模式
    - `callback`: 回调模式（默认），异步处理完成后回调通知
    - `blocking`: 阻塞模式，同步等待结果返回
    - `streaming`: 流式模式，支持SSE实时流式响应
- `callback_url`: 回调地址（callback模式必需）
- `timeout`: 超时时间（秒）

#### 接口响应:

单任务支持三种响应模式，满足不同场景的需求：

##### 1. callback模式（回调模式）:

```json
{
  "code": 200,
  "data": "task-id-123"
}
```

##### 1. blocking模式（同步阻塞模式）:

同步返回下游能力点执行的结果，默认超时时间300秒

##### 2. streaming模式（流式模式）:

返回sse连接对象，默认超时时间300秒

### 2.3.2 任务取消

仅callback模式的任务支持取消操作，不保证取消成功。

```bash
curl -X POST "https://${openApiBase}/v1/queue/{taskId}/cancel" \
-H "Authorization: Bearer ${openApiKey}"
```
