package redacted.client.module.impl.render;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.lwjgl.opengl.GL11;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.PacketEvent;
import redacted.client.event.impl.Render2DEvent;
import redacted.client.event.impl.Render3DEvent;
import redacted.client.font.Fonts;
import redacted.client.font.Weight;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.module.impl.world.BedProtection;
import redacted.client.module.impl.world.Scaffold;
import redacted.client.util.animation.Easing;
import redacted.client.util.font.Font;
import redacted.client.util.player.InventoryUtil;
import redacted.client.util.render.ColorUtil;
import redacted.client.util.render.ESPUtil;
import redacted.client.util.render.EntityColorUtil;
import redacted.client.util.render.Render3DUtil;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ColorValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

// One module, five sub-features. Each owner toggle reveals its own child settings (dependency-gated
// in the GUI) and gates its own packet/tick/render work. Everything off by default -- enabling the
// module renders nothing until you turn on a category. Behavior of the old BedESP/ChestESP/ItemESP/
// ItemCounter/BlockEffect is ported faithfully, just namespaced and folded under one class.
public class BlockESP extends Module {
    private static BlockESP instance;

    // Beds
    private final BooleanValue bedEsp     = new BooleanValue("Beds", this, false).describe("Box every bed in the world");
    private final ColorValue bedColor     = new ColorValue("Bed Color", this, 0xFFFF3232, bedEsp, true).describe("Bed box color");
    private final NumberValue bedAlpha    = new NumberValue("Bed Alpha",      this, 120,  0, 255, 1,    bedEsp, true).describe("Bed box opacity");
    private final NumberValue bedLineWidth = new NumberValue("Bed Line Width", this, 2.0D, 0.5D, 5.0D, 0.5D, bedEsp, true).describe("Bed box outline thickness");

    // Chests
    private final BooleanValue chestEsp   = new BooleanValue("Chests", this, false).describe("Box every chest (doubles merge into one)");
    private final ModeValue chestMode     = new ModeValue("Chest Mode",  this, chestEsp, true, "Outline", "Outline", "Filled").describe("Outline draws just edges, Filled tints the whole box");
    private final ModeValue chestColorMode = new ModeValue("Chest Color", this, chestEsp, true, "Gold", "Gold", "Custom", "Sync").describe("Gold, your Custom RGB, or rainbow Sync");
    private final ColorValue chestColor   = new ColorValue("Chest Color Picker", this, 0xFFFFAA00, chestColorMode, "Custom").describe("Chest box color when Custom");
    private final NumberValue chestRange  = new NumberValue("Chest Range", this, 64.0, 16.0, 256.0, 1.0, chestEsp, true).describe("How far out chests still draw");
    private final NumberValue chestLineWidth = new NumberValue("Chest Line Width", this, 2.0D, 0.5D, 5.0D, 0.5D, chestEsp, true).describe("Chest box outline thickness");

    // Items
    private final BooleanValue itemEsp    = new BooleanValue("Items", this, false).describe("Highlight dropped items on the ground");
    private final ModeValue itemMode      = new ModeValue("Item Mode",  this, itemEsp, true, "Box", "Box", "Chams").describe("Box draws an outline, Chams draws the item model through walls");
    private final ModeValue itemColorMode = new ModeValue("Item Color", this, itemEsp, true, "Rarity", "Rarity", "Custom", "Random", "Sync").describe("Rarity tint, Custom RGB, Random per-item, or Sync");
    private final ColorValue itemColor    = new ColorValue("Item Color Picker", this, 0xFFFFC832, itemColorMode, "Custom").describe("Item box color when Custom");
    private final NumberValue itemAlpha   = new NumberValue("Item Alpha", this, 120, 0, 255, 1, itemEsp, true).describe("Item box opacity");
    private final NumberValue itemSize    = new NumberValue("Item Size",  this, 1.0, 0.5, 3.0, 0.1, itemEsp, true).describe("Item box size (grows with distance)");
    private final NumberValue itemRange   = new NumberValue("Item Range", this, 32.0, 5.0, 256.0, 1.0, itemEsp, true).describe("How far out dropped items still draw");
    private final NumberValue itemMinCount = new NumberValue("Item Min Count", this, 1, 1, 64, 1, itemEsp, true).describe("Hide piles below this stack total");
    private final BooleanValue itemValuablesOnly = new BooleanValue("Item Valuables Only", this, false, itemEsp, true).describe("Only show non-common rarity drops");
    private final BooleanValue itemCount  = new BooleanValue("Item Count", this, true, itemEsp, true).describe("Show the stack total over the pile");
    private final BooleanValue itemIcon   = new BooleanValue("Item Icon", this, true, itemEsp, true).describe("Show a floating item icon over the drop");
    private final BooleanValue itemTracers = new BooleanValue("Item Tracers", this, false, itemEsp, true).describe("Draw a line to each dropped item");

