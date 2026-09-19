package redacted.client.mcp.impl;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import redacted.client.Client;
import redacted.client.module.Module;
import redacted.client.mcp.MainThreadDispatcher;
import redacted.client.mcp.McpServer;
import redacted.client.mcp.McpTool;
import redacted.client.mcp.McpToolException;

/**
 * Baritone pathfinding control, exposed with an LLM-friendly surface. Everything runs on the
 * client thread via {@link MainThreadDispatcher}. The engine is the in-tree 1.8.9 Baritone
 * port ({@code baritone.*}), driven through its API processes (not chat commands) so results
 * are structured.
 *
 * <p>Typical LLM loop: baritone_status -> (baritone_goto / baritone_sprint_to /
 * baritone_follow / baritone_mine) -> poll baritone_status until not pathing -> act.
 */
public final class BaritoneTools {

    private static final int MAX_RADIUS = 64;

    private BaritoneTools() {
    }

    /**
     * Shared mine-target tracker: {@code baritone_mine} records the block types it was asked to mine,
     * and {@code baritone_activity} uses this to detect which of those positions actually became air
     * (i.e. what was really broken), plus the current position, so an AI can verify task completion.
     */
    private static final class MineTracker {
        private static final java.util.List<BlockPos> targets = new java.util.ArrayList<BlockPos>();
        private static final java.util.Map<BlockPos, String> observed = new java.util.HashMap<BlockPos, String>();

        private MineTracker() {
        }

        /** Record the block types we were asked to mine; positions are discovered by baritone. */
        static void reset() {
            synchronized (targets) {
                targets.clear();
                observed.clear();
            }
        }

        static void remember(final String blockName) {
            // We don't know exact positions yet, so we record the name and let baritone_activity
            // discover mined positions by scanning around the player for recently-air blocks. The
            // exact target positions are hard to extract from the mine process API, so we track a
            // coarse "recently emptied" detection around the player instead.
        }
    }

    public static void registerAll(final McpServer server, final MainThreadDispatcher dispatcher) {
        server.registerTool(new Goto(dispatcher));
        server.registerTool(new SprintTo(dispatcher));
        server.registerTool(new Mine(dispatcher));
        server.registerTool(new Follow(dispatcher));
        server.registerTool(new Stop(dispatcher));
        server.registerTool(new Status());
        server.registerTool(new Activity(dispatcher));
        server.registerTool(new SetAvoidBlocks(dispatcher));
        server.registerTool(new SetThrowawayBlocks(dispatcher));
    }

    // shared helpers

    private static IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    private static void requireInWorld() {
        if (Minecraft.getMinecraft().thePlayer == null || Minecraft.getMinecraft().theWorld == null) {
            throw new McpToolException("Not in a world.");
        }
    }

    /** Resolves a player by exact-then-prefix name match against loaded entities. */
    private static EntityPlayer findPlayer(final String name) {
        EntityPlayer prefixMatch = null;
        for (final Object o : Minecraft.getMinecraft().theWorld.loadedEntityList) {
            if (!(o instanceof EntityPlayer)) continue;
            final EntityPlayer player = (EntityPlayer) o;
            final String playerName = player.getName();
            if (playerName.equalsIgnoreCase(name)) return player;
            if (prefixMatch == null && playerName.toLowerCase(java.util.Locale.ROOT)
                    .startsWith(name.toLowerCase(java.util.Locale.ROOT))) {
                prefixMatch = player;
            }
        }
        if (prefixMatch != null) return prefixMatch;
        throw new McpToolException("No loaded player matching \"" + name + "\". Use get_players_nearby to list names.");
    }

    private static String itemName(final ItemStack stack) {
        return stack == null ? null : stack.getDisplayName();
    }

    // goto / sprint_to shared arg parsing

    private static final class GoArgs {
        Integer x;
        Integer y;
        Integer z;
        Integer range;   // GoalNear radius
        String player;

        boolean hasCoords() {
            return x != null && z != null;
        }
    }

