前面分析了在 Nacos 在客户端是如何实现动态更新配置的，本文就来分析一下在 Nacos console 修改了配置以后，服务端底层做了哪些处理。比如：

- 持久化到 Mysql
- 写入磁盘
- 通知客户端

## 服务端接收请求

在控制台页面更新一项配置，看看控制台发送了什么请求给服务端。

![image-20241120140118352](./Nacos源码分析-更新配置时服务端做了什么.assets/image-20241120140118352.png)

![image-20241120140048490](./Nacos源码分析-更新配置时服务端做了什么.assets/image-20241120140048490.png)

控制台发送了一个 POST 请求：`/nacos/v1/cs/configs`，在[官方 API指南](https://nacos.io/docs/v1/open-api/) 可以找到 API 定义。

![image-20241120140623329](./Nacos源码分析-更新配置时服务端做了什么.assets/image-20241120140623329.png)

> 我 Nacos 源码是 2.* 版本，但是打开的 console 发布配置请求的 API 还是 1.* 版本。不过官方说，v2 是兼容 v1 的，在 Controller 层面两个版本使用的也是相同的 Service 类处理请求。

请求进入**com.alibaba.nacos.config.server.controller.ConfigController#publishConfig**，封装配置和请求信息调用`com.alibaba.nacos.config.server.service.ConfigOperationService#publishConfig`。

![image-20241120142833789](./Nacos源码分析-更新配置时服务端做了什么.assets/image-20241120142833789.png)