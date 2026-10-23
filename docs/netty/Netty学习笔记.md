# Netty 学习笔记（结合本项目）

> 本文记录了在复刻 EasyChat 项目过程中，关于 Netty 的疑问与解答。
> 所有代码均来自 `com.wechat.www.websocket` 包。

---

## 1. closeContext 为什么不清心跳？removeContext 为什么不清 token？

两个方法代表两种完全不同的语义：

| 方法 | 触发场景 | 做什么 | 不做什么 |
|---|---|---|---|
| `removeContext(channel)` | 网络断开、关窗口、心跳超时 | 只清心跳（在线状态） | **保留 token** |
| `closeContext(userId)` | 主动登出、管理员踢人、改密码 | 清 token（登录态）+ 关 Channel | — |

### closeContext 为什么不清心跳？

因为它**后面会触发 `removeContext`**，心跳在那边清了。

调用链：

```
closeContext(userId)
  ├─ cleanUserTokenByUserId()    // 删 token
  ├─ USER_CONTEXT_MAP.remove()   // 从在线表移除
  └─ channel.close()             // 关 Channel
       └─ Netty 触发 channelInactive 事件
            └─ HandlerWebSocket.channelInactive()
                 └─ removeContext(channel)
                      └─ redisComponet.removeUserHeartBeat()  // ← 心跳在这清
```

`channel.close()` 不是立刻同步执行完的，但 Netty 一定会触发 `channelInactive`，进而走 `removeContext`，心跳就清了。所以不用在 `closeContext` 里重复清。

> 唯一的边界情况：如果用户当前不在线（`channel == null`），`closeContext` 不会触发 `removeContext`，心跳也没清。但人不在线时本来就没有心跳，残留一个过期 key 6 秒后自动消失。

### removeContext 为什么不清 token？

因为正常掉线不删 token：

- 用户可能只是**断网/关电脑/切后台**，几秒钟后重连，不该被要求重新登录。
- token 在 Redis 里有 2 天有效期，到期自动删。
- 心跳才是"在线标记"——心跳没了，登录时查不到，就不会报"此账号已在别处登录"。

什么时候才删 token？只有**明确不想让这个账号再用旧凭证连上来**的时候：
- 用户点"退出登录" → 删 token
- 管理员强制下线 → 删 token
- 改了密码 → 删 token

简单记：
- **掉线** = 人走了，但钥匙还在兜里（token 留着，心跳撤了）
- **踢人/登出** = 钥匙没收（token 删了，顺便把人拉出去）

---

## 2. 为什么触发 channelInactive 会走 removeContext？removeContext 不是我们定义的方法吗

`removeContext` 是我们自己写的方法，Netty 根本不认识它。是我们在 `HandlerWebSocket` 里**手动调的**。

看我们写的代码：

```java
@Override
public void channelInactive(ChannelHandlerContext ctx) {
    // ↑ 这个方法是 Netty 规定好的：Channel 断开时自动调用
    // ↓ 方法体里，是我们自己写的代码去调 removeContext
    channelContextUtils.removeContext(ctx.channel());
}
```

完整链路：

```
channel.close()
  ↓
Netty 检测到 Channel 关闭，在 Pipeline 里传递"channelInactive"事件
  ↓
Pipeline 上每个 Handler 都会收到这个事件
  ↓
我们的 HandlerWebSocket 重写了 channelInactive 方法
  ↓
在我们重写的方法里，手动写了一行：
    channelContextUtils.removeContext(ctx.channel());
  ↓
removeContext 才被执行
```

关键点：

1. **Netty 只会调它认识的那些方法**——`channelActive`、`channelInactive`、`channelRead0`、`userEventTriggered` 这些都是父类定义好的钩子。
2. **它不会知道 `removeContext` 是什么**——这是我们自己业务层的方法。
3. **桥接是我们做的**——我们重写 `channelInactive`，在里面写了"顺便调一下 removeContext"。

---

## 3. Netty 常用功能和基础概念

### Netty 是什么

一句话：**一个高性能的网络通信框架**，让你不用自己写 `ServerSocket`、处理多线程、粘包拆包这些底层脏活。

为什么用它做 WebSocket？因为 Spring Boot 自带的 Tomcat 只擅长处理 HTTP 请求（一问一答），不擅长长连接。Netty 就是干长连接的。

### EventLoopGroup（线程池）

```java
NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);      // 接收连接
NioEventLoopGroup workerGroup = new NioEventLoopGroup();      // 处理读写
```

