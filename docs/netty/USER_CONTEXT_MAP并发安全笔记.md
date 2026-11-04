# USER_CONTEXT_MAP 并发安全学习笔记

> 日期：2026-11-03
> 触发问题：退出登录 → 立刻重新登录后，双方都收不到消息（连接还活着，但 map 里查不到）
> 涉及代码：`com.wechat.www.netty.ChannelContextUtils`、`com.wechat.www.netty.HandlerWebSocket`

---

## 0. 背景：bug 是怎么来的

老代码 `removeContext` 里是**无条件删除**：

```java
public void removeContext(Channel channel) {
  String userId = ...;                       // 从 channel 属性反查
  if (!StringTools.isEmpty(userId)) {
    USER_CONTEXT_MAP.remove(userId);         // ← 不管当前存的是谁，直接删
    ...
  }
}
```

时序：用户"退出 → 立刻重登"时，旧连接的 `channelInactive` 是**延迟触发**的。
它触发时，map 里已经是新连接了，一行 `remove(userId)` 就把新连接删了 → 新连接 TCP 还活着，但 `sendMsg` 里 `USER_CONTEXT_MAP.get()` 拿到 null → 消息静默丢弃。

修复：改成带值删除，只有"当前存的还是这条连接"才清理。

```java
if (!USER_CONTEXT_MAP.remove(userId, channel)) {
  return;   // 已被新连接顶替，不能碰新连接的心跳/下线时间
}
```

---

## 1. `USER_CONTEXT_MAP.put(userId, channel)` 有返回值吗？

**有。**

`ConcurrentHashMap.put(K, V)` 的签名继承自 `java.util.Map`：

```java
V put(K key, V value)
```

返回**该 key 原先映射的旧 value**；此前没有映射时返回 `null`。

`USER_CONTEXT_MAP` 声明为 `ConcurrentMap<String, Channel>`，所以：

```java
Channel oldChannel = USER_CONTEXT_MAP.put(userId, channel);
//      ↑ 类型是 Channel，没有旧连接时是 null
```

| 场景 | 返回值 |
|---|---|
| 首次登录，map 里没有这个 userId | `null` |
| 重连，map 里已有旧 Channel | 旧的那个 `Channel` 对象 |

原代码 `USER_CONTEXT_MAP.put(userId, channel);` 只是**丢弃了返回值**，旧连接被覆盖后没人管，成了僵尸连接。所以要接住它：

```java
// 同一用户重连：把旧连接顶掉并主动关掉，避免旧连接一直挂着变成僵尸。
Channel oldChannel = USER_CONTEXT_MAP.put(userId, channel);
if (oldChannel != null && oldChannel != channel) {
  oldChannel.close();
}
```

> 顺带对比 `remove`：
> - `remove(key)` → 返回被删掉的 value，单参数，**不看当前值是什么，直接删**
> - `remove(key, value)` → 返回 `boolean`，**当前值等于 value 才删**

---

## 2. `remove(key, value)` 是原子的"比较并删除"，是什么意思？

**核心：把"看一眼现在是谁"和"删掉"两步，合成一个不可分割的动作。**

### 2.1 两个 remove 的区别

```java
map.remove(userId)           // 无脑删：不管现在存的是谁，直接抹掉
map.remove(userId, channel)  // 认人删：现在存的就是 channel 才删，否则不动
```

### 2.2 为什么不拆成"先查后删"

直觉写法：

```java
// ❌ 看起来对，其实有缝
Channel now = USER_CONTEXT_MAP.get(userId);
if (now == channel) {
  USER_CONTEXT_MAP.remove(userId);
}
```

按 bug 场景演一遍（旧连接 A 断开、新连接 B 刚登录）：

| 时刻 | 谁在执行 | 操作 | map 里 userId 对应的值 |
|---|---|---|---|
| t1 | Netty 线程（A 的 channelInactive） | `get(userId)` 拿到 **A** | A |
| t2 | 登录线程 | `put(userId, B)` | **B** |
| t3 | Netty 线程 | 比较 `A == A` 成立 → `remove(userId)` | **空！B 被误删** |

问题出在 **t1 和 t3 之间有一条缝**——`get` 已经返回、`remove` 还没执行，这中间任何其他线程都能改 map。
基于"过时的读"做删除决定，就是经典的 **TOCTOU**（check 之后、use 之前状态变了）。

`remove(key, value)` 没有这条缝：比较和删除在**同一把锁内**一步完成，中间插不进任何人。

### 2.3 内部实现（JDK 8+ 简化版）

```java
final V replaceNode(Object key, V value, Object cv) {
  ...
  synchronized (f) {                       // ← 锁住这个桶的头节点
    if (tabAt(tab, i) == f) {              // ← ① 定位（锁内再确认一次）
      for (Node e = f; ...) {
        if (e.hash == hash && key.equals(e.key)) {
          V ev = e.val;
          if (cv == ev) {                  // ← ② 比较 value（锁内）
            ...                            // ← ③ 删除（锁内）
          }
        }
      }
    }
  }                                        // ← 解锁
}
```

