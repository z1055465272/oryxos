package com.oryxos.tool.notify;

/**
 * 通知渠道适配器：把一段内容发送到指定通知目标.
 *
 * <p>接口签名不绑定任何具体渠道类型（webhook、企业微信、飞书等）， 未来新增渠道只需新增实现类，不修改接口和调用方.
 */
public interface NotifyChannelAdapter {
  void send(NotifyTarget target, String content);
}