- **bossGroup**：门口接待，只负责"有人来连了，接进来"。1 个线程就够。
- **workerGroup**：接进来之后真正干活的。默认 CPU 核数×2 个线程。

### Channel（一条连接）

- **一个 Channel = 一条客户端连接**。就像打电话时的那根电话线。
- 你往 Channel 里 `writeAndFlush()`，消息就推给客户端了。
- `channel.close()` 就是挂断。

### ChannelPipeline（责任链）

每个连接进来，都会按顺序穿过这条"流水线"：

```
客户端发消息
    ↓
HttpServerCodec         把 HTTP 字节流 → HttpRequest 对象
    ↓
HttpObjectAggregator    把分散的 HTTP 片段拼成一个完整请求
    ↓
IdleStateHandler        定时检查：60秒没读到东西？触发心跳事件
    ↓
HandlerHeartBeat        收到心跳事件 → 关连接
    ↓
WebSocketServerProtocolHandler  HTTP 升级成 WebSocket
    ↓
HandlerWebSocket        ★ 我们的业务代码
```

**消息进来**：从前往后传；**消息出去**（write）：从后往前传。

### ChannelHandler 生命周期方法

| 方法 | 什么时候触发 | 我们在里面干了什么 |
|---|---|---|
| `channelActive` | 连接刚建立 | （只打日志） |
| `channelInactive` | 连接断开 | 调 `removeContext` 清理在线状态 |
| `channelRead0` | 收到一条消息 | 续约心跳 |
| `userEventTriggered` | 特殊事件（握手完成、心跳超时） | 校验 token、登记 Channel |

### 心智模型

把 Netty 想象成一个餐厅：

- **bossGroup** = 门口接待，负责开门迎客
- **workerGroup** = 后厨厨师，真正处理请求
- **Channel** = 一桌客人
- **Pipeline** = 传菜窗口，每道菜经过一个窗口加一味调料
- **Handler** = 每个窗口的师傅
- **ChannelHandlerContext (ctx)** = 服务员手里的对讲机

---

## 4. WebSocket 和 Netty 有什么区别？Channel 是先建还是握手后建

### WebSocket vs Netty

不是同一层的东西：

- **WebSocket**：一种**协议**（像 HTTP），规定了客户端和服务器怎么双向传消息，跑在 TCP 上。
- **Netty**：一个**网络编程框架**，帮你实现各种协议。

类比：WebSocket = "普通话"（语言规范），Netty = "好用的对讲机"（工具）。

### Channel 什么时候建

**TCP 连接建立那一刻 Channel 就有了**，不是握手后才建。完整顺序：

```
① TCP 三次握手
   前端："我要连你 5051 端口"
   ↓
   此时 Netty 就创建了 Channel
   Channel 上挂好了整条 Pipeline

② HTTP 请求（在这条 Channel 上）
   前端发：GET /ws?token=xxx HTTP/1.1
          Upgrade: websocket
   ↓
   HttpServerCodec 解析这个 HTTP 请求

③ HTTP → WebSocket 升级（还是这条 Channel！）
   服务器回：101 Switching Protocols
   ↓
   WebSocketServerProtocolHandler 把 Channel 的"身份"从 HTTP 改成 WebSocket
   触发 HandshakeComplete 事件
   ↓
   我们的 userEventTriggered 在这里校验 token、addContext

④ 双向通信（还是这条 Channel）
```

**关键点**：从头到尾就一条 Channel。不是"先建 Channel 再握手"，也不是"握手完再建 Channel"，而是：

> TCP 连接建立 → Channel 创建 → 在这条 Channel 上做 HTTP 握手 → 升级成 WebSocket → 继续用这条 Channel 通信。

---

## 5. Pipeline 顺序为什么有讲究？64*1024 是什么？IdleStateHandler 能放握手后吗

### 为什么顺序不能换

Pipeline 是**责任链**，消息进来时从前往后穿。顺序必须按"数据形态变化"排：

```
原始字节
  ↓ ① HttpServerCodec：字节 → HttpRequest 对象
HttpRequest
  ↓ ② HttpObjectAggregator：碎片 → 完整 FullHttpRequest
完整请求
  ↓ ③ IdleStateHandler：只负责定时检查，不改变数据
  ↓ ④ HandlerHeartBeat：只处理超时事件
  ↓ ⑤ WebSocketServerProtocolHandler：HTTP 升级 → WebSocket 帧
WebSocket 帧
  ↓ ⑥ HandlerWebSocket：我们的业务代码
```

