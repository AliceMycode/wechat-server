# WebSocket 消息推送 extendData 学习笔记

> 日期：2026-11-04
> 触发问题：群主拉人时 `MessageSendDto` 没有 `setExtendData`，前端为什么收到的 `extendData` 有数据？
> 涉及代码：`MessageSendDto`、`ChannelContextUtils`、`GroupInfoServiceImpl`、`UserContactServiceImpl`、`UserInfoServiceImpl`
> 对照前端：`D:\download\easychat-front\src\main\wsClient.js`、`src\renderer\src\views\chat\Chat.vue`

---

## 0. extendData 是什么

`MessageSendDto` 是服务端推给前端所有 WS 消息的统一信封：

```java
public class MessageSendDto<T> implements Serializable {
  private Long messageId;
  private String sessionId;
  private String sendUserId;
  private String contactId;
  private String messageContent;
  private Integer messageType;
  // ... 其余公共字段
  private T extendData;   // ← 泛型附加载荷
}
```

主字段是所有消息都有的公共字段。但不同消息类型要捎带的东西完全不一样——初始化要塞一堆列表、退群要塞一个用户 ID、改名要塞一个字符串——**这些放不进固定字段，就统一塞进 `extendData`**，这样不必为每种消息定义子类。

它的类型**由消息类型约定**，编译期管不了：

| 消息类型 | extendData 的实际类型 |
|---|---|
| INIT(0) | `WsInitData` |
| GROUP_CREATE(3) | `ChatSessionUser` |
| CONTACT_NAME_UPDATE(10) | `String` |
| LEAVE_GROUP(11) / REMOVE_GROUP(12) | `String`（退群人的 userId） |
| ADD_FRIEND_SELF(13) | `UserInfo` |

---

## 1. 服务端 6 处赋值

| # | 位置 | 消息类型 | 装什么 | 谁消费 |
|---|---|---|---|---|
| 1 | `ChannelContextUtils:203` | INIT(0) | `WsInitData`：会话列表 + 离线消息 + 申请数 | 前端 `wsClient.js:61-65` 建本地会话、存离线消息、刷申请红点 |
| 2 | `UserContactServiceImpl:380` | ADD_FRIEND_SELF(13) | 对方的 `UserInfo` | **服务端自己**：`sendMsg:273-277` 取出 userId/nickName，转成普通 ADD_FRIEND(1)，然后置 null，前端看不到 13 |
| 3 | `GroupInfoServiceImpl:157` | GROUP_CREATE(3) | `ChatSessionUser`（群主视角会话行） | 前端 `wsClient.js:99` 判定为对象 → 合并进 sessionInfo，直接建一条群会话 |
| 4 | `GroupInfoServiceImpl:183` | CONTACT_NAME_UPDATE(10) | 新群名 `String` | 前端 `wsClient.js:78` → `updateGroupName(contactId, 新名字)` |
| 5 | `GroupInfoServiceImpl:347` | LEAVE_GROUP(11) / REMOVE_GROUP(12) | 退群人的 userId `String` | 前端 `wsClient.js:56` 判断"是不是我自己退的"；服务端 `sendMsg2Group:323` 清 Redis + 摘 ChannelGroup |
| 6 | `UserInfoServiceImpl:266` | CONTACT_NAME_UPDATE(10) | 新昵称 `String` | 前端 `wsClient.js:78` 改好友会话里显示的名字 |

用途分两类：

- **给前端补数据**：0、3、10
- **服务端内部传参**：13（UserInfo）、11/12（userId，前端顺带也用）

---

## 2. 服务端没赋值，前端为什么有数据

**数据是前端主进程自己塞进去的。**

`wsClient.js:118-119`：

```js
//查询本地session 单聊联系人就是发送人，群聊联系人就是群号
const dbSessionInfo = await selectUserSessionByContactId(message.contactId);
message.extendData = dbSessionInfo;   // ← 覆盖服务端发来的 extendData
```

前端收到 `1/2/3/5/8/9/11/12` 这些类型后：

1. 按 `contactId` 查本地 SQLite 里的会话记录
2. 把这个会话对象直接赋给 `message.extendData`
3. 再 `sender.send("reciveMessage", message)` 转给渲染进程

