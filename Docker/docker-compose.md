## docker-compose

> 首先要安装docker

官方文档地址：https://docs.docker.com/compose/install/linux/#install-the-plugin-manually

1. 从Github下载二进制文件

   ~~~bash
   sudo curl -SL https://github.com/docker/compose/releases/download/v2.21.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
   ~~~

   > 如果从[Github](https://github.com/docker/compose/releases)下载失败，可以用浏览器下载下来上传到虚拟机(服务器)，是个笨办法但实用
   >
   > 虽然网上也有从daoCloud下载的方法，但是在我安装时daoCloud镜像服务有问题

2. 给二进制文件添加执行权限

   ~~~bash	
   sudo chmod +x /usr/local/bin/docker-compose
   ~~~

3. 验证安装是否成功

   ~~~bash
   [root@localhost ~]# docker-compose -v
   Docker Compose version v2.21.0
   [root@localhost ~]# 
   ~~~

   

## docker-compose 安装nginx



~~~yaml
version: '3'
services:
  nginx:
    image: registry.cn-hangzhou.aliyuncs.com/zhengqing/nginx:1.21.1                 # 镜像`nginx:1.21.1`
    container_name: nginx               # 容器名为'nginx'
    restart: unless-stopped                                       # 指定容器退出后的重启策略为始终重启，但是不考虑在Docker守护进程启动时就已经停止了的容器
    volumes:                            # 数据卷挂载路径设置,将本机目录映射到容器目录
      - "/docker/nginx/conf/nginx.conf:/etc/nginx/nginx.conf"
      - "/docker/nginx/conf/conf.d:/etc/nginx/conf.d"
      - "/docker/nginx/html:/usr/share/nginx/html"
      - "/docker/nginx/log:/var/log/nginx"
      - "/docker/nginx/cert:/etc/nginx/cert"
    environment:                        # 设置环境变量,相当于docker run命令中的-e
      TZ: Asia/Shanghai
      LANG: en_US.UTF-8
    ports:                              # 映射端口
      - "80:80"
      - "443:443"
~~~