**核心原则：先解码，再业务。** 如果把 `HttpServerCodec` 放在 `HandlerWebSocket` 后面，业务 Handler 收到的是原始字节流，根本看不懂 HTTP 请求。

### 64 * 1024 是什么

`HttpObjectAggregator(64 * 1024)` = 64KB。

HTTP 请求是分块传的，`HttpObjectAggregator` 把碎片拼成完整请求。**64KB 是允许拼成的最大体积**——超过会被拒绝。我们只传 WebSocket 握手和小文本消息，64KB 够用。

### IdleStateHandler 能不能放握手之后

**不能。**

`IdleStateHandler` 是双向处理器，它从 **TCP 连接建立那一刻就开始计时**，不管现在是 HTTP 阶段还是 WebSocket 阶段。

如果放在 WebSocketServerProtocolHandler 后面，握手前那几秒（TCP 连接到 HTTP 握手之间）如果客户端卡住，服务器检测不到，会白白挂着连接。

---

## 6. netty 什么时候才会调 userEventTriggered？读超时时还没握手成功怎么触发

`userEventTriggered` 是 Netty 的**通用事件通道**，任何"自定义事件"都会从 Pipeline 里传过来。它**不是 WebSocket 专属的**。

它能收到两种完全不同的事件：

| 事件来源 | 事件类型 | 在哪个 Handler 里处理 |
|---|---|---|
| IdleStateHandler（60秒没收到字节） | IdleStateEvent | HandlerHeartBeat |
| WebSocketServerProtocolHandler（握手完成） | HandshakeComplete | HandlerWebSocket |

### 握手前超时怎么触发

**IdleStateHandler 从 TCP 连接建立那一刻就开始计时了**，跟握没握手没关系。

```
T0: TCP 三次握手成功，Channel 创建
    IdleStateHandler 开始倒计时 60 秒
    （此时 Pipeline 里还是 HTTP 阶段，还没升级 WebSocket）

T0+60s: 如果客户端一直不发 HTTP 请求（网络断了、恶意连接）
    IdleStateHandler 自动往 Pipeline 里塞一个 IdleStateEvent(READER_IDLE)
    → HandlerHeartBeat.userEventTriggered 收到
    → ctx.close()  断开这个僵尸连接
```

这正是我们要的——**防止有人连了 TCP 但不发请求，一直占着资源**。

### 两个 Handler 各自监听什么

```java
// HandlerHeartBeat.java
if (evt instanceof IdleStateEvent e) {
  if (e.state() == IdleState.READER_IDLE) ctx.close();
}

// HandlerWebSocket.java
if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete complete) {
  // 校验 token、addContext
}
```

事件是广播式的，每个 Handler 都能听到，但只处理自己关心的类型。

| | HandlerHeartBeat | HandlerWebSocket |
|---|---|---|
| 监听的事件 | IdleStateEvent（超时） | HandshakeComplete（握手完成） |
| 触发时机 | TCP 连接后 60 秒没读到数据 | HTTP 升级 WebSocket 成功后 |
| 握手前会触发吗 | **会**（防僵尸连接） | 不会（必须先握手） |

---

## 7. 客户端一直发的是什么？是心跳包吗

### 握手前 vs 握手后

```
握手前（HTTP 阶段）：
  客户端只发一个 HTTP GET 请求：GET /ws?token=xxx
  → 发完就没了

握手后（WebSocket 阶段）：
  客户端和服务器随时互相发消息
  → 聊天消息、心跳包、文件...
```

### IdleStateHandler 怎么续上的

**它不关心发的是什么内容，只关心"有没有字节进来"。**

前端（wsClient.js）在 WebSocket 连接成功后，每 5 秒发一个文本帧：

```js
ws.onopen = () => ws.send('heart beat');          // 连上发一次
setInterval(() => ws.send('heart beat'), 5000);   // 每5秒发一次
```

这个"heart beat"就是心跳包。内容不重要，重要的是"有数据进来了"。

### 两个心跳

| | IdleStateHandler（Netty 层） | Redis 心跳（业务层） |
|---|---|---|
| 干什么 | 60 秒没收到任何字节就断开 | 记录"用户在线" |
| 谁来续 | 前端发任何 WebSocket 消息都续 | 在 channelRead0 里手动调 Redis |
| 超时后 | 断开 TCP 连接 | Redis key 自动过期 |

