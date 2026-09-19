package com.replaymod.recording.handler;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.ModCompat;
import com.replaymod.core.utils.Utils;
import com.replaymod.core.versions.MCVer;
import com.replaymod.recording.ServerInfoExt;
import com.replaymod.recording.Setting;
import com.replaymod.recording.gui.GuiRecordingControls;
import com.replaymod.recording.gui.GuiRecordingOverlay;
import com.replaymod.recording.mixin.NetworkManagerAccessor;
import com.replaymod.recording.packet.PacketListener;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import io.netty.channel.Channel;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.network.NetworkManager;
import net.minecraft.world.WorldType;
import org.apache.logging.log4j.Logger;

public class ConnectionEventHandler {
   private static final String DATE_FORMAT = "yyyy_MM_dd_HH_mm_ss";
   private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
   private static final Minecraft mc = MCVer.getMinecraft();
   private final Logger logger;
   private final ReplayMod core;
   private RecordingEventHandler recordingEventHandler;
   private PacketListener packetListener;
   private GuiRecordingOverlay guiOverlay;
   private GuiRecordingControls guiControls;

   public ConnectionEventHandler(Logger logger, ReplayMod core) {
      this.logger = logger;
      this.core = core;
   }

   public GuiRecordingOverlay getGuiOverlay() {
      return this.guiOverlay;
   }

   public GuiRecordingControls getGuiControls() {
      return this.guiControls;
   }

   public void onConnectedToServerEvent(NetworkManager networkManager) {
      try {
         boolean local = networkManager.isLocalChannel();
         if (local) {
            if (mc.getIntegratedServer().getEntityWorld().getWorldType() == WorldType.DEBUG_WORLD) {
               this.logger.info("Debug World recording is not supported.");
               return;
            }

            if (!this.core.getSettingsRegistry().get(Setting.RECORD_SINGLEPLAYER)) {
               this.logger.info("Singleplayer Recording is disabled");
               return;
            }
         } else if (!this.core.getSettingsRegistry().get(Setting.RECORD_SERVER)) {
            this.logger.info("Multiplayer Recording is disabled");
            return;
         }

         ServerData serverInfo = mc.getCurrentServerData();
         String serverName = null;
         boolean autoStart = this.core.getSettingsRegistry().get(Setting.AUTO_START_RECORDING);
         String worldName;
         if (local) {
            worldName = mc.getIntegratedServer().getFolderName();
            serverName = worldName;
         } else {
            if (serverInfo == null) {
               this.logger.info("Recording not started as the world is neither local nor remote (probably a replay).");
               return;
            }

            worldName = serverInfo.serverIP;
            if (!I18n.format("selectServer.defaultName", new Object[0]).equals(serverInfo.serverName)) {
               serverName = serverInfo.serverName;
            }

            Boolean autoStartServer = ServerInfoExt.from(serverInfo).getAutoRecording();
            if (autoStartServer != null) {
               autoStart = autoStartServer;
            }
         }

         if (ReplayMod.isMinimalMode()) {
            autoStart = true;
         }

         String name = sdf.format(Calendar.getInstance().getTime());
         Path outputPath = Utils.replayNameToPath(this.core.folders.getRecordingFolder(), name);
         ReplayFile replayFile = this.core.files.open(outputPath);
         replayFile.writeModInfo(ModCompat.getInstalledNetworkMods());
         ReplayMetaData metaData = new ReplayMetaData();
         metaData.setSingleplayer(local);
         metaData.setServerName(worldName);
         metaData.setCustomServerName(serverName);
         metaData.setGenerator("ReplayMod v" + ReplayMod.instance.getVersion());
         metaData.setDate(System.currentTimeMillis());
         metaData.setMcVersion(ReplayMod.instance.getMinecraftVersion());
         Channel channel = ((NetworkManagerAccessor)networkManager).getChannel();
         this.packetListener = new PacketListener(this.core, channel, outputPath, replayFile, metaData);
         if (channel.pipeline().get("decoder") != null) {
            channel.pipeline().addBefore("decoder", "replay_recorder_raw", this.packetListener);
            channel.pipeline().addAfter("decoder", "replay_recorder_decoded", this.packetListener.new DecodedPacketListener());
         } else {
            channel.pipeline().addFirst("replay_recorder_raw", this.packetListener);
            channel.pipeline().addAfter("replay_recorder_raw", "replay_recorder_decoded", this.packetListener.new DecodedPacketListener());
         }

         this.recordingEventHandler = new RecordingEventHandler(this.packetListener);
         this.recordingEventHandler.register();
         this.guiControls = new GuiRecordingControls(this.core, this.packetListener, autoStart);
         this.guiControls.register();
         this.guiOverlay = new GuiRecordingOverlay(mc, this.core.getSettingsRegistry(), this.guiControls);
         this.guiOverlay.register();
         if (autoStart) {
            this.core.printInfoToChat("replaymod.chat.recordingstarted");
         } else {
            this.packetListener.addMarker("_RM_START_CUT", 0);
            this.core.printInfoToChat("replaymod.chat.recordingoff");
         }
      } catch (Throwable e) {
         e.printStackTrace();
         this.core.printWarningToChat("replaymod.chat.recordingfailed");
      }
   }

   public void reset() {
      if (this.packetListener != null) {
         this.guiControls.unregister();
         this.guiControls = null;
         this.guiOverlay.unregister();
         this.guiOverlay = null;
         this.recordingEventHandler.unregister();
         this.recordingEventHandler = null;
         this.packetListener = null;
      }
   }

   public RecordingEventHandler getRecordingEventHandler() {
      return this.recordingEventHandler;
   }

   public PacketListener getPacketListener() {
      return this.packetListener;
   }
}