三行代码全在同一个 `synchronized` 块里。其他线程想动这个桶，只能排队等锁。

### 2.4 返回值语义

| 返回值 | 含义 | 后续动作 |
|---|---|---|
| `true` | map 里存的确实是这条刚断开的 Channel → **真下线** | 清心跳、写 `last_off_time`、打日志 |
| `false` | map 里已经是新连接（被顶替），或已被 `closeContext` 清掉 | 直接 `return`，**一个字节都别碰** |

---

## 3. 退出登录走 `removeContext`、重新登录走 `addContext`，`addContext` 之前 `removeContext` 应该执行完了吧？

**不一定。这里有一个关键事实：退出登录调用的不是 `removeContext`，是 `closeContext`。**

`removeContext` 是 `channelInactive` 回调里调的，它什么时候执行由 Netty 的 EventLoop 决定，**不由你的代码顺序决定**。

### 3.1 两个方法，两条线程

| 方法 | 谁调用 | 在哪个线程 | 同步/异步 |
|---|---|---|---|
| `closeContext` | logout 接口 | HTTP 请求线程 | 同步执行完就返回 |
| `removeContext` | `channelInactive` 回调 | Netty EventLoop 线程 | **异步，时间不确定** |

### 3.2 `channel.close()` 为什么是异步的

`closeContext` 里：

```java
redisComponet.cleanUserTokenByUserId(userId);
Channel channel = USER_CONTEXT_MAP.get(userId);
USER_CONTEXT_MAP.remove(userId);
if (channel != null) {
  channel.close();      // ← 重点：异步，立刻返回
}
```

Netty 的 `close()` 只是**把"关闭"这件事排进 EventLoop 的任务队列**，然后马上返回。真正干活的是 EventLoop 线程：

```
你的线程                        EventLoop 线程
   │                                │
   ├─ channel.close() ───排任务──→  │ （任务进队列）
   ├─ 返回                          │
   ├─ closeContext 结束             │
   ├─ HTTP 响应返回前端             │
   │                                ├─ 出站 close 帧
   │                                ├─ TCP 四次挥手
   │                                └─ channelInactive 回调
   │                                        └─ removeContext(channel)
   ▼                                ▼
```

### 3.3 实际时间线

```
T0  logout → closeContext 全部跑完，返回
T1  前端收到响应，跳登录页
T2  用户登录 → 握手 → addContext → put(userId, newChannel)   ← 新连接进来了
T3  EventLoop 才处理完关闭 → channelInactive → removeContext(oldChannel)
     └─ 老代码 remove(userId) → 把 T2 刚放进去的 newChannel 删了！
```

**T2 和 T3 是两条独立的线程，谁先谁后没有保证。**

### 3.4 为什么平时感觉"顺序是对的"

平时确实 `removeContext` 先跑完——因为人从点退出到输密码登录要好几十秒，EventLoop 早就处理完了。**但这只是"通常"，不是"必然"**。三种情况会翻车：

**① 自动登录 / 记住密码**
前端拿到 logout 响应后立刻自动登录，几秒钟内就重连上了，EventLoop 可能还没轮到那个关闭任务。

**② 前端自动重连（最常见）**

`wsClient.js` 的 `onclose` 会 5 秒后重试，最多 5 次。网络闪断时：
- 客户端 5 秒后重连成功 → `put(userId, newChannel)`
- 服务端这边旧 TCP **还没断**（`IdleStateHandler` 是 60 秒读超时），要等 60 秒才触发 `channelInactive`
- 60 秒后 `removeContext(oldChannel)` → 老代码删掉 newChannel

**③ 同一账号在另一台设备登录**

老客户端直接关电脑（没走 logout），TCP 连接还在。新设备登录 → `put(newChannel)`。老连接最终超时 → `removeContext(oldChannel)` → 老代码删掉 newChannel。

### 3.5 结论

> **`closeContext` 返回 ≠ 连接已经关闭 ≠ `removeContext` 已经执行。**
> 关闭是异步的，`channelInactive` 什么时候来，取决于 EventLoop 的调度，和你的代码顺序无关。

所以 `addContext` 里主动关旧连接的那段：

```java
Channel oldChannel = USER_CONTEXT_MAP.put(userId, channel);
if (oldChannel != null && oldChannel != channel) {
  oldChannel.close();
}
```

不是"在等 `removeContext` 执行完"，而是**主动把可能还挂着的旧连接关掉**——不管它是 logout 断的、闪断断的、还是根本没断，重连时统一清理一遍。旧连接真正触发 `channelInactive` 时，`remove(userId, channel)` 会返回 `false` 把它拦下，伤不到新连接。

---

## 4. 旧连接触发 `channelInactive` 时，`remove(userId, channel)` 为什么返回 false？不是该把新连接也删了吗？

**关键：`remove(userId, channel)` 里的 `channel` 是旧连接，不是新连接。**

### 4.1 这个 `channel` 从哪来

```java
public void removeContext(Channel channel) {   // ← 入参
  ...
  if (!USER_CONTEXT_MAP.remove(userId, channel)) {
```

