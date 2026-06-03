const fields = [
  "name", "school", "major", "grade", "targetRoles", "cities",
  "skills", "internships", "projects", "interests", "resumeText"
];

let latestReports = [];
let isSearching = false;
let sampleFilled = false;

const storageKeys = {
  resumeHistory: "offerCatcher.resumeHistory",
  matchHistory: "offerCatcher.matchHistory",
  favoriteJobs: "offerCatcher.favoriteJobs",
  llmConfig: "offerCatcher.llmConfig"
};
const historyLimit = 20;
const llmProviders = {
  qwen: {
    label: "Qwen / 阿里云百炼",
    baseUrl: "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
    apiKeyPlaceholder: "填写 DASHSCOPE_API_KEY",
    models: ["qwen-plus", "qwen-turbo", "qwen-max"]
  },
  deepseek: {
    label: "DeepSeek",
    baseUrl: "https://api.deepseek.com",
    apiKeyPlaceholder: "填写 DEEPSEEK_API_KEY",
    models: ["deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner"]
  },
  zhipu: {
    label: "智谱 GLM",
    baseUrl: "https://open.bigmodel.cn/api/paas/v4",
    apiKeyPlaceholder: "填写 ZHIPU_API_KEY / BIGMODEL_API_KEY",
    models: ["glm-4-flash", "glm-4-plus", "glm-4-air"]
  },
  siliconflow: {
    label: "硅基流动",
    baseUrl: "https://api.siliconflow.cn/v1",
    apiKeyPlaceholder: "填写 SILICONFLOW_API_KEY",
    models: ["Qwen/Qwen2.5-7B-Instruct", "deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-R1"]
  }
};
const defaultLlmConfig = {
  enabled: false,
  provider: "qwen",
  baseUrl: llmProviders.qwen.baseUrl,
  model: llmProviders.qwen.models[0],
  apiKey: ""
};

const split = (value) => value.split(/[,，;；、\n]/).map((item) => item.trim()).filter(Boolean);

function readProfile() {
  return {
    name: value("name"),
    school: value("school"),
    major: value("major"),
    grade: value("grade"),
    targetRoles: split(value("targetRoles")),
    cities: split(value("cities")),
    skills: split(value("skills")),
    internships: split(value("internships")),
    projects: split(value("projects")),
    interests: split(value("interests")),
    constraints: [],
    resumeText: value("resumeText")
  };
}

function value(id) {
  return document.getElementById(id).value.trim();
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.message || `请求失败：${response.status}`);
  }
  return response.json();
}

async function loadJobs() {
  const data = await api("/api/jobs");
  renderCompliance(data.compliance);
  renderInitialState(data.sources.length);
}

async function matchJobs() {
  if (isSearching) return;
  setSearchLoading(true);
  try {
    const profile = readProfile();
    const data = await api("/api/match", {
      method: "POST",
      body: JSON.stringify(profile)
    });
    latestReports = [...data.reports].sort((a, b) => b.totalScore - a.totalScore);
    renderReports(latestReports);
    renderJobOptions(latestReports.map((report) => report.job));
    saveMatchHistory(profile, latestReports, data.generatedAt);
    document.getElementById("resultCount").textContent = `已实时检索 ${data.searchedSources.length} 家招聘官网，生成 ${latestReports.length} 个匹配入口`;
    if (latestReports[0]) {
      document.getElementById("topScore").textContent = latestReports[0].totalScore;
      document.getElementById("jobSelect").value = latestReports[0].job.id;
      document.getElementById("diagnosisBox").innerHTML = "<p>推荐岗位已按匹配分排序。点击任意岗位卡片的“查看诊断”后，这里才会生成该岗位的诊断报告。</p>";
    } else {
      document.getElementById("topScore").textContent = "--";
      document.getElementById("diagnosisBox").innerHTML = "<p>没有找到足够匹配的岗位，请补充技能、项目经历或目标城市后重新搜索。</p>";
    }
  } catch (error) {
    document.getElementById("jobList").innerHTML = `<div class="empty-state"><h3>搜索失败</h3><p>${escapeHtml(error.message)}</p></div>`;
    document.getElementById("resultCount").textContent = "搜索失败";
  } finally {
    setSearchLoading(false);
  }
}

