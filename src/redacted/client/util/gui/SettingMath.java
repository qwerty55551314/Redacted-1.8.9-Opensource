package redacted.client.util.gui;

import redacted.client.value.impl.RangeValue;

// shared slider math for the clickgui themes
public class SettingMath {

    // map a 0..1 track position to a snapped setting value
    public static double percentToValue(double min, double max, double inc, double pct) {
        pct = Math.max(0.0, Math.min(1.0, pct));
        double val = min + pct * (max - min);
        return Math.round(val / inc) * inc;
    }

    // inverse: where a value sits on the 0..1 track (clamped)
    public static double valueToPercent(double min, double max, double value) {
        if (max <= min) return 0.0;
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }

    // which RangeValue handle (0 = min, 1 = max) the mouse is closest to
    public static int nearestHandle(RangeValue r, double trackStartX, double trackWidth, double mouseX) {
        double minX = trackStartX + trackWidth * valueToPercent(r.getMinimum(), r.getMaximum(), r.getRangeMin());
        double maxX = trackStartX + trackWidth * valueToPercent(r.getMinimum(), r.getMaximum(), r.getRangeMax());
        return Math.abs(mouseX - minX) <= Math.abs(mouseX - maxX) ? 0 : 1;
    }
}
