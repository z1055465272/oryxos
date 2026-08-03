package com.oryxos.provider;

/** Provider 名在显式映射表中找不到时抛出的异常. */
public class ProviderNotFoundException extends RuntimeException {

  public ProviderNotFoundException(String providerName) {
    super("Provider not found: " + providerName);
  }
}
