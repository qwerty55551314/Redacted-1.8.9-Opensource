package redacted.client.module.impl.render;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjglx.BufferUtils;
import org.lwjglx.input.Keyboard;
import org.lwjglx.util.glu.Project;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.PacketEvent;
import redacted.client.event.impl.Render2DEvent;
import redacted.client.event.impl.Render3DEvent;
import redacted.client.font.Fonts;
import redacted.client.font.Weight;
import redacted.client.gui.edit.GuiEditHud;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.module.impl.combat.Team;
import redacted.client.util.animation.Animation;
import redacted.client.util.animation.Easing;
import redacted.client.util.font.Font;
import redacted.client.util.gui.Draggable;
import redacted.client.util.notification.NotificationType;
import redacted.client.util.player.FixHealth;
import redacted.client.util.render.ColorUtil;
import redacted.client.util.render.ESPUtil;
import redacted.client.util.render.EntityColorUtil;
import redacted.client.util.render.Render3DUtil;
import redacted.client.util.render.RenderUtil;
import redacted.client.util.render.StencilUtil;
import redacted.client.util.time.StopWatch;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ColorValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;
import redacted.util.RotationUtil;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// One module, four entity-render sub-features. Each owner toggle reveals its own child settings
// (dependency-gated in the GUI) and gates its own render/tick/packet work. The Players/Mobs/Animals/
// NPCs/Invisibles filters are global -- one shared set the loop features all read. Everything off by
// default; enabling the module renders nothing until you turn on a sub-feature. ESP/Nametags/Tracers/
// TargetHud behavior is ported faithfully, just namespaced and folded under one class.
public class EntityESP extends Module {
    private static EntityESP instance;

    // global entity filters (shared by ESP / Tracers / Nametags loops + TargetHUD capture)
    private final BooleanValue players    = new BooleanValue("Players", this, true).describe("Apply every entity feature to players");
    private final BooleanValue mobs       = new BooleanValue("Mobs", this, false).describe("Apply every entity feature to hostile mobs");
    private final BooleanValue animals    = new BooleanValue("Animals", this, false).describe("Apply every entity feature to passive animals");
    private final BooleanValue npcs       = new BooleanValue("NPCs", this, false).describe("Apply every entity feature to NPCs");
    private final BooleanValue invisibles = new BooleanValue("Invisibles", this, false).describe("Also include invisible entities");
    private final NumberValue range       = new NumberValue("Range", this, 64.0D, 1.0D, 256.0D, 1.0D).describe("Max distance for every entity feature");

    // ESP
    private final BooleanValue esp        = new BooleanValue("ESP", this, false).describe("2D/3D box, outline, or chams living entities");
    private final ModeValue espMode       = new ModeValue("ESP Mode", this, esp, true, "3D", "2D", "3D", "Outline", "Chams").describe("2D draws a screen rectangle, 3D draws the world hitbox, Outline adds a glowing shader outline, Chams draws the entity model through walls");
    private final ColorValue espColor      = new ColorValue("ESP Color", this, 0xFFFF0000, esp, true).describe("Color for everyone except teammates");
    private final BooleanValue espTeamColors = new BooleanValue("Team Colors", this, true, esp, true).describe("Teammates show their team color instead");
    private final NumberValue espOpacity   = new NumberValue("ESP Opacity", this, 128, 0, 255, 1, esp, true).describe("Fill/outline opacity");
    private final NumberValue espLineWidth = new NumberValue("ESP Line Width", this, 2.0D, 0.5D, 5.0D, 0.5D, esp, true).describe("ESP outline thickness");

    // Tracers
    private final BooleanValue tracers          = new BooleanValue("Tracers", this, false).describe("Draw a line from your view to each entity");
    private final ModeValue tracerRenderMode    = new ModeValue("Tracer Mode", this, tracers, true, "Lines", "Lines", "Arrows").describe("Lines to each target, or HUD Arrows for off-screen ones");
    private final ModeValue tracerColorMode     = new ModeValue("Tracer Color", this, tracers, true, "Custom", "Range", "Team", "Custom").describe("Range gradient, Team color, or Custom RGB");
    // Range mode: fades red->yellow within Red Distance, yellow->green out to Yellow Distance, solid green past that.
    private final NumberValue tracerRedDistance    = new NumberValue("Tracer Red Distance", this, 7.0, 0.0, 64.0, 1.0, tracerColorMode, "Range").describe("Within this range the line fades red to yellow");
    private final NumberValue tracerYellowDistance = new NumberValue("Tracer Yellow Distance", this, 14.0, 0.0, 128.0, 1.0, tracerColorMode, "Range").describe("Out to here the line fades yellow to green");
    private final ColorValue tracerColor  = new ColorValue("Tracer Color Picker", this, 0xFFFF0000, tracerColorMode, "Custom").describe("Line color when Tracer Color is Custom");
    private final NumberValue tracerAlpha = new NumberValue("Tracer Alpha", this, 255, 0, 255, 1, tracers, true).describe("Tracer line opacity");
    // Arrows mode only: ring arrowhead geometry. Size = triangle, Radius = distance out from the crosshair.
    private final NumberValue tracerArrowSize   = new NumberValue("Arrow Size",   this, 6.0,  3.0,  16.0,  1.0, tracerRenderMode, "Arrows").describe("Arrowhead triangle size (Arrows mode)");
    private final NumberValue tracerArrowRadius = new NumberValue("Arrow Radius", this, 30.0, 10.0, 120.0, 1.0, tracerRenderMode, "Arrows").describe("How far the arrows sit from the crosshair");

    // Nametags
    private final BooleanValue nametags          = new BooleanValue("Nametags", this, false).describe("Draw a custom name panel above entities");
    private final ModeValue nametagStyle         = new ModeValue("Nametag Style", this, nametags, true, "Panel", "Panel", "Modern").describe("Panel shows name/health/distance rows; Modern is the compact TargetHUD-style bar");
    private final NumberValue nametagScale        = new NumberValue("Nametag Scale", this, 1.0D, 0.5D, 2.0D, 0.1D, nametags, true).describe("Nametag panel size");
    private final ModeValue nametagFont           = new ModeValue("Nametag Font", this, nametags, true, "SF-Pro-Rounded", "SF-Pro-Rounded", "Titan One", "Minecraft").describe("SF-Pro-Rounded, Titan One, or Minecraft font");
    private final BooleanValue nametagShowDistance  = new BooleanValue("Nametag Show Distance", this, true, nametags, true).describe("Show the distance row on the tag");
    private final BooleanValue nametagShowHealth    = new BooleanValue("Nametag Show Health", this, true, nametags, true).describe("Show a health bar on the tag");
    private final BooleanValue nametagShowEquipment = new BooleanValue("Nametag Show Equipment", this, true, nametags, true).describe("Show the armor/held-item row above the tag");
    private final NumberValue nametagBgAlpha       = new NumberValue("Nametag BG Alpha", this, 200, 0, 255, 5, nametags, true).describe("Nametag panel background opacity");
    // floating placement knobs for the Modern style, ported from TargetHUD's floating modes
    private final NumberValue nametagHeight       = new NumberValue("Nametag Height", this, 0.5D, -1.0D, 2.0D, 0.1D, nametagStyle, "Modern").describe("Vertical offset above the head (Modern style)");
    private final NumberValue nametagSideOffset   = new NumberValue("Nametag Side Offset", this, 0.0D, -2.0D, 2.0D, 0.1D, nametagStyle, "Modern").describe("Sideways offset of the Modern tag");

