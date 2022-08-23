package de.uhh;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class CsvLogger {
    private static final DateFormat df;

    static {
        df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        df.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
    }

    private static Writer WRITER = null;

    public static synchronized void log(final String filename,
                                        final boolean sequential,
                                        final long holePunchingStart,
                                        final long holePunchingEnd,
                                        final long holePunchingDuration,
                                        final long holePunchingSentMessages,
                                        final long holePunchingReceivedMessages,
                                        final int holePunchingRtt,
                                        final long quicStart,
                                        final long quicEnd,
                                        final long quicDuration,
                                        final long quicSentMessages,
                                        final long quicReceivedMessages,
                                        final int quicRtt,
                                        final long totalDuration,
                                        final InetSocketAddress activeClientEndpoint,
                                        final InetSocketAddress passiveClientEndpoint,
                                        final InetSocketAddress rendezvousServerEndpoint) {
        if (WRITER == null) {
            try {
                WRITER = new FileWriter(filename);
                WRITER.append("time");
                WRITER.append(',');
                WRITER.append("mode");
                WRITER.append(',');
                WRITER.append("hp_start");
                WRITER.append(',');
                WRITER.append("hp_end");
                WRITER.append(',');
                WRITER.append("hp_duration");
                WRITER.append(',');
                WRITER.append("hp_sent");
                WRITER.append(',');
                WRITER.append("hp_received");
                WRITER.append(',');
                WRITER.append("hp_rtt");
                WRITER.append(',');
                WRITER.append("quic_start");
                WRITER.append(',');
                WRITER.append("quic_end");
                WRITER.append(',');
                WRITER.append("quic_duration");
                WRITER.append(',');
                WRITER.append("quic_sent");
                WRITER.append(',');
                WRITER.append("quic_received");
                WRITER.append(',');
                WRITER.append("quic_rtt");
                WRITER.append(',');
                WRITER.append("total_duration");
                WRITER.append(',');
                WRITER.append("active_endpoint");
                WRITER.append(',');
                WRITER.append("passive_endpoint");
                WRITER.append(',');
                WRITER.append("server_endpoint");
                WRITER.append('\n');
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            WRITER.append(df.format(new Date()));
            WRITER.append(',');
            WRITER.append(sequential ? "sequential" : "parallel");
            WRITER.append(',');
            WRITER.append(String.valueOf(holePunchingStart));
            WRITER.append(',');
            WRITER.append(String.valueOf(holePunchingEnd));
            WRITER.append(',');
            WRITER.append(String.valueOf(holePunchingDuration));
            WRITER.append(',');
            WRITER.append(String.valueOf(holePunchingSentMessages));
            WRITER.append(',');
            WRITER.append(String.valueOf(holePunchingReceivedMessages));
            WRITER.append(',');
            WRITER.append(String.valueOf(holePunchingRtt));
            WRITER.append(',');
            WRITER.append(String.valueOf(quicStart));
            WRITER.append(',');
            WRITER.append(String.valueOf(quicEnd));
            WRITER.append(',');
            WRITER.append(String.valueOf(quicDuration));
            WRITER.append(',');
            WRITER.append(String.valueOf(quicSentMessages));
            WRITER.append(',');
            WRITER.append(String.valueOf(quicReceivedMessages));
            WRITER.append(',');
            WRITER.append(String.valueOf(quicRtt));
            WRITER.append(',');
            WRITER.append(String.valueOf(totalDuration));
            WRITER.append(',');
            WRITER.append(activeClientEndpoint.toString());
            WRITER.append(',');
            WRITER.append(passiveClientEndpoint.toString());
            WRITER.append(',');
            WRITER.append(rendezvousServerEndpoint.toString());
            WRITER.append('\n');
            WRITER.flush();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized void log(final String filename,
                                        final boolean sequential,
                                        final long holePunchingStart,
                                        final long holePunchingEnd,
                                        final long holePunchingDuration,
                                        final int holePunchingRtt,
                                        final long quicStart,
                                        final long quicEnd,
                                        final long quicDuration,
                                        final int quicRtt,
                                        final long totalDuration,
                                        final InetSocketAddress activeClientEndpoint,
                                        final InetSocketAddress passiveClientEndpoint,
                                        final InetSocketAddress rendezvousServerEndpoint) {
        log(filename, sequential, holePunchingStart, holePunchingEnd, holePunchingDuration, 0, 0, holePunchingRtt, quicStart, quicEnd, quicDuration, 0, 0, quicRtt, totalDuration, activeClientEndpoint, passiveClientEndpoint, rendezvousServerEndpoint);
    }
}
