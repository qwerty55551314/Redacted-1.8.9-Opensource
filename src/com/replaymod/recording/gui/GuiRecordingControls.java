package com.replaymod.recording.gui;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.Utils;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.InitScreenCallback;
import com.replaymod.recording.packet.PacketListener;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;

public class GuiRecordingControls extends EventRegistrations {
   private ReplayMod core;
   private PacketListener packetListener;
   private boolean paused;
   private boolean stopped;
   private GuiPanel panel = new GuiPanel().setLayout(new HorizontalLayout().setSpacing(4));
   private com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton buttonPauseResume = new com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton(
         this.panel
      )
      .onClick(() -> {
         if (!Utils.ifMinimalModeDoPopup(this.panel, () -> {})) {
            this.togglePause();
         }
      })
      .setSize(98, 20);
   private com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton buttonStartStop = new com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton(
         this.panel
      )
      .onClick(() -> {
         if (!Utils.ifMinimalModeDoPopup(this.panel, () -> {})) {
            if (this.stopped) {
               this.startRecording();
            } else {
               this.stopRecording();
            }
         }
      })
      .setSize(98, 20);

   public GuiRecordingControls(ReplayMod core, PacketListener packetListener, boolean autoStart) {
      this.on(InitScreenCallback.EVENT, this::injectIntoIngameMenu);
      this.core = core;
      this.packetListener = packetListener;
      this.paused = this.stopped = !autoStart;
      this.updateState();
   }

   /** Start recording now (shared by the pause-menu button, the P keybind and the Start Recording keybind). */
   public void startRecording() {
      if (this.stopped) {
         this.paused = false;
         this.packetListener.addMarker("_RM_END_CUT");
         this.core.printInfoToChat("replaymod.chat.recordingstarted");
         this.stopped = false;
         this.updateState();
      }
   }

   /** Stop recording now (shared by the pause-menu button and the Stop Recording keybind). */
   public void stopRecording() {
      if (!this.stopped) {
         int timestamp = (int)this.packetListener.getCurrentDuration();
         if (!this.paused) {
            this.packetListener.addMarker("_RM_START_CUT", timestamp);
         }

         this.packetListener.addMarker("_RM_SPLIT", timestamp + 1);
         this.stopped = true;
         this.updateState();
      }
   }

   private void updateState() {
      this.buttonPauseResume.setI18nLabel("replaymod.gui.recording." + (this.paused ? "resume" : "pause"));
      this.buttonStartStop.setI18nLabel("replaymod.gui.recording." + (this.stopped ? "start" : "stop"));
      this.buttonPauseResume.setEnabled(!this.stopped);
   }

   private void injectIntoIngameMenu(GuiScreen guiScreen, Collection<GuiButton> buttonList) {
      if (guiScreen instanceof GuiIngameMenu) {
         if (!buttonList.isEmpty()) {
            final Function<Integer, Integer> yPos = MCVer.findButton(buttonList, "menu.returnToMenu", 1)
               .map(Optional::of)
               .orElse(MCVer.findButton(buttonList, "menu.disconnect", 1))
               .<Function<Integer, Integer>>map(it -> height -> it.yPosition)
               .orElse(height -> height / 4 + 120 - 16);
            VanillaGuiScreen vanillaGui = VanillaGuiScreen.wrap(guiScreen);
            vanillaGui.setLayout(new CustomLayout<com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen>(vanillaGui.getLayout()) {
               protected void layout(com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen container, int width, int height) {
                  this.pos(GuiRecordingControls.this.panel, width / 2 - 100, yPos.apply(height) + 16 + 8);
               }
            }).addElements(null, this.panel);
         }
      }
   }

   public boolean isPaused() {
      return this.paused;
   }

   /**
    * Pause/resume the recording: marks a cut point in the stream and updates
    * the indicator + chat feedback. Shared by the pause-menu button and the
    * P (play/pause) keybind.
    */
   public void togglePause() {
      if (this.paused) {
         this.packetListener.addMarker("_RM_END_CUT");
         this.core.printInfoToChat("replaymod.chat.recordingresumed");
      } else {
         this.packetListener.addMarker("_RM_START_CUT");
         this.core.printInfoToChat("replaymod.chat.recordingpaused");
      }

      this.paused = !this.paused;
      this.updateState();
   }

   public boolean isStopped() {
      return this.stopped;
   }
}
