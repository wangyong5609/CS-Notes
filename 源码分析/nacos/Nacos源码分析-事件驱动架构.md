## 1. 引言

### 1.1 本文目的

- Nacos 源码中使用了大量的事件来做异步处理，比如注册服务，配置变更等；所以单独写一篇来认识什么是事件驱动架构
- 分析 Nacos 采用事件驱动架构的原因
- 理解 Nacos 的设计和关键源码分析

### 1.2 事件驱动架构概述

#### 1.2.1 定义和核心概念

🐳**定义**：事件驱动架构（Event-Driven Architecture, EDA）是一种软件架构模式，基于事件的产生、传播和响应来设计系统。它使得系统可以通过事件来解耦组件之间的关系，从而提高系统的灵活性和可扩展性。

🐳**核心概念**

- **事件**: 事件是系统中发生的某个特定动作或状态变化的通知。例如，注册实例，发布配置等都可以视为事件
- **事件源**: 事件源是产生事件的组件或系统
- **事件处理器**: 事件处理器是响应和处理特定事件的组件或服务。它们可以根据接收到的事件执行相应的逻辑
- **事件总线**: 事件总线是一个中介，用于传递事件。在许多事件驱动架构中，事件会首先被发送到事件总线，然后由感兴趣的事件处理器接收和处理

#### 1.2.2 事件驱动架构的特性

- **解耦**: 事件驱动架构使得系统的各个组件之间通过事件进行通信，而不是直接相互调用。这种解耦降低了组件之间的依赖性，提高了系统的灵活性
- **异步处理**: 事件的处理通常是异步的，这意味着事件产生后不需要等待处理完成，可以继续执行其他操作。这种方式可以提高系统的响应性和性能
- **可扩展性**: 通过添加新的事件处理器，可以轻松扩展系统的功能，而不需要对现有组件进行重大更改
- **灵活性**: 新的事件源和事件处理器可以在不影响现有系统的情况下进行添加或修改，从而提高了系统的灵活性

## 2. 为什么 Nacos 选择事件驱动架构

### 2.1 解耦组件之间的关系

- **降低依赖性**: 事件驱动架构使得 Nacos 中的不同组件（如服务注册、服务发现、配置管理等）能够通过事件进行通信，而不是直接调用。

### 2.2 异步处理和高效性

- **非阻塞操作**: 通过事件驱动，Nacos 能够实现非阻塞的操作。例如，服务注册发布`ClientRegisterServiceEvent`事件交给订阅者处理，而无需等待响应。这样可以提高系统的响应速度和处理能力，特别是在高并发的环境中。

### 2.3 可扩展性

- **灵活的扩展与修改**: 事件驱动架构允许开发者在不影响现有系统的情况下添加新的事件源和事件处理器。这使得 Nacos 能够灵活地适应新的需求和功能，而不需要进行重大更改。

### 2.4 复杂业务流程的处理

- **工作流管理**: 事件驱动架构非常适合于处理复杂的业务流程。在 Nacos 中，多个事件可以组合在一起，形成复杂的工作流，从而更好地管理服务的生命周期和配置的状态。例如，配置动态更新使用了多个事件完成刷新配置信息和初始化配置Bean。

### 2.5 监控与故障处理

- **事件记录与监控**: Nacos 可以记录事件的发生和处理过程，便于监控系统的运行状态和性能指标。在源码中`TraceEvent`记录了服务及其健康状态变化的发生和处理过程。

## 3. Nacos 事件驱动架构的核心组件

### 3.1 事件模型

`Event`是所有事件的抽象类父类，图中以服务注册为例，`ClientOperationEvent`继承了事件基类，并定义了服务注册与注销内部类。

