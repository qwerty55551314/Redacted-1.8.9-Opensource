package com.replaymod.render.blend.exporters;

import com.replaymod.render.blend.BlendMeshBuilder;
import com.replaymod.render.blend.Exporter;
import com.replaymod.render.blend.Util;
import com.replaymod.render.blend.data.DMesh;
import com.replaymod.render.blend.data.DObject;
import java.io.IOException;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import org.lwjgl.opengl.GL11;

public class ModelRendererExporter implements Exporter {
   private final RenderState renderState;

   public ModelRendererExporter(RenderState renderState) {
      this.renderState = renderState;
   }

   @Override
   public void setup() throws IOException {
   }

   public void preRenderModel(ModelRenderer model, float scale) {
      DObject object = this.getObjectForModel(model, scale);
      this.renderState.pushObject(object);
      this.renderState.pushModelView();
   }

   public void onRenderModel() {
      if (GL11.glIsEnabled(3553) && Util.isGlTextureMatrixIdentity()) {
         this.renderState.popModelView();
         this.renderState.pushModelView();
         this.renderState.applyLastModelViewTransformToObject();
         DObject object = this.renderState.peekObject();
         object.setVisible(this.renderState.getFrame());
         object.keyframeLocRotScale(this.renderState.getFrame());
      }
   }

   public void postRenderModel() {
      this.renderState.pop();
   }

   private DObject getObjectForModel(ModelRenderer model, float scale) {
      int frame = this.renderState.getFrame();
      DObject parent = this.renderState.peekObject();
      DObject object = null;

      for (DObject child : parent.getChildren()) {
         if (child.lastFrame < frame
            && child instanceof ModelRendererExporter.ModelBasedDObject
            && ((ModelRendererExporter.ModelBasedDObject)child).isBasedOn(model, scale)) {
            object = child;
            break;
         }
      }

      if (object == null) {
         object = new ModelRendererExporter.ModelBasedDObject(model, scale);
         object.id.name = model.boxName;
         object.setParent(parent);
      }

      object.lastFrame = frame;
      return object;
   }

   private static DMesh generateMesh(ModelRenderer model, float scale) {
      DMesh mesh = new DMesh();
      BlendMeshBuilder builder = new BlendMeshBuilder(mesh);

      for (ModelBox box : model.cubeList) {
         box.render(builder, scale);
      }

      builder.maybeFinishDrawing();
      return mesh;
   }

   private static class ModelBasedDObject extends DObject {
      private final ModelRenderer model;
      private final float scale;
      private boolean valid;

      public ModelBasedDObject(ModelRenderer model, float scale) {
         super(ModelRendererExporter.generateMesh(model, scale));
         this.model = model;
         this.scale = scale;
      }

      public boolean isBasedOn(ModelRenderer model, float scale) {
         return this.model == model && Math.abs(this.scale - scale) < 1.0E-4;
      }

      @Override
      public void setVisible(int frame) {
         this.valid = true;
         super.setVisible(frame);
      }

      @Override
      public boolean isValid() {
         return this.valid;
      }
   }
}
