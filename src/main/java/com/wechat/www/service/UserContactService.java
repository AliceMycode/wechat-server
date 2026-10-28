package com.wechat.www.service;

import com.wechat.www.dto.AddContactDto;
import com.wechat.www.dto.UserContactSearchResultDto;
import com.wechat.www.entity.UserContact;
import com.wechat.www.enums.UserContactTypeEnum;
import com.wechat.www.vo.PaginationResultVO;

import java.util.List;

/**
 * 联系人业务接口
 * （对应源项目 UserContactService；先只放注册时用的机器人好友方法，后面加好友/加群再加）
 */
public interface UserContactService {

  /**
   * 新用户注册后，自动加"机器人好友"，并初始化一个会话 + 一条欢迎消息
   * @param userId 新注册用户ID
   */
  void addContact4Robot(String userId);

  /**
   * 加载联系人列表（好友/群组）
   */
  List<UserContact> loadContact(
    String userId, UserContactTypeEnum contactType);


  /**
   * 搜索联系人：按完整用户ID精确查一个人，顺便返回"我和TA的关系状态"
   * （对应源项目 searchContact；模糊匹配是前端拿本地数据做的，后端只认完整ID）
   * @param myUserId 当前登录用户ID
   * @param contactId 用户完整ID（U开头）或群ID（G开头）
   */
  UserContactSearchResultDto searchContact(String myUserId, String contactId);

  /**
   * 建立联系人关系（好友或入群）的唯一入口
   * 调用场景：①审批好友申请时同意 ②applyAdd 遇到"直接加入"(joinType=0) ③入群（阶段4补）
   */
  void addContact(AddContactDto dto);


}