    // TargetHUD
    private final BooleanValue targetHud   = new BooleanValue("TargetHUD", this, false).describe("Show a HUD panel for your current combat target");
    private final ModeValue targetMode     = new ModeValue("TargetHUD Mode", this, targetHud, true, "2D Screen", "2D Screen", "Circle").describe("2D Screen panel, or a floating Circle over the target");
    private final BooleanValue targetShowHealth = new BooleanValue("TargetHUD Show Health", this, true, targetMode, "2D Screen").describe("Show the health bar (2D Screen mode)");
    private final BooleanValue targetShowArmor  = new BooleanValue("TargetHUD Show Armor", this, true, targetMode, "2D Screen").describe("Show the armor bar (2D Screen mode)");
    private final ModeValue targetFont     = new ModeValue("TargetHUD Font", this, targetHud, true, "SF-Pro-Rounded", "SF-Pro-Rounded", "Titan One", "Minecraft").describe("SF-Pro-Rounded, Titan One, or Minecraft font");
    private final NumberValue targetFadeTime        = new NumberValue("TargetHUD Fade Time", this, 3.0, 1.0, 10.0, 0.5, targetHud, true).describe("Seconds the HUD stays after losing the target");
    private final NumberValue targetScale           = new NumberValue("TargetHUD Scale", this, 0.7, 0.5, 1.5, 0.1, targetHud, true).describe("TargetHUD size");
    private final NumberValue targetDisplayDistance = new NumberValue("TargetHUD Display Distance", this, 5.3, 1.0, 10.0, 0.1, targetHud, true).describe("Max distance to show the floating modes");
    private final NumberValue targetBgAlpha         = new NumberValue("TargetHUD BG Alpha", this, 220, 0, 255, 5, targetHud, true).describe("TargetHUD background opacity");
    private final NumberValue targetHeight          = new NumberValue("TargetHUD Height", this, 0.4, -1.0, 1.0, 0.1, targetHud, true).describe("Vertical offset above the target (floating modes)");
    private final NumberValue targetSideOffset      = new NumberValue("TargetHUD Side Offset", this, 0.0, -2.0, 2.0, 0.1, targetHud, true).describe("Sideways offset of the floating HUD");

    // ESP / Tracers shared color state
    private final StopWatch espTeamColorWarn = new StopWatch();
    private final StopWatch tracerTeamColorWarn = new StopWatch();
    private static final Color RANGE_NEAR = new Color(255, 0, 0);
    private static final Color RANGE_MID = new Color(255, 255, 0);
    private static final Color RANGE_FAR = new Color(0, 255, 0);
    // packed twins for the per-entity hot paths (identical channels, no Color churn)
    private static final int RANGE_NEAR_RGB = RANGE_NEAR.getRGB();
    private static final int RANGE_MID_RGB = RANGE_MID.getRGB();
    private static final int RANGE_FAR_RGB = RANGE_FAR.getRGB();

    // render-thread scratch: hitbox corner walk for isOnScreen/projectRect and the
    // interpolated tracer position, reused per entity per frame
    private static final double[][] CORNER_SCRATCH = new double[8][3];
    private final float[] rectScratch = new float[4];
    private final double[] tracerPosScratch = new double[3];

    // Tracers "Arrows" mode (ColdPlay port): off-screen targets get a HUD arrowhead pointing at them.
    // Matrices captured in the 3D pass feed gluProject's off-screen test; the queue drains in the 2D pass.
    // The arrow* buffers double as the 2D-ESP scratch: renderEsp finishes its capture+projection before
    // renderTracers starts each frame, so the two collectors never interleave.
    private final FloatBuffer arrowModelview  = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer arrowProjection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer   arrowViewport   = BufferUtils.createIntBuffer(16);
    private final FloatBuffer arrowScreen     = BufferUtils.createFloatBuffer(3);
    private final List<ScreenArrow> tracerArrows = new ArrayList<>();
    private final List<ScreenRect> espRects = new ArrayList<>();
    private static final double ARROW_FADE_RANGE = 64.0; // distance over which near->far fades red->black
    private static final Color ARROW_FAR  = new Color(0, 0, 0);       // far target  -> black
    private static final Color ARROW_MID  = new Color(139, 0, 0);     // mid          -> dark red
    private static final Color ARROW_NEAR = new Color(255, 100, 100); // point-blank  -> light red
    private static final int ARROW_FAR_RGB = ARROW_FAR.getRGB();
    private static final int ARROW_MID_RGB = ARROW_MID.getRGB();
    private static final int ARROW_NEAR_RGB = ARROW_NEAR.getRGB();

    // Nametag layout/palette
    private static final int NT_COLOR_BG     = 0xFF171A21;
    private static final int NT_COLOR_ACCENT = 0x554794FD;
    private static final int NT_COLOR_TRACK  = 0xFF2A2D34;
    private static final int NT_COLOR_NAME   = 0xFFFFFFFF;
    private static final int NT_COLOR_VALUE  = 0xFFCCCCCC;
    private static final float NT_PAD = 5f, NT_RADIUS = 6f;
    private static final float NT_NAME_H = 11f, NT_BAR_H = 4f, NT_BAR_GAP = 3f, NT_DIST_H = 9f;
    private static final float NT_BAR_MIN_W = 58f;
    private static final float NT_BASE_SCALE = 0.016f;
    private final List<ItemStack> equipStacks = new ArrayList<>();

    // TargetHUD layout/palette
    private static final int TH_COLOR_BG    = 0xFF171A21;
    private static final int TH_COLOR_INSET = 0xFF121419;
    private static final int TH_COLOR_ACCENT = 0xFF4794FD;
    private static final int TH_COLOR_TRACK = 0xFF2A2D34;
    private static final int TH_COLOR_NAME  = 0xFFFFFFFF;
    private static final int TH_COLOR_VALUE = 0xFFCCCCCC;
    private static final int TH_PAD = 7, TH_PORT = 44, TH_GAP = 9, TH_RPAD = 12, TH_BW = 80, TH_BH = 5, TH_HEI = 56;
    private static final ResourceLocation HEART = new ResourceLocation("textures/gui/icons.png");
    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");
    private Draggable draggable;
    private final Animation alphaAnimation = new Animation(Easing.EASE_OUT_QUAD, 500);
    private final Animation fadeAnimation = new Animation(Easing.LINEAR, 200);
    private EntityLivingBase currentTarget;
    private EntityLivingBase renderEntity;
    private long lastTargetTime;
    private final DecimalFormat df = new DecimalFormat("0.0");

    public EntityESP() {
        super("EntityESP", "Entity ESP suite", Keyboard.KEY_NONE, Category.RENDER);
        instance = this;
        setCheckboxBooleans(true);   // sub-feature toggles render as checkboxes (user request)
    }

