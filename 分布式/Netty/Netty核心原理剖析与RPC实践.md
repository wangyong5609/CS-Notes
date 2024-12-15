> 极客时间《Netty核心原理剖析与RPC实践》笔记

## 00 学好 Netty，是你修炼 Java 内功的必经之路

**为什么学习netty？**

- 掌握底层原理，一定会成为你求职面试的加分项
- 锻炼你的编程思维，对 Java 其他的知识体系起到融会贯通的作用
- Netty 的易用性和可靠性也极大程度上降低了开发者的心智负担
- 正是因为有 Netty 的存在，网络编程领域 Java 才得以与 C++ 并肩而立

**思维导图**

![image ](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/CgqCHl-NAQaABGcDAAZa0pmBs40719.png)

## 01 初识 Netty：为什么 Netty 这么流行？

### 为什么选择 Netty？

Netty 是一款用于高效开发网络应用的 NIO 网络框架，它大大简化了网络应用的开发过程

既然 Netty 是网络应用框架，那我们永远绕不开以下几个核心关注点：

- **I/O 模型、线程模型和事件处理机制；**
- **易用性 API 接口；**
- **对数据协议、序列化的支持。**

我们之所以会最终选择 Netty，是因为 Netty 围绕这些核心要点可以做到尽善尽美，其健壮性、性能、可扩展性在同领域的框架中都首屈一指。下面我们从以下三个方面一起来看看，Netty 到底有多厉害

#### 高性能，低延迟

 I/O 多路复用

![3.png](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/CgqCHl-OnV2ADXBhAAFUZ6oiz6U529.png)

多路复用实现了**一个线程处理多个 I/O 句柄的操作**。多路指的是多个**数据通道**，复用指的是使用一个或多个固定线程来处理每一个 Socket。select、poll、epoll 都是 I/O 多路复用的具体实现，线程一次 select 调用可以获取内核态中多个数据通道的数据状态。多路复用解决了同步阻塞 I/O 和同步非阻塞 I/O 的问题，是一种非常高效的 I/O 模型。

Netty 的 I/O 模型是基于非阻塞 I/O 实现的，底层依赖的是 JDK NIO 框架的多路复用器 Selector。一个多路复用器 Selector 可以同时轮询多个 Channel，采用 epoll 模式后，只需要一个线程负责 Selector 的轮询，就可以接入成千上万的客户端。

在 I/O 多路复用的场景下，当有数据处于就绪状态后，需要一个事件分发器（Event Dispather），它负责将读写事件分发给对应的读写事件处理器（Event Handler）。事件分发器有两种设计模式：Reactor 和 Proactor，**Reactor 采用同步 I/O， Proactor 采用异步 I/O**。

![6.png](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/Ciqc1F-NKE-AWqZfAARsOnKW3pg690.png)

上图所描述的便是 Netty 所采用的主从 Reactor 多线程模型，所有的 I/O 事件都注册到一个 I/O 多路复用器上，当有 I/O 事件准备就绪后，I/O 多路复用器会将该 I/O 事件通过事件分发器分发到对应的事件处理器中。该线程模型避免了同步问题以及多线程切换带来的资源开销，真正做到高性能、低延迟

#### 完美弥补 Java NIO 的缺陷

Netty 相比 JDK NIO 有哪些突出的优势：

- **易用性。** Netty 在 NIO 基础上进行了更高层次的封装，屏蔽了 NIO 的复杂性；Netty 封装了更加人性化的 API，统一的 API（阻塞/非阻塞） 大大降低了开发者的上手难度；与此同时，Netty 提供了很多开箱即用的工具，例如常用的行解码器、长度域解码器等，而这些在 JDK NIO 中都需要你自己实现。
- **稳定性。** Netty 更加可靠稳定，修复和完善了 JDK NIO 较多已知问题，例如臭名昭著的 select 空转导致 CPU 消耗 100%，TCP 断线重连，keep-alive 检测等问题。
- **可扩展性。** Netty 的可扩展性在很多地方都有体现，这里我主要列举其中的两点：一个是可定制化的线程模型，用户可以通过启动的配置参数选择 Reactor 线程模型；另一个是可扩展的事件驱动模型，将框架层和业务层的关注点分离。大部分情况下，开发者只需要关注 ChannelHandler 的业务逻辑实现。

