package redacted.client.baritone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.util.Randoms;
import redacted.client.util.player.InventoryUtil;
import redacted.client.util.world.PlaceUtil;

/**
 * Tick-driven assisted bridging: the "BridgeAssist method". Pure vanilla inputs, zero
 * rotation spoofing -- the controller owns the real camera (turns it backwards-and-down),
 * holds sneak + the back key so the body travels toward the target while the camera watches
 * the deck edge, and fires a right-click place on whatever support face the real look
 * raytraces. Because every packet (rotation, place ray, click) comes from one honest
 * client state there is no desync for an anticheat to flag; it looks exactly like a player
 * shift-bridging.
 *
 * <p>Started/stopped by the MCP bridge tools ({@code redacted.client.mcp.impl.BridgeTools}).
 * Bridges straight lines on a locked deck plane; climbing ledges is left to Baritone goto.
 */
public final class AutoBridgeController {

    private static final double PLACE_RANGE = 4.5D;
    private static final float LOOK_SPEED = 200.0F;   // max deg/tick camera step -- human flick range
    private static final float BRIDGE_PITCH = 80.0F;  // back-and-down look at the deck edge
    private static final double ARRIVE_DIST = 1.25D;  // horizontal blocks to target = done
    private static final int MIN_PLACE_INTERVAL_TICKS = 3; // ~150ms between clicks + jitter
    private static final int STUCK_TICKS = 60;        // 3s of no progress -> abort
    private static final double STUCK_EPSILON = 0.05D;
    private static final int FALL_LIMIT = 8;          // blocks below deck plane -> abort
    private static final int LOW_BLOCKS = 1;

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static AutoBridgeController instance;

    private boolean active;
    private String status = "idle";
    private double targetX;
    private double targetZ;
    private int targetY = Integer.MIN_VALUE;
    private int deckY;
    private boolean deckLocked;
    private int previousSlot = -1;
    private int placeCooldownTicks;
    private int stuckCounter;
    private double lastProgressX;
    private double lastProgressZ;
    // travel direction unit vector, captured each tick from current pos -> target
    private double dirX;
    private double dirZ;

    private AutoBridgeController() {
    }

    public static synchronized AutoBridgeController getInstance() {
        if (instance == null) instance = new AutoBridgeController();
        return instance;
    }

    public boolean isActive() {
        return this.active;
    }

    public String getStatus() {
        return this.status;
    }

    public double getTargetX() {
        return this.targetX;
    }

    public double getTargetZ() {
        return this.targetZ;
    }

    /**
     * Starts bridging toward (x, z) on the current feet-Y deck plane.
     *
     * @return error message or null on success
     */
    public synchronized String start(final double x, final double z, final Integer y) {
        final EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || mc.playerController == null) {
            return "not in a world";
        }
        if (this.active) {
            this.stopQuietly();
        }

        this.targetX = x;
        this.targetZ = z;
        this.targetY = y == null ? Integer.MIN_VALUE : y;
        // the deck plane is the layer of blocks UNDER our feet (feet sit on top of it)
        this.deckY = new BlockPos(player.posX, player.posY, player.posZ).getY() - 1;
        this.deckLocked = true;
        this.previousSlot = -1;
        this.placeCooldownTicks = 5;   // small settle delay before the first click
        this.stuckCounter = 0;
        this.lastProgressX = player.posX;
        this.lastProgressZ = player.posZ;
        this.status = "bridging";
        this.active = true;

