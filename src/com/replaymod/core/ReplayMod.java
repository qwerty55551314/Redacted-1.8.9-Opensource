package com.replaymod.core;

import com.replaymod.compat.ReplayModCompat;
import com.replaymod.core.files.ReplayFilesService;
import com.replaymod.core.files.ReplayFoldersService;
import com.replaymod.core.gui.GuiBackgroundProcesses;
import com.replaymod.core.gui.GuiReplaySettings;
import com.replaymod.core.versions.scheduler.Scheduler;
import com.replaymod.core.versions.scheduler.SchedulerImpl;
import com.replaymod.editor.ReplayModEditor;
import com.replaymod.extras.ReplayModExtras;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import com.replaymod.recording.ReplayModRecording;
import com.replaymod.render.ReplayModRender;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.studio.ReplayStudio;
import com.replaymod.replaystudio.util.I18n;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.settings.GameSettings.Options;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

public class ReplayMod implements Module, Scheduler {
   public static final String MOD_ID = "replaymod";
   public static final ResourceLocation TEXTURE = MCVer.identifier("replaymod", "replay_gui.png");
   public static final int TEXTURE_SIZE = 256;
   public static final ResourceLocation LOGO_FAVICON = MCVer.identifier("replaymod", "favicon_logo.png");
   private static final Minecraft mc = com.replaymod.core.versions.MCVer.getMinecraft();
   private final ReplayModBackend backend;
   private final SchedulerImpl scheduler = new SchedulerImpl();
   private final KeyBindingRegistry keyBindingRegistry = new KeyBindingRegistry();
   private final SettingsRegistry settingsRegistry = new SettingsRegistry();
   public static ReplayMod instance;
   private final List<Module> modules;
   private final GuiBackgroundProcesses backgroundProcesses;
   public final ReplayFoldersService folders;
   public final ReplayFilesService files;
   private boolean minimalMode;
   public static final FolderResourcePack jGuiResourcePack = createJGuiResourcePack();
   public static final String JGUI_RESOURCE_PACK_NAME = "replaymod_jgui";

   public ReplayMod(ReplayModBackend backend) {
      this.settingsRegistry.register(Setting.class);
      instance = this;
      this.modules = new ArrayList<>();
      this.backgroundProcesses = new GuiBackgroundProcesses();
      this.folders = new ReplayFoldersService(this.settingsRegistry);
      this.files = new ReplayFilesService(this.folders);
      this.backend = backend;
      I18n.setI18n(net.minecraft.client.resources.I18n::format);
      if (!ProtocolVersion.isRegistered(com.replaymod.core.versions.MCVer.getProtocolVersion())
         && !Boolean.parseBoolean(System.getProperty("replaymod.skipversioncheck", "false"))) {
         this.minimalMode = true;
      }

      this.modules.add(this);
      this.modules.add(new ReplayModRecording(this));
      ReplayModReplay replayModule = new ReplayModReplay(this);
      this.modules.add(replayModule);
      this.modules.add(new ReplayModRender(this));
      this.modules.add(new ReplayModSimplePathing(this));
      this.modules.add(new ReplayModEditor(this));
      this.modules.add(new ReplayModExtras(this));
      this.modules.add(new ReplayModCompat());
      this.settingsRegistry.register();
   }

   public KeyBindingRegistry getKeyBindingRegistry() {
      return this.keyBindingRegistry;
   }

   public SettingsRegistry getSettingsRegistry() {
      return this.settingsRegistry;
   }

   private static FolderResourcePack createJGuiResourcePack() {
      File folder = new File("../jGui/src/main/resources");
      if (!folder.exists()) {
         folder = new File("../../../jGui/src/main/resources");
         if (!folder.exists()) {
            return null;
         }
      }

      return new FolderResourcePack(folder) {
         public String getPackName() {
            return "replaymod_jgui";
         }

         protected InputStream getInputStreamByName(String resourceName) throws IOException {
            try {
               return super.getInputStreamByName(resourceName);
            } catch (IOException e) {
               if ("pack.mcmeta".equals(resourceName)) {
                  return new ByteArrayInputStream(this.generatePackMeta());
               } else {
                  throw e;
               }
            }
         }

         private byte[] generatePackMeta() {
            int version = 1;
            return ("{\"pack\": {\"description\": \"dummy pack for jGui resources in dev-env\", \"pack_format\": " + version + "}}")
               .getBytes(StandardCharsets.UTF_8);
         }
      };
   }

