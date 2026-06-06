# Brief 人工审核闭环实现计划

> **面向 AI 代理的工作者：** 建议使用 subagent-driven-development 或 executing-plans 按任务执行此计划。步骤使用复选框语法跟踪进度。涉及业务规则的 Java 文件必须按本计划中的 review 关卡逐个交给用户确认。

**目标：** 实现 Brief 历史查询、最新版本查询、基于最新版本创建人工修改版本和批准最新版本的后端闭环，批准后将任务推进到 `GENERATING`。

**架构：** 新增独立 `BriefReviewService` 承担 Brief 审核规则，现有 `ListingWorkflowService` 保持任务提交与详情查询职责。Brief 修改采用不可变版本链，审批在同一事务内更新 Brief 审计字段和任务状态。

**技术栈：** Java 17、Spring Boot 4、Spring Web MVC、Spring Data JPA、Jackson、H2、JUnit 5、AssertJ、MockMvc。

**设计依据：** `docs/plans/Brief 人工审核闭环设计.md`

---

## 1. 文件结构

### 1.1 新建文件

```text
e-commerce/src/main/java/com/snails/ecommerce/listing/api/ApproveBriefRequest.java
e-commerce/src/main/java/com/snails/ecommerce/listing/api/BriefVersionResponse.java
e-commerce/src/main/java/com/snails/ecommerce/listing/api/CreateBriefVersionRequest.java
e-commerce/src/main/java/com/snails/ecommerce/listing/application/BriefReviewService.java

e-commerce/src/test/java/com/snails/ecommerce/listing/application/BriefReviewServiceTest.java
```

### 1.2 修改文件

```text
e-commerce/build.gradle
e-commerce/src/main/java/com/snails/ecommerce/listing/domain/ListingBriefVersion.java
e-commerce/src/main/java/com/snails/ecommerce/listing/infrastructure/ListingBriefVersionRepository.java
e-commerce/src/main/java/com/snails/ecommerce/listing/application/ListingWorkflowService.java
e-commerce/src/main/java/com/snails/ecommerce/listing/api/ListingTaskController.java

e-commerce/src/test/java/com/snails/ecommerce/listing/infrastructure/ListingEntityMappingTest.java
e-commerce/src/test/java/com/snails/ecommerce/listing/application/ListingWorkflowServiceTest.java
e-commerce/src/test/java/com/snails/ecommerce/listing/api/ListingTaskControllerTest.java

docs/plans/Brief 人工审核闭环实现计划.md
```

### 1.3 文件职责

- `ListingBriefVersion`：持久化版本创建人、审批人和审批时间。
- `ListingBriefVersionRepository`：提供稳定排序的最新版本与历史版本查询。
- `CreateBriefVersionRequest`：接收人工修改内容和基础版本 ID。
- `ApproveBriefRequest`：接收审批人。
- `BriefVersionResponse`：稳定输出完整 Brief 审核字段，不暴露 JPA 实体。
- `BriefReviewService`：集中执行版本创建、最新版本校验、审批和状态流转。
- `ListingTaskController`：暴露四个 Brief HTTP 接口，不承载业务判断。

---

### 任务 1：扩展 Brief 持久化模型和稳定排序查询

**文件：**

- 修改：`e-commerce/src/main/java/com/snails/ecommerce/listing/domain/ListingBriefVersion.java`
- 修改：`e-commerce/src/main/java/com/snails/ecommerce/listing/infrastructure/ListingBriefVersionRepository.java`
- 修改：`e-commerce/src/test/java/com/snails/ecommerce/listing/infrastructure/ListingEntityMappingTest.java`

- [ ] **步骤 1：编写审计字段持久化失败测试**

在 `ListingEntityMappingTest` 的 Brief 数据中补充：

```java
brief.setCreatedBy("operator@example.com");
brief.setApproved(true);
brief.setApprovedBy("reviewer@example.com");
brief.setApprovedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
```

保存并重新读取后断言：

