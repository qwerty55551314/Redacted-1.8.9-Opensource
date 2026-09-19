package com.replaymod.extras;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.events.PostRenderCallback;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiImage;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.IGuiImage;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.events.ReplayOpenedCallback;
import com.replaymod.replay.gui.overlay.GuiReplayOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

public class FullBrightness extends EventRegistrations implements Extra {
   private ReplayMod core;
   private ReplayModReplay module;
   private final IGuiImage indicator = new GuiImage().setTexture(ReplayMod.TEXTURE, 90, 20, 19, 16).setSize(19, 16);
   private Minecraft mc;
   private boolean active;
   private float originalGamma;

   public FullBrightness() {
      this.on(PreRenderCallback.EVENT, this::preRender);
      this.on(PostRenderCallback.EVENT, this::postRender);
      this.on(ReplayOpenedCallback.EVENT, replayHandler -> this.updateIndicator(replayHandler.getOverlay()));
   }

   @Override
   public void register(ReplayMod mod) throws Exception {
      this.core = mod;
      this.module = ReplayModReplay.instance;
      this.mc = mod.getMinecraft();
      mod.getKeyBindingRegistry().registerKeyBinding("replaymod.input.lighting", 44, new Runnable() {
         @Override
         public void run() {
            FullBrightness.this.active = !FullBrightness.this.active;
            mod.getMinecraft().entityRenderer.updateRenderer();
            ReplayHandler replayHandler = FullBrightness.this.module.getReplayHandler();
            if (replayHandler != null) {
               FullBrightness.this.updateIndicator(replayHandler.getOverlay());
            }
         }
      }, true);
      this.register();
   }

   public FullBrightness.Type getType() {
      String str = this.core.getSettingsRegistry().get(Setting.FULL_BRIGHTNESS);

      for (FullBrightness.Type type : FullBrightness.Type.values()) {
         if (type.toString().equals(str)) {
            return type;
         }
      }

      return FullBrightness.Type.Gamma;
   }

   private void preRender() {
      if (this.active && this.module.getReplayHandler() != null) {
         FullBrightness.Type type = this.getType();
         if (type == FullBrightness.Type.Gamma || type == FullBrightness.Type.Both) {
            this.originalGamma = this.mc.gameSettings.gammaSetting;
            this.mc.gameSettings.gammaSetting = 1000.0F;
         }

         if ((type == FullBrightness.Type.NightVision || type == FullBrightness.Type.Both) && this.mc.thePlayer != null) {
            this.mc.thePlayer.addPotionEffect(new PotionEffect(Potion.nightVision.id, Integer.MAX_VALUE));
         }
      }
   }

   private void postRender() {
      if (this.active && this.module.getReplayHandler() != null) {
         FullBrightness.Type type = this.getType();
         if (type == FullBrightness.Type.Gamma || type == FullBrightness.Type.Both) {
            this.mc.gameSettings.gammaSetting = this.originalGamma;
         }

         if ((type == FullBrightness.Type.NightVision || type == FullBrightness.Type.Both) && this.mc.thePlayer != null) {
            this.mc.thePlayer.removePotionEffect(Potion.nightVision.id);
         }
      }
   }

   private void updateIndicator(GuiReplayOverlay overlay) {
      if (this.active) {
         overlay.statusIndicatorPanel.addElements(new HorizontalLayout.Data(1.0), this.indicator);
      } else {
         overlay.statusIndicatorPanel.removeElement(this.indicator);
      }
   }

   enum Type {
      Gamma,
      NightVision,
      Both;

      @Override
      public String toString() {
         return "replaymod.gui.settings.fullbrightness." + this.name().toLowerCase();
      }
   }
}
