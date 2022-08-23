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
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Command(
        name = "passive-client",
        showDefaultValues = true
)
@SuppressWarnings("java:S106")
public class PassiveClientCommand implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(PassiveClientCommand.class);
    public static final int IDLE_RESET_TIMEOUT = 10; // seconds
    @Option(
            names = { "--own-id" },
            defaultValue = "b"
    )
    protected Character ownId;
    @Option(
            names = { "--bind-host" },
            defaultValue = "0.0.0.0"
    )
    protected String bindHost;
    @Option(
            names = { "--bind-port" },
            defaultValue = "8011"
    )
    protected int bindPort;
    @Option(
            names = { "--server-host" },
            defaultValue = "127.0.0.1"
    )
    protected String serverHost;
    @Option(
            names = { "--server-port" },
            defaultValue = "8012"
    )
    protected int serverPort;
    private InetSocketAddress serverEndpoint;
    @Option(
            names = { "--target-host" },
            defaultValue = "127.0.0.1"
    )
    protected String targetHost;
    @Option(
            names = { "--target-port" },
            defaultValue = "4433"
    )
    protected int targetPort;
    private InetSocketAddress targetEndpoint;

    @Override
    public void run() {
        serverEndpoint = new InetSocketAddress(serverHost, serverPort);
        targetEndpoint = new InetSocketAddress(targetHost, targetPort);

        final EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            final Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();
                            p.addLast(new IdleStateHandler(0, 0, IDLE_RESET_TIMEOUT));
                            p.addLast(new TlsHolePunchingCodec());
                            p.addLast(new PassiveClientHandler(PassiveClientCommand.this));
                        }
                    });
            final Channel ch = b.bind(bindHost, bindPort).syncUninterruptibly().channel();

            // print configuration
            System.out.println(PassiveClientCommand.class.getSimpleName() + " has id `" + ownId + "`.");
            System.out.println(PassiveClientCommand.class.getSimpleName() + " listening on `" + ch.localAddress() + "`.");
            System.out.println(PassiveClientCommand.class.getSimpleName() + " will contact server at `" + serverEndpoint + "`.");

            ch.closeFuture().awaitUninterruptibly();
        }
        finally {
            group.shutdownGracefully();
        }
    }

    private static class PassiveClientHandler extends SimpleChannelInboundHandler<AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress>> {
        private final PassiveClientCommand client;
        private InetSocketAddress otherClientEndpoint;

        private enum State {
            INITIALIZED,
            CHECKING, // performing reachability checks
            ACKNOWLEDGED // got acknowledgement
        }

        private State state = State.INITIALIZED;
        private List<AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress>> pendingWrites = new ArrayList<>();

        public PassiveClientHandler(final PassiveClientCommand client) {
            this.client = client;
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            ctx.fireChannelActive();

            ctx.executor().scheduleWithFixedDelay(() -> {
                LOG.trace("Register at rendezvous server `{}`.", client.serverEndpoint);
                ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new Register(client.ownId), client.serverEndpoint));
            }, 0, 30_000, MILLISECONDS);
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx,
                                       final Object evt) {
            if (evt instanceof IdleStateEvent) {
                if (state != State.INITIALIZED || !pendingWrites.isEmpty()) {
                    LOG.info("No read for {}s. Reset state.", IDLE_RESET_TIMEOUT);
                }
                state = State.INITIALIZED;
                otherClientEndpoint = null;
                pendingWrites.clear();
            }
            else {
                ctx.fireUserEventTriggered(evt);
            }
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx,
                                    final AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress> msg) {
            LOG.trace("Got `{}` from `{}`.", msg.content(), msg.sender());
            switch (state) {
                case INITIALIZED:
                    if (client.serverEndpoint.equals(msg.sender())) {
                        // from server
                        if (msg.content() instanceof ForwardEndpoints) {
                            state = State.CHECKING;
                            otherClientEndpoint = ((ForwardEndpoints) msg.content()).getEndpoint();
                            pendingWrites.clear();
                            LOG.trace("Got endpoints. Perform reachability checks to `{}`.", otherClientEndpoint);
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new ReachabilityCheck(), otherClientEndpoint));
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    else if (Objects.equals(otherClientEndpoint, msg.sender())) {
                        // from other client
                        LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                    }
                    else if (client.targetEndpoint.equals(msg.sender())) {
                        // from QUIC server
                        LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                    }
                    else {
                        LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                    }
                    break;
                case CHECKING:
                    if (client.serverEndpoint.equals(msg.sender())) {
                        // from server
                        if (msg.content() instanceof QuicMessage) {
                            LOG.trace("Redirect {} to QUIC server {}.", msg, client.targetEndpoint);
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(msg.content(), client.targetEndpoint));
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    else if (Objects.equals(otherClientEndpoint, msg.sender())) {
                        // from other client
                        if (msg.content() instanceof ReachabilityCheck) {
                            LOG.trace("Confirm check.");
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new Acknowledgement(), otherClientEndpoint));
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new ReachabilityCheck(), otherClientEndpoint));
                        }
                        else if (msg.content() instanceof Acknowledgement) {
                            state = State.ACKNOWLEDGED;
                            LOG.trace("Got acknowledgement. Flush {} pending write(s)!.", pendingWrites.size());
                            for (AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress> pendingWrite : pendingWrites) {
                                LOG.trace("Redirect {} to PassiveClient {}.", pendingWrite, otherClientEndpoint);
                                ctx.writeAndFlush(new DefaultAddressedEnvelope<>(pendingWrite.content(), otherClientEndpoint));
                            }
                            pendingWrites.clear();
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    else if (client.targetEndpoint.equals(msg.sender())) {
                        // from QUIC server
                        if (msg.content() instanceof QuicMessage) {
                            LOG.trace("Redirect {} to ActiveClient {}.", msg, otherClientEndpoint);
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(msg.content(), otherClientEndpoint));
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    else {
                        LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                    }
                    break;
                case ACKNOWLEDGED:
                    if (client.serverEndpoint.equals(msg.sender())) {
                        // from server
                        if (msg.content() instanceof ForwardEndpoints) {
                            state = State.CHECKING;
                            otherClientEndpoint = ((ForwardEndpoints) msg.content()).getEndpoint();
                            pendingWrites.clear();
                            LOG.trace("New connection? Got endpoints. Perform reachability checks to `{}`.", otherClientEndpoint);
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new ReachabilityCheck(), otherClientEndpoint));
                        }
                        else if (msg.content() instanceof QuicMessage) {
                            LOG.trace("Redirect {} to QUIC server {}.", msg, client.targetEndpoint);
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(msg.content(), client.targetEndpoint));
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    else if (Objects.equals(otherClientEndpoint, msg.sender())) {
                        // from other client
                        if (msg.content() instanceof ReachabilityCheck) {
                            LOG.trace("Confirm check.");
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new Acknowledgement(), otherClientEndpoint));
                        }
                        else if (msg.content() instanceof Acknowledgement) {
                            // ignore
                        }
                        else if (msg.content() instanceof QuicMessage) {
                            LOG.trace("Redirect {} to QUIC server {}.", msg, client.targetEndpoint);
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(msg.content(), client.targetEndpoint));
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    else if (client.targetEndpoint.equals(msg.sender())) {
                        // from QUIC server
                        LOG.trace("Redirect {} to ActiveClient {}.", msg, otherClientEndpoint);
                        ctx.writeAndFlush(new DefaultAddressedEnvelope<>(msg.content(), otherClientEndpoint));
                    }
                    else {
                        LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                    }
                    break;
            }
        }
    }
}
