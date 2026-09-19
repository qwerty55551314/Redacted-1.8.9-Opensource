package com.replaymod.replay.gui.screen;

import com.google.common.util.concurrent.SettableFuture;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.SettingsRegistry;
import com.replaymod.core.gui.GuiReplaySettings;
import com.replaymod.core.utils.Utils;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiImage;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTextField;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTooltip;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.AbstractGuiResourceLoadingList;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.CharHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.CharInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.GuiYesNoPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.Image;
import com.replaymod.lib.org.apache.commons.lang3.StringUtils;
import com.replaymod.lib.org.apache.commons.lang3.tuple.Pair;
import com.replaymod.render.gui.GuiRenderQueue;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.render.utils.RenderJob;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.Setting;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ReportedException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.SuffixFileFilter;

public class GuiReplayViewer extends GuiScreen {
   private final ReplayModReplay mod;
   public final GuiReplayViewer.GuiReplayList list = new GuiReplayViewer.GuiReplayList(this)
      .onSelectionChanged(this::updateButtons)
      .onSelectionDoubleClicked(() -> {
         if (this.loadButton.isEnabled()) {
            this.loadButton.onClick(new Click(-1, -1, 0, 0));
         }
      });
   public final GuiButton loadButton = new GuiButton()
      .onClick(
         new Runnable() {
            private boolean loading = false;

            @Override
            public void run() {
               if (!this.loading) {
                  this.loading = true;
                  GuiReplayViewer.this.loadButton.setDisabled();
                  List<GuiReplayViewer.GuiReplayEntry> selected = GuiReplayViewer.this.list.getSelected();
                  if (selected.size() == 1) {
                     File file = selected.get(0).file;
                     ReplayModReplay.LOGGER.info("Opening replay in viewer: " + file);

                     try {
                        GuiReplayViewer.this.mod.startReplay(file);
                     } catch (IOException e) {
                        e.printStackTrace();
                     }
                  } else {
                     Iterator<Pair<File, List<RenderJob>>> replays = selected.stream()
                        .filter(it -> !it.renderQueue.isEmpty())
                        .map(it -> Pair.of(it.file, it.renderQueue))
                        .iterator();
                     GuiRenderQueue.processMultipleReplays(GuiReplayViewer.this, GuiReplayViewer.this.mod, replays, () -> {
                        this.loading = false;
                        GuiReplayViewer.this.updateButtons();
                        GuiReplayViewer.this.display();
                     });
                  }
               }
            }
         }
      )
      .setSize(150, 20);
   public final GuiButton folderButton = new GuiButton().onClick(new Runnable() {
      @Override
      public void run() {
         try {
            File folder = GuiReplayViewer.this.mod.getCore().folders.getReplayFolder().toFile();
            MCVer.openFile(folder);
         } catch (IOException e) {
            GuiReplayViewer.this.mod.getLogger().error("Cannot open file", e);
         }
      }
   }).setSize(150, 20).setI18nLabel("replaymod.gui.viewer.replayfolder");
   public final GuiButton renameButton = new GuiButton()
      .onClick(
         new Runnable() {
            @Override
            public void run() {
               Path path = GuiReplayViewer.this.list.getSelected().get(0).file.toPath();
               String name = Utils.fileNameToReplayName(path.getFileName().toString());
               GuiTextField nameField = new GuiTextField().setSize(200, 20).setFocused(true).setText(name);
               final GuiYesNoPopup popup = GuiYesNoPopup.open(
                     GuiReplayViewer.this, new GuiLabel().setI18nText("replaymod.gui.viewer.rename.name").setColor(Colors.BLACK), nameField
                  )
                  .setYesI18nLabel("replaymod.gui.rename")
                  .setNoI18nLabel("replaymod.gui.cancel");
               ((VerticalLayout)popup.getInfo().getLayout()).setSpacing(7);
               nameField.onEnter(new Runnable() {
                     @Override
                     public void run() {
                        if (popup.getYesButton().isEnabled()) {
                           popup.getYesButton().onClick(new Click(-1, -1, 0, 0));
                        }
                     }
                  })
                  .onTextChanged(
                     obj -> popup.getYesButton()
                        .setEnabled(!nameField.getText().isEmpty() && Files.notExists(Utils.replayNameToPath(path.getParent(), nameField.getText())))
                  );
               popup.onAccept(
                  () -> {
                     String newName = nameField.getText().trim();
                     Path targetPath = Utils.replayNameToPath(path.getParent(), newName);

                     try {
                        Files.move(path, targetPath);
                     } catch (IOException e) {
                        e.printStackTrace();
                        GuiReplayViewer.this.getMinecraft()
                           .displayGuiScreen(
                              new GuiErrorScreen(
                                 I18n.format("replaymod.gui.viewer.delete.failed1", new Object[0]),
                                 I18n.format("replaymod.gui.viewer.delete.failed2", new Object[0])
                              )
                           );
                        return;
                     }

                     GuiReplayViewer.this.list.load();
                  }
               );
            }
         }
      )
      .setSize(73, 20)
      .setI18nLabel("replaymod.gui.rename")
      .setDisabled();
   public final GuiButton deleteButton = new GuiButton()
      .onClick(
         () -> {
            for (GuiReplayViewer.GuiReplayEntry entry : this.list.getSelected()) {
               String name = entry.name.getText();
               GuiYesNoPopup.open(
                     this,
                     new GuiLabel().setI18nText("replaymod.gui.viewer.delete.linea").setColor(Colors.BLACK),
                     new GuiLabel().setI18nText("replaymod.gui.viewer.delete.lineb", name + EnumChatFormatting.RESET).setColor(Colors.BLACK)
                  )
                  .setYesI18nLabel("replaymod.gui.delete")
                  .setNoI18nLabel("replaymod.gui.cancel")
                  .onAccept(() -> {
                     try {
                        FileUtils.forceDelete(entry.file);
                     } catch (IOException e) {
                        e.printStackTrace();
                     }

                     this.list.load();
                  });
            }
         }
      )
      .setSize(73, 20)
      .setI18nLabel("replaymod.gui.delete")
      .setDisabled();
   public final GuiButton settingsButton = new GuiButton(this)
      .setSize(20, 20)
      .setTexture(ReplayMod.TEXTURE, 256)
      .setSpriteUV(20, 0)
      .setTooltip(new GuiTooltip().setI18nText("replaymod.gui.settings"))
      .onClick(() -> new GuiReplaySettings(this.toMinecraft(), this.getMod().getCore().getSettingsRegistry()).display());
   public final GuiButton cancelButton = new GuiButton().onClick(new Runnable() {
      @Override
      public void run() {
         GuiReplayViewer.this.getMinecraft().displayGuiScreen(null);
      }
   }).setSize(73, 20).setI18nLabel("replaymod.gui.cancel");
   public final List<GuiButton> replaySpecificButtons = new ArrayList<>();
   public final GuiPanel editorButton;
   public final GuiPanel upperButtonPanel;
   public final GuiPanel lowerButtonPanel;
   public final GuiPanel buttonPanel;
   private static final GuiImage DEFAULT_THUMBNAIL = new GuiImage().setTexture(Utils.DEFAULT_THUMBNAIL);