```java
assertThat(savedBrief.getCreatedBy()).isEqualTo("operator@example.com");
assertThat(savedBrief.getApprovedBy()).isEqualTo("reviewer@example.com");
assertThat(savedBrief.getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 6, 6, 10, 0));
```

- [ ] **步骤 2：运行映射测试并确认编译失败**

运行：

```powershell
cd e-commerce
.\gradlew.bat test --tests "*ListingEntityMappingTest"
```

预期：FAIL，`ListingBriefVersion` 尚无审计字段访问方法。

- [ ] **步骤 3：添加 Brief 审计字段**

在 `ListingBriefVersion` 新增：

```java
/** Brief 版本创建人；系统生成版本固定为 SYSTEM。 */
@Column(nullable = false, length = 128)
private String createdBy;

/** 批准该 Brief 版本的操作人，未批准时为空。 */
@Column(length = 128)
private String approvedBy;

/** Brief 批准时间，未批准时为空。 */
private LocalDateTime approvedAt;
```

- [ ] **步骤 4：增加稳定排序仓储方法**

将旧方法替换为：

```java
Optional<ListingBriefVersion> findTopByTaskIdOrderByCreatedAtDescBriefVersionIdDesc(String taskId);

List<ListingBriefVersion> findByTaskIdOrderByCreatedAtDescBriefVersionIdDesc(String taskId);
```

同步修改当前调用旧方法的测试，使工程恢复编译。

- [ ] **步骤 5：运行映射测试**

运行：

```powershell
.\gradlew.bat test --tests "*ListingEntityMappingTest"
```

预期：PASS。

- [ ] **步骤 6：提交基础持久化变更**

```powershell
git add e-commerce/src/main/java/com/snails/ecommerce/listing/domain/ListingBriefVersion.java
git add e-commerce/src/main/java/com/snails/ecommerce/listing/infrastructure/ListingBriefVersionRepository.java
git add e-commerce/src/test/java/com/snails/ecommerce/listing/infrastructure/ListingEntityMappingTest.java
git commit -m "feat: 扩展 Brief 审核审计字段"
```

---

### 任务 2：建立 Brief API DTO 契约

**文件：**

- 修改：`e-commerce/build.gradle`
- 创建：`e-commerce/src/main/java/com/snails/ecommerce/listing/api/CreateBriefVersionRequest.java`
- 创建：`e-commerce/src/main/java/com/snails/ecommerce/listing/api/ApproveBriefRequest.java`
- 创建：`e-commerce/src/main/java/com/snails/ecommerce/listing/api/BriefVersionResponse.java`

- [ ] **步骤 1：增加 Bean Validation 依赖**

在 `dependencies` 中加入：

```gradle
implementation 'org.springframework.boot:spring-boot-starter-validation'
```

- [ ] **步骤 2：定义创建版本请求**

创建 record：

```java
public record CreateBriefVersionRequest(
        @NotBlank String baseBriefVersionId,
        @NotBlank String createdBy,
        @NotBlank String targetAudience,
        @NotNull List<@NotBlank String> coreSellingPoints,
        @NotNull List<@NotBlank String> targetKeywords,
        @NotNull List<@NotBlank String> forbiddenClaims,
        @NotNull List<@NotBlank String> imageDirectionPrompts,
        @NotNull List<@NotBlank String> complianceNotes
) {
}
```

空列表允许存在，但列表本身不能为 `null`，列表元素不能是空白字符串。

- [ ] **步骤 3：定义批准请求**

创建 record：

```java
public record ApproveBriefRequest(@NotBlank String approvedBy) {
}
```

- [ ] **步骤 4：定义完整 Brief 响应**

创建 record，字段固定为：

```java
public record BriefVersionResponse(
        String briefVersionId,
        String taskId,
        String parentBriefVersionId,
        String targetAudience,
        List<String> coreSellingPoints,
        List<String> targetKeywords,
        List<String> forbiddenClaims,
        List<String> imageDirectionPrompts,
        List<String> complianceNotes,
        boolean approved,
        String createdBy,
        String approvedBy,
        LocalDateTime approvedAt,
        LocalDateTime createdAt
) {
}
```