如果前端挂了，5 秒一次的心跳停了：
- 60 秒后 Netty 超时 → 断开连接
- channelInactive 触发 → 清 Redis 心跳

---

## 8. TCP 连接和 HTTP 请求是一起的吗

不是。它们在不同网络层，有先后顺序：

```
① TCP 三次握手（操作系统内核干的）
   客户端 → 发 SYN
   服务器 → 回 SYN-ACK
   客户端 → 回 ACK
   → TCP 连接建立，Channel 创建
   → IdleStateHandler 开始 60 秒倒计时

② 在已经建立好的 TCP 连接上，发 HTTP 升级请求
   GET /ws?token=xxx HTTP/1.1
   Upgrade: websocket
```

**类比**：
- TCP 三次握手 = 打电话对方"喂"了一声，线路通了
- HTTP 请求 = 线路通了之后你才说"我要聊微信"

---

## 9. 前端怎么触发三次握手？前端只发了一个 ws:// 请求

因为 TCP 三次握手是**操作系统和浏览器自动完成的**。

当你写 `new WebSocket("ws://localhost:5000/?token=xxx")` 时：

1. 浏览器解析 URL：host=localhost, port=5000, path=/?token=xxx
2. **操作系统自动**建立 TCP 连接（三次握手）——这一步你在 devtools 里看不到
3. TCP 连好后，浏览器自动发 HTTP GET 请求
4. 服务器回 101，升级 WebSocket

devtools 是**应用层调试工具**，只展示 HTTP/WebSocket 协议层的东西。TCP 连接是传输层，由操作系统处理，对开发者不可见。

类比：你在微信发"你好"，微信只显示"发送了：你好"，底下 TCP 连接、数据包分片是操作系统自动做的，你看不到。

---

## 10. HTTP 请求到后端走到哪了？是进 initChannel 那条 Pipeline 吗

**initChannel 是 TCP 连接建立时就执行的，不是 HTTP 请求来了才执行。**

完整时序：

```
① TCP 三次握手完成
   bossGroup 接收到连接，交给 workerGroup
   ↓
② Netty 创建一个新的 Channel
   自动调用 initChannel(ch)
   → 把 Pipeline 搭好（HttpServerCodec → Aggregator → Idle → ...）
   → 此时 Pipeline 已准备好，但还没收到任何数据

③ HTTP 请求字节从网络上来
   → 字节从 Pipeline 头部开始，逐个 Handler 穿过去：
     HttpServerCodec → Aggregator → Idle → HeartBeat → WebSocketProtocol → HandlerWebSocket
```

| 时机 | 做什么 | 对应代码 |
|---|---|---|
| TCP 连接建立时 | **搭 Pipeline**（只做一次） | `initChannel` 里的 `p.addLast(...)` |
| 每次有数据进来 | **数据穿过 Pipeline** | HttpServerCodec → ... → HandlerWebSocket |

类比：initChannel = 新员工入职时安排工位（只做一次）；之后每个任务都顺着流水线走。

---

## 11. bossGroup/workerGroup 怎么处理连接？HTTP 为什么有多个片段

### 线程模型

**不是每个连接一个线程。**

```
bossGroup（1个线程）：只接收新 TCP 连接

workerGroup（默认 CPU核数×2 个线程，比如 8 个）
  线程1：同时看管 1000 个连接
  线程2：同时看管 1000 个连接
  ...
```

- 一个 Channel 一旦分配给某个 worker 线程，**一辈子绑死在这个线程上**
- 但一个线程同时看管很多个连接
- 因为 WebSocket 连接大部分时间是空闲的，不需要独占线程

### HTTP 为什么有多个片段

因为 **TCP 是流协议，不保留消息边界**。

HttpServerCodec 解码出来不是一个完整请求，而是一串对象：

```
包1到达 → HttpRequest（只有请求行和部分头）
包2到达 → HttpContent（body 的一部分）
包3到达 → HttpContent（body 的另一部分）
包4到达 → LastHttpContent（结束标记）
```

为什么会拆：
- **MTU 限制**：一个网络包最大约 1500 字节，超了就拆
- **POST body 大**：上传文件几 MB，不可能一个包发完
- **Nagle 算法**：小包攒一攒再发

`HttpObjectAggregator(64*1024)` 把这些碎片拼成一个完整的 `FullHttpRequest`。对于 WebSocket 握手（GET 请求，无 body），通常一个包就到了。但文件上传时必须聚合。

