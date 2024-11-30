![image-20241130162733261](https://qny.bbbwdc.com/blog/image-20241130162733261.png)



Nacos 中使用 SDK 对于永久实例的注册实际也是使用 OpenAPI 的方式进行注册，这样可以保证即使是客户端下线后也不会影响永久实例的健康检查。

对于永久实例的的健康检查，Nacos 采用的是注册中心探测机制，注册中心会在持久化服务初始化时根据客户端选择的协议类型注册探活的定时任务。

Nacos 现在内置提供了三种探测的协议，即 Http、TCP 以及 MySQL 。

一般而言 Http 和 TCP 已经可以涵盖绝大多数的健康检查场景。本文主要分析的也是 TCP 健康检查场景。

MySQL 主要用于特殊的业务场景，例如数据库的主备需要通过服务名对外提供访问，需要确定当前访问数据库是否为主库时，那么我们此时的健康检查接口，是一个检查数据库是否为主库的 MySQL 命令。



## 注册永久实例

首先需要在配置中将服务标记为永久实例, 然后注册实例到注册中心。如果在这之前这个实例是临时实例，你需要重启注册中心。

```properties
spring.cloud.nacos.discovery.ephemeral=false
```

注册永久示例会调用`PersistentClientOperationServiceImpl#onInstanceRegister`, 如果是初次注册，则需要初始化客户端信息，创建健康检查任务

> 如果你对服务注册流程感兴趣，可以看往期文章 [Nacos源码分析-服务注册](https://juejin.cn/post/7436204791620141095#heading-22)

```java
private void onInstanceRegister(Service service, Instance instance, String clientId) {
    Service singleton = ServiceManager.getInstance().getSingleton(service);
    if (!clientManager.contains(clientId)) {
        // 初次注册注册该客户端，初始化客户端信息，创建健康检查任务
        clientManager.clientConnected(clientId, new ClientAttributes());
    }
    Client client = clientManager.getClient(clientId);
    InstancePublishInfo instancePublishInfo = getPublishInfo(instance);
    client.addServiceInstance(singleton, instancePublishInfo);
    client.setLastUpdatedTime();
    // 发布客户端注册服务事件
    NotifyCenter.publishEvent(new ClientOperationEvent.ClientRegisterServiceEvent(singleton, clientId));
}
```

调用`PersistentIpPortClientManager#clientConnected()`去创建客户端信息，并进行初始化。

```java
@Override
public boolean clientConnected(String clientId, ClientAttributes attributes) {
    return clientConnected(clientFactory.newClient(clientId, attributes));
}

@Override
public boolean clientConnected(final Client client) {
    clients.computeIfAbsent(client.getClientId(), s -> {
        Loggers.SRV_LOG.info("Client connection {} connect", client.getClientId());
        // 创建基于IP和端口的客户端
        IpPortBasedClient ipPortBasedClient = (IpPortBasedClient) client;
        // 初始化
        ipPortBasedClient.init();
        return ipPortBasedClient;
    });
    return true;
}
```

## 健康检查任务

在初始化方法`init`中创建了一个健康检查任务`healthCheckTaskV2`。

```java
public void init() {
    if (ephemeral) {
        beatCheckTask = new ClientBeatCheckTaskV2(this);
        HealthCheckReactor.scheduleCheck(beatCheckTask);
    } else {
        // 为永久实例创建健康检查任务
        healthCheckTaskV2 = new HealthCheckTaskV2(this);
        HealthCheckReactor.scheduleCheck(healthCheckTaskV2);
    }
}
```

`healthCheckTaskV2`实现了`Runnable`接口，它的`run()`方法调用了`doHealthCheck()`取出服务实例信息，使用健康检查处理器做健康检测。

```java
public void doHealthCheck() {
    try {
        // 初始化 bean 和其他参数
        initIfNecessary();
        for (Service each : client.getAllPublishedService()) {
            // 是否开启健康检查，因为有些服务不需要健康检查
            if (switchDomain.isHealthCheckEnabled(each.getGroupedServiceName())) {
                // 获取实例发布信息
                InstancePublishInfo instancePublishInfo = client.getInstancePublishInfo(each);
                // 获取集群元数据
                ClusterMetadata metadata = getClusterMetadata(each, instancePublishInfo);
                // 调用健康检查处理器
                ApplicationUtils.getBean(HealthCheckProcessorV2Delegate.class).process(this, each, metadata);
            }
        }
    } catch (Throwable e) {
        Loggers.SRV_LOG.error("[HEALTH-CHECK] error while process health check for {}", client.getClientId(), e);
    } finally {
    }
}
```

`HealthCheckProcessorV2Delegate`是一个处理器代理类，代理了四种类型的处理器类，其中三种对应了 Nacos 三种探测的协议，即 Http、TCP 以及 MySQL。

![image-20241129102512083](https://qny.bbbwdc.com/blog/image-20241129102512083.png)

根据健康检查类型获取对应的处理器处理任务。

```java
@Override
public void process(HealthCheckTaskV2 task, Service service, ClusterMetadata metadata) {
    // tcp health check
    String type = metadata.getHealthyCheckType();
    // 有四种类型的处理器，分别是：tcp、http、mysql、none
    HealthCheckProcessorV2 processor = healthCheckProcessorMap.get(type);
    // 默认处理器是none
    if (processor == null) {
        processor = healthCheckProcessorMap.get(NoneHealthCheckProcessor.TYPE);
    }
    processor.process(task, service, metadata);
}
```

## TCP 健康检查处理器

本文分析的是 TCP 健康检查场景，`TcpHealthCheckProcessor`是 TCP 健康检查处理器。

![image-20241129102700323](https://qny.bbbwdc.com/blog/image-20241129102700323.png)

- **Beat**：用于表示一个心跳检查任务。它封装了与健康检查相关的信息，包括服务实例、健康检查任务、元数据等
- **TimeOutTask**：用于处理连接的超时任务。当某个连接在设定的时间内没有响应时，执行相应的超时处理逻辑
- **TaskProcessor**：用于处理具体的心跳检测任务。它实现了 `Callable<Void>` 接口，能够被异步执行
- **PostProcessor**：用于处理 NIO 选择器中已准备好的连接。它在连接状态发生变化时执行相应的逻辑
- **BeatKey**：用于在健康检查过程中管理与连接相关的状态信息，特别是在 NIO 中使用选择键的场景



接着看代码，进入`TcpHealthCheckProcessor#process`，此方法中创建了一个心跳检查任务，放入任务队列。

那么队列中的任务是如何被处理的呢？

- `TcpHealthCheckProcessor`实现了`Runnable`接口，允许在线程中执行
- **`@Component`**: 这个注解使得该类被 Spring 管理为一个 Bean。
- **初始化**: 构造函数中初始化 `selector`，并将当前实例提交到全局执行器 `GlobalExecutor`，以便开始运行健康检查任务。
- 在`run`方法中调用`processTask()`从任务队列中取出心跳任务，放入`TaskProcessor`中，接着调用`TaskProcessor`的`call()`开启处理心跳任务。

```java
@Component
public class TcpHealthCheckProcessor implements HealthCheckProcessorV2, Runnable {
  	// 任务队列
    private final BlockingQueue<Beat> taskQueue = new LinkedBlockingQueue<>();
    // NIO Selector，用于管理可连接的 SocketChannel
    private final Selector selector;

    public TcpHealthCheckProcessor(HealthCheckCommonV2 healthCheckCommon, SwitchDomain switchDomain) {
      this.healthCheckCommon = healthCheckCommon;
      this.switchDomain = switchDomain;
      try {
          // 初始化 selector
          selector = Selector.open();
          // 提交到全局执行器，执行健康检查任务
          GlobalExecutor.submitTcpCheck(this);
      } catch (Exception e) {
          throw new IllegalStateException("Error while initializing SuperSense(TM).");
      }
    }
  
    @Override
    public void process(HealthCheckTaskV2 task, Service service, ClusterMetadata metadata) {
        HealthCheckInstancePublishInfo instance = (HealthCheckInstancePublishInfo) task.getClient()
                .getInstancePublishInfo(service);
        // 向taskQueue中添加一个心跳对象
        taskQueue.add(new Beat(task, service, metadata, instance));
    }
  	
      @Override
    public void run() {
       // 持续处理任务，处理 TCP 连接
        while (true) {
            processTask();
            // 检查是否有任何通道（如 SocketChannel）准备好进行 I/O 操作
            int readyCount = selector.selectNow();
            if (readyCount <= 0) {
                continue;
            }
            // 获取所有已准备好的 SelectionKey 对象，并创建一个迭代器
            Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                // 从 selectedKeys 集合中移除当前键。这是必要的，因为一旦处理完毕，就不再需要该键，以避免重复处理
                iter.remove();
                // 将处理逻辑提交给全局执行器，使用 PostProcessor 处理每个连接的状态变化
                GlobalExecutor.executeTcpSuperSense(new PostProcessor(key));
            }
        }
    }
  
    private void processTask() throws Exception {
        Collection<Callable<Void>> tasks = new LinkedList<>();
        do {
            Beat beat = taskQueue.poll(CONNECT_TIMEOUT_MS / 2, TimeUnit.MILLISECONDS);
            if (beat == null) {
                return;
            }
            // 为每个心跳任务创建处理任务
            tasks.add(new TaskProcessor(beat));
        } while (taskQueue.size() > 0 && tasks.size() < NIO_THREAD_COUNT * 64);
        
        for (Future<?> f : GlobalExecutor.invokeAllTcpSuperSenseTask(tasks)) {
            f.get();
        }
    }
}
```

心跳任务现在交给了`TaskProcessor`处理，调用它的`call()`

`call()` 方法在 `TaskProcessor` 类中负责执行 TCP 健康检查的具体逻辑。它通过非阻塞 I/O 机制与服务进行连接，并管理与心跳相关的状态。

```java
public Void call() {
    long waited = System.currentTimeMillis() - beat.getStartTime();
    // 如果等待时间超过500ms，则打印警告日志
    if (waited > MAX_WAIT_TIME_MILLISECONDS) {
        Loggers.SRV_LOG.warn("beat task waited too long: " + waited + "ms");
    }
    
    SocketChannel channel = null;
    try {
        // 服务实例信息
        HealthCheckInstancePublishInfo instance = beat.getInstance();
        
        // 从 keyMap 中获取与 beat 关联的 BeatKey
        BeatKey beatKey = keyMap.get(beat.toString());
        // 如果 beatKey 不为空且 key 有效，则直接返回
        if (beatKey != null && beatKey.key.isValid()) {
            // 如果键有效且最近的活动时间小于 TCP_KEEP_ALIVE_MILLIS，则表示连接仍然有效，调用 instance.finishCheck() 并返回
            if (System.currentTimeMillis() - beatKey.birthTime < TCP_KEEP_ALIVE_MILLIS) {
                instance.finishCheck();
                return null;
            }
            // 如果连接不再有效，取消 SelectionKey 并关闭相关的 SocketChannel
            beatKey.key.cancel();
            beatKey.key.channel().close();
        }
        // 打开通道: 创建一个新的 SocketChannel 实例。
        // 非阻塞模式: 将通道设置为非阻塞模式，以允许异步 I/O 操作
        channel = SocketChannel.open();
        channel.configureBlocking(false);
        // only by setting this can we make the socket close event asynchronous
        channel.socket().setSoLinger(false, -1);
        channel.socket().setReuseAddress(true);
        channel.socket().setKeepAlive(true);
        channel.socket().setTcpNoDelay(true);
        
        // 获取元数据
        ClusterMetadata cluster = beat.getMetadata();
        int port = cluster.isUseInstancePortForCheck() ? instance.getPort() : cluster.getHealthyCheckPort();
        // 使用 connect 方法尝试连接到指定的 IP 和端口
        channel.connect(new InetSocketAddress(instance.getIp(), port));
        
        // 注册通道: 将 SocketChannel 注册到 Selector，并设置操作类型为连接和读取。
        SelectionKey key = channel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
        // 关联 beat: 将 beat 对象附加到选择键上，以便后续处理。
        key.attach(beat);
        // 更新键映射: 将新的 BeatKey 存储到 keyMap 中，以便后续查找。
        keyMap.put(beat.toString(), new BeatKey(key));
        
        // 设置开始时间: 设置 beat 的开始时间为当前时间戳。
        beat.setStartTime(System.currentTimeMillis());
        // 调度超时任务: 使用全局执行器调度一个超时任务，以处理连接超时的情况
        GlobalExecutor
                .scheduleTcpSuperSenseTask(new TimeOutTask(key), CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
        // 如果出现异常，设置健康检查失败，并关闭通道
        beat.finishCheck(false, false, switchDomain.getTcpHealthParams().getMax(),
                "tcp:error:" + e.getMessage());
        
        if (channel != null) {
            try {
                channel.close();
            } catch (Exception ignore) {
            }
        }
    }
    
    return null;
}
```

现在连接建立了，就需要使用 `PostProcessor` 处理每个连接的状态变化.

执行`run()`, 如果`SelectionKey` 有效且可以连接，说明实例是健康的，调用`beat.finishCheck`记录健康检查结果。

```java
@Override
    public void run() {
        // 从选择键中获取与之关联的 Beat 对象，用于检查心跳状态
        Beat beat = (Beat) key.attachment();
        //  获取与选择键关联的 SocketChannel，用于进行网络操作。
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            // 如果 beat 对象被标记为不健康，取消选择键并关闭通道，同时调用 finishCheck() 方法标记检查完成
            if (!beat.isHealthy()) {
                //invalid beat means this server is no longer responsible for the current service
                key.cancel();
                key.channel().close();
                
                beat.finishCheck();
                return;
            }
            // 如果选择键有效且可以连接，调用 finishConnect() 方法完成连接，并记录健康检查结果
            if (key.isValid() && key.isConnectable()) {
                //connected
                channel.finishConnect();
                beat.finishCheck(true, false, System.currentTimeMillis() - beat.getTask().getStartTime(),
                        "tcp:ok+");
            }
            
            if (key.isValid() && key.isReadable()) {
                //disconnected
                ByteBuffer buffer = ByteBuffer.allocate(128);
                if (channel.read(buffer) == -1) {
                    key.cancel();
                    key.channel().close();
                } else {
                    // not terminate request, ignore
                    SRV_LOG.warn(
                            "Tcp check ok, but the connected server responses some msg. Connection won't be closed.");
                }
            }
        } catch (ConnectException e) {
            // unable to connect, possibly port not opened
            beat.finishCheck(false, true, switchDomain.getTcpHealthParams().getMax(),
                    "tcp:unable2connect:" + e.getMessage());
        } catch (Exception e) {
            beat.finishCheck(false, false, switchDomain.getTcpHealthParams().getMax(),
                    "tcp:error:" + e.getMessage());
            
            try {
                key.cancel();
                key.channel().close();
            } catch (Exception ignore) {
            }
        }
    }
}
```

`Beat#finishCheck()`处理健康检查结果。

```java
public void finishCheck(boolean success, boolean now, long rt, String msg) {
    if (success) {
        healthCheckCommon.checkOk(task, service, msg);
    } else {
        if (now) {
            healthCheckCommon.checkFailNow(task, service, msg);
        } else {
            healthCheckCommon.checkFail(task, service, msg);
        }
        
        keyMap.remove(toString());
    }
    
    healthCheckCommon.reEvaluateCheckRT(rt, task, switchDomain.getTcpHealthParams());
}
```

这里以检查到实例健康为例，调用`HealthCheckCommonV2#checkOk()`。不论是什么状态，只要状态发生变化，都会修改健康状态并通知集群其他节点。

```java
public void checkOk(HealthCheckTaskV2 task, Service service, String msg) {
    try {
        HealthCheckInstancePublishInfo instance = (HealthCheckInstancePublishInfo) task.getClient()
                .getInstancePublishInfo(service);
        if (instance == null) {
            return;
        }
        try {
            // 如果实例之前被标记为不健康，现在健康了，则修改健康状态并通知集群其他节点
            if (!instance.isHealthy()) {
                String serviceName = service.getGroupedServiceName();
                String clusterName = instance.getCluster();
                // 检查服务实例的健康状态是否达到了指定的阈值
                if (instance.getOkCount().incrementAndGet() >= switchDomain.getCheckTimes()) {
                    if (switchDomain.isHealthCheckEnabled(serviceName) && !task.isCancelled() && distroMapper
                            .responsible(task.getClient().getResponsibleId())) {
                        // 修改实例的健康状态
                        healthStatusSynchronizer.instanceHealthStatusChange(true, task.getClient(), service, instance);
                    }
                }
            }
        } finally {
            instance.resetFailCount();
            instance.finishCheck();
        }
    } catch (Throwable t) {
        Loggers.SRV_LOG.error("[CHECK-OK] error when close check task.", t);
    }
}
```

## 通知集群节点

`PersistentHealthStatusSynchronizer#instanceHealthStatusChange()`，设置健康状态

```java
public void instanceHealthStatusChange(boolean isHealthy, Client client, Service service,
        InstancePublishInfo instance) {
    Instance updateInstance = InstanceUtil.parseToApiInstance(service, instance);
    // 修改健康状态
    updateInstance.setHealthy(isHealthy);
    persistentClientOperationService.updateInstance(service, updateInstance, client.getClientId());
}
```

`PersistentClientOperationServiceImpl#updateInstance()`，构建写请求数据，包括服务实例，服务数据，客户端id；操作类型设置为`CHANGE`，告诉集群节点更新实例。

```java
public void updateInstance(Service service, Instance instance, String clientId) {
    Service singleton = ServiceManager.getInstance().getSingleton(service);
    // 如果是临时实例，抛出异常
    if (singleton.isEphemeral()) {
        throw new NacosRuntimeException(NacosException.INVALID_PARAM,
                String.format("Current service %s is ephemeral service, can't update persistent instance.",
                        singleton.getGroupedServiceName()));
    }
    // 构建请求
    final PersistentClientOperationServiceImpl.InstanceStoreRequest request = new PersistentClientOperationServiceImpl.InstanceStoreRequest();
    request.setService(service);
    request.setInstance(instance);
    request.setClientId(clientId);
    // 创建写请求，序列化请求数据，设置操作类型为CHANGE
    final WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
            .setData(ByteString.copyFrom(serializer.serialize(request))).setOperation(DataOperation.CHANGE.name())
            .build();
    try {
        // CPProtocol 写入,通知集群节点
        protocol.write(writeRequest);
    } catch (Exception e) {
        throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
    }
}
```

这个写请求会通过 Nacos状态机`NacosStateMachine`，最后调用注册到状态机的请求处理器`PersistentClientOperationServiceImpl`的`onApply()`。

```java
public Response onApply(WriteRequest request) {
    final Lock lock = readLock;
    lock.lock();
    try {
        final InstanceStoreRequest instanceRequest = serializer.deserialize(request.getData().toByteArray());
        final DataOperation operation = DataOperation.valueOf(request.getOperation());
        switch (operation) {
            // 注册实例
            case ADD:
                onInstanceRegister(instanceRequest.service, instanceRequest.instance,
                        instanceRequest.getClientId());
                break;
            case DELETE:
                onInstanceDeregister(instanceRequest.service, instanceRequest.getClientId());
                break;
            // 修改实例
            case CHANGE:
                if (instanceAndServiceExist(instanceRequest)) {
                    onInstanceRegister(instanceRequest.service, instanceRequest.instance,
                            instanceRequest.getClientId());
                }
                break;
            default:
                return Response.newBuilder().setSuccess(false).setErrMsg("unsupport operation : " + operation)
                        .build();
        }
        return Response.newBuilder().setSuccess(true).build();
    } catch (Exception e) {
        Loggers.RAFT.warn("Persistent client operation failed. ", e);
        return Response.newBuilder().setSuccess(false)
                .setErrMsg("Persistent client operation failed. " + e.getMessage()).build();
    } finally {
        lock.unlock();
    }
}
```



## 永久实例的应用场景是什么

永久实例（ephemeral = false）的应用场景主要集中在那些需要长期存在且不随微服务应用生命周期变化的服务上。

这些场景的特点是服务的稳定性要求较高，或者服务提供者的变化不频繁，例如：

1. **数据库服务**：如MySQL服务，这类基础服务的地址和状态相对固定，不适合随着应用的启停而变化，使用永久实例可以确保服务地址的持久可靠。
2. **审计与维护需求的服务**：在需要进行定期审计或维护操作的系统中，如DNS和CoreDNS服务，采用永久实例可以方便地进行管理和追踪。
3. **非动态更新的基础设施服务**：对于那些不经常更新或变动的基础设施组件，使用永久实例可以减少因实例频繁注册注销带来的管理复杂度。
4. **不便于集成Nacos客户端的场景**：某些特殊的服务可能由于技术限制或架构设计原因，难以集成Nacos客户端进行心跳续约，这时使用永久实例并通过手动管理来确保服务的注册状态。

根据上述分析，永久实例适用于那些强调服务稳定性和管理便捷性的场景，特别是在基础架构层或对外提供公共服务的组件中。

## 总结

在本文中，我们深入探讨了 Nacos 中永久实例的健康检查机制。文章重点介绍了 TCP 健康检查的实现细节，包括心跳任务的创建与处理过程。永久实例在需要长期稳定的服务中发挥了重要作用，例如数据库服务和基础设施服务。通过这种机制，Nacos确保了服务的可用性和集群的一致性，从而提升了系统的可靠性和可维护性。在实际应用中，根据不同的场景选择合适的实例类型至关重要。

> 您的点赞和关注是我写作的最大动力，感谢支持！