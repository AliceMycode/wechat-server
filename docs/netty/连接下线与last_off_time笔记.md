# 连接下线与 last_off_time 学习笔记

> 日期：2026-11-04
> 触发问题：`removeContext` 里的 `saveUserOffTime(userId)` 什么场景会被调用？自然断开是不是被 `closeContext` 清理了，走不到入库？
> 涉及代码：`ChannelContextUtils`、`HandlerWebSocket`、`HandlerHeartBeat`、`NettyWebSocketStarter`、`UserInfoController`
> 对照前端：`D:\download\easychat-front\src\main\wsClient.js`、`src\main\ipc.js`、`src\renderer\src\views\setting\UserInfo.vue`

---

## 0. 调用链

```
Netty 的 channelInactive 事件
  └─ HandlerWebSocket.channelInactive()        HandlerWebSocket.java:35-37
       └─ ChannelContextUtils.removeContext()   ChannelContextUtils.java:218
            ├─ USER_CONTEXT_MAP.remove(userId, channel)   ← 决定后面走不走
            ├─ redisComponet.removeUserHeartBeat(userId)
            ├─ saveUserOffTime(userId)                    ← ChannelContextUtils.java:235
            └─ log.info("用户 {} 下线...")
```

`removeContext` 只有一个外部调用点：`HandlerWebSocket.channelInactive`。

---

## 1. channelInactive 的触发场景

`channelInactive` 是 Netty 在 **Channel 关闭并进入非活动状态**时的回调，不管关闭是客户端发起的还是服务端发起的。

### 客户端主动断开

| 场景 | 是否写 last_off_time |
|---|---|
| 关闭应用窗口 / 进程退出 | ✅ |
| 断网、休眠、拔网线 | ✅（延迟到 60 秒后，见下） |
| 前端 `closeWs()`（`ws.close()`） | ✅（前提是这条连接还在 map 里） |

### 服务端主动关闭

| 场景 | 位置 | 是否写 |
|---|---|---|
| 心跳超时：60 秒没收到任何数据 → `ctx.close()` | `HandlerHeartBeat:30` | ✅ |
| 连接异常 → `ctx.close()` | `HandlerWebSocket:94` | ✅ |
| **解散群** → `group.close()` 关闭组内所有 Channel | `ChannelContextUtils:337` | ✅（每个在线成员都会被写下线时间） |
| 重新登录顶号 → `oldChannel.close()` | `ChannelContextUtils:105` | ❌ 旧连接被顶替，`remove` 返回 false |
| `logout` / 改密码 → `closeContext` 里的 `channel.close()` | `ChannelContextUtils:250` | ❌ 同上，由 `closeContext` 自己写 |
| token 无效 → `ctx.close()` | `HandlerWebSocket:62/68` | ❌ 还没执行 `addContext`，Channel 上没有 userId 属性 |
| 服务端停机 `shutdownGracefully()` | `NettyWebSocketStarter` | 由 `@PreDestroy` 的 `saveAllUserOffTime` 兜底 |

> 断网时 TCP 不会立刻收到 FIN，服务端感知不到，要等 `IdleStateHandler(60, 0, 0)` 触发 READER_IDLE、`HandlerHeartBeat` 关连接。所以断网用户的下线时间最多晚 60 秒，这 60 秒内别人发给他的消息，下次上线会被重推一次。

---

## 2. 写不写取决于 remove(userId, channel)

`ChannelContextUtils:230-232`：

```java
if (!USER_CONTEXT_MAP.remove(userId, channel)) {
  return;   // ← 写时间的代码在后面，走不到
}
```

所以**"写不写库"取决于这条连接关的时候还是不是 map 里登记的那条**：

- 自然断开（关窗口、断网、心跳超时、解散群）：map 里还是它 → `remove` 成功 → ✅ 写
- 被顶替 / 被 `closeContext` 主动清理：map 里已经换了人或没了 → `remove` 失败 → ❌ 不写（由各自的新路径负责）

---

## 3. closeContext 与 removeContext 是两条互斥路径

`closeContext` **只有两个调用点**，都是 HTTP 接口：

```
UserInfoController.java:87   updatePassword()  改密码踢下线
UserInfoController.java:99   logout()          主动退出登录
```

自然断开不会经过任何 HTTP 接口，服务端只会在连接层收到 `channelInactive`，**没有人会去调 `closeContext`**。

两条路径分开看：

