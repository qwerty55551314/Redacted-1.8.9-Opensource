package com.replaymod.render;

import com.google.gson.annotations.SerializedName;
import com.replaymod.core.utils.FileTypeAdapter;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.Util;

public class RenderSettings {
   private final RenderSettings.RenderMethod renderMethod;
   private final RenderSettings.EncodingPreset encodingPreset;
   private final int videoWidth;
   private final int videoHeight;
   private final int framesPerSecond;
   private final int bitRate;
   private final File outputFile;
   private final boolean renderNameTags;
   private final boolean includeAlphaChannel;
   private final boolean stabilizeYaw;
   private final boolean stabilizePitch;
   private final boolean stabilizeRoll;
   private final Color chromaKeyingColor;
   private final int sphericalFovX;
   private final int sphericalFovY;
   private final boolean injectSphericalMetadata;
   private final boolean depthMap;
   private final boolean cameraPathExport;
   private final RenderSettings.AntiAliasing antiAliasing;
   private final String exportCommand;
   @SerializedName("exportArguments")
   private final String exportArgumentsPreBgra = "";
   @SerializedName("exportArgumentsBgra")
   private final String exportArguments;
   private final boolean highPerformance;

   public RenderSettings() {
      this(
         RenderSettings.RenderMethod.DEFAULT,
         RenderSettings.EncodingPreset.MP4_CUSTOM,
         1920,
         1080,
         60,
         20971520,
         null,
         true,
         false,
         false,
         false,
         false,
         null,
         360,
         180,
         false,
         false,
         false,
         RenderSettings.AntiAliasing.NONE,
         "",
         RenderSettings.EncodingPreset.MP4_CUSTOM.getValue(),
         false
      );
   }

   public RenderSettings(
      RenderSettings.RenderMethod renderMethod,
      RenderSettings.EncodingPreset encodingPreset,
      int videoWidth,
      int videoHeight,
      int framesPerSecond,
      int bitRate,
      File outputFile,
      boolean renderNameTags,
      boolean includeAlphaChannel,
      boolean stabilizeYaw,
      boolean stabilizePitch,
      boolean stabilizeRoll,
      ReadableColor chromaKeyingColor,
      int sphericalFovX,
      int sphericalFovY,
      boolean injectSphericalMetadata,
      boolean depthMap,
      boolean cameraPathExport,
      RenderSettings.AntiAliasing antiAliasing,
      String exportCommand,
      String exportArguments,
      boolean highPerformance
   ) {
      this.renderMethod = renderMethod;
      this.encodingPreset = encodingPreset;
      this.videoWidth = videoWidth;
      this.videoHeight = videoHeight;
      this.framesPerSecond = framesPerSecond;
      this.bitRate = bitRate;
      this.outputFile = outputFile;
      this.renderNameTags = renderNameTags;
      this.includeAlphaChannel = includeAlphaChannel;
      this.stabilizeYaw = stabilizeYaw;
      this.stabilizePitch = stabilizePitch;
      this.stabilizeRoll = stabilizeRoll;
      this.chromaKeyingColor = chromaKeyingColor == null ? null : new Color(chromaKeyingColor);
      this.sphericalFovX = sphericalFovX;
      this.sphericalFovY = sphericalFovY;
      this.injectSphericalMetadata = injectSphericalMetadata;
      this.depthMap = depthMap;
      this.cameraPathExport = cameraPathExport;
      this.antiAliasing = antiAliasing;
      this.exportCommand = exportCommand;
      this.exportArguments = exportArguments;
      this.highPerformance = highPerformance;
   }

   public RenderSettings withEncodingPreset(RenderSettings.EncodingPreset encodingPreset) {
      return new RenderSettings(
         this.renderMethod,
         encodingPreset,
         this.videoWidth,
         this.videoHeight,
         this.framesPerSecond,
         this.bitRate,
         this.outputFile,
         this.renderNameTags,
         this.includeAlphaChannel,
         this.stabilizeYaw,
         this.stabilizePitch,
         this.stabilizeRoll,
         this.chromaKeyingColor,
         this.sphericalFovX,
         this.sphericalFovY,
         this.injectSphericalMetadata,
         this.depthMap,
         this.cameraPathExport,
         this.antiAliasing,
         this.exportCommand,
         this.exportArguments,
         this.highPerformance
      );
   }

   public int getVideoWidth() {
      return this.videoWidth * this.antiAliasing.getFactor();
   }

   public int getVideoHeight() {
      return this.videoHeight * this.antiAliasing.getFactor();
   }

   public int getTargetVideoWidth() {
      return this.videoWidth;
   }

   public int getTargetVideoHeight() {
      return this.videoHeight;
   }

