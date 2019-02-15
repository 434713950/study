package netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;

/**
 * <p></p>
 *
 * @author PengCheng
 * @date 2019/2/7
 */
public class Server {

    private static final int BIZGROUPSIZE = Runtime.getRuntime().availableProcessors() * 2;

    private static final int BIZTHREADSIZE = 100;

    /**
     * boss线程组，用于服务端接受客户端的连接
     */
    private static final EventLoopGroup boosGroup = new NioEventLoopGroup(BIZGROUPSIZE);

    /**
     * worker线程组，用于进行SocketChannel的数据读写
     */
    private static final EventLoopGroup workGroup = new NioEventLoopGroup(BIZTHREADSIZE);

    public static void start(int port){
        try {
            //创建serverBootstrap对象
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            //设置使用的EventLoopGroup
            serverBootstrap.group(boosGroup, workGroup)
                    //设置要被实例化的类
                    .channel(NioServerSocketChannel.class)
                    //设置NioServerSocketChannel的处理器
                    .handler(new LoggingHandler(LogLevel.INFO))
                    //设置连入服务端的Client的SocketChannel的处理器
                    .childHandler(new ServerInitializer());
            //绑定端口并同步等待成功，即启动服务端
            ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
            System.out.println("server start");

            //监听服务端关闭，并阻塞等待
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e){
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    protected static void shutdown(){
        workGroup.shutdownGracefully();
        boosGroup.shutdownGracefully();
    }

    public static void main(String[] args){
        Server.start(6379);
    }

}
