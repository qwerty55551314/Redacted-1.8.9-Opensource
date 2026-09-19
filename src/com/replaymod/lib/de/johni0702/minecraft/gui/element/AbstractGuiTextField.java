package com.replaymod.lib.de.johni0702.minecraft.gui.element;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.CharHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.CharInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Clickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Focusable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Tickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Consumer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Utils;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

public abstract class AbstractGuiTextField<T extends AbstractGuiTextField<T>>
   extends AbstractGuiElement<T>
   implements Clickable,
   Tickable,
   KeyHandler,
   CharHandler,
   IGuiTextField<T> {
   private static final ReadableColor BORDER_COLOR = new Color(160, 160, 160);
   private static final ReadableColor CURSOR_COLOR = new Color(240, 240, 240);
   private static final int BORDER = 4;
   private boolean focused;
   private Focusable next;
   private Focusable previous;
   private int maxLength = 32;
   private String text = "";
   private int cursorPos;
   private int selectionPos;
   private String hint;
   private int currentOffset;
   private int blinkCursorTick;
   private ReadableColor textColorEnabled = new Color(224, 224, 224);
   private ReadableColor textColorDisabled = new Color(112, 112, 112);
   private ReadableDimension size = new Dimension(0, 0);
   private Consumer<String> textChanged;
   private Consumer<Boolean> focusChanged;
   private Runnable onEnter;

   public AbstractGuiTextField() {
   }

   public AbstractGuiTextField(GuiContainer container) {
      super(container);
   }

   public T setText(String text) {
      if (text.length() > this.maxLength) {
         text = text.substring(0, this.maxLength);
      }

      this.text = text;
      this.selectionPos = this.cursorPos = text.length();
      return this.getThis();
   }

   public T setI18nText(String text, Object... args) {
      return this.setText(I18n.format(text, args));
   }

   public T setMaxLength(int maxLength) {
      Preconditions.checkArgument(maxLength >= 0, "maxLength must not be negative");
      this.maxLength = maxLength;
      if (this.text.length() > maxLength) {
         this.setText(this.text);
      }

      return this.getThis();
   }

   @Override
   public String deleteText(int from, int to) {
      Preconditions.checkArgument(from <= to, "from must not be greater than to");
      Preconditions.checkArgument(from >= 0, "from must be greater than zero");
      Preconditions.checkArgument(to < this.text.length(), "to must be less than test.length()");
      String deleted = this.text.substring(from, to + 1);
      this.text = this.text.substring(0, from) + this.text.substring(to + 1);
      return deleted;
   }

   @Override
   public int getSelectionFrom() {
      return this.cursorPos > this.selectionPos ? this.selectionPos : this.cursorPos;
   }

   @Override
   public int getSelectionTo() {
      return this.cursorPos > this.selectionPos ? this.cursorPos : this.selectionPos;
   }

   @Override
   public String getSelectedText() {
      return this.text.substring(this.getSelectionFrom(), this.getSelectionTo());
   }

   @Override
   public String deleteSelectedText() {
      if (this.cursorPos == this.selectionPos) {
         return "";
      }

      int from = this.getSelectionFrom();
      String deleted = this.deleteText(from, this.getSelectionTo() - 1);
      this.cursorPos = this.selectionPos = from;
      return deleted;
   }

   private void updateCurrentOffset() {
      this.currentOffset = Math.min(this.currentOffset, this.cursorPos);
      String line = this.text.substring(this.currentOffset, this.cursorPos);
      FontRenderer fontRenderer = MCVer.getFontRenderer();
      int currentWidth = fontRenderer.getStringWidth(line);
      if (currentWidth > this.size.getWidth() - 8) {
         this.currentOffset = this.cursorPos - fontRenderer.trimStringToWidth(line, this.size.getWidth() - 8, true).length();
      }
   }

   public T writeText(String append) {
      for (char c : append.toCharArray()) {
         this.writeChar(c);
      }

      return this.getThis();
   }

   public T writeChar(char c) {
      if (!CharInput.isValidChar(c)) {
         return this.getThis();
      }

      this.deleteSelectedText();
      if (this.text.length() >= this.maxLength) {
         return this.getThis();
      }

      this.text = this.text.substring(0, this.cursorPos) + c + this.text.substring(this.cursorPos);
      this.selectionPos = ++this.cursorPos;
      return this.getThis();
   }

   public T deleteNextChar() {
      if (this.cursorPos < this.text.length()) {
         this.text = this.text.substring(0, this.cursorPos) + this.text.substring(this.cursorPos + 1);
      }

      this.selectionPos = this.cursorPos;
      return this.getThis();
   }

   protected int getNextWordLength() {
      int length = 0;
      boolean inWord = true;

      for (int i = this.cursorPos; i < this.text.length(); i++) {
         if (inWord) {
            if (this.text.charAt(i) == ' ') {
               inWord = false;
            }
         } else if (this.text.charAt(i) != ' ') {
            return length;
         }

         length++;
      }

      return length;
   }

   @Override
   public String deleteNextWord() {
      int worldLength = this.getNextWordLength();
      return worldLength > 0 ? this.deleteText(this.cursorPos, this.cursorPos + worldLength - 1) : "";
   }

   public T deletePreviousChar() {
      if (this.cursorPos > 0) {
         this.text = this.text.substring(0, this.cursorPos - 1) + this.text.substring(this.cursorPos);
         this.selectionPos = --this.cursorPos;
      }

      return this.getThis();
   }

   protected int getPreviousWordLength() {
      int length = 0;
      boolean inWord = false;

      for (int i = this.cursorPos - 1; i >= 0; i--) {
         if (inWord) {
            if (this.text.charAt(i) == ' ') {
               return length;
            }
         } else if (this.text.charAt(i) != ' ') {
            inWord = true;
         }

         length++;
      }

      return length;
   }

   @Override
   public String deletePreviousWord() {
      int worldLength = this.getPreviousWordLength();
      String deleted = "";
      if (worldLength > 0) {
         deleted = this.deleteText(this.cursorPos - worldLength, this.cursorPos - 1);
         this.selectionPos = this.cursorPos -= worldLength;
      }

      return deleted;
   }

   public T setCursorPosition(int pos) {
      Preconditions.checkArgument(pos >= 0 && pos <= this.text.length());
      this.selectionPos = this.cursorPos = pos;
      return this.getThis();
   }

   @Override
   protected ReadableDimension calcMinSize() {
      return new Dimension(0, 0);
   }

   @Override
   public boolean mouseClick(Click click) {
      ReadablePoint position = click;
      if (this.getContainer() != null) {
         this.getContainer().convertFor(this, (Point)(position = new Point(position)));
      }

      boolean hovering = this.isMouseHovering(position);
      if (hovering && this.isFocused() && click.button == 0) {
         this.updateCurrentOffset();
         int mouseX = position.getX() - 4;
         FontRenderer fontRenderer = MCVer.getFontRenderer();
         String text = this.text.substring(this.currentOffset);
         int textX = fontRenderer.trimStringToWidth(text, mouseX).length() + this.currentOffset;
         this.setCursorPosition(textX);
         return true;
      } else {
         this.setFocused(hovering);
         return false;
      }
   }

   protected boolean isMouseHovering(ReadablePoint pos) {
      return pos.getX() > 0 && pos.getY() > 0 && pos.getX() < this.size.getWidth() && pos.getY() < this.size.getHeight();
   }

   public T setFocused(boolean isFocused) {
      if (isFocused && !this.focused) {
         this.blinkCursorTick = 0;
      }

      if (this.focused != isFocused) {
         this.focused = isFocused;
         this.onFocusChanged(this.focused);
      }

      return this.getThis();
   }

   public T setNext(Focusable next) {
      this.next = next;
      return this.getThis();
   }

   public T setPrevious(Focusable previous) {
      this.previous = previous;
      return this.getThis();
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      this.size = size;
      this.updateCurrentOffset();
      super.draw(renderer, size, renderInfo);
      int width = size.getWidth();
      int height = size.getHeight();
      FontRenderer fontRenderer = MCVer.getFontRenderer();
      int posY = height / 2 - fontRenderer.FONT_HEIGHT / 2;
      renderer.drawRect(0, 0, width, height, this.isFocused() ? ReadableColor.WHITE : BORDER_COLOR);
      renderer.drawRect(1, 1, width - 2, height - 2, ReadableColor.BLACK);
      if (this.text.isEmpty() && !this.isFocused() && !Strings.isNullOrEmpty(this.hint)) {
         String text = fontRenderer.trimStringToWidth(this.hint, width - 8);
         renderer.drawString(4, posY, this.textColorDisabled, text);
      } else {
         String renderText = this.text.substring(this.currentOffset);
         renderText = fontRenderer.trimStringToWidth(renderText, width - 8);
         ReadableColor color = this.isEnabled() ? this.textColorEnabled : this.textColorDisabled;
         int lineEnd = renderer.drawString(4, height / 2 - fontRenderer.FONT_HEIGHT / 2, color, renderText);
         int from = this.getSelectionFrom();
         int to = this.getSelectionTo();
         String leftStr = renderText.substring(0, Utils.clamp(from - this.currentOffset, 0, renderText.length()));
         String rightStr = renderText.substring(Utils.clamp(to - this.currentOffset, 0, renderText.length()));
         int left = 4 + fontRenderer.getStringWidth(leftStr);
         int right = lineEnd - fontRenderer.getStringWidth(rightStr) - 1;
         renderer.invertColors(right, height - 2, left, 2);
         if (this.blinkCursorTick / 6 % 2 == 0 && this.focused) {
            String beforeCursor = renderText.substring(0, this.cursorPos - this.currentOffset);
            int posX = 4 + fontRenderer.getStringWidth(beforeCursor);
            if (this.cursorPos == this.text.length()) {
               renderer.drawString(posX, posY, CURSOR_COLOR, "_", true);
            } else {
               renderer.drawRect(posX, posY - 1, 1, 1 + fontRenderer.FONT_HEIGHT, CURSOR_COLOR);
            }
         }
      }
   }

   @Override
   public boolean handleKey(KeyInput keyInput) {
      if (!this.focused) {
         return false;
      }

      int keyCode = keyInput.key;
      if (keyCode == 15) {
         Focusable other = keyInput.hasShift() ? this.previous : this.next;
         if (other != null) {
            this.setFocused(false);
            other.setFocused(true);
            if (other instanceof AbstractGuiTextField) {
               AbstractGuiTextField<?> field = (AbstractGuiTextField<?>)other;
               field.cursorPos = 0;
               field.selectionPos = field.text.length();
            }
         }

         return true;
      } else {
         if (keyCode == 28) {
            this.onEnter();
            return true;
         }

         String textBefore = this.text;

         try {
            if (keyInput.hasCtrl()) {
               switch (keyCode) {
                  case 30:
                     this.cursorPos = 0;
                     this.selectionPos = this.text.length();
                     return true;
                  case 45:
                     if (this.isEnabled()) {
                        MCVer.setClipboardString(this.deleteSelectedText());
                     }

                     return true;
                  case 46:
                     MCVer.setClipboardString(this.getSelectedText());
                     return true;
                  case 47:
                     if (this.isEnabled()) {
                        this.writeText(MCVer.getClipboardString());
                     }

                     return true;
               }
            }

            boolean words = keyInput.hasCtrl();
            boolean select = keyInput.hasShift();
            switch (keyCode) {
               case 14:
                  if (this.isEnabled()) {
                     if (this.getSelectedText().length() > 0) {
                        this.deleteSelectedText();
                     } else if (words) {
                        this.deletePreviousWord();
                     } else {
                        this.deletePreviousChar();
                     }
                  }

                  return true;
               case 199:
                  this.cursorPos = 0;
                  break;
               case 203:
                  if (this.cursorPos != 0) {
                     if (words) {
                        this.cursorPos = this.cursorPos - this.getPreviousWordLength();
                     } else {
                        this.cursorPos--;
                     }
                  }
                  break;
               case 205:
                  if (this.cursorPos != this.text.length()) {
                     if (words) {
                        this.cursorPos = this.cursorPos + this.getNextWordLength();
                     } else {
                        this.cursorPos++;
                     }
                  }
                  break;
               case 207:
                  this.cursorPos = this.text.length();
                  break;
               case 211:
                  if (this.isEnabled()) {
                     if (this.getSelectedText().length() > 0) {
                        this.deleteSelectedText();
                     } else if (words) {
                        this.deleteNextWord();
                     } else {
                        this.deleteNextChar();
                     }
                  }

                  return true;
               case 1:
                  // Escape is screen-level (close/back), never text input.
                  return false;
               default:
                  return true;
            }

            if (!select) {
               this.selectionPos = this.cursorPos;
            }

            return true;
         } finally {
            if (!textBefore.equals(this.text)) {
               this.onTextChanged(textBefore);
            }
         }
      }
   }

   @Override
   public boolean handleChar(CharInput charInput) {
      if (!this.focused) {
         return false;
      }

      if (this.isEnabled() && charInput.isValidChar()) {
         String textBefore = this.text;

         try {
            String inputText = charInput.asString();
            if (inputText.equals("\r")) {
               inputText = "\n";
            }

            this.writeText(inputText);
            return true;
         } finally {
            if (!textBefore.equals(this.text)) {
               this.onTextChanged(textBefore);
            }
         }
      } else {
         return true;
      }
   }

   @Override
   public void tick() {
      this.blinkCursorTick++;
   }

   protected void onEnter() {
      if (this.onEnter != null) {
         this.onEnter.run();
      }
   }

   protected void onTextChanged(String from) {
      if (this.textChanged != null) {
         this.textChanged.consume(from);
      }
   }

   protected void onFocusChanged(boolean focused) {
      if (this.focusChanged != null) {
         this.focusChanged.consume(focused);
      }
   }

   public T onEnter(Runnable onEnter) {
      this.onEnter = onEnter;
      return this.getThis();
   }

   public T onTextChanged(Consumer<String> textChanged) {
      this.textChanged = textChanged;
      return this.getThis();
   }

   public T onFocusChange(Consumer<Boolean> focusChanged) {
      this.focusChanged = focusChanged;
      return this.getThis();
   }

   public T setHint(String hint) {
      this.hint = hint;
      return this.getThis();
   }

   public T setI18nHint(String hint, Object... args) {
      return this.setHint(I18n.format(hint, new Object[0]));
   }

   @Override
   public ReadableColor getTextColor() {
      return this.textColorEnabled;
   }

   public T setTextColor(ReadableColor textColor) {
      this.textColorEnabled = textColor;
      return this.getThis();
   }

   @Override
   public ReadableColor getTextColorDisabled() {
      return this.textColorDisabled;
   }

   public T setTextColorDisabled(ReadableColor textColorDisabled) {
      this.textColorDisabled = textColorDisabled;
      return this.getThis();
   }

   @Override
   public boolean isFocused() {
      return this.focused;
   }

   @Override
   public Focusable getNext() {
      return this.next;
   }

   @Override
   public Focusable getPrevious() {
      return this.previous;
   }

   @Override
   public int getMaxLength() {
      return this.maxLength;
   }

   @Override
   public String getText() {
      return this.text;
   }

   @Override
   public String getHint() {
      return this.hint;
   }
}
