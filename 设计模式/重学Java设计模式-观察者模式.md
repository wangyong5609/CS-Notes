## 简介

**观察者模式**是一种行为设计模式， 允许你定义一种订阅机制， 可在对象事件发生时通知多个 “观察” 该对象的其他对象。

> **亦称：** 事件订阅者、监听者、Event-Subscriber、Listener、Observer

##  

## 1. 定义与基本概念

### 定义

观察者模式其中一个对象（称为“主题”或“主体”）维护一组依赖于它的对象（称为“观察者”），并在其状态发生变化时通知这些观察者

### 基本概念

#### 主题（Subject）

- **定义**：主题是被观察的对象，它维护一个观察者的列表，并在状态发生变化时通知这些观察者。
- 角色
  - 注册观察者（`registerObserver(Observer observer)`）
  - 移除观察者（`removeObserver(Observer observer)`）
  - 通知观察者（`notifyObservers()`）

#### 观察者（Observer）

- **定义**：观察者是对主题感兴趣的对象，它需要实现一个接口，以便在主题状态变化时接收通知。
- 角色
  - 更新方法（`update()`）：用于接收主题状态变化的通知。

#### 具体主题（Concrete Subject）

- **定义**：具体主题是实现主题接口的具体类，它包含状态数据，并在状态变化时调用通知观察者的方法。
- **示例**：天气数据提供者，它包含温度、湿度等数据。

#### 具体观察者（Concrete Observer）

- **定义**：具体观察者是实现观察者接口的具体类，它根据主题的状态变化进行相应的更新。
- **示例**：显示当前天气的用户界面组件。



## 2. 场景案例

假设一个抖音博主（Subject）发布视频通知粉丝（Observer）的场景，博主发布视频通知所有粉丝。



## 3. 代码一把梭

**定义博主类**

```java
/**
 * 抖音博主
 */
public class DouyinBlogger {
    private String latestVideoTitle;

    // 模拟发布新视频
    public void publishNewVideo(String title) {
        this.latestVideoTitle = title;
        System.out.println("博主发布了新视频: " + title);
    }

    // 通知粉丝
    public void notifyFollower(String userName) {
        System.out.println(userName + ", 快来看我的新视频: " + latestVideoTitle);
    }


    public String getLatestVideoTitle() {
        return latestVideoTitle;
    }

    public void setLatestVideoTitle(String latestVideoTitle) {
        this.latestVideoTitle = latestVideoTitle;
    }
}
```

**发布视频->通知粉丝测试**

```java
@Test
public void shit() {
    DouyinBlogger douyinBlogger = new DouyinBlogger();
    douyinBlogger.publishNewVideo("国足五年来首次对日本破门");

    douyinBlogger.notifyFollower("小王");
    douyinBlogger.notifyFollower("小李");
    douyinBlogger.notifyFollower("小张");
    douyinBlogger.notifyFollower("小杨");
    douyinBlogger.notifyFollower("小明");
    douyinBlogger.notifyFollower("小红");
    douyinBlogger.notifyFollower("小赵");
    douyinBlogger.notifyFollower("小何");
    douyinBlogger.notifyFollower("其他N个粉丝");
}
```

**测试结果**

```
博主发布了新视频: 国足五年来首次对日本破门
小王, 快来看我的新视频: 国足五年来首次对日本破门
小李, 快来看我的新视频: 国足五年来首次对日本破门
小张, 快来看我的新视频: 国足五年来首次对日本破门
小杨, 快来看我的新视频: 国足五年来首次对日本破门
小明, 快来看我的新视频: 国足五年来首次对日本破门
小红, 快来看我的新视频: 国足五年来首次对日本破门
小赵, 快来看我的新视频: 国足五年来首次对日本破门
小何, 快来看我的新视频: 国足五年来首次对日本破门
其他粉丝, 快来看我的新视频: 国足五年来首次对日本破门
```



## 4. 观察者模式重构代码

**工程结构**

```
├── src
│   ├── main
│   │   └── java
│   │       └── com
│   │           └── bbbwdc
│   │               └── observer
│   │                   ├── pattern
│   │                   │   ├── observer
│   │                   │   │   ├── IObserver.java
│   │                   │   │   ├── XiaohongObserver.java
│   │                   │   │   ├── XiaoliObserver.java
│   │                   │   │   ├── XiaomingObserver.java
│   │                   │   │   ├── XiaowangObserver.java
│   │                   │   │   ├── XiaoyangObserver.java
│   │                   │   │   └── XiaozhangObserver.java
│   │                   │   └── subject
│   │                   │       ├── DouyinBloggerSubject.java
│   │                   │       └── ISubject.java
```

**定义主题接口**

```java
/**
 * 主题接口
 */
public interface ISubject {
    /**
     * 注册观察者
     * @param observer
     */
    void registerObserver(IObserver observer);

    /**
     * 移除观察者
     * @param observer
     */
    void removeObserver(IObserver observer);
    /**
     * 通知观察者
     */
    void notifyObservers();
}
```

**定义观察者接口**

```java
/**
 * 观察者接口
 */
public interface IObserver {
    
    void update(String videoTitle);
}
```

**实现主题接口（抖音博主）**

