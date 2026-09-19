package redacted.client.value.impl;

import redacted.client.module.Module;
import redacted.client.value.Value;
import java.util.Arrays;
import java.util.List;

public class ModeValue extends Value<String> {
    private final List<String> modes;

    public ModeValue(String name, Module parent, String defaultValue, String... modes) {
        super(name, parent, defaultValue);
        this.modes = Arrays.asList(modes);
    }

    public <V>ModeValue(String name, Module parent, Value<V> parentValue, V requiredValue, String defaultValue, String... modes) {
        super(name, parent, defaultValue, parentValue, requiredValue);
        this.modes = Arrays.asList(modes);
    }

    public List<String> getModes() { return modes; }

    public void cycle() {
        int index = modes.indexOf(getValue());
        setValue(modes.get((index + 1) % modes.size()));
    }

    public void cycleBackwards() {
        int index = modes.indexOf(getValue());
        setValue(modes.get((index - 1 + modes.size()) % modes.size()));
    }

    @Override
    public void applyConfig(Object raw) {
        String parsed = asString(raw);
        if (parsed != null && modes.contains(parsed)) setValue(parsed);
    }
}
