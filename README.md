# Offer 捕手

「Offer 捕手」是一个 Java 实现的学生求职匹配智能体 MVP，覆盖学生画像、招聘官网实时检索入口生成、匹配排序、单岗位诊断、简历优化建议和合规岗位采集说明。

## 功能

- 学生画像录入：学校、专业、届别、目标岗位、城市、技能、项目和简历正文。
- PDF 简历上传：一键上传可复制文本型 PDF，自动提取简历正文并填充学生画像。
- 岗位推荐：基于技能、项目经历、偏好、硬约束、职业兴趣和简历表达质量生成官网检索入口并计算匹配分。
- 简历优先搜索：系统不保存岗位候选池，只有上传/填写简历后才为各招聘官网动态生成匹配入口。
- 官网来源目录：接入腾讯、华为、字节跳动、阿里巴巴、美团、京东、小米、蔚来、百度、快手、网易等招聘官网入口，并在卡片保留招聘官网链接。
- 真实官网 adapter：12 家官网均已接入 adapter；百度、华为云可解析具体职位列表/岗位页，其他官网会实时连接官网页面并生成简历相关检索入口，连接失败时才降级。
- 岗位诊断：展示命中点、缺口、风险和简历优化建议。
- 合规采集策略：保留企业官网来源链接，强调 robots、用户协议、频率限制和最小字段存储。
- CloudBase Ready：提供 Dockerfile，可部署为 CloudBase CloudRun 服务。

当前版本不维护岗位池，只维护公司招聘官网来源目录。系统会根据简历画像生成搜索词和官网检索入口。岗位状态、城市、届别和投递资格必须以企业招聘官网原页面为准。

已解析来源会在卡片标记为“官网已解析”；已请求官网但首屏不暴露稳定职位字段的来源会标记为“官网已连接”；连接失败才标记为“实时检索入口”。

## 当前官方来源

- 华为云工具技术创新 Lab 校招岗位页：`https://www.huaweicloud.com/lab/paas/sacampusrecruitment.html`
- 百度校园招聘职位列表：`https://talent.baidu.com/jobs/list`
- 腾讯招聘官网：`https://join.qq.com/`
- 字节跳动校园招聘官网：`https://jobs.bytedance.com/campus/`
- 阿里巴巴校园招聘官网：`https://talent.alibaba.com/campus/home`
- 美团校园招聘官网：`https://zhaopin.meituan.com/web/campus`
- 京东校园招聘官网：`https://campus.jd.com/`
- 小米校园招聘官网：`https://hr.xiaomi.com/campus`
- 快手招聘官网：`https://zhaopin.kuaishou.cn/`
- 网易招聘官网：`https://hr.163.com/`
- 蔚来校园招聘官网：`https://nio.jobs.feishu.cn/campus`

## 本地运行

