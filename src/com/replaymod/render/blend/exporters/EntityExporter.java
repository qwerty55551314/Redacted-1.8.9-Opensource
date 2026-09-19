package com.replaymod.render.blend.exporters;

import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Matrix4f;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector3f;
import com.replaymod.render.blend.BlendState;
import com.replaymod.render.blend.Exporter;
import com.replaymod.render.blend.Util;
import com.replaymod.render.blend.data.DObject;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

public class EntityExporter implements Exporter {
   private final Minecraft mc = MCVer.getMinecraft();
   private final RenderState renderState;
   private DObject entitiesObject;
   private Map<Entity, DObject> entityObjects;

   public EntityExporter(RenderState renderState) {
      this.renderState = renderState;
   }

   @Override
   public void setup() {
      this.entitiesObject = new DObject(DObject.Type.OB_EMPTY);
      this.entitiesObject.id.name = "Entities";
      this.entitiesObject.layers = 2;
      BlendState.getState().getScene().base.add(this.entitiesObject);
      this.entityObjects = new IdentityHashMap<>();
   }

   public void preEntitiesRender() {
      Matrix4f modelView = Util.getGlModelViewMatrix();
      Matrix4f.translate(Util.getCameraPos(), modelView, modelView);
      this.renderState.push(this.entitiesObject, modelView);
   }

   public void postEntitiesRender() {
      this.renderState.pop();
   }

   public void preRender(Entity entity, double dx, double dy, double dz, float yaw, float renderPartialTicks) {
      DObject entityObject = this.entityObjects.get(entity);
      if (entityObject == null) {
         entityObject = new DObject(DObject.Type.OB_EMPTY);
         entityObject.setParent(this.renderState.peekObject());
         entityObject.id.name = entity.getName();
         this.entityObjects.put(entity, entityObject);
      }

      this.renderState.pushObject(entityObject);
      if (!(entity instanceof EntityLivingBase)) {
         Matrix4f modelView = Util.getGlModelViewMatrix();
         Matrix4f.translate(new Vector3f((float)dx, (float)dy, (float)dz), modelView, modelView);
         this.renderState.pushModelView(modelView);
         this.renderState.applyLastModelViewTransformToObject();
         entityObject.keyframeLocRotScale(this.renderState.getFrame());
      }

      entityObject.setVisible(this.renderState.getFrame());
   }

   public void postEntityLivingSetup() {
      this.renderState.pushModelView();
      this.renderState.applyLastModelViewTransformToObject();
      this.renderState.peekObject().keyframeLocRotScale(this.renderState.getFrame());
   }

   public void postRender(Entity entity, double dx, double dy, double dz, float yaw, float renderPartialTicks) {
      this.renderState.pop();
   }
}
