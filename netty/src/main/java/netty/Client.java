package netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

/**
 * <p></p>
 *
 * @author PengCheng
 * @date 2019/2/8
 */
public class Client implements Runnable{


    @Override
    public void run() {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ClientInitializer());
                    //该处关闭Nagle算法也可实现解决粘包现象
//                    .option(ChannelOption.TCP_NODELAY, true);
            Channel ch = bootstrap.connect("127.0.0.1",6379).sync().channel();
            ch.writeAndFlush("hello service!"+Thread.currentThread().getName()+"\r\n");
            ch.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }


    public static void main(String[] args) {
        for (int i = 0;i<3;i++){
            new Thread(new Client(),">>> this thread "+ i).start();
        }
    }
}
