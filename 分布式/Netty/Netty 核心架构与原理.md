# Netty 核心架构与原理

> 文章是我学习黑马程序员 Netty 课程的笔记，适合作为 Netty 入门学习，也可以作为学习 Netty 源码前的铺垫。

## 概述

[Netty](https://netty.io/)是由JBOSS提供的一个java开源框架，现为 Github上的独立项目。Netty提供**非阻塞的、事件驱动**的网络应用程序框架和工具，用以快速开发**高性能、高可靠性的网络服务器和客户端程序**

- 本质：网络应用程序框架
- 实现：异步、事件驱动
- 特性：高性能、可维护、快速开发
- 用途：开发服务器和客户端

> 如何理解异步：线程同步、异步是相对的，在请求或执行过程中，如果会阻塞等待，就是同步操作，反之就是异步操作

![image-20231223140916958](https://qny.bbbwdc.com/blog/image-20231223140916958.png)

客户端请求后无需等待返回结果则为异步。

## 核心架构

![img](https://qny.bbbwdc.com/blog/components.png)

### 核心

- 可扩展的事件模型
- 统一的通信API，简化了通信编码
- 零拷贝机制与丰富的字节缓冲区

### 传输服务

- 支持socket以及datagram（数据报）
- HTTP传输服务
- In-VM Pipe （管道协议，是jvm的一种进程）

### 协议支持

- HTTP 以及 Websocket
- SSL 安全套接字协议支持
- Google Protobuf （序列化框架）
- 支持zlib、gzip压缩
- 支持大文件的传输RTSP（实时流传输协议，是TCP/IP协议体系中的一个应用层协议）
- 支持二进制协议并且提供了完整的单元测试

## Netty对三种IO的支持

在 Netty 4.1版本中，BIO支持已标记`Deprecated`，移除了对AIO的支持

![image-20231223141908703](https://qny.bbbwdc.com/blog/image-20231223141908703.png)

![image-20231223143611529](https://qny.bbbwdc.com/blog/image-20231223143611529.png)

## Netty中的Reactor实现

Netty线程模型是基于Reactor模型实现的，对Reactor三种模式都有非常好的支持，并做了一定的改进，也非常的灵活，一般情况，在服务端会采用主从架构模型

![image-20231223143826827](https://qny.bbbwdc.com/blog/image-20231223143826827.png)

### 工作流程

1. Netty 抽象出两组线程池：BossGroup 和 WorkerGroup，每个线程池中都有EventLoop 线程。BossGroup中的线程专门负责和客户端建立连接，WorkerGroup 中的线程专门负责处理连接上的读写, EventLoopGroup 相当于一个事件循环组，这个组中含有多个事件循环
2. EventLoop 表示一个不断循环的执行事件处理的线程，每个EventLoop 都包含一个 Selector，用于监听注册在其上的 Socket 网络连接（Channel）。
3. 每个 Boss EventLoop 中循环执行以下三个步骤：
   1. select：轮训注册在其上的 ServerSocketChannel 的 accept 事件（OP_ACCEPT 事件）
   2. processSelectedKeys：处理 accept 事件，与客户端建立连接，生成一个SocketChannel，并将其注册到某个 Worker EventLoop 上的 Selector 上
   3. runAllTasks：再去以此循环处理任务队列中的其他任务
4. 每个 Worker EventLoop 中循环执行以下三个步骤：
   1. select：轮训注册在其上的SocketChannel 的 read/write 事件（OP_READ/OP_WRITE 事件） 
   2. processSelectedKeys：在对应的SocketChannel 上处理 read/write 事件 
   3. runAllTasks：再去以此循环处理任务队列中的其他任务
5. 在以上两个processSelectedKeys步骤中，会使用 Pipeline（管道），Pipeline 中引用了 Channel，即通过 Pipeline 可以获取到对应的 Channel，Pipeline 中维护了很多的处理器（拦截处理器、过滤处理器、自定义处理器等）

> 对比主从Reactor-多线程模型就会发现，Boss EventLoopGroup 就是 mainReactor，Worker EventLoopGroup 就是 subReactor

![image-20231223144027155](https://qny.bbbwdc.com/blog/image-20231223144027155.png)

## Pipeline 和 Handler

`ChannelPipeline` 提供了 `ChannelHandler` **链的容器**。以服务端程序为例，客户端发送过来的数据要接收，读取处理，我们称数据是入站的，需要经过一系列Handler处理后；如果服务器想向客户端写回数据，也需要经过一系列Handler处理，我们称数据是出站的

![image-20231223203543326](https://qny.bbbwdc.com/blog/image-20231223203543326.png)

### ChannelHandler 分类

对于数据的出站和入站，有着不同的ChannelHandler类型与之对应：

- `ChannelInboundHandler` 入站事件处理器
- `ChannelOutBoundHandler` 出站事件处理器
- `ChannelHandlerAdapter` 提供了一些方法的默认实现，可减少用户对于ChannelHandler的编写
- `ChannelDuplexHandler` 混合型，既能处理入站事件又能处理出站事件

![image-20231223204219699](https://qny.bbbwdc.com/blog/image-20231223204219699.png)

> inbound入站事件处理顺序（方向）是由链表的头到链表尾，outbound事件的处理顺序是由链表尾到链表头。
>
> inbound入站事件由netty内部触发，最终由netty外部的代码消费。数据是netty读完成后交给业务代码使用，所以说是由外部代码消费
>
> outbound事件由netty外部的代码触发，最终由netty内部消费。什么时候写数据是由业务代码出发的，然后netty帮你处理好发给客户端

![image-20231223204351129](https://qny.bbbwdc.com/blog/image-20231223204351129.png)



## Netty如何使用Reactor模式

前面说了 netty 是基于Reactor模型实现的，那具体是怎么用的呢？

![image-20231224212016498](https://qny.bbbwdc.com/blog/image-20231224212016498.png)

NioEventLoopEvent 可以理解为一个线程池，传参数为 1 就是只创建一个线程，这就是**Reactor单线程模式**

NioEventLoopEvent 构造函数不传参数的话默认会创建当前主机逻辑内核数量的 2 倍数量的 NioEventLoop。

![image-20231224212612971](https://qny.bbbwdc.com/blog/image-20231224212612971.png)

![image-20231224212708548](https://qny.bbbwdc.com/blog/image-20231224212708548.png)

ServerBootstrap 是一个核心引导启动类，我们来看它的构造函数

![image-20231224213425469](https://qny.bbbwdc.com/blog/image-20231224213425469.png)

所以可以使用两个group构建父子关系，父NioEventLoopEvent 使用单线程多线程都可以，子NioEventLoopEvent 使用多线程，这就是主从 Reactor 多线程模式

## Hello world

下面我们来写一个简易的CS示例

> **Maven依赖**
>
> <dependency>
>     <groupId>io.netty</groupId>
>     <artifactId>netty-all</artifactId>
>     <version>4.1.42.Final</version>
> </dependency>

### Netty Server

```java
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class NettyServer {
    public static void main(String[] args) {
        NettyServer nettyServer = new NettyServer();
        // 指定服务端端口
        nettyServer.start(8088);
    }

    public void start(int port) {
        // 使用Reactor主从多线程模式，准备 Boos 和 worker
        NioEventLoopGroup boos = new NioEventLoopGroup(1);
        NioEventLoopGroup worker = new NioEventLoopGroup();
        try {
            // 核心引导类
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap
                    // 设置父子线程组
                    .group(boos, worker)
                    // 说明服务端通道的实现类（便于netty做反射处理）
                    .channel(NioServerSocketChannel.class)
                    // handler()方法用于给 BossGroup 设置业务处理器
                    // childHandler()方法用于给 WorkerGroup 设置业务处理器
                    .handler(new LoggingHandler(LogLevel.INFO))
                    // 创建一个通道初始化对象
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // 这里方法是有客户端新的连接过来,Channel初始化时才会回调
                            ChannelPipeline pipeline = ch.pipeline();
                        
                            // 将OutBoundHandler放在后面
//                            pipeline.addLast(new NettyServerInBoundHandler());
//                            pipeline.addLast(new NettyServerOutBoundHandler1());
//                            pipeline.addLast(new NettyServerOutBoundHandler2());
                            
                            // 将OutBoundHandler放在前面
                            pipeline.addFirst(new NettyServerOutBoundHandler1());
                            pipeline.addFirst(new NettyServerOutBoundHandler2());
                            pipeline.addLast(new NettyServerInBoundHandler());
                        
                        }
                    });
            // 绑定端口启动
            ChannelFuture future = serverBootstrap.bind(port).sync();
            // 监听端口的关闭
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 释放资源
            worker.shutdownGracefully();
            boos.shutdownGracefully();
        }
    }

    /**
     * 自定义一个 Handler，需要继承 Netty 规定好的某个 HandlerAdapter（规范）
     * InboundHandler 用于处理数据流入本端（服务端）的 IO 事件
     * OutboundHandler 用于处理数据流出本端（服务端）的 IO 事件
     */
    static class NettyServerInBoundHandler extends ChannelInboundHandlerAdapter {
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // SocketChannel准备好的时候回调这个函数
            System.out.println("NettyServerInBoundHandler channelActive");
            super.channelActive(ctx);
        }

        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // SocketChannel断开连接的时候回调这个函数
            System.out.println("NettyServerInBoundHandler channelInactive");
            super.channelInactive(ctx);
        }

        /**
         * 当通道有数据可读时执行
         *
         * @param ctx 当前handler的上下文对象，可以从中取得相关联的 Pipeline、Channel、客户端地址等
         * @param msg 客户端发送的数据
         * @throws Exception
         */
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("NettyServerInBoundHandler channelRead");
            // msg其实是一个ByteBuf对象，Reactor中的缓冲区是ByteBuffer, netty中的缓冲区是ByteBuf
            ByteBuf byteBuf = (ByteBuf) msg;
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            String content = new String(bytes, Charset.defaultCharset());
            System.out.println("收到的数据" + content);

            super.channelRead(ctx, msg);
        }

        /**
         * 数据读取完毕后执行
         *
         * @param ctx 上下文对象
         * @throws Exception
         */
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            System.out.println("NettyServerInBoundHandler channelReadComplete");
            // 向客户端写回数据
            Channel channel = ctx.channel();
            // 写回数据也是要放到ByteBuf里面的
            // 分配一个ByteBuf
            ByteBuf buffer = ctx.alloc().buffer();
            buffer.writeBytes("Hello, Netty Client".getBytes(StandardCharsets.UTF_8));
            channel.writeAndFlush(buffer);
            // 如果使用context写回数据，事件会从当前handler流向头部，如果这个handler后面还有outboundHandler，那么outboundHandler不会执行
//            ctx.writeAndFlush(buffer);
            super.channelReadComplete(ctx);
            
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.out.println("NettyServerInBoundHandler exceptionCaught," + cause.getMessage());
            super.exceptionCaught(ctx, cause);
        }
    }

    static class NettyServerOutBoundHandler1 extends ChannelOutboundHandlerAdapter {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            System.out.println("ServerOutboundHandler1 " + ((ByteBuf) msg).toString(StandardCharsets.UTF_8));
            super.write(ctx, msg, promise);
        }
    }

    static class NettyServerOutBoundHandler2 extends ChannelOutboundHandlerAdapter {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            System.out.println("ServerOutboundHandler2 " + ((ByteBuf) msg).toString(StandardCharsets.UTF_8));
            super.write(ctx, msg, promise);
        }
    }
}
```

### Netty Client

```java
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;

public class NettyClient {
    public static void main(String[] args) {
        NettyClient nettyClient = new NettyClient();
        nettyClient.connect("127.0.0.1", 8088);
    }

    public void connect(String host, int port) {
        // 客户端只需要一个事件循环组，可以看做 BossGroup
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            // 创建客户端的启动对象
            Bootstrap bootstrap = new Bootstrap();
            bootstrap
                    // 设置线程组
                    .group(group)
                    // 说明客户端通道的实现类（便于 Netty 做反射处理）
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new NettyClientInboundHandler());
                        }
                    });
            System.out.println("client is ready...");

            ChannelFuture channelFuture = bootstrap.connect(host, port);
            // 对通道关闭进行监听
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            group.shutdownGracefully();
        }

    }

    static class NettyClientInboundHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // SocketChannel准备好的时候回调这个函数
            System.out.println("NettyClientInboundHandler channelActive");
            // 向服务器发送数据
            ctx.writeAndFlush(
                    // Unpooled 类是 Netty 提供的专门操作缓冲区的工具
                    // 类，copiedBuffer 方法返回的 ByteBuf 对象类似于
                    // NIO 中的 ByteBuffer，但性能更高
                    Unpooled.copiedBuffer(
                            "Hello, Netty Server!",
                            CharsetUtil.UTF_8
                    )
            );
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // SocketChannel断开连接的时候回调这个函数
            System.out.println("NettyClientInboundHandler channelInactive");
            super.channelInactive(ctx);
        }

        /**
         * 当通道有数据可读时执行
         *
         * @param ctx 当前handler的上下文对象，可以从中取得相关联的 Pipeline、Channel、客户端地址等
         * @param msg 客户端发送的数据
         * @throws Exception
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("NettyClientInboundHandler channelRead");
            // msg其实是一个ByteBuf对象，Reactor中的缓冲区是ByteBuffer, netty中的缓冲区是ByteBuf
            ByteBuf byteBuf = (ByteBuf) msg;
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            String content = new String(bytes, Charset.defaultCharset());
            System.out.println("收到的数据" + content);

            super.channelRead(ctx, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            super.channelReadComplete(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
        }
    }
}
```

### inbound/outbound 加载顺序和执行顺序

![image-20240127205550863](https://qny.bbbwdc.com/blog/image-20240127205550863.png)

> addLast：新添加的在最后
>
> addFirst: 新添加的在最前
>
> InboundHandler：从左往右执行，顺序执行
>
> OutboundHandler：从右往左执行，逆序执行

### 回写数据时会经过哪些outboundHandler?

![image-20240127212415540](https://qny.bbbwdc.com/blog/image-20240127212415540.png)

所以当通过ChannelHandlerContext对象进行数据回写时，右侧的handler不会被执行

### 如何让outboundHandler 一定能执行到？  

> 把OutboundHandler排在前面

![image-20240127212721498](https://qny.bbbwdc.com/blog/image-20240127212721498.png)

### 出站事件传播和outboundHandler中的数据修改  

![image-20240127213759524](https://qny.bbbwdc.com/blog/image-20240127213759524.png)

> 上面提到`如果是通过Channel对象进行数据回写，事件会从pipeline尾部流向头部 `,所以这里会造成递归问题，导致堆栈溢出



## Netty核心组件剖析

### Bootstrap

Bootstrap是引导的意思，它的作用是配置整个Netty程序，将各个组件都串起来，最后绑定端口、启动Netty服务



Netty中提供了2种类型的引导类，一种用于客户端(Bootstrap)，而另一种(ServerBootstrap)用于服务器，区别在于：



1、ServerBootstrap 将绑定到一个端口，因为服务器必须要监听连接，而 Bootstrap 则是由想要连接到远程节点的客户端应用程序所使用的



2、引导一个客户端只需要一个EventLoopGroup，但是一个ServerBootstrap则需要两个

### Channel

Netty中的Channel是与网络套接字相关的，可以理解为是socket连接，在客户端与服务端连接的时候就会建立一个Channel，它负责基本的IO操作，比如：bind()、connect()，read()，write() 等



主要作用：



1. 通过Channel可获得当前网络连接的通道状态。



2. 通过Channel可获得网络连接的配置参数（缓冲区大小等）。



3. Channel提供异步的网络I/O操作，比如连接的建立、数据的读写、端口的绑定等。



不同协议、不同的I/O类型的连接都有不同的 Channel 类型与之对应

![image-20240128100346483](https://qny.bbbwdc.com/blog/image-20240128100346483.png)

### EventLoopGroup 和 EventLoop  

Netty是基于事件驱动的，比如：连接注册，连接激活；数据读取；异常事件等等，有了事件，就**需要一个组件去监控事件的产生和事件的协调处理**，这个组件就是EventLoop（事件循环/EventExecutor）



在Netty 中每个Channel 都会被分配到一个 EventLoop。一个 EventLoop 可以服务于多个 Channel。每个EventLoop 会占用一个 Thread，同时这个 Thread 会处理 EventLoop 上面发生的所有 IO 操作和事件。



EventLoopGroup 是用来生成 EventLoop 的，包含了一组EventLoop（可以初步理解成Netty线程池）

### ByteBuf

Netty 使用 ByteBuf 来替代 Java NIO 的 ByteBuffer，它是一个强大的实现，既解决了JDK API 的局限性， 又为网络应用程序的开发者提供了更好的API。
从结构上来说，ByteBuf 由一串字节数组构成。数组中每个字节用来存放信息，ByteBuf提供了两个索引，一个用于读取数据（**readerIndex** ），一个用于写入数据（**writerIndex**）。这两个索引通过在字节数组中移动，来定位需要读或者写信息的位置。而JDK的ByteBuffer只有一个索引，因此需要使用flip方法进行读写切换。

![image-20241212144510323](https://qny.bbbwdc.com/blog/image-20241212144510323.png)

#### ByteBuf 的三类使用模式

- 堆缓冲区（HeapByteBuf）：内存分配在JVM 堆，分配和回收速度比较快，可以被JVM自动回收，缺点是，如果进行 socket 的IO读写，需要额外做一次内存复制，将堆内存对应的缓冲区复制到内核Channel中，性能会有一定程度的下降。由于在堆上被 JVM 管理，在不被使用时可以快速释放。可以通过 ByteBuf.array() 来获取 byte[] 数据。

- 直接缓冲区（DirectByteBuf）：内存分配的是堆外内存（系统内存），相比堆内存，它的分配和回收速度会慢一些，但是将它写入或从Socket Channel中读取时，由于减少了一次内存拷贝，速度比堆内存块。**Netty 默认使用 DirectByteBuf**。

- 复合缓冲区（CompositeByteBuf）：顾名思义就是将两个不同的缓冲区从逻辑上合并，只保存缓冲区的引用，不实际复制缓冲区数据。

#### ByteBuf 的分配器

Netty 提供了两种 ByteBufAllocator 的实现，分别是：

- PooledByteBufAllocator：实现了 ByteBuf 的对象的池化，提高性能减少并最大限度地减少内存碎片，池化思想通过预先申请一块专用内存地址作为内存池进行管理，从而不需要每次都进行分配和释放。(只能由Netty内部自己使用)
- UnpooledByteBufAllocator：没有实现对象的池化，每次会生成新的对象实例

### Future/Promise异步模型

在 Netty 中，异步模型的主要思想是允许某些操作在后台处理，而不会阻塞调用线程。

- Future: 表示一个异步计算的结果。它继承自 JUC 包下的 Future，扩展了一些好用的 API，可以向 Future 添加监听者，当程序执行完成时通知监听者。

- Promise: 是一种可写的 Future，它允许用户手动设置结果或异常。Future 只是增加了监听器，整个异步的状态，是不能进行设置和修改的，Promise接口扩展了 Future接口，可以设置异步执行的结果。在IO操作过程，如果顺利完成、或者发生异常，都可以设置 Promise 的结果，并且通知 Promise 的 Listener 们。

在 Java 的 Future 中，业务逻辑为一个 Callable 或 Runnable 实现类，该类的 call() 或 run() 执行完毕才能返回处理结果，在 Promise 机制中，可以在业务逻辑中人工设置业务逻辑的成功与失败。



### TCP 粘包拆包

粘包是指多个消息被合并成一个包发送，而拆包则是指一个消息被分成多个包发送粘包是多个数据包粘在一起，如在应用层发送的两个消息是 ABC，DEF，粘在一起之后是 ABCDEF，拆包是一个数据包被拆开了，如 AB，CD，EF。



**根本原因**：TCP 协议是面向连接的、可靠的、基于字节流的传输层通信协议，是一种流式协议，消息无边界。

#### Netty 解决粘包拆包

Netty提供了针对封装成帧这种形式下不同方式的拆包器，所谓的拆包其实就是数据的解码,所谓解码就是将网络中的一些原始数据解码成上层应用的数据，那对应在发送数据的时候要按照同样的方式进行数据的编码操作然后发送到网络中。

| 作用                   | 解码                         | 编码                                   |
| ---------------------- | ---------------------------- | -------------------------------------- |
| 固定长度               | FixedLengthFrameDecoder      | 不需要，实现简单                       |
| 分隔符                 | DelimiterBasedFrameDecoder   | 应用层在每条消息后加上对应的分隔符即可 |
| 固定长度字段存消息长度 | LengthFieldBasedFrameDecoder | LengthFieldPrepender                   |

重点是 LengthFieldBasedFrameDecoder，可以理解为一个包由 header 和 body 组成，在 header 中定义了消息长度。

### 二次编解码 codec

我们把解决半包粘包问题的常用三种解码器叫一次解码器，其作用是将原始数据流(可能会出现粘包和半包的数据流)转换为用户数据(ByteBuf中存储)，但仍然是字节数据，所以我们需要二次解码器将字节数组转换为 Java对象，或者将将一种格式转化为另一种格式，方便上层应用程序使用。
一次解码器继承自：ByteToMessageDecoder；二次解码器继承自：MessageToMessageDecoder；但他们的本质都是继承 ChannelInboundHandlerAdapter。



常用的二次编解码器：



![image-20241212164551482](https://qny.bbbwdc.com/blog/image-20241212164551482.png)

![image-20241212164603592](https://qny.bbbwdc.com/blog/image-20241212164603592.png)

