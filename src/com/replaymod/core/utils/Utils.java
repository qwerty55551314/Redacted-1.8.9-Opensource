package com.replaymod.core.utils;

import com.google.common.base.Throwables;
import com.google.common.net.PercentEscaper;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScrollable;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScrollable;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.GuiInfoPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.Image;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import net.minecraft.crash.CrashReport;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjglx.input.Keyboard;

public class Utils {
   private static Logger LOGGER = LogManager.getLogger();
   public static final float DEFAULT_MS_PER_TICK = 50.0F;
   public static final Image DEFAULT_THUMBNAIL;
   public static final SSLContext SSL_CONTEXT;
   public static final SSLSocketFactory SSL_SOCKET_FACTORY;
   private static final PercentEscaper REPLAY_NAME_ENCODER;

   private static InputStream getResourceAsStream(String path) {
      return Utils.class.getResourceAsStream(path);
   }

   public static String convertSecondsToShortString(int seconds) {
      int hours = seconds / 3600;
      int min = seconds / 60 - hours * 60;
      int sec = seconds - (min * 60 + hours * 60 * 60);
      StringBuilder builder = new StringBuilder();
      if (hours > 0) {
         builder.append(String.format("%02d", hours)).append(":");
      }

      builder.append(String.format("%02d", min)).append(":");
      builder.append(String.format("%02d", sec));
      return builder.toString();
   }

   public static Dimension fitIntoBounds(ReadableDimension toFit, ReadableDimension bounds) {
      int width = toFit.getWidth();
      int height = toFit.getHeight();
      float w = (float)width / bounds.getWidth();
      float h = (float)height / bounds.getHeight();
      if (w > h) {
         height = (int)(height / w);
         width = (int)(width / w);
      } else {
         height = (int)(height / h);
         width = (int)(width / h);
      }

      return new Dimension(width, height);
   }

   public static boolean isValidEmailAddress(String mail) {
      return mail.matches("^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$");
   }

   public static Path replayNameToPath(Path folder, String replayName) {
      return isUsable(folder, replayName + ".mcpr") ? folder.resolve(replayName + ".mcpr") : folder.resolve(REPLAY_NAME_ENCODER.escape(replayName) + ".mcpr");
   }

   private static boolean isUsable(Path folder, String fileName) {
      if (fileName.contains(folder.getFileSystem().getSeparator())) {
         return false;
      }

      Path path;
      try {
         path = folder.resolve(fileName);
      } catch (InvalidPathException e) {
         return false;
      }

      if (Files.exists(path)) {
         return true;
      }

      try (OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
         outputStream.flush();
      } catch (IOException e) {
         return false;
      }

      int attempts = 0;

      while (true) {
         try {
            Files.delete(path);
            return true;
         } catch (IOException e) {
            if (attempts++ > 100) {
               LOGGER.warn("Repeatedly failed to clean up temporary test file at " + path + ": ", e);
               return false;
            }
         }
      }
   }

   public static String fileNameToReplayName(String fileName) {
      String baseName = FilenameUtils.getBaseName(fileName);

      try {
         return URLDecoder.decode(baseName, Charsets.UTF_8.name());
      } catch (IllegalArgumentException e) {
         return baseName;
      } catch (UnsupportedEncodingException e) {
         throw Throwables.propagate(e);
      }
   }

