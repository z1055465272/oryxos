package com.oryxos.tool.sandbox;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 文件路径白名单配置：file.allowed_paths，为空 = 不允许任何路径（不是"不校验"）. */
@ConfigurationProperties(prefix = "file")
public record FileSandboxProperties(List<String> allowedPaths) {}
