package redacted.client.value.impl;

import net.minecraft.item.ItemStack;
import redacted.client.module.Module;
import redacted.client.value.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Cleaner's item picker. same nine-category registry grid as AutoHotBar (it borrows HotbarValue's
// shared model), but no hotbar slots -- instead each token carries a clean mode. a token is either an
// item key ("registry:meta") or a "@cat:Name" category ref; a per-item mode overrides its category's
// mode. the serialized value is token -> mode name; tokens with no entry are left alone.
public class CleanerGridValue extends Value<Map<String, String>> implements ItemPicker {

    // click cycle on a cell: unset -> DROP -> KEEP_ONE -> IGNORE -> unset
    public enum CleanerMode { DROP, KEEP_ONE, IGNORE }

    private final Map<String, CleanerMode> modes = new LinkedHashMap<>();

    // transient GUI state -- mirrors HotbarValue's picker, none serialized
    private transient boolean widgetOpen;
    private transient int openCategory = -1;
    private transient String search = "";
    private transient double scroll;
    private transient List<HotbarValue.Entry> filtered;
    private transient String filteredKey;

    public CleanerGridValue(final String name, final Module parent) {
        super(name, parent, new LinkedHashMap<String, String>());
    }

    public <V> CleanerGridValue(final String name, final Module parent, final Value<V> parentValue, final V requiredValue) {
        super(name, parent, new LinkedHashMap<String, String>(), parentValue, requiredValue);
    }

    // mode lookup / cycling
    public CleanerMode getMode(final String token) { return modes.get(token); }

    public void cycle(final String token) {
        if (token == null) return;
        final CleanerMode cur = modes.get(token);
        if (cur == null) modes.put(token, CleanerMode.DROP);
        else if (cur == CleanerMode.DROP) modes.put(token, CleanerMode.KEEP_ONE);
        else if (cur == CleanerMode.KEEP_ONE) modes.put(token, CleanerMode.IGNORE);
        else modes.remove(token);                 // IGNORE -> unset
        setValue(snapshot());
    }

    public void cycleBack(final String token) {
        if (token == null) return;
        final CleanerMode cur = modes.get(token);
        if (cur == null) modes.put(token, CleanerMode.IGNORE);
        else if (cur == CleanerMode.IGNORE) modes.put(token, CleanerMode.KEEP_ONE);
        else if (cur == CleanerMode.KEEP_ONE) modes.put(token, CleanerMode.DROP);
        else modes.remove(token);                 // DROP -> unset
        setValue(snapshot());
    }

    // the mode that applies to this stack: a per-item override beats its category's mode. null = leave it.
    public CleanerMode effectiveMode(final ItemStack stack) {
        final String key = HotbarValue.keyOf(stack);
        if (key == null) return null;
        final CleanerMode item = modes.get(key);
        if (item != null) return item;
        return modes.get(HotbarValue.categoryRef(HotbarValue.bucket(stack)));
    }

    // ItemPicker (shared registry model, no second walk)
    @Override
    public List<HotbarValue.Category> getCategories() { return HotbarValue.sharedCategories(); }

    @Override
    public List<HotbarValue.Entry> getFilteredEntries() {
        final List<HotbarValue.Category> cats = getCategories();
        if (openCategory < 0 || openCategory >= cats.size()) return Collections.emptyList();
        final String q = search.toLowerCase();
        final String key = openCategory + "|" + q;
        if (!key.equals(filteredKey)) {
            filteredKey = key;
            filtered = new ArrayList<>();
            for (final HotbarValue.Entry e : cats.get(openCategory).getEntries()) {
                if (q.isEmpty() || e.displayName().toLowerCase().contains(q)) filtered.add(e);
            }
        }
        return filtered;
    }

    @Override public boolean isWidgetOpen() { return widgetOpen; }
    @Override public void setWidgetOpen(final boolean v) { widgetOpen = v; }
    @Override public int getOpenCategory() { return openCategory; }
    @Override public void setOpenCategory(final int v) { openCategory = v; scroll = 0; filteredKey = null; }
    @Override public String getSearch() { return search; }
    @Override public void setSearch(final String v) { search = v == null ? "" : v; scroll = 0; }
    @Override public double getScroll() { return scroll; }
    @Override public void setScroll(final double v) { scroll = v; }

    // config IO
    @Override
    public void setValue(final Map<String, String> map) {
        syncFromMap(map);          // keep the mode map consistent on both cycle and config load
        super.setValue(map);
    }

    @Override
    public void applyConfig(final Object raw) {
        if (!(raw instanceof Map)) return;
        final Map<String, String> map = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
            if (e.getKey() == null) continue;
            final String v = asString(e.getValue());
            if (v != null) map.put(String.valueOf(e.getKey()), v);
        }
        setValue(map);
    }

    // sorted so equal sets serialize identically (matches HotbarValue's excluded handling) and so a
    // fresh map instance is handed to Value.setValue -- its equals check would otherwise no-op the save
    private Map<String, String> snapshot() {
        final Map<String, String> m = new TreeMap<>();
        for (final Map.Entry<String, CleanerMode> e : modes.entrySet()) m.put(e.getKey(), e.getValue().name());
        return m;
    }

    private void syncFromMap(final Map<String, String> map) {
        modes.clear();
        if (map == null) return;
        for (final Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey() != null) modes.put(e.getKey(), parseMode(e.getValue()));
        }
    }

    private static CleanerMode parseMode(final String s) {
        if (s != null) {
            try { return CleanerMode.valueOf(s); } catch (final IllegalArgumentException ignored) {}
        }
        return CleanerMode.DROP;   // unknown -> sane default
    }
}