```java
/**
 * 抖音博主
 */
public class DouyinBloggerSubject implements ISubject {
    // 粉丝列表
    private List<IObserver> followers;
    // 最新视频标题
    private String latestVideoTitle;

    public DouyinBloggerSubject() {
        followers = new ArrayList<>();
    }

    @Override
    public void registerObserver(IObserver observer) {
        followers.add(observer);
    }

    @Override
    public void removeObserver(IObserver observer) {
        followers.remove(observer);
    }

    @Override
    public void notifyObservers() {
        for (IObserver follower : followers) {
            follower.update(latestVideoTitle);
        }
    }

    // 模拟发布新视频
    public void publishNewVideo(String title) {
        this.latestVideoTitle = title;
        System.out.println("博主发布了新视频: " + title);
        notifyObservers(); // 通知所有粉丝
    }
```

**实现观察者接口（粉丝）**

有多个粉丝，这里仅拿小明作为代码示例。在实际场景中，一个主题可能有很多的观察者。

```java
public class XiaomingObserver implements IObserver {
    @Override
    public void update(String videoTitle) {
        String name = "小明";
        System.out.println(name + ", 快来看我的新视频: " + videoTitle);
    }
}
```

**测试**

```java
@Test
public void pattern() {
    DouyinBloggerSubject blogger = new DouyinBloggerSubject();
    blogger.registerObserver(new XiaowangObserver());
    blogger.registerObserver(new XiaoliObserver());
    blogger.registerObserver(new XiaozhangObserver());
    blogger.registerObserver(new XiaoyangObserver());
    blogger.registerObserver(new XiaomingObserver());
    XiaohongObserver xiaohongObserver = new XiaohongObserver();
    blogger.registerObserver(xiaohongObserver);
    blogger.publishNewVideo("国足五年来首次对日本破门");

    blogger.removeObserver(xiaohongObserver);
    
    blogger.publishNewVideo("李子柒复出");
}
```

**测试结果**

```
博主发布了新视频: 国足五年来首次对日本破门
小王, 快来看我的新视频: 国足五年来首次对日本破门
小李, 快来看我的新视频: 国足五年来首次对日本破门
小张, 快来看我的新视频: 国足五年来首次对日本破门
小杨, 快来看我的新视频: 国足五年来首次对日本破门
小明, 快来看我的新视频: 国足五年来首次对日本破门
小红, 快来看我的新视频: 国足五年来首次对日本破门
博主发布了新视频: 李子柒复出
小王, 快来看我的新视频: 李子柒复出
小李, 快来看我的新视频: 李子柒复出
小张, 快来看我的新视频: 李子柒复出
小杨, 快来看我的新视频: 李子柒复出
小明, 快来看我的新视频: 李子柒复出
```

## 5. 观察者模式的优缺点

**优点**

- 解耦合
- 灵活性和可扩展性
- 动态订阅和取消订阅
- 符合单一职责原则

 **缺点**

- 可能导致内存泄漏
- 过多的通知可能影响性能
- 复杂性增加

## 6. 观察者模式的应用场景

- 用户界面事件处理
- 数据变化通知
- 事件驱动架构
- 实时数据更新（如股票价格、天气信息）
- 消息推送系统

## 7. 观察者模式和发布订阅模式的区别

> 在学习观察者模式时，总感觉它和发布订阅模式很像，两者傻傻分不清楚

### 7.1 定义

观察者模式

观察者模式是一种**设计模式**，其中一个对象（主题或被观察者）维护一组依赖于它的对象（观察者），并在其状态发生变化时自动通知这些观察者。

发布-订阅模式

发布-订阅模式是一种更松耦合的**架构模式**，其中发布者（发布事件的对象）与订阅者（接收事件的对象）之间没有直接的引用关系，而是通过一个中介（如消息代理或事件总线）进行通信。

### 7.2 触发机制

观察者模式

- 观察者**直接注册到主题上**，主题在状态变化时主动调用观察者的更新方法。
- 主题和观察者之间有直接的依赖关系。

发布-订阅模式

- 发布者**将消息或事件发布到中介**，由中介负责将消息分发给所有订阅者。
- **发布者和订阅者之间没有直接的依赖关系**，通常通过中介进行解耦。

### 7.3 关系

观察者模式

- **一对多关系**：一个主题可以有多个观察者。
- 观察者与主题之间是紧耦合的，观察者需要知道主题的存在。

发布-订阅模式

- **多对多关系**：一个发布者可以有多个订阅者，一个订阅者可以订阅多个发布者的消息。
- 发布者与订阅者之间完全解耦，彼此之间不需要了解对方的存在。

### 7.4 通知方式

观察者模式

- 通常是**同步通知**，主题在状态变化时立即通知所有观察者。
- 观察者会在接收到通知后立即执行更新操作。

发布-订阅模式

- 通常**支持异步通知**，发布者可以在发布消息后继续执行，不必等待所有订阅者处理完毕。
- 订阅者可以在合适的时机处理接收到的消息。

### 7.5 用途

观察者模式

- 适用于对象之间有直接依赖关系的场景，例如 GUI 事件处理、实时数据更新等。

发布-订阅模式

- 适用于更复杂的事件驱动架构，特别是在需要解耦、灵活性和异步处理的场景中，例如消息队列、事件总线等。



参考资料：

[观察者模式](https://refactoringguru.cn/design-patterns/observer)

[重学 Java 设计模式](https://bugstack.cn/md/develop/design-pattern/2020-06-30-%E9%87%8D%E5%AD%A6%20Java%20%E8%AE%BE%E8%AE%A1%E6%A8%A1%E5%BC%8F%E3%80%8A%E5%AE%9E%E6%88%98%E8%A7%82%E5%AF%9F%E8%80%85%E6%A8%A1%E5%BC%8F%E3%80%8B.html)