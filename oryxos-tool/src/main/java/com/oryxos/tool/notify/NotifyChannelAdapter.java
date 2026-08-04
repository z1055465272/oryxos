package com.oryxos.tool.notify;

/**
 * 通知渠道适配器：把一段内容发送到指定通知目标.
 *
 * <p>接口签名不绑定任何具体渠道类型（webhook、企业微信、飞书等）， 未来新增渠道只需新增实现类，不修改接口和调用方.
 *
 * <p>{@link #supports} 用于路由层（{@link NotifyChannelAdapterRouter}）判断某个 URL 是否由该适配器处理； 通用 webhook
 * 适配器恒返回 true 作为兜底，且必须排在其他平台适配器之后.
 */
public interface NotifyChannelAdapter {

  /** 该适配器是否处理此 URL；通用 webhook 恒 true 作兜底. */
  boolean supports(String url);

  void send(NotifyTarget target, String content);
}