#### 更低的资源消耗

作为网络通信框架，需要处理海量的网络数据，那么必然面临有大量的网络对象需要创建和销毁的问题，对于 JVM GC 并不友好。为了降低 JVM 垃圾回收的压力，Netty 主要采用了两种优化手段：

- **对象池复用技术。** Netty 通过复用对象，避免频繁创建和销毁带来的开销。
- **零拷贝技术。** 除了操作系统级别的零拷贝技术外，Netty 提供了更多面向用户态的零拷贝技术，例如 Netty 在 I/O 读写时直接使用 DirectBuffer，从而避免了数据在堆内存和堆外内存之间的拷贝。

### 谁在使用 Netty？

Netty 经过很多出名产品在线上的大规模验证，其健壮性和稳定性都被业界认可，其中典型的产品有一下几个。

- 服务治理：Apache Dubbo、gRPC。
- 大数据：Hbase、Spark、Flink、Storm。
- 搜索引擎：Elasticsearch。
- 消息队列：RocketMQ、ActiveMQ。

## 02 纵览全局：把握 Netty 整体架构脉络

### Netty 整体结构

![Drawing 0.png](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/CgqCHl-NO7eATPMMAAH8t8KvehQ985.png)

#### 1. Core 核心层

Core 核心层是 Netty 最精华的内容，它提供了底层网络通信的通用抽象和实现，包括可扩展的事件模型、通用的通信 API、支持零拷贝的 ByteBuf 等。

#### 2. Protocol Support 协议支持层

协议支持层基本上覆盖了主流协议的编解码实现，如 HTTP、SSL、Protobuf、压缩、大文件传输、WebSocket、文本、二进制等主流协议，此外 Netty 还支持自定义应用层协议。Netty 丰富的协议支持降低了用户的开发成本，基于 Netty 我们可以快速开发 HTTP、WebSocket 等服务。

#### 3. Transport Service 传输服务层

传输服务层提供了网络传输能力的定义和实现方法。它支持 Socket、HTTP 隧道、虚拟机管道等传输方式。Netty 对 TCP、UDP 等数据传输做了抽象和封装，用户可以更聚焦在业务逻辑实现上，而不必关系底层数据传输的细节。

Netty 的模块设计具备较高的**通用性和可扩展性**，它不仅是一个优秀的网络框架，还可以作为网络编程的工具箱。Netty 的设计理念非常优雅，值得我们学习借鉴。

### Netty 逻辑架构

Netty 的逻辑处理架构为典型网络分层架构设计，共分为网络通信层、事件调度层、服务编排层，每一层各司其职。

![Drawing 1.png](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/Ciqc1F-NO9KAUOtaAAE1S5uRlDE275.png)

#### 网络通信层

网络通信层的职责是执行网络 I/O 的操作。它支持多种网络协议和 I/O 模型的连接操作。当网络数据读取到内核缓冲区后，会触发各种网络事件，这些网络事件会分发给事件调度层进行处理。

网络通信层的**核心组件**包含**BootStrap、ServerBootStrap、Channel**三个组件。

##### **BootStrap & ServerBootStrap**

Bootstrap 是“引导”的意思，它主要负责整个 Netty 程序的启动、初始化、服务器连接等过程，它相当于一条主线，串联了 Netty 的其他核心组件。

如下图所示，Netty 中的引导器共分为两种类型：一个为**用于客户端引导的 Bootstrap**，另一个为**用于服务端引导的 ServerBootStrap**，它们都继承自抽象类 AbstractBootstrap。

![Drawing 2.png](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/Ciqc1F-NO9yAeCsoAAHf2YCqjsQ005.png)

Bootstrap 和 ServerBootStrap两者非常重要的区别:

- Bootstrap 可用于连接远端服务器，只绑定一个 EventLoopGroup。
- ServerBootStrap 则用于服务端启动绑定本地端口，会绑定两个 EventLoopGroup，这两个 EventLoopGroup 通常称为 Boss 和 Worker。

