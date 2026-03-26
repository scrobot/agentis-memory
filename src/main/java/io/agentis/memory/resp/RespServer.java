package io.agentis.memory.resp;

import io.agentis.memory.command.CommandRouter;
import io.agentis.memory.config.ServerConfig;
import io.agentis.memory.persistence.AofWriter;
import io.agentis.memory.persistence.SnapshotWriter;
import io.agentis.memory.store.KvStore;
import io.agentis.memory.vector.VectorEngine;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * Netty TCP server: bind, pipeline setup.
 * Uses io_uring on Linux (epoll fallback) and kqueue on macOS.
 */
public class RespServer {

    private final ServerConfig config;
    private final CommandRouter router;
    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public RespServer(ServerConfig config, KvStore kvStore, VectorEngine vectorEngine,
                      AofWriter aofWriter, SnapshotWriter snapshotWriter) {
        this.config = config;
        this.router = new CommandRouter(config, kvStore, vectorEngine, aofWriter, snapshotWriter);
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

        System.out.println("Agentis Memory listening on " + config.bind + ":" + config.port);
        serverChannel.closeFuture().sync();
    }

    public void shutdown() {
        if (serverChannel != null) serverChannel.close();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }
}
