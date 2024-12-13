# Netty源码解析-环境搭建

> netty版本：4.1.115.Final
>
> 我的 netty 仓库：https://github.com/wangyong5609/netty

## 1.拉取源码

拉取源码有两种方式：

- Fork GitHub 仓库（推荐）
- 下载源码压缩包

### Fork netty 仓库

netty Github 仓库地址：https://github.com/netty/netty。

Fork 到自己仓库然后在 IDEA 通过 URL 导入。

fork 仓库有以下好处：

- 通过 fork 仓库，您可以在自己的副本上进行独立的开发和实验，添加注释，不会影响原始项目。

- 当您对项目进行修改后，可以通过提交 pull request 来建议将您的更改合并到原始仓库中。这是开源项目中贡献代码的常见方式。
- Forked 仓库允许您使用 Git 的版本控制功能，跟踪更改和管理不同版本的代码。
- 您可以定期从原始仓库拉取更新，以保持您的 fork 与主项目的同步。

![image-20241213221407795](./Netty%E6%BA%90%E7%A0%81%E8%A7%A3%E6%9E%90-%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/image-20241213221407795.png)

### 下载源码压缩包

从GitHub官网下载压缩包文件。

![image-20241213222044545](./Netty%E6%BA%90%E7%A0%81%E8%A7%A3%E6%9E%90-%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/image-20241213222044545.png)

## 2.编译项目

我使用的源码 tag是 `4.1.115.Final`，所以我基于这个 tag 新建了一个分支。

> git checkout -b 4.1.115.analysis 4.1.115.Final 

### 配置 Maven 和项目结构

首先你需要配置 Maven home 和 JDK版本，如果你害怕编译失败，你可以完全按照我的环境来配置：

- Maven：3.9.2
- JDK：1.8

![image-20241213223616144](./Netty%E6%BA%90%E7%A0%81%E8%A7%A3%E6%9E%90-%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/image-20241213223616144.png)

配置 JDK，否则编译可能出现包依赖找不到的问题。

![image-20241213223505115](./Netty%E6%BA%90%E7%A0%81%E8%A7%A3%E6%9E%90-%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/image-20241213223505115.png)

### 编译项目

![image-20241213223716716](./Netty%E6%BA%90%E7%A0%81%E8%A7%A3%E6%9E%90-%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/image-20241213223716716.png)

## 3.启动示例

在`example`包下有很多示例可供我们学习和调试，比如 file，http，redis，websocket，telnet等。

我这里启动了一个 websocket 示例：`WebSocketServer`,`WebSocketClient`。你可以在浏览器访问`http://localhost:8080/`访问服务端玩玩。

![image-20241213225034152](./Netty%E6%BA%90%E7%A0%81%E8%A7%A3%E6%9E%90-%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/image-20241213225034152.png)

也可以在控制台发送消息

![image-20241213225146975](./Netty%E6%BA%90%E7%A0%81%E8%A7%A3%E6%9E%90-%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/image-20241213225146975.png)

好了，环境搭建完毕，后面就开始正式解析源码了。

如果你还不是很了解 netty 的基础概念和核心组件，你可以试试看我的这篇文章[Netty核心架构与原理](https://juejin.cn/post/7447489519589195828)，希望对你有帮助。



>  您的点赞和关注是我写作的最大动力，感谢支持！

