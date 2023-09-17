1. 创建nginx容器

   ~~~bash
   docker run -itd --name nginx -p 80:80 -p 443:443 \
   -v /docker/nginx/conf/nginx.conf:/etc/nginx/nginx.conf \
   -v /docker/nginx/conf/conf.d:/etc/nginx/conf.d \
   -v /docker/nginx/html:/usr/share/nginx/html \
   -v /docker/nginx/log:/var/log/nginx \
   -v /docker/nginx/cert:/etc/nginx/cert \
   nginx
   ~~~

   - -P 80:80 -p 443:443
     - 宿主机和容器做端口映射，443端口是为了配置SSL
   - -v /docker/nginx/conf/nginx.conf:/etc/nginx/nginx.conf
     - nginx.conf是nginx的核心配置文件，映射到宿主机便于管理
   - -v /docker/nginx/conf/conf.d:/etc/nginx/conf.d
     - conf.d 目录我一般用来存网站的配置文件，映射到宿主机便于管理
   - -v /docker/nginx/html:/usr/share/nginx/html
     - /usr/share/nginx/html 目录主要存放的是静态网页文件
   - -v /docker/nginx/log:/var/log/nginx
     - /var/log/nginx 是nginx的日志目录，映射到宿主机，便于查看nginx访问日志或者错误日志
   - -v /docker/nginx/cert:/etc/nginx/cert
     - /etc/nginx/cert 是我自己创建用来存放SSL证书的目录