DTO 只定义 HTTP 契约，不在其中访问 Repository 或修改实体。

- [ ] **步骤 5：编译 DTO**

运行：

```powershell
.\gradlew.bat compileJava
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 6：提交 DTO 契约**

```powershell
git add e-commerce/build.gradle
git add e-commerce/src/main/java/com/snails/ecommerce/listing/api/ApproveBriefRequest.java
git add e-commerce/src/main/java/com/snails/ecommerce/listing/api/BriefVersionResponse.java
git add e-commerce/src/main/java/com/snails/ecommerce/listing/api/CreateBriefVersionRequest.java
git commit -m "feat: 定义 Brief 审核接口契约"
```

---

### 任务 3：实现 Brief 查询和版本创建服务

**文件：**

- 创建：`e-commerce/src/test/java/com/snails/ecommerce/listing/application/BriefReviewServiceTest.java`
- 创建：`e-commerce/src/main/java/com/snails/ecommerce/listing/application/BriefReviewService.java`

- [ ] **步骤 1：建立服务测试数据夹具**

使用 `@SpringBootTest` 注入真实 H2 Repository，并在 `@BeforeEach` 清理 Brief 和任务数据。测试内直接构造：

```java
ListingTask task = waitingTask("task_review");
ListingBriefVersion original = brief("brief_001", task.getTaskId(), null, "SYSTEM");
```

测试类自行实例化服务：

```java
service = new BriefReviewService(
        listingTaskRepository,
        listingBriefVersionRepository,
        new IdGenerator(),
        objectMapper);
```

- [ ] **步骤 2：编写查询与创建版本失败测试**

至少加入：

```java
@Test
void listsBriefVersionsNewestFirst()

@Test
void getsLatestBrief()

@Test
void createsVersionFromLatestBrief()

@Test
void rejectsCreatingVersionFromHistoricalBrief()

@Test
void rejectsCreatingVersionWhenTaskIsNotWaitingForApproval()
```

成功创建时断言：

```java
assertThat(created.parentBriefVersionId()).isEqualTo("brief_001");
assertThat(created.createdBy()).isEqualTo("operator@example.com");
assertThat(created.approved()).isFalse();
assertThat(listingBriefVersionRepository.count()).isEqualTo(2);
```

历史版本和错误状态断言 `BusinessException.errorCode == TASK_STATUS_INVALID`。

- [ ] **步骤 3：运行服务测试并确认失败**

运行：

```powershell
.\gradlew.bat test --tests "*BriefReviewServiceTest"
```

预期：FAIL，服务尚未实现。

- [ ] **步骤 4：实现只读查询**

在 `BriefReviewService` 实现：

```java
@Transactional(readOnly = true)
public List<BriefVersionResponse> listBriefVersions(String taskId)

