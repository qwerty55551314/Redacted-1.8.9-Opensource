package com.replaymod.extras;

import com.replaymod.core.KeyBindingRegistry;
import com.replaymod.core.ReplayMod;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTooltip;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.GridLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.LayoutData;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.replay.events.ReplayOpenedCallback;
import com.replaymod.replay.gui.overlay.GuiReplayOverlay;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import net.minecraft.client.resources.I18n;

public class HotkeyButtons extends EventRegistrations implements Extra {
   private ReplayMod mod;

   public HotkeyButtons() {
      this.on(ReplayOpenedCallback.EVENT, replayHandler -> new HotkeyButtons.Gui(this.mod, replayHandler.getOverlay()));
   }

   @Override
   public void register(ReplayMod mod) {
      this.mod = mod;
      this.register();
   }

   public static final class Gui {
      private final GuiButton toggleButton;
      private final GridLayout panelLayout;
      private final GuiPanel panel;
      private boolean open;

      public Gui(ReplayMod mod, GuiReplayOverlay overlay) {
         this.toggleButton = new GuiButton(overlay).setSize(20, 20).setTexture(ReplayMod.TEXTURE, 256).setSpriteUV(0, 120).onClick(new Runnable() {
            @Override
            public void run() {
               Gui.this.open = !Gui.this.open;
            }
         });
         this.panel = (new GuiPanel(overlay) {
            @Override
            public Collection<GuiElement> getChildren() {
               return Gui.this.open ? super.getChildren() : Collections.emptyList();
            }

            @Override
            public Map<GuiElement, LayoutData> getElements() {
               return Gui.this.open ? super.getElements() : Collections.emptyMap();
            }
         }).setLayout(this.panelLayout = new GridLayout().setSpacingX(5).setSpacingY(5).setColumns(1));
         KeyBindingRegistry keyBindingRegistry = mod.getKeyBindingRegistry();
         keyBindingRegistry.getBindings()
            .values()
            .stream()
            .sorted(Comparator.comparing(it -> I18n.format(it.name, new Object[0])))
            .forEachOrdered(
               keyBinding -> {
                  final GuiButton button = (new GuiButton() {
                        @Override
                        public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
                           this.setLabel(keyBinding.isBound() ? keyBinding.getBoundKey() : "");
                           if (keyBinding.supportsAutoActivation()) {
                              this.setTooltip(
                                 new GuiTooltip()
                                    .setText(
                                       new String[]{
                                          I18n.format("replaymod.gui.ingame.autoactivating", new Object[0]),
                                          I18n.format(
                                             "replaymod.gui.ingame.autoactivating." + (keyBinding.isAutoActivating() ? "disable" : "enable"), new Object[0]
                                          )
                                       }
                                    )
                              );
                              this.setLabelColor(keyBinding.isAutoActivating() ? 65280 : 14737632);
                           }

                           super.draw(renderer, size, renderInfo);
                        }
                     })
                     .onClick(click -> {
                        if (keyBinding.supportsAutoActivation() && click.hasCtrl()) {
                           keyBinding.setAutoActivating(!keyBinding.isAutoActivating());
                        } else {
                           keyBinding.trigger();
                        }
                     });
                  final GuiLabel label = new GuiLabel().setI18nText(keyBinding.name);
                  this.panel.addElements(null, new GuiPanel().setLayout(new CustomLayout<GuiPanel>() {
                     protected void layout(GuiPanel container, int width, int height) {
                        this.width(button, Math.max(10, this.width(button)) + 10);
                        this.height(button, 20);
                        int textWidth = this.width(label);
                        this.x(label, this.width(button) + 4);
                        this.width(label, width - this.x(label));
                        if (textWidth > width - this.x(label)) {
                           this.height(label, this.height(label) * 2);
                        }

                        this.y(label, (height - this.height(label)) / 2);
                     }
                  }).addElements(null, button, label).setSize(150, 20));
               }
            );
         overlay.setLayout(new CustomLayout<GuiReplayOverlay>(overlay.getLayout()) {
            protected void layout(GuiReplayOverlay container, int width, int height) {
               Gui.this.panelLayout.setColumns(Math.max(1, (width - 10) / 155));
               this.size(Gui.this.panel, Gui.this.panel.getMinSize());
               this.pos(Gui.this.toggleButton, 5, height - 25);
               this.pos(Gui.this.panel, 5, this.y(Gui.this.toggleButton) - 5 - this.height(Gui.this.panel));
            }
         });
      }
   }
}