    // Held Count (2D HUD)
    private final BooleanValue heldCount  = new BooleanValue("Held Count", this, false).describe("Show your held block count under the crosshair");
    private final NumberValue heldScale   = new NumberValue("Held Count Scale",    this, 1.0, 0.5, 3.0, 0.1, heldCount, true).describe("Held-count icon and text size");
    private final NumberValue heldYOffset = new NumberValue("Held Count Y Offset", this, 20,  0,   60,  1,   heldCount, true).describe("Gap below the crosshair for the held count");

    // Place Effect
    private final BooleanValue placeEffect = new BooleanValue("Place Effect", this, false).describe("Flash a fading box where you place a block");
    private final ModeValue placeMode      = new ModeValue("Place Mode",  this, placeEffect, true, "Both", "Both", "Filled", "Outline").describe("Both, Filled tint only, or Outline only");
    private final ModeValue placeColorMode = new ModeValue("Place Color", this, placeEffect, true, "Custom", "Custom", "Rainbow", "Accent").describe("Custom picker, rainbow Rainbow, or GUI Accent");
    private final ColorValue placeColor    = new ColorValue("Place Color Picker", this, 0xFF55FFFF, placeColorMode, "Custom").describe("Color of the place flash when Custom");
    private final NumberValue placeFadeTime = new NumberValue("Place Fade Time", this, 1000.0, 100.0, 5000.0, 50.0, placeEffect, true).describe("How long the place flash lingers (ms)");
    private final NumberValue placeFillOpacity = new NumberValue("Place Fill Opacity", this, 90.0, 0.0, 255.0, 1.0, placeEffect, true).describe("Place flash fill opacity");
    private final NumberValue placeOutlineOpacity = new NumberValue("Place Outline Opacity", this, 255.0, 0.0, 255.0, 1.0, placeEffect, true).describe("Place flash outline opacity");
    private final NumberValue placeLineWidth = new NumberValue("Place Line Width", this, 2.0, 0.5, 5.0, 0.5, placeEffect, true).describe("Place flash outline thickness");
    private final ModeValue placeFadeCurve = new ModeValue("Place Fade Curve", this, placeEffect, true, "Ease Out", "Ease Out", "Linear", "Expo", "Quint").describe("Ease Out, Linear, Expo, or Quint fade shape");
    private final BooleanValue placeModulePlacements = new BooleanValue("Place Module Placements", this, true, placeEffect, true).describe("Also flash blocks placed by Scaffold/BedProtection");

    // bed state (packet-driven, see BedESP threading notes)
    private static final double BED_HEIGHT = 0.5625D; // matches BlockBed bounds
    private static final int BED_ID = Block.getIdFromBlock(Blocks.bed);
    private static final int CHUNKS_PER_TICK = 32;

    private final Map<BlockPos, AxisAlignedBB> beds      = new HashMap<>(); // live set; render reads values(), main thread only
    private final Map<BlockPos, BlockPos>      cellOwner = new HashMap<>(); // head cell AND foot cell -> head, for O(1) removal
    private final Map<BlockPos, BlockPos>      footOf    = new HashMap<>(); // head -> foot cell
    private final ArrayDeque<Long>             pending   = new ArrayDeque<>(); // chunk columns awaiting a scan
    private final Queue<Action>                inbox     = new ConcurrentLinkedQueue<>(); // ONLY cross-thread bed structure
    private WorldClient lastWorld;

    // chest state (20-tick poll)
    private static final Color CHEST = new Color(255, 170, 0);
    private static final int FILL_ALPHA = 55;
    private final List<AxisAlignedBB> chestBoxes = new ArrayList<>();
    private int scanCooldown;

    // item state
    private static final double FAR_SCALE = 3.0; // box grows toward this x as a drop nears the range edge
    private final Map<Entity, Color> randomColors = new WeakHashMap<>();
    private final Set<EntityItem> visible = new HashSet<>();

    // place state (Netty thread writes, main thread reads -> concurrent)
    private final Map<BlockPos, Long> placedBlocks = new ConcurrentHashMap<>();

    // owner-edge tracking so each sub-feature seeds/clears its own state on toggle
    private boolean prevBeds, prevChests, prevItems, prevPlace;

    public BlockESP() {
        super("BlockESP", "Block & item ESP suite", Keyboard.KEY_NONE, Category.RENDER);
        instance = this;
    }

    @Override
    public void onDisable() {
        clearBeds();
        lastWorld = null;
        chestBoxes.clear();
        visible.clear();
        randomColors.clear();
        placedBlocks.clear();
        prevBeds = prevChests = prevItems = prevPlace = false;
    }

