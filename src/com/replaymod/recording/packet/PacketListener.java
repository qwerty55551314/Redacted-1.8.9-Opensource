package com.replaymod.recording.packet;

import com.google.gson.Gson;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.versions.MCVer;
import com.replaymod.editor.gui.MarkerProcessor;
import com.replaymod.lib.com.github.steveice10.netty.buffer.ByteBuf;
import com.replaymod.lib.com.github.steveice10.netty.buffer.PooledByteBufAllocator;
import com.replaymod.lib.com.github.steveice10.packetlib.tcp.io.ByteBufNetOutput;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import com.replaymod.lib.org.apache.commons.lang3.tuple.Pair;
import com.replaymod.recording.ReplayModRecording;
import com.replaymod.recording.Setting;
import com.replaymod.recording.gui.GuiSavingReplay;
import com.replaymod.recording.handler.ConnectionEventHandler;
import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.data.Marker;
import com.replaymod.replaystudio.io.ReplayOutputStream;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import com.replaymod.replaystudio.util.Utils;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.util.AttributeKey;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.login.server.S01PacketEncryptionRequest;
import net.minecraft.network.login.server.S03PacketEnableCompression;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.network.play.server.S46PacketSetCompressionLevel;
import net.minecraft.network.play.server.S48PacketResourcePackSend;
import net.minecraft.util.ChatComponentText;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Sharable
public class PacketListener extends ChannelInboundHandlerAdapter {
   public static final String RAW_RECORDER_KEY = "replay_recorder_raw";
   public static final String DECODED_RECORDER_KEY = "replay_recorder_decoded";
   public static final String DECOMPRESS_KEY = "decompress";
   public static final String DECODER_KEY = "decoder";
   private static final Minecraft mc = MCVer.getMinecraft();
   private static final Logger logger = LogManager.getLogger();
   private final ReplayMod core;
   private final Path outputPath;
   private final ReplayFile replayFile;
   private final ResourcePackRecorder resourcePackRecorder;
   private final ExecutorService saveService = Executors.newSingleThreadExecutor();
   private final ReplayOutputStream packetOutputStream;
   private ReplayMetaData metaData;
   private final Channel channel;
   private Packet currentRawPacket;
   private final long startTime;
   private long lastSentPacket;
   private long timePassedWhilePaused;
   private volatile boolean serverWasPaused;
   private final AtomicInteger lastSaveMetaDataId = new AtomicInteger();

   public PacketListener(ReplayMod core, Channel channel, Path outputPath, ReplayFile replayFile, ReplayMetaData metaData) throws IOException {
      this.core = core;
      this.channel = channel;
      this.outputPath = outputPath;
      this.replayFile = replayFile;
      this.metaData = metaData;
      this.resourcePackRecorder = new ResourcePackRecorder(replayFile);
      this.packetOutputStream = replayFile.writePacketData();
      this.startTime = metaData.getDate();
      this.saveMetaData();
   }

   private void saveMetaData() {
      int id = this.lastSaveMetaDataId.incrementAndGet();
      this.saveService.submit(() -> {
         if (this.lastSaveMetaDataId.get() == id) {
            try {
               synchronized (this.replayFile) {
                  if (ReplayMod.isMinimalMode()) {
                     this.metaData.setFileFormat("MCPR");
                     this.metaData.setFileFormatVersion(14);
                     this.metaData.setProtocolVersion(MCVer.getProtocolVersion());
                     this.metaData.setGenerator("ReplayMod in Minimal Mode");

                     try (OutputStream out = this.replayFile.write("metaData.json")) {
                        String json = new Gson().toJson(this.metaData);
                        out.write(json.getBytes());
                     }
                  } else {
                     this.replayFile.writeMetaData(MCVer.getPacketTypeRegistry(State.LOGIN), this.metaData);
                  }
               }
            } catch (IOException e) {
               logger.error("Writing metadata:", e);
            }
         }
      });
   }

   public void save(net.minecraft.network.Packet packet) {
      Packet encoded;
      try {
         encoded = this.encodeMcPacket(this.getConnectionState(), packet);
      } catch (Exception e) {
         logger.error("Encoding packet:", e);
         return;
      }

      this.save(encoded);
   }

