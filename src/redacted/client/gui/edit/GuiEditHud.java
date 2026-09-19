package redacted.client.gui.edit;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.impl.Render2DEvent;
import redacted.client.font.Fonts;
import redacted.client.font.Weight;
import redacted.client.module.ModuleManager;
import redacted.client.module.impl.render.EntityESP;
import redacted.client.module.impl.render.HUD;
import redacted.client.module.impl.render.Mirror;
import redacted.client.util.gui.Draggable;
import redacted.client.util.gui.DragManager;
import redacted.client.value.impl.BooleanValue;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edit-HUD screen. Controls:
 *  - LMB drag an element: move it (corner handle resizes).
 *  - LMB drag EMPTY space: draw a box; everything it touches becomes one temporary
 *    group, then dragging any selected element moves the whole group together,
 *    keeping every element's relative distance.
 *  - SHIFT + RMB on an element: hide it AND disable its module. It stays hidden in
 *    this screen until the module is re-enabled.
 *  - MMB on an element: reset its position. L-SHIFT ignores the snap-to-center lines.
 */
public class GuiEditHud extends GuiScreen {

    // box-select state
    private boolean marqueeActive;
    private int marqueeX, marqueeY, marqueeEndX, marqueeEndY;

    // group selection + group drag
    private final List<Draggable> selected = new ArrayList<Draggable>();
    private boolean groupDragging;
    private int groupStartMouseX, groupStartMouseY;
    private final Map<Draggable, float[]> groupStart = new LinkedHashMap<Draggable, float[]>();

    private boolean anyShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private Draggable hitTest(int mx, int my) {
        DragManager dm = Client.getInstance().getDragManager();
        final List<Draggable> all = new ArrayList<Draggable>(dm.getAll().values());
        // topmost first: later-registered elements are drawn last / on top
        for (int i = all.size() - 1; i >= 0; i--) {
            Draggable d = all.get(i);
            if (!d.isHidden() && (d.overBody(mx, my) || d.overResize(mx, my))) return d;
        }
        return null;
    }

    private void clearSelection() {
        selected.clear();
        groupDragging = false;
        groupStart.clear();
    }

