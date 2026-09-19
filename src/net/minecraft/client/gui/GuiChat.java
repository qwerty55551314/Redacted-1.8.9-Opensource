package net.minecraft.client.gui;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;

public class GuiChat extends GuiScreen
{
    private static final Logger logger = LogManager.getLogger();
    private String historyBuffer = "";

    /**
     * keeps position of which chat message you will select when you press up, (does not increase for duplicated
     * messages sent immediately after each other)
     */
    private int sentHistoryCursor = -1;
    private boolean playerNamesFound;
    private boolean waitingOnAutocomplete;
    private int autocompleteIndex;
    private List<String> foundPlayerNames = Lists.<String>newArrayList();

    /** Client command (".") completion state -- mirrors the player-name cycle above but never touches the server */
    private boolean clientCompletionActive;
    private final List<String> clientCandidates = Lists.<String>newArrayList();
    private int clientCandidateIndex;
    private int clientCompletionStart;
    private String clientCompletionInserted = "";

    /** Chat entry field */
    protected GuiTextField inputField;

    /**
     * is the text that appears when you press the chat key and the input box appears pre-filled
     */
    private String defaultInputFieldText = "";

    public GuiChat()
    {
    }

    public GuiChat(String defaultText)
    {
        this.defaultInputFieldText = defaultText;
    }

