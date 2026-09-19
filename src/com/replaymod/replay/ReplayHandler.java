package com.replaymod.replay;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.mixin.TimerAccessor;
import com.replaymod.core.utils.Restrictions;
import com.replaymod.core.utils.Utils;
import com.replaymod.core.versions.MCVer;
import com.replaymod.core.versions.Window;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiProgressBar;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replay.camera.SpectatorCameraController;
import com.replaymod.replay.events.ReplayClosedCallback;
import com.replaymod.replay.events.ReplayClosingCallback;
import com.replaymod.replay.events.ReplayOpenedCallback;
import com.replaymod.replay.gui.overlay.GuiReplayOverlay;
import com.replaymod.replay.mixin.EntityOtherPlayerMPAccessor;
import com.replaymod.replaystudio.data.Marker;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.util.Location;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import redacted.client.util.debug.DebugSystem;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.MessageDeserializer;
import net.minecraft.util.MessageSerializer;
import org.lwjglx.opengl.Display;

public class ReplayHandler {
   public static final String PACKET_HANDLER_NAME = "ReplayModReplay_packetHandler";
   private static Minecraft mc = MCVer.getMinecraft();
   private final ReplayFile replayFile;
   private final FullReplaySender fullReplaySender;
   private final QuickReplaySender quickReplaySender;
   private boolean quickMode = false;
   private Restrictions restrictions = new Restrictions();
   private boolean suppressCameraMovements;
   private Set<Marker> markers;
   private final GuiReplayOverlay overlay;
   private EmbeddedChannel channel;
   private int replayDuration;
   private Location targetCameraPosition;
   private UUID spectating;

   public ReplayHandler(ReplayFile replayFile, boolean asyncMode) throws IOException {
      Preconditions.checkState(mc.isCallingFromMinecraftThread(), "Must be called from Minecraft thread.");
      this.replayFile = replayFile;
      this.replayDuration = replayFile.getMetaData().getDuration();
      this.markers = replayFile.getMarkers().or(Collections.emptySet());
      this.fullReplaySender = new FullReplaySender(this, replayFile);
      this.quickReplaySender = new QuickReplaySender(ReplayModReplay.instance, replayFile);
      this.setup();
      this.overlay = new GuiReplayOverlay(this);
      this.overlay.setVisible(true);
      ReplayOpenedCallback.EVENT.invoker().replayOpened(this);
      this.fullReplaySender.setAsyncMode(asyncMode);
   }

   void restartedReplay() {
      Preconditions.checkState(mc.isCallingFromMinecraftThread(), "Must be called from Minecraft thread.");
      this.channel.close();
      mc.setIngameNotInFocus();
      mc.displayGuiScreen(new GuiScreen() {});
      mc.loadWorld(null);
      this.restrictions = new Restrictions();
      this.setup();
   }

   public void endReplay() throws IOException {
      Preconditions.checkState(mc.isCallingFromMinecraftThread(), "Must be called from Minecraft thread.");
      ReplayClosingCallback.EVENT.invoker().replayClosing(this);
      this.fullReplaySender.terminateReplay();
      if (this.quickMode) {
         this.quickReplaySender.unregister();
      }

      this.replayFile.save();
      this.replayFile.close();
      this.channel.close().awaitUninterruptibly();
      if (mc.thePlayer instanceof CameraEntity) {
         mc.thePlayer.setDead();
      }

      if (mc.theWorld != null) {
         mc.theWorld.sendQuittingDisconnectingPacket();
         mc.loadWorld(null);
      }

      TimerAccessor timer = (TimerAccessor)((MinecraftAccessor)mc).getTimer();
      timer.setTimerSpeed(1.0F);
      this.overlay.setVisible(false);
      ReplayModReplay.instance.forcefullyStopReplay();
      mc.displayGuiScreen(null);
      ReplayClosedCallback.EVENT.invoker().replayClosed(this);
   }

