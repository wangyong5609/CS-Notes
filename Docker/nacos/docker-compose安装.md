根据 [官方文档](https://github.com/nacos-group/nacos-docker/blob/master/README_ZH.md) 安装 Standalone Mysql

启动容器前修改healthcheck，healthcheck默认采用root账号和空密码验证
```yaml
healthcheck:
      test: mysqladmin ping -h 127.0.0.1 -u $$MYSQL_USER --password=$$MYSQL_PASSWORD
```
