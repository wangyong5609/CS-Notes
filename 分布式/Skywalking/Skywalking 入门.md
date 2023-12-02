# Skywalking 入门

## 微服务系统监控三要素

![image-20231202135750748](./Skywalking%20%E5%85%A5%E9%97%A8.assets/image-20231202135750748.png)

**Logging** 就是**记录系统行为的离散事件**，例如，服务在处理某个请求时打印的错误日志，我们可以将这些日志信息记录到 ElasticSearch 或是其他存储中，然后通过 Kibana 或是其他工具来分析这些日志了解服务的行为和状态。大多数情况下，日志记录的数据很分散，并且相互独立，比如错误日志、请求处理过程中关键步骤的日志等等。

**Metrics** 是**系统在一段时间内某一方面的某个度量，可聚合的数据，且通常是固定类型的时序数据**，例如，电商系统在一分钟内的请求次数。我们常见的监控系统中记录的数据都属于这个范畴，例如 Promethus、Open-Falcon 等，这些监控系统最终给运维人员展示的是一张张二维的折线图。

Metrics 是可以聚合的，例如，为电商系统中每个 HTTP 接口添加一个计数器，计算每个接口的 QPS，之后我们就可以通过简单的加和计算得到系统的总负载情况。

**Tracing** 即我们常说的分布式链路追踪，**记录单个请求的处理流程，其中包括服务调用和处理时长等信息**。在微服务架构系统中一个请求会经过很多服务处理，调用链路会非常长，要确定中间哪个服务出现异常是非常麻烦的一件事。通过分布式链路追踪，运维人员就可以构建一个请求的视图，这个视图上展示了一个请求从进入系统开始到返回响应的整个流程。这样，就可以从中了解到所有服务的异常情况、网络调用，以及系统的性能瓶颈等。

## 什么是链路追踪

谷歌在 2010 年 4 月发表了一篇论文《Dapper, a Large-Scale Distributed Systems Tracing Infrastructure》介绍了分布式追踪的概念，APM 系统的核心技术就是分布式链路追踪。

> 论文在线地址：https://storage.googleapis.com/pub-tools-public-publicatio
> n-data/pdf/36356.pdf
> 国内的翻译版：https://bigbully.github.io/Dapper-translation/

国内各大厂商初期自研的分布式追踪方案互不兼容，于是诞生了 **OpenTracing**

OpenTracing 是一个 Library，定义了一套通用的数据上报接口，要求各个分布式追踪系统都来实现这套接口。这样一来，应用程序只需要对接OpenTracing，而无需关心后端采用的到底什么分布式追踪系统，因此开发者可以无缝切换分布式追踪系统，也使得在通用代码库增加对分布式追踪的支持成为可能。

目前，主流的分布式追踪实现基本都已经支持 OpenTracing。

### 链路追踪

追踪一个事务或者调用流程

![image-20231202143141218](./Skywalking%20%E5%85%A5%E9%97%A8.assets/image-20231202143141218.png)

上面的图也有缺点，它不能看出一次调用的开始与结束，耗费时间，串行调用或者并行调用，所以有了下面这种典型的trace视图

![image-20231202143418652](./Skywalking%20%E5%85%A5%E9%97%A8.assets/image-20231202143418652.png)

纵轴为调用顺序，横轴为时间轴

**分布式追踪系统的原理：**

分布式追踪系统大体分为三个部分，**数据采集、数据持久化、数据展示**。数据采集是指在代码中埋点，设置请求中要上报的阶段，以及设置当前记录的阶段隶属于哪个上级阶段。数据持久化则是指将上报的数据落盘存储，数据展示则是前端查询与之关联的请求阶段，并在界面上呈现。

上图是一个请求的流程例子，请求从客户端发出，到达负载均衡，再依次进行认证、计费，最后取到目标资源。请求过程被采集之后，会以上图的形式呈现，横坐标是时间，圆角矩形是请求的执行的各个阶段

### OpenTracing

学好OpenTracing，更有助于我们运用Skywalking 

#### 数据模型

这部分在 OpenTracing 的规范中写的非常清楚，下面只大概翻译一下其中的关键部分，细节可参考原始文档 《The OpenTracing Semantic Specification》。

```text
Causal relationships between Spans in a single Trace

        [Span A]  ←←←(the root span)
            |
     +------+------+
     |             |
 [Span B]      [Span C] ←←←(Span C is a `ChildOf` Span A)
     |             |
 [Span D]      +---+-------+
               |           |
           [Span E]    [Span F] >>> [Span G] >>> [Span H]
                                       ↑
                                       ↑
                                       ↑
                         (Span G `FollowsFrom` Span F)
```

