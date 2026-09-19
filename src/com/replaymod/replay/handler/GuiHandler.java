package com.replaymod.replay.handler;

import com.replaymod.core.gui.GuiReplayButton;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTooltip;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.InitScreenCallback;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.Setting;
import com.replaymod.replay.gui.screen.GuiReplayViewer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import redacted.client.util.debug.DebugSystem;

public class GuiHandler extends EventRegistrations {
   private static final int BUTTON_REPLAY_VIEWER = 17890234;
   private static final int BUTTON_EXIT_REPLAY = 17890235;
   private final ReplayModReplay mod;

   public GuiHandler(ReplayModReplay mod) {
      this.on(InitScreenCallback.EVENT, this::injectIntoIngameMenu);
      this.on(InitScreenCallback.EVENT, (screen, buttons) -> this.ensureReplayStopped(screen));
      this.on(InitScreenCallback.EVENT, this::injectIntoMainMenu);
      this.mod = mod;
   }

   private void injectIntoIngameMenu(GuiScreen guiScreen, Collection<GuiButton> buttonList) {
      if (guiScreen instanceof GuiIngameMenu) {
         if (this.mod.getReplayHandler() != null) {
            this.mod.getReplayHandler().getReplaySender().setReplaySpeed(0.0);
            /* 
             * Layout the replay pause menu as a compact, non-overlapping grid.
             * Vanilla rows are 24px apart; hiding achievements/stats/LAN and replacing "exit server" with "exit replay" frees exactly the rows the native replay controls need.
             * (The original ReplayMod shifted buttons via moveAllButtonsInRect, which this port's GuiHandler previously applied to already-off-screen buttons and thus never moved anything, leaving the replay controls on top of Options and Exit Replay.)
             */
            int cx = guiScreen.width / 2 - 100;
            int r1 = guiScreen.height / 4 + 8;    // back to game
            int r2 = guiScreen.height / 4 + 32;   // options
            int r3 = guiScreen.height / 4 + 56;   // exit replay
            int r4 = guiScreen.height / 4 + 80;   // pause | speed
            int r5 = guiScreen.height / 4 + 104;  // - | +

            for (GuiButton b : new ArrayList<>(buttonList)) {
               switch (b.id) {
                  case 1: // exit server -> replaced by exit replay
                     b.xPosition = -1000;
                     b.yPosition = -1000;
                     MCVer.addButton(
                        guiScreen,
                        new GuiHandler.InjectedButton(guiScreen, 17890235, cx, r3, 200, 20, "replaymod.gui.exit", null, btn -> GuiHandler.this.onButton(guiScreen, btn))
                     );
                     break;
                  case 4: // back to game
                     b.xPosition = cx;
                     b.yPosition = r1;
                     break;
                  case 0: // options
                     b.xPosition = cx;
                     b.yPosition = r2;
                     break;
                  case 5: // achievements
                  case 6: // stats
                  case 7: // open to LAN: none of these make sense in a replay
                     b.xPosition = -1000;
                     b.yPosition = -1000;
                     break;
               }
            }

            /* 
             * Native replay controls: play/pause + speed.
             * The jGui overlay is disabled in this port, so these live in the pause menu.
             */
            com.replaymod.replay.ReplaySender sender = this.mod.getReplayHandler().getReplaySender();
            buttonList.add(
               new GuiHandler.InjectedButton(
                  guiScreen,
                  17890236,
                  cx,
                  r4,
                  98,
                  20,
                  sender.paused() ? I18n.format("replaymod.gui.ingame.menu.unpause") : I18n.format("replaymod.gui.ingame.menu.pause"),
                  null,
                  btn -> GuiHandler.this.onButton(guiScreen, btn)
               )
            );
            buttonList.add(
               new GuiHandler.InjectedButton(
                  guiScreen, 17890237, cx + 102, r4, 98, 20, I18n.format("replaymod.gui.speed") + ": " + sender.getReplaySpeed() + "x", null, btn -> GuiHandler.this.onButton(guiScreen, btn)
               )
            );
            buttonList.add(new GuiHandler.InjectedButton(guiScreen, 17890238, cx, r5, 98, 20, "-", null, btn -> GuiHandler.this.onButton(guiScreen, btn)));
            buttonList.add(new GuiHandler.InjectedButton(guiScreen, 17890239, cx + 102, r5, 98, 20, "+", null, btn -> GuiHandler.this.onButton(guiScreen, btn)));
         }
      }
   }

