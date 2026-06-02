# Offer 捕手 — 项目分析与优化建议

> 生成日期：2026-06-02  
> 项目类型：零依赖 Java MVP（`HttpServer` + 12 家招聘官网 adapter + 关键词匹配）

---

## 项目概览

「Offer 捕手」是一个学生求职匹配智能体 MVP，主要能力包括：

- 学生画像录入与 PDF 简历解析
- 12 家招聘官网并发检索与匹配排序
- 单岗位诊断与简历优化建议
- 合规岗位采集策略说明
- CloudBase CloudRun 部署支持

技术栈：JDK 17+（Docker 使用 21）、原生 `HttpServer`、手写 JSON、无 Maven/Gradle、Vanilla JS 前端。

---

## 一、高优先级（性能 / 正确性）

### 1. `/api/analyze` 会重复触发完整搜索

**位置：** `LiveJobFinder.findGeneratedJob()`

**问题：** 每次调用 `/api/analyze` 且传入 `jobId` 时，都会重新并发请求 12 家招聘官网（单次约 8–15 秒）。前端点击「查看诊断」已用本地 `latestReports` 缓存，但「分析心仪岗位」仍会走 API 触发全量搜索。

**建议：**

- 前端诊断优先使用 `latestReports`，仅在自定义 JD 时调用 `/api/analyze`
- 或后端接受完整 `job` 对象，直接调用 `MatchEngine.analyze()`，跳过搜索
- 或对 `(profileHash, jobId)` 做短期内存缓存（如 5 分钟 TTL）

**预期收益：** 诊断响应从秒级降至毫秒级。

---

### 2. 每次搜索都新建 / 销毁线程池

**位置：** `LiveJobFinder.search()`

**问题：** 每次 `search()` 都 `Executors.newFixedThreadPool(...)` 并在 `finally` 中 `shutdownNow()`，频繁创建线程池有开销，高并发下可能引发线程创建风暴。

**建议：** 使用类级共享 `ExecutorService`，在 JVM shutdown hook 中 graceful shutdown。

---

### 3. 反馈 API 是空实现

**位置：** `OfferCatcherApplication.handleFeedback()`

**问题：** README 描述「记录收藏、忽略、投递倾向」，实际只返回文案，未持久化，重启后丢失，也无法影响后续推荐权重。

**建议：**

- 短期：内存 Map 或本地 JSON 文件记录
- 长期：接入 CloudBase 数据库，并在 `MatchEngine` 中读取反馈调整权重

---

## 二、中优先级（架构与可维护性）

### 4. 缺少构建工具与测试

**问题：** 使用 `javac` 直接编译，无 Maven/Gradle，无任何单元测试。

**风险：**

- Adapter 正则（如百度 `2027AIDU-`）官网改版即失效，无回归保护
- `MatchEngine`、`ResumeParser`、`Json` 等核心逻辑难以安全重构

**建议：**

- 引入 Maven 或 Gradle（可保持运行时零第三方依赖）
- 优先为 `MatchEngine`、`TextUtils`、`ResumeParser`、`Json` 编写单元测试
- Adapter 使用 HTML fixture 做快照测试

---

### 5. 热路径重复编译正则

**位置：**

- `MatchEngine.resumeQuality()` — 每次调用 `Pattern.compile(...)`
- `HttpUtils.firstRegex()` — 每次新建 Pattern

**建议：** 提取为 `private static final Pattern` 常量。

---

### 6. Adapter 每次 new 实例

**位置：** `LiveJobFinder.adapterFor()`

**问题：** 每次 fetch 都 `new TencentAdapter()` 等，这些类无状态。

**建议：** 改为单例或 `Map<String, OfficialJobAdapter>` 注册表。

---

### 7. 重复代码

**位置：** `RolePageAdapter.appendQuery()` 与 `FallbackAdapter.appendQuery()` 完全相同。

**建议：** 抽取到 `HttpUtils` 或公共基类。

---

### 8. JDK 版本不一致

| 位置 | 版本 |
|------|------|
| README | JDK 17+ |
| Dockerfile | JDK 21 |

**建议：** 统一文档、CI 与 Docker 镜像版本。

---

### 9. 缺少 `.gitignore`

**问题：** 仓库中存在 `build/server.pid`、`build/classes` 等构建产物，缺少 `.gitignore` 容易误提交。

**建议：** 添加标准 Java 项目 `.gitignore`（`build/`、`*.class`、`.env` 等）。

---

## 三、功能与体验

### 10. PDF 解析能力有限

**位置：** `PdfTextExtractor`

**问题：** 使用字节流 + 正则抽取文本，对扫描件、复杂字体编码 PDF 效果差（README 已说明）。

**建议：** 引入 Apache PDFBox，或对接 OCR 服务。

---

### 11. 匹配算法偏简单

**当前机制：** 子串关键词命中 + 固定权重。

**缺失能力：**

