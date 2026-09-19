package redacted.client.command.impl;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import redacted.client.Client;
import redacted.client.command.Argument;
import redacted.client.command.Command;
import redacted.client.module.Module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ToggleCommand extends Command {

    public ToggleCommand() {
        super("toggle", "Toggle modules on/off", new String[]{"t"},
                Argument.text("module", "module to toggle, or \"all off\" to disable everything",
                        () -> {
                            final List<String> names = new ArrayList<String>();
                            for (final Module mod : Client.getInstance().getModuleManager().getModules()) {
                                names.add(mod.getName());
                            }
                            names.add("all");
                            return names;
                        }));
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        if (args[0].equalsIgnoreCase("all")) {
            if (args.length < 2 || !args[1].equalsIgnoreCase("off")) {
                sendMessage("§cUsage: §r.toggle §a<all off>");
                return;
            }
            int count = 0;
            for (final Module mod : Client.getInstance().getModuleManager().getModules()) {
                if (mod.isEnabled()) {
                    mod.setEnabled(false);
                    count++;
                }
            }
            sendMessage("§aDisabled §e" + count + " §amodule" + (count == 1 ? "" : "s") + ".");
            return;
        }

        final Module target = Client.getInstance().getModuleManager().getModule(args[0]);
        if (target == null) {
            sendMessage("§cModule §e" + args[0] + "§c not found. Use §e.toggle <module>§c.");
            return;
        }

        target.toggle();

        final IChatComponent msg = new ChatComponentText("§a");
        msg.appendSibling(clickCommand("§e" + target.getName(), ".toggle " + target.getName(),
                "§7Click to toggle §e" + target.getName()));
        msg.appendSibling(new ChatComponentText(" §a" + (target.isEnabled() ? "enabled" : "disabled") + "."));
        sendMessage(msg);
    }

    @Override
    public List<String> getArgCompletions(String[] args, String token) {
        if (args != null && args.length == 1 && "all".equalsIgnoreCase(args[0])) {
            return Collections.singletonList("off");
        }
        return super.getArgCompletions(args, token);
    }
}
