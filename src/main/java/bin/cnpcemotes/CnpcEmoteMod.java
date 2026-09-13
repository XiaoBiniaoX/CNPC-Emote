package bin.cnpcemotes;

import bin.cnpcemotes.animation.EmoteAnimation;
import bin.cnpcemotes.command.BinCnpcEmoteCommand;
import bin.cnpcemotes.state.NpcEmoteState;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import io.github.kosmx.emotes.api.events.server.ServerEmoteAPI;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Mod("cnpcemotes")
public class CnpcEmoteMod {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PROTOCOL_VERSION = "1";
    private static final AtomicInteger MESSAGE_ID = new AtomicInteger(0);
    private static int serverTickCounter = 0;
    private static net.minecraft.server.level.ServerLevel serverLevel;
    
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("cnpcemotes", "main"),
            () -> PROTOCOL_VERSION,
            s -> true,
            s -> true
    );

    public CnpcEmoteMod() {
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("CNPC-Emote initializing");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NETWORK.registerMessage(MESSAGE_ID.getAndIncrement(), EmoteStartPacket.class, 
                (msg, buf) -> EmoteStartPacket.toBytes(msg, buf),
                EmoteStartPacket::fromBytes,
                (msg, ctxSupplier) -> EmoteStartPacket.handle(msg, ctxSupplier));
            NETWORK.registerMessage(MESSAGE_ID.getAndIncrement(), EmoteStopPacket.class,
                (msg, buf) -> EmoteStopPacket.toBytes(msg, buf),
                EmoteStopPacket::fromBytes,
                (msg, ctxSupplier) -> EmoteStopPacket.handle(msg, ctxSupplier));
            // 在服务器端安装默认emote
            installDefaultEmotes();
            LOGGER.info("CNPC-Emote network registered");
        });
        LOGGER.info("CNPC-Emote common setup complete");
    }

    private void installDefaultEmotes() {
        try {
            File emoteDir = new File(new File(System.getProperty("user.home"), ".minecraft"), "emotes");
            emoteDir.mkdirs();
            
            File waveFile = new File(emoteDir, "wave.emote");
            if (!waveFile.exists()) {
                InputStream stream = getClass().getClassLoader().getResourceAsStream("assets/cnpcemotes/emotes/default/wave.emote");
                if (stream != null) {
                    Files.copy(stream, waveFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    stream.close();
                    LOGGER.info("Installed default wave emote to {}", waveFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to install default emotes", e);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(BinCnpcEmoteCommand.register());
        LOGGER.info("Registered /bincnpcemote command");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        NpcEmoteState.restoreAll();
        loadCustomNpcEmotes(event.getServer());
        serverLevel = event.getServer().overworld();
        bin.cnpcemotes.event.CnpcEmoteNpcEventListener.register();
        LOGGER.info("CNPC-Emote server started");
    }

    private void loadCustomNpcEmotes(net.minecraft.server.MinecraftServer server) {
        try {
            java.io.File worldFolder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
            java.io.File savesDir = worldFolder.getParentFile().getParentFile();
            File[] levelDirs = savesDir.listFiles();
            if (levelDirs == null) {
                return;
            }

            for (File levelDir : levelDirs) {
                if (!levelDir.isDirectory()) continue;
                java.nio.file.Path npcEmoteDir = levelDir.toPath().resolve("customnpcs/emote");
                if (!java.nio.file.Files.exists(npcEmoteDir)) continue;

                java.nio.file.Files.list(npcEmoteDir)
                    .filter(p -> p.toString().toLowerCase().endsWith(".emotecraft") || p.toString().toLowerCase().endsWith(".emote") || p.toString().toLowerCase().endsWith(".json"))
                    .forEach(file -> {
                        try {
                            java.io.InputStream is = java.nio.file.Files.newInputStream(file);
                            String ext = file.getFileName().toString().toLowerCase();
                            java.util.List<dev.kosmx.playerAnim.core.data.KeyframeAnimation> animations;
                            if (ext.endsWith(".json")) {
                                animations = io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer.readData(is, null, "json");
                            } else if (ext.endsWith(".emotecraft")) {
                                animations = io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer.readData(is, null, "emotecraft");
                            } else {
                                animations = io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer.readData(is, null, "quark");
                            }
                            if (animations != null) {
                                for (dev.kosmx.playerAnim.core.data.KeyframeAnimation anim : animations) {
                                    java.util.UUID uuid = anim.getUuid();
                                    if (uuid != null) {
                                        io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer.hiddenServerEmotes.put(uuid, anim);
                                    }
                                }
                            }
                            is.close();
                        } catch (Exception e) {
                            LOGGER.error("Failed to load emote: {}", file.getFileName(), e);
                        }
                    });
            }
        } catch (Exception e) {
            LOGGER.error("Failed to scan CNPC emotes", e);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (serverLevel == null) return;
        // Process any pending stops first (sent from EntityLeaveLevel)
        java.util.Queue<UUID> pending = NpcEmoteState.getPendingStops();
        java.util.List<UUID> toProcess = new java.util.ArrayList<>();
        UUID uuid;
        while ((uuid = pending.poll()) != null) {
            if (!NpcEmoteState.getEntityIdMap().containsKey(uuid)) continue;
            int entityId = NpcEmoteState.getEntityIdMap().get(uuid);
            boolean found = false;
            for (net.minecraft.world.entity.Entity e : serverLevel.getEntities(null, new net.minecraft.world.phys.AABB(-10000, -10000, -10000, 10000, 10000, 10000))) {
                if (e.getId() == entityId) { found = true; break; }
            }
            if (!found) toProcess.add(uuid);
        }
        // Also check for entities that disappeared this tick
        java.util.List<UUID> toCheck = new java.util.ArrayList<>();
        for (java.util.Map.Entry<UUID, Integer> entry : NpcEmoteState.getEntityIdMap().entrySet()) {
            int entityId = entry.getValue();
            boolean found = false;
            for (net.minecraft.world.entity.Entity e : serverLevel.getEntities(null, new net.minecraft.world.phys.AABB(-10000, -10000, -10000, 10000, 10000, 10000))) {
                if (e.getId() == entityId) { found = true; break; }
            }
            if (!found) toCheck.add(entry.getKey());
        }
        toCheck.addAll(toProcess);
        java.util.Set<UUID> stopSet = new java.util.HashSet<>(toCheck);
        for (UUID npcUuid : stopSet) {
            LOGGER.info("[CNPC-Emote] Server END tick: NPC {} entity gone, sending stop", npcUuid);
            broadcastEmoteStop(serverLevel, npcUuid);
            NpcEmoteState.remove(npcUuid);
        }
    }

    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof noppes.npcs.entity.EntityNPCInterface npc) {
            UUID npcUuid = npc.getUUID();
            LOGGER.info("[CNPC-Emote] Server EntityLeaveLevel: uuid={}, name={}, anim={}, playing={}",
                    npcUuid, npc.display.getName(), npc.currentAnimation, NpcEmoteState.getOrCreate(npcUuid).isPlaying());
            if (NpcEmoteState.getOrCreate(npcUuid).isPlaying()) {
                NpcEmoteState.requestStop(npcUuid);
                // Broadcast immediately - don't wait for END tick
                ServerLevel serverLevel = (ServerLevel) event.getEntity().level();
                broadcastEmoteStop(serverLevel, npcUuid);
                NpcEmoteState.remove(npcUuid);
                npc.currentAnimation = 0;
                npc.animationStart = 0;
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        NpcEmoteState.clearAll();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        syncPersistentEmotes(player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        syncPersistentEmotes(player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        syncPersistentEmotes(player);
    }

    private static void syncPersistentEmotes(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long gameTime = level.getGameTime();
        Map<UUID, KeyframeAnimation> emotes = NpcEmoteState.getEmoteMap();
        emotes.forEach((npcId, emote) -> {
            NETWORK.send(PacketDistributor.PLAYER.with(() -> player), 
                new EmoteStartPacket(npcId, emote.get(), gameTime));
        });
    }

    public static void broadcastEmoteStart(ServerLevel level, UUID npcId, UUID emoteId, long gameTime, int entityId) {
        NETWORK.send(PacketDistributor.DIMENSION.with(() -> level.dimension()),
            new EmoteStartPacket(npcId, emoteId, gameTime, entityId));
    }

    public static void broadcastEmoteStop(ServerLevel level, UUID npcId) {
        int playerCount = level.players().size();
        LOGGER.info("[CNPC-Emote] Server broadcastEmoteStop: npcId={}, dimension={}, playersOnline={}",
                npcId, level.dimension(), playerCount);
        NETWORK.send(PacketDistributor.DIMENSION.with(() -> level.dimension()),
            new EmoteStopPacket(npcId));
    }

    public static class EmoteStartPacket {
        public final UUID npcId;
        public final UUID emoteId;
        public final long gameTime;
        public final int entityId;

        public EmoteStartPacket(UUID npcId, UUID emoteId, long gameTime) {
            this(npcId, emoteId, gameTime, -1);
        }

        public EmoteStartPacket(UUID npcId, UUID emoteId, long gameTime, int entityId) {
            this.npcId = npcId;
            this.emoteId = emoteId;
            this.gameTime = gameTime;
            this.entityId = entityId;
        }

        public static void toBytes(EmoteStartPacket packet, FriendlyByteBuf buffer) {
            buffer.writeUUID(packet.npcId);
            buffer.writeUUID(packet.emoteId);
            buffer.writeLong(packet.gameTime);
            buffer.writeInt(packet.entityId);
        }

        public static EmoteStartPacket fromBytes(FriendlyByteBuf buffer) {
            return new EmoteStartPacket(buffer.readUUID(), buffer.readUUID(), buffer.readLong(), buffer.readInt());
        }

        public static void handle(EmoteStartPacket packet, Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
            net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                bin.cnpcemotes.state.NpcEmoteClientState.getOrCreate(packet.npcId).play(packet.emoteId, packet.gameTime, packet.entityId);
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
            boolean isLocal = context.getSender() == null;
            LOGGER.info("[CNPC-Emote] Client received EmoteStopPacket: npcId={}, isLocal={} (singleplayer)", packet.npcId, isLocal);
            context.enqueueWork(() -> {
                LOGGER.info("[CNPC-Emote] Client working thread: removing npcId={}, activeBefore={}",
                        packet.npcId, bin.cnpcemotes.state.NpcEmoteClientState.ACTIVE_SIZE());
                bin.cnpcemotes.state.NpcEmoteClientState.remove(packet.npcId);
            });
            context.setPacketHandled(true);
        }
    }
}
