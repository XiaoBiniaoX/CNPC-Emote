package bin.cnpcemotes.network;

import bin.cnpcemotes.state.NpcEmoteClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

public class BinCnpcEmotePacketHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PROTOCOL_VERSION = "1";
    private static int messageCounter = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryBuild("cnpcemotes", "main"),
            () -> PROTOCOL_VERSION,
            s -> true,
            s -> true
    );

    public static void register() {
        CHANNEL.registerMessage(messageCounter++, EmoteStartPacket.class,
            EmoteStartPacket::toBytes, EmoteStartPacket::fromBytes, EmoteStartPacket::handle);
        CHANNEL.registerMessage(messageCounter++, EmoteStopPacket.class,
            EmoteStopPacket::toBytes, EmoteStopPacket::fromBytes, EmoteStopPacket::handle);
    }

    public static void broadcastEmoteStart(ServerLevel level, UUID npcId, UUID emoteId, long startGameTime) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> level.dimension()),
            new EmoteStartPacket(npcId, emoteId, startGameTime));
    }

    public static class EmoteStartPacket {
        public final UUID npcId;
        public final UUID emoteId;
        public final long startGameTime;

        public EmoteStartPacket(UUID npcId, UUID emoteId, long startGameTime) {
            this.npcId = npcId;
            this.emoteId = emoteId;
            this.startGameTime = startGameTime;
        }

        public static void toBytes(EmoteStartPacket packet, FriendlyByteBuf buffer) {
            buffer.writeUUID(packet.npcId);
            buffer.writeUUID(packet.emoteId);
            buffer.writeLong(packet.startGameTime);
        }

        public static EmoteStartPacket fromBytes(FriendlyByteBuf buffer) {
            return new EmoteStartPacket(buffer.readUUID(), buffer.readUUID(), buffer.readLong());
        }

        public static void handle(EmoteStartPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                NpcEmoteClientState.getOrCreate(packet.npcId).play(packet.emoteId, packet.startGameTime);
            });
            context.setPacketHandled(true);
        }
    }

    public static class EmoteStopPacket {
        public final UUID npcId;

        public EmoteStopPacket(UUID npcId) {
            this.npcId = npcId;
        }

        public static void toBytes(EmoteStopPacket packet, FriendlyByteBuf buffer) {
            buffer.writeUUID(packet.npcId);
        }

        public static EmoteStopPacket fromBytes(FriendlyByteBuf buffer) {
            return new EmoteStopPacket(buffer.readUUID());
        }

        public static void handle(EmoteStopPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                NpcEmoteClientState.remove(packet.npcId);
            });
            context.setPacketHandled(true);
        }
    }
}