   void initModules() {
      this.modules.forEach(Module::initCommon);
      this.modules.forEach(Module::initClient);
      this.modules.forEach(m -> m.registerKeyBindings(this.keyBindingRegistry));
   }

   @Override
   public void registerKeyBindings(KeyBindingRegistry registry) {
      registry.registerKeyBinding("replaymod.input.settings", 0, () -> new GuiReplaySettings(null, this.settingsRegistry).display(), false);
   }

   @Override
   public void initClient() {
      this.backgroundProcesses.register();
      this.keyBindingRegistry.register();
      if (!com.replaymod.core.versions.MCVer.hasOptifine()) {
         Options.RENDER_DISTANCE.setValueMax(64.0F);
      }

      this.runPostStartup(() -> this.files.initialScan(this));
   }

   @Override
   public void runSync(Runnable runnable) throws InterruptedException, ExecutionException, TimeoutException {
      this.scheduler.runSync(runnable);
   }

   @Override
   public void runPostStartup(Runnable runnable) {
      this.scheduler.runPostStartup(runnable);
   }

   @Override
   public void runLaterWithoutLock(Runnable runnable) {
      this.scheduler.runLaterWithoutLock(runnable);
   }

   @Override
   public void runLater(Runnable runnable) {
      this.scheduler.runLater(runnable);
   }

   @Override
   public void runTasks() {
      this.scheduler.runTasks();
   }

   public String getVersion() {
      return this.backend.getVersion();
   }

   public String getMinecraftVersion() {
      return this.backend.getMinecraftVersion();
   }

   public boolean isModLoaded(String id) {
      return this.backend.isModLoaded(id);
   }

   public Minecraft getMinecraft() {
      return mc;
   }

   public void printInfoToChat(String message, Object... args) {
      this.printToChat(false, message, args);
   }

   public void printWarningToChat(String message, Object... args) {
      this.printToChat(true, message, args);
   }

   private void printToChat(boolean warning, String message, Object... args) {
      if (!mc.isCallingFromMinecraftThread()) {
         this.runLater(() -> this.printToChat(warning, message, args));
      } else {
         if (this.getSettingsRegistry().get(Setting.NOTIFICATIONS)) {
            ChatStyle coloredDarkGray = new ChatStyle().setColor(EnumChatFormatting.DARK_GRAY);
            ChatStyle coloredGold = new ChatStyle().setColor(EnumChatFormatting.GOLD);
            IChatComponent text = new ChatComponentText("[")
               .setChatStyle(coloredDarkGray)
               .appendSibling(new ChatComponentTranslation("replaymod.title", new Object[0]).setChatStyle(coloredGold))
               .appendSibling(new ChatComponentText("] "))
               .appendSibling(
                  new ChatComponentTranslation(message, args)
                     .setChatStyle(new ChatStyle().setColor(warning ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GREEN))
               );
            mc.ingameGUI.getChatGUI().printChatMessage(text);
         }
      }
   }

   public GuiBackgroundProcesses getBackgroundProcesses() {
      return this.backgroundProcesses;
   }

   public static boolean isMinimalMode() {
      return instance.minimalMode;
   }

   public static boolean isCompatible(int fileFormatVersion, int protocolVersion) {
      return isMinimalMode()
         ? protocolVersion == com.replaymod.core.versions.MCVer.getProtocolVersion()
         : new ReplayStudio().isCompatible(fileFormatVersion, protocolVersion, com.replaymod.core.versions.MCVer.getProtocolVersion());
   }
}
