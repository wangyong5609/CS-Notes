前面分析了在 Nacos 客户端是如何实现动态更新配置的，那么在 Nacos console 修改了配置以后，服务端底层怎么存储配置？客户端怎么知道配置修改了？怎么通知集群其他节点？让我来揭开它神秘的面纱。

## 服务端接收配置更新请求

在控制台页面更新一项配置，看看控制台发送了什么请求给服务端。

![image-20241120140118352](https://qny.bbbwdc.com/blog/image-20241120140118352.png)

![image-20241120140048490](https://qny.bbbwdc.com/blog/image-20241120140048490.png)

控制台发送了一个 POST 请求：`/nacos/v1/cs/configs`，在[官方 API指南](https://nacos.io/docs/v1/open-api/) 可以找到 API 定义。

![image-20241120140623329](https://qny.bbbwdc.com/blog/image-20241120140623329.png)

> 我 Nacos 源码是 2.* 版本，但是打开的 console 发布配置请求的 API 还是 1.* 版本。
>
> 官方说v2 是兼容 v1 的，在源码的 Controller 层面两个版本使用的也是相同的 Service 类处理请求。

请求进入**com.alibaba.nacos.config.server.controller.ConfigController#publishConfig**，封装配置和请求信息后调用`ConfigOperationService#publishConfig`。

![image-20241120142833789](https://qny.bbbwdc.com/blog/image-20241120142833789.png)

`publishConfig`方法做了两件事：

- 添加或更新配置信息到数据库
- 发布配置数据变更事件`ConfigDataChangeEvent`，这是客户端能感知配置更新的根本原因

![image-20241121165044280](https://qny.bbbwdc.com/blog/image-20241121165044280.png)

`ConfigDataChangeEvent`被两个类监听：

- `DumpService`: 将配置信息转存到本地磁盘
- `AsyncNotifyService`：通知集群其他节点和订阅配置的客户端配置发生变更

## DumpService：转存配置信息到本地

`DumpService`在构造函数中订阅了`ConfigDataChangeEvent`事件，当监听到事件发生，调用`handleConfigDataChange`方法。

![image-20241121170312201](https://qny.bbbwdc.com/blog/image-20241121170312201.png)

> 我回看了一下前面的文章，一些不重要的方法也有截图，白白的浪费了大家的阅读时间，对于简单的方法后面只会列出方法调用链路

- 👇`handleConfigDataChange(Event event)` 
- 👇`dumpFormal(String dataId, String group, String tenant, long lastModified, String handleIp)`

`dumpFormal`方法作用是转存正式数据到本地磁盘和缓存，向任务管理器`dumpTaskMgr`提交了一个 任务`DumpTask`。

![image-20241121171502273](https://qny.bbbwdc.com/blog/image-20241121171502273.png)

`dumpTaskMgr`是`TaskManager`实例，在`DumpService`的构造函数中创建了该实例，并且为它指定了`DumpProcessor`作为默认任务处理器

![image-20241122095303734](https://qny.bbbwdc.com/blog/image-20241122095303734.png)

`TaskManager`类本身没有实现执行任务的方法，而是继承自` NacosDelayTaskExecuteEngine`, 来看下他是如何执行任务的。

在构造函数中创建单线程执行器，确保任务都能处理成功，并且每个任务之间间隔 100 毫秒。

![image-20241121173736126](https://qny.bbbwdc.com/blog/image-20241121173736126.png)

执行器会一直执行`ProcessRunnable`，`ProcessRunnable`实现了`Runnable`接口，在`run`方法内调用`processTaks`方法处理任务

![image-20241121174039790](https://qny.bbbwdc.com/blog/image-20241121174039790.png)

通过 `taskKey` 获取`NacosTaskProcessor`类型的处理器，调用处理器的`process`方法处理任务。这里实现了任务重试机制，如果执行失败则放入队列稍后执行。

`NacosTaskProcessor`是一个处理器接口，他有很多实现，分别用来处理不同的任务

![image-20241121175209799](https://qny.bbbwdc.com/blog/image-20241121175209799.png)

dump 正式数据的任务则是由上面指定的默认任务处理器`DumpProcessor`来处理。

调用处理器的的`process`方法处理任务：

- 👇process(NacosTask task)
- 👇DumpConfigHandler.configDump(build.build())
- 👇ConfigCacheService.dump(dataId, group, namespaceId, content, lastModified, event.getType(),  event.getEncryptedDataKey())
- 👇dumpWithMd5(dataId, group, tenant, content, null, lastModifiedTs, type, encryptedDataKey)
- 👇ConfigDiskServiceFactory.getInstance().saveToDisk(dataId, group, tenant, content)

调用`saveToDisk`方法保存到磁盘；`updateMd5`方法更新本地缓存的 md5和最后更新时间，并发布`LocalDataChangeEvent`事件。

![image-20241122173301754](https://qny.bbbwdc.com/blog/image-20241122173301754.png)

进入`com.alibaba.nacos.config.server.service.dump.disk.ConfigRawDiskService#saveToDisk`, 可以看到`targetFile`就是`nacos.home`下要更新的文件路径，向文件写入新的配置信息。![image-20241122173750569](https://qny.bbbwdc.com/blog/image-20241122173750569.png)

继续看`updateMd5`方法，更新本地JVM缓存中的配置以后，发布`LocalDataChangeEvent`本地数据变更事件，这个事件的目的就是告诉客户端：配置发生了变更

![image-20241122174633242](https://qny.bbbwdc.com/blog/image-20241122174633242.png)

## AsyncNotifyService：通知集群节点

上面`DumpService`把修改后的配置更新到数据库，磁盘，JVM缓存了，但是这都是在本机发生的，集群的其他节点还是旧的配置呢，那么如何同步呢？

`AsyncNotifyService`异步通知服务就发挥作用了，它也监听了`ConfigDataChangeEvent`事件，所以当配置变更时，它就负责通知其它节点。

![image-20241124205802480](https://qny.bbbwdc.com/blog/image-20241124205802480.png)

当事件发生时，调用`handleConfigDataChangeEvent()`

- 获取除了自己以外的集群成员
- 为每个成员创建一个`NotifySingleRpcTask`放入同一队列中
- 创建一个`AsyncRpcTask`去处理队列中的任务

![image-20241124205934568](https://qny.bbbwdc.com/blog/image-20241124205934568.png)

`AsyncRpcTask`调用`executeAsyncRpcTask()`处理任务

- 依次从队列取出任务
- **构建集群同步请求体`ConfigChangeClusterSyncRequest`，集群其他节点收到此类型请求，会进行数据同步**
- 检查集群成员节点健康状态
- 调用`configClusterRpcClientProxy.syncConfigChange()`通知配置变更

![image-20241124210552163](https://qny.bbbwdc.com/blog/image-20241124210552163.png)

**集群成员收到请求会做什么呢**

`ConfigChangeClusterSyncRequestHandler`继承了`RequestHandler`，处理`ConfigChangeClusterSyncRequest`类型的请求。

- 从请求体中拿到配置修改的关键信息，比如dataId,group
- 调用`DumpService.dump()`
- `dump()`调用`dumpFormal()`转存配置信息到本地

![image-20241124211050621](https://qny.bbbwdc.com/blog/image-20241124211050621.png)

## 通知客户端配置变更

上面在`DumpService`小节中讲到，更新了本地JVM缓存以后，发布了`LocalDataChangeEvent`本地数据变更事件。

一共有两处监听了`LocalDataChangeEvent`本地数据变更事件

- RpcConfigChangeNotifier
- LongPollingService

### RpcConfigChangeNotifier

`RpcConfigChangeNotifier`订阅了`LocalDataChangeEvent`事件，事件发生时调用`configDataChanged`方法通知配置的监听者。

![image-20241122175117634](https://qny.bbbwdc.com/blog/image-20241122175117634.png)

- 首先获取监听配置的客户端列表、
- **创建通知请求`ConfigChangeNotifyRequest`，客户端收到这个请求后，会重新获取配置**
- 为每个客户端创建`RpcPushTask`

![image-20241124192118777](https://qny.bbbwdc.com/blog/image-20241124192118777.png)

然后将任务放入执行器准备执行。

- 👇ConfigExecutor.scheduleClientConfigNotifier(retryTask, retryTask.getTryTimes() * 2, TimeUnit.SECONDS)

#### Rpc推送任务

`RpcPushTask`实现了`Runnable`接口，它的`run`方法做了两件事：

- 检查`TPS`,避免服务器资源耗尽。官方称为反脆弱，可参考[反脆弱插件](https://nacos.io/zh-cn/docs/v2/plugin/control-plugin.html)![image-20241124185123706](https://qny.bbbwdc.com/blog/image-20241124185123706.png)
- 推送请求并设置回调

调用`RpcPushService.pushWithCallback()`发送`GRPC`请求通知客户端配置变更

![image-20241124190040297](https://qny.bbbwdc.com/blog/image-20241124190040297.png)

在客户端的`ClientWorker`中注册了`ConfigChangeNotifyRequest`的处理器，当收到请求后调用`notifyListenConfig()`更新配置。

> 在《nacos源码分析-客户端启动与配置动态更新的实现细节》中讲过`ClientWorker` 主要用于封装与 Nacos 配置服务的交互逻辑，提供配置的获取、监听和更新等功能。

![image-20241124192540984](https://qny.bbbwdc.com/blog/image-20241124192540984.png)

### LongPollingService

Nacos1.*版本 是基于长轮询机制监听配置变更，客户端调用`/nacos/v1/cs/configs/listener`api监听配置（参考[API指南](https://nacos.io/zh-cn/docs/open-api.html)）。

当配置发生变更时，也需要通知那些通过长轮询监听的客户端。

从下图可以看到本地数据变更事件`LocalDataChangeEvent`发生时，创建了一个`DataChangeTask`放入执行器执行。

![image-20241124201940623](https://qny.bbbwdc.com/blog/image-20241124201940623.png)

`DataChangeTask`遍历全部订阅者，如果客户端监听了修改的配置 key，那么移除和客户端的订阅关系，因为客户端即将接收响应，然后向客户端发送响应

![image-20241124203348938](https://qny.bbbwdc.com/blog/image-20241124203348938.png)

调用`sendResponse()`响应客户端请求

调用`generateResponse()`返回修改的配置 key 给客户端

![image-20241124203703381](https://qny.bbbwdc.com/blog/image-20241124203703381.png)

## 原理图

> ProcessOn 地址：https://www.processon.com/diagraming/67400f6a108b5a60353dd0bd

![Nacos配置变更通知集群节点和客户端](https://qny.bbbwdc.com/blog/Nacos配置变更通知集群节点和客户端.png)

## 总结

现在可以来解答开头的问题了。

**1.服务端底层怎么存储配置？**

- 添加或更新配置信息到数据库
- 发布配置数据变更事件`ConfigDataChangeEvent`
- `DumpService`监听到`ConfigDataChangeEvent`事件，将配置保存到本地磁盘和JVM缓存

**2.客户端怎么知道配置修改了？**

- 本地缓存更新后，发布`LocalDataChangeEvent`事件

- `RpcConfigChangeNotifier`监听到本地数据变更事件`LocalDataChangeEvent`
- 创建通知请求`ConfigChangeNotifyRequest`，发起 GRPC调用通知客户端
- 通过长轮询监听的客户端，则由`LongPollingService`携带更新的配置 key 响应客户端

**3.怎么通知集群其他节点？**

- `AsyncNotifyService`监听配置数据变更事件`ConfigDataChangeEvent`
- 监听到事件发生后，向集群成员发送`ConfigChangeClusterSyncRequest`，告知变更的配置信息