package redacted.client.util.misc;

import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayServer;
import net.minecraft.network.play.client.*;

public final class PacketList {
    private PacketList() {
    }

    @SuppressWarnings("unchecked")
    public static final Class<Packet<INetHandlerPlayServer>>[] serverbound = new Class[]{
            C00PacketKeepAlive.class,
            C01PacketChatMessage.class,
            C02PacketUseEntity.class,
            C03PacketPlayer.class,
            C03PacketPlayer.C04PacketPlayerPosition.class,
            C03PacketPlayer.C05PacketPlayerLook.class,
            C03PacketPlayer.C06PacketPlayerPosLook.class,
            C07PacketPlayerDigging.class,
            C08PacketPlayerBlockPlacement.class,
            C09PacketHeldItemChange.class,
            C0APacketAnimation.class,
            C0BPacketEntityAction.class,
            C0CPacketInput.class,
            C0DPacketCloseWindow.class,
            C0EPacketClickWindow.class,
            C0FPacketConfirmTransaction.class,
            C10PacketCreativeInventoryAction.class,
            C11PacketEnchantItem.class,
            C12PacketUpdateSign.class,
            C13PacketPlayerAbilities.class,
            C14PacketTabComplete.class,
            C15PacketClientSettings.class,
            C16PacketClientStatus.class,
            C17PacketCustomPayload.class,
            C18PacketSpectate.class,
            C19PacketResourcePackStatus.class,
    };
}
