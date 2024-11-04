# 

## 介绍

抽象工厂模式（Abstract Factory Pattern）是一种创建型设计模式，它提供了一种方式，用于创建一系列相关或相互依赖的对象，而不需要指定它们具体的类。抽象工厂模式允许系统在不指定具体类的情况下，**通过配置或参数来选择需要的工厂子类**，从而创建出所需的对象

抽象工厂模式和工厂方法模式的区别：工厂方法模式主要用于创建一个具体类的实例，这个类有一个共同的父类或接口。它关注于处理对象的创建。抽象工厂模式用于创建一系列相关或相互依赖的对象，这些对象通常属于同一个产品族。



## 场景

多语言版本软件本地化：我们常用的开发工具IDEA，有英文版本和中文版本，如果要实现根据用户选择的语言改变外观，该如何实现呢？



## 代码一把梭

使用if/else快速实现，所见即所得

### 工程结构

```
├── component
│   ├── Editor.java
│   ├── MenuBar.java
│   ├── ToolBar.java
│   └── impl
│       ├── chinese
│       │   ├── ChineseEditor.java
│       │   ├── ChineseMenuBar.java
│       │   └── ChineseToolBar.java
│       └── english
│           ├── EnglishEditor.java
│           ├── EnglishMenuBar.java
│           └── EnglishToolBar.java
```

### 定义抽象产品接口

简单选取了菜单栏，工具栏，编辑器

```java
/**
 * 菜单栏
 */
public interface MenuBar {
    void display();
}

/**
 * 工具栏
 */
public interface ToolBar {
    void display();
}

/**
 * 编辑器
 */
public interface Editor {
    void display();
}
```

### 根据语言版本实现产品

```java
public class ChineseMenuBar implements MenuBar {
    @Override
    public void display() {
        System.out.println("中文菜单栏");
    }
}

public class ChineseToolBar implements ToolBar {
    @Override
    public void display() {
        System.out.println("中文工具栏");
    }
}

public class ChineseEditor implements Editor {
    @Override
    public void display() {
        System.out.println("中文编辑器");
    }
}

public class EnglishMenuBar implements MenuBar {
    @Override
    public void display() {
        System.out.println("English MenuBar");
    }
}

public class EnglishToolBar implements ToolBar {
    @Override
    public void display() {
        System.out.println("English ToolBar");
    }
}

public class EnglishEditor implements Editor {
    @Override
    public void display() {
        System.out.println("English Editor");
    }
}
```

### 测试

```java
@Test
public void shit() {
    String language = "chinese";
    display(language);

    language = "english";
    display(language);
}

public void display(String language) {
    MenuBar menuBar;
    if ("Chinese".equalsIgnoreCase(language)) {
        menuBar =  new ChineseMenuBar();
    } else if ("English".equalsIgnoreCase(language)) {
        menuBar =  new EnglishMenuBar();
    } else {
        throw new IllegalArgumentException("Unsupported language: " + language);
    }

    ToolBar toolBar;
    if ("Chinese".equalsIgnoreCase(language)) {
        toolBar =  new ChineseToolBar();
    } else if ("English".equalsIgnoreCase(language)) {
        toolBar =  new EnglishToolBar();
    } else {
        throw new IllegalArgumentException("Unsupported language: " + language);
    }

    Editor editor;
    if ("Chinese".equalsIgnoreCase(language)) {
        editor =  new ChineseEditor();
    } else if ("English".equalsIgnoreCase(language)) {
        editor =  new EnglishEditor();
    } else {
        throw new IllegalArgumentException("Unsupported language: " + language);
    }

    toolBar.display();
    menuBar.display();
    editor.display();
}
```

测试结果：

```
中文工具栏
中文菜单栏
中文编辑器
English ToolBar
English MenuBar
English Editor
```



## 使用抽象工厂模式优化

### 代码结构

```
├── component
│   ├── Editor.java
│   ├── MenuBar.java
│   ├── ToolBar.java
│   └── impl
│       ├── chinese
│       │   ├── ChineseEditor.java
│       │   ├── ChineseMenuBar.java
│       │   └── ChineseToolBar.java
│       └── english
│           ├── EnglishEditor.java
│           ├── EnglishMenuBar.java
│           └── EnglishToolBar.java
└── factory
    ├── IdeaFactory.java
    └── impl
        ├── ChineseIdeaFactory.java
        └── EnglishIdeaFactory.java
```

在上面代码一把梭的基础上，定义了抽象工厂接口和实现了多语言工厂

### 定义抽象工厂

我们定义一个抽象工厂接口，它包含创建各种抽象产品的方法。

```java
/**
 * IDEA工厂
 */
public interface IdeaFactory {
    ToolBar createToolBar();
    Editor createEditor();
    MenuBar createMenuBar();
}
```

### 根据多语言版本实现工厂接口

中文工厂

```java
public class ChineseIdeaFactory implements IdeaFactory {
    @Override
    public ToolBar createToolBar() {
        return new ChineseToolBar();
    }

    @Override
    public Editor createEditor() {
        return new ChineseEditor();
    }

    @Override
    public MenuBar createMenuBar() {
        return new ChineseMenuBar();
    }
}
```

英文工厂

```java
public class EnglishIdeaFactory implements IdeaFactory {
    @Override
    public ToolBar createToolBar() {
        return new EnglishToolBar();
    }

    @Override
    public Editor createEditor() {
        return new EnglishEditor();
    }

    @Override
    public MenuBar createMenuBar() {
        return new EnglishMenuBar();
    }
}
```

可以看到不同语言的工厂创建了一系列相关联的产品族，这时候再来看上面对抽象工厂模式的介绍，似乎也好理解了，也可以理解为工厂的工厂。

### 测试

```java
@Test
public void test() {
    ChineseIdeaFactory chineseIdeaFactory = new ChineseIdeaFactory();
    ToolBar toolBar = chineseIdeaFactory.createToolBar();
    MenuBar menuBar = chineseIdeaFactory.createMenuBar();
    Editor editor = chineseIdeaFactory.createEditor();
    toolBar.display();
    menuBar.display();
    editor.display();

    EnglishIdeaFactory englishIdeaFactory = new EnglishIdeaFactory();
    toolBar = englishIdeaFactory.createToolBar();
    menuBar = englishIdeaFactory.createMenuBar();
    editor = englishIdeaFactory.createEditor();
    toolBar.display();
    menuBar.display();
    editor.display();
}
```

结果：

```
中文工具栏
中文菜单栏
中文编辑器
English ToolBar
English MenuBar
English Editor
```



## 总结

上面代码可以总结为以下实现步骤：

1. **定义抽象产品**：确定系统中需要创建的对象的接口。
2. **创建具体产品**：为每种产品实现具体的类。
3. **定义抽象工厂**：定义一个接口，声明创建各种抽象产品的方法。
4. **实现具体工厂**：为每种产品系列实现一个具体工厂类，生成一系列具体产品的实例。
5. **客户端代码**：使用抽象工厂来创建所需的产品，无需关心具体的产品类

抽象工厂模式隐藏了对象创建的细节，我们不需要关心产品的实现。

前面学过工厂方法模式，对比两者，工厂方法适合由工厂根据某种类型创建一个具体实现类的场景，而抽象工厂模式适合用来创建一系列相关联的产品族这样的场景。



本章源码地址：https://github.com/wangyong5609/design-patterns/tree/main/abstract-factory