function setSearchLoading(loading) {
  isSearching = loading;
  const button = document.getElementById("matchBtn");
  button.disabled = loading;
  button.textContent = loading ? "正在检索 12 家官网..." : "根据简历寻找岗位";
  if (loading) {
    document.getElementById("resultCount").textContent = "正在请求 12 家招聘官网，请稍候";
    document.getElementById("jobList").innerHTML = `
      <div class="empty-state">
        <h3>正在实时搜索</h3>
        <p>系统正在根据简历画像并发请求企业招聘官网，单个站点不可用时会自动降级为官网检索入口。</p>
      </div>
    `;
  }
}

async function analyzeSelected() {
  const profile = readProfile();
  const customJd = value("customJd");
  const jobId = document.getElementById("jobSelect").value;
  if (!customJd && !jobId) {
    document.getElementById("diagnosisBox").innerHTML = "<p>请先上传简历并搜索岗位，或粘贴一个心仪岗位 JD。</p>";
    return;
  }
  const data = await api("/api/analyze", {
    method: "POST",
    body: JSON.stringify({ profile, jobId, customJd, llmConfig: activeLlmConfig() })
  });
  renderDiagnosis(data.report, data);
}

async function uploadResume(file) {
  const status = document.getElementById("uploadStatus");
  status.textContent = `正在解析 ${file.name} ...`;
  const form = new FormData();
  form.append("resume", file);
  const response = await fetch("/api/resume/upload", {
    method: "POST",
    body: form
  });
  const rawBody = await response.text();
  let data;
  try {
    data = rawBody ? JSON.parse(rawBody) : {};
  } catch {
    throw new Error("简历解析接口返回格式异常，请换用可复制文本型 PDF 后重试。");
  }
  if (!response.ok) {
    throw new Error(data.message || `简历上传失败：${response.status}`);
  }
  if (!data.profile || typeof data.profile !== "object" || !data.fileName) {
    throw new Error("简历解析结果为空，请换用可复制文本型 PDF，或手动补充学生画像。");
  }
  applyParsedProfile(data.profile);
  const warningText = data.warnings && data.warnings.length ? ` ${data.warnings.join(" ")}` : "";
  status.textContent = `已解析 ${data.fileName}，置信度 ${data.confidence}%。请检查画像后点击“根据简历寻找岗位”。${warningText}`;
  saveResumeHistory(data);
}

async function handleResumeFile(file) {
  if (!file) return;
  const isPdf = file.type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf");
  if (!isPdf) {
    document.getElementById("uploadStatus").textContent = "当前只支持上传 PDF 简历。";
    return;
  }
  try {
    await uploadResume(file);
  } catch (error) {
    document.getElementById("uploadStatus").textContent = error.message;
  }
}

function applyParsedProfile(profile) {
  setSampleFilled(false);
  const safeProfile = profile || {};
  const assign = (id, value) => {
    if (Array.isArray(value)) {
      document.getElementById(id).value = value.join(", ");
    } else if (value) {
      document.getElementById(id).value = value;
    }
  };
  fields.forEach((id) => assign(id, safeProfile[id]));
}

function renderCompliance(compliance) {
  const box = document.getElementById("complianceBox");
  box.innerHTML = `
    <h2>合规岗位采集策略</h2>
    <ul>${compliance.principles.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>
  `;
}

function renderJobOptions(jobs) {
  const select = document.getElementById("jobSelect");
  select.innerHTML = jobs.map((job) =>
    `<option value="${escapeHtml(job.id)}">${escapeHtml(job.company)} · ${escapeHtml(job.title)} · ${escapeHtml(job.city)}</option>`
  ).join("");
}

