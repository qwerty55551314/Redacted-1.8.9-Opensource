package com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiElement;
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
import java.util.Arrays;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

public abstract class AbstractGuiTextArea<T extends AbstractGuiTextArea<T>>
   extends AbstractGuiElement<T>
   implements Clickable,
   KeyHandler,
   CharHandler,
   Tickable,
   IGuiTextArea<T> {
   private static final ReadableColor BACKGROUND_COLOR = new Color(160, 160, 160);
   private static final ReadableColor CURSOR_COLOR = new Color(240, 240, 240);
   private static final int BORDER = 4;
   private static final int LINE_SPACING = 2;
   private boolean focused;
   private Focusable next;
   private Focusable previous;
   private Consumer<Boolean> focusChanged;
   private int maxTextWidth = -1;
   private int maxTextHeight = -1;
   private int maxCharCount = -1;
   private String[] text = new String[]{""};
   private String[] hint;
   private int cursorX;
   private int cursorY;
   private int selectionX;
   private int selectionY;
   private int currentXOffset;
   private int currentYOffset;
   private int blinkCursorTick;
   public ReadableColor textColorEnabled = new Color(224, 224, 224);
   public ReadableColor textColorDisabled = new Color(112, 112, 112);
   private ReadableDimension size = new Dimension(0, 0);

   public AbstractGuiTextArea() {
   }

   public AbstractGuiTextArea(GuiContainer container) {
      super(container);
   }

   public T setText(String[] lines) {
      if (lines.length > this.maxTextHeight) {
         lines = Arrays.copyOf(lines, this.maxTextHeight);
      }

      this.text = lines;

      for (int i = 0; i < lines.length; i++) {
         if (lines[i].length() > this.maxTextWidth) {
            lines[i] = lines[i].substring(0, this.maxTextWidth);
         }
      }

      return this.getThis();
   }

   @Override
   public String[] getText() {
      return this.text;
   }

   @Override
   public String getText(int fromX, int fromY, int toX, int toY) {
      StringBuilder sb = new StringBuilder();
      if (fromY == toY) {
         sb.append(this.text[fromY].substring(fromX, toX));
      } else {
         sb.append(this.text[fromY].substring(fromX)).append('\n');

         for (int y = fromY + 1; y < toY; y++) {
            sb.append(this.text[y]).append('\n');
         }

         sb.append(this.text[toY].substring(0, toX));
      }

      return sb.toString();
   }

   private void deleteText(int fromX, int fromY, int toX, int toY) {
      String[] newText = new String[this.text.length - (toY - fromY)];
      if (fromY > 0) {
         System.arraycopy(this.text, 0, newText, 0, fromY);
      }

      newText[fromY] = this.text[fromY].substring(0, fromX) + this.text[toY].substring(toX);
      if (toY + 1 < this.text.length) {
         System.arraycopy(this.text, toY + 1, newText, fromY + 1, this.text.length - toY - 1);
      }

      this.text = newText;
   }

   @Override
   public int getSelectionFromX() {
      if (this.cursorY == this.selectionY) {
         return this.cursorX > this.selectionX ? this.selectionX : this.cursorX;
      } else {
         return this.cursorY > this.selectionY ? this.selectionX : this.cursorX;
      }
   }

   @Override
   public int getSelectionToX() {
      if (this.cursorY == this.selectionY) {
         return this.cursorX > this.selectionX ? this.cursorX : this.selectionX;
      } else {
         return this.cursorY > this.selectionY ? this.cursorX : this.selectionX;
      }
   }

   @Override
   public int getSelectionFromY() {
      return this.cursorY > this.selectionY ? this.selectionY : this.cursorY;
   }

   @Override
   public int getSelectionToY() {
      return this.cursorY > this.selectionY ? this.cursorY : this.selectionY;
   }

   @Override
   public String getSelectedText() {
      if (this.cursorX == this.selectionX && this.cursorY == this.selectionY) {
         return "";
      }

      int fromX = this.getSelectionFromX();
      int fromY = this.getSelectionFromY();
      int toX = this.getSelectionToX();
      int toY = this.getSelectionToY();
      return this.getText(fromX, fromY, toX, toY);
   }

   @Override
   public void deleteSelectedText() {
      if (this.cursorX != this.selectionX || this.cursorY != this.selectionY) {
         int fromX = this.getSelectionFromX();
         int fromY = this.getSelectionFromY();
         int toX = this.getSelectionToX();
         int toY = this.getSelectionToY();
         this.deleteText(fromX, fromY, toX, toY);
         this.cursorX = this.selectionX = fromX;
         this.cursorY = this.selectionY = fromY;
      }
   }

   private void updateCurrentOffset() {
      this.currentXOffset = Math.min(this.currentXOffset, this.cursorX);
      String line = this.text[this.cursorY].substring(this.currentXOffset, this.cursorX);
      FontRenderer fontRenderer = MCVer.getFontRenderer();
      int currentWidth = fontRenderer.getStringWidth(line);
      if (currentWidth > this.size.getWidth() - 8) {
         this.currentXOffset = this.cursorX - fontRenderer.trimStringToWidth(line, this.size.getWidth() - 8, true).length();
      }

      this.currentYOffset = Math.min(this.currentYOffset, this.cursorY);
      int lineHeight = MCVer.getFontRenderer().FONT_HEIGHT + 2;
      int contentHeight = this.size.getHeight() - 8;
      int maxLines = contentHeight / lineHeight;
      if (this.cursorY - this.currentYOffset >= maxLines) {
         this.currentYOffset = this.cursorY - maxLines + 1;
      }
   }

   @Override
   public String cutSelectedText() {
      String selection = this.getSelectedText();
      this.deleteSelectedText();
      return selection;
   }

   @Override
   public void writeText(String append) {
      for (char c : append.toCharArray()) {
         this.writeChar(c);
      }
   }

   @Override
   public void writeChar(char c) {
      if (CharInput.isValidChar(c)) {
         int totalCharCount = 0;

         for (String line : this.text) {
            totalCharCount += line.length();
         }

         if (this.maxCharCount <= 0 || totalCharCount - this.getSelectedText().length() < this.maxCharCount) {
            this.deleteSelectedText();
            if (c == '\n') {
               if (this.text.length >= this.maxTextHeight) {
                  return;
               }

               String[] newText = new String[this.text.length + 1];
               if (this.cursorY > 0) {
                  System.arraycopy(this.text, 0, newText, 0, this.cursorY);
               }

               newText[this.cursorY] = this.text[this.cursorY].substring(0, this.cursorX);
               newText[this.cursorY + 1] = this.text[this.cursorY].substring(this.cursorX);
               if (this.cursorY + 1 < this.text.length) {
                  System.arraycopy(this.text, this.cursorY + 1, newText, this.cursorY + 2, this.text.length - this.cursorY - 1);
               }

               this.text = newText;
               this.selectionX = this.cursorX = 0;
               this.selectionY = ++this.cursorY;
            } else {
               String line = this.text[this.cursorY];
               if (line.length() >= this.maxTextWidth) {
                  return;
               }

               line = line.substring(0, this.cursorX) + c + line.substring(this.cursorX);
               this.text[this.cursorY] = line;
               this.selectionX = ++this.cursorX;
            }
         }
      }
   }

   private void deleteNextChar() {
      String line = this.text[this.cursorY];
      if (this.cursorX < line.length()) {
         line = line.substring(0, this.cursorX) + line.substring(this.cursorX + 1);
         this.text[this.cursorY] = line;
      } else if (this.cursorY + 1 < this.text.length) {
         this.deleteText(this.cursorX, this.cursorY, 0, this.cursorY + 1);
      }
   }

   private int getNextWordLength() {
      int length = 0;
      String line = this.text[this.cursorY];
      boolean inWord = true;

      for (int i = this.cursorX; i < line.length(); i++) {
         if (inWord) {
            if (line.charAt(i) == ' ') {
               inWord = false;
            }
         } else if (line.charAt(i) != ' ') {
            return length;
         }

         length++;
      }

      return length;
   }

   private void deleteNextWord() {
      int worldLength = this.getNextWordLength();
      if (worldLength == 0) {
         this.deleteNextChar();
      } else {
         this.deleteText(this.cursorX, this.cursorY, this.cursorX + worldLength, this.cursorY);
      }
   }

   private void deletePreviousChar() {
      if (this.cursorX > 0) {
         String line = this.text[this.cursorY];
         line = line.substring(0, this.cursorX - 1) + line.substring(this.cursorX);
         this.selectionX = --this.cursorX;
         this.text[this.cursorY] = line;
      } else if (this.cursorY > 0) {
         int fromX = this.text[this.cursorY - 1].length();
         this.deleteText(fromX, this.cursorY - 1, this.cursorX, this.cursorY);
         this.selectionX = this.cursorX = fromX;
         this.selectionY = --this.cursorY;
      }
   }

   private int getPreviousWordLength() {
      int length = 0;
      String line = this.text[this.cursorY];
      boolean inWord = false;

      for (int i = this.cursorX - 1; i >= 0; i--) {
         if (inWord) {
            if (line.charAt(i) == ' ') {
               return length;
            }
         } else if (line.charAt(i) != ' ') {
            inWord = true;
         }

         length++;
      }

      return length;
   }

   private void deletePreviousWord() {
      int worldLength = this.getPreviousWordLength();
      if (worldLength == 0) {
         this.deletePreviousChar();
      } else {
         this.deleteText(this.cursorX, this.cursorY, this.cursorX - worldLength, this.cursorY);
         this.selectionX = this.cursorX -= worldLength;
      }
   }

   public T setCursorPosition(int x, int y) {
      this.selectionY = this.cursorY = Utils.clamp(y, 0, this.text.length - 1);
      this.selectionX = this.cursorX = Utils.clamp(x, 0, this.text[this.cursorY].length());
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
         int mouseY = position.getY() - 4;
         FontRenderer fontRenderer = MCVer.getFontRenderer();
         int textY = Utils.clamp(mouseY / (fontRenderer.FONT_HEIGHT + 2) + this.currentYOffset, 0, this.text.length - 1);
         if (this.cursorY != textY) {
            this.currentXOffset = 0;
         }

         String line = this.text[textY].substring(this.currentXOffset);
         int textX = fontRenderer.trimStringToWidth(line, mouseX).length() + this.currentXOffset;
         this.setCursorPosition(textX, textY);
      }

      this.setFocused(hovering);
      return hovering;
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
      FontRenderer fontRenderer = MCVer.getFontRenderer();
      int width = size.getWidth();
      int height = size.getHeight();
      renderer.drawRect(0, 0, width, height, BACKGROUND_COLOR);
      renderer.drawRect(1, 1, width - 2, height - 2, ReadableColor.BLACK);
      ReadableColor textColor = this.isEnabled() ? this.textColorEnabled : this.textColorDisabled;
      int lineHeight = fontRenderer.FONT_HEIGHT + 2;
      int contentHeight = height - 8;
      int maxLines = contentHeight / lineHeight;
      int contentWidth = width - 8;
      if (this.hint != null && !this.isFocused() && Arrays.stream(this.text).allMatch(String::isEmpty)) {
         for (int i = 0; i < maxLines && i < this.hint.length; i++) {
            String line = fontRenderer.trimStringToWidth(this.hint[i], contentWidth);
            int posY = 4 + i * lineHeight;
            renderer.drawString(4, posY, this.textColorDisabled, line, true);
         }
      } else {
         for (int i = 0; i < maxLines && i + this.currentYOffset < this.text.length; i++) {
            int lineY = i + this.currentYOffset;
            String line = this.text[lineY];
            int leftTrimmed = 0;
            if (lineY == this.cursorY) {
               line = line.substring(this.currentXOffset);
               leftTrimmed = this.currentXOffset;
            }

            line = fontRenderer.trimStringToWidth(line, contentWidth);
            int posY = 4 + i * lineHeight;
            int lineEnd = renderer.drawString(4, posY, textColor, line, true);
            int fromX = this.getSelectionFromX();
            int fromY = this.getSelectionFromY();
            int toX = this.getSelectionToX();
            int toY = this.getSelectionToY();
            if (lineY > fromY && lineY < toY) {
               renderer.invertColors(lineEnd, posY - 1 + lineHeight, 4, posY - 1);
            } else if (lineY == fromY && lineY == toY) {
               String leftStr = line.substring(0, Utils.clamp(fromX - leftTrimmed, 0, line.length()));
               String rightStr = line.substring(Utils.clamp(toX - leftTrimmed, 0, line.length()));
               int left = 4 + fontRenderer.getStringWidth(leftStr);
               int right = lineEnd - fontRenderer.getStringWidth(rightStr) - 1;
               renderer.invertColors(right, posY - 1 + lineHeight, left, posY - 1);
            } else if (lineY == fromY) {
               String rightStr = line.substring(Utils.clamp(fromX - leftTrimmed, 0, line.length()));
               renderer.invertColors(lineEnd, posY - 1 + lineHeight, lineEnd - fontRenderer.getStringWidth(rightStr), posY - 1);
            } else if (lineY == toY) {
               String leftStr = line.substring(0, Utils.clamp(toX - leftTrimmed, 0, line.length()));
               int right = 4 + fontRenderer.getStringWidth(leftStr);
               renderer.invertColors(right, posY - 1 + lineHeight, 4, posY - 1);
            }

            if (lineY == this.cursorY && this.blinkCursorTick / 6 % 2 == 0 && this.focused) {
               String beforeCursor = line.substring(0, this.cursorX - leftTrimmed);
               int posX = 4 + fontRenderer.getStringWidth(beforeCursor);
               if (this.cursorX == this.text[lineY].length()) {
                  renderer.drawString(posX, posY, CURSOR_COLOR, "_", true);
               } else {
                  renderer.drawRect(posX, posY - 1, 1, 1 + fontRenderer.FONT_HEIGHT, CURSOR_COLOR);
               }
            }
         }
      }
   }

   @Override
   public boolean handleKey(KeyInput keyInput) {
      int keyCode = keyInput.key;
      if (keyCode == 15) {
         Focusable other = keyInput.hasShift() ? this.previous : this.next;
         if (other != null) {
            this.setFocused(false);
            other.setFocused(true);
         }

         return true;
      } else {
         if (!this.focused) {
            return false;
         }

         if (keyInput.hasCtrl()) {
            switch (keyCode) {
               case 30:
                  this.cursorX = this.cursorY = 0;
                  this.selectionY = this.text.length - 1;
                  this.selectionX = this.text[this.selectionY].length();
                  return true;
               case 45:
                  if (this.isEnabled()) {
                     MCVer.setClipboardString(this.cutSelectedText());
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
               this.cursorX = 0;
               break;
            case 200:
               if (this.cursorY > 0) {
                  this.cursorY--;
                  this.cursorX = Math.min(this.cursorX, this.text[this.cursorY].length());
               }
               break;
            case 203:
               if (this.cursorX == 0) {
                  if (this.cursorY > 0) {
                     this.cursorY--;
                     this.cursorX = this.text[this.cursorY].length();
                  }
               } else if (words) {
                  this.cursorX = this.cursorX - this.getPreviousWordLength();
               } else {
                  this.cursorX--;
               }
               break;
            case 205:
               if (this.cursorX == this.text[this.cursorY].length()) {
                  if (this.cursorY < this.text.length - 1) {
                     this.cursorY++;
                     this.cursorX = 0;
                  }
               } else if (words) {
                  this.cursorX = this.cursorX + this.getNextWordLength();
               } else {
                  this.cursorX++;
               }
               break;
            case 207:
               this.cursorX = this.text[this.cursorY].length();
               break;
            case 208:
               if (this.cursorY + 1 < this.text.length) {
                  this.cursorY++;
                  this.cursorX = Math.min(this.cursorX, this.text[this.cursorY].length());
               }
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
            default:
               return true;
         }

         if (!select) {
            this.selectionX = this.cursorX;
            this.selectionY = this.cursorY;
         }

         return true;
      }
   }

   @Override
   public boolean handleChar(CharInput charInput) {
      if (!this.focused) {
         return false;
      }

      if (!this.isEnabled()) {
         return true;
      }

      String inputText = charInput.asString();
      if (inputText.equals("\r")) {
         inputText = "\n";
      }

      this.writeText(inputText);
      return true;
   }

   @Override
   public void tick() {
      this.blinkCursorTick++;
   }

   public T setMaxTextWidth(int maxTextWidth) {
      this.maxTextWidth = maxTextWidth;
      return this.getThis();
   }

   public T setMaxTextHeight(int maxTextHeight) {
      this.maxTextHeight = maxTextHeight;
      return this.getThis();
   }

   public T setMaxCharCount(int maxCharCount) {
      this.maxCharCount = maxCharCount;
      return this.getThis();
   }

   public T setTextColor(ReadableColor textColor) {
      this.textColorEnabled = textColor;
      return this.getThis();
   }

   public T setTextColorDisabled(ReadableColor textColorDisabled) {
      this.textColorDisabled = textColorDisabled;
      return this.getThis();
   }

   public T onFocusChange(Consumer<Boolean> focusChanged) {
      this.focusChanged = focusChanged;
      return this.getThis();
   }

   protected void onFocusChanged(boolean focused) {
      if (this.focusChanged != null) {
         this.focusChanged.consume(focused);
      }
   }

   @Override
   public String[] getHint() {
      return this.hint;
   }

   public T setHint(String... hint) {
      this.hint = hint;
      return this.getThis();
   }

   public T setI18nHint(String hint, Object... args) {
      this.setHint(I18n.format(hint, args).split("/n"));
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
   public int getMaxTextWidth() {
      return this.maxTextWidth;
   }

   @Override
   public int getMaxTextHeight() {
      return this.maxTextHeight;
   }

   @Override
   public int getMaxCharCount() {
      return this.maxCharCount;
   }
}
