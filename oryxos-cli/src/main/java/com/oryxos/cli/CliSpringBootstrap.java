package com.oryxos.cli;

import com.oryxos.core.AgentService;
import com.oryxos.core.ContextLoader;
import com.oryxos.core.OryxTool;
import com.oryxos.core.ProfileLoader;
import com.oryxos.core.ProfileRegistry;
import com.oryxos.core.PromptBuilder;
import com.oryxos.core.ReActLoop;
import com.oryxos.core.SessionManager;
import com.oryxos.core.ToolExecutor;
import com.oryxos.core.ToolInvocationStore;
import com.oryxos.core.ToolRegistry;
import com.oryxos.provider.DefaultProviderService;
import com.oryxos.provider.OryxOsProperties;
import com.oryxos.provider.ProviderNotFoundException;
import com.oryxos.provider.ToolSchemaAdapter;
import com.oryxos.storage.JpaSessionManager;
import com.oryxos.storage.JpaToolInvocationStore;
import com.oryxos.storage.LlmCallRepository;
import com.oryxos.storage.SessionRepository;
import com.oryxos.storage.ToolInvocationRepository;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 重命令（chat/serve/gateway）共用的 Spring 上下文启动器.
 *
 * <p><strong>模块扫描范围显式声明（课件坑 4）</strong>：CLI 模块在 {@code com.oryxos.cli}、持久化模块在 {@code
 * com.oryxos.storage}，不同 Java 包。{@code @SpringBootApplication(scanBasePackages=...)}
 * 只管组件扫描，不会带动自动配置的 {@code @EnableJpaRepositories}/{@code @EntityScan} 跟着扫到别的模块——不显式声明 basePackages
 * 会得到 "Found 0 JPA repository interfaces"、审计写不进去直接报错退出.
 *
 * <p>把 16/17 节的 POJO 引擎（ProviderService/ReActLoop/PromptBuilder/ToolExecutor/AgentService 等）以
 * {@code @Bean} 方法装配成上下文；Provider 走显式 {@code Map<String, ChatModel>} 映射（宪法 III），不靠类型扫描。 数据源与 JPA
 * 配置来自 classpath 根 {@code application.yaml}（运行 fat JAR 时由 boot 模块提供）.
 */
@Configuration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "com.oryxos.storage")
@EntityScan(basePackages = "com.oryxos.storage")
@EnableConfigurationProperties(OryxOsProperties.class)
public class CliSpringBootstrap {

  private static final Logger log = LoggerFactory.getLogger(CliSpringBootstrap.class);

  private static final Path WORKSPACE_DIR = Path.of(".oryxos");

  @Bean
  JpaSessionManager jpaSessionManager(SessionRepository sessionRepository) {
    return new JpaSessionManager(sessionRepository);
  }

  @Bean
  JpaToolInvocationStore jpaToolInvocationStore(ToolInvocationRepository toolInvocationRepository) {
    return new JpaToolInvocationStore(toolInvocationRepository);
  }

  /** 工具注册表：第 20 节在 oryxos-tool 落地；本节先给空实现让 ReAct 循环可装配（无内置工具）. */
  @Bean
  ToolRegistry toolRegistry() {
    return new ToolRegistry() {
      @Override
      public Optional<OryxTool> get(String name) {
        return Optional.empty();
      }

      @Override
      public Collection<OryxTool> listAll() {
        return java.util.Collections.emptyList();
      }
    };
  }

  @Bean
  ProfileRegistry profileRegistry() {
    return new ProfileRegistry();
  }

  /** 启动时按配置的 provider 名校验集，扫 .oryxos/profiles/ 加载 Profile 注册进注册表（无目录则 WARN 跳过）. */
  @Bean
  ProfileLoader profileLoader(OryxOsProperties oryxOsProperties, ProfileRegistry profileRegistry) {
    Set<String> validProviderNames =
        oryxOsProperties.getProviders().stream()
            .map(OryxOsProperties.ProviderConfig::getName)
            .collect(Collectors.toSet());
    ProfileLoader loader = new ProfileLoader(WORKSPACE_DIR.resolve("profiles"), validProviderNames);
    for (com.oryxos.core.Profile profile : loader.loadAll()) {
      profileRegistry.register(profile);
      log.info("Registered profile: {}", profile.name());
    }
    return loader;
  }

  @Bean
  ContextLoader contextLoader() {
    return new ContextLoader(WORKSPACE_DIR);
  }

  @Bean
  PromptBuilder promptBuilder(ContextLoader contextLoader, ToolRegistry toolRegistry) {
    return new PromptBuilder(contextLoader, toolRegistry);
  }

  @Bean
  ToolExecutor toolExecutor(ToolRegistry toolRegistry, ToolInvocationStore toolInvocationStore) {
    return new ToolExecutor(toolRegistry, toolInvocationStore);
  }

  @Bean
  ToolSchemaAdapter toolSchemaAdapter() {
    return new ToolSchemaAdapter();
  }

  @Bean
  DefaultProviderService defaultProviderService(
      OryxOsProperties oryxOsProperties,
      ToolSchemaAdapter toolSchemaAdapter,
      LlmCallRepository llmCallRepository) {
    Map<String, ChatModel> providerMap = buildProviderMap(oryxOsProperties);
    return new DefaultProviderService(providerMap, toolSchemaAdapter, llmCallRepository);
  }

  @Bean
  ReActLoop reActLoop(
      DefaultProviderService defaultProviderService,
      PromptBuilder promptBuilder,
      ToolExecutor toolExecutor) {
    return new ReActLoop(defaultProviderService, promptBuilder, toolExecutor);
  }

  @Bean
  AgentService agentService(
      ProfileRegistry profileRegistry, ReActLoop reActLoop, SessionManager sessionManager) {
    return new AgentService(profileRegistry, reActLoop, sessionManager);
  }

  /** 按配置的 provider 列表构建显式映射表（宪法 III：不靠类型扫描区分 Provider）. */
  private Map<String, ChatModel> buildProviderMap(OryxOsProperties properties) {
    LinkedHashMap<String, ChatModel> map = new LinkedHashMap<>();
    for (OryxOsProperties.ProviderConfig config : properties.getProviders()) {
      map.put(config.getName(), buildChatModel(config));
    }
    return Map.copyOf(map);
  }

  private ChatModel buildChatModel(OryxOsProperties.ProviderConfig config) {
    String apiKey = config.getApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      throw new ProviderNotFoundException(config.getName() + " api-key not configured");
    }
    OpenAiApi api =
        OpenAiApi.builder()
            .baseUrl(
                config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.openai.com/v1")
            .apiKey(apiKey)
            .build();
    return OpenAiChatModel.builder()
        .openAiApi(api)
        .defaultOptions(OpenAiChatOptions.builder().model("deepseek-chat").build())
        .build();
  }
}
