package com.replaymod.pathing.gui;

import com.google.common.util.concurrent.SettableFuture;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.Utils;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiClickableContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiVerticalList;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTextField;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Closeable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.GridLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.GuiYesNoPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Consumer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import com.replaymod.render.gui.GuiRenderQueue;
import com.replaymod.render.gui.GuiRenderSettings;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replaystudio.pathing.PathingRegistry;
import com.replaymod.replaystudio.pathing.path.Path;
import com.replaymod.replaystudio.pathing.path.Timeline;
import com.replaymod.replaystudio.pathing.serialize.TimelineSerialization;
import com.replaymod.replaystudio.replay.ReplayFile;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.crash.CrashReport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GuiKeyframeRepository extends GuiScreen implements Closeable, KeyHandler {
   private static final Logger LOGGER = LogManager.getLogger();
   public final GuiPanel contentPanel = new GuiPanel(this).setBackgroundColor(Colors.DARK_TRANSPARENT);
   public final GuiLabel title = new GuiLabel(this.contentPanel).setI18nText("replaymod.gui.keyframerepository.title");
   public final GuiVerticalList list = new GuiVerticalList(this.contentPanel).setDrawShadow(true).setDrawSlider(true);
   public final GuiButton overwriteButton = new GuiButton()
      .onClick(
         new Runnable() {
            @Override
            public void run() {
               GuiYesNoPopup.open(GuiKeyframeRepository.this, new GuiLabel().setI18nText("replaymod.gui.keyframerepo.overwrite").setColor(Colors.BLACK))
                  .setYesI18nLabel("gui.yes")
                  .setNoI18nLabel("gui.no")
                  .onAccept(() -> {
                     for (GuiKeyframeRepository.Entry entry : GuiKeyframeRepository.this.selectedEntries) {
                        GuiKeyframeRepository.this.timelines.put(entry.name, GuiKeyframeRepository.this.currentTimeline);
                     }

                     GuiKeyframeRepository.this.overwriteButton.setDisabled();
                     GuiKeyframeRepository.this.save();
                  });
            }
         }
      )
      .setSize(75, 20)
      .setI18nLabel("replaymod.gui.overwrite")
      .setDisabled();
   public final GuiButton saveAsButton = new GuiButton()
      .onClick(
         new Runnable() {
            @Override
            public void run() {
               final GuiTextField nameField = new GuiTextField().setSize(200, 20).setFocused(true);
               final GuiYesNoPopup popup = GuiYesNoPopup.open(
                     GuiKeyframeRepository.this, new GuiLabel().setI18nText("replaymod.gui.saveas").setColor(Colors.BLACK), nameField
                  )
                  .setYesI18nLabel("replaymod.gui.save")
                  .setNoI18nLabel("replaymod.gui.cancel");
               popup.getYesButton().setDisabled();
               ((VerticalLayout)popup.getInfo().getLayout()).setSpacing(7);
               nameField.onEnter(new Runnable() {
                  @Override
                  public void run() {
                     if (popup.getYesButton().isEnabled()) {
                        popup.getYesButton().onClick(new Click(-1, -1, 0, 0));
                     }
                  }
               }).onTextChanged(new Consumer<String>() {
                  public void consume(String obj) {
                     popup.getYesButton().setEnabled(!nameField.getText().isEmpty() && !GuiKeyframeRepository.this.timelines.containsKey(nameField.getText()));
                  }
               });
               popup.onAccept(() -> {
                  String name = nameField.getText();
                  GuiKeyframeRepository.this.timelines.put(name, GuiKeyframeRepository.this.currentTimeline);
                  GuiKeyframeRepository.this.list.getListPanel().addElements(null, GuiKeyframeRepository.this.new Entry(name));
                  GuiKeyframeRepository.this.save();
               });
            }
         }
      )
      .setSize(75, 20)
      .setI18nLabel("replaymod.gui.saveas");
   public final GuiButton loadButton = new GuiButton().onClick(new Runnable() {
      @Override
      public void run() {
         GuiKeyframeRepository.this.getMinecraft().displayGuiScreen(null);

         try {
            Timeline timeline = GuiKeyframeRepository.this.timelines.get(GuiKeyframeRepository.this.selectedEntries.iterator().next().name);

            for (Path path : timeline.getPaths()) {
               path.updateAll();
            }

            GuiKeyframeRepository.this.future.set(timeline);
         } catch (Throwable t) {
            GuiKeyframeRepository.this.future.setException(t);
         }
      }
   }).setSize(75, 20).setI18nLabel("replaymod.gui.load").setDisabled();
   public final GuiButton renameButton = new GuiButton()
      .onClick(
         new Runnable() {
            @Override
            public void run() {
               GuiKeyframeRepository.Entry selectedEntry = GuiKeyframeRepository.this.selectedEntries.iterator().next();
               final GuiTextField nameField = new GuiTextField().setSize(200, 20).setFocused(true).setText(selectedEntry.name);
               final GuiYesNoPopup popup = GuiYesNoPopup.open(
                     GuiKeyframeRepository.this, new GuiLabel().setI18nText("replaymod.gui.rename").setColor(Colors.BLACK), nameField
                  )
                  .setYesI18nLabel("replaymod.gui.done")
                  .setNoI18nLabel("replaymod.gui.cancel");
               popup.getYesButton().setDisabled();
               ((VerticalLayout)popup.getInfo().getLayout()).setSpacing(7);
               nameField.onEnter(new Runnable() {
                  @Override
                  public void run() {
                     if (popup.getYesButton().isEnabled()) {
                        popup.getYesButton().onClick(new Click(-1, -1, 0, 0));
                     }
                  }
               }).onTextChanged(new Consumer<String>() {
                  public void consume(String obj) {
                     popup.getYesButton().setEnabled(!nameField.getText().isEmpty() && !GuiKeyframeRepository.this.timelines.containsKey(nameField.getText()));
                  }
               });
               popup.onAccept(() -> {
                  String name = nameField.getText();
                  GuiKeyframeRepository.this.timelines.put(name, GuiKeyframeRepository.this.timelines.remove(selectedEntry.name));
                  selectedEntry.name = name;
                  selectedEntry.label.setText(name);
                  GuiKeyframeRepository.this.save();
               });
            }
         }
      )
      .setSize(75, 20)
      .setI18nLabel("replaymod.gui.rename")
      .setDisabled();
   public final GuiButton removeButton = new GuiButton()
      .onClick(
         new Runnable() {
            @Override
            public void run() {
               GuiYesNoPopup.open(GuiKeyframeRepository.this, new GuiLabel().setI18nText("replaymod.gui.keyframerepo.delete").setColor(Colors.BLACK))
                  .setYesI18nLabel("replaymod.gui.delete")
                  .setNoI18nLabel("replaymod.gui.cancel")
                  .onAccept(() -> {
                     for (GuiKeyframeRepository.Entry entry : GuiKeyframeRepository.this.selectedEntries) {
                        GuiKeyframeRepository.this.timelines.remove(entry.name);
                        GuiKeyframeRepository.this.list.getListPanel().removeElement(entry);
                     }

                     GuiKeyframeRepository.this.selectedEntries.clear();
                     GuiKeyframeRepository.this.updateButtons();
                     GuiKeyframeRepository.this.save();
                  });
            }
         }
      )
      .setSize(75, 20)
      .setI18nLabel("replaymod.gui.remove")
      .setDisabled();
   public final GuiButton copyButton = new GuiButton().onClick(new Runnable() {
      @Override
      public void run() {
         Map<String, Timeline> toBeSerialized = new HashMap<>();

         for (GuiKeyframeRepository.Entry entry : GuiKeyframeRepository.this.selectedEntries) {
            toBeSerialized.put(entry.name, GuiKeyframeRepository.this.timelines.get(entry.name));
         }

         try {
            TimelineSerialization serialization = new TimelineSerialization(GuiKeyframeRepository.this.registry, null);
            MCVer.setClipboardString(serialization.serialize(toBeSerialized));
         } catch (Throwable t) {
            t.printStackTrace();
            CrashReport report = CrashReport.makeCrashReport(t, "Copying timeline(s)");
            Utils.error(GuiKeyframeRepository.LOGGER, GuiKeyframeRepository.this, report, () -> {});
         }
      }
   }).setSize(75, 20).setI18nLabel("replaymod.gui.copy").setDisabled();
   public final GuiButton pasteButton = new GuiButton().onClick(new Runnable() {
      @Override
      public void run() {
         try {
            TimelineSerialization serialization = new TimelineSerialization(GuiKeyframeRepository.this.registry, null);

            for (Map.Entry<String, Timeline> entry : serialization.deserialize(MCVer.getClipboardString()).entrySet()) {
               String name = entry.getKey();

               while (GuiKeyframeRepository.this.timelines.containsKey(name)) {
                  name = name + " (Copy)";
               }

               GuiKeyframeRepository.this.timelines.put(name, entry.getValue());
               GuiKeyframeRepository.this.list.getListPanel().addElements(null, GuiKeyframeRepository.this.new Entry(name));
            }

            GuiKeyframeRepository.this.save();
         } catch (Throwable t) {
            t.printStackTrace();
         }
      }
   }).setSize(75, 20).setI18nLabel("replaymod.gui.paste");
   public final GuiButton addToQueueButton = new GuiButton().onClick(new Runnable() {
      @Override
      public void run() {
         ReplayHandler replayHandler = ReplayModReplay.instance.getReplayHandler();
         final GuiRenderQueue queue = new GuiRenderQueue(GuiKeyframeRepository.this, replayHandler, null);
         queue.open();
         Set<String> selected = GuiKeyframeRepository.this.selectedEntries.stream().map(e -> e.name).collect(Collectors.toSet());
         final ArrayDeque<Map.Entry<String, Timeline>> toBeAdded = new ArrayDeque<>();

         for (Map.Entry<String, Timeline> entry : GuiKeyframeRepository.this.timelines.entrySet()) {
            if (selected.contains(entry.getKey())) {
               toBeAdded.offerLast(entry);
            }
         }

         (new Runnable() {
            @Override
            public void run() {
               Map.Entry<String, Timeline> entry = toBeAdded.pollFirst();
               if (entry != null) {
                  String name = entry.getKey();
                  Timeline timeline = entry.getValue();
                  GuiRenderSettings settingsGui = queue.addJob(timeline);
                  settingsGui.buttonPanel.removeElement(settingsGui.renderButton);
                  settingsGui.setOutputFileBaseName(name);
                  Consumer<Click> orgOnClick = settingsGui.queueButton.getOnClick();
                  settingsGui.queueButton.onClick(click -> {
                     orgOnClick.consume(click);
                     this.run();
                  });
                  settingsGui.open();
               }
            }
         }).run();
      }
   }).setSize(75, 20).setI18nLabel("replaymod.gui.rendersettings.addtoqueue");
   public final GuiPanel buttonPanel = new GuiPanel(this.contentPanel)
      .setLayout(new GridLayout().setColumns(4).setSpacingX(5).setSpacingY(5))
      .addElements(
         null,
         this.overwriteButton,
         this.saveAsButton,
         this.renameButton,
         this.removeButton,
         this.loadButton,
         this.addToQueueButton,
         this.copyButton,
         this.pasteButton
      );
   private final Map<String, Timeline> timelines = new LinkedHashMap<>();
   private final Timeline currentTimeline;
   private final SettableFuture<Timeline> future = SettableFuture.create();
   private final PathingRegistry registry;
   private final ReplayFile replayFile;
   private final Set<GuiKeyframeRepository.Entry> selectedEntries = new HashSet<>();

   public GuiKeyframeRepository(PathingRegistry registry, ReplayFile replayFile, Timeline currentTimeline) throws IOException {
      this.setBackground(AbstractGuiScreen.Background.NONE);
      this.setLayout(
         new CustomLayout<GuiScreen>() {
            protected void layout(GuiScreen container, int width, int height) {
               this.pos(
                  GuiKeyframeRepository.this.contentPanel,
                  width / 2 - this.width(GuiKeyframeRepository.this.contentPanel) / 2,
                  height / 2 - this.height(GuiKeyframeRepository.this.contentPanel) / 2
               );
            }
         }
      );
      this.contentPanel
         .setLayout(
            new CustomLayout<GuiPanel>() {
               protected void layout(GuiPanel container, int width, int height) {
                  this.pos(GuiKeyframeRepository.this.title, width / 2 - this.width(GuiKeyframeRepository.this.title) / 2, 5);
                  this.size(
                     GuiKeyframeRepository.this.list,
                     width,
                     height
                        - 10
                        - this.height(GuiKeyframeRepository.this.buttonPanel)
                        - 10
                        - this.y(GuiKeyframeRepository.this.title)
                        - this.height(GuiKeyframeRepository.this.title)
                        - 5
                  );
                  this.pos(
                     GuiKeyframeRepository.this.list,
                     width / 2 - this.width(GuiKeyframeRepository.this.list) / 2,
                     this.y(GuiKeyframeRepository.this.title) + this.height(GuiKeyframeRepository.this.title) + 5
                  );
                  this.pos(
                     GuiKeyframeRepository.this.buttonPanel,
                     width / 2 - this.width(GuiKeyframeRepository.this.buttonPanel) / 2,
                     this.y(GuiKeyframeRepository.this.list) + this.height(GuiKeyframeRepository.this.list) + 10
                  );
               }

               @Override
               public ReadableDimension calcMinSize(GuiContainer<?> container) {
                  ReadableDimension screenSize = GuiKeyframeRepository.this.getMinSize();
                  return new Dimension(screenSize.getWidth() - 10, screenSize.getHeight() - 10);
               }
            }
         );
      this.registry = registry;
      this.replayFile = replayFile;
      this.currentTimeline = currentTimeline;
      this.timelines.putAll(replayFile.getTimelines(registry));

      for (Map.Entry<String, Timeline> entry : this.timelines.entrySet()) {
         if (!entry.getKey().isEmpty()) {
            this.list.getListPanel().addElements(null, new GuiKeyframeRepository.Entry(entry.getKey()));
         }
      }

      this.updateButtons();
   }

   private void updateButtons() {
      int selected = this.selectedEntries.size();
      this.overwriteButton.setEnabled(selected >= 1);
      this.loadButton.setEnabled(selected == 1);
      this.renameButton.setEnabled(selected == 1);
      this.removeButton.setEnabled(selected >= 1);
      this.copyButton.setEnabled(selected >= 1);
      this.addToQueueButton.setEnabled(selected >= 1);
   }

   @Override
   public void display() {
      super.display();
      ReplayModReplay.instance.getReplayHandler().getOverlay().setVisible(false);
   }

   @Override
   public void close() {
      ReplayModReplay.instance.getReplayHandler().getOverlay().setVisible(true);
   }

   public SettableFuture<Timeline> getFuture() {
      return this.future;
   }

   public void save() {
      try {
         this.replayFile.writeTimelines(this.registry, this.timelines);
      } catch (IOException e) {
         e.printStackTrace();
         ReplayMod.instance.printWarningToChat("Error saving timelines: " + e.getMessage());
      }
   }

   @Override
   public boolean handleKey(KeyInput keyInput) {
      if (keyInput.hasCtrl()) {
         switch (keyInput.key) {
            case 30:
               if (this.selectedEntries.size() < this.timelines.size()) {
                  for (GuiElement<?> child : this.list.getListPanel().getChildren()) {
                     if (child instanceof GuiKeyframeRepository.Entry) {
                        this.selectedEntries.add((GuiKeyframeRepository.Entry)child);
                     }
                  }
               } else {
                  this.selectedEntries.clear();
               }

               this.updateButtons();
               return true;
            case 46:
               this.copyButton.onClick(new Click(-1, -1, 0, 0));
               return true;
            case 47:
               this.pasteButton.onClick(new Click(-1, -1, 0, 0));
               return true;
         }
      }

      return false;
   }

   public class Entry extends AbstractGuiClickableContainer<GuiKeyframeRepository.Entry> {
      public final GuiLabel label = new GuiLabel(this);
      private String name;

      public Entry(String name) {
         this.name = name;
         this.setLayout(new CustomLayout<GuiKeyframeRepository.Entry>() {
            protected void layout(GuiKeyframeRepository.Entry container, int width, int height) {
               this.pos(Entry.this.label, 5, height / 2 - this.height(Entry.this.label) / 2);
            }

            @Override
            public ReadableDimension calcMinSize(GuiContainer<?> container) {
               return new Dimension(GuiKeyframeRepository.this.buttonPanel.calcMinSize().getWidth(), 16);
            }
         });
         this.label.setText(name);
      }

      @Override
      protected void onClick(Click click) {
         if (!click.hasCtrl()) {
            GuiKeyframeRepository.this.selectedEntries.clear();
         }

         if (GuiKeyframeRepository.this.selectedEntries.contains(this)) {
            GuiKeyframeRepository.this.selectedEntries.remove(this);
         } else {
            GuiKeyframeRepository.this.selectedEntries.add(this);
         }

         GuiKeyframeRepository.this.updateButtons();
      }

      @Override
      public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
         if (GuiKeyframeRepository.this.selectedEntries.contains(this)) {
            renderer.drawRect(0, 0, size.getWidth(), size.getHeight(), Colors.BLACK);
            renderer.drawRect(0, 0, 2, size.getHeight(), Colors.WHITE);
         }

         super.draw(renderer, size, renderInfo);
      }

      protected GuiKeyframeRepository.Entry getThis() {
         return this;
      }
   }
}
