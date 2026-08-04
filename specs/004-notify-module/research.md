# Research: Notify 模块

**Feature**: Notify 模块 — Agent 主动推送出口 | **Date**: 2026-08-04

## Decision: Webhook 适配器使用 Spring RestClient（非 RestTemplate / WebClient）

- **Decision**: 使用 Spring 6.1+ 的 `RestClient` 接口
- **Rationale**: 课件代码明确定义 `RestClient restClient` 构造函数注入；RestClient 是 Spring Boot 3.x 推荐的同步 HTTP 客户端，与原则 VII（同步执行）一致
- **Alternatives considered**: RestTemplate（已进入维护模式）、WebClient（Reactive，违反原则 VII）

## Decision: NotifyChannelConfig 的 type 字段作为渠道标识

- **Decision**: `type` 字段（如 "webhook"）同时作为渠道类型和 `channel` 参数的匹配键
- **Rationale**: clarify 阶段用户确认选择 Option B；简化模型，不引入额外 `name` 字段
- **Alternatives considered**: 新增独立 `name` 字段（Option A）；按列表索引引用（Option C）

## Decision: Profile.notifyChannels 改造不回退兼容

- **Decision**: 直接将 `List<String> notifyChannels` 改为 `List<NotifyChannelConfig> notifyChannels`，所有引用处同步更新
- **Rationale**: 前序节 ProfileLoaderTest 的 `notify_channels` 测试数据格式同步改为结构化列表；YAML 配置文件中的 `notify_channels: ["webhook-ops"]` 改为 `notify_channels: [{type: "webhook", url: "..."}]`
- **Alternatives considered**: 新增字段保留旧字段（增加复杂度，无实际价值——核心阶段尚未发布）

## Decision: Sandbox 占位实现 — NoOpSandbox

- **Decision**: 创建 `Sandbox` 接口 + `NoOpSandbox` @Component 实现（`enforce()` 空方法，默认放行），加上 `SandboxAction` record + `ActionType` enum
- **Rationale**: 课件明确"先接进去，具体校验留给 23/24 节"；NoOp 实现让 NotifyTools 的 DI 链完整可测，24 节替换为真实实现即可
- **Alternatives considered**: 不创建 Sandbox 接口，NotifyTools 中直接注释掉校验行（破坏"调用链就位"的目标）

## Decision: ProfileContext 保持 ThreadLocal + 新增 @Component 包装

- **Decision**: `ProfileContext` 保留 static ThreadLocal 机制，新增 `@Component` 注解 + 实例方法 `resolveNotifyChannel(String channel)`
- **Rationale**: 课件代码用构造函数注入 `ProfileContext`；17 节 ThreadLocal 机制正确且在虚拟线程下天然隔离；实例方法内部委托 static `current()` 读取 ThreadLocal
- **Alternatives considered**: 完全重构为纯实例（改动范围太大，影响 AgentService 等调用方）

## Decision: 测试使用 okhttp3 MockWebServer

- **Decision**: 使用 `com.squareup.okhttp3:mockwebserver3` 做 webhook 适配器测试
- **Rationale**: 课件验收 harness 明确"用 MockWebServer 在本地起一个假 webhook"；纯单元测试，无外部网络依赖；须确认 BOM 或显式依赖已包含此库
- **Alternatives considered**: WireMock（更重量级，需额外 JUnit 扩展）；Spring MockRestServiceServer（仅适用于 RestTemplate，不适用 RestClient）
