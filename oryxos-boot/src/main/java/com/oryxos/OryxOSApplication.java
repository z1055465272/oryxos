package com.oryxos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OryxOS Spring Boot 启动入口。
 *
 * <p>单二进制部署：{@code mvn clean package} 后 {@code java -jar oryxos-boot-*.jar} 启动。
 */
@SpringBootApplication
public class OryxOSApplication {

  public static void main(String[] args) {
    SpringApplication.run(OryxOSApplication.class, args);
  }
}
