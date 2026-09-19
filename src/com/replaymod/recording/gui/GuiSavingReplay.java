package com.replaymod.recording.gui;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTextField;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTooltip;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiProgressBar;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Focusable;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Utils;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.org.apache.commons.lang3.tuple.Pair;
import com.replaymod.recording.Setting;
import com.replaymod.replay.gui.screen.GuiReplayViewer;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GuiSavingReplay {
   private static final Minecraft mc = MCVer.getMinecraft();
   private static final Logger logger = LogManager.getLogger();
   private final GuiLabel label = new GuiLabel().setI18nText("replaymod.gui.replaysaving.title").setColor(Colors.BLACK);
   private final GuiProgressBar progressBar = new GuiProgressBar().setHeight(14);
   private final GuiPanel panel = new GuiPanel()
      .setLayout(new VerticalLayout().setSpacing(2))
      .addElements(new VerticalLayout.Data(0.5), this.label, this.progressBar);
   private final ReplayMod core;
   private final List<Runnable> apply = new ArrayList<>();

   public GuiSavingReplay(ReplayMod core) {
      this.core = core;
   }

   public void open() {
      this.core.getBackgroundProcesses().addProcess(this.panel);
   }

   public void close() {
      this.core.getBackgroundProcesses().removeProcess(this.panel);
      AbstractGuiScreen<?> currentScreen = GuiScreen.from(mc.currentScreen);
      if (currentScreen instanceof GuiReplayViewer) {
         ((GuiReplayViewer)currentScreen).list.load();
      }
   }

   public GuiProgressBar getProgressBar() {
      return this.progressBar;
   }

   public void presentRenameDialog(List<Pair<Path, ReplayMetaData>> outputPaths) {
      this.panel.removeElement(this.progressBar);
      Utils.link(outputPaths.stream().map(it -> this.addOutput(it.getKey(), it.getValue())).toArray(Focusable[]::new));
      GuiButton applyButton = new GuiButton().setSize(150, 20).setI18nLabel("replaymod.gui.done").onClick(this::apply);
      this.panel.addElements(new VerticalLayout.Data(0.5), applyButton);
      if (!this.core.getSettingsRegistry().get(Setting.RENAME_DIALOG)) {
         this.apply();
      }
   }

   private GuiTextField addOutput(Path path, ReplayMetaData metaData) {
      String originalName = com.replaymod.core.utils.Utils.fileNameToReplayName(path.getFileName().toString());
      GuiTextField textField = new GuiTextField()
         .setSize(130, 20)
         .setText(originalName)
         .setI18nHint("replaymod.gui.delete")
         .setTextColorDisabled(Colors.RED)
         .onEnter(this::apply)
         .setTooltip(this.createTooltip(path, metaData));
      GuiButton clearButton = new GuiButton().setSize(20, 20).setLabel("X").setTooltip(new GuiTooltip().setI18nText("replaymod.gui.delete")).onClick(() -> {
         GuiTextField var10000 = textField.setText("");
      });
      GuiPanel row = new GuiPanel().setLayout(new HorizontalLayout()).addElements(null, textField, clearButton);
      this.panel.addElements(new VerticalLayout.Data(0.5), row);
      this.apply.add(() -> this.applyOutput(path, textField.getText()));
      return textField;
   }

   private GuiPanel createTooltip(Path path, ReplayMetaData metaData) {
      final GuiTooltip tooltip = new GuiTooltip();
      final GuiReplayViewer.GuiReplayEntry entry = new GuiReplayViewer.GuiReplayEntry(path.toFile(), metaData, null, new ArrayList<>());
      return new GuiPanel().setLayout(new CustomLayout<GuiPanel>() {
         protected void layout(GuiPanel container, int width, int height) {
            this.pos(entry, 4, 4);
            this.size(entry, width - 8, height - 8);
            this.size(tooltip, width, height);
         }

         @Override
         public ReadableDimension calcMinSize(GuiContainer<?> container) {
            ReadableDimension size = entry.calcMinSize();
            return new Dimension(size.getWidth() + 8, size.getHeight() + 8);
         }
      }).addElements(null, tooltip, entry);
   }

   private void apply() {
      this.apply.forEach(Runnable::run);
      this.close();
   }

   private void applyOutput(Path path, String newName) {
      if (newName.isEmpty()) {
         try {
            Files.delete(path);
         } catch (IOException e) {
            logger.error("Deleting replay file:", e);
            CrashReport crashReport = CrashReport.makeCrashReport(e, "Deleting replay file");
            this.core.runLater(() -> com.replaymod.core.utils.Utils.error(logger, VanillaGuiScreen.wrap(mc.currentScreen), crashReport, () -> {}));
         }
      } else {
         try {
            Path replaysFolder = this.core.folders.getReplayFolder();
            Path newPath = com.replaymod.core.utils.Utils.replayNameToPath(replaysFolder, newName);

            for (int i = 1; Files.exists(newPath); i++) {
               newPath = com.replaymod.core.utils.Utils.replayNameToPath(replaysFolder, newName + " (" + i + ")");
            }

            Files.move(path, newPath);
         } catch (IOException e) {
            logger.error("Renaming replay file:", e);
            CrashReport crashReport = CrashReport.makeCrashReport(e, "Renaming replay file");
            this.core.runLater(() -> com.replaymod.core.utils.Utils.error(logger, VanillaGuiScreen.wrap(mc.currentScreen), crashReport, () -> {}));
         }
      }
   }
}
