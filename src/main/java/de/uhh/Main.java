package de.uhh;

import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

@Command(
        name = "tls-hole-punching",
        subcommands = {
                HelpCommand.class,
                ActiveClientCommand.class,
                PassiveClientCommand.class,
                RendezvousServer.class
        }
)
public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    static {
        // first call of this method triggers some expensive static initialization
        // do it here, otherwise initilation will affect our experiment
        ByteBufUtil.hexDump(new byte[]{ 1, 3, 37 });
    }

    public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
    }
}
