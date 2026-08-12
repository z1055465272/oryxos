package com.oryxos.tool.sandbox;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shell 命令白名单配置：shell.allowed_commands，为空 = 不允许任何命令（不是"不校验"）. */
@ConfigurationProperties(prefix = "shell")
public record ShellSandboxProperties(List<String> allowedCommands) {}