   private void moveAllButtonsInRect(Collection<GuiButton> buttons, int xStart, int xEnd, int yStart, int yEnd, int moveBy) {
      buttons.stream()
         .filter(button -> button.xPosition <= xEnd && button.xPosition + button.width >= xStart)
         .filter(button -> button.yPosition <= yEnd && button.yPosition + button.height >= yStart)
         .forEach(button -> button.yPosition += moveBy);
   }

   private void ensureReplayStopped(GuiScreen guiScreen) {
      if (guiScreen instanceof GuiMainMenu || guiScreen instanceof GuiMultiplayer) {
         if (this.mod.getReplayHandler() != null) {
            try {
               this.mod.getReplayHandler().endReplay();
            } catch (IOException e) {
               ReplayModReplay.LOGGER.error("Trying to stop broken replay: ", e);
            } finally {
               if (this.mod.getReplayHandler() != null) {
                  this.mod.forcefullyStopReplay();
               }
            }
         }
      }
   }

   private void injectIntoMainMenu(GuiScreen guiScreen, Collection<GuiButton> buttonList) {
      if (guiScreen instanceof GuiMainMenu || guiScreen.getClass().getName().equals("redacted.client.gui.menu.GuiCustomMainMenu")) {
         this.legacyInjectIntoMainMenu(guiScreen, buttonList);
      }
   }

   private void legacyInjectIntoMainMenu(GuiScreen guiScreen, Collection<GuiButton> buttonList) {
      boolean isCustomMainMenuMod = guiScreen.getClass().getName().endsWith("custommainmenu.gui.GuiFakeMain");
      final GuiHandler.MainMenuButtonPosition buttonPosition = GuiHandler.MainMenuButtonPosition.valueOf(
         this.mod.getCore().getSettingsRegistry().get(Setting.MAIN_MENU_BUTTON)
      );
      if (buttonPosition != GuiHandler.MainMenuButtonPosition.BIG && !isCustomMainMenuMod) {
         VanillaGuiScreen vanillaGui = VanillaGuiScreen.wrap(guiScreen);
         final GuiReplayButton replayButton = new GuiReplayButton();
         replayButton.onClick(() -> new GuiReplayViewer(this.mod).display()).setTooltip(new GuiTooltip().setI18nText("replaymod.gui.replayviewer"));
         vanillaGui.setLayout(new CustomLayout<com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen>(vanillaGui.getLayout()) {
            private Point pos;

            protected void layout(com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen container, int width, int height) {
               if (this.pos == null) {
                  this.pos = GuiHandler.this.determineButtonPos(buttonPosition, guiScreen, buttonList);
               }

               this.size(replayButton, 20, 20);
               this.pos(replayButton, this.pos.getX(), this.pos.getY());
            }
         }).addElements(null, replayButton);
      } else {
         int x = guiScreen.width / 2 - 100;
         int y = MCVer.findButton(buttonList, "menu.online", 14)
            .map(Optional::of)
            .orElse(MCVer.findButton(buttonList, "menu.multiplayer", 2))
            .<Integer>map(it -> it.yPosition)
            .orElse(guiScreen.height / 4 + 10 + 96);
         this.moveAllButtonsInRect(buttonList, x, x + 200, Integer.MIN_VALUE, y, -24);
         GuiHandler.InjectedButton button = new GuiHandler.InjectedButton(
            guiScreen, 17890234, x, y, 200, 20, "replaymod.gui.replayviewer", null, btn -> GuiHandler.this.onButton(guiScreen, btn)
         );
         if (isCustomMainMenuMod) {
            buttonList.add(button);
         } else {
            MCVer.addButton(guiScreen, button);
         }
      }
   }

