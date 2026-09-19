package redacted.client.util.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjglx.input.Keyboard;
import redacted.client.font.Fonts;
import redacted.client.font.Weight;
import redacted.client.module.Module;
import redacted.client.util.animation.Animation;
import redacted.client.util.animation.Easing;
import redacted.client.util.render.RenderUtil;
import redacted.client.value.impl.BooleanValue;

import java.awt.Color;

public class Draggable {
    private static final float HANDLE_SIZE = 6;
    private static final float SNAP_RADIUS = 5.0f;

    private final Module module;
    private final String name;
    private float x, y;
    // normalized (0..1) position of the top-left corner, kept in sync with x/y so the layout
    // survives resolution changes (window starts small -> fullscreen) instead of drifting left.
    // -1 = "unplaced" sentinel (module places me at my default spot at render time).
    private float relX, relY;
    private float baseWidth, baseHeight;
    private float scale = 1.0f;
    
    private float startX, startY;
    private boolean dragging;
    
    private boolean resizeDragging;
    private float resizeStartMouseX, resizeStartMouseY;
    private float resizeStartScale;
    
    private boolean snappedX, snappedY;

    // shift+RMB in the edit GUI hides this element and disables its sub-component
    // (or module if there's no per-component toggle); it reappears automatically once
    // that gate is turned back on (isHidden auto-clears).
    private boolean hidden;
    private BooleanValue hiddenGate;
    
    private final float defaultX, defaultY;
    private final Animation hoverAnimation = new Animation(Easing.EASE_OUT_QUAD, 250);

    // short-lived shared resolution cache: accessors (getX/getY) and drag math used to
    // construct a ScaledResolution per call -- 20-60+ allocations per frame across the
    // HUD elements, for a value that only changes on window resize or GUI-scale change
    private static ScaledResolution cachedSr;
    private static long cachedSrAt;
    private static ScaledResolution cachedResolution() {
        final long now = System.currentTimeMillis();
        if (cachedSr == null || now - cachedSrAt >= 200L) {
            try {
                cachedSr = new ScaledResolution(Minecraft.getMinecraft());
            } catch (Exception ignored) {}
            cachedSrAt = now;
        }
        return cachedSr;
    }

    public Draggable(Module module, String name, float defaultX, float defaultY, float baseWidth, float baseHeight) {
        this.module = module;
        this.name = name;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.x = defaultX;
        this.y = defaultY;
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
        this.relX = defaultX;
        this.relY = defaultY;
        if (defaultX >= 0 && defaultY >= 0) storeRatio();   // pixel defaults -> normalized
    }

    // write the current pixel position back as a normalized ratio of the scaled resolution
    private void storeRatio() {
        try {
            ScaledResolution sr = cachedResolution();
            relX = x / Math.max(1.0f, sr.getScaledWidth());
            relY = y / Math.max(1.0f, sr.getScaledHeight());
        } catch (Exception ignored) {}
    }

    // re-derive the pixel position from the ratio so a window resize repositions the element
    // proportionally instead of leaving it at a stale pixel offset
    private void syncToScreen() {
        if (relX < 0 || relY < 0) return;   // unplaced sentinel
        try {
            ScaledResolution sr = cachedResolution();
            x = relX * sr.getScaledWidth();
            y = relY * sr.getScaledHeight();
        } catch (Exception ignored) {}
    }

