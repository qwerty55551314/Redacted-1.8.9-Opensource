package redacted.client.value.impl;

import redacted.client.module.Module;
import redacted.client.value.Value;

import java.util.List;

public class RangeValue extends Value<double[]> {
    private final double min, max, increment;

    public RangeValue(String name, Module parent, double defaultLow, double defaultHigh,
                      double min, double max, double increment) {
        super(name, parent, new double[] { defaultLow, defaultHigh });
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    public <V>RangeValue(String name, Module parent, double defaultLow, double defaultHigh,
                      double min, double max, double increment, Value<V> parentValue, V requiredValue) {
        super(name, parent, new double[] { defaultLow, defaultHigh }, parentValue, requiredValue);
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    public double getMinimum() { return min; }
    public double getMaximum() { return max; }
    public double getIncrement() { return increment; }

    public double getRangeMin() { return getValue()[0]; }
    public double getRangeMax() { return getValue()[1]; }

    public void setRangeMin(double low) { setValue(new double[] { low, getRangeMax() }); }
    public void setRangeMax(double high) { setValue(new double[] { getRangeMin(), high }); }

    @Override
    public void setValue(double[] value) {
        // Always hand the base class a fresh array: it compares by reference/equals, and arrays
        // never deep-equal, so reusing the stored array would make every update a no-op.
        double lo = Math.max(min, Math.min(max, Math.min(value[0], value[1])));
        double hi = Math.max(min, Math.min(max, Math.max(value[0], value[1])));
        double[] current = getValue();
        if (current != null && current.length == 2 && current[0] == lo && current[1] == hi) {
            return;
        }
        super.setValue(new double[] { lo, hi });
    }

    @Override
    public void applyConfig(Object raw) {
        if (!(raw instanceof List)) return;
        List<?> list = (List<?>) raw;
        if (list.size() < 2) return;
        Double low = asDouble(list.get(0));
        Double high = asDouble(list.get(1));
        if (low != null && high != null) setValue(new double[] { low, high });
    }
}
