package com.replaymod.pathing.properties;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replaystudio.pathing.property.AbstractProperty;
import com.replaymod.replaystudio.pathing.property.PropertyPart;
import com.replaymod.replaystudio.pathing.property.PropertyParts;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

public class SpectatorProperty extends AbstractProperty<Integer> {
   public static final SpectatorProperty PROPERTY = new SpectatorProperty();
   public final PropertyPart<Integer> ENTITY_ID = new PropertyParts.ForInteger(this, false);

   private SpectatorProperty() {
      super("spectate", "replaymod.gui.playeroverview.spectate", null, -1);
   }

   @Override
   public Collection<PropertyPart<Integer>> getParts() {
      return Collections.singletonList(this.ENTITY_ID);
   }

   public void applyToGame(Integer value, Object replayHandler) {
      ReplayHandler handler = (ReplayHandler)replayHandler;
      CameraEntity cameraEntity = handler.getCameraEntity();
      if (cameraEntity != null) {
         World world = cameraEntity.getEntityWorld();
         Entity target = world.getEntityByID(value);
         handler.spectateEntity(target);
      }
   }

   public void toJson(JsonWriter writer, Integer value) throws IOException {
      writer.value(value);
   }

   public Integer fromJson(JsonReader reader) throws IOException {
      return reader.nextInt();
   }
}
