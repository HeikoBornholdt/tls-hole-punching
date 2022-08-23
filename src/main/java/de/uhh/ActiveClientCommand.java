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
import io.netty.incubator.codec.quic.QuicPacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Command(
        name = "active-client",
        showDefaultValues = true
)
@SuppressWarnings("java:S106")
public class ActiveClientCommand implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ActiveClientCommand.class);
    public static final int IDLE_RESET_TIMEOUT = 10; // seconds
    @Option(
            names = { "--own-id" },
            defaultValue = "a"
    )
    protected Character ownId;
    @Option(
            names = { "--contact-id" },
            defaultValue = "b"
    )
    protected Character contactId;
    @Option(
            names = { "--bind-host" },
            defaultValue = "0.0.0.0"
    )
    protected String bindHost;
    @Option(
            names = { "--bind-port" },
            defaultValue = "8010"
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
    @ArgGroup
    private Mode mode;
    @Option(
            names = { "--logfile" },
            defaultValue = "tls-hole-punching.csv"
    )
    protected String logfile;
    private InetSocketAddress serverEndpoint;
    private InetSocketAddress otherClientEndpoint;
    private InetSocketAddress ownEndpoint;

    @Override
    public void run() {
        serverEndpoint = new InetSocketAddress(serverHost, serverPort);

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
                            p.addLast(new ActiveClientHandler(ActiveClientCommand.this));
                        }
                    });
            final Channel ch = b.bind(bindHost, bindPort).syncUninterruptibly().channel();

            // print configuration
            System.out.println(ActiveClientCommand.class.getSimpleName() + " has id `" + ownId + "`.");
            System.out.println(ActiveClientCommand.class.getSimpleName() + " will contact id `" + contactId + "`.");
            ownEndpoint = (InetSocketAddress) ch.localAddress();
            System.out.println(ActiveClientCommand.class.getSimpleName() + " listening on `" + ownEndpoint + "`.");
            System.out.println(ActiveClientCommand.class.getSimpleName() + " will contact server at `" + serverEndpoint + "`.");

            // wait for programm to finish
            ch.closeFuture().awaitUninterruptibly();
        }
        finally {
            group.shutdownGracefully();
        }
    }

    private static class ActiveClientHandler extends SimpleChannelInboundHandler<AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress>> {
        private long holePunchTime;
        private long holePunchedTime;
        private int holePunchingSentMessages;
        private int holePunchingReceivedMessages;
        private boolean holePunchingAwaitResponse;
        private int holePunchingRtt;
        private long quicHandshakeTime;
        private long quicHandshakedTime;
        private int quicSentMessages;
        private int quicReceivedMessages;
        private boolean quicAwaitResponse;
        private int quicRtt;
        private final ActiveClientCommand client;
        private final QuicMessageHandler quicMessageHandler;
        private long quicLastReceivedMessageTime;
        private boolean completed;
        private boolean doZeroRtt;

        private enum State {
            INITIALIZED,
            REQUESTED, // requested endpoints from server
            CHECKING, // performing reachability checks
            ACKNOWLEDGED // got acknowledgement
        }

        private State state = State.INITIALIZED;
        private InetSocketAddress quicClientEndpoint;

        public ActiveClientHandler(final ActiveClientCommand client) {
            this.client = client;
            if (client.mode == null || client.mode.sequential) {
                LOG.trace("Sequential mode.");
                quicMessageHandler = new SequentialQuicMessageHandler();
            }
            else {
                LOG.trace("Parallel mode.");
                quicMessageHandler = new ParallelQuicMessageHandler();
            }
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
                if (state != State.INITIALIZED || !quicMessageHandler.isEmpty()) {
                    LOG.info("No read for {}s. Reset state.", IDLE_RESET_TIMEOUT);
                }
                state = State.INITIALIZED;
                client.otherClientEndpoint = null;
                quicMessageHandler.clear();
                holePunchTime = 0;
                holePunchedTime = 0;
                holePunchingSentMessages = 0;
                holePunchingReceivedMessages = 0;
                holePunchingAwaitResponse = false;
                holePunchingRtt = 0;
                quicHandshakedTime = 0;
                quicHandshakeTime = 0;
                quicSentMessages = 0;
                quicReceivedMessages = 0;
                quicAwaitResponse = false;
                quicRtt = 0;
                quicLastReceivedMessageTime = 0;
                completed = false;
                doZeroRtt = false;
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
                        LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                    }
                    else if (Objects.equals(client.otherClientEndpoint, msg.sender())) {
                        // from other client
                        LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                    }
                    else {
                        // QUIC client
                        if (msg.content() instanceof QuicMessage) {
                            state = State.REQUESTED;
                            quicClientEndpoint = msg.sender();
                            quicMessageHandler.clear();
                            // enqueue write
                            holePunchTime = System.currentTimeMillis();
                            LOG.debug("Got QUIC connection attempt from {}.", msg.sender());
                            LOG.trace("No direct connection present. Request endpoints from rendezvous server.");
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new ConnectionRequest(), client.serverEndpoint));
                            holePunchingAwaitResponse = true;
                            holePunchingSentMessages++;
                            quicMessageHandler.channelRead(ctx, state, msg, client, ActiveClientHandler.this);
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    break;
                case REQUESTED:
                    if (client.serverEndpoint.equals(msg.sender())) {
                        // from server
                        if (msg.content() instanceof ForwardEndpoints) {
                            state = State.CHECKING;
                            if (holePunchingAwaitResponse) {
                                holePunchingAwaitResponse = false;
                                holePunchingRtt++;
                            }
                            holePunchingReceivedMessages++;
                            client.otherClientEndpoint = ((ForwardEndpoints) msg.content()).getEndpoint();
                            LOG.trace("Got endpoints. Perform reachability checks to `{}`.", client.otherClientEndpoint);
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new ReachabilityCheck(), client.otherClientEndpoint));
                            holePunchingSentMessages++;
                            holePunchingAwaitResponse = true;
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    else if (Objects.equals(client.otherClientEndpoint, msg.sender())) {
                        // from other client
                        LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                    }
                    else {
                        // QUIC client
                        if (msg.content() instanceof QuicMessage) {
                            checkComplete(ctx, msg);
                            LOG.trace("Waiting for endpoints. Enqueue write.");
                            quicMessageHandler.channelRead(ctx, state, msg, client, ActiveClientHandler.this);
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    break;
                case CHECKING:
                    if (client.serverEndpoint.equals(msg.sender())) {
                        // from server
                        LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                    }
                    else if (Objects.equals(client.otherClientEndpoint, msg.sender())) {
                        // from other client
                        if (msg.content() instanceof ReachabilityCheck) {
                            LOG.trace("Confirm check.");
                            holePunchingReceivedMessages++;
                            holePunchingSentMessages++;
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new Acknowledgement(), client.otherClientEndpoint));
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new ReachabilityCheck(), client.otherClientEndpoint));
                        }
                        else if (msg.content() instanceof Acknowledgement) {
                            LOG.trace("Got acknowledgement.");
                            holePunchingReceivedMessages++;
                            if (holePunchingAwaitResponse) {
                                holePunchingAwaitResponse = false;
                                holePunchingRtt++;
                            }
                            state = State.ACKNOWLEDGED;
                            checkComplete(ctx, msg);
                        }
                        else if (msg.content() instanceof QuicMessage) {
                            if (quicHandshakedTime == 0) {
                                if (quicAwaitResponse) {
                                    quicAwaitResponse = false;
                                    quicRtt++;
                                }
                            }
                            quicReceivedMessages++;
                            checkComplete(ctx, msg);
                            LOG.trace("Redirect {} to QUIC client {}.", msg, quicClientEndpoint);
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(msg.content(), quicClientEndpoint));
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    else {
                        // QUIC client
                        if (msg.content() instanceof QuicMessage) {
                            checkComplete(ctx, msg);
                            LOG.trace("Waiting for acknowledgement. Enqueue write.");
                            quicMessageHandler.channelRead(ctx, state, msg, client, ActiveClientHandler.this);
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    break;
                case ACKNOWLEDGED:
                    if (client.serverEndpoint.equals(msg.sender())) {
                        // from server
                        LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                    }
                    else if (Objects.equals(client.otherClientEndpoint, msg.sender())) {
                        // from other client
                        if (msg.content() instanceof ReachabilityCheck) {
                            LOG.trace("Confirm check.");
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(new Acknowledgement(), client.otherClientEndpoint));
                        }
                        else if (msg.content() instanceof Acknowledgement) {
                            // ignore
                        }
                        else if (msg.content() instanceof QuicMessage) {
                            if (quicHandshakedTime == 0) {
                                if (quicAwaitResponse) {
                                    quicAwaitResponse = false;
                                    quicRtt++;
                                }
                            }
                            quicReceivedMessages++;
                            checkComplete(ctx, msg);
                            LOG.trace("Redirect {} to QUIC client {}.", msg, quicClientEndpoint);
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(msg.content(), quicClientEndpoint));
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    else {
                        // from QUIC client
                        if (msg.content() instanceof QuicMessage) {
                            quicSentMessages++;
                            checkComplete(ctx, msg);
                            quicAwaitResponse = true;
                            LOG.trace("Redirect {} to PassiveClient {}.", msg, client.otherClientEndpoint);
                            ctx.writeAndFlush(new DefaultAddressedEnvelope<>(msg.content(), client.otherClientEndpoint));
                        }
                        else {
                            LOG.error("Got unexpected message {} in state {}.", msg.content(), state);
                        }
                    }
                    break;
            }
        }

        private void checkComplete(final ChannelHandlerContext ctx,
                                   final AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress> msg) {
            if (msg.content() instanceof QuicMessage) {
                if (quicHandshakedTime == 0) {
                    final QuicPacketType type = ((QuicMessage) msg.content()).getType();
                    if (type == QuicPacketType.ZERO_RTT) {
                        final long currentTimeMillis = System.currentTimeMillis();
                        quicHandshakeTime = currentTimeMillis;
                        quicHandshakedTime = currentTimeMillis;
                        LOG.debug("0-RTT was sent. QUIC handshake is done.");
                    }
                    else if (type == QuicPacketType.SHORT) {
                        // got first payload, handshake was done in previous message
                        quicHandshakedTime = quicLastReceivedMessageTime;
                        LOG.debug("Got first QUIC payload. QUIC handshake done since previous message.");
                        quicSentMessages -= 1; // remove current message, handshake was done in previous message
                    }
                    quicLastReceivedMessageTime = System.currentTimeMillis();
                }
            }
            else {
                if (holePunchedTime == 0) {
                    if (msg.content() instanceof Acknowledgement) {
                        holePunchedTime = System.currentTimeMillis();
                        LOG.debug("Hole Punching done.");
                        quicMessageHandler.holePunched(ctx, client, ActiveClientHandler.this);
                    }
                }
            }

            if (!completed && quicHandshakedTime != 0 && holePunchedTime != 0) {
                completed = true;
                final long totalTime = Math.max(holePunchedTime, quicHandshakedTime) - holePunchTime;
                final long holePunchingDuration = holePunchedTime - holePunchTime;
                final long quicHandshakeDuration = quicHandshakedTime - quicHandshakeTime;
                final int holePunchingStart = 0;
                System.out.printf("    UDP Hole Punching Start : +%5dms%n", holePunchingStart);
                final long holePunchingEnd = holePunchedTime - holePunchTime;
                System.out.printf("      UDP Hole Punching End : +%5dms%n", holePunchingEnd);
                System.out.printf(" UDP Hole Punching Duration : %6dms (%3.0f%%)%n", holePunchingDuration, (double) holePunchingDuration / totalTime * 100);
                System.out.printf("       UDP Hole Punching TX : %6d%n", holePunchingSentMessages);
                System.out.printf("       UDP Hole Punching RX : %6d%n", holePunchingReceivedMessages);
                System.out.printf("      UDP Hole Punching RTT : %6d%n", holePunchingRtt);
                System.out.printf("%n");
                final long quicHandshakeStart = quicHandshakeTime - holePunchTime;
                System.out.printf("       QUIC Handshake Start : +%5dms%n", quicHandshakeStart);
                final long quicHandshakeEnd = quicHandshakedTime - holePunchTime;
                System.out.printf("         QUIC Handshake End : +%5dms%n", quicHandshakeEnd);
                System.out.printf("    QUIC Handshake Duration : %6dms (%3.0f%%)%n", quicHandshakeDuration, (double) quicHandshakeDuration / totalTime * 100);
                System.out.printf("          QUIC Handshake TX : %6d%n", quicSentMessages);
                System.out.printf("          QUIC Handshake RX : %6d%n", quicReceivedMessages);
                System.out.printf("         QUIC Handshake RTT : %6d%n", quicRtt);
                System.out.printf("%n");
                System.out.printf("             Total Duration : %6dms (%3.0f%%)%n", totalTime, (double) 100);
                System.out.printf("%n");

                CsvLogger.log(client.logfile, quicMessageHandler instanceof SequentialQuicMessageHandler, holePunchingStart, holePunchingEnd, holePunchingDuration, holePunchingSentMessages, holePunchingReceivedMessages, holePunchingRtt, quicHandshakeStart, quicHandshakeEnd, quicHandshakeDuration, quicSentMessages, quicReceivedMessages, quicRtt, totalTime, client.ownEndpoint, client.serverEndpoint, client.otherClientEndpoint);
            }
        }

        private interface QuicMessageHandler {
            void channelRead(final ChannelHandlerContext ctx,
                             State state,
                             AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress> msg,
                             final ActiveClientCommand client, final ActiveClientHandler handler);

            void clear();

            void holePunched(ChannelHandlerContext ctx,
                             final ActiveClientCommand client, final ActiveClientHandler handler);

            boolean isEmpty();
        }

        private static class SequentialQuicMessageHandler implements QuicMessageHandler {
            private final List<AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress>> pendingWrites = new ArrayList<>();

            @Override
            public void channelRead(final ChannelHandlerContext ctx,
                                    final State state,
                                    final AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress> msg,
                                    final ActiveClientCommand client,
                                    final ActiveClientHandler handler) {
                LOG.trace("Enqueue QUIC write `{}` till hole punching is done.", msg);
                if (((QuicMessage) msg.content()).getType() == QuicPacketType.ZERO_RTT) {
                    handler.doZeroRtt = true;
                }
                pendingWrites.add(msg);
            }

            @Override
            public void clear() {
                pendingWrites.clear();
            }

            @Override
            public void holePunched(final ChannelHandlerContext ctx,
                                    final ActiveClientCommand client,
                                    final ActiveClientHandler handler) {
                if (handler.quicHandshakeTime == 0) {
                    // if we do 0-RTT. time has already been set!
                    handler.quicHandshakeTime = System.currentTimeMillis();
                }
                handler.quicAwaitResponse = true;
                LOG.trace("Got acknowledgement. Flush {} pending write(s)!.", pendingWrites.size());
                for (AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress> pendingWrite : pendingWrites) {
                    handler.quicSentMessages++;
                    LOG.trace("Redirect {} to PassiveClient {}.", pendingWrite, client.otherClientEndpoint);
                    ctx.writeAndFlush(new DefaultAddressedEnvelope<>(pendingWrite.content(), client.otherClientEndpoint));
                }
                pendingWrites.clear();
            }

            @Override
            public boolean isEmpty() {
                return pendingWrites.isEmpty();
            }
        }

        private static class ParallelQuicMessageHandler implements QuicMessageHandler {
            @Override
            public void channelRead(final ChannelHandlerContext ctx,
                                    final State state,
                                    final AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress> msg,
                                    final ActiveClientCommand client,
                                    final ActiveClientHandler handler) {
                if (((QuicMessage) msg.content()).getType() == QuicPacketType.ZERO_RTT) {
                    handler.doZeroRtt = true;
                }
                handler.quicAwaitResponse = true;
                handler.quicSentMessages++;
                if (handler.quicHandshakeTime == 0) {
                    handler.quicHandshakeTime = System.currentTimeMillis();
                }
                switch (state) {
                    case REQUESTED:
                    case CHECKING:
                        LOG.trace("Redirect {} to RendezvousServer {}.", msg, client.serverEndpoint);
                        ctx.writeAndFlush(new DefaultAddressedEnvelope<>(msg.content(), client.serverEndpoint));
                        break;
                    case ACKNOWLEDGED:
                        LOG.trace("Redirect {} to PassiveClient {}.", msg, client.otherClientEndpoint);
                        ctx.writeAndFlush(new DefaultAddressedEnvelope<>(msg.content(), client.otherClientEndpoint));
                        break;
                }
            }

            @Override
            public void clear() {
                // do nothing
            }

            @Override
            public void holePunched(final ChannelHandlerContext ctx,
                                    final ActiveClientCommand client,
                                    final ActiveClientHandler handler) {
                // do nothing
            }

            @Override
            public boolean isEmpty() {
                return true;
            }
        }
    }

    static class Mode {
        @Option(names = "--sequential")
        boolean sequential;
        @Option(names = "--parallel")
        boolean parallel;
    }
}
