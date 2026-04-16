# Plan: #58 添加 Hello World 示例

## 目标

为 bella-queue Task API 添加一个最简 Hello World 示例，帮助新开发者在 5 分钟内完成第一次任务提交与结果接收，降低接入门槛。

## 非目标

- 不修改任何生产代码或接口逻辑
- 不覆盖 Batch API 的示例（已有 USAGE.md 中的 Batch 部分）
- 不引入新的依赖或外部工具

## 验收标准

- `api/docs/user-guide/USAGE.md` 中新增"Hello World 快速上手"章节
- 包含完整可运行的 curl 示例，覆盖 blocking 模式（同步返回，最适合 Hello World 场景）
- 文档说明所需环境变量（`openApiBase`、`openApiKey`）
- 示例清晰展示任务数据与预期响应格式

## 约束

- 仅修改文档，不触碰任何 Java 代码
- 示例使用 blocking 模式（`response_mode: blocking`），无需额外部署 callback server，适合快速体验

## 变更范围

| 文件 | 操作 | 说明 |
|------|------|------|
| `api/docs/user-guide/USAGE.md` | 修改 | 在 Task API 接入章节前新增 Hello World 快速上手段落 |

## 实现思路

**步骤 1：在 `USAGE.md` 中 Task API 章节前插入新章节**

新章节标题：`## Hello World 快速上手`

内容结构：
1. **前置条件**：说明需要 `openApiBase` 和 `openApiKey`
2. **提交任务**：一条 blocking 模式的 curl，`data` 中携带一条 chat completions 请求，内容为 "Say hello world"
3. **预期响应**：展示同步返回的 JSON 结构示例
4. **说明**：一句话解释 blocking 模式适合快速验证，生产中可改用 callback 或 streaming

## 风险与依赖

- 无代码变更，风险极低
- 示例中的模型名需使用占位符（如 `${modelName}`），避免与实际部署环境绑定