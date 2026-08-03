package com.oryxos.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent 的核心循环引擎（ReAct：Reason + Act）.
 *
 * <p>主循环手写、不使用 Spring AI 的 Agent 抽象：每轮 组装 Prompt → 经 {@link ProviderService} 调大模型 →
 * 判断是否有工具调用；无则返回最终答复，有则交给 {@link ToolExecutor} 集中执行并把结果回填会话， 直到无工具调用或达到最大轮数。每轮响应与工具结果都累积回
 * Session（可审计、下一轮接得上）.
 */
public class ReActLoop {

  private static final Logger log = LoggerFactory.getLogger(ReActLoop.class);

  /** 最大迭代轮数默认值（Profile.settings.maxIterations 未配置时）——防死循环兜底. */
  public static final int DEFAULT_MAX_ITERATIONS = 10;

  /** 转满最大轮数时的停止提示（课件约定字面量，测试据此断言）. */
  public static final String MAX_ITERATIONS_MESSAGE = "达到最大轮数，已停止";

  private final ProviderService providerService;
  private final PromptBuilder promptBuilder;
  private final ToolExecutor toolExecutor;

  /** 构建 ReAct 循环引擎. */
  public ReActLoop(
      ProviderService providerService, PromptBuilder promptBuilder, ToolExecutor toolExecutor) {
    this.providerService = providerService;
    this.promptBuilder = promptBuilder;
    this.toolExecutor = toolExecutor;
  }

  /**
   * 运行 ReAct 循环，返回最终响应文本.
   *
   * @param session 当前会话（累积对话历史与审计关联）
   * @param userMessage 用户新消息
   * @param profile Agent 配置（maxIterations / maxHistoryTurns）
   */
  public String run(Session session, String userMessage, Profile profile) {
    session.appendUserMessage(userMessage);
    int maxIterations = maxIterations(profile);
    for (int i = 0; i < maxIterations; i++) {
      Prompt prompt = promptBuilder.build(session, profile);
      Response resp = providerService.chat(session.id(), profile, prompt);
      session.appendAssistant(resp.text(), resp.toolCalls());
      if (!resp.hasToolCalls()) {
        return resp.text();
      }
      for (ToolCall call : resp.toolCalls()) {
        ToolResult result = toolExecutor.execute(session.id(), call);
        session.appendToolResult(call, result);
      }
    }
    return MAX_ITERATIONS_MESSAGE;
  }

  /** maxIterations 取值：Profile 配置优先，缺失取默认 10；非法（<=0）按 1 轮处理（至少执行一轮）. */
  private int maxIterations(Profile profile) {
    Integer configured = profile.settings() != null ? profile.settings().maxIterations() : null;
    if (configured == null) {
      return DEFAULT_MAX_ITERATIONS;
    }
    return Math.max(1, configured);
  }
}
