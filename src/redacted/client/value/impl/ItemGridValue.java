package redacted.client.value.impl;

import net.minecraft.item.ItemStack;
import redacted.client.module.Module;
import redacted.client.value.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// a row of toggleable item cells (ColdPlay's ItemGrid). the stored value is just the enabled
// entry names -- gson writes it as a string array, applyConfig reads it back. the entries
// themselves (icon + flag) are construction metadata and stay in sync with that list.
public class ItemGridValue extends Value<List<String>> {

    public static final class Entry {
        private final String name;
        private final ItemStack icon;
        private boolean enabled;

        public Entry(final String name, final ItemStack icon, final boolean enabled) {
            this.name = name;
            this.icon = icon;
            this.enabled = enabled;
        }

        public String getName() { return name; }
        public ItemStack getIcon() { return icon; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(final boolean enabled) { this.enabled = enabled; }
        public void toggle() { enabled = !enabled; }
    }

    private final List<Entry> entries;
    private transient boolean gridOpen;

    public ItemGridValue(final String name, final Module parent, final Entry... entries) {
        super(name, parent, enabledNames(entries));
        this.entries = Arrays.asList(entries);
    }

    public <V> ItemGridValue(final String name, final Module parent,
                             final Value<V> parentValue, final V requiredValue, final Entry... entries) {
        super(name, parent, enabledNames(entries), parentValue, requiredValue);
        this.entries = Arrays.asList(entries);
    }

    private static List<String> enabledNames(final Entry[] entries) {
        final List<String> names = new ArrayList<>();
        for (final Entry e : entries) {
            if (e.isEnabled()) names.add(e.getName());
        }
        return names;
    }

    public List<Entry> getEntries() { return entries; }

    public boolean isSelected(final String name) {
        for (final Entry e : entries) {
            if (e.getName().equals(name)) return e.isEnabled();
        }
        return false;
    }

    public boolean isGridOpen() { return gridOpen; }
    public void setGridOpen(final boolean v) { gridOpen = v; }

    public void toggle(final Entry entry) {
        entry.toggle();
        // hand a fresh list -- Value.setValue compares by equals, so reusing the stored one no-ops the save
        setValue(enabledNames(entries.toArray(new Entry[0])));
    }

    @Override
    public void setValue(final List<String> names) {
        // keep entries and the serialized list consistent whether the change is a toggle or a config load
        if (entries != null && names != null) {
            for (final Entry e : entries) e.setEnabled(names.contains(e.getName()));
        }
        super.setValue(names);
    }

    @Override
    public void applyConfig(final Object raw) {
        if (!(raw instanceof List)) return;
        final List<String> names = new ArrayList<>();
        for (final Object o : (List<?>) raw) {
            final String s = asString(o);
            if (s != null) names.add(s);
        }
        setValue(names);
    }
}
