package redacted.client.mcp.impl;

import baritone.api.BaritoneAPI;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import redacted.client.mcp.MainThreadDispatcher;
import redacted.client.mcp.McpServer;
import redacted.client.mcp.McpTool;
import redacted.client.mcp.McpToolException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * World-sensing tools: the LLM's eyes. Everything is read-only and cheap enough to poll in a
 * loop. Positions are absolute block coordinates; distances are horizontal+vertical meters
 * from the player.
 */
public final class WorldTools {

    private static final int MAX_SCAN_RADIUS = 48;

    // blocks worth reporting by default (Skywars/PvP relevant + hazards)
    private static final String[] DEFAULT_INTERESTING = {
            "chest", "trapped_chest", "ender_chest", "crafting_table", "furnace",
            "web", "water", "flowing_water", "lava", "flowing_lava", "tnt",
            "enchanting_table", "anvil"
    };

    private WorldTools() {
    }

    public static void registerAll(final McpServer server, final MainThreadDispatcher dispatcher) {
        server.registerTool(new PlayersNearby(dispatcher));
        server.registerTool(new ChestsNearby(dispatcher));
        server.registerTool(new BlocksNearby(dispatcher));
        server.registerTool(new SenseSurroundings(dispatcher));
        server.registerTool(new EntitiesNearby(dispatcher));
        server.registerTool(new JoinWorld());
    }

    /** Joins a singleplayer world on demand (dev/testing aid). */
    private static final class JoinWorld implements McpTool {
        @Override
        public String name() {
            return "join_world";
        }

        @Override
        public String description() {
            return "Launch a singleplayer world by name (default 'New World'). Used to get into a world "
                    + "automatically for testing.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject props = new JsonObject();
            props.add("name", ModuleTools.schemaProperty("string", "World save folder name (default 'New World')."));
            return ModuleTools.schema(props, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            final String name = args.has("name") ? args.get("name").getAsString() : "New World";
            return MainThreadDispatcher.dispatch(() -> {
                final Minecraft mc = Minecraft.getMinecraft();
                if (mc.theWorld != null) {
                    final JsonObject out = new JsonObject();
                    out.addProperty("ok", false);
                    out.addProperty("reason", "Already in a world.");
                    return out;
                }
                mc.launchIntegratedServer(name, name, null);
                final JsonObject out = new JsonObject();
                out.addProperty("ok", true);
                out.addProperty("world", name);
                return out;
            });
        }
    }

    // shared helpers

    private static void requireInWorld() {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            throw new McpToolException("Not in a world.");
        }
    }

