Nacos 是 Alibaba 提供的一个开源项目，除了服务发现之外，还可以作为配置中心使用。

本文围绕以下两个问题展开：

- 客户端启动时是如何从 nacos 服务端拉取并加载配置？
- 配置如何动态更新？

## 原理简图

> 读完文章后再看此图更易于理解
>
> ProcessOn 链接：https://www.processon.com/diagraming/673474c6cb069f21f520813d
>
> 其他作者分享的一份更细致的流程图：https://www.processon.com/view/link/62d678c31e08531cf8db16ef

![](https://qny.bbbwdc.com/blog/4523r32r23rdf.png)



## 启动加载 NacosConfigBootstrapConfiguration

`Springboot`在启动的时候会读取 `spring-cloud-starter-alibaba-nacos-config-2021.0.5.0.jar`下的 `spring.factories`加载`com.alibaba.cloud.nacos.NacosConfigBootstrapConfiguration`

![image-20241114152350162](https://qny.bbbwdc.com/blog/image-20241114152350162.png)

调用`SpringFactoriesLoader#loadFactoryNames`获取工厂类型名为`org.springframework.cloud.bootstrap.BootstrapConfiguration`的 类名列表。`org.springframework.cloud.bootstrap.BootstrapConfiguration`就是上面 `spring.factories`中的 `KEY`值

`loadSpringFactories`是加载 `spring.factories`文件的具体执行方法，返回一个`HashMap`。

![image-20241114153917736](https://qny.bbbwdc.com/blog/image-20241114153917736.png)

![image-20241114152249007](https://qny.bbbwdc.com/blog/image-20241114152249007.png)

## NacosConfigBootstrapConfiguration

`NacosConfigBootstrapConfiguration` 是 Spring Cloud Alibaba 中与 Nacos 配置管理相关的一个配置类。它主要用于在 Spring Boot 应用程序中引导 Nacos 配置的加载和管理。

![image-20241114155252523](https://qny.bbbwdc.com/blog/image-20241114155252523.png)

配置类中一共加载了四个`Bean`，它们的作用如下：

### 1. NacosConfigProperties

`NacosConfigProperties`用于封装与 Nacos 配置相关的属性。它提供了对 Nacos 配置中心的连接和配置管理所需的各种设置。

![image-20241114155745939](https://qny.bbbwdc.com/blog/image-20241114155745939.png)



### 2. NacosConfigManager

主要用于管理和操作 Nacos 配置相关的功能。它提供了一种简便的方式来获取、更新和管理 Nacos 配置中心中的配置信息。

来看下`NacosConfigManager`的构造函数做了什么：

![image-20241114173808589](https://qny.bbbwdc.com/blog/image-20241114173808589.png)

上面创建的 Bean `NacosConfigProperties` 作为参数传入构造函数，将配置信息交给 `NacosConfigManager`管理，并且调用`createConfigService`创建 `ConfigService`。

![image-20241114174513189](https://qny.bbbwdc.com/blog/image-20241114174513189.png)

`createConfigService`方法中通过反射的方式调用构造函数创建了`NacosConfigService`

#### 2.1 NacosConfigService

`NacosConfigService` 是 Nacos 客户端中的一个核心类，负责与 Nacos 配置中心进行交互。`NacosConfigService`实现了`ConfigService`接口，可以从下图看到，`ConfigService`接口定义了发布、获取、移除、监听配置的接口，提供了多种方法来获取、发布和管理配置数据的功能。这个类是 Nacos Java 客户端的主要入口之一，允许开发者方便地操作 Nacos 配置。

![image-20241114175748329](https://qny.bbbwdc.com/blog/image-20241114175748329.png)

继续看`NacosConfigService`的构造函数

![image-20241114174851225](https://qny.bbbwdc.com/blog/image-20241114174851225.png)

```java
public NacosConfigService(Properties properties) throws NacosException {
    // 派生 properties 并生成NacosClientProperties
    final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
    // 检查 contextPath 是否符合正则
    ValidatorUtils.checkInitParam(clientProperties);
		
    // 初始化命名空间
    initNamespace(clientProperties);
    // 配置过滤器链
    this.configFilterChainManager = new ConfigFilterChainManager(clientProperties.asProperties());
    // 管理服务器列表，它的成员变量isFixed决定它使用固定的服务器列表还是动态的从 nacos 拉取服务器列表
    ServerListManager serverListManager = new ServerListManager(clientProperties);
    // 如果是动态获取服务器列表，就从 nacos 服务端拉取
    serverListManager.start();
		
  	// 初始化 ClientWorker 实例，负责长轮询和配置管理
    this.worker = new ClientWorker(this.configFilterChainManager, serverListManager, clientProperties);
    // will be deleted in 2.0 later versions
    // ServerHttpAgent 是一个用于与 Nacos 服务器进行 HTTP 通信的代理类。它负责处理 HTTP 请求（GET、POST、DELETE 等）
    agent = new ServerHttpAgent(serverListManager);

}
```

重点在于创建`ClientWorker`实例

#### 2.2 ClientWorker

`ClientWorker` 主要用于封装与 Nacos 配置服务的交互逻辑，提供配置的获取、监听和更新等功能。它确保了客户端能够高效地与 Nacos 服务器通信，并管理配置的生命周期。

构造函数：

```java
public ClientWorker(final ConfigFilterChainManager configFilterChainManager, ServerListManager serverListManager,
        final NacosClientProperties properties) throws NacosException {
    this.configFilterChainManager = configFilterChainManager;
		
    init(properties);
		
  	// 创建ConfigTransportClient
    agent = new ConfigRpcTransportClient(properties, serverListManager);
  	// 根据系统的处理器核心数和一个指定的线程倍数，计算出一个适合的工作线程数，THREAD_MULTIPLE为 1，假如服务器是 2 核，那 count 就是 2
    int count = ThreadUtils.getSuitableThreadCount(THREAD_MULTIPLE);
  	// 创建线程池
    ScheduledExecutorService executorService = Executors
            .newScheduledThreadPool(Math.max(count, MIN_THREAD_NUM), r -> {
                Thread t = new Thread(r);
                t.setName("com.alibaba.nacos.client.Worker");
                t.setDaemon(true);
                return t;
            });
    agent.setExecutor(executorService);
    agent.start();
}
```

重点是 **`agen.start`**方法，前面都是为启动它做准备

`start`方法启动了一个安全代理，并且每 5 秒登录一次，维持会话的有效性；然后调用`startInternal`方法执行核心逻辑

```java
public void start() throws NacosException {
      securityProxy.login(this.properties);
  		// 定时登录
      this.executor.scheduleWithFixedDelay(() -> securityProxy.login(properties), 0,
              this.securityInfoRefreshIntervalMills, TimeUnit.MILLISECONDS);
      startInternal();
}
```

**`startInternal`**方法比较有意思

1. **调度任务**：

   - `executor.schedule(...)`：使用 `ScheduledExecutorService` 的 `schedule` 方法安排一个任务。这个任务将在立即执行（延迟为 0 毫秒）。

2. **循环监听**：

   - `while (!executor.isShutdown() && !executor.isTerminated())`：这个循环会持续运行，直到 `executor` 被关闭或终止。它确保在调度任务未被停止的情况下持续进行配置监听。

3. **阻塞等待**：

   - `listenExecutebell`是一个容易为 1 的有界阻塞队列

   - `listenExecutebell.poll(5L, TimeUnit.SECONDS);`：从 `listenExecutebell` 队列中尝试获取一个元素。如果队列在 5 秒内没有可用的元素，`poll` 方法将返回 `null`。这提供了一个阻塞的等待机制，允许该线程在没有新事件的情况下休眠一段时间。

4. **条件检查**：

   - `if (executor.isShutdown() || executor.isTerminated()) { continue; }`：如果在 `poll` 后发现 `executor` 已经被关闭或终止，继续下一个循环。这样可以确保在处理配置监听逻辑之前，检查是否还需要继续运行。

5. **执行监听逻辑**：

   - `executeConfigListen();`：如果 `executor` 仍在运行且队列有元素可用，调用 `executeConfigListen()` 方法来处理配置监听的逻辑。

![image-20241115105321347](https://qny.bbbwdc.com/blog/image-20241115105321347.png)



#### 2.3 动态监听和更新配置

`executeConfigListen()` 方法通过管理缓存数据的监听和更新，确保应用能够及时响应配置的变化。它实现了配置的动态监听，通过网络请求与配置中心进行交互，确保本地配置的有效性和一致性。

> 方法比较复杂，看一遍有个印象就行，下面会对重点进行分析

```java
// 本地缓存
private final AtomicReference<Map<String, CacheData>> cacheMap = new AtomicReference<>(new HashMap<>());
```

```java
public void executeConfigListen() {

    Map<String, List<CacheData>> listenCachesMap = new HashMap<>(16);
    Map<String, List<CacheData>> removeListenCachesMap = new HashMap<>(16);
    long now = System.currentTimeMillis();
    // 5 分钟全量同步一次
    boolean needAllSync = now - lastAllSyncTime >= ALL_SYNC_INTERNAL;
    for (CacheData cache : cacheMap.get().values()) {

        synchronized (cache) {

            // 检查本地缓存的 MD5和监听器的 Md5 一致性
            if (cache.isSyncWithServer()) {
                cache.checkListenerMd5();
                if (!needAllSync) {
                    continue;
                }
            }
			// 如果缓存没被丢弃（当缓存不被任何监听器监听时，就会丢弃）
            if (!cache.isDiscard()) {
                // 如果缓存未丢弃且未使用本地配置信息，则将其添加到需要监听的缓存列表中
                if (!cache.isUseLocalConfigInfo()) {
                    List<CacheData> cacheDatas = listenCachesMap.get(String.valueOf(cache.getTaskId()));
                    if (cacheDatas == null) {
                        cacheDatas = new LinkedList<>();
                        listenCachesMap.put(String.valueOf(cache.getTaskId()), cacheDatas);
                    }
                    cacheDatas.add(cache);

                }
            } else if (cache.isDiscard()) {
     			// 如果缓存被丢弃且未使用本地配置信息，则将其添加到需要移除监听的缓存列表中
                if (!cache.isUseLocalConfigInfo()) {
                    List<CacheData> cacheDatas = removeListenCachesMap.get(String.valueOf(cache.getTaskId()));
                    if (cacheDatas == null) {
                        cacheDatas = new LinkedList<>();
                        removeListenCachesMap.put(String.valueOf(cache.getTaskId()), cacheDatas);
                    }
                    cacheDatas.add(cache);

                }
            }
        }

    }

    boolean hasChangedKeys = false;
		
    if (!listenCachesMap.isEmpty()) {
        for (Map.Entry<String, List<CacheData>> entry : listenCachesMap.entrySet()) {
            String taskId = entry.getKey();
            Map<String, Long> timestampMap = new HashMap<>(listenCachesMap.size() * 2);

            List<CacheData> listenCaches = entry.getValue();
            for (CacheData cacheData : listenCaches) {
                timestampMap.put(GroupKey.getKeyTenant(cacheData.dataId, cacheData.group, cacheData.tenant),
                        cacheData.getLastModifiedTs().longValue());
            }
			// 构建请求，获取更新了的配置信息
            ConfigBatchListenRequest configChangeListenRequest = buildConfigRequest(listenCaches);
            configChangeListenRequest.setListen(true);
            try {
                RpcClient rpcClient = ensureRpcClient(taskId);
                ConfigChangeBatchListenResponse configChangeBatchListenResponse = (ConfigChangeBatchListenResponse) requestProxy(
                        rpcClient, configChangeListenRequest);
                if (configChangeBatchListenResponse.isSuccess()) {

                    Set<String> changeKeys = new HashSet<>();
                    //handle changed keys,notify listener
                    if (!CollectionUtils.isEmpty(configChangeBatchListenResponse.getChangedConfigs())) {
                        hasChangedKeys = true;
                        for (ConfigChangeBatchListenResponse.ConfigContext changeConfig : configChangeBatchListenResponse
                                .getChangedConfigs()) {
                            String changeKey = GroupKey
                                    .getKeyTenant(changeConfig.getDataId(), changeConfig.getGroup(),
                                            changeConfig.getTenant());
                            changeKeys.add(changeKey);
                            // 更新本地缓存状态
                            refreshContentAndCheck(changeKey);
                        }

                    }

                    //handler content configs
                    for (CacheData cacheData : listenCaches) {
                        String groupKey = GroupKey
                                .getKeyTenant(cacheData.dataId, cacheData.group, cacheData.getTenant());
                        if (!changeKeys.contains(groupKey)) {
                            //sync:cache data md5 = server md5 && cache data md5 = all listeners md5.
                            synchronized (cacheData) {
                                if (!cacheData.getListeners().isEmpty()) {

                                    Long previousTimesStamp = timestampMap.get(groupKey);
                                    if (previousTimesStamp != null && !cacheData.getLastModifiedTs()
                                            .compareAndSet(previousTimesStamp, System.currentTimeMillis())) {
                                        continue;
                                    }
                                    cacheData.setSyncWithServer(true);
                                }
                            }
                        }

                        cacheData.setInitializing(false);
                    }

                }
            } catch (Exception e) {

                LOGGER.error("Async listen config change error ", e);
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException interruptedException) {
                    //ignore
                }
            }
        }
    }
		// 对于需要移除监听的缓存，构建请求并发送，成功后从缓存中移除
    if (!removeListenCachesMap.isEmpty()) {
        for (Map.Entry<String, List<CacheData>> entry : removeListenCachesMap.entrySet()) {
            String taskId = entry.getKey();
            List<CacheData> removeListenCaches = entry.getValue();
            ConfigBatchListenRequest configChangeListenRequest = buildConfigRequest(removeListenCaches);
            configChangeListenRequest.setListen(false);
            try {
                RpcClient rpcClient = ensureRpcClient(taskId);
                boolean removeSuccess = unListenConfigChange(rpcClient, configChangeListenRequest);
                if (removeSuccess) {
                    for (CacheData cacheData : removeListenCaches) {
                        synchronized (cacheData) {
                            if (cacheData.isDiscard()) {
                                ClientWorker.this
                                        .removeCache(cacheData.dataId, cacheData.group, cacheData.tenant);
                            }
                        }
                    }
                }

            } catch (Exception e) {
                LOGGER.error("async remove listen config change error ", e);
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException interruptedException) {
                //ignore
            }
        }
    }

    if (needAllSync) {
        lastAllSyncTime = now;
    }
    //如果有变更的键，调用 notifyListenConfig() 方法通知相关监听器。
    if (hasChangedKeys) {
        notifyListenConfig();
    }
}
```



##### 2.3.1 检查本地缓存和监听者装饰器的一致性

`checkListenerMd5`方法遍历当前 cache 的监听者装饰器，检查他们的 MD5 是否一致，如果不一样，通知监听器

![image-20241115172131360](https://qny.bbbwdc.com/blog/image-20241115172131360.png)

你可以尝试在 nacos 配置中心修改一个配置，进入`safeNotifyListener`方法

![image-20241115172936989](https://qny.bbbwdc.com/blog/image-20241115172936989.png)

可以看到当前 cache 和监听者装饰器的 md5 值已经不一样了。

然后创建了一个可执行任务，丢给监听器自己的执行器或者`CacheData`类的执行器

![image-20241115173808078](https://qny.bbbwdc.com/blog/image-20241115173808078.png)

##### 2.3.2 **通知任务**(配置更新入口)

> 配置更新的关键入口就是这里啦，后面讲配置动态更新就从这里入手

下面是 job 的关键代码：

- 创建`ConfigResponse`对象，并设置数据ID、组、内容和加密数据键
- 使用过滤器链过滤，将过滤后的内容通过`listener.receiveConfigInfo`方法通知给监听器
- 如果监听器是`AbstractConfigChangeListener`的实例，那么解析配置变更数据，并通知监听器配置变更事件
- 更新监听器装饰器的最后调用 md5 值

![image-20241116184134619](https://qny.bbbwdc.com/blog/image-20241116184134619.png)



##### 2.3.3 请求 Nacos 服务器做 MD5 对比

继续看`executeConfigListen`方法，下面这段代码的目的就是发送一个超时时间为 **30s** 的请求询问配置中心，哪些配置改了

![image-20241117184559908](https://qny.bbbwdc.com/blog/image-20241117184559908.png)

将被监听的缓存配置信息批量提交给 nacos 服务端做 MD5 对比, 首先构建请求对象

![image-20241115142824287](https://qny.bbbwdc.com/blog/image-20241115142824287.png)

将配置的dataId, group, md5, tennat 封装到`configListenContext`，为什么用 MD5，而不是整个配置文件呢？因为传输整个文件会给服务器网络带来巨大的压力，而先对比 MD5 值，如果不一致再拉取配置信息的方式，明显更加节省资源

![image-20241115143813186](https://qny.bbbwdc.com/blog/image-20241115143813186.png)

看下图，请求成功以后，如果有配置发生变更，则返回了配置的`group, dataId, tenant`, 将它们组成一个`changeKey`, 调用`refreshContentAndCheck`方法

![image-20241116192924807](https://qny.bbbwdc.com/blog/image-20241116192924807.png)

`refreshContentAndCheck`方法做了两件事：

- 拉取 nacos 服务端的配置文件信息，请求超时时间为 30S，更新到本地缓存`cacheData`
- 调用`checkListenerMd5`方法检查本地缓存和监听者装饰器 `md5` 值是否一致，如果不一致，通知监听者

![image-20241116193458433](https://qny.bbbwdc.com/blog/image-20241116193458433.png)



### 3. 加载服务端配置信息：NacosPropertySourceLocator

`NacosPropertySourceLocator` 是用于从 Nacos 配置中心加载配置的核心类,通过实现 `PropertySourceLocator` 接口并使用 `@Order(0)` 注解设置优先级，使其在 Spring 应用启动时能够优先加载配置

![image-20241114160229534](https://qny.bbbwdc.com/blog/image-20241114160229534.png)

它的构造函数通过接收 `NacosConfigManager` 实例来初始化类，并获取 Nacos 配置属性

![image-20241116195707257](https://qny.bbbwdc.com/blog/image-20241116195707257.png)

`NacosPropertySourceLocator`实现了 `PropertySourceLocator` 接口的`locate`方法加载配置信息：

- loadSharedConfiguration：加载共享配置。多个服务共享的配置，比如数据源
- loadExtConfiguration：加载扩展配置
- loadApplicationConfiguration：加载应用程序特定的配置

![image-20241116204212161](https://qny.bbbwdc.com/blog/image-20241116204212161.png)

上面三个方法会根据你在本地`spring.cloud.nacos.config`的配置加载不同类型的配置 。

![image-20241118111615456](https://qny.bbbwdc.com/blog/image-20241118111615456.png)

最终都是调用**`com.alibaba.cloud.nacos.client.NacosPropertySourceBuilder#loadNacosData`**方法，使用 `NacosConfigService`实例请求 Nacos 服务端拉取配置信息。

> 上面`2.1`小节讲过`NacosConfigService`提供了多种方法来获取、发布和管理配置数据的功能。

![image-20241118110850881](https://qny.bbbwdc.com/blog/image-20241118110850881.png)

加载完成的多属性源示例如下：

![image-20241118110032241](https://qny.bbbwdc.com/blog/image-20241118110032241.png)

**总结：**

​	本文的第一个问题（客户端启动时是如何从 nacos 服务端拉取并加载配置？）到此就有答案了。在 Springboot 启动时优先加载 Bean `NacosPropertySourceLocator`, 根据本地`spring.cloud.nacos.config`的配置依次加载共享配置，扩展配置，应用配置，底层是使用`NacosConfigService`与 Nacos 服务端交互拉取配置信息。



## 配置动态更新基石：ConfigurationPropertiesRebinder

**`ConfigurationPropertiesRebinder`** 是 Spring Cloud context的一个类，通常与动态配置更新相关。它的主要作用是支持在运行时重新绑定配置属性，以实现配置的动态更新。

主要方法：`rebind()`, 用于触发重新绑定。当配置源中的配置发生变化时，相关的监听器会调用 `rebind` 方法，从而使得 Spring 容器中的 Bean 能够获得最新的配置值

![image-20241116211218979](https://qny.bbbwdc.com/blog/image-20241116211218979.png)

`ConfigurationPropertiesRebinder` 实现`ApplicationListener`接口，监听了`EnvironmentChangeEvent`，这个事件在 Spring 的环境（`Environment`）发生变化时发布。

![image-20241117170445658](https://qny.bbbwdc.com/blog/image-20241117170445658.png)

接着看核心方法：rebind(), 我在 nacos console 修改配置触发重新绑定。



在下main断点处可以看到，我使用了注解` @ConfigurationProperties`的类`MyAppProperties`需要重新绑定。

![image-20241116213944841](https://qny.bbbwdc.com/blog/image-20241116213944841.png)

进入`rebind(String name, ApplicationContext appContext)`方法，进行 bean 的销毁和初始化

代码执行到137行，销毁当前的 bean，此时配置中的 'name'还是张三

![image-20241116214148371](https://qny.bbbwdc.com/blog/image-20241116214148371.png)

代码执行到139行时，bean 已经重新初始化完成，变成了 nacos 中最新修改的值

![image-20241116214342287](https://qny.bbbwdc.com/blog/image-20241116214342287.png)

这样，配置动态更新就完成了，销毁和重新初始化 bean 不是本文的重点，感兴趣可自行研究。



## 刷新 Nacos 配置：NacosContextRefresher

在上面`2.3.2`小节提到过，当客户端检测到服务端配置发生改变，会通知监听者，那么谁是监听者，又是如何注册的呢？这就涉及到一个类：**`NacosContextRefresher`**。

![image-20241118140322678](https://qny.bbbwdc.com/blog/image-20241118140322678.png)

![image-20241118140450609](https://qny.bbbwdc.com/blog/image-20241118140450609.png)

作者在类注释写道：在应用程序启动时，NacosContextRefresher 向所有应用程序级别的 dataId 添加 nacos 监听器，当数据发生变化时，监听器将刷新配置。

### 1. 在应用程序启动时添加监听器

首先，在应用程序启动时就添加监听器依赖于它实现了`ApplicationListener`接口，并且监听了`ApplicationReadyEvent`事件。Spring 应用程序的上下文完全启动后触发`ApplicationReadyEvent`事件，进入 `NacosContextRefresher.onApplicationEvent()`。

![image-20241118141410638](https://qny.bbbwdc.com/blog/image-20241118141410638.png)

### 2. 注册 Nacos 监听器

接着为所有应用程序级别的 dataId 添加 nacos 监听器

![image-20241118141741320](https://qny.bbbwdc.com/blog/image-20241118141741320.png)

![image-20241118142756028](https://qny.bbbwdc.com/blog/image-20241118142756028.png)

- 以 group 和 dataId 为 key，添加一个`NacosContextRefresher`监听者到`listenerMap`中。
- `NacosContextRefresher`实现了 `innerReveive`方法，接收更新后的配置信息，更新刷新次数，添加新配置信息到刷新历史记录链表头部，**发布`RefreshEvent`事件**。
- 添加监听者到`NacosConfigService`实例

### 3. 添加监听器到 CacheData

在`2.2`提到过，`ClientWorker` 主要用于封装与 Nacos 配置服务的交互逻辑，提供配置的获取、监听和更新等功能，这里将监听者注册到 ClientWorker，配置发生变更时通知监听者。

![image-20241118143646906](https://qny.bbbwdc.com/blog/image-20241118143646906.png)

监听器最终是添加到`CacheData`实例的，在绑定之前，先确保不同租户和 group 下的 dataId 使用唯一的缓存数据。调用`addCacheDataIfAbsent`，如果缓存不存则创建。

![image-20241118144812449](https://qny.bbbwdc.com/blog/image-20241118144812449.png)

`cache`有个属性：`taskId`，它的值是 `cacheMap` 的数量除以 `perTaskConfigSize`（3000）的商。通过 RPC 长链接获取服务端配置数据时，`taskId`将决定使用哪一个`rpcClient`。简单来说就是 3000 个 cache 使用同一个长链接，这样做是为了控制报文大小，提高性能

![image-20241118151548148](https://qny.bbbwdc.com/blog/image-20241118151548148.png)

`cache` 创建完成，然后调用`addListener`添加监听器。这里使用了装饰模式为`listener`添加了额外属性：md5 串，配置信息。然后将装饰对象添加到`listeners`中。

![image-20241118160159666](https://qny.bbbwdc.com/blog/image-20241118160159666.png)

## 事件驱动配置动态更新

上面**`2.3.2 `通知任务(配置更新入口)** 提到过，当配置发生变更，将调用监听者的`receiveConfigInfo`方法推送配置给监听者。我们注册到 cache 的监听者是`NacosContextRefresher`, `NacosContextRefresher`中的`receiveConfigInfo`方法会发布一个`RefreshEvent`事件。

`RefreshEventListener`监听了`RefreshEvent`事件，调用 `ContextRefresher#refresh`方法处理事件。

![image-20241118164133098](https://qny.bbbwdc.com/blog/image-20241118164133098.png)

 `refresh`方法调用`refreshEnvironment()`发布了一个环境改变事件**`EnvironmentChangeEvent`**。

![image-20241118164429414](https://qny.bbbwdc.com/blog/image-20241118164429414.png)

配置动态更新的基石：ConfigurationPropertiesRebinder 监听了`EnvironmentChangeEvent`，于是触发了 bean 重新绑定，这样在 nacos console 修改的配置信息就更新到 spring 的运行环境中了。

到此本文的第二个问题（配置如何动态更新？）就解决了。

![image-20241118164844528](https://qny.bbbwdc.com/blog/image-20241118164844528.png)



## 总结

客户端启动与配置动态更新的实现细节可以简单总结为以下几点：

- 启动加载`spring.factories`中的 `NacosConfigBootstrapConfiguration`，实例化`NacosConfigProperties`，`NacosConfigManager`, `NacosPropertySourceLocator`
- `NacosConfigManager`创建`NacosConfigService`与配置中心交互，提供获取配置，监听配置等的重要功能
- 使用长链接定时与服务端做配置数据MD5对比，监听服务端配置信息变更，发布事件通知监听者 `NacosContextRefresher`
- 监听者`NacosContextRefresher`接收配置信息，发布环境变更事件
- 触发`ConfigurationPropertiesRebinder`，销毁并重新初始化配置信息 Bean