package redacted.client.util.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjglx.input.Keyboard;
import redacted.client.util.render.RenderUtil;

// single-line text input with rounded-rect skin, masking, cursor blink, select-all
// used by GuiAltManager and GuiTeamManager (was copy-pasted before lmao)
public class InputField {
    private static final int INPUT_BG = 0xFF111111;
    private static final int BORDER_FOCUS = 0xFF555555;
    private static final int BORDER_DEF = 0xFF333333;
    private static final int SELECT_HL = 0x33FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_FAINT = 0xFF555555;

    private boolean masked;
    private final String placeholder;
    private double leftPad = 8;

    private double x, y, width, height;
    private String text = "";
    private int maxStringLength = 256;
    private int cursorCounter;
    private boolean focused;
    private boolean selectAll;

    public void setLeftPad(final double leftPad) {
        this.leftPad = leftPad;
    }

    public InputField(final boolean masked, final String placeholder) {
        this.masked = masked;
        this.placeholder = placeholder;
    }

    public InputField(final boolean masked) {
        this(masked, "");
    }

    public InputField() {
        this(false, "");
    }

    public void setBounds(final double x, final double y, final double width, final double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void updateCursorCounter() {
        this.cursorCounter++;
    }

    public void setMaxStringLength(final int maxStringLength) {
        this.maxStringLength = maxStringLength;
        if (this.text.length() > maxStringLength) {
            this.text = this.text.substring(0, maxStringLength);
        }
    }

    public void setText(final String value) {
        final String v = value == null ? "" : value;
        this.text = v.length() > this.maxStringLength ? v.substring(0, this.maxStringLength) : v;
        this.selectAll = false;
    }

    public String getText() {
        return this.text;
    }

    public void setFocused(final boolean focused) {
        if (focused && !this.focused) {
            this.cursorCounter = 0;
        }
        this.focused = focused;
    }

    public boolean isFocused() {
        return this.focused;
    }

    public boolean isMasked() {
        return this.masked;
    }

    public void setMasked(final boolean masked) {
        this.masked = masked;
    }

    public boolean textboxKeyTyped(final char typedChar, final int keyCode) {
        if (!this.focused) return false;

        if (GuiScreen.isKeyComboCtrlA(keyCode)) {
            this.selectAll = true;
            return true;
        }
        if (GuiScreen.isKeyComboCtrlC(keyCode)) return true;
        if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            writeText(GuiScreen.getClipboardString());
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (this.selectAll) {
                this.text = "";
                this.selectAll = false;
            } else if (!this.text.isEmpty()) {
                this.text = this.text.substring(0, this.text.length() - 1);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            this.text = "";
            this.selectAll = false;
            return true;
        }
        if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
            writeText(Character.toString(typedChar));
            return true;
        }
        return false;
    }

    public void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        final boolean hovering = mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;
        if (mouseButton == 0) {
            setFocused(hovering);
            this.selectAll = false;
        }
    }

    public void draw() {
        RenderUtil.drawRoundedRect(this.x, this.y, this.width, this.height, 4, INPUT_BG);
        RenderUtil.drawRoundedOutline(this.x, this.y, this.width, this.height, 4, 1.0f, this.focused ? BORDER_FOCUS : BORDER_DEF);

        final float fontH = GuiText.height(15);
        final double textY = this.y + (this.height - fontH) / 2.0;

        if (this.text.isEmpty() && !this.focused) {
            if (this.placeholder != null && !this.placeholder.isEmpty()) {
                GuiText.draw(15, this.placeholder, this.x + this.leftPad, textY, TEXT_FAINT);
            }
            return;
        }

        final String display = this.masked ? mask(this.text.length()) : this.text;
        String shown = display;
        while (shown.length() > 1 && GuiText.width(15, shown) > this.width - this.leftPad - 8) {
            shown = shown.substring(1);
        }

        if (this.focused && this.selectAll) {
            RenderUtil.drawRoundedRect(this.x + this.leftPad - 3, textY - 1, Math.min(this.width - this.leftPad - 5, GuiText.width(15, shown) + 2), fontH + 2, 0, SELECT_HL);
        }

        GuiText.draw(15, shown, this.x + this.leftPad, textY, TEXT);

        if (this.focused && this.cursorCounter / 6 % 2 == 0) {
            final double caretX = this.x + this.leftPad + GuiText.width(15, shown);
            RenderUtil.drawRect(caretX, textY, 1, fontH, TEXT);
        }
    }

    private void writeText(final String value) {
        if (value == null || value.isEmpty()) return;

        final String filtered = ChatAllowedCharacters.filterAllowedCharacters(value);
        if (this.selectAll) {
            this.text = "";
            this.selectAll = false;
        }
        final int remaining = this.maxStringLength - this.text.length();
        if (remaining <= 0) return;

        this.text += filtered.length() > remaining ? filtered.substring(0, remaining) : filtered;
    }

    private String mask(final int length) {
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append('*');
        return sb.toString();
    }
}