2. 申请SSL证书

   网上有很多获取证书的方法，这里就不赘述了。我申请的阿里云的免费证书，可以参考[这篇文章](https://developer.aliyun.com/article/790697#:~:text=%E9%98%BF%E9%87%8C%E4%BA%91%E5%85%8D%E8%B4%B9SSL%E8%AF%81%E4%B9%A6%EF%BC%88%E5%8E%9FDIgicert%E5%85%8D%E8%B4%B9%E5%8D%95%E5%9F%9F%E5%90%8D%E8%AF%81%E4%B9%A6%EF%BC%89%EF%BC%8C%E6%AF%8F%E4%B8%AA%E9%98%BF%E9%87%8C%E4%BA%91%E8%B4%A6%E5%8F%B7%E4%B8%80%E4%B8%AA%E8%87%AA%E7%84%B6%E5%B9%B4%E9%99%90%E5%88%B6%E7%94%B3%E8%AF%B720%E4%B8%AA%E5%85%8D%E8%B4%B9SSL%E8%AF%81%E4%B9%A6%EF%BC%8C%E9%98%BF%E5%B0%8F%E4%BA%91%E6%9D%A5%E8%AF%A6%E7%BB%86%E8%AF%B4%E4%B8%8B%E9%98%BF%E9%87%8C%E4%BA%91%E5%85%8D%E8%B4%B9SSL%E8%AF%81%E4%B9%A6%E7%94%B3%E8%AF%B7%E6%95%99%E7%A8%8B%E5%8F%8A%E9%99%90%E5%88%B6%E8%AF%B4%E6%98%8E%EF%BC%9A%20%E9%98%BF%E9%87%8C%E4%BA%91SSL%E5%85%8D%E8%B4%B9%E8%AF%81%E4%B9%A6%E7%94%B3%E8%AF%B7%E6%95%99%E7%A8%8B%20%E5%9C%A8,%E9%98%BF%E9%87%8C%E4%BA%91SSL%E8%AF%81%E4%B9%A6%E9%A1%B5%E9%9D%A2%20%EF%BC%8C%E7%82%B9%E5%87%BB%E2%80%9C%E9%80%89%E8%B4%ADSSL%E8%AF%81%E4%B9%A6%E2%80%9D%EF%BC%8C%E5%9C%A8%E6%89%93%E5%BC%80%E7%9A%84%E9%A1%B5%E9%9D%A2%E9%80%89%E6%8B%A9%E2%80%9CDV%E5%8D%95%E5%9F%9F%E5%90%8D%E8%AF%81%E4%B9%A6%E3%80%90%E5%85%8D%E8%B4%B9%E8%AF%95%E7%94%A8%E3%80%91%E2%80%9D%E5%A6%82%E4%B8%8B%E5%9B%BE%EF%BC%9A%20%E6%95%B0%E9%87%8F%E4%B8%BA20%E4%B8%AA%EF%BC%8C%E9%80%89%E6%8B%A920%E4%B8%AA%E6%98%AF%E5%85%8D%E8%B4%B9%E7%9A%84%EF%BC%8C%E5%A6%82%E6%9E%9C%E9%80%89%E6%8B%A940%E3%80%8150%E6%88%96100%E6%98%AF%E9%9C%80%E8%A6%81%E4%BB%98%E8%B4%B9%E7%9A%84%EF%BC%8C%E4%B8%80%E8%88%AC%E6%9D%A5%E8%AE%B220%E4%B8%AA%E5%85%8D%E8%B4%B9SSL%E8%B6%B3%E5%A4%9F%E7%94%A8%E4%BA%86%E3%80%82%20%E7%84%B6%E5%90%8E%E7%82%B9%E2%80%9C%E7%AB%8B%E5%8D%B3%E8%B4%AD%E4%B9%B0%E2%80%9D%EF%BC%8C%E6%94%AF%E4%BB%980%E5%85%83%E5%8D%B3%E5%8F%AF%E3%80%82)

   证书申请成功并下载以后，分别是一个key和crt文件，上传到服务器 /docker/nginx/cert 目录，就像这样：

   ~~~bash
   root@hecs-148770:~# ls /docker/nginx/cert/
   scswww.bbbwdc.com.crt  scswww.bbbwdc.com.key
   ~~~

3. 网站配置

> 如果是云服务器，请确保安全组规则允许访问80和443端口

我的网站配置：/docker/nginx/conf/conf.d/bbbwdc.conf

~~~nginx
server {
    listen  443 ssl;
    server_name      www.bbbwdc.com;
    
    # SSL证书文件路径
    ssl_certificate /etc/nginx/cert/scswww.bbbwdc.com.crt;
    # SSL证书私钥文件路径
    ssl_certificate_key /etc/nginx/cert/scswww.bbbwdc.com.key;

    # 访问日志
    access_log /var/log/nginx/access_bbbwdc.log;

    # 错误日志
    error_log /var/log/nginx/error_bbbwdc.log;

	# 指定使用的SSL协议版本。这里使用了TLSv1.2和TLSv1.3
	ssl_protocols TLSv1.2 TLSv1.3;  
	
	# 指定使用的加密算法套件
    ssl_ciphers HIGH:!aNULL:!MD5;
    
    # 指定服务器偏好的加密算法套件
    ssl_prefer_server_ciphers on;
    
    # 指定SSL会话缓存的类型和大小。这里选择了共享缓存，大小为10MB
    ssl_session_cache shared:SSL:10m;  
    
    # 指定SSL会话的超时时间。这里设置为10分钟
    ssl_session_timeout 10m;  
    
    location / {
        root   /usr/share/nginx/html;
        index  index.html index.htm;
	    proxy_pass http://你的服务器IP:8080;
    }
}
~~~

> 注意更换为自己的服务器IP
>
> 配置修改后，记得重启容器，查看容器日志检查配置是否生效

4. http 转 https

上面的配置完成以后，你会发现在浏览器访问443端口是https，但是访问其他端口还是http，所以我们需要将所有http请求重定向到https

我的重定向配置在：/docker/nginx/conf/conf.d/default.conf

~~~nginx
server {
    listen 80;
    server_name bbbwdc.com;
    access_log /var/log/nginx/access_bbbwdc.log;
    error_log /var/log/nginx/error_bbbwdc.log;
    # 转发规则
    location / {
        proxy_pass http://你的服务器IP:443;

        # 设置代理相关的头信息
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
~~~

> 重启nginx容器

ok，这样一个简单的Nginx服务器就配置完成啦，可以打开[我的博客](https://bbbwdc.com)看下效果。