   private Point determineButtonPos(GuiHandler.MainMenuButtonPosition buttonPosition, GuiScreen guiScreen, Collection<GuiButton> buttonList) {
      Point topRight = new Point(guiScreen.width - 20 - 5, 5);
      if (buttonPosition == GuiHandler.MainMenuButtonPosition.TOP_LEFT) {
         return new Point(5, 5);
      } else if (buttonPosition == GuiHandler.MainMenuButtonPosition.TOP_RIGHT) {
         return topRight;
      } else {
         return buttonPosition == GuiHandler.MainMenuButtonPosition.DEFAULT
            ? Stream.of(
                  MCVer.findButton(buttonList, "menu.singleplayer", 1),
                  MCVer.findButton(buttonList, "menu.multiplayer", 2),
                  MCVer.findButton(buttonList, "menu.online", 14),
                  MCVer.findButton(buttonList, "modmenu.title", 6)
               )
               .flatMap(it -> it.map(Stream::of).orElseGet(Stream::empty))
               .filter(
                  it -> buttonList.stream()
                     .noneMatch(
                        button -> button.xPosition <= it.xPosition + it.width + 4 + 20
                           && button.yPosition <= it.yPosition + it.height
                           && button.xPosition + button.width >= it.xPosition + it.width + 4
                           && button.yPosition + button.height >= it.yPosition
                     )
               )
               .max(Comparator.<GuiButton>comparingInt(it -> it.yPosition).thenComparingInt(it -> it.xPosition))
               .map(it -> new Point(it.xPosition + it.width + 4, it.yPosition))
               .orElse(topRight)
            : Optional.of(buttonList).flatMap(buttons -> {
               switch (buttonPosition) {
                  case LEFT_OF_SINGLEPLAYER:
                  case RIGHT_OF_SINGLEPLAYER:
                     return MCVer.findButton(buttons, "menu.singleplayer", 1);
                  case LEFT_OF_MULTIPLAYER:
                  case RIGHT_OF_MULTIPLAYER:
                     return MCVer.findButton(buttons, "menu.multiplayer", 2);
                  case LEFT_OF_REALMS:
                  case RIGHT_OF_REALMS:
                     return MCVer.findButton(buttons, "menu.online", 14);
                  case LEFT_OF_MODS:
                  case RIGHT_OF_MODS:
                     return MCVer.findButton(buttons, "modmenu.title", 6);
                  default:
                     throw new RuntimeException();
               }
            }).map(button -> {
               switch (buttonPosition) {
                  case LEFT_OF_SINGLEPLAYER:
                  case LEFT_OF_MULTIPLAYER:
                  case LEFT_OF_REALMS:
                  case LEFT_OF_MODS:
                     return new Point(button.xPosition - 4 - 20, button.yPosition);
                  case RIGHT_OF_MODS:
                  case RIGHT_OF_SINGLEPLAYER:
                  case RIGHT_OF_MULTIPLAYER:
                  case RIGHT_OF_REALMS:
                     return new Point(button.xPosition + button.width + 4, button.yPosition);
                  default:
                     throw new RuntimeException();
               }
            }).orElse(topRight);
      }
   }

   private int determineButtonIndex(Collection<GuiButton> buttons, GuiButton button) {
      GuiButton best = null;
      int bestIndex = -1;
      int index = 0;

      for (GuiButton other : buttons) {
         if (other.yPosition <= button.yPosition && (other.yPosition != button.yPosition || other.xPosition <= button.xPosition)) {
            if (best == null || other.yPosition > best.yPosition || other.yPosition == best.yPosition && other.xPosition > best.xPosition) {
               best = other;
               bestIndex = index + 1;
            }

            index++;
         } else {
            index++;
         }
      }

      return bestIndex;
   }

