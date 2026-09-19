package redacted.client.util.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public class Render3DUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void start3D() {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        // never write into the depth buffer: ESP/tracer geometry is overlay, and writing it poisons
        // anything rendered later in the pass that still tests depth
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        // cull can be left enabled by the world pass; boxes behind the camera or viewed edge-on then
        // vanish face-by-face depending on winding, which read as "ESP works close, not at distance"
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        // through GlStateManager so its blend cache stays honest -- a raw glBlendFunc here makes
        // the next tryBlendFuncSeparate caller (nametag/redacted text) think it's already set
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glLineWidth(1.5F);
    }

    public static void end3D() {
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.popMatrix();
    }

    // Box outline using the current GL color (vanilla batched WorldRenderer).
    public static void drawBoundingBox(AxisAlignedBB bb) {
        RenderGlobal.drawSelectionBoundingBox(bb);
    }

    // Fill + outline in one call; pass alpha-0 for a layer you don't want.
    public static void drawBox(AxisAlignedBB bb, int fillRGBA, int outlineRGBA) {
        if ((fillRGBA >>> 24) != 0) {
            ColorUtil.glColor(fillRGBA);
            drawFilledBox(bb);
        }
        if ((outlineRGBA >>> 24) != 0) {
            ColorUtil.glColor(outlineRGBA);
            drawBoundingBox(bb);
        }
    }

    // Solid box using the current GL color. Drawn as GL_TRIANGLES on purpose: GL_QUADS is only
    // translated to triangles when OptiFine's quads-to-triangles option is on, and on a core-profile
    // context an untranslated GL_QUADS draw silently renders NOTHING -- the exact failure behind
    // "ESP boxes show close but vanish at distance" on some machines. Triangles are valid everywhere.
    public static void drawFilledBox(AxisAlignedBB bb) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION);

        // each face as two triangles (v0,v1,v2)+(v0,v2,v3), same winding as the old quads
        quad(wr, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ); // bottom
        quad(wr, bb.minX, bb.maxY, bb.minZ, bb.minX, bb.maxY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, bb.maxX, bb.maxY, bb.minZ); // top
        quad(wr, bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.minY, bb.minZ); // north
        quad(wr, bb.minX, bb.minY, bb.maxZ, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ); // south
        quad(wr, bb.minX, bb.minY, bb.minZ, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ); // west
        quad(wr, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, bb.maxX, bb.minY, bb.maxZ); // east

        tessellator.draw();
    }

    private static void quad(WorldRenderer wr, double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4) {
        wr.pos(x1, y1, z1).endVertex();
        wr.pos(x2, y2, z2).endVertex();
        wr.pos(x3, y3, z3).endVertex();
        wr.pos(x1, y1, z1).endVertex();
        wr.pos(x3, y3, z3).endVertex();
        wr.pos(x4, y4, z4).endVertex();
    }

    public static double[] viewerOffset() {
        return new double[]{
                mc.getRenderManager().viewerPosX,
                mc.getRenderManager().viewerPosY,
                mc.getRenderManager().viewerPosZ
        };
    }

    // Line in 3D space using the current GL color, drawn through the vanilla Tessellator instead of
    // immediate mode (glBegin/glEnd): the client's GL context can be a core profile where immediate
    // mode silently draws nothing, which made tracer lines vanish on some machines/drivers.
    public static void drawLine(double x1, double y1, double z1, double x2, double y2, double z2) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        wr.pos(x1, y1, z1).endVertex();
        wr.pos(x2, y2, z2).endVertex();
        tessellator.draw();
    }

    // Batched variant: begin once, emit all segment pairs, draw once. Output is pixel-identical
    // to N drawLine calls (same primitive, same vertices) at one draw call instead of N.
    public static void beginLines() {
        Tessellator.getInstance().getWorldRenderer().begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
    }

    public static void lineVertex(double x, double y, double z) {
        Tessellator.getInstance().getWorldRenderer().pos(x, y, z).endVertex();
    }

    public static void drawLines() {
        Tessellator.getInstance().draw();
    }

    // eyes + look in render space - where tracer lines should start from
    public static double[] tracerStart(float partialTicks) {
        double[] v = viewerOffset();
        Vec3 eyes = mc.thePlayer.getPositionEyes(partialTicks);
        Vec3 look = mc.thePlayer.getLook(partialTicks);
        return new double[]{
                eyes.xCoord - v[0] + look.xCoord,
                eyes.yCoord - v[1] + look.yCoord,
                eyes.zCoord - v[2] + look.zCoord
        };
    }
}
