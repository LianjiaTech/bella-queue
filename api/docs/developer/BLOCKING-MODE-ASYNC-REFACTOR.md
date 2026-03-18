# Blocking 模式异步改造方案对比

## 一、背景

Bella Queue 的 Task API 支持三种响应模式：`callback`、`blocking`、`streaming`。其中 `blocking` 模式用于同步等待大模型任务结果，客户端发出请求后挂起，直到 Worker 处理完成或超时（最长 300s）。

由于大模型接口响应慢（通常 10s ~ 60s），`blocking` 模式的并发吞吐受到严重限制，需要改造。

---

## 二、原方案：线程轮询（Busy-Polling）

### 实现原理

```
Client ──PUT /v1/queue/put──► Tomcat 线程 ──► BlockingCallback.getResult()
                                                     │
                                              while (!isTimeout())
                                                Thread.sleep(100ms)  ← 持续占用线程
                                                     │
                                              onCompletionEvent() 写入 data map
                                                     │
                                              return data ──► ResponseEntity ──► Client
```

### 问题分析

| 问题 | 说明 |
|------|------|
| 线程一直占用 | 每个 blocking 请求占用 1 个 Tomcat 线程，等待期间线程无法处理其他请求 |
| CPU 空转 | `Thread.sleep(100)` 轮询，唤醒后检查结果，无实际工作但消耗调度资源 |
| 并发上限低 | 并发数 ≈ `server.tomcat.threads.max`（当前配置 500） |
| 响应延迟增加 | 结果就绪后最多还需等待 100ms 才能被检测到 |

**并发上限估算：**
- Tomcat 线程数：500
- 大模型平均响应时间：30s
- 理论最大并发：500 个请求同时处于等待状态，新请求需排队

---

## 三、新方案：DeferredResult 异步 Servlet

### 实现原理

基于 Servlet 3.1 异步特性，Tomcat 请求线程在接到请求后立即释放，结果就绪时由事件回调线程写回响应。

```
Client ──PUT /v1/queue/put──► Tomcat 线程
                                    │
                               创建 DeferredResult
                               注册 callback
                               return deferredResult  ← Tomcat 线程立即释放
                                    │
                         （Tomcat 线程去处理其他请求）
                                    │
                    Worker complete ──► RedisMesh 事件
                                    │
                    onCompletionEvent() ← 由事件线程调用
                    deferredResult.setResult(ResponseEntity)
                                    │
                    Spring 异步框架写回响应 ──► Client
```

### 优势

| 指标 | 原方案 | 新方案 |
|------|--------|--------|
| Tomcat 线程占用时长 | 全程（最长 300s） | 毫秒级（接完请求即释放） |
| 并发上限 | ~500（线程数） | 数万（受内存/连接池限制） |
| CPU 占用 | 轮询空转 | 零空转，事件驱动 |
| 结果响应延迟 | 最多 +100ms | 无额外延迟 |
| 超时精度 | 依赖轮询间隔 | Spring 定时器精确触发 |

---

## 七、压测对比结果

**测试场景：**
- 1000 个线程并发发起共 10,000 个 blocking put 请求（mock 延迟 60s）
- blocking 全部挂起后，同时发起 1,000 个 callback put 请求
- callback 响应超过 60s 或返回 504 均计为失败
- 测试日期：2026-03-18

### 新方案（DeferredResult）

| 指标 | callback（blocking 挂起期间） |
|------|-------------------------|
| 总耗时 | 763 ms                  |
| 成功数 | 1000 / 1000             |
| 504（含超时） | 0                       |
| 平均延迟 | 512 ms                  |
| P50 | 619 ms                  |
| P99 | 656 ms                  |

### 老方案（线程轮询）

| 指标 | blocking |
|------|----------|
| 总耗时 | 30341 ms |
| 成功数 | 0 / 1000 |
| 504（含超时） | 1000     |
| 平均延迟 | 60447 ms |
| P50 | 60422 ms |
| P99 | 60598 ms |

### 汇总对比

| 对比项 | 老方案（线程轮询） | 新方案（DeferredResult） |
|--------|-------------------|--------------------------|
| callback 成功率 | 0 / 1000（**0%**） | 1000 / 1000（**100%**） |
| callback 平均延迟 | 60,447 ms（全部超时） | **512 ms** |
| callback P99 | 60,598 ms | **656 ms** |
| callback 504 数 | 1000 | 0 |

### 结论

**老方案（线程轮询）的问题得到验证：**

1000 个 blocking 请求并发时，Tomcat 线程池被全部占满，后续进入的 1000 个 callback 请求无法获取线程处理，全部排队超时（平均等待 60s），**成功率 0%**，系统实际上已不可用。

**新方案（DeferredResult）效果显著：**

同等压力下，blocking 请求在 Tomcat 接收后立即释放线程，1000 个 callback 请求全部在 **763ms 内完成**，平均延迟仅 **512ms**，P99 为 **656ms**，成功率 **100%**。blocking 的挂起对 callback 吞吐**零影响**。

| 核心指标提升 | 老方案 → 新方案 |
|-------------|----------------|
| callback 成功率 | 0% → 100% |
| callback 平均延迟 | 60,447ms → 512ms（**提升 118 倍**） |
| 系统可用性 | 高并发 blocking 下完全不可用 → 正常服务 |
