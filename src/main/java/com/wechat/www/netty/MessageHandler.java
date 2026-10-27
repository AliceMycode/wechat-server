package com.wechat.www.netty;

import com.wechat.www.constant.RedisKeyConstants;
import com.wechat.www.dto.MessageSendDto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 消息广播器（对照源项目 MessageHandler）
 * 多实例部署：sendMessage 不是直接推 Channel，而是 publish 到 Redis Topic；
 * 每台实例（包括自己）订阅到后，把消息推给"连在本机上的"接收人
 */
@Component
@Slf4j
public class MessageHandler {


  @Resource
  private RedissonClient redissonClient;

  @Resource
  private ChannelContextUtils channelContextUtils;

  /** 启动时订阅消息 Topic：收到广播就本地推送 */
  @PostConstruct
  public void initListener() {
    RTopic topic = redissonClient.getTopic(RedisKeyConstants.REDIS_KEY_MESSAGE_TOPIC);
    topic.addListener(MessageSendDto.class, (redisChannel, sendDto) -> {
      // 接收人ID就在 sendDto.contactId 里，推给本机登录的这个用户
      log.info("收到广播消息:{}", sendDto);
      channelContextUtils.sendMsg(sendDto, sendDto.getContactId());
    });
  }

  /** 发消息：广播给所有实例 */
  public void sendMessage(MessageSendDto<?> sendDto) {
    RTopic topic = redissonClient.getTopic(RedisKeyConstants.REDIS_KEY_MESSAGE_TOPIC);
    topic.publish(sendDto);
  }
}
