package redacted.client.value.impl;

import redacted.client.module.Module;
import redacted.client.value.Value;

import java.awt.Color;

public class ColorValue extends Value<Integer> {

    private transient boolean pickerOpen;
    private transient float pickerHue;
    private transient float pickerSat;
    private transient float pickerVal;
    private transient boolean draggingSquare;
    private transient boolean draggingHue;

    public ColorValue(final String name, final Module parent, final int defaultValue) {
        super(name, parent, defaultValue);
        initPicker(defaultValue);
    }

    public <V> ColorValue(final String name, final Module parent, final int defaultValue, final Value<V> parentValue, final V requiredValue) {
        super(name, parent, defaultValue, parentValue, requiredValue);
        initPicker(defaultValue);
    }

    private void initPicker(final int rgb) {
        final float[] hsv = new float[3];
        Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsv);
        pickerHue = hsv[0];
        pickerSat = hsv[1];
        pickerVal = hsv[2];
    }

    public int getRGB() {
        return getValue() & 0xFFFFFF;
    }

    public float getHue() { return pickerHue; }
    public float getSat() { return pickerSat; }
    public float getVal() { return pickerVal; }

    public boolean isPickerOpen() { return pickerOpen; }
    public void setPickerOpen(final boolean v) { pickerOpen = v; }
    public boolean isDraggingSquare() { return draggingSquare; }
    public void setDraggingSquare(final boolean v) { draggingSquare = v; }
    public boolean isDraggingHue() { return draggingHue; }
    public void setDraggingHue(final boolean v) { draggingHue = v; }

    public void setHSB(final float h, final float s, final float v) {
        pickerHue = h;
        pickerSat = s;
        pickerVal = v;
        final int rgb = Color.HSBtoRGB(h, s, v);
        setValue(0xFF000000 | rgb);
    }

    public void syncFromValue() {
        final int rgb = getValue();
        final float[] hsv = new float[3];
        Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsv);
        pickerHue = hsv[0];
        pickerSat = hsv[1];
        pickerVal = hsv[2];
    }

    @Override
    public void applyConfig(final Object raw) {
        final Double parsed = asDouble(raw); // gson hands ints back as doubles
        if (parsed != null) {
            setValue(parsed.intValue());
            syncFromValue();
        }
    }
}
