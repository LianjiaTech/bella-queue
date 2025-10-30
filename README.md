<div align="center">

# Bella-Queue

<h3>高性能的AI任务队列处理引擎，支持Batch API和Task API</h3>

[![License](https://img.shields.io/badge/License-MIT-blue?style=flat)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-8+-orange?style=flat)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.12-brightgreen?style=flat)](https://spring.io/projects/spring-boot)

</div>

<div align="center">

**中文** | [English](./README_EN.md) | [文档中心](./api/docs/)

</div>

## 🔥 项目简介

**Bella-Queue** 是专为AI应用场景设计的**高性能任务队列处理引擎**，致力于为开发者提供**更可靠、高效、易用**的AI任务处理能力。

系统采用现代化微服务架构设计，构建了一套功能完备的任务调度系统，支持Batch API和Task
API两种处理模式。不仅提供了强大的任务生命周期管理能力，还支持多种响应模式和智能队列策略，能够灵活应对从小规模实时处理到大规模批量处理的各种业务场景。

无论是需要高并发实时响应的在线服务，还是需要大规模批量处理的离线任务，Bella-Queue都能提供稳定可靠的解决方案，让AI应用开发变得更加简单高效。

## ✨ 功能介绍

### 📋 Batch API

**Batch API专为大规模AI任务批量处理而设计**，适用于需要处理大量数据但对实时性要求不高的场景，如批量内容生成、模型评估、数据分析等。通过将多个请求打包成批次处理，可以显著降低成本并提高处理效率。

- **完全兼容OpenAI协议**: 100%兼容OpenAI Batch API标准，无缝迁移现有应用
- **大规模任务处理**: 单次Batch API支持最多50,000个任务，文件大小最大100MB
- **异步处理**: 后台处理，不阻塞用户操作，支持24小时完成窗口配置
- **进度跟踪**: 实时处理进度更新
- **任务取消**: 支持主动取消未完成的批处理任务，已完成部分结果仍可获取
- **结果下载**: 完成后自动生成输出文件和错误文件，支持通过file-api下载

### ⚡ Task API

**Task API专为单个AI任务实时处理而设计**
，适用于需要快速响应和实时交互的场景，如在线客服、实时对话、即时内容生成等。支持单任务提交和处理，提供多种响应模式，满足不同实时性要求的业务需求。

- **多种响应模式**:
    - `callback`: 异步回调模式，任务完成后HTTP回调通知，适合长时间运行的任务
    - `blocking`: 同步阻塞模式，实时返回结果，默认超时300秒，适合快速响应场景
    - `streaming`: 流式响应模式，支持SSE实时流式响应，适合需要实时反馈的场景

### 🎛️ 队列管理

- **智能队列策略**:
    - `fifo`: 先进先出，按任务入队时间顺序处理，保证处理顺序
    - `round_robin`: 轮询调度，在多个队列间轮流拉取任务，平均分配任务负载
    - `active_passive`: 主备模式，优先拉取主队列任务，主队列无任务时拉取备队列任务

- **队列管理能力**:
    - **优先级支持**: 支持0-N级队列优先级，数值越小优先级越高（0为在线，1+为离线）
    - **动态注册**: 支持动态注册新队列，灵活扩展业务场景

## ✨ 核心优势

| 能力              | 介绍                                                               |
|-----------------|------------------------------------------------------------------|
| **☕ Java友好**    | 后端完全基于Java技术栈构建，方便快速基于Java活跃的生态融合，充分利用已有技术积累                     |
| **🚀 高性能处理**    | 基于Redis + Lua脚本的高效队列实现，支持万级并发                                    |
| **🔧 OpenAI兼容** | 100%兼容OpenAI Batch API标准，无缝迁移现有应用                                |
| **🔄 多种响应模式**   | Task API支持callback、blocking、streaming三种响应模式，满足从快速响应到长时间运行的各种场景需求 |
| **🎯 智能调度**     | 队列支持FIFO、轮询、主备三种调度策略，0-N级优先级队列，智能任务分发和负载均衡                       |

## 📍 快速开始

### 使用方式

| 部署方式            | 说明                                                                                                     |
|-----------------|--------------------------------------------------------------------------------------------------------|
| **🐳 Docker部署** | 推荐使用Docker快速部署，无需复杂配置，一键启动完整服务栈（包含MySQL、Redis、API）。<br/>详细步骤请参考 [部署指南](./api/deploy/README.md)。 |
| **💻 源码部署**     | 在您自己的环境中从源码构建部署，完全控制配置和数据。<br/>适合需要自定义配置或二次开发的场景。                                                      |

### 快速部署

**前置条件**:

- Docker 已安装并运行
- docker-compose 已安装
- 至少 4GB 可用内存
- 必须部署 bella-openapi 项目并确保其正常运行

**一键启动** (推荐):

```bash
# 克隆代码
git clone https://github.com/LianjiaTech/bella-queue.git
cd bella-queue/api/deploy/docker

# 一键启动完整服务栈
./start.sh \
  --bella-openapi-host https://your-bella-openapi-service.com \
  --bella-openapi-key your_bella_openapi_secret_key
```

**说明**:

- `--bella-openapi-host` 和 `--bella-openapi-key` 为必需参数
- 首次启动会自动构建应用镜像
- 启动成功后健康检查: http://localhost:8080/actuator/health

更详细部署指南和参数配置，请参考 [部署指南](./api/deploy/README.md) 。

## ❓ 常见问题解答

### Q1: 如何选择Batch API还是Task API？

**A**: 根据业务场景选择：

- **Batch API**：大量任务、非实时、降低成本 → 批量内容生成、模型评估
- **Task API**：单个任务、实时响应、灵活交互 → 在线客服、实时对话

### Q2: 系统支持的最大并发数？

**A**: 支持万级并发，具体取决于硬件配置和Worker节点数量。

## 👨‍💻 贡献指南

我们热心欢迎社区贡献！贡献者需要同意项目维护者可根据需要调整开源协议，以及贡献代码可能被用于商业目的。

详细的贡献指南请参考 [贡献指南](./CONTRIBUTING.md) 。

## 🔐 商业使用须知

Bella-Queue 采用 MIT 许可协议，支持商业使用：

| 许可与支持       | 说明                                                |
|-------------|---------------------------------------------------|
| **🌟 自由使用** | 采用 MIT 许可协议，允许自由使用、修改和分发，包括商业用途，只要保留原始版权声明和许可证文本。 |
| **🔧 技术支持** | 提供完整的文档和示例代码，支持企业级部署。如需专业技术支持，请联系我们获取商业支持服务。      |

## 📃 许可协议

Bella-Queue 基于 MIT 许可协议开源，允许商业使用。详细条款请参阅 [LICENSE](./LICENSE) 文件。

---

<div align="center">

© 2025 Bella. 保留所有权利。

[项目仓库](https://github.com/LianjiaTech/bella-queue) · [API文档](http://localhost:8080/v1/doc.html) · [文档中心](./api/docs/)

</div>