![image-20241203093509733](https://qny.bbbwdc.com/blog/image-20241203093509733.png)

### 3.2 事件订阅者Subscriber

`Subscriber`是所有订阅者的父类，订阅者订阅`Event`类型的事件并处理事件，基类定义了以下基本功能：

- `onEvent`: 事件发生时的回调方法
- `subscribeType`: 订阅的事件类型
- `executor`: 订阅者自身实现的任务执行器，如果是异步任务由执行器执行
- `ignoreExpireEvent`:  是否忽略过期事件
- `scopeMatches`: 事件的范围是否与当前订阅者匹配。默认实现是所有范围都匹配

![image-20241203095345326](https://qny.bbbwdc.com/blog/image-20241203095345326.png)

有一个特别的订阅者在服务注册源码中被使用：`SmartSubscriber`。

它定义了新的抽象方法`subscribeTypes`, 为订阅者扩展了订阅多个事件的功能。

```java
/**
 * 可以监听多个事件的订阅者
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class SmartSubscriber extends Subscriber<Event> {
    
    /**
     * 订阅者订阅的的事件类型列表
     *
     * @return The interested event types.
     */
    public abstract List<Class<? extends Event>> subscribeTypes();
}
```

### 3.3 事件发布器EventPublisher

`EventPublisher` 接口是 Nacos 中用于事件发布的核心接口，定义了事件发布器的基本功能和行为。

![image-20241203102003084](https://qny.bbbwdc.com/blog/image-20241203102003084.png)

- `publish`: 发布事件。将事件发送到所有注册的订阅者，触发相应的处理逻辑
- `init`: 初始化事件发布器
- `notifySubscriber`: 通知事件的订阅者。直接调用订阅者的处理逻辑，执行事件的具体处理
- `currentEventSize`: 获取当前暂存事件的数量。发布的事件会放入阻塞队列依次处理
- `addSubscriber`: 添加事件订阅者
- `removeSubscriber`: 移除事件订阅者

`EventPublisher` 接口有多个不同类型的发布器实现，以下是对它们的解析：

![image-20241203101425727](https://qny.bbbwdc.com/blog/image-20241203101425727.png)

- `DefaultPublisher`: 默认发布器，主要作用是将事件广播到所有注册的订阅者
- `ShardedEventPublisher`: 分片事件发布器，旨在将事件发布负载分散到多个发布器上，从而提高系统的可扩展性和性能
- `DefaultSharePublisher`: 默认分片发布器，主要用来处理一些耗时的事件, 事件共享同一个发布器
- `NamingEventPublisher`: 是专门用于发布与服务注册、发现、健康检查等相关事件的发布器
- `TraceEventPublisher`: 用于发布追踪事件的发布器。它主要用于记录和发布与服务追踪相关的事件信息

### 3.4 通知中心NotifyCenter

`NotifyCenter` 是 Nacos 中用于事件通知的核心组件，在事件驱动架构中充当了事件的“邮递员”，负责将事件从发布者传递到所有感兴趣的订阅者。发布者与订阅者不直接交互，降低耦合，便于扩展且提升系统处理性能。

![image-20241203155433728](https://qny.bbbwdc.com/blog/image-20241203155433728.png)

以下是一些重要的属性和方法解析：

**属性**

```java
// 事件类型和发布器的映射表
private final Map<String, EventPublisher> publisherMap = new ConcurrentHashMap<>(16);
// 慢事件分片发布器
private DefaultSharePublisher sharePublisher;
```

**方法**

- `publishEvent`: 使用发布器发布一个事件，通知所有注册的订阅者
- `registerSubscriber`: 注册一个事件订阅者，供其接收特定事件的通知
- `registerToPublisher`: 注册事件与发布器到`publisherMap`, 当事件发生时，便于取出事件对应的发布器

## 4. 源码分析

下面以注册临时实例流程为例分析事件发布订阅通知的流程。涉及以下核心类：

- com.alibaba.nacos.naming.controllers.v2.**InstanceControllerV2**
- com.alibaba.nacos.naming.core.**InstanceOperatorClientImpl**
- com.alibaba.nacos.naming.core.v2.service.impl.**EphemeralClientOperationServiceImpl**
- com.alibaba.nacos.naming.core.v2.index.**ClientServiceIndexesManager**

### 4.1 发布客户端注册服务事件ClientRegisterServiceEvent

从接收到注册临时实例的 HTTP 请求到发布客户端注册服务事件的方法调用流程

- `InstanceControllerV2#register()`
- `InstanceOperatorClientImpl#registerInstance()`
- `EphemeralClientOperationServiceImpl#registerInstance()` 

在`registerInstance`方法中发布了客户端注册服务事件`ClientRegisterServiceEvent`。

```java
// EphemeralClientOperationServiceImpl#registerInstance
public void registerInstance(Service service, Instance instance, String clientId) throws NacosException {
    // 检查实例参数
    NamingUtils.checkInstanceIsLegal(instance);
    // 获取服务实例发布信息,如namespace,group,name等
    Service singleton = ServiceManager.getInstance().getSingleton(service);
    // 如果当前服务是持久服务，不能注册临时实例
    if (!singleton.isEphemeral()) {
        throw new NacosRuntimeException(NacosException.INVALID_PARAM,
                String.format("Current service %s is persistent service, can't register ephemeral instance.",
                        singleton.getGroupedServiceName()));
    }
    Client client = clientManager.getClient(clientId);
    checkClientIsLegal(client, clientId);
    InstancePublishInfo instanceInfo = getPublishInfo(instance);
    client.addServiceInstance(singleton, instanceInfo);
    client.setLastUpdatedTime();
    client.recalculateRevision();
    // 发布客户端注册服务事件
    NotifyCenter.publishEvent(new ClientOperationEvent.ClientRegisterServiceEvent(singleton, clientId));
    // 发布元数据事件
    NotifyCenter
            .publishEvent(new MetadataEvent.InstanceMetadataEvent(singleton, instanceInfo.getMetadataId(), false));
}
```

### 4.2 事件订阅者ClientServiceIndexesManager

订阅者订阅事件，那么谁是客户端注册服务事件的订阅者呢？要找到它的订阅者，就要去找继承`Subscriber`类并订阅`ClientRegisterServiceEvent`的代码，类似这样：`extends Subscriber<ClientRegisterServiceEvent>`。

![image-20241204155952191](https://qny.bbbwdc.com/blog/image-20241204155952191.png)

在事件类的用法中，并没有找到类似的代码，却有一段代码是添加这个事件类:`result.add(ClientOperationEvent.ClientRegisterServiceEvent.class);`;还记得上面提到了一个特殊的订阅者：`SmartSubscriber`,它可以订阅多种事件。`ClientServiceIndexesManager`继承了`SmartSubscriber`, 订阅了多个客户端操作事件，就包括客户端注册服务事件。

```java
// ClientServiceIndexesManager#subscribeTypes
public List<Class<? extends Event>> subscribeTypes() {
    List<Class<? extends Event>> result = new LinkedList<>();
    // 订阅客户端注册服务事件
    result.add(ClientOperationEvent.ClientRegisterServiceEvent.class);
    result.add(ClientOperationEvent.ClientDeregisterServiceEvent.class);
    result.add(ClientOperationEvent.ClientSubscribeServiceEvent.class);
    result.add(ClientOperationEvent.ClientUnsubscribeServiceEvent.class);
    result.add(ClientOperationEvent.ClientReleaseEvent.class);
    return result;
}
```

### 4.3 注册订阅者到发布器NamingEventPublisher

事件的订阅者找到了，那么这个订阅者是在哪里注册到通知中心的呢，答案是它的构造函数。在构造函数中注册自身到通知中心，并且传入了一个发布器工厂参数，用来生成发布器。

```java
public ClientServiceIndexesManager() {
    NotifyCenter.registerSubscriber(this, NamingEventPublisherFactory.getInstance());
}
```

接着看`NotifyCenter#registerSubscriber()`。

```java
public static void registerSubscriber(final Subscriber consumer, final EventPublisherFactory factory) {
    if (consumer instanceof SmartSubscriber) {
        // 获取订阅的事件类型
        for (Class<? extends Event> subscribeType : ((SmartSubscriber) consumer).subscribeTypes()) {
            // For case, producer: defaultSharePublisher -> consumer: smartSubscriber.
            // 是否为SlowEvent的子类
            if (ClassUtils.isAssignableFrom(SlowEvent.class, subscribeType)) {
                INSTANCE.sharePublisher.addSubscriber(consumer, subscribeType);
            } else {
                // 添加订阅者到发布器
                addSubscriber(consumer, subscribeType, factory);
            }
        }
        return;
    }
    
    final Class<? extends Event> subscribeType = consumer.subscribeType();
    if (ClassUtils.isAssignableFrom(SlowEvent.class, subscribeType)) {
        INSTANCE.sharePublisher.addSubscriber(consumer, subscribeType);
        return;
    }
    
    addSubscriber(consumer, subscribeType, factory);
}
```

`addSubscriber`方法会为事件匹配发布器，然后把订阅者添加到发布器上。

`ClientRegisterServiceEvent`的发布器是通过发布器工厂`NamingEventPublisherFactory#apply()`创建的。并且**把发布器放入事件与发布器映射表`publisherMap`中**。

```java
private static void addSubscriber(final Subscriber consumer, Class<? extends Event> subscribeType,
        EventPublisherFactory factory) {
    // 类的规范名称，比如：com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent.ClientRegisterServiceEvent
    final String topic = ClassUtils.getCanonicalName(subscribeType);
    synchronized (NotifyCenter.class) {
        // 创建发布器，并放入事件与发布器映射表中
        MapUtil.computeIfAbsent(INSTANCE.publisherMap, topic, factory, subscribeType, ringBufferSize);
    }
    EventPublisher publisher = INSTANCE.publisherMap.get(topic);
  	// 订阅者添加到发布器
    if (publisher instanceof ShardedEventPublisher) {
        ((ShardedEventPublisher) publisher).addSubscriber(consumer, subscribeType);
    } else {
        publisher.addSubscriber(consumer);
    }
}
```

工厂为`ClientRegisterServiceEvent`创建一个发布器：`NamingEventPublisher`

```java
public EventPublisher apply(final Class<? extends Event> eventType, final Integer maxQueueSize) {
    // Like ClientEvent$ClientChangeEvent cache by ClientEvent
    Class<? extends Event> cachedEventType =
            eventType.isMemberClass() ? (Class<? extends Event>) eventType.getEnclosingClass() : eventType;
    return publisher.computeIfAbsent(cachedEventType, eventClass -> {
        NamingEventPublisher result = new NamingEventPublisher();
        result.init(eventClass, maxQueueSize);
        return result;
    });
}
```

### 4.4 发布器发布事件

现在订阅者和发布器都注册到通知中心了，处理事件的基本要素都已具备，接着看发布客户端注册服务事件。

调用`publishEvent`方法匹配事件的发布器，并使用发布器发布事件。

```java
// NotifyCenter#publishEvent
private static boolean publishEvent(final Class<? extends Event> eventType, final Event event) {
    if (ClassUtils.isAssignableFrom(SlowEvent.class, eventType)) {
        return INSTANCE.sharePublisher.publish(event);
    }
    // 事件类型
    final String topic = ClassUtils.getCanonicalName(eventType);
    // 从映射表取出发布器
    EventPublisher publisher = INSTANCE.publisherMap.get(topic);
    if (publisher != null) {
        // 发布器发布事件
        return publisher.publish(event);
    }
    if (event.isPluginEvent()) {
        return true;
    }
    return false;
}
```

### 4.5 通知订阅者

客户端注册服务事件的发布器是`NamingEventPublisher`, 调用它的`pulish`方法，`publish`方法会将事件放入阻塞队列，然后调用`handleEvent`方法。

```java
private void handleEvent(Event event) {
    Class<? extends Event> eventType = event.getClass();
    // 事件的全部订阅者
    Set<Subscriber<? extends Event>> subscribers = subscribes.get(eventType);
    if (null == subscribers) {
        if (Loggers.EVT_LOG.isDebugEnabled()) {
            Loggers.EVT_LOG.debug("[NotifyCenter] No subscribers for slow event {}", eventType.getName());
        }
        return;
    }
    for (Subscriber subscriber : subscribers) {
        // 通知订阅者
        notifySubscriber(subscriber, event);
    }
}
```

`notifySubscriber`方法调用订阅者事件回调方法`onEvent`通知订阅者。

```java
public void notifySubscriber(Subscriber subscriber, Event event) {
    // 调用订阅者事件回调方法
    final Runnable job = () -> subscriber.onEvent(event);
    // 如果订阅者有自己的线程池，使用线程池执行，否则立即执行
    final Executor executor = subscriber.executor();
    if (executor != null) {
        executor.execute(job);
    } else {
        try {
            job.run();
        } catch (Throwable e) {
            Loggers.EVT_LOG.error("Event callback exception: ", e);
        }
    }
}
```

到此，客户端注册服务事件的发布订阅流程就结束了。

## 5. 总结

Nacos 的事件驱动架构为微服务环境中的服务管理和配置管理提供了灵活、高效的解决方案。通过将系统中的组件解耦，支持异步处理和动态响应，Nacos 能够在快速变化的环境中保持高可用性和可靠性。

> 了解 Nacos 事件驱动架构，对于阅读服务注册和配置管理相关源码也很有帮助。往往是多个事件组成工作流完成一个业务流程。

>  您的点赞和关注是我写作的最大动力，感谢支持！



参考链接

- [什么是事件驱动的架构](https://www.ibm.com/cn-zh/topics/event-driven-architecture)
- [nacos2.x的事件驱动架构](https://blog.csdn.net/likang_1167/article/details/143752764)


