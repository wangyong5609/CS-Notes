## 服务器准备

| **主机**   | **IP**        | **角色**   | **软件**                                    |
|----------|---------------|----------|-------------------------------------------|
| docker01 | 192.168.88.11 | 普罗米修斯服务端 | Prometheus、node-exporter、cadvisor、Grafana |
| docker01 | 192.168.88.12 | 普罗米修斯客户端 | node-exporter、cadvisor                    |

## 部署 Prometheus 和 Grafana

首先，创建 `/docker/prometheus/` 目录，然后创建 `prometheus.yml`，填入如下内容：

```yaml
global:
  scrape_interval:     15s
  evaluation_interval: 15s
 
alerting:
  alertmanagers:
  - static_configs:
    - targets: ['192.168.88.11:9093']
 
rule_files:
  - "node_down.yml"
 
scrape_configs:
 
  - job_name: 'prometheus'
    static_configs:
    - targets: ['192.168.88.11:9090']
 
  - job_name: 'node'
    scrape_interval: 8s
    static_configs:
    - targets: ['192.168.88.11:9100', '192.168.88.12:9100']
 
  - job_name: 'cadvisor'
    scrape_interval: 8s
    static_configs:
    - targets: ['192.168.88.11:8088', '192.168.88.12:8088']
  - job_name: 'mysql'
    static_configs:
      - targets: [ 'mysqld-exporter:9104' ]
  - job_name: 'redis'
    static_configs:
      - targets: [ 'redis-exporter:9121' ]

  - job_name: 'elasticsearch'
    static_configs:
      - targets: [ 'elasticsearch-exporter:9108' ]

  - job_name: 'kafka'
    static_configs:
      - targets: [ 'kafka-exporter:9308' ]

  - job_name: 'mongodb'
    static_configs:
      - targets: [ 'mongodb-exporter:9216' ]
```

接着进行创建 `node_down.yml`, 添加如下内容：

```yaml
groups:
- name: node_down
  rules:
  - alert: InstanceDown
    expr: up == 0
    for: 1m
    labels:
      user: test
    annotations:
      summary: "Instance {{ $labels.instance }} down"
      description: "{{ $labels.instance }} of job {{ $labels.job }} has been down for more than 1 minutes."
```

### 创建服务端的 docker-compose (docker01)

继续在 docker01 中 `/docker/prometheus/` 目录中创建 `docker-compose-prometheus.yml`, 添加如下内容：

```yaml
version: '2'
 
networks:
    monitor:
        driver: bridge
 
services:
    prometheus:
        image: prom/prometheus
        container_name: prometheus
        hostname: prometheus
        restart: always
        volumes:
            - /docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
            - /docker/prometheus/node_down.yml:/etc/prometheus/node_down.yml
        ports:
            - "9090:9090"
        networks:
            - monitor
 
    grafana:
        image: grafana/grafana
        container_name: grafana
        hostname: grafana
        restart: always
        ports:
            - "3000:3000"
        networks:
            - monitor
 
    node-exporter:
        image: quay.io/prometheus/node-exporter
        container_name: node-exporter
        hostname: node-exporter
        restart: always
        ports:
            - "9100:9100"
        networks:
            - monitor
 
    cadvisor:
        image: google/cadvisor:latest
        container_name: cadvisor
        hostname: cadvisor
        restart: always
        volumes:
            - /:/rootfs:ro
            - /var/run:/var/run:rw
            - /sys:/sys:ro
            - /var/lib/docker/:/var/lib/docker:ro
        ports:
            - "8088:8080"
        networks:
            - monitor
    mysqld-exporter:
      image: prom/mysqld-exporter
      container_name: mysqld-exporter
      environment:
        DATA_SOURCE_NAME: 'root:root@(192.168.88.11:3306)/'
      ports:
        - "9104:9104"
      networks:
        - monitor
    redis-exporter:
      image: oliver006/redis_exporter
      container_name: redis_exporter
      environment:
        - REDIS_ADDR=192.168.88.11:6379
      ports:
        - "9121:9121"
      networks:
        - monitor
    elasticsearch-exporter:
      image: justwatch/elasticsearch_exporter:1.1.0
      ports:
        - "9108:9108"
      networks:
        - monitor
    kafka-exporter:
      image: danielqsj/kafka-exporter
      ports:
        - "9308:9308"
      networks:
        - monitor
    mongodb-exporter:
      image: bitnami/mongodb-exporter:latest
      ports:
        - "9216:9216"
      networks:
        - monitor

```

### 创建客户端的 docker-compose (docker02)

继续在 `/docker/prometheus/` 目录中创建 `docker-compose.yml`, 添加如下内容：

~~~yaml
version: '2'
 
networks:
    monitor:
        driver: bridge
 
services:
    node-exporter:
        image: quay.io/prometheus/node-exporter
        container_name: node-exporter
        hostname: node-exporter
        restart: always
        ports:
            - "9100:9100"
        networks:
            - monitor
 
    cadvisor:
        image: google/cadvisor:latest
        container_name: cadvisor
        hostname: cadvisor
        restart: always
        volumes:
            - /:/rootfs:ro
            - /var/run:/var/run:rw
            - /sys:/sys:ro
            - /var/lib/docker/:/var/lib/docker:ro
        ports:
            - "8088:8080"
        networks:
            - monitor

