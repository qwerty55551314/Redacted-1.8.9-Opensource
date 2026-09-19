package redacted.client.command.impl;

import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.command.Command;
import redacted.client.module.Module;
import redacted.client.value.Value;

public class ResetCommand extends Command {

    private static final String CLICK_GUI = "ClickGUI";

    public ResetCommand() {
        super("reset", "Reset all modules to defaults: disables everything, clears keybinds, resets settings.");
    }

    @Override
    public void execute(String[] args) {
        int count = 0;
        for (final Module mod : Client.getInstance().getModuleManager().getModules()) {
            if (mod.getName().equals(CLICK_GUI)) continue;
            if (mod.isEnabled()) mod.setEnabled(false);
            mod.setKey(Keyboard.KEY_NONE);
            for (final Value<?> val : mod.getSettings()) {
                val.reset();
            }
            count++;
        }
        sendMessage("§aReset §e" + count + " §amodules to defaults.");
    }
}
