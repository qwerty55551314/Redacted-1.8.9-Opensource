package com.replaymod.render.blend.data;

import com.replaymod.lib.org.blender.dna.Image;
import com.replaymod.lib.org.blender.dna.Tex;
import com.replaymod.lib.org.cakelab.blender.io.block.BlockCodes;
import com.replaymod.lib.org.cakelab.blender.nio.CPointer;
import java.io.IOException;

public class DTexture {
   public final DId id = new DId(BlockCodes.ID_TE);
   public DImage image;

   public CPointer<Tex> serialize(Serializer serializer) throws IOException {
      return serializer.maybeMajor(this, this.id, Tex.class, () -> {
         CPointer<Image> image = this.image.serialize(serializer);
         return tex -> {
            tex.setIma(image);
            tex.setType((short)8);
            tex.setImaflag((short)2);
            tex.setCropxmax(1.0F);
            tex.setCropymax(1.0F);
            tex.setRfac(1.0F);
            tex.setGfac(1.0F);
            tex.setBfac(1.0F);
            tex.setBright(1.0F);
            tex.setContrast(1.0F);
            tex.setSaturation(1.0F);
         };
      });
   }
}