本项目不依赖 Maven/Gradle，只需要 JDK 17+。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
powershell -ExecutionPolicy Bypass -File scripts/run.ps1
```

打开：

```text
http://localhost:8080
```

健康检查：

```text
http://localhost:8080/healthz
```

## API

- `GET /api/jobs`：返回招聘官网来源目录和合规采集策略，不返回岗位池。
- `POST /api/resume/upload`：上传 PDF 简历，返回抽取文本、推断画像、解析提示和置信度。
- `POST /api/match`：输入学生画像，调用 12 家官网 adapter；优先解析具体职位，无法解析时生成招聘官网检索入口、匹配分、检索词和已搜索官网来源。
- `POST /api/analyze`：输入学生画像和 `jobId` 或自定义 `customJd`，返回单岗位诊断。
- `POST /api/feedback`：记录收藏、忽略、投递等反馈倾向。

学生画像示例：

```json
{
  "name": "林同学",
  "school": "某双一流高校",
  "major": "软件工程",
  "grade": "2026届",
  "targetRoles": ["后端开发", "云计算开发"],
  "cities": ["深圳", "杭州"],
  "skills": ["Java", "Spring", "MySQL", "Redis", "Docker"],
  "projects": ["校园二手交易平台：负责后端接口、MySQL 表设计、Redis 缓存和登录鉴权。"],
  "interests": ["云服务", "高并发"],
  "constraints": [],
  "resumeText": "熟悉 Java 后端开发，使用 Spring Boot、MySQL、Redis 完成项目。"
}
```

## Qwen 大模型增强（可选）

默认不启用大模型，岗位诊断使用规则引擎。配置 Qwen / DashScope OpenAI-compatible API 后，`/api/analyze` 会在规则诊断基础上尝试生成更自然的命中点、缺口、风险和简历优化建议；模型失败、超时或未配置密钥时会自动回退规则诊断。

也可以在页面右上角点击“添加大模型”，选择 Qwen、DeepSeek、智谱 GLM 或硅基流动模型并填写 API Key。该配置只保存在当前浏览器，服务端不写入数据库或配置文件。

本地启用示例：

```powershell
$env:LLM_ENABLED = "true"
$env:LLM_PROVIDER = "qwen"
$env:LLM_BASE_URL = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
$env:LLM_MODEL = "qwen-plus"
$env:DASHSCOPE_API_KEY = "<your-api-key>"
powershell -ExecutionPolicy Bypass -File scripts/run.ps1
```

可选 provider 默认值：

- `qwen`：`https://dashscope-intl.aliyuncs.com/compatible-mode/v1`，默认模型 `qwen-plus`，环境变量 `DASHSCOPE_API_KEY` 或 `QWEN_API_KEY`。
- `deepseek`：`https://api.deepseek.com`，默认模型 `deepseek-v4-flash`，环境变量 `DEEPSEEK_API_KEY`。
- `zhipu`：`https://open.bigmodel.cn/api/paas/v4`，默认模型 `glm-4-flash`，环境变量 `ZHIPU_API_KEY`、`ZHIPUAI_API_KEY` 或 `BIGMODEL_API_KEY`。
- `siliconflow`：`https://api.siliconflow.cn/v1`，默认模型 `Qwen/Qwen2.5-7B-Instruct`，环境变量 `SILICONFLOW_API_KEY`。

CloudBase 部署时只在环境变量中配置上述值，不要把 API Key 写入仓库。接口返回会新增 `llmEnhanced` 和 `llmProvider` 字段，原有 `report` 结构保持兼容。

## CloudBase CloudRun 部署

不要把腾讯云密钥或 `.env` 写入仓库。先在本机完成 CloudBase CLI 登录或配置凭据，然后执行：

```powershell
cloudbase cloudrun deploy `
  -e <envId> `
  -s offer-catcher `
  --source . `
  --port 8080 `
  --installDependency false `
  --force `
  --json
```

部署后查询默认域名：

```powershell
$body = '{\"EnvId\":\"<envId>\",\"ServerName\":\"offer-catcher\"}'
cloudbase api tcbr DescribeCloudRunServerDetail --api-version 2022-02-17 --body $body --json
```

验证：

```powershell
Invoke-WebRequest -Uri '<DefaultDomainName>/healthz' -UseBasicParsing
Invoke-WebRequest -Uri '<DefaultDomainName>/' -UseBasicParsing
```

## 后续接入真实岗位数据

建议新增岗位采集服务时遵守以下约束：

- 优先使用官方 API、RSS、站点地图和公开校招页面。
- 遵守企业官网用户协议、robots.txt、访问频率限制和反爬策略。
- 不绕过登录、验证码、风控、权限校验或技术保护措施。
- 仅存储岗位匹配所需字段，例如岗位名、城市、届别、技能、截止时间、来源链接。
- 岗位详情页引导用户回到企业官网完成投递。

## PDF 解析说明

当前版本通过构建脚本自动下载 Apache PDFBox，用于抽取文本型 PDF 简历内容。系统不支持扫描件、图片简历或图片型 PDF OCR；若 PDFBox 无法提取可读文本，会提示用户换用可复制文本型 PDF，而不是把乱码写入画像。
