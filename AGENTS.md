# AGENTS.md

## 项目概览

本仓库用于开发“电商 AI 图文资产流水线”系统。当前阶段是企业内部使用的模块化单体应用。

- 后端工程：`e-commerce`
- 项目文档：`docs`
- PRD：`docs/prd/电商 AI 图文资产流水线 PRD V1.0 开发排期版.md`
- 架构设计：`docs/architecture/电商 AI 图文资产流水线 架构设计 V1.0.md`
- 实现计划：`docs/plans/后端第一阶段骨架实现计划.md`

后续所有工程项目文档统一维护在根目录 `docs` 下，不再放到 `e-commerce/docs`。

## 当前技术栈

后端：

- Java 17
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- PostgreSQL
- H2 测试数据库
- Gradle Wrapper

前端规划：

- Vue 3
- TypeScript
- Vite
- Naive UI

## 开发约束

### 代码修改节奏

基础 CRUD、配置、实体、仓储、DTO 等骨架代码可以直接实现。

涉及实际业务逻辑时，必须逐个 Java 文件提交给用户 review，用户确认后再继续下一个文件。当前已执行过该节奏的业务区域包括：

- 产品资料解析端口与 Markdown Parser
- Listing 任务提交工作流
- Listing 任务详情查询
- Listing Controller 提交与查询接口

### 文档维护

所有计划、架构、PRD、后续设计文档都维护在根目录 `docs`。

修改实现计划时，同步更新 `docs/plans/后端第一阶段骨架实现计划.md` 中的任务勾选状态。

### 注释要求

Java 主代码需要中文 JavaDoc 或中文注释，至少说明：

- 类职责
- 关键字段含义
- 业务边界
- 端口/适配器设计意图

不要用空泛注释解释显而易见的 getter/setter 行为。

## 后端运行与测试

后端工作目录：

```powershell
cd e-commerce
```

运行测试：

```powershell
.\gradlew.bat test
```

提交前必须运行完整测试，并确认输出为 `BUILD SUCCESSFUL`。

注意：不要并行运行多个 Gradle `test` 命令，因为它们会竞争同一个 `build/test-results` 目录，可能导致 Gradle 结果文件异常。

## Git 规则

提交前检查：

```powershell
git status --short
```

不要提交以下内容：

- `.gradle/`
- `build/`
- `bin/`
- `.vscode/`
- `storage/`
- 本地 `.env`
- 运行时生成文件

Gradle Wrapper 需要提交：

- `e-commerce/gradlew`
- `e-commerce/gradlew.bat`
- `e-commerce/gradle/wrapper/gradle-wrapper.properties`
- `e-commerce/gradle/wrapper/gradle-wrapper.jar`

## 当前后端边界

当前第一阶段已经实现或正在实现的范围：

- 统一响应与异常
- 本地文件存储端口
- 类目模板与 Car Stereo 初始化
- Listing 核心实体和仓储
- 产品资料解析端口
- Markdown 文件读取并委托提取器
- 第一阶段占位 `ProductDocumentExtractor`
- Listing 任务提交
- Listing 任务详情查询
- 对应 service/controller 测试

第一阶段暂不实现：

- 真实 Spring AI Alibaba 文档提取
- Bright Data 竞品采集
- 文案生成
- 图片生成
- 合规检查
- ZIP/Excel/Word 导出
- 前端工程

## 设计约定

产品资料文档格式不统一，不能用固定字符串规则抽取业务字段。`MarkdownDocumentParser` 只负责读取 Markdown 原文，结构化抽取由 `ProductDocumentExtractor` 端口负责。当前 `PlaceholderProductDocumentExtractor` 只是第一阶段占位实现，后续应替换为大模型实现。

多个 `ProductDocumentParser` 实现通过 `ProductDocumentParserFactory` 按扩展名选择，`ListingWorkflowService` 不直接依赖解析器列表。

API 响应 DTO 不直接暴露 JPA 实体。实体用于持久化，DTO 用于稳定对外接口。
