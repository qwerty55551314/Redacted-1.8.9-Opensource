package com.replaymod.extras.youtube;

import com.replaymod.core.ReplayMod;
import com.replaymod.extras.Extra;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.InitScreenCallback;
import com.replaymod.render.gui.GuiRenderingDone;
import net.minecraft.client.gui.GuiScreen;

public class YoutubeUpload extends EventRegistrations implements Extra {
   public YoutubeUpload() {
      this.on(InitScreenCallback.EVENT, (screen, buttons) -> this.onGuiOpen(screen));
   }

   @Override
   public void register(ReplayMod mod) {
      this.register();
   }

   private void onGuiOpen(GuiScreen vanillaGui) {
      AbstractGuiScreen<?> abstractScreen = com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen.from(vanillaGui);
      if (abstractScreen instanceof GuiRenderingDone) {
         GuiRenderingDone gui = (GuiRenderingDone)abstractScreen;
         if (gui.actionsPanel.getChildren().stream().anyMatch(it -> it instanceof YoutubeUpload.YoutubeButton)) {
            return;
         }

         gui.actionsPanel
            .addElements(
               null,
               new YoutubeUpload.YoutubeButton()
                  .onClick(() -> new GuiYoutubeUpload(gui, gui.videoFile, gui.videoFrames, gui.settings).display())
                  .setSize(200, 20)
                  .setI18nLabel("replaymod.gui.youtubeupload")
            );
      }
   }

   private static class YoutubeButton extends GuiButton {
      private YoutubeButton() {
      }
   }
}
