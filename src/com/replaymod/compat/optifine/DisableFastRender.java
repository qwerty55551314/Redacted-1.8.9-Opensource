package com.replaymod.compat.optifine;

import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.render.events.ReplayRenderCallback;
import net.minecraft.client.Minecraft;

public class DisableFastRender extends EventRegistrations {
   private final Minecraft mc = Minecraft.getMinecraft();
   private boolean wasFastRender = false;

   public DisableFastRender() {
      this.on(ReplayRenderCallback.Pre.EVENT, renderer -> this.onRenderBegin());
      this.on(ReplayRenderCallback.Post.EVENT, renderer -> this.onRenderEnd());
   }

   private void onRenderBegin() {
      if (MCVer.hasOptifine()) {
         try {
            this.wasFastRender = (Boolean)OptifineReflection.gameSettings_ofFastRender.get(this.mc.gameSettings);
            OptifineReflection.gameSettings_ofFastRender.set(this.mc.gameSettings, false);
         } catch (IllegalAccessException e) {
            e.printStackTrace();
         }
      }
   }

   private void onRenderEnd() {
      if (MCVer.hasOptifine()) {
         try {
            OptifineReflection.gameSettings_ofFastRender.set(this.mc.gameSettings, this.wasFastRender);
         } catch (IllegalAccessException e) {
            e.printStackTrace();
         }
      }
   }
}
