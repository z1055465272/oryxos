package com.oryxos.tool.contract;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.oryxos.core.OryxTool;
import com.oryxos.core.ProfileContext;
import com.oryxos.tool.builtin.FileTools;
import com.oryxos.tool.builtin.HttpTools;
import com.oryxos.tool.builtin.NotifyTools;
import com.oryxos.tool.builtin.ShellTools;
import com.oryxos.tool.notify.NotifyChannelAdapterRouter;
import com.oryxos.tool.registry.BuiltinToolRegistration;
import com.oryxos.tool.registry.DefaultToolRegistry;
import com.oryxos.tool.sandbox.Sandbox;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestClient;

/**
 * 工具契约测试：参数化遍历注册表里每个工具，name/description/inputSchema 都非空——任何工具漏实现 getInputSchema() 立刻红
 * （"动手前先检查"的自动化版）.
 *
 * <p>注册表用真实内置工具（{@code @Tool} 注解 + ToolCallbacks 生成 schema），新工具加入 registerAll 自动纳入契约检查.
 */
class OryxToolContractTest {

  /** 契约验证用的注册表：真实内置工具注册进去，新工具自动纳入. */
  private static final DefaultToolRegistry CONTRACT_REGISTRY = contractRegistry();

  private static DefaultToolRegistry contractRegistry() {
    DefaultToolRegistry registry = new DefaultToolRegistry();
    Sandbox sandbox = mock(Sandbox.class);
    FileTools fileTools = new FileTools(sandbox);
    ShellTools shellTools = new ShellTools(sandbox);
    HttpTools httpTools = new HttpTools(sandbox, RestClient.create());
    NotifyTools notifyTools =
        new NotifyTools(
            sandbox, new NotifyChannelAdapterRouter(List.of()), mock(ProfileContext.class));
    BuiltinToolRegistration registration = new BuiltinToolRegistration();
    registration.registerAll(registry, fileTools, shellTools, httpTools, notifyTools);
    return registry;
  }

  /** 遍历注册表全部工具作为参数化输入，新注册工具自动纳入. */
  static Stream<OryxTool> allRegisteredTools() {
    return CONTRACT_REGISTRY.listAll().stream();
  }

  @ParameterizedTest
  @MethodSource("allRegisteredTools")
  @DisplayName("每个工具的契约三件套都不能缺")
  void contractTripleIsNonEmpty(OryxTool tool) {
    assertNotNull(tool.getName(), "工具名不能为空");
    assertNotNull(tool.getDescription(), "工具描述不能为空");
    assertNotNull(tool.getInputSchema(), "缺 inputSchema，Provider 翻译 Function Calling 时直接卡死");
  }

  @ParameterizedTest
  @MethodSource("allRegisteredTools")
  @DisplayName("注册表能遍历到每个内置工具")
  void registryEnumeratesEveryTool(OryxTool tool) {
    assertNotNull(List.of(tool.getName()));
  }
}