   public String getVideoFilters() {
      StringBuilder filters = new StringBuilder();
      if (this.antiAliasing != RenderSettings.AntiAliasing.NONE) {
         double factor = 1.0 / this.antiAliasing.getFactor();
         filters.append(String.format("-filter:v scale=iw*%1$s:ih*%1$s ", factor));
      }

      return filters.toString();
   }

   public String getExportCommandOrDefault() {
      return this.exportCommand.isEmpty() ? findFFmpeg() : this.exportCommand;
   }

   private static String findFFmpeg() {
      switch (Util.getOSType()) {
         case WINDOWS:
            File dotMinecraft = MCVer.getMinecraft().mcDataDir;
            File inDotMinecraft = new File(dotMinecraft, "ffmpeg/bin/ffmpeg.exe");
            if (inDotMinecraft.exists()) {
               ReplayModRender.LOGGER.debug("FFmpeg found in .minecraft/ffmpeg");
               return inDotMinecraft.getAbsolutePath();
            }

            try {
               final Path[] result = new Path[1];
               Files.walkFileTree(dotMinecraft.toPath(), new SimpleFileVisitor<Path>() {
                  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                     if ("ffmpeg.exe".equals(file.getFileName().toString())) {
                        result[0] = file;
                        return FileVisitResult.TERMINATE;
                     } else {
                        return super.visitFile(file, attrs);
                     }
                  }
               });
               if (result[0] != null) {
                  return result[0].toAbsolutePath().toString();
               }
            } catch (IOException e) {
               ReplayModRender.LOGGER.debug("Error searching .minecraft for ffmpeg.exe:", e);
            }
            break;
         case OSX:
            for (String path : new String[]{"/usr/local/bin/ffmpeg", "/usr/bin/ffmpeg"}) {
               File file = new File(path);
               if (file.exists()) {
                  ReplayModRender.LOGGER.debug("Found FFmpeg at {}", new Object[]{path});
                  return path;
               }

               ReplayModRender.LOGGER.debug("FFmpeg not located at {}", new Object[]{path});
            }

            for (String path : new String[]{"/usr/local", "/opt/homebrew"}) {
               File homebrewFolder = new File(path + "/Cellar/ffmpeg");
               String[] homebrewVersions = homebrewFolder.list();
               if (homebrewVersions != null) {
                  Optional<File> latestOpt = Arrays.stream(homebrewVersions)
                     .sorted(Comparator.reverseOrder())
                     .map(v -> new File(new File(homebrewFolder, v), "bin/ffmpeg"))
                     .filter(File::exists)
                     .findFirst();
                  if (latestOpt.isPresent()) {
                     File latest = latestOpt.get();
                     ReplayModRender.LOGGER
                        .debug("Found {} versions of FFmpeg installed with homebrew, chose {}", new Object[]{homebrewVersions.length, latest});
                     return latest.getAbsolutePath();
                  }
               }
            }
         case LINUX:
         case SOLARIS:
         case UNKNOWN:
      }