@Transactional(readOnly = true)
public BriefVersionResponse getLatestBrief(String taskId)
```

两种查询都先确认任务存在，不存在时抛出：

```java
new BusinessException(ErrorCode.TASK_NOT_FOUND, "Listing task not found: " + taskId)
```

任务存在但没有 Brief 时抛出 `INVALID_REQUEST`。

- [ ] **步骤 5：实现创建新版本**

实现：

```java
@Transactional
public BriefVersionResponse createVersion(String taskId, CreateBriefVersionRequest request)
```

严格按以下顺序校验：

1. 查找任务。
2. 校验任务为 `WAIT_BRIEF_APPROVE` 且 Brief 为 `WAIT_APPROVE`。
3. 查询最新 Brief。
4. 校验 `baseBriefVersionId` 等于最新 Brief ID。
5. 校验最新 Brief 未批准。
6. 使用 `ObjectMapper.writeValueAsString` 序列化列表字段。
7. 创建新实体，父版本指向最新版本。
8. 设置 `approved=false`、`createdBy=request.createdBy().trim()`。
9. 保存并映射为响应。

不要直接覆盖旧 Brief。

- [ ] **步骤 6：实现 JSON 映射**

`BriefReviewService` 使用 `ObjectMapper` 和 `TypeReference<List<String>>` 处理实体 JSON 字段。序列化或解析内部持久化数据失败时抛出：

```java
new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process Brief JSON fields")
```

不要复制 `ListingWorkflowService` 中的手工 JSON 拼接逻辑。

- [ ] **步骤 7：运行服务测试**

运行：

```powershell
.\gradlew.bat test --tests "*BriefReviewServiceTest"
```

预期：查询和创建版本测试 PASS。

- [ ] **步骤 8：业务文件 review 关卡**

向用户单独展示并说明：

```text
e-commerce/src/main/java/com/snails/ecommerce/listing/application/BriefReviewService.java
```

等待用户确认后才执行任务 4。用户要求修改时，先修改并重新运行 `BriefReviewServiceTest`。

- [ ] **步骤 9：提交查询与创建版本服务**

```powershell
git add e-commerce/src/main/java/com/snails/ecommerce/listing/application/BriefReviewService.java
git add e-commerce/src/test/java/com/snails/ecommerce/listing/application/BriefReviewServiceTest.java
git commit -m "feat: 实现 Brief 查询与版本创建"
```

---

### 任务 4：实现 Brief 批准和任务状态流转

**文件：**

- 修改：`e-commerce/src/test/java/com/snails/ecommerce/listing/application/BriefReviewServiceTest.java`
- 修改：`e-commerce/src/main/java/com/snails/ecommerce/listing/application/BriefReviewService.java`

- [ ] **步骤 1：编写批准规则失败测试**

至少加入：

```java
@Test
void approvesLatestBriefAndMovesTaskToGenerating()

@Test
void rejectsApprovingHistoricalBrief()

@Test
void rejectsRepeatedApproval()

@Test
void rejectsBriefOwnedByAnotherTask()
```

成功审批断言：

```java
assertThat(approved.approved()).isTrue();
assertThat(approved.approvedBy()).isEqualTo("reviewer@example.com");
assertThat(approved.approvedAt()).isNotNull();

ListingTask savedTask = listingTaskRepository.findById(taskId).orElseThrow();
assertThat(savedTask.getStatus()).isEqualTo(ListingTaskStatus.GENERATING);
assertThat(savedTask.getBriefStatus()).isEqualTo(BriefStatus.APPROVED);
assertThat(savedTask.getTextStatus()).isEqualTo(GenerationStatus.NOT_STARTED);
assertThat(savedTask.getImageStatus()).isEqualTo(GenerationStatus.NOT_STARTED);
```

- [ ] **步骤 2：运行批准测试并确认失败**

运行：

```powershell
.\gradlew.bat test --tests "*BriefReviewServiceTest"
```

预期：FAIL，批准方法尚未实现。

- [ ] **步骤 3：实现事务审批**

在同一 `@Transactional` 方法中实现：

```java
public BriefVersionResponse approveBrief(
        String taskId,
        String briefVersionId,
        ApproveBriefRequest request)