ServerBootStrap 中的 Boss 和 Worker 是什么角色呢？它们之间又是什么关系？这里的 Boss 和 Worker **可以理解为“老板”和“员工”的关系**。每个服务器中都会有一个 Boss，也会有一群做事情的 Worker。Boss 会不停地接收新的连接，然后将连接分配给一个个 Worker 处理连接。

有了 Bootstrap 组件，我们可以更加方便地配置和启动 Netty 应用程序，它是整个 Netty 的入口，串接了 Netty 所有核心组件的初始化工作。

##### **Channel**

Channel 的字面意思是“通道”，它是网络通信的载体。Channel提供了基本的 API 用于网络 I/O 操作，如 register、bind、connect、read、write、flush 等。

下图是 Channel 家族的图谱。AbstractChannel 是整个家族的基类，派生出 AbstractNioChannel、AbstractOioChannel、AbstractEpollChannel 等子类，每一种都代表了不同的 I/O 模型和协议类型。常用的 Channel 实现类有：

![Drawing 3.png](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/Ciqc1F-NO_CABg8ZAAW1jtSV2JU753.png)

- NioServerSocketChannel 异步 TCP 服务端。
- NioSocketChannel 异步 TCP 客户端。
- OioServerSocketChannel 同步 TCP 服务端。
- OioSocketChannel 同步 TCP 客户端。
- NioDatagramChannel 异步 UDP 连接。
- OioDatagramChannel 同步 UDP 连接。

当然 Channel 会有多种状态，如**连接建立、连接注册、数据读写、连接销毁**等。随着状态的变化，Channel 处于不同的生命周期，每一种状态都会绑定相应的事件回调，下面的表格我列举了 Channel 最常见的状态所对应的事件回调。

| 事件                | 说明                                          |
| :------------------ | :-------------------------------------------- |
| channelRegistered   | Channel 创建后被注册到 EventLoop 上           |
| channelUnregistered | Channel 创建后未注册或者从 EventLoop 取消注册 |
| channelActive       | Channel 处于就绪状态，可以被读写              |
| channelInactive     | Channel 处于非就绪状态                        |
| channelRead         | Channel 可以从远端读取到数据                  |
| channelReadComplete | Channel 读取数据完成                          |

有关网络通信层我就先介绍到这里，简单地总结一下。BootStrap 和 ServerBootStrap 分别负责客户端和服务端的启动，它们是非常强大的辅助工具类；Channel 是网络通信的载体，提供了与底层 Socket 交互的能力。那么 **Channel 生命周期内的事件都是如何被处理的呢？那就是 Netty 事件调度层的工作职责**了。

#### 事件调度层

事件调度层的职责是通过 Reactor 线程模型对各类事件进行聚合处理，通过 Selector 主循环线程集成多种事件（ I/O 事件、信号事件、定时事件等），实际的业务处理逻辑是交由服务编排层中相关的 Handler 完成。

EventLoopGroup 本质是一个线程池，主要负责接收 I/O 请求，并分配线程执行处理请求

![Drawing 4.png](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/CgqCHl-NPG6APzDfAAbX5ACAFh8001.png)

从上图中，我们可以总结出 EventLoopGroup、EventLoop、Channel 的几点关系。

1. 一个 EventLoopGroup 往往包含一个或者多个 EventLoop。EventLoop 用于处理 Channel 生命周期内的所有 I/O 事件，如 accept、connect、read、write 等 I/O 事件。
2. EventLoop 同一时间会与一个线程绑定，每个 EventLoop 负责处理多个 Channel。
3. 每新建一个 Channel，EventLoopGroup 会选择一个 EventLoop 与其绑定。该 Channel 在生命周期内都可以对 EventLoop 进行多次绑定和解绑。

下图是 EventLoopGroup 的家族图谱。可以看出 Netty 提供了 EventLoopGroup 的多种实现，而且 EventLoop 则是 EventLoopGroup 的子接口，所以也可以把 EventLoop 理解为 EventLoopGroup，但是它只包含一个 EventLoop 。