   public GuiReplayViewer(ReplayModReplay mod) {
      this.replaySpecificButtons.addAll(Arrays.asList(this.renameButton));
      this.editorButton = new GuiPanel();
      this.upperButtonPanel = new GuiPanel().setLayout(new HorizontalLayout().setSpacing(5)).addElements(null, this.loadButton);
      this.lowerButtonPanel = new GuiPanel()
         .setLayout(new HorizontalLayout().setSpacing(5))
         .addElements(null, this.renameButton, this.deleteButton, this.editorButton, this.cancelButton);
      this.buttonPanel = new GuiPanel(this).setLayout(new VerticalLayout().setSpacing(5)).addElements(null, this.upperButtonPanel, this.lowerButtonPanel);
      this.mod = mod;

      try {
         this.list.setFolder(mod.getCore().folders.getReplayFolder().toFile());
      } catch (IOException e) {
         throw new ReportedException(CrashReport.makeCrashReport(e, "Getting replay folder"));
      }

      this.setTitle(new GuiLabel().setI18nText("replaymod.gui.replayviewer"));
      this.setLayout(
         new CustomLayout<GuiScreen>() {
            protected void layout(GuiScreen container, int width, int height) {
               this.pos(
                  GuiReplayViewer.this.buttonPanel,
                  width / 2 - this.width(GuiReplayViewer.this.buttonPanel) / 2,
                  height - 10 - this.height(GuiReplayViewer.this.buttonPanel)
               );
               this.pos(GuiReplayViewer.this.list, 0, 30);
               this.size(GuiReplayViewer.this.list, width, this.y(GuiReplayViewer.this.buttonPanel) - 10 - this.y(GuiReplayViewer.this.list));
               this.pos(GuiReplayViewer.this.settingsButton, width - this.width(GuiReplayViewer.this.settingsButton) - 5, 5);
            }
         }
      );
      // Same animated shader background as the singleplayer/multiplayer menus.
      this.setBackground(GuiScreen.Background.NONE);
      this.updateButtons();
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      if (renderInfo.layer == 0) {
         redacted.client.util.render.MenuBackground.render(size.getWidth(), size.getHeight());
      }
      super.draw(renderer, size, renderInfo);
   }