##### Trace
一个 Trace 代表一个事务、请求或是流程在分布式系统中的执行过程。
OpenTracing 中的一条 Trace调用链，由多个 Span 组成，一个 Span 代表系统中具有开始时间和执行时长的逻辑单元，Span 一般会有一个名称，一条 Trace中 Span 是首尾连接的。

##### Span

Span 的单词含义是范围，代表系统中具有开始时间和执行时长的逻辑单元，可以理解为某个处理阶段。Span 之间通过嵌套或者顺序排列建立逻辑因果关系。Span 和 Span 的关系称为 Reference。

基于时间轴的时序图可以更好的展现**Trace**（调用链）：

```text
Temporal relationships between Spans in a single Trace

––|–––––––|–––––––|–––––––|–––––––|–––––––|–––––––|–––––––|–> time

 [Span A···················································]
   [Span B··············································]
      [Span D··········································]
    [Span C········································]
         [Span E·······]        [Span F··] [Span G··] [Span H··]
```

每个 Span 中可以包含以下的信息：

- **操作名称**：例如访问的具体 RPC 服务，访问的 URL 地址等
- **起始时间**：2021-1-25 22:00:00
- **结束时间**：2021-1-30 22:00:00
- **Span Tag**：一组键值对（k-v）构成的Span标签集合，其中键必须为字符串类型，值可以是字符串、bool 值或者数字
- **Span Log**：一组 Span 的日志集合
- **SpanContext**：Trace 的全局上下文信息
- **References**：Span 之间的引用关系，下面详细说明 Span 之间的引用关系

在一个 Trace 中，一个 Span 可以和一个或者多个 Span 间存在因果关系。目前，OpenTracing 定义了 ChildOf 和 FollowsFrom 两种 Span 之间的引用关系。这两种引用类型代表了子节点和父节点间的直接因果关系。

- **ChildOf 关系**：一个 Span 可能是一个父级 Span 的孩子，即为 ChildOf 关系。下面这些情况会构成 ChildOf 关系
  - 一个 HTTP 请求之中，被调用的服务端产生的 Span，与发起调用的客户端产生的 Span，就构成了 ChildOf 关系
  - 一个 SQL Insert 操作的 Span，和 ORM 的 save 方法的 Span 构成ChildOf 关系

很明显，上述 ChildOf 关系中的父级 Span 都要等待子 Span 的返回，子 Span的执行时间影响了其所在父级 Span 的执行时间，父级 Span 依赖子 Span 的执行结果。除了串行的任务之外，我们的逻辑中还有很多并行的任务，它们对应的 Span 也是并行的，这种情况下一个父级 Span 可以合并所有子 Span 的执行结果并等待所有并行子 Span 结束。 

- **FollowsFrom 关系**：表示跟随关系，意为在某个阶段之后发生了另一个阶段，用来描述顺序执行关系

##### Logs

每个 Span 可以进行多次 Logs 操作，每一次 Logs 操作，都需要带一个时间戳，以及一个可选的附加信息。

##### Tags

每个 Span 可以有多个键值对形式的 Tags，Tags 是没有时间戳的，只是为Span 添加一些简单解释和补充信息。

##### SpanContext 和 Baggage
SpanContext 表示进程边界，在跨进调用时需要将一些全局信息，例如：TraceId、当前 SpanId 等信息封装到 Baggage 中传递到另一个进程（下游系统）中。

Baggage 是存储在 SpanContext 中的一个键值对集合。它会在一条 Trace 中全局传输，该 Trace 中的所有 Span 都可以获取到其中的信息。

需要注意的是，由于 Baggage 需要跨进程全局传输，就会涉及相关数据的序列化和反序列化操作，如果在 Baggage 中存放过多的数据，就会导致序列化和反序列化操作耗时变长，使整个系统的 RPC 的延迟增加、吞吐量下降。

虽然 Baggage 与 Span Tags 一样，都是键值对集合，但两者最大区别在于Span Tags 中的信息不会跨进程传输，而 Baggage 需要全局传输。因此，OpenTracing 要求实现提供 Inject 和 Extract 两种操作，SpanContext 可以通过 Inject 操作向 Baggage 中添加键值对数据，通过 Extract 从 Baggage 中获取键值对数据

## Skywalking介绍

