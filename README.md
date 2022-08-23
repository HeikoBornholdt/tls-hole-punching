# Low-Latency TLS 1.3-aware Hole Punching PoC

Proof of Concept (PoC) for Low-Latency TLS 1.3-aware Hole Punching.
While existing approaches perform UDP Hole Punching and the cryptographic TLS-Handshake in sequence, we perform them in parallel.
Our approach is intended to be used with UDP-based protocols that are secured by TLS (like QUIC, or DTLS).
As an example, this PoC uses QUIC as application protocol.
More information about our approach can be find in the following publication: **TODO**

Our PoC consists of...
* a RendezvousServer that helps in traversing any present NATs,
* a ActiveClient that initiates the hole punching process once the QUIC client connects to it,
* and a PassiveClient that initiates the hole punching process upon request from the Client Router and forwards all traffic to the QUIC server.

```text
                                  +------------------+
                         +------->| RendezvousServer |--------+
                         |        +------------------+        |
                         |                                    v
+-------------+   +------+-------+                    +-------+-------+   +-------------+
| QUIC Client +-->| ActiveClient |<------------------>| PassiveClient +-->| QUIC Server |
+-------------+   +--------------+                    +---------------+   +-------------+
```

While in our PoC the QUIC client and QUIC server run in processes separated from the routers, a production-ready implementation should include the router functionality directly.

For comparison reasons, this PoC supports two modes: sequential mode (like existing approaches) and parallel mode (our approach).

## Usage

We assume that you're running a public reachable computer (that will become our rendezvous server),
a NATed computer with a QUIC server you would like to access (that will be become our passive client),
and another (potentially) NATed computer which want to connect to the QUIC server (that will become our active client).

```bash
# Start rendezvous server
rendezvous$ mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="server --bind-host=<public-rendezvous-address>"

# Start passive client (we assume the QUIC server is running in port 4433)
passive$ mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="passive-client --server-host <public-rendezvous-address> --target-port 4433"

# Start active client (will listen on port 8010)
active$ mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="active-client --server-host <public-rendezvous-address> --bind-port 8010"
```

Now your QUIC client on `active` can connect to `127.0.0.1:8010`.
The connection request will then forwared to the QUIC server listening on `127.0.0.1:4433` on `passive`.
You can pass `--sequential` (default) or `--parallel` to specify what hole punching mode should be performed.

In our experiments, we used [quiche](https://github.com/cloudflare/quiche) 0.14.0 for running a QUIC server and QUIC client:
```bash
# Start QUIC server
passive$ cargo run --bin quiche-server -- --cert apps/src/bin/cert.crt --key apps/src/bin/cert.key --no-retry --early-data

# Start QUIC client (full handshake)
active$ cargo run --bin quiche-client -- --no-verify https://127.0.0.1:8010 --wire-version 1

# Start QUIC client (0-RTT handshake)
active$ cargo run --bin quiche-client -- --no-verify https://127.0.0.1:8010 --wire-version 1 --early-data --session-file session-file
```