    public void onDraw(int mouseX, int mouseY) {
        syncToScreen();   // reposition proportionally if the window was resized since last frame
        // unplaced sentinel: park the dummy somewhere visible (bottom-right) so it can be grabbed
        // in the edit GUI even before its module ever rendered. not persisted unless the user
        // actually drags it (storeRatio only runs on drag).
        if (x < 0 || y < 0) {
            try {
                ScaledResolution sr = cachedResolution();
                x = sr.getScaledWidth() - getWidth() - 16;
                y = sr.getScaledHeight() - getHeight() - 16;
            } catch (Exception ignored) {}
        }
        snappedX = false;
        snappedY = false;

        if (dragging) {
            float targetX = mouseX - startX;
            float targetY = mouseY - startY;

            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);

            if (!shift) {
                try {
                    ScaledResolution sr = cachedResolution();
                    float centerX = sr.getScaledWidth() / 2.0f;
                    float centerY = sr.getScaledHeight() / 2.0f;

                    float elemCenterX = targetX + getWidth() / 2.0f;
                    float elemCenterY = targetY + getHeight() / 2.0f;

                    if (Math.abs(elemCenterX - centerX) < SNAP_RADIUS) {
                        targetX = centerX - getWidth() / 2.0f;
                        snappedX = true;
                    }
                    if (Math.abs(elemCenterY - centerY) < SNAP_RADIUS) {
                        targetY = centerY - getHeight() / 2.0f;
                        snappedY = true;
                    }
                } catch (Exception ignored) {}
            }

            x = targetX;
            y = targetY;
            clampToScreen();
            storeRatio();   // keep the normalized position in step with the drag
        }

        if (resizeDragging) {
            double startDist = Math.hypot(resizeStartMouseX - x, resizeStartMouseY - y);
            double currentDist = Math.hypot(mouseX - x, mouseY - y);
            
            float newScale = resizeStartScale;
            if (startDist > 0) {
                newScale = (float) (resizeStartScale * (currentDist / startDist));
            }

            try {
                ScaledResolution sr = cachedResolution();
                float maxScaleX = (sr.getScaledWidth() - x) / Math.max(1.0f, baseWidth);
                float maxScaleY = (sr.getScaledHeight() - y) / Math.max(1.0f, baseHeight);
                newScale = Math.min(newScale, Math.min(maxScaleX, maxScaleY));
            } catch (Exception ignored) {}

            scale = Math.max(0.3f, Math.min(3.0f, newScale));
        }

        float w = getWidth();
        float h = getHeight();

        boolean hoverBody = GUIUtil.mouseOver(x, y, w, h, mouseX, mouseY);
        boolean hoverHandle = GUIUtil.mouseOver(x + w - HANDLE_SIZE, y + h - HANDLE_SIZE,
                HANDLE_SIZE * 2, HANDLE_SIZE * 2, mouseX, mouseY);
        boolean hovering = hoverBody || hoverHandle;
        
        hoverAnimation.run(hovering ? 1.0 : 0.0);
        float hover = (float) hoverAnimation.getValue();

        RenderUtil.drawRoundedRect(x - 2, y - 2, w + 4, h + 4, 4, 0x22000000);
        RenderUtil.drawRoundedOutline(x - 2, y - 2, w + 4, h + 4, 4, 1.0f,
                new Color(255, 255, 255, (int) (60 * hover)).getRGB());

        RenderUtil.drawFilledCircle(x + w, y + h, HANDLE_SIZE,
                resizeDragging || hoverHandle ? 0xCC4794FD : 0x66FFFFFF);