所以 `Chat.vue` 拿到的 `extendData` 是**前端本地数据库的会话信息**，不是服务端发的。

这么设计的理由：服务端在不同消息类型下塞的东西类型不一致（对象/字符串），前端统一处理会对字符串执行 `Object.assign` 出错。干脆在 `wsClient` 里统一换成"本地会话记录"。

### 覆盖前要先取走的值

`wsClient.js:56`：

```js
const leaveGroupUserId = message.extendData;   // 56 行，此时还是服务端发的 userId 字符串
...
message.extendData = dbSessionInfo;            // 119 行，之后才覆盖
...
if (messageType == 11 && leaveGroupUserId == store.getUserId()) { break; }  // 121 行
```

所以 `leaveGroup` 里 `messageSend.setExtendData(userId)` 是**必须的**，前端靠它判断"是不是我自己退的"。

### 对 9 号消息的结论

拉人时服务端设不设 `extendData` 都无所谓，119 行会覆盖。真正有用的是：

- `contactName`：被拉的人本地没这条会话，`saveOrUpdate4Message` 需要它建记录
- `memberCount`：前端 `wsClient.js:110-112` 读的是 `message.memberCount`，不是 `extendData` 里的

---

## 3. 类型擦除与 instanceof

跨 Redis 广播回来后，`extendData` 已经变成 `LinkedHashMap`（JSON 反序列化默认形态），不一定还是原来的类型。所以消费端必须判断：

```java
// ChannelContextUtils:273
if (msg.getExtendData() instanceof UserInfo userInfo) { ... }

// ChannelContextUtils:323
if (msg.getExtendData() instanceof String userId) { ... }
```

直接强转可能 `ClassCastException`，把整个 Redis 监听线程炸掉。

---

## 4. 顺带：解散群为什么不在 sendMsg2Group 里清 Redis

`sendMsg2Group` 里退群/踢人会清 Redis：

```java
if (typeEnum == MessageTypeEnum.LEAVE_GROUP || typeEnum == MessageTypeEnum.REMOVE_GROUP) {
  if (msg.getExtendData() instanceof String userId) {
    redisComponet.removeUserContact(userId, msg.getContactId());
```

但解散群没有，因为**已经在 Service 层做过了**（`GroupInfoServiceImpl:381-384`）：

```java
List<UserContact> memberList = userContactMapper.selectList(...);  // 全部成员，含离线的
for (UserContact uc : memberList) {
  redisComponet.removeUserContact(uc.getUserId(), groupId);
}
```

不能挪到 `sendMsg2Group` 的原因：

| 数据源 | 包含谁 |
|---|---|
| `user_contact` 表（Service 里查） | **全部成员，含离线的** |
| `GROUP_CONTEXT_MAP` 里的 `ChannelGroup` | **只有在线的 Channel** |

解散时 `group.writeAndFlush(...)` 只有在线成员收得到。清理写在 `sendMsg2Group` 里，离线成员的 Redis 缓存永远清不掉。

退群/踢人只需要清一个人，且 userId 就在 `extendData` 里，所以放 `sendMsg2Group` 顺便做没问题。

> `sendMsg2Group` 每个实例都会执行（Redis 广播到所有实例），多实例时 `removeUserContact` 会重复执行 N 次。因为是 `lRem` 幂等操作，无副作用，只是多几次 Redis 调用。

---

## 5. 一页速查

| 问题 | 答案 |
|---|---|
| `extendData` 是什么 | `MessageSendDto` 的泛型附加载荷，类型由消息类型约定 |
| 为什么不用固定字段 | 不同消息类型要带的数据类型各异，避免为每种消息建子类 |
| 服务端没设，前端为什么有值 | `wsClient.js:119` 用本地会话记录覆盖了 |
| 11 号退群的 userId 从哪来 | 覆盖前在 56 行取走的 `leaveGroupUserId` |
| 9 号消息 extendData 有用吗 | 没用，前端会覆盖；靠 `contactName` + `memberCount` |
| 为什么要 `instanceof` | Redis 序列化后类型可能变成 `LinkedHashMap` |
| 解散群为什么在 Service 清 Redis | ChannelGroup 只有在线成员，离线成员清不到 |
