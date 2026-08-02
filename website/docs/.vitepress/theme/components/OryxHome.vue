<template>
  <div class="ory-page">
    <!-- ============ 1. Hero ============ -->
    <section class="ory-hero">
      <div class="ory-hero-inner">
        <div class="ory-badge"><span class="ory-badge-dot"></span> {{ t.hero.badge }}</div>
        <h1 class="ory-title"><span class="ory-title-name">OryxOS</span></h1>
        <p class="ory-title-sub">{{ t.hero.sub }}</p>
        <p class="ory-hero-desc">{{ t.hero.desc }}</p>
        <div class="ory-hero-actions">
          <a class="ory-btn-primary" href="/docs/what">{{ t.hero.btnPrimary }}</a>
          <a class="ory-btn-ghost" href="/docs/architecture">{{ t.hero.btnGhost1 }}</a>
          <a class="ory-btn-ghost" :href="github">{{ t.hero.btnGhost2 }}</a>
        </div>
        <div class="ory-hero-note">{{ t.hero.note }}</div>
      </div>
    </section>

    <!-- ============ 2. Two Foundational Problems ============ -->
    <section class="ory-section">
      <div class="ory-section-inner">
        <div class="ory-problem">
          <div class="ory-problem-text">
            <h2 class="ory-section-title">{{ t.problem.title }}</h2>
            <p>{{ t.problem.lead }}</p>
            <p class="ory-problem-item"><strong>① {{ t.problem.p1t }}</strong>{{ t.problem.p1 }}</p>
            <p class="ory-problem-item"><strong>② {{ t.problem.p2t }}</strong>{{ t.problem.p2 }}</p>
            <p class="ory-solution-line">{{ t.problem.solution }}</p>
          </div>
          <div class="ory-problem-compare">
            <div class="ory-compare-item ory-compare-bad">
              <div class="ory-compare-label">{{ t.problem.today }}</div>
              <div class="ory-compare-rows">
                <div class="ory-compare-row" v-for="r in t.problem.bad" :key="r">
                  <span class="ory-compare-icon">✗</span><span>{{ r }}</span>
                </div>
              </div>
            </div>
            <div class="ory-compare-item ory-compare-good">
              <div class="ory-compare-label">OryxOS</div>
              <div class="ory-compare-rows">
                <div class="ory-compare-row" v-for="r in t.problem.good" :key="r">
                  <span class="ory-compare-icon ory-icon-ok">✓</span><span>{{ r }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 3. Architecture Flow ============ -->
    <section class="ory-section ory-flow-section">
      <div class="ory-section-inner">
        <img src="/flow.svg" alt="OryxOS architecture flow" class="ory-flow-img" />
      </div>
    </section>

    <!-- ============ 4. Core Capabilities ============ -->
    <section class="ory-section ory-primitives-section">
      <div class="ory-section-inner ory-primitives-inner">
        <div class="ory-section-header">
          <div class="ory-section-tag">{{ t.caps.tag }}</div>
          <h2 class="ory-section-title">{{ t.caps.title }}</h2>
        </div>
        <div class="ory-primitives">
          <div class="ory-primitive" v-for="p in t.caps.cards" :key="p.title">
            <div class="ory-primitive-header">
              <span class="ory-primitive-icon">{{ p.icon }}</span>
              <div>
                <h3 class="ory-primitive-title">{{ p.title }}</h3>
                <p class="ory-primitive-subtitle">{{ p.sub }}</p>
              </div>
            </div>
            <pre class="ory-code"><code>{{ p.code }}</code></pre>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 5. Real Scenarios ============ -->
    <section class="ory-section">
      <div class="ory-section-inner">
        <div class="ory-section-header">
          <div class="ory-section-tag">{{ t.scen.tag }}</div>
          <h2 class="ory-section-title">{{ t.scen.title }}</h2>
        </div>
        <div class="ory-scenarios">
          <div class="ory-scenario" v-for="(s, i) in t.scen.list" :key="i">
            <div class="ory-scenario-num">{{ (i + 1).toString().padStart(2, '0') }}</div>
            <div>
              <h3 class="ory-scenario-title">{{ s.title }}</h3>
              <p class="ory-scenario-desc">{{ s.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 6. Integration ============ -->
    <section class="ory-section ory-sdk-section">
      <div class="ory-section-inner">
        <div class="ory-section-header">
          <div class="ory-section-tag">{{ t.sdk.tag }}</div>
          <h2 class="ory-section-title">{{ t.sdk.title }}</h2>
        </div>
        <div class="ory-sdk-cards">
          <div
            v-for="c in t.sdk.cards"
            :key="c.title"
            class="ory-sdk-card"
            :class="{ 'ory-sdk-card-featured': c.featured }"
          >
            <div class="ory-sdk-card-icon">{{ c.icon }}</div>
            <h3 class="ory-sdk-card-title">{{ c.title }}</h3>
            <p class="ory-sdk-card-desc">{{ c.desc }}</p>
            <template v-if="c.cmds">
              <div class="ory-sdk-installs"><code v-for="cmd in c.cmds" :key="cmd">{{ cmd }}</code></div>
            </template>
            <template v-else-if="c.langs">
              <div class="ory-langs"><span class="ory-lang" v-for="l in c.langs" :key="l">{{ l }}</span></div>
            </template>
            <template v-if="c.badges">
              <div class="ory-sdk-badges"><span class="ory-sdk-badge" v-for="b in c.badges" :key="b">{{ b }}</span></div>
            </template>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 7. Protocol ============ -->
    <section class="ory-section">
      <div class="ory-section-inner">
        <div class="ory-section-header">
          <div class="ory-section-tag">{{ t.proto.tag }}</div>
          <h2 class="ory-section-title">{{ t.proto.title }}</h2>
          <p class="ory-section-desc">{{ t.proto.desc }}</p>
        </div>
        <div class="ory-proto-grid">
          <div class="ory-proto-group" v-for="g in t.proto.groups" :key="g.label">
            <div class="ory-proto-group-label">{{ g.label }}</div>
            <div class="ory-proto-row" v-for="r in g.rows" :key="r.subject">
              <code class="ory-proto-subject">{{ r.subject }}</code>
              <span class="ory-proto-desc">{{ r.desc }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 8. CTA ============ -->
    <section class="ory-section ory-cta-section">
      <div class="ory-section-inner">
        <div class="ory-cta">
          <h2 class="ory-cta-title">{{ t.cta.title }}</h2>
          <p class="ory-cta-desc">{{ t.cta.desc }}</p>
          <pre class="ory-code ory-cta-code"><code>{{ t.cta.code }}</code></pre>
          <div class="ory-cta-links">
            <a class="ory-btn-primary" href="/docs/quick-start">{{ t.cta.btn1 }}</a>
            <a class="ory-btn-ghost" :href="github">{{ t.cta.btn2 }}</a>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const github = 'https://github.com/oryxos/oryxos'

const props = defineProps({
  lang: { type: String, default: 'en' },
})

const EN = {
  hero: {
    badge: 'Private · Auditable · Agent OS for the Enterprise',
    sub: 'The Agent OS your enterprise fully controls',
    desc: 'OryxOS is a Java-native Agent OS that runs on your own Kubernetes or servers. Deploy one base to run every business agent — shared channels, model routing, tools, memory, and sandbox — while all data stays inside your infrastructure. No cloud lock-in.',
    btnPrimary: 'Get Started → ',
    btnGhost1: 'Architecture',
    btnGhost2: ' GitHub ',
    note: 'JDK 21 · Spring Boot 3.x · Spring AI · self-built ReAct loop · SQLite · MCP',
  },
  problem: {
    title: 'Two Foundational Problems',
    lead: 'Every enterprise that wants production AI agents hits the same two foundational problems.',
    p1t: 'Agents need a runtime you can host',
    p1: 'A framework hands you code, not a runtime. Model calls, sessions, tool wiring, memory, execution — every team rebuilds the same plumbing before any business logic exists.',
    p2t: 'Agents must be governed and audited',
    p2: 'In banks, telecom, energy and healthcare, every model call and tool action must be traceable, and every capability must stay inside the network. SaaS agents leak data and fail compliance.',
    solution: 'OryxOS solves exactly these two problems, so teams can focus on agent logic rather than infrastructure.',
    today: 'Today',
    bad: [
      'SaaS platforms bind you to a cloud ecosystem — data leaves the enterprise',
      'Frameworks give you code, not a runtime — every team rebuilds the plumbing',
      'No audit trail — model calls and tool actions are untraceable',
      'Each new agent starts from scratch — no shared base',
    ],
    good: [
      'Self-hosted on your own K8s or servers — data never leaves',
      'One base for all agents — channels, models, tools, memory shared',
      'Audit from day one — tool_invocations & llm_calls persisted',
      'Java-native — aligns with your existing enterprise stack',
    ],
  },
  caps: {
    tag: 'Core Capabilities',
    title: 'Five capabilities, one shared engine',
    cards: [
      {
        icon: '🤖',
        title: 'Model Routing',
        sub: 'provider name → ChatModel · hot-swap · no lock-in',
        code: `# profile.yaml — one Agent, any model
provider:
  name: deepseek        # or qwen / kimi / anthropic
  model: deepseek-chat
  temperature: 0.7

$ oryxos provider list   # every provider in one view`,
      },
      {
        icon: '🔁',
        title: 'Self-built ReAct Loop',
        sub: 'think → act → observe · fully under your control',
        code: `$ oryxos chat
You: 查一下服务器状态并生成报告

[1/3] Tool: read_file   /var/log/app.log
[2/3] Tool: shell       uptime && free -h
[3/3] Answer: 服务器运行正常,内存使用率 42%…`,
      },
      {
        icon: '🧠',
        title: 'Memory + Tool System',
        sub: 'MEMORY.md long-term memory · MCP · sandbox whitelist',
        code: `# Long-term memory — persists across sessions
$ oryxos chat
You: 记住,告警窗口时间从 18:00 开始

# Tools pass the sandbox whitelist before running
$ oryxos tool list
  read_file · write_file · shell · http_get
  save_memory · recall_memory`,
      },
      {
        icon: '🔧',
        title: 'Plugin Tool System',
        sub: 'SKILL.md zero-code · MCP light-code · @Tool deep Java',
        code: `# Three tiers to extend OryxOS
# Tier 1: SKILL.md — drop a markdown, get a capability
# Tier 2: MCP — stdio/HTTP tools in any language
# Tier 3: @Tool annotation — deep Java integration

$ oryxos tool list
  read_file · write_file · shell · http_get
  save_memory · recall_memory · mcp:weather`,
      },
      {
        icon: '🌐',
        title: 'Web Service',
        sub: '10 REST endpoints · OpenAPI 3.0 · embed agents anywhere',
        code: `# Start the HTTP API
$ oryxos serve   # port 8080

# Create a session & send a message
$ curl -X POST http://localhost:8080/api/v1/sessions \\
  -H "Content-Type: application/json" \\
  -d '{"profile":"default","user_id":"u1"}'

# Swagger UI at /swagger-ui
# Sessions, agents, profiles, memory, tools — all via REST`,
      },
    ],
  },
  scen: {
    tag: 'Real Scenarios',
    title: 'Eight real-world use cases',
    list: [
      { title: 'Omnichannel customer service', desc: 'One agent base serves WeChat, email and tickets — switch channels without changing agent code.' },
      { title: 'Ops auto-remediation', desc: 'The agent triages alerts, reads logs and restarts services — every action written to the audit log.' },
      { title: 'HR assistant', desc: 'Employee Q&A and resume screening run on your own infrastructure — data never leaves the company.' },
      { title: 'Enterprise knowledge Q&A', desc: 'Agents answer from internal docs and remember user preferences across sessions.' },
      { title: 'Sales insight', desc: 'Plug into CRM via MCP, pull data, and the agent produces analysis — no SaaS middleman.' },
      { title: 'Multi-agent on one base', desc: 'Run ops, support and HR agents side by side, sharing providers, tools and memory.' },
      { title: 'Compliance & audit', desc: 'Every model call and tool invocation is persisted to SQLite — fully traceable, day one.' },
      { title: 'Sandboxed execution', desc: 'File, shell and HTTP actions pass a whitelist check — agents can only do what you allow.' },
    ],
  },
  sdk: {
    tag: 'Integration',
    title: 'Three ways to connect — pick what fits',
    cards: [
      {
        icon: '🔌',
        title: 'CLI Channel',
        desc: 'The fastest way to start. One jar, interactive multi-turn chat, and the full 12-command toolbox.',
        cmds: ['curl -LO https://github.com/oryxos/oryxos/releases/download/latest/oryxos.jar', 'java -jar oryxos.jar init', 'java -jar oryxos.jar chat'],
      },
      {
        icon: '📦',
        title: 'Web Service / REST API',
        desc: 'The only gateway for business systems to embed agents — 10 endpoints for sessions, agents, profiles, memory and tools. OpenAPI 3.0 built in.',
        featured: true,
        cmds: ['POST /api/v1/sessions', 'POST /api/v1/sessions/{id}/messages', 'POST /api/v1/agents/default/invoke'],
      },
      {
        icon: '🤖',
        title: 'Plugin Tools & MCP',
        desc: 'Three tiers to extend OryxOS — SKILL.md zero-code, MCP lightweight-code, @Tool deep Java integration.',
        badges: ['SKILL.md', 'MCP Client', '@Tool', 'Sandbox'],
      },
    ],
  },
  proto: {
    tag: 'Protocol',
    title: 'One CLI + one REST API to every capability',
    desc: 'OryxOS is a single Spring Boot binary. CLI and HTTP share the same engine, profiles and session storage.',
    groups: [
      {
        label: 'CLI Commands',
        rows: [
          { subject: 'oryxos init', desc: 'Create the .oryxos/ workspace' },
          { subject: 'oryxos chat', desc: 'Interactive multi-turn conversation' },
          { subject: 'oryxos serve', desc: 'Start the HTTP API service (port 8080)' },
          { subject: 'oryxos gateway', desc: 'Resident daemon across channels' },
          { subject: 'oryxos provider list', desc: 'List configured LLM providers' },
          { subject: 'oryxos tool list', desc: 'List registered tools' },
        ],
      },
      {
        label: 'REST API (core phase)',
        rows: [
          { subject: 'POST /api/v1/sessions', desc: 'Create a session' },
          { subject: 'POST /api/v1/sessions/{id}/messages', desc: 'Send a message (keeps context)' },
          { subject: 'POST /api/v1/agents/{name}/invoke', desc: 'Stateless single agent call' },
          { subject: 'GET /api/v1/profiles', desc: 'List profiles' },
          { subject: 'GET /api/v1/tools', desc: 'List tools' },
          { subject: 'GET /api/v1/health', desc: 'Health check' },
        ],
      },
      {
        label: 'Workspace',
        rows: [
          { subject: '.oryxos/profiles/', desc: 'Profile YAML, one per agent' },
          { subject: '.oryxos/memory/MEMORY.md', desc: 'Long-term memory' },
          { subject: '.oryxos/skills/', desc: 'SKILL.md capability templates' },
          { subject: '.oryxos/oryxos.db', desc: 'SQLite: sessions + audit tables' },
        ],
      },
    ],
  },
  cta: {
    title: 'Start Building',
    desc: 'Self-host on your own infrastructure — no sign-up, no cloud.',
    code: `# 1. Build the fat JAR (Maven Wrapper included)
./mvnw clean package

# 2. Initialize the workspace
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar init

# 3. Inject your LLM key via environment (never in config files)
export DEEPSEEK_API_KEY=sk-xxx

# 4. Chat with your agent
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar chat`,
    btn1: 'Read the Docs',
    btn2: ' GitHub ',
  },
}

const ZH = {
  hero: {
    badge: '企业私有 · 完全可审计 · Agent 统一底座',
    sub: '企业能完全掌控的 Agent 底座',
    desc: 'OryxOS 是基于 Java 实现的、面向企业（尤其严监管企业）的私有可审计 Agent OS 统一底座。装在企业自己的 K8s 或服务器上，统一跑各种业务 Agent，共享渠道接入、模型路由、工具调用、记忆系统、沙箱执行能力。数据完全留在企业基础设施，不锁任何云生态。',
    btnPrimary: '快速开始 → ',
    btnGhost1: '架构',
    btnGhost2: ' GitHub ',
    note: 'JDK 21 · Spring Boot 3.x · Spring AI · 自实现 ReAct loop · SQLite · MCP',
  },
  problem: {
    title: '两个根本问题',
    lead: '每个想真正跑生产 AI Agent 的企业，都会撞上同样的两个根本问题。',
    p1t: 'Agent 需要一个你自建的运行时',
    p1: '框架只给你代码，不给你运行环境。模型调用、会话、工具接线、记忆、执行——每个团队都要先把这些管道重造一遍，才开始写业务逻辑。',
    p2t: 'Agent 必须可控、可审计',
    p2: '银行、政府、电信、能源、医疗：每一次模型调用、每一次工具操作都要可追溯，每个能力都要留在内网。SaaS 方案数据出企业，过不了合规。',
    solution: 'OryxOS 正好解决这两个问题，让团队专注 Agent 逻辑而不是基础设施。',
    today: '现状',
    bad: [
      'SaaS 绑定云生态——数据离开企业',
      '框架只给代码——运行环境自己搭，重复造轮子',
      '无审计——模型调用、工具操作不可追溯',
      '每个新 Agent 从零开始——没有共享底座',
    ],
    good: [
      '私有部署——装在自己的 K8s/服务器上，数据不出企业',
      '统一底座——渠道、模型、工具、记忆全共享',
      '审计 day one——tool_invocations / llm_calls 落库',
      'Java 原生——对齐企业现有技术栈',
    ],
  },
  caps: {
    tag: '核心能力',
    title: '五大能力，一套共享引擎',
    cards: [
      {
        icon: '🤖',
        title: '模型路由',
        sub: 'provider name → ChatModel 显式映射 · 运行时切换 · 无锁定',
        code: `# profile.yaml —— 一个 Agent，任意模型
provider:
  name: deepseek        # 或 qwen / kimi / anthropic
  model: deepseek-chat
  temperature: 0.7

$ oryxos provider list   # 所有 Provider 一览`,
      },
      {
        icon: '🔁',
        title: '自实现 ReAct 循环',
        sub: '思考 → 调工具 → 看结果 → 再决定 · 完全可控',
        code: `$ oryxos chat
你: 查一下服务器状态并生成报告

[1/3] Tool: read_file   /var/log/app.log
[2/3] Tool: shell       uptime && free -h
[3/3] 回答: 服务器运行正常，内存占用 42%…`,
      },
      {
        icon: '🧠',
        title: '记忆 + 工具体系',
        sub: 'MEMORY.md 长期记忆 · MCP 接入 · 沙箱白名单',
        code: `# 长期记忆 —— 跨会话保留
$ oryxos chat
你: 记住，告警窗口时间从 18:00 开始

# 工具经沙箱白名单校验后才执行
$ oryxos tool list
  read_file · write_file · shell · http_get
  save_memory · recall_memory`,
      },
      {
        icon: '🔧',
        title: '插件工具体系',
        sub: 'SKILL.md 零代码 · MCP 轻代码 · @Tool 深度 Java 集成',
        code: `# 三档接入扩展 OryxOS
# 第一档：SKILL.md —— 丢个 markdown 就获得一项能力
# 第二档：MCP —— stdio/HTTP 工具，不限语言
# 第三档：@Tool 注解 —— 深度 Java 集成

$ oryxos tool list
  read_file · write_file · shell · http_get
  save_memory · recall_memory · mcp:weather`,
      },
      {
        icon: '🌐',
        title: 'Web Service',
        sub: '10 个 REST 端点 · OpenAPI 3.0 · Agent 嵌入任意系统',
        code: `# 启动 HTTP API
$ oryxos serve   # 默认端口 8080

# 创建会话并发消息
$ curl -X POST http://localhost:8080/api/v1/sessions \\
  -H "Content-Type: application/json" \\
  -d '{"profile":"default","user_id":"u1"}'

# Swagger UI 见 /swagger-ui
# 会话、Agent、Profile、记忆、工具 —— 全部走 REST`,
      },
    ],
  },
  scen: {
    tag: '真实场景',
    title: '八大真实落地场景',
    list: [
      { title: '全渠道智能客服', desc: '企业微信、邮件、工单一个底座搞定——换渠道不用改 Agent 代码。' },
      { title: '运维自愈', desc: 'Agent 分诊告警、查日志、重启服务，每一步都写入审计日志。' },
      { title: 'HR 助手', desc: '员工问答、简历初筛跑在自己基础设施上——数据不出公司。' },
      { title: '企业知识问答', desc: 'Agent 基于内部文档回答，并跨会话记住用户偏好。' },
      { title: '销售洞察', desc: '经 MCP 接入 CRM 拉取数据，Agent 产出分析——无 SaaS 中间商。' },
      { title: '一底座多 Agent', desc: '运维、客服、HR Agent 并存，共享 Provider、工具与记忆。' },
      { title: '合规与审计', desc: '每次模型调用、工具调用落 SQLite——day one 可追溯。' },
      { title: '沙箱执行', desc: '文件/Shell/HTTP 动作过白名单校验——Agent 只能干你允许的事。' },
    ],
  },
  sdk: {
    tag: '接入方式',
    title: '三种接入方式——按需选择',
    cards: [
      {
        icon: '🔌',
        title: 'CLI 命令行',
        desc: '最快捷的起步方式。一个 JAR，交互式多轮对话，完整的 12 个命令工具箱。',
        cmds: ['curl -LO https://github.com/oryxos/oryxos/releases/download/latest/oryxos.jar', 'java -jar oryxos.jar init', 'java -jar oryxos.jar chat'],
      },
      {
        icon: '📦',
        title: 'Web Service / REST API',
        desc: '业务系统把 Agent 嵌入现有流程的唯一通道——会话、Agent、Profile、记忆、工具 10 个端点，自带 OpenAPI 3.0。',
        featured: true,
        cmds: ['POST /api/v1/sessions', 'POST /api/v1/sessions/{id}/messages', 'POST /api/v1/agents/default/invoke'],
      },
      {
        icon: '🤖',
        title: '插件工具与 MCP',
        desc: '三档接入扩展 OryxOS——SKILL.md 零代码、MCP 轻代码、@Tool 深度 Java 集成。',
        badges: ['SKILL.md', 'MCP Client', '@Tool', '沙箱'],
      },
    ],
  },
  proto: {
    tag: '协议',
    title: '一个 CLI + 一套 REST API，全部能力直达',
    desc: 'OryxOS 是单体 Spring Boot 二进制。CLI 与 HTTP 共享同一引擎、同一份 Profile 与会话存储。',
    groups: [
      {
        label: 'CLI 命令',
        rows: [
          { subject: 'oryxos init', desc: '生成 .oryxos/ 工作区' },
          { subject: 'oryxos chat', desc: '交互式多轮对话' },
          { subject: 'oryxos serve', desc: '启动 HTTP API 服务（默认 8080）' },
          { subject: 'oryxos gateway', desc: '跨 Channel 常驻守护进程' },
          { subject: 'oryxos provider list', desc: '列出已配置的 LLM Provider' },
          { subject: 'oryxos tool list', desc: '列出已注册工具' },
        ],
      },
      {
        label: 'REST API（核心阶段）',
        rows: [
          { subject: 'POST /api/v1/sessions', desc: '创建会话' },
          { subject: 'POST /api/v1/sessions/{id}/messages', desc: '发消息（保持上下文）' },
          { subject: 'POST /api/v1/agents/{name}/invoke', desc: '无状态单次 Agent 调用' },
          { subject: 'GET /api/v1/profiles', desc: '列出 Profile' },
          { subject: 'GET /api/v1/tools', desc: '列出工具' },
          { subject: 'GET /api/v1/health', desc: '健康检查' },
        ],
      },
      {
        label: '工作区',
        rows: [
          { subject: '.oryxos/profiles/', desc: 'Profile YAML，一个 Agent 一个' },
          { subject: '.oryxos/memory/MEMORY.md', desc: '长期记忆' },
          { subject: '.oryxos/skills/', desc: 'SKILL.md 能力模板' },
          { subject: '.oryxos/oryxos.db', desc: 'SQLite：会话 + 审计表' },
        ],
      },
    ],
  },
  cta: {
    title: '开始构建',
    desc: '部署在自己基础设施上——无需注册，不绑云。',
    code: `# 1. 构建 fat JAR（仓库自带 Maven Wrapper）
./mvnw clean package

# 2. 初始化工作区
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar init

# 3. 用环境变量注入 LLM API key（绝不写进配置文件）
export DEEPSEEK_API_KEY=sk-xxx

# 4. 开始对话
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar chat`,
    btn1: '阅读文档',
    btn2: ' GitHub ',
  },
}

const t = computed(() => (props.lang === 'zh' ? ZH : EN))
</script>

<style scoped>
/* ================================================================
 * 视觉对齐 mq9.robustmq.com 首页：极简黑白灰 + 灰底分区 + 等宽代码块
 * （mq9-* 类 → ory-* 类，样式规则 1:1 复刻）
 * ================================================================ */
.ory-page {
  min-height: 100vh;
  background: #fff;
  color: #000;
  font-family: inherit;
}

/* ---------- Hero ---------- */
.ory-hero {
  position: relative;
  padding: 100px 24px 80px;
  text-align: center;
  overflow: hidden;
}
.ory-hero-inner {
  position: relative;
  max-width: 760px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.ory-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  border-radius: 20px;
  border: 1px solid #d4d4d4;
  background: #f5f5f5;
  color: #555;
  font-size: 12px;
  margin-bottom: 28px;
}
.ory-badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #000;
  animation: ory-pulse 2s infinite;
}
.ory-title {
  margin: 0 0 12px;
  line-height: 1;
}
.ory-title-name {
  font-size: clamp(72px, 14vw, 120px);
  font-weight: 900;
  letter-spacing: -0.03em;
  color: #000;
}
.ory-title-sub {
  font-size: 18px;
  color: #666;
  margin: 0 0 20px;
}
.ory-hero-desc {
  font-size: 16px;
  line-height: 1.7;
  color: #444;
  max-width: 600px;
  margin: 0 0 32px;
}
.ory-hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 20px;
}
.ory-btn-primary {
  padding: 11px 28px;
  border-radius: 8px;
  background: #000;
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  text-decoration: none;
  transition: opacity 0.2s, transform 0.15s;
}
.ory-btn-primary:hover {
  opacity: 0.75;
  transform: translateY(-1px);
}
.ory-btn-ghost {
  padding: 11px 28px;
  border-radius: 8px;
  border: 1px solid #d4d4d4;
  color: #333;
  font-weight: 600;
  font-size: 14px;
  text-decoration: none;
  transition: border-color 0.2s, background 0.2s;
}
.ory-btn-ghost:hover {
  border-color: #000;
  background: #f5f5f5;
}
.ory-hero-note {
  font-size: 12px;
  color: #999;
}

/* ---------- Sections ---------- */
.ory-section {
  padding: 72px 24px;
}
.ory-section-inner {
  max-width: 1000px;
  margin: 0 auto;
}
.ory-primitives-inner {
  max-width: 1400px;
}
.ory-section-header {
  text-align: center;
  margin-bottom: 48px;
}
.ory-section-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #555;
  padding: 4px 12px;
  border-radius: 20px;
  border: 1px solid #d4d4d4;
  background: #f5f5f5;
  margin-bottom: 14px;
}
.ory-section-title {
  font-size: clamp(22px, 4vw, 32px);
  font-weight: 700;
  color: #000;
  margin: 0 0 12px;
}
.ory-section-desc {
  font-size: 15px;
  color: #666;
  max-width: 600px;
  margin: 0 auto;
  line-height: 1.6;
}

/* ---------- Problem / Compare ---------- */
.ory-problem {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 48px;
  align-items: start;
}
.ory-problem-text p {
  color: #666;
  line-height: 1.7;
  margin: 0 0 14px;
  font-size: 15px;
}
.ory-problem-item strong {
  color: #000;
  display: block;
  margin-bottom: 4px;
}
.ory-solution-line {
  color: #000 !important;
  font-weight: 600;
}
.ory-problem-compare {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.ory-compare-item {
  padding: 20px;
  border-radius: 12px;
  border: 1px solid #e5e5e5;
}
.ory-compare-bad {
  background: #fafafa;
}
.ory-compare-good {
  background: #f5f5f5;
  border-color: #d4d4d4;
}
.ory-compare-label {
  font-size: 11px;
  font-weight: 700;
  color: #999;
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
.ory-compare-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ory-compare-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 13px;
  color: #555;
  line-height: 1.5;
}
.ory-compare-icon {
  flex-shrink: 0;
  font-style: normal;
  color: #bbb;
  font-weight: 700;
  width: 14px;
}
.ory-icon-ok {
  color: #000;
}

/* ---------- Flow ---------- */
.ory-flow-section {
  padding: 0 24px 72px;
}
.ory-flow-img {
  width: 100%;
  display: block;
  border: 1px solid #e5e5e5;
  border-radius: 12px;
}

/* ---------- Primitives (core capabilities) ---------- */
.ory-primitives-section {
  background: #f5f5f5;
}
.ory-primitives {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-auto-rows: 1fr;
  gap: 16px;
}
/* 5 items in 3-col grid: center the last row by pushing 4th & 5th to cols 2-3 */
.ory-primitive:nth-child(4):nth-last-child(2) {
  grid-column: 2;
}
.ory-primitive {
  padding: 20px;
  border-radius: 14px;
  border: 1px solid #e5e5e5;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: border-color 0.2s, box-shadow 0.2s;
  min-width: 0;
  overflow: hidden;
}
.ory-primitive .ory-code {
  flex: 1;
}
.ory-primitive:hover {
  border-color: #000;
  box-shadow: 0 4px 16px #0000000f;
}
.ory-primitive-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.ory-primitive-icon {
  font-size: 28px;
  flex-shrink: 0;
}
.ory-primitive-title {
  font-size: 17px;
  font-weight: 700;
  color: #000;
  margin: 0 0 2px;
}
.ory-primitive-subtitle {
  font-size: 12px;
  color: #999;
  margin: 0;
}

/* ---------- Code blocks ---------- */
.ory-code {
  background: #f5f5f5;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  padding: 14px 16px;
  font-size: 12px;
  line-height: 1.6;
  color: #333;
  overflow-x: auto;
  margin: 0;
  white-space: pre;
}
.ory-code code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  background: none;
  color: inherit;
}

/* ---------- Scenarios ---------- */
.ory-scenarios {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}
.ory-scenario {
  display: flex;
  gap: 16px;
  padding: 20px;
  border-radius: 12px;
  border: 1px solid #e5e5e5;
  background: #fafafa;
}
.ory-scenario-num {
  font-size: 28px;
  font-weight: 900;
  color: #e5e5e5;
  line-height: 1;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.ory-scenario-title {
  font-size: 15px;
  font-weight: 600;
  color: #000;
  margin: 0 0 6px;
}
.ory-scenario-desc {
  font-size: 13px;
  color: #666;
  line-height: 1.6;
  margin: 0;
}

/* ---------- Integration (SDK cards) ---------- */
.ory-sdk-section {
  background: #f5f5f5;
}
.ory-sdk-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-auto-rows: 1fr;
  gap: 20px;
}
.ory-sdk-card {
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 16px;
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ory-sdk-card-featured {
  border-color: #000;
}
.ory-sdk-card-icon {
  font-size: 28px;
}
.ory-sdk-card-title {
  font-size: 17px;
  font-weight: 700;
  color: #000;
  margin: 0;
}
.ory-sdk-card-desc {
  font-size: 14px;
  color: #666;
  line-height: 1.6;
  margin: 0;
  flex: 1;
}
.ory-langs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.ory-lang {
  padding: 4px 12px;
  border-radius: 20px;
  border: 1px solid #d4d4d4;
  background: #f5f5f5;
  color: #333;
  font-size: 12px;
  font-weight: 600;
}
.ory-sdk-installs {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ory-sdk-installs code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  background: #f5f5f5;
  border: 1px solid #e5e5e5;
  border-radius: 6px;
  padding: 5px 10px;
  color: #000;
  display: block;
  overflow-wrap: break-word;
  word-break: break-word;
}
.ory-sdk-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.ory-sdk-badge {
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 700;
  background: #f0f0f0;
  border: 1px solid #d4d4d4;
  color: #333;
}

/* ---------- Protocol ---------- */
.ory-proto-grid {
  display: flex;
  flex-direction: column;
  gap: 28px;
}
.ory-proto-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ory-proto-group-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #555;
  margin-bottom: 4px;
}
.ory-proto-row {
  display: flex;
  align-items: baseline;
  gap: 16px;
  padding: 8px 14px;
  border-radius: 8px;
  background: #fafafa;
  border: 1px solid #e5e5e5;
  flex-wrap: wrap;
}
.ory-proto-subject {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: #000;
  background: #f0f0f0;
  border: 1px solid #d4d4d4;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
  white-space: nowrap;
}
.ory-proto-desc {
  font-size: 13px;
  color: #666;
  flex: 1;
}

/* ---------- CTA ---------- */
.ory-cta-section {
  background: #f5f5f5;
}
.ory-cta {
  text-align: center;
  max-width: 680px;
  margin: 0 auto;
}
.ory-cta-title {
  font-size: 28px;
  font-weight: 700;
  color: #000;
  margin: 0 0 12px;
}
.ory-cta-desc {
  font-size: 15px;
  color: #666;
  margin: 0 0 24px;
}
.ory-cta-code {
  text-align: left;
  margin-bottom: 28px;
}
.ory-cta-links {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

@keyframes ory-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(0.7);
  }
}

