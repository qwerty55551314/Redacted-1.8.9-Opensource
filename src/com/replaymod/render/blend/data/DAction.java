package com.replaymod.render.blend.data;

import com.replaymod.lib.org.blender.dna.BezTriple;
import com.replaymod.lib.org.blender.dna.FCurve;
import com.replaymod.lib.org.blender.dna.bAction;
import com.replaymod.lib.org.cakelab.blender.io.block.BlockCodes;
import com.replaymod.lib.org.cakelab.blender.nio.CArrayFacade;
import com.replaymod.lib.org.cakelab.blender.nio.CPointer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DAction {
   public final DId id = new DId(BlockCodes.ID_AC);
   public List<DAction.DFCurve> curves = new ArrayList<>();

   public CPointer<bAction> serialize(Serializer serializer) throws IOException {
      return serializer.maybeMajor(
         this, this.id, bAction.class, () -> bAction -> serializer.writeDataList(FCurve.class, bAction.getCurves(), this.curves.size(), (i, fCurve) -> {
            DAction.DFCurve dfCurve = this.curves.get(i);
            fCurve.setRna_path(serializer.writeString0(dfCurve.rnaPath));
            fCurve.setArray_index(dfCurve.rnaArrayIndex);
            fCurve.setTotvert(dfCurve.keyframes.size());
            fCurve.setBezt(serializer.writeData(BezTriple.class, dfCurve.keyframes.size(), (j, fBezTriple) -> {
               DAction.DKeyframe dKeyframe = dfCurve.keyframes.get(j);
               fBezTriple.setIpo((byte)dKeyframe.interpolationType.ordinal());
               CArrayFacade<Float> vec = fBezTriple.getVec().get(1);
               vec.set(0, (float)dKeyframe.frame);
               vec.set(1, dKeyframe.value);
            }));
         })
      );
   }

   public static class DFCurve {
      public String rnaPath;
      public int rnaArrayIndex;
      public List<DAction.DKeyframe> keyframes = new ArrayList<>();
   }

   public static class DKeyframe {
      public DAction.InterpolationType interpolationType = DAction.InterpolationType.CONSTANT;
      public int frame;
      public float value;
   }

   public enum InterpolationType {
      CONSTANT,
      LINEAR;
   }
}