function renderReports(reports) {
  const sortedReports = [...reports].sort((a, b) => b.totalScore - a.totalScore);
  document.getElementById("jobList").innerHTML = sortedReports.map((report) => {
    const job = report.job;
    const sourceBadge = sourceStatusLabel(job);
    const favorite = isFavorite(job);
    return `
      <article class="job-card">
        <div class="job-title">
          <div>
            <h3>${escapeHtml(job.company)} · ${escapeHtml(job.title)}</h3>
            <p>${escapeHtml(job.city)} / ${escapeHtml(job.sourceType)} / ${escapeHtml(job.deadline)}</p>
          </div>
          <div class="score">${report.totalScore}</div>
        </div>
        <div class="meta">
          <span class="pill">${escapeHtml(report.priority)}</span>
          <span class="pill">${sourceBadge}</span>
          ${job.grades.map((grade) => `<span class="pill">${escapeHtml(grade)}</span>`).join("")}
        </div>
        <div class="tags">${job.hardSkills.slice(0, 6).map((tag) => `<span class="pill">${escapeHtml(tag)}</span>`).join("")}</div>
        <p>${escapeHtml(report.explanation)}</p>
        <div class="card-actions">
          <button type="button" data-action="diagnose" data-job-id="${escapeHtml(job.id)}">查看诊断</button>
          <button type="button" class="${favorite ? "favorite-active" : ""}" data-action="favorite" data-job-id="${escapeHtml(job.id)}">${favorite ? "已收藏" : "收藏"}</button>
          <a href="${escapeHtml(job.sourceUrl)}" target="_blank" rel="noreferrer">招聘官网</a>
        </div>
      </article>
    `;
  }).join("");
}

function sourceStatusLabel(job) {
  if (job.sourceStatus === "PARSED" || job.sourceType.includes("已解析")) return "官网已解析";
  if (job.sourceStatus === "CONNECTED" || job.sourceType.includes("已连接")) return "官网已连接";
  return job.demo ? "实时检索入口" : "官网入口";
}

function renderInitialState(sourceCount) {
  document.getElementById("topScore").textContent = "--";
  document.getElementById("resultCount").textContent = `已连接 ${sourceCount} 家招聘官网，等待简历`;
  document.getElementById("jobSelect").innerHTML = "";
  document.getElementById("jobList").innerHTML = `
    <div class="empty-state">
      <h3>先上传简历，再寻找岗位</h3>
      <p>系统不展示本地岗位候选池。上传简历后，会根据技能、项目、届别、城市和职业兴趣，实时请求招聘官网并生成匹配结果。</p>
    </div>
  `;
  document.getElementById("diagnosisBox").innerHTML = "<p>搜索结果生成后，点击岗位卡片的“查看诊断”才会生成诊断报告。</p>";
}

function renderDiagnosis(report, meta = {}) {
  const dimensions = Object.entries(report.dimensions).map(([name, score]) => `
    <div class="dimension">
      <strong>${score}</strong>
      <span>${escapeHtml(name)}</span>
    </div>
  `).join("");
  const llmState = diagnosisLlmState(meta);

  document.getElementById("diagnosisBox").innerHTML = `
    <h3>${escapeHtml(report.job.company)} · ${escapeHtml(report.job.title)}</h3>
    <span class="diagnosis-mode ${escapeHtml(llmState.className)}">${escapeHtml(llmState.label)}</span>
    ${llmState.message ? `<p class="llm-message">${escapeHtml(llmState.message)}</p>` : ""}
    <p>${escapeHtml(report.explanation)}</p>
    <div class="dimension-grid">${dimensions}</div>
    <div class="list-block">
      ${listBlock("命中点", report.strengths)}
      ${listBlock("缺口", report.gaps)}
      ${listBlock("风险", report.risks)}
      ${listBlock("简历优化", report.resumeAdvice)}
    </div>
  `;
}

function diagnosisLlmState(meta = {}) {
  if (meta.llmEnhanced) {
    const suffix = meta.llmProvider ? ` / ${meta.llmProvider}${meta.llmModel ? ` / ${meta.llmModel}` : ""}` : "";
    return { label: `大模型增强${suffix}`, className: "llm-ok", message: meta.llmMessage || "" };
  }
  if (meta.llmAttempted) {
    const suffix = meta.llmProvider ? ` / ${meta.llmProvider}${meta.llmModel ? ` / ${meta.llmModel}` : ""}` : "";
    return { label: `大模型调用失败，已回退${suffix}`, className: "llm-fallback", message: meta.llmMessage || "" };
  }
  if (meta.llmStatus === "missing_key") {
    return { label: "规则诊断 / 大模型缺少 API Key", className: "llm-fallback", message: meta.llmMessage || "" };
  }
  return { label: "规则诊断", className: "", message: meta.llmMessage || "" };
}

