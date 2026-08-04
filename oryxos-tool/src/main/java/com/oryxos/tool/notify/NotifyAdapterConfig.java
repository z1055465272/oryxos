package com.oryxos.tool.notify;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 通知适配器共享的同步 HTTP 客户端.
 *
 * <p>Spring Boot 只自动装配 {@code RestClient.Builder}，不装配 {@code RestClient} 实例； 四个适配器（Webhook / 企业微信 /
 * 飞书 / 钉钉）都依赖它做同步 POST（原则 VII），在此集中定义.
 */
@Configuration
public class NotifyAdapterConfig {

  @Bean
  RestClient restClient() {
    return RestClient.create();
  }
}
