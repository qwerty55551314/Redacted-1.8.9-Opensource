package com.replaymod.render.blend.exporters;

import com.replaymod.render.blend.BlendMeshBuilder;
import com.replaymod.render.blend.Exporter;
import com.replaymod.render.blend.data.DMesh;
import com.replaymod.render.blend.data.DObject;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

public class ItemExporter implements Exporter {
   private final RenderState renderState;

   public ItemExporter(RenderState renderState) {
      this.renderState = renderState;
   }

   public void onRender(Object renderItem, IBakedModel model, ItemStack stack) {
      DObject object = this.getObjectForItemStack(renderItem, model, stack);
      this.renderState.pushObject(object);
      this.renderState.pushModelView();
      this.renderState.applyLastModelViewTransformToObject();
      object.setVisible(this.renderState.getFrame());
      object.keyframeLocRotScale(this.renderState.getFrame());
      this.renderState.pop();
   }

   private DObject getObjectForItemStack(Object renderItem, IBakedModel model, ItemStack stack) {
      int frame = this.renderState.getFrame();
      DObject parent = this.renderState.peekObject();
      DObject object = null;

      for (DObject child : parent.getChildren()) {
         if (child.lastFrame < frame
            && child instanceof ItemExporter.ItemBasedDObject
            && ((ItemExporter.ItemBasedDObject)child).isBasedOn(renderItem, model, stack)) {
            object = child;
            break;
         }
      }

      if (object == null) {
         object = new ItemExporter.ItemBasedDObject(renderItem, model, stack);
         object.id.name = stack.getDisplayName();
         object.setParent(parent);
      }

      object.lastFrame = frame;
      return object;
   }

   private static DMesh generateMeshForItemStack(Object renderItem, IBakedModel model, ItemStack stack) {
      DMesh mesh = new DMesh();
      BlendMeshBuilder builder = new BlendMeshBuilder(mesh);
      builder.setWellBehaved(true);
      builder.begin(7, DefaultVertexFormats.ITEM);

      for (EnumFacing face : EnumFacing.values()) {
         renderQuads(renderItem, builder, model.getFaceQuads(face), stack);
      }

      renderQuads(renderItem, builder, model.getGeneralQuads(), stack);
      builder.finishDrawing();
      return mesh;
   }

   private static void renderQuads(Object renderItem, BlendMeshBuilder buffer, List<BakedQuad> quads, ItemStack stack) {
      for (BakedQuad quad : quads) {
         int color = stack != null && quad.hasTintIndex() ? stack.getItem().getColorFromItemStack(stack, quad.getTintIndex()) | 0xFF000000 : -1;
         if (color != -1) {
            float r = (color >> 16 & 0xFF) / 255.0F;
            float g = (color >> 8 & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            buffer.putColorMultiplier(r, g, b, 4);
         }
      }
   }

   private static class ItemBasedDObject extends DObject {
      private final Object renderItem;
      private final IBakedModel model;
      private final ItemStack stack;
      private boolean valid;

      public ItemBasedDObject(Object renderItem, IBakedModel model, ItemStack stack) {
         super(ItemExporter.generateMeshForItemStack(renderItem, model, stack));
         this.renderItem = renderItem;
         this.model = model;
         this.stack = stack;
      }

      public boolean isBasedOn(Object renderItem, IBakedModel model, ItemStack stack) {
         return this.renderItem == renderItem && this.model == model && this.stack == stack;
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