   public ReplayModReplay getMod() {
      return this.mod;
   }

   private void updateButtons() {
      List<GuiReplayViewer.GuiReplayEntry> selected = this.list.getSelected();
      int count = selected.size();
      this.replaySpecificButtons.forEach(b -> {
         GuiButton var10000 = b.setEnabled(count == 1);
      });
      this.deleteButton.setEnabled(count > 0);
      if (count > 1) {
         Set<RenderJob> jobs = selected.stream().flatMap(entry -> entry.renderQueue.stream()).collect(Collectors.toSet());
         String[] tooltipLines = jobs.stream().map(RenderJob::getName).toArray(String[]::new);
         this.loadButton.setI18nLabel("replaymod.gui.viewer.bulkrender", jobs.size());
         this.loadButton.setTooltip(new GuiTooltip().setText(tooltipLines));
         this.loadButton.setEnabled(!jobs.isEmpty());
         String[] compatError = VideoRenderer.checkCompat(jobs.stream().map(RenderJob::getSettings));
         if (compatError != null) {
            this.loadButton.setDisabled().setTooltip(new GuiTooltip().setText(compatError));
         }
      } else {
         this.loadButton.setI18nLabel("replaymod.gui.load");
         this.loadButton.setTooltip(null);
         this.loadButton.setEnabled(count == 1 && !selected.get(0).incompatible);
      }
   }

   public static class GuiReplayEntry extends AbstractGuiContainer<GuiReplayViewer.GuiReplayEntry> implements Comparable<GuiReplayViewer.GuiReplayEntry> {
      public final File file;
      public final GuiLabel name = new GuiLabel();
      public final GuiLabel server = new GuiLabel().setColor(Colors.LIGHT_GRAY);
      public final GuiLabel date = new GuiLabel().setColor(Colors.LIGHT_GRAY);
      public final GuiPanel infoPanel = new GuiPanel(this).setLayout(new VerticalLayout().setSpacing(2)).addElements(null, this.name, this.server, this.date);
      public final GuiLabel version = new GuiLabel(this).setColor(Colors.RED);
      public final GuiImage thumbnail;
      public final GuiLabel duration = new GuiLabel();
      public final GuiPanel durationPanel = new GuiPanel()
         .setBackgroundColor(Colors.HALF_TRANSPARENT)
         .addElements(null, this.duration)
         .setLayout(new CustomLayout<GuiPanel>() {
            protected void layout(GuiPanel container, int width, int height) {
               this.pos(GuiReplayEntry.this.duration, 2, 2);
            }

            @Override
            public ReadableDimension calcMinSize(GuiContainer<?> container) {
               ReadableDimension dimension = GuiReplayEntry.this.duration.calcMinSize();
               return new Dimension(dimension.getWidth() + 2, dimension.getHeight() + 2);
            }
         });
      public final GuiImage renderQueueIcon = new GuiImage().setSize(10, 10).setTexture(ReplayMod.TEXTURE, 40, 0, 20, 20);
      private final long dateMillis;
      private final boolean incompatible;
      private final List<RenderJob> renderQueue;

