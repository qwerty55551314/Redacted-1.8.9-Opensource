package com.replaymod.core.versions;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.replaymod.core.mixin.GuiScreenAccessor;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector2f;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector3f;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.entity.Entity;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.Vec3;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;

public class MCVer {
   public static int getProtocolVersion() {
      return 47; // 1.8.9
   }

   public static EnumConnectionState asMc(State state) {
      switch (state) {
         case HANDSHAKE:
            return EnumConnectionState.HANDSHAKING;
         case STATUS:
            return EnumConnectionState.STATUS;
         case LOGIN:
            return EnumConnectionState.LOGIN;
         case PLAY:
            return EnumConnectionState.PLAY;
         default:
            throw new IllegalArgumentException("Unexpected value: " + state);
      }
   }

   public static State fromMc(EnumConnectionState mcState) {
      switch (mcState) {
         case HANDSHAKING:
            return State.HANDSHAKE;
         case STATUS:
            return State.STATUS;
         case LOGIN:
            return State.LOGIN;
         case PLAY:
            return State.PLAY;
         default:
            throw new IllegalArgumentException("Unexpected value: " + mcState);
      }
   }

   public static PacketTypeRegistry getPacketTypeRegistry(EnumConnectionState state) {
      return getPacketTypeRegistry(fromMc(state));
   }

   public static PacketTypeRegistry getPacketTypeRegistry(State state) {
      return PacketTypeRegistry.get(ProtocolVersion.getProtocol(getProtocolVersion()), state);
   }

   public static PacketTypeRegistry getPacketTypeRegistry(boolean loginPhase) {
      return PacketTypeRegistry.get(ProtocolVersion.getProtocol(getProtocolVersion()), loginPhase ? State.LOGIN : State.PLAY);
   }

   public static void resizeMainWindow(Minecraft mc, int width, int height) {
      if (width != mc.displayWidth || height != mc.displayHeight) {
         mc.resize(width, height);
      }
   }

   public static ListenableFuture<?> setServerResourcePack(File file) {
      ResourcePackRepository repo = getMinecraft().getResourcePackRepository();
      return repo.setResourcePackInstance(file);
   }

   public static <T> void addCallback(ListenableFuture<T> future, Consumer<T> success, Consumer<Throwable> failure) {
      Futures.addCallback(future, new FutureCallback<T>() {
         public void onSuccess(T result) {
            success.accept(result);
         }

         public void onFailure(Throwable throwable) {
            failure.accept(throwable);
         }
      });
   }

   public static List<VertexFormatElement> getElements(VertexFormat vertexFormat) {
      return vertexFormat.getElements();
   }

   public static Minecraft getMinecraft() {
      return Minecraft.getMinecraft();
   }

   public static void addButton(GuiScreen screen, GuiButton button) {
      GuiScreenAccessor acc = (GuiScreenAccessor)screen;
      acc.getButtons().add(button);
   }

   public static Optional<GuiButton> findButton(Iterable<GuiButton> buttonList, String text, int id) {
      for (GuiButton b : buttonList) {
         if (b.id == id) {
            return Optional.of(b);
         }
      }

      return Optional.empty();
   }

   public static long milliTime() {
      return Minecraft.getSystemTime();
   }

   public static Vec3 getTrackedPosition(Entity entity) {
      return new Vec3(entity.serverPosX / 4096.0, entity.serverPosY / 4096.0, entity.serverPosZ / 4096.0);
   }

   public static void openFile(File file) {
      String path = file.getAbsolutePath();

      try {
         switch (Util.getOSType()) {
            case WINDOWS:
               Runtime.getRuntime().exec(String.format("cmd.exe /C start \"Open file\" \"%s\"", path));
               return;
            case OSX:
               Runtime.getRuntime().exec(new String[]{"/usr/bin/open", path});
               return;
            case LINUX:
               Runtime.getRuntime().exec(new String[]{"xdg-open", path});
               return;
         }
      } catch (IOException e) {
         LogManager.getLogger().error("Cannot open file", e);
      }

      try {
         Desktop.getDesktop().open(file);
      } catch (Throwable throwable) {
         LogManager.getLogger().error("Cannot open file: " + path, throwable);
      }
   }