    /**
     * Adds the buttons (and other controls) to the screen in question. Called when the GUI is displayed and when the
     * window resizes, the buttonList is cleared beforehand.
     */
    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);
        this.sentHistoryCursor = this.mc.ingameGUI.getChatGUI().getSentMessages().size();
        this.inputField = new GuiTextField(0, this.fontRendererObj, 4, this.height - 12, this.width - 4, 12);
        this.inputField.setMaxStringLength(100);
        this.inputField.setEnableBackgroundDrawing(false);
        this.inputField.setFocused(true);
        this.inputField.setText(this.defaultInputFieldText);
        this.inputField.setCanLoseFocus(false);
    }

    /**
     * Called when the screen is unloaded. Used to disable keyboard repeat events
     */
    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
        this.mc.ingameGUI.getChatGUI().resetScroll();
    }

    /**
     * Called from the main game loop to update the screen.
     */
    public void updateScreen()
    {
        this.inputField.updateCursorCounter();
    }

    /**
     * Fired when a key is typed (except F11 which toggles full screen). This is the equivalent of
     * KeyListener.keyTyped(KeyEvent e). Args : character (character on the key), keyCode (lwjgl Keyboard key code)
     */
    protected void keyTyped(char typedChar, int keyCode) throws IOException
    {
        this.waitingOnAutocomplete = false;

        if (keyCode == 15)
        {
            if (isClientCommandInput())
            {
                this.autocompleteClientCommand();
            }
            else
            {
                this.autocompletePlayerNames();
            }
        }
        else
        {
            this.playerNamesFound = false;
            this.clientCompletionActive = false;
        }

        if (keyCode == 1)
        {
            this.mc.displayGuiScreen((GuiScreen)null);
        }
        else if (keyCode != 28 && keyCode != 156)
        {
            if (keyCode == 200)
            {
                this.getSentHistory(-1);
            }
            else if (keyCode == 208)
            {
                this.getSentHistory(1);
            }
            else if (keyCode == 201)
            {
                this.mc.ingameGUI.getChatGUI().scroll(this.mc.ingameGUI.getChatGUI().getLineCount() - 1);
            }
            else if (keyCode == 209)
            {
                this.mc.ingameGUI.getChatGUI().scroll(-this.mc.ingameGUI.getChatGUI().getLineCount() + 1);
            }
            else
            {
                this.inputField.textboxKeyTyped(typedChar, keyCode);
            }
        }
        else
        {
            String s = this.inputField.getText().trim();

            if (s.length() > 0)
            {
                this.sendChatMessage(s);
            }

            this.mc.displayGuiScreen((GuiScreen)null);
        }
    }

    /**
     * Handles mouse input.
     */
    public void handleMouseInput() throws IOException
    {
        super.handleMouseInput();
        int i = Mouse.getEventDWheel();

        if (i != 0)
        {
            if (i > 1)
            {
                i = 1;
            }

            if (i < -1)
            {
                i = -1;
            }

            if (!isShiftKeyDown())
            {
                i *= 7;
            }

            this.mc.ingameGUI.getChatGUI().scroll(i);
        }
    }

    /**
     * Called when the mouse is clicked. Args : mouseX, mouseY, clickedButton
     */
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException
    {
        if (mouseButton == 0)
        {
            IChatComponent ichatcomponent = this.mc.ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY());

            if (this.handleComponentClick(ichatcomponent))
            {
                return;
            }
        }

        this.inputField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * Sets the text of the chat
     */
    protected void setText(String newChatText, boolean shouldOverwrite)
    {
        if (shouldOverwrite)
        {
            this.inputField.setText(newChatText);
        }
        else
        {
            this.inputField.writeText(newChatText);
        }
    }

    public void autocompletePlayerNames()
    {
        if (this.playerNamesFound)
        {
            this.inputField.deleteFromCursor(this.inputField.func_146197_a(-1, this.inputField.getCursorPosition(), false) - this.inputField.getCursorPosition());

            if (this.autocompleteIndex >= this.foundPlayerNames.size())
            {
                this.autocompleteIndex = 0;
            }
        }
        else
        {
            int i = this.inputField.func_146197_a(-1, this.inputField.getCursorPosition(), false);
            this.foundPlayerNames.clear();
            this.autocompleteIndex = 0;
            String s = this.inputField.getText().substring(i).toLowerCase();
            String s1 = this.inputField.getText().substring(0, this.inputField.getCursorPosition());
            this.sendAutocompleteRequest(s1, s);

            if (this.foundPlayerNames.isEmpty())
            {
                return;
            }

            this.playerNamesFound = true;
            this.inputField.deleteFromCursor(i - this.inputField.getCursorPosition());
        }

        if (this.foundPlayerNames.size() > 1)
        {
            StringBuilder stringbuilder = new StringBuilder();

            for (String s2 : this.foundPlayerNames)
            {
                if (stringbuilder.length() > 0)
                {
                    stringbuilder.append(", ");
                }

                stringbuilder.append(s2);
            }

            this.mc.ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(new ChatComponentText(stringbuilder.toString()), 1);
        }

        this.inputField.writeText((String)this.foundPlayerNames.get(this.autocompleteIndex++));
    }

    /**
     * Client-side tab completion for "." commands -- mirrors vanilla's player-name flow
     * (complete common prefix, list the options, then cycle on consecutive tabs) without
     * ever sending a C14PacketTabComplete to the server. Quote-aware: candidates whose
     * name contains spaces are auto-wrapped in quotes so they survive the next parse.
     */
    public void autocompleteClientCommand()
    {
        String text = this.inputField.getText();
        int cursor = this.inputField.getCursorPosition();

        if (this.clientCompletionActive && !this.undoClientCompletion())
        {
            // the user edited/moved around since the last tab -- restart fresh
            this.clientCompletionActive = false;
        }

        if (!this.clientCompletionActive)
        {
            int tokenStart = this.lastUnquotedSpace(text, cursor) + 1;
            final String prefix = redacted.client.Client.getInstance().getCommandManager().getPrefix();
            // the leading prefix is fixed and never part of the replaceable token
            if (tokenStart == 0 && text.startsWith(prefix))
            {
                tokenStart = prefix.length();
            }
            final String rawToken = text.substring(tokenStart, cursor);
            // skip an opening quote so it survives the replacement (".config load "MineB ...")
            int leadingQuotes = 0;
            while (leadingQuotes < rawToken.length() && rawToken.charAt(leadingQuotes) == '"')
            {
                leadingQuotes++;
            }
            tokenStart += leadingQuotes;
            String token = this.stripQuotes(rawToken);

            List<String> matches = Lists.<String>newArrayList();

            for (String candidate : redacted.client.Client.getInstance().getCommandManager().getCompletions(text.substring(0, cursor)))
            {
                if (candidate.regionMatches(true, 0, token, 0, token.length()))
                {
                    matches.add(candidate);
                }
            }

            if (matches.isEmpty())
            {
                return;
            }

            this.clientCandidates.clear();
            this.clientCandidates.addAll(matches);
            this.clientCompletionStart = tokenStart;

            String commonPrefix = StringUtils.getCommonPrefix(matches.toArray(new String[matches.size()]));
            String insertion;
            if (commonPrefix.length() > token.length())
            {
                // extend to the shared prefix; keep completing fresh on the next tab
                insertion = this.escapeCompletion(commonPrefix);
                this.clientCompletionActive = false;
            }
            else
            {
                // start with the first candidate; consecutive tabs cycle through the rest
                insertion = this.escapeCompletion(matches.get(0));
                this.clientCandidateIndex = 1;
                this.clientCompletionActive = matches.size() > 1;
            }

            this.clientCompletionInserted = insertion;
            this.replaceClientCompletion(tokenStart, cursor, insertion);

            if (matches.size() == 1 && tokenStart == prefix.length())
            {
                // a unique command name reads like vanilla "/cmd" -- leave a space ready for args
                this.clientCompletionInserted = insertion + " ";
                this.inputField.writeText(" ");
            }

            if (matches.size() > 1)
            {
                this.printCompletionOptions(matches);
            }
        }
        else
        {
            if (this.clientCandidateIndex >= this.clientCandidates.size())
            {
                this.clientCandidateIndex = 0;
                this.printCompletionOptions(this.clientCandidates);
            }

            this.clientCompletionInserted = this.escapeCompletion(this.clientCandidates.get(this.clientCandidateIndex++));
            // after the undo above the cursor is back at clientCompletionStart, so this is a pure insert
            this.replaceClientCompletion(this.clientCompletionStart, this.clientCompletionStart, this.clientCompletionInserted);
        }
    }

    /** Removes the previously inserted completion (if still intact under the cursor). */
    private boolean undoClientCompletion()
    {
        int cursor = this.inputField.getCursorPosition();
        boolean intact = !this.clientCompletionInserted.isEmpty()
                && cursor == this.clientCompletionStart + this.clientCompletionInserted.length()
                && this.inputField.getText().startsWith(this.clientCompletionInserted, this.clientCompletionStart);

        if (intact)
        {
            // negative deletes the chars before the cursor, i.e. the completion we inserted
            this.inputField.deleteFromCursor(-this.clientCompletionInserted.length());
        }

        return intact;
    }

    /** Replaces the typed token (start..end) with the completion and moves the cursor after it. */
    private void replaceClientCompletion(int start, int end, String replacement)
    {
        final String text = this.inputField.getText();
        this.inputField.setText(text.substring(0, start) + replacement + text.substring(end));
        this.inputField.setCursorPosition(start + replacement.length());
    }

    /** Index of the last space in [0, cursor) that sits outside a double-quoted run. */
    private int lastUnquotedSpace(String text, int cursor)
    {
        boolean inQuotes = false;
        int last = -1;
        for (int i = 0; i < cursor && i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '"')
            {
                inQuotes = !inQuotes;
            }
            else if (c == ' ' && !inQuotes)
            {
                last = i;
            }
        }
        return last;
    }

    /** Removes surrounding quotes from a partial token, e.g. "\"MineB" -> "MineB". */
    private String stripQuotes(String s)
    {
        String out = s;
        while (out.startsWith("\""))
        {
            out = out.substring(1);
        }
        while (out.endsWith("\""))
        {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private boolean insideQuotes(String text, int before)
    {
        int count = 0;
        for (int i = 0; i < before && i < text.length(); i++)
        {
            if (text.charAt(i) == '"')
            {
                count++;
            }
        }
        return (count & 1) == 1;
    }

    /** Wraps a candidate in quotes when it contains spaces and isn't already inside a quoted run. */
    private String escapeCompletion(String candidate)
    {
        if (candidate.indexOf(' ') < 0)
        {
            return candidate;
        }
        // probe at the cursor: typing "MineB means we're already inside a quoted run
        return this.insideQuotes(this.inputField.getText(), this.inputField.getCursorPosition())
                ? candidate
                : "\"" + candidate + "\"";
    }

    private void printCompletionOptions(List<String> options)
    {
        this.mc.ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(
                new ChatComponentText("§7Options§8: §e" + String.join("§7, §e", options)), 1);
    }

    private boolean isClientCommandInput()
    {
        String text = this.inputField.getText();
        return text != null && text.startsWith(redacted.client.Client.getInstance().getCommandManager().getPrefix());
    }

    private void sendAutocompleteRequest(String p_146405_1_, String p_146405_2_)
    {
        if (p_146405_1_.length() >= 1)
        {
            BlockPos blockpos = null;

            if (this.mc.objectMouseOver != null && this.mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
            {
                blockpos = this.mc.objectMouseOver.getBlockPos();
            }

            this.mc.thePlayer.sendQueue.addToSendQueue(new C14PacketTabComplete(p_146405_1_, blockpos));
            this.waitingOnAutocomplete = true;
        }
    }

    /**
     * input is relative and is applied directly to the sentHistoryCursor so -1 is the previous message, 1 is the next
     * message from the current cursor position
     */
    public void getSentHistory(int msgPos)
    {
        int i = this.sentHistoryCursor + msgPos;
        int j = this.mc.ingameGUI.getChatGUI().getSentMessages().size();
        i = MathHelper.clamp_int(i, 0, j);

        if (i != this.sentHistoryCursor)
        {
            if (i == j)
            {
                this.sentHistoryCursor = j;
                this.inputField.setText(this.historyBuffer);
            }
            else
            {
                if (this.sentHistoryCursor == j)
                {
                    this.historyBuffer = this.inputField.getText();
                }

                this.inputField.setText((String)this.mc.ingameGUI.getChatGUI().getSentMessages().get(i));
                this.sentHistoryCursor = i;
            }
        }
    }

    /**
     * Draws the screen and all the components in it. Args : mouseX, mouseY, renderPartialTicks
     */
    public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
        drawRect(2, this.height - 14, this.width - 2, this.height - 2, Integer.MIN_VALUE);
        this.inputField.drawTextBox();
        IChatComponent ichatcomponent = this.mc.ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY());

        if (ichatcomponent != null && ichatcomponent.getChatStyle().getChatHoverEvent() != null)
        {
            this.handleComponentHover(ichatcomponent, mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    public void onAutocompleteResponse(String[] p_146406_1_)
    {
        if (this.waitingOnAutocomplete)
        {
            this.playerNamesFound = false;
            this.foundPlayerNames.clear();

            for (String s : p_146406_1_)
            {
                if (s.length() > 0)
                {
                    this.foundPlayerNames.add(s);
                }
            }

            String s1 = this.inputField.getText().substring(this.inputField.func_146197_a(-1, this.inputField.getCursorPosition(), false));
            String s2 = StringUtils.getCommonPrefix(p_146406_1_);

            if (s2.length() > 0 && !s1.equalsIgnoreCase(s2))
            {
                this.inputField.deleteFromCursor(this.inputField.func_146197_a(-1, this.inputField.getCursorPosition(), false) - this.inputField.getCursorPosition());
                this.inputField.writeText(s2);
            }
            else if (this.foundPlayerNames.size() > 0)
            {
                this.playerNamesFound = true;
                this.autocompletePlayerNames();
            }
        }
    }

    /**
     * Returns true if this GUI should pause the game when it is displayed in single-player
     */
    public boolean doesGuiPauseGame()
    {
        return false;
    }
}
