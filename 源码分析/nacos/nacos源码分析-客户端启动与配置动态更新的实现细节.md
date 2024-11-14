## 启动加载 NacosConfigBootstrapConfiguration

`Springboot`在启动的时候会读取 `spring-cloud-starter-alibaba-nacos-config-2021.0.5.0.jar`下的 `spring.factories`加载`com.alibaba.cloud.nacos.NacosConfigBootstrapConfiguration`

![image-20241114152350162](./nacos源码分析-客户端启动与配置动态更新的实现细节.assets/image-20241114152350162.png)

调用`org.springframework.core.io.support.SpringFactoriesLoader#loadFactoryNames`获取工厂类型名为`org.springframework.cloud.bootstrap.BootstrapConfiguration`的 类名列表。`org.springframework.cloud.bootstrap.BootstrapConfiguration`就是上面 `spring.factories`中的 `KEY`值

`loadSpringFactories`是加载 `spring.factories`文件的具体执行方法，返回一个`HashMap`。

![image-20241114153917736](./nacos源码分析-客户端启动与配置动态更新的实现细节.assets/image-20241114153917736.png)

![image-20241114152249007](./nacos源码分析-客户端启动与配置动态更新的实现细节.assets/image-20241114152249007.png)



## NacosConfigBootstrapConfiguration

`NacosConfigBootstrapConfiguration` 是 Spring Cloud Alibaba 中与 Nacos 配置管理相关的一个配置类。它主要用于在 Spring Boot 应用程序中引导 Nacos 配置的加载和管理。

![image-20241114155252523](./nacos源码分析-客户端启动与配置动态更新的实现细节.assets/image-20241114155252523.png)

配置类中一共加载了四个`Bean`，它们的作用如下：

### 1. NacosConfigProperties

`NacosConfigProperties`用于封装与 Nacos 配置相关的属性。它提供了对 Nacos 配置中心的连接和配置管理所需的各种设置。

![image-20241114155745939](./nacos源码分析-客户端启动与配置动态更新的实现细节.assets/image-20241114155745939.png)



### 2. NacosConfigManager

主要用于管理和操作 Nacos 配置相关的功能。它提供了一种简便的方式来获取、更新和管理 Nacos 配置中心中的配置信息，支持动态配置更新，允许应用在运行时获取最新的配置，而不需要重启应用。

来看下`NacosConfigManager`的构造函数做了什么：

![image-20241114173808589](./nacos源码分析-客户端启动与配置动态更新的实现细节.assets/image-20241114173808589.png)

上面创建的 Bean `NacosConfigProperties` 作为参数传入构造函数，将配置信息交给 `NacosConfigManager`管理，并且调用`createConfigService`创建 `ConfigService`。

![image-20241114174513189](./nacos源码分析-客户端启动与配置动态更新的实现细节.assets/image-20241114174513189.png)

`createConfigService`方法中通过反射的方式调用构造函数创建了`NacosConfigService`

#### 2.1 NacosConfigService

`NacosConfigService` 是 Nacos 客户端中的一个核心类，负责与 Nacos 配置中心进行交互。它提供了多种方法来获取、发布和管理配置数据。这个类是 Nacos Java 客户端的主要入口之一，允许开发者方便地操作 Nacos 配置。

![image-20241114174851225](./nacos源码分析-客户端启动与配置动态更新的实现细节.assets/image-20241114174851225.png)



![image-20241114175748329](./nacos源码分析-客户端启动与配置动态更新的实现细节.assets/image-20241114175748329.png)



### 3. NacosPropertySourceLocator

![image-20241114160229534](./nacos源码分析-客户端启动与配置动态更新的实现细节.assets/image-20241114160229534.png)