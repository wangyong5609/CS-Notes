# Netty 核心架构与原理

## 概述

[Netty](https://netty.io/)是由JBOSS提供的一个java开源框架，现为 Github上的独立项目。Netty提供**非阻塞的、事件驱动**的网络应用程序框架和工具，用以快速开发**高性能、高可靠性的网络服务器和客户端程序**

- 本质：网络应用程序框架
- 实现：异步、事件驱动
- 特性：高性能、可维护、快速开发
- 用途：开发服务器和客户端

> 如何理解异步：线程同步、异步是相对的，在请求或执行过程中，如果会阻塞等待，就是同步操作，反之就是异步操作

![image-20231223140916958](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231223140916958.png)

客户端请求后无需等待返回结果则为异步。

## 核心架构

![img](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/components.png)

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

![image-20231223141908703](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231223141908703.png)

![image-20231223143611529](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231223143611529.png)

## Netty中的Reactor实现

Netty线程模型是基于Reactor模型实现的，对Reactor三种模式都有非常好的支持，并做了一定的改进，也非常的灵活，一般情况，在服务端会采用主从架构模型

![image-20231223143826827](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231223143826827.png)

### 工作流程

1. Netty 抽象出两组线程池：BossGroup 和 WorkerGroup，每个线程池中都有EventLoop 线程（可以是OIO,NIO,AIO）。BossGroup中的线程专门负责和客户端建立连接，WorkerGroup 中的线程专门负责处理连接上的读写, EventLoopGroup 相当于一个事件循环组，这个组中含有多个事件循环
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

![image-20231223144027155](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231223144027155.png)

## Pipeline 和 Handler

`ChannelPipeline` 提供了 `ChannelHandler` **链的容器**。以服务端程序为例，客户端发送过来的数据要接收，读取处理，我们称数据是入站的，需要经过一系列Handler处理后；如果服务器想向客户端写回数据，也需要经过一系列Handler处理，我们称数据是出站的

![image-20231223203543326](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231223203543326.png)

### ChannelHandler 分类

对于数据的出站和入站，有着不同的ChannelHandler类型与之对应：

- `ChannelInboundHandler` 入站事件处理器
- `ChannelOutBoundHandler` 出站事件处理器
- `ChannelHandlerAdapter` 提供了一些方法的默认实现，可减少用户对于ChannelHandler的编写
- `ChannelDuplexHandler` 混合型，既能处理入站事件又能处理出站事件

![image-20231223204219699](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231223204219699.png)

> inbound入站事件处理顺序（方向）是由链表的头到链表尾，outbound事件的处理顺序是由链表尾到链表头。
>
> inbound入站事件由netty内部触发，最终由netty外部的代码消费。数据是netty读完成后交给业务代码使用，所以说是由外部代码消费
>
> outbound事件由netty外部的代码触发，最终由netty内部消费。什么时候写数据是由业务代码出发的，然后netty帮你处理好发给客户端

![image-20231223204351129](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231223204351129.png)



## Netty如何使用Reactor模式

前面说了 netty 是基于Reactor模型实现的，那具体是怎么用的呢？

![image-20231224212016498](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231224212016498.png)

NioEventLoopEvent 可以理解为一个线程池，传参数为 1 就是只创建一个线程，这就是**Reactor单线程模式**

NioEventLoopEvent 构造函数不传参数的话默认会创建当前主机逻辑内核数量的 2 倍数量的 NioEventLoop。

![image-20231224212612971](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231224212612971.png)

![image-20231224212708548](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231224212708548.png)

ServerBootstrap 是一个核心引导启动类，我们来看它的构造函数

![image-20231224213425469](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231224213425469.png)

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
package com.itheima.netty.mydemo;

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
     * InboundHandler 用于处理数据流出本端（服务端）的 IO 事件
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
            super.channelReadComplete(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.out.println("NettyServerInBoundHandler exceptionCaught," + cause.getMessage());
            super.exceptionCaught(ctx, cause);
        }
    }
}
```

### Netty Client

```java
package com.itheima.netty.mydemo;

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