   public static void openURL(URI url) {
      String osName = System.getProperty("os.name", "").toLowerCase();
      if (osName.contains("linux")) {
         try {
            Runtime.getRuntime().exec(new String[]{"xdg-open", url.toString()});
            return;
         } catch (IOException e) {
            LogManager.getLogger().error("Cannot open URL: " + url, e);
         }
      }
      try {
         Desktop.getDesktop().browse(url);
      } catch (Throwable e) {
         LogManager.getLogger().error("Failed to open URL: " + url, e);
      }
   }

   public static void pushMatrix() {
      GlStateManager.pushMatrix();
   }

   public static void popMatrix() {
      GlStateManager.popMatrix();
   }

   public static void emitLine(MatrixStack matrixStack, WorldRenderer buffer, Vector2f p1, Vector2f p2, int color, float lineWidth) {
      emitLine(matrixStack, buffer, new Vector3f(p1.x, p1.y, 0.0F), new Vector3f(p2.x, p2.y, 0.0F), color, lineWidth);
   }

   public static void emitLine(MatrixStack matrixStack, WorldRenderer buffer, Vector3f p1, Vector3f p2, int color, float lineWidth) {
      GL11.glLineWidth(lineWidth);
      int r = color >> 24 & 0xFF;
      int g = color >> 16 & 0xFF;
      int b = color >> 8 & 0xFF;
      int a = color & 0xFF;
      buffer.pos(p1.x, p1.y, p1.z).color(r, g, b, a);
      buffer.endVertex();
      buffer.pos(p2.x, p2.y, p2.z).color(r, g, b, a);
      buffer.endVertex();
   }

   public static void bindTexture(ResourceLocation id) {
      com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer.bindTexture(id);
   }

   public static boolean hasOptifine() {
      try {
         return Class.forName("net.optifine.Config") != null;
      } catch (ClassNotFoundException e) {
         return false;
      }
   }

   public interface ChunkRenderWorkerAccessor {
      void doRunTask(ChunkCompileTaskGenerator var1) throws InterruptedException;
   }

   public abstract static class Keyboard {
      public static final int KEY_LCONTROL = 29;
      public static final int KEY_LSHIFT = 42;
      public static final int KEY_ESCAPE = 1;
      public static final int KEY_HOME = 199;
      public static final int KEY_END = 207;
      public static final int KEY_UP = 200;
      public static final int KEY_DOWN = 208;
      public static final int KEY_LEFT = 203;
      public static final int KEY_RIGHT = 205;
      public static final int KEY_BACK = 14;
      public static final int KEY_DELETE = 211;
      public static final int KEY_RETURN = 28;
      public static final int KEY_TAB = 15;
      public static final int KEY_F1 = 59;
      public static final int KEY_A = 30;
      public static final int KEY_B = 48;
      public static final int KEY_C = 46;
      public static final int KEY_D = 32;
      public static final int KEY_E = 18;
      public static final int KEY_F = 33;
      public static final int KEY_G = 34;
      public static final int KEY_H = 35;
      public static final int KEY_I = 23;
      public static final int KEY_J = 36;
      public static final int KEY_K = 37;
      public static final int KEY_L = 38;
      public static final int KEY_M = 50;
      public static final int KEY_N = 49;
      public static final int KEY_O = 24;
      public static final int KEY_P = 25;
      public static final int KEY_Q = 16;
      public static final int KEY_R = 19;
      public static final int KEY_S = 31;
      public static final int KEY_T = 20;
      public static final int KEY_U = 22;
      public static final int KEY_V = 47;
      public static final int KEY_W = 17;
      public static final int KEY_X = 45;
      public static final int KEY_Y = 21;
      public static final int KEY_Z = 44;

      public static boolean isKeyDown(int keyCode) {
         return org.lwjglx.input.Keyboard.isKeyDown(keyCode);
      }

      public static int getEventKey() {
         return org.lwjglx.input.Keyboard.getEventKey();
      }

      public static boolean getEventKeyState() {
         return org.lwjglx.input.Keyboard.getEventKeyState();
      }

      public static String getKeyName(int code) {
         return org.lwjglx.input.Keyboard.getKeyName(code);
      }
   }

   public interface MinecraftMethodAccessor {
      void replayModSetEarlyReturnFromRunTick(boolean var1);
   }

   public static class SoundEvent {
   }
}
