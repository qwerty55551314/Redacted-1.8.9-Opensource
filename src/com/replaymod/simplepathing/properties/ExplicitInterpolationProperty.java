package com.replaymod.simplepathing.properties;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.NonNull;
import com.replaymod.lib.org.apache.commons.lang3.ObjectUtils;
import com.replaymod.replaystudio.pathing.property.AbstractProperty;
import com.replaymod.replaystudio.pathing.property.PropertyPart;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class ExplicitInterpolationProperty extends AbstractProperty<Object> {
   public static final ExplicitInterpolationProperty PROPERTY = new ExplicitInterpolationProperty();

   private ExplicitInterpolationProperty() {
      super("interpolationFixed", "<internal>", null, new Object());
   }

   @Override
   public Collection<PropertyPart<Object>> getParts() {
      return Collections.emptyList();
   }

   @Override
   public void applyToGame(Object value, @NonNull Object replayHandler) {
   }

   @Override
   public void toJson(JsonWriter writer, Object value) throws IOException {
      writer.nullValue();
   }

   @Override
   public Object fromJson(JsonReader reader) throws IOException {
      reader.nextNull();
      return ObjectUtils.NULL;
   }
}