    // RenderEntityItem skips the vanilla draw for these so our through-wall chams is the only render.
    public static boolean chamsThroughWalls(EntityItem item) {
        BlockESP m = instance;
        return m != null && m.isEnabled() && m.itemEsp.getValue()
                && m.itemMode.getValue().equals("Chams") && m.visible.contains(item);
    }

    // packets (Netty IO thread)
    // Read off the packet, enqueue only. Never read the world, never mutate the live maps, never cancel.
    @EventTarget
    public void onPacket(PacketEvent event) {
        PacketEvent.Direction direction = event.getDirection();
        Packet packet = event.getPacket();

        if (direction == PacketEvent.Direction.INBOUND && bedEsp.getValue()) {
            if (packet instanceof S22PacketMultiBlockChange) {
                for (S22PacketMultiBlockChange.BlockUpdateData d : ((S22PacketMultiBlockChange) packet).getChangedBlocks()) {
                    enqueueCellChange(d.getPos(), d.getBlockState());
                }
            } else if (packet instanceof S23PacketBlockChange) {
                S23PacketBlockChange c = (S23PacketBlockChange) packet;
                enqueueCellChange(c.getBlockPosition(), c.getBlockState());
            } else if (packet instanceof S26PacketMapChunkBulk) {
                S26PacketMapChunkBulk b = (S26PacketMapChunkBulk) packet;
                for (int i = 0; i < b.getChunkCount(); i++) inbox.offer(Action.scan(b.getChunkX(i), b.getChunkZ(i)));
            } else if (packet instanceof S21PacketChunkData) {
                S21PacketChunkData c = (S21PacketChunkData) packet;
                // func_149274_i() && extractedSize == 0 is the chunk-unload marker, otherwise it's a load
                if (c.func_149274_i() && c.getExtractedSize() == 0) inbox.offer(Action.removeChunk(c.getChunkX(), c.getChunkZ()));
                else inbox.offer(Action.scan(c.getChunkX(), c.getChunkZ()));
            }
        } else if (direction == PacketEvent.Direction.OUTBOUND && placeEffect.getValue()) {
            if (!(packet instanceof C08PacketPlayerBlockPlacement)) return;
            C08PacketPlayerBlockPlacement pkt = (C08PacketPlayerBlockPlacement) packet;

            int dir = pkt.getPlacedBlockDirection();
            if (dir == 255) return; // right-click-in-air sentinel

            // eating / buckets / tools also send C08 -- only track real block placements
            if (!InventoryUtil.isBlockStack(pkt.getStack())) return;

            if (!placeModulePlacements.getValue()) {
                Scaffold scaffold = Client.getInstance().getModuleManager().getModule(Scaffold.class);
                BedProtection bedProt = Client.getInstance().getModuleManager().getModule(BedProtection.class);
                if ((scaffold != null && scaffold.isEnabled()) || (bedProt != null && bedProt.isEnabled())) return;
            }

            // pure packet math, no world access -- safe on the Netty thread
            BlockPos placedPos = pkt.getPosition().offset(EnumFacing.getFront(dir));
            placedBlocks.put(placedPos, System.currentTimeMillis()); // re-place refreshes the timer
        }
    }

