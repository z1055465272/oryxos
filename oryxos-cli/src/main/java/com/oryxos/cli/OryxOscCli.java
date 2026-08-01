package com.oryxos.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * OryxOS 命令行入口（Picocli），整个 OryxOS 的 main 函数。
 * <p>
 * 裸命令（无子命令）时打印版本信息；后续按 12 个子命令分发
 * （init / status / chat / serve / gateway / profile / provider / tool / session）。
 * 需要 LLM 调用的子命令（chat / serve / gateway）内部再启动 Spring 上下文，
 * 不需要 Spring 的命令（init / profile list）直接走文件操作，保证启动快。
 */
@Command(
        name = "oryxos",
        description = "OryxOS —— 企业私有可审计的 Agent OS 统一底座",
        mixinStandardHelpOptions = true,
        versionProvider = OryxOscCli.VersionProvider.class
)
public class OryxOscCli implements Runnable {

    @Override
    public void run() {
        // 裸命令：打印版本信息。显式 UTF-8 编码输出，避免 Windows 默认控制台编码导致中文乱码
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        new CommandLine(this).printVersionHelp(out);
        out.flush();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OryxOscCli()).execute(args);
        System.exit(exitCode);
    }

    /**
     * 版本信息：优先读 jar manifest 的 Implementation-Version（Maven 打包自动写入），
     * 读不到时回退到与 pom 一致的常量。
     */
    static class VersionProvider implements IVersionProvider {

        private static final String FALLBACK_VERSION = "1.0.0-SNAPSHOT";

        @Override
        public String[] getVersion() {
            String v = OryxOscCli.class.getPackage().getImplementationVersion();
            if (v == null || v.isBlank()) {
                v = FALLBACK_VERSION;
            }
            return new String[]{
                    "oryxos " + v,
                    "企业私有可审计的 Agent OS 统一底座",
                    "JDK 21 + Spring Boot 3.x + Spring AI + 自实现 ReAct loop + SQLite + Picocli"
            };
        }
    }
}
