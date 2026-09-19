package redacted.client.value.impl;

import redacted.client.module.Module;
import redacted.client.value.Value;

public class NumberValue extends Value<Double> {
    private final double min, max, increment;

    public NumberValue(String name, Module parent, double defaultValue, double min, double max, double increment) {
        super(name, parent, defaultValue);
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    public <V>NumberValue(String name, Module parent, double defaultValue, double min, double max, double increment, Value<V> parentValue, V requiredValue) {
        super(name, parent, defaultValue, parentValue, requiredValue);
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    public double getMinimum() { return min; }
    public double getMaximum() { return max; }
    public double getIncrement() { return increment; }

    // shorthands so call sites don't have to do getValue().doubleValue() everywhere
    public double doubleValue() { return getValue(); }
    public float floatValue() { return getValue().floatValue(); }
    public int intValue() { return getValue().intValue(); }

    @Override
    public void setValue(Double value) {
        super.setValue(Math.max(min, Math.min(max, value)));
    }

    @Override
    public void applyConfig(Object raw) {
        Double parsed = asDouble(raw);
        if (parsed != null) setValue(parsed);
    }
}