function listBlock(title, items) {
  return `
    <div>
      <h4>${title}</h4>
      <ul>${items.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>
    </div>
  `;
}

async function selectReport(jobId) {
  const report = latestReports.find((item) => item.job.id === jobId);
  if (report) {
    document.getElementById("jobSelect").value = jobId;
    document.getElementById("diagnosisBox").innerHTML = "<p>正在生成岗位诊断...</p>";
    document.querySelector(".diagnosis").scrollIntoView({ behavior: "smooth", block: "start" });
    try {
      const data = await api("/api/analyze", {
        method: "POST",
        body: JSON.stringify({ profile: readProfile(), jobId, customJd: "", llmConfig: activeLlmConfig() })
      });
      renderDiagnosis(data.report, data);
    } catch (error) {
      renderDiagnosis(report, { llmEnhanced: false, llmProvider: "rules" });
      showToast(error.message);
    }
  }
}

async function sendFeedback(jobId, action) {
  try {
    return await api("/api/feedback", {
      method: "POST",
      body: JSON.stringify({ jobId, action })
    });
  } catch (error) {
    console.warn("feedback failed", error);
    return null;
  }
}

function storageRead(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function storageWrite(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function savedLlmConfig() {
  const raw = { ...defaultLlmConfig, ...storageRead(storageKeys.llmConfig, {}) };
  const provider = llmProviders[raw.provider] ? raw.provider : "qwen";
  const meta = llmProviders[provider];
  const model = meta.models.includes(raw.model) ? raw.model : meta.models[0];
  return { ...raw, provider, baseUrl: meta.baseUrl, model };
}

function activeLlmConfig() {
  const config = savedLlmConfig();
  if (!config.enabled) return { ...config, apiKey: "" };
  const meta = llmProviders[config.provider] || llmProviders.qwen;
  return {
    enabled: true,
    provider: config.provider,
    baseUrl: meta.baseUrl,
    model: config.model || meta.models[0],
    apiKey: config.apiKey || ""
  };
}

function renderLlmStatus() {
  const config = savedLlmConfig();
  const button = document.getElementById("llmConfigBtn");
  if (!button) return;
  const active = Boolean(config.enabled && config.apiKey);
  const providerLabel = llmProviders[config.provider]?.label || config.provider;
  button.classList.toggle("llm-active", active);
  button.textContent = active ? `大模型：${providerLabel}` : "添加大模型";
}

function openLlmModal() {
  const config = savedLlmConfig();
  renderLlmProviderOptions();
  document.getElementById("llmEnabled").checked = Boolean(config.enabled);
  document.getElementById("llmProvider").value = config.provider;
  renderLlmModelOptions(config.provider, config.model);
  document.getElementById("llmApiKey").value = config.apiKey || "";
  document.getElementById("llmModal").hidden = false;
}

function closeLlmModal() {
  document.getElementById("llmModal").hidden = true;
}

function saveLlmConfig() {
  const provider = value("llmProvider") || "qwen";
  const meta = llmProviders[provider] || llmProviders.qwen;
  const config = {
    enabled: document.getElementById("llmEnabled").checked,
    provider,
    baseUrl: meta.baseUrl,
    model: value("llmModel") || meta.models[0],
    apiKey: value("llmApiKey")
  };
  if (config.enabled && !config.apiKey) {
    showToast("请先填写 API Key");
    return;
  }
  storageWrite(storageKeys.llmConfig, config);
  renderLlmStatus();
  closeLlmModal();
  showToast(config.enabled ? "大模型配置已启用" : "大模型配置已保存但未启用");
}

function clearLlmConfig() {
  localStorage.removeItem(storageKeys.llmConfig);
  document.getElementById("llmEnabled").checked = false;
  document.getElementById("llmApiKey").value = "";
  document.getElementById("llmProvider").value = defaultLlmConfig.provider;
  renderLlmModelOptions(defaultLlmConfig.provider, defaultLlmConfig.model);
  renderLlmStatus();
  closeLlmModal();
  showToast("已清除大模型配置");
}

function renderLlmProviderOptions() {
  const select = document.getElementById("llmProvider");
  select.innerHTML = Object.entries(llmProviders).map(([id, provider]) =>
    `<option value="${escapeHtml(id)}">${escapeHtml(provider.label)}</option>`
  ).join("");
}

function renderLlmModelOptions(providerId, selectedModel) {
  const provider = llmProviders[providerId] || llmProviders.qwen;
  const select = document.getElementById("llmModel");
  select.innerHTML = provider.models.map((model) =>
    `<option value="${escapeHtml(model)}">${escapeHtml(model)}</option>`
  ).join("");
  select.value = provider.models.includes(selectedModel) ? selectedModel : provider.models[0];
  document.getElementById("llmApiKey").placeholder = provider.apiKeyPlaceholder;
  document.getElementById("llmBaseUrlText").textContent = provider.baseUrl;
}

function profileSummary(profile = {}) {
  const safeProfile = profile || {};
  return [
    safeProfile.name || "未命名",
    safeProfile.major || "未填专业",
    safeProfile.grade || "未填届别",
    [...(safeProfile.targetRoles || [])].slice(0, 2).join(" / "),
    [...(safeProfile.cities || [])].slice(0, 2).join(" / ")
  ].filter(Boolean).join(" · ");
}

function jobKey(job) {
  return `${job.id}::${job.sourceUrl}`;
}

function saveMatchHistory(profile, reports, generatedAt) {
  const history = storageRead(storageKeys.matchHistory, []);
  const safeProfile = profile || {};
  const entry = {
    id: `match-${Date.now()}`,
    createdAt: generatedAt || new Date().toISOString(),
    profile: safeProfile,
    profileSummary: profileSummary(safeProfile),
    topScore: reports[0]?.totalScore || 0,
    resultCount: reports.length,
    previewJobs: reports.slice(0, 3).map((report) => ({
      company: report.job.company,
      title: report.job.title,
      score: report.totalScore
    })),
    reports
  };
  storageWrite(storageKeys.matchHistory, [entry, ...history].slice(0, historyLimit));
  renderHistory();
}

function saveResumeHistory(data) {
  const history = storageRead(storageKeys.resumeHistory, []);
  const profile = data.profile || {};
  const entry = {
    id: `resume-${Date.now()}`,
    createdAt: new Date().toISOString(),
    fileName: data.fileName,
    confidence: data.confidence,
    profile,
    profileSummary: profileSummary(profile),
    textSummary: String(data.extractedText || "").slice(0, 140)
  };
  storageWrite(storageKeys.resumeHistory, [entry, ...history].slice(0, historyLimit));
  renderHistory();
}

function getFavorites() {
  return storageRead(storageKeys.favoriteJobs, {});
}

function isFavorite(job) {
  return Boolean(getFavorites()[jobKey(job)]);
}

async function toggleFavorite(jobId) {
  const report = latestReports.find((item) => item.job.id === jobId);
  if (!report) return;
  const favorites = getFavorites();
  const key = jobKey(report.job);
  const active = Boolean(favorites[key]);
  if (active) {
    delete favorites[key];
  } else {
    favorites[key] = {
      id: key,
      createdAt: new Date().toISOString(),
      report
    };
  }
  storageWrite(storageKeys.favoriteJobs, favorites);
  renderReports(latestReports);
  renderHistory();
  showToast(active ? "已取消收藏" : "已收藏岗位");
  await sendFeedback(jobId, active ? "unsave" : "save");
}

function renderHistory() {
  renderMatchHistory();
  renderResumeHistory();
  renderFavorites();
}

function renderMatchHistory() {
  const list = document.getElementById("matchHistoryList");
  const history = storageRead(storageKeys.matchHistory, []);
  if (!history.length) {
    list.innerHTML = `<div class="history-item"><p>还没有匹配历史。</p></div>`;
    return;
  }
  list.innerHTML = history.map((entry) => `
    <div class="history-item">
      <strong>${escapeHtml(entry.profileSummary)}</strong>
      <p>${formatTime(entry.createdAt)} · 最高分 ${entry.topScore} · ${entry.resultCount} 个结果</p>
      <p>${(entry.previewJobs || []).map((job) => `${escapeHtml(job.company)} ${escapeHtml(job.title)} ${job.score}`).join(" / ")}</p>
      <div class="history-actions">
        <button type="button" data-history-action="restore-match" data-history-id="${escapeHtml(entry.id)}">查看结果</button>
      </div>
    </div>
  `).join("");
}

function renderResumeHistory() {
  const list = document.getElementById("resumeHistoryList");
  const history = storageRead(storageKeys.resumeHistory, []);
  if (!history.length) {
    list.innerHTML = `<div class="history-item"><p>还没有上传历史。</p></div>`;
    return;
  }
  list.innerHTML = history.map((entry) => `
    <div class="history-item">
      <strong>${escapeHtml(entry.fileName || "简历")}</strong>
      <p>${formatTime(entry.createdAt)} · 置信度 ${entry.confidence || "--"}%</p>
      <p>${escapeHtml(entry.profileSummary || "")}</p>
      <div class="history-actions">
        <button type="button" data-history-action="restore-resume" data-history-id="${escapeHtml(entry.id)}">恢复画像</button>
      </div>
    </div>
  `).join("");
}

function renderFavorites() {
  const list = document.getElementById("favoriteList");
  const favorites = Object.values(getFavorites()).sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));
  if (!favorites.length) {
    list.innerHTML = `<div class="history-item"><p>还没有收藏岗位。</p></div>`;
    return;
  }
  list.innerHTML = favorites.map((item) => {
    const report = item.report;
    const job = report.job;
    return `
      <div class="history-item">
        <strong>${escapeHtml(job.company)} · ${escapeHtml(job.title)}</strong>
        <p>${formatTime(item.createdAt)} · 匹配分 ${report.totalScore} · ${escapeHtml(job.city)}</p>
        <div class="history-actions">
          <button type="button" data-history-action="diagnose-favorite" data-favorite-id="${escapeHtml(item.id)}">查看诊断</button>
          <a href="${escapeHtml(job.sourceUrl)}" target="_blank" rel="noreferrer">官网</a>
        </div>
      </div>
    `;
  }).join("");
}