![Drawing 5.png](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/Ciqc1F-NPHWAH8ExAAkQr2tQ9xM350.png)

EventLoopGroup 的实现类是 NioEventLoopGroup，NioEventLoopGroup 也是 Netty 中最被推荐使用的线程模型。NioEventLoopGroup 继承于 MultithreadEventLoopGroup，是基于 NIO 模型开发的，可以把 NioEventLoopGroup 理解为一个线程池，每个线程负责处理多个 Channel，而同一个 Channel 只会对应一个线程。

Netty 通过创建不同的 EventLoopGroup 参数配置，可以支持 Reactor 的三种线程模型：

1. **单线程模型**：EventLoopGroup 只包含一个 EventLoop，Boss 和 Worker 使用同一个EventLoopGroup；
2. **多线程模型**：EventLoopGroup 包含多个 EventLoop，Boss 和 Worker 使用同一个EventLoopGroup；
3. **主从多线程模型**：EventLoopGroup 包含多个 EventLoop，Boss 是主 Reactor，Worker 是从 Reactor，它们分别使用不同的 EventLoopGroup，主 Reactor 负责新的网络连接 Channel 创建，然后把 Channel 注册到从 Reactor

#### 服务编排层

服务编排层的职责是负责组装各类服务，它是 Netty 的核心处理链，用以实现网络事件的动态编排和有序传播。

服务编排层的**核心组件**包括 **ChannelPipeline**、**ChannelHandler、ChannelHandlerContext**。

ChannelPipeline 是 Netty 的核心编排组件，**负责组装各种 ChannelHandler**，实际数据的编解码以及加工处理操作都是由 ChannelHandler 完成的。ChannelPipeline 可以理解为**ChannelHandler 的实例列表**——内部通过双向链表将不同的 ChannelHandler 链接在一起

![Drawing 7.png](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/CgqCHl-NPKaASxvgAAFHMPYQFhM940.png)

每创建一个 Channel 都会绑定一个新的 ChannelPipeline，ChannelPipeline 中每加入一个 ChannelHandler 都会绑定一个 ChannelHandlerContext。

ChannelHandlerContext 用于保存 ChannelHandler 上下文·

![Drawing 8.png](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/CgqCHl-NPK-ADq0pAABb1k5Zwu8681.png)

### 组件关系梳理

当你了解每个 Netty 核心组件的概念后。你会好奇这些组件之间如何协作？结合客户端和服务端的交互流程，我画了一张图，为你完整地梳理一遍 Netty 内部逻辑的流转。

![Drawing 9.png](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/Ciqc1F-NPLeAPdjRAADyud16HmQ759.png)

- 服务端启动初始化时有 Boss EventLoopGroup 和 Worker EventLoopGroup 两个组件，其中 Boss 负责监听网络连接事件。当有新的网络连接事件到达时，则将 Channel 注册到 Worker EventLoopGroup。
- Worker EventLoopGroup 会被分配一个 EventLoop 负责处理该 Channel 的读写事件。每个 EventLoop 都是单线程的，通过 Selector 进行事件循环。
- 当客户端发起 I/O 读写事件时，服务端 EventLoop 会进行数据的读取，然后通过 Pipeline 触发各种监听器进行数据的加工处理。
- 客户端数据会被传递到 ChannelPipeline 的第一个 ChannelInboundHandler 中，数据处理完成后，将加工完成的数据传递给下一个 ChannelInboundHandler。
- 当数据写回客户端时，会将处理结果在 ChannelPipeline 的 ChannelOutboundHandler 中传播，最后到达客户端。

### Netty 源码结构

Netty 源码分为多个模块，模块之间职责划分非常清楚。如同上文整体功能模块一样，Netty 源码模块的划分也是基本契合的。

![Drawing 10.png](./Netty%E6%A0%B8%E5%BF%83%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E4%B8%8ERPC%E5%AE%9E%E8%B7%B5.assets/Ciqc1F-NPNOAcv3mAA2V7Vw0BwM920.png)