   private void setup() {
      Preconditions.checkState(mc.isCallingFromMinecraftThread(), "Must be called from Minecraft thread.");
      mc.ingameGUI.getChatGUI().clearChatMessages();
      NetworkManager networkManager = new NetworkManager(EnumPacketDirection.CLIENTBOUND) {
         public void exceptionCaught(ChannelHandlerContext ctx, Throwable t) {
            t.printStackTrace();
         }
      };
      ChannelOutboundHandlerAdapter dummyHandler = new ChannelOutboundHandlerAdapter();
      this.channel = new EmbeddedChannel(new ChannelHandler[]{dummyHandler});
      this.channel.pipeline().remove(dummyHandler);
      this.channel.pipeline().addFirst("ReplayModReplay_head", new ReplayHandler.DropOutboundMessagesHandler());
      this.quickReplaySender.setChannel(this.channel);
      this.fullReplaySender.setChannel(this.channel);
      this.channel.pipeline().addLast("decoder", new MessageDeserializer(EnumPacketDirection.CLIENTBOUND));
      this.channel.pipeline().addLast("encoder", new MessageSerializer(EnumPacketDirection.SERVERBOUND));
      this.channel.pipeline().addLast("ReplayModReplay_packetHandler", this.quickMode ? this.quickReplaySender : this.fullReplaySender);
      this.channel.pipeline().addLast("packet_handler", networkManager);
      this.channel.pipeline().fireChannelActive();
      networkManager.setConnectionState(EnumConnectionState.LOGIN);
      networkManager.setNetHandler(new NetHandlerLoginClient(networkManager, mc, null));
   }

   public ReplayFile getReplayFile() {
      return this.replayFile;
   }

   public Restrictions getRestrictions() {
      return this.restrictions;
   }

   public ReplaySender getReplaySender() {
      return this.quickMode ? this.quickReplaySender : this.fullReplaySender;
   }

   public GuiReplayOverlay getOverlay() {
      return this.overlay;
   }

