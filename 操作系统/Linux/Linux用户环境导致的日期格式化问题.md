最近在项目中遇到一个Linux服务器语言环境的问题，下面来描述一下。

## 上下文场景

在项目中使用了 SimpleDateFormat 格式化时间，注意 pattern 中的 a，它是上午或者下午的标记。

```java
public static void main(String[] args) {
    Date date = new Date();
    String pattern = "yyyy-MM-dd a HH:mm:ss";
    SimpleDateFormat sdf = new SimpleDateFormat(pattern);
    System.out.println(sdf.format(date));
}
```

通过 **locale** 命令服务器的语言环境如下：

![image-20231119195938389](./Linux%E7%94%A8%E6%88%B7%E7%8E%AF%E5%A2%83%E5%AF%BC%E8%87%B4%E7%9A%84%E6%97%A5%E6%9C%9F%E6%A0%BC%E5%BC%8F%E5%8C%96%E9%97%AE%E9%A2%98.assets/image-20231119195938389.png)

在项目中没有设置默认 locale 时，SimpleDateFormat 会使用系统语言环境来格式化日期

![image-20231119201447786](./Linux%E7%94%A8%E6%88%B7%E7%8E%AF%E5%A2%83%E5%AF%BC%E8%87%B4%E7%9A%84%E6%97%A5%E6%9C%9F%E6%A0%BC%E5%BC%8F%E5%8C%96%E9%97%AE%E9%A2%98.assets/image-20231119201447786.png)

我在项目中并没有手动设置 locale，所以 **pattern** 中 **a** 的预期结果应该是中文，就像这样：

```
2023-11-19 下午 20:02:11
```

可是实际输出的结果却是

~~~
2023-11-19 PM 20:17:54
~~~

看来是程序没有获取到正确的 locale。

## 原因

重点来了

**sudo -u user** 和 **su - user** 的区别：

> 在Linux中，`sudo -u user`和 `su - user`都可以用来切换用户，但它们的工作方式有所不同。
>
> - `sudo -u user`：这个命令允许你**以另一个用户的身份执行命令**，但是你需要使用当前用户的密码。`sudo -u user`命令可以让你有选择地执行具有超级用户权限的命令，而不需要完全切换到另一个用户
>
> - `su - user`：这个命令会切换到指定的用户，并且会启动一个新的登录shell。这意味着你的**工作环境（包括工作目录和环境变量）会被设置为新用户的默认工作环境**
>



我们的项目在启动的时候使用的命令是

~~~bash
sudo -u weblogic ./run.sh
~~~

虽然以 weblogic 账号启动了程序，但是使用了执行者本身的语言环境：en_US.UTF-8。所以程序拿到的 locale 是英文，导致格式化后的日期显示不正确。

![image-20231119204942232](./Linux%E7%94%A8%E6%88%B7%E7%8E%AF%E5%A2%83%E5%AF%BC%E8%87%B4%E7%9A%84%E6%97%A5%E6%9C%9F%E6%A0%BC%E5%BC%8F%E5%8C%96%E9%97%AE%E9%A2%98.assets/image-20231119204942232.png)

