> Nacos server: 2.4.3
>
> JDK: 1.8
>
> spring-cloud-starter-alibaba-nacos-discovery-2021.0.5.0.jar
>
> Maven: 3.8.8

## 一、Nacos简介

Nacos 是一个更易于构建云原生应用的动态服务发现、配置管理和服务管理平台

![image.png](./nacos源码分析-服务注册.assets/1637460801625-abaec6c8-82a8-46cf-9b86-7b7ecc2968e4.png)

### 架构图

整体架构分为用户层、业务层、内核层和插件，用户层主要解决用户使用的易用性问题，业务层主要解决服务发现和配置管理的功能问题，内核层解决分布式系统一致性、存储、高可用等核心问题，插件解决扩展性问题。

![img](./nacos源码分析-服务注册.assets/1543984258587-3a2cc018-728f-414e-8186-216b896122c9.png)

## 二、服务注册原理

客户端请求原理图：

> Processon 地址：https://www.processon.com/diagraming/672d9c0ca8011b320f4a064c

![image-20241108135709194](./nacos源码分析-服务注册.assets/image-20241108135709194.png)

简单来说就是，项目引入 `spring-cloud-starter-alibaba-nacos-discovery` 以后，利用Spring的自动装配，在它的`spring.facotries`中加载了 `NacosServiceRegistryAutoConfiguration`, `NacosServiceRegistryAutoConfiguration` 中加载了一个叫 `NacosAutoServiceRegistration` 的Bean，

这个Bean的父类实现了 `ApplicationListener` 这个接口，并监听了`WebServerInitializedEvent`类型的事件，在Tomcat启动后会发布`WebServerInitializedEvent`的事件，事件被监听到以后，就会调用 `NacosServiceRegistry`的 `register` 方法向Nacos服务端注册实例。

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

永久使用调用 `NamingHttpClientProxy#registerService`注册

![image-20241108122242165](./nacos源码分析-服务注册.assets/image-20241108122242165.png)

> 感谢你看到了这里，我的朋友。
>
> 写累了，刷会儿抖音，哈哈哈哈



### 服务端

#### 1. 注册请求处理器

在服务端有个类 `RequestHandlerRegistry`, 这个类实现了 `ApplicationListener`  接口，并且指定了它监听的事件类型为 `ContextRefreshedEvent`。
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
        //register tps control.
        try {
            Method method = clazz.getMethod("handle", Request.class, RequestMeta.class);
            // handle方法上有TpsControl注解，且开启了tps控制
            if (method.isAnnotationPresent(TpsControl.class) && TpsControlConfig.isTpsControlEnabled()) {
                TpsControl tpsControl = method.getAnnotation(TpsControl.class);
                String pointName = tpsControl.pointName();
                ControlManagerCenter.getInstance().getTpsControlManager().registerTpsPoint(pointName);
            }
        } catch (Exception e) {
            //ignore.
        }

        Class tClass = (Class) ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments()[0];

        //register invoke source.
        try {
            if (clazz.isAnnotationPresent(InvokeSource.class)) {
                InvokeSource tpsControl = clazz.getAnnotation(InvokeSource.class);
                // 类的调用来源
                String[] sources = tpsControl.source();
                if (sources != null && sources.length > 0) {
                    sourceRegistry.put(tClass.getSimpleName(), Sets.newHashSet(sources));
                }
            }
        } catch (Exception e) {
            //ignore.
        }
				// 将处理器放到 registryHandlers
        registryHandlers.putIfAbsent(tClass.getSimpleName(), requestHandler);
    }
}
```

注册所有 `RequestHandler` 的实现类，这里面就包括处理注册实例请求的处理器：`InstanceRequestHandler`

#### 2. 实例请求处理器
