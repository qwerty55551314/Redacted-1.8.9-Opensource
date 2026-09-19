package redacted.client.value.impl;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.ResourceLocation;
import redacted.client.module.Module;
import redacted.client.util.world.PlaceUtil;
import redacted.client.value.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// AutoHotBar's composite widget. twelve categories cover the whole item registry; each holds the
// toggleable item variants you can mark + drag from. nine slots hold an ordered preference list of
// item keys (index 0 = primary, rest = fallbacks). only the slot lists drive the sort -- the enabled
// marks are a palette/curation flag. the serialized value is {slots, enabled}; the categories'
// entries are rebuilt lazily from the registry and never persisted (icons are construction metadata).
public class HotbarValue extends Value<Map<String, Object>> implements ItemPicker {

    public static final int SLOTS = 9;

    // bucket ids -- also the index into `categories`, so bucket(stack) lands an item in its category
    private static final int CAT_SWORD = 0, CAT_BOW = 1, CAT_ARMOR = 2, CAT_TOOLS = 3,
            CAT_FOOD = 4, CAT_POTION = 5, CAT_PEARL = 6, CAT_THROW = 7, CAT_ROD = 8,
            CAT_AXE = 9, CAT_BLOCKS = 10, CAT_OTHER = 11;

    // one item variant in a category grid. key = "registryName:meta"; subtypes carries whether meta
    // is a real variant id (apple type, wool color -> matters) or just durability (tools -> ignore it)
    public static final class Entry {
        private final String key;
        private final ItemStack icon;
        private final boolean subtypes;

        Entry(final String key, final ItemStack icon, final boolean subtypes) {
            this.key = key;
            this.icon = icon;
            this.subtypes = subtypes;
        }

        public String getKey() { return key; }
        public ItemStack getIcon() { return icon; }
        public boolean isSubtypes() { return subtypes; }
        // human-readable name for search filtering; falls back to the key if the stack can't resolve one
        public String displayName() {
            try { return icon.getDisplayName(); } catch (final Throwable t) { return key; }
        }
    }

    public static final class Category {
        private final String name;
        private final ItemStack icon;
        private final List<Entry> entries = new ArrayList<>();

        Category(final String name, final ItemStack icon) {
            this.name = name;
            this.icon = icon;
        }

        public String getName() { return name; }
        public ItemStack getIcon() { return icon; }
        public List<Entry> getEntries() { return entries; }
    }

    // working model -- kept in sync with the serialized map via setValue/snapshot
    private final List<List<String>> slots = new ArrayList<>();
    private final Set<String> excluded = new HashSet<>();   // items clicked-off; everything else counts

    // slot keys are either an item key ("registry:meta") or a whole-category ref ("@cat:Name")
    private static final String CAT_PREFIX = "@cat:";

    // registry-derived category model -- shared across every HotbarValue/CleanerGridValue instance.
    // it's a pure classification of the item registry (no per-instance state), built once on first
    // access and immutable after, so one copy serves every picker widget instead of a walk per value.
    private static final List<Category> CATEGORIES = buildCategories();
    private static final Map<String, Entry> BY_KEY = new HashMap<>();
    private static boolean BUILT;

    // transient GUI state -- not serialized (only getValue()'s map is)
    private transient boolean widgetOpen;
    private transient int openCategory = -1;
    private transient String search = "";
    private transient double scroll;            // top visible row index in the open category grid
    private transient List<Entry> filtered;
    private transient String filteredKey;

    public HotbarValue(final String name, final Module parent) {
        super(name, parent, defaultMap());
        initSlots();
    }

    public <V> HotbarValue(final String name, final Module parent, final Value<V> parentValue, final V requiredValue) {
        super(name, parent, defaultMap(), parentValue, requiredValue);
        initSlots();
    }

    private void initSlots() {
        for (int i = 0; i < SLOTS; i++) slots.add(new ArrayList<String>());
    }

    private static List<Category> buildCategories() {
        final List<Category> c = new ArrayList<>();
        c.add(new Category("Sword", new ItemStack(Items.diamond_sword)));
        c.add(new Category("Bow", new ItemStack(Items.bow)));
        c.add(new Category("Armor", new ItemStack(Items.diamond_chestplate)));
        c.add(new Category("Tools", new ItemStack(Items.diamond_pickaxe)));
        c.add(new Category("Food", new ItemStack(Items.cooked_beef)));
        c.add(new Category("Potion", new ItemStack(Items.potionitem)));
        c.add(new Category("Pearl", new ItemStack(Items.ender_pearl)));
        c.add(new Category("Throwables", new ItemStack(Items.snowball)));
        c.add(new Category("Fishing Rod", new ItemStack(Items.fishing_rod)));
        c.add(new Category("Axe", new ItemStack(Items.diamond_axe)));
        c.add(new Category("Blocks", new ItemStack(Blocks.stone)));
        c.add(new Category("Other", new ItemStack(Items.nether_star)));
        return c;
    }