    // tick (main thread): bed map writes, chest poll, owner-edge seed/clear
    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre() || mc.theWorld == null || mc.thePlayer == null) return;

        boolean bedsOn = bedEsp.getValue();
        if (bedsOn) {
            // rising edge or new WorldClient (join / respawn / dimension swap): wipe and re-seed from loaded chunks,
            // since packets only catch changes after this point
            if (!prevBeds || mc.theWorld != lastWorld) {
                clearBeds();
                lastWorld = mc.theWorld;
                seedBackfill();
            }
            for (Action a; (a = inbox.poll()) != null; ) apply(a);
            for (int budget = 0; budget < CHUNKS_PER_TICK && !pending.isEmpty(); budget++) {
                long key = pending.poll();
                scanChunk((int) (key >> 32), (int) key);
            }
        } else if (prevBeds) {
            clearBeds();
            lastWorld = null;
        }
        prevBeds = bedsOn;

        boolean chestsOn = chestEsp.getValue();
        if (chestsOn) {
            if (!prevChests) scanCooldown = 0; // rising edge: scan this tick
            if (--scanCooldown <= 0) {
                scanCooldown = 20;
                collectChestBoxes();
            }
        } else if (prevChests) {
            chestBoxes.clear();
        }
        prevChests = chestsOn;

        boolean itemsOn = itemEsp.getValue();
        if (!itemsOn && prevItems) { // handlers stay registered now, so clear here on the falling edge
            visible.clear();
            randomColors.clear();
        }
        prevItems = itemsOn;

        boolean placeOn = placeEffect.getValue();
        if (!placeOn && prevPlace) placedBlocks.clear();
        prevPlace = placeOn;
    }

    // in-world render: each section keeps its own GL discipline (start3D/end3D)
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (bedEsp.getValue()) renderBeds();
        if (chestEsp.getValue()) renderChests();
        if (itemEsp.getValue()) renderItems(event.getPartialTicks());
        if (placeEffect.getValue()) renderPlaceEffect();
    }

    // 2D HUD: held block + count under the crosshair
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!heldCount.getValue() || mc.thePlayer == null) return;
        if (mc.gameSettings.showDebugInfo) return;

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (!InventoryUtil.isBlockStack(held)) return; // blocks only

        ScaledResolution sr = event.getScaledResolution();
        float cx = sr.getScaledWidth()  / 2f;
        float cy = sr.getScaledHeight() / 2f;

        float s    = heldScale.floatValue() * 1.6f;  // 1.6x base so the block + count read clearly under the crosshair
        float topY = cy + heldYOffset.floatValue();  // top of the count text, a fixed gap below the crosshair

        // count is the top element so it never creeps up over the crosshair; block hangs below it
        int color   = ColorUtil.healthColor(held.stackSize, held.getMaxStackSize());
        Font font   = Fonts.MAIN.get(Fonts.size(13 * s), Weight.REGULAR);
        String text = String.valueOf(held.stackSize);

        float iconTop = topY + font.height() + 2f;
        int ix = (int) (cx - 8);
        int iy = (int) iconTop;

        RenderItem ri = mc.getRenderItem();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);

        // scale the 16x16 icon about its top-centre so it stays centred on cx and grows downward
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, iconTop, 0f);
        GlStateManager.scale(s, s, 1f);
        GlStateManager.translate(-cx, -iconTop, 0f);
        ri.renderItemAndEffectIntoGUI(held, ix, iy);
        GlStateManager.popMatrix();

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();

        font.drawWithShadow(text, cx - font.width(text) / 2f, topY, color);
    }

    // BEDS

    private void renderBeds() {
        if (beds.isEmpty()) return;

        Render3DUtil.start3D();
        GL11.glLineWidth(bedLineWidth.floatValue());

        double[] viewer = Render3DUtil.viewerOffset();
        double vx = viewer[0], vy = viewer[1], vz = viewer[2];

        ColorUtil.glColor(ColorUtil.withAlpha(bedColor.getRGB(), bedAlpha.intValue()));

        for (AxisAlignedBB box : beds.values()) {
            Render3DUtil.drawBoundingBox(box.offset(-vx, -vy, -vz));
        }

        Render3DUtil.end3D();
    }

    private void clearBeds() {
        beds.clear();
        cellOwner.clear();
        footOf.clear();
        pending.clear();
        inbox.clear();
    }

    private void enqueueCellChange(BlockPos pos, IBlockState state) {
        if (state != null && state.getBlock() instanceof BlockBed) {
            // only the head carries the box; a foot-half update isn't a removal (the foot cell points at the head)
            if (state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD) {
                BlockPos foot = footOf(state, pos);
                inbox.offer(Action.addBed(pos, foot, bedBox(pos, foot)));
            }
            return;
        }
        // air or any non-bed block at this cell evicts whatever bed owned it (head or foot)
        inbox.offer(Action.removeAt(pos));
    }

    private void apply(Action a) {
        switch (a.tag) {
            case ADD_BED:      putBed(a.head, a.foot, a.box); break;
            case REMOVE_AT:    removeAt(a.cell);              break;
            case SCAN_CHUNK:   pending.add(pack(a.cx, a.cz)); break;
            case REMOVE_CHUNK: removeChunk(a.cx, a.cz);       break;
        }
    }

    private void putBed(BlockPos head, BlockPos foot, AxisAlignedBB box) {
        BlockPos oldFoot = footOf.get(head); // rotated re-place on the same head: drop the stale foot index
        if (oldFoot != null && !oldFoot.equals(foot)) cellOwner.remove(oldFoot);
        beds.put(head, box);
        footOf.put(head, foot);
        cellOwner.put(head, head);
        cellOwner.put(foot, head);
    }

    private void removeAt(BlockPos cell) {
        BlockPos head = cellOwner.get(cell);
        if (head == null) return;
        beds.remove(head);
        cellOwner.remove(head);
        BlockPos foot = footOf.remove(head);
        if (foot != null) cellOwner.remove(foot);
    }

    private void removeChunk(int cx, int cz) {
        // collect-then-remove so we don't mutate beds while iterating its keySet; n per column is tiny
        List<BlockPos> hit = new ArrayList<>();
        for (BlockPos h : beds.keySet()) if ((h.getX() >> 4) == cx && (h.getZ() >> 4) == cz) hit.add(h);
        for (BlockPos h : hit) removeAt(h);
    }

    // One-shot ring walk over the current render distance (nearest-first) that just queues columns; no continuous rescan.
    private void seedBackfill() {
        int maxRadius = mc.gameSettings.renderDistanceChunks + 1;
        int ccx = mc.thePlayer.chunkCoordX;
        int ccz = mc.thePlayer.chunkCoordZ;
        for (int ring = 0; ring <= maxRadius; ring++) {
            if (ring == 0) {
                pending.add(pack(ccx, ccz));
                continue;
            }
            for (int pos = 0; pos < 8 * ring; pos++) {
                int side = 2 * ring;
                int t = pos % side;
                int dx, dz;
                switch (pos / side) {
                    case 0:  dx = -ring + t; dz = -ring;     break; // top, left to right
                    case 1:  dx =  ring;     dz = -ring + t; break; // right, top to bottom
                    case 2:  dx =  ring - t; dz =  ring;     break; // bottom, right to left
                    default: dx = -ring;     dz =  ring - t; break; // left, bottom to top
                }
                pending.add(pack(ccx + dx, ccz + dz));
            }
        }
    }

    // Reconcile: wipe the column, then re-add what's actually there, so a re-sent/partial chunk converges to truth.
    private void scanChunk(int cx, int cz) {
        removeChunk(cx, cz);

        Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
        if (chunk == null || !chunk.isLoaded()) return;

        for (ExtendedBlockStorage section : chunk.getBlockStorageArray()) {
            if (section == null || section.isEmpty()) continue;

            char[] data = section.getData();
            int baseY = section.getYLocation();

            for (int i = 0; i < data.length; i++) {
                int stateId = data[i];
                if ((stateId >> 4) != BED_ID) continue; // cheap reject before touching the palette

                IBlockState state = (IBlockState) Block.BLOCK_STATE_IDS.getByValue(stateId);
                if (state == null || !(state.getBlock() instanceof BlockBed)) continue;
                if (state.getValue(BlockBed.PART) != BlockBed.EnumPartType.HEAD) continue;

                // index packs as y << 8 | z << 4 | x
                BlockPos head = new BlockPos(cx * 16 + (i & 15), baseY + ((i >> 8) & 15), cz * 16 + ((i >> 4) & 15));
                BlockPos foot = footOf(state, head);
                putBed(head, foot, bedBox(head, foot));
            }
        }
    }

    private static BlockPos footOf(IBlockState headState, BlockPos head) {
        EnumFacing toFoot = ((EnumFacing) headState.getValue(BlockDirectional.FACING)).getOpposite();
        return new BlockPos(head.getX() + toFoot.getFrontOffsetX(), head.getY(), head.getZ() + toFoot.getFrontOffsetZ());
    }

    private static AxisAlignedBB bedBox(BlockPos h, BlockPos f) {
        return new AxisAlignedBB(
            Math.min(h.getX(), f.getX()),     h.getY(),               Math.min(h.getZ(), f.getZ()),
            Math.max(h.getX(), f.getX()) + 1, h.getY() + BED_HEIGHT,  Math.max(h.getZ(), f.getZ()) + 1
        );
    }

    private static long pack(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private static final class Action {
        enum Tag { ADD_BED, REMOVE_AT, SCAN_CHUNK, REMOVE_CHUNK }

        final Tag tag;
        final BlockPos head, foot, cell;
        final AxisAlignedBB box;
        final int cx, cz;

        private Action(Tag tag, BlockPos head, BlockPos foot, BlockPos cell, AxisAlignedBB box, int cx, int cz) {
            this.tag = tag;
            this.head = head;
            this.foot = foot;
            this.cell = cell;
            this.box = box;
            this.cx = cx;
            this.cz = cz;
        }

        static Action addBed(BlockPos h, BlockPos f, AxisAlignedBB b) { return new Action(Tag.ADD_BED, h, f, null, b, 0, 0); }
        static Action removeAt(BlockPos c)                            { return new Action(Tag.REMOVE_AT, null, null, c, null, 0, 0); }
        static Action scan(int cx, int cz)                            { return new Action(Tag.SCAN_CHUNK, null, null, null, null, cx, cz); }
        static Action removeChunk(int cx, int cz)                     { return new Action(Tag.REMOVE_CHUNK, null, null, null, null, cx, cz); }
    }

    // CHESTS

    private void renderChests() {
        if (chestBoxes.isEmpty()) return;

        Render3DUtil.start3D();
        GL11.glLineWidth(chestLineWidth.floatValue());

        double[] view = Render3DUtil.viewerOffset();
        Color base = chestBaseColor();
        boolean filled = chestMode.getValue().equals("Filled");

        double rangeSq = chestRange.getValue() * chestRange.getValue();
        double px = mc.thePlayer.posX, py = mc.thePlayer.posY, pz = mc.thePlayer.posZ;

        for (AxisAlignedBB box : chestBoxes) {
            double cx = (box.minX + box.maxX) * 0.5;
            double cy = (box.minY + box.maxY) * 0.5;
            double cz = (box.minZ + box.maxZ) * 0.5;
            double dx = cx - px, dy = cy - py, dz = cz - pz;
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

            AxisAlignedBB bb = box.offset(-view[0], -view[1], -view[2]);
            Render3DUtil.drawBox(bb,
                filled ? ColorUtil.withAlpha(base, FILL_ALPHA).getRGB() : 0,
                base.getRGB());
        }

        Render3DUtil.end3D();
    }

    private Color chestBaseColor() {
        switch (chestColorMode.getValue()) {
            case "Custom":
                return new Color(chestColor.getRGB());
            case "Sync":
                return ColorUtil.sync();
            default:
                return CHEST;
        }
    }

    // Each chest uses its own block selection bounds to hug the model; double chests merge into one box (no seam).
    private void collectChestBoxes() {
        chestBoxes.clear();
        if (mc.theWorld == null) return;

        for (Object obj : mc.theWorld.loadedTileEntityList) {
            if (!(obj instanceof TileEntityChest)) continue;
            BlockPos pos = ((TileEntity) obj).getPos();

            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (!(block instanceof BlockChest)) continue;

            BlockPos other = adjacentChest(pos, block);
            if (other == null) {
                chestBoxes.add(chestBox(pos, block).expand(0.002D, 0.002D, 0.002D));
                continue;
            }

            // only the lower-coordinate half emits, so a double chest is drawn once
            boolean primary = pos.getX() < other.getX() || (pos.getX() == other.getX() && pos.getZ() < other.getZ());
            if (!primary) continue;

            AxisAlignedBB a = chestBox(pos, block);
            AxisAlignedBB b = chestBox(other, block);
            chestBoxes.add(new AxisAlignedBB(
                Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ),
                Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ)
            ).expand(0.002D, 0.002D, 0.002D));
        }
    }

    private AxisAlignedBB chestBox(BlockPos pos, Block block) {
        block.setBlockBoundsBasedOnState(mc.theWorld, pos);
        return block.getSelectedBoundingBox(mc.theWorld, pos);
    }

    private BlockPos adjacentChest(BlockPos pos, Block block) {
        if (mc.theWorld.getBlockState(pos.north()).getBlock() == block) return pos.north();
        if (mc.theWorld.getBlockState(pos.south()).getBlock() == block) return pos.south();
        if (mc.theWorld.getBlockState(pos.west()).getBlock() == block) return pos.west();
        if (mc.theWorld.getBlockState(pos.east()).getBlock() == block) return pos.east();
        return null;
    }

    // ITEMS

    private void renderItems(float pt) {
        visible.clear();

        double rangeSq = itemRange.getValue() * itemRange.getValue();

        List<EntityItem> items = new ArrayList<>();
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityItem)) continue;
            EntityItem item = (EntityItem) obj;
            ItemStack stack = item.getEntityItem();
            if (stack == null || stack.getItem() == null) continue;
            if (mc.thePlayer.getDistanceSqToEntity(item) > rangeSq) continue;
            items.add(item);
        }
        if (items.isEmpty()) return;

        List<Cluster> clusters = cluster(items, pt);
        if (clusters.isEmpty()) return;

        boolean chamsMode = itemMode.getValue().equals("Chams");
        boolean boxMode = itemMode.getValue().equals("Box");

        // Chams renders the real item model itself (depth off) instead of leaning on the vanilla entity pass,
        // which culls items past ~16 blocks and behind walls. RenderEntityItem skips these so nothing double-draws.
        if (chamsMode) renderChams(clusters, pt);

        if (boxMode || itemTracers.getValue()) {
            Render3DUtil.start3D();
            GL11.glLineWidth(2.0F);

            double[] start = Render3DUtil.tracerStart(pt);

            for (Cluster c : clusters) {
                if (boxMode) {
                    for (EntityItem item : c.items) {
                        double[] p = ESPUtil.getInterpolatedPos(item, pt);
                        double dist = mc.thePlayer.getDistanceToEntity(item);
                        float intensity = Math.max(0.2F, 1.0F - (float) (dist / itemRange.getValue()));
                        int a = (int) (itemAlpha.intValue() * intensity);
                        double hw = 0.2 * sizeScale(dist);  // half width, scaled by Size + distance
                        double ht = 0.4 * sizeScale(dist);  // height
                        AxisAlignedBB bb = new AxisAlignedBB(
                                p[0] - hw, p[1], p[2] - hw,
                                p[0] + hw, p[1] + ht, p[2] + hw
                        );
                        Render3DUtil.drawBox(bb,
                                ColorUtil.withAlpha(c.color, a).getRGB(),
                                ColorUtil.withAlpha(c.color, Math.min(255, a + 60)).getRGB());
                    }
                }

                if (itemTracers.getValue()) {
                    ColorUtil.glColor(ColorUtil.withAlpha(c.color, 180));
                    Render3DUtil.drawLine(start[0], start[1], start[2], c.x, c.y + 0.2, c.z);
                }
            }

            Render3DUtil.end3D();
        }

        // Chams already draws the real item through walls, so the floating icon would just stack on top of it.
        boolean showIcons = itemIcon.getValue() && !chamsMode;
        if (showIcons || itemCount.getValue()) {
            for (Cluster c : clusters) {
                boolean showCount = itemCount.getValue() && c.total > 1;
                if (showIcons || showCount) drawItemLabel(c, showIcons, showCount);
            }
        }
    }

    private void renderChams(List<Cluster> clusters, float pt) {
        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        RenderHelper.enableStandardItemLighting();
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        for (Cluster c : clusters) {
            for (EntityItem item : c.items) {
                ItemStack stack = item.getEntityItem();
                IBakedModel model = mc.getRenderItem().getItemModelMesher().getItemModel(stack);
                double[] p = ESPUtil.getInterpolatedPos(item, pt);
                float s = sizeScale(mc.thePlayer.getDistanceToEntity(item));

                GlStateManager.pushMatrix();
                GlStateManager.translate((float) p[0], (float) p[1] + 0.18F, (float) p[2]);
                GlStateManager.scale(s, s, s);
                if (model.isGui3d()) GlStateManager.scale(0.5F, 0.5F, 0.5F); // matches vanilla dropped-block scale
                model.getItemCameraTransforms().applyTransform(ItemCameraTransforms.TransformType.GROUND);
                mc.getRenderItem().renderItem(stack, model);
                GlStateManager.popMatrix();
            }
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    // 1x at the player's feet, smoothly up to Size * FAR_SCALE at the range edge.
    private float sizeScale(double dist) {
        double t = MathHelper.clamp_double(dist / itemRange.getValue(), 0.0, 1.0);
        return (float) (itemSize.getValue() * (1.0 + (FAR_SCALE - 1.0) * t));
    }

    // Transitive flood-fill: same-type drops chain together as long as each hop stays within a 3x3x3 block region.
    private List<Cluster> cluster(List<EntityItem> items, float pt) {
        List<Cluster> clusters = new ArrayList<>();
        boolean[] visited = new boolean[items.size()];

        for (int i = 0; i < items.size(); i++) {
            if (visited[i]) continue;

            List<EntityItem> group = new ArrayList<>();
            List<Integer> queue = new ArrayList<>();
            queue.add(i);
            visited[i] = true;

            while (!queue.isEmpty()) {
                EntityItem kItem = items.get(queue.remove(queue.size() - 1));
                group.add(kItem);
                for (int j = 0; j < items.size(); j++) {
                    if (visited[j]) continue;
                    EntityItem jItem = items.get(j);
                    if (!near(kItem, jItem)) continue;
                    if (!ItemStack.areItemsEqual(kItem.getEntityItem(), jItem.getEntityItem())) continue;
                    visited[j] = true;
                    queue.add(j);
                }
            }

            int total = 0;
            double cx = 0, cy = 0, cz = 0;
            for (EntityItem item : group) {
                total += item.getEntityItem().stackSize;
                double[] p = ESPUtil.getInterpolatedPos(item, pt);
                cx += p[0];
                cy += p[1];
                cz += p[2];
            }

            // pick a stable representative (lowest entity id) so Random color and rarity don't flip with BFS seed order
            EntityItem rep = group.get(0);
            for (EntityItem item : group) {
                if (item.getEntityId() < rep.getEntityId()) rep = item;
            }
            ItemStack stack = rep.getEntityItem();
            if (itemValuablesOnly.getValue() && stack.getRarity() == EnumRarity.COMMON) continue;
            if (total < itemMinCount.intValue()) continue;

            int n = group.size();
            visible.addAll(group);
            clusters.add(new Cluster(group, stack, total, cx / n, cy / n, cz / n, itemResolveColor(rep, stack)));
        }

        return clusters;
    }

    private Color itemResolveColor(EntityItem rep, ItemStack stack) {
        // Rarity is item-specific; everything else is the shared entity-color policy
        if (itemColorMode.getValue().equals("Rarity")) return rarityColor(stack);
        Color c = new Color(itemColor.getRGB());
        return EntityColorUtil.resolve(rep, itemColorMode.getValue(), 255, c.getRed(), c.getGreen(), c.getBlue(), randomColors);
    }

    private static Color rarityColor(ItemStack stack) {
        switch (stack.getRarity()) {
            case EPIC: return new Color(255, 85, 255);
            case RARE: return new Color(85, 255, 255);
            case UNCOMMON: return new Color(255, 255, 85);
            default: return new Color(255, 255, 255);
        }
    }

    private boolean near(EntityItem a, EntityItem b) {
        return Math.abs(MathHelper.floor_double(a.posX) - MathHelper.floor_double(b.posX)) <= 1
                && Math.abs(MathHelper.floor_double(a.posY) - MathHelper.floor_double(b.posY)) <= 1
                && Math.abs(MathHelper.floor_double(a.posZ) - MathHelper.floor_double(b.posZ)) <= 1;
    }

    private void drawItemLabel(Cluster c, boolean showIcon, boolean showCount) {
        FontRenderer font = mc.fontRendererObj;
        float scale = 0.026666668F;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) c.x, (float) (c.y + 0.5), (float) c.z);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-scale, -scale, scale);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

            if (showIcon) drawIcon(c.stack, showCount);

            if (showCount) {
                String text = String.valueOf(c.total);
                font.drawStringWithShadow(text, -font.getStringWidth(text) / 2.0F, 0.0F, c.color.getRGB());
            }
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableLighting();
            GlStateManager.disableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    // Skip renderItemOverlays so the pile's own aggregated count (drawn as text) isn't fighting the
    // single stack's vanilla number.
    private void drawIcon(ItemStack stack, boolean countShown) {
        int x = -8;
        int y = countShown ? -19 : -8;
        ESPUtil.drawBillboardItems(() -> mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y));
    }

    private static final class Cluster {
        final List<EntityItem> items;
        final ItemStack stack;
        final int total;
        final double x, y, z;
        final Color color;

        Cluster(List<EntityItem> items, ItemStack stack, int total, double x, double y, double z, Color color) {
            this.items = items;
            this.stack = stack;
            this.total = total;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
        }
    }

    // PLACE EFFECT

    private void renderPlaceEffect() {
        if (placedBlocks.isEmpty()) return;

        Render3DUtil.start3D();
        GL11.glLineWidth(placeLineWidth.floatValue());

        double[] view = Render3DUtil.viewerOffset();
        long now = System.currentTimeMillis();
        double durationMs = placeFadeTime.getValue();
        boolean drawFill = !placeMode.getValue().equals("Outline");
        boolean drawOutline = !placeMode.getValue().equals("Filled");
        int base = placeBaseColor();

        Iterator<Map.Entry<BlockPos, Long>> it = placedBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> entry = it.next();
            float t = (float) ((now - entry.getValue()) / durationMs);
            if (t >= 1.0f) {
                it.remove();
                continue;
            }
            float fade = applyEasing(1.0f - t); // 1 at spawn, eases to 0

            BlockPos pos = entry.getKey();
            AxisAlignedBB bb = new AxisAlignedBB(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1
            ).expand(0.002, 0.002, 0.002).offset(-view[0], -view[1], -view[2]);

            Render3DUtil.drawBox(bb,
                    drawFill ? ColorUtil.withAlpha(base, (int) (placeFillOpacity.getValue() * fade)) : 0,
                    drawOutline ? ColorUtil.withAlpha(base, (int) (placeOutlineOpacity.getValue() * fade)) : 0);
        }

        Render3DUtil.end3D();
    }

    // always returns a fully-opaque packed int; per-draw alpha is applied by withAlpha
    private int placeBaseColor() {
        switch (placeColorMode.getValue()) {
            case "Rainbow":
                return ColorUtil.sync().getRGB() | 0xFF000000;
            case "Accent": {
                ClickGUI gui = Client.getInstance().getModuleManager().getModule(ClickGUI.class);
                return gui != null ? (gui.getColorValue().getValue() | 0xFF000000) : 0xFF8888FF;
            }
            default:
                return placeColor.getValue() | 0xFF000000;
        }
    }

    // x is the remaining alpha fraction (1 -> 0); ease it for a smoother vanish.
    // Expo is ease-IN on purpose -- drops fast then tails off, the inverse would hang near-opaque.
    private float applyEasing(float x) {
        switch (placeFadeCurve.getValue()) {
            case "Linear":
                return (float) Easing.LINEAR.apply(x);
            case "Expo":
                return (float) Easing.EASE_IN_EXPO.apply(x);
            case "Quint":
                return (float) Easing.EASE_OUT_QUINT.apply(x);
            default: // Ease Out (quad)
                return (float) Easing.EASE_OUT_QUAD.apply(x);
        }
    }
}
