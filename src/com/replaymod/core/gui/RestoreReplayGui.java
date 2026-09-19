package com.replaymod.core.gui;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.Utils;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiProgressBar;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.replaystudio.io.ReplayInputStream;
import com.replaymod.replaystudio.io.ReplayOutputStream;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;
import net.minecraft.crash.CrashReport;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RestoreReplayGui extends AbstractGuiScreen<RestoreReplayGui> {
   private static Logger LOGGER = LogManager.getLogger();
   public final GuiScreen parent;
   public final File file;
   public final GuiPanel textPanel = new GuiPanel().setLayout(new VerticalLayout().setSpacing(3));
   public final GuiPanel buttonPanel = new GuiPanel().setLayout(new HorizontalLayout().setSpacing(5));
   public final GuiPanel contentPanel = new GuiPanel(this)
      .addElements(new VerticalLayout.Data(0.5), this.textPanel, this.buttonPanel)
      .setLayout(new VerticalLayout().setSpacing(20));
   public final GuiButton yesButton = new GuiButton(this.buttonPanel).setSize(150, 20).setI18nLabel("gui.yes");
   public final GuiButton noButton = new GuiButton(this.buttonPanel).setSize(150, 20).setI18nLabel("gui.no");
   private final ReplayMod core;

   public RestoreReplayGui(ReplayMod core, GuiScreen parent, File file) {
      this.core = core;
      this.parent = parent;
      this.file = file;
      this.textPanel
         .addElements(
            new VerticalLayout.Data(0.5),
            new GuiLabel().setI18nText("replaymod.gui.restorereplay1"),
            new GuiLabel().setI18nText("replaymod.gui.restorereplay2", Files.getNameWithoutExtension(file.getName())),
            new GuiLabel().setI18nText("replaymod.gui.restorereplay3")
         );
      LOGGER.info("Found partially saved replay, offering recovery: " + file);
      this.yesButton.onClick(() -> {
         LOGGER.info("Attempting recovery: " + file);
         this.recoverInBackground();
         parent.display();
      });
      this.noButton.onClick(() -> {
         LOGGER.info("Recovery rejected, marking for deletion: " + file);

         try {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            File deleted = new File(file.getParentFile(), file.getName() + ".del");
            if (deleted.exists()) {
               FileUtils.deleteDirectory(deleted);
            }

            Files.move(tmp, deleted);
         } catch (IOException e) {
            e.printStackTrace();
         }

         parent.display();
      });
      this.setLayout(
         new CustomLayout<RestoreReplayGui>() {
            protected void layout(RestoreReplayGui container, int width, int height) {
               this.pos(
                  RestoreReplayGui.this.contentPanel,
                  width / 2 - this.width(RestoreReplayGui.this.contentPanel) / 2,
                  height / 2 - this.height(RestoreReplayGui.this.contentPanel) / 2
               );
            }
         }
      );
   }

   protected RestoreReplayGui getThis() {
      return this;
   }

   private void recoverInBackground() {
      GuiLabel label = new GuiLabel().setI18nText("replaymod.gui.replaysaving.title").setColor(Colors.BLACK);
      GuiProgressBar progressBar = new GuiProgressBar().setHeight(14);
      GuiPanel savingProcess = new GuiPanel().setLayout(new VerticalLayout()).addElements(new VerticalLayout.Data(0.5), label, progressBar);
      new Thread(() -> {
         this.core.runLater(() -> this.core.getBackgroundProcesses().addProcess(savingProcess));

         try {
            this.tryRecover(progressBar::setProgress);
         } catch (IOException e) {
            LOGGER.error("Recovering replay file:", e);
            CrashReport crashReport = CrashReport.makeCrashReport(e, "Recovering replay file");
            this.core.runLater(() -> Utils.error(LOGGER, VanillaGuiScreen.wrap(this.getMinecraft().currentScreen), crashReport, () -> {}));
         } finally {
            this.core.runLater(() -> this.core.getBackgroundProcesses().removeProcess(savingProcess));
         }
      }).start();
   }

   private void tryRecover(Consumer<Float> progress) throws IOException {
      ReplayFile replayFile = ReplayMod.instance.files.open(this.file.toPath());
      replayFile.save();
      progress.accept(0.4F);
      ReplayMetaData metaData = replayFile.getMetaData();
      if (metaData != null && metaData.getDuration() == 0) {
         try (
            ReplayInputStream in = replayFile.getPacketData(MCVer.getPacketTypeRegistry(State.LOGIN));
            ReplayOutputStream out = replayFile.writePacketData();
         ) {
            while (true) {
               int time = com.replaymod.replaystudio.util.Utils.readInt(in);
               int length = com.replaymod.replaystudio.util.Utils.readInt(in);
               if (time == -1 || length == -1) {
                  break;
               }

               byte[] buf = new byte[length];
               IOUtils.readFully(in, buf);
               metaData.setDuration(time);
               com.replaymod.replaystudio.util.Utils.writeInt(out, time);
               com.replaymod.replaystudio.util.Utils.writeInt(out, length);
               out.write(buf);
            }
         } catch (Throwable t) {
            t.printStackTrace();
         }

         try (OutputStream out = replayFile.write("metaData.json")) {
            metaData.setGenerator(metaData.getGenerator() + "(+ ReplayMod Replay Recovery)");
            String json = new Gson().toJson(metaData);
            out.write(json.getBytes());
         }
      }

      progress.accept(0.6F);
      replayFile.save();
      progress.accept(0.9F);
      replayFile.close();
      progress.accept(1.0F);
   }
}
