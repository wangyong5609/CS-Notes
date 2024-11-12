> Nacos server: 2.4.3
>
> JDK: 1.8
>
> spring-cloud-starter-alibaba-nacos-discovery-2021.0.5.0.jar
>
> Maven: 3.8.8
>
> 我的Nacos仓库：https://github.com/wangyong5609/nacos，分支：2.4.3-analysis

## 一、Nacos简介

Nacos 是一个更易于构建云原生应用的动态服务发现、配置管理和服务管理平台

![image.png](./nacos源码分析-服务注册.assets/1637460801625-abaec6c8-82a8-46cf-9b86-7b7ecc2968e4.png)

> 目前主要关注 Nacos 服务注册与发现相关的内容

- 服务提供者在启动时会向Nacos注册中心发送注册请求，包括服务名称、IP地址、端口号等信息。
- Nacos服务端接收到注册请求后，将服务实例信息存储在注册中心的数据库中，并缓存到内存中以便快速查询。
- 注册成功后，服务提供者会定期向Nacos发送心跳请求，以表明服务实例仍在运行中。
- 服务消费者从服务注册中心发现并调用服务。

![img](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/1598000046685-864bbfe0-ae90-4e24-85b1-bd352ee24314.png)



## 二、服务注册原理

### 1.客户端请求原理图

> Processon 地址：https://www.processon.com/diagraming/672d9c0ca8011b320f4a064c

![image-20241108135709194](./nacos源码分析-服务注册.assets/image-20241108135709194.png)

简单来说就是，项目引入 `spring-cloud-starter-alibaba-nacos-discovery` 以后，利用Spring的自动装配，在它的`spring.facotries`中加载了 `NacosServiceRegistryAutoConfiguration`, `NacosServiceRegistryAutoConfiguration` 中加载了一个叫 `NacosAutoServiceRegistration` 的Bean，

这个Bean的父类实现了 `ApplicationListener` 这个接口，并监听了`WebServerInitializedEvent`类型的事件，在Tomcat启动后会发布`WebServerInitializedEvent`的事件，事件被监听到以后，就会调用 `NacosServiceRegistry`的 `register` 方法向Nacos服务端注册实例，临时实例使用 RPC 调用，永久实例使用 HTTP 调用。



### 2.服务端处理请求原理图

![Nacos服务注册原理-服务端](./nacos源码分析-服务注册.assets/Nacos服务注册原理-服务端.png)

**注册临时实例：**

`RequestHandlerRegistry`监听了`ContextRefreshedEvent`事件，在 Springboot 启动时context初始化完成后，通知`RequestHandlerRegistry`开始注册`RequestHandler`的全部实现类，这里面就包括`InstanceRequestHandler`。

注册实例的 grpc 请求会被`GrpcRequestAcceptor`接口, 从`RequestHandlerRegistry`中拿到`InstanceRequestHandler`处理实例注册请求，然后调用`EphemeralClientOperationServiceImpl.registerInstance() `, 发布客户端注册事件`ClientRegisterServiceEvent`.

**注册永久实例：**

在Springboot 启动时会加载Bean`PersistentClientOperationServiceImpl`,初始化`CPProtocol`, 创建 `NacosStateMachine`, 并向状态机注册请求处理器`EphemeralClientOperationServiceImpl`， 启动`RaftServer`集群。

注册实例的 HTTP 请求进入`InstanceController`的`register`方法，然后调用`EphemeralClientOperationServiceImplde`的`registerInstance()`方法，提交注册请求给 raft 集群，集群会让 Leader 节点处理本次写操作，最终会由状态机注册的`processor`也就是`EphemeralClientOperationServiceImpl`处理，调用它的`onApply`方法，发布客户端注册事件`ClientRegisterServiceEvent`.

**处理客户端注册事件**

监听客户端注册事件的`ClientServiceIndexesManager`收到通知以后，将实例信息保存到本地注册表，并发布`ServiceChangedEvent`事件通知其他客户端，

`NamingSubscriberServiceV2Impl`监听到`ServiceChangedEvent`发布后，创建信息推送任务，添加到延迟任务执行引擎`PushDelayTaskExecuteEngine`, 引擎执行推送任务。

## 三、源码分析

### 客户端

