package net.minecraft.util;

import net.minecraft.client.settings.GameSettings;
import redacted.client.Client;
import redacted.client.event.impl.player.EventMoveInput;

public class MovementInputFromOptions extends MovementInput
{
    private final GameSettings gameSettings;

    public MovementInputFromOptions(GameSettings gameSettingsIn)
    {
        this.gameSettings = gameSettingsIn;
    }

    public void updatePlayerMoveState()
    {
        this.moveStrafe = 0.0F;
        this.moveForward = 0.0F;

        if (this.gameSettings.keyBindForward.isKeyDown())
        {
            ++this.moveForward;
        }

        if (this.gameSettings.keyBindBack.isKeyDown())
        {
            --this.moveForward;
        }

        if (this.gameSettings.keyBindLeft.isKeyDown())
        {
            ++this.moveStrafe;
        }

        if (this.gameSettings.keyBindRight.isKeyDown())
        {
            --this.moveStrafe;
        }

        this.jump = this.gameSettings.keyBindJump.isKeyDown();
        this.sneak = this.gameSettings.keyBindSneak.isKeyDown();

        final double sneakMultiplier = 0.3D;

        if (this.sneak)
        {
            this.moveStrafe = (float)((double)this.moveStrafe * sneakMultiplier);
            this.moveForward = (float)((double)this.moveForward * sneakMultiplier);
        }

        final EventMoveInput event = Client.getInstance().getEventManager().call(
                new EventMoveInput(this.moveForward, this.moveStrafe, this.jump, this.sneak, sneakMultiplier));
        this.moveForward = event.getMoveForward();
        this.moveStrafe = event.getMoveStrafe();
        this.jump = event.isJump();
        this.sneak = event.isSneak();
    }
}
