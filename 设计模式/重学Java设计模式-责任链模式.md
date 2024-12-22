## 简介

在软件开发中，设计模式是解决特定问题的成熟模板，它们提供了一种标准的方式来解决常见的软件设计问题。

**责任链模式**是一种行为设计模式，允许你将请求沿着处理者链进行发送。收到请求后，每个处理者均可对请求进行处理，或将其传递给链上的下个处理者。

## 1. 定义与基本概念

### 定义

责任链模式是一种对象行为型模式，它包含多个对象，每个对象都包含对下一个对象的引用，构成一条链。

请求在这个链上传递，每个对象都可以选择处理请求或者传递给下一个处理者，甚至停止传递请求直接返回。

责任链模式定义了一种使多个对象都有机会处理请求的机制，从而解耦请求的发送者和接收者。

请求的发送者不必知道链的结构，也不必知道哪个对象会最终处理这个请求。

### 基本概念

- 处理者（Handler）
  处理者是责任链中的对象，它们负责处理请求。每个处理者都有责任决定是否能够处理该请求，如果能够处理，则进行处理；如果不能处理，则将请求传递给链中的下一个处理者。

- 抽象处理者（Abstract Handler）
  这是一个包含所有具体处理者共有方法的抽象类或接口。它通常包含至少两个主要的方法：
  - handle()：用于接收请求，并决定如何处理它。
  - setNext()：用于设置下一个处理者，建立责任链。

- 具体处理者（Concrete Handler）
  这些是抽象处理者的子类，实现了具体的处理逻辑。每个具体处理者都知道它能够处理哪些请求，以及如何处理这些请求。
- 客户端（Client）
  客户端是创建责任链和发送请求的代码部分。客户端通常不知道哪个具体处理者会最终处理请求，它只需要将请求发送到链的开始即可。

## 2. 应用场景

> 设计模式的应用场景也可以理解为它解决了什么软件设计问题

### 解耦请求的发送者和接收者

- **案例**：一个社交平台需要对用户发布的内容进行审核，审核流程包括敏感词过滤、违规内容识别等多个步骤。用户在社交平台发布内容时，只需要将内容提交给审核链的第一个处理器，无需关心后续的审核步骤。如果需要新增一个审核步骤，只需要添加一个新的处理器类并将其添加到责任链即可。

### 增强系统的灵活性和可扩展性

- **案例**：电商交易系统中的订单状态处理。订单可能会经历“已支付”、“已发货”、“已收货”等状态，每个状态的处理都可以看作是责任链中的一个环节。使用责任链模式可以将不同状态的处理分离到各自的处理器中，增强代码的灵活性和可维护性。

### 简化对象的相互连接

- **案例**：设计一个缓存模块。通过将缓存的不同功能（如存储机制、过期淘汰策略等）抽象成接口，然后将这些部分组合成一个缓存器，基于配置来决定使用哪个部件，简化了对象之间的连接。

### 动态指定处理者

- **案例**：OA流程审批。用户在提交审批时，可以根据实际情况选择审批人，增加审批步骤，审批流程更加灵活，同时也避免了在代码中硬编码各个审批处理者的关系。

### 支持多个处理者处理同一请求

- **案例**：客服支持系统。客户提交的问题根据其优先级需要经过不同层级的客服人员处理，使用责任链模式来处理这个场景，使得问题可以逐级上报，直到找到合适的处理者。

### 提高代码的可维护性

- **案例**：责任链模式重构复杂业务场景。通过责任链模式，可以将复杂的业务逻辑分解成多个独立的处理器，每个处理器只关注自己的业务逻辑，提高了代码的可维护性。

## 3. 场景案例

在我们公司中，员工请假需要经过不同级别的领导审批。根据请假天数的不同，审批权限也会不同。例如，请假1-2天由经理审批，3-6天由经理+HR审批，超过6天则需要经理+HR+总经理审批。

逐级审批，如果下级拒绝了请假，也就不需要上级再处理了。

## 4. 代码一把梭

如果不使用责任链模式，每个请假请求都需要在代码中硬编码判断逻辑，确定由哪个领导审批。

**请假请求**

```java
/**
 * 请假请求
 */
public class LeaveRequest {
    public String reason;
    public int days;

    public LeaveRequest(int days, String reason) {
        this.reason = reason;
        this.days = days;
    }
}

```

**请求处理类**

```java
public class RequestHandler {
    public void handle(LeaveRequest request) {
        int leaveDays = request.getDays();
        String reason = request.getReason();
        if (leaveDays <= 2) {
            managerHandle(leaveDays, reason);
        } else if (leaveDays < 7) {
            boolean managerPass = managerHandle(leaveDays, reason);
            // 不需要上级继续审批
            if (!managerPass) {
                return;
            }
            hrHandle(leaveDays, reason);
        } else {
            boolean managerPass = managerHandle(leaveDays, reason);
            if (!managerPass) {
                return;
            }
            boolean hrPass = hrHandle(leaveDays, reason);
            if (!hrPass) {
                return;
            }
            CEOHandle(leaveDays, reason);
        }
    }

    private boolean managerHandle(int leaveDays, String reason) {
        if (Objects.equals(reason, "钓鱼去")) {
            System.out.println("经理拒绝了你的请假: "+ leaveDays+" 天，原因：摸鱼更香。");
            return false;
        }
        System.out.println("经理批准您请假" + leaveDays + "天。");
        return true;
    }

    private boolean hrHandle(int leaveDays, String reason) {
        System.out.println("HR批准您请假" + leaveDays + "天。");
        return true;
    }

    private boolean CEOHandle(int leaveDays, String reason) {
        System.out.println("总经理批准您请假" + leaveDays + "天。");
        return true;
    }
}
```

