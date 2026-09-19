package redacted.client.mcp.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import redacted.client.mcp.MainThreadDispatcher;
import redacted.client.mcp.McpServer;
import redacted.client.mcp.McpTool;
import redacted.client.mcp.McpToolException;

/**
 * Open-GUI sensing + slot interaction. Works with ANY container the player has open --
 * chest, crafting table (3x3), furnace, or your own inventory screen's 2x2 crafting grid.
 * Slots are addressed by CONTAINER SLOT INDEX (what windowClick sends), and every sense
 * response labels each slot so an LLM can reason about crafting grids vs storage.
 *
 * <p>Click semantics mirror a real mouse: pickup (left/right), quick_move (shift-click),
 * swap-with-hotbar, throw. Crafting = pickup from a source slot, then pickup/place into
 * grid slots; re-run get_open_container between clicks to see state.
 */
public final class ContainerTools {

    ContainerTools() {
    }

    public static void registerAll(final McpServer server, final MainThreadDispatcher dispatcher) {
        server.registerTool(new GetOpenContainer(dispatcher));
        server.registerTool(new ContainerClick(dispatcher));
    }

    // get_open_container

    private static final class GetOpenContainer implements McpTool {
        private final MainThreadDispatcher dispatcher;

        GetOpenContainer(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "get_open_container";
        }

