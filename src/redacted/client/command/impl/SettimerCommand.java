package redacted.client.command.impl;

import redacted.client.Client;
import redacted.client.command.Argument;
import redacted.client.command.Command;
import redacted.client.util.notification.NotificationType;

public class SettimerCommand extends Command {

    public SettimerCommand() {
        super("settimer", "Notify you after a delay",
                Argument.text("name", "label for the timer"),
                Argument.text("time", "how long to wait, e.g. 90s, 5m, 2h"),
                Argument.optionalLiteral("mode", "add \"true\" to keep showing until you stop it",
                        "true", "false"));
    }

    @Override
    public void execute(String[] args) {
        if (!requireArgs(args, 2, 3)) {
            return;
        }
        final String t = args[1];
        if (!t.substring(t.length() - 1).matches("[smh]")) {
            sendMessage("§cTime needs an s/m/h suffix, e.g. §e90s§c. Usage: §r" + getUsage());
            return;
        }
        final String strTime = t.substring(0, t.length() - 1);
        try {
            final float time = Float.valueOf(strTime);

            if (time < 0) {
                sendMessage("Can't set negative time");
                return;
            }

            final boolean always = args.length == 3 && Boolean.valueOf(args[2]);

            final int mult = t.endsWith("s") ? 1 : t.endsWith("m") ? 60 : 3600;
            final float seconds = time * mult;
            if (seconds > 360000) {
                sendMessage("Too long time given");
                return;
            }

            Client.getInstance().getNotificationManager().showNotification(NotificationType.TIMER,
                    String.format("Timer \"%s\" %s", args[0], always ? "" : "done"),
                    always ? seconds : 5F, always ? 0 : seconds, true);
        } catch (NumberFormatException e) {
            sendMessage("§cBad time value §e" + strTime + "§c. Usage: §r" + getUsage());
        }
    }
}