/* ---------- Responsive ---------- */
@media (max-width: 768px) {
  .ory-sdk-cards {
    grid-template-columns: 1fr;
  }
  .ory-hero {
    padding: 72px 20px 60px;
  }
  .ory-problem,
  .ory-primitives,
  .ory-scenarios {
    grid-template-columns: 1fr;
  }
  .ory-primitive:nth-child(4):nth-last-child(2) {
    grid-column: auto;
  }
  .ory-section {
    padding: 48px 20px;
  }
}
</style>

<style>
/* ========== Dark Mode Overrides (unscoped so html.dark selector works) ========== */
html.dark .ory-page {
  background: #0a0a0a;
  color: #e5e5e5;
}

/* Hero */
html.dark .ory-badge {
  background: #1a1a1a;
  border-color: #333;
  color: #999;
}
html.dark .ory-badge-dot { background: #fff; }
html.dark .ory-title-name { color: #fff; }
html.dark .ory-title-sub { color: #999; }
html.dark .ory-hero-desc { color: #aaa; }
html.dark .ory-hero-note { color: #666; }

/* Buttons */
html.dark .ory-btn-primary {
  background: #fff;
  color: #000;
}
html.dark .ory-btn-primary:hover { opacity: 0.8; }
html.dark .ory-btn-ghost {
  border-color: #333;
  color: #ccc;
}
html.dark .ory-btn-ghost:hover {
  border-color: #fff;
  background: #1a1a1a;
}

/* Sections */
html.dark .ory-section-tag {
  background: #1a1a1a;
  border-color: #333;
  color: #999;
}
html.dark .ory-section-title { color: #fff; }
html.dark .ory-section-desc { color: #999; }

/* Problem / Compare */
html.dark .ory-problem-text p { color: #999; }
html.dark .ory-problem-item strong { color: #fff; }
html.dark .ory-solution-line { color: #fff !important; }
html.dark .ory-compare-item {
  border-color: #333;
}
html.dark .ory-compare-bad { background: #111; }
html.dark .ory-compare-good {
  background: #1a1a1a;
  border-color: #444;
}
html.dark .ory-compare-label { color: #666; }
html.dark .ory-compare-row { color: #999; }
html.dark .ory-compare-icon { color: #555; }
html.dark .ory-icon-ok { color: #fff; }

/* Flow */
html.dark .ory-flow-img { border-color: #333; }

/* Primitives (core capabilities) */
html.dark .ory-primitives-section { background: #111; }
html.dark .ory-primitive {
  background: #1a1a1a;
  border-color: #333;
}
html.dark .ory-primitive:hover {
  border-color: #fff;
  box-shadow: 0 4px 16px rgba(255,255,255,.06);
}
html.dark .ory-primitive-title { color: #fff; }
html.dark .ory-primitive-subtitle { color: #666; }

/* Code blocks */
html.dark .ory-code {
  background: #111;
  border-color: #333;
  color: #ccc;
}

/* Scenarios */
html.dark .ory-scenario {
  background: #111;
  border-color: #333;
}
html.dark .ory-scenario-num { color: #333; }
html.dark .ory-scenario-title { color: #fff; }
html.dark .ory-scenario-desc { color: #999; }

/* SDK cards */
html.dark .ory-sdk-section { background: #111; }
html.dark .ory-sdk-card {
  background: #1a1a1a;
  border-color: #333;
}
html.dark .ory-sdk-card-featured { border-color: #fff; }
html.dark .ory-sdk-card-title { color: #fff; }
html.dark .ory-sdk-card-desc { color: #999; }
html.dark .ory-sdk-installs code {
  background: #111;
  border-color: #333;
  color: #ccc;
}
html.dark .ory-sdk-badge {
  background: #222;
  border-color: #333;
  color: #ccc;
}

/* Protocol */
html.dark .ory-proto-group-label { color: #999; }
html.dark .ory-proto-row {
  background: #111;
  border-color: #333;
}
html.dark .ory-proto-subject {
  background: #222;
  border-color: #333;
  color: #fff;
}
html.dark .ory-proto-desc { color: #999; }

/* CTA */
html.dark .ory-cta-section { background: #111; }
html.dark .ory-cta-title { color: #fff; }
html.dark .ory-cta-desc { color: #999; }
</style>
