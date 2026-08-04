# Contracts: Notify 模块

**Feature**: Notify 模块 — Agent 主动推送出口 | **Date**: 2026-08-04

## 公共接口契约

### 1. NotifyChannelAdapter（oryxos-tool/notify）

```java
package com.oryxos.tool.notify;

/**
 * 通知渠道适配器：表达"把一段内容送到某个通知目标"的意图。
 * 接口签名不绑定任何具体渠道类型，未来新增渠道只需新增实现类。
 */
public interface NotifyChannelAdapter {
    void send(NotifyTarget target, String content);
}
```

### 2. NotifyTarget（oryxos-tool/notify）

```java
package com.oryxos.tool.notify;

/**
 * 通知目标值对象：渠道类型 + 配置键值对。
 * 由 NotifyTools 从 NotifyChannelConfig 转换而来，适配器按 channelType 解释 config。
 */
public record NotifyTarget(String channelType, Map<String, String> config) {}
```

### 3. NotifyChannelConfig（oryxos-core）

```java
package com.oryxos.core;

/**
 * Agent 通知渠道配置（Profile.notifyChannels 列表的单项）。
 * type 同时作为通知渠道的匹配键；url 支持 ${ENV_VAR} 占位符。
 */
public record NotifyChannelConfig(String type, String url) {}
```

### 4. Sandbox（oryxos-tool/sandbox）

```java
package com.oryxos.tool.sandbox;

/** 沙箱安全校验接口。核心阶段为 NoOp 占位实现，23/24 节替换为真实白名单校验。 */
public interface Sandbox {
    void enforce(SandboxAction action);
}
```

### 5. NotifyTools（oryxos-tool/builtin）

```java
package com.oryxos.tool.builtin;

/**
 * @Tool 注解的 notify 方法签名契约：
 *
 * {@code @Tool(description = "把一条消息推送到当前 Agent 配置好的通知渠道")
 * public ToolResult notify(String content, @Nullable String channel)}
 *
 * 三步执行顺序（不可变）：
 *   1. ProfileContext.current().notifyChannels() 按 channel 匹配
 *   2. Sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url))
 *   3. NotifyChannelAdapter.send(new NotifyTarget(type, Map.of("url", url)), content)
 */
```

### 6. ProfileContext 新增方法（oryxos-core）

```java
package com.oryxos.core;

/**
 * 新增实例方法（原有 static ThreadLocal 机制保留不动）：
 *
 * public NotifyChannelConfig resolveNotifyChannel(String channel) {
 *     Profile p = current();
 *     if (p == null || p.notifyChannels().isEmpty()) {
 *         throw new IllegalStateException("未配置通知渠道");
 *     }
 *     if (channel == null || channel.isBlank()) {
 *         return p.notifyChannels().get(0);
 *     }
 *     return p.notifyChannels().stream()
 *         .filter(c -> c.type().equals(channel))
 *         .findFirst()
 *         .orElseThrow(() -> new IllegalArgumentException("未找到渠道: " + channel));
 * }
 */
```

## 前序节接口变更（向下兼容）

### Profile.notifyChannels（oryxos-core）

```java
// Before (16 节):
List<String> notifyChannels,

// After (19 节):
List<NotifyChannelConfig> notifyChannels,
```

关联影响：
- `ProfileLoader.parseNotifyChannels()` 从 `getStringList()` 改为解析 `List<Map>` 结构化数据
- `ProfileLoaderTest` 测试数据同步更新
