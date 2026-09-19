package redacted.client.util.notification;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import redacted.client.Client;
import redacted.client.font.Fonts;
import redacted.client.gui.edit.GuiEditHud;
import redacted.client.font.Weight;
import redacted.client.module.impl.render.Notifications;
import redacted.client.util.font.Font;
import redacted.client.util.gui.Draggable;
import redacted.client.util.render.ColorUtil;
import redacted.client.util.render.RenderUtil;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class NotificationManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double REFERENCE_HEIGHT = 540.0;
    private static final double NOTIFICATION_WIDTH = 220.0;

    private final List<Notification> notifications = new ArrayList<>();
    // sample cards shown in the edit GUI when the real queue is empty
    private final List<Notification> editPreviews = new ArrayList<>();

    // render-thread scratch: started-cards filter + per-card heights, reused per frame
    private final List<Notification> startedScratch = new ArrayList<>();
    private double[] heightScratch = new double[8];
    // sort only when the pool's membership changes; remaining times shift uniformly
    // so a stable sort's relative order is unchanged between membership changes
    private boolean poolDirty;

    // "INFO" -> "Info" computed once per type
    private static final Map<NotificationType, String> TITLES = new EnumMap<NotificationType, String>(NotificationType.class);

    public void showNotification(NotificationType type, String description, float seconds) {
        showNotification(type, description, seconds, 0.0F, false);
    }
    
    public void showNotification(NotificationType type, String description, float seconds, float delay, boolean force) {
        if (!canShow(type, force)) return;

        notifications.add(new Notification(type, description, seconds, delay));
        poolDirty = true;
    }

    public void render(ScaledResolution sr) {
        if (Minecraft.getMinecraft().theWorld == null || Minecraft.getMinecraft().thePlayer == null) {
            notifications.clear();
            return;
        }

        Notifications module = Client.getInstance().getModuleManager().getModule(Notifications.class);
        // the edit GUI previews the element even while the module is off
        boolean editing = mc.currentScreen instanceof GuiEditHud;
        if (module == null || (!module.isEnabled() && !editing)) {
            notifications.clear();
            return;
        }

        long now = System.currentTimeMillis();
        if (notifications.removeIf(n -> now - n.getStartTime() >= n.getDurationMs())) poolDirty = true;
        if (!editing) editPreviews.clear();
        List<Notification> pool = notifications;
        if (notifications.isEmpty()) {
            if (!editing) return;
            // sample cards so the element isn't a blank box while placing it
            if (editPreviews.isEmpty()) {
                editPreviews.add(new Notification(NotificationType.INFO, "Preview notification", 600, 0));
                editPreviews.add(new Notification(NotificationType.WARNING, "Drag or resize me", 600, 0));
            }
            pool = editPreviews;
        }

        if (poolDirty && pool == notifications) {
            pool.sort((a, b) -> (int) (((a.getStartTime() + a.getDurationMs()) - now) - (((b.getStartTime() + b.getDurationMs()) - now))));
            poolDirty = false;
        }

        // Scale comes from Draggable, same as HUD ArrayList.
        // Recalculated every frame so resizing in Edit GUI takes effect immediately.
        Draggable d = module.getDraggable();
        double scale = d.getScale();
        double width = NOTIFICATION_WIDTH * scale;
        double padding = 8 * scale;
        double gap = 4 * scale;
        double margin = 16 * scale;
        
        // started cards only (delayed ones wait); filtered in place instead of a
        // stream pipeline + collect allocation per frame
        startedScratch.clear();
        for (int i = 0; i < pool.size(); i++) {
            final Notification notif = pool.get(i);
            if ((notif.getStartTime() - now) <= 0) startedScratch.add(notif);
        }
        final List<Notification> notifs = startedScratch;

        int count = notifs.size();
        if (heightScratch.length < count) heightScratch = new double[Math.max(8, count * 2)];
        double[] heights = heightScratch;
        double totalHeight = 0;
        for (int i = 0; i < count; i++) {
            heights[i] = getHeight(scale, module);
            totalHeight += heights[i];
        }
        totalHeight += (count - 1) * gap;

        float baseX = d.getX();
        float baseY = d.getY();
        // while the edit GUI is open it owns x/y -- re-anchoring here would snap the stack
        // to the corner mid-resize and overwrite drags (render runs again after drawScreen)
        if (!editing && (baseX < 0 || baseY < 0
                || baseX + width > sr.getScaledWidth()
                || baseY + totalHeight > sr.getScaledHeight())) {
            baseX = (float) (sr.getScaledWidth() - margin - width);
            float screenBottom = (float) (sr.getScaledHeight() - margin);
            baseY = screenBottom - (float) totalHeight;
            d.setX(baseX);
            d.setY(baseY);
            d.setBaseWidth((float) (width / d.getScale()));
            d.setBaseHeight((float) (totalHeight / d.getScale()));
        }

        float dScale = d.getScale();
        float bottomY = editing ? baseY + (float) totalHeight : d.getY() + d.getHeight();
        if (!editing) {
            baseY = bottomY - (float) totalHeight;
            d.setY(baseY);   // keep the stack bottom-anchored as cards come and go
        }
        d.setBaseWidth((float) (width / dScale));
        d.setBaseHeight((float) (totalHeight / dScale));

        double x = baseX;
        double y = bottomY;
        double textX = x + 34 * scale;   // room for the icon badge

        int headSize = headSize(scale);
        int descSize = descSize(scale);

        int timerSize = Fonts.size(16 * scale);
        Font headerFont = module.getSelectedFont(headSize, Weight.REGULAR);
        Font descFont = module.getSelectedFont(descSize, Weight.REGULAR);
        Font timerFont = module.getSelectedFont(timerSize, Weight.BOLD);

        for (int i = count - 1; i >= 0; i--) {
            Notification n = notifs.get(i);
            n.getAnimation().run(1.0);
            double fade = n.getAnimation().getValue();
            double h = heights[i];
            y -= h;

            int textAlpha = (int) (255 * fade);
            int cardAlpha = (int) (module.getCardAlpha() * fade);
            float iconA = (float) (Math.min(1.0, fade) * module.getIconAlpha() / 255.0);

            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

            // modern Material card (LiquidBounce++ style): dark grey card to match the clickgui
            // theme (NO type-colour wash -- that read as a blue blob), subtle grey border,
            // flat grey icon, type-colour only on the small title/time accent + draining bar
            RenderUtil.drawRoundedRect(x, y, width, h, 6 * scale,
                    (cardAlpha << 24) | 0x171A21);
            RenderUtil.drawRoundedOutline(x, y, width, h, 6 * scale, 1.0f,
                    ColorUtil.withAlpha(0xFF2A2F3A, (int) (170 * fade)));

            // flat grey icon texture (Glass v0.1 tenacity set, whitened, tinted 0.72 grey) -- no badge circle
            drawTypeIcon(n.getType(), x + 16 * scale, y + h / 2.0, 9 * scale, iconA);

            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();

            double headerY = y + padding;
            int headerColor = ColorUtil.withAlpha(n.getType().getColor(), textAlpha);

            headerFont.drawWithShadow(title(n.getType()), (float) textX, (float) headerY, headerColor);

            double headerH = headerFont.height() + 2 * scale;
            double descY = headerY + headerH;

            int descTextColor = ColorUtil.withAlpha(0xFFFFFFFF, textAlpha);

            descFont.drawWithShadow(n.getDescription(), (float) textX, (float) descY, descTextColor);

            // draining bottom bar (LB++ Material): full-width at spawn, SHRINKS with the
            // remaining time. inset by the corner radius so it stays inside the rounded card.
            double remaining = n.getRemainingRatio();
            if (remaining > 0) {
                final double rad = 6 * scale;
                final double barW = (width - rad * 2) * remaining;
                RenderUtil.drawRoundedRect(x + rad, y + h - 2 * scale, barW, 2 * scale, 1 * scale,
                        ColorUtil.withAlpha(n.getType().getColor(), (int) (230 * fade)));
            }

            double remainingSec = remaining * n.getDurationMs() / 1000.0;
            String timeText = n.getTimerText(remainingSec, this::formatTimeLeft);
            double timerMargin = 4 * scale;
            double timerTextWidth = timerFont.width(timeText);
            double timerRight = x + width - timerMargin;

            int timeColor = ColorUtil.withAlpha(n.getType().getColor(), textAlpha);
            timerFont.drawWithShadow(timeText, (float) (timerRight - timerTextWidth), (float) (y + h - padding - 2 * scale), timeColor);

            y -= gap;
        }
    }

    // single source for the font sizes so render() and getHeight() can't drift apart
    private int headSize(double scale) { return Fonts.size(20 * scale); }
    private int descSize(double scale) { return Fonts.size(16 * scale); }

    private double getHeight(double scale, Notifications module) {
        double padding = 8 * scale;
        int headSize = headSize(scale);
        int descSize = descSize(scale);

        double headerH = module.getSelectedFont(headSize, Weight.REGULAR).height() + 2 * scale;
        double descH = module.getSelectedFont(descSize, Weight.REGULAR).height();

        double timelineH = 2 * scale;
        return padding + headerH + descH + 4 * scale + timelineH + padding;
    }

    private String formatTimeLeft(double seconds) {
        if (seconds < 120) {
            return String.format("%.1f s", seconds);
        } else if (seconds < 3600) {
            return String.format("%.1f m", seconds / 60.0);
        } else {
            return String.format("%.1f h", seconds / 3600.0);
        }
    }

    // "INFO" -> "Info" -- the raw enum name as a title reads like debug output
    private String title(NotificationType type) {
        String cached = TITLES.get(type);
        if (cached != null) return cached;
        final String n = type.name();
        final String computed = n.charAt(0) + n.substring(1).toLowerCase();
        TITLES.put(type, computed);
        return computed;
    }

    // notification icons ported from the Glass v0.1 (Tenacity-style) client
    // assets/minecraft/redacted/notifications/{warning,error,info,success}.png
    private static final ResourceLocation ICON_WARNING = new ResourceLocation("minecraft", "redacted/notifications/warning.png");
    private static final ResourceLocation ICON_ERROR = new ResourceLocation("minecraft", "redacted/notifications/error.png");
    private static final ResourceLocation ICON_INFO = new ResourceLocation("minecraft", "redacted/notifications/info.png");
    private static final ResourceLocation ICON_SUCCESS = new ResourceLocation("minecraft", "redacted/notifications/success.png");

    // draw the real icon texture (flat Tenacity-style set) with the fade applied as alpha
    private void drawTypeIcon(NotificationType type, double cx, double cy, double s, float alpha) {
        final ResourceLocation icon;
        switch (type) {
            case WARNING: icon = ICON_WARNING; break;
            case ERROR:   icon = ICON_ERROR; break;
            case INFO:    icon = ICON_INFO; break;
            default:      icon = ICON_SUCCESS; break;   // TIMER -> green check
        }
        final double size = s * 2.0;
        // grey tint (0.72) so the icons match the client theme instead of their original colours;
        // the tinted overload is REQUIRED because the 4-arg drawIcon force-resets colour to white
        RenderUtil.drawIcon(icon, cx - s, cy - s, size, 0.72F, 0.72F, 0.72F, Math.max(0.0F, Math.min(1.0F, alpha)));
    }

    private boolean canShow(NotificationType type, boolean force) {
        if (force) return true;
        Notifications module = Client.getInstance().getModuleManager().getModule(Notifications.class);
        return module != null && module.isEnabled() && module.isTypeEnabled(type);
    }
}
