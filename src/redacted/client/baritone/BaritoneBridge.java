package redacted.client.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.utils.Helper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.PacketEvent;
import redacted.client.event.impl.Render3DEvent;

/**
 * Feeds client events into Baritone's own event bus. This is the replacement for the
 * 1.13 reference build's mixins: every vanilla hook that Baritone needs either fires a
 * client event (tick, render, packets) or a direct call in a vanilla class (chat, chunk
 * data, block interact, player update, rotation move, sprint state, world load).
 *
 * The bridge itself only has to forward the events the client already dispatches.
 */
public final class BaritoneBridge implements Helper {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private WorldClient lastWorld;

    public BaritoneBridge() {
        // keep Baritone's cache/save/region logging out of the console by default; a
        // debug settings module can flip CacheLog.setQuiet(false) to see it again
        baritone.cache.CacheLog.setQuiet(true);
    }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) {
            // the 1.13 reference fires baritone ticks once per client tick (PRE only)
            return;
        }
        // world change detection: loadWorld edits also fire, this catches dimension switches
        final WorldClient world = mc.theWorld;
        if (world != this.lastWorld) {
            this.lastWorld = world;
            BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onWorldEvent(
                    new baritone.api.event.events.WorldEvent(world, EventState.POST));
        }

        final boolean inGame = mc.thePlayer != null && world != null;
        final TickEvent.Type type = inGame ? TickEvent.Type.IN : TickEvent.Type.OUT;
        BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onTick(
                new TickEvent(EventState.PRE, type, 0));
    }

    @EventTarget
    public void onRender(final Render3DEvent event) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onRenderPass(
                new RenderEvent(event.getPartialTicks()));
    }

    @EventTarget
    public void onPacket(final PacketEvent event) {
        final baritone.api.event.events.PacketEvent baritoneEvent = new baritone.api.event.events.PacketEvent(
                mc.getNetHandler() != null ? mc.getNetHandler().getNetworkManager() : null,
                EventState.PRE,
                event.getPacket());
        if (event.getDirection() == PacketEvent.Direction.INBOUND) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onReceivePacket(baritoneEvent);
        } else {
            BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onSendPacket(baritoneEvent);
        }
    }
}
