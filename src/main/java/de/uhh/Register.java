package de.uhh;

public class Register implements TlsHolePunchingMessage {
    private final Character peerId;

    public Register(final Character peerId) {
        this.peerId = peerId;
    }

    @Override
    public String toString() {
        return "Register{" +
                "peerId=" + peerId +
                '}';
    }

    public Character getPeerId() {
        return peerId;
    }
}
