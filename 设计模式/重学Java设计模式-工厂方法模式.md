# 重学Java设计模式-工厂方法模式

## 介绍

工厂方法模式（Factory Method Pattern）是一种创建型设计模式，提供了一种方法用于创建对象，但允许子类决定实例化哪一个类。工厂方法模式将类的实例化推迟到子类中。它主要解决了将对象的创建和使用解耦的问题



## 场景

在我们项目中，有多种不同类型的优惠券，在添加优惠券时，需要进行有效性验证。



## 简单实现

```java
public void shit() {
    CouponDTO couponDTO = new CouponDTO();
    couponDTO.setCouponType("01");
    if ("01".equals(couponDTO.getCouponType())) {
        log.info("优惠券验证，类型：01");
    } else if ("02".equals(couponDTO.getCouponType())) {
        log.info("优惠券验证，类型：02");
    } else if ("03".equals(couponDTO.getCouponType())) {
        log.info("优惠券验证，类型：03");
    }
}
```

使用简单的 if/else 嵌套 的确可以实现需求，在开发初期要理解这块代码也很容易；但是随着项目的发展，Coupon 引入新的复杂的验证规则，新的Coupon类型加入，不同开发人员的经手，这段代码将变得难以维护。



## 工厂模式实现

### 结构

```
└── src
    ├── main
    │   └── java
    │       └── com
    │           └── bbbwdc
    │               └── factory
    │                   ├── CouponDTO.java
    │                   ├── ValidationStrategyFactory.java
    │                   └── validation
    │                       ├── Coupon01ValidationStrategy.java
    │                       ├── Coupon02ValidationStrategy.java
    │                       ├── Coupon03ValidationStrategy.java
    │                       └── ValidationStrategy.java
    └── test
        └── java
            └── com
                └── bbbwdc
                    └── test
                        └── ApiTest.java
```

### 代码实现

**定义优惠券验证策略接口**

```java
/**
 * 验证策略接口
 */
public interface ValidationStrategy {

    void validate(CouponDTO couponDTO);
}
```

在接口中定义了基本的验证方法和要验证的实体类

**多种验证策略的具体实现**

为不同类型的优惠券实现验证逻辑，如果新增了优惠券类型，则新增`ValidationStrategy`的实现类即可，更易于维护。

验证”01“类型的优惠券

```java
public class Coupon01ValidationStrategy implements ValidationStrategy {
    @Override
    public void validate(CouponDTO couponDTO) {
        log.info("优惠券验证，类型：01");
    }
}
```

验证”02“类型的优惠券

```java
public class Coupon02ValidationStrategy implements ValidationStrategy {
    @Override
    public void validate(CouponDTO couponDTO) {
        log.info("优惠券验证，类型：02");
    }
}
```

验证”03“类型的优惠券

```java
public class Coupon03ValidationStrategy implements ValidationStrategy {
    @Override
    public void validate(CouponDTO couponDTO) {
        log.info("优惠券验证，类型：03");
    }
}
```

**定义工厂类**

```java
/**
 * 验证策略工厂
 */
public class ValidationStrategyFactory {
    public static ValidationStrategy createValidationStrategy(String couponTypeCode) { 
        if ("01".equals(couponTypeCode)) {
            return new Coupon01ValidationStrategy();
        } else if ("02".equals(couponTypeCode)) {
            return new Coupon02ValidationStrategy();
        } else if ("03".equals(couponTypeCode)) {
            return new Coupon03ValidationStrategy();
        }else {
            throw new IllegalArgumentException("Unsupported coupon type: " + couponTypeCode);
        }
    }
}
```

根据优惠券类型，返回验证策略处理类

### 测试

```java
@Test
public void testValidation() {
    CouponDTO couponDTO1 = new CouponDTO();
    couponDTO1.setCouponType("01");
    ValidationStrategy validationStrategy = ValidationStrategyFactory.createValidationStrategy(couponDTO1.getCouponType());
    validationStrategy.validate(couponDTO1);

    CouponDTO couponDTO2 = new CouponDTO();
    couponDTO2.setCouponType("02");
    ValidationStrategy validationStrategy2 = ValidationStrategyFactory.createValidationStrategy(couponDTO2.getCouponType());
    validationStrategy2.validate(couponDTO2);

    CouponDTO couponDTO3 = new CouponDTO();
    couponDTO3.setCouponType("03");
    ValidationStrategy validationStrategy3 = ValidationStrategyFactory.createValidationStrategy(couponDTO3.getCouponType());
    validationStrategy3.validate(couponDTO3);
}
```

测试结果如下：

```
十月 24, 2024 1:57:34 下午 com.bbbwdc.factory.validation.Coupon01ValidationStrategy validate
信息: 优惠券验证，类型：01
十月 24, 2024 1:57:34 下午 com.bbbwdc.factory.validation.Coupon02ValidationStrategy validate
信息: 优惠券验证，类型：02
十月 24, 2024 1:57:34 下午 com.bbbwdc.factory.validation.Coupon03ValidationStrategy validate
信息: 优惠券验证，类型：03
```



## 总结

我认为在业务中工厂方法适用于根据某个业务实体的某种类型值进行相应逻辑处理的场景。

比如支付网关集成，在电子商务平台中，可能需要支持多种支付方式（如支付宝、微信支付、PayPal等）。每种支付方式的实现可能不同，但它们遵循相同的接口。



源码地址：https://github.com/wangyong5609/design-patterns/tree/main/factory