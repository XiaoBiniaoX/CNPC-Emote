package bin.cnpcemotes;

import bin.cnpcemotes.command.BinCnpcEmoteCommand;
import bin.cnpcemotes.state.NpcEmoteClientState;
import bin.cnpcemotes.state.NpcEmoteState;
import dev.kosmx.playerAnim.core.data.AnimationBinary;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import io.github.kosmx.emotes.api.events.server.ServerEmoteAPI;
import io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import noppes.npcs.entity.EntityNPCInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Mod("cnpcemotes")
public class CnpcEmoteMod {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PROTOCOL_VERSION = "2";
    private static final int MAX_ANIMATION_BYTES = 0x100000;
    private static final AtomicInteger MESSAGE_ID = new AtomicInteger(0);

    /** 存在 NPC 的 Forge ForgeData 里，随实体 NBT 自动存档/读档。 */
    private static final String NBT_EMOTE_ID = "CnpcEmoteId";

    /** 存档 emote 文件名（去扩展名，小写）→ 动画 UUID。脚本 npc.playEmote() 按文件名查找。 */
    public static final Map<String, UUID> EMOTE_BY_NAME = new ConcurrentHashMap<>();
    private static final Map<UUID, KeyframeAnimation> WORLD_EMOTES = new ConcurrentHashMap<>();

    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("cnpcemotes", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public CnpcEmoteMod(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(this::commonSetup);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("CNPC-Emote initializing");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NETWORK.registerMessage(MESSAGE_ID.getAndIncrement(), EmoteStartPacket.class,
                (msg, buf) -> EmoteStartPacket.toBytes(msg, buf),
                EmoteStartPacket::fromBytes,
                (msg, ctxSupplier) -> EmoteStartPacket.handle(msg, ctxSupplier), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            NETWORK.registerMessage(MESSAGE_ID.getAndIncrement(), EmoteStopPacket.class,
                (msg, buf) -> EmoteStopPacket.toBytes(msg, buf),
                EmoteStopPacket::fromBytes,
                (msg, ctxSupplier) -> EmoteStopPacket.handle(msg, ctxSupplier), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            LOGGER.info("CNPC-Emote network registered");
        });
        LOGGER.info("CNPC-Emote common setup complete");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(BinCnpcEmoteCommand.register());
        LOGGER.info("Registered /bincnpcemote command");
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        // 必须在世界/实体加载前清理，不能在 ServerStarted 抹掉刚从 NBT 恢复的状态。
        NpcEmoteState.clearAll();
        clearWorldEmotes();
        EmoteWorldInitializer.initEmoteDirectory(event.getServer());
        loadCustomNpcEmotes(event.getServer());
        LOGGER.info("CNPC-Emote world emotes loaded");
    }

    private void loadCustomNpcEmotes(net.minecraft.server.MinecraftServer server) {
        try {
            Path npcEmoteDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("customnpcs/emote");
            if (!Files.isDirectory(npcEmoteDir)) return;
            try (var files = Files.list(npcEmoteDir)) {
                files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".emotecraft") || name.endsWith(".emote") || name.endsWith(".json");
                    })
                    .forEach(file -> {
                        try {
                            byte[] bytes = java.nio.file.Files.readAllBytes(file);
                            String ext = file.getFileName().toString().toLowerCase(Locale.ROOT);
                            // .emote 可能是 quark 二进制，也可能内容恰为 JSON（jsonFormatVersion）——按内容嗅探
                            String format;
                            if (ext.endsWith(".json")) {
                                format = "json";
                            } else if (ext.endsWith(".emotecraft")) {
                                format = "emotecraft";
                            } else {
                                format = isJsonContent(bytes) ? "json" : "quark";
                            }
                            java.util.List<dev.kosmx.playerAnim.core.data.KeyframeAnimation> animations =
                                UniversalEmoteSerializer.readData(new java.io.ByteArrayInputStream(bytes), file.getFileName().toString(), format);
                            if (animations != null) {
                                String baseName = file.getFileName().toString();
                                int dot = baseName.lastIndexOf('.');
                                if (dot > 0) baseName = baseName.substring(0, dot);
                                String nameKey = baseName.toLowerCase(Locale.ROOT);
                                for (dev.kosmx.playerAnim.core.data.KeyframeAnimation anim : animations) {
                                    java.util.UUID uuid = anim.getUuid();
                                    if (uuid != null) {
                                        int size = AnimationBinary.calculateSize(anim, 2);
                                        if (size < 0 || size > MAX_ANIMATION_BYTES) {
                                            throw new IllegalArgumentException("NPC animation exceeds 1 MiB network limit");
                                        }
                                        io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer.hiddenServerEmotes.put(uuid, anim);
                                        WORLD_EMOTES.put(uuid, anim);
                                        EMOTE_BY_NAME.put(nameKey, uuid);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to load emote: {}", file.getFileName(), e);
                        }
                    });
            }
        } catch (Exception e) {
            LOGGER.error("Failed to scan CNPC emotes", e);
        }
    }

    /**
     * 判断字节内容是否为 JSON 格式：跳过前导空白后第一个字符是 '{'。
     * 用于区分 .emote 文件里到底是 quark 二进制还是 JSON 文本。
     */
    private static boolean isJsonContent(byte[] bytes) {
        for (byte b : bytes) {
            if (b == ' ' || b == '\t' || b == '\r' || b == '\n') continue;
            return b == '{';
        }
        return false;
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        NpcEmoteState.clearAll();
        clearWorldEmotes();
    }

    private static void clearWorldEmotes() {
        WORLD_EMOTES.forEach((uuid, animation) -> {
            if (UniversalEmoteSerializer.hiddenServerEmotes.get(uuid) == animation) {
                UniversalEmoteSerializer.hiddenServerEmotes.remove(uuid);
            }
        });
        WORLD_EMOTES.clear();
        EMOTE_BY_NAME.clear();
    }

    /**
     * NPC 实体进入世界（存档读取 / 区块加载 / 跨维度）时，从 NBT 恢复播放状态。
     * 只恢复服务端状态，客户端由 {@link #onStartTracking} 在玩家开始追踪该实体时补发。
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof EntityNPCInterface npc)) return;

        CompoundTag data = npc.getPersistentData();
        if (!data.hasUUID(NBT_EMOTE_ID)) return;

        UUID npcUuid = npc.getUUID();
        NpcEmoteState.getOrCreate(npcUuid).play(data.getUUID(NBT_EMOTE_ID), event.getLevel().getGameTime());
        NpcEmoteState.trackEntityId(npcUuid, npc.getId());
    }

    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof EntityNPCInterface npc) {
            // 卸载只清内存，不清 ForgeData，重新加载时仍可恢复。
            NpcEmoteState.remove(npc.getUUID());
        }
    }

    /** 玩家开始追踪某 NPC（进服、跨维度、区块进入视野）时补发动画，覆盖持久化恢复的可见性。 */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof EntityNPCInterface npc)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        NpcEmoteState state = NpcEmoteState.get(npc.getUUID());
        if (state == null || !state.isPlaying()) return;

