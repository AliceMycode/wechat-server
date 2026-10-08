package com.wechat.www.redis;

import com.wechat.www.constant.RedisKeyConstants;
import com.wechat.www.dto.SysSettingDto;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.utils.StringTools;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 业务组件：集中封装"登录态、心跳、联系人/会话缓存、系统设置"
 * （对应源项目 RedisComponet）
 */
@Component
public class RedisComponet {

  @Resource
  private RedisUtils redisUtils;

  // ============ 登录 token ============

  /** 根据 token 取登录用户信息 */
  public TokenUserInfoDto getTokenUserInfoDto(String token) {
    return (TokenUserInfoDto) redisUtils.get(RedisKeyConstants.REDIS_KEY_WS_TOKEN + token);
  }

  /** 根据用户ID取登录信息（先查 userId→token，再查 token→信息） */
  public TokenUserInfoDto getTokenUserInfoDtoByUserId(String userId) {
    String token = (String) redisUtils.get(RedisKeyConstants.REDIS_KEY_WS_TOKEN_USERID + userId);
    return getTokenUserInfoDto(token);
  }

  /**
   * 保存登录态，存两份：
   * 1) token → 用户信息（鉴权用）
   * 2) userId → token（按用户查登录态、踢人用）
   * 有效期 2 天
   */
  public void saveTokenUserInfoDto(TokenUserInfoDto tokenUserInfoDto) {
    redisUtils.setex(RedisKeyConstants.REDIS_KEY_WS_TOKEN + tokenUserInfoDto.getToken(),
      tokenUserInfoDto, RedisKeyConstants.REDIS_KEY_TOKEN_EXPIRES);
    redisUtils.setex(RedisKeyConstants.REDIS_KEY_WS_TOKEN_USERID + tokenUserInfoDto.getUserId(),
      tokenUserInfoDto.getToken(), RedisKeyConstants.REDIS_KEY_TOKEN_EXPIRES);
  }

  /** 按用户ID清除登录态（登出/踢人） */
  public void cleanUserTokenByUserId(String userId) {
    String token = (String) redisUtils.get(RedisKeyConstants.REDIS_KEY_WS_TOKEN_USERID + userId);
    if (!StringTools.isEmpty(token)) {
      redisUtils.delete(RedisKeyConstants.REDIS_KEY_WS_TOKEN + token);
      redisUtils.delete(RedisKeyConstants.REDIS_KEY_WS_TOKEN_USERID + userId);
    }
  }

  // ============ 心跳（在线判断） ============

  /** 保存心跳：值为当前时间戳，6 秒过期（WS 每 6 秒续约） */
  public void saveUserHeartBeat(String userId) {
    redisUtils.setex(RedisKeyConstants.REDIS_KEY_WS_USER_HEART_BEAT + userId,
      System.currentTimeMillis(), RedisKeyConstants.REDIS_KEY_EXPIRES_HEART_BEAT);
  }

  /** 删除心跳 */
  public void removeUserHeartBeat(String userId) {
    redisUtils.delete(RedisKeyConstants.REDIS_KEY_WS_USER_HEART_BEAT + userId);
  }

  /** 取心跳时间戳，存在说明在线 */
  public Long getUserHeartBeat(String userId) {
    return (Long) redisUtils.get(RedisKeyConstants.REDIS_KEY_WS_USER_HEART_BEAT + userId);
  }

  // ============ 联系人缓存（Redis List） ============

  /** 取联系人ID列表 */
  public List<String> getUserContactList(String userId) {
    List<Object> raw = redisUtils.getQueueList(RedisKeyConstants.REDIS_KEY_USER_CONTACT + userId);
    @SuppressWarnings("unchecked")
    List<String> list = (List<String>) (List<?>) raw;
    return list;
  }

  /** 添加一个联系人（去重，不存在才加） */
  public void addUserContact(String userId, String contactId) {
    List<Object> contactList = redisUtils.getQueueList(RedisKeyConstants.REDIS_KEY_USER_CONTACT + userId);
    if (!contactList.contains(contactId)) {
      redisUtils.lpush(RedisKeyConstants.REDIS_KEY_USER_CONTACT + userId,
        contactId, RedisKeyConstants.REDIS_KEY_TOKEN_EXPIRES);
    }
  }

  /** 清空联系人缓存 */
  public void cleanUserContact(String userId) {
    redisUtils.delete(RedisKeyConstants.REDIS_KEY_USER_CONTACT + userId);
  }

  /** 删除一个联系人缓存 */
  public void removeUserContact(String userId, String contactId) {
    redisUtils.remove(RedisKeyConstants.REDIS_KEY_USER_CONTACT + userId, contactId);
  }

  /** 批量添加联系人（登录时一次性加载） */
  public void addUserContactBatch(String userId, List<String> contactIdList) {
    @SuppressWarnings("unchecked")
    List<Object> values = (List<Object>) (List<?>) contactIdList;
    redisUtils.lpushAll(RedisKeyConstants.REDIS_KEY_USER_CONTACT + userId,
      values, RedisKeyConstants.REDIS_KEY_TOKEN_EXPIRES);
  }

  // ============ 会话缓存（Redis List） ============

  /** 取会话ID列表 */
  public List<String> getUserSessionList(String userId) {
    List<Object> raw = redisUtils.getQueueList(RedisKeyConstants.REDIS_KEY_USER_SESSION + userId);
    @SuppressWarnings("unchecked")
    List<String> list = (List<String>) (List<?>) raw;
    return list;
  }

  /** 添加一个会话（去重） */
  public void addUserSession(String userId, String sessionId) {
    List<Object> sessionList = redisUtils.getQueueList(RedisKeyConstants.REDIS_KEY_USER_SESSION + userId);
    if (!sessionList.contains(sessionId)) {
      redisUtils.lpush(RedisKeyConstants.REDIS_KEY_USER_SESSION + userId,
        sessionId, RedisKeyConstants.REDIS_KEY_TOKEN_EXPIRES);
    }
  }

  /** 清空会话缓存 */
  public void cleanUserSession(String userId) {
    redisUtils.delete(RedisKeyConstants.REDIS_KEY_USER_SESSION + userId);
  }

  // ============ 系统设置 ============

  /** 保存系统设置 */
  public void saveSysSetting(SysSettingDto sysSettingDto) {
    redisUtils.set(RedisKeyConstants.REDIS_KEY_SYS_SETTING, sysSettingDto);
  }

  /** 取系统设置，没有则返回一份带默认值的新对象（机器人信息就在默认值里） */
  public SysSettingDto getSysSetting() {
    SysSettingDto sysSettingDto = (SysSettingDto) redisUtils.get(RedisKeyConstants.REDIS_KEY_SYS_SETTING);
    return sysSettingDto == null ? new SysSettingDto() : sysSettingDto;
  }
}
