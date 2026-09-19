package redacted.client.value.impl;

import redacted.client.module.Module;
import redacted.client.value.Value;

public class BooleanValue extends Value<Boolean> {
    public BooleanValue(String name, Module parent, Boolean defaultValue) {
        super(name, parent, defaultValue);
    }

    public <V>BooleanValue(String name, Module parent, Boolean defaultValue, Value<V> parentValue, V requiredValue) {
        super(name, parent, defaultValue, parentValue, requiredValue);
    }
    public void toggle() { setValue(!getValue()); }

    @Override
    public void applyConfig(Object raw) {
        Boolean parsed = asBoolean(raw);
        if (parsed != null) setValue(parsed);
    }
}