        Client.getInstance().getEventManager().register(this);
        return null;
    }

    /** Stops and releases everything. Safe to call when idle. */
    public synchronized void stop(final String reason) {
        if (!this.active) return;
        this.stopQuietly();
        this.status = reason == null ? "stopped" : reason;
    }

    private void stopQuietly() {
        this.active = false;
        try {
            Client.getInstance().getEventManager().unregister(this);
        } catch (final Exception ignored) {
        }
        releaseKeys();
        restoreSlot();
    }

    // per-tick control loop

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;

        final EntityPlayerSP player = mc.thePlayer;
        final WorldClient world = mc.theWorld;
        if (!this.active || player == null || world == null || mc.playerController == null) {
            if (this.active) this.stop("world gone");
            return;
        }

        // any screen (inventory, chest...) means keys don't process -- hold still until closed
        if (mc.currentScreen != null || !mc.inGameHasFocus) return;

        if (player.capabilities.isFlying || !player.onGround && player.fallDistance > FALL_LIMIT) {
            this.stop("airborne/falling");
            return;
        }

        if (ensureBlockSlot()) {
            // ok -- block in hand (or swapped in)
        } else {
            this.stop("out of blocks");
            return;
        }

        // travel vector toward the target
        final double dx = this.targetX + 0.5D - player.posX;
        final double dz = this.targetZ + 0.5D - player.posZ;
        final double hDist = Math.sqrt(dx * dx + dz * dz);

        if (hDist <= ARRIVE_DIST) {
            this.stop("arrived");
            return;
        }
        this.dirX = dx / hDist;
        this.dirZ = dz / hDist;

        // fell off / dropped well under the deck plane
        if (this.deckLocked && player.posY < this.deckY - FALL_LIMIT) {
            this.stop("fell below deck");
            return;
        }

        // stuck watchdog: grounded, no meaningful movement for a while
        if (player.onGround) {
            final double movedSq = (player.posX - this.lastProgressX) * (player.posX - this.lastProgressX)
                    + (player.posZ - this.lastProgressZ) * (player.posZ - this.lastProgressZ);
            if (movedSq > STUCK_EPSILON * STUCK_EPSILON) {
                this.stuckCounter = 0;
                this.lastProgressX = player.posX;
                this.lastProgressZ = player.posZ;
            } else if (++this.stuckCounter > STUCK_TICKS) {
                this.stop("stuck (no progress)");
                return;
            }
        }

        // camera: turn backwards-and-down onto the deck edge we're extending
        final float travelYaw = (float) Math.toDegrees(Math.atan2(-this.dirX, this.dirZ));
        final float wantYaw = MathHelper.wrapAngleTo180_float(travelYaw + 180.0F);
        final float newYaw = stepAngle(player.rotationYaw, wantYaw, LOOK_SPEED);
        final float newPitch = stepToward(player.rotationPitch, BRIDGE_PITCH, LOOK_SPEED);
        player.rotationYaw = newYaw;
        player.rotationPitch = newPitch;
        player.setRotationYawHead(newYaw);

        // movement: back key walks us toward the target (camera faces away), sneak keeps
        // us glued to the edge while the deck extends. sprint stays off -- sneaking anyway.
        setKey(mc.gameSettings.keyBindBack, true);
        setKey(mc.gameSettings.keyBindForward, false);
        setKey(mc.gameSettings.keyBindLeft, false);
        setKey(mc.gameSettings.keyBindRight, false);
        setKey(mc.gameSettings.keyBindJump, false);
        setKey(mc.gameSettings.keyBindSneak, true);
        player.setSprinting(false);

        // placement: right-click whatever support face our REAL look raytraces
        if (this.placeCooldownTicks > 0) {
            this.placeCooldownTicks--;
        } else if (tryPlace()) {
            this.placeCooldownTicks = MIN_PLACE_INTERVAL_TICKS + (int) Randoms.between(0.0D, 2.0D);
        }

        // arrival re-check after moving this tick happens next tick -- fine at these speeds
    }

    /**
     * Places one deck block: raytrace along the live camera look, require a clean hit on a
     * valid support face whose adjacent cell is on our deck plane and roughly behind/at us
     * (never far ahead), then fire the vanilla right-click path.
     */
    private boolean tryPlace() {
        final EntityPlayerSP player = mc.thePlayer;
        final ItemStack held = player.inventory.getCurrentItem();
        if (!PlaceUtil.isPlaceableBlock(held)) return false;

        final MovingObjectPosition hit = traceLook(PLACE_RANGE);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return false;

        final BlockPos support = hit.getBlockPos();
        final EnumFacing face = hit.sideHit;
        final BlockPos place = support.offset(face);

        if (place.getY() != this.deckY) return false;                    // flat deck only
        if (!PlaceUtil.isReplaceable(place)) return false;
        if (!PlaceUtil.isValidSupport(support, PLACE_RANGE)) return false;
        if (!PlaceUtil.canPlaceOnSide(support, face, held)) return false;
        if (!sideClickLegal(support, face)) return false;

        // only extend the deck behind/at us along the travel line -- a hit ahead means the
        // look caught some other geometry; ignore it and keep backing up
        final double along = (place.getX() + 0.5D - player.posX) * this.dirX
                + (place.getZ() + 0.5D - player.posZ) * this.dirZ;
        if (along > 0.75D || along < -2.0D) return false;

        final boolean placed = mc.playerController.onPlayerRightClick(
                player, mc.theWorld, held, support, face, hit.hitVec);
        if (placed) player.swingItem();
        return placed;
    }

    /** Verus-style physical-reachability gate: never click a side face whose plane is behind the eye. */
    private boolean sideClickLegal(final BlockPos support, final EnumFacing face) {
        final double eyeX = mc.thePlayer.posX;
        final double eyeZ = mc.thePlayer.posZ;
        switch (face) {
            case NORTH: return eyeZ - support.getZ() < 0.6D;
            case SOUTH: return eyeZ - support.getZ() > 0.4D;
            case WEST:  return eyeX - support.getX() < 0.6D;
            case EAST:  return eyeX - support.getX() > 0.4D;
            default:    return true;
        }
    }

    // inventory / key helpers

    /** Keep a healthy block stack in hand; swap to the fullest hotbar slot before running dry. */
    private boolean ensureBlockSlot() {
        final ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (PlaceUtil.isPlaceableBlock(held) && held.stackSize > LOW_BLOCKS) return true;

        int best = -1;
        int bestCount = LOW_BLOCKS;
        for (int slot = 0; slot < InventoryUtil.HOTBAR_SIZE; slot++) {
            final ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (PlaceUtil.isPlaceableBlock(stack) && stack.stackSize > bestCount) {
                best = slot;
                bestCount = stack.stackSize;
            }
        }
        if (best < 0) return PlaceUtil.isPlaceableBlock(held);   // ride the current low stack out
        if (best != mc.thePlayer.inventory.currentItem) {
            if (this.previousSlot < 0) this.previousSlot = mc.thePlayer.inventory.currentItem;
            mc.playerController.setCurrentItemAndSync(best);
        }
        return true;
    }

    private void restoreSlot() {
        if (this.previousSlot >= 0 && mc.thePlayer != null && mc.playerController != null) {
            mc.playerController.setCurrentItemAndSync(this.previousSlot);
        }
        this.previousSlot = -1;
    }

    private void releaseKeys() {
        if (mc.thePlayer == null) return;
        setKey(mc.gameSettings.keyBindBack, GameSettings.isKeyDown(mc.gameSettings.keyBindBack));
        setKey(mc.gameSettings.keyBindForward, GameSettings.isKeyDown(mc.gameSettings.keyBindForward));
        setKey(mc.gameSettings.keyBindLeft, GameSettings.isKeyDown(mc.gameSettings.keyBindLeft));
        setKey(mc.gameSettings.keyBindRight, GameSettings.isKeyDown(mc.gameSettings.keyBindRight));
        setKey(mc.gameSettings.keyBindSneak, GameSettings.isKeyDown(mc.gameSettings.keyBindSneak));
        setKey(mc.gameSettings.keyBindJump, GameSettings.isKeyDown(mc.gameSettings.keyBindJump));
    }

    private static void setKey(final KeyBinding binding, final boolean down) {
        KeyBinding.setKeyBindState(binding.getKeyCode(), down);
    }

    // math helpers

    private static float stepAngle(final float from, final float to, final float speed) {
        final float diff = MathHelper.wrapAngleTo180_float(to - from);
        if (Math.abs(diff) <= speed) return MathHelper.wrapAngleTo180_float(from + diff);
        return MathHelper.wrapAngleTo180_float(from + Math.signum(diff) * speed);
    }

    private static float stepToward(final float from, final float to, final float speed) {
        final float diff = to - from;
        if (Math.abs(diff) <= speed) return to;
        return from + Math.signum(diff) * speed;
    }

    private static MovingObjectPosition traceLook(final double distance) {
        final EntityPlayerSP player = mc.thePlayer;
        final Vec3 eyes = new Vec3(player.posX,
                player.posY + player.getEyeHeight(), player.posZ);
        final float yawRad = (float) Math.toRadians(player.rotationYaw);
        final float pitchRad = (float) Math.toRadians(player.rotationPitch);
        final Vec3 look = new Vec3(
                -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad),
                -MathHelper.sin(pitchRad),
                MathHelper.cos(yawRad) * MathHelper.cos(pitchRad));
        return mc.theWorld.rayTraceBlocks(eyes, addScaled(eyes, look, distance), false);
    }

    private static Vec3 addScaled(final Vec3 base, final Vec3 dir, final double scale) {
        return new Vec3(base.xCoord + dir.xCoord * scale,
                base.yCoord + dir.yCoord * scale,
                base.zCoord + dir.zCoord * scale);
    }
}
