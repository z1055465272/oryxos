package com.oryxos.provider;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 全局 Provider 配置：application.yaml 中 oryxos.providers 列表. */
@ConfigurationProperties(prefix = "oryxos")
public class OryxOsProperties {

  private List<ProviderConfig> providers = new ArrayList<>();

  public List<ProviderConfig> getProviders() {
    return new ArrayList<>(providers);
  }

  public void setProviders(List<ProviderConfig> providers) {
    this.providers = List.copyOf(providers);
  }

  /** 单个 Provider 的配置：name、baseUrl、apiKey. */
  public static class ProviderConfig {
    private String name;
    private String baseUrl;
    private String apiKey;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }
  }
}
