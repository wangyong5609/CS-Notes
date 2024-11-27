## 简介

> 亦称：proxy

**代理模式**（Proxy Pattern）是一种**结构性设计模式**，主要用于提供一个替代或占位符对象，以控制对其他对象的访问。代理模式通过代理对象来间接访问真实对象，从而实现对真实对象的控制和扩展。

## 1. 基本概念

1. **服务接口（ServiceInterface）**：
   - 定义了真实服务和代理所共有的接口。
2. **真实服务（RealService）**：
   - 实现了服务接口的具体对象，包含实际的业务逻辑。
3. **代理（Proxy）**：
   - 持有对真实服务的引用，并实现了服务接口。代理可以在调用真实服务的方法之前或之后添加一些额外的操作。

在大部分情况下， 代理在完成一些任务后应将工作委派给真实服务对象。

## 2. 代理模式

### **虚代理（Virtual Proxy）**

- **定义**：虚代理是在需要时才创建和加载真实对象的代理。通过延迟加载，虚代理可以提高性能并节省资源，尤其是在处理开销较大的对象时。
- **应用场景**：例如，加载一个耗时但不常用的大对象，在需要时再加载，提升系统性能

### **保护代理（Protection Proxy）**

- **定义**：保护代理用于控制对真实对象的访问权限。它可以在调用真实对象的方法之前进行权限检查，从而确保只有授权的用户才能执行特定操作。
- **应用场景**：常用于系统中管理用户权限，例如，只有管理员才能执行某些敏感操作。

### **远程代理（Remote Proxy）**

- **定义**：远程代理用于代表一个在远程服务器上运行的对象。它负责处理客户端与服务器之间的通信，将请求转发给远程对象，并返回结果。
- **应用场景**：如分布式系统中的远程方法调用（RMI）或微服务架构中的服务调用。

### **缓存代理（Cache Proxy）**

- **定义**：缓存代理在访问真实对象时，可以缓存返回的结果，以提高后续相同请求的效率。它在一定时期内保存数据，避免重复计算或请求。
- **应用场景**：如 Web 应用程序中，常用缓存代理来存储常用数据，减少对数据库的查询。

### **智能代理（Smart Proxy）**

- **定义**：智能代理在访问真实对象时提供附加的功能，如引用计数、延迟初始化等。它可以在访问对象时提供更多的控制和管理。
- **应用场景**：如内存管理、资源控制等，确保对象在被使用时已被正确初始化和管理。

### **同步代理（Synchronization Proxy）**

- **定义**：同步代理用于处理多线程环境中的对象访问。它可以确保对真实对象的访问是线程安全的。
- **应用场景**：如在多线程应用中，确保对共享资源的安全访问，避免数据竞争和不一致状态。

## 3. 虚代理案例

模拟一个在线视频播放器的场景。在这个示例中，视频对象（`RealVideo`）的加载过程是耗时的，因此我们使用虚代理（`ProxyVideo`）来延迟加载视频数据，直到用户真正需要播放视频时才进行加载。

**视频接口**

```java
/**
 * 视频接口
 */
public interface Video {
    /**
     * 播放视频
     */
    void play();
}
```

**真实视频类**

```java
/**
 * 真实视频类
 */
public class RealVideo implements Video {
    private String filename;

    public RealVideo(String filename) {
        this.filename = filename;
        loadVideoFromDisk();
    }

    /**
     * 从磁盘加载视频
     */
    private void loadVideoFromDisk() {
        System.out.println("Loading video: " + filename);
        // 模拟加载时间，假设加载时间为2秒
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void play() {
        System.out.println("Playing video: " + filename);
    }
}
```

**代理视频类**

```java
/**
 * 视频代理
 */
public class ProxyVideo implements Video {
    private RealVideo realVideo;
    private String filename;

    public ProxyVideo(String filename) {
        this.filename = filename;
    }

    @Override
    public void play() {
        // 只有在需要时才加载真实视频
        if (realVideo == null) {
            realVideo = new RealVideo(filename);
        }
        realVideo.play();
    }
}
```

**测试**

```java
@Test
public void testVirtualProxy() {
    ProxyVideo video1 = new ProxyVideo("video1.mp4");
    ProxyVideo video2 = new ProxyVideo("video2.mp4");
    System.out.println("Ready to play video1...");
    video1.play(); // 第一次调用，加载视频
    System.out.println();

    System.out.println("Ready to play video1 again...");
    video1.play(); // 第二次调用，直接播放视频
    System.out.println();

    System.out.println("Ready to play video2...");
    video2.play(); // 第一次调用，加载视频
    System.out.println();
}
```

**测试结果**

```
Ready to play video1...
Loading video: video1.mp4
Playing video: video1.mp4

Ready to play video1 again...
Playing video: video1.mp4

Ready to play video2...
Loading video: video2.mp4
Playing video: video2.mp4
```

在示例中，代理持有真实视频对象的引用，虽然创建了代理对象，但是实际在调用 play 方法时，真实视频对象才被创建，实现延迟加载的效果。

