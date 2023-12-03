# MongoDB入门

## NoSQL

**什么是 NoSQL**

NoSQL，指的是非关系型的数据库。NoSQL有时也称作Not Only SQL的缩写，是对不同于传统的关系型数据库的数据库管理系统的统称。

NoSQL用于超大规模数据的存储。（例如谷歌或Facebook每天为他们的用户收集万亿比特的数据）。这些类型的数据存储不需要固定的模式，无需多余操作就可以横向扩展。

**为什么使用NoSQL**

今天我们可以通过第三方平台（如：Google,Facebook等）可以很容易的访问和抓取数据。用户的个人信息，社交网络，地理位置，用户生成的数据和用户操作日志已经成倍的增加。我们如果要对这些用户数据进行挖掘，那SQL数据库已经不适合这些应用了, NoSQL 数据库的发展却能很好的处理这些大的数据。

### NoSQL的优缺点

**优点**

- **可扩展性**：NoSQL数据库通常提供了更好的水平扩展性，适合大数据和高流量的应用。
- **灵活的数据模型**：NoSQL数据库允许存储非结构化和半结构化数据，提供更灵活的数据模型，易于应对数据模式的变化。
- **高性能**：针对特定类型的查询和操作，NoSQL数据库往往比关系型数据库更高效。
- **易于部署**：NoSQL数据库易于设置和管理，特别是在云计算环境中。
- **分布式计算**：NoSQL数据库设计用于分布式数据存储和计算，很适合分布式系统的需求。
- **成本效益**：许多NoSQL数据库是开源的，减少了使用成本，并且它们通常能够在廉价硬件上运行。
- **适合大数据应用**：NoSQL数据库非常适合于大规模数据存储和实时的大数据分析

**缺点**

- **事务处理**：大多数NoSQL数据库不支持传统关系型数据库中的ACID（原子性、一致性、隔离性、持久性）事务。
- **一致性模型**：NoSQL数据库通常使用最终一致性模型，而不是严格的ACID一致性，这可能不适用于所有类型的应用。
- **标准化缺乏**：相比于SQL，NoSQL没有统一的查询语言和标准，这可能导致学习曲线陡峭和开发复杂度增加。
- **数据完整性和安全性**：关系型数据库提供了更复杂的数据完整性和安全性特性，这在某些NoSQL数据库中可能缺乏。
- **分析和报告**：对于复杂的数据分析和报告需求，传统的关系型数据库通常提供更强大的支持。
- **成熟度和生态系统**：相比较而言，关系型数据库拥有更成熟的技术和更广泛的生态系统。
- **管理和维护**：尽管部署相对简单，但大规模NoSQL系统的管理和维护可能比关系型数据库复杂

### RDBMS vs NoSQL

**RDBMS**

- **数据模型**：RDBMS使用严格的表结构，数据以行和列的形式存储。这要求数据模式（Schema）在数据插入前就被定义。
- **事务处理**：RDBMS通常提供完整的ACID事务支持，适合需要强一致性和数据完整性的应用。
- **标准化查询语言**：SQL（结构化查询语言）是RDBMS的标准，允许执行复杂的查询和数据操作。
- **可扩展性**：传统上，RDBMS更倾向于垂直扩展（升级现有硬件）。
- **数据一致性和完整性**：提供强数据一致性、外键约束和数据完整性功能。
- **适用场景**：复杂的事务系统、银行和财务系统、ERP系统等。

**NoSQL**

- **数据模型**：NoSQL数据库支持灵活的数据模型，包括键值存储、文档存储、宽列存储和图形数据库等。不需要预先定义数据模式。
- **事务处理**：大多数NoSQL数据库提供有限的事务支持，一些数据库仅支持单个文档/行的原子操作。
- **查询语言**：NoSQL数据库没有统一的查询语言，不同的数据库有各自的查询方法和API。
- **可扩展性**：NoSQL数据库设计用于水平扩展，可以通过增加更多服务器来提升性能和存储能力。
- **数据一致性**：许多NoSQL数据库采用最终一致性模型，而非严格的ACID原则。
- **适用场景**：大数据应用、实时Web应用、内容管理系统、大规模的在线服务等

## MongoDB基础

### 什么是MongoDB

