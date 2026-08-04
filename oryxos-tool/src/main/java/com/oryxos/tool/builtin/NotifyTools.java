package com.oryxos.tool.builtin;

import com.oryxos.core.NotifyChannelConfig;
import com.oryxos.core.ProfileContext;
import com.oryxos.core.ToolResult;
import com.oryxos.tool.notify.NotifyChannelAdapterRouter;
import com.oryxos.tool.notify.NotifyTarget;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.util.HashMap;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Agent 可见的统一通知入口，串联渠道解析 → 安全校验 → 适配器发送三步.
 *
 * <p>完整 {@code @Tool} 注册接线依赖 20 节 ToolRegistry；本节先定义注解和方法， NotifyToolsTest 用 mock 独立验证三步逻辑.
 */
@Component
public class NotifyTools {

  private final Sandbox sandbox;
  private final NotifyChannelAdapterRouter router;
  private final ProfileContext profileContext;

  /** DI constructor. */
  public NotifyTools(
      Sandbox sandbox, NotifyChannelAdapterRouter router, ProfileContext profileContext) {
    this.sandbox = sandbox;
    this.router = router;
    this.profileContext = profileContext;
  }

  /** Notify tool: resolve channel → sandbox enforce → router send. */
  @Tool(description = "把一条消息推送到当前 Agent 配置好的通知渠道")
  public ToolResult notify(String content, String channel) {
    NotifyChannelConfig channelConfig = profileContext.resolveNotifyChannel(channel);
    String url = channelConfig.url();
    sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url));
    router.send(new NotifyTarget(channelConfig.type(), toConfigMap(channelConfig)), content);
    return ToolResult.ok("已推送");
  }

  private static Map<String, String> toConfigMap(NotifyChannelConfig config) {
    Map<String, String> cfg = new HashMap<>();
    cfg.put("url", config.url());
    if (config.secret() != null) {
      cfg.put("secret", config.secret());
    }
    return cfg;
  }
}