function restoreMatchHistory(id) {
  const entry = storageRead(storageKeys.matchHistory, []).find((item) => item.id === id);
  if (!entry) return;
  latestReports = [...(entry.reports || [])].sort((a, b) => b.totalScore - a.totalScore);
  applyParsedProfile(entry.profile || {});
  renderReports(latestReports);
  renderJobOptions(latestReports.map((report) => report.job));
  document.getElementById("topScore").textContent = latestReports[0]?.totalScore || "--";
  document.getElementById("resultCount").textContent = `已恢复 ${formatTime(entry.createdAt)} 的匹配历史，共 ${latestReports.length} 个结果`;
  document.getElementById("diagnosisBox").innerHTML = "<p>已恢复历史匹配结果。点击岗位卡片的“查看诊断”后，这里才会生成诊断报告。</p>";
}

function restoreResumeHistory(id) {
  const entry = storageRead(storageKeys.resumeHistory, []).find((item) => item.id === id);
  if (!entry) return;
  applyParsedProfile(entry.profile || {});
  document.getElementById("uploadStatus").textContent = `已恢复 ${entry.fileName || "简历"} 的画像。`;
  showToast("已恢复简历画像");
}

function diagnoseFavorite(id) {
  const item = getFavorites()[id];
  if (!item) return;
  renderDiagnosis(item.report, { llmEnhanced: false, llmProvider: "rules" });
  document.querySelector(".diagnosis").scrollIntoView({ behavior: "smooth", block: "start" });
}

