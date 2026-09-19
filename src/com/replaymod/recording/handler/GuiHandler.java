package com.replaymod.recording.handler;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.SettingsRegistry;
import com.replaymod.core.gui.GuiReplayButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiCheckbox;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiToggleButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.GuiInfoPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.InitScreenCallback;
import com.replaymod.recording.ServerInfoExt;
import com.replaymod.recording.Setting;
import com.replaymod.recording.mixin.AddServerScreenAccessor;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenAddServer;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;

public class GuiHandler extends EventRegistrations {
   private final ReplayMod mod;

   public GuiHandler(ReplayMod mod) {
      this.on(InitScreenCallback.EVENT, (screen, buttons) -> this.onGuiInit(screen));
      this.mod = mod;
   }

   private void onGuiInit(GuiScreen gui) {
      if (gui instanceof GuiSelectWorld || gui instanceof GuiMultiplayer) {
         boolean sp = gui instanceof GuiSelectWorld;
         SettingsRegistry settingsRegistry = this.mod.getSettingsRegistry();
         Setting<Boolean> setting = sp ? Setting.RECORD_SINGLEPLAYER : Setting.RECORD_SERVER;
         final GuiCheckbox recordingCheckbox = new GuiCheckbox()
            .setI18nLabel("replaymod.gui.settings.record" + (sp ? "singleplayer" : "server"))
            .setChecked(settingsRegistry.get(setting));
         recordingCheckbox.onClick(() -> {
            settingsRegistry.set(setting, recordingCheckbox.isChecked());
            settingsRegistry.save();
         });
         VanillaGuiScreen vanillaGui = VanillaGuiScreen.wrap(gui);
         vanillaGui.setLayout(new CustomLayout<com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen>(vanillaGui.getLayout()) {
            protected void layout(com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen container, int width, int height) {
               this.pos(recordingCheckbox, width - this.width(recordingCheckbox) - 5, 5);
            }
         }).addElements(null, recordingCheckbox);
      }

      if (gui instanceof GuiScreenAddServer) {
         VanillaGuiScreen vanillaGui = VanillaGuiScreen.wrap(gui);
         final GuiButton replayButton = new GuiReplayButton()
            .onClick(
               () -> {
                  ServerData serverInfo = ((AddServerScreenAccessor)gui).getServer();
                  ServerInfoExt serverInfoExt = ServerInfoExt.from(serverInfo);
                  Boolean state = serverInfoExt.getAutoRecording();
                  GuiToggleButton<String> autoRecording = new GuiToggleButton<String>().setI18nLabel("replaymod.gui.settings.autostartrecording")
                     .setValues(
                        I18n.format("replaymod.gui.settings.default", new Object[0]),
                        I18n.format("options.off", new Object[0]),
                        I18n.format("options.on", new Object[0])
                     )
                     .setSelected(state == null ? 0 : (state ? 2 : 1));
                  autoRecording.onClick(() -> {
                     int selected = autoRecording.getSelected();
                     serverInfoExt.setAutoRecording(selected == 0 ? null : selected == 2);
                  });
                  GuiInfoPopup.open(vanillaGui, autoRecording);
               }
            );
         vanillaGui.setLayout(new CustomLayout<com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen>(vanillaGui.getLayout()) {
            protected void layout(com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen container, int width, int height) {
               this.size(replayButton, 20, 20);
               this.pos(replayButton, width - this.width(replayButton) - 5, 5);
            }
         }).addElements(null, replayButton);
      }
   }
}
