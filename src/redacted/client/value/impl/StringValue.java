package redacted.client.value.impl;

import redacted.client.module.Module;
import redacted.client.value.Value;

public class StringValue extends Value<String> {
    public StringValue(final String name, final Module parent, final String defaultValue) {
        super(name, parent, defaultValue);
    }

    public <V> StringValue(final String name, final Module parent, final String defaultValue,
                           final Value<V> parentValue, final V requiredValue) {
        super(name, parent, defaultValue, parentValue, requiredValue);
    }

    @Override
    public void applyConfig(final Object raw) {
        final String parsed = asString(raw);
        if (parsed != null) setValue(parsed);
    }
}