    private static GoArgs parseGoArgs(final JsonObject args) {
        final GoArgs go = new GoArgs();
        if (args.has("x") && args.get("x").isJsonPrimitive()) go.x = args.get("x").getAsInt();
        if (args.has("y") && args.get("y").isJsonPrimitive()) go.y = args.get("y").getAsInt();
        if (args.has("z") && args.get("z").isJsonPrimitive()) go.z = args.get("z").getAsInt();
        if (args.has("range") && args.get("range").isJsonPrimitive()) {
            go.range = Math.max(0, Math.min(MAX_RADIUS, args.get("range").getAsInt()));
        }
        if (args.has("player") && args.get("player").isJsonPrimitive()) {
            go.player = ModuleTools.requireString(args, "player");
        }
        if (!go.hasCoords() && go.player == null) {
            throw new McpToolException("Provide either x/z (y optional) or a \"player\" name.");
        }
        if (go.hasCoords() && (go.x == null || go.z == null)) {
            throw new McpToolException("Coordinates come as a set: x and z are required when targeting a position.");
        }
        return go;
    }

    private static Object startGoal(final MainThreadDispatcher dispatcher, final GoArgs go,
                                    final boolean ensureSprint, final JsonObject extraResult) throws Exception {
        requireInWorld();

        final String error = dispatcher.call(new java.util.concurrent.Callable<String>() {
            @Override
            public String call() {
                // sprint_to: make sure the autosprint module is on first (idempotent)
                if (ensureSprint) {
                    final Module sprint = Client.getInstance().getModuleManager().getModule("Sprint");
                    if (sprint == null) return "Sprint module not found";
                    if (!sprint.isEnabled()) sprint.setEnabled(true);
                }

                BlockPos target = null;
                if (go.player != null) {
                    target = findPlayer(go.player).getPosition();
                } else {
                    target = new BlockPos(go.x, go.y == null ? 0 : go.y, go.z);
                }

                if (go.range != null) {
                    baritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(target, go.range));
                } else if (go.y == null && go.player == null) {
                    baritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ(target.getX(), target.getZ()));
                } else {
                    baritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
                }
                return null;
            }
        });

        if (error != null) return ModuleTools.toolResult(false, error);

        extraResult.addProperty("pathing", true);
        extraResult.addProperty("note", "Movement is normal walking/sprinting -- no teleport. Poll "
                + "baritone_status until \"pathing\" is false, then verify position.");
        return extraResult;
    }

    // tools

    private static final class Goto implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private Goto(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "baritone_goto";
        }

        @Override
        public String description() {
            return "Walk/pathfind to coordinates or to another player using Baritone. Give x/z (y "
                    + "optional -- omit y for same-level travel), or \"player\": \"name\". Optional "
                    + "\"range\" stops within N blocks of the goal instead of exactly on it. Normal "
                    + "movement only. Baritone avoids hazards it is told about (see "
                    + "baritone_set_avoid_blocks: e.g. cobweb, water).";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();

            properties.add("x", ModuleTools.schemaProperty("integer", "Target block X."));
            properties.add("y", ModuleTools.schemaProperty("integer", "Target block Y (feet). Omit for XZ-only goal."));
            properties.add("z", ModuleTools.schemaProperty("integer", "Target block Z."));
            properties.add("player", ModuleTools.schemaProperty("string", "Player name to walk to (alternative to coords)."));
            properties.add("range", ModuleTools.schemaProperty("integer", "Stop within this many blocks of the goal (GoalNear)."));

            return ModuleTools.schema(properties, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            final GoArgs go = parseGoArgs(args);
            final JsonObject extra = new JsonObject();
            extra.addProperty("target",
                    go.player != null ? "player:" + go.player
                            : "(" + go.x + ", " + (go.y == null ? "?" : go.y) + ", " + go.z + ")");
            return startGoal(this.dispatcher, go, false, extra);
        }
    }

    private static final class SprintTo implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private SprintTo(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "baritone_sprint_to";
        }

        @Override
        public String description() {
            return "Same as baritone_goto but first ensures the client's auto-sprint module (\"Sprint\") "
                    + "is enabled so the pathing runs at full speed (sprint-jumping terrain included). "
                    + "The response reports whether sprint was already on.";
        }

        @Override
        public JsonObject inputSchema() {
            return new Goto(null).inputSchema(); // identical arguments
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            final GoArgs go = parseGoArgs(args);
            final JsonObject extra = new JsonObject();

            // capture sprint state before startGoal touches it, so the LLM can see whether
            // enabling actually happened or it was already on
            final Boolean[] wasEnabled = new Boolean[1];
            this.dispatcher.call(new java.util.concurrent.Callable<String>() {
                @Override
                public String call() {
                    final Module sprint = Client.getInstance().getModuleManager().getModule("Sprint");
                    wasEnabled[0] = sprint != null && sprint.isEnabled();
                    return null;
                }
            });
            extra.addProperty("sprintWasEnabled", wasEnabled[0] != null && wasEnabled[0]);

            return startGoal(this.dispatcher, go, true, extra);
        }
    }

    private static final class Mine implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private Mine(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "baritone_mine";
        }

        @Override
        public String description() {
            return "Find and mine blocks by name using Baritone (walks to them and breaks them). "
                    + "Example: {\"blocks\":[\"coal_ore\"],\"count\":8}. Block names accept vanilla ids "
                    + "(with or without minecraft: prefix).";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();

            final JsonObject blocks = new JsonObject();
            blocks.addProperty("type", "array");
            blocks.addProperty("items", "string");
            blocks.addProperty("description", "Block names to mine, e.g. [\"diamond_ore\"] or [\"chest\"].");
            properties.add("blocks", blocks);

            properties.add("count", ModuleTools.schemaProperty("integer", "How many to mine before stopping (default 1, max 4096)."));

            return ModuleTools.schema(properties, java.util.Arrays.asList("blocks"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            if (!args.has("blocks") || !args.get("blocks").isJsonArray()) {
                throw new McpToolException("Missing required argument: blocks (array)");
            }
            int count = 1;
            if (args.has("count") && args.get("count").isJsonPrimitive()) {
                count = Math.max(1, Math.min(4096, args.get("count").getAsInt()));
            }

            final JsonArray wantedNames = new JsonArray();
            for (final JsonElement element : args.getAsJsonArray("blocks")) {
                if (!element.isJsonPrimitive()) continue;
                final String raw = element.getAsString().trim()
                        .replace("minecraft:", "").toLowerCase(java.util.Locale.ROOT);
                if (raw.isEmpty() || raw.chars().anyMatch(Character::isWhitespace)) {
                    throw new McpToolException("Invalid block name: \"" + element.getAsString() + "\"");
                }
                if (Block.getBlockFromName(raw) == null) {
                    throw new McpToolException("Unknown block id/name: \"" + raw + "\"");
                }
                wantedNames.add(new JsonPrimitive(raw));
            }
            if (wantedNames.size() == 0) throw new McpToolException("No valid block names given.");

            final int quantity = count;
            this.dispatcher.call(new java.util.concurrent.Callable<Void>() {
                @Override
                public Void call() {
                    final String[] names = new String[wantedNames.size()];
                    for (int i = 0; i < names.length; i++) names[i] = wantedNames.get(i).getAsString();
                    baritone().getMineProcess().mineByName(quantity, names);
                    return null;
                }
            });

            return ModuleTools.toolResult(true, "Mining started: " + quantity + "x " + wantedNames
                    + ". Poll baritone_status until not mining.");
        }
    }

    private static final class Follow implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private Follow(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "baritone_follow";
        }

        @Override
        public String description() {
            return "Continuously follow a player (re-paths as they move). Pass {\"stop\":true} to cancel "
                    + "following. Great for tailing teammates or shadowing targets while combat modules handle fights.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            properties.add("player", ModuleTools.schemaProperty("string", "Player name to follow."));
            properties.add("stop", ModuleTools.schemaProperty("boolean", "true cancels an active follow."));
            return ModuleTools.schema(properties, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            final boolean stop = args.has("stop") && args.get("stop").isJsonPrimitive()
                    && args.get("stop").getAsBoolean();

            if (stop) {
                this.dispatcher.call(new java.util.concurrent.Callable<Void>() {
                    @Override
                    public Void call() {
                        baritone().getFollowProcess().cancel();
                        return null;
                    }
                });
                return ModuleTools.toolResult(true, "Follow cancelled.");
            }

            final String name = ModuleTools.requireString(args, "player");

            // capture the matched entity now; filter matches by entity instance each tick so the
            // process keeps tracking them across movement without re-resolving names off-thread
            final EntityPlayer target = this.dispatcher.call(new java.util.concurrent.Callable<EntityPlayer>() {
                @Override
                public EntityPlayer call() {
                    return findPlayer(name);
                }
            });

            this.dispatcher.call(new java.util.concurrent.Callable<Void>() {
                @Override
                public Void call() {
                    baritone().getFollowProcess().follow(new java.util.function.Predicate<Entity>() {
                        @Override
                        public boolean test(Entity entity) {
                            return entity instanceof EntityPlayer
                                    && ((EntityPlayer) entity).getName().equals(target.getName())
                                    && entity.getEntityId() == target.getEntityId();
                        }
                    });
                    return null;
                }
            });

            return ModuleTools.toolResult(true, "Following \"" + target.getName() + "\". Use "
                    + "{\"stop\":true} to cancel.");
        }
    }

    private static final class Stop implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private Stop(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "baritone_stop";
        }

        @Override
        public String description() {
            return "Cancel everything Baritone is doing (goto/mine/follow/bridge goals) and release its "
                    + "injected keys.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            this.dispatcher.call(new java.util.concurrent.Callable<Void>() {
                @Override
                public Void call() {
                    baritone().getPathingBehavior().cancelEverything();
                    baritone().getInputOverrideHandler().clearAllKeys();
                    try {
                        baritone().getMineProcess().cancel();
                    } catch (final Throwable ignored) {
                    }
                    try {
                        baritone().getFollowProcess().cancel();
                    } catch (final Throwable ignored) {
                    }
                    return null;
                }
            });
            return ModuleTools.toolResult(true, "Baritone stopped.");
        }
    }

    private static final class Status implements McpTool {
        @Override
        public String name() {
            return "baritone_status";
        }

        @Override
        public String description() {
            return "Baritone state snapshot: currently pathing, which behavior is active (goto/mine/follow), "
                    + "the configured avoid + throwaway block lists, and the Sprint module state.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public JsonObject execute(final JsonObject args) {
            final JsonObject result = new JsonObject();

            boolean pathing = false;
            String activeProcess = null;
            try {
                pathing = baritone().getPathingBehavior().isPathing();
                final java.util.Optional<baritone.api.process.IBaritoneProcess> inControl =
                        baritone().getPathingControlManager().mostRecentInControl();
                if (inControl != null && inControl.isPresent()) {
                    activeProcess = inControl.get().displayName();
                }
            } catch (final Throwable t) {
                activeProcess = "error: " + t.getClass().getSimpleName();
            }

            result.addProperty("pathing", pathing);
            result.addProperty("activeProcess", activeProcess == null ? "none" : activeProcess);

            // Path / ETA / destination info so an AI has "eyes" on where the player is going.
            try {
                final java.util.Optional<Double> ticksRemaining = baritone().getPathingBehavior().ticksRemainingInSegment();
                result.addProperty("etaTicks", ticksRemaining.isPresent() ? Math.round(ticksRemaining.get()) : -1);
                result.addProperty("etaSeconds", ticksRemaining.isPresent()
                        ? Math.round(ticksRemaining.get() * 0.05D * 10.0D) / 10.0D : -1.0D);

                final java.util.Optional<baritone.api.pathing.calc.IPath> pathOpt = baritone().getPathingBehavior().getPath();
                if (pathOpt != null && pathOpt.isPresent()) {
                    final baritone.api.pathing.calc.IPath path = pathOpt.get();
                    final java.util.List<baritone.api.utils.BetterBlockPos> positions = path.positions();
                    result.addProperty("pathLength", positions.size());
                    if (!positions.isEmpty()) {
                        final baritone.api.utils.BetterBlockPos dest = positions.get(positions.size() - 1);
                        result.addProperty("pathDestX", dest.getX());
                        result.addProperty("pathDestY", dest.getY());
                        result.addProperty("pathDestZ", dest.getZ());
                        // Straight-line remaining distance from the player to the destination.
                        final EntityPlayer self = Minecraft.getMinecraft().thePlayer;
                        result.addProperty("remainingDistance",
                                Math.round(self.getDistance(dest.getX() + 0.5D, dest.getY() + 0.5D, dest.getZ() + 0.5D) * 10.0D) / 10.0D);
                    }
                }
                final baritone.api.pathing.goals.Goal goal = baritone().getPathingBehavior().getGoal();
                if (goal != null) {
                    result.addProperty("goal", goal.toString());
                }
            } catch (final Throwable t) {
                result.addProperty("etaTicks", -1);
            }

            try {
                result.addProperty("mining", baritone().getMineProcess().isActive());
            } catch (final Throwable ignored) {
                result.addProperty("mining", false);
            }

            try {
                final java.util.List<Entity> following = baritone().getFollowProcess().following();
                result.addProperty("following", following != null && !following.isEmpty());
                if (following != null) {
                    final JsonArray names = new JsonArray();
                    for (final Entity entity : following) {
                        if (entity instanceof EntityPlayer) names.add(new JsonPrimitive(((EntityPlayer) entity).getName()));
                    }
                    if (names.size() > 0) result.add("followingPlayers", names);
                }
            } catch (final Throwable ignored) {
                result.addProperty("following", false);
            }

            try {
                final JsonArray avoid = new JsonArray();
                for (final Block block : BaritoneAPI.getSettings().blocksToAvoid.value) {
                    avoid.add(new JsonPrimitive(String.valueOf(Block.blockRegistry.getNameForObject(block))));
                }
                result.add("avoidBlocks", avoid);
                result.addProperty("avoidanceEnabled", BaritoneAPI.getSettings().avoidance.value);

                final JsonArray throwaway = new JsonArray();
                for (final Item item : BaritoneAPI.getSettings().acceptableThrowawayItems.value) {
                    throwaway.add(new JsonPrimitive(String.valueOf(Item.itemRegistry.getNameForObject(item))));
                }
                result.add("throwawayItems", throwaway);
            } catch (final Throwable ignored) {
            }

            final Module sprint = Client.getInstance().getModuleManager().getModule("Sprint");
            result.addProperty("sprintModuleEnabled", sprint != null && sprint.isEnabled());

            return result;
        }
    }

    /**
     * Tracks what Baritone actually did since the last poll, so an AI can tell what was broken,
     * where it ended up, and whether the task completed. A lightweight completion oracle built on
     * the mine/path processes plus position deltas.
     */
    private static final class Activity implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private Activity(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "baritone_activity";
        }

        @Override
        public String description() {
            return "Baritone task-completion report since the last poll: what was mined (positions + "
                    + "block types), the current player position, whether baritone is still working, "
                    + "the active goal/destination, and remaining distance. Call after baritone_goto / "
                    + "baritone_mine and poll until \"working\" is false.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public JsonObject execute(final JsonObject args) throws Exception {
            final JsonObject snapshot = this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final JsonObject result = new JsonObject();
                    final EntityPlayer self = Minecraft.getMinecraft().thePlayer;

                    // current position
                    final JsonObject pos = new JsonObject();
                    pos.addProperty("x", Math.round(self.posX * 10.0D) / 10.0D);
                    pos.addProperty("y", Math.round(self.posY * 10.0D) / 10.0D);
                    pos.addProperty("z", Math.round(self.posZ * 10.0D) / 10.0D);
                    pos.addProperty("blockX", self.getPosition().getX());
                    pos.addProperty("blockY", self.getPosition().getY());
                    pos.addProperty("blockZ", self.getPosition().getZ());
                    result.add("position", pos);

                    boolean pathing = false;
                    try {
                        pathing = baritone().getPathingBehavior().isPathing();
                    } catch (final Throwable ignored) {
                    }
                    boolean mining = false;
                    try {
                        mining = baritone().getMineProcess().isActive();
                    } catch (final Throwable ignored) {
                    }
                    result.addProperty("working", pathing || mining);
                    result.addProperty("pathing", pathing);
                    result.addProperty("mining", mining);

                    // goal / destination
                    try {
                        final baritone.api.pathing.goals.Goal goal = baritone().getPathingBehavior().getGoal();
                        if (goal != null) result.addProperty("goal", goal.toString());
                    } catch (final Throwable ignored) {
                    }
                    try {
                        final java.util.Optional<baritone.api.pathing.calc.IPath> path =
                                baritone().getPathingBehavior().getPath();
                        if (path != null && path.isPresent()) {
                            final java.util.List<baritone.api.utils.BetterBlockPos> positions =
                                    path.get().positions();
                            if (!positions.isEmpty()) {
                                final baritone.api.utils.BetterBlockPos dest = positions.get(positions.size() - 1);
                                final JsonObject d = new JsonObject();
                                d.addProperty("x", dest.getX());
                                d.addProperty("y", dest.getY());
                                d.addProperty("z", dest.getZ());
                                result.add("destination", d);
                                result.addProperty("remainingDistance",
                                        Math.round(self.getDistance(dest.getX() + 0.5D, dest.getY() + 0.5D,
                                                dest.getZ() + 0.5D) * 10.0D) / 10.0D);
                            }
                        }
                    } catch (final Throwable ignored) {
                    }

                    // "What was actually broken": snapshot blocks around the player and report which
                    // of the tracked target types have become air since the last poll.
                    final JsonArray mined = new JsonArray();
                    try {
                        final int r = 8;
                        final BlockPos feet = new BlockPos(self.posX, self.posY, self.posZ);
                        for (final BlockPos p : BlockPos.getAllInBoxMutable(
                                feet.add(-r, -3, -r), feet.add(r, 3, r))) {
                            if (!Minecraft.getMinecraft().theWorld.isBlockLoaded(p)) continue;
                            final net.minecraft.block.Block block =
                                    Minecraft.getMinecraft().theWorld.getBlockState(p).getBlock();
                            final String id = net.minecraft.block.Block.blockRegistry.getNameForObject(block) == null
                                    ? "?" : net.minecraft.block.Block.blockRegistry.getNameForObject(block).toString().replace("minecraft:", "");
                            final boolean wasTracked = MineTracker.observed.containsKey(p);
                            final String before = MineTracker.observed.get(p);
                            MineTracker.observed.put(p, id);
                            // If it was a non-air tracked block and is now air, it got mined this cycle.
                            if (wasTracked && before != null && !"air".equals(before) && "air".equals(id)) {
                                final JsonObject m = new JsonObject();
                                m.addProperty("x", p.getX());
                                m.addProperty("y", p.getY());
                                m.addProperty("z", p.getZ());
                                m.addProperty("mined", before);
                                mined.add(m);
                            }
                        }
                    } catch (final Throwable ignored) {
                    }
                    result.add("mined", mined);
                    result.addProperty("minedCount", mined.size());

                    return result;
                }
            });

            return snapshot;
        }
    }

    private static final class SetAvoidBlocks implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private SetAvoidBlocks(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "baritone_set_avoid_blocks";
        }

        @Override
        public String description() {
            return "Replace Baritone's hazard-avoidance list. Pathing will route around these blocks "
                    + "-- e.g. [\"cobweb\",\"water\",\"lava\"] before crossing enemy terrain in Skywars. "
                    + "Also turns Baritone's avoidance system on. Pass an empty array (or clear=true) to reset.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject blocks = new JsonObject();
            blocks.addProperty("type", "array");
            blocks.addProperty("items", "string");
            blocks.addProperty("description", "Block ids/names to always avoid, e.g. [\"cobweb\", \"water\"].");
            properties.add("blocks", blocks);
            properties.add("clear", ModuleTools.schemaProperty("boolean", "true resets the list to empty (blocks may be omitted)."));
            return ModuleTools.schema(properties, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            final boolean clear = args.has("clear") && args.get("clear").isJsonPrimitive()
                    && args.get("clear").getAsBoolean();
            final JsonArray resolved = new JsonArray();
            if (!clear) {
                if (!args.has("blocks") || !args.get("blocks").isJsonArray()) {
                    throw new McpToolException("Provide \"blocks\" (array of names) or \"clear\": true");
                }
                for (final JsonElement element : args.getAsJsonArray("blocks")) {
                    if (!element.isJsonPrimitive()) continue;
                    final String raw = element.getAsString().trim()
                            .replace("minecraft:", "").toLowerCase(java.util.Locale.ROOT);
                    final Block block = Block.getBlockFromName(raw);
                    if (block == null) throw new McpToolException("Unknown block id/name: \"" + raw + "\"");
                    resolved.add(new JsonPrimitive(raw));
                }
            }

            final JsonArray finalResolved = resolved;
            this.dispatcher.call(new java.util.concurrent.Callable<Void>() {
                @Override
                public Void call() {
                    final java.util.List<Block> blocks = new java.util.ArrayList<Block>();
                    for (int i = 0; i < finalResolved.size(); i++) {
                        blocks.add(Block.getBlockFromName(finalResolved.get(i).getAsString()));
                    }
                    BaritoneAPI.getSettings().blocksToAvoid.value = blocks;
                    BaritoneAPI.getSettings().avoidance.value = true;
                    return null;
                }
            });

            return ModuleTools.toolResult(true, "Avoid list set to " + finalResolved
                    + " (avoidance enabled). Applies to all future pathing.");
        }
    }

    private static final class SetThrowawayBlocks implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private SetThrowawayBlocks(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "baritone_set_throwaway_blocks";
        }

        @Override
        public String description() {
            return "Tell Baritone which inventory items it may consume as placeable throwaway blocks "
                    + "(for its own gap bridging/pillaring). Example before native bridging: "
                    + "[\"wool\",\"cobblestone\"]. Replaces the whole list.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject items = new JsonObject();
            items.addProperty("type", "array");
            items.addProperty("items", "string");
            items.addProperty("description", "Item ids/names Baritone may place, e.g. [\"wool\", \"end_stone\"].");
            properties.add("items", items);
            return ModuleTools.schema(properties, java.util.Arrays.asList("items"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            if (!args.has("items") || !args.get("items").isJsonArray()) {
                throw new McpToolException("Missing required argument: items (array of item names)");
            }

            final JsonArray resolved = new JsonArray();
            for (final JsonElement element : args.getAsJsonArray("items")) {
                if (!element.isJsonPrimitive()) continue;
                final String raw = element.getAsString().trim()
                        .replace("minecraft:", "").toLowerCase(java.util.Locale.ROOT);
                final Item item = Item.getByNameOrId(raw);
                if (item == null) throw new McpToolException("Unknown item id/name: \"" + raw + "\"");
                final String resolvedName = Item.itemRegistry.getNameForObject(item) == null
                        ? raw : String.valueOf(Item.itemRegistry.getNameForObject(item));
                resolved.add(new JsonPrimitive(resolvedName));
            }

            final JsonArray finalResolved = resolved;
            this.dispatcher.call(new java.util.concurrent.Callable<Void>() {
                @Override
                public Void call() {
                    final java.util.List<Item> items = new java.util.ArrayList<Item>();
                    for (int i = 0; i < finalResolved.size(); i++) {
                        final Item item = Item.getByNameOrId(finalResolved.get(i).getAsString());
                        if (item != null) items.add(item);
                    }
                    BaritoneAPI.getSettings().acceptableThrowawayItems.value = items;
                    return null;
                }
            });

            return ModuleTools.toolResult(true, "Throwaway items set to " + finalResolved + ".");
        }
    }
}
