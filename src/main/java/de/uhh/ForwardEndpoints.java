package de.uhh;

import java.net.Inet6Address;
import java.net.InetSocketAddress;

import static java.util.Objects.requireNonNull;

public class ForwardEndpoints implements TlsHolePunchingMessage {
    private final InetSocketAddress endpoint;

    public ForwardEndpoints(final InetSocketAddress endpoint) {
        if (endpoint.getAddress() instanceof Inet6Address) {
            throw new RuntimeException("IPv6 not supported!");
        }
        this.endpoint = requireNonNull(endpoint);
    }

    @Override
    public String toString() {
        return "ForwardEndpoints{" +
                "endpoint=" + endpoint +
                '}';
    }

    public InetSocketAddress getEndpoint() {
        return endpoint;
    }
}