   public static boolean isCtrlDown() {
      return Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157);
   }

   public static <T> void addCallback(ListenableFuture<T> future, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
      Futures.addCallback(future, new FutureCallback<T>() {
         public void onSuccess(@Nullable T result) {
            onSuccess.accept(result);
         }

         public void onFailure(@Nonnull Throwable t) {
            onFailure.accept(t);
         }
      });
   }

   public static GuiInfoPopup error(Logger logger, GuiContainer container, CrashReport crashReport, Runnable onClose) {
      String crashReportStr = crashReport.getCompleteReport();
      logger.error(crashReportStr);
      if (crashReport.getFile() == null) {
         try {
            File folder = new File(MCVer.getMinecraft().mcDataDir, "crash-reports");
            File file = new File(folder, "crash-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + "-client.txt");
            logger.debug("Saving crash report to file: {}", new Object[]{file});
            crashReport.saveToFile(file);
         } catch (Throwable t) {
            logger.error("Saving crash report file:", t);
         }
      } else {
         logger.debug("Not saving crash report as file already exists: {}", new Object[]{crashReport.getFile()});
      }

      logger.trace("Opening crash report popup GUI");
      Utils.GuiCrashReportPopup popup = new Utils.GuiCrashReportPopup(container, crashReportStr);
      popup.onClosed(() -> {
         logger.trace("Crash report popup closed");
         if (onClose != null) {
            onClose.run();
         }
      });
      return popup;
   }

   public static <T extends Throwable> void throwIfInstanceOf(Throwable t, Class<T> cls) throws T {
      if (cls.isInstance(t)) {
         throw cls.cast(t);
      }
   }

   public static void throwIfUnchecked(Throwable t) {
      if (t instanceof RuntimeException) {
         throw (RuntimeException)t;
      }

      if (t instanceof Error) {
         throw (Error)t;
      }
   }

   public static void denyIfMinimalMode(GuiContainer container, Runnable onPopupClosed, Runnable orElseRun) {
      if (isNotMinimalModeElsePopup(container, onPopupClosed)) {
         orElseRun.run();
      }
   }

   public static boolean ifMinimalModeDoPopup(GuiContainer container, Runnable onPopupClosed) {
      return !isNotMinimalModeElsePopup(container, onPopupClosed);
   }

   public static boolean isNotMinimalModeElsePopup(GuiContainer container, Runnable onPopupClosed) {
      if (!ReplayMod.isMinimalMode()) {
         LOGGER.trace("Minimal mode not active, continuing");
         return true;
      } else {
         LOGGER.trace("Minimal mode active, denying action, opening popup");
         Utils.MinimalModeUnsupportedPopup popup = new Utils.MinimalModeUnsupportedPopup(container);
         popup.onClosed(() -> {
            LOGGER.trace("Minimal mode popup closed");
            if (onPopupClosed != null) {
               onPopupClosed.run();
            }
         });
         return false;
      }
   }

   public static <T> T configure(T instance, Consumer<T> configure) {
      configure.accept(instance);
      return instance;
   }

   public static Path ensureDirectoryExists(Path path) throws IOException {
      return Files.createDirectories(Files.exists(path) ? path.toRealPath() : path);
   }

   static {
      Image thumbnail;
      try {
         thumbnail = Image.read(getResourceAsStream("/default_thumb.png"));
      } catch (Exception e) {
         thumbnail = new Image(1, 1);
         e.printStackTrace();
      }

      DEFAULT_THUMBNAIL = thumbnail;

      try (InputStream in = getResourceAsStream("/dst_root_ca_x3.pem")) {
         Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(in);
         KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
         keyStore.load(null, null);
         keyStore.setCertificateEntry("1", certificate);
         TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
         trustManagerFactory.init(keyStore);
         SSLContext ctx = SSLContext.getInstance("TLS");
         ctx.init(null, trustManagerFactory.getTrustManagers(), null);
         SSL_CONTEXT = ctx;
         SSL_SOCKET_FACTORY = ctx.getSocketFactory();
      } catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
         throw new RuntimeException(e);
      }

      REPLAY_NAME_ENCODER = new PercentEscaper(".-_ ", false);
   }

   private static class GuiCrashReportPopup extends GuiInfoPopup {
      private final GuiScrollable scrollable;

      public GuiCrashReportPopup(GuiContainer container, String crashReport) {
         super(container);
         this.setBackgroundColor(Colors.DARK_TRANSPARENT);
         this.getInfo()
            .addElements(
               new VerticalLayout.Data(0.5),
               new GuiLabel().setColor(Colors.BLACK).setI18nText("replaymod.gui.unknownerror"),
               this.scrollable = new GuiScrollable()
                  .setScrollDirection(AbstractGuiScrollable.Direction.VERTICAL)
                  .setLayout(new VerticalLayout().setSpacing(2))
                  .addElements(
                     null,
                     Arrays.stream(crashReport.replace("\t", "    ").split("\n"))
                        .map(l -> new GuiLabel().setText(l).setColor(Colors.BLACK))
                        .toArray(GuiElement[]::new)
                  )
            );
         GuiButton copyToClipboardButton = new GuiButton()
            .setI18nLabel("chat.copy")
            .onClick(() -> com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer.setClipboardString(crashReport))
            .setSize(150, 20);
         GuiButton closeButton = this.getCloseButton();
         this.popup.removeElement(closeButton);
         this.popup
            .addElements(
               new VerticalLayout.Data(1.0),
               new GuiPanel().setLayout(new HorizontalLayout().setSpacing(5)).setSize(305, 20).addElements(null, copyToClipboardButton, closeButton)
            );
         this.open();
      }

      @Override
      public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
         this.scrollable.setSize(size.getWidth() * 3 / 4, size.getHeight() * 3 / 4);
         super.draw(renderer, size, renderInfo);
      }
   }

   private static class MinimalModeUnsupportedPopup extends GuiInfoPopup {
      private MinimalModeUnsupportedPopup(GuiContainer container) {
         super(container);
         this.setBackgroundColor(Colors.DARK_TRANSPARENT);
         ProtocolVersion latestVersion = ProtocolVersion.getProtocols()
            .stream()
            .max(Comparator.comparing(ProtocolVersion::getVersion))
            .orElseThrow(RuntimeException::new);
         this.getInfo()
            .addElements(
               new VerticalLayout.Data(0.5),
               new GuiLabel().setColor(Colors.BLACK).setI18nText("replaymod.gui.minimalmode.unsupported"),
               new GuiLabel().setColor(Colors.BLACK).setI18nText("replaymod.gui.minimalmode.supportedversion", "1.7.10 - " + latestVersion.getName())
            );
         this.open();
      }
   }
}
