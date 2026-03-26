package io.agentis.memory.resp;

import io.agentis.memory.command.CommandRouter;
import io.agentis.memory.config.ServerConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty TCP server: bind, pipeline setup.
 */
@Singleton
public class RespServer {
    private static final Logger log = LoggerFactory.getLogger(RespServer.class);

    private final ServerConfig config;
    private final CommandRouter router;
    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    @Inject
    public RespServer(ServerConfig config, CommandRouter router) {
        this.config = config;
        this.router = router;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new RespDecoder())
                                .addLast(new RespEncoder())
                                .addLast(new CommandDispatcher(router));
                    }
                });

        serverChannel = bootstrap
                .bind(config.bind, config.port)
                .sync()
                .channel();

        log.info("Agentis Memory listening on {}:{}", config.bind, config.port);
        // serverChannel.closeFuture().sync(); // Don't block here!
    }

    public void shutdown() {
        if (serverChannel != null) serverChannel.close();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }

    public void waitForShutdown() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }
}