   public void onButton(GuiScreen guiScreen, GuiButton button) {
      DebugSystem.log(DebugSystem.GUI_INPUT, "onButton id=" + button.id + " text='" + button.displayString + "' screen=" + guiScreen.getClass().getSimpleName());
      if (button.enabled) {
         boolean isMainMenu =
            guiScreen instanceof GuiMainMenu || guiScreen.getClass().getName().equals("redacted.client.gui.menu.GuiCustomMainMenu");
         if (isMainMenu && button.id == 17890234) {
            new GuiReplayViewer(this.mod).display();
         }

         if (guiScreen instanceof GuiIngameMenu && this.mod.getReplayHandler() != null && button.id == 17890235) {
            button.enabled = false;

            try {
               this.mod.getReplayHandler().endReplay();
            } catch (IOException e) {
               e.printStackTrace();
            }
         }

         // Native replay controls (play/pause, speed)
         if (guiScreen instanceof GuiIngameMenu && this.mod.getReplayHandler() != null) {
            com.replaymod.replay.ReplaySender sender = this.mod.getReplayHandler().getReplaySender();
            if (button.id == 17890236) {
               sender.setReplaySpeed(sender.paused() ? 1.0 : 0.0);
            } else if (button.id == 17890237) {
               double speed = sender.getReplaySpeed();
               sender.setReplaySpeed(speed == 0.0 ? 1.0 : speed);
            } else if (button.id == 17890238) {
               double speed = Math.max(0.1, sender.getReplaySpeed() - 0.25);
               sender.setReplaySpeed(speed);
            } else if (button.id == 17890239) {
               double speed = Math.min(8.0, sender.getReplaySpeed() + 0.25);
               sender.setReplaySpeed(speed == 0.0 ? 0.25 : speed);
            }
            // Refresh labels without re-running initGui (which would pause).
            for (GuiButton b : new ArrayList<>(guiScreen.getButtons())) {
               if (b.id == 17890236) {
                  b.displayString = sender.paused() ? I18n.format("replaymod.gui.ingame.menu.unpause") : I18n.format("replaymod.gui.ingame.menu.pause");
               } else if (b.id == 17890237) {
                  b.displayString = I18n.format("replaymod.gui.speed") + ": " + sender.getReplaySpeed() + "x";
               }
            }
         }
      }
   }

   public static class InjectedButton extends GuiButton {
      public final GuiScreen guiScreen;
      public final int id;
      private Consumer<GuiHandler.InjectedButton> onClick;

      public InjectedButton(GuiScreen guiScreen, int buttonId, int x, int y, int width, int height, String buttonText, String tooltip, Consumer<GuiHandler.InjectedButton> onClick) {
         super(buttonId, x, y, width, height, I18n.format(buttonText, new Object[0]));
         this.guiScreen = guiScreen;
         this.id = buttonId;
         this.onClick = onClick;
      }

      /**
       * The original ReplayMod fired these via a Forge
       * {@code GuiScreenEvent.ActionPerformedEvent.Pre} subscriber; with Forge
       * gone, invoke the callback directly from the button press (vanilla then
       * still calls the screen's actionPerformed, whose default branch ignores
       * these ids).
       */
      @Override
      public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
         if (super.mousePressed(mc, mouseX, mouseY)) {
            if (this.onClick != null) {
               this.onClick.accept(this);
            }
            return true;
         }
         return false;
      }
   }

   public enum MainMenuButtonPosition {
      BIG,
      DEFAULT,
      TOP_LEFT,
      TOP_RIGHT,
      LEFT_OF_SINGLEPLAYER,
      RIGHT_OF_SINGLEPLAYER,
      LEFT_OF_MULTIPLAYER,
      RIGHT_OF_MULTIPLAYER,
      LEFT_OF_REALMS,
      RIGHT_OF_REALMS,
      LEFT_OF_MODS,
      RIGHT_OF_MODS;
   }
}