当我们服务引入`spring-cloud-starter-alibaba-nacos-discovery`,便可以实现自动进行注册，这是因为在`spring.facotries`中自动装配了`NacosServiceRegistryAutoConfiguration`

![image-20241107130511850](./nacos源码分析-服务注册.assets/image-20241107130511850.png)

#### 1. NacosServiceRegistryAutoConfiguration加载Bean

![image-20241107135455045](./nacos源码分析-服务注册.assets/image-20241107135455045.png)

此类中定义了三个 Bean：`NacosServiceRegistry`, `NacosRegistration` , `NacosAutoServiceRegistration`

仔细看会发现，前面两个Bean 都是 `NacosAutoServiceRegistration` 的入参

##### 1.1 NacosServiceRegistry

`NacosServiceRegistry` 的构造函数入参主要是一些注册需要的配置信息，下面的`register` 方法就是实现服务注册的，不过要想在服务启动时自动完成注册，还得靠 `NacosAutoServiceRegistration`

![image-20241107144358116](./nacos源码分析-服务注册.assets/image-20241107144358116.png)

##### 1.2 NacosRegistration

- `registrationCustomizers`：一个 `NacosRegistrationCustomizer` 类型的列表，可能用于自定义注册过程。
- `nacosDiscoveryProperties`：包含 Nacos 服务发现的相关配置。
- `context`：`ApplicationContext` 类型的对象，表示 Spring 应用上下文，可能用于访问 Spring 框架的功能。

![image-20241107151522271](./nacos源码分析-服务注册.assets/image-20241107151522271.png)

##### 1.3 NacosAutoServiceRegistration

![image-20241107155551813](./nacos源码分析-服务注册.assets/image-20241107155551813.png)

> 这里我在查看类图的时候，图中并不显示 `NacosAutoServiceRegistration` ，而是从它的父类开始展示，没找到在哪里可以配置。不过可以按空格添加这个进来，也算是个办法。

从类图中可以看到实现了 `ApplicationListener` 接口，这是实现自动注册的关键。

![image-20241107165110867](./nacos源码分析-服务注册.assets/image-20241107165110867.png)

#### 2. 监听WEB容器事件

`ApplicationListener` 是 Spring 框架中一个接口，它属于 `org.springframework.context` 包。这个接口允许 beans 监听 Spring 事件发布系统发布的事件。

![image-20241107165642159](./nacos源码分析-服务注册.assets/image-20241107165642159.png)



`WebServerInitializedEvent` 是 Spring Boot 中的一个具体事件类，它属于 `org.springframework.boot.web.context` 包，继承自ApplicationEvent。这个事件在嵌入式 web 服务器（如 Tomcat、Jetty 或 Undertow）初始化完成后发布。

当你创建一个 `ApplicationListener` 的实现，并指定 `WebServerInitializedEvent` 作为泛型参数时，Spring 容器会自动调用你的 `onApplicationEvent` 方法，并将 `WebServerInitializedEvent` 的实例作为参数传递给你的 listener。

![image-20241107165813117](./nacos源码分析-服务注册.assets/image-20241107165813117.png)



`SmartLifecycle` 是一个接口，它继承自 `Lifecycle` 接口，允许细粒度的控制Bean的生命周期行为。

`start`方法在`Tomcat`容器启动后，发布事件 `ServletWebServerInitializedEvent`, `ServletWebServerInitializedEvent` 继承自`WebServerInitializedEvent` 

![image-20241107171719648](./nacos源码分析-服务注册.assets/image-20241107171719648.png)



#### 3. 处理容器事件

所以当容器初始化完成后，会调用 `org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration#onApplicationEvent`

![image-20241107174118933](./nacos源码分析-服务注册.assets/image-20241107174118933.png)

```java
// org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration#start
public void start() {
  	// 配置：spring.cloud.nacos.discovery.enabled
    if (!this.isEnabled()) {
        if (logger.isDebugEnabled()) {
            logger.debug("Discovery Lifecycle disabled. Not starting");
        }

    } else {
        // 是否已启动
        if (!this.running.get()) {
          	// 发布预注册事件
            this.context.publishEvent(new InstancePreRegisteredEvent(this, this.getRegistration()));
          	// 开始注册
            this.register();
            if (this.shouldRegisterManagement()) {
                this.registerManagement();
            }
						// 发布注册后事件
            this.context.publishEvent(new InstanceRegisteredEvent(this, this.getConfiguration()));
            // 修改running状态值
            this.running.compareAndSet(false, true);
        }

    }
}
```

