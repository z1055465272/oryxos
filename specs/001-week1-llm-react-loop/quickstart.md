# 快速验证指南：第一周 Demo 一（查天气穿衣）

**日期**: 2026-08-02

**用途**: 端到端验证第一周交付（对接 LLM + ReAct 循环 + HTTP 天气 Tool）。完整实现细节见 `contracts/` 与 `data-model.md`。

---

## 前置条件

- **JDK 21**（必选，宪法原则：Java 21 运行时代）
- **Maven 3.9+**
- 至少一家 LLM 服务商密钥（DeepSeek 或 Kimi），以环境变量注入
- 网络可达 `wttr.in`（天气数据源，见 [research.md](research.md)）

## 环境准备

```bash
# Windows (bash / cmd)
export DEEPSEEK_API_KEY="sk-xxx"
# 或 Kimi
export KIMI_API_KEY="sk-xxx"
```

## 构建与启动

```bash
mvn clean package -DskipTests        # 全模块编译打包
java -jar oryxos-boot/target/oryxos-boot-*.jar init   # 初始化 .oryxos/ 工作区
java -jar oryxos-boot/target/oryxos-boot-*.jar chat --profile default
```

## 验证场景

### 场景 A：无工具直接回答

```
你> 你好
Oryx> 你好！有什么可以帮你？
```

**预期**: 直接回复，不触发任何工具调用（`http_get` 未被调用）。

### 场景 B（验收 Demo 一）：查天气穿衣

```
你> 查一下北京天气并告诉我穿什么
```

**预期**：
1. Agent 调用 `http_get`（URL 指向 wttr.in，域名白名单放行）
2. 读取返回的天气 JSON
3. 基于温度/天气状况给出穿衣建议
4. 完整对话按序累积：`user → assistant(tool_call) → tool(result) → assistant(text)`

### 场景 C：多轮上下文

```
你> 查一下北京天气并告诉我穿什么
你> 那上海呢？
```

**预期**: 第二问基于第一问的上下文与工具调用模式，再次查上海天气并作答。

### 场景 D：沙箱边界（负向）

通过配置 `http.allowed_domains` 仅含 `wttr.in`，向 Agent 索要访问其他域名的操作。

**预期**: 请求被 `WhitelistSandbox` 拒绝，工具调用 `success=false`，错误信息进审计记录，Agent 基于失败结果降级回答。

## 验收核对表（对应 spec Success Criteria）

- [ ] **SC-001**: 配置密钥后在 5 分钟内完成首次对话
- [ ] **SC-003**: 查天气场景 2 分钟内完成"调工具→看结果→给建议"全流程
- [ ] **SC-004**: 对话日志按序累积，工具调用轮数未超上限（默认 10）
- [ ] **SC-005**: 无工具消息直接回答，不触发工具
- [ ] **SC-006**: Demo 一跑通 = 本周交付完成

## 审计核对（宪法原则五）

Demo 过程中每次工具/模型调用均可核对：
- `LlmCallRecorder`：provider、model、token 用量、耗时
- `ToolInvocationRecorder`：tool_name、input、result、success、error、耗时

（本周为内存实现，供验收核对；第四周落 SQLite 后可跨重启查询。）
