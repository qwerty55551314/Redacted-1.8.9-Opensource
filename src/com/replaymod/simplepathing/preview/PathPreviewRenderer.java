package com.replaymod.simplepathing.preview;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.events.PostRenderWorldCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector3f;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;
import com.replaymod.lib.org.apache.commons.lang3.tuple.Pair;
import com.replaymod.lib.org.apache.commons.lang3.tuple.Triple;
import com.replaymod.pathing.properties.CameraProperties;
import com.replaymod.pathing.properties.SpectatorProperty;
import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.interpolation.Interpolator;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import com.replaymod.replaystudio.pathing.path.Path;
import com.replaymod.replaystudio.pathing.path.PathSegment;
import com.replaymod.replaystudio.util.EntityPositionTracker;
import com.replaymod.replaystudio.util.Location;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import com.replaymod.simplepathing.SPTimeline;
import com.replaymod.simplepathing.gui.GuiPathing;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class PathPreviewRenderer extends EventRegistrations {
   private static final ResourceLocation CAMERA_HEAD = MCVer.identifier("replaymod", "camera_head.png");
   private static final Minecraft mc = com.replaymod.core.versions.MCVer.getMinecraft();
   private static final int SLOW_PATH_COLOR = 16764108;
   private static final int FAST_PATH_COLOR = 6684672;
   private static final double FASTEST_PATH_SPEED = 0.01;
   private final ReplayModSimplePathing mod;
   private final ReplayHandler replayHandler;

   public PathPreviewRenderer(ReplayModSimplePathing mod, ReplayHandler replayHandler) {
      this.on(PostRenderWorldCallback.EVENT, this::renderCameraPath);
      this.mod = mod;
      this.replayHandler = replayHandler;
   }

   private void renderCameraPath(MatrixStack matrixStack) {
      if (this.replayHandler.getReplaySender().isAsyncMode() && !mc.gameSettings.hideGUI) {
         Entity view = mc.getRenderViewEntity();
         if (view != null) {
            GuiPathing guiPathing = this.mod.getGuiPathing();
            if (guiPathing != null) {
               EntityPositionTracker entityTracker = guiPathing.getEntityTracker();
               SPTimeline timeline = this.mod.getCurrentTimeline();
               if (timeline != null) {
                  Path path = timeline.getPositionPath();
                  if (!path.getKeyframes().isEmpty()) {
                     Path timePath = timeline.getTimePath();
                     path.update();
                     int renderDistance = mc.gameSettings.renderDistanceChunks * 16;
                     int renderDistanceSquared = renderDistance * renderDistance;
                     Vector3f viewPos = new Vector3f((float)view.posX, (float)view.posY - view.getEyeHeight(), (float)view.posZ);
                     GL11.glPushAttrib(1048575);
                     com.replaymod.core.versions.MCVer.pushMatrix();

                     try {
                        GL11.glDisable(2896);
                        GL11.glDisable(3553);

                        for (PathSegment segment : path.getSegments()) {
                           Interpolator interpolator = segment.getInterpolator();
                           Keyframe start = segment.getStartKeyframe();
                           Keyframe end = segment.getEndKeyframe();
                           long diff = (int)(end.getTime() - start.getTime());
                           boolean spectator = interpolator.getKeyframeProperties().contains(SpectatorProperty.PROPERTY);
                           if (!spectator || entityTracker != null) {
                              long steps = spectator ? Math.max(diff / 50L, 10L) : 100L;
                              Vector3f prevPos = null;

                              for (int i = 0; i <= steps; i++) {
                                 long time = start.getTime() + diff * i / steps;
                                 if (spectator) {
                                    Optional<Integer> entityId = path.getValue(SpectatorProperty.PROPERTY, time);
                                    Optional<Integer> replayTime = timePath.getValue(TimestampProperty.PROPERTY, time);
                                    if (entityId.isPresent() && replayTime.isPresent()) {
                                       Location loc = entityTracker.getEntityPositionAtTimestamp(entityId.get(), replayTime.get().intValue());
                                       if (loc != null) {
                                          Vector3f pos = this.loc2Vec(loc);
                                          if (prevPos != null) {
                                             this.drawConnection(matrixStack, viewPos, prevPos, pos, 65535, renderDistanceSquared);
                                          }

                                          prevPos = pos;
                                          continue;
                                       }
                                    }
                                 } else {
                                    Optional<Vector3f> optPos = path.getValue(CameraProperties.POSITION, time).map(this::tripleD2Vec);
                                    if (optPos.isPresent()) {
                                       Vector3f pos = optPos.get();
                                       if (prevPos != null) {
                                          double distance = Math.sqrt(distanceSquared(prevPos, pos));
                                          double speed = Math.min(distance / (diff / steps), 0.01);
                                          double speedFraction = speed / 0.01;
                                          int color = interpolateColor(16764108, 6684672, speedFraction);
                                          this.drawConnection(matrixStack, viewPos, prevPos, pos, color << 8 | 0xFF, renderDistanceSquared);
                                       }

                                       prevPos = pos;
                                       continue;
                                    }
                                 }

                                 prevPos = null;
                              }
                           }
                        }

                        GL11.glEnable(3042);
                        GL11.glBlendFunc(774, 768);
                        GL11.glDisable(2929);
                        GL11.glEnable(3553);
                        path.getKeyframes()
                           .stream()
                           .map(k -> Pair.of(k, k.getValue(CameraProperties.POSITION).map(this::tripleD2Vec)))
                           .filter(p -> p.getRight().isPresent())
                           .map(p -> Pair.of(p.getLeft(), p.getRight().get()))
                           .filter(p -> distanceSquared(p.getRight(), viewPos) < renderDistanceSquared)
                           .sorted(new PathPreviewRenderer.KeyframeComparator(viewPos))
                           .forEachOrdered(p -> this.drawPoint(matrixStack, viewPos, p.getRight(), p.getLeft()));
                        GL11.glBlendFunc(770, 771);
                        GL11.glEnable(2929);
                        int time = guiPathing.timeline.getCursorPosition();
                        Optional<Integer> entityId = path.getValue(SpectatorProperty.PROPERTY, time);
                        if (entityId.isPresent()) {
                           if (entityTracker != null) {
                              Optional<Integer> replayTime = timePath.getValue(TimestampProperty.PROPERTY, time);
                              if (replayTime.isPresent()) {
                                 Location loc = entityTracker.getEntityPositionAtTimestamp(entityId.get(), replayTime.get().intValue());
                                 if (loc != null) {
                                    this.drawCamera(matrixStack, viewPos, this.loc2Vec(loc), new Vector3f(loc.getYaw(), loc.getPitch(), 0.0F));
                                 }
                              }
                           }
                        } else {
                           Optional<Vector3f> cameraPos = path.getValue(CameraProperties.POSITION, time).map(this::tripleD2Vec);
                           Optional<Vector3f> cameraRot = path.getValue(CameraProperties.ROTATION, time).map(this::tripleF2Vec);
                           if (cameraPos.isPresent() && cameraRot.isPresent()) {
                              this.drawCamera(matrixStack, viewPos, cameraPos.get(), cameraRot.get());
                           }
                        }
                     } finally {
                        com.replaymod.core.versions.MCVer.popMatrix();
                        GL11.glPopAttrib();
                     }
                  }
               }
            }
         }
      }
   }

   private Vector3f loc2Vec(Location loc) {
      return new Vector3f((float)loc.getX(), (float)loc.getY(), (float)loc.getZ());
   }

   private Vector3f tripleD2Vec(Triple<Double, Double, Double> loc) {
      return new Vector3f(loc.getLeft().floatValue(), loc.getMiddle().floatValue(), loc.getRight().floatValue());
   }

   private Vector3f tripleF2Vec(Triple<Float, Float, Float> loc) {
      return new Vector3f(loc.getLeft(), loc.getMiddle(), loc.getRight());
   }

   private static int interpolateColor(int c1, int c2, double weight) {
      return interpolateColorComponent(c1 >> 16 & 0xFF, c2 >> 16 & 0xFF, weight) << 16
         | interpolateColorComponent(c1 >> 8 & 0xFF, c2 >> 8 & 0xFF, weight) << 8
         | interpolateColorComponent(c1 & 0xFF, c2 & 0xFF, weight);
   }

   private static int interpolateColorComponent(int c1, int c2, double weight) {
      return (int)(c1 + (1.0 - Math.pow(Math.E, -4.0 * weight)) * (c2 - c1)) & 0xFF;
   }

   private static double distanceSquared(Vector3f p1, Vector3f p2) {
      return Vector3f.sub(p1, p2, null).lengthSquared();
   }

   private void drawConnection(MatrixStack stack, Vector3f view, Vector3f pos1, Vector3f pos2, int color, int renderDistanceSquared) {
      if (!(distanceSquared(view, pos1) > renderDistanceSquared)) {
         if (!(distanceSquared(view, pos2) > renderDistanceSquared)) {
            MatrixStack pose = new MatrixStack();
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer buffer = tessellator.getWorldRenderer();
            buffer.begin(1, DefaultVertexFormats.POSITION_COLOR);
            com.replaymod.core.versions.MCVer.emitLine(pose, buffer, Vector3f.sub(pos1, view, null), Vector3f.sub(pos2, view, null), color, 3.0F);
            tessellator.draw();
         }
      }
   }

   private void drawPoint(MatrixStack stack, Vector3f view, Vector3f pos, Keyframe keyframe) {
      com.replaymod.core.versions.MCVer.pushMatrix();
      Vector3f t = Vector3f.sub(pos, view, null);
      GL11.glTranslatef(t.x, t.y, t.z);
      GL11.glRotatef(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
      GL11.glRotatef(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
      com.replaymod.core.versions.MCVer.bindTexture(ReplayMod.TEXTURE);
      float size = 0.0390625F;
      float posX = 0.3125F + (keyframe.getValue(SpectatorProperty.PROPERTY).isPresent() ? size : 0.0F);
      float posY = 0.0F + (this.mod.isSelected(keyframe) ? size : 0.0F);
      float minX = -0.5F;
      float minY = -0.5F;
      float maxX = 0.5F;
      float maxY = 0.5F;
      MatrixStack pose = stack;
      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer buffer = tessellator.getWorldRenderer();
      buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
      this.vertex(buffer, pose, minX, minY, 0.0F, posX + size, posY + size, 255);
      this.vertex(buffer, pose, minX, maxY, 0.0F, posX + size, posY, 255);
      this.vertex(buffer, pose, maxX, maxY, 0.0F, posX, posY, 255);
      this.vertex(buffer, pose, maxX, minY, 0.0F, posX, posY + size, 255);
      tessellator.draw();
      com.replaymod.core.versions.MCVer.popMatrix();
   }

   private void drawCamera(MatrixStack stack, Vector3f view, Vector3f pos, Vector3f rot) {
      com.replaymod.core.versions.MCVer.bindTexture(CAMERA_HEAD);
      com.replaymod.core.versions.MCVer.pushMatrix();
      Vector3f t = Vector3f.sub(pos, view, null);
      GL11.glTranslatef(t.x, t.y, t.z);
      GL11.glRotatef(-rot.x, 0.0F, 1.0F, 0.0F);
      GL11.glRotatef(rot.y, 1.0F, 0.0F, 0.0F);
      GL11.glRotatef(rot.z, 0.0F, 0.0F, 1.0F);
      MatrixStack pose = new MatrixStack();
      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer buffer = tessellator.getWorldRenderer();
      buffer.begin(1, DefaultVertexFormats.POSITION_COLOR);
      com.replaymod.core.versions.MCVer.emitLine(pose, buffer, new Vector3f(0.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 2.0F), 16711850, 3.0F);
      GL11.glDisable(3553);
      tessellator.draw();
      GL11.glEnable(3553);
      float cubeSize = 0.5F;
      float r = -cubeSize / 2.0F;
      buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
      this.vertex(buffer, pose, r, r + cubeSize, r, 0.375F, 0.125F, 200);
      this.vertex(buffer, pose, r + cubeSize, r + cubeSize, r, 0.5F, 0.125F, 200);
      this.vertex(buffer, pose, r + cubeSize, r, r, 0.5F, 0.25F, 200);
      this.vertex(buffer, pose, r, r, r, 0.375F, 0.25F, 200);
      this.vertex(buffer, pose, r + cubeSize, r, r + cubeSize, 0.25F, 0.25F, 200);
      this.vertex(buffer, pose, r + cubeSize, r + cubeSize, r + cubeSize, 0.25F, 0.125F, 200);
      this.vertex(buffer, pose, r, r + cubeSize, r + cubeSize, 0.125F, 0.125F, 200);
      this.vertex(buffer, pose, r, r, r + cubeSize, 0.125F, 0.25F, 200);
      this.vertex(buffer, pose, r + cubeSize, r + cubeSize, r, 0.0F, 0.125F, 200);
      this.vertex(buffer, pose, r + cubeSize, r + cubeSize, r + cubeSize, 0.125F, 0.125F, 200);
      this.vertex(buffer, pose, r + cubeSize, r, r + cubeSize, 0.125F, 0.25F, 200);
      this.vertex(buffer, pose, r + cubeSize, r, r, 0.0F, 0.25F, 200);
      this.vertex(buffer, pose, r, r + cubeSize, r + cubeSize, 0.25F, 0.125F, 200);
      this.vertex(buffer, pose, r, r + cubeSize, r, 0.375F, 0.125F, 200);
      this.vertex(buffer, pose, r, r, r, 0.375F, 0.25F, 200);
      this.vertex(buffer, pose, r, r, r + cubeSize, 0.25F, 0.25F, 200);
      this.vertex(buffer, pose, r + cubeSize, r, r, 0.375F, 0.0F, 200);
      this.vertex(buffer, pose, r + cubeSize, r, r + cubeSize, 0.375F, 0.125F, 200);
      this.vertex(buffer, pose, r, r, r + cubeSize, 0.25F, 0.125F, 200);
      this.vertex(buffer, pose, r, r, r, 0.25F, 0.0F, 200);
      this.vertex(buffer, pose, r, r + cubeSize, r, 0.125F, 0.0F, 200);
      this.vertex(buffer, pose, r, r + cubeSize, r + cubeSize, 0.125F, 0.125F, 200);
      this.vertex(buffer, pose, r + cubeSize, r + cubeSize, r + cubeSize, 0.25F, 0.125F, 200);
      this.vertex(buffer, pose, r + cubeSize, r + cubeSize, r, 0.25F, 0.0F, 200);
      tessellator.draw();
      com.replaymod.core.versions.MCVer.popMatrix();
   }

   private void vertex(WorldRenderer buffer, MatrixStack stack, float x, float y, float z, float u, float v, int alpha) {
      buffer.pos(x, y, z).tex(u, v).color(255, 255, 255, alpha).endVertex();
   }

   private class KeyframeComparator implements Comparator<Pair<Keyframe, Vector3f>> {
      private final Vector3f viewPos;

      public KeyframeComparator(Vector3f viewPos) {
         this.viewPos = viewPos;
      }

      public int compare(Pair<Keyframe, Vector3f> o1, Pair<Keyframe, Vector3f> o2) {
         return -Double.compare(
            PathPreviewRenderer.distanceSquared(o1.getRight(), this.viewPos), PathPreviewRenderer.distanceSquared(o2.getRight(), this.viewPos)
         );
      }
   }
}
