package com.replaymod.recording.packet;

import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.ListenableFuture;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Consumer;
import com.replaymod.replaystudio.replay.ReplayFile;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerData.ServerResourceMode;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C19PacketResourcePackStatus;
import net.minecraft.network.play.client.C19PacketResourcePackStatus.Action;
import net.minecraft.network.play.server.S48PacketResourcePackSend;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ResourcePackRecorder {
   private static final Logger logger = LogManager.getLogger();
   private static final Minecraft mc = MCVer.getMinecraft();
   private final ReplayFile replayFile;
   private int nextRequestId;

   public ResourcePackRecorder(ReplayFile replayFile) {
      this.replayFile = replayFile;
   }

   public void recordResourcePack(Path file, int requestId) {
      try {
         byte[] bytes = Files.readAllBytes(file);
         String hash = Hashing.sha1().hashBytes(bytes).toString();
         boolean doWrite = false;
         synchronized (this.replayFile) {
            Map<Integer, String> index = this.replayFile.getResourcePackIndex();
            if (index == null) {
               index = new HashMap<>();
            }

            if (!index.containsValue(hash)) {
               doWrite = true;
            }

            index.put(requestId, hash);
            this.replayFile.writeResourcePackIndex(index);
         }

         if (doWrite) {
            try (OutputStream out = this.replayFile.writeResourcePack(hash)) {
               out.write(bytes);
            }
         }
      } catch (IOException e) {
         logger.warn("Failed to save resource pack.", e);
      }
   }

   public C19PacketResourcePackStatus makeStatusPacket(String hash, Action action) {
      return new C19PacketResourcePackStatus(hash, action);
   }

   public synchronized S48PacketResourcePackSend handleResourcePack(NetworkManager netManager, S48PacketResourcePackSend packet) {
      int requestId = this.nextRequestId++;
      String url = packet.getURL();
      String hash = packet.getHash();
      if (url.startsWith("level://")) {
         String levelName = url.substring("level://".length());
         File savesDir = new File(mc.mcDataDir, "saves");
         File levelDir = new File(savesDir, levelName);
         if (levelDir.isFile()) {
            netManager.sendPacket(this.makeStatusPacket(hash, Action.ACCEPTED));
            MCVer.addCallback((ListenableFuture<Object>)MCVer.setServerResourcePack(levelDir), result -> {
               this.recordResourcePack(levelDir.toPath(), requestId);
               netManager.sendPacket(this.makeStatusPacket(hash, Action.SUCCESSFULLY_LOADED));
            }, throwable -> netManager.sendPacket(this.makeStatusPacket(hash, Action.FAILED_DOWNLOAD)));
         } else {
            netManager.sendPacket(this.makeStatusPacket(hash, Action.FAILED_DOWNLOAD));
         }
      } else {
         ServerData serverData = mc.getCurrentServerData();
         if (serverData != null && serverData.getResourceMode() == ServerResourceMode.ENABLED) {
            netManager.sendPacket(this.makeStatusPacket(hash, Action.ACCEPTED));
            this.downloadResourcePackFuture(netManager, requestId, url, hash);
         } else if (serverData != null && serverData.getResourceMode() != ServerResourceMode.PROMPT) {
            netManager.sendPacket(this.makeStatusPacket(hash, Action.DECLINED));
         } else {
            mc.addScheduledTask(() -> mc.displayGuiScreen(new GuiYesNo(new GuiYesNoCallback() {
               public void confirmClicked(boolean result, int id) {
                  if (serverData != null) {
                     serverData.setResourceMode(result ? ServerResourceMode.ENABLED : ServerResourceMode.DISABLED);
                  }

                  if (result) {
                     netManager.sendPacket(ResourcePackRecorder.this.makeStatusPacket(hash, Action.ACCEPTED));
                     ResourcePackRecorder.this.downloadResourcePackFuture(netManager, requestId, url, hash);
                  } else {
                     netManager.sendPacket(ResourcePackRecorder.this.makeStatusPacket(hash, Action.DECLINED));
                  }

                  ServerList.func_147414_b(serverData);
                  ResourcePackRecorder.mc.displayGuiScreen(null);
               }
            }, I18n.format("multiplayer.texturePrompt.line1", new Object[0]), I18n.format("multiplayer.texturePrompt.line2", new Object[0]), 0)));
         }
      }

      return new S48PacketResourcePackSend("replay://" + requestId, "");
   }

   private void downloadResourcePackFuture(NetworkManager connection, int requestId, String url, String hash) {
      MCVer.addCallback(
         (ListenableFuture<Object>)this.downloadResourcePack(requestId, url, hash),
         result -> connection.sendPacket(this.makeStatusPacket(hash, Action.SUCCESSFULLY_LOADED)),
         throwable -> connection.sendPacket(this.makeStatusPacket(hash, Action.FAILED_DOWNLOAD))
      );
   }

   private ListenableFuture<?> downloadResourcePack(int requestId, String url, String hash) {
      ResourcePackRepository packFinder = mc.getResourcePackRepository();
      ((ResourcePackRecorder.IDownloadingPackFinder)packFinder).setRequestCallback(file -> this.recordResourcePack(file.toPath(), requestId));
      return packFinder.downloadResourcePack(url, hash);
   }

   public interface IDownloadingPackFinder {
      void setRequestCallback(Consumer<File> var1);
   }
}
