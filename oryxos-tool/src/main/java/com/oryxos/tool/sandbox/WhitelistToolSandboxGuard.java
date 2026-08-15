package com.oryxos.tool.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.ToolCall;
import com.oryxos.core.ToolSandboxGuard;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 全局工具沙箱守卫：ToolExecutor 执行任意工具前调用，按工具名映射动作类型，从 JSON 参数取目标过白名单校验.
 *
 * <p>工具名→动作类型映射与内置 {@code @Tool(name=...)} 契约一致：read_file/write_file/list_dir 取 {@code path}、shell
 * 取 {@code command}、http_get/http_post 取 {@code url}。save_memory/recall_memory 是本地文件操作（白名单内
 * .oryxos/memory/）、notify 由 NotifyTools 内部用解析后的 url 自校验，均不在本守卫 管辖范围（返回 null 放行，由各工具自身兜底）。参数解析失败（非法
 * JSON / 缺字段）按拒绝处理，防绕过。
 */
@Component
public class WhitelistToolSandboxGuard implements ToolSandboxGuard {

  /** 工具名 → 动作类型 + 目标参数名. */
  private static final Map<String, TargetRef> TARGETS =
      Map.of(
          "read_file", new TargetRef(ActionType.FILE_READ, "path"),
          "write_file", new TargetRef(ActionType.FILE_WRITE, "path"),
          "list_dir", new TargetRef(ActionType.FILE_READ, "path"),
          "shell", new TargetRef(ActionType.SHELL, "command"),
          "http_get", new TargetRef(ActionType.HTTP_REQUEST, "url"),
          "http_post", new TargetRef(ActionType.HTTP_REQUEST, "url"));

  private final WhitelistSandbox sandbox;
  private final ObjectMapper objectMapper;

  public WhitelistToolSandboxGuard(WhitelistSandbox sandbox) {
    this(sandbox, new ObjectMapper());
  }

  WhitelistToolSandboxGuard(WhitelistSandbox sandbox, ObjectMapper objectMapper) {
    this.sandbox = sandbox;
    this.objectMapper = objectMapper;
  }

  @Override
  public String check(ToolCall toolCall) {
    TargetRef ref = TARGETS.get(toolCall.name());
    if (ref == null) {
      return null;
    }
    String target = extractTarget(toolCall.arguments(), ref);
    if (target == null) {
      return "参数无法解析或缺少目标字段: " + toolCall.name();
    }
    try {
      sandbox.enforce(new SandboxAction(ref.type(), target));
      return null;
    } catch (SandboxViolationException e) {
      return e.getMessage();
    }
  }

  /** 从工具参数 JSON 提取目标字段；非法 JSON 或缺字段返回 null. */
  private String extractTarget(String arguments, TargetRef ref) {
    try {
      JsonNode node = objectMapper.readTree(arguments);
      JsonNode value = node.get(ref.argName());
      return value != null && value.isTextual() ? value.asText() : null;
    } catch (Exception e) {
      return null;
    }
  }

  /** 动作类型 + 目标参数名的配对. */
  private record TargetRef(ActionType type, String argName) {}
}
