安装wordpress以后，想要上传插件，却发现上传文件限制大小为2M

> 上传的文件大小超过php.ini文件中定义的upload_max_filesize值。

## 如何修改呢

我是用docker安装的wordpress,因为php.ini是PHP的配置文件，所以需要到容器中，先找到这个文件。

1. 进入容器

~~~bash
docker exec -it wordpress bash
~~~

2. 查看php ini

~~~bash
php --ini
~~~

![image-20230922213622634](https://qny.bbbwdc.com/blog/image-20230922213622634.png)

> /usr/local/etc/php/php.ini 默认是没有的，需要自己创建

3. 创建php.ini

进入 /usr/local/etc/php 目录，目录下有文件 php.ini-production，复制一份这个文件，命名为 php.ini。

~~~bash
cp php.ini-production php.ini
~~~

4. 修改文件上传限制


在php.ini中找到 **upload_max_filesize = 2M**, 修改后退出并重启容器。

5. 检查是否生效

打开wordpress admin，在菜单 【媒体】【新增文件】中查看最大上传文件大小。

![image-20230922215006428](https://qny.bbbwdc.com/blog/image-20230922215006428.png)