        // name label only while hovered -- an always-on label overlaps the content of small elements
        if (hover > 0.1f) {
            int labelColor = ((int) (0x99 * hover) << 24) | 0xFFFFFF;
            try {
                float lw = Fonts.MAIN.get(12, Weight.REGULAR).width("[" + name + "]");
                float lh = Fonts.MAIN.get(12, Weight.REGULAR).height();
                Fonts.MAIN.get(12, Weight.REGULAR).draw("[" + name + "]", x + w - lw - 4, y + h - lh - 4, labelColor);
            } catch (Exception e) {
                Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow("[" + name + "]",
                        x + w - Minecraft.getMinecraft().fontRendererObj.getStringWidth("[" + name + "]") - 4,
                        y + h - 10, labelColor);
            }
        }
    }

    public void onClick(int mouseX, int mouseY, int button) {
        syncToScreen();   // same re-sync as onDraw so the grab offset matches what's on screen
        float w = getWidth();
        float h = getHeight();

        if (button == 2) {
            if (GUIUtil.mouseOver(x, y, w, h, mouseX, mouseY)) resetPosition();
            return;
        }

        if (button != 0) return;

        boolean inResize = GUIUtil.mouseOver(x + w - HANDLE_SIZE, y + h - HANDLE_SIZE,
                HANDLE_SIZE * 2, HANDLE_SIZE * 2, mouseX, mouseY);
                
        if (inResize) {
            resizeDragging = true;
            resizeStartMouseX = mouseX;
            resizeStartMouseY = mouseY;
            resizeStartScale = scale;
            return;
        }
        
        if (GUIUtil.mouseOver(x, y, w, h, mouseX, mouseY)) {
            dragging = true;
            startX = mouseX - x;
            startY = mouseY - y;
        }
    }

    public void onRelease(int button) {
        if (button == 0) {
            dragging = false;
            resizeDragging = false;
        }
    }

    public void resetPosition() {
        x = defaultX;
        y = defaultY;
        scale = 1.0f;
        relX = defaultX;
        relY = defaultY;
        if (defaultX >= 0 && defaultY >= 0) storeRatio();
    }

    public void clampToScreen() {
        try {
            ScaledResolution sr = cachedResolution();
            float sw = sr.getScaledWidth();
            float sh = sr.getScaledHeight();
            if (x < 0) x = 0;
            if (y < 0) y = 0;
            if (x + getWidth() > sw) x = sw - getWidth();
            if (y + getHeight() > sh) y = sh - getHeight();
            storeRatio();   // persist the clamp, or the next syncToScreen() undoes it
        } catch (Exception ignored) {}
    }

    public boolean isDragging() { return dragging; }
    public boolean isResizeDragging() { return resizeDragging; }
    public boolean isSnappedX() { return snappedX; }
    public boolean isSnappedY() { return snappedY; }

    /** Over the element body (not the resize handle). */
    public boolean overBody(int mx, int my) {
        return GUIUtil.mouseOver(x, y, getWidth(), getHeight(), mx, my);
    }

    /** Over the bottom-right resize handle. */
    public boolean overResize(int mx, int my) {
        return GUIUtil.mouseOver(x + getWidth() - HANDLE_SIZE, y + getHeight() - HANDLE_SIZE,
                HANDLE_SIZE * 2, HANDLE_SIZE * 2, mx, my);
    }

    /** Hidden via shift+RMB in the edit GUI. Clears itself once the gate is re-enabled. */
    public boolean isHidden() {
        if (hidden) {
            if (hiddenGate != null) {
                if (hiddenGate.getValue()) hidden = false;
            } else if (module != null && module.isEnabled()) {
                hidden = false;
            }
        }
        return hidden;
    }

    /** Hide this element; {@code gate} is the sub-component toggle that must be re-enabled to show it. */
    public void hideWith(BooleanValue gate) {
        this.hidden = true;
        this.hiddenGate = gate;
    }

    public Module getModule() { return module; }
    public String getName() { return name; }

    // getters re-sync the pixel position from the ratio first, so callers always read a position
    // that fits the CURRENT window (a resolution change mid-session repositions proportionally)
    public float getX() { syncToScreen(); return x; }
    public float getY() { syncToScreen(); return y; }
    public float getWidth() { return baseWidth * scale; }
    public float getHeight() { return baseHeight * scale; }
    public float getScale() { return scale; }
    public float getBaseWidth() { return baseWidth; }
    public float getBaseHeight() { return baseHeight; }
    public float getDefaultX() { return defaultX; }
    public float getDefaultY() { return defaultY; }

    public void setX(float x) { this.x = x; storeRatio(); }
    public void setY(float y) { this.y = y; storeRatio(); }

    // restore from a saved normalized position (config format), then place at the current size
    public void setRel(float relX, float relY) {
        this.relX = relX;
        this.relY = relY;
        syncToScreen();
    }

    public float getRelX() { return relX; }
    public float getRelY() { return relY; }
    public void setBaseWidth(float w) { this.baseWidth = Math.max(10, w); }
    public void setBaseHeight(float h) { this.baseHeight = Math.max(10, h); }
    public void setScale(float s) { this.scale = Math.max(0.3f, Math.min(3.0f, s)); }
}
