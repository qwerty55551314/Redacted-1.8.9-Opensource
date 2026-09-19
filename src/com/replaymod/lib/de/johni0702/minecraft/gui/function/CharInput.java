package com.replaymod.lib.de.johni0702.minecraft.gui.function;

import net.minecraft.util.ChatAllowedCharacters;

public class CharInput {
   public final char character;
   public final int modifiers;

   public CharInput(char character, int modifiers) {
      this.character = character;
      this.modifiers = modifiers;
   }

   public CharInput(char character) {
      this(character, InputWithModifiers.currentModifiers());
   }

   public boolean isValidChar() {
      return isValidChar(this.character);
   }

   public String asString() {
      return Character.toString(this.character);
   }

   public static boolean isValidChar(char c) {
      return ChatAllowedCharacters.isAllowedCharacter(c);
   }
}
