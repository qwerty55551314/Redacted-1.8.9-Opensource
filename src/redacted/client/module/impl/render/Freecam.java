package redacted.client.module.impl.render;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.PacketEvent;
import redacted.client.event.impl.player.MotionEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.NumberValue;

public class Freecam extends Module {

    private final NumberValue speed = new NumberValue("Speed", this, 1.0, 0.1, 5.0, 0.1).describe("Camera fly speed");

    private EntityOtherPlayerMP fakePlayer;
    private double oldX, oldY, oldZ;
    private float oldYaw, oldPitch;
    private boolean oldOnGround;
    private WorldClient oldWorld;

    public Freecam() {
        super("Freecam", "Move your camera freely without moving your body",
                Keyboard.KEY_NONE, Category.RENDER);
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        oldX = mc.thePlayer.posX;
        oldY = mc.thePlayer.posY;
        oldZ = mc.thePlayer.posZ;
        oldYaw = mc.thePlayer.rotationYaw;
        oldPitch = mc.thePlayer.rotationPitch;
        oldOnGround = mc.thePlayer.onGround;
        oldWorld = mc.theWorld;

        fakePlayer = new EntityOtherPlayerMP(mc.theWorld, mc.thePlayer.getGameProfile());
        fakePlayer.copyLocationAndAnglesFrom(mc.thePlayer);
        fakePlayer.setEntityId(-69420);
        fakePlayer.noClip = true;
        mc.theWorld.addEntityToWorld(-69420, fakePlayer);

        mc.thePlayer.noClip = true;
        mc.thePlayer.capabilities.isFlying = true;
        mc.thePlayer.setPosition(oldX, oldY + 0.1, oldZ);
    }

    @Override
    public void onDisable() {
        if (fakePlayer != null && oldWorld != null) {
            oldWorld.removeEntity(fakePlayer);
            fakePlayer = null;
        }
        oldWorld = null;

        if (mc.thePlayer == null) return;

        mc.thePlayer.setPositionAndRotation(oldX, oldY, oldZ, oldYaw, oldPitch);
        mc.thePlayer.motionX = 0;
        // downward probe: moveEntity derives onGround from (motionY < 0), so a flat 0 reports airborne -> GroundSpoof.
        // when grounded, collision clamps the -0.1 so we don't actually move.
        mc.thePlayer.motionY = oldOnGround ? -0.1 : 0;
        mc.thePlayer.motionZ = 0;
        mc.thePlayer.noClip = false;
        mc.thePlayer.capabilities.isFlying = false;
        mc.thePlayer.capabilities.setFlySpeed(0.05f);

        if (mc.thePlayer.ridingEntity != null) {
            mc.thePlayer.ridingEntity.motionX = 0;
            mc.thePlayer.ridingEntity.motionY = 0;
            mc.thePlayer.ridingEntity.motionZ = 0;
        }
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // World changed; disable to avoid ghost entities.
        if (mc.theWorld != oldWorld) {
            toggle();
            return;
        }

        // Don't interfere while world is still loading
        if (mc.theWorld.loadedEntityList == null || mc.theWorld.loadedEntityList.size() < 2) return;

        mc.thePlayer.noClip = true;
        mc.thePlayer.capabilities.isFlying = true;
        mc.thePlayer.capabilities.setFlySpeed((float) (speed.doubleValue() / 5.0));
        mc.thePlayer.onGround = false;

        float forward = 0, strafe = 0;
        if (mc.gameSettings.keyBindForward.isKeyDown()) forward++;
        if (mc.gameSettings.keyBindBack.isKeyDown()) forward--;
        if (mc.gameSettings.keyBindLeft.isKeyDown()) strafe++;
        if (mc.gameSettings.keyBindRight.isKeyDown()) strafe--;

        double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
        double mx = forward * -Math.sin(yaw) + strafe * Math.cos(yaw);
        double mz = forward * Math.cos(yaw) + strafe * Math.sin(yaw);

        double spd = speed.doubleValue() * 0.2;
        mc.thePlayer.motionX = mx * spd;
        mc.thePlayer.motionZ = mz * spd;

        if (mc.gameSettings.keyBindJump.isKeyDown()) {
            mc.thePlayer.motionY = spd;
        } else if (mc.gameSettings.keyBindSneak.isKeyDown()) {
            mc.thePlayer.motionY = -spd;
        } else {
            mc.thePlayer.motionY = 0;
        }

        mc.thePlayer.setPosition(mc.thePlayer.posX + mc.thePlayer.motionX,
                mc.thePlayer.posY + mc.thePlayer.motionY,
                mc.thePlayer.posZ + mc.thePlayer.motionZ);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre()) return;
        // report the frozen body to the server every tick -- the camera roams client-side only. keeping the
        // normal packet cadence (instead of cancelling C03) means Grim's reminder counters never freeze, so
        // cutting back to the body produces no teleport / duplicate-look / ground / slow-move desync.
        event.setPosX(oldX);
        event.setPosY(oldY);
        event.setPosZ(oldZ);
        event.setYaw(oldYaw);
        event.setPitch(oldPitch);
        event.setOnGround(oldOnGround);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            event.setCancelled(true);
            return;
        }

        if (event.getDirection() != PacketEvent.Direction.OUTBOUND) return;

        // Don't cancel packets while world is loading/joining
        if (mc.theWorld == null || mc.theWorld.loadedEntityList == null
                || mc.theWorld.loadedEntityList.size() < 2) return;

        if (event.getPacket() instanceof C0BPacketEntityAction
                || event.getPacket() instanceof C0APacketAnimation
                || event.getPacket() instanceof C07PacketPlayerDigging
                || event.getPacket() instanceof C08PacketPlayerBlockPlacement
                || event.getPacket() instanceof C02PacketUseEntity) {
            event.setCancelled(true);
        }
    }
}
