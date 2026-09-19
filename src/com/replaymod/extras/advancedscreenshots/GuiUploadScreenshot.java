package com.replaymod.extras.advancedscreenshots;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.SettingsRegistry;
import com.replaymod.core.versions.MCVer;
import com.replaymod.extras.Setting;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiCheckbox;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;
import com.replaymod.render.RenderSettings;
import java.net.URI;

public class GuiUploadScreenshot extends AbstractGuiPopup<GuiUploadScreenshot> {
   public final ReplayMod mod;
   public final RenderSettings renderSettings;
   public final GuiLabel successLabel = new GuiLabel().setI18nText("replaymod.gui.advancedscreenshots.finished.description").setColor(ReadableColor.BLACK);
   public final GuiLabel veerLabel = new GuiLabel().setI18nText("replaymod.gui.advancedscreenshots.finished.description.veer").setColor(ReadableColor.BLACK);
   public final GuiButton veerUploadButton = new GuiButton().setSize(150, 20).setI18nLabel("replaymod.gui.advancedscreenshots.finished.upload.veer");
   public final GuiButton showOnDiskButton = new GuiButton().setSize(150, 20).setI18nLabel("replaymod.gui.advancedscreenshots.finished.showfile");
   public final GuiButton closeButton = new GuiButton().setSize(150, 20).setI18nLabel("replaymod.gui.close");
   public final GuiCheckbox neverOpenCheckbox = new GuiCheckbox();
   public final GuiLabel neverOpenLabel = new GuiLabel().setI18nText("replaymod.gui.notagain").setColor(ReadableColor.BLACK);
   public final GuiPanel checkboxPanel = GuiPanel.builder()
      .layout(new HorizontalLayout(HorizontalLayout.Alignment.RIGHT).setSpacing(5))
      .with(this.neverOpenCheckbox, new HorizontalLayout.Data(0.5))
      .with(this.neverOpenLabel, new HorizontalLayout.Data(0.5))
      .build();

   public GuiUploadScreenshot(GuiContainer container, ReplayMod mod, RenderSettings renderSettings) {
      super(container);
      this.mod = mod;
      this.renderSettings = renderSettings;
      boolean veer = renderSettings.getRenderMethod() == RenderSettings.RenderMethod.EQUIRECTANGULAR;
      if (renderSettings.getRenderMethod() == RenderSettings.RenderMethod.EQUIRECTANGULAR) {
         this.successLabel.setI18nText("replaymod.gui.advancedscreenshots.finished.description.360");
      }

      if (veer) {
         this.veerUploadButton.onClick(() -> MCVer.openURL(URI.create("https://veer.tv/upload")));
      }

      this.showOnDiskButton.onClick(() -> MCVer.openFile(renderSettings.getOutputFile().getParentFile()));
      this.closeButton.onClick(() -> {
         if (this.neverOpenCheckbox.isChecked()) {
            SettingsRegistry settingsRegistry = mod.getSettingsRegistry();
            settingsRegistry.set(Setting.SKIP_POST_SCREENSHOT_GUI, true);
            settingsRegistry.save();
         }

         this.close();
      });
      this.popup.addElements(new VerticalLayout.Data(0.5), this.successLabel);
      if (veer) {
         this.popup.addElements(new VerticalLayout.Data(0.5), this.veerLabel, this.veerUploadButton);
      }

      this.popup.addElements(new VerticalLayout.Data(0.5), this.successLabel, this.showOnDiskButton, this.closeButton);
      this.popup.addElements(new VerticalLayout.Data(1.0), this.checkboxPanel);
      this.popup.setLayout(new VerticalLayout().setSpacing(5));
   }

   @Override
   protected void open() {
      if (!this.mod.getSettingsRegistry().get(Setting.SKIP_POST_SCREENSHOT_GUI)) {
         super.open();
      }
   }

   protected GuiUploadScreenshot getThis() {
      return this;
   }
}
