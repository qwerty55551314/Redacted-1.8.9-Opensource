package com.replaymod.compat.shaders;

import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.render.hooks.EntityRendererHandler;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.client.Minecraft;

public class ShaderBeginRender extends EventRegistrations {
   private final Minecraft mc = Minecraft.getMinecraft();

   public ShaderBeginRender() {
      this.on(PreRenderCallback.EVENT, this::onRenderTickStart);
   }

   private void onRenderTickStart() {
      if (ShaderReflection.shaders_beginRender != null) {
         if (ShaderReflection.config_isShaders != null) {
            try {
               if (((EntityRendererHandler.IEntityRenderer)this.mc.entityRenderer).replayModRender_getHandler() == null) {
                  return;
               }

               if (!(Boolean)ShaderReflection.config_isShaders.invoke(null)) {
                  return;
               }

               ShaderReflection.shaders_beginRender.invoke(null, this.mc, ((MinecraftAccessor)this.mc).getTimer().renderPartialTicks, 0);
            } catch (IllegalAccessException | InvocationTargetException e) {
               e.printStackTrace();
            }
         }
      }
   }
}
