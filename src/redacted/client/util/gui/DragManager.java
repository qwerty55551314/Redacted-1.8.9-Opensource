package redacted.client.util.gui;

import redacted.client.Client;
import redacted.client.module.Module;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DragManager {
    private final Map<String, Draggable> draggables = new LinkedHashMap<>();

    // reference scaled resolution the legacy pixel positions were authored on (1080p fullscreen,
    // gui scale 2). used to convert old absolute-pixel configs to normalized ratios.
    private static final float LEGACY_REF_WIDTH = 960.0f;
    private static final float LEGACY_REF_HEIGHT = 540.0f;

    public Draggable register(Module module, String name, float defaultX, float defaultY, float width, float height) {
        if (draggables.containsKey(name)) {
            return draggables.get(name);
        }
        Draggable d = new Draggable(module, name, defaultX, defaultY, width, height);
        draggables.put(name, d);

        Map<String, double[]> saved = Client.getInstance().getThemeManager().getDraggablePositions();
        if (saved != null && saved.containsKey(name)) {
            applySaved(d, saved.get(name));
        }
        return d;
    }

    public Draggable get(String name) {
        return draggables.get(name);
    }

    public Map<String, Draggable> getAll() {
        return draggables;
    }

    public void loadFromTheme() {
        Map<String, double[]> saved = Client.getInstance().getThemeManager().getDraggablePositions();
        if (saved == null) return;

        for (Map.Entry<String, double[]> entry : saved.entrySet()) {
            Draggable d = draggables.get(entry.getKey());
            if (d != null) applySaved(d, entry.getValue());
        }
    }

    // saved format is now {relX, relY, scale} -- normalized ratios in [-1, 1] (legacy configs
    // hold absolute pixels and are converted once, then re-saved as ratios).
    private void applySaved(Draggable d, double[] pos) {
        boolean ratio = pos.length >= 2 && pos[0] >= -1.0 && pos[0] <= 1.0 && pos[1] >= -1.0 && pos[1] <= 1.0;
        if (ratio) {
            d.setRel((float) pos[0], (float) pos[1]);
        } else {
            // legacy absolute pixels: normalize against the reference 1080p scaled resolution so
            // the layout survives a small startup window -> fullscreen transition (old behaviour
            // clamped to the small window and permanently drifted left).
            d.setRel((float) (Math.max(0.0, Math.min(1.0, pos[0] / LEGACY_REF_WIDTH))),
                     (float) (Math.max(0.0, Math.min(1.0, pos[1] / LEGACY_REF_HEIGHT))));
        }
        if (pos.length > 2) d.setScale((float) pos[2]);
    }

    public void saveToConfig() {
        resolveOverlaps();
        Map<String, double[]> data = new LinkedHashMap<>();
        // keep positions for elements that never rendered this session, otherwise
        // a menu-only launch (alt relog -> quit) wipes the whole file
        Map<String, double[]> existing = Client.getInstance().getThemeManager().getDraggablePositions();
        if (existing != null) data.putAll(existing);
        for (Map.Entry<String, Draggable> entry : draggables.entrySet()) {
            Draggable d = entry.getValue();
            data.put(entry.getKey(), new double[]{d.getRelX(), d.getRelY(), d.getScale()});
        }
        Client.getInstance().getThemeManager().setDraggablePositions(data);
    }

    public void resetAll() {
        for (Draggable d : draggables.values()) {
            d.resetPosition();
        }
        saveToConfig();
    }

    // edit mode lets the user overlap dummies freely, but a saved layout should never have two
    // elements stacked: push each overlapping element below the previously-placed one (+ gap).
    private static final float OVERLAP_GAP = 4.0f;

    public void resolveOverlaps() {
        final List<Draggable> placed = new java.util.ArrayList<>();
        for (Draggable d : draggables.values()) {
            if (d.getRelX() < 0 || d.getRelY() < 0) continue;   // unplaced sentinels can't overlap
            float x = d.getX(), y = d.getY(), w = d.getWidth(), h = d.getHeight();
            for (Draggable other : placed) {
                final float ox = other.getX(), oy = other.getY(), ow = other.getWidth(), oh = other.getHeight();
                if (x < ox + ow && x + w > ox && y < oy + oh && y + h > oy) {
                    y = oy + oh + OVERLAP_GAP;   // drop below the already-placed element
                }
            }
            if (y != d.getY()) {
                d.setY(y);
                d.clampToScreen();
            }
            placed.add(d);
        }
    }
}