**SkyWalking** 是一个开源可观测平台，用于收集、分析、聚合和可视化来自服务和云原生基础设施的数据。SkyWalking 提供了一种简单的方法来保持分布式系统的清晰视图，甚至跨云。它是一种现代 APM，专为云原生、基于容器的分布式系统而设计。

### 核心功能

**指标分析**：服务，实例，端点指标分析

**问题分析**：在运行时分析代码，找到问题的根本原因

**服务拓扑**：提供服务的拓扑图分析

**依赖分析**：服务实例和端点依赖性分析

**服务检测**：检测慢速的服务和端点

**性能优化**：根据服务监控的结果提供性能优化的思路

**链路追踪**：分布式跟踪和上下文传播

**数据库监控**：数据库访问指标监控统计，检测慢速数据库访问语句（包括SQL语句）

**服务告警**：服务告警功能

> 名词解释
>
> - **服务**。表示一组/组工作负载，它们为传入请求提供相同的行为。您可以在使用仪器代理或 SDK 时定义服务名称。比如用户服务，订单服务
> - **服务实例**。服务组中的每个单独的工作负载称为一个实例。就像`pods`在 Kubernetes 中一样，它不需要是单个操作系统进程，但是，如果您使用仪器代理，则实例实际上是一个真正的操作系统进程。可以理解为一台服务器就是一个实例
> - **端点**。服务中用于传入请求的路径，例如 HTTP URI 路径或 gRPC 服务类 + 方法签名。可以理解为接口URL
> - **过程**。一个操作系统进程。在某些场景下，一个Service Instance并不是一个进程，比如Kubernetes的一个pod可以包含多个进程

### 特点

- 多语言自动探针，支持 Java、.NET Code 等多种语言
- 为多种开源项目提供了插件，为 Tomcat、 HttpClient、Spring
- RabbitMQ、MySQL 等常见基础设施和组件提供了自动探针
- 微内核 + 插件的架构，存储、集群管理、使用插件集合都可以进行自由选择
- 支持告警
- 优秀的可视化效果

### **Skywalking**架构图

![image-20231202153451510](./Skywalking%20%E5%85%A5%E9%97%A8.assets/image-20231202153451510.png)

**客户端：agent组件**

基于探针技术采集服务相关信息（包括跟踪数据和统计数据），然后将采集到的数据上报给 skywalking 的数据收集器

**服务端：**

**OAP**：observability analysis platform可观测性分析平台，负责接收客户端上报的数据，对数据进行分析，聚合，计算后将数据进行存储，并且还会提供一些查询API进行数据的查询，这个模块其实就是我们所说的链路追踪系统的 Collector 收集器

**Storage**：skyWalking 的存储介质，默认是采用H2，同时支持许多其他的存储介质，比如：ElastaticSearch，mysql等

**WebUI**：提供一些图形化界面展示对应的跟踪数据，指标数据等等

## Skywalking安装

### docker-compose部署

docker-compose.yml

~~~yaml
version: '3.3'
networks:
  skywalking:
    driver: bridge
services:
  elasticsearch:
    image: elasticsearch:7.6.2
    container_name: elasticsearch
    restart: always
    privileged: true
    hostname: elasticsearch
    ports:
      - 9200:9200
      - 9300:9300
    environment:
      - discovery.type=single-node
      - bootstrap.memory_lock=true
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
      - TZ=Asia/Shanghai
    networks:
      - skywalking
    ulimits:
      memlock:
        soft: -1
        hard: -1
  elasticsearch-hq:
    image: elastichq/elasticsearch-hq
    container_name: elasticsearch-hq
    restart: always
    privileged: true
    hostname: elasticsearch-hq
    ports:
      - 5000:5000
    environment:
      - TZ=Asia/Shanghai
    networks:
      - skywalking
  oap:
    image: apache/skywalking-oap-server:8.7.0-es7
    container_name: oap
    hostname: oap
    privileged: true
    depends_on:
      - elasticsearch
    links:
      - elasticsearch
    restart: always
    ports:
      - 11800:11800
      - 12800:12800
    environment:
      SW_STORAGE: elasticsearch7
      SW_STORAGE_ES_CLUSTER_NODES: elasticsearch:9200
      TZ: Asia/Shanghai
    volumes:
      - ./config/alarm-settings.yml:/skywalking/config/alarm-settings.yml
    networks:
      - skywalking
  ui:
    image: apache/skywalking-ui:8.7.0
    container_name: ui
    privileged: true
    depends_on:
      - oap
    links:
      - oap
    restart: always
    ports:
      - 8080:8080
    environment:
      SW_OAP_ADDRESS: http://oap:12800
      TZ: Asia/Shanghai
    networks:
      - skywalking
      
