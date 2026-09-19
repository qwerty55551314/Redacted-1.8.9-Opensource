package com.replaymod.render.blend.data;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector2f;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector3f;
import com.replaymod.lib.org.blender.dna.CustomData;
import com.replaymod.lib.org.blender.dna.CustomDataLayer;
import com.replaymod.lib.org.blender.dna.Image;
import com.replaymod.lib.org.blender.dna.MEdge;
import com.replaymod.lib.org.blender.dna.MFace;
import com.replaymod.lib.org.blender.dna.MLoop;
import com.replaymod.lib.org.blender.dna.MLoopCol;
import com.replaymod.lib.org.blender.dna.MLoopUV;
import com.replaymod.lib.org.blender.dna.MPoly;
import com.replaymod.lib.org.blender.dna.MTexPoly;
import com.replaymod.lib.org.blender.dna.MVert;
import com.replaymod.lib.org.blender.dna.Material;
import com.replaymod.lib.org.blender.dna.Mesh;
import com.replaymod.lib.org.cakelab.blender.io.block.BlockCodes;
import com.replaymod.lib.org.cakelab.blender.nio.CArrayFacade;
import com.replaymod.lib.org.cakelab.blender.nio.CPointer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DMesh {
   public final DId id = new DId(BlockCodes.ID_ME);
   public final List<DMesh.Vertex> vertices = new ArrayList<>();
   public final List<DMesh.Edge> edges = new ArrayList<>();
   public final List<DMesh.Face> faces = new ArrayList<>();
   public final List<DMesh.Loop> loops = new ArrayList<>();
   public final List<DMesh.Poly> polys = new ArrayList<>();
   public final List<DMaterial> materials = new ArrayList<>();

   public CPointer<Mesh> serialize(Serializer serializer) throws IOException {
      return serializer.maybeMajor(
         this,
         this.id,
         Mesh.class,
         () -> {
            List<CPointer<Material>> materials = new ArrayList<>();
            List<CPointer<Image>> images = new ArrayList<>();

            for (DMaterial material : this.materials) {
               materials.add(material.serialize(serializer));
               images.add(material.textures.isEmpty() ? null : material.textures.get(0).texture.image.serialize(serializer));
            }

            return mesh -> {
               mesh.setDrawflag(3);
               CArrayFacade<CPointer<Material>> mat = serializer.writeDataPArray(Material.class, materials.size(), materials::get);
               mesh.setMat(mat);
               mesh.setTotcol((short)materials.size());
               if (!this.vertices.isEmpty()) {
                  CArrayFacade<MVert> mVerts = serializer.writeData(MVert.class, this.vertices.size(), (ix, mVert) -> this.vertices.get(ix).serialize(mVert));
                  CustomDataLayer vDataLayer = serializer.writeData(CustomDataLayer.class);
                  vDataLayer.setType(0);
                  vDataLayer.setData(mVerts.cast(Object.class));
                  CustomData vData = mesh.getVdata();
                  vData.setMaxlayer(1);
                  vData.setTotlayer(1);
                  vData.setLayers(vDataLayer.__io__addressof());
                  mesh.setMvert(mVerts);
                  mesh.setTotvert(this.vertices.size());
               }

               if (!this.edges.isEmpty()) {
                  CArrayFacade<MEdge> mEdges = serializer.writeData(MEdge.class, this.edges.size(), (ix, mEdge) -> this.edges.get(ix).serialize(mEdge));
                  CustomDataLayer eDataLayer = serializer.writeData(CustomDataLayer.class);
                  eDataLayer.setType(3);
                  eDataLayer.setData(mEdges.cast(Object.class));
                  CustomData eData = mesh.getEdata();
                  eData.setMaxlayer(1);
                  eData.setTotlayer(1);
                  eData.setLayers(eDataLayer.__io__addressof());
                  mesh.setMedge(mEdges);
                  mesh.setTotedge(this.edges.size());
                  mesh.setMface(serializer.writeData(MFace.class, this.faces.size(), (ix, mFace) -> this.faces.get(ix).serialize(mFace)));
                  mesh.setTotface(this.faces.size());
               }

               if (!this.loops.isEmpty()) {
                  CArrayFacade<MLoop> mLoops = serializer.writeData(MLoop.class, this.loops.size(), (ix, mLoop) -> this.loops.get(ix).serialize(mLoop));
                  CArrayFacade<MLoopUV> mLoopUVs = serializer.writeData(
                     MLoopUV.class, this.loops.size(), (ix, mLoopUV) -> this.loops.get(ix).serialize(mLoopUV)
                  );
                  CArrayFacade<MLoopCol> mLoopCols = serializer.writeData(
                     MLoopCol.class, this.loops.size(), (ix, mLoopCol) -> this.loops.get(ix).serialize(mLoopCol)
                  );
                  CArrayFacade<CustomDataLayer> dataLayers = serializer.writeData(CustomDataLayer.class, 3);
                  int i = 0;
                  CustomDataLayer luvDataLayer = dataLayers.get(i++);
                  luvDataLayer.setType(16);
                  luvDataLayer.setData(mLoopUVs.cast(Object.class));
                  CustomDataLayer lcolDataLayer = dataLayers.get(i++);
                  lcolDataLayer.setType(17);
                  lcolDataLayer.setData(mLoopCols.cast(Object.class));
                  CustomDataLayer lDataLayer = dataLayers.get(i++);
                  lDataLayer.setType(26);
                  lDataLayer.setData(mLoops.cast(Object.class));
                  CustomData lData = mesh.getLdata();
                  lData.setMaxlayer(i);
                  lData.setTotlayer(i);
                  lData.setLayers(dataLayers);
                  mesh.setMloop(mLoops);
                  mesh.setMloopuv(mLoopUVs);
                  mesh.setTotloop(this.loops.size());
               }

               if (!this.polys.isEmpty()) {
                  CArrayFacade<MPoly> mPolys = serializer.writeData(MPoly.class, this.polys.size(), (ix, mPoly) -> this.polys.get(ix).serialize(mPoly));
                  CArrayFacade<MTexPoly> mTexPolys = serializer.writeData(
                     MTexPoly.class, this.polys.size(), (ix, mTexPoly) -> mTexPoly.setTpage(images.get(this.polys.get(ix).materialSlot))
                  );
                  CArrayFacade<CustomDataLayer> dataLayers = serializer.writeData(CustomDataLayer.class, mTexPolys != null ? 2 : 1);
                  int i = 0;
                  if (mTexPolys != null) {
                     CustomDataLayer pTexDataLayer = dataLayers.get(i++);
                     pTexDataLayer.getName().fromString("UVMap");
                     pTexDataLayer.setType(15);
                     pTexDataLayer.setData(mTexPolys.cast(Object.class));
                  }

                  CustomDataLayer pDataLayer = dataLayers.get(i++);
                  pDataLayer.setType(25);
                  pDataLayer.setData(mPolys.cast(Object.class));
                  CustomData pData = mesh.getPdata();
                  pData.setMaxlayer(i);
                  pData.setTotlayer(i);
                  pData.setLayers(dataLayers);
                  mesh.setMpoly(mPolys);
                  mesh.setMtpoly(mTexPolys);
                  mesh.setTotpoly(this.polys.size());
               }
            };
         }
      );
   }

   public void addTriangle(
      DMesh.Vertex v1, DMesh.Vertex v2, DMesh.Vertex v3, Vector2f uv1, Vector2f uv2, Vector2f uv3, int c1, int c2, int c3, int materialSlot
   ) {
      int vOffset = this.vertices.size();
      int eOffset = this.edges.size();
      int lOffset = this.loops.size();
      this.vertices.add(v1);
      this.vertices.add(v2);
      this.vertices.add(v3);
      this.edges.add(new DMesh.Edge(vOffset, vOffset + 1));
      this.edges.add(new DMesh.Edge(vOffset + 1, vOffset + 2));
      this.edges.add(new DMesh.Edge(vOffset + 2, vOffset));
      this.loops.add(new DMesh.Loop(vOffset, eOffset, uv1.x, uv1.y, c1));
      this.loops.add(new DMesh.Loop(vOffset + 1, eOffset + 1, uv2.x, uv2.y, c2));
      this.loops.add(new DMesh.Loop(vOffset + 2, eOffset + 2, uv3.x, uv3.y, c3));
      this.polys.add(new DMesh.Poly(lOffset, 3, materialSlot));
   }

   public void addQuad(
      DMesh.Vertex v1,
      DMesh.Vertex v2,
      DMesh.Vertex v3,
      DMesh.Vertex v4,
      Vector2f uv1,
      Vector2f uv2,
      Vector2f uv3,
      Vector2f uv4,
      int c1,
      int c2,
      int c3,
      int c4,
      int materialSlot
   ) {
      int vOffset = this.vertices.size();
      int eOffset = this.edges.size();
      int lOffset = this.loops.size();
      this.vertices.add(v1);
      this.vertices.add(v2);
      this.vertices.add(v3);
      this.vertices.add(v4);
      this.edges.add(new DMesh.Edge(vOffset, vOffset + 1));
      this.edges.add(new DMesh.Edge(vOffset + 1, vOffset + 2));
      this.edges.add(new DMesh.Edge(vOffset + 2, vOffset + 3));
      this.edges.add(new DMesh.Edge(vOffset + 3, vOffset));
      this.loops.add(new DMesh.Loop(vOffset, eOffset, uv1.x, uv1.y, c1));
      this.loops.add(new DMesh.Loop(vOffset + 1, eOffset + 1, uv2.x, uv2.y, c2));
      this.loops.add(new DMesh.Loop(vOffset + 2, eOffset + 2, uv3.x, uv3.y, c3));
      this.loops.add(new DMesh.Loop(vOffset + 3, eOffset + 3, uv4.x, uv4.y, c4));
      this.polys.add(new DMesh.Poly(lOffset, 4, materialSlot));
   }

   public float getSizeX() {
      float minX = Float.POSITIVE_INFINITY;
      float maxX = Float.NEGATIVE_INFINITY;

      for (DMesh.Vertex vertex : this.vertices) {
         if (vertex.pos.x < minX) {
            minX = vertex.pos.x;
         }

         if (vertex.pos.x > maxX) {
            maxX = vertex.pos.x;
         }
      }

      return maxX - minX;
   }

   public boolean hasZeroLengthEdge(float delta) {
      float deltaSquared = delta * delta;
      Vector3f dst = new Vector3f();

      for (DMesh.Edge edge : this.edges) {
         DMesh.Vertex v1 = this.vertices.get(edge.v1);
         DMesh.Vertex v2 = this.vertices.get(edge.v2);
         if (Vector3f.sub(v1.pos, v2.pos, dst).lengthSquared() < deltaSquared) {
            return true;
         }
      }

      return false;
   }

   public static class Edge {
      public int v1;
      public int v2;

      public Edge(int v1, int v2) {
         this.v1 = v1;
         this.v2 = v2;
      }

      public void serialize(MEdge mEdge) throws IOException {
         mEdge.setV1(this.v1);
         mEdge.setV2(this.v2);
      }
   }

   public static class Face {
      public int v1;
      public int v2;
      public int v3;
      public int v4;

      public Face(int v1, int v2, int v3, int v4) {
         this.v1 = v1;
         this.v2 = v2;
         this.v3 = v3;
         this.v4 = v4;
      }

      public void serialize(MFace mFace) throws IOException {
         mFace.setV1(this.v1);
         mFace.setV2(this.v2);
         mFace.setV3(this.v3);
         mFace.setV4(this.v4);
      }
   }

   public static class Loop {
      public int vertex;
      public int edge;
      public float u;
      public float v;
      public int col;

      public Loop(int vertex, int edge, float u, float v, int col) {
         this.vertex = vertex;
         this.edge = edge;
         this.u = u;
         this.v = v;
         this.col = col;
      }

      public void serialize(MLoop mLoop) throws IOException {
         mLoop.setV(this.vertex);
         mLoop.setE(this.edge);
      }

      public void serialize(MLoopUV mLoop) throws IOException {
         CArrayFacade<Float> uv = mLoop.getUv();
         uv.set(0, this.u);
         uv.set(1, this.v);
      }

      public void serialize(MLoopCol mLoop) throws IOException {
         mLoop.setR((byte)(this.col & 0xFF));
         mLoop.setG((byte)(this.col >> 8 & 0xFF));
         mLoop.setB((byte)(this.col >> 16 & 0xFF));
         mLoop.setA((byte)(this.col >> 24 & 0xFF));
      }
   }

   public static class Poly {
      public int loopStart;
      public int size;
      public short materialSlot;

      public Poly(int loopStart, int size, int materialSlot) {
         this.loopStart = loopStart;
         this.size = size;
         this.materialSlot = (short)materialSlot;
      }

      public void serialize(MPoly mPoly) throws IOException {
         mPoly.setLoopstart(this.loopStart);
         mPoly.setTotloop(this.size);
         mPoly.setMat_nr(this.materialSlot);
      }
   }

   public static class Vertex {
      public Vector3f pos;
      public short normX;
      public short normY;
      public short normZ;

      public Vertex(float x, float y, float z) {
         this.pos = new Vector3f(x, y, z);
      }

      public void serialize(MVert mVert) throws IOException {
         CArrayFacade<Float> pos = mVert.getCo();
         pos.set(0, this.pos.getX());
         pos.set(1, this.pos.getY());
         pos.set(2, this.pos.getZ());
         CArrayFacade<Short> norm = mVert.getNo();
         norm.set(0, this.normX);
         norm.set(1, this.normY);
         norm.set(2, this.normZ);
      }
   }
}
