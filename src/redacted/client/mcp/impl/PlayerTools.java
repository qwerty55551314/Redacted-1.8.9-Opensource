package redacted.client.mcp.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import redacted.client.mcp.MainThreadDispatcher;
import redacted.client.mcp.McpServer;
import redacted.client.mcp.McpTool;
import redacted.client.mcp.McpToolException;

/**
 * Direct player control + self-sensing: vitals, inventory contents, hotbar selection, camera,
 * left/right clicks, using named items (e.g. a "Play Again" item), and raw key holds for
 * micro-movement. All actions go through the vanilla client paths so the server sees ordinary
 * play.
 */
public final class PlayerTools {

    PlayerTools() {
    }

    public static void registerAll(final McpServer server, final MainThreadDispatcher dispatcher) {
        server.registerTool(new GetPlayerState(dispatcher));
        server.registerTool(new GetInventory(dispatcher));
        server.registerTool(new SelectHotbarSlot(dispatcher));
        server.registerTool(new LookAt(dispatcher));
        server.registerTool(new LeftClick(dispatcher));
        server.registerTool(new RightClick(dispatcher));
        server.registerTool(new UseItemNamed(dispatcher));
        server.registerTool(new OpenInventory(dispatcher));
        server.registerTool(new HoldKey(dispatcher));
    }

    // shared helpers

