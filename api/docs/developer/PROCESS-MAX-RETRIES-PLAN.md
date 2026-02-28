# 任务最大重试次数功能实现计划

## 一、需求背景

### 1.1 当前系统现状

当前系统已实现了基于 `processTimeout` 的超时机制：
- Worker 通过 `take` 接口拉取任务时可指定 `processTimeout`
- 超时后任务自动标记为完成（status_code: 408）
- 使用 Redis 键过期 + Keyspace Notifications 实现

**现有问题**：
- 任务超时后直接标记为完成，无法重试
- 对于临时性失败（网络抖动、服务暂时不可用），无法自动恢复
- 用户需要手动重新提交失败的任务

### 1.2 新需求

在 `take` 接口中增加 `processMaxRetries` 参数，实现以下功能：
- 指定任务的最大重试次数（例如：3次）
- 任务超时后不直接完成，而是重新放回队列
- 达到最大重试次数后才标记为最终失败
- 每次重试都有独立的超时时间
- 
---

## 二、设计方案

### 2.1 核心思路

**基于现有超时机制扩展**：
- 复用现有的 Redis 键过期 + Keyspace Notifications 机制
- 在 Redis 中记录任务的当前重试次数和最大重试次数
- 超时时的处理逻辑：
  - 检查任务是否已过期（expireTime），已过期则直接失败，不重试
  - 检查重试次数是否达到上限，未达到则重新入队，达到上限则标记失败

### 2.2 处理流程

1. **Take 阶段**：初始化重试追踪（retry=0, max_retry=N, process_timeout=T）
2. **超时触发**：监听 timeout 键过期事件
3. **过期检查**：检查任务是否已过期（expireTime），已过期则直接标记失败，不重试
4. **判断重试**：检查当前重试次数是否达到上限（-1 表示无限重试）
5. **执行动作**：未达上限则重新入队并增加重试计数，达到上限则标记失败并清理追踪键

---

## 三、详细设计

### 3.1 数据模型

#### Take 类新增字段

**文件**: `openai-api/src/main/java/com/theokanning/openai/queue/Take.java`

```java
private Integer processMaxRetries;  // null/0: 不重试, -1: 无限重试, >0: 具体次数
```

#### Redis 数据结构

**新增键**：
```
retry:{taskId}            # 当前重试次数（从1开始）
max_retry:{taskId}        # 最大重试次数（-1表示无限）
process_timeout:{taskId}  # 原始超时值（用于重新设置）
```

**现有键**：
```
timeout:{taskId}          # 超时控制键（已存在）
```

### 3.2 核心实现

**涉及文件**：`QueueService.java`

**主要改动**：
1. **Take 流程**：增加参数校验和重试追踪初始化
2. **超时处理**：改造监听器回调，增加重试逻辑判断
3. **任务重入队**：查询任务，检查过期，更新 Redis 队列和重试计数
4. **Complete 流程**：清理重试追踪键

**关键约束**：
- 在线队列（level = 0）不支持重试
- 任务已过期（expireTime）则不重试，直接标记失败
- 数据库状态保持不变，仅操作 Redis

---

## 四、性能影响评估

### Redis 操作增量

**每次 take（启用重试）**：
- 现有：N 个任务 × 1 次 SET（timeout键）
- 新增：N 个任务 × 3 次 SET（retry键 + max_retry键 + process_timeout键）
- **影响**：Redis 操作量增加 3倍，但使用 Pipeline 批量操作，延迟可控

### 超时处理延迟

- 现有流程：超时 → 直接 complete（~10ms）
- 新流程：超时 → 读取配置 → requeue（~50ms）
- **影响**：可接受

### 队列长度

- 重试任务会放回队列头部，不影响正常任务处理
- 建议设置合理的 `processMaxRetries`（≤10）或使用任务过期时间控制