## 4. 保护代理案例

模拟一个在线图书馆管理系统。只有管理员角色可以添加或删除书籍，而普通用户只能查看书籍列表。

**图书接口**

```java
/**
 * 图书接口
 */
public interface Book {
    /**
     * 查看图书
     */
    void viewBooks();
    /**
     * 添加图书
     * @param bookName 图书名称
     */
    void addBook(String bookName);
    /**
     * 删除图书
     * @param bookName 图书名称
     */
    void removeBook(String bookName);
}
```

**真实图书类**

```java
import java.util.ArrayList;
import java.util.List;

/**
 * 真实图书
 */
public class RealBook implements Book {
    // 图书列表
    private List<String> books;

    public RealBook() {
        books = new ArrayList<>();
        books.add("Java 编程思想");
        books.add("设计模式：可复用面向对象软件的基础");
    }

    @Override
    public void viewBooks() {
        System.out.println("当前书籍列表：");
        for (String book : books) {
            System.out.println("- " + book);
        }
    }

    @Override
    public void addBook(String bookName) {
        books.add(bookName);
        System.out.println("已添加书籍：" + bookName);
    }

    @Override
    public void removeBook(String bookName) {
        books.remove(bookName);
        System.out.println("已删除书籍：" + bookName);
    }
}
```

**代理图书类**

```java
/**
 * 图书代理
 */
public class BookProxy implements Book {
    // 真实图书
    private RealBook realBook;
    // 用户角色
    private String role;

    public BookProxy(RealBook realBook, String role) {
        this.realBook = realBook;
        this.role = role;
    }

    @Override
    public void viewBooks() {
        realBook.viewBooks();
    }

    @Override
    public void addBook(String bookName) {
        // 只有管理员可以添加书籍
        if (role.equals("ADMIN")) {
            realBook.addBook(bookName);
        } else {
            System.out.println("访问被拒绝：" + role + " 没有权限添加书籍。");
        }
    }

    @Override
    public void removeBook(String bookName) {
        // 只有管理员可以删除书籍
        if (role.equals("ADMIN")) {
            realBook.removeBook(bookName);
        } else {
            System.out.println("访问被拒绝：" + role + " 没有权限删除书籍。");
        }
    }
}
```

**测试**

```java
@Test
public void testProtectionProxy() {
    RealBook realBook = new RealBook();

    // 创建代理对象，分别为管理员和普通用户
    Book adminProxy = new BookProxy(realBook, "ADMIN");
    Book userProxy = new BookProxy(realBook, "USER");

    // 管理员用户可以查看书籍
    System.out.println("管理员尝试查看书籍：");
    adminProxy.viewBooks(); // 允许执行

    // 普通用户也可以查看书籍
    System.out.println("普通用户尝试查看书籍：");
    userProxy.viewBooks(); // 允许执行

    // 普通用户尝试添加书籍
    System.out.println("普通用户尝试添加书籍：");
    userProxy.addBook("设计模式之禅");
    
    // 管理员可以添加书籍
    System.out.println("管理员尝试添加书籍：");
    adminProxy.addBook("深入理解Java虚拟机");

    // 普通用户尝试删除书籍
    System.out.println("普通用户尝试删除书籍：");
    userProxy.removeBook("Java 编程思想");

    // 管理员可以删除书籍
    System.out.println("管理员尝试删除书籍：");
    adminProxy.removeBook("Java 编程思想");

    // 查看当前书籍列表
    System.out.println("查看当前书籍列表：");
    adminProxy.viewBooks();
}
```

**测试结果**

```
管理员尝试查看书籍：
当前书籍列表：
- Java 编程思想
- 设计模式：可复用面向对象软件的基础
普通用户尝试查看书籍：
当前书籍列表：
- Java 编程思想
- 设计模式：可复用面向对象软件的基础
普通用户尝试添加书籍：
访问被拒绝：USER 没有权限添加书籍。
管理员尝试添加书籍：
已添加书籍：深入理解Java虚拟机
普通用户尝试删除书籍：
访问被拒绝：USER 没有权限删除书籍。
管理员尝试删除书籍：
已删除书籍：Java 编程思想
查看当前书籍列表：
当前书籍列表：
- 设计模式：可复用面向对象软件的基础
- 深入理解Java虚拟机
```

上面的示例使用保护代理模式实现了权限控制，只有管理员才可以添加删除图书。



## 3. 优缺点

✅**控制访问**：代理可以控制对真实对象的访问，提供安全性和权限管理。

✅**附加功能**：可以在不修改真实对象的情况下添加额外的功能（如日志、监控等）。

✅**懒加载**：可以在需要时才创建真实对象，从而节省资源。

❌**增加复杂性**：引入代理对象会增加系统的复杂性。

❌**性能开销**：代理可能会引入额外的性能开销，尤其是在方法调用频繁时。

## 4. 与其他模式的关系

### 4.1 代理模式 VS 装饰器模式