package com.replaymod.pathing.properties;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.NonNull;
import com.replaymod.lib.org.apache.commons.lang3.tuple.Triple;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replaystudio.pathing.change.Change;
import com.replaymod.replaystudio.pathing.property.AbstractProperty;
import com.replaymod.replaystudio.pathing.property.AbstractPropertyGroup;
import com.replaymod.replaystudio.pathing.property.PropertyPart;
import com.replaymod.replaystudio.pathing.property.PropertyParts;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Callable;

public class CameraProperties extends AbstractPropertyGroup {
   public static final CameraProperties GROUP = new CameraProperties();
   public static final CameraProperties.Position POSITION = new CameraProperties.Position();
   public static final CameraProperties.Rotation ROTATION = new CameraProperties.Rotation();

   private CameraProperties() {
      super("camera", "replaymod.gui.camera");
   }

   @Override
   public Optional<Callable<Change>> getSetter() {
      return Optional.empty();
   }

   public static class Position extends AbstractProperty<Triple<Double, Double, Double>> {
      public final PropertyPart<Triple<Double, Double, Double>> X = new PropertyParts.ForDoubleTriple(this, true, PropertyParts.TripleElement.LEFT);
      public final PropertyPart<Triple<Double, Double, Double>> Y = new PropertyParts.ForDoubleTriple(this, true, PropertyParts.TripleElement.MIDDLE);
      public final PropertyPart<Triple<Double, Double, Double>> Z = new PropertyParts.ForDoubleTriple(this, true, PropertyParts.TripleElement.RIGHT);

      private Position() {
         super("position", "replaymod.gui.position", CameraProperties.GROUP, Triple.of(0.0, 0.0, 0.0));
      }

      @Override
      public Collection<PropertyPart<Triple<Double, Double, Double>>> getParts() {
         return Arrays.asList(this.X, this.Y, this.Z);
      }

      public void applyToGame(Triple<Double, Double, Double> value, @NonNull Object replayHandler) {
         ReplayHandler handler = (ReplayHandler)replayHandler;
         handler.spectateCamera();
         CameraEntity cameraEntity = handler.getCameraEntity();
         if (cameraEntity != null) {
            cameraEntity.setCameraPosition(value.getLeft(), value.getMiddle(), value.getRight());
         }
      }

      public void toJson(JsonWriter writer, Triple<Double, Double, Double> value) throws IOException {
         writer.beginArray().value(value.getLeft()).value(value.getMiddle()).value(value.getRight()).endArray();
      }

      public Triple<Double, Double, Double> fromJson(JsonReader reader) throws IOException {
         reader.beginArray();

         try {
            return Triple.of(reader.nextDouble(), reader.nextDouble(), reader.nextDouble());
         } finally {
            reader.endArray();
         }
      }
   }

   public static class Rotation extends AbstractProperty<Triple<Float, Float, Float>> {
      public final PropertyPart<Triple<Float, Float, Float>> YAW = new PropertyParts.ForFloatTriple(this, true, 360.0F, PropertyParts.TripleElement.LEFT);
      public final PropertyPart<Triple<Float, Float, Float>> PITCH = new PropertyParts.ForFloatTriple(this, true, 360.0F, PropertyParts.TripleElement.MIDDLE);
      public final PropertyPart<Triple<Float, Float, Float>> ROLL = new PropertyParts.ForFloatTriple(this, true, 360.0F, PropertyParts.TripleElement.RIGHT);

      private Rotation() {
         super("rotation", "replaymod.gui.rotation", CameraProperties.GROUP, Triple.of(0.0F, 0.0F, 0.0F));
      }

      @Override
      public Collection<PropertyPart<Triple<Float, Float, Float>>> getParts() {
         return Arrays.asList(this.YAW, this.PITCH, this.ROLL);
      }

      public void applyToGame(Triple<Float, Float, Float> value, @NonNull Object replayHandler) {
         ReplayHandler handler = (ReplayHandler)replayHandler;
         handler.spectateCamera();
         CameraEntity cameraEntity = handler.getCameraEntity();
         if (cameraEntity != null) {
            cameraEntity.setCameraRotation(value.getLeft(), value.getMiddle(), value.getRight());
         }
      }

      public void toJson(JsonWriter writer, Triple<Float, Float, Float> value) throws IOException {
         writer.beginArray().value(value.getLeft()).value(value.getMiddle()).value(value.getRight()).endArray();
      }

      public Triple<Float, Float, Float> fromJson(JsonReader reader) throws IOException {
         reader.beginArray();

         try {
            return Triple.of((float)reader.nextDouble(), (float)reader.nextDouble(), (float)reader.nextDouble());
         } finally {
            reader.endArray();
         }
      }
   }
}
