package redacted.client.value;

import redacted.client.Client;
import redacted.client.module.Module;

public abstract class Value<T> {
    private final String name;
    private T value;
    private final T defaultValue;
    private final Value<?> parent;
    private final Object requiredValue;
    private String description = "";

    public Value(String name, Module parent, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.parent = null;
        this.requiredValue = null;
        parent.addValue(this);
    }

    public <V>Value(String name, Module parent, T defaultValue, Value<V> parentValue, V requiredValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.parent = parentValue;
        this.requiredValue = requiredValue;
        parent.addValue(this);
    }

    public String getName() { return name; }

    public String getDescription() { return description; }

    // self-typed so it chains inline on the `private final XxxValue x = new XxxValue(...)` fields
    // without dropping the concrete type -- the assignment target infers V back to the subclass.
    @SuppressWarnings("unchecked")
    public <V extends Value<T>> V describe(final String description) {
        this.description = description;
        return (V) this;
    }

    public T getValue() { return value; }

    // dependency parent (null for top-level values) -- lets the ClickGUI draw owner/child grouping
    public Value<?> getParent() { return parent; }

    public void reset() { this.value = this.defaultValue; }

    public void setValue(T value) {
        if (this.value == value || (this.value != null && this.value.equals(value))) return;
        this.value = value;
        Client.getInstance().getConfigManager().save();
    }

    public boolean isVisible() {
        return parent == null || (parent.getValue().equals(requiredValue) && parent.isVisible());
    }

    // gson hands saved settings back as untyped soup (numbers as doubles, arrays as lists);
    // each value type owns turning that back into itself. Abstract on purpose -- a new value
    // type can't ship without config IO (ColorValue once reset to defaults every restart
    // because the old central instanceof chain didn't know it).
    public abstract void applyConfig(Object raw);

    protected static Boolean asBoolean(final Object raw) {
        if (raw instanceof Boolean) return (Boolean) raw;
        if (raw instanceof String) {
            final String value = ((String) raw).trim();
            if ("true".equalsIgnoreCase(value)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(value)) return Boolean.FALSE;
        }
        return null;
    }

    protected static Double asDouble(final Object raw) {
        if (raw instanceof Number) return ((Number) raw).doubleValue();
        if (raw instanceof String) {
            try { return Double.valueOf((String) raw); } catch (final NumberFormatException ignored) {}
        }
        return null;
    }

    protected static String asString(final Object raw) {
        return raw instanceof String ? (String) raw : null;
    }
}
