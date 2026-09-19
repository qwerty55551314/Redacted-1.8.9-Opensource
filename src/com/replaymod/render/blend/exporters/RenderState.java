package com.replaymod.render.blend.exporters;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Matrix4f;
import com.replaymod.render.ReplayModRender;
import com.replaymod.render.blend.BlendState;
import com.replaymod.render.blend.Exporter;
import com.replaymod.render.blend.Util;
import com.replaymod.render.blend.data.DMaterial;
import com.replaymod.render.blend.data.DObject;
import java.io.IOException;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

public class RenderState implements Exporter {
   private final BlendState blendState;
   private final ModelRendererExporter modelExporter = new ModelRendererExporter(this);
   private final Deque<DObject> objectStack = new LinkedList<>();
   private final Deque<Matrix4f> modelViewStack = new LinkedList<>();
   private int frame;

   public RenderState(BlendState blendState) {
      this.blendState = blendState;
      blendState.register(this.modelExporter);
   }

   public int getFrame() {
      return this.frame;
   }

   public DMaterial getCurrentMaterial() {
      return this.blendState.getMaterials().getActiveMaterial();
   }

   public ModelRendererExporter getModelExporter() {
      return this.modelExporter;
   }

   public Deque<DObject> getObjectStack() {
      return this.objectStack;
   }

   public void push(DObject object, Matrix4f modelView) {
      this.pushObject(object);
      this.pushModelView(modelView);
   }

   public void pushObject(DObject object) {
      this.objectStack.push(object);
   }

   public void pushModelView() {
      this.pushModelView(Util.getGlModelViewMatrix());
   }

   public void pushModelView(Matrix4f modelView) {
      this.modelViewStack.push(modelView);
   }

   public void pop() {
      this.popObject();
      this.popModelView();
   }

   public DObject popObject() {
      return this.objectStack.pop();
   }

   public Matrix4f popModelView() {
      return this.modelViewStack.pop();
   }

   public DObject peekObject() {
      return this.objectStack.peek();
   }

   public Matrix4f peekModelView() {
      return this.modelViewStack.peek();
   }

   public void applyLastModelViewTransformToObject() {
      this.applyLastModelViewTransformToObject(this.peekObject());
   }

   public void applyLastModelViewTransformToObject(DObject object) {
      Matrix4f mat = this.calcLastModelViewTransform();
      Util.posFromMat(mat, object.loc);
      Util.scaleFromMat(mat, object.scale);
      Util.scaleMat3(mat, object.scale);
      Util.rotFromMat(mat, object.rot);
      Util.glToBlend(object.loc);
      Util.glScaleToBlend(object.scale);
      Util.glToBlend(object.rot);
   }

   public Matrix4f calcLastModelViewTransform() {
      Iterator<Matrix4f> iter = this.modelViewStack.iterator();
      Matrix4f newModelView = iter.next();
      Matrix4f prevModelView = iter.next();
      return Matrix4f.mul(Matrix4f.invert(prevModelView, null), newModelView, null);
   }

   @Override
   public void setup() throws IOException {
   }

   @Override
   public void preFrame(int frame) throws IOException {
      this.frame = frame;
   }

   @Override
   public void postFrame(int frame) throws IOException {
      if (!this.objectStack.isEmpty()) {
         ReplayModRender.LOGGER.warn("Post frame with non-empty object stack! ({} remaining)", new Object[]{this.objectStack.size()});
         this.objectStack.clear();
      }

      if (!this.modelViewStack.isEmpty()) {
         ReplayModRender.LOGGER.warn("Post frame with non-empty model-view stack! ({} remaining)", new Object[]{this.modelViewStack.size()});
         this.modelViewStack.clear();
      }
   }

   @Override
   public void tearDown() throws IOException {
   }
}