---

## 12. bossGroup 具体有什么作用

就一件事：**接收新的 TCP 连接**。

```
while(true) {
  等新连接进来
  有 → 接收，创建 Channel，扔给 workerGroup
  没有 → 继续等
}
```

类比：餐厅门口接待，只负责开门迎客、领位，不进厨房炒菜。boss 不会处理数据，worker 不会去接连接，各司其职。

---

## 13. shutdownGracefully 怎么优雅？channelRead0 什么时候触发

### shutdownGracefully

```
Spring 关闭
  ↓
1. bossGroup.shutdownGracefully()
   → 停止接收新连接（端口不再 listen）
   → 但已经连着的连接不断开
  ↓
2. workerGroup.shutdownGracefully()
   → 等正在处理的请求处理完
   → 等正在写的消息写完
   → 等几秒宽限期
   → 然后关闭所有 Channel、释放线程
```

| | 直接退出 | shutdownGracefully() |
|---|---|---|
| 新连接 | 直接断 | 停止接收，老连接不断 |
| 正在写的消息 | 可能写一半就断 | 等写完 |
| 线程 | 强杀 | 等任务执行完再回收 |

### channelRead0 什么时候触发

**每收到一个 WebSocket 文本帧就触发一次。**

前端每 5 秒发 `"heart beat"`，所以 channelRead0 每 5 秒被调一次，在里面续约 Redis 心跳。`frame.text()` 内容是 `"heart beat"`，但我们不读内容，只需要知道"有数据进来了"。

---

## 14. 只能继承 SimpleChannelInboundHandler 吗？@ChannelHandler.Sharable 是干什么的

### SimpleChannelInboundHandler 是什么

帮你省事的基类：
1. **类型过滤**：泛型 `<TextWebSocketFrame>` 表示只处理文本帧，其他类型自动忽略
2. **自动释放内存**：Netty 引用计数管理 ByteBuf，不用手动 release
3. **只需重写 channelRead0**

如果不用它，得继承 `ChannelInboundHandlerAdapter`，自己判断类型、自己释放内存。

| 父类 | 什么时候用 |
|---|---|
| SimpleChannelInboundHandler<TextWebSocketFrame> | 只处理文本消息（我们的 HandlerWebSocket） |
| ChannelInboundHandlerAdapter | 需要处理多种消息类型 |
| ChannelDuplexHandler | 同时处理入站和出站（HandlerHeartBeat 用的它） |

### @ChannelHandler.Sharable

默认一个 Handler 实例只能加到一个 Channel 的 Pipeline。

但我们的 `HandlerWebSocket` 是 Spring Bean，整个项目只有一个实例。每个新连接都把这同一个实例加到新 Channel 的 Pipeline：

```
Channel A: [..., handlerWebSocket（同一个实例）]
Channel B: [..., handlerWebSocket（同一个实例）]
```

不加 `@Sharable`，第二个连接就报错。加了就是告诉 Netty："这个 Handler 是线程安全的，可以共用"。

对比：`HttpServerCodec` 没有 @Sharable，因为它有内部状态，所以每次都 `new HttpServerCodec()`。

---

## 15. Pipeline 完整流程图

```
客户端发字节
  ↓
┌─────────────────┐
│ HttpServerCodec │  字节 ↔ HTTP 请求/响应
└─────────────────┘
  ↓ HttpRequest
┌───────────────────┐
│ HttpObjectAggreg. │  碎片 → 完整请求（64KB上限）
└───────────────────┘
  ↓ FullHttpRequest
┌──────────────────┐
│ IdleStateHandler │  60秒没读到东西 → 触发 READER_IDLE 事件
└──────────────────┘
  ↓ (数据透传)
┌──────────────────┐
│ HandlerHeartBeat │  收到 READER_IDLE → ctx.close()
└──────────────────┘
  ↓ (数据透传)
┌──────────────────────────────┐
│ WebSocketServerProtocolHandler│  GET /ws + Upgrade → 回101 → 升级
└──────────────────────────────┘
  ↓ WebSocket 帧
┌──────────────────┐
│ HandlerWebSocket │  校验token、收心跳、推消息
└──────────────────┘
```

---

## 16. HandlerHeartBeat 能不能也做成 Bean + @Sharable

**可以，但没必要。**

判断标准：这个 Handler 有没有内部状态？

