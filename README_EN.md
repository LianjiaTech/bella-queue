<div align="center">

# Bella-Queue

<h3>High-Performance AI Task Queue Processing Engine, Supporting Batch API and Task API</h3>

[![License](https://img.shields.io/badge/License-MIT-blue?style=flat)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-8+-orange?style=flat)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.12-brightgreen?style=flat)](https://spring.io/projects/spring-boot)

</div>

<div align="center">

[中文](./README.md) | **English** | [Documentation](./api/docs/)

</div>

## 🔥 Project Overview

**Bella-Queue** is a **high-performance task queue processing engine** specifically designed for AI application
scenarios, committed to providing developers with **more reliable, efficient, and user-friendly** AI task processing
capabilities.

The system adopts modern microservice architecture design, building a comprehensive task scheduling system that supports
both Batch API and Task API processing modes. It not only provides powerful task lifecycle management capabilities but
also supports multiple response modes and intelligent queue strategies, flexibly addressing various business scenarios
from small-scale real-time processing to large-scale batch processing.

Whether for online services requiring high-concurrency real-time responses or offline tasks needing large-scale batch
processing, Bella-Queue provides stable and reliable solutions, making AI application development simpler and more
efficient.

## ✨ Features

### 📋 Batch API

**Batch API is designed for large-scale AI task batch processing**, suitable for scenarios that need to process large
amounts of data but don't require high real-time performance, such as bulk content generation, model evaluation, data
analysis, etc. By packaging multiple requests into batch processing, it can significantly reduce costs and improve
processing efficiency.

- **Full OpenAI Protocol Compatibility**: 100% compatible with OpenAI Batch API standards, seamless migration of
  existing applications
- **Large-Scale Task Processing**: Single Batch API supports up to 50,000 tasks, maximum file size 100MB
- **Asynchronous Processing**: Background processing without blocking user operations, supports 24-hour completion
  window configuration
- **Progress Tracking**: Real-time processing progress updates
- **Task Cancellation**: Supports active cancellation of incomplete batch processing tasks, completed partial results
  remain accessible
- **Result Download**: Automatically generates output files and error files upon completion, supports download through
  file-api

### ⚡ Task API

**Task API is designed for single AI task real-time processing**, suitable for scenarios requiring quick response and
real-time interaction, such as online customer service, real-time dialogue, instant content generation, etc. Supports
single task submission and processing, provides multiple response modes, meeting different real-time business
requirements.

- **Multiple Response Modes**:
    - `callback`: Asynchronous callback mode, HTTP callback notification after task completion, suitable for
      long-running tasks
    - `blocking`: Synchronous blocking mode, real-time result return, default timeout 300 seconds, suitable for quick
      response scenarios
    - `streaming`: Streaming response mode, supports SSE real-time streaming response, suitable for scenarios requiring
      real-time feedback

### 🎛️ Queue Management

- **Intelligent Queue Strategies**:
    - `fifo`: First-in-first-out, processes tasks in order of queue entry time, guarantees processing order
    - `round_robin`: Round-robin scheduling, alternately pulls tasks from multiple queues, evenly distributes task load
    - `active_passive`: Active-passive mode, prioritizes pulling tasks from primary queue, pulls from backup queue when
      primary queue is empty
    - `sequential`: Global sequential mode, ensures only one task runs at a time per queue, guaranteeing global
      execution order

- **Queue Management Capabilities**:
    - **Priority Support**: Supports 0-N level queue priorities, smaller values have higher priority (0 for online, 1+
      for offline)
    - **Dynamic Registration**: Supports dynamic registration of new queues, flexible expansion of business scenarios

## ✨ Core Advantages

| Capability                         | Description                                                                                                                                                          |
|------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **☕ Java-Friendly**                | Backend built entirely on Java technology stack, facilitating rapid integration with Java's active ecosystem, fully utilizing existing technical expertise           |
| **🚀 High-Performance Processing** | Efficient queue implementation based on Redis + Lua scripts, supporting ten-thousand-level concurrency                                                               |
| **🔧 OpenAI Compatible**           | 100% compatible with OpenAI Batch API standards, seamless migration of existing applications                                                                         |
| **🔄 Multiple Response Modes**     | Task API supports callback, blocking, streaming three response modes, meeting various scenario requirements from quick response to long-running tasks                |
| **🎯 Intelligent Scheduling**      | Queue supports FIFO, round-robin, active-passive, sequential four scheduling strategies, 0-N level priority queues, intelligent task distribution and load balancing |

## 📍 Quick Start

### Usage Methods

| Deployment Method             | Description                                                                                                                                                                                                                                       |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **🐳 Docker Deployment**      | Recommended for quick deployment using Docker, no complex configuration needed, one-click startup of complete service stack (including MySQL, Redis, API).<br/>For detailed steps, please refer to [Deployment Guide](./api/deploy/README_EN.md). |
| **💻 Source Code Deployment** | Build and deploy from source code in your own environment, full control over configuration and data.<br/>Suitable for scenarios requiring custom configuration or secondary development.                                                          |

### Quick Deployment

**Prerequisites**:

- Docker installed and running
- docker-compose installed
- At least 4GB available memory
- bella-openapi project must be deployed and running properly

**One-Click Startup** (Recommended):

```bash
# Clone the code
git clone https://github.com/LianjiaTech/bella-queue.git
cd bella-queue/api/deploy/docker

# One-click startup of complete service stack
./start.sh \
  --bella-openapi-host https://your-bella-openapi-service.com \
  --bella-openapi-key your_bella_openapi_secret_key
```

**Notes**:

- `--bella-openapi-host` and `--bella-openapi-key` are required parameters
- First startup will automatically build application image
- After successful startup, health check: http://localhost:8080/actuator/health

For more detailed deployment guide and parameter configuration, please refer
to [Deployment Guide](./api/deploy/README_EN.md).

## ❓ Frequently Asked Questions

### Q1: How to choose between Batch API and Task API?

**A**: Choose based on business scenarios:

- **Batch API**: Large volume tasks, non-real-time, cost reduction → Bulk content generation, model evaluation
- **Task API**: Single tasks, real-time response, flexible interaction → Online customer service, real-time dialogue

### Q2: What is the maximum concurrency supported by the system?

**A**: Supports ten-thousand-level concurrency, specific performance depends on hardware configuration and number of
Worker nodes.

## 👨‍💻 Contributing Guide

We warmly welcome community contributions! Contributors need to agree that project maintainers may adjust the open
source license as needed, and that contributed code may be used for commercial purposes.

For detailed contribution guidelines, please refer to [Contributing Guide](./CONTRIBUTING.md).

## 🔐 Commercial Use Notice

Bella-Queue adopts MIT license agreement, supporting commercial use:

| License & Support        | Description                                                                                                                                                                      |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **🌟 Free Usage**        | Adopts MIT license agreement, allows free use, modification and distribution, including commercial purposes, as long as original copyright notice and license text are retained. |
| **🔧 Technical Support** | Provides complete documentation and sample code, supports enterprise-level deployment. For professional technical support, please contact us for commercial support services.    |

## 📃 License Agreement

Bella-Queue is open-sourced under MIT license agreement, allowing commercial use. For detailed terms, please refer
to [LICENSE](./LICENSE) file.

---

<div align="center">

© 2025 Bella. All rights reserved.

[Project Repository](https://github.com/LianjiaTech/bella-queue) · [API Documentation](http://localhost:8080/v1/doc.html) · [Documentation Center](./api/docs/)

</div>
