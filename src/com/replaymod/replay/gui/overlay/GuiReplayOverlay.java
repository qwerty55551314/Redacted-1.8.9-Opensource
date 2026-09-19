package com.replaymod.replay.gui.overlay;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.events.KeyBindingEventCallback;
import com.replaymod.core.events.KeyEventCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiOverlay;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiSlider;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTooltip;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.IGuiTimeline;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.WritablePoint;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.ReplaySender;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import redacted.client.util.debug.DebugSystem;

public class GuiReplayOverlay extends AbstractGuiOverlay<GuiReplayOverlay> {
   private final ReplayModReplay mod = ReplayModReplay.instance;
   public final GuiPanel topPanel = new GuiPanel(this).setLayout(new HorizontalLayout(HorizontalLayout.Alignment.LEFT).setSpacing(5));
   public final GuiButton playPauseButton = (new GuiButton() {
      @Override
      public GuiElement getTooltip(RenderInfo renderInfo) {
         GuiTooltip tooltip = (GuiTooltip)super.getTooltip(renderInfo);
         if (tooltip != null) {
            String label;
            if (this.getSpriteUV().getY() == 0) {
               label = "replaymod.gui.ingame.menu.unpause";
            } else {
               label = "replaymod.gui.ingame.menu.pause";
            }

            tooltip.setText(I18n.format(label, new Object[0]) + " (" + GuiReplayOverlay.this.mod.keyPlayPause.getBoundKey() + ")");
         }

         return tooltip;
      }
   }).setSize(20, 20).setTexture(ReplayMod.TEXTURE, 256).setTooltip(new GuiTooltip());
   public final GuiSlider speedSlider = new GuiSlider().setSize(100, 20).setSteps(37);
   public final GuiMarkerTimeline timeline;
   public final GuiPanel statusIndicatorPanel = new GuiPanel(this)
      .setSize(100, 16)
      .setLayout(new HorizontalLayout(HorizontalLayout.Alignment.RIGHT).setSpacing(5));
   private final GuiReplayOverlay.EventHandler eventHandler = new GuiReplayOverlay.EventHandler();
   private boolean hidden;

   public GuiReplayOverlay(ReplayHandler replayHandler) {
      this.timeline = (new GuiMarkerTimeline(replayHandler) {
         @Override
         public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
            this.setCursorPosition(replayHandler.getReplaySender().currentTimeStamp());
            super.draw(renderer, size, renderInfo);
         }
      }).setSize(Integer.MAX_VALUE, 20);
      this.topPanel.addElements(null, this.playPauseButton, this.speedSlider, this.timeline);
      this.setLayout(new CustomLayout<GuiReplayOverlay>() {
         protected void layout(GuiReplayOverlay container, int width, int height) {
            this.pos(GuiReplayOverlay.this.topPanel, 10, 10);
            this.size(GuiReplayOverlay.this.topPanel, width - 20, 20);
            this.pos(GuiReplayOverlay.this.statusIndicatorPanel, width / 2, height - 21);
            this.width(GuiReplayOverlay.this.statusIndicatorPanel, width / 2 - 5);
         }
      });
      this.playPauseButton.setSpriteUV(new ReadablePoint() {
         @Override
         public int getX() {
            return 0;
         }

         @Override
         public int getY() {
            return replayHandler.getReplaySender().paused() ? 0 : 20;
         }

         @Override
         public void getLocation(WritablePoint dest) {
            dest.setLocation(this.getX(), this.getY());
         }
      }).onClick(new Runnable() {
         @Override
         public void run() {
            ReplaySender replaySender = replayHandler.getReplaySender();
            if (replaySender.paused()) {
               replaySender.setReplaySpeed(GuiReplayOverlay.this.getSpeedSliderValue());
            } else {
               replaySender.setReplaySpeed(0.0);
            }
         }
      });
      this.speedSlider.onValueChanged(new Runnable() {
         @Override
         public void run() {
            double speed = GuiReplayOverlay.this.getSpeedSliderValue();
            GuiReplayOverlay.this.speedSlider.setText(I18n.format("replaymod.gui.speed", new Object[0]) + ": " + speed + "x");
            ReplaySender replaySender = replayHandler.getReplaySender();
            if (!replaySender.paused()) {
               replaySender.setReplaySpeed(speed);
            }
         }
      }).setValue(9);
      this.timeline.onClick(new IGuiTimeline.OnClick() {
         @Override
         public void run(int time) {
            replayHandler.doJump(time, true);
         }
      }).setLength(replayHandler.getReplayDuration());
   }

   public double getSpeedSliderValue() {
      int value = this.speedSlider.getValue() + 1;
      return value <= 9 ? value / 10.0 : 1.0 + 0.25 * (value - 10);
   }

   @Override
   public void setVisible(boolean visible) {
      if (this.isVisible() != visible) {
         if (visible) {
            this.eventHandler.register();
         } else {
            this.eventHandler.unregister();
         }
      }

      super.setVisible(visible);
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      if (!this.getMinecraft().gameSettings.hideGUI && !this.hidden || !this.isAllowUserInput()) {
         super.draw(renderer, size, renderInfo);
      }
   }

   protected GuiReplayOverlay getThis() {
      return this;
   }

   private class EventHandler extends EventRegistrations {
      private EventHandler() {
         this.on(KeyBindingEventCallback.EVENT, this::onKeyBindingEvent);
         this.on(KeyEventCallback.EVENT, this::onKeyInput);
      }

      private void onKeyBindingEvent() {
         GameSettings gameSettings = GuiReplayOverlay.this.getMinecraft().gameSettings;
         /* 
          * T (chat) / / (command): toggle mouse visibility ONCE per press.
          * Do not toggle while a vanilla menu is open - that would steal the screen from the pause menu and make its buttons unclickable.
          */
         if (gameSettings.keyBindChat.isPressed() || gameSettings.keyBindCommand.isPressed()) {
            boolean visible = GuiReplayOverlay.this.isMouseVisible();
            GuiReplayOverlay.this.setMouseVisible(!visible);
            org.lwjglx.input.Mouse.setGrabbed(visible); // grabbed when we HIDE the mouse
            DebugSystem.log(DebugSystem.GUI_INPUT, "T toggled mouse visible=" + !visible);
         }
      }

      private boolean onKeyInput(KeyInput keyInput, int action) {
         if (action != 1) {
            return false;
         } else if (GuiReplayOverlay.this.isMouseVisible() && keyInput.key == 59) {
            GuiReplayOverlay.this.hidden = !GuiReplayOverlay.this.hidden;
            return true;
         } else {
            return false;
         }
      }
   }
}