- 同义词（如「后端」↔「服务端」）
- 技能层级（Spring Boot ⊃ Spring）
- 岗位标题与目标角色的模糊匹配

**具体问题：** `preferenceScore` 对岗位标题是 0/60 二值匹配，标题含「Java 后端工程师」但目标写「后端开发」可能得 0 分。

**建议：**

- 扩展角色别名表
- 中长期考虑 embedding 语义匹配

---

### 12. 前端可优化点

| 问题 | 建议 |
|------|------|
| 「12 家官网」写死 | 使用 `/api/jobs` 返回的 `sources.length` |
| 诊断 API 重复请求 | 卡片诊断直接使用 `latestReports` |
| `alert()` 反馈 | 改为页面内 toast 提示 |
| 分析无 loading 态 | 与 match 一致增加 loading |
| 无 CORS preflight | 跨域部署时需处理 `OPTIONS` |

---

### 13. 静态资源无缓存

**位置：** `OfferCatcherApplication.handleStatic()`

**问题：** 每次 `Files.readAllBytes()`，无 `Cache-Control` / `ETag`。

**建议：** 生产环境为 CSS/JS 添加缓存头。

---

### 14. 合规字段未落地

**位置：** `CompliancePolicy`

**问题：** 预留了 `robotsAllowed`、`rateLimitPolicy`、`lastFetchedAt` 等字段，但 adapter 未实现 robots 检查、限速、抓取审计。

**建议：** 至少在 `HttpUtils.get` 前做 per-domain 限速（如每域名 1 req/s），并记录 `lastFetchedAt`。

---

## 四、部署与运维

### 15. Docker 可加强

**当前状态：** 多阶段构建合理，但缺少 HEALTHCHECK。

**建议：**

```dockerfile
HEALTHCHECK CMD wget -qO- http://localhost:8080/healthz || exit 1
```

可选：非 root 用户、JVM 容器参数（`-XX:+UseContainerSupport`）。

---

### 16. 无优雅关闭

**问题：** `HttpServer` 启动后无 shutdown hook，CloudRun 滚动更新时可能中断进行中的 12 路并发请求。

**建议：** 注册 shutdown hook，停止接受新请求并等待进行中的任务完成。

---

### 17. 无 JSON 请求体大小限制

**问题：** PDF 上传有 8MB 限制，JSON 接口（`/api/match` 等）无限制，存在 DoS 风险。

**建议：** 对 JSON body 设置合理上限（如 256KB）。

---

## 五、架构演进建议（中长期）

### 当前架构

```
前端 → 单体 Java → 12 官网实时抓取
```

### 建议演进

```
前端 → API 层 → 匹配引擎
              → 短期缓存
独立采集服务 → 岗位快照/队列 → 官网
API 层 → 岗位快照（匹配读快照，减少用户等待）
```

**方向：**

1. **采集与匹配分离**：定时合规采集 → 本地快照；用户匹配读快照，减少等待
2. **引入 Maven + 测试 + CI**
3. **PDFBox + 可选 LLM** 做简历解析与 JD 理解（若合规允许）

---

## 六、做得好的地方

- **合规设计**：强调官网来源、不绕过风控、最小字段存储
- **降级策略**：PARSED → CONNECTED → FALLBACK 三级清晰
- **并发搜索**：`CompletableFuture` + timeout + `exceptionally` 较稳健
- **去重逻辑**：每公司保留最高分 + 总量上限 36，结果质量可控
- **路径穿越防护**：静态文件 `normalize()` + `startsWith(base)` 正确
- **零依赖 MVP**：部署简单，Docker 多阶段构建合理

---

## 七、建议实施顺序

| 阶段 | 动作 | 预期收益 |
|------|------|----------|
| 1 | 修复 analyze 重复搜索 + 前端本地诊断 | 诊断从秒级 → 毫秒级 |
| 2 | 共享线程池 + 静态 Pattern | 降低 CPU/线程开销 |
| 3 | `.gitignore` + Maven + 核心单元测试 | 可维护性 |
| 4 | 匹配结果短期缓存 | 重复搜索体验提升 |
| 5 | PDFBox + 反馈持久化 | 功能完整度 |

---

## 附录：关键文件索引

| 模块 | 路径 |
|------|------|
| 入口 / API | `src/main/java/com/offercatcher/OfferCatcherApplication.java` |
| 并发搜索 | `src/main/java/com/offercatcher/adapters/LiveJobFinder.java` |
| 匹配引擎 | `src/main/java/com/offercatcher/matching/MatchEngine.java` |
| 官网 Adapter | `src/main/java/com/offercatcher/adapters/` |
| 简历解析 | `src/main/java/com/offercatcher/resume/` |
| 前端 | `src/main/resources/static/app.js` |
| 构建脚本 | `scripts/build.ps1`、`scripts/run.ps1` |
| 部署 | `Dockerfile` |
