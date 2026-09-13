package bin.cnpcemotes.state;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.UUIDMap;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NpcEmoteState {
    private static final Map<UUID, NpcEmoteState> ACTIVE_STATES = new ConcurrentHashMap<>();
    private static final UUIDMap<KeyframeAnimation> EMOTE_MAP = new UUIDMap<>();
    private static final Map<UUID, Integer> NPC_ENTITY_ID = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> STOP_REQUESTED = new ConcurrentHashMap<>();
    private static final Queue<UUID> PENDING_STOPS = new ConcurrentLinkedQueue<>();

    private final UUID npcUuid;
    private UUID currentEmoteId;
    private long startedAtGameTime;

    private NpcEmoteState(UUID npcUuid) {
        this.npcUuid = npcUuid;
    }

    public static NpcEmoteState getOrCreate(UUID npcUuid) {
        return ACTIVE_STATES.computeIfAbsent(npcUuid, NpcEmoteState::new);
    }

    public static void trackEntityId(UUID npcUuid, int entityId) {
        NPC_ENTITY_ID.put(npcUuid, entityId);
    }

    public static void registerEmote(KeyframeAnimation emote) {
        EMOTE_MAP.add(emote);
    }

    public static void restoreAll() {
        ACTIVE_STATES.clear();
    }

    public static void clearAll() {
        ACTIVE_STATES.clear();
        EMOTE_MAP.clear();
        NPC_ENTITY_ID.clear();
        STOP_REQUESTED.clear();
        // Don't clear PENDING_STOPS - let END tick broadcast them
    }

    public static Map<UUID, KeyframeAnimation> getEmoteMap() {
        return EMOTE_MAP;
    }

    public static Map<UUID, Integer> getEntityIdMap() {
        return NPC_ENTITY_ID;
    }

    public static Queue<UUID> getPendingStops() {
        return PENDING_STOPS;
    }

    public void play(UUID emoteId, long gameTime) {
        this.currentEmoteId = emoteId;
        this.startedAtGameTime = gameTime;
    }

    public void stop() {
        this.currentEmoteId = null;
    }

    public boolean isPlaying() {
        return this.currentEmoteId != null;
    }

    public UUID getNpcUuid() {
        return this.npcUuid;
    }

    public UUID getCurrentEmoteId() {
        return this.currentEmoteId;
    }

    public long getStartedAtGameTime() {
        return this.startedAtGameTime;
    }

    public static void remove(UUID npcUuid) {
        ACTIVE_STATES.remove(npcUuid);
        NPC_ENTITY_ID.remove(npcUuid);
        STOP_REQUESTED.remove(npcUuid);
        PENDING_STOPS.remove(npcUuid);
    }

    public static void requestStop(UUID npcUuid) {
        STOP_REQUESTED.put(npcUuid, true);
        PENDING_STOPS.add(npcUuid);
    }

    public static boolean stopRequested(UUID npcUuid) {
        return STOP_REQUESTED.containsKey(npcUuid);
    }
}
