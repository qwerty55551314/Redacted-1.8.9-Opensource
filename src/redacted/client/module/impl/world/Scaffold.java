package redacted.client.module.impl.world;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.player.EventMoveInput;
import redacted.client.event.impl.player.MotionEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.player.InventoryUtil;
import redacted.client.util.world.PlaceUtil;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ModeValue;
import redacted.util.RaycastUtil;
import redacted.util.RotationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Scaffold - now with proper modes and Grim-safe handling.
 * Based on: Rise Scaffold (polar wobble, tower), Kevin LegitScaffold (brute-force pitch, sneak),
 * Polarware LegitScaffold (slow sneak), and our RotationUtil for 1:1.
 * See RESEARCH-ViaMCP-Offsets-DevTools-HumanCheating.md for full references.
 */
public class Scaffold extends Module {
    private static Scaffold instance;
    private static final double PLACE_RANGE = 4.5D, FACE_INSET = 0.15D;
    private static final float MAX_PITCH = 89.0F, MIN_PITCH = 50.0F, MIN_STEP = 0.25F;
    private static final float ROTATION_SPEED = 180.0F, SETTLE_ALIGN = 5.0F, IDLE_PITCH = 80.0F;
    private static final int LOW_BLOCKS = 1;

    private final ModeValue mode = new ModeValue("Mode", this, "Normal", "Normal", "Legit", "Polar")
            .describe("Normal: vanilla (Grim-safe, no sneak). Legit: Polarware sneak. Polar: Rise wobble.");
    private final BooleanValue autoSwitch = new BooleanValue("Auto Switch", this, true)
            .describe("Swap to the fullest hotbar block stack before the held one runs out");
    private final BooleanValue tower = new BooleanValue("Tower", this, true)
            .describe("Place while jumping to tower (fixes towering).");
    private final BooleanValue safeWalk = new BooleanValue("SafeWalk", this, true)
            .describe("Prevent falling off edges (fixes falling).");
    private final redacted.client.value.impl.NumberValue speed = new redacted.client.value.impl.NumberValue("Speed", this, 1.0, 0.5, 3.0, 0.1)
            .describe("Blocks per second (slowish default). Higher = faster bridging, but flags more. Adjust to taste.");

    private boolean sneaked;
    private int ticksOverEdge;
    private long lastPlaceMs;
    private Target aimed;
    private BlockPos lastCell;
    private boolean settling, bridging;
    private float heldPitch = IDLE_PITCH, lockedYaw;
    private int bridgeY = Integer.MIN_VALUE, previousSlot = -1;
    private int ticksSincePlace;

    public Scaffold() {
        super("Scaffold", "Places blocks beneath you while bridging.", Keyboard.KEY_NONE, Category.WORLD);
        instance = this;
    }
    public static boolean isSprintSuppressed() { return instance != null && instance.isEnabled(); }

    @Override public void onEnable() {
        aimed = null; lastCell = null; previousSlot = -1; heldPitch = IDLE_PITCH; bridgeY = Integer.MIN_VALUE;
        settling = true; bridging = false; sneaked = false; ticksOverEdge = 0; lastPlaceMs = 0; ticksSincePlace = 0;
        RotationUtil.setMovementFix(true);
    }
    @Override public void onDisable() {
        aimed = null; settling = false; bridging = false; sneaked = false; ticksOverEdge = 0;
        if (previousSlot >= 0 && mc.thePlayer != null && mc.playerController != null) mc.playerController.setCurrentItemAndSync(previousSlot);
        previousSlot = -1; if (mc.thePlayer != null) mc.thePlayer.safeWalk = false;
        RotationUtil.setMovementFix(true); RotationUtil.beginRelease(ROTATION_SPEED, true);
    }

    @EventTarget public void onTick(EventTick event) {
        if (!event.isPre()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) { aimed = null; return; }
        RotationUtil.setMovementFix(true);
        if (mc.thePlayer.onGround || bridgeY == Integer.MIN_VALUE) bridgeY = new BlockPos(mc.thePlayer).down().getY();
        // Tower: allow bridging while jumping if tower enabled and we're going up
        boolean isTowering = tower.getValue() && mc.gameSettings.keyBindJump.isKeyDown() && mc.thePlayer.motionY > 0;
        // FIX: don't block on sneak - safeWalk sneak is what *prevents* falling on edge, but we still need to place while sneaking
        // Original had `&& !keyBindSneak.isKeyDown()` for Normal, which made it stop on edge when sneaking
        boolean canBridge = (isMoving() || isTowering) && !mc.thePlayer.isUsingItem() && ensureBlockSlot();
        bridging = canBridge;
        lockedYaw = getMovementYaw() - 180.0F;
        if (aimed != null) { if (bridging && !settling) placeAimed(); aimed = null; }
        RotationUtil.setRotationDirect(lockedYaw, heldPitch, ROTATION_SPEED);
        if (settling && RotationUtil.getRotationDifference(RotationUtil.getServerYaw(), RotationUtil.getServerPitch(), lockedYaw, heldPitch) <= SETTLE_ALIGN) settling = false;
        ticksSincePlace++;
        if (safeWalk.getValue() && bridging && mc.thePlayer != null) mc.thePlayer.safeWalk = true;
        else if (mc.thePlayer != null) mc.thePlayer.safeWalk = false;
    }

