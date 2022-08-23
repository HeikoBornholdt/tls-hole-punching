package de.uhh;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.incubator.codec.quic.QuicPacketType;

public class QuicMessage implements TlsHolePunchingMessage {
    private final ByteBuf packet;
    private final QuicPacketType type;

    public QuicMessage(final ByteBuf packet,
                       final QuicPacketType type) {

        this.packet = packet;
        this.type = type;
    }

    public ByteBuf getPacket() {
        return packet;
    }

    public QuicPacketType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "QuicMessage{" +
//                "packet=" + packet +
                ", type=" + type +
//                ", version=" + version +
//                ", scid=" + ByteBufUtil.hexDump(scid) +
//                ", dcid=" + ByteBufUtil.hexDump(dcid) +
                '}';
    }
}