#### 4. NacosServiceRegistry 注册服务

![image-20241107175345519](./nacos源码分析-服务注册.assets/image-20241107175345519.png)

这里 `serviceRegistry` 就是 `NacosServiceRegistryAutoConfiguration` 中加载的 `NacosServiceRegistry` Bean

下面核心代码是 `registerInstance`

```java
// com.alibaba.cloud.nacos.registry.NacosServiceRegistry#register
public void register(Registration registration) {
    // ServiceId不能为空，其实就是配置：spring.application.name
    if (StringUtils.isEmpty(registration.getServiceId())) {
        log.warn("No service to register for nacos client...");
    } else {
        NamingService namingService = this.namingService();
        // 应用名称
        String serviceId = registration.getServiceId();
        // 组名
        String group = this.nacosDiscoveryProperties.getGroup();
        // 将服务实例的信息封装为Instance对象
        Instance instance = this.getNacosInstanceFromRegistration(registration);

        try {
            // 注册示例
            namingService.registerInstance(serviceId, group, instance);
            log.info("nacos registry, {} {} {}:{} register finished", new Object[]{group, serviceId, instance.getIp(), instance.getPort()});
        } catch (Exception var7) {
            if (this.nacosDiscoveryProperties.isFailFast()) {
                log.error("nacos registry, {} register failed...{},", new Object[]{serviceId, registration.toString(), var7});
                ReflectionUtils.rethrowRuntimeException(var7);
            } else {
                log.warn("Failfast is false. {} register failed...{},", new Object[]{serviceId, registration.toString(), var7});
            }
        }

    }
}
```

 `registerInstance` 方法作用：

- 检查集群名称和心跳配置是否合法
- 调用 `NamingClientProxyDelegate#registerService` 注册服务

![image-20241108115204178](./nacos源码分析-服务注册.assets/image-20241108115204178.png)

![image-20241108115702451](./nacos源码分析-服务注册.assets/image-20241108115702451.png)

![image-20241108115924913](./nacos源码分析-服务注册.assets/image-20241108115924913.png)

`getExecuteClientProxy` 方法，如果是临时示例使用grpc代理，永久示例则用http代理。

临时实例和永久实例的使用场景可以拿双十一举例，在双十一期间，为了应对流量高峰，需要增加更多的实例，它们就是临时实例，双十一过后，这些实例会被注销，剩下的维持服务平稳运行的实例就是永久实例

##### 4.1 临时实例

临时实例使用 `grpcClientProxy` 注册

![image-20241108120739949](./nacos源码分析-服务注册.assets/image-20241108120739949.png)

`cacheInstanceForRedo`

```java
// 用于缓存需要重做的实例信息。
// 当服务实例需要重新注册时，该方法会将实例信息存储在 registeredInstances 映射中，以便在连接恢复后重新注册这些实例。
public void cacheInstanceForRedo(String serviceName, String groupName, Instance instance) {
  	// groupName + "@@" + serviceName （如DEFAULT_GROUP@@service-provider）
    String key = NamingUtils.getGroupedName(serviceName, groupName);
    // 实例信息，包括组名，服务名，IP，端口等信息
    InstanceRedoData redoData = InstanceRedoData.build(serviceName, groupName, instance);
    synchronized(this.registeredInstances) {
        this.registeredInstances.put(key, redoData);
    }
}
```

将实例信息封装到 `InstanceRequest` ，`requestToServer` 方法就是请求服务端接口注册实例了

![image-20241108121654641](./nacos源码分析-服务注册.assets/image-20241108121654641.png)

##### 4.2 永久实例

永久实例调用 `NamingHttpClientProxy#registerService`注册

![image-20241108122242165](./nacos源码分析-服务注册.assets/image-20241108122242165.png)

> 感谢你看到了这里，我的朋友。
>
> 写累了，刷会儿抖音，哈哈哈哈



### 服务端

#### 注册临时实例

##### 1.注册请求处理器