```

严格校验：

1. 任务存在。
2. 任务为 `WAIT_BRIEF_APPROVE` 和 `WAIT_APPROVE`。
3. 指定 Brief 存在且 `brief.taskId == taskId`。
4. 指定 Brief 等于当前最新版本。
5. 指定 Brief 尚未批准。

成功后：

```java
brief.setApproved(true);
brief.setApprovedBy(request.approvedBy().trim());
brief.setApprovedAt(LocalDateTime.now());
task.setBriefStatus(BriefStatus.APPROVED);
task.setStatus(ListingTaskStatus.GENERATING);
```

不得修改 `textStatus` 和 `imageStatus`，也不得调用任何生成服务。

- [ ] **步骤 4：运行全部服务测试**

运行：

```powershell
.\gradlew.bat test --tests "*BriefReviewServiceTest"
```

预期：PASS。

- [ ] **步骤 5：再次执行业务文件 review**

向用户展示 `BriefReviewService` 新增审批逻辑，等待确认后才能进入 Controller 实现。

- [ ] **步骤 6：提交审批逻辑**

```powershell
git add e-commerce/src/main/java/com/snails/ecommerce/listing/application/BriefReviewService.java
git add e-commerce/src/test/java/com/snails/ecommerce/listing/application/BriefReviewServiceTest.java
git commit -m "feat: 实现 Brief 批准状态流转"
```

---

### 任务 5：补齐占位 Brief 创建人与最新版本兼容调用

**文件：**

- 修改：`e-commerce/src/main/java/com/snails/ecommerce/listing/application/ListingWorkflowService.java`
- 修改：`e-commerce/src/test/java/com/snails/ecommerce/listing/application/ListingWorkflowServiceTest.java`

- [ ] **步骤 1：先补失败断言**

在 `submitsTaskAndCreatesWaitBriefApproveRecords` 中加入：

```java
assertThat(brief.getCreatedBy()).isEqualTo("SYSTEM");
```

并将最新 Brief 查询改为稳定排序方法。

- [ ] **步骤 2：运行工作流测试并确认失败**

运行：

```powershell
.\gradlew.bat test --tests "*ListingWorkflowServiceTest"
```

预期：FAIL，首个占位 Brief 尚未设置 `createdBy`。

- [ ] **步骤 3：设置系统创建人**

在 `createPlaceholderBrief` 中加入：

```java
brief.setCreatedBy("SYSTEM");
```

`getTaskDetail` 改用新的稳定排序 Repository 方法。

- [ ] **步骤 4：运行工作流测试**

运行：

```powershell
.\gradlew.bat test --tests "*ListingWorkflowServiceTest"
```

预期：PASS。

- [ ] **步骤 5：业务文件 review 关卡**

该文件只允许本任务所需的两处兼容修改。向用户展示 diff，确认后提交。

- [ ] **步骤 6：提交兼容修改**

```powershell
git add e-commerce/src/main/java/com/snails/ecommerce/listing/application/ListingWorkflowService.java
git add e-commerce/src/test/java/com/snails/ecommerce/listing/application/ListingWorkflowServiceTest.java
git commit -m "feat: 标记系统生成的初始 Brief"
```

---

### 任务 6：实现 Brief HTTP 接口

**文件：**

- 修改：`e-commerce/src/test/java/com/snails/ecommerce/listing/api/ListingTaskControllerTest.java`
- 修改：`e-commerce/src/main/java/com/snails/ecommerce/listing/api/ListingTaskController.java`

- [ ] **步骤 1：扩展 Controller 测试依赖**

增加：

```java
@MockitoBean
private BriefReviewService briefReviewService;
```

增加 `post`、`contentType`、`MediaType` 等 MockMvc import。

- [ ] **步骤 2：编写四个成功接口测试**

覆盖：

```text
GET  /api/v1/listing/task_123/briefs
GET  /api/v1/listing/task_123/briefs/latest
POST /api/v1/listing/task_123/briefs
POST /api/v1/listing/task_123/briefs/brief_002/approve
```

验证统一响应结构及关键字段：

```java
.andExpect(jsonPath("$.success").value(true))
.andExpect(jsonPath("$.data.briefVersionId").value("brief_002"))
.andExpect(jsonPath("$.data.createdBy").value("operator@example.com"))
```

- [ ] **步骤 3：编写请求校验和业务错误测试**

至少覆盖：

- 创建请求缺少 `createdBy` 返回 HTTP 400 和 `INVALID_REQUEST`。
- 审批请求 `approvedBy` 为空返回 HTTP 400 和 `INVALID_REQUEST`。
- Service 抛出 `TASK_STATUS_INVALID` 时返回 HTTP 400 和同名错误码。

- [ ] **步骤 4：运行 Controller 测试并确认失败**

运行：

```powershell
.\gradlew.bat test --tests "*ListingTaskControllerTest"
```

预期：FAIL，路由尚未实现。

- [ ] **步骤 5：注入 BriefReviewService**

在 Controller 增加：

```java
private final BriefReviewService briefReviewService;
```

- [ ] **步骤 6：实现四个薄 Controller 方法**

```java
@GetMapping("/{taskId}/briefs")
public ApiResponse<List<BriefVersionResponse>> listBriefVersions(@PathVariable String taskId)

@GetMapping("/{taskId}/briefs/latest")
public ApiResponse<BriefVersionResponse> getLatestBrief(@PathVariable String taskId)

@PostMapping("/{taskId}/briefs")
public ApiResponse<BriefVersionResponse> createBriefVersion(
        @PathVariable String taskId,
        @Valid @RequestBody CreateBriefVersionRequest request)

@PostMapping("/{taskId}/briefs/{briefVersionId}/approve")
public ApiResponse<BriefVersionResponse> approveBrief(
        @PathVariable String taskId,
        @PathVariable String briefVersionId,
        @Valid @RequestBody ApproveBriefRequest request)
```

Controller 只转发参数并包装 `ApiResponse.ok`，不得判断版本或任务状态。

- [ ] **步骤 7：运行 Controller 测试**

运行：

```powershell
.\gradlew.bat test --tests "*ListingTaskControllerTest"
```

预期：PASS。

- [ ] **步骤 8：业务文件 review 关卡**

向用户单独展示：

```text
e-commerce/src/main/java/com/snails/ecommerce/listing/api/ListingTaskController.java
```

等待用户确认后再提交。

- [ ] **步骤 9：提交 HTTP 接口**

```powershell
git add e-commerce/src/main/java/com/snails/ecommerce/listing/api/ListingTaskController.java
git add e-commerce/src/test/java/com/snails/ecommerce/listing/api/ListingTaskControllerTest.java
git commit -m "feat: 提供 Brief 人工审核接口"
```

---

### 任务 7：完整回归和计划收尾

**文件：**

- 修改：`docs/plans/Brief 人工审核闭环实现计划.md`

- [ ] **步骤 1：运行 Listing 模块测试**

运行：

```powershell
cd e-commerce
.\gradlew.bat test --tests "com.snails.ecommerce.listing.*"
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 2：运行完整测试**

运行：

```powershell
.\gradlew.bat test
```

预期：`BUILD SUCCESSFUL`，无失败、无跳过。

- [ ] **步骤 3：检查工作区**

运行：

```powershell
git status --short
```

确认不包含：

- `.gradle/`
- `build/`
- `bin/`
- `.vscode/`
- `storage/`
- 本地 `.env`
- 运行时生成文件

不得覆盖或提交用户现有的 `.gitignore` 修改，除非用户明确要求。

- [ ] **步骤 4：更新计划勾选状态**

将实际完成的步骤更新为 `[x]`。未执行项必须保持 `[ ]`，不得仅因计划结束而批量标记完成。

- [ ] **步骤 5：提交计划状态**

```powershell
git add "docs/plans/Brief 人工审核闭环实现计划.md"
git commit -m "docs: 完成 Brief 人工审核闭环计划"
```

---

## 2. 验收清单

- [ ] 可以查询指定任务的全部 Brief 版本，结果按创建时间和版本 ID 倒序排列。
- [ ] 可以查询指定任务的最新 Brief。
- [ ] 人工修改始终创建新版本，不覆盖历史数据。
- [ ] 新版本的父版本指向修改前的最新版本。
- [ ] 历史版本不能被继续修改或批准。
- [ ] 任务不在等待 Brief 审批状态时，修改和审批均返回 `TASK_STATUS_INVALID`。
- [ ] Brief 与任务不匹配时返回 `INVALID_REQUEST`。
- [ ] 首个占位 Brief 的创建人是 `SYSTEM`。
- [ ] 人工版本记录 `createdBy`。
- [ ] 批准版本记录 `approvedBy` 和 `approvedAt`。
- [ ] 批准后任务状态为 `GENERATING`，Brief 状态为 `APPROVED`。
- [ ] 批准后文案和图片状态仍为 `NOT_STARTED`。
- [ ] 批准不会触发真实文案或图片生成。
- [ ] 四个 HTTP 接口返回统一 `ApiResponse`。
- [ ] DTO 不直接暴露 JPA 实体。
- [ ] 完整 Gradle 测试输出 `BUILD SUCCESSFUL`。
