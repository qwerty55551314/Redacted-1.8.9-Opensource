package redacted.client.module.impl.utility;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.module.impl.combat.Team;
import redacted.client.util.ChatUtil;

public class MiddleClickFriend extends Module {
    // pickBlock's press counter is drained by vanilla between PRE and POST, so isPressed()
    // never catches it from a tick handler - read the raw button edge instead.
    private boolean wasDown;

    public MiddleClickFriend() {
        super("MiddleClickFriend", "Middle-click a player to add/remove them from your Team", Keyboard.KEY_NONE, Category.UTILITY);
    }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;

        final boolean down = mc.gameSettings.keyBindPickBlock.isKeyDown();
        if (down && !wasDown) toggleFriend();
        wasDown = down;
    }

    private void toggleFriend() {
        final MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return;
        if (!(hit.entityHit instanceof EntityPlayer)) return;

        final Team team = Team.get();
        if (team == null) return;

        final String name = ((EntityPlayer) hit.entityHit).getGameProfile().getName();
        if (team.contains(name)) {
            team.remove(name);
            ChatUtil.print("§b[Team] §cRemoved §f" + name + " §cfrom friends");
        } else {
            team.add(name);
            ChatUtil.print("§b[Team] §aAdded §f" + name + " §ato friends");
        }
    }
}