      ReplayModRender.LOGGER.debug("Using default FFmpeg executable");
      return "ffmpeg";
   }

   public RenderSettings.RenderMethod getRenderMethod() {
      return this.renderMethod;
   }

   public RenderSettings.EncodingPreset getEncodingPreset() {
      return this.encodingPreset;
   }

   public int getFramesPerSecond() {
      return this.framesPerSecond;
   }

   public int getBitRate() {
      return this.bitRate;
   }

   public File getOutputFile() {
      return this.outputFile;
   }

   public boolean isRenderNameTags() {
      return this.renderNameTags;
   }

   public boolean isIncludeAlphaChannel() {
      return this.includeAlphaChannel;
   }

   public boolean isStabilizeYaw() {
      return this.stabilizeYaw;
   }

   public boolean isStabilizePitch() {
      return this.stabilizePitch;
   }

   public boolean isStabilizeRoll() {
      return this.stabilizeRoll;
   }

   public ReadableColor getChromaKeyingColor() {
      return this.chromaKeyingColor;
   }

   public int getSphericalFovX() {
      return this.sphericalFovX;
   }

   public int getSphericalFovY() {
      return this.sphericalFovY;
   }

   public boolean isInjectSphericalMetadata() {
      return this.injectSphericalMetadata;
   }

   public boolean isDepthMap() {
      return this.depthMap;
   }

   public boolean isCameraPathExport() {
      return this.cameraPathExport;
   }

   public RenderSettings.AntiAliasing getAntiAliasing() {
      return this.antiAliasing;
   }

   public String getExportCommand() {
      return this.exportCommand;
   }

   public String getExportArguments() {
      return this.exportArguments;
   }

   public boolean isHighPerformance() {
      return this.highPerformance;
   }

   @Override
   public String toString() {
      return ("RenderSettings{renderMethod="
         + this.renderMethod
         + ", encodingPreset="
         + this.encodingPreset
         + ", videoWidth="
         + this.videoWidth
         + ", videoHeight="
         + this.videoHeight
         + ", framesPerSecond="
         + this.framesPerSecond
         + ", bitRate="
         + this.bitRate
         + ", outputFile="
         + this.outputFile
         + ", renderNameTags="
         + this.renderNameTags
         + ", includeAlphaChannel="
         + this.includeAlphaChannel
         + ", stabilizeYaw="
         + this.stabilizeYaw
         + ", stabilizePitch="
         + this.stabilizePitch
         + ", stabilizeRoll="
         + this.stabilizeRoll
         + ", chromaKeyingColor="
         + this.chromaKeyingColor
         + ", sphericalFovX="
         + this.sphericalFovX
         + ", sphericalFovY="
         + this.sphericalFovY
         + ", injectSphericalMetadata="
         + this.injectSphericalMetadata
         + ", depthMap="
         + this.depthMap
         + ", cameraPathExport="
         + this.cameraPathExport
         + ", antiAliasing="
         + this.antiAliasing
         + ", exportCommand='"
         + this.exportCommand
         + '\''
         + ", exportArgumentsPreBgra='"
         + ""
         + '\''
         + ", exportArguments='"
         + this.exportArguments
         + '\''
         + ", highPerformance="
         + this.highPerformance
         + '}');
   }

   public enum AntiAliasing {
      NONE(1),
      X2(2),
      X4(4),
      X8(8);

      private final int factor;

      AntiAliasing(int factor) {
         this.factor = factor;
      }

      public int getFactor() {
         return this.factor;
      }

      @Override
      public String toString() {
         return I18n.format("replaymod.gui.rendersettings.antialiasing." + this.name().toLowerCase(), new Object[0]);
      }
   }

   public enum EncodingPreset {
      MP4_CUSTOM("-an -c:v libx264 -b:v %BITRATE% -pix_fmt yuv420p \"%FILENAME%\"", "mp4"),
      MP4_POTATO("-an -c:v libx264 -preset ultrafast -crf 51 -pix_fmt yuv420p \"%FILENAME%\"", "mp4"),
      WEBM_CUSTOM("-an -c:v libvpx -b:v %BITRATE% -pix_fmt yuv420p \"%FILENAME%\"", "webm"),
      MKV_LOSSLESS("-an -c:v libx264 -preset ultrafast -qp 0 \"%FILENAME%\"", "mkv"),
      BLEND(null, "blend"),
      EXR(null, "exr"),
      PNG(null, "png");

      private final String preset;
      private final String fileExtension;

      EncodingPreset(String preset, String fileExtension) {
         this.preset = preset;
         this.fileExtension = fileExtension;
      }

      public String getValue() {
         return "-y -f rawvideo -pix_fmt bgra -s %WIDTH%x%HEIGHT% -r %FPS% -i - %FILTERS%" + this.preset;
      }

      public String getFileExtension() {
         return this.fileExtension;
      }

      public boolean hasBitrateSetting() {
         return this.preset != null && this.preset.contains("%BITRATE%");
      }

      public boolean isYuv420() {
         return this.preset != null && this.preset.contains("-pix_fmt yuv420p");
      }

      @Override
      public String toString() {
         return I18n.format("replaymod.gui.rendersettings.presets." + this.name().replace('_', '.').toLowerCase(), new Object[0]);
      }

      public boolean isSupported() {
         return this == BLEND ? RenderSettings.RenderMethod.BLEND.isSupported() : true;
      }

      public static RenderSettings.EncodingPreset[] getSupported() {
         return Arrays.stream(values()).filter(RenderSettings.EncodingPreset::isSupported).toArray(RenderSettings.EncodingPreset[]::new);
      }
   }

   public enum RenderMethod {
      DEFAULT,
      STEREOSCOPIC,
      CUBIC,
      EQUIRECTANGULAR,
      ODS,
      BLEND;

      @Override
      public String toString() {
         return I18n.format("replaymod.gui.rendersettings.renderer." + this.name().toLowerCase(), new Object[0]);
      }

      public String getDescription() {
         return I18n.format("replaymod.gui.rendersettings.renderer." + this.name().toLowerCase() + ".description", new Object[0]);
      }

      public boolean isSpherical() {
         return this == EQUIRECTANGULAR || this == ODS;
      }

      public boolean hasFixedAspectRatio() {
         return this == EQUIRECTANGULAR || this == ODS || this == CUBIC;
      }

      public boolean isSupported() {
         return true;
      }

      public static RenderSettings.RenderMethod[] getSupported() {
         return Arrays.stream(values()).filter(RenderSettings.RenderMethod::isSupported).toArray(RenderSettings.RenderMethod[]::new);
      }
   }
}
