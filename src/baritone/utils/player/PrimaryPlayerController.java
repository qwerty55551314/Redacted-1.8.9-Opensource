/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils.player;

import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerController;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.World;

/**
 * Implementation of {@link IPlayerController} that chains to the primary player controller's methods
 *
 * @author Brady
 * @since 12/14/2018
 */
public enum PrimaryPlayerController implements IPlayerController, Helper {

    INSTANCE;

    @Override
    public void syncHeldItem() {
        mc.playerController.syncCurrentPlayItem();
    }

    @Override
    public boolean hasBrokenBlock() {
        return mc.playerController.getCurrentBlock().getY() == -1;
    }

    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, EnumFacing side) {
        return mc.playerController.onPlayerDamageBlock(pos, side);
    }

    @Override
    public void resetBlockRemoving() {
        mc.playerController.resetBlockRemoving();
    }

    @Override
    public ItemStack windowClick(int windowId, int slotId, int mouseButton, int mode, EntityPlayer player) {
        return mc.playerController.windowClick(windowId, slotId, mouseButton, mode, player);
    }

    @Override
    public WorldSettings.GameType getGameType() {
        return mc.playerController.getCurrentGameType();
    }

    @Override
    public boolean processRightClickBlock(EntityPlayerSP player, World world, BlockPos pos, EnumFacing direction, Vec3 vec) {
        PlayerControllerMP c = mc.playerController;
        ItemStack held = player.getHeldItem();
        return c.onPlayerRightClick(player, (WorldClient) world, held, pos, direction, vec);
    }

    @Override
    public boolean processRightClick(EntityPlayerSP player, World world) {
        PlayerControllerMP c = mc.playerController;
        ItemStack held = player.getHeldItem();
        return c.sendUseItem(player, world, held);
    }

    @Override
    public boolean clickBlock(BlockPos loc, EnumFacing face) {
        return mc.playerController.clickBlock(loc, face);
    }

    @Override
    public void setHittingBlock(boolean hittingBlock) {
        mc.playerController.setHittingBlock(hittingBlock);
    }
}