    static void requireInWorld() {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            throw new McpToolException("Not in a world.");
        }
    }

    static JsonObject itemJson(final ItemStack stack, final int slot, final String area) {
        final JsonObject o = new JsonObject();
        o.addProperty("slot", slot);
        if (area != null) o.addProperty("area", area);
        if (stack == null) {
            o.addProperty("item", "empty");
            return o;
        }
        o.addProperty("item", stack.getDisplayName());
        final net.minecraft.util.ResourceLocation id =
                net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        o.addProperty("id", id == null ? "?" : id.toString().replace("minecraft:", ""));
        o.addProperty("count", stack.stackSize);
        if (stack.isItemStackDamageable()) {
            o.addProperty("damage", stack.getItemDamage());
            o.addProperty("maxDamage", stack.getMaxDamage());
            o.addProperty("durabilityPct", Math.round(
                    100.0D * (stack.getMaxDamage() - stack.getItemDamage()) / stack.getMaxDamage()));
        }
        if (stack.getItem() instanceof ItemArmor) {
            o.addProperty("armorType", armorTypeName((ItemArmor) stack.getItem()));
        }
        o.addProperty("enchanted", stack.isItemEnchanted());
        return o;
    }

    private static String armorTypeName(final ItemArmor armor) {
        switch (armor.armorType) {
            case 0: return "helmet";
            case 1: return "chestplate";
            case 2: return "leggings";
            case 3: return "boots";
            default: return "armor" + armor.armorType;
        }
    }

    /** yaw/pitch to look from the eyes at a point (vanilla convention). */
    static float[] rotationsTo(final double x, final double y, final double z) {
        final EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        final double dx = x - player.posX;
        final double dy = y - (player.posY + player.getEyeHeight());
        final double dz = z - player.posZ;
        final double horizontal = Math.sqrt(dx * dx + dz * dz);
        final float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        final float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{yaw, MathHelper.wrapAngleTo180_float(pitch)};
    }

    // get_player_state

    private static final class GetPlayerState implements McpTool {
        private final MainThreadDispatcher dispatcher;

        GetPlayerState(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "get_player_state";
        }

        @Override
        public String description() {
            return "Your own live status: health (+absorption), hunger, air, position, look angles, "
                    + "grounded/sprinting/sneaking/using-item flags, active potion effects, equipped "
                    + "armor and held item. Cheap -- poll freely.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();

            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;

                    final JsonObject result = new JsonObject();
                    result.addProperty("health", Math.round(player.getHealth() * 10.0D) / 10.0D);
                    result.addProperty("absorption", Math.round(player.getAbsorptionAmount() * 10.0D) / 10.0D);
                    result.addProperty("hunger", player.getFoodStats().getFoodLevel());
                    result.addProperty("saturation", Math.round(player.getFoodStats().getSaturationLevel() * 10.0D) / 10.0D);
                    result.addProperty("air", player.getAir());
                    result.addProperty("x", Math.round(player.posX * 100.0D) / 100.0D);
                    result.addProperty("y", Math.round(player.posY * 100.0D) / 100.0D);
                    result.addProperty("z", Math.round(player.posZ * 100.0D) / 100.0D);
                    result.addProperty("blockX", (int) Math.floor(player.posX));
                    result.addProperty("blockY", (int) Math.floor(player.posY));
                    result.addProperty("blockZ", (int) Math.floor(player.posZ));
                    result.addProperty("yaw",
                            Math.round(net.minecraft.util.MathHelper.wrapAngleTo180_float(player.rotationYaw)));
                    result.addProperty("pitch", Math.round(player.rotationPitch));
                    result.addProperty("onGround", player.onGround);
                    result.addProperty("sprinting", player.isSprinting());
                    result.addProperty("sneaking", player.isSneaking());
                    result.addProperty("usingItem", player.isUsingItem());
                    result.addProperty("inWater", player.isInWater());
                    result.addProperty("dimension", player.dimension);

                    final ItemStack held = player.getHeldItem();
                    result.add("heldItem", held == null ? itemJson(null, player.inventory.currentItem, null)
                            : itemJson(held, player.inventory.currentItem, "hotbar"));
                    result.addProperty("selectedHotbarSlot", player.inventory.currentItem);

                    final JsonArray armor = new JsonArray();
                    final String[] names = {"boots", "leggings", "chestplate", "helmet"};
                    for (int i = 0; i < 4; i++) {
                        armor.add(itemJson(player.inventory.armorItemInSlot(i), i, names[i]));
                    }
                    result.add("equippedArmor", armor);

                    final JsonArray effects = new JsonArray();
                    for (final Object o : player.getActivePotionEffects()) {
                        final net.minecraft.potion.PotionEffect effect = (net.minecraft.potion.PotionEffect) o;
                        final JsonObject e = new JsonObject();
                        e.addProperty("name", effect.getEffectName()
                                .replace("effect.", "").replace("potion.", ""));
                        e.addProperty("amplifier", effect.getAmplifier());
                        e.addProperty("durationTicks", effect.getDuration());
                        effects.add(e);
                    }
                    result.add("effects", effects);
                    return result;
                }
            });
        }
    }

    // get_inventory

    private static final class GetInventory implements McpTool {
        private final MainThreadDispatcher dispatcher;

        GetInventory(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "get_inventory";
        }

        @Override
        public String description() {
            return "Full inventory listing: hotbar slots 0-8, main storage slots 9-35, equipped armor, "
                    + "and the selected hotbar slot. Each entry has display name, registry id, count, "
                    + "durability and enchantment flag. Use with container_click to move things around.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();

            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;

                    final JsonObject result = new JsonObject();
                    result.addProperty("selectedHotbarSlot", player.inventory.currentItem);

                    final JsonArray hotbar = new JsonArray();
                    for (int slot = 0; slot < 9; slot++) {
                        hotbar.add(itemJson(player.inventory.getStackInSlot(slot), slot, "hotbar"));
                    }
                    result.add("hotbar", hotbar);

                    final JsonArray main = new JsonArray();
                    for (int slot = 9; slot < 36; slot++) {
                        final ItemStack stack = player.inventory.getStackInSlot(slot);
                        if (stack != null) main.add(itemJson(stack, slot, "main"));
                    }
                    result.add("main", main);

                    final JsonArray armor = new JsonArray();
                    final String[] armorNames = {"boots", "leggings", "chestplate", "helmet"};
                    for (int i = 0; i < 4; i++) {
                        armor.add(itemJson(player.inventory.armorItemInSlot(i), i, armorNames[i]));
                    }
                    result.add("equippedArmor", armor);

                    int used = 0;
                    int blocksHeld = 0;
                    for (int slot = 0; slot < 36; slot++) {
                        final ItemStack stack = player.inventory.getStackInSlot(slot);
                        if (stack != null) {
                            used++;
                            if (redacted.client.util.world.PlaceUtil.isPlaceableBlock(stack)) blocksHeld++;
                        }
                    }
                    result.addProperty("slotsUsed", used);
                    result.addProperty("placeableBlocksTotal", blocksHeld);
                    return result;
                }
            });
        }
    }

    // select_hotbar_slot

    private static final class SelectHotbarSlot implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private SelectHotbarSlot(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "select_hotbar_slot";
        }

        @Override
        public String description() {
            return "Switch to a hotbar slot (0-8). The switch is sent as the normal carried-item packet.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            properties.add("slot", ModuleTools.schemaProperty("integer", "Hotbar slot 0-8 (0 = leftmost)."));
            return ModuleTools.schema(properties, java.util.Arrays.asList("slot"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            final int slot = args.get("slot").getAsInt();
            if (slot < 0 || slot > 8) throw new McpToolException("slot must be 0-8");

            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    Minecraft.getMinecraft().thePlayer.inventory.currentItem = slot;
                    Minecraft.getMinecraft().playerController.setCurrentItemAndSync(slot);
                    final ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
                    final JsonObject result = new JsonObject();
                    result.addProperty("ok", true);
                    result.addProperty("slot", slot);
                    result.addProperty("heldItem", held == null ? "empty" : held.getDisplayName());
                    return result;
                }
            });
        }
    }

    // look_at

    private static final class LookAt implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private LookAt(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "look_at";
        }

        @Override
        public String description() {
            return "Smoothly turn your camera (human-speed rotation, over a few ticks) to face a "
                    + "world-space point (block coords or entity position). Rotation only -- does not "
                    + "move you. Pair with right_click/left_click which act on whatever is under the "
                    + "crosshair once the turn completes.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            properties.add("x", ModuleTools.schemaProperty("number", "Target X."));
            properties.add("y", ModuleTools.schemaProperty("number", "Target Y."));
            properties.add("z", ModuleTools.schemaProperty("number", "Target Z."));
            return ModuleTools.schema(properties, java.util.Arrays.asList("x", "y", "z"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            final double x = args.get("x").getAsDouble();
            final double y = args.get("y").getAsDouble();
            final double z = args.get("z").getAsDouble();

            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
                    final float[] rots = rotationsTo(x, y, z);
                    // smooth human turn through the baritone aim processor -- an instant
                    // snap is both ugly to watch and a rotation-check signature
                    baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                            .getLookBehavior().requestManualLook(rots[0], rots[1], true);

                    final JsonObject result = new JsonObject();
                    result.addProperty("ok", true);
                    result.addProperty("smooth", true);
                    result.addProperty("targetYaw", Math.round(rots[0] * 10.0D) / 10.0D);
                    result.addProperty("targetPitch", Math.round(rots[1] * 10.0D) / 10.0D);
                    result.addProperty("currentYaw", Math.round(player.rotationYaw * 10.0D) / 10.0D);
                    result.addProperty("currentPitch", Math.round(player.rotationPitch * 10.0D) / 10.0D);
                    return result;
                }
            });
        }
    }

    // left_click / right_click

    private static final class LeftClick implements McpTool {
        private final MainThreadDispatcher dispatcher;

        LeftClick(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "left_click";
        }

        @Override
        public String description() {
            return "One left-click on the CURRENT crosshair target: attacks entities, starts breaking "
                    + "the targeted block (single pulse, not sustained mining -- use baritone_mine for that), "
                    + "or swings at air. Combat modules like SilentAura do their own clicking when enabled.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
                    final MovingObjectPosition hit = Minecraft.getMinecraft().objectMouseOver;
                    final JsonObject result = new JsonObject();
                    result.addProperty("ok", true);

                    if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                            && hit.entityHit != null) {
                        Minecraft.getMinecraft().playerController.attackEntity(player, hit.entityHit);
                        player.swingItem();
                        result.addProperty("action", "attacked_entity");
                        result.addProperty("target", hit.entityHit.getName());
                        return result;
                    }

                    if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                        Minecraft.getMinecraft().playerController.clickBlock(hit.getBlockPos(), hit.sideHit);
                        player.swingItem();
                        result.addProperty("action", "break_pulse");
                        result.addProperty("target", hit.getBlockPos().getX() + " " + hit.getBlockPos().getY()
                                + " " + hit.getBlockPos().getZ());
                        return result;
                    }

                    player.swingItem();
                    result.addProperty("action", "swing_air");
                    return result;
                }
            });
        }
    }

    private static final class RightClick implements McpTool {
        private final MainThreadDispatcher dispatcher;

        RightClick(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "right_click";
        }

        @Override
        public String description() {
            return "One right-click on the CURRENT crosshair target: interacts with entities, uses "
                    + "blocks (opens chests/crafting tables/doors, places held blocks), falling back to "
                    + "using the held item (eats food, throws pearls, fires bows). To use an item by NAME "
                    + "(e.g. a \"Play Again\" book) prefer use_item_named.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final Minecraft mc = Minecraft.getMinecraft();
                    final EntityPlayerSP player = mc.thePlayer;
                    final MovingObjectPosition hit = mc.objectMouseOver;
                    final ItemStack held = player.inventory.getCurrentItem();
                    final JsonObject result = new JsonObject();
                    result.addProperty("ok", true);

                    if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                            && hit.entityHit != null) {
                        final boolean used = mc.playerController.interactWithEntitySendPacket(player, hit.entityHit);
                        result.addProperty("action", used ? "interact_entity" : "interact_entity_no_action");
                        result.addProperty("target", hit.entityHit.getName());
                        return result;
                    }

                    if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                            && held != null) {
                        final boolean placed = mc.playerController.onPlayerRightClick(player, mc.theWorld,
                                held, hit.getBlockPos(), hit.sideHit, hit.hitVec);
                        if (placed) player.swingItem();
                        result.addProperty("action", placed ? "used_block" : "block_use_rejected");
                        result.addProperty("target", hit.getBlockPos().getX() + " " + hit.getBlockPos().getY()
                                + " " + hit.getBlockPos().getZ());
                        if (!placed) {
                            final boolean usedItem = mc.playerController.sendUseItem(player, mc.theWorld, held);
                            result.addProperty("fallbackUsedItem", usedItem);
                        }
                        return result;
                    }

                    final boolean usedItem = mc.playerController.sendUseItem(player, mc.theWorld, held);
                    result.addProperty("action", "use_held_item");
                    result.addProperty("consumed", usedItem);
                    return result;
                }
            });
        }
    }

    // use_item_named

    private static final class UseItemNamed implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private UseItemNamed(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "use_item_named";
        }

        @Override
        public String description() {
            return "Find an item by name in your hotbar (case-insensitive substring, e.g. \"play again\"), "
                    + "select it, and right-click it in the air -- the standard way to click lobby/rejoin "
                    + "items. With searchInventory=true it also checks storage and SWAP-clicks the match "
                    + "into your current hotbar slot first.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            properties.add("name", ModuleTools.schemaProperty("string", "All-or-part of the item's display name."));
            properties.add("searchInventory", ModuleTools.schemaProperty("boolean", "Also search main storage (swaps match into current slot). Default false."));
            return ModuleTools.schema(properties, java.util.Arrays.asList("name"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            final String wanted = ModuleTools.requireString(args, "name").toLowerCase(Locale.ROOT);
            final boolean searchInventory = args.has("searchInventory") && args.get("searchInventory").isJsonPrimitive()
                    && args.get("searchInventory").getAsBoolean();

            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final Minecraft mc = Minecraft.getMinecraft();
                    final EntityPlayerSP player = mc.thePlayer;

                    int foundSlot = findInHotbar(wanted);

                    if (foundSlot < 0 && searchInventory) {
                        for (int slot = 9; slot < 36; slot++) {
                            final ItemStack stack = player.inventory.getStackInSlot(slot);
                            if (matches(stack, wanted)) {
                                // SWAP-click (mode 2): button = target hotbar index, swaps instantly
                                mc.playerController.windowClick(player.inventoryContainer.windowId,
                                        slot, player.inventory.currentItem, 2, player);
                                foundSlot = player.inventory.currentItem;
                                break;
                            }
                        }
                    }

                    final JsonObject result = new JsonObject();
                    if (foundSlot < 0) {
                        result.addProperty("ok", false);
                        result.addProperty("message", "No hotbar item matching \"" + wanted + "\""
                                + (searchInventory ? "" : " (try searchInventory=true)"));
                        return result;
                    }

                    mc.playerController.setCurrentItemAndSync(foundSlot);
                    final ItemStack held = player.getHeldItem();
                    final boolean used = mc.playerController.sendUseItem(player, mc.theWorld, held);

                    result.addProperty("ok", true);
                    result.addProperty("slot", foundSlot);
                    result.addProperty("item", held == null ? "empty" : held.getDisplayName());
                    result.addProperty("useSent", used);
                    return result;
                }
            });
        }

        private static int findInHotbar(final String wanted) {
            final EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            for (int slot = 0; slot < 9; slot++) {
                if (matches(player.inventory.getStackInSlot(slot), wanted)) return slot;
            }
            return -1;
        }

        private static boolean matches(final ItemStack stack, final String wanted) {
            if (stack == null) return false;
            return stack.getDisplayName().toLowerCase(Locale.ROOT).contains(wanted);
        }
    }

    // open_inventory

    private static final class OpenInventory implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private OpenInventory(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "open_inventory";
        }

        @Override
        public String description() {
            return "Open (true) or close (false) your own inventory screen. Opening it lets inventory-"
                    + "managing modules (InvManager auto-armor/hotbar/cleaner with its \"Inventory\" option "
                    + "on) work while it's up. Refuses if another GUI is already open -- close that one "
                    + "first via its own flow or open:false only when it is your inventory.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            properties.add("open", ModuleTools.schemaProperty("boolean", "true = open, false = close."));
            return ModuleTools.schema(properties, java.util.Arrays.asList("open"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            final boolean open = args.get("open").getAsBoolean();

            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final Minecraft mc = Minecraft.getMinecraft();
                    final JsonObject result = new JsonObject();

                    if (open) {
                        if (mc.currentScreen != null) {
                            result.addProperty("ok", false);
                            result.addProperty("message", "Another screen is open: "
                                    + mc.currentScreen.getClass().getSimpleName() + ". Close it first.");
                            return result;
                        }
                        mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
                        result.addProperty("ok", true);
                        result.addProperty("screen", "inventory");
                        return result;
                    }

                    if (mc.currentScreen == null) {
                        result.addProperty("ok", true);
                        result.addProperty("message", "No screen was open.");
                        return result;
                    }
                    if (!(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer)) {
                        result.addProperty("ok", false);
                        result.addProperty("message", "Refusing to close non-container screen: "
                                + mc.currentScreen.getClass().getSimpleName());
                        return result;
                    }
                    final String closed = mc.currentScreen.getClass().getSimpleName();
                    mc.displayGuiScreen(null);
                    result.addProperty("ok", true);
                    result.addProperty("closed", closed);
                    return result;
                }
            });
        }
    }

    // hold_key

    private static final class HoldKey implements McpTool {
        private final MainThreadDispatcher dispatcher;

        HoldKey(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "hold_key";
        }

        @Override
        public String description() {
            return "Hold a movement key for N milliseconds: forward/back/left/right/jump/sneak/sprint/use/attack. "
                    + "Raw vanilla key injection -- pairs with the Scaffold module for bridging "
                    + "(enable Scaffold, then hold_key forward), or for tiny repositioning. Returns immediately; "
                    + "the release is scheduled automatically.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject key = new JsonObject();
            key.addProperty("type", "string");
            key.add("enum", ModuleTools.jsonArrayOf(
                    "forward", "back", "left", "right", "jump", "sneak", "sprint", "use", "attack"));
            key.addProperty("description", "Which key to hold.");
            properties.add("key", key);
            properties.add("durationMs", ModuleTools.schemaProperty("integer", "How long to hold (default 500, max 10000)."));
            return ModuleTools.schema(properties, java.util.Arrays.asList("key"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            final String key = ModuleTools.requireString(args, "key").toLowerCase(Locale.ROOT);
            long durationMs = 500L;
            if (args.has("durationMs") && args.get("durationMs").isJsonPrimitive()) {
                durationMs = Math.max(50L, Math.min(10000L, args.get("durationMs").getAsLong()));
            }

            final net.minecraft.client.settings.KeyBinding binding = bindingFor(key);
            if (binding == null) throw new McpToolException("Unknown key \"" + key + "\"");

            final long ms = durationMs;
            this.dispatcher.post(new Runnable() {
                @Override
                public void run() {
                    net.minecraft.client.settings.KeyBinding.setKeyBindState(binding.getKeyCode(), true);
                }
            });

            // schedule the release back onto the client thread
            new java.util.Timer("MCP-KeyRelease", true).schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    final MainThreadDispatcher active = MainThreadDispatcher.get();
                    if (active == null) return;
                    active.post(new Runnable() {
                        @Override
                        public void run() {
                            net.minecraft.client.settings.KeyBinding.setKeyBindState(binding.getKeyCode(),
                                    org.lwjglx.input.Keyboard.isKeyDown(binding.getKeyCode()));
                        }
                    });
                }
            }, ms);

            final JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.addProperty("key", key);
            result.addProperty("durationMs", ms);
            return result;
        }

        private static net.minecraft.client.settings.KeyBinding bindingFor(final String key) {
            final net.minecraft.client.settings.GameSettings settings = Minecraft.getMinecraft().gameSettings;
            switch (key) {
                case "forward": return settings.keyBindForward;
                case "back":    return settings.keyBindBack;
                case "left":    return settings.keyBindLeft;
                case "right":   return settings.keyBindRight;
                case "jump":    return settings.keyBindJump;
                case "sneak":   return settings.keyBindSneak;
                case "sprint":  return settings.keyBindSprint;
                case "use":     return settings.keyBindUseItem;
                case "attack":  return settings.keyBindAttack;
                default: return null;
            }
        }
    }
}