```
自然断开：
  Channel 关闭
    → HandlerWebSocket.channelInactive()
      → removeContext()
        → USER_CONTEXT_MAP.remove(userId, channel)  ← 返回 true（没人提前删过）
          → ✅ 写 last_off_time

主动登出 / 改密：
  HTTP 请求到 Controller
    → closeContext()
      → USER_CONTEXT_MAP.remove(userId)   ← 主动先删
      → channel.close()
        → channelInactive()
          → removeContext()
            → remove(userId, channel)  ← 返回 false，提前 return
      → ✅ closeContext 自己补写 last_off_time
```

自然断开这条路上没有人提前从 map 删东西，所以 `remove(userId, channel)` 必然成功，写库那行一定走到。

`closeContext` 里那段 `saveUserOffTime` 不是为了防止"自然断开漏写"，而是因为**它自己就是那个抢跑的人**——先 `remove` 再 `close`，等于把自己那条 `channelInactive` 路径废掉了，必须自己补上。

一句话：`removeContext` 里的管"自然死"的连接，`closeContext` 里的管"被主动杀"的连接，各管各的。

---

## 4. 前端 logout 的实际时序

`UserInfo.vue:79-82`：

```js
window.ipcRenderer.send('reLogin')                              // ① 异步通知主进程
let result = await proxy.Request({ url: proxy.Api.logout })     // ② 立刻发 HTTP
```

主进程收到 `reLogin` 后（`ipc.js:55`）才 `closeWs()`。所以 `ws.close()` 和 HTTP `logout` 几乎同时发出，谁先到服务端是竞态：

| 谁先到 | 发生什么 |
|---|---|
| WS close frame 先到 | `channelInactive` → `removeContext` 写一次；HTTP 到后 `closeContext` 的 `get` 拿到 null、`channel == null`，末尾仍会再写一次（时间稍晚，幂等无害） |
| HTTP 先到 | `closeContext` 先删先关，自己写一次；随后 `channelInactive` 里 remove 失败直接 return |

两种情况都能写到，只是次数不同（一次或两次），结果一样。

---

## 5. 副作用：closeContext 不清心跳、不打下线日志

因为 `closeContext` 先 `USER_CONTEXT_MAP.remove(userId)` 再 `channel.close()`，后续 `channelInactive` 触发 `removeContext` 时：

```java
if (!USER_CONTEXT_MAP.remove(userId, channel)) {
  return;   // ← 在这里就返回了
}
redisComponet.removeUserHeartBeat(userId);   // 走不到
saveUserOffTime(userId);                     // 走不到（closeContext 自己补了）
log.info("用户 {} 下线，当前在线 {} 人", ...);  // 走不到
```

所以走 `logout` / 改密码这条路时：

- **心跳 key 没有被主动删除**，只能靠 Redis TTL 自然过期
  - 心跳 TTL：`RedisKeyConstants.REDIS_KEY_EXPIRES_HEART_BEAT = 6`（秒）
  - token 已经在 `closeContext` 里清掉了，所以登录时不会被判"已在别处登录"
- **不会打"用户 xxx 下线"日志**

---

## 6. 旧笔记勘误

`docs/netty/Netty学习笔记.md` 第 1 节曾写"`closeContext` 不清心跳，因为后面会触发 `removeContext`，心跳在那边清了"。

**这个说法在当前代码下已经不成立**：`closeContext` 先删 map，导致 `removeContext` 里的 `remove(userId, channel)` 返回 false 提前 return，`removeUserHeartBeat` 走不到。

现在的心跳清理只有两条路：

- 自然断开 → `removeContext` → `removeUserHeartBeat`
- 登出/改密 → 不主动删，靠 6 秒 TTL 过期

---

## 7. 一页速查

| 问题 | 答案 |
|---|---|
| `saveUserOffTime` 从哪触发 | `channelInactive` → `removeContext` → `saveUserOffTime` |
| `closeContext` 会因自然断开被调吗 | 不会，只有 logout / updatePassword 两个 HTTP 接口调它 |
| 自然断开能走到写库吗 | 能，没人提前删 map，`remove(userId, channel)` 返回 true |
| 什么时候写不了 | 被新连接顶替、被 `closeContext` 抢先删除 |
| 解散群会写下线时间吗 | 会，`group.close()` 让每个在线成员走一次 `removeContext` |
| 断网多久算下线 | 60 秒后心跳超时，`HandlerHeartBeat` 关连接 |
| logout 后心跳谁清 | 没人主动清，靠 6 秒 TTL 过期 |
| logout 会打下线日志吗 | 不会，日志在 `removeContext` 里，被提前 return 跳过了 |