在服务端有个类 `RequestHandlerRegistry`, 这个类实现了 `ApplicationListener`接口，并且指定了它监听的事件类型为 `ContextRefreshedEvent`。
`ApplicationListener` 是Spring框架中的一个接口，用于定义一个事件监听器，它可以监听Spring应用上下文中发生的事件。
`ContextRefreshedEvent` 是 Spring 框架中的一个事件，表示Spring应用上下文已经初始化完成并且已经刷新，即所有的Bean都已经创建和配置完成

![image-20241108174909000](./nacos源码分析-服务注册.assets/image-20241108174909000.png)

然后看下事件回调方法做了什么

```java
// RequestHandlerRegistry#onApplicationEvent
public void onApplicationEvent(ContextRefreshedEvent event) {
    // 获取RequestHandler的所有实现类
    Map<String, RequestHandler> beansOfType = event.getApplicationContext().getBeansOfType(RequestHandler.class);
    Collection<RequestHandler> values = beansOfType.values();
    for (RequestHandler requestHandler : values) {
      	// ...省略部分代码
        Class<?> clazz = requestHandler.getClass();
        Class tClass = (Class) ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments()[0];
      	// ...省略部分代码
		// 将处理器放到 registryHandlers
        registryHandlers.putIfAbsent(tClass.getSimpleName(), requestHandler);
    }
}
```

注册所有 `RequestHandler` 的实现类，这里面就包括处理注册实例请求的处理器：`InstanceRequestHandler`

##### 2.请求接收器

上面提到过，临时实例使用 `grpcClientProxy` 注册，rpc请求将由`GrpcRequestAcceptor`接收并处理

![image-20241109130421143](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241109130421143.png)

可以看到注入了 `RequestHandlerRegistry`, 在下面的 `request`方法中从`RequestHandlerRegistry`取出对应请求类型的hanlder，然后调用`handleRequest`方法。 

```java
@Override
public void request(Payload grpcRequest, StreamObserver<Payload> responseObserver) {
    // 请求类型，比如"InstanceRequest"
    String type = grpcRequest.getMetadata().getType();
    //.. 省略代码
    RequestHandler requestHandler = requestHandlerRegistry.getByRequestType(type);
    //.. 省略代码
    Response response = requestHandler.handleRequest(request, requestMeta);
    //.. 省略代码
}
```

##### 3.实例请求处理器InstanceRequestHandler

`InstanceRequestHandler`有两个作用：

- 注册临时实例
- 注销临时实例

`handle`方法中如果请求类型是 `registerInstance`,则调用 `registerInstance`方法。

![image-20241109134908184](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241109134908184.png)

从上面图中可以看到，通过构造函数注入了 `EphemeralClientOperationServiceImpl`，然后调用它的`registerInstance`方法继续注册实例

![image-20241109140027793](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241109140027793.png)

在`registerInstance`方法中发布了客户端注册事件`ClientOperationEvent.ClientRegisterServiceEvent`,监听该事件的Listener将会处理该事件完成服务注册

![image-20241109201321583](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241109201321583.png)

##### 4.处理客户端注册事件

`ClientRegisterServiceEvent`类图

![image-20241109210412217](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241109210412217.png)

`ClientRegisterServiceEvent`被`ClientServiceIndexesManager`订阅

事件发生时，进入`onEvent`方法，如果是事件类型是客户端注册服务事件，调用`addPublisherIndexes`

<img src="./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241109210850719.png" alt="image-20241109210850719" style="zoom:150%;" />

