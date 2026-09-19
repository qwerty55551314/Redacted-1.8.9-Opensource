package com.replaymod.render.blend;

import com.replaymod.lib.org.blender.utils.BlenderFactory;
import com.replaymod.lib.org.cakelab.blender.io.BlenderFile;
import com.replaymod.render.ReplayModRender;
import com.replaymod.render.blend.data.DScene;
import com.replaymod.render.blend.data.Serializer;
import com.replaymod.render.blend.exporters.ChunkExporter;
import com.replaymod.render.blend.exporters.EntityExporter;
import com.replaymod.render.blend.exporters.ItemExporter;
import com.replaymod.render.blend.exporters.ParticlesExporter;
import com.replaymod.render.blend.exporters.RenderState;
import com.replaymod.render.blend.exporters.TileEntityExporter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ReportedException;
import org.apache.commons.io.output.NullOutputStream;

public class BlendState implements Exporter {
   private static BlendState currentState;
   private final List<Exporter> exporters = new ArrayList<>();
   private final BlenderFile blenderFile;
   private final BlenderFactory factory;
   private final DScene scene = new DScene();
   private final BlendMaterials materials = new BlendMaterials();

   public static void setState(BlendState state) {
      currentState = state;
   }

   public static BlendState getState() {
      return currentState;
   }

   public BlendState(File file) throws IOException {
      this.blenderFile = BlenderFactory.newBlenderFile(file);
      this.factory = new BlenderFactory(this.blenderFile);
      RenderState renderState = new RenderState(this);
      this.register(renderState);
      this.register(new ChunkExporter());
      this.register(new EntityExporter(renderState));
      this.register(new TileEntityExporter(renderState));
      this.register(new ParticlesExporter(renderState));
      this.register(new ItemExporter(renderState));
   }

   public void register(Exporter exporter) {
      this.exporters.add(exporter);
   }

   public <T extends Exporter> T get(Class<T> clazz) {
      for (Exporter exporter : this.exporters) {
         if (clazz.isInstance(exporter)) {
            return clazz.cast(exporter);
         }
      }

      throw new NoSuchElementException("No exporter of type " + clazz);
   }

   @Override
   public void setup() {
      for (Exporter exporter : this.exporters) {
         try {
            exporter.setup();
         } catch (IOException e) {
            CrashReport report = CrashReport.makeCrashReport(e, "Setup of blend exporter");
            CrashReportCategory category = report.makeCategory("Exporter");
            category.addCrashSectionCallable("Exporter", exporter::toString);
            throw new ReportedException(report);
         }
      }
   }

   @Override
   public void tearDown() {
      for (Exporter exporter : this.exporters) {
         try {
            exporter.tearDown();
         } catch (IOException e) {
            CrashReport report = CrashReport.makeCrashReport(e, "Tear down of blend exporter");
            CrashReportCategory category = report.makeCategory("Exporter");
            category.addCrashSectionCallable("Exporter", exporter::toString);
            throw new ReportedException(report);
         }
      }

      try {
         Serializer serializer = new Serializer();
         this.scene.serialize(serializer);
         PrintStream sysout = System.out;

         try {
            System.setOut(new PrintStream(new NullOutputStream()));
            this.blenderFile.write();
         } finally {
            System.setOut(sysout);
         }
      } catch (IOException e) {
         ReplayModRender.LOGGER.error("Exception writing blend file: ", e);
      } finally {
         try {
            this.blenderFile.close();
         } catch (IOException e) {
            ReplayModRender.LOGGER.error("Exception closing blend file: ", e);
         }
      }
   }

   @Override
   public void preFrame(int frame) {
      for (Exporter exporter : this.exporters) {
         try {
            exporter.preFrame(frame);
         } catch (IOException e) {
            CrashReport report = CrashReport.makeCrashReport(e, "Pre frame of blend exporter");
            CrashReportCategory category = report.makeCategory("Exporter");
            category.addCrashSectionCallable("Exporter", exporter::toString);
            category.addCrashSectionCallable("Frame", () -> String.valueOf(frame));
            throw new ReportedException(report);
         }
      }
   }

   @Override
   public void postFrame(int frame) {
      for (Exporter exporter : this.exporters) {
         try {
            exporter.postFrame(frame);
         } catch (IOException e) {
            CrashReport report = CrashReport.makeCrashReport(e, "Post frame of blend exporter");
            CrashReportCategory category = report.makeCategory("Exporter");
            category.addCrashSectionCallable("Exporter", exporter::toString);
            category.addCrashSectionCallable("Frame", () -> String.valueOf(frame));
            throw new ReportedException(report);
         }
      }

      this.scene.endFrame = frame;
   }

   public BlenderFile getBlenderFile() {
      return this.blenderFile;
   }

   public BlenderFactory getFactory() {
      return this.factory;
   }

   public DScene getScene() {
      return this.scene;
   }

   public BlendMaterials getMaterials() {
      return this.materials;
   }
}
