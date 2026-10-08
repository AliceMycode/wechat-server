package com.wechat.www.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wechat.www.config.AppConfig;
import com.wechat.www.constant.CommonConstants;
import com.wechat.www.dto.TokenUserInfoDto;
import com.wechat.www.entity.UserContact;
import com.wechat.www.entity.UserInfo;
import com.wechat.www.enums.UserContactStatusEnum;
import com.wechat.www.enums.UserStatusEnum;
import com.wechat.www.exception.BusinessException;
import com.wechat.www.mapper.UserContactMapper;
import com.wechat.www.mapper.UserInfoMapper;
import com.wechat.www.redis.RedisComponet;
import com.wechat.www.service.UserInfoService;
import com.wechat.www.utils.CopyTools;
import com.wechat.www.utils.StringTools;
import com.wechat.www.vo.UserInfoVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户信息业务实现
 * （对应源项目 UserInfoServiceImpl）
 * 靓号逻辑按要求不写；机器人好友 addContact4Robot 依赖聊天域，
 * 等聊天域里程碑建好后回来补（register 里有 TODO）
 */
@Service("userInfoService")
public class UserInfoServiceImpl implements UserInfoService {

  @Resource
  private UserInfoMapper userInfoMapper;

  @Resource
  private UserContactMapper userContactMapper;

  @Resource
  private RedisComponet redisComponet;

  @Resource
  private AppConfig appConfig;

  /**
   * 注册
   * @Transactional：插入用户 + 后面加机器人好友要在同一事务，
   * 任何一步失败整体回滚；rollbackFor = Exception.class 表示所有异常都回滚
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void register(String email, String nickName, String password) {
    // 1. 邮箱查重
    UserInfo existUser = userInfoMapper.selectOne(
      new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getEmail, email));
    if (existUser != null) {
      throw new BusinessException("邮箱账号已经存在");
    }

    Date curDate = new Date();
    // 2. 生成用户ID：U + 11 位随机数字
    String userId = StringTools.getUserId();

    // 3. 组装用户信息（注册前端传明文，这里 MD5 后存）
    UserInfo userInfo = new UserInfo();
    userInfo.setUserId(userId);
    userInfo.setNickName(nickName);
    userInfo.setEmail(email);
    userInfo.setPassword(StringTools.encodeByMD5(password));
    userInfo.setCreateTime(curDate);
    userInfo.setStatus(UserStatusEnum.ENABLE.getStatus());
    userInfo.setLastOffTime(curDate.getTime());

    // 4. 插入
    userInfoMapper.insert(userInfo);

    // 5. 创建机器人好友（依赖聊天域，暂时留空，聊天域里程碑补这里）
    // TODO 聊天域建好后：userContactService.addContact4Robot(userId);
  }

  /**
   * 登录
   */
  @Override
  public UserInfoVO login(String email, String password) {
    // 1. 按邮箱查用户
    UserInfo userInfo = userInfoMapper.selectOne(
      new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getEmail, email));

    // 2. 用户不存在或密码错误
    // 注意：登录前端已把密码 MD5 后传过来，password 就是哈希，直接和数据库哈希比
    if (userInfo == null || !userInfo.getPassword().equals(password)) {
      throw new BusinessException("账号或者密码错误");
    }

    // 3. 账号被禁用
    if (UserStatusEnum.DISABLE.getStatus().equals(userInfo.getStatus())) {
      throw new BusinessException("账号已禁用");
    }

    // 4. 查询该用户"好友状态"的联系人，整理出联系人ID列表
    List<UserContact> contactList = userContactMapper.selectList(
      new LambdaQueryWrapper<UserContact>()
        .eq(UserContact::getUserId, userInfo.getUserId())
        .eq(UserContact::getStatus, UserContactStatusEnum.FRIEND.getStatus()));
    List<String> contactIdList = contactList.stream()
      .map(UserContact::getContactId).collect(Collectors.toList());

    // 5. 先清空旧联系人缓存，再批量写入最新的（保证缓存与DB一致）
    redisComponet.cleanUserContact(userInfo.getUserId());
    if (!contactIdList.isEmpty()) {
      redisComponet.addUserContactBatch(userInfo.getUserId(), contactIdList);
    }

    // 6. 组装登录态（判断是否管理员）
    TokenUserInfoDto tokenUserInfoDto = buildTokenUserInfo(userInfo);

    // 7. 单点登录：心跳还在说明别处已登录，拒绝
    Long lastHeartBeat = redisComponet.getUserHeartBeat(tokenUserInfoDto.getUserId());
    if (lastHeartBeat != null) {
      throw new BusinessException("此账号已经在别处登录，请退出后再登录");
    }

    // 8. 生成 token：MD5(userId + 20位随机串)
    String token = StringTools.encodeByMD5(
      tokenUserInfoDto.getUserId() + StringTools.getRandomString(CommonConstants.LENGTH_20));
    tokenUserInfoDto.setToken(token);

    // 9. 登录态存 Redis（2 天）
    redisComponet.saveTokenUserInfoDto(tokenUserInfoDto);

    // 10. 复制成 UserInfoVO，补上 token 和 admin
    UserInfoVO userInfoVO = CopyTools.copy(userInfo, UserInfoVO.class);
    userInfoVO.setToken(token);
    userInfoVO.setAdmin(tokenUserInfoDto.getAdmin());
    return userInfoVO;
  }

  /**
   * 组装登录态：userId、nickName、admin
   * admin 判断：邮箱在 application.yml 的 admin.emails 列表里
   */
  private TokenUserInfoDto buildTokenUserInfo(UserInfo userInfo) {
    TokenUserInfoDto dto = new TokenUserInfoDto();
    dto.setUserId(userInfo.getUserId());
    dto.setNickName(userInfo.getNickName());

    String adminEmails = appConfig.getAdminEmails();
    if (!StringTools.isEmpty(adminEmails)
      && List.of(adminEmails.split(",")).contains(userInfo.getEmail())) {
      dto.setAdmin(true);
    } else {
      dto.setAdmin(false);
    }
    return dto;
  }
}