        @Override
        public String description() {
            return "Describe whatever GUI/container is currently open (chest, crafting table, furnace, "
                    + "or your own inventory): type, title, window id, and EVERY slot with its container "
                    + "slot index, item, count and area label (container/crafting/craftResult/armor/main/hotbar). "
                    + "Slot indexes from here feed container_click directly.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            PlayerTools.requireInWorld();
            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    return describe();
                }
            });
        }
    }

    static JsonObject describe() {
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityPlayer player = mc.thePlayer;
        final Container container = player.openContainer;

        final JsonObject result = new JsonObject();

        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiContainer)) {
            result.addProperty("type", mc.currentScreen.getClass().getSimpleName());
            result.addProperty("note", "A non-container GUI is open; slot interaction unavailable.");
            return result;
        }

        if (container == null || container == player.inventoryContainer) {
            // own inventory -- always available via windowId 0 even without the screen up
            result.addProperty("type", "inventory");
            result.addProperty("title", "Inventory");
            result.addProperty("windowId", player.inventoryContainer.windowId);
            result.addProperty("screenVisible",
                    mc.currentScreen != null && mc.currentScreen instanceof GuiContainer);
            result.addProperty("layoutNote",
                    "0=craftResult 1-4=crafting grid 5-8=armor(helmet..boots) 9-35=main 36-44=hotbar");
            result.add("slots", describeSlots(player.inventoryContainer));
            return result;
        }

        result.addProperty("windowId", container.windowId);
        if (container instanceof ContainerChest) {
            final IInventory lower = ((ContainerChest) container).getLowerChestInventory();
            result.addProperty("type", "chest");
            result.addProperty("title", lower.getName());
            result.addProperty("containerSlots", lower.getSizeInventory());
            result.addProperty("layoutNote", "0.." + (lower.getSizeInventory() - 1)
                    + "=chest, rest is your main/hotbar (shift-click moves between them)");
        } else if (container instanceof ContainerWorkbench) {
            result.addProperty("type", "crafting_table");
            result.addProperty("title", "Crafting");
            result.addProperty("layoutNote", "0=craftResult 1-9=crafting grid (rows of 3), 10-36=main, 37-45=hotbar");
        } else if (container instanceof ContainerFurnace) {
            result.addProperty("type", "furnace");
            result.addProperty("title", "Furnace");
            result.addProperty("layoutNote", "0=input 1=fuel 2=output 3-30=main 31-39=hotbar");
        } else {
            result.addProperty("type", container.getClass().getSimpleName());
        }

        result.add("slots", describeSlots(container));
        return result;
    }

    private static JsonArray describeSlots(final Container container) {
        final JsonArray slots = new JsonArray();
        for (final Object o : container.inventorySlots) {
            final Slot slot = (Slot) o;
            final ItemStack stack = slot.getStack();

            final JsonObject s = new JsonObject();
            s.addProperty("slot", slot.slotNumber);
            s.addProperty("area", guessArea(slot));

            if (stack == null) {
                s.addProperty("item", "empty");
            } else {
                s.addProperty("item", stack.getDisplayName());
                s.addProperty("count", stack.stackSize);
                final net.minecraft.util.ResourceLocation id =
                        net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
                if (id != null) s.addProperty("id", id.toString().replace("minecraft:", ""));
            }
            slots.add(s);
        }
        return slots;
    }

    private static String guessArea(final Slot slot) {
        // vanilla layout heuristic by slot number within the container
        final int n = slot.slotNumber;
        final int total = slot.inventory.getSizeInventory();
        final boolean playerInvStyle = total == 36 || total == 27 || total == 10 || total == 9;
        if (!playerInvStyle) {
            // container-specific region before the player inventory starts
            return "container";
        }
        if (n >= total - 9 && total > 9) return "hotbar";
        if (n < 9 && total == 36) return n < 5 ? "craftingOrArmor" : "main";
        if (n >= 36 && total == 46) return "hotbar";
        return "main";
    }

    // container_click

    private static final class ContainerClick implements McpTool {
        private final MainThreadDispatcher dispatcher;

        ContainerClick(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "container_click";
        }

        @Override
        public String description() {
            return "Click one slot in the open GUI exactly like a mouse would. Modes: "
                    + "\"pickup\" (button 0=left grab/place, 1=right half/one), \"quick_move\" "
                    + "(shift-click transfer, button 0), \"swap\" (button = hotbar index 0-8), "
                    + "\"throw\" (button 0=one, 1=whole stack). For crafting: get_open_container first, "
                    + "pickup ingredients into grid slots, then take the result from craftResult.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            properties.add("slot", ModuleTools.schemaProperty("integer",
                    "Container slot index as reported by get_open_container."));
            properties.add("mode", ModuleTools.schemaProperty("string",
                    "One of: pickup, quick_move, swap, throw."));
            properties.add("button", ModuleTools.schemaProperty("integer",
                    "Mouse button / variant per mode (see description). Default 0."));
            return ModuleTools.schema(properties, java.util.Arrays.asList("slot"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            PlayerTools.requireInWorld();
            if (!args.has("slot") || !args.get("slot").isJsonPrimitive()) {
                throw new McpToolException("Missing required argument: slot");
            }
            final int slot = args.get("slot").getAsInt();

            String mode = "pickup";
            if (args.has("mode") && args.get("mode").isJsonPrimitive()) {
                mode = args.get("mode").getAsString().trim().toLowerCase(java.util.Locale.ROOT);
            }
            int button = 0;
            if (args.has("button") && args.get("button").isJsonPrimitive()) {
                button = args.get("button").getAsInt();
            }

            final int modeId;
            switch (mode) {
                case "pickup":     modeId = 0; break;
                case "quick_move": modeId = 1; break;
                case "swap":
                    modeId = 2;
                    if (button < 0 || button > 8) {
                        throw new McpToolException("swap button must be a hotbar index 0-8");
                    }
                    break;
                case "throw":      modeId = 4; break;
                default:
                    throw new McpToolException("Unknown mode \"" + mode + "\" (pickup|quick_move|swap|throw)");
            }
            if (modeId == 0 && (button < 0 || button > 1)) {
                throw new McpToolException("pickup button must be 0 (left) or 1 (right)");
            }
            if (modeId == 4 && (button < 0 || button > 1)) {
                throw new McpToolException("throw button must be 0 (one item) or 1 (whole stack)");
            }

            final int fButton = button;
            final int fMode = modeId;

            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final Minecraft mc = Minecraft.getMinecraft();
                    final EntityPlayer player = mc.thePlayer;
                    final Container container = player.openContainer;

                    if (slot < 0 || container == null || slot >= container.inventorySlots.size()) {
                        throw new McpToolException("Slot " + slot + " out of range for the current GUI"
                                + (container == null ? " (nothing open)" : " (" + container.inventorySlots.size()
                                + " slots). Run get_open_container."));
                    }

                    final Slot target = (Slot) container.inventorySlots.get(slot);
                    final String hadBefore = target.getStack() == null ? "empty" : target.getStack().getDisplayName();

                    mc.playerController.windowClick(container.windowId, slot, fButton, fMode, player);

                    final JsonObject result = new JsonObject();
                    result.addProperty("ok", true);
                    result.addProperty("clicked", slot);
                    result.addProperty("hadItem", hadBefore);
                    final ItemStack after = target.getStack();
                    result.addProperty("hasNow", after == null ? "empty" : after.getDisplayName());
                    final ItemStack cursor = player.inventory.getItemStack();
                    result.addProperty("cursorItem", cursor == null ? "empty" : cursor.getDisplayName());
                    return result;
                }
            });
        }
    }
}