    public static EntityESP getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        // enabling the module should SHOW something: if no sub-feature is on, turn the ESP boxes
        // on automatically. (the old behaviour -- module enabled, filters on, nothing rendering
        // because the ESP sub-toggle sat off -- is exactly how this module reads as "broken".)
        if (!esp.getValue() && !tracers.getValue() && !nametags.getValue() && !targetHud.getValue()) {
            esp.setValue(true);
        }
    }

    @Override
    public void onDisable() {
        currentTarget = null;
        renderEntity = null;
        lastTargetTime = 0L;
        alphaAnimation.setValue(0.0);
        fadeAnimation.setValue(0.0);
        // stale 3D-pass queues would otherwise redraw when the edit GUI drives onRender2D while disabled
        tracerArrows.clear();
        espRects.clear();
    }

    private boolean passesGlobalFilter(Entity e) {
        if (mc.thePlayer != null && mc.thePlayer.getDistanceToEntity(e) > range.getValue()) return false;
        return ESPUtil.shouldRender(e, players.getValue(), mobs.getValue(), animals.getValue(), npcs.getValue(), invisibles.getValue());
    }

    // events

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre() || mc.thePlayer == null || mc.theWorld == null) return;
        if (!targetHud.getValue()) return;
        if (mc.objectMouseOver == null) return;
        if (!(mc.objectMouseOver.entityHit instanceof EntityLivingBase)) return;
        final EntityLivingBase e = (EntityLivingBase) mc.objectMouseOver.entityHit;
        if (e != mc.thePlayer && e.isEntityAlive() && passesGlobalFilter(e)) {
            currentTarget = e;
            renderEntity = e;
            lastTargetTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onPacket(final PacketEvent event) {
        if (!targetHud.getValue()) return;
        if (event.getDirection() != PacketEvent.Direction.OUTBOUND) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!(event.getPacket() instanceof C02PacketUseEntity)) return;
        final C02PacketUseEntity pkt = (C02PacketUseEntity) event.getPacket();
        if (pkt.getAction() != C02PacketUseEntity.Action.ATTACK) return;
        final Entity e = pkt.getEntityFromWorld(mc.theWorld);
        if (e instanceof EntityLivingBase && e != mc.thePlayer && passesGlobalFilter(e)) {
            currentTarget = (EntityLivingBase) e;
            renderEntity = (EntityLivingBase) e;
            lastTargetTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onRender2D(final Render2DEvent event) {
        if (mc.thePlayer == null) return;

        if (tracers.getValue() && tracerRenderMode.getValue().equals("Arrows")) {
            drawTracerArrows(event.getScaledResolution());
        }

        if (esp.getValue() && espMode.getValue().equals("2D")) {
            drawEspRects(event.getScaledResolution());
        }

        final boolean editMode = mc.currentScreen instanceof GuiEditHud;
        if (!targetHud.getValue() && !editMode) return;
        final String cur = targetMode.getValue();
        if (cur.equals("Circle")) return;

        final long elapsed = System.currentTimeMillis() - lastTargetTime;
        boolean shouldShow = currentTarget != null && !currentTarget.isDead && elapsed < (long) (targetFadeTime.getValue() * 1000.0);
        EntityLivingBase previewTarget = currentTarget;
        // edit-GUI preview: nothing live to show -> pose the player in the HUD
        if (editMode && !shouldShow) { previewTarget = mc.thePlayer; shouldShow = true; }
        alphaAnimation.run(shouldShow ? 1.0 : 0.0);
        final float alpha = (float) alphaAnimation.getValue();
        if (alpha < 0.01f) { if (!shouldShow) currentTarget = null; return; }

        final EntityLivingBase target = previewTarget;
        final ScaledResolution sr = event.getScaledResolution();
        Draggable d = getDraggable();
        float baseX = d.getX(), baseY = d.getY();
        if (baseX < 0 || baseY < 0) {
            baseX = sr.getScaledWidth() / 2f - 90;
            baseY = sr.getScaledHeight() - TH_HEI - 27;
            d.setX(baseX); d.setY(baseY);
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(baseX, baseY, 0);
        GL11.glScalef(d.getScale(), d.getScale(), 1);
        GL11.glTranslatef(-baseX, -baseY, 0);

        render2DScreen(target, baseX, baseY, alpha, sr);

        GL11.glPopMatrix();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    @EventTarget
    public void onRender3D(final Render3DEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        final float pt = event.getPartialTicks();
        if (esp.getValue()) renderEsp(pt);
        if (tracers.getValue()) renderTracers(pt);
        if (nametags.getValue()) renderNametags(pt);
        // not in the mirror pass: its stencil path re-attaches depth on whichever FBO is bound
        if (targetHud.getValue() && !Mirror.isRendering()) renderTargetHud3D(pt);
    }

    // ESP

    /** RenderGlobal gates the vanilla spectator outline shader pass on this. */
    public static boolean isOutlineActive() {
        EntityESP m = instance;
        return m != null && m.isEnabled() && m.esp.getValue() && m.espMode.getValue().equals("Outline");
    }

    /** Packed 0xFFRRGGBB for an ESP-selected living entity in Outline mode, else 0 (not selected).
     *  ponytail: alpha bit forced on so a pure-black ESP color can't collide with the 0 sentinel. */
    public static int outlineColor(Entity entity) {
        if (!isOutlineActive()) return 0;
        return instance.passesGlobalFilter(entity) ? (instance.getColorARGB(entity) | 0xFF000000) : 0;
    }

    /** RendererLivingEntity offsets these models' depth so they draw through terrain.
     *  ponytail: RenderGlobal only feeds doRender entities in chunks the visibility flood fill kept,
     *  so a distant fully-enclosed entity still won't chams (3D, which walks loadedEntityList itself,
     *  would). Fix that with a second entity loop like the outline pass only if it ever matters. */
    public static boolean chamsActive(Entity entity) {
        EntityESP m = instance;
        return m != null && m.isEnabled() && m.esp.getValue()
                && m.espMode.getValue().equals("Chams") && m.passesGlobalFilter(entity);
    }

    /** Packed-ARGB variant; the per-entity render paths call this to avoid Color churn. */
    public int getColorARGB(Entity entity) {
        final int alpha = espOpacity.intValue();
        if (espTeamColors.getValue() && Team.isTeam(entity)) {
            return ColorUtil.withAlpha(Team.getTeamColor().getRGB(), alpha);
        }
        return ColorUtil.withAlpha(espColor.getRGB(), alpha);
    }

    public Color getColor(Entity entity) {
        return new Color(getColorARGB(entity), true);
    }

    private void renderEsp(float pt) {
        // Chams draws the entity's own texture, so there's no enemy-vs-team color to clash
        if (!espMode.getValue().equals("Chams")) warnEspTeamColorClash();

        // 3D = fill + outline drawn here; 2D queues screen rects for the HUD pass. Outline is the
        // vanilla entity-outline shader pass and Chams is a depth offset in RendererLivingEntity
        // both gated by the EntityESP statics above, so there's nothing for this pass to draw.
        final String mode = espMode.getValue();
        if (mode.equals("2D")) { collectEspRects(pt); return; }
        if (!mode.equals("3D")) return;

        Render3DUtil.start3D();
        try {
            GL11.glLineWidth(espLineWidth.floatValue());

            for (Object obj : mc.theWorld.loadedEntityList) {
                if (!(obj instanceof Entity)) continue;
                Entity entity = (Entity) obj;

                if (entity instanceof EntityLivingBase && passesGlobalFilter(entity)) {
                    int rgba = getColorARGB(entity);
                    AxisAlignedBB bb = ESPUtil.getInterpolatedBoundingBox(entity, pt);
                    Render3DUtil.drawBox(bb, ColorUtil.withAlpha(rgba, 0.5F), ColorUtil.withAlpha(rgba, 2.0F));
                }
            }
        } finally {
            Render3DUtil.end3D();
        }
    }

    // 2D mode: project each hitbox to a screen rect while the camera matrices are live; the queue
    // drains in onRender2D. Shares the arrow* scratch buffers (renderEsp runs before renderTracers).
    private void collectEspRects(float pt) {
        // the mirror's inner world pass re-fires Render3D with its flipped camera + FBO viewport
        // collecting there would poison the HUD drain (handler order is enable-order-dependent)
        if (Mirror.isRendering()) return;
        espRects.clear();

        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, arrowModelview);
        GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, arrowProjection);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, arrowViewport);

        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityLivingBase)) continue;
            final Entity entity = (Entity) obj;
            if (!passesGlobalFilter(entity)) continue;

            // box is already viewer-relative (matches the captured modelview's input space)
            final float[] r = projectRect(ESPUtil.getInterpolatedBoundingBox(entity, pt));
            // doubled alpha matches the 3D box's outline treatment, so default opacity reads solid
            if (r != null) espRects.add(new ScreenRect(r[0], r[1], r[2], r[3],
                    ColorUtil.withAlpha(getColorARGB(entity), 2.0F)));
        }
    }

    // warn-only: if the team color blends into the enemy color, teammates won't stand out on ESP. we never
    // touch the user's chosen colors -- just a periodic nudge while they actually clash.
    private void warnEspTeamColorClash() {
        if (!espTeamColorWarn.finished(30_000L)) return;
        espTeamColorWarn.reset();

        if (!players.getValue()) return;
        // with Team Colors off, teammates already use the ESP color -- nothing to stand out from
        if (!espTeamColors.getValue()) return;

        Color enemy = new Color(espColor.getRGB());

        Team team = Team.get();
        if (team == null || team.getMembers().isEmpty()) return;
        if (!ColorUtil.colorsTooSimilar(Team.getTeamColor(), enemy)) return;

        Client.getInstance().getNotificationManager().showNotification(
                NotificationType.WARNING, "Team color matches ESP color -- teammates won't stand out", 4);
    }

    // Tracers

    private void renderTracers(float pt) {
        if (tracerRenderMode.getValue().equals("Arrows")) {
            collectTracerArrows(pt);
            return;
        }

        warnTracerTeamColorClash();

        Render3DUtil.start3D();
        try {
            GL11.glLineWidth(2.0F);

            final double[] start = Render3DUtil.tracerStart(pt);

            for (Object obj : mc.theWorld.loadedEntityList) {
                if (!(obj instanceof Entity)) continue;
                Entity entity = (Entity) obj;
                if (!passesGlobalFilter(entity)) continue;

            final double[] pos = ESPUtil.getInterpolatedPos(entity, pt, tracerPosScratch);

            ColorUtil.glColor(resolveTracerColor(entity));
            Render3DUtil.drawLine(start[0], start[1], start[2], pos[0], pos[1], pos[2]);
            }
        } finally {
            Render3DUtil.end3D();
        }
    }

    private int resolveTracerColor(Entity entity) {
        final int a = tracerAlpha.intValue();

        if (Team.isTeam(entity)) {
            return ColorUtil.withAlpha(Team.getTeamColor().getRGB(), a);
        }

        if (tracerColorMode.getValue().equals("Range")) {
            final double dist = mc.thePlayer.getDistanceToEntity(entity);
            final double redDist = tracerRedDistance.getValue();
            final double yellowDist = tracerYellowDistance.getValue();

            // anchors land on the thresholds: 0=red, redDist=yellow, yellowDist=green, then hold green
            if (dist <= redDist) {
                // redDist<=0 means no red zone, snap to yellow
                final float t = redDist <= 0.0 ? 1.0F : (float) (dist / redDist);
                return ColorUtil.withAlpha(ColorUtil.lerpColor(RANGE_NEAR_RGB, RANGE_MID_RGB, t), a);
            }
            if (dist <= yellowDist) {
                final double span = yellowDist - redDist;
                final float t = span <= 0.0 ? 1.0F : (float) ((dist - redDist) / span);
                return ColorUtil.withAlpha(ColorUtil.lerpColor(RANGE_MID_RGB, RANGE_FAR_RGB, t), a);
            }
            return ColorUtil.withAlpha(RANGE_FAR_RGB, a);
        }

        final int cc = tracerColor.getRGB();
        return EntityColorUtil.resolveARGB(
                entity,
                tracerColorMode.getValue(),
                a,
                cc >> 16 & 0xFF,
                cc >> 8 & 0xFF,
                cc & 0xFF,
                null
        );
    }

    // warn-only: same team color drives Tracers, so flag it when it blends into the enemy line color.
    private void warnTracerTeamColorClash() {
        if (!tracerTeamColorWarn.finished(30_000L)) return;
        tracerTeamColorWarn.reset();

        if (!players.getValue()) return;
        if (!tracerColorMode.getValue().equals("Custom")) return;   // Range/Team have no single enemy color

        Team team = Team.get();
        if (team == null || team.getMembers().isEmpty()) return;

        Color enemy = new Color(tracerColor.getRGB());
        if (!ColorUtil.colorsTooSimilar(Team.getTeamColor(), enemy)) return;

        Client.getInstance().getNotificationManager().showNotification(
                NotificationType.WARNING, "Team color matches Tracers color -- teammates won't stand out", 4);
    }

    // Tracers: Arrows mode (ColdPlay port)
    // off-screen targets get a filled arrowhead on a horizon-compass ring around the crosshair:
    // front sits at the top, right at the right, directly behind at the bottom, left at the left
    // so a target behind the player reads as "bottom" instead of collapsing onto the side positions
    // (feeding yaw/pitch turn deltas into atan2 did exactly that: yawDiff saturates at +-180 deg
    // behind while pitchDiff stays tiny at eye level). Pitch is intentionally not part of the
    // bearing, which also keeps the ring stable while the player free-looks up/down. collect runs
    // in the 3D pass (camera matrices live for gluProject); the queue is drained by drawTracerArrows
    // in the 2D HUD pass.
    private void collectTracerArrows(float pt) {
        tracerArrows.clear();

        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, arrowModelview);
        GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, arrowProjection);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, arrowViewport);

        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityLivingBase)) continue;
            final EntityLivingBase living = (EntityLivingBase) obj;
            if (!passesGlobalFilter(living)) continue;

            // box is already viewer-relative (ESPUtil subtracts viewerPos), which matches the captured
            // modelview's input space -- corners feed gluProject directly, no second subtraction.
            if (isOnScreen(ESPUtil.getInterpolatedBoundingBox(living, pt))) continue;

            final float[] rot = RotationUtil.getRotations(mc.thePlayer, living);
            // horizon compass: only relative yaw decides the ring position. camera yaw is
            // interpolated across the tick like the 3D pass so the ring doesn't jitter on turns.
            final float camYaw = mc.thePlayer.prevRotationYaw
                    + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * pt;
            final float yawDiff = RotationUtil.getYawDifference(camYaw, rot[0]);
            final float angle = (float) (Math.toRadians(yawDiff) - Math.PI / 2.0);

            // teammates pin to the team color (like nametags/lines); everyone else keeps the distance fade
            final int arrowColor = Team.isTeam(living)
                    ? Team.getTeamColorARGB()
                    : arrowDistanceColor(mc.thePlayer.getDistanceToEntity(living));
            tracerArrows.add(new ScreenArrow(angle, arrowColor));
        }
    }

    /** Fills the shared 8-corner scratch from the box (same corner order as before). */
    private static double[][] fillCorners(AxisAlignedBB box) {
        CORNER_SCRATCH[0][0] = box.minX; CORNER_SCRATCH[0][1] = box.minY; CORNER_SCRATCH[0][2] = box.minZ;
        CORNER_SCRATCH[1][0] = box.minX; CORNER_SCRATCH[1][1] = box.minY; CORNER_SCRATCH[1][2] = box.maxZ;
        CORNER_SCRATCH[2][0] = box.minX; CORNER_SCRATCH[2][1] = box.maxY; CORNER_SCRATCH[2][2] = box.minZ;
        CORNER_SCRATCH[3][0] = box.minX; CORNER_SCRATCH[3][1] = box.maxY; CORNER_SCRATCH[3][2] = box.maxZ;
        CORNER_SCRATCH[4][0] = box.maxX; CORNER_SCRATCH[4][1] = box.minY; CORNER_SCRATCH[4][2] = box.minZ;
        CORNER_SCRATCH[5][0] = box.maxX; CORNER_SCRATCH[5][1] = box.minY; CORNER_SCRATCH[5][2] = box.maxZ;
        CORNER_SCRATCH[6][0] = box.maxX; CORNER_SCRATCH[6][1] = box.maxY; CORNER_SCRATCH[6][2] = box.minZ;
        CORNER_SCRATCH[7][0] = box.maxX; CORNER_SCRATCH[7][1] = box.maxY; CORNER_SCRATCH[7][2] = box.maxZ;
        return CORNER_SCRATCH;
    }

    // projects the 8 viewer-relative hitbox corners; off-screen iff nothing's in front, or (all in front)
    // the screen-space AABB misses the viewport. spanning the near plane counts as on-screen (point-blank).
    private boolean isOnScreen(AxisAlignedBB box) {
        final double[][] corners = fillCorners(box);

        final int fbWidth = arrowViewport.get(2);
        final int fbHeight = arrowViewport.get(3);
        float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        int inFront = 0;
        boolean anyBehind = false;

        for (double[] c : corners) {
            if (!Project.gluProject((float) c[0], (float) c[1], (float) c[2],
                    arrowModelview, arrowProjection, arrowViewport, arrowScreen)) {
                anyBehind = true;
                continue;
            }
            final float depth = arrowScreen.get(2);
            if (depth <= 0.0f || depth >= 1.0f) { anyBehind = true; continue; } // behind near / past far plane
            inFront++;
            final float sx = arrowScreen.get(0);
            final float sy = fbHeight - arrowScreen.get(1); // gluProject Y is bottom-up; flip to top-left origin
            if (sx < left) left = sx;
            if (sx > right) right = sx;
            if (sy < top) top = sy;
            if (sy > bottom) bottom = sy;
        }

        if (inFront == 0) return false;     // wholly behind the camera
        if (anyBehind) return true;         // spans the near plane -> treat as on-screen (point-blank)
        return right >= 0 && left <= fbWidth && bottom >= 0 && top <= fbHeight;
    }

    // 2D-ESP variant of isOnScreen's corner walk: needs the actual rect, and rejects near-plane
    // spans outright (their projected corners are garbage). Framebuffer px, top-left origin.
    // No viewport-intersect test -- GL clips off-screen line loops for free.
    private float[] projectRect(AxisAlignedBB box) {
        final double[][] corners = fillCorners(box);

        final int fbHeight = arrowViewport.get(3);
        float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;

        for (double[] c : corners) {
            if (!Project.gluProject((float) c[0], (float) c[1], (float) c[2],
                    arrowModelview, arrowProjection, arrowViewport, arrowScreen)) return null;
            final float depth = arrowScreen.get(2);
            if (depth <= 0.0f || depth >= 1.0f) return null; // behind near / past far plane
            final float sx = arrowScreen.get(0);
            final float sy = fbHeight - arrowScreen.get(1);  // gluProject Y is bottom-up
            if (sx < left) left = sx;
            if (sx > right) right = sx;
            if (sy < top) top = sy;
            if (sy > bottom) bottom = sy;
        }

        // caller consumes immediately (render-thread only), so the scratch is safe to reuse
        rectScratch[0] = left;
        rectScratch[1] = top;
        rectScratch[2] = right;
        rectScratch[3] = bottom;
        return rectScratch;
    }

    // ColdPlay's distance gradient: black (far) -> dark red -> light red (near). ARROW_FADE_RANGE
    // normalizes the falloff since EntityESP has no module-wide range value. Same two-stop lerp idiom
    // as resolveTracerColor's Range arm.
    private int arrowDistanceColor(double dist) {
        final double t = ARROW_FADE_RANGE <= 0 ? 0.0 : 1.0 - Math.min(1.0, dist / ARROW_FADE_RANGE);
        return t < 0.5
                ? ColorUtil.lerpColor(ARROW_FAR_RGB, ARROW_MID_RGB, (float) (t / 0.5))
                : ColorUtil.lerpColor(ARROW_MID_RGB, ARROW_NEAR_RGB, (float) ((t - 0.5) / 0.5));
    }

    // drains the queue in the HUD pass: one filled triangle per arrow on a ring around the crosshair,
    // tip pointing outward along its angle. disableCull because the winding flips around the ring.
    private void drawTracerArrows(ScaledResolution sr) {
        if (tracerArrows.isEmpty()) return;

        final float cx = sr.getScaledWidth() / 2.0F;
        final float cy = sr.getScaledHeight() / 2.0F;
        final double radius = tracerArrowRadius.getValue();
        final double size = tracerArrowSize.getValue();

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableCull();

        GL11.glBegin(GL11.GL_TRIANGLES);
        for (ScreenArrow arrow : tracerArrows) {
            final double cos = Math.cos(arrow.angle);
            final double sin = Math.sin(arrow.angle);
            final double px = cx + cos * radius;       // ring point this arrow sits on
            final double py = cy + sin * radius;
            final double tipX = px + cos * size;
            final double tipY = py + sin * size;
            final double baseX = px - cos * (size * 0.5);
            final double baseY = py - sin * (size * 0.5);
            final double half = size * 0.6;            // base half-width, along the perpendicular (-sin, cos)
            ColorUtil.glColor(arrow.color);
            GL11.glVertex2d(tipX, tipY);
            GL11.glVertex2d(baseX - sin * half, baseY + cos * half);
            GL11.glVertex2d(baseX + sin * half, baseY - cos * half);
        }
        GL11.glEnd();

        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        tracerArrows.clear();
    }

    // drains the ESP 2D queue in the HUD pass. gluProject returns framebuffer px; the HUD draws in
    // GUI-scaled units, so divide by the scale factor. radius 0 = plain rect outline.
    private void drawEspRects(ScaledResolution sr) {
        if (espRects.isEmpty()) return;

        final float sf = sr.getScaleFactor();
        final float lw = espLineWidth.floatValue();
        for (ScreenRect r : espRects) {
            RenderUtil.drawRoundedOutline(r.left / sf, r.top / sf,
                    (r.right - r.left) / sf, (r.bottom - r.top) / sf, 0, lw, r.color);
        }
        espRects.clear();
    }

    private static final class ScreenArrow {
        final float angle;
        final int color;
        ScreenArrow(float angle, int color) { this.angle = angle; this.color = color; }
    }

    private static final class ScreenRect {
        final float left, top, right, bottom;
        final int color;
        ScreenRect(float left, float top, float right, float bottom, int color) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom; this.color = color;
        }
    }

    // Nametags

    // vanilla RendererLivingEntity.canRenderName asks this before drawing its own tag
    public static boolean overridesVanilla(EntityLivingBase entity) {
        EntityESP m = instance;
        return m != null && m.isEnabled() && m.nametags.getValue() && m.isNametagValid(entity);
    }

    private boolean isNametagValid(EntityLivingBase entity) {
        if (entity == mc.thePlayer) return false;
        return passesGlobalFilter(entity);
    }

    private void renderNametags(float pt) {
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityLivingBase)) continue;

            EntityLivingBase entity = (EntityLivingBase) obj;
            if (!isNametagValid(entity)) continue;

            double[] pos = ESPUtil.getInterpolatedPos(entity, pt, tracerPosScratch);
            renderNametag(entity, pos[0], pos[1], pos[2]);
        }
    }

    private void renderNametag(EntityLivingBase entity, double x, double y, double z) {
        if (nametagStyle.getValue().equals("Modern")) {
            renderNametagModern(entity, x, y, z);
            return;
        }

        float dist = mc.thePlayer.getDistanceToEntity(entity);
        float fade = ntFade(dist);
        if (fade <= 0.02f) return;

        // grow with distance so the tag keeps roughly constant apparent size at range
        float distScale = MathHelper.clamp_float(dist / 6f, 1f, 4f);
        float labelScale = NT_BASE_SCALE * nametagScale.floatValue() * distScale;

        String name = entity.getName();
        float nameW = ntTw(name);
        String distText = (int) dist + "m";
        float distW = nametagShowDistance.getValue() ? ntTw14(distText) : 0f;

        float panelW = Math.max(NT_BAR_MIN_W, Math.max(nameW, distW)) + 2f * NT_PAD;
        float panelH = NT_PAD + NT_NAME_H
                + (nametagShowHealth.getValue() ? NT_BAR_GAP + NT_BAR_H : 0f)
                + (nametagShowDistance.getValue() ? NT_BAR_GAP + NT_DIST_H : 0f)
                + NT_PAD;

        List<ItemStack> equipmentStacks = nametagShowEquipment.getValue() && fade > 0.5f
                ? getEquipmentStacks(entity) : Collections.<ItemStack>emptyList();

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) x, (float) y + entity.height + 0.5F, (float) z);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-labelScale, -labelScale, labelScale);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

            // panel spans y in [-panelH, 0] -- negative local y is up after the billboard flip
            float left = -panelW / 2f;
            float top = -panelH;
            RenderUtil.drawRoundedRect(left, top, panelW, panelH, NT_RADIUS, ColorUtil.withAlpha(NT_COLOR_BG, (int) (nametagBgAlpha.floatValue() * fade)));
            RenderUtil.drawRoundedOutline(left, top, panelW, panelH, NT_RADIUS, 1.5f, ColorUtil.withAlpha(NT_COLOR_ACCENT, fade));

            float rowY = top + NT_PAD;
            int nameColor = Team.isTeam(entity) ? Team.getTeamColorARGB() : NT_COLOR_NAME;
            ntFont(16).draw(name, -nameW / 2f, rowY, ColorUtil.withAlpha(nameColor, fade));
            rowY += NT_NAME_H;

            if (nametagShowHealth.getValue()) {
                rowY += NT_BAR_GAP;
                float barW = panelW - 2f * NT_PAD;
                RenderUtil.drawRoundedRect(left + NT_PAD, rowY, barW, NT_BAR_H, 2, ColorUtil.withAlpha(NT_COLOR_TRACK, fade));
                float hp = FixHealth.getHealth(entity);
                float ratio = ColorUtil.healthRatio(hp, entity.getMaxHealth());
                if (ratio > 0f) {
                    RenderUtil.drawRoundedRect(left + NT_PAD, rowY, barW * ratio, NT_BAR_H, 2, ColorUtil.withAlpha(ColorUtil.healthColor(hp, entity.getMaxHealth()), fade));
                }
                // border last so it sits on top of the fill's edge instead of under it
                RenderUtil.drawRoundedOutline(left + NT_PAD, rowY, barW, NT_BAR_H, 2, 1f, ColorUtil.withAlpha(0xFF000000, fade));
                rowY += NT_BAR_H;
            }

            if (nametagShowDistance.getValue()) {
                rowY += NT_BAR_GAP;
                ntFont(14).draw(distText, -distW / 2f, rowY, ColorUtil.withAlpha(NT_COLOR_VALUE, fade));
            }

            // items last: drawBillboardItems re-arms the billboard state its GUI lighting clobbers
            if (!equipmentStacks.isEmpty()) renderEquipmentRow(equipmentStacks, (int) (top - 19f));
        }
        finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private void renderEquipmentRow(List<ItemStack> stacks, int y) {
        final int itemSize = 16;
        final int spacing = 2;
        final int rowWidth = stacks.size() * itemSize + (stacks.size() - 1) * spacing;

        ESPUtil.drawBillboardItems(() -> {
            int x = -rowWidth / 2;
            for (ItemStack stack : stacks) {
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
                mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, stack, x, y);
                x += itemSize + spacing;
            }
        });
    }

    private List<ItemStack> getEquipmentStacks(EntityLivingBase entity) {
        equipStacks.clear();
        for (int slot = 4; slot >= 0; --slot) {
            ItemStack stack = entity.getEquipmentInSlot(slot);
            if (stack != null && stack.getItem() != null) equipStacks.add(stack);
        }
        return equipStacks;
    }

    private Font ntFont(int size) {
        return Fonts.getForMode(nametagFont.getValue(), size, Weight.REGULAR);
    }

    // both styles fade over the last quarter of the global Range and are gone at Range, which is
    // where passesGlobalFilter drops the entity anyway. ponytail: Range min is 1, so r*0.25 != 0.
    private float ntFade(float dist) {
        float r = range.floatValue();
        return 1f - MathHelper.clamp_float((dist - r * 0.75f) / (r * 0.25f), 0f, 1f);
    }

    // width() can throw on chars >= U+0100 (non-Latin names), so keep the vanilla fallback
    private float ntTw(String t) {
        try { return ntFont(16).width(t); } catch (Exception e) { return mc.fontRendererObj.getStringWidth(t); }
    }

    private float ntTw14(String t) {
        try { return ntFont(14).width(t); } catch (Exception e) { return mc.fontRendererObj.getStringWidth(t); }
    }

    // "Modern" nametag style: the TargetHUD Modern bar ported onto the nametag billboard
    // face portrait, name, health bar with heart + hp, and a Winning/Losing/Tying status.
    private void renderNametagModern(EntityLivingBase entity, double x, double y, double z) {
        float dist = mc.thePlayer.getDistanceToEntity(entity);
        float fade = ntFade(dist);
        if (fade <= 0.02f) return;

        // grow with distance so the tag keeps roughly constant apparent size at range
        float distScale = MathHelper.clamp_float(dist / 6f, 1f, 4f);
        float labelScale = NT_BASE_SCALE * nametagScale.floatValue() * distScale;

        final float w = 140, h = 40, hs = 32, p = 4;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) x, (float) y + entity.height + nametagHeight.floatValue(), (float) z);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.translate(nametagSideOffset.floatValue(), 0, 0);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.scale(-labelScale, -labelScale, labelScale);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

            int bg = (int) (nametagBgAlpha.floatValue() * fade);
            RenderUtil.drawRoundedRect(-w / 2f, -h / 2f, w, h, 5, ColorUtil.withAlpha(0xFF141A14, bg));
            face(entity, -w / 2f + p + 2, -h / 2f + p, hs, fade);

            float ix = -w / 2f + hs + p * 2 + 2;
            float bw = w - (hs + p * 3) - 2;
            int nameColor = Team.isTeam(entity) ? Team.getTeamColorARGB() : NT_COLOR_NAME;
            ntFont(16).draw(entity.getName(), ix, -h / 2f + p, ColorUtil.withAlpha(nameColor, fade));

            if (nametagShowHealth.getValue()) {
                float hp = FixHealth.getHealth(entity), mh = entity.getMaxHealth();
                float ratio = ColorUtil.healthRatio(hp, mh);
                float barY = -h / 2f + p + 14;
                RenderUtil.drawRoundedRect(ix, barY, bw, 6, 3, ColorUtil.withAlpha(0xFF2A2D34, (int) (200 * fade)));
                if (bw * ratio > 1) {
                    RenderUtil.drawRoundedRect(ix, barY, bw * ratio, 6, 3,
                            ColorUtil.withAlpha(Color.HSBtoRGB(ratio / 3, 0.8f, 0.9f), (int) (255 * fade)));
                }
                // border last so it sits on top of the fill's edge instead of under it
                RenderUtil.drawRoundedOutline(ix, barY, bw, 6, 3, 1f, ColorUtil.withAlpha(0xFF000000, fade));
                mc.getTextureManager().bindTexture(HEART);
                GlStateManager.color(1, 1, 1, fade);
                Gui.drawModalRectWithCustomSizedTexture((int) ix, (int) (barY + 10), 52, 0, 9, 9, 256, 256);
                ntFont(16).draw(df.format(hp), ix + 11, barY + 11, ColorUtil.withAlpha(NT_COLOR_VALUE, fade));
                float myHp = FixHealth.getHealth(mc.thePlayer);
                String status = hp > myHp ? "Losing" : hp < myHp ? "Winning" : "Tying";
                int sc = hp > myHp ? 0xFFFF5555 : hp < myHp ? 0xFF55FF55 : 0xFFFFFF55;
                ntFont(16).draw(status, ix + bw - ntTw(status), barY + 11, ColorUtil.withAlpha(sc, fade));
            }

            // items last: drawBillboardItems re-arms the billboard state its GUI lighting clobbers.
            // fade gate matches the Panel style -- items ignore alpha, so they'd pop in opaque on a
            // half-faded tag. -h/2-19 is Modern's equivalent of Panel's top-19 (16px tall, 3px gap).
            if (nametagShowEquipment.getValue() && fade > 0.5f) {
                List<ItemStack> stacks = getEquipmentStacks(entity);
                if (!stacks.isEmpty()) renderEquipmentRow(stacks, (int) (-h / 2f - 19f));
            }
        }
        finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    // TargetHUD

    private void renderTargetHud3D(float pt) {
        final String cur = targetMode.getValue();
        if (cur.equals("2D Screen")) return;
        float displayDist = targetDisplayDistance.floatValue();
        if (currentTarget != null && (currentTarget.isDead || mc.thePlayer.getDistanceToEntity(currentTarget) > displayDist || !mc.thePlayer.canEntityBeSeen(currentTarget))) currentTarget = null;
        final long elapsed = System.currentTimeMillis() - lastTargetTime;
        final boolean shouldShow = currentTarget != null && !currentTarget.isDead && elapsed < (long) (targetFadeTime.getValue() * 1000.0);
        fadeAnimation.run(shouldShow ? 1.0 : 0.0);
        if (renderEntity == null || fadeAnimation.getValue() < 0.01) { if (renderEntity != null && fadeAnimation.getValue() < 0.01) renderEntity = null; return; }
        EntityLivingBase entity = renderEntity;
        double[] pos = ESPUtil.getInterpolatedPos(entity, pt);
        renderFloatingHUD(entity, pos[0], pos[1], pos[2]);
    }

    private Draggable getDraggable() {
        if (draggable == null) {
            draggable = registerDraggable("TargetHUD", -1, -1, 200, TH_HEI + 6);
        }
        return draggable;
    }

    // eager registration so the edit-HUD dummy exists from startup (see Client.start)
    public void registerAllDraggables() {
        getDraggable();
    }

    private Font thFont(int size) { return thFont(size, Weight.REGULAR); }
    private Font thFont(int size, Weight w) {
        return Fonts.getForMode(targetFont.getValue(), size, w);
    }

    private void thTx(String t, double x, double y, int c) {
        thFont(16).draw(t, x, y, c);
    }

    // width() can throw on chars >= U+0100 (non-Latin names), so keep the vanilla fallback here
    private float thTw(String t) {
        try { return thFont(16).width(t); } catch (Exception e) { return mc.fontRendererObj.getStringWidth(t); }
    }

    // Render.getEntityTexture is protected in 1.8.9 so mobs can't be textured externally -> fall back to Steve
    private void face(EntityLivingBase e, float x, float y, float s, float a) {
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, a);
        ResourceLocation skin = e instanceof AbstractClientPlayer
                ? ((AbstractClientPlayer) e).getLocationSkin() : STEVE;
        mc.getTextureManager().bindTexture(skin);
        Gui.drawScaledCustomSizeModalRect((int)x, (int)y, 8, 8, 8, 8, (int)s, (int)s, 64, 64);
    }

    private void body(EntityLivingBase e, float x, float y, int s) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        ScaledResolution sr = new ScaledResolution(mc);
        int sf = sr.getScaleFactor();
        GL11.glScissor((int)(x * sf), (sr.getScaledHeight() - (int)(y + s)) * sf, (int)(s * sf), (int)(s * sf));
        GlStateManager.color(1, 1, 1, 1);
        GuiInventory.drawEntityOnScreen((int)x + s / 2, (int)y + s - 6, Math.max(12, Math.min(30, (int)(35 / Math.max(e.height, e.width)))), 0, 0, e);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }

    private void bar(double x, double y, double w, double h, double r, int c) {
        if (w <= 0) return;
        RenderUtil.drawRoundedRect(x, y, w, h, Math.min(r, Math.min(w / 2, h / 2)), c);
    }

    private void hbar(EntityLivingBase e, float x, float y, float w, float a) {
        float hp = FixHealth.getHealth(e), mh = e.getMaxHealth();
        float r = ColorUtil.healthRatio(hp, mh);
        bar(x, y, w, 4, 2, ColorUtil.withAlpha(TH_COLOR_TRACK, a));
        if (r > 0) bar(x, y, w * r, 4, 2, ColorUtil.withAlpha(ColorUtil.healthColor(hp, mh), a));
    }

    // 2D Screen mode

    private void render2DScreen(EntityLivingBase target, float px, float py, float alpha, ScaledResolution sr) {
        String name = target.getName();
        float nw = thTw(name);
        int cw = (int)Math.max(nw, TH_BW + 24);
        int pw = TH_PAD + TH_PORT + TH_GAP + cw + TH_RPAD;
        // box hugs the real card (its width follows the name), not the registered dummy size
        Draggable box = getDraggable();
        box.setBaseWidth(pw);
        box.setBaseHeight(TH_HEI);
        RenderUtil.drawRoundedRect(px, py, pw, TH_HEI, 8, ColorUtil.withAlpha(TH_COLOR_BG, alpha));
        RenderUtil.drawRoundedOutline(px, py, pw, TH_HEI, 8, 1, ColorUtil.withAlpha(0x554794FD, alpha));
        int bx = (int)(px + TH_PAD), by = (int)(py + TH_PAD);
        RenderUtil.drawRoundedRect(bx, by, TH_PORT, TH_PORT, 6, ColorUtil.withAlpha(TH_COLOR_INSET, alpha));
        GlStateManager.color(1, 1, 1, 1);
        if (alpha > 0.1f) body(target, bx, by, TH_PORT);
        int x0 = (int)(px + TH_PAD + TH_PORT + TH_GAP);
        thTx(name, x0, py + 9, ColorUtil.withAlpha(TH_COLOR_NAME, alpha));
        bar(x0, py + 27, cw, 1, 0, ColorUtil.withAlpha(0xAA4794FD, alpha));
        if (targetShowHealth.getValue()) hbar(target, x0, py + 32, TH_BW, alpha);
        if (targetShowArmor.getValue()) {
            float ar = Math.max(0, Math.min(1, target.getTotalArmorValue() / 20f));
            bar(x0, py + 43, TH_BW, TH_BH, 2, ColorUtil.withAlpha(TH_COLOR_TRACK, alpha));
            bar(x0, py + 43, TH_BW * ar, TH_BH, 2, ColorUtil.withAlpha(TH_COLOR_ACCENT, alpha));
        }
    }

    // 3D floating modes

    private void renderFloatingHUD(EntityLivingBase entity, double x, double y, double z) {
        String cur = targetMode.getValue();
        float s = targetScale.floatValue();
        float hOff = targetHeight.floatValue();
        float distScale = getDistanceScale(entity);
        float dScale = getDraggable().getScale();
        float finalScale = s * distScale * dScale;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + entity.height + hOff, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.translate(targetSideOffset.floatValue(), 0, 0);
        float globalScale = 0.02f * finalScale;
        GlStateManager.scale(-globalScale, -globalScale, globalScale);
        float animProgress = (float) MathHelper.clamp_double(fadeAnimation.getValue(), 0.0, 1.0);

        final float heightOfMode = 48f;
        int animOffset = (int) (-heightOfMode * (1 - animProgress) / 2f);
        GlStateManager.translate(0, animOffset, 0);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        try {
            renderCircleMode(entity, animProgress);
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.popMatrix();
        }
    }

    private float getDistanceScale(EntityLivingBase entity) {
        float dist = mc.thePlayer.getDistanceToEntity(entity);
        return 0.85f + 0.15f * MathHelper.clamp_float((dist - 1) / 9, 0, 1);
    }

    private void renderCircleMode(EntityLivingBase entity, float animProgress) {
        float w = 110, h = 48, hs = 32, p = 4;
        float headX = -w / 2f + p + 2, headY = -h / 2f + (h - hs) / 2f;
        int bg = (int)(targetBgAlpha.getValue() * animProgress);
        RenderUtil.drawRoundedRect(-w / 2f, -h / 2f, w, h, 6, ColorUtil.withAlpha(0xFF000000, bg));
        float cx = headX + hs / 2f, cy = headY + hs / 2f, r = hs / 2f + 3;
        float hp = FixHealth.getHealth(entity), mh = entity.getMaxHealth();
        float ratio = ColorUtil.healthRatio(hp, mh);
        drawArc(cx, cy, r, 0, 360, new Color(50, 50, 50, (int)(200 * animProgress)), 3);
        int hc = Color.HSBtoRGB(ratio / 3, 0.9f, 1);
        drawArc(cx, cy, r, 0, 360 * ratio, new Color(ColorUtil.withAlpha(hc, (int) (255 * animProgress)), true), 3);
        if (entity instanceof AbstractClientPlayer) {
            drawClippedCircleFace(((AbstractClientPlayer)entity).getLocationSkin(), headX, headY, hs, animProgress);
        } else {
            face(entity, headX, headY, hs, animProgress);
        }
        float ix = headX + hs + p * 2;
        thTx(entity.getName(), ix, -h / 2f + p, ColorUtil.withAlpha(TH_COLOR_NAME, animProgress));
        mc.getTextureManager().bindTexture(HEART);
        GlStateManager.color(1, 1, 1, animProgress);
        Gui.drawModalRectWithCustomSizedTexture((int)ix, (int)(-h / 2f + p + 14), 52, 0, 9, 9, 256, 256);
        thTx(df.format(hp), ix + 11, -h / 2f + p + 15, ColorUtil.withAlpha(TH_COLOR_VALUE, animProgress));
        thTx(df.format(entity.getDistanceToEntity(mc.thePlayer)) + "m", ix, -h / 2f + p + 26, ColorUtil.withAlpha(TH_COLOR_VALUE, animProgress));
        float myHp = FixHealth.getHealth(mc.thePlayer);
        String status = hp > myHp ? "Losing" : hp < myHp ? "Winning" : "Tying";
        int sc = hp > myHp ? 0xFFFF5555 : hp < myHp ? 0xFF55FF55 : 0xFFFFFF55;
        thTx(status, ix, -h / 2f + p + 36, ColorUtil.withAlpha(sc, animProgress));
    }

    private void drawArc(float x, float y, float radius, float startAngle, float endAngle, Color c, float lineWidth) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(lineWidth);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (float i = startAngle; i <= endAngle; i += 1) {
            float a = (float) Math.toRadians(i - 90);
            GL11.glVertex2f(x + (float) Math.cos(a) * radius, y + (float) Math.sin(a) * radius);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    private void drawClippedCircleFace(ResourceLocation skin, float x, float y, float size, float alpha) {
        StencilUtil.checkSetupFBO();
        GL11.glPushAttrib(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        GL11.glColorMask(false, false, false, false);
        GlStateManager.disableTexture2D();
        RenderUtil.drawFilledCircle(x + size / 2, y + size / 2, size / 2, 0xFFFFFFFF);
        GlStateManager.enableTexture2D();
        GL11.glColorMask(true, true, true, true);
        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        // drawFilledCircle disables blend on exit; re-arm it so the masked face still honors its fade alpha
        GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(skin);
        GlStateManager.color(1, 1, 1, alpha);
        Gui.drawScaledCustomSizeModalRect((int)x, (int)y, 8, 8, 8, 8, (int)size, (int)size, 64, 64);
        GL11.glPopAttrib();
    }
}