    public static int bucket(final ItemStack stack) {
        final Item it = stack.getItem();
        if (it instanceof ItemSword) return CAT_SWORD;
        if (it instanceof ItemBow) return CAT_BOW;
        if (it instanceof ItemArmor) return CAT_ARMOR;
        // fine-grained tool classes -- axes and rods got their own categories so a hotbar slot
        // can pin "axe" or "best knockback rod" instead of any tool
        if (it instanceof ItemAxe) return CAT_AXE;
        if (it instanceof ItemPickaxe || it instanceof ItemSpade || it instanceof ItemHoe || it instanceof ItemShears) return CAT_TOOLS;
        if (it instanceof ItemFishingRod) return CAT_ROD;
        if (it instanceof ItemFood) return CAT_FOOD;
        if (it instanceof ItemPotion) return CAT_POTION;
        // throwables = stuff you actually hurl in a fight. pearls have their own category so a
        // slot can prioritise them; snowball/egg are the plain throwables. XP bottles and fire
        // charges are "projectiles" by registry class but useless in combat -- right-click does
        // nothing useful with them, so they land in Other instead
        if (it instanceof ItemEnderPearl) return CAT_PEARL;
        if (it instanceof ItemSnowball || it instanceof ItemEgg) return CAT_THROW;
        // only real building blocks count as "Blocks" -- chests, cobwebs, doors, rails, torches,
        // signs etc. are ItemBlocks too but they're not building material, so they land in Other
        if (it instanceof ItemBlock && isBuildingBlock(stack)) return CAT_BLOCKS;
        return CAT_OTHER;
    }

    // walk the registry once, expanding subtype items into their variants (same index the creative
    // search tab builds), and drop each into its category. dedupe by key so a variant can't double up.
    private static void ensureBuilt() {
        if (BUILT) return;
        BUILT = true;
        for (final Item item : Item.itemRegistry) {
            if (item == null) continue;
            final List<ItemStack> variants = new ArrayList<>();
            if (item.getHasSubtypes()) {
                try { item.getSubItems(item, CreativeTabs.tabAllSearch, variants); }
                catch (final Throwable ignored) { variants.clear(); }
            }
            if (variants.isEmpty()) variants.add(new ItemStack(item));
            for (final ItemStack stack : variants) {
                if (stack == null || stack.getItem() == null) continue;
                final String key = keyOf(stack);
                if (key == null || BY_KEY.containsKey(key)) continue;
                final Entry e = new Entry(key, stack, item.getHasSubtypes());
                CATEGORIES.get(bucket(stack)).getEntries().add(e);
                BY_KEY.put(key, e);
            }
        }
    }

    // can you actually build with it? mirrors PlaceUtil.isPlaceableBlock (full solid cube, not
    // falling, not tnt/soul sand -- glass counts) so the Blocks category matches what Scaffold can use
    private static boolean isBuildingBlock(final ItemStack stack) {
        return PlaceUtil.isPlaceableBlock(stack);
    }

    // canonical key for a stack: "registry:meta", with meta zeroed for damageable gear (durability
    // isn't an identity) so an inventory stack maps to the same key its category Entry was built with.
    public static String keyOf(final ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        final Item item = stack.getItem();
        final ResourceLocation rl = Item.itemRegistry.getNameForObject(item);
        final String base = rl == null ? ("id:" + Item.getIdFromItem(item)) : rl.toString();
        return base + ":" + (item.getHasSubtypes() ? stack.getMetadata() : 0);
    }

    // whole-category slot references
    public String categoryKey(final int idx) { return categoryRef(idx); }

    // "@cat:Name" token for category idx -- static so CleanerGridValue / InvManager can build one
    // without holding a HotbarValue instance (names exist pre-build, no ensureBuilt needed)
    public static String categoryRef(final int idx) { return CAT_PREFIX + CATEGORIES.get(idx).getName(); }

    public static boolean isCategoryKey(final String key) { return key != null && key.startsWith(CAT_PREFIX); }