   public void save(Packet packet) {
      if (!mc.isCallingFromMinecraftThread()) {
         mc.addScheduledTask(() -> this.save(packet));
      } else {
         try {
            long now = System.currentTimeMillis();
            if (this.serverWasPaused) {
               this.timePassedWhilePaused = now - this.startTime - this.lastSentPacket;
               this.serverWasPaused = false;
            }

            int timestamp = (int)(now - this.startTime - this.timePassedWhilePaused);
            this.lastSentPacket = timestamp;
            PacketData packetData = new PacketData(timestamp, packet);
            this.saveService.submit(() -> {
               try {
                  if (ReplayMod.isMinimalMode()) {
                     ByteBuf packetIdBuf = PooledByteBufAllocator.DEFAULT.buffer();
                     ByteBuf packetBuf = packetData.getPacket().getBuf();

                     try {
                        new ByteBufNetOutput(packetIdBuf).writeVarInt(packetData.getPacket().getId());
                        int packetIdLen = packetIdBuf.readableBytes();
                        int packetBufLen = packetBuf.readableBytes();
                        Utils.writeInt(this.packetOutputStream, (int)packetData.getTime());
                        Utils.writeInt(this.packetOutputStream, packetIdLen + packetBufLen);
                        packetIdBuf.readBytes(this.packetOutputStream, packetIdLen);
                        packetBuf.getBytes(packetBuf.readerIndex(), this.packetOutputStream, packetBufLen);
                     } finally {
                        packetIdBuf.release();
                        packetBuf.release();
                     }
                  } else {
                     this.packetOutputStream.write(packetData);
                  }
               } catch (IOException e) {
                  throw new RuntimeException(e);
               }
            });
         } catch (Exception e) {
            logger.error("Writing packet:", e);
         }
      }
   }

   public void channelInactive(ChannelHandlerContext ctx) {
      this.metaData.setDuration((int)this.lastSentPacket);
      this.saveMetaData();
      this.core.runLater(() -> {
         ConnectionEventHandler connectionEventHandler = ReplayModRecording.instance.getConnectionEventHandler();
         if (connectionEventHandler.getPacketListener() == this) {
            connectionEventHandler.reset();
         }
      });
      GuiSavingReplay guiSavingReplay = new GuiSavingReplay(this.core);
      new Thread(
            () -> {
               this.core.runLater(guiSavingReplay::open);
               this.saveService.shutdown();

               try {
                  this.saveService.awaitTermination(10L, TimeUnit.SECONDS);
               } catch (InterruptedException e) {
                  logger.error("Waiting for save service termination:", e);
               }

               try {
                  this.packetOutputStream.close();
               } catch (IOException e) {
                  logger.error("Failed to close packet output stream:", e);
               }

               List<Pair<Path, ReplayMetaData>> outputPaths;
               synchronized (this.replayFile) {
                  label57: {
                     try {
                        if (MarkerProcessor.producesAnyOutput(this.replayFile)) {
                           this.replayFile.save();
                           this.replayFile.close();
                           if (this.core.getSettingsRegistry().get(Setting.AUTO_POST_PROCESS) && !ReplayMod.isMinimalMode()) {
                              outputPaths = MarkerProcessor.apply(this.outputPath, guiSavingReplay.getProgressBar()::setProgress);
                              break label57;
                           }

                           outputPaths = Collections.singletonList(Pair.of(this.outputPath, this.metaData));
                           break label57;
                        }

                        this.core.runLater(guiSavingReplay::close);
                        Path noRecoverMarker = this.outputPath.resolveSibling(this.outputPath.getFileName() + ".no_recover");
                        Files.createFile(noRecoverMarker);
                        String replayName = FilenameUtils.getBaseName(this.outputPath.getFileName().toString());
                        Path rawFolder = ReplayMod.instance.folders.getRawReplayFolder();
                        Path rawPath = rawFolder.resolve(this.outputPath.getFileName());

                        for (int i = 1; Files.exists(rawPath); i++) {
                           rawPath = rawPath.resolveSibling(replayName + "." + i + ".mcpr");
                        }

                        this.replayFile.saveTo(rawPath.toFile());
                        this.replayFile.close();
                        Files.delete(noRecoverMarker);
                     } catch (Exception e) {
                        logger.error("Saving replay file:", e);
                        CrashReport crashReport = CrashReport.makeCrashReport(e, "Saving replay file");
                        this.core
                           .runLater(
                              () -> com.replaymod.core.utils.Utils.error(logger, VanillaGuiScreen.wrap(mc.currentScreen), crashReport, guiSavingReplay::close)
                           );
                        return;
                     }

                     return;
                  }
               }

               this.core.runLater(() -> guiSavingReplay.presentRenameDialog(outputPaths));
            }
         )
         .start();
   }

   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      EnumConnectionState connectionState = this.getConnectionState();
      Packet packet = null;
      if (msg instanceof io.netty.buffer.ByteBuf) {
         io.netty.buffer.ByteBuf buf = (io.netty.buffer.ByteBuf)msg;
         if (buf.readableBytes() > 0) {
            packet = decodePacket(connectionState, buf);
         }
      } else if (msg instanceof net.minecraft.network.Packet) {
         packet = this.encodeMcPacket(connectionState, (net.minecraft.network.Packet)msg);
      }

      this.currentRawPacket = packet;