    @EventTarget(priority = 1) public void onMotion(final MotionEvent event) {
        if (!event.isPre() || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return;
        if ("Polar".equals(mode.getValue()) && bridging && !settling) {
            // Polar wobble (was 2 deg, now 0.7) to avoid Simulation
            if (ThreadLocalRandom.current().nextDouble() < 0.15) {
                float offsetYaw = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 0.7F;
                float offsetPitch = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 0.5F;
                lockedYaw += offsetYaw;
                heldPitch = RotationUtil.clampPitch(heldPitch + offsetPitch);
            }
        }
        final Target solved = bridging && !settling ? solve() : null;
        lastCell = new BlockPos(mc.thePlayer.posX, new BlockPos(mc.thePlayer).down().getY(), mc.thePlayer.posZ);
        if (solved != null) {
            heldPitch = solved.pitch;
            if ("Polar".equals(mode.getValue())) {
                RotationUtil.setRotationDirect(lockedYaw, heldPitch, ROTATION_SPEED);
            } else {
                RotationUtil.setRotationDirect(RotationUtil.getServerYaw(), heldPitch, ROTATION_SPEED);
            }
        }
        aimed = solved;
        if ("Legit".equals(mode.getValue())) {
            updateLegitSneak();
        } else if (sneaked) {
            sneaked = false; ticksOverEdge = 0;
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
    }
    @EventTarget public void onMoveInput(final EventMoveInput event) {
        if (settling) { event.setMoveForward(0.0F); event.setMoveStrafe(0.0F); event.setJump(false); return; }
        if ("Legit".equals(mode.getValue())) {
            if (bridging && sneaked) {
                if (ticksOverEdge <= 2) event.applyForcedSneakSlowdown();
                else event.setSneak(true);
            }
        }
        // Tower: allow jumping while bridging (don't zero jump)
        if (tower.getValue() && bridging && mc.gameSettings.keyBindJump.isKeyDown()) {
            // Keep jump true so tower works, but don't let settling kill it
        }
    }
    private void placeAimed() {
        // Speed control: blocks per second, default slow (1.0 = ~1 block/sec, 2.0 = ~2 blocks/sec)
        // Legit uses simple delay, others use fixed speed slider
        if ("Legit".equals(mode.getValue())) {
            long now = System.currentTimeMillis();
            if (now - lastPlaceMs < 100) return; // 100ms delay for legit
            lastPlaceMs = now;
        } else {
            // Normal/Polar: use speed slider (0.5-3.0 blocks/sec) -> delay = 1000/speed
            long now = System.currentTimeMillis();
            double s = speed.getValue();
            long delayMs = (long) (1000.0 / Math.max(0.5, s));
            // Add slight randomization to avoid DuplicateRotPlace (same delta)
            delayMs += ThreadLocalRandom.current().nextInt(-15, 16);
            if (now - lastPlaceMs < delayMs) return;
            lastPlaceMs = now;
        }
        final MovingObjectPosition hit = PlaceUtil.traceFace(aimed.support, aimed.face, RotationUtil.getServerYaw(), RotationUtil.getServerPitch(), PLACE_RANGE);
        if (hit == null || !PlaceUtil.isStillPlaceable(aimed.place, aimed.support, aimed.face, PLACE_RANGE)) {
            // On edge, traceFace can fail because yaw is locked to movement. Try with current yaw/pitch directly
            // This fixes the "bitches out and stops building" on edge - vanilla scaffold would still place
            final MovingObjectPosition hit2 = PlaceUtil.traceFace(aimed.support, aimed.face, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, PLACE_RANGE);
            if (hit2 == null || !PlaceUtil.isStillPlaceable(aimed.place, aimed.support, aimed.face, PLACE_RANGE)) return;
            // Use hit2 if hit failed
            final ItemStack stack2 = mc.thePlayer.inventory.getCurrentItem(); if (!PlaceUtil.isPlaceableBlock(stack2)) return;
            mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack2, aimed.support, aimed.face, hit2.hitVec);
            mc.thePlayer.swingItem();
            ticksSincePlace = 0;
            return;
        }
        final ItemStack stack = mc.thePlayer.inventory.getCurrentItem(); if (!PlaceUtil.isPlaceableBlock(stack)) return;
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack, aimed.support, aimed.face, hit.hitVec);
        mc.thePlayer.swingItem();
        ticksSincePlace = 0;
    }
    private Target solve() {
        final ItemStack held = mc.thePlayer.inventory.getCurrentItem(); final List<BlockPos> wanted = computeCandidates();
        final float basePitch = RotationUtil.getServerPitch(); final float yaw = RotationUtil.applyGCD(RotationUtil.getServerYaw(), lockedYaw);
        final float step = Math.max(RotationUtil.getGCD(), MIN_STEP);
        // For tower, we need to look more downwards (higher pitch) than for bridging
        boolean isTowering = tower.getValue() && mc.gameSettings.keyBindJump.isKeyDown();
        float maxP = isTowering ? 90.0F : MAX_PITCH;
        float minP = isTowering ? 70.0F : MIN_PITCH;
        for (float probe = maxP; probe >= minP; probe -= step) {
            final float pitch = RotationUtil.clampPitch(RotationUtil.applyGCD(basePitch, probe));
            final MovingObjectPosition hit = RaycastUtil.rayTraceBlocks(yaw, pitch, PLACE_RANGE);
            if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) continue;
            final BlockPos support = hit.getBlockPos(); final EnumFacing face = hit.sideHit; final BlockPos place = support.offset(face);
            if (!wanted.contains(place) || !wellInsideFace(hit, support) || !PlaceUtil.isReplaceable(place)) continue;
            if (!PlaceUtil.isValidSupport(support, PLACE_RANGE) || !PlaceUtil.canPlaceOnSide(support, face, held)) continue;
            return new Target(place, support, face, pitch);
        }
        return null;
    }
    private static boolean wellInsideFace(final MovingObjectPosition hit, final BlockPos support) {
        final double fx = hit.hitVec.xCoord - support.getX(), fy = hit.hitVec.yCoord - support.getY(), fz = hit.hitVec.zCoord - support.getZ();
        switch (hit.sideHit.getAxis()) {
            case X: return inset(fy) && inset(fz);
            case Y: return inset(fx) && inset(fz);
            default: return inset(fx) && inset(fy);
        }
    }
    private static boolean inset(final double coord) { return coord >= FACE_INSET && coord <= 1.0D - FACE_INSET; }

    private List<BlockPos> computeCandidates() {
        final int footY = new BlockPos(mc.thePlayer).down().getY(); final List<BlockPos> list = new ArrayList<>(6);
        // Tower: if jumping, place at footY (current) and bridgeY (start), and also one above if going up
        boolean isTowering = tower.getValue() && mc.gameSettings.keyBindJump.isKeyDown();
        final int high = Math.max(footY, bridgeY);
        if (isTowering) {
            // When towering, the next block is under your feet at footY, but also check one up if you're jumping
            addPlane(list, footY);
            if (footY != bridgeY) addPlane(list, bridgeY);
            // Also try one block above footY for tower (so you can place while going up)
            if (mc.thePlayer.motionY > 0) addPlane(list, footY + 1);
        } else {
            if (high == bridgeY || mc.thePlayer.onGround || mc.thePlayer.motionY <= 0.0D) addPlane(list, high);
            if (footY != bridgeY) addPlane(list, Math.min(footY, bridgeY));
        }
        return list;
    }
    private void addPlane(final List<BlockPos> list, final int planeY) {
        final BlockPos foot = new BlockPos(mc.thePlayer.posX, planeY, mc.thePlayer.posZ);
        if (lastCell != null && lastCell.getX() != foot.getX() && lastCell.getZ() != foot.getZ()) {
            list.add(new BlockPos(foot.getX(), planeY, lastCell.getZ())); list.add(new BlockPos(lastCell.getX(), planeY, foot.getZ()));
        }
        list.add(foot);
        // Fix edge: when on edge (e.g., x=0.9), foot is still block 0 but next block is at 1.
        // Add the block one ahead in movement direction, plus diagonal for strafe, so scaffold doesn't bitch out on edge.
        if (isMoving()) {
            float yawRad = (float) Math.toRadians(getMovementYaw());
            // 1.0 block ahead in movement direction - this is the block we *will* be on next tick
            double aheadX = mc.thePlayer.posX - Math.sin(yawRad) * 1.0;
            double aheadZ = mc.thePlayer.posZ + Math.cos(yawRad) * 1.0;
            BlockPos ahead = new BlockPos(aheadX, planeY, aheadZ);
            if (!list.contains(ahead)) list.add(ahead);
            // Also add 0.6 ahead for diagonal/strafe (fixes falling when strafing)
            double nearAheadX = mc.thePlayer.posX - Math.sin(yawRad) * 0.6;
            double nearAheadZ = mc.thePlayer.posZ + Math.cos(yawRad) * 0.6;
            BlockPos nearAhead = new BlockPos(nearAheadX, planeY, nearAheadZ);
            if (!list.contains(nearAhead)) list.add(nearAhead);
            // When on edge, also add the block directly under the player's *next* foot position (fixes tower on edge)
            double edgeX = mc.thePlayer.posX + mc.thePlayer.motionX * 2;
            double edgeZ = mc.thePlayer.posZ + mc.thePlayer.motionZ * 2;
            BlockPos edge = new BlockPos(edgeX, planeY, edgeZ);
            if (!list.contains(edge)) list.add(edge);
        } else {
            // Not moving but still need to handle edge for tower: add block under player even if not moving
            if (tower.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
                BlockPos towerPos = new BlockPos(mc.thePlayer.posX, planeY, mc.thePlayer.posZ);
                if (!list.contains(towerPos)) list.add(towerPos);
            }
        }
    }
    private boolean ensureBlockSlot() {
        final ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (!autoSwitch.getValue()) return PlaceUtil.isPlaceableBlock(held);
        if (PlaceUtil.isPlaceableBlock(held) && held.stackSize > LOW_BLOCKS) return true;
        int best = -1, bestCount = LOW_BLOCKS;
        for (int slot = 0; slot < InventoryUtil.HOTBAR_SIZE; slot++) {
            final ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (PlaceUtil.isPlaceableBlock(stack) && stack.stackSize > bestCount) { best = slot; bestCount = stack.stackSize; }
        }
        if (best < 0) return PlaceUtil.isPlaceableBlock(held);
        if (best == mc.thePlayer.inventory.currentItem) return true;
        if (previousSlot < 0) previousSlot = mc.thePlayer.inventory.currentItem;
        mc.playerController.setCurrentItemAndSync(best); return true;
    }
    private boolean isMoving() {
        return mc.gameSettings.keyBindForward.isKeyDown() || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown() || mc.gameSettings.keyBindRight.isKeyDown();
    }
    private float getMovementYaw() {
        final float forward = (mc.gameSettings.keyBindForward.isKeyDown() ? 1.0F : 0.0F) - (mc.gameSettings.keyBindBack.isKeyDown() ? 1.0F : 0.0F);
        final float strafe = (mc.gameSettings.keyBindLeft.isKeyDown() ? 1.0F : 0.0F) - (mc.gameSettings.keyBindRight.isKeyDown() ? 1.0F : 0.0F);
        if (forward == 0.0F && strafe == 0.0F) return mc.thePlayer.rotationYaw;
        return mc.thePlayer.rotationYaw + (float) Math.toDegrees(Math.atan2(-strafe, forward));
    }

    private boolean isDiagonal() {
        boolean fwd = mc.gameSettings.keyBindForward.isKeyDown() || mc.gameSettings.keyBindBack.isKeyDown();
        boolean strafe = mc.gameSettings.keyBindLeft.isKeyDown() || mc.gameSettings.keyBindRight.isKeyDown();
        return fwd && strafe;
    }

    private void updateLegitSneak() {
        boolean overAir = false;
        try {
            BlockPos under = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ);
            overAir = mc.theWorld.getBlockState(under).getBlock().getMaterial().isReplaceable() || mc.theWorld.isAirBlock(under);
            if (!overAir && isMoving()) {
                float yawRad = (float) Math.toRadians(getMovementYaw());
                double aheadX = mc.thePlayer.posX - Math.sin(yawRad) * 0.5;
                double aheadZ = mc.thePlayer.posZ + Math.cos(yawRad) * 0.5;
                BlockPos aheadUnder = new BlockPos(aheadX, mc.thePlayer.posY - 1, aheadZ);
                overAir = mc.theWorld.isAirBlock(aheadUnder);
            }
        } catch (Throwable ignored) {}
        boolean shouldSneak = bridging && overAir && !mc.gameSettings.keyBindSneak.isKeyDown();
        if (shouldSneak && !sneaked) sneaked = true;
        else if (!shouldSneak && sneaked) sneaked = false;
        if (sneaked) ticksOverEdge++; else ticksOverEdge = 0;
        if (sneaked) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
    }

    private static final class Target {
        private final BlockPos place, support; private final EnumFacing face; private final float pitch;
        private Target(BlockPos place, BlockPos support, EnumFacing face, float pitch) {
            this.place = place; this.support = support; this.face = face; this.pitch = pitch;
        }
    }
}