我们不仅可以使用 Netty all-in-one 的 Jar 包，也可以单独使用其中某些工具包。下面我根据 Netty 的分层结构以及实际的业务场景具体介绍 Netty 中常用的工具包。

#### Core 核心层模块

**netty-common**模块是 Netty 的核心基础包，提供了丰富的工具类，其他模块都需要依赖它。在 common 模块中，常用的包括**通用工具类**和**自定义并发包**。

- 通用工具类：比如定时器工具 TimerTask、时间轮 HashedWheelTimer 等。
- 自定义并发包：比如异步模型Future & Promise、相比 JDK 增强的 FastThreadLocal 等。

在**netty-buffer 模块中**Netty自己实现了的一个更加完备的**ByteBuf 工具类**，用于网络通信中的数据载体。由于人性化的 Buffer API 设计，它已经成为 Java ByteBuffer 的完美替代品。ByteBuf 的动态性设计不仅解决了 ByteBuffer 长度固定造成的内存浪费问题，而且更安全地更改了 Buffer 的容量。此外 Netty 针对 ByteBuf 做了很多优化，例如缓存池化、减少数据拷贝的 CompositeByteBuf 等。

**netty-resover**模块主要提供了一些有关**基础设施**的解析工具，包括 IP Address、Hostname、DNS 等。

#### Protocol Support 协议支持层模块

**netty-codec**模块主要负责编解码工作，通过编解码实现原始字节数据与业务实体对象之间的相互转化。如下图所示，Netty 支持了大多数业界主流协议的编解码器，如 HTTP、HTTP2、Redis、XML 等，为开发者节省了大量的精力。此外该模块提供了抽象的编解码类 ByteToMessageDecoder 和 MessageToByteEncoder，通过继承这两个类我们可以轻松实现自定义的编解码逻辑。

**netty-handler**模块主要负责数据处理工作。Netty 中关于数据处理的部分，本质上是一串有序 handler 的集合。netty-handler 模块提供了开箱即用的 ChannelHandler 实现类，例如日志、IP 过滤、流量整形等，如果你需要这些功能，仅需在 pipeline 中加入相应的 ChannelHandler 即可。

#### Transport Service 传输服务层模块

netty-transport 模块可以说是 Netty 提供数据**处理和传输的核心模块**。该模块提供了很多非常重要的接口，如 Bootstrap、Channel、ChannelHandler、EventLoop、EventLoopGroup、ChannelPipeline 等。其中 Bootstrap 负责客户端或服务端的启动工作，包括创建、初始化 Channel 等；EventLoop 负责向注册的 Channel 发起 I/O 读写操作；ChannelPipeline 负责 ChannelHandler 的有序编排，这些组件在介绍 Netty 逻辑架构的时候都有所涉及。

## 03 引导器作用：客户端和服务端启动都要做些什么？

#### 服务端启动类

所有 Netty 服务端的启动类都可以采用如下代码结构进行开发。简单梳理一下流程：首先创建引导器；然后配置线程模型，通过引导器绑定业务逻辑处理器，并配置一些网络参数；最后绑定端口，就可以完成服务器的启动了。

```java
public class HttpServer {

    public void start(int port) throws Exception {

        EventLoopGroup bossGroup = new NioEventLoopGroup();

        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {

            ServerBootstrap b = new ServerBootstrap();

            b.group(bossGroup, workerGroup)

                    .channel(NioServerSocketChannel.class)

                    .localAddress(new InetSocketAddress(port))

                    .childHandler(new ChannelInitializer<SocketChannel>() {

                        @Override

                        public void initChannel(SocketChannel ch) {

                            ch.pipeline()

                                    .addLast("codec", new HttpServerCodec())                  // HTTP 编解码

                                    .addLast("compressor", new HttpContentCompressor())       // HttpContent 压缩

                                    .addLast("aggregator", new HttpObjectAggregator(65536))   // HTTP 消息聚合

                                    .addLast("handler", new HttpServerHandler());             // 自定义业务逻辑处理器

                        }

                    })

                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind().sync();

            System.out.println("Http Server started， Listening on " + port);

            f.channel().closeFuture().sync();

        } finally {

            workerGroup.shutdownGracefully();

            bossGroup.shutdownGracefully();

        }

    }

    public static void main(String[] args) throws Exception {

        new HttpServer().start(8088);

    }

}
```

