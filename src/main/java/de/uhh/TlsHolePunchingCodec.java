package de.uhh;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.incubator.codec.quic.QuicHeaderParser;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

@Sharable
public class TlsHolePunchingCodec extends MessageToMessageCodec<AddressedEnvelope<ByteBuf, InetSocketAddress>, AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress>> {
    private static final Logger LOG = LoggerFactory.getLogger(TlsHolePunchingCodec.class);
    // 41 -> InsecureQuicTokenHandler#MAX_TOKEN_LEN
    // 20 -> Quiche.QUICHE_MAX_CONN_ID_LEN
    private final QuicHeaderParser headerParser = new QuicHeaderParser(41, 20);
    static final byte MAGIC_NUMBER_CONNECTION_REQUEST = 101;
    static final byte MAGIC_NUMBER_FORWARD_ENDPOINTS = 102;
    static final byte MAGIC_NUMBER_REACHABILITY_CHECK = 103;
    static final byte MAGIC_NUMBER_ACKNOWLEDGEMENT = 104;
    static final byte MAGIC_NUMBER_REGISTER = 105;

    @SuppressWarnings("unchecked")
    @Override
    public boolean acceptOutboundMessage(Object msg) {
        return msg instanceof AddressedEnvelope && ((AddressedEnvelope<?, InetSocketAddress>) msg).content() instanceof TlsHolePunchingMessage;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final AddressedEnvelope<TlsHolePunchingMessage, InetSocketAddress> msg,
                          final List<Object> out) {
        if (msg.content() instanceof QuicMessage) {
            // no magic number!
            out.add(new DatagramPacket(((QuicMessage) msg.content()).getPacket().retain(), msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof ConnectionRequest) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeByte(MAGIC_NUMBER_CONNECTION_REQUEST);

            out.add(new DatagramPacket(buf, msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof ForwardEndpoints) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeByte(MAGIC_NUMBER_FORWARD_ENDPOINTS);
            buf.writeBytes(((ForwardEndpoints) msg.content()).getEndpoint().getAddress().getAddress());
            buf.writeInt(((ForwardEndpoints) msg.content()).getEndpoint().getPort());

            out.add(new DatagramPacket(buf, msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof ReachabilityCheck) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeByte(MAGIC_NUMBER_REACHABILITY_CHECK);

            out.add(new DatagramPacket(buf, msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof Acknowledgement) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeByte(MAGIC_NUMBER_ACKNOWLEDGEMENT);

            out.add(new DatagramPacket(buf, msg.recipient(), msg.sender()));
        }
        else if (msg.content() instanceof Register) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeByte(MAGIC_NUMBER_REGISTER);
            buf.writeChar(((Register) msg.content()).getPeerId());

            out.add(new DatagramPacket(buf, msg.recipient(), msg.sender()));
        }
        else {
            throw new EncoderException("Unknown " + StringUtil.simpleClassName(TlsHolePunchingMessage.class) + " type: " + StringUtil.simpleClassName(msg.content()));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean acceptInboundMessage(final Object msg) {
        return msg instanceof AddressedEnvelope && ((AddressedEnvelope<?, InetSocketAddress>) msg).content() instanceof ByteBuf;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final AddressedEnvelope<ByteBuf, InetSocketAddress> msg,
                          final List<Object> out) throws Exception {
        msg.content().markReaderIndex();
        final byte magicNumber = msg.content().readByte();
        switch (magicNumber) {
            case MAGIC_NUMBER_CONNECTION_REQUEST:
                out.add(new DefaultAddressedEnvelope<>(new ConnectionRequest(), msg.recipient(), msg.sender()));
                break;
            case MAGIC_NUMBER_FORWARD_ENDPOINTS:
                final byte[] addressBuffer = new byte[4];
                msg.content().readBytes(addressBuffer);
                final InetAddress address = InetAddress.getByAddress(addressBuffer);
                final int port = msg.content().readInt();
                final InetSocketAddress endpoint = new InetSocketAddress(address, port);
                out.add(new DefaultAddressedEnvelope<>(new ForwardEndpoints(endpoint), msg.recipient(), msg.sender()));
                break;
            case MAGIC_NUMBER_REACHABILITY_CHECK:
                out.add(new DefaultAddressedEnvelope<>(new ReachabilityCheck(), msg.recipient(), msg.sender()));
                break;
            case MAGIC_NUMBER_ACKNOWLEDGEMENT:
                out.add(new DefaultAddressedEnvelope<>(new Acknowledgement(), msg.recipient(), msg.sender()));
                break;
            case MAGIC_NUMBER_REGISTER:
                final char peerId = msg.content().readChar();
                out.add(new DefaultAddressedEnvelope<>(new Register(peerId), msg.recipient(), msg.sender()));
                break;
            default:
                // QUIC message
                msg.content().resetReaderIndex();
                headerParser.parse(msg.sender(), msg.recipient(), msg.content(), (sender, recipient, packet, type, version, scid, dcid, token) -> {
                    // QUIC message
                    switch (type) {
                        case INITIAL:
                        case HANDSHAKE:
                        case VERSION_NEGOTIATION:
                        case RETRY:
                        case ZERO_RTT:
//                            LOG.trace("[{} => {}]: {}, DCID={}, SCID={}", msg.sender(), msg.recipient(), type, ByteBufUtil.hexDump(dcid), ByteBufUtil.hexDump(scid));
                            break;
                        case SHORT:
//                            LOG.trace("[{} => {}]: Protected Payload, DCID={}", msg.sender(), msg.recipient(), ByteBufUtil.hexDump(dcid));
                            break;
                        default:
                            LOG.error("Unexpected type: {}", type);
                            break;
                    }

                    final QuicMessage quicMessage = new QuicMessage(packet.retain(), type);
                    out.add(new DefaultAddressedEnvelope<>(quicMessage, msg.recipient(), msg.sender()));
                });
                break;
        }
    }
}
