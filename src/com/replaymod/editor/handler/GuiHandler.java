package com.replaymod.editor.handler;

import com.replaymod.core.utils.Utils;
import com.replaymod.editor.ReplayModEditor;
import com.replaymod.editor.gui.GuiEditReplay;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.InitScreenCallback;
import com.replaymod.replay.gui.screen.GuiReplayViewer;
import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.crash.CrashReport;

public class GuiHandler extends EventRegistrations {
   public GuiHandler() {
      this.on(InitScreenCallback.EVENT, (vanillaGuiScreen, buttonList) -> this.injectIntoReplayViewer(vanillaGuiScreen));
   }

   public void injectIntoReplayViewer(GuiScreen vanillaGuiScreen) {
      AbstractGuiScreen guiScreen = com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen.from(vanillaGuiScreen);
      if (guiScreen instanceof GuiReplayViewer) {
         GuiReplayViewer replayViewer = (GuiReplayViewer)guiScreen;
         if (replayViewer.editorButton.getChildren().isEmpty()) {
            replayViewer.replaySpecificButtons.add(new GuiButton(replayViewer.editorButton).onClick(() -> {
               if (!Utils.ifMinimalModeDoPopup(replayViewer, () -> {})) {
                  try {
                     (new GuiEditReplay(replayViewer, replayViewer.list.getSelected().get(0).file.toPath()) {
                        @Override
                        protected void close() {
                           super.close();
                           replayViewer.list.load();
                        }
                     }).open();
                  } catch (IOException e) {
                     Utils.error(ReplayModEditor.LOGGER, replayViewer, CrashReport.makeCrashReport(e, "Opening replay editor"), () -> {});
                  }
               }
            }).setSize(73, 20).setI18nLabel("replaymod.gui.edit").setDisabled());
         }
      }
   }
}