      public GuiReplayEntry(File file, ReplayMetaData metaData, Image thumbImage, List<RenderJob> renderQueue) {
         this.file = file;
         this.renderQueue = renderQueue;
         this.name.setText(EnumChatFormatting.UNDERLINE + Utils.fileNameToReplayName(file.getName()));
         if (!StringUtils.isEmpty(metaData.getCustomServerName())) {
            this.server.setText(metaData.getCustomServerName());
         } else if (!StringUtils.isEmpty(metaData.getServerName()) && ReplayMod.instance.getSettingsRegistry().get(Setting.SHOW_SERVER_IPS)) {
            this.server.setText(metaData.getServerName());
         } else {
            this.server.setI18nText("replaymod.gui.iphidden").setColor(Colors.DARK_RED);
         }

         this.incompatible = !ReplayMod.isCompatible(metaData.getFileFormatVersion(), metaData.getRawProtocolVersionOr0());
         if (this.incompatible) {
            this.version.setText("Minecraft " + metaData.getMcVersion());
         }

         this.dateMillis = metaData.getDate();
         this.date.setText(new SimpleDateFormat().format(new Date(this.dateMillis)));
         if (thumbImage == null) {
            this.thumbnail = new GuiImage(GuiReplayViewer.DEFAULT_THUMBNAIL).setSize(53, 30);
            this.addElements(null, this.thumbnail);
         } else {
            this.thumbnail = new GuiImage(this).setTexture(thumbImage).setSize(53, 30);
         }

         this.duration.setText(Utils.convertSecondsToShortString(metaData.getDuration() / 1000));
         this.addElements(null, this.durationPanel);
         if (!renderQueue.isEmpty()) {
            this.renderQueueIcon.setTooltip(new GuiTooltip().setText(renderQueue.stream().map(RenderJob::getName).toArray(String[]::new)));
            this.addElements(null, this.renderQueueIcon);
         }

         this.setLayout(new CustomLayout<GuiReplayViewer.GuiReplayEntry>() {
            protected void layout(GuiReplayViewer.GuiReplayEntry container, int width, int height) {
               this.pos(GuiReplayEntry.this.thumbnail, 0, 0);
               this.x(GuiReplayEntry.this.durationPanel, this.width(GuiReplayEntry.this.thumbnail) - this.width(GuiReplayEntry.this.durationPanel));
               this.y(GuiReplayEntry.this.durationPanel, this.height(GuiReplayEntry.this.thumbnail) - this.height(GuiReplayEntry.this.durationPanel));
               this.pos(GuiReplayEntry.this.infoPanel, this.width(GuiReplayEntry.this.thumbnail) + 5, 0);
               this.pos(GuiReplayEntry.this.version, width - this.width(GuiReplayEntry.this.version), 0);
               if (GuiReplayEntry.this.renderQueueIcon.getContainer() != null) {
                  this.pos(GuiReplayEntry.this.renderQueueIcon, this.width(GuiReplayEntry.this.thumbnail) - this.width(GuiReplayEntry.this.renderQueueIcon), 0);
               }
            }

            @Override
            public ReadableDimension calcMinSize(GuiContainer<?> container) {
               return new Dimension(300, GuiReplayEntry.this.thumbnail.getMinSize().getHeight());
            }
         });
      }

      protected GuiReplayViewer.GuiReplayEntry getThis() {
         return this;
      }

      public int compareTo(GuiReplayViewer.GuiReplayEntry o) {
         return Long.compare(o.dateMillis, this.dateMillis);
      }
   }

