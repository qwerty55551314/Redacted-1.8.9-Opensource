package com.replaymod.render.blend.exporters;

import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Matrix4f;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector3f;
import com.replaymod.render.blend.BlendState;
import com.replaymod.render.blend.Exporter;
import com.replaymod.render.blend.Util;
import com.replaymod.render.blend.data.DObject;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;

public class TileEntityExporter implements Exporter {
   private final Minecraft mc = MCVer.getMinecraft();
   private final RenderState renderState;
   private DObject tileEntitiesObject;
   private Map<TileEntity, DObject> tileEntityObjects;
   private Map<TileEntity, DObject> tileEntityObjectsSeen;

   public TileEntityExporter(RenderState renderState) {
      this.renderState = renderState;
   }

   @Override
   public void setup() throws IOException {
      this.tileEntitiesObject = new DObject(DObject.Type.OB_EMPTY);
      this.tileEntitiesObject.id.name = "TileEntities";
      this.tileEntitiesObject.layers = 2;
      BlendState.getState().getScene().base.add(this.tileEntitiesObject);
      this.tileEntityObjects = new IdentityHashMap<>();
      this.tileEntityObjectsSeen = new IdentityHashMap<>();
   }

   public void preTileEntitiesRender() {
      Matrix4f modelView = Util.getGlModelViewMatrix();
      Matrix4f.translate(
         new Vector3f(
            (float)(-this.mc.getRenderManager().viewerPosX), (float)(-this.mc.getRenderManager().viewerPosY), (float)(-this.mc.getRenderManager().viewerPosZ)
         ),
         modelView,
         modelView
      );
      this.renderState.push(this.tileEntitiesObject, modelView);
   }

   public void postTileEntitiesRender() {
      this.renderState.pop();
   }

   public void preRender(TileEntity tileEntity, double dx, double dy, double dz, float renderPartialTicks, int destroyStage, float alpha) {
      DObject tileEntityObject = this.tileEntityObjects.get(tileEntity);
      if (tileEntityObject == null) {
         tileEntityObject = new DObject(DObject.Type.OB_EMPTY);
         tileEntityObject.setParent(this.renderState.peekObject());
         BlockPos pos = tileEntity.getPos();
         tileEntityObject.id.name = Util.getTileEntityId(tileEntity) + "( " + pos.getX() + "/" + pos.getY() + "/" + pos.getZ() + ")";
         tileEntityObject.keyframe("hide", 0, this.renderState.getFrame() - 1, 1.0F);
         tileEntityObject.keyframe("hide", 0, this.renderState.getFrame(), 0.0F);
         this.tileEntityObjects.put(tileEntity, tileEntityObject);
      }

      this.tileEntityObjectsSeen.put(tileEntity, tileEntityObject);
      this.renderState.pushObject(tileEntityObject);
      Matrix4f modelView = Util.getGlModelViewMatrix();
      Matrix4f.translate(new Vector3f((float)dx, (float)dy, (float)dz), modelView, modelView);
      this.renderState.pushModelView(modelView);
      this.renderState.applyLastModelViewTransformToObject();
      tileEntityObject.keyframeLocRotScale(this.renderState.getFrame());
   }

   public void postRender() {
      this.renderState.pop();
   }

   @Override
   public void postFrame(int frame) throws IOException {
      for (Entry<TileEntity, DObject> entry : this.tileEntityObjects.entrySet()) {
         if (!this.tileEntityObjectsSeen.containsKey(entry.getKey())) {
            DObject object = entry.getValue();
            object.keyframe("hide", 0, this.renderState.getFrame(), 1.0F);
         }
      }

      this.tileEntityObjects = this.tileEntityObjectsSeen;
      this.tileEntityObjectsSeen = new IdentityHashMap<>();
   }
}
