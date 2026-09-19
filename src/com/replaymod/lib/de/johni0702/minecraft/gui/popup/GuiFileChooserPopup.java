package com.replaymod.lib.de.johni0702.minecraft.gui.popup;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScrollable;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiVerticalList;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTextField;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiDropdownMenu;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Consumer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class GuiFileChooserPopup extends AbstractGuiPopup<GuiFileChooserPopup> implements KeyHandler {
   private Consumer<File> onAccept = file -> {};
   private Runnable onCancel = () -> {};
   private final GuiScrollable pathScrollable = new GuiScrollable(this.popup) {
      @Override
      public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
         this.scrollX(0);
         super.draw(renderer, size, renderInfo);
      }
   };
   private final GuiPanel pathPanel = new GuiPanel(this.pathScrollable).setLayout(new HorizontalLayout());
   private final GuiVerticalList fileList = new GuiVerticalList(this.popup);
   private final GuiTextField nameField = new GuiTextField(this.popup).onEnter(new Runnable() {
      @Override
      public void run() {
         if (GuiFileChooserPopup.this.acceptButton.isEnabled()) {
            GuiFileChooserPopup.this.acceptButton.onClick(new Click(-1, -1, 0, 0));
         }
      }
   }).onTextChanged(new Consumer<String>() {
      public void consume(String oldName) {
         GuiFileChooserPopup.this.updateButton();
      }
   }).setMaxLength(Integer.MAX_VALUE);
   private final GuiButton acceptButton = new GuiButton(this.popup).onClick(new Runnable() {
      @Override
      public void run() {
         String fileName = GuiFileChooserPopup.this.nameField.getText();
         if (!GuiFileChooserPopup.this.load && GuiFileChooserPopup.this.fileExtensions.length > 0 && !GuiFileChooserPopup.this.hasValidExtension(fileName)) {
            fileName = fileName + "." + GuiFileChooserPopup.this.fileExtensions[0];
         }

         GuiFileChooserPopup.this.onAccept.consume(new File(GuiFileChooserPopup.this.folder, fileName));
         GuiFileChooserPopup.this.close();
      }
   }).setSize(50, 20);
   private final GuiButton cancelButton = new GuiButton(this.popup).onClick(new Runnable() {
      @Override
      public void run() {
         GuiFileChooserPopup.this.onCancel.run();
         GuiFileChooserPopup.this.close();
      }
   }).setI18nLabel("gui.cancel").setSize(50, 20);
   private final String[] fileExtensions;
   private final boolean load;
   private File folder;

   public static GuiFileChooserPopup openSaveGui(GuiContainer container, String buttonLabel, String... fileExtensions) {
      GuiFileChooserPopup popup = new GuiFileChooserPopup(container, fileExtensions, false).setBackgroundColor(Colors.DARK_TRANSPARENT);
      popup.acceptButton.setI18nLabel(buttonLabel);
      popup.open();
      return popup;
   }

   public static GuiFileChooserPopup openLoadGui(GuiContainer container, String buttonLabel, String... fileExtensions) {
      GuiFileChooserPopup popup = new GuiFileChooserPopup(container, fileExtensions, true).setBackgroundColor(Colors.DARK_TRANSPARENT);
      popup.acceptButton.setI18nLabel(buttonLabel).setDisabled();
      popup.open();
      return popup;
   }

   public GuiFileChooserPopup(GuiContainer container, String[] fileExtensions, boolean load) {
      super(container);
      this.fileList.setLayout(new VerticalLayout().setSpacing(1));
      this.popup
         .setLayout(
            new CustomLayout<GuiPanel>() {
               protected void layout(GuiPanel container, int width, int height) {
                  this.pos(GuiFileChooserPopup.this.pathScrollable, 0, 0);
                  this.size(GuiFileChooserPopup.this.pathScrollable, width, 20);
                  this.pos(
                     GuiFileChooserPopup.this.cancelButton,
                     width - this.width(GuiFileChooserPopup.this.cancelButton),
                     height - this.height(GuiFileChooserPopup.this.cancelButton)
                  );
                  this.pos(
                     GuiFileChooserPopup.this.acceptButton,
                     this.x(GuiFileChooserPopup.this.cancelButton) - 5 - this.width(GuiFileChooserPopup.this.acceptButton),
                     this.y(GuiFileChooserPopup.this.cancelButton)
                  );
                  this.size(GuiFileChooserPopup.this.nameField, this.x(GuiFileChooserPopup.this.acceptButton) - 5, 20);
                  this.pos(GuiFileChooserPopup.this.nameField, 0, height - this.height(GuiFileChooserPopup.this.nameField));
                  this.pos(
                     GuiFileChooserPopup.this.fileList,
                     0,
                     this.y(GuiFileChooserPopup.this.pathScrollable) + this.height(GuiFileChooserPopup.this.pathScrollable) + 5
                  );
                  this.size(
                     GuiFileChooserPopup.this.fileList, width, this.y(GuiFileChooserPopup.this.nameField) - this.y(GuiFileChooserPopup.this.fileList) - 5
                  );
               }

               @Override
               public ReadableDimension calcMinSize(GuiContainer container) {
                  return new Dimension(300, 200);
               }
            }
         );
      this.fileExtensions = fileExtensions;
      this.load = load;
      this.setFolder(new File("."));
   }

   protected void updateButton() {
      String name = this.nameField.getText();
      File file = new File(this.folder, name);
      boolean valid = !name.contains(File.separator);

      try {
         file.toPath();
      } catch (InvalidPathException ignored) {
         valid = false;
      }

      if (this.load) {
         valid &= file.exists();
      }

      this.acceptButton.setEnabled(valid);
   }

   public void setFolder(File folder) {
      if (!folder.isDirectory()) {
         throw new IllegalArgumentException("Folder has to be a directory.");
      }

      try {
         this.folder = folder = folder.getCanonicalFile();
      } catch (IOException e) {
         this.close();
         throw new RuntimeException(e);
      }

      this.updateButton();

      for (GuiElement element : new ArrayList<>(this.pathPanel.getElements().keySet())) {
         this.pathPanel.removeElement(element);
      }

      for (GuiElement element : new ArrayList<>(this.fileList.getListPanel().getElements().keySet())) {
         this.fileList.getListPanel().removeElement(element);
      }

      File[] files = folder.listFiles();
      if (files != null) {
         Arrays.sort(files, new Comparator<File>() {
            public int compare(File f1, File f2) {
               if (f1.isDirectory() && !f2.isDirectory()) {
                  return -1;
               } else {
                  return !f1.isDirectory() && f2.isDirectory() ? 1 : f1.getName().compareToIgnoreCase(f2.getName());
               }
            }
         });

         for (final File file : files) {
            if (file.isDirectory()) {
               this.fileList.getListPanel().addElements(new VerticalLayout.Data(0.0), new GuiButton().onClick(new Runnable() {
                  @Override
                  public void run() {
                     GuiFileChooserPopup.this.setFolder(file);
                  }
               }).setLabel(file.getName() + File.separator));
            } else if (this.hasValidExtension(file.getName())) {
               this.fileList.getListPanel().addElements(new VerticalLayout.Data(0.0), new GuiButton().onClick(new Runnable() {
                  @Override
                  public void run() {
                     GuiFileChooserPopup.this.setFileName(file.getName());
                  }
               }).setLabel(file.getName()));
            }
         }
      }

      this.fileList.setOffsetY(0);
      File[] roots = File.listRoots();
      if (roots != null && roots.length > 1) {
         final GuiDropdownMenu<File> dropdown = new GuiDropdownMenu<File>(this.pathPanel) {
            private final GuiButton skin = new GuiButton();

            @Override
            protected ReadableDimension calcMinSize() {
               ReadableDimension dim = super.calcMinSize();
               return new Dimension(dim.getWidth() - 5 - MCVer.getFontRenderer().FONT_HEIGHT, dim.getHeight());
            }

            @Override
            public void layout(ReadableDimension size, RenderInfo renderInfo) {
               super.layout(size, renderInfo);
               if (renderInfo.layer == 0) {
                  this.skin.layout(size, renderInfo);
               }
            }

            @Override
            public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
               super.draw(renderer, size, renderInfo);
               if (renderInfo.layer == 0) {
                  this.skin.setLabel(this.getSelectedValue().toString());
                  this.skin.draw(renderer, size, renderInfo);
               }
            }
         };
         List<File> actualRoots = new ArrayList<>();
         File selected = null;

         for (File root : roots) {
            if (root.isDirectory()) {
               actualRoots.add(root);
               if (folder.getAbsolutePath().startsWith(root.getAbsolutePath())) {
                  selected = root;
               }
            }
         }

         assert selected != null;
         dropdown.setValues(actualRoots.toArray(new File[actualRoots.size()])).setSelected(selected);
         dropdown.onSelection(new Consumer<Integer>() {
            public void consume(Integer old) {
               GuiFileChooserPopup.this.setFolder(dropdown.getSelectedValue());
            }
         });
      }

      LinkedList<File> parents = new LinkedList<>();

      while (folder != null) {
         parents.addFirst(folder);
         folder = folder.getParentFile();
      }

      for (final File parent : parents) {
         this.pathPanel.addElements(null, new GuiButton().onClick(new Runnable() {
            @Override
            public void run() {
               GuiFileChooserPopup.this.setFolder(parent);
            }
         }).setLabel(parent.getName() + File.separator));
      }

      this.pathScrollable.setOffsetX(Integer.MAX_VALUE);
   }

   public void setFileName(String fileName) {
      this.nameField.setText(fileName);
      this.nameField.setCursorPosition(fileName.length());
      this.updateButton();
   }

   private boolean hasValidExtension(String name) {
      for (String fileExtension : this.fileExtensions) {
         if (name.endsWith("." + fileExtension)) {
            return true;
         }
      }

      return false;
   }

   protected GuiFileChooserPopup getThis() {
      return this;
   }

   public GuiFileChooserPopup onAccept(Consumer<File> onAccept) {
      this.onAccept = onAccept;
      return this;
   }

   public GuiFileChooserPopup onCancel(Runnable onCancel) {
      this.onCancel = onCancel;
      return this;
   }

   @Override
   public boolean handleKey(KeyInput keyInput) {
      if (keyInput.isEscape()) {
         this.cancelButton.onClick(new Click(-1, -1, 0, keyInput.modifiers));
         return true;
      } else {
         return false;
      }
   }

   public GuiButton getAcceptButton() {
      return this.acceptButton;
   }

   public GuiButton getCancelButton() {
      return this.cancelButton;
   }
}