### 引导器实践指南

Netty 服务端的启动过程大致分为三个步骤：

1. **配置线程池；**
2. **Channel 初始化；**
3. **端口绑定。**

#### 配置线程池

Netty 是采用 Reactor 模型进行开发的，可以非常容易切换三种 Reactor 模式：**单线程模式**、**多线程模式**、**主从多线程模式**。

#### **单线程模式**

Reactor 单线程模型所有 I/O 操作都由一个线程完成，所以只需要启动一个 EventLoopGroup 即可。

```csharp
EventLoopGroup group = new NioEventLoopGroup(1);

ServerBootstrap b = new ServerBootstrap();

b.group(group)
```

#### **多线程模式**

Reactor 单线程模型有非常严重的性能瓶颈，因此 Reactor 多线程模型出现了。在 Netty 中使用 Reactor 多线程模型与单线程模型非常相似，区别是 NioEventLoopGroup 可以不需要任何参数，它默认会启动 2 倍 CPU 核数的线程。当然，你也可以自己手动设置固定的线程数。

```csharp
EventLoopGroup group = new NioEventLoopGroup();

ServerBootstrap b = new ServerBootstrap();

b.group(group)
```

#### **主从多线程模式**

在大多数场景下，我们采用的都是**主从多线程 Reactor 模型**。Boss 是主 Reactor，Worker 是从 Reactor。它们分别使用不同的 NioEventLoopGroup，主 Reactor 负责处理 Accept，然后把 Channel 注册到从 Reactor 上，从 Reactor 主要负责 Channel 生命周期内的所有 I/O 事件。

```java
EventLoopGroup bossGroup = new NioEventLoopGroup();

EventLoopGroup workerGroup = new NioEventLoopGroup();

ServerBootstrap b = new ServerBootstrap();

b.group(bossGroup, workerGroup)
```

### Channel 初始化

#### 设置 Channel 类型

推荐 Netty 服务端采用 NioServerSocketChannel 作为 Channel 的类型，客户端采用 NioSocketChannel。设置方式如下：

```cpp
 b.channel(NioServerSocketChannel.class);
```

#### 设置 Channel 参数

ServerBootstrap 设置 Channel 属性有**option**和**childOption**两个方法，option 主要负责设置 Boss 线程组，而 childOption 对应的是 Worker 线程组。

这里我列举了经常使用的参数含义，你可以结合业务场景，按需设置。

| 参数                   | 含义                                                         |
| :--------------------- | :----------------------------------------------------------- |
| SO_KEEPALIVE           | 设置为 true 代表启用了 TCP SO_KEEPALIVE 属性，TCP 会主动探测连接状态，即连接保活 |
| SO_BACKLOG             | 已完成三次握手的请求队列最大长度，同一时刻服务端可能会处理多个连接，在高并发海量连接的场景下，该参数应适当调大 |
| TCP_NODELAY            | Netty 默认是 true，表示立即发送数据。如果设置为 false 表示启用 Nagle 算法，该算法会将 TCP 网络数据包累积到一定量才会发送，虽然可以减少报文发送的数量，但是会造成一定的数据延迟。Netty 为了最小化数据传输的延迟，默认禁用了 Nagle 算法 |
| SO_SNDBUF              | TCP 数据发送缓冲区大小                                       |
| SO_RCVBUF              | TCP数据接收缓冲区大小，TCP数据接收缓冲区大小                 |
| SO_LINGER              | 设置延迟关闭的时间，等待缓冲区中的数据发送完成               |
| CONNECT_TIMEOUT_MILLIS | 建立连接的超时时间                                           |

#### 端口绑定

在完成上述 Netty 的配置之后，bind() 方法会真正触发启动，sync() 方法则会阻塞，直至整个启动过程完成.

## 04 事件调度层：为什么 EventLoop 是 Netty 的精髓？