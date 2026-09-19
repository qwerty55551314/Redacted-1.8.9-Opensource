package com.replaymod.render.blend.data;

import com.replaymod.lib.org.blender.dna.MTex;
import com.replaymod.lib.org.blender.dna.Material;
import com.replaymod.lib.org.blender.dna.Tex;
import com.replaymod.lib.org.cakelab.blender.io.block.BlockCodes;
import com.replaymod.lib.org.cakelab.blender.nio.CArrayFacade;
import com.replaymod.lib.org.cakelab.blender.nio.CPointer;
import com.replaymod.render.blend.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DMaterial {
   public final DId id = new DId(BlockCodes.ID_MA);
   public List<DMaterial.DMTex> textures = new ArrayList<>();

   public CPointer<Material> serialize(Serializer serializer) throws IOException {
      return serializer.maybeMajor(this, this.id, Material.class, () -> {
         List<Util.IOCallable<CPointer<MTex>>> textures = new ArrayList<>();

         for (DMaterial.DMTex texture : this.textures) {
            textures.add(texture.serialize(serializer));
         }

         return material -> {
            material.setMode(65728);
            material.setAlpha(1.0F);
            material.setRef(1.0F);
            material.setR(1.0F);
            material.setG(1.0F);
            material.setB(1.0F);
            CArrayFacade<CPointer<MTex>> mTexPointers = material.getMtex();

            for (int i = 0; i < textures.size(); i++) {
               mTexPointers.set(i, textures.get(i).call());
            }
         };
      });
   }

   public static class DMTex {
      public DTexture texture;

      public Util.IOCallable<CPointer<MTex>> serialize(Serializer serializer) throws IOException {
         CPointer<Tex> texture = this.texture.serialize(serializer);
         return () -> {
            MTex mTex = serializer.writeData(MTex.class);
            mTex.setTex(texture);
            mTex.getUvname().fromString("UVMap");
            mTex.setMapto((short)129);
            mTex.setBlendtype((short)1);
            mTex.setColfac(1.0F);
            mTex.setAlphafac(1.0F);
            mTex.setDef_var(1.0F);
            mTex.setTexco((short)16);
            mTex.setProjx((byte)1);
            mTex.setProjy((byte)2);
            mTex.setProjz((byte)3);
            CArrayFacade<Float> size = mTex.getSize();
            size.set(0, 1.0F);
            size.set(1, 1.0F);
            size.set(2, 1.0F);
            return mTex.__io__addressof();
         };
      }
   }
}