      try {
         super.channelRead(ctx, msg);
      } finally {
         if (this.currentRawPacket != null) {
            this.currentRawPacket.release();
            this.currentRawPacket = null;
         }
      }
   }

   private EnumConnectionState getConnectionState() {
      AttributeKey<EnumConnectionState> key = NetworkManager.attrKeyConnectionState;
      return (EnumConnectionState)this.channel.attr(key).get();
   }

   private Packet encodeMcPacket(EnumConnectionState connectionState, net.minecraft.network.Packet packet) throws Exception {
      Integer packetId = connectionState.getPacketId(EnumPacketDirection.CLIENTBOUND, packet);
      if (packetId == null) {
         throw new IOException("Unknown packet type:" + packet.getClass());
      }

      io.netty.buffer.ByteBuf byteBuf = Unpooled.buffer();

      try {
         packet.writePacketData(new PacketBuffer(byteBuf));
         return new Packet(
            MCVer.getPacketTypeRegistry(connectionState),
            packetId,
            com.replaymod.lib.com.github.steveice10.netty.buffer.Unpooled.wrappedBuffer(byteBuf.array(), byteBuf.arrayOffset(), byteBuf.readableBytes())
         );
      } finally {
         byteBuf.release();
      }
   }

   private static Packet decodePacket(EnumConnectionState connectionState, io.netty.buffer.ByteBuf buf) {
      PacketBuffer packetBuf = new PacketBuffer(buf.slice());
      int packetId = packetBuf.readVarIntFromBuffer();
      byte[] bytes = new byte[packetBuf.readableBytes()];
      packetBuf.readBytes(bytes);
      return new Packet(
         MCVer.getPacketTypeRegistry(connectionState), packetId, com.replaymod.lib.com.github.steveice10.netty.buffer.Unpooled.wrappedBuffer(bytes)
      );
   }

   public void addMarker(String name) {
      this.addMarker(name, (int)this.getCurrentDuration());
   }

   public void addMarker(String name, int timestamp) {
      Entity view = mc.getRenderViewEntity();
      Marker marker = new Marker();
      marker.setName(name);
      marker.setTime(timestamp);
      if (view != null) {
         marker.setX(view.posX);
         marker.setY(view.posY);
         marker.setZ(view.posZ);
         marker.setYaw(view.rotationYaw);
         marker.setPitch(view.rotationPitch);
      }

      this.saveService.submit(() -> {
         synchronized (this.replayFile) {
            try {
               Set<Marker> markers = this.replayFile.getMarkers().or(HashSet::new);
               markers.add(marker);
               this.replayFile.writeMarkers(markers);
            } catch (IOException e) {
               logger.error("Writing markers:", e);
            }
         }
      });
   }

   public long getCurrentDuration() {
      return this.lastSentPacket;
   }

   public void setServerWasPaused() {
      this.serverWasPaused = true;
   }

   public ResourcePackRecorder getResourcePackRecorder() {
      return this.resourcePackRecorder;
   }

   public class DecodedPacketListener extends ChannelDuplexHandler {
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
         if (msg instanceof S01PacketEncryptionRequest) {
            super.channelRead(ctx, msg);
         } else if (msg instanceof S03PacketEnableCompression) {
            super.channelRead(ctx, msg);
         } else if (msg instanceof S46PacketSetCompressionLevel) {
            super.channelRead(ctx, msg);
         } else {
            if (msg instanceof S3FPacketCustomPayload) {
               S3FPacketCustomPayload packet = (S3FPacketCustomPayload)msg;
               if ("Replay|Restrict".equals(packet.getChannelName())) {
                  PacketListener.this.save(new S40PacketDisconnect(new ChatComponentText("Please update to view this replay.")));
               }
            }

            if (msg instanceof S0CPacketSpawnPlayer) {
               UUID uuid = ((S0CPacketSpawnPlayer)msg).getPlayer();
               Set<String> uuids = new HashSet<>(Arrays.asList(PacketListener.this.metaData.getPlayers()));
               uuids.add(uuid.toString());
               PacketListener.this.metaData.setPlayers(uuids.toArray(new String[uuids.size()]));
               PacketListener.this.saveMetaData();
            }

            if (msg instanceof S48PacketResourcePackSend) {
               NetworkManager connection = (NetworkManager)ctx.pipeline().get(NetworkManager.class);
               PacketListener.this.save(PacketListener.this.resourcePackRecorder.handleResourcePack(connection, (S48PacketResourcePackSend)msg));
            } else {
               if (PacketListener.this.currentRawPacket != null) {
                  PacketListener.this.save(PacketListener.this.currentRawPacket);
                  PacketListener.this.currentRawPacket = null;
               }

               super.channelRead(ctx, msg);
            }
         }
      }

      public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
         super.write(ctx, msg, promise);
      }
   }
}