~~~

在docker-compose.yml目录执行`docker-compose up -d`启动

服务全部启动完成后访问`8080`端口

![image-20231202172742533](./Skywalking%20%E5%85%A5%E9%97%A8.assets/image-20231202172742533.png)

## Skywalking应用

相关术语：

> **skywalking-collector**: 链路数据归集器，数据可以落地 ElasticSearch/H2
>
> **skywalking-ui**: web可视化平台，用来展示落地的数据
>
> **skywalking-agent**: 探针，用来收集和发送数据到归集器

### agent下载

Skywalking-agent，它简称探针，用来收集和发送数据到归集器，探针对应的jar包在Skywalking源码中。

Skywalking源码下载地址： https://archive.apache.org/dist/skywalking/

> 版本： 8.3.0

![image-20231202181749919](./Skywalking%20%E5%85%A5%E9%97%A8.assets/image-20231202181749919.png)

```
agent
├── activations
│ ├── apm-toolkit-kafka-activation-8.3.0.jar
│ ├── ...
│ └── apm-toolkit-trace-activation-8.3.0.jar
├── config # Agent 配置文件
│ └── agent.config
├── logs # 日志文件
├── optional-plugins # 可选插件
│ ├── apm-customize-enhance-plugin-8.3.0.jar
│ ├── apm-gson-2.x-plugin-8.3.0.jar
│ └── ... ...
├── bootstrap-plugins # jdk插件
│ ├── apm-jdk-http-plugin-8.3.0.jar
│ └── apm-jdk-threading-plugin-8.3.0.jar
├── plugins # 当前生效插件
│ ├── apm-activemq-5.x-plugin-8.3.0.jar
│ ├── apm-armeria-0.84.x-plugin-8.3.0.jar
│ ├── apm-armeria-0.85.x-plugin-8.3.0.jar
│ └── ... ...
├── optional-reporter-plugins
│ └── kafka-reporter-plugin-8.3.0.jar
└── skywalking-agent.jar【应用的jar包】
```

目录结构说明：

- **activations** 当前 skywalking 正在使用的功能组件。
- **agent.config** 文件是 SkyWalking Agent 的唯一配置文件。
- **plugins** 目录存储了当前 Agent 生效的插件。
- **optional-plugins** 目录存储了一些可选的插件（这些插件可能会影响整个系统的性能或是有版权问题），如果需要使用这些插件，需将相应 jar 包移动到plugins 目录下。
- **skywalking-agent.jar** 是 Agent 的核心 jar 包，由它负责读取 agent.config 配置文件，加载上述插件 jar 包，运行时收集到 的 Trace和 Metrics 数据也是由它发送到 OAP 集群的。

### agent应用

项目使用agent，如果是开发环境，可以使用IDEA集成，如果是生产环境，需要将项目打包上传到服务器。为了使用agent，我们同时需要将下载的 apache-skywalking-apm-bin 文件包上传到服务器上去。不过无论是开发环境还是生产环境使用agent，对项目都是无侵入式的

#### IDEA集成使用agent

1. 修改agent中数据收集服务的地址： agent/config/agent.confg

~~~properties
# 这里配置的上面 docker-compose.yml 中 oap 的地址
collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:192.168.200.129:11800}
~~~

2. 使用探针配置为项目分别配置agent

```java
-javaagent:D:\softRepo\sky-walking-agent\skywalking-agent.jar
-Dskywalking.agent.service_name=leadnews-user
```

IDEA添加VM参数

> 把你想追踪的服务都加上这个VM参数

![image-20231202215800452](./Skywalking%20%E5%85%A5%E9%97%A8.assets/image-20231202215800452.png)



**如果你要追踪Gateway的话，你会发现：无法通过gateway发现路由的服务链路？**

原因： Spring Cloud Gateway 是基于 WebFlux 实现，必须搭配上apm-spring-cloud-gateway-2.1.x-plugin 和 apm-spring-webflux-x.x-plugin 两个插件

方案：将agent/optional-plugins下的两个插件 复制到 agent/plugins目录下

#### 生产环境使用agent

生产环境将 agent 和 jar 包上传到服务器，启动 jar 时添加 VM 参数，例如：

```bash
java -javaagent:/usr/local/server/skywalking/apache-skywalking-apm-bin/agent/skywalking-agent.jar -Dskywalking.agent.service_name=user-service -jar
user-service-1.0-SNAPSHOT.jar &
```