调用方是 `HandlerWebSocket.channelInactive`：

```java
@Override
public void channelInactive(ChannelHandlerContext ctx) throws Exception {
  channelContextUtils.removeContext(ctx.channel());   // ← 这里传进来的
}
```

而 **`ctx.channel()` 永远是"触发这次回调的那条通道"**，不是"当前在线的通道"。
`channelInactive` 是"我这条连接断了"的通知，所以 `ctx.channel()` = **正在断开的那条 = 旧连接**。

### 4.2 参数含义对照

```java
USER_CONTEXT_MAP.remove(userId, channel)
//                     ↑key    ↑要匹配的预期值
```

`remove(key, value)` 的两个参数**不是**"删这两项"，而是：

> **当 map 里 key 对应的值恰好 == value 时才删，否则什么都不做。**

逐项对照当前状态：

| 名字 | 实际是什么 |
|---|---|
| `userId` | key，比如 `"U38912482170"` |
| `channel`（第二个参数） | **旧连接**（正在断开的这条） |
| `map.get(userId)` | **新连接**（`addContext` 刚 put 进去的） |

比较：`map[userId]`（新连接）`== channel`（旧连接）？→ **不相等** → **不删** → 返回 `false`。

### 4.3 两种情况

**情况 A：有新连接顶替（旧连接延迟断开）**

```
map = { userId → 新连接 }

remove(userId, 旧连接)
  比较: 新连接 == 旧连接 ?  ❌
  → 不删任何东西，return false
  → 走 return，新连接毫发无伤 ✅
```

**情况 B：真正下线（没有新连接）**

```
map = { userId → 旧连接 }

remove(userId, 旧连接)
  比较: 旧连接 == 旧连接 ?  ✅
  → 删掉，return true
  → 继续清心跳、写 last_off_time ✅
```

### 4.4 对比老代码

```java
USER_CONTEXT_MAP.remove(userId);   // 单参数
```

这个方法**不看当前值是什么，直接删**。所以旧连接的 `channelInactive` 一延迟触发，就把 map 里那个"新连接"一起抹掉了——这正是 bug 的根源。

`remove(userId, channel)` 多出来的第二个参数，就是用来回答"**现在存的还是我吗？是我才删**"。

---

## 5. "原子操作"到底是什么意思？`remove(userId, channel)` 是先找 key、再比 value、再删吗？

**对，内部确实是"定位 → 比较 → 删除"三步。**
"原子"不是说不存在这三步，而是说**这三步之间不允许别的线程插进来**。

### 5.1 手动拆开写：三步之间有空隙

```java
Channel now = map.get(userId);      // ① 定位 + 读值
// ← 空隙！线程可能被切换
if (now == channel) {               // ② 比较
  // ← 空隙！
  map.remove(userId);               // ③ 删除
}
```

每个 `map.xxx()` 都是一次独立的方法调用，**方法返回后就没有任何保护了**。别的线程可以在这两个空隙里随便改 map：

```
线程 A(旧连接断开)          线程 B(新登录)
      │                         │
      ├─ get(userId) → 旧连接   │
      │                         ├─ put(userId, 新连接)   ← 插进空隙！
      ├─ 比较 旧==旧 → 成立      │
      ├─ remove(userId) ────────┼→ 把新连接删了 💥
      ▼                         ▼
```

### 5.2 `remove(key, value)`：三步被"焊"在一起

```java
map.remove(userId, channel);   // 定位 → 比较 → 删除，一口气做完
```

从其他线程的视角看，这个方法**要么还没开始，要么已经做完了**，看不到"比较完了但还没删"的中间态。这就是原子。

### 5.3 对比总结

| | 手动三步 | `remove(key, value)` |
|---|---|---|
| 执行步骤 | 定位 → 比较 → 删除 | 定位 → 比较 → 删除 |
| 中间能否被插队 | **能** | **不能**（持锁） |
| 结果 | 可能误删别人的值 | 要么匹配上删掉，要么原样不动 |

### 5.4 原子的精确定义

> **一组操作对外表现为一个不可分割的单位——其他线程无法观察到它执行到一半的状态，也无法在这组操作中途改变它依赖的数据。**

---

## 6. 一页速查

| 问题 | 答案 |
|---|---|
| `put(k, v)` 有返回值吗 | 有，返回旧 value，没有则 `null` |
| `remove(k)` 和 `remove(k, v)` 区别 | 前者无脑删，后者"值匹配才删"，返回 `boolean` |
| `channel.close()` 会等连接真断吗 | 不会，异步排进 EventLoop，立即返回 |
| `channelInactive` 什么时候触发 | EventLoop 调度时，不确定，可能延迟很久 |
| `removeContext` 的 `channel` 参数是谁 | 触发回调的那条连接，即**旧连接** |
| `remove(userId, channel)` 何时返回 true | map 里存的确实是 `channel`（真下线） |
| `remove(userId, channel)` 何时返回 false | map 里已是别的连接（被顶替），不该动 |
| "原子"是什么意思 | 多步操作对外不可分割，中间插不进任何线程 |