``addPublisherIndexes` 方法的作用是将新的服务实例（由 `clientId` 标识）注册到服务（`service`）的发布者列表中，并发布`ServiceChangedEvent`事件，通知所有监听器服务数据已经发生了变化

![image-20241109211751434](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241109211751434.png)

##### 5.推送实例信息到其他客户端

`NamingSubscriberServiceV2Impl`类订阅了`ServiceChangedEvent`

当 `ServiceChangedEvent` 事件发生时，`NamingSubscriberServiceV2Impl` 会将服务变更信息封装成 `PushDelayTask`，然后添加到延迟任务执行引擎 `PushDelayTaskExecuteEngine` 中，以便稍后推送给所有订阅了该服务的客户端

`PushDelayTask` 在 Nacos 中是一个用于处理服务推送延迟任务的类。它主要负责在服务注册或变更时，将最新的服务实例列表推送给所有订阅了该服务的客户端

![image-20241109213109934](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241109213109934.png)

`PushDelayTaskExecuteEngine` 继承了 `NacosDelayTaskExecuteEngine`

![image-20241110172031608](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110172031608.png)

`NacosDelayTaskExecuteEngine`的构造函数中初始化了`tasks`任务列表，还定义了一个单线程的延迟任务执行器`processingExecutor`, 定时执行`ProcessRunnable`任务

![image-20241110172724023](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110172724023.png)

`ProcessRunnable`实现了`Runnable`接口，调用`processTasks`方法处理实例注册任务

![image-20241110172709799](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110172709799.png)

![image-20241110173016094](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110173016094.png)

上面的`processor`是 推送延迟任务处理器：`PushDelayTaskProcessor`，调用`process`方法

从task拿到服务信息，封装成 `PushExecuteTask`,调度器调用执行引擎指定推送任务。

![image-20241110183725281](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110183725281.png)

核心逻辑在`PushExecuteTask`的`run`方法中，生成包装器，然后向客户端的全部订阅者或者部分客户端推送数据

![image-20241110184854642](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110184854642.png)



#### 注册永久实例

##### 1. 注册实例接口

注册永久实例首先需要修改实例的配置信息，然后启动 nacos 集群

```properties
spring.cloud.nacos.discovery.ephemeral=false
```

前面讲过，注册永久实例是通过HTTP调用的方式，我们可以看下官方给出的[`OpenAPI指南`](https://nacos.io/zh-cn/docs/open-api.html)

![image-20241110190247838](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110190247838.png)

请求路径为`/nacos/v1/ns/instance`,如果你直接在源码中 ctrl+shif+f 是搜不到的，因为它是由几个常量组成的

![image-20241110190521110](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110190521110.png)

注册实例由`InstanceController`的`register`方法实现

![image-20241110191304164](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110191304164.png)

`InstanceOperatorClientImpl#registerInstance`方法检查实例参数合法性，封装服务信息，继续调用service注册实例

![image-20241110191824730](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110191824730.png)

##### 2. ClientOperationService

`ClientOperationService`接口中定义了注册和注销实例的方法，分别有注册临时实例和永久实例两种实现

![image-20241110192627682](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110192627682.png)

![image-20241110192452043](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110192452043.png)

因为我们是注册永久实例，所以调用`PersistentClientOperationServiceImpl`的`registerInstance`方法

![image-20241110194717154](./nacos%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90-%E6%9C%8D%E5%8A%A1%E6%B3%A8%E5%86%8C.assets/image-20241110194717154.png)

##### 3. JRaft实现CP模型

上面源码截图中，有一句关键代码`protocol.write(writeRequest)`, 那么 `protocol`是什么呢？

![image-20241111123843539](./nacos源码分析-服务注册.assets/image-20241111123843539.png)

`protocol`是一个`CPProtocol`类型的成员变量，在`PersistentClientOperationServiceImpl`的构造函数中通过`ProtocolManager`获取并赋值给`protocol`。

看下`getCpProtocol`方法做了什么

![image-20241111124403018](./nacos源码分析-服务注册.assets/image-20241111124403018.png)

拿到`CPProtocol`类型的Bean并执行初始化，`CPProtocol`类型的Bean只有一个：`JRaftProtocol`,  在继续看源码之前，先了解一下什么是 `JRaft`



JRaft 是一个纯 Java 开发的 Raft 算法实现库，它基于百度的 braft 实现而来，并使用 Java 重写了所有功能。以下是 JRaft 支持的主要功能：

1. **Raft 协议实现**：JRaft 提供了 Raft 一致性算法的核心实现，包括领导者选举、日志复制、持久化和快照等。