~~~

## 启动 docker-compose

使用下面的命令启动 docker-compose 定义的容器

~~~bash
# docker01
docker-compose -f /docker/prometheus/docker-compose-prometheus.yml up -d
 
# docker02
docker-compose -f /docker/prometheus/docker-compose.yml up -d
~~~

输入如下内容即代表启动成功：

```bash
Creating network "prometheus_monitor" with driver "bridge"
Creating cadvisor       ... done
Creating prometheus     ... done
Creating node-exporter  ... done
Creating redis_exporter ... done
Creating grafana        ... done
```

打开 `http://192.168.88.11:9095/targets` 查看target的状态，都是UP说明就成功了

![image-20231122212342809](./docker-compose%20%E6%90%AD%E5%BB%BA%20Prometheus+Grafana%20%E7%9B%91%E6%8E%A7%E7%B3%BB%E7%BB%9F.assets/image-20231122212342809.png)

如果state不是UP，可能因为 CentOS7 的防火墙 firewall 导致的，将对应的端口添加到防火墙策略里即可：

```java
firewall-cmd--zone=public --add-port=9100/tcp--permanent
        firewall-cmd--zone=public --add-port=8088/tcp--permanent
        firewall-cmd--zone=public --add-port=9121/tcp--permanent
        firewall-cmd--zone=public --add-port=3000/tcp--permanent
        firewall-cmd--zone=public --add-port=9090/tcp--permanent
        firewall-cmd--reload
```

可通过如下命令查看端口策略是否已经生效

```java
firewall-cmd--permanent--zone=public --list-ports
```

本地测试也可以关闭防火墙（可选）

~~~bash
# 关闭防火墙
systemctl stop firewalld.service
# 禁止防火墙开启自启动
systemctl disable firewalld.service
# 查看防火墙状态
firewall-cmd --state
~~~

## 配置 Grafana

打开 [http://192.168.88.11:3000](http://192.168.88.11:3000/) 使用默认账号密码 admin/admin
登录并修改密码（可以继续用admin作为密码，免得忘了），点击添加数据源。

![image-20231122215340454](./docker-compose%20%E6%90%AD%E5%BB%BA%20Prometheus+Grafana%20%E7%9B%91%E6%8E%A7%E7%B3%BB%E7%BB%9F.assets/image-20231122215340454.png)

点击 `Promethus`

![image-20231122213635824](./docker-compose%20%E6%90%AD%E5%BB%BA%20Prometheus+Grafana%20%E7%9B%91%E6%8E%A7%E7%B3%BB%E7%BB%9F.assets/image-20231122213635824.png)

名字随便填填，`Prometheus server URL`填上 promethus 的访问地址，其他保持默认就可以了，然后保存。

![image-20231122213827686](./docker-compose%20%E6%90%AD%E5%BB%BA%20Prometheus+Grafana%20%E7%9B%91%E6%8E%A7%E7%B3%BB%E7%BB%9F.assets/image-20231122213827686.png)

![image-20231122214206134](./docker-compose%20%E6%90%AD%E5%BB%BA%20Prometheus+Grafana%20%E7%9B%91%E6%8E%A7%E7%B3%BB%E7%BB%9F.assets/image-20231122214206134.png)

### 导入模板

数据源有了，接下来选择展示数据的模板，打开 [https://grafana.com/grafana/dashboards/](https://grafana.com/grafana/dashboards/)

> 主机监控推荐 [12633](https://grafana.com/grafana/dashboards/12633-linux/)
>
> docker监控推荐 [10619](https://grafana.com/grafana/dashboards/10619-docker-host-container-overview/)

可以选择 ID 或者 Json 两种方式导入

![image-20231122220334375](./docker-compose%20%E6%90%AD%E5%BB%BA%20Prometheus+Grafana%20%E7%9B%91%E6%8E%A7%E7%B3%BB%E7%BB%9F.assets/image-20231122220334375.png)

![image-20231122215610795](./docker-compose%20%E6%90%AD%E5%BB%BA%20Prometheus+Grafana%20%E7%9B%91%E6%8E%A7%E7%B3%BB%E7%BB%9F.assets/image-20231122215610795.png)

![image-20231122220102004](./docker-compose%20%E6%90%AD%E5%BB%BA%20Prometheus+Grafana%20%E7%9B%91%E6%8E%A7%E7%B3%BB%E7%BB%9F.assets/image-20231122220102004.png)

Linux主机监控

![image-20231122215239997](./docker-compose%20%E6%90%AD%E5%BB%BA%20Prometheus+Grafana%20%E7%9B%91%E6%8E%A7%E7%B3%BB%E7%BB%9F.assets/image-20231122215239997.png)

Docker 容器监控

![image-20231122221123870](./docker-compose%20%E6%90%AD%E5%BB%BA%20Prometheus+Grafana%20%E7%9B%91%E6%8E%A7%E7%B3%BB%E7%BB%9F.assets/image-20231122221123870.png)

参考文章：[使用 docker-compose 搭建 Prometheus+Grafana 监控系统](https://www.cnblogs.com/xiutai/p/17749211.html)