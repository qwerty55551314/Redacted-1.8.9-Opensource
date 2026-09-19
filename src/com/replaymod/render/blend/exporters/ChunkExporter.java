package com.replaymod.render.blend.exporters;

import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector3f;
import com.replaymod.lib.org.apache.commons.lang3.tuple.Pair;
import com.replaymod.render.blend.BlendMeshBuilder;
import com.replaymod.render.blend.BlendState;
import com.replaymod.render.blend.Exporter;
import com.replaymod.render.blend.data.DMesh;
import com.replaymod.render.blend.data.DObject;
import com.replaymod.render.blend.mixin.ContainerLocalRenderInformationAccessor;
import com.replaymod.render.blend.mixin.WorldRendererAccessor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import org.lwjgl.opengl.GL15;

public class ChunkExporter implements Exporter {
   private final Map<BlockPos, DObject> chunkObjects = new HashMap<>();
   private final Map<BlockPos, Map<EnumWorldBlockLayer, DObject>> chunkLayerObjects = new HashMap<>();
   private final List<Pair<RenderChunk, EnumWorldBlockLayer>> chunks = new ArrayList<>();
   private DObject chunksObject;
   private int frame;
   private static final int STRIDE = 28;

   public void addChunkUpdate(RenderChunk chunk, CompiledChunk compiledChunk) {
      for (EnumWorldBlockLayer layer : EnumWorldBlockLayer.values()) {
         if (compiledChunk == null || compiledChunk.isLayerStarted(layer)) {
            this.chunks.add(Pair.of(chunk, layer));
         }
      }
   }

   @Override
   public void setup() throws IOException {
      Minecraft mc = MCVer.getMinecraft();

      for (Object obj : ((WorldRendererAccessor)mc.renderGlobal).getRenderInfos()) {
         ContainerLocalRenderInformationAccessor renderInfo = (ContainerLocalRenderInformationAccessor) obj;
         RenderChunk renderChunk = renderInfo.getRenderChunk();
         CompiledChunk compiledChunk = renderChunk.getCompiledChunk();
         if (!compiledChunk.isEmpty()) {
            this.addChunkUpdate(renderChunk, null);
         }
      }

      this.chunksObject = new DObject(DObject.Type.OB_EMPTY);
      this.chunksObject.id.name = "Chunks";
      this.chunksObject.layers = 2;
      BlendState.getState().getScene().base.add(this.chunksObject);
   }

   @Override
   public void tearDown() throws IOException {
      this.chunks.clear();
      this.chunkObjects.clear();
      this.chunksObject = null;
   }

   @Override
   public void preFrame(int frame) throws IOException {
      this.frame = frame;
   }

   @Override
   public void postFrame(int frame) throws IOException {
      for (Pair<RenderChunk, EnumWorldBlockLayer> pair : this.chunks) {
         EnumWorldBlockLayer layer = pair.getRight();
         RenderChunk chunk = pair.getLeft();
         DObject chunkObject = this.chunkObjects.get(chunk.getPosition());
         if (chunkObject == null) {
            chunkObject = this.buildChunkObject(chunk);
            this.chunkObjects.put(chunk.getPosition(), chunkObject);
            this.chunkLayerObjects.put(chunk.getPosition(), new EnumMap<>(layer.getDeclaringClass()));
         }

         DObject layerObject = this.buildChunkLayerObject(chunkObject, chunk, layer);
         if (layerObject != null) {
            Map<EnumWorldBlockLayer, DObject> layerObjects = this.chunkLayerObjects.get(chunk.getPosition());
            DObject oldLayerObject = layerObjects.get(layer);
            if (oldLayerObject != null) {
               oldLayerObject.keyframe("hide", 0, frame, 1.0F);
            }

            if (frame > 0) {
               layerObject.keyframe("hide", 0, frame - 1, 1.0F);
               layerObject.keyframe("hide", 0, frame, 0.0F);
            } else {
               layerObject.keyframe("hide", 0, frame, 0.0F);
            }

            layerObjects.put(layer, layerObject);
         }
      }

      this.chunks.clear();
   }

   private DObject buildChunkObject(RenderChunk renderChunk) {
      BlockPos pos = renderChunk.getPosition();
      DObject chunkObject = new DObject(DObject.Type.OB_EMPTY);
      chunkObject.setParent(this.chunksObject);
      chunkObject.id.name = "Chunk[" + pos.getX() / 16 + "/" + pos.getY() / 16 + "/" + pos.getZ() / 16 + "]";
      chunkObject.loc = new Vector3f(pos.getX(), -pos.getZ(), pos.getY());
      return chunkObject;
   }

   private DObject buildChunkLayerObject(DObject chunkObject, RenderChunk renderChunk, EnumWorldBlockLayer layer) {
      VertexBuffer vertexBuffer = renderChunk.getVertexBufferByLayer(layer.ordinal());
      if (vertexBuffer == null) {
         return null;
      }

      DObject layerObject = new DObject(this.buildChunkLayerMesh(vertexBuffer));
      layerObject.setParent(chunkObject);
      layerObject.id.name = chunkObject.id.name + " - " + layer + " - Frame " + this.frame;
      return layerObject;
   }

   private DMesh buildChunkLayerMesh(VertexBuffer vertexBuffer) {
      vertexBuffer.bindBuffer();
      int size = GL15.glGetBufferParameteri(OpenGlHelper.GL_ARRAY_BUFFER, 34660);
      ByteBuffer byteBuffer = GLAllocation.createDirectByteBuffer(size);
      GL15.glGetBufferSubData(OpenGlHelper.GL_ARRAY_BUFFER, 0L, byteBuffer);
      vertexBuffer.unbindBuffer();
      MCVer.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
      return BlendMeshBuilder.addBufferToMesh(byteBuffer, 7, DefaultVertexFormats.BLOCK, null, null);
   }
}
