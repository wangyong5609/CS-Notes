# Halo admin忘记账号密码怎么办

> Halo version：2.8.0

最近把halo博客后台管理的密码忘了，官方文档里提供了一个[解决方案](https://docs.halo.run/user-guide/faq#%E5%BF%98%E8%AE%B0%E5%AF%86%E7%A0%81%E6%80%8E%E4%B9%88%E5%8A%9E)，我尝试过后依然无法登录。



于是我先备份halo然后卸载重新创建，在我重新创建成功并导入备份以后，依然无法登录。



因为halo是docker容器启动的，所以打开备份包查找相关的docker配置，备份包文件名类似这样：`halo_20231126115840.tar.gz`



在`.env`文件中找到了admin登录配置

> halo_20231126115840.tar.gz\halo_20231126115840.tar\halo_20231126115840\app.tar.gz\app.tar\halo\\.env



halo使用备份包恢复以后，会重新生成admin密码，默认保存在服务器 `/opt/1panel/apps/halo/halo/.env` 文件中，如果你在安装1panel是修改了安装目录，请自行对应目录地址。



~~~properties
CONTAINER_NAME="1Panel-halo-uIgL"
CPUS=0
HALO_ADMIN="admin"
HALO_ADMIN_PASSWORD="halo_00000" 
HALO_DB_PORT=3306
HALO_EXTERNAL_URL="http://bbbwdc.com"
HALO_PLATFORM="mysql"
HOST_IP="0.0.0.0"
MEMORY_LIMIT=0
PANEL_APP_PORT_HTTP=8080
PANEL_DB_HOST="mysql"
PANEL_DB_NAME="halo_11111"
PANEL_DB_USER="halo_22222"
PANEL_DB_USER_PASSWORD="halo_3333"
~~~



使用 `HALO_ADMIN` 和 `HALO_ADMIN_PASSWORD` 即可登录Halo admin。



另外，如果在恢复备份过程中出现数据库相关的异常，也可尝试修改备份包中的`.env`配置信息。