[MongoDB](https://www.mongodb.com/docs/manual/) 是由C++语言编写的，是一个基于分布式文件存储的开源数据库系统。

- 在高负载的情况下，添加更多的节点，可以保证服务器性能。
- MongoDB 旨在为WEB应用提供可扩展的高性能数据存储解决方案。

### 存储结构

MongoDB 将数据存储为一个文档，数据结构由键值(key=>value)对组成。MongoDB 文档类似于JSON 对象。字段值可以包含其他文档，数组及文档数组。

![image-20231203195622192](./MongoDB%E5%85%A5%E9%97%A8.assets/image-20231203195622192.png)

### 主要特点

- 非关系型数据库，基于 Document data model (文档数据模型)
- MongoDB以 BSON **(BinaryJSON)** 格式存储数据，类似于 JSON 数据形式
- 关系型数据库使用 table (tables of rows)形式存储数据，而MongoDB使用 collections(collections of documents)
- 支持 **临时查询**( ad hoc queries ): 系统不用提前定义可以接收的查询类型
- 索引通过 B**-**tree 数据结构， 3.2版本的**WiredTiger** 支持 log**-**structured merge**-**trees(LSM)
- 支持索引和次级索引( secondary indexes ): 次级索引是指文档或row有一个 主键( primarykey )作为索引，同时允许文档或row内部还拥有一个索引，提升查询的效率，这也是MongoDB比较大的一个特点

### MongoDB安装

官方提供了不同操作系统的安装文档：https://www.mongodb.com/docs/manual/installation/

> 学习阶段建议使用docker快速安装

## 基本概念

### 和传统数据库对比

| SQL术语/概念 | MongoDB术语/概念 | 解释/说明                           |
| :----------- | :--------------- | :---------------------------------- |
| database     | database         | 数据库                              |
| table        | collection       | 数据库表/集合                       |
| row          | document         | 数据记录行/文档                     |
| column       | field            | 数据字段/域                         |
| index        | index            | 索引                                |
| table joins  |                  | 表连接,MongoDB不支持                |
| primary key  | primary key      | 主键,MongoDB自动将_id字段设置为主键 |

通过下图实例，我们也可以更直观的了解Mongo中的一些概念：

![img](./MongoDB%E5%85%A5%E9%97%A8.assets/Figure-1-Mapping-Table-to-Collection-1.png)

### 数据库

一个mongoDB的实例可以运行多个database，database之间是完全独立的，每个database有自己的权限，每个database存储于磁盘的不同文件。

**命名规范**

数据库名可以是满足以下条件的任意UTF-8字符串

- 不能是空字符串（"")
- 不得含有' '（空格)、.、$、/、\和\0 (空字符)
- 应全部小写
- 最多64字节

**特殊数据库**

- **admin**： 从权限的角度来看，这是"root"数据库。要是将一个用户添加到这个数据库，这个用户自动继承所有数据库的权限。一些特定的服务器端命令也只能从这个数据库运行，比如列出所有的数据库或者关闭服务器。
- **local:** 这个数据永远不会被复制，可以用来存储限于本地单台服务器的任意集合
- **config**: 当Mongo用于分片设置时，config数据库在内部使用，用于保存分片的相关信息

**常用命令**

```sql
# 查看数据库列表
show dbs;
# 显示当前数据库
db;
# 创建或切换数据库,在 MongoDB 中，只有在数据库中插入集合后才会创建! 就是说，创建数据库后要再插入一个集合，数据库才会真正创建。
use testdb;
# 删除数据库
db.dropDatabase();
```

### 集合

相当于关系数据库的表，不过没有数据结构的定义。它由多个document组成

**命名规范**

- 集合名不能是空字符串""。
- 集合名不能含有\0字符（空字符)，这个字符表示集合名的结尾。
- 集合名不能以"system."开头，这是为系统集合保留的前缀。
- 用户创建的集合名字不能含有保留字符。有些驱动程序的确支持在集合名里面包含，这是因为某些系统生成的集合中包含该字符。除非你要访问这种系统创建的集合，否则千万不要在名字里出现$

**常用命令**

```sql
# 在db数据库创建一个blog的集合
db.createCollection("blog");
# 查看集合
show collections;
show tables;
# 删除集合
db.COLLECTION_NAME.drop()
```

### 文档

mongoDB的基本单位，相当于关系数据库中的行，它是一组有序的key/value键值对，使用json格式，如：{"foo" : 3, "greeting": "Hello, world!"}。

**命名规范**

- 能包含\0字符（null字符），它用于标识key的结束
- .和$字符在mangodb中有特殊含义，如$被用于修饰符($inc表示更新修饰符)，应该考虑保留，以免被驱动解析
- 以`_`开始的key也应该保留，比如_id是mangodb中的关键字

**注意事项**

- 在mangodb中key是不能重复的
- value 是弱类型，甚至可以嵌入一个document
- key/value键值对在mangodb中是有序的
- mangodb是类型和大小写敏感的，如{"foo" : 3}和{"foo" : "3"}是两个不同的document，{"foo" :3}和{"Foo" : 3}

**常用命令**

```sql
# insertOne 向文档中写入一个文档
db.COLLECTION_NAME.insertOne()
# insertMany 批量插入
db.COLLECTION_NAME.insertMany()
# 更新文档，MongoDB 使用 update() 和 save() 方法来更新集合中的文档
db.COLLECTION_NAME.update()
# 存在就更新，不存在就插入
db.COLLECTION_NAME.save()
# 删除文档
db.COLLECTION_NAME.remove()
db.COLLECTION_NAME.deleteOne()
db.COLLECTION_NAME.deleteMany()
# 查询文档
db.COLLECTION_NAME.find()
db.COLLECTION_NAME.findOne()
# 格式化文档
db.COLLECTION_NAME.find().pretty()
```

## 总结

MongoDB 入门主要一些基本概念，NoSQL 和传统关系型数据库的区别。跟着[菜鸟教程](https://www.runoob.com/mongodb/mongodb-tutorial.html)敲一遍基本操作命令，命令太多我就懒得在这记录了。

> 手敲一遍才是硬道理



