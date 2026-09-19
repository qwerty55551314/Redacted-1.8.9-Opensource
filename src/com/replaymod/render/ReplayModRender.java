package com.replaymod.render;

import com.replaymod.core.Module;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.Utils;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.render.utils.RenderJob;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.events.ReplayClosedCallback;
import com.replaymod.replay.events.ReplayOpenedCallback;
import com.replaymod.replaystudio.replay.ReplayFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ReportedException;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReplayModRender extends EventRegistrations implements Module {
   public static ReplayModRender instance;
   private ReplayMod core;
   public static Logger LOGGER = LogManager.getLogger();
   private ReplayFile replayFile;
   private final List<RenderJob> renderQueue;

   public ReplayModRender(ReplayMod core) {
      instance = this;
      this.renderQueue = new ArrayList<>();
      this.on(ReplayOpenedCallback.EVENT, this::onReplayOpened);
      this.on(ReplayClosedCallback.EVENT, replayHandler -> this.onReplayClosed());
      this.core = core;
      core.getSettingsRegistry().register(Setting.class);
   }

   public ReplayMod getCore() {
      return this.core;
   }

   @Override
   public void initClient() {
      this.register();
   }

   public File getVideoFolder() {
      String path = this.core.getSettingsRegistry().get(Setting.RENDER_PATH);
      File folder = new File(path.startsWith("./") ? this.core.getMinecraft().mcDataDir : null, path);

      try {
         FileUtils.forceMkdir(folder);
         return folder;
      } catch (IOException e) {
         throw new ReportedException(CrashReport.makeCrashReport(e, "Cannot create video folder."));
      }
   }

   public Path getRenderSettingsPath() {
      return this.core.getMinecraft().mcDataDir.toPath().resolve("config/replaymod-rendersettings.json");
   }

   public List<RenderJob> getRenderQueue() {
      return this.renderQueue;
   }

   private void onReplayOpened(ReplayHandler replayHandler) {
      this.replayFile = replayHandler.getReplayFile();

      try {
         this.renderQueue.addAll(RenderJob.readQueue(this.replayFile));
      } catch (IOException e) {
         throw new ReportedException(CrashReport.makeCrashReport(e, "Reading timeline"));
      }
   }

   private void onReplayClosed() {
      this.renderQueue.clear();
      this.replayFile = null;
   }

   public void saveRenderQueue() {
      try {
         RenderJob.writeQueue(this.replayFile, this.renderQueue);
      } catch (IOException e) {
         e.printStackTrace();
         VanillaGuiScreen screen = VanillaGuiScreen.wrap(this.getCore().getMinecraft().currentScreen);
         CrashReport report = CrashReport.makeCrashReport(e, "Reading timeline");
         Utils.error(LOGGER, screen, report, () -> {});
      }
   }
}
