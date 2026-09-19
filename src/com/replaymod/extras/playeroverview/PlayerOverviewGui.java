package com.replaymod.extras.playeroverview;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiClickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiVerticalList;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiCheckbox;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiImage;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTooltip;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.IGuiCheckbox;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Closeable;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.replay.ReplayModReplay;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;

public class PlayerOverviewGui extends GuiScreen implements Closeable {
   protected static final int ENTRY_WIDTH = 200;
   public final GuiPanel contentPanel = new GuiPanel(this).setBackgroundColor(Colors.DARK_TRANSPARENT);
   public final GuiLabel spectateLabel = new GuiLabel(this.contentPanel).setI18nText("replaymod.gui.playeroverview.spectate");
   public final GuiLabel visibleLabel = new GuiLabel(this.contentPanel).setI18nText("replaymod.gui.playeroverview.visible");
   public final GuiVerticalList playersScrollable = new GuiVerticalList(this.contentPanel).setDrawSlider(true).setDrawShadow(true);
   public final GuiCheckbox saveCheckbox = new GuiCheckbox(this.contentPanel)
      .setTooltip(new GuiTooltip().setI18nText("replaymod.gui.playeroverview.remembersettings.description"))
      .setI18nLabel("replaymod.gui.playeroverview.remembersettings");
   public final GuiCheckbox checkAll = (new GuiCheckbox(this.contentPanel) {
      @Override
      public void onClick(Click click) {
         PlayerOverviewGui.this.playersScrollable.invokeAll(IGuiCheckbox.class, e -> e.setChecked(true));
      }
   }).setLabel("").setChecked(true).setTooltip(new GuiTooltip().setI18nText("replaymod.gui.playeroverview.showall"));
   public final GuiCheckbox uncheckAll = (new GuiCheckbox(this.contentPanel) {
      @Override
      public void onClick(Click click) {
         PlayerOverviewGui.this.playersScrollable.invokeAll(IGuiCheckbox.class, e -> e.setChecked(false));
      }
   }).setLabel("").setChecked(false).setTooltip(new GuiTooltip().setI18nText("replaymod.gui.playeroverview.hideall"));
   private final PlayerOverview extra;