        NETWORK.send(PacketDistributor.PLAYER.with(() -> player),
            new EmoteStartPacket(npc.getUUID(), state.getCurrentEmoteId(), state.getStartedAtGameTime(), npc.getId()));
    }

    /**
     * 由 {@code CnpcEmoteResetMixin} 在 {@code EntityNPCInterface.reset()} 返回后调用。
     * 覆盖魔杖「重置」/「全部重置」、关闭编辑菜单、/noppes npc reset、脚本 npc.reset()、死亡重生。
     */
    public static void onNpcReset(EntityNPCInterface npc) {
        UUID npcUuid = npc.getUUID();
        if (npc.level() instanceof ServerLevel serverLevel) {
            npc.getPersistentData().remove(NBT_EMOTE_ID);
            if (!NpcEmoteState.isPlaying(npcUuid)) return;
            NpcEmoteState.remove(npcUuid);
            broadcastEmoteStop(serverLevel, npcUuid);
        } else {
            // 魔杖 GUI 在客户端也会本地调一次 reset()，顺手清客户端状态，重置手感即时
            NpcEmoteClientState.remove(npcUuid);
        }
    }

    /** 开始播放：写状态 + 写 NBT 持久化 + 广播。 */
    public static void startEmote(ServerLevel level, EntityNPCInterface npc, UUID emoteId, long gameTime) {
        UUID npcUuid = npc.getUUID();
        NpcEmoteState.getOrCreate(npcUuid).play(emoteId, gameTime);
        NpcEmoteState.trackEntityId(npcUuid, npc.getId());
        npc.getPersistentData().putUUID(NBT_EMOTE_ID, emoteId);
        broadcastEmoteStart(level, npcUuid, emoteId, gameTime, npc.getId());
    }

    /** 停止播放：清状态 + 清 NBT + 广播。 */
    public static void stopEmote(ServerLevel level, EntityNPCInterface npc) {
        UUID npcUuid = npc.getUUID();
        npc.getPersistentData().remove(NBT_EMOTE_ID);
        NpcEmoteState.remove(npcUuid);
        broadcastEmoteStop(level, npcUuid);
    }

    public static void broadcastEmoteStart(ServerLevel level, UUID npcId, UUID emoteId, long gameTime, int entityId) {
        NETWORK.send(PacketDistributor.DIMENSION.with(() -> level.dimension()),
            new EmoteStartPacket(npcId, emoteId, gameTime, entityId));
    }

    public static void broadcastEmoteStop(ServerLevel level, UUID npcId) {
        NETWORK.send(PacketDistributor.DIMENSION.with(() -> level.dimension()),
            new EmoteStopPacket(npcId));
    }

    public static class EmoteStartPacket {
        public final UUID npcId;
        public final UUID emoteId;
        public final long gameTime;
        public final int entityId;
        public final byte[] animationData;

        public EmoteStartPacket(UUID npcId, UUID emoteId, long gameTime, int entityId) {
            this(npcId, emoteId, gameTime, entityId, encodeAnimation(emoteId));
        }

        private EmoteStartPacket(UUID npcId, UUID emoteId, long gameTime, int entityId, byte[] animationData) {
            this.npcId = npcId;
            this.emoteId = emoteId;
            this.gameTime = gameTime;
            this.entityId = entityId;
            this.animationData = animationData;
        }

        private static byte[] encodeAnimation(UUID emoteId) {
            KeyframeAnimation animation = ServerEmoteAPI.getEmote(emoteId);
            if (animation == null) return new byte[0];
            int size = AnimationBinary.calculateSize(animation, 2);
            if (size < 0 || size > MAX_ANIMATION_BYTES) {
                throw new IllegalArgumentException("NPC animation exceeds 1 MiB network limit");
            }
            // 只发骨骼/关键帧，不发图标、显示名等元数据，不写玩家个人 emotes 目录。
            return AnimationBinary.write(animation, ByteBuffer.allocate(size), 2).array();
        }

        public static void toBytes(EmoteStartPacket packet, FriendlyByteBuf buffer) {
            buffer.writeUUID(packet.npcId);
            buffer.writeUUID(packet.emoteId);
            buffer.writeLong(packet.gameTime);
            buffer.writeInt(packet.entityId);
            buffer.writeByteArray(packet.animationData);
        }

        public static EmoteStartPacket fromBytes(FriendlyByteBuf buffer) {
            return new EmoteStartPacket(buffer.readUUID(), buffer.readUUID(), buffer.readLong(), buffer.readInt(), buffer.readByteArray(MAX_ANIMATION_BYTES));
        }

        public static void handle(EmoteStartPacket packet, Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
            net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                // 服务端安全：这些是 S2C 包，若被恶意客户端伪造发到服务端，直接忽略，避免加载客户端类 NpcEmoteClientState
                if (context.getSender() != null) return;
                NpcEmoteClientState.getOrCreate(packet.npcId).play(packet.emoteId, packet.gameTime, packet.entityId, packet.animationData);
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

        public static void handle(EmoteStopPacket packet, Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
            net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                // 服务端安全：这些是 S2C 包，若被恶意客户端伪造发到服务端，直接忽略
                if (context.getSender() != null) return;
                NpcEmoteClientState.remove(packet.npcId);
            });
            context.setPacketHandled(true);
        }
    }
}
