package de.uhh;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Command(
        name = "server",
        showDefaultValues = true
)
public class RendezvousServer implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(RendezvousServer.class);
    @Option(
            names = { "--bind-host" },
            defaultValue = "127.0.0.1"
    )
    protected String bindHost;
    @Option(
            names = { "--bind-port" },
            defaultValue = "8012"
    )
    protected int bindPort;

    @Override
    public void run() {
        final EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            final Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();
                            p.addLast(new TlsHolePunchingCodec());
                            p.addLast(new RendezvousServerHandler());
                        }
                    });
            final Channel ch = b.bind(bindHost, bindPort).syncUninterruptibly().channel();

            // print configuration
            System.out.println(RendezvousServer.class.getSimpleName() + " listening on `" + ch.localAddress() + "`.");

            ch.closeFuture().awaitUninterruptibly();
        }
        finally {
            group.shutdownGracefully();
        }
    }

    private static class RendezvousServerHandler extends SimpleChannelInboundHandler<AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress>> {
        public final Map<Character, InetSocketAddress> clients = new HashMap<>();

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx,
                                    final AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress> msg) {
            LOG.trace("Got `{}` from `{}`.", msg.content(), msg.sender());
            if (msg.content() instanceof Register) {
                LOG.trace("Got registration for peer `{}` from endpoint `{}`.", ((Register) msg.content()).getPeerId(), msg.sender());
                clients.put(((Register) msg.content()).getPeerId(), msg.sender());
            }
            else if (msg.content() instanceof ConnectionRequest) {
                if (clients.containsKey('a') && clients.containsKey('b')) {
                    LOG.trace("Send endpoints to both clients.");
                    ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new ForwardEndpoints(clients.get('a')), clients.get('b')));
                    ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new ForwardEndpoints(clients.get('b')), clients.get('a')));
                }
                else {
                    LOG.trace("Unable to send endpoints as not both clients are registered.");
                }
            }
            else if (msg.content() instanceof QuicMessage) {
                if (Objects.equals(msg.sender(), clients.get('a')) && clients.containsKey('b')) {
                    LOG.trace("Redirect QUIC message from `a` to `b`: `{}`", msg.content());
                    ctx.writeAndFlush(new DefaultAddressedEnvelope<>(msg.content(), clients.get('b')));
                }
                else if (Objects.equals(msg.sender(), clients.get('b')) && clients.containsKey('a')) {
                    LOG.trace("Redirect QUIC message from `b` to `a` `{}`", msg.content());
                    ctx.writeAndFlush(new DefaultAddressedEnvelope<>(msg.content(), clients.get('a')));
                }
                else {
                    LOG.trace("Got unroutable QUIC message `{}`.", msg);
                }
            }
            else {
                LOG.error("Unexpected message: {}", msg);
            }
        }
    }
}
