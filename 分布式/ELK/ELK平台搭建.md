# ELK平台搭建

## 概述

### 什么是ELK

> ELK是三个开源软件的缩写，分别表示：Elasticsearch , Logstash, Kibana , 它们都是开源软件

- Elasticsearch是强大的数据搜索引擎，并且是分布式、能够通过restful方式进行交互的近实时搜索平台框架
- Logstash是免费且开源的服务器端数据处理通道，能够从多个来源收集数据，能够对数据进行转换和清洗，将转换数据后将数据发送到数据存储库中，并且不受格式和复杂度的影响。
- Kibana是针对Elasticsearch的开源分析及可视化平台，用于搜索、查看交互存储在Elasticsearch索引中的数据。

### ELK能做什么

#### 日志收集

![image-20231207212518657](./ELK%E5%B9%B3%E5%8F%B0%E6%90%AD%E5%BB%BA.assets/image-20231207212518657.png)

#### 异构数据同步

借助`ELK+Canal`将`MySQL`数据同步到`ES`

![image-20231207212659515](./ELK%E5%B9%B3%E5%8F%B0%E6%90%AD%E5%BB%BA.assets/image-20231207212659515.png)

组件介绍

##### MySQL

MySQL作为主数据库，所有操作都会写入MySQL。

##### **Canal**

> canal是用java开发的基于数据库增量日志解析，提供增量数据订阅&消费的中间件

canal的工作原理就是把自己伪装成MySQL slave，模拟MySQL slave的交互协议向MySQL Mater发送 dump协议，MySQL mater收到canal发送过来的dump请求，开始推送binary log给canal，然后canal解析binary log，再发送到存储目的地，比如MySQL，Kafka，Elastic Search等等

![image-20231207213532893](./ELK%E5%B9%B3%E5%8F%B0%E6%90%AD%E5%BB%BA.assets/image-20231207213532893.png)

##### RabbitMQ

RabbitMQ 主要用来做消息消费缓冲，Logstash 消费速度跟不上Canal同步数据的速度

##### Logstash

> Logstash是具有实时流水线能力的开源的数据收集引擎

Logstash可以动态统一不同来源的数据，并将数据标准化到您选择的目标输出，它提供了大量插件，可帮助我们解析，丰富，转换和缓冲任何类型的数据。

我们需要使用logstash对RabbitMQ过来的数据进行解析以及清晰，并将清洗过的数据放进ES中

##### ElasticSearch

我们使用ElasticSearch存储logstash清洗完成的数据，通过ES可以对数据进行全文检索

##### Kibana

> 针对ES的开源分析可视化工具，与存储在ES的数据进行交互

Kibana是一个开源的分析与可视化平台，设计出来用于和Elasticsearch一起使用的，你可以用kibana搜索、查看存放在Elasticsearch中的数据，Kibana与Elasticsearch的交互方式是各种不同的图表、表格、地图等，直观的展示数据，从而达到高级的数据分析与可视化的目的。

### ES物理部署

#### ES单机部署