    private static double dist(final EntityPlayer from, final BlockPos pos) {
        return Math.sqrt(from.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
    }

    private static String blockId(final Block block) {
        final ResourceLocation name = Block.blockRegistry.getNameForObject(block);
        return name == null ? block.getClass().getSimpleName() : name.toString().replace("minecraft:", "");
    }

    private static JsonObject blockPosJson(final BlockPos pos, final double distance) {
        final JsonObject o = new JsonObject();
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        o.addProperty("dist", Math.round(distance * 10.0D) / 10.0D);
        return o;
    }

    private static boolean isInterestingName(final String id, final List<String> filters) {
        if (filters != null) {
            for (final String filter : filters) {
                if (id.contains(filter)) return true;
            }
            return false;
        }
        for (final String interesting : DEFAULT_INTERESTING) {
            if (id.equals(interesting)) return true;
        }
        return false;
    }

    /** Scans loaded blocks around the player; returns [blockPos, id] pairs sorted by distance. */
    private static List<Object[]> scanBlocks(final int radius, final List<String> filters,
                                             final Boolean sameLevelOnly) {
        final EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        final BlockPos feet = new BlockPos(player.posX, player.posY, player.posZ);
        final int feetY = feet.getY();

        final List<Object[]> found = new ArrayList<Object[]>();
        for (final BlockPos pos : BlockPos.getAllInBoxMutable(
                feet.add(-radius, -Math.min(radius, 8), -radius),
                feet.add(radius, Math.min(radius, 8), radius))) {

            if (sameLevelOnly != null && sameLevelOnly && Math.abs(pos.getY() - feetY) > 1) continue;

            final Block block = Minecraft.getMinecraft().theWorld.getBlockState(pos).getBlock();
            if (block == null) continue;

            final String id = blockId(block);
            if (!isInterestingName(id, filters)) continue;

            found.add(new Object[]{new BlockPos(pos), id});
            if (found.size() >= 512) break;   // hard cap on scan output
        }
        return found;
    }

    private static List<String> parseFilters(final JsonObject args) {
        if (!args.has("types") || !args.get("types").isJsonArray()) return null;
        final List<String> filters = new ArrayList<String>();
        for (final com.google.gson.JsonElement element : args.getAsJsonArray("types")) {
            if (!element.isJsonPrimitive()) continue;
            filters.add(element.getAsString().trim()
                    .replace("minecraft:", "").toLowerCase(Locale.ROOT));
        }
        return filters.isEmpty() ? null : filters;
    }

    // get_players_nearby

    private static final class PlayersNearby implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private PlayersNearby(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "get_players_nearby";
        }

        @Override
        public String description() {
            return "List players within radius: exact position, health, held item, sneaking state, "
                    + "distance -- sorted nearest first. Use names with baritone_goto/baritone_follow. "
                    + "includeMobs=true also summarizes nearby hostile mobs.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            properties.add("radius", ModuleTools.schemaProperty("number", "Search radius in blocks (default 32)."));
            properties.add("includeMobs", ModuleTools.schemaProperty("boolean", "Also include a hostile-mob summary."));
            return ModuleTools.schema(properties, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();

            float radius = 32F;
            if (args.has("radius") && args.get("radius").isJsonPrimitive()) {
                radius = (float) Math.min(128D, args.get("radius").getAsDouble());
            }
            final boolean includeMobs = args.has("includeMobs") && args.get("includeMobs").isJsonPrimitive()
                    && args.get("includeMobs").getAsBoolean();
            final float r = radius;

            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final EntityPlayer self = Minecraft.getMinecraft().thePlayer;
                    final List<EntityPlayer> players = new ArrayList<EntityPlayer>();
                    for (final Object o : Minecraft.getMinecraft().theWorld.loadedEntityList) {
                        if (!(o instanceof EntityPlayer)) continue;
                        final EntityPlayer other = (EntityPlayer) o;
                        if (other.getEntityId() == self.getEntityId()) continue;
                        if (self.getDistanceToEntity(other) > r) continue;
                        players.add(other);
                    }
                    Collections.sort(players, new Comparator<EntityPlayer>() {
                        @Override
                        public int compare(EntityPlayer a, EntityPlayer b) {
                            return Double.compare(self.getDistanceToEntity(a), self.getDistanceToEntity(b));
                        }
                    });

                    final JsonArray array = new JsonArray();
                    for (final EntityPlayer other : players) {
                        final JsonObject p = new JsonObject();
                        p.addProperty("name", other.getName());
                        p.addProperty("dist", Math.round(self.getDistanceToEntity(other) * 10.0D) / 10.0D);
                        p.addProperty("x", Math.round(other.posX * 10.0D) / 10.0D);
                        p.addProperty("y", Math.round(other.posY * 10.0D) / 10.0D);
                        p.addProperty("z", Math.round(other.posZ * 10.0D) / 10.0D);
                        p.addProperty("health", Math.round(other.getHealth() * 10.0D) / 10.0D);
                        p.addProperty("maxHealth", other.getMaxHealth());
                        p.addProperty("sneaking", other.isSneaking());
                        p.addProperty("onGround", other.onGround);
                        final ItemStack held = other.getHeldItem();
                        p.addProperty("heldItem", held == null ? "empty" : held.getDisplayName());
                        array.add(p);
                    }

                    final JsonObject result = new JsonObject();
                    result.add("players", array);
                    result.addProperty("total", array.size());

                    if (includeMobs) {
                        final Map<String, Integer> mobs = new LinkedHashMap<String, Integer>();
                        for (final Object o : Minecraft.getMinecraft().theWorld.loadedEntityList) {
                            if (!(o instanceof EntityMob)) continue;
                            final EntityMob mob = (EntityMob) o;
                            if (self.getDistanceToEntity(mob) > r) continue;
                            final String id = net.minecraft.entity.EntityList.getEntityString(mob);
                            final String key = id == null ? mob.getClass().getSimpleName() : id;
                            final Integer count = mobs.get(key);
                            mobs.put(key, count == null ? 1 : count + 1);
                        }
                        final JsonObject mobSummary = new JsonObject();
                        for (final Map.Entry<String, Integer> entry : mobs.entrySet()) {
                            mobSummary.addProperty(entry.getKey(), entry.getValue());
                        }
                        result.add("hostileMobsNearby", mobSummary);
                    }
                    return result;
                }
            });
        }
    }

    // get_chests_nearby

    private static final class ChestsNearby implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private ChestsNearby(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "get_chests_nearby";
        }

        @Override
        public String description() {
            return "Find chest/trapped_chest/ender_chest blocks near you (loaded chunks only), sorted "
                    + "by distance. If Baritone has seen inside one this session its remembered contents "
                    + "are attached. Typical loot loop: get_chests_nearby -> baritone_goto(nearest) -> "
                    + "look_at + right_click to open -> ChestStealer drains it.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            properties.add("radius", ModuleTools.schemaProperty("number", "Scan radius in blocks (default 24, max 48)."));
            return ModuleTools.schema(properties, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            int radius = 24;
            if (args.has("radius") && args.get("radius").isJsonPrimitive()) {
                radius = (int) Math.max(4D, Math.min(MAX_SCAN_RADIUS, args.get("radius").getAsDouble()));
            }

            final List<String> chestFilters = java.util.Arrays.asList(
                    "chest", "trapped_chest", "ender_chest");
            final int r = radius;
            final List<Object[]> found = this.dispatcher.call(new java.util.concurrent.Callable<List<Object[]>>() {
                @Override
                public List<Object[]> call() {
                    return scanBlocks(r, chestFilters, null);
                }
            });

            final Map<BlockPos, baritone.api.cache.IRememberedInventory> remembered =
                    rememberedInventories();

            final JsonArray chests = new JsonArray();
            for (final Object[] entry : found) {
                final BlockPos pos = (BlockPos) entry[0];
                final String id = (String) entry[1];
                final JsonObject c = blockPosJson(pos, dist(Minecraft.getMinecraft().thePlayer, pos));
                c.addProperty("type", id);

                final baritone.api.cache.IRememberedInventory inv = remembered == null ? null : remembered.get(pos);
                if (inv != null) {
                    final JsonArray contents = new JsonArray();
                    for (final ItemStack stack : inv.getContents()) {
                        if (stack == null) continue;
                        contents.add(new com.google.gson.JsonPrimitive(
                                stack.getDisplayName() + " x" + stack.stackSize));
                    }
                    c.add("rememberedContents", contents);
                }
                chests.add(c);
            }

            final JsonObject result = new JsonObject();
            result.add("chests", chests);
            result.addProperty("total", chests.size());
            result.addProperty("note", remembered == null || remembered.isEmpty()
                    ? "Baritone remembers contents of chests you open; open one to track loot."
                    : null);
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockPos, baritone.api.cache.IRememberedInventory> rememberedInventories() {
        try {
            return (Map<BlockPos, baritone.api.cache.IRememberedInventory>)
                    BaritoneAPI.getProvider().getPrimaryBaritone().getWorldProvider()
                            .getCurrentWorld().getContainerMemory().getRememberedInventories();
        } catch (final Throwable t) {
            return null;
        }
    }

    // get_blocks_nearby

    private static final class BlocksNearby implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private BlocksNearby(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "get_blocks_nearby";
        }

        @Override
        public String description() {
            return "Scan for notable blocks around you, grouped by type with nearest positions first. "
                    + "Default set covers loot stations (crafting_table/furnace), hazards (web/water/lava/tnt) "
                    + "and containers. Pass \"types\" to filter (substring match, e.g. [\"stone\",\"wood\"]), "
                    + "\"sameLevel\": true to only report blocks within 1 of your feet Y (same flooring), "
                    + "or \"hazardsOnly\": true.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            properties.add("radius", ModuleTools.schemaProperty("number", "Scan radius (default 16, max 48)."));
            final JsonObject types = new JsonObject();
            types.addProperty("type", "array");
            types.addProperty("items", "string");
            types.addProperty("description", "Substring filters on block ids, e.g. [\"log\",\"planks\"].");
            properties.add("types", types);
            properties.add("sameLevel", ModuleTools.schemaProperty("boolean", "Only blocks within 1 block of your feet Y."));
            properties.add("hazardsOnly", ModuleTools.schemaProperty("boolean", "Only web/water/lava/cactus/fire/tnt."));
            properties.add("limitPerType", ModuleTools.schemaProperty("integer", "Max positions kept per type (default 6)."));
            return ModuleTools.schema(properties, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            int radius = 16;
            if (args.has("radius") && args.get("radius").isJsonPrimitive()) {
                radius = (int) Math.max(4D, Math.min(MAX_SCAN_RADIUS, args.get("radius").getAsDouble()));
            }
            final boolean sameLevel = args.has("sameLevel") && args.get("sameLevel").isJsonPrimitive()
                    && args.get("sameLevel").getAsBoolean();
            final boolean hazardsOnly = args.has("hazardsOnly") && args.get("hazardsOnly").isJsonPrimitive()
                    && args.get("hazardsOnly").getAsBoolean();
            int limitPerType = 6;
            if (args.has("limitPerType") && args.get("limitPerType").isJsonPrimitive()) {
                limitPerType = Math.max(1, Math.min(32, args.get("limitPerType").getAsInt()));
            }

            List<String> filters = parseFilters(args);
            if (hazardsOnly) {
                filters = java.util.Arrays.asList("web", "water", "lava", "cactus", "fire", "tnt");
            }

            final int r = radius;
            final List<String> f = filters;
            final Boolean sl = sameLevel;
            final int limit = limitPerType;

            final List<Object[]> found = this.dispatcher.call(new java.util.concurrent.Callable<List<Object[]>>() {
                @Override
                public List<Object[]> call() {
                    return scanBlocks(r, f, sl);
                }
            });

            // group by id, keep nearest-first per group
            final Map<String, JsonArray> grouped = new LinkedHashMap<String, JsonArray>();
            final Map<String, Integer> totals = new LinkedHashMap<String, Integer>();
            for (final Object[] entry : found) {
                final BlockPos pos = (BlockPos) entry[0];
                final String id = (String) entry[1];
                final double distance = dist(Minecraft.getMinecraft().thePlayer, pos);

                final Integer total = totals.get(id);
                totals.put(id, total == null ? 1 : total + 1);

                JsonArray positions = grouped.get(id);
                if (positions == null) {
                    positions = new JsonArray();
                    grouped.put(id, positions);
                }
                if (positions.size() < limit) positions.add(blockPosJson(pos, distance));
            }

            final JsonArray groups = new JsonArray();
            for (final Map.Entry<String, JsonArray> entry : grouped.entrySet()) {
                final JsonObject g = new JsonObject();
                g.addProperty("type", entry.getKey());
                g.addProperty("count", totals.get(entry.getKey()));
                g.add("nearest", entry.getValue());
                groups.add(g);
            }

            final JsonObject result = new JsonObject();
            result.add("groups", groups);
            result.addProperty("totalFound", found.size());
            result.addProperty("scanRadius", radius);
            result.addProperty("sameLevelOnly", sameLevel);
            return result;
        }
    }

    // sense_surroundings

    private static final class SenseSurroundings implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private SenseSurroundings(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "sense_surroundings";
        }

        @Override
        public String description() {
            return "One-call situational snapshot for decision loops: your vitals/position/held item, "
                    + "nearest players (up to 8), nearby chests (up to 8), hazard blocks, same-flooring "
                    + "notable blocks, and Baritone pathing state. Call this before deciding what to do next.";
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
                    final EntityPlayer self = Minecraft.getMinecraft().thePlayer;

                    final JsonObject result = new JsonObject();

                    // self
                    final ItemStack held = self.getHeldItem();
                    final JsonObject selfJson = new JsonObject();
                    selfJson.addProperty("health", Math.round((self.getHealth() + self.getAbsorptionAmount()) * 10.0D) / 10.0D);
                    selfJson.addProperty("hunger", self.getFoodStats().getFoodLevel());
                    selfJson.addProperty("x", Math.round(self.posX * 10.0D) / 10.0D);
                    selfJson.addProperty("y", Math.round(self.posY * 10.0D) / 10.0D);
                    selfJson.addProperty("z", Math.round(self.posZ * 10.0D) / 10.0D);
                    selfJson.addProperty("yaw",
                            Math.round(net.minecraft.util.MathHelper.wrapAngleTo180_float(self.rotationYaw)));
                    selfJson.addProperty("pitch", Math.round(self.rotationPitch));
                    selfJson.addProperty("heldItem", held == null ? "empty" : held.getDisplayName());
                    result.add("self", selfJson);

                    // players
                    final JsonArray players = new JsonArray();
                    final List<EntityPlayer> nearby = new ArrayList<EntityPlayer>();
                    for (final Object o : Minecraft.getMinecraft().theWorld.loadedEntityList) {
                        if (!(o instanceof EntityPlayer)) continue;
                        final EntityPlayer other = (EntityPlayer) o;
                        if (other.getEntityId() == self.getEntityId()) continue;
                        if (self.getDistanceToEntity(other) <= 24F) nearby.add(other);
                    }
                    Collections.sort(nearby, new Comparator<EntityPlayer>() {
                        @Override
                        public int compare(EntityPlayer a, EntityPlayer b) {
                            return Double.compare(self.getDistanceToEntity(a), self.getDistanceToEntity(b));
                        }
                    });
                    for (final EntityPlayer other : nearby.subList(0, Math.min(8, nearby.size()))) {
                        final JsonObject p = new JsonObject();
                        p.addProperty("name", other.getName());
                        p.addProperty("dist", Math.round(self.getDistanceToEntity(other) * 10.0D) / 10.0D);
                        p.addProperty("health", Math.round(other.getHealth() * 10.0D) / 10.0D);
                        final ItemStack theirHeld = other.getHeldItem();
                        p.addProperty("heldItem", theirHeld == null ? "empty" : theirHeld.getDisplayName());
                        players.add(p);
                    }
                    result.addProperty("playerCount", nearby.size());
                    result.add("players", players);

                    // chests (nearest 8)
                    final List<Object[]> chestHits = scanBlocks(24,
                            java.util.Arrays.asList("chest", "trapped_chest", "ender_chest"), null);
                    final JsonArray chests = new JsonArray();
                    for (final Object[] entry : chestHits.subList(0, Math.min(8, chestHits.size()))) {
                        final BlockPos pos = (BlockPos) entry[0];
                        chests.add(blockPosJson(pos, dist(self, pos)));
                    }
                    result.addProperty("chestCount", chestHits.size());
                    result.add("chests", chests);

                    // hazards
                    final List<Object[]> hazards = scanBlocks(12,
                            java.util.Arrays.asList("web", "water", "lava", "cactus", "fire", "tnt"), null);
                    final JsonArray hazardList = new JsonArray();
                    for (final Object[] entry : hazards.subList(0, Math.min(10, hazards.size()))) {
                        final BlockPos pos = (BlockPos) entry[0];
                        final JsonObject h = blockPosJson(pos, dist(self, pos));
                        h.addProperty("type", (String) entry[1]);
                        hazardList.add(h);
                    }
                    result.addProperty("hazardCount", hazards.size());
                    result.add("hazards", hazardList);

                    // baritone
                    boolean pathing = false;
                    try {
                        pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
                    } catch (final Throwable ignored) {
                    }
                    result.addProperty("baritonePathing", pathing);

                    return result;
                }
            });
        }
    }

    // get_entities_nearby

    /** Rich entity scan: players (with armor/held/health), living mobs, and other entities. */
    private static final class EntitiesNearby implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private EntitiesNearby(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "get_entities_nearby";
        }

        @Override
        public String description() {
            return "List all entities around you with their positions, velocity, and id. Players get extra "
                    + "data: health, max health, armor pieces, held item, sneak state. Living mobs get health. "
                    + "Useful for threat awareness and target picking.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            properties.add("radius", ModuleTools.schemaProperty("number", "Search radius in blocks (default 32, max 128)."));
            properties.add("includeSelf", ModuleTools.schemaProperty("boolean", "Also include the local player."));
            return ModuleTools.schema(properties, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            float radius = 32F;
            if (args.has("radius") && args.get("radius").isJsonPrimitive()) {
                radius = (float) Math.min(128D, args.get("radius").getAsDouble());
            }
            final boolean includeSelf = args.has("includeSelf") && args.get("includeSelf").isJsonPrimitive()
                    && args.get("includeSelf").getAsBoolean();
            final float r = radius;

            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final EntityPlayer self = Minecraft.getMinecraft().thePlayer;
                    final JsonArray entities = new JsonArray();
                    final JsonArray players = new JsonArray();
                    final JsonArray mobs = new JsonArray();
                    final JsonArray others = new JsonArray();

                    for (final Object o : Minecraft.getMinecraft().theWorld.loadedEntityList) {
                        if (!(o instanceof Entity)) continue;
                        final Entity entity = (Entity) o;
                        if (entity == self && !includeSelf) continue;
                        if (self.getDistanceToEntity(entity) > r) continue;

                        final JsonObject e = new JsonObject();
                        e.addProperty("id", entity.getEntityId());
                        e.addProperty("name", entity.getName());
                        e.addProperty("type", net.minecraft.entity.EntityList.getEntityString(entity));
                        e.addProperty("x", Math.round(entity.posX * 10.0D) / 10.0D);
                        e.addProperty("y", Math.round(entity.posY * 10.0D) / 10.0D);
                        e.addProperty("z", Math.round(entity.posZ * 10.0D) / 10.0D);
                        e.addProperty("motionX", Math.round(entity.motionX * 100.0D) / 100.0D);
                        e.addProperty("motionY", Math.round(entity.motionY * 100.0D) / 100.0D);
                        e.addProperty("motionZ", Math.round(entity.motionZ * 100.0D) / 100.0D);
                        e.addProperty("dist", Math.round(self.getDistanceToEntity(entity) * 10.0D) / 10.0D);
                        e.addProperty("yaw", Math.round(entity.rotationYaw));
                        e.addProperty("pitch", Math.round(entity.rotationPitch));
                        e.addProperty("onGround", entity.onGround);
                        e.addProperty("sprinting", entity.isSprinting());
                        e.addProperty("sneaking", entity.isSneaking());
                        e.addProperty("alive", entity.isEntityAlive());

                        if (entity instanceof EntityLivingBase) {
                            final EntityLivingBase living = (EntityLivingBase) entity;
                            e.addProperty("health", Math.round(living.getHealth() * 10.0D) / 10.0D);
                            e.addProperty("maxHealth", living.getMaxHealth());
                            e.addProperty("armorValue", living.getTotalArmorValue());
                            e.addProperty("fire", living.isBurning());
                        }

                        if (entity instanceof EntityPlayer) {
                            final EntityPlayer other = (EntityPlayer) entity;
                            e.addProperty("health", Math.round(other.getHealth() * 10.0D) / 10.0D);
                            final ItemStack held = other.getHeldItem();
                            e.addProperty("heldItem", held == null ? "empty" : held.getDisplayName());
                            e.addProperty("heldItemId", held == null ? "empty"
                                    : (net.minecraft.item.Item.itemRegistry.getNameForObject(held.getItem()) == null
                                    ? held.getItem().getUnlocalizedName()
                                    : net.minecraft.item.Item.itemRegistry.getNameForObject(held.getItem()).toString().replace("minecraft:", "")));
                            final JsonArray armor = new JsonArray();
                            for (int slot = 0; slot < other.inventory.armorInventory.length; slot++) {
                                final ItemStack piece = other.inventory.armorInventory[slot];
                                armor.add(new com.google.gson.JsonPrimitive(piece == null ? "empty" : piece.getDisplayName()));
                            }
                            e.add("armor", armor);
                            players.add(e);
                        } else if (entity instanceof EntityMob || entity instanceof EntityLivingBase) {
                            mobs.add(e);
                        } else {
                            others.add(e);
                        }
                    }

                    final JsonObject result = new JsonObject();
                    result.add("players", players);
                    result.add("mobs", mobs);
                    result.add("other", others);
                    result.addProperty("totalPlayers", players.size());
                    result.addProperty("totalMobs", mobs.size());
                    result.addProperty("totalOther", others.size());
                    result.addProperty("total", players.size() + mobs.size() + others.size());
                    return result;
                }
            });
        }
    }
}
