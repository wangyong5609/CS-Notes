# Centos7 安装Mysql5.7

## 官网下载RPM文件

打开官网:https://dev.mysql.com/downloads/mysql/

![image-20231121203654959](./Centos7%20%E5%AE%89%E8%A3%85Mysql5.7.assets/image-20231121203654959.png)

！注意啊，下载页面不需要登录

![image-20231121204556139](./Centos7%20%E5%AE%89%E8%A3%85Mysql5.7.assets/image-20231121204556139.png)

## 上传并解压缩

1. 创建MySQL安装目录

~~~bash
mkdir /var/lib/mysql
~~~

上传到服务器任意目录，执行命令解压缩到安装目录

~~~bash
tar -xvf mysql-5.7.44-1.el7.x86_64.rpm-bundle.tar -C /var/lib/mysql/
~~~



## 检查是否已经安装了 MySQL 或者 MariaDB

CentOS 7 默认安装了 MariaDB，如果已经安装，需要先卸载。

执行命令来检查是否已经安装了 MariaDB

~~~bash
rpm -qa | grep mariadb
~~~

如果已经安装，可以看到以下结果：

![image-20231121205113201](./Centos7%20%E5%AE%89%E8%A3%85Mysql5.7.assets/image-20231121205113201.png)

执行命令卸载 MariaDB

~~~bash
rpm -e --nodeps mariadb-libs-5.5.68-1.el7.x86_64
~~~



## 安装rmp包

【必须安装】

```bash
rpm -ivh mysql-community-common-5.7.44-1.el7.x86_64.rpm

rpm -ivh mysql-community-libs-5.7.44-1.el7.x86_64.rpm

rpm -ivh mysql-community-client-5.7.44-1.el7.x86_64.rpm

rpm -ivh mysql-community-server-5.7.44-1.el7.x86_64.rpm
```

【非必须安装】

~~~bash
rpm -ivh mysql-community-libs-compat-5.7.44-1.el7.x86_64.rpm

rpm -ivh mysql-community-embedded-compat-5.7.44-1.el7.x86_64.rpm

rpm -ivh mysql-community-devel-5.7.44-1.el7.x86_64.rpm

rpm -ivh mysql-community-test-5.7.44-1.el7.x86_64.rpm
~~~

> **必须安装**的部分包括：
>
> - `mysql-community-common`：这是 MySQL 的公共文件。
> - `mysql-community-libs`：这是 MySQL 的库文件。
> - `mysql-community-client`：这是 MySQL 的客户端工具。
> - `mysql-community-server`：这是 MySQL 服务器。
>
> **非必须安装**的部分包括：
>
> - `mysql-community-libs-compat`：这是 MySQL 的兼容库文件。
> - `mysql-community-embedded-compat`：这是 MySQL 的嵌入式兼容库文件。
> - `mysql-community-devel`：这是 MySQL 的开发库和头文件。
> - `mysql-community-test`：这是 MySQL 的测试套件。

> 如果安装出现一下问题，请执行 `sudo yum install perl perl-Getopt-Long`
>
> ~~~
> 错误：依赖检测失败：
>         /usr/bin/perl 被 mysql-community-server-5.7.44-1.el7.x86_64 需要
>         perl(Getopt::Long) 被 mysql-community-server-5.7.44-1.el7.x86_64 需要
>         perl(strict) 被 mysql-community-server-5.7.44-1.el7.x86_64 需要
> ~~~

// todo