   public void ensureQuickModeInitialized(Runnable andThen) {
      if (!Utils.ifMinimalModeDoPopup(this.overlay, () -> {})) {
         ListenableFuture<Void> future = this.quickReplaySender.getInitializationPromise();
         if (future == null) {
            final ReplayHandler.InitializingQuickModePopup popup = new ReplayHandler.InitializingQuickModePopup(this.overlay);
            future = this.quickReplaySender.initialize(progress -> {
               GuiProgressBar var10000 = popup.progressBar.setProgress(progress.floatValue());
            });
            Futures.addCallback(future, new FutureCallback<Void>() {
               public void onSuccess(@Nullable Void result) {
                  popup.close();
               }

               public void onFailure(@Nonnull Throwable t) {
                  String message = "Failed to initialize quick mode. It will not be available.";
                  Utils.error(ReplayModReplay.LOGGER, ReplayHandler.this.overlay, CrashReport.makeCrashReport(t, message), popup::close);
               }
            });
         }

         Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(@Nullable Void result) {
               andThen.run();
            }

            public void onFailure(@Nonnull Throwable t) {
            }
         });
      }
   }

   public void setQuickMode(boolean quickMode) {
      if (ReplayMod.isMinimalMode()) {
         throw new UnsupportedOperationException("Quick Mode not supported in minimal mode.");
      }

      if (quickMode != this.quickMode) {
         if (quickMode && this.fullReplaySender.isAsyncMode()) {
            throw new IllegalStateException("Cannot switch to quick mode while in async mode.");
         }

         this.quickMode = quickMode;
         CameraEntity cam = this.getCameraEntity();
         if (cam != null) {
            this.targetCameraPosition = new Location(cam.posX, cam.posY, cam.posZ, cam.rotationYaw, cam.rotationPitch);
         } else {
            this.targetCameraPosition = null;
         }

         this.channel
            .pipeline()
            .replace("ReplayModReplay_packetHandler", "ReplayModReplay_packetHandler", quickMode ? this.quickReplaySender : this.fullReplaySender);
         if (quickMode) {
            this.quickReplaySender.register();
            this.quickReplaySender.restart();
            this.quickReplaySender.sendPacketsTill(this.fullReplaySender.currentTimeStamp());
         } else {
            this.quickReplaySender.unregister();
            this.fullReplaySender.sendPacketsTill(0);
            this.fullReplaySender.sendPacketsTill(this.quickReplaySender.currentTimeStamp());
         }

         this.moveCameraToTargetPosition();
      }
   }

   public boolean isQuickMode() {
      return this.quickMode;
   }

   public int getReplayDuration() {
      return this.replayDuration;
   }

   public boolean shouldSuppressCameraMovements() {
      return this.suppressCameraMovements;
   }

   public void setSuppressCameraMovements(boolean suppressCameraMovements) {
      this.suppressCameraMovements = suppressCameraMovements;
   }

   public void spectateEntity(Entity e) {
      CameraEntity cameraEntity = this.getCameraEntity();
      if (cameraEntity != null) {
         if (e == null || e == cameraEntity) {
            this.spectating = null;
            e = cameraEntity;
         } else if (e instanceof EntityPlayer) {
            this.spectating = e.getUniqueID();
         }

         if (e == cameraEntity) {
            cameraEntity.setCameraController(ReplayModReplay.instance.createCameraController(cameraEntity));
         } else {
            cameraEntity.setCameraController(new SpectatorCameraController(cameraEntity));
         }

         if (mc.getRenderViewEntity() != e) {
            mc.setRenderViewEntity(e);
            cameraEntity.setCameraPosRot(e);
         }
      }
   }

   public void spectateCamera() {
      this.spectateEntity(null);
   }

   public boolean isCameraView() {
      return mc.thePlayer instanceof CameraEntity && mc.thePlayer == mc.getRenderViewEntity();
   }

   public CameraEntity getCameraEntity() {
      return mc.thePlayer instanceof CameraEntity ? (CameraEntity)mc.thePlayer : null;
   }

   public UUID getSpectatedUUID() {
      return this.spectating;
   }

   public void moveCameraToTargetPosition() {
      CameraEntity cam = this.getCameraEntity();
      if (cam != null && this.targetCameraPosition != null) {
         cam.setCameraPosRot(this.targetCameraPosition);
      }
   }

   public void doJump(int targetTime, boolean retainCameraPosition) {
      if (this.getReplaySender().isAsyncMode()) {
         if (this.getReplaySender() == this.quickReplaySender) {
            targetTime += targetTime % 50;
            if (targetTime >= 50) {
               this.quickReplaySender.sendPacketsTill(targetTime - 50);
            }

            for (Entity entity : mc.theWorld.loadedEntityList) {
               this.skipTeleportInterpolation(entity);
               entity.lastTickPosX = entity.prevPosX = entity.posX;
               entity.lastTickPosY = entity.prevPosY = entity.posY;
               entity.lastTickPosZ = entity.prevPosZ = entity.posZ;
               entity.prevRotationYaw = entity.rotationYaw;
               entity.prevRotationPitch = entity.rotationPitch;
            }

            try {
               mc.runTick();
            } catch (IOException e) {
               throw new RuntimeException(e);
            }

            this.quickReplaySender.sendPacketsTill(targetTime);

            for (Entity entity : mc.theWorld.loadedEntityList) {
               this.skipTeleportInterpolation(entity);
            }
         } else {
            FullReplaySender replaySender = this.fullReplaySender;
            if (!replaySender.isHurrying()) {
               if (targetTime < replaySender.currentTimeStamp()) {
                  mc.displayGuiScreen(null);
               }

               if (retainCameraPosition) {
                  CameraEntity cam = this.getCameraEntity();
                  if (cam != null) {
                     this.targetCameraPosition = new Location(cam.posX, cam.posY, cam.posZ, cam.rotationYaw, cam.rotationPitch);
                  } else {
                     this.targetCameraPosition = null;
                  }
               }

               long diff = targetTime - (replaySender.isHurrying() ? replaySender.getDesiredTimestamp() : replaySender.currentTimeStamp());
               if (diff != 0L) {
                  if (diff <= 0L || diff >= 5000L) {
                     com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen guiScreen = new com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen();
                     guiScreen.setBackground(AbstractGuiScreen.Background.DIRT);
                     guiScreen.setLayout(new HorizontalLayout(HorizontalLayout.Alignment.CENTER));
                     guiScreen.addElements(new HorizontalLayout.Data(0.5), new GuiLabel().setI18nText("replaymod.gui.pleasewait"));
                     replaySender.setSyncModeAndWait();
                     MCVer.pushMatrix();
                     GlStateManager.clear(16640);
                     GlStateManager.enableTexture2D();
                     mc.getFramebuffer().bindFramebuffer(true);
                     Window window = new Window(mc);
                     mc.entityRenderer.setupOverlayRendering();
                     guiScreen.toMinecraft().setWorldAndResolution(mc, window.getScaledWidth(), window.getScaledHeight());
                     guiScreen.toMinecraft().drawScreen(0, 0, 0.0F);
                     guiScreen.toMinecraft().onGuiClosed();
                     mc.getFramebuffer().unbindFramebuffer();
                     MCVer.popMatrix();
                     MCVer.pushMatrix();
                     mc.getFramebuffer().framebufferRender(new Window(mc).getFramebufferWidth(), new Window(mc).getFramebufferHeight());
                     MCVer.popMatrix();
                     Display.update();

                     int replayEndTime = this.replayDuration;
                     do {
                        replaySender.sendPacketsTill(targetTime);
                        targetTime += 500;
                     } while ((mc.thePlayer == null || mc.currentScreen instanceof GuiDownloadTerrain) && targetTime <= replayEndTime + 5000);
                     DebugSystem.log(DebugSystem.REPLAY, "doJump load loop exited: targetTime=" + targetTime + " replayEnd=" + replayEndTime + " thePlayer=" + (mc.thePlayer == null ? "null" : "present") + " world=" + (mc.theWorld == null ? "null" : "present"));

                     replaySender.setAsyncMode(true);
                     replaySender.setReplaySpeed(0.0);
                     mc.getNetHandler().getNetworkManager().processReceivedPackets();
                     if (mc.theWorld == null) {
                        return;
                     }

                     for (Entity entity : mc.theWorld.loadedEntityList) {
                        this.skipTeleportInterpolation(entity);
                        entity.lastTickPosX = entity.prevPosX = entity.posX;
                        entity.lastTickPosY = entity.prevPosY = entity.posY;
                        entity.lastTickPosZ = entity.prevPosZ = entity.posZ;
                        entity.prevRotationYaw = entity.rotationYaw;
                        entity.prevRotationPitch = entity.rotationPitch;
                     }

                     try {
                        mc.runTick();
                     } catch (IOException e) {
                        e.printStackTrace();
                     }

                     this.moveCameraToTargetPosition();
                  } else if (replaySender.paused()) {
                     replaySender.setSyncModeAndWait();

                     int replayEndTime2 = this.replayDuration;
                     do {
                        replaySender.sendPacketsTill(targetTime);
                        targetTime += 500;
                     } while ((mc.thePlayer == null || mc.currentScreen instanceof GuiDownloadTerrain) && targetTime <= replayEndTime2 + 5000);

                     replaySender.setAsyncMode(true);

                     for (int i = 0; i < Math.min(diff / 50L, 3L); i++) {
                        try {
                           mc.runTick();
                        } catch (IOException e) {
                           e.printStackTrace();
                        }
                     }
                  } else {
                     replaySender.jumpToTime(targetTime);
                  }
               }
            }
         }
      }
   }

   private void skipTeleportInterpolation(Entity entity) {
      if (entity instanceof EntityOtherPlayerMP) {
         EntityOtherPlayerMP e = (EntityOtherPlayerMP)entity;
         EntityOtherPlayerMPAccessor ea = (EntityOtherPlayerMPAccessor)e;
         e.setPosition(ea.getOtherPlayerMPX(), ea.getOtherPlayerMPY(), ea.getOtherPlayerMPZ());
         e.rotationYaw = (float)ea.getOtherPlayerMPYaw();
         e.rotationPitch = (float)ea.getOtherPlayerMPPitch();
      }
   }

   private static class DropOutboundMessagesHandler extends ChannelOutboundHandlerAdapter {
      private DropOutboundMessagesHandler() {
      }

      public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
         // The replay's outbound packets are discarded, but the encoded buffer
         // must still be released or netty's leak detector flags it (SEVERE
         // "ByteBuf.release() was not called" spam after timeline jumps).
         io.netty.util.ReferenceCountUtil.release(msg);
         promise.setSuccess();
      }

      public void flush(ChannelHandlerContext ctx) {
      }
   }

   private class InitializingQuickModePopup extends AbstractGuiPopup<ReplayHandler.InitializingQuickModePopup> {
      private final GuiProgressBar progressBar = new GuiProgressBar(this.popup).setSize(300, 20).setI18nLabel("replaymod.gui.loadquickmode");

      public InitializingQuickModePopup(GuiContainer container) {
         super(container);
         this.open();
      }

      @Override
      public void close() {
         super.close();
      }

      protected ReplayHandler.InitializingQuickModePopup getThis() {
         return this;
      }
   }
}