2. **领导者选举**：JRaft 支持自动的领导者选举机制，能够在当前领导者失败时快速选举出新的领导者。
3. **日志复制**：JRaft 实现了日志复制功能，确保集群中的所有节点都能保持日志的一致性。
4. **数据持久化**：JRaft 支持数据的持久化存储，确保节点重启后能够恢复到之前的状态。
5. **快照机制**：为了优化性能和减少存储空间的使用，JRaft 提供了快照机制，可以将当前状态保存为快照文件。
6. **数据恢复**：JRaft 支持从快照和日志中恢复数据，以便在节点故障后快速恢复服务。
7. **读写一致性**：JRaft 提供了线性一致性和最终一致性的读写操作，以满足不同的业务需求。
8. **集群管理**：JRaft 允许动态地添加、移除和配置 Raft 集群中的节点。
9. **异步接口**：JRaft 提供了异步接口，支持非阻塞的读写操作，提高系统的吞吐量。
10. **多线程支持**：JRaft 支持多线程环境，可以在高并发场景下工作。
11. **客户端支持**：JRaft 提供了客户端库，使得客户端可以轻松地与 Raft 集群交互。
12. **容错机制**：JRaft 实现了容错机制，能够在节点故障时继续提供服务。
13. **可插拔的存储和网络层**：JRaft 允许用户自定义存储和网络层，以适应不同的存储和网络环境。
14. **监控和日志**：JRaft 提供了监控接口和详细的日志输出，方便用户监控集群状态和调试问题。
15. **跨平台**：JRaft 可以在多种操作系统和平台上运行，具有良好的跨平台性。