   public static class GuiReplayList
      extends AbstractGuiResourceLoadingList<GuiReplayViewer.GuiReplayList, GuiReplayViewer.GuiReplayEntry>
      implements KeyHandler,
      CharHandler {
      private File folder = null;
      private final GuiTextField filterTextField = new GuiTextField().setFocused(true);

      public GuiReplayList(GuiContainer container) {
         super(container);
         this.onLoad(results -> {
            /* 
             * Finished recordings are written to <replayFolder>/recording and only moved into the replay folder by the startup scan, so list both directories or new recordings would stay invisible until a restart.
             */
            Map<File, Long> lastModified = new HashMap<>();
            List<File> files = new ArrayList<>();
            File[] replayFiles = this.folder.listFiles((java.io.FileFilter) new SuffixFileFilter(".mcpr", IOCase.INSENSITIVE));
            if (replayFiles != null) {
               files.addAll(Arrays.asList(replayFiles));
            } else {
               ReplayModReplay.LOGGER.warn("Failed to list files in {}", new Object[]{this.folder});
            }

            File recordingFolder = new File(this.folder, "recording");
            if (recordingFolder.isDirectory()) {
               File[] recordingFiles = recordingFolder.listFiles((java.io.FileFilter) new SuffixFileFilter(".mcpr", IOCase.INSENSITIVE));
               if (recordingFiles != null) {
                  files.addAll(Arrays.asList(recordingFiles));
               }
            }

            files.sort(Comparator.<File>comparingLong(f -> lastModified.computeIfAbsent(f, File::lastModified)).reversed());

            for (File file : files) {
                  if (Thread.interrupted()) {
                     break;
                  }

                  try (ReplayFile replayFile = ReplayMod.instance.files.open(file.toPath())) {
                     Image thumb = Optional.ofNullable(replayFile.getThumbBytes().orNull()).flatMap(stream -> {
                        try (InputStream in = stream) {
                           return Optional.of(Image.read(in));
                        } catch (IOException e) {
                           e.printStackTrace();
                           return Optional.empty();
                        }
                     }).orElse(null);
                     ReplayMetaData metaData = replayFile.getMetaData();
                     List<RenderJob> renderQueue = RenderJob.readQueue(replayFile);
                     if (metaData != null) {
                        results.consume(() -> new GuiReplayViewer.GuiReplayEntry(file, metaData, thumb, renderQueue) {
                           @Override
                           public ReadableDimension calcMinSize() {
                              return GuiReplayList.this.isFiltered(this) ? new Dimension(-4, -4) : super.calcMinSize();
                           }
                        });
                     }
                  } catch (Exception e) {
                     ReplayModReplay.LOGGER.error("Could not load Replay File {}", new Object[]{file.getName(), e});
                  }
            }
         }).setDrawShadow(true).setDrawSlider(true);
      }

      public void setFolder(File folder) {
         this.folder = folder;
      }

      private boolean isFiltered(GuiReplayViewer.GuiReplayEntry entry) {
         String filter = this.filterTextField.getText().toLowerCase();
         return filter.isEmpty() ? false : !entry.name.getText().toLowerCase().contains(filter);
      }

      @Override
      public boolean handleKey(KeyInput keyInput) {
         if (keyInput.key == 59) {
            SettingsRegistry reg = ReplayMod.instance.getSettingsRegistry();
            reg.set(Setting.SHOW_SERVER_IPS, !reg.get(Setting.SHOW_SERVER_IPS));
            reg.save();
            this.load();
         }

         boolean filterHasPriority = !this.filterTextField.getText().isEmpty();
         if (filterHasPriority && this.filterTextField.handleKey(keyInput)) {
            this.scrollY(0);
            return true;
         } else if (super.handleKey(keyInput)) {
            return true;
         } else if (!filterHasPriority && this.filterTextField.handleKey(keyInput)) {
            this.scrollY(0);
            return true;
         } else {
            return false;
         }
      }

      @Override
      public boolean handleChar(CharInput charInput) {
         if (this.filterTextField.handleChar(charInput)) {
            this.scrollY(0);
            return true;
         } else {
            return false;
         }
      }

      @Override
      public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
         super.draw(renderer, size, renderInfo);
         String filter = this.filterTextField.getText();
         if (!filter.isEmpty()) {
            boolean anyMatches = this.getListPanel().calcMinSize().getHeight() > 0;
            FontRenderer fontRenderer = com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer.getFontRenderer();
            int filterTextWidth = fontRenderer.getStringWidth(filter);
            int filterTextHeight = fontRenderer.FONT_HEIGHT;
            renderer.drawRect(
               size.getWidth() - 3 - 2 - filterTextWidth - 2,
               size.getHeight() - 3 - 2 - filterTextHeight - 2,
               2 + filterTextWidth + 2,
               2 + filterTextHeight + 2,
               Colors.WHITE
            );
            renderer.drawString(
               size.getWidth() - 3 - 2 - filterTextWidth, size.getHeight() - 3 - 2 - filterTextHeight, anyMatches ? Colors.BLACK : Colors.DARK_RED, filter
            );
         }
      }