   public PlayerOverviewGui(PlayerOverview extra, List<EntityPlayer> players) {
      this.setBackground(AbstractGuiScreen.Background.NONE);
      this.setTitle(new GuiLabel().setI18nText("replaymod.input.playeroverview"));
      this.setLayout(new CustomLayout<GuiScreen>() {
         protected void layout(GuiScreen container, int width, int height) {
            this.size(PlayerOverviewGui.this.contentPanel, 230, height - 40);
            this.pos(PlayerOverviewGui.this.contentPanel, width / 2 - this.width(PlayerOverviewGui.this.contentPanel) / 2, 20);
         }
      });
      this.contentPanel
         .setLayout(
            new CustomLayout<GuiPanel>() {
               protected void layout(GuiPanel container, int width, int height) {
                  this.pos(PlayerOverviewGui.this.spectateLabel, 10, 10);
                  this.pos(PlayerOverviewGui.this.visibleLabel, width - 10 - this.width(PlayerOverviewGui.this.visibleLabel), 10);
                  this.pos(
                     PlayerOverviewGui.this.playersScrollable,
                     10,
                     this.y(PlayerOverviewGui.this.spectateLabel) + this.height(PlayerOverviewGui.this.spectateLabel) + 5
                  );
                  this.size(
                     PlayerOverviewGui.this.playersScrollable,
                     width - 10 - 5,
                     height - 15 - this.height(PlayerOverviewGui.this.saveCheckbox) - this.y(PlayerOverviewGui.this.playersScrollable)
                  );
                  this.pos(PlayerOverviewGui.this.saveCheckbox, 10, height - 10 - this.height(PlayerOverviewGui.this.saveCheckbox));
                  this.pos(
                     PlayerOverviewGui.this.uncheckAll,
                     width - this.width(PlayerOverviewGui.this.uncheckAll) - 8,
                     height - this.height(PlayerOverviewGui.this.uncheckAll) - 10
                  );
                  this.pos(
                     PlayerOverviewGui.this.checkAll,
                     this.x(PlayerOverviewGui.this.uncheckAll) - 3 - this.width(PlayerOverviewGui.this.checkAll),
                     this.y(PlayerOverviewGui.this.uncheckAll)
                  );
               }
            }
         );
      this.extra = extra;
      Collections.sort(players, new PlayerOverviewGui.PlayerComparator());

      for (final EntityPlayer p : players) {
         if (p instanceof AbstractClientPlayer) {
            final ResourceLocation texture = ((AbstractClientPlayer)p).getLocationSkin();
            final GuiClickable panel = new GuiClickable()
               .setLayout(new HorizontalLayout().setSpacing(2))
               .addElements(new HorizontalLayout.Data(0.5), (new GuiImage() {
                  @Override
                  public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
                     renderer.bindTexture(texture);
                     renderer.drawTexturedRect(0, 0, 8, 8, 16, 16, 8, 8, 64, 64);
                     if (p.isWearing(EnumPlayerModelParts.HAT)) {
                        renderer.drawTexturedRect(0, 0, 40, 8, size.getWidth(), size.getHeight(), 8, 8, 64, 64);
                     }
                  }
               }).setSize(16, 16), new GuiLabel().setText(p.getName()).setColor(isSpectator(p) ? Colors.DKGREY : Colors.WHITE))
               .onClick(new Runnable() {
                  @Override
                  public void run() {
                     ReplayModReplay.instance.getReplayHandler().spectateEntity(p);
                  }
               });
            final GuiCheckbox checkbox = (new GuiCheckbox() {
               public GuiCheckbox setChecked(boolean checked) {
                  extra.setHidden(p.getUniqueID(), !checked);
                  return super.setChecked(checked);
               }
            }).setChecked(!extra.isHidden(p.getUniqueID()));
            new GuiPanel(this.playersScrollable.getListPanel()).setLayout(new CustomLayout<GuiPanel>() {
               protected void layout(GuiPanel container, int width, int height) {
                  this.pos(panel, 5, 0);
                  this.pos(checkbox, width - this.width(checkbox) - 5, height / 2 - this.height(checkbox) / 2);
               }

               @Override
               public ReadableDimension calcMinSize(GuiContainer<?> container) {
                  return new Dimension(200, panel.getMinSize().getHeight());
               }
            }).addElements(null, panel, checkbox);
         }
      }

      this.saveCheckbox.setChecked(extra.isSavingEnabled()).onClick(new Runnable() {
         @Override
         public void run() {
            extra.setSavingEnabled(PlayerOverviewGui.this.saveCheckbox.isChecked());
         }
      });
      ReplayModReplay.instance.getReplayHandler().getOverlay().setVisible(false);
   }

   @Override
   public void close() {
      ReplayModReplay.instance.getReplayHandler().getOverlay().setVisible(true);
      this.extra.saveHiddenPlayers();
   }

   private static boolean isSpectator(EntityPlayer e) {
      return e.isInvisible() && e.getActivePotionEffect(Potion.invisibility) == null;
   }

   private static final class PlayerComparator implements Comparator<EntityPlayer> {
      private PlayerComparator() {
      }

      public int compare(EntityPlayer o1, EntityPlayer o2) {
         if (PlayerOverviewGui.isSpectator(o1) && !PlayerOverviewGui.isSpectator(o2)) {
            return 1;
         } else {
            return PlayerOverviewGui.isSpectator(o2) && !PlayerOverviewGui.isSpectator(o1) ? -1 : o1.getName().compareToIgnoreCase(o2.getName());
         }
      }
   }
}