> 更多信息参考 [`JRaft用户指南`](https://www.sofastack.tech/projects/sofa-jraft/jraft-user-guide/)

Nacos 在设计时考虑了CAP理论，并提供了两种一致性模型：AP（Availability & Partition tolerance）和CP（Consistency & Partition tolerance）。

1. **AP模型**：Nacos默认采用AP模型，即在网络分区的情况下，优先保证系统的可用性，而不是一致性。这意味着在网络分区时，Nacos仍然可以对外提供服务，但可能会出现数据不一致的情况。AP模型适用于对可用性要求较高，但对一致性要求相对较低的场景，例如电商系统中的服务发现和配置管理。
2. **CP模型**：Nacos也支持CP模型，即在网络分区的情况下，优先保证数据的一致性，而不是可用性。这意味着在网络分区时，Nacos可能会暂时无法对外提供服务，直到网络恢复并达到一致性。CP模型适用于对一致性要求较高，但对可用性要求相对较低的场景，例如金融系统中的数据一致性至关重要，因此可能需要选择CP模型。

> `Nacos2.X`版本采用 JRaft 框架实现`CP`模型



继续看源码，`JRaftProtocol` Bean的`init`方法，初始化并启动 JRaft Server

![image-20241111131709997](./nacos源码分析-服务注册.assets/image-20241111131709997.png)

启动 JRaftServer

![image-20241111162702811](./nacos源码分析-服务注册.assets/image-20241111162702811.png)

创建多 Raft 组

```java
// com.alibaba.nacos.core.distributed.raft.JRaftServer#createMultiRaftGroup
synchronized void createMultiRaftGroup(Collection<RequestProcessor4CP> processors) {
    // There is no reason why the LogProcessor cannot be processed because of the synchronization
    if (!this.isStarted) {
        this.processors.addAll(processors);
        return;
    }
    // 定义 parentPath 为 nacos.home下的data/protocol/raft 目录
    final String parentPath = Paths.get(EnvUtil.getNacosHome(), "data/protocol/raft").toString();

    for (RequestProcessor4CP processor : processors) {
        // 获取处理器的 groupName, 如：naming_persistent_service_v2
        final String groupName = processor.group();
        // 检查 multiRaftGroup 中是否已存在该 groupName，如果存在则抛出 DuplicateRaftGroupException。
        if (multiRaftGroup.containsKey(groupName)) {
            throw new DuplicateRaftGroupException(groupName);
        }

        // 复制当前的 Configuration 和 NodeOptions
        // Ensure that each Raft Group has its own configuration and NodeOptions
        Configuration configuration = conf.copy();
        NodeOptions copy = nodeOptions.copy();
        // 初始化目录
        JRaftUtils.initDirectory(parentPath, groupName, copy);

        // 创建 NacosStateMachine 并设置到 NodeOptions 中
        // 在这里，LogProcessor被传递给StateMachine，当StateMachine触发onApply时，实际调用LogProcessor的onApply
        // 比如调用PersistentClientOperationServiceImpl的onApply去注册实例
        NacosStateMachine machine = new NacosStateMachine(this, processor);

        copy.setFsm(machine);
        copy.setInitialConf(configuration);

        // Set snapshot interval, default 1800 seconds 设置快照间隔，默认1800秒
        int doSnapshotInterval = ConvertUtils.toInt(raftConfig.getVal(RaftSysConstants.RAFT_SNAPSHOT_INTERVAL_SECS),
                RaftSysConstants.DEFAULT_RAFT_SNAPSHOT_INTERVAL_SECS);

        // If the business module does not implement a snapshot processor, cancel the snapshot
        doSnapshotInterval = CollectionUtils.isEmpty(processor.loadSnapshotOperate()) ? 0 : doSnapshotInterval;

        copy.setSnapshotIntervalSecs(doSnapshotInterval);
        Loggers.RAFT.info("create raft group : {}", groupName);
        // 创建 RaftGroupService 并启动节点
        RaftGroupService raftGroupService = new RaftGroupService(groupName, localPeerId, copy, rpcServer, true);

        // Because BaseRpcServer has been started before, it is not allowed to start again here
        Node node = raftGroupService.start(false);
        machine.setNode(node);
        // 更新 RouteTable 配置
        RouteTable.getInstance().updateConfiguration(groupName, configuration);

        // 注册自己到集群中
        RaftExecutor.executeByCommon(() -> registerSelfToCluster(groupName, localPeerId, configuration));

        // Turn on the leader auto refresh for this group
        Random random = new Random();
        long period = nodeOptions.getElectionTimeoutMs() + random.nextInt(5 * 1000);
        // 设置定时任务定期刷新路由表
        RaftExecutor.scheduleRaftMemberRefreshJob(() -> refreshRouteTable(groupName),
                nodeOptions.getElectionTimeoutMs(), period, TimeUnit.MILLISECONDS);
        // 将 Raft 组信息存储到 multiRaftGroup 中
        multiRaftGroup.put(groupName, new RaftGroupTuple(node, processor, raftGroupService, machine));
    }
}
```

上面代码中重点是 Nacos 状态机的创建，注册了`processor`(这里注册的是永久实例，所以`processor`是`PersistentClientOperationServiceImpl`)。

将`Task`提交到`sofa-jraft`框架后，当超半数节点commit log成功，最终会调用用户实现的状态机的`onApply`方法

> 释义来自 [JRaft 用户指南](https://www.sofastack.tech/projects/sofa-jraft/jraft-user-guide/)

![image-20241111150336558](./nacos源码分析-服务注册.assets/image-20241111150336558.png)



以上就是`protocol`变量的一个初始化过程，然后继续看`protocol.write(writeRequest)`这行代码，进入`JRaftProtocol`类`commit`方法处理注册请求

![image-20241111141333646](./nacos源码分析-服务注册.assets/image-20241111141333646.png)

![image-20241111141825526](./nacos源码分析-服务注册.assets/image-20241111141825526.png)

`commit`方法的核心是`applyOperation`方法，就算不是 Leader 节点，转发请求以后，最终还是会由 Leader 节点执行`applyOperation`

![image-20241111144824139](./nacos源码分析-服务注册.assets/image-20241111144824139.png)

调用`node.apply`方法，这里使用`sofa-jraft`的`Node.apply(Task)`方法提交本次写入请求到Raft集群

上面在创建 Nacos 状态机 那里提到，请求提交给 Raft 集群后，最终会调用用户实现的状态机的`onApply`方法

下面看下 Nacos 状态机`onApply`的实现

![image-20241111153555093](./nacos源码分析-服务注册.assets/image-20241111153555093.png)

如果是写请求，则调用`processor`的`onApply`方法，我们是写入永久实例，这里注册的`processor`是`PersistentClientOperationServiceImpl`

如果是注册实例，调用`onInstanceRegister`

![image-20241111154312180](./nacos源码分析-服务注册.assets/image-20241111154312180.png)

![image-20241111154710628](./nacos源码分析-服务注册.assets/image-20241111154710628.png)

最终发布了客户端注册事件`ClientRegisterServiceEvent`，后面的逻辑就和注册临时实例时发布客户端注册事件`ClientRegisterServiceEvent`一样了。



> 感谢看到这里的朋友，如果你觉得写的还行或者对你有帮助，希望能求一个赞，如果你觉得文章很垃圾，也希望你能提出宝贵的意见，非常感谢
