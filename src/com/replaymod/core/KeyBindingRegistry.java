package com.replaymod.core;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.replaymod.core.events.KeyBindingEventCallback;
import com.replaymod.core.events.KeyEventCallback;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.core.mixin.KeyBindingAccessor;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ReportedException;
import org.lwjglx.input.Keyboard;

public class KeyBindingRegistry extends EventRegistrations {
   private static final String CATEGORY = "replaymod.title";
   private final Map<String, KeyBindingRegistry.Binding> bindings = new HashMap<>();
   private Set<KeyBinding> onlyInReplay = new HashSet<>();
   private Multimap<Integer, Function<KeyInput, Boolean>> rawHandlers = ArrayListMultimap.create();

   public KeyBindingRegistry() {
      this.on(PreRenderCallback.EVENT, this::handleRepeatedKeyBindings);
      this.on(KeyBindingEventCallback.EVENT, this::handleKeyBindings);
      this.on(KeyEventCallback.EVENT, this::handleRaw);
   }

   public KeyBindingRegistry.Binding registerKeyBinding(String name, int keyCode, Runnable whenPressed, boolean onlyInRepay) {
      KeyBindingRegistry.Binding binding = this.registerKeyBinding(name, keyCode, onlyInRepay);
      binding.handlers.add(whenPressed);
      return binding;
   }

   public KeyBindingRegistry.Binding registerRepeatedKeyBinding(String name, int keyCode, Runnable whenPressed, boolean onlyInRepay) {
      KeyBindingRegistry.Binding binding = this.registerKeyBinding(name, keyCode, onlyInRepay);
      binding.repeatedHandlers.add(whenPressed);
      return binding;
   }

   private KeyBindingRegistry.Binding registerKeyBinding(String name, int keyCode, boolean onlyInRepay) {
      KeyBindingRegistry.Binding binding = this.bindings.get(name);
      if (binding == null) {
         KeyBinding keyBinding = new KeyBinding(name, keyCode, "replaymod.title");
         net.minecraft.client.Minecraft.getMinecraft().gameSettings.keyBindings =
            org.apache.commons.lang3.ArrayUtils.add(net.minecraft.client.Minecraft.getMinecraft().gameSettings.keyBindings, keyBinding);
         binding = new KeyBindingRegistry.Binding(name, keyBinding);
         this.bindings.put(name, binding);
         if (onlyInRepay) {
            this.onlyInReplay.add(keyBinding);
         }
      } else if (!onlyInRepay) {
         this.onlyInReplay.remove(binding.keyBinding);
      }

      return binding;
   }

   public void registerRaw(int keyCode, Function<KeyInput, Boolean> whenPressed) {
      this.rawHandlers.put(keyCode, whenPressed);
   }

   public Map<String, KeyBindingRegistry.Binding> getBindings() {
      return Collections.unmodifiableMap(this.bindings);
   }

   public Set<KeyBinding> getOnlyInReplay() {
      return Collections.unmodifiableSet(this.onlyInReplay);
   }

   public void handleRepeatedKeyBindings() {
      for (KeyBindingRegistry.Binding binding : this.bindings.values()) {
         if (binding.keyBinding.isKeyDown()) {
            this.invokeKeyBindingHandlers(binding, binding.repeatedHandlers);
         }
      }
   }

   private void handleKeyBindings() {
      for (KeyBindingRegistry.Binding binding : this.bindings.values()) {
         while (binding.keyBinding.isPressed()) {
            this.invokeKeyBindingHandlers(binding, binding.handlers);
            this.invokeKeyBindingHandlers(binding, binding.repeatedHandlers);
         }
      }
   }

   private void invokeKeyBindingHandlers(KeyBindingRegistry.Binding binding, Collection<Runnable> handlers) {
      for (Runnable runnable : handlers) {
         try {
            runnable.run();
         } catch (Throwable cause) {
            CrashReport crashReport = CrashReport.makeCrashReport(cause, "Handling Key Binding");
            CrashReportCategory category = crashReport.makeCategory("Key Binding");
            category.addCrashSectionCallable("Key Binding", () -> binding.name);
            category.addCrashSectionCallable("Handler", runnable::toString);
            throw new ReportedException(crashReport);
         }
      }
   }

   private boolean handleRaw(KeyInput keyInput, int action) {
      if (action != 1) {
         return false;
      }

      for (Function<KeyInput, Boolean> handler : this.rawHandlers.get(keyInput.key)) {
         try {
            if (handler.apply(keyInput)) {
               return true;
            }
         } catch (Throwable cause) {
            CrashReport crashReport = CrashReport.makeCrashReport(cause, "Handling Raw Key Binding");
            CrashReportCategory category = crashReport.makeCategory("Key Binding");
            category.addCrashSectionCallable("Key Code", () -> "" + keyInput.key);
            category.addCrashSectionCallable("Handler", handler::toString);
            throw new ReportedException(crashReport);
         }
      }

      return false;
   }

   /**
    * Fires the ReplayMod key bindings for a raw key press without touching the
    * vanilla bindings. KeyBinding.onTick would also bump the vanilla chat
    * binding's pressTime, which opens the chat box when the chat key is
    * pressed - not wanted while the replay overlay input screen is up.
    *
    * <p>The overlay input screen needs this because while it is current,
    * GuiScreen.handleInput consumes key events before the runTick keyboard
    * loop (the normal KeyBinding.onTick path) ever sees them, so the replay
    * keybinds (P play/pause, camera roll, keyframes, ...) would otherwise die
    * as soon as the mouse is visible.
    */
   public void handleKeyEvent(int keyCode) {
      if (KeyEventCallback.EVENT.invoker().onKeyEvent(new KeyInput(keyCode), 1)) {
         return;
      }

      for (KeyBindingRegistry.Binding binding : this.bindings.values()) {
         if (binding.keyBinding.getKeyCode() == keyCode) {
            binding.trigger();
         }
      }
   }

   public class Binding {
      public final String name;
      public final KeyBinding keyBinding;
      private final List<Runnable> handlers = new ArrayList<>();
      private final List<Runnable> repeatedHandlers = new ArrayList<>();
      private boolean autoActivation;
      private Consumer<Boolean> autoActivationUpdate;

      public Binding(String name, KeyBinding keyBinding) {
         this.name = name;
         this.keyBinding = keyBinding;
      }

      public String getBoundKey() {
         try {
            return Keyboard.getKeyName(this.keyBinding.getKeyCode());
         } catch (ArrayIndexOutOfBoundsException e) {
            return "Unknown";
         }
      }

      public boolean isBound() {
         return this.keyBinding.getKeyCode() != 0;
      }

      public void trigger() {
         KeyBindingAccessor acc = (KeyBindingAccessor)this.keyBinding;
         acc.setPressTime(acc.getPressTime() + 1);
         KeyBindingRegistry.this.handleKeyBindings();
      }

      public void registerAutoActivationSupport(boolean active, Consumer<Boolean> update) {
         this.autoActivation = active;
         this.autoActivationUpdate = update;
      }

      public boolean supportsAutoActivation() {
         return this.autoActivationUpdate != null;
      }

      public boolean isAutoActivating() {
         return this.supportsAutoActivation() && this.autoActivation;
      }

      public void setAutoActivating(boolean active) {
         if (this.autoActivation != active) {
            this.autoActivation = active;
            this.autoActivationUpdate.accept(active);
         }
      }
   }
}