| Handler | 有状态吗 | 怎么做 |
|---|---|---|
| HttpServerCodec | **有**（记录正在解析哪个 HTTP 请求） | 必须每次 new |
| HttpObjectAggregator | **有**（攒着碎片等拼完） | 必须每次 new |
| HandlerHeartBeat | **没有**（只判断 evt 类型，啥也不存） | new 或 Bean 都行 |
| HandlerWebSocket | **没有**（只注入了无状态的工具类） | 做成了 Bean + @Sharable |

HandlerHeartBeat 现在每次 new 一个新实例，虽然浪费一点点，但因为它无状态，完全没问题。

---

## 17. 读超时时，两个 userEventTriggered 都会触发吗

**都会触发，但 HandlerWebSocket 会忽略它。**

事件从 IdleStateHandler 发出后，顺着 Pipeline 往后传，每个 Handler 的 userEventTriggered 都会被调：

```
IdleStateEvent（超时事件）
  ↓
HandlerHeartBeat.userEventTriggered
  → evt instanceof IdleStateEvent? 是 → ctx.close()
  ↓ 继续往后传
WebSocketServerProtocolHandler.userEventTriggered
  → 不认识这个事件，不管
  ↓ 继续往后传
HandlerWebSocket.userEventTriggered
  → evt instanceof HandshakeComplete? 不是，是 IdleStateEvent
  → 直接忽略
```

就像广播：每个 Handler 都听到了，但只处理自己关心的类型。

---

## 18. 两个用户互相发消息，前端怎么发？经过 Pipeline 吗

**A 发消息走的是 HTTP（5050 端口），不是 WebSocket。服务器推给 B 才走 WebSocket。**

完整流程：

```
A 要给 B 发消息：

① A 的前端 → HTTP POST http://localhost:5050/api/chat/sendMessage
   （走 Spring MVC，不是 Netty）
   ↓
   服务器保存消息到 chat_message 表
   ↓
② 服务器找到 B 的 Channel（在 USER_CONTEXT_MAP 里）
   channel.writeAndFlush(new TextWebSocketFrame(JSON))
   ↓
   这个 WebSocket 帧从 B 的 Pipeline 尾部开始，反向穿过：
   HandlerWebSocket ← WebSocketServerProtocolHandler ← ... ← 字节发出去
   ↓
③ B 的前端 onmessage 收到 JSON → 渲染聊天窗口
```

关键点：
- **A 发消息走 HTTP**：就像普通的 REST 请求
- **B 收消息走 WebSocket**：服务器主动推
- **A 的 Channel 不参与发送过程**：A 的消息是通过 HTTP 到服务器的

| | 入站消息（客户端→服务器） | 出站消息（服务器→客户端） |
|---|---|---|
| 方向 | Pipeline 从前往后 | Pipeline 从后往前 |
| 谁发的 | 客户端 | 服务器 |

---

## 19. WebSocket 握手不成功？checkStartsWith 才是关键

### 现象

TCP 连上了，HTTP 请求路径 `/ws?token=xxx` 正确，请求头也标准（Upgrade: websocket），但就是不触发 `HandshakeComplete`，前端一直报"连接失败了准备重连"。

### 原因

简化版构造函数：
```java
new WebSocketServerProtocolHandler("/ws")  // checkStartsWith 默认 false
```

Netty 匹配路径时用的是 `req.uri()`，它返回**完整 URI 包括查询参数**：

```
req.uri() = "/ws?token=xxx"
```

- `checkStartsWith=false`（默认）：精确匹配 `"/ws?token=xxx".equals("/ws")` → **false**，握手不升级
- `checkStartsWith=true`：前缀匹配 `"/ws?token=xxx".startsWith("/ws")` → **true**，握手成功

### 正确写法（照源项目）

```java
new WebSocketServerProtocolHandler(
  "/ws",           // 路径
  null,            // 子协议
  false,           // allowExtensions
  64 * 1024,       // 最大帧大小（字节）= 64KB
  false,           // allowMaskMismatch
  true,            // checkStartsWith ← 关键！
  10000L           // 握手超时（毫秒）= 10秒
)
```

### 参数单位

| 参数 | 值 | 单位 | 含义 |
|---|---|---|---|
| maxFrameSize | 64 * 1024 = 65536 | 字节（byte） | 单条 WebSocket 消息最大 64KB |
| handshakeTimeoutMillis | 10000L | 毫秒（ms） | 握手后 10 秒内没完成就断开 |
