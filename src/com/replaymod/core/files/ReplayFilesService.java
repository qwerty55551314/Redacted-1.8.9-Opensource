package com.replaymod.core.files;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.gui.RestoreReplayGui;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ZipReplayFile;
import com.replaymod.replaystudio.studio.ReplayStudio;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

public class ReplayFilesService {
   private final ReplayFoldersService folders;
   private final Set<Path> lockedPaths = Collections.newSetFromMap(new ConcurrentHashMap<>());

   public ReplayFilesService(ReplayFoldersService folders) {
      this.folders = folders;
   }

   public ReplayFile open(Path path) throws IOException {
      return this.open(path, path);
   }

   public ReplayFile open(Path input, Path output) throws IOException {
      Path realInput = input != null ? input.toAbsolutePath().normalize() : null;
      Path realOutput = output.toAbsolutePath().normalize();
      if (realInput != null && !this.lockedPaths.add(realInput)) {
         throw new ReplayFilesService.FileLockedException(realInput);
      }

      if (!Objects.equals(realInput, realOutput) && !this.lockedPaths.add(realOutput)) {
         if (realInput != null) {
            this.lockedPaths.remove(realInput);
         }

         throw new ReplayFilesService.FileLockedException(realOutput);
      } else {
         Runnable onClose = () -> {
            if (realInput != null) {
               this.lockedPaths.remove(realInput);
            }

            this.lockedPaths.remove(realOutput);
         };

         ReplayFile replayFile;
         try {
            replayFile = new ZipReplayFile(
               new ReplayStudio(), realInput != null ? realInput.toFile() : null, realOutput.toFile(), this.folders.getCachePathForReplay(realOutput).toFile()
            );
         } catch (IOException e) {
            onClose.run();
            throw e;
         }

         return new ManagedReplayFile(replayFile, onClose);
      }
   }

   public void initialScan(ReplayMod core) {
      try (DirectoryStream<Path> paths = Files.newDirectoryStream(this.folders.getRecordingFolder())) {
         for (Path path : paths) {
            Path destination = this.folders.getReplayFolder().resolve(path.getFileName());
            if (!Files.exists(destination)) {
               Files.move(path, destination);
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
      }

      try (DirectoryStream<Path> paths = Files.newDirectoryStream(this.folders.getReplayFolder())) {
         for (Path path : paths) {
            String name = path.getFileName().toString();
            if (name.endsWith(".mcpr.tmp") && Files.isDirectory(path)) {
               Path original = path.resolveSibling(FilenameUtils.getBaseName(name));
               Path noRecoverMarker = original.resolveSibling(original.getFileName() + ".no_recover");
               if (Files.exists(noRecoverMarker)) {
                  FileUtils.deleteDirectory(path.toFile());
                  Files.delete(noRecoverMarker);
               } else {
                  new RestoreReplayGui(core, GuiScreen.wrap(core.getMinecraft().currentScreen), original.toFile()).display();
               }
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
      }

      new Thread(this::cleanup, "replaymod-cleanup").start();
   }

   private void cleanup() {
      long DAYS = 86400000L;

      try {
         Files.walkFileTree(this.folders.getReplayFolder(), new SimpleFileVisitor<Path>() {
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
               String name = dir.getFileName().toString();
               if (name.endsWith(".mcpr.cache")) {
                  FileUtils.deleteDirectory(dir.toFile());
                  return FileVisitResult.SKIP_SUBTREE;
               } else {
                  return super.preVisitDirectory(dir, attrs);
               }
            }
         });
      } catch (IOException e) {
         e.printStackTrace();
      }

      try (DirectoryStream<Path> paths = Files.newDirectoryStream(this.folders.getRawReplayFolder())) {
         for (Path path : paths) {
            if (Files.getLastModifiedTime(path).toMillis() + 1814400000L < System.currentTimeMillis()) {
               Files.delete(path);
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
      }

      try (DirectoryStream<Path> paths = Files.newDirectoryStream(this.folders.getCacheFolder())) {
         for (Path path : paths) {
            if (Files.isDirectory(path)) {
               Path replay = this.folders.getReplayPathForCache(path);
               long lastModified = Files.getLastModifiedTime(path).toMillis();
               if (lastModified + 604800000L < System.currentTimeMillis() || !Files.exists(replay)) {
                  FileUtils.deleteDirectory(path.toFile());
               }
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
      }

      try (DirectoryStream<Path> paths = Files.newDirectoryStream(this.folders.getReplayFolder())) {
         for (Path path : paths) {
            String name = path.getFileName().toString();
            if (name.endsWith(".mcpr.del") && Files.isDirectory(path)) {
               long lastModified = Files.getLastModifiedTime(path).toMillis();
               if (lastModified + 172800000L < System.currentTimeMillis()) {
                  FileUtils.deleteDirectory(path.toFile());
               }
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
      }

      try (DirectoryStream<Path> paths = Files.newDirectoryStream(this.folders.getReplayFolder())) {
         for (Path path : paths) {
            String name = path.getFileName().toString();
            if (name.endsWith(".no_recover")) {
               Files.delete(path);
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public static class FileLockedException extends IOException {
      public FileLockedException(Path path) {
         super(path.toString());
      }
   }
}
