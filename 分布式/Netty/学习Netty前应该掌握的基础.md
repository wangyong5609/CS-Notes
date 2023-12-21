网路 IO 中阻塞、非阻塞、异步、同步这几个术语的含义和关系：

* 阻塞：如果线程调用 read/write 过程，但 read/write 过程没有就绪或没有完成，则调用 read/write 过程的线程会一直等待，这个过程叫做阻塞式读写。
* 非阻塞：如果线程调用 read/write 过程，但 read/write 过程没有就绪或没有完成，调用 read/write 过程的线程并不会一直等待，而是去处理其他工作，等到 read/write 过程就绪或完成后再回来处理，这个过程叫做非阻塞式读写。
* 异步：read/write 过程托管给操作系统来完成，完成后操作系统会通知（通过回调或者事件）应用网络 IO 程序（其中的线程）来进行后续的处理。
* 同步：read/write 过程由网络 IO 程序（其中的线程）来完成



**NIO**

NIO 以缓冲区（也被叫做块）的方式处理数据

![img](学习Netty前应该掌握的基础.assets/c5ncwho7v8.png)

关于上图，再进行几点说明：

- 一个 Selector 对应一个处理线程
- 一个 Selector 上可以注册多个 Channel
- 每个 Channel 都会对应一个 Buffer（有时候一个 Channel 可以使用多个 Buffer，这时候程序要进行多个 Buffer 的分散和聚集操作），Buffer 的本质是一个内存块，底层是一个数组
- Selector 会根据不同的事件在各个 Channel 上切换
- Buffer 是双向的，既可以读也可以写，切换读写方向要调用 Buffer 的 flip()方法
- 同样，Channel 也是双向的，数据既可以流入也可以流出





```java
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class NioTest {
    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        Selector selector = Selector.open();
        
        // 绑定端口
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        
        // 设置非阻塞模式
        serverSocketChannel.configureBlocking(false);
        
        // 注册serverSocketChannel 到 selector 中，关注OP_ACCEPT事件
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        while (true) {
            // 等待一秒，没有事件发生
            if (selector.select(1000) == 0) {
                continue;
            }
            // 有事件发生， 找到发生事件的Channel对应的selection key集合
            Set<SelectionKey> selectionKeys = selector.selectedKeys();

            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                // 处理连接事件
                if (selectionKey.isAcceptable()) {
                    // 接受该通道的套接字连接
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    // 将socketChannel 也注册到selector，关注读事件，并给socketChannel关联buffer
                    socketChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(1024));
                }
                
                // 发生OP_READ事件，读取客户端数据
                if (selectionKey.isReadable()) {
                    SocketChannel channel = (SocketChannel) selectionKey.channel();
                    ByteBuffer buffer = (ByteBuffer) selectionKey.attachment();
                    channel.read(buffer);
                    System.out.println("msg form client: " + new String(buffer.array()));
                }
                // 防止重复处理事件
                iterator.remove();
            }
        }
    }
}
```

在上面的使用 Java NIO 编写的服务端示例代码中，服务端的工作流程为：

1. 当客户端发起连接时，会通过 ServerSocketChannel 创建对应的 SocketChannel。
2. 调用 SocketChannel 的注册方法将 SocketChannel 注册到 Selector 上，注册方法返回一个 SelectionKey，该 SelectionKey 会被放入 Selector 内部的 SelectionKey 集合中。该 SelectionKey 和 Selector 关联（即通过 SelectionKey 可以找到对应的Selector），也和 SocketChannel 关联（即通过 SelectionKey 可以找到对应的 SocketChannel）。
3. Selector 会调用 select()/select(timeout)/selectNow()方法对内部的 SelectionKey 集合关联的 SocketChannel 集合进行监听，找到有事件发生的 SocketChannel 对应的 SelectionKey。
4. 通过 SelectionKey 找到有事件发生的 SocketChannel，完成数据处理



[45 张图深度解析 Netty 架构与原理](https://cloud.tencent.com/developer/article/1754078)
