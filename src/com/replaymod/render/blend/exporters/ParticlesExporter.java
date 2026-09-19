package com.replaymod.render.blend.exporters;

import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Matrix4f;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector3f;
import com.replaymod.render.blend.BlendMeshBuilder;
import com.replaymod.render.blend.BlendState;
import com.replaymod.render.blend.Exporter;
import com.replaymod.render.blend.Util;
import com.replaymod.render.blend.data.DMesh;
import com.replaymod.render.blend.data.DObject;
import com.replaymod.render.blend.mixin.ParticleAccessor;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;

public class ParticlesExporter implements Exporter {
   private final Minecraft mc = MCVer.getMinecraft();
   private final RenderState renderState;
   private DObject pointAtObject;
   private DObject particlesObject;
   private DObject litParticlesObject;
   private Map<Entity, DObject> particleObjects;
   private Map<Entity, DObject> particleObjectsSeen;

   public ParticlesExporter(RenderState renderState) {
      this.renderState = renderState;
   }

   @Override
   public void setup() throws IOException {
      this.pointAtObject = new DObject(DObject.Type.OB_EMPTY);
      this.pointAtObject.id.name = "Particles Target";
      this.pointAtObject.layers = 4;
      BlendState.getState().getScene().base.add(this.pointAtObject);
      this.particlesObject = new DObject(DObject.Type.OB_EMPTY);
      this.particlesObject.id.name = "Particles";
      this.particlesObject.layers = 2;
      BlendState.getState().getScene().base.add(this.particlesObject);
      this.litParticlesObject = new DObject(DObject.Type.OB_EMPTY);
      this.litParticlesObject.id.name = "Lit Particles";
      this.litParticlesObject.layers = 2;
      BlendState.getState().getScene().base.add(this.litParticlesObject);
      this.particleObjects = new IdentityHashMap<>();
      this.particleObjectsSeen = new IdentityHashMap<>();
   }

   public void preParticlesRender(boolean lit) {
      Matrix4f modelView = Util.getGlModelViewMatrix();
      Matrix4f.translate(Util.getCameraPos(), modelView, modelView);
      this.renderState.push(lit ? this.litParticlesObject : this.particlesObject, modelView);
   }

   public void postParticlesRender() {
      this.renderState.pop();
   }

   public void onRender(EntityFX particle, float renderPartialTicks) {
      DObject particleObject = this.particleObjects.get(particle);
      if (particleObject == null) {
         particleObject = new DObject(DObject.Type.OB_EMPTY);
         particleObject.setParent(this.renderState.peekObject());
         particleObject.pointAt = this.pointAtObject;
         particleObject.layers = 4;
         particleObject.id.name = particle.getClass().getSimpleName();
         particleObject.keyframe("hide", 0, this.renderState.getFrame() - 1, 1.0F);
         particleObject.keyframe("hide", 0, this.renderState.getFrame(), 0.0F);
         this.particleObjects.put(particle, particleObject);
      }

      this.particleObjectsSeen.put(particle, particleObject);
      this.renderState.pushObject(particleObject);
      Matrix4f modelView = Util.getGlModelViewMatrix();
      ParticleAccessor acc = (ParticleAccessor)particle;
      double dx = acc.getPrevPosX() + (acc.getPosX() - acc.getPrevPosX()) * renderPartialTicks;
      double dy = acc.getPrevPosY() + (acc.getPosY() - acc.getPrevPosY()) * renderPartialTicks;
      double dz = acc.getPrevPosZ() + (acc.getPosZ() - acc.getPrevPosZ()) * renderPartialTicks;
      dx -= EntityFX.interpPosX;
      dy -= EntityFX.interpPosY;
      dz -= EntityFX.interpPosZ;
      Vector3f offset = new Vector3f((float)dx, (float)dy, (float)dz);
      Matrix4f.translate(offset, modelView, modelView);
      this.renderState.pushModelView(modelView);
      this.renderState.applyLastModelViewTransformToObject();
      particleObject.keyframeLoc(this.renderState.getFrame());
      DMesh mesh = this.generateMeshForParticle(particle, offset);
      if (mesh.vertices.isEmpty()) {
         this.renderState.pop();
      } else {
         if (particleObject.mesh == null) {
            if (mesh.hasZeroLengthEdge(0.1F)) {
               particleObject.scale.set(0.0F, 0.0F, 0.0F);
            } else {
               particleObject.type = DObject.Type.OB_MESH;
               particleObject.mesh = mesh;
               particleObject.scale.set(1.0F, 1.0F, 1.0F);
            }
         } else {
            float unitSize = particleObject.mesh.getSizeX();
            float currentSize = mesh.getSizeX();
            float relativeScale = currentSize / unitSize;
            particleObject.scale.set(relativeScale, relativeScale, relativeScale);
         }

         particleObject.keyframeScale(this.renderState.getFrame());
         this.renderState.pop();
      }
   }

   private DMesh generateMeshForParticle(EntityFX particle, Vector3f offset) {
      DMesh mesh = new DMesh();
      BlendMeshBuilder builder = new BlendMeshBuilder(mesh);
      builder.setReverseOffset(offset);
      builder.setWellBehaved(true);
      builder.begin(7, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
      particle.renderParticle(builder, MCVer.getMinecraft().getRenderViewEntity(), 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F);
      builder.finishDrawing();
      return mesh;
   }

   @Override
   public void postFrame(int frame) throws IOException {
      this.particleObjects
         .entrySet()
         .stream()
         .filter(entry -> !this.particleObjectsSeen.containsKey(entry.getKey()))
         .forEach(entry -> entry.getValue().keyframe("hide", 0, this.renderState.getFrame(), 1.0F));
      this.particleObjects = this.particleObjectsSeen;
      this.particleObjectsSeen = new IdentityHashMap<>();
   }
}
