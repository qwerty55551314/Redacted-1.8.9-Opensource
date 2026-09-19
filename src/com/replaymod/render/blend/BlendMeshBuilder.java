package com.replaymod.render.blend;

import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.ReadableVector3f;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector2f;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector3f;
import com.replaymod.render.blend.data.DMaterial;
import com.replaymod.render.blend.data.DMesh;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.client.renderer.vertex.VertexFormatElement.EnumType;

public class BlendMeshBuilder extends WorldRenderer {
   private static final ReadableVector3f VEC3F_ZERO = new Vector3f(0.0F, 0.0F, 0.0F);
   private final DMesh mesh;
   private Vector3f offset;
   private boolean isDrawing;
   private boolean wellBehaved;

   public BlendMeshBuilder(DMesh mesh) {
      super(1024);
      this.mesh = mesh;
   }

   public void setWellBehaved(boolean wellBehaved) {
      this.wellBehaved = wellBehaved;
   }

   public void setReverseOffset(Vector3f offset) {
      this.offset = offset;
   }

   public void begin(int mode, VertexFormat vertexFormat) {
      if (this.isDrawing) {
         if (this.wellBehaved) {
            throw new IllegalStateException("Already drawing!");
         }

         super.finishDrawing();
         this.addBufferToMesh();
      }

      this.isDrawing = true;
      if (!this.wellBehaved) {
         Tessellator.getInstance().getWorldRenderer().begin(mode, DefaultVertexFormats.POSITION_TEX_COLOR);
      }

      super.begin(mode, vertexFormat);
   }

   public void maybeFinishDrawing() {
      if (this.isDrawing) {
         this.isDrawing = false;
         super.finishDrawing();
         this.addBufferToMesh();
      }
   }

   public void finishDrawing() {
      if (!this.isDrawing) {
         throw new IllegalStateException("Not building!");
      }

      if (!this.wellBehaved) {
         Tessellator.getInstance().getWorldRenderer().finishDrawing();
      }

      super.finishDrawing();
      this.addBufferToMesh();
   }

   private void addBufferToMesh() {
      addBufferToMesh(this, this.mesh, this.offset);
   }

   public static DMesh addBufferToMesh(WorldRenderer bufferBuilder, DMesh mesh, Vector3f vertOffset) {
      return addBufferToMesh(bufferBuilder.getByteBuffer(), bufferBuilder.getDrawMode(), bufferBuilder.getVertexFormat(), mesh, vertOffset);
   }

   public static DMesh addBufferToMesh(ByteBuffer buffer, int mode, VertexFormat vertexFormat, DMesh mesh, ReadableVector3f vertOffset) {
      int vertexCount = buffer.remaining() / vertexFormat.getNextOffset();
      return addBufferToMesh(buffer, vertexCount, mode, vertexFormat, mesh, vertOffset);
   }

   public static DMesh addBufferToMesh(ByteBuffer buffer, int vertexCount, int mode, VertexFormat vertexFormat, DMesh mesh, ReadableVector3f vertOffset) {
      if (mesh == null) {
         mesh = new DMesh();
      }

      if (vertOffset == null) {
         vertOffset = VEC3F_ZERO;
      }

      int posOffset = -1;
      int colorOffset = -1;
      int uvOffset = -1;
      int index = 0;

      for (VertexFormatElement element : MCVer.getElements(vertexFormat)) {
         int offset = vertexFormat.getOffset(index);
         switch (element.getUsage()) {
            case POSITION:
               if (element.getType() != EnumType.FLOAT) {
                  throw new UnsupportedOperationException("Only float is supported for position elements!");
               }

               posOffset = offset;
               break;
            case COLOR:
               if (element.getType() != EnumType.UBYTE) {
                  throw new UnsupportedOperationException("Only unsigned byte is supported for color elements!");
               }

               colorOffset = offset;
               break;
            case UV:
               if (element.getIndex() == 0) {
                  if (element.getType() != EnumType.UBYTE) {
                     throw new UnsupportedOperationException("Only float is supported for UV elements!");
                  }

                  uvOffset = offset;
               }
         }

         index++;
      }

      if (posOffset == -1) {
         throw new IllegalStateException("No position element in " + vertexFormat);
      }

      List<DMesh.Vertex> vertices = new ArrayList<>(vertexCount);
      List<Vector2f> uvs = new ArrayList<>(vertexCount);
      List<Integer> colors = new ArrayList<>(vertexCount);
      int step = vertexFormat.getNextOffset();

      for (int offset = 0; offset < vertexCount * step; offset += step) {
         vertices.add(
            new DMesh.Vertex(
               buffer.getFloat(offset) - vertOffset.getX(), -buffer.getFloat(offset + 8) + vertOffset.getZ(), buffer.getFloat(offset + 4) - vertOffset.getY()
            )
         );
         if (colorOffset != -1) {
            colors.add(buffer.getInt(offset + colorOffset));
         } else {
            colors.add(-1);
         }

         if (uvOffset != -1) {
            uvs.add(new Vector2f(buffer.getFloat(offset + uvOffset), 1.0F - buffer.getFloat(offset + uvOffset + 4)));
         } else {
            uvs.add(new Vector2f(0.0F, 0.0F));
         }
      }

      DMaterial activeMaterial = BlendState.getState().getMaterials().getActiveMaterial();
      int materialSlot = mesh.materials.indexOf(activeMaterial);
      if (materialSlot < 0) {
         materialSlot = mesh.materials.size();
         mesh.materials.add(activeMaterial);
      }

      switch (mode) {
         case 4:
            for (int i = 0; i < vertices.size(); i += 3) {
               mesh.addTriangle(
                  vertices.get(i),
                  vertices.get(i + 1),
                  vertices.get(i + 2),
                  uvs.get(i),
                  uvs.get(i + 1),
                  uvs.get(i + 2),
                  colors.get(i),
                  colors.get(i + 1),
                  colors.get(i + 2),
                  materialSlot
               );
            }
            break;
         case 7:
            for (int i = 0; i < vertices.size(); i += 4) {
               mesh.addQuad(
                  vertices.get(i),
                  vertices.get(i + 1),
                  vertices.get(i + 2),
                  vertices.get(i + 3),
                  uvs.get(i),
                  uvs.get(i + 1),
                  uvs.get(i + 2),
                  uvs.get(i + 3),
                  colors.get(i),
                  colors.get(i + 1),
                  colors.get(i + 2),
                  colors.get(i + 3),
                  materialSlot
               );
            }
            break;
         default:
            throw new UnsupportedOperationException("Unsupported mode: " + mode);
      }

      return mesh;
   }
}
