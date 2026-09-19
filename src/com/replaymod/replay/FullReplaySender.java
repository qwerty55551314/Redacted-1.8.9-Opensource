package com.replaymod.replay;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.mixin.TimerAccessor;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.com.github.steveice10.netty.buffer.ByteBuf;
import com.replaymod.lib.com.github.steveice10.packetlib.io.NetOutput;
import com.replaymod.lib.com.github.steveice10.packetlib.tcp.io.ByteBufNetOutput;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.PreTickCallback;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replaystudio.io.ReplayInputStream;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.util.Utils;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandler.Sharable;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.login.server.S01PacketEncryptionRequest;
import net.minecraft.network.login.server.S02PacketLoginSuccess;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
import net.minecraft.network.play.server.S0FPacketSpawnMob;
import net.minecraft.network.play.server.S10PacketSpawnPainting;
import net.minecraft.network.play.server.S11PacketSpawnExperienceOrb;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S1FPacketSetExperience;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.network.play.server.S2BPacketChangeGameState;
import net.minecraft.network.play.server.S2CPacketSpawnGlobalEntity;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraft.network.play.server.S2EPacketCloseWindow;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.network.play.server.S36PacketSignEditorOpen;
import net.minecraft.network.play.server.S37PacketStatistics;
import net.minecraft.network.play.server.S39PacketPlayerAbilities;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.network.play.server.S43PacketCamera;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.network.play.server.S48PacketResourcePackSend;
import net.minecraft.network.play.server.S08PacketPlayerPosLook.EnumFlags;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

@Sharable
public class FullReplaySender extends ChannelInboundHandlerAdapter implements ReplaySender {
   private static final List<Class> BAD_PACKETS = Arrays.asList(
      S01PacketEncryptionRequest.class,
      S43PacketCamera.class,
      S45PacketTitle.class,
      S06PacketUpdateHealth.class,
      S2DPacketOpenWindow.class,
      S2EPacketCloseWindow.class,
      S2FPacketSetSlot.class,
      S30PacketWindowItems.class,
      S36PacketSignEditorOpen.class,
      S37PacketStatistics.class,
      S1FPacketSetExperience.class,
      S39PacketPlayerAbilities.class
   );
   private static int TP_DISTANCE_LIMIT = 128;
   private final ReplayHandler replayHandler;
   protected boolean asyncMode;
   protected int lastTimeStamp;
   protected int currentTimeStamp;
   protected ReplayFile replayFile;
   protected Channel channel;
   protected ReplayInputStream replayIn;
   protected FullReplaySender.PacketData nextPacket;
   private PacketTypeRegistry registry = MCVer.getPacketTypeRegistry(State.LOGIN);
   protected boolean startFromBeginning = true;
   protected boolean terminate;
   protected double replaySpeed = 1.0;
   protected boolean hasWorldLoaded;
   protected boolean inBundle;
   protected Minecraft mc = MCVer.getMinecraft();
   protected final int replayLength;
   protected int actualID = -1;
   protected boolean allowMovement;
   private final File tempResourcePackFolder = Files.createTempDir();
   private final FullReplaySender.EventHandler events = new FullReplaySender.EventHandler();
   private long realTimeStart;
   private double realTimeStartSpeed;
   private long desiredTimeStamp = -1L;
   private Runnable asyncSender = new Runnable() {
      @Override
      public void run() {
         try {
            while (!FullReplaySender.this.terminate) {
               synchronized (FullReplaySender.this) {
                  if (FullReplaySender.this.replayIn == null) {
                     FullReplaySender.this.replayIn = FullReplaySender.this.replayFile.getPacketData(MCVer.getPacketTypeRegistry(State.LOGIN));
                  }

                  while (true) {
                     try {
                        while (
                           FullReplaySender.this.paused()
                              && FullReplaySender.this.hasWorldLoaded
                              && !FullReplaySender.this.inBundle
                              && !FullReplaySender.this.terminate
                              && !FullReplaySender.this.startFromBeginning
                              && FullReplaySender.this.desiredTimeStamp == -1L
                        ) {
                           Thread.sleep(10L);
                        }

                        if (FullReplaySender.this.terminate && !FullReplaySender.this.inBundle) {
                           return;
                        }

                        if (!FullReplaySender.this.startFromBeginning) {
                           if (FullReplaySender.this.nextPacket == null) {
                              FullReplaySender.this.nextPacket = new FullReplaySender.PacketData(FullReplaySender.this.replayIn);
                           }

                           int nextTimeStamp = FullReplaySender.this.nextPacket.timestamp;
                           if (!FullReplaySender.this.isHurrying() && FullReplaySender.this.hasWorldLoaded && !FullReplaySender.this.inBundle) {
                              long expectedTime = FullReplaySender.this.realTimeStart + (long)(nextTimeStamp / FullReplaySender.this.replaySpeed);
                              long now = System.currentTimeMillis();
                              if (expectedTime > now) {
                                 Thread.sleep(expectedTime - now);
                              }
                           }

                           if (FullReplaySender.this.nextPacket.type == PacketType.Bundle) {
                              FullReplaySender.this.inBundle = !FullReplaySender.this.inBundle;
                           }

                           FullReplaySender.this.channel.pipeline().fireChannelRead(Unpooled.wrappedBuffer(FullReplaySender.this.nextPacket.bytes));
                           FullReplaySender.this.nextPacket = null;
                           FullReplaySender.this.lastTimeStamp = nextTimeStamp;
                           if (FullReplaySender.this.isHurrying()
                              && FullReplaySender.this.lastTimeStamp > FullReplaySender.this.desiredTimeStamp
                              && !FullReplaySender.this.startFromBeginning) {
                              FullReplaySender.this.desiredTimeStamp = -1L;
                              FullReplaySender.this.replayHandler.moveCameraToTargetPosition();
                              FullReplaySender.this.setReplaySpeed(0.0);
                           }
                           continue;
                        }
                     } catch (EOFException eof) {
                        FullReplaySender.this.setReplaySpeed(0.0);

                        while (
                           FullReplaySender.this.paused()
                              && FullReplaySender.this.hasWorldLoaded
                              && FullReplaySender.this.desiredTimeStamp == -1L
                              && !FullReplaySender.this.terminate
                        ) {
                           Thread.sleep(10L);
                        }

                        if (FullReplaySender.this.terminate) {
                           return;
                        }
                     } catch (IOException e) {
                        e.printStackTrace();
                        continue;
                     }

                     FullReplaySender.this.hasWorldLoaded = false;
                     FullReplaySender.this.inBundle = false;
                     FullReplaySender.this.lastTimeStamp = 0;
                     FullReplaySender.this.registry = MCVer.getPacketTypeRegistry(State.LOGIN);
                     FullReplaySender.this.startFromBeginning = false;
                     FullReplaySender.this.nextPacket = null;
                     FullReplaySender.this.realTimeStart = System.currentTimeMillis();
                     if (FullReplaySender.this.replayIn != null) {
                        FullReplaySender.this.replayIn.close();
                        FullReplaySender.this.replayIn = null;
                     }

                     ReplayMod.instance.runSync(FullReplaySender.this.replayHandler::restartedReplay);
                     break;
                  }
               }
            }
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   };
   private final ExecutorService syncSender = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "replaymod-sync-sender"));

   public FullReplaySender(ReplayHandler replayHandler, ReplayFile file) throws IOException {
      this.replayHandler = replayHandler;
      this.replayFile = file;
      this.replayLength = file.getMetaData().getDuration();
      this.events.register();
   }

   public void setChannel(Channel channel) {
      this.channel = channel;
   }

   @Override
   public void setAsyncMode(boolean asyncMode) {
      if (this.asyncMode != asyncMode) {
         this.asyncMode = asyncMode;
         if (asyncMode) {
            this.terminate = false;
            new Thread(this.asyncSender, "replaymod-async-sender").start();
         } else {
            this.terminate = true;
         }
      }
   }

   @Override
   public boolean isAsyncMode() {
      return this.asyncMode;
   }

   @Override
   public void setSyncModeAndWait() {
      if (this.asyncMode) {
         this.asyncMode = false;
         this.terminate = true;
         synchronized (this) {
            ;
         }
      }
   }

   @Override
   public int currentTimeStamp() {
      return this.asyncMode && !this.paused() ? (int)((System.currentTimeMillis() - this.realTimeStart) * this.realTimeStartSpeed) : this.lastTimeStamp;
   }

   public void terminateReplay() {
      if (!this.terminate) {
         this.terminate = true;
         this.syncSender.shutdown();
         this.events.unregister();

         try {
            this.channel.pipeline().fireChannelInactive();
            this.channel.pipeline().close();
            FileUtils.deleteDirectory(this.tempResourcePackFolder);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (!this.terminate || !this.asyncMode) {
         if (msg instanceof Packet) {
            try {
               Packet p = (Packet)msg;
               if (p != null) {
                  p = this.processPacket(p);
                  if (p != null) {
                     super.channelRead(ctx, p);
                  }

                  this.maybeRemoveDeadEntities(p);
               }
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      }
   }

   private void maybeRemoveDeadEntities(Packet packet) {
      if (!this.asyncMode) {
         boolean relevantPacket = packet instanceof S0EPacketSpawnObject
            || packet instanceof S0CPacketSpawnPlayer
            || packet instanceof S0FPacketSpawnMob
            || packet instanceof S10PacketSpawnPainting
            || packet instanceof S2CPacketSpawnGlobalEntity
            || packet instanceof S11PacketSpawnExperienceOrb
            || packet instanceof S13PacketDestroyEntities;
         if (relevantPacket) {
            this.mc.addScheduledTask(() -> {
               WorldClient world = this.mc.theWorld;
               if (world != null) {
                  this.removeDeadEntities(world);
               }
            });
         }
      }
   }

   private void removeDeadEntities(WorldClient world) {
      Iterator<Entity> iter = world.loadedEntityList.iterator();

      while (iter.hasNext()) {
         Entity entity = iter.next();
         if (entity.isDead) {
            int chunkX = entity.chunkCoordX;
            int chunkY = entity.chunkCoordZ;
            if (entity.addedToChunk && world.getChunkProvider().chunkExists(chunkX, chunkY)) {
               world.getChunkFromChunkCoords(chunkX, chunkY).removeEntity(entity);
            }

            iter.remove();
            world.onEntityRemoved(entity);
         }
      }
   }

   protected Packet processPacket(Packet p) throws Exception {
      if (p instanceof S02PacketLoginSuccess) {
         this.registry = this.registry.withLoginSuccess();
         return p;
      }

      if (p instanceof S3FPacketCustomPayload) {
         S3FPacketCustomPayload packet = (S3FPacketCustomPayload)p;
         if ("Replay|Restrict".equals(packet.getChannelName())) {
            String unknown = this.replayHandler.getRestrictions().handle(packet);
            if (unknown == null) {
               return null;
            }

            this.terminateReplay();
            ReplayMod.instance
               .runLater(
                  () -> {
                     try {
                        this.replayHandler.endReplay();
                     } catch (IOException e) {
                        e.printStackTrace();
                     }

                     this.mc
                        .displayGuiScreen(
                           new GuiErrorScreen(
                              I18n.format("replaymod.error.unknownrestriction1", new Object[0]),
                              I18n.format("replaymod.error.unknownrestriction2", new Object[]{unknown})
                           )
                        );
                  }
               );
         }
      }

      if (p instanceof S40PacketDisconnect) {
         IChatComponent reason = ((S40PacketDisconnect)p).getReason();
         String message = reason.getUnformattedText();
         if ("Please update to view this replay.".equals(message)) {
            return null;
         }
      }

      if (BAD_PACKETS.contains(p.getClass())) {
         return null;
      }

      if (p instanceof S3FPacketCustomPayload) {
         S3FPacketCustomPayload packet = (S3FPacketCustomPayload)p;
         String channelName = packet.getChannelName();
         String channelNameStr = channelName.toString();
         if (channelNameStr.startsWith("fabric-screen-handler-api-v")) {
            return null;
         }

         if ("MC|BOpen".equals(channelName)) {
            return null;
         }
      }

      if (p instanceof S48PacketResourcePackSend) {
         S48PacketResourcePackSend packet = (S48PacketResourcePackSend)p;
         String url = packet.getURL();
         if (url.startsWith("replay://")) {
            int id = Integer.parseInt(url.substring("replay://".length()));
            Map<Integer, String> index = this.replayFile.getResourcePackIndex();
            if (index != null) {
               String hash = index.get(id);
               if (hash != null) {
                  File file = new File(this.tempResourcePackFolder, hash + ".zip");
                  if (!file.exists()) {
                     IOUtils.copy(this.replayFile.getResourcePack(hash).get(), new FileOutputStream(file));
                  }

                  MCVer.setServerResourcePack(file);
               }
            }

            return null;
         }
      }

      if (p instanceof S01PacketJoinGame) {
         S01PacketJoinGame packet = (S01PacketJoinGame)p;
         int entId = packet.getEntityId();
         this.schedulePacketHandler(() -> this.allowMovement = true);
         this.actualID = entId;
         entId = -1789435;
         int dimension = packet.getDimension();
         EnumDifficulty difficulty = packet.getDifficulty();
         int maxPlayers = packet.getMaxPlayers();
         WorldType worldType = packet.getWorldType();
         p = new S01PacketJoinGame(entId, GameType.SPECTATOR, false, dimension, difficulty, maxPlayers, worldType, false);
      }

      if (p instanceof S07PacketRespawn) {
         S07PacketRespawn respawn = (S07PacketRespawn)p;
         p = new S07PacketRespawn(respawn.getDimensionID(), respawn.getDifficulty(), respawn.getWorldType(), GameType.SPECTATOR);
         this.schedulePacketHandler(() -> this.allowMovement = true);
      }

      if (!(p instanceof S08PacketPlayerPosLook)) {
         if (p instanceof S2BPacketChangeGameState) {
            S2BPacketChangeGameState pg = (S2BPacketChangeGameState)p;
            if (!Arrays.asList(1, 2, 7, 8).contains(pg.getGameState())) {
               return null;
            }
         }

         if (p instanceof S02PacketChat && !ReplayModReplay.instance.getCore().getSettingsRegistry().get(Setting.SHOW_CHAT)) {
            return null;
         }

         if (this.asyncMode) {
            return this.processPacketAsync(p);
         }

         Packet fp = p;
         this.schedulePacketHandler(() -> this.processPacketSync(fp));
         return p;
      } else {
         final S08PacketPlayerPosLook ppl = (S08PacketPlayerPosLook)p;
         if (!this.hasWorldLoaded) {
            this.hasWorldLoaded = true;
         }

         ReplayMod.instance.runLater(() -> {
            if (this.mc.currentScreen instanceof GuiDownloadTerrain) {
               this.mc.displayGuiScreen(null);
            }
         });
         if (this.replayHandler.shouldSuppressCameraMovements()) {
            return null;
         }

         for (Object relative : ppl.func_179834_f()) {
            if (relative == EnumFlags.X || relative == EnumFlags.Y || relative == EnumFlags.Z) {
               return null;
            }
         }

         this.schedulePacketHandler(
            new Runnable() {
               @Override
               public void run() {
                  if (FullReplaySender.this.mc.theWorld != null && FullReplaySender.this.mc.isCallingFromMinecraftThread()) {
                     CameraEntity cent = FullReplaySender.this.replayHandler.getCameraEntity();
                     if (FullReplaySender.this.allowMovement
                        || Math.abs(cent.posX - ppl.getX()) > FullReplaySender.TP_DISTANCE_LIMIT
                        || Math.abs(cent.posZ - ppl.getZ()) > FullReplaySender.TP_DISTANCE_LIMIT) {
                        FullReplaySender.this.allowMovement = false;
                        cent.setCameraPosition(ppl.getX(), ppl.getY(), ppl.getZ());
                        cent.setCameraRotation(ppl.getYaw(), ppl.getPitch(), cent.roll);
                     }
                  } else {
                     ReplayMod.instance.runLater(this);
                  }
               }
            }
         );
         return null;
      }
   }

   @Override
   public double getReplaySpeed() {
      return !this.paused() ? this.replaySpeed : 0.0;
   }

   @Override
   public void setReplaySpeed(double d) {
      if (d != 0.0) {
         this.replaySpeed = d;
         this.realTimeStartSpeed = d;
         this.realTimeStart = System.currentTimeMillis() - (long)(this.lastTimeStamp / d);
      }

      TimerAccessor timer = (TimerAccessor)((MinecraftAccessor)this.mc).getTimer();
      timer.setTimerSpeed((float)d);
   }

   public boolean isHurrying() {
      return this.desiredTimeStamp != -1L;
   }

   public void stopHurrying() {
      this.desiredTimeStamp = -1L;
   }

   public long getDesiredTimestamp() {
      return this.desiredTimeStamp;
   }

   @Override
   public void jumpToTime(int millis) {
      Preconditions.checkState(this.asyncMode, "Can only jump in async mode. Use sendPacketsTill(int) instead.");
      if (millis < this.lastTimeStamp && !this.isHurrying()) {
         this.startFromBeginning = true;
      }

      this.desiredTimeStamp = millis;
   }

   protected Packet processPacketAsync(Packet p) {
      if (this.desiredTimeStamp - this.lastTimeStamp > 1000L) {
         if (p instanceof S2APacketParticles) {
            return null;
         }

         if (p instanceof S0EPacketSpawnObject) {
            S0EPacketSpawnObject pso = (S0EPacketSpawnObject)p;
            int type = pso.getType();
            if (type == 76) {
               return null;
            }
         }
      }

      return p;
   }

   @Override
   public void sendPacketsTill(int timestamp) {
      Preconditions.checkState(!this.asyncMode, "This method cannot be used in async mode. Use jumpToTime(int) instead.");
      AtomicBoolean doneSending = new AtomicBoolean();
      this.syncSender.submit(() -> {
         try {
            this.doSendPacketsTill(timestamp);
         } finally {
            doneSending.set(true);
         }
      });

      while (!doneSending.get()) {
         this.executeTaskQueue();

         try {
            Thread.sleep(0L, 100000);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
         }
      }

      this.executeTaskQueue();
   }

   private void doSendPacketsTill(int timestamp) {
      try {
         synchronized (this) {
            if (timestamp == this.lastTimeStamp) {
               return;
            }

            if (timestamp < this.lastTimeStamp) {
               this.hasWorldLoaded = false;
               this.inBundle = false;
               this.lastTimeStamp = 0;
               if (this.replayIn != null) {
                  this.replayIn.close();
                  this.replayIn = null;
               }

               this.registry = MCVer.getPacketTypeRegistry(State.LOGIN);
               this.startFromBeginning = false;
               this.nextPacket = null;
               ReplayMod.instance.runSync(this.replayHandler::restartedReplay);
            }

            if (this.replayIn == null) {
               this.replayIn = this.replayFile.getPacketData(MCVer.getPacketTypeRegistry(State.LOGIN));
            }

            while (true) {
               try {
                  FullReplaySender.PacketData pd;
                  if (this.nextPacket != null) {
                     pd = this.nextPacket;
                     this.nextPacket = null;
                  } else {
                     pd = new FullReplaySender.PacketData(this.replayIn);
                  }

                  int nextTimeStamp = pd.timestamp;
                  if (nextTimeStamp > timestamp && !this.inBundle) {
                     this.nextPacket = pd;
                     break;
                  }

                  if (pd.type == PacketType.Bundle) {
                     this.inBundle = !this.inBundle;
                  }

                  this.channel.pipeline().fireChannelRead(Unpooled.wrappedBuffer(pd.bytes));
               } catch (EOFException eof) {
                  this.replayIn = null;
                  break;
               } catch (IOException e) {
                  e.printStackTrace();
               }
            }

            this.realTimeStart = System.currentTimeMillis() - (long)(timestamp / this.replaySpeed);
            this.lastTimeStamp = timestamp;
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   private void executeTaskQueue() {
      Queue<FutureTask<?>> scheduledTasks = ((MinecraftAccessor)this.mc).getScheduledTasks();
      if (Thread.holdsLock(scheduledTasks)) {
         throw new IllegalStateException("Task queue already locked. You may want to use `Scheduler.runLaterWithoutLock` to run while the lock is not taken.");
      }

      synchronized (scheduledTasks) {
         while (!scheduledTasks.isEmpty()) {
            scheduledTasks.poll().run();
         }
      }

      ReplayMod.instance.runTasks();
   }

   private void schedulePacketHandler(Runnable runnable) {
      if (this.mc.isCallingFromMinecraftThread()) {
         runnable.run();
      } else {
         this.mc.addScheduledTask(runnable);
      }
   }

   protected void processPacketSync(Packet p) {
      if (p instanceof S21PacketChunkData && ((S21PacketChunkData)p).getExtractedSize() == 0) {
         S21PacketChunkData packet = (S21PacketChunkData)p;
         int x = packet.getChunkX();
         int z = packet.getChunkZ();
         World world = this.mc.theWorld;
         IChunkProvider chunkProvider = world.getChunkProvider();
         Chunk chunk = chunkProvider.provideChunk(x, z);
         if (!chunk.isEmpty()) {
            List<Entity> entitiesInChunk = new ArrayList<>();

            for (Collection<Entity> entityList : chunk.getEntityLists()) {
               entitiesInChunk.addAll(entityList);
            }

            for (Entity entity : entitiesInChunk) {
               this.forcePositionForVehicleAndSelf(entity);
               int chunkX = MathHelper.floor_double(entity.posX / 16.0);
               int chunkZ = MathHelper.floor_double(entity.posZ / 16.0);
               if (entity.chunkCoordX == chunkX && entity.chunkCoordZ == chunkZ) {
                  chunk.removeEntityAtIndex(entity, entity.chunkCoordY);
                  entity.addedToChunk = false;
               } else {
                  chunk.removeEntityAtIndex(entity, entity.chunkCoordY);
                  Chunk newChunk = chunkProvider.chunkExists(chunkX, chunkZ) ? chunkProvider.provideChunk(chunkX, chunkZ) : null;
                  if (newChunk != null) {
                     newChunk.addEntity(entity);
                  } else {
                     entity.addedToChunk = false;
                  }
               }
            }
         }
      }
   }

   private void forcePositionForVehicleAndSelf(Entity entity) {
      Entity vehicle = entity.ridingEntity;
      if (vehicle != null) {
         this.forcePositionForVehicleAndSelf(vehicle);
      }

      int ticks = 0;

      Vec3 prevPos;
      do {
         prevPos = entity.getPositionVector();
         if (vehicle != null) {
            entity.updateRidden();
         } else {
            entity.onUpdate();
         }
      } while (prevPos.squareDistanceTo(entity.getPositionVector()) > 1.0E-4 && ticks++ < 100);
   }

   private class EventHandler extends EventRegistrations {
      private EventHandler() {
         this.on(PreTickCallback.EVENT, this::onWorldTick);
      }

      private void onWorldTick() {
         if (FullReplaySender.this.mc.theWorld != null) {
            for (EntityPlayer playerEntity : FullReplaySender.this.mc.theWorld.playerEntities) {
               if (!playerEntity.addedToChunk && playerEntity instanceof EntityOtherPlayerMP) {
                  playerEntity.onLivingUpdate();
               }
            }
         }
      }
   }

   private static final class PacketData {
      private static final ByteBuf byteBuf = com.replaymod.lib.com.github.steveice10.netty.buffer.Unpooled.buffer();
      private static final NetOutput netOutput = new ByteBufNetOutput(byteBuf);
      private final int timestamp;
      private final byte[] bytes;
      private final PacketType type;

      PacketData(ReplayInputStream in) throws IOException {
         if (ReplayMod.isMinimalMode()) {
            this.timestamp = Utils.readInt(in);
            int length = Utils.readInt(in);
            if (this.timestamp == -1 || length == -1) {
               throw new EOFException();
            }

            this.bytes = new byte[length];
            IOUtils.readFully(in, this.bytes);
            this.type = PacketType.UnknownLogin;
         } else {
            com.replaymod.replaystudio.PacketData data = in.readPacket();
            if (data == null) {
               throw new EOFException();
            }

            this.timestamp = (int)data.getTime();
            com.replaymod.replaystudio.protocol.Packet packet = data.getPacket();
            this.type = packet.getType();
            if (packet.getId() == -1) {
               this.bytes = new byte[0];
               return;
            }

            synchronized (byteBuf) {
               byteBuf.markReaderIndex();
               byteBuf.markWriterIndex();
               netOutput.writeVarInt(packet.getId());
               int idSize = byteBuf.readableBytes();
               int contentSize = packet.getBuf().readableBytes();
               this.bytes = new byte[idSize + contentSize];
               byteBuf.readBytes(this.bytes, 0, idSize);
               packet.getBuf().readBytes(this.bytes, idSize, contentSize);
               byteBuf.resetReaderIndex();
               byteBuf.resetWriterIndex();
            }

            packet.getBuf().release();
         }
      }
   }
}