**测试结果**

```
经理批准您请假2天。
-------------------------
经理拒绝了你的请假: 4 天，原因：摸鱼更香。
-------------------------
经理批准您请假4天。
HR批准您请假4天。
-------------------------
经理批准您请假7天。
HR批准您请假7天。
总经理批准您请假7天。
```

## 5. 设计模式重构代码

责任链模式可以让各个服务模块更加清晰，而每一个模块间可以通过`next`的方式进行获取。而每一个`next`是由继承的统一抽象类实现的。最终所有类的职责可以动态的进行编排使用，编排的过程可以做成可配置化

### 5.1 定义抽象处理者

```java
public abstract class AbstractApprover {
    private  AbstractApprover nextApprover;

    public AbstractApprover next() {
        return nextApprover;
    }

    public AbstractApprover appendNext(AbstractApprover next) {
        this.nextApprover = next;
        return this;
    }

    public abstract void handle(LeaveRequest leaveRequest);
}
```

抽象处理者类是整个处理链路的核心，它使用`appendNext()`链接下一个审核节点，使用`next()`获取下一个节点。

抽象方法`handle()`，这是每一个实现者必须实现的类，不同的审核人在自己的实现类中实现自己的处理逻辑。

### 5.2 经理审批

```java
public class ManagerApprover extends AbstractApprover{
    @Override
    public void handle(LeaveRequest leaveRequest) {
        if (Objects.equals(leaveRequest.getReason(), "钓鱼去")) {
            System.out.println("经理拒绝了你的请假: "+ leaveRequest.getDays()+" 天，原因：摸鱼更香。");
            // 经理拒绝了请假，不需要继续审批
            return;
        }
        System.out.println("经理批准您请假" + leaveRequest.getDays() + "天。");
        AbstractApprover next = next();
        if (next == null) {
            return;
        }
        next.handle(leaveRequest);
    }
}
```

在处理链路中，如果不再需要后续节点处理逻辑，可以选择中断链路传递。

### 5.3 HR审批

```java
public class HRApprover extends AbstractApprover{
    @Override
    public void handle(LeaveRequest leaveRequest) {
        if (leaveRequest.getDays() >= 3 ) {
            System.out.println("HR批准您请假" + leaveRequest.getDays() + "天。");
        }
        AbstractApprover next = next();
        if (next == null) {
            return;
        }
        next.handle(leaveRequest);
    }
}
```

### 5.4 CEO审批

```java
public class CEOApprover extends AbstractApprover{
    @Override
    public void handle(LeaveRequest leaveRequest) {
        if (leaveRequest.getDays() >= 7 ) {
            System.out.println("CEO批准您请假" + leaveRequest.getDays() + "天。");
        }
        AbstractApprover next = next();
        if (next == null) {
            return;
        }
        next.handle(leaveRequest);
    }
}
```

### 5.5 测试

```java
@Test
public void pattern() {
    LeaveRequest leaveRequest1 = new LeaveRequest(2, "不想上班");
    LeaveRequest leaveRequest2 = new LeaveRequest(4, "钓鱼去");
    LeaveRequest leaveRequest3 = new LeaveRequest(4, "川西四日游");
    LeaveRequest leaveRequest4 = new LeaveRequest(7, "七日出国跟团游");

    AbstractApprover approver = new ManagerApprover().appendNext(new HRApprover().appendNext(new CEOApprover()));
    approver.handle(leaveRequest1);
    System.out.println("-------------------------");
    approver.handle(leaveRequest2);
    System.out.println("-------------------------");
    approver.handle(leaveRequest3);
    System.out.println("-------------------------");
    approver.handle(leaveRequest4);
}
```

测试结果同上`代码一把梭`。

使用设计模式优化以后，代码变的更加美观了，并且可以继承`AbstractApprover`添加新的审核者，灵活扩展；我们也可以动态建立审核者链路，这在复杂的审批流程代码设计中值得使用。

> 本章节案例源码地址：https://github.com/wangyong5609/design-patterns

## 6. 优缺点

> 设计模式不是银弹，应该根据实际情况和需求来选择和应用设计模式

✅你可以控制请求处理的顺序

✅*单一职责原则*,你可对发起操作和执行操作的类进行解耦

✅*开闭原则*，你可以在不更改现有代码的情况下在程序中新增处理者

❌如果链过长，请求可能会逐个通过多个处理者，这可能导致性能问题

❌可能引起循环调用，导致系统崩溃

❌不能保证请求一定会被处理，如果链中的处理者都不能处理请求，可能会导致请求被忽略

## 参考文献

[refactoringguru.cn](https://refactoringguru.cn/design-patterns/chain-of-responsibility)

[重学 Java 设计模式：实战责任链模式](https://bugstack.cn/md/develop/design-pattern/2020-06-18-%E9%87%8D%E5%AD%A6%20Java%20%E8%AE%BE%E8%AE%A1%E6%A8%A1%E5%BC%8F%E3%80%8A%E5%AE%9E%E6%88%98%E8%B4%A3%E4%BB%BB%E9%93%BE%E6%A8%A1%E5%BC%8F%E3%80%8B.html)

