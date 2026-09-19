package redacted.client.command.impl;

import redacted.client.command.Argument;
import redacted.client.command.Command;
import redacted.client.module.impl.developer.PacketLogger;

public class PacketLogCommand extends Command {
    public PacketLogCommand() {
        super("packetlog", "Packet logger phase control and GUI",
                Argument.literal("action", "action to perform",
                        new Argument.Option("tower", "tower phase — hold jump, place under you"),
                        new Argument.Option("straight", "straight bridge — W only"),
                        new Argument.Option("diagonal", "diagonal bridge — W+A or W+D"),
                        new Argument.Option("backward", "backward bridge — S only"),
                        new Argument.Option("strafe", "strafe bridge — A or D only"),
                        new Argument.Option("jump", "jump-bridge — bridge while jumping"),
                        new Argument.Option("stop", "end current phase"),
                        new Argument.Option("start", "start tracking (no phase)"),
                        new Argument.Option("status", "show current stats"),
                        new Argument.Option("gui", "open PacketLogger GUI")));
    }

    @Override
    public void execute(String[] args) {
        PacketLogger logger = PacketLogger.getInstance();
        if (logger == null) { sendMessage("§cPacketLogger not loaded."); return; }
        if (args.length == 0) { printUsage(); return; }
        logger.handleCommand(args[0]);
    }
}