function clearHistory() {
  localStorage.removeItem(storageKeys.resumeHistory);
  localStorage.removeItem(storageKeys.matchHistory);
  renderHistory();
  showToast("已清空历史记录");
}

function formatTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleString("zh-CN", { hour12: false });
}

function showToast(message) {
  const existing = document.querySelector(".toast");
  if (existing) existing.remove();
  const toast = document.createElement("div");
  toast.className = "toast";
  toast.textContent = message;
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 1800);
}

function toggleSampleResume() {
  const sample = {
    name: "林同学",
    school: "某双一流高校",
    major: "软件工程",
    grade: "2026届",
    targetRoles: "后端开发, 云计算开发, 测试开发",
    cities: "深圳, 杭州, 北京",
    skills: "Java, Spring, MySQL, Redis, Linux, Docker, SQL, Python",
    internships: "小米 | 后端开发实习 | 2025.01 - 2025.03：参与接口开发、缓存优化和接口文档编写。",
    projects: "校园二手交易平台：负责后端接口、MySQL 表设计、Redis 缓存和登录鉴权；将首页接口响应从 800ms 优化到 220ms。",
    interests: "云服务, 高并发, 后端工程, 自动化平台",
    resumeText: "熟悉 Java 后端开发，使用 Spring Boot、MySQL、Redis 完成课程项目和实习项目；了解 Linux、Docker、接口测试和 CI/CD。"
  };
  if (sampleFilled) {
    fields.forEach((id) => {
      document.getElementById(id).value = "";
    });
    setSampleFilled(false);
    document.getElementById("uploadStatus").textContent = "点击上传，或将 PDF 简历拖入此区域；图片型 PDF 需要后续接 OCR。";
    return;
  }
  fields.forEach((id) => {
    document.getElementById(id).value = sample[id] || "";
  });
  setSampleFilled(true);
}