    public static int staticCategoryIndexOf(final String key) {
        if (!isCategoryKey(key)) return -1;
        final String name = key.substring(CAT_PREFIX.length());
        for (int i = 0; i < CATEGORIES.size(); i++) {
            if (CATEGORIES.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    public int categoryIndexOf(final String key) {
        return staticCategoryIndexOf(key);
    }

    // does this stack count toward category catIdx -- right bucket and not clicked-off
    public boolean categoryIncludes(final int catIdx, final ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (bucket(stack) != catIdx) return false;
        return !isExcluded(keyOf(stack));
    }

    // per-category "best" scoring (used by InvManager's category slots)
    // pearls/throwables/blocks rank by stack size (you want the biggest stack in hand), the rod
    // by knockback enchant first (lure/luck/unbreaking/durability as tie-breaks), food by the
    // user's priority order (golden apple > golden carrot > steak > rest by nutrition); swords,
    // bows, axes and the other tools use the shared gear scores.
    public static double categoryScore(final int catIdx, final ItemStack stack) {
        if (stack == null) return 0.0;
        switch (catIdx) {
            case CAT_PEARL:
            case CAT_THROW:
            case CAT_BLOCKS:
                return stack.stackSize;
            case CAT_ROD:
                return scoreRod(stack);
            case CAT_FOOD:
                return scoreFood(stack);
            default:
                return redacted.client.util.player.InventoryUtil.scoreItem(stack);
        }
    }

    // best knockback fishing rod: knockback dominates, then lure, luck of the sea, unbreaking,
    // then remaining durability (same shape as Unfair's rod scorer, reweighted for knockback)
    private static double scoreRod(final ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemFishingRod)) return 0.0;
        final int knockback = EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack);
        final int lure = EnchantmentHelper.getEnchantmentLevel(Enchantment.lure.effectId, stack);
        final int luck = EnchantmentHelper.getEnchantmentLevel(Enchantment.luckOfTheSea.effectId, stack);
        final int unbreaking = EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack);
        return knockback * 1000.0 + lure * 100.0 + luck * 10.0 + unbreaking + durabilityFraction(stack);
    }

    // golden apple (and enchanted) > golden carrot > steak > everything else by nutrition
    private static double scoreFood(final ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemFood)) return 0.0;
        final Item item = stack.getItem();
        double priority = 0.0;
        if (item == Items.golden_apple) priority = 3000.0;
        else if (item == Items.golden_carrot) priority = 2000.0;
        else if (item == Items.cooked_beef) priority = 1000.0;
        final ItemFood food = (ItemFood) item;
        return priority + food.getHealAmount(stack) * 2.0 + food.getSaturationModifier(stack);
    }

    private static double durabilityFraction(final ItemStack stack) {
        if (stack == null || !stack.isItemStackDamageable()) return 1.0;
        return (double) (stack.getMaxDamage() - stack.getItemDamage()) / Math.max(1, stack.getMaxDamage());
    }

    // score a stack the way a slot's preference list sees it: if the first category ref that
    // matches the stack is a category, use the category score; otherwise the plain gear score.
    // used by AutoHotBar's upgrade-or-keep comparison so food/rod/pearl slots rank correctly.
    public static double scoreForPrefs(final List<String> prefs, final ItemStack stack) {
        if (stack == null) return 0.0;
        if (prefs != null) {
            for (final String key : prefs) {
                if (isCategoryKey(key)) {
                    final int cat = staticCategoryIndexOf(key);
                    if (cat >= 0 && bucket(stack) == cat) return categoryScore(cat, stack);
                }
            }
        }
        return redacted.client.util.player.InventoryUtil.scoreItem(stack);
    }

    public List<Category> getCategories() { return sharedCategories(); }

    // the shared registry model -- nine categories filled with their item variants (lazy-built once).
    // CleanerGridValue reuses this so there's a single source of truth and no second registry walk.
    public static List<Category> sharedCategories() { ensureBuilt(); return CATEGORIES; }

    public Entry entryForKey(final String key) { ensureBuilt(); return BY_KEY.get(key); }

    public ItemStack iconForKey(final String key) {
        if (isCategoryKey(key)) {
            final int idx = categoryIndexOf(key);
            return idx >= 0 ? CATEGORIES.get(idx).getIcon() : null;
        }
        final Entry e = entryForKey(key);
        return e == null ? null : e.getIcon();
    }

    public boolean isExcluded(final String key) { return excluded.contains(key); }

    public List<List<String>> getSlots() { return slots; }

    // mutators: change the working model, then push a fresh snapshot so Value.setValue's equals
    // check doesn't no-op the save
    public void toggleItem(final String key) {
        if (!excluded.remove(key)) excluded.add(key);   // click toggles exclusion from its category
        setValue(snapshot());
    }

    // append to a slot's preference list (first drop = primary). re-dropping moves it to the end.
    public void dropOnSlot(final int slotIdx, final String key) {
        if (slotIdx < 0 || slotIdx >= SLOTS || key == null) return;
        final List<String> list = slots.get(slotIdx);
        list.remove(key);
        list.add(key);
        setValue(snapshot());
    }

    public void removeFromSlot(final int slotIdx, final String key) {
        if (slotIdx < 0 || slotIdx >= SLOTS) return;
        if (slots.get(slotIdx).remove(key)) setValue(snapshot());
    }

    @Override
    public void setValue(final Map<String, Object> map) {
        syncFromMap(map);          // keep slots + enabled consistent on both toggle and config load
        super.setValue(map);
    }

    @Override
    public void applyConfig(final Object raw) {
        if (!(raw instanceof Map)) return;
        final Map<String, Object> map = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
            if (e.getKey() != null) map.put(String.valueOf(e.getKey()), e.getValue());
        }
        setValue(map);
    }

    private Map<String, Object> snapshot() {
        final Map<String, Object> m = new LinkedHashMap<>();
        final List<Object> slotsOut = new ArrayList<>();
        for (final List<String> s : slots) slotsOut.add(new ArrayList<>(s));   // slot order is the preference order
        m.put("slots", slotsOut);
        // sort excluded so equal sets serialize identically -- HashSet order would otherwise churn saves
        final List<String> ex = new ArrayList<>(excluded);
        java.util.Collections.sort(ex);
        m.put("excluded", ex);
        return m;
    }

    // gson hands the map back with List values and String elements; rebuild defensively
    private void syncFromMap(final Map<String, Object> map) {
        for (final List<String> s : slots) s.clear();
        excluded.clear();
        if (map == null) return;

        final Object slotsRaw = map.get("slots");
        if (slotsRaw instanceof List) {
            final List<?> outer = (List<?>) slotsRaw;
            for (int i = 0; i < SLOTS && i < outer.size(); i++) {
                final Object inner = outer.get(i);
                if (inner instanceof List) {
                    for (final Object o : (List<?>) inner) {
                        if (o instanceof String) slots.get(i).add((String) o);
                    }
                }
            }
        }
        final Object exRaw = map.get("excluded");
        if (exRaw instanceof List) {
            for (final Object o : (List<?>) exRaw) {
                if (o instanceof String) excluded.add((String) o);
            }
        }
    }

    private static Map<String, Object> defaultMap() {
        final Map<String, Object> m = new LinkedHashMap<>();
        final List<Object> s = new ArrayList<>();
        // default layout: sword, axe, best-knockback rod, bow, pearls (fallback throwables),
        // other throwables, best food (gapple > golden carrot > steak), blocks
        s.add(java.util.Collections.singletonList("@cat:Sword"));
        s.add(java.util.Collections.singletonList("@cat:Axe"));
        s.add(java.util.Collections.singletonList("@cat:Fishing Rod"));
        s.add(java.util.Collections.singletonList("@cat:Bow"));
        final List<String> pearlSlot = new ArrayList<>();
        pearlSlot.add("@cat:Pearl");
        pearlSlot.add("@cat:Throwables");
        s.add(pearlSlot);
        s.add(java.util.Collections.singletonList("@cat:Throwables"));
        s.add(java.util.Collections.singletonList("@cat:Food"));
        s.add(java.util.Collections.singletonList("@cat:Blocks"));
        s.add(new ArrayList<String>());
        m.put("slots", s);
        m.put("excluded", new ArrayList<String>());
        return m;
    }

    // transient GUI state
    public boolean isWidgetOpen() { return widgetOpen; }
    public void setWidgetOpen(final boolean v) { widgetOpen = v; }
    public int getOpenCategory() { return openCategory; }
    public void setOpenCategory(final int v) { openCategory = v; scroll = 0; filteredKey = null; }
    public String getSearch() { return search; }
    public void setSearch(final String v) { search = v == null ? "" : v; scroll = 0; }
    public double getScroll() { return scroll; }
    public void setScroll(final double v) { scroll = v; }

    // the open category's entries filtered by the search box, recomputed only when category/search changes
    public List<Entry> getFilteredEntries() {
        ensureBuilt();
        if (openCategory < 0 || openCategory >= CATEGORIES.size()) return java.util.Collections.emptyList();
        final String q = search.toLowerCase();
        final String key = openCategory + "|" + q;
        if (!key.equals(filteredKey)) {
            filteredKey = key;
            filtered = new ArrayList<>();
            for (final Entry e : CATEGORIES.get(openCategory).getEntries()) {
                if (q.isEmpty() || e.displayName().toLowerCase().contains(q)) filtered.add(e);
            }
        }
        return filtered;
    }
}
