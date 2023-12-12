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

## ES物理部署

### ES单机部署

#### 下载Elasticsearch

> 我们下载的Elasticsearch 版本是 7.17.5，安装目录：/opt，下载地址
>
> https://www.elastic.co/cn/downloads/past-releases/elasticsearch-7-17-5

```bash
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-7.17.5-linux-x86_64.tar.gz
tar -zvxf elasticsearch-7.17.5-linux-x86_64.tar.gz
```

#### 配置Elasticsearch

##### 关闭防火墙

```bash
systemctl status firewalld.service
systemctl stop firewalld.service
systemctl disable firewalld.service
```

##### 配置elasticsearch.yml

> 该配置文件是ES的主配置文件
>

```bash
cd /opt/elasticsearch-7.17.5 && vi config/elasticsearch.yml
```

```yml
#设置允许访问地址，配置位0.0.0.0允许任意主机访问
- #network.host: 192.168.0.1
+ network.host: 0.0.0.0

# 配置集群, 这里只配置node-1一台
# node.name: node-1
+ node.name: node-1
- #discovery.seed_hosts: ["host1", "host2"]
+ discovery.seed_hosts: ["node-1"]
- #cluster.initial_master_nodes: ["node-1", "node-2"]
+ cluster.initial_master_nodes: ["node-1"]
```

#### 修改Linux句柄数

> 为什么要改？因为ES需要创建大量的内存映射区域（mapped memory areas），vm.max_map_count用于限制一个进程可以拥有的VMA（虚拟内存映射区域）数量，如果vm.max_map_count过低，可能导致OOM，ES性能下降等问题

##### 查看当前最大句柄数

```bash
sysctl -a | grep vm.max_map_count
```

##### 修改句柄数

```bash
vi /etc/sysctl.conf
```

```
vm.max_map_count=262144
```

##### 生效配置

> 修改后需要重启才能生效，不想重启可以设置临时生效

```
sysctl -w vm.max_map_count=262144
```

#### 关闭swap

> 因为ES的数据大量都是常驻内存的，一旦使用了虚拟内存就会导致查询速度下降，一般需要关闭swap，但是要保证有足够的内存

##### 临时关闭

```
swapoff -a
```

##### 永久关闭

```
vi /etc/fstab
```

> 注释掉 swap 这一行的配置

![image-20231209210259282](./ELK%E5%B9%B3%E5%8F%B0%E6%90%AD%E5%BB%BA.assets/image-20231209210259282.png)

#### 修改最大线程数

> 因为ES运行期间可能创建大量线程，如果线程数支持较少可能报错

##### 配置修改

> 修改后需要重新登录生效

```
vi /etc/security/limits.conf
```

```
# 添加以下内容
* soft nofile 65536
* hard nofile 65536
* soft nproc 4096
* hard nproc 4096
```

##### 重启服务

```
reboot
```

#### 创建ES用户

> 注意ES不能以 root 用户启动，否则会报错

##### 添加用户

> 密码有强度验证，可以使用命令`openssl rand -base64 12`生成一个密码使用

```
useradd es
# 密码：yWntzbX2hpHrDkTp
passwd es
```

##### 增加管理员权限

使用`visudo`命令打开`/etc/sudoer`文件

```
visudo
```

```
+ es ALL=(ALL) ALL
```

##### 修改Elasticsearch权限

```
chown -R es:es elasticsearch-7.17.5
chmod -R 755 /opt/elasticsearch-7.17.5/config/elasticsearch.keystore
```

### JVM配置

> 根据自己的内存自行调整，内存不够则会启动失败

```
vi /opt/elasticsearch-7.17.5/config/jvm.options
```

```
- ##-Xms4g
- ##-Xmx4g
+ -Xms4g
+ -Xmx4g
```

### 添加IK分词器

> 在github中下载对应版本的分词器

```
https://github.com/medcl/elasticsearch-analysis-ik/releases
```

> 根据自己的ES版本选择相应版本的IK分词器，因为安装的ES是`7.17.5`，所以也下载相应的IK分词器，

![image-20231212210037000](./ELK%E5%B9%B3%E5%8F%B0%E6%90%AD%E5%BB%BA.assets/image-20231212210037000.png)

> 将下载的分词器复制到ES安装目录的`plugins`目录中并进行解压

```
mkdir ik && cd ik
unzip elasticsearch-analysis-ik-7.17.5.zip
```

### 启动ElasticSearch

#### 切换用户

> 切换到刚刚创建的`es`用户

```
su es
```

#### 启动命令

> 我们可以使用以下命令来进行使用

```
# 前台启动
sh bin/elasticsearch


# 后台启动
sh bin/elasticsearch -d
```

#### 访问测试

> 访问对应宿主机的`9200`端口

```
http://192.168.88.30:9200/
```

显示以下信息就启动成功了

![image-20231209214906899](./ELK%E5%B9%B3%E5%8F%B0%E6%90%AD%E5%BB%BA.assets/image-20231209214906899.png)

#### 重启ElasticSearch

##### 查找进程

> 先查找ElasticSearch的进程号

```
ps -ef | grep elastic
```

![image-20231212212029292](./ELK%E5%B9%B3%E5%8F%B0%E6%90%AD%E5%BB%BA.assets/image-20231212212029292.png)

#### 杀死进程

> 杀死对应的进程

```
kill -9 49736
```

##### 启动ElasticSearch

> 注意不要使用ROOT用户启动

```
sh bin/elasticsearch -d
```

### kibana安装

#### 下载安装 Kibana

> kibana 版本 7.17.5 下载地址：`https://www.elastic.co/cn/downloads/past-releases/kibana-7-17-5`

```
wget https://artifacts.elastic.co/downloads/kibana/kibana-7.17.5-linux-x86_64.tar.gz
tar -zvxf kibana-7.17.5-linux-x86_64.tar.gz
mv kibana-7.17.5-linux-x86_64 kibana-7.17.5
```

#### 配置 Kibana

```
vi config/kibana.yml
- #server.port: 5601
+ server.port: 5601


- #server.host: "localhost"
+ server.host: "0.0.0.0"


- #elasticsearch.hosts: ["http://localhost:9200"]
+ elasticsearch.hosts: ["http://localhost:9200"]
```

#### 启动 Kibana

##### 切换用户

> Kibana也不能以root用户运行，需要切换到`elasticsearch权限`

```
su elasticsearch
```

##### 启动kibaba

```
#前台运行
sh bin/kibana

#后台运行
nohup sh bin/kibana  >/dev/null 2>&1 &
```

##### 访问测试

> 访问对应宿主机的`5601`端口

```
http://192.168.245.151:5601/
```