function setSampleFilled(active) {
  sampleFilled = active;
  const button = document.getElementById("fillSample");
  if (!button) return;
  button.classList.toggle("sample-active", active);
  button.textContent = active ? "移除示例简历" : "填入示例简历";
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#039;"
  }[char]));
}

document.getElementById("matchBtn").addEventListener("click", matchJobs);
document.getElementById("analyzeBtn").addEventListener("click", analyzeSelected);
document.getElementById("fillSample").addEventListener("click", toggleSampleResume);
document.getElementById("clearHistoryBtn").addEventListener("click", clearHistory);
document.getElementById("llmConfigBtn").addEventListener("click", openLlmModal);
document.getElementById("llmCloseBtn").addEventListener("click", closeLlmModal);
document.getElementById("llmSaveBtn").addEventListener("click", saveLlmConfig);
document.getElementById("llmClearBtn").addEventListener("click", clearLlmConfig);
document.getElementById("llmProvider").addEventListener("change", (event) => {
  renderLlmModelOptions(event.target.value);
});
document.getElementById("llmModal").addEventListener("click", (event) => {
  if (event.target.id === "llmModal") closeLlmModal();
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !document.getElementById("llmModal").hidden) closeLlmModal();
});
document.getElementById("uploadResumeBtn").addEventListener("click", () => {
  document.getElementById("resumeFile").click();
});
document.getElementById("resumeFile").addEventListener("change", async (event) => {
  const file = event.target.files && event.target.files[0];
  await handleResumeFile(file);
  event.target.value = "";
});
const resumeDropZone = document.getElementById("resumeDropZone");
["dragenter", "dragover"].forEach((eventName) => {
  resumeDropZone.addEventListener(eventName, (event) => {
    event.preventDefault();
    event.stopPropagation();
    resumeDropZone.classList.add("drag-over");
  });
});
["dragleave", "drop"].forEach((eventName) => {
  resumeDropZone.addEventListener(eventName, (event) => {
    event.preventDefault();
    event.stopPropagation();
    resumeDropZone.classList.remove("drag-over");
  });
});
resumeDropZone.addEventListener("drop", async (event) => {
  const file = event.dataTransfer && event.dataTransfer.files && event.dataTransfer.files[0];
  await handleResumeFile(file);
});
document.getElementById("jobList").addEventListener("click", (event) => {
  const button = event.target.closest("button[data-action]");
  if (!button) return;
  const jobId = button.dataset.jobId;
  if (button.dataset.action === "diagnose") selectReport(jobId);
  if (button.dataset.action === "favorite") toggleFavorite(jobId);
});
document.querySelector(".history").addEventListener("click", (event) => {
  const button = event.target.closest("button[data-history-action]");
  if (!button) return;
  const action = button.dataset.historyAction;
  if (action === "restore-match") restoreMatchHistory(button.dataset.historyId);
  if (action === "restore-resume") restoreResumeHistory(button.dataset.historyId);
  if (action === "diagnose-favorite") diagnoseFavorite(button.dataset.favoriteId);
});

renderHistory();
renderLlmProviderOptions();
renderLlmStatus();
loadJobs().catch((error) => {
  document.getElementById("jobList").innerHTML = `<p>${escapeHtml(error.message)}</p>`;
});
