![image.png](https://cdn.nlark.com/yuque/0/2021/png/110282/1638273966070-17a3b2e7-124e-46ad-8221-1f904f0cac14.png#clientId=uc139c711-0875-4&from=paste&height=368&id=ub890028c&originHeight=368&originWidth=676&originalType=binary&ratio=1&rotation=0&showTitle=false&size=24794&status=done&style=none&taskId=u7cf9877c-2b43-498c-88a0-344e2d4100b&title=&width=676)



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

`HealthCheckProcessorV2Delegate`是一个处理器代理类，代理了四种类型的处理器类，其中三种对应了 Nacos 三种探测的协议，即 Http、TCP 以及 MySQL

```
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