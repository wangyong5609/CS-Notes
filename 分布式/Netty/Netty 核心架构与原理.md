# Netty 核心架构与原理

## 概述

[Netty](https://netty.io/)是由JBOSS提供的一个java开源框架，现为 Github上的独立项目。Netty提供**非阻塞的、事件驱动**的网络应用程序框架和工具，用以快速开发**高性能、高可靠性的网络服务器和客户端程序**

- 本质：网络应用程序框架
- 实现：异步、事件驱动
- 特性：高性能、可维护、快速开发
- 用途：开发服务器和客户端

> 如何理解异步：线程同步、异步是相对的，在请求或执行过程中，如果会阻塞等待，就是同步操作，反之就是异步操作

![image-20231223140916958](./Netty%20%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%8E%9F%E7%90%86.assets/image-20231223140916958.png)

客户端请求后等待返回结果则为异步。

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