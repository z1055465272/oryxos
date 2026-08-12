package com.oryxos.tool.sandbox;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** HTTP 域名白名单配置：http.allowed_domains，为空 = 不允许任何域名（不是"不校验"）. */
@ConfigurationProperties(prefix = "http")
public record HttpSandboxProperties(List<String> allowedDomains) {}