      protected GuiReplayViewer.GuiReplayList getThis() {
         return this;
      }
   }

   public static class GuiSelectReplayPopup extends AbstractGuiPopup<GuiReplayViewer.GuiSelectReplayPopup> {
      private final SettableFuture<File> future = SettableFuture.create();
      private final GuiReplayViewer.GuiReplayList list = new GuiReplayViewer.GuiReplayList(this.popup);
      private final GuiButton acceptButton = new GuiButton(this.popup).setI18nLabel("gui.done").setSize(50, 20).setDisabled();
      private final GuiButton cancelButton = new GuiButton(this.popup).setI18nLabel("gui.cancel").setSize(50, 20);

      public static GuiReplayViewer.GuiSelectReplayPopup openGui(GuiContainer container, File folder) {
         GuiReplayViewer.GuiSelectReplayPopup popup = new GuiReplayViewer.GuiSelectReplayPopup(container, folder);
         popup.list.load();
         popup.open();
         return popup;
      }

      public GuiSelectReplayPopup(GuiContainer container, File folder) {
         super(container);
         this.list.setFolder(folder);
         this.list.onSelectionChanged(() -> this.acceptButton.setEnabled(this.list.getSelected() != null)).onSelectionDoubleClicked(() -> {
            this.close();
            this.future.set(this.list.getSelected().get(0).file);
         });
         this.acceptButton.onClick(() -> {
            this.future.set(this.list.getSelected().get(0).file);
            this.close();
         });
         this.cancelButton.onClick(() -> {
            this.future.set(null);
            this.close();
         });
         this.popup
            .setLayout(
               new CustomLayout<GuiPanel>() {
                  protected void layout(GuiPanel container, int width, int height) {
                     this.pos(
                        GuiSelectReplayPopup.this.cancelButton,
                        width - this.width(GuiSelectReplayPopup.this.cancelButton),
                        height - this.height(GuiSelectReplayPopup.this.cancelButton)
                     );
                     this.pos(
                        GuiSelectReplayPopup.this.acceptButton,
                        this.x(GuiSelectReplayPopup.this.cancelButton) - 5 - this.width(GuiSelectReplayPopup.this.acceptButton),
                        this.y(GuiSelectReplayPopup.this.cancelButton)
                     );
                     this.pos(GuiSelectReplayPopup.this.list, 0, 5);
                     this.size(GuiSelectReplayPopup.this.list, width, height - this.height(GuiSelectReplayPopup.this.cancelButton) - 10);
                  }

                  @Override
                  public ReadableDimension calcMinSize(GuiContainer container) {
                     return new Dimension(330, 200);
                  }
               }
            );
      }

      public SettableFuture<File> getFuture() {
         return this.future;
      }

      public GuiReplayViewer.GuiReplayList getList() {
         return this.list;
      }

      public GuiButton getAcceptButton() {
         return this.acceptButton;
      }

      public GuiButton getCancelButton() {
         return this.cancelButton;
      }

      protected GuiReplayViewer.GuiSelectReplayPopup getThis() {
         return this;
      }
   }
}