    // Disabled modules never receive Render2DEvent, so their elements would sit here as blank
    // boxes. Drive their draw paths directly; each module's edit-mode branch substitutes sample
    // data where live data is missing. Mirror gets a cheap pane instead of a real world pass.
    // (Notifications needs no driving: EntityRenderer calls NotificationManager.render every frame.)
    private void renderDisabledPreviews(float partialTicks) {
        try {
            // drawRect above left its fill color bound and blend off; fonts would render tinted
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            Render2DEvent event = new Render2DEvent(partialTicks, new ScaledResolution(mc));
            ModuleManager mm = Client.getInstance().getModuleManager();
            HUD hud = mm.getModule(HUD.class);
            if (hud != null && !hud.isEnabled()) hud.onRender2D(event);
            EntityESP esp = mm.getModule(EntityESP.class);
            if (esp != null && !esp.isEnabled()) esp.onRender2D(event);
            Mirror mirror = mm.getModule(Mirror.class);
            if (mirror != null && !mirror.isEnabled()) mirror.renderEditPreview(event.getScaledResolution());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, new Color(0, 0, 0, 100).getRGB());

        DragManager dm = Client.getInstance().getDragManager();

        // follow the mouse while the box-select is being drawn
        if (marqueeActive) {
            marqueeEndX = mouseX;
            marqueeEndY = mouseY;
        }

        // group drag first so this frame's positions reflect the movement
        if (groupDragging) {
            int dx = mouseX - groupStartMouseX;
            int dy = mouseY - groupStartMouseY;
            for (Map.Entry<Draggable, float[]> e : groupStart.entrySet()) {
                Draggable d = e.getKey();
                float[] s = e.getValue();
                d.setX(s[0] + dx);
                d.setY(s[1] + dy);
                d.clampToScreen();
            }
        }

        renderDisabledPreviews(partialTicks);

        boolean draggingAny = false;
        boolean snappedX = false;
        boolean snappedY = false;

        for (Draggable d : dm.getAll().values()) {
            if (d.isHidden()) continue;
            d.onDraw(mouseX, mouseY);

            // highlight grouped/selected elements so the user sees what will move
            if (selected.contains(d)) {
                RenderUtilOutline.drawSelection(d);
            }

            if (d.isDragging() || d.isResizeDragging() || groupDragging) {
                draggingAny = true;
            }
            if (d.isSnappedX()) snappedX = true;
            if (d.isSnappedY()) snappedY = true;
        }

        if (marqueeActive) {
            int x1 = Math.min(marqueeX, marqueeEndX);
            int y1 = Math.min(marqueeY, marqueeEndY);
            int x2 = Math.max(marqueeX, marqueeEndX);
            int y2 = Math.max(marqueeY, marqueeEndY);
            drawRect(x1, y1, x2, y2, 0x2A4794FD);
            drawVerticalLine(x1, y1, y2, 0x994794FD);
            drawVerticalLine(x2, y1, y2, 0x994794FD);
            drawHorizontalLine(x1, x2, y1, 0x994794FD);
            drawHorizontalLine(x1, x2, y2, 0x994794FD);
            draggingAny = true;
        }

        if (draggingAny) {
            int cx = width / 2;
            int cy = height / 2;

            int colorX = snappedX ? 0x994794FD : 0x20FFFFFF;
            int colorY = snappedY ? 0x994794FD : 0x20FFFFFF;

            drawRect(cx - 1, 0, cx + 1, height, colorX);
            drawRect(0, cy - 1, width, cy + 1, colorY);
        }

        String info = selected.size() > 1 || groupDragging
                ? "Group of " + selected.size() + " selected - drag any to move all"
                : "Drag element to move | Drag EMPTY space to box-select a group | SHIFT+RMB hides+disables | Drag corner to resize | MMB resets | L-SHIFT ignores snap | ESC exits";
        try {
            Fonts.MAIN.get(14, Weight.REGULAR).draw(info, 10, 10, 0x66FFFFFF);
            Fonts.MAIN.get(14, Weight.REGULAR).draw("X: " + mouseX + " Y: " + mouseY, mouseX + 10, mouseY + 10, 0x66FFFFFF);
        } catch (Exception e) {
            mc.fontRendererObj.drawStringWithShadow(info, 10, 10, 0x66FFFFFF);
            mc.fontRendererObj.drawStringWithShadow("X: " + mouseX + " Y: " + mouseY, mouseX + 10, mouseY + 10, 0x66FFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        DragManager dm = Client.getInstance().getDragManager();

        if (mouseButton == 0) {
            Draggable hit = hitTest(mouseX, mouseY);

            if (hit != null) {
                if (hit.overResize(mouseX, mouseY)) {
                    clearSelection();
                    hit.onClick(mouseX, mouseY, 0);
                } else if (selected.contains(hit)) {
                    // dragging a selected element moves the whole group
                    groupDragging = true;
                    groupStartMouseX = mouseX;
                    groupStartMouseY = mouseY;
                    groupStart.clear();
                    for (Draggable d : selected) {
                        groupStart.put(d, new float[]{d.getX(), d.getY()});
                    }
                } else {
                    clearSelection();
                    hit.onClick(mouseX, mouseY, 0);
                }
                super.mouseClicked(mouseX, mouseY, mouseButton);
                return;
            }

            // empty space -> start a box-select
            clearSelection();
            marqueeActive = true;
            marqueeX = marqueeEndX = mouseX;
            marqueeY = marqueeEndY = mouseY;
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (mouseButton == 1) {
            Draggable hit = hitTest(mouseX, mouseY);
            if (hit != null && anyShiftDown()) {
                // hide + disable the sub-component if it has one, otherwise the whole module;
                // reappears when the gate is re-enabled
                BooleanValue gate = hit.getModule().getComponentToggle(hit.getName());
                if (gate != null) {
                    gate.setValue(false);
                    hit.hideWith(gate);
                } else {
                    hit.getModule().disable();
                    hit.hideWith(null);
                }
                clearSelection();
                super.mouseClicked(mouseX, mouseY, mouseButton);
                return;
            }
            if (hit != null && selected.contains(hit) && selected.size() > 1) {
                // right-click on the group: keep it, no-op (avoid clearing on stray RMB)
                super.mouseClicked(mouseX, mouseY, mouseButton);
                return;
            }
        }

        for (Draggable d : dm.getAll().values()) {
            d.onClick(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        DragManager dm = Client.getInstance().getDragManager();

        if (marqueeActive) {
            marqueeActive = false;
            marqueeEndX = mouseX;
            marqueeEndY = mouseY;
            finalizeMarquee();
        } else if (groupDragging) {
            groupDragging = false;
            groupStart.clear();
        } else {
            for (Draggable d : dm.getAll().values()) {
                d.onRelease(state);
            }
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    /** Collects every element whose box touches the drawn rectangle into {@link #selected}. */
    private void finalizeMarquee() {
        selected.clear();
        int x1 = Math.min(marqueeX, marqueeEndX);
        int y1 = Math.min(marqueeY, marqueeEndY);
        int x2 = Math.max(marqueeX, marqueeEndX);
        int y2 = Math.max(marqueeY, marqueeEndY);

        for (Draggable d : Client.getInstance().getDragManager().getAll().values()) {
            if (d.isHidden()) continue;
            float dx = d.getX(), dy = d.getY(), dw = d.getWidth(), dh = d.getHeight();
            if (dx < x2 && dx + dw > x1 && dy < y2 && dy + dh > y1) {
                selected.add(d);
            }
        }
    }

    /** Small helper to keep the draw path self-contained. */
    private static class RenderUtilOutline {
        static void drawSelection(Draggable d) {
            float x = d.getX(), y = d.getY(), w = d.getWidth(), h = d.getHeight();
            drawRoundedOutline(x - 2, y - 2, w + 4, h + 4, 0x994794FD);
        }
        private static void drawRoundedOutline(float x, float y, float w, float h, int color) {
            net.minecraft.client.gui.Gui.drawRect((int) x, (int) y, (int) (x + w), (int) (y + 1), color);
            net.minecraft.client.gui.Gui.drawRect((int) x, (int) (y + h - 1), (int) (x + w), (int) (y + h), color);
            net.minecraft.client.gui.Gui.drawRect((int) x, (int) y, (int) (x + 1), (int) (y + h), color);
            net.minecraft.client.gui.Gui.drawRect((int) (x + w - 1), (int) y, (int) (x + w), (int) (y + h), color);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            Client.getInstance().getDragManager().saveToConfig();
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
