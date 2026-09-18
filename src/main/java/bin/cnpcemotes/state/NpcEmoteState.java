package bin.cnpcemotes.state;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NpcEmoteState {
    private static final Map<UUID, NpcEmoteState> ACTIVE_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> NPC_ENTITY_ID = new ConcurrentHashMap<>();

    private final UUID npcUuid;
    private UUID currentEmoteId;
    private long startedAtGameTime;

    private NpcEmoteState(UUID npcUuid) {
        this.npcUuid = npcUuid;
    }

    public static NpcEmoteState getOrCreate(UUID npcUuid) {
        return ACTIVE_STATES.computeIfAbsent(npcUuid, NpcEmoteState::new);
    }

    public static NpcEmoteState get(UUID npcUuid) {
        return ACTIVE_STATES.get(npcUuid);
    }

    public static boolean isPlaying(UUID npcUuid) {
        NpcEmoteState state = ACTIVE_STATES.get(npcUuid);
        return state != null && state.isPlaying();
    }

    public static void trackEntityId(UUID npcUuid, int entityId) {
        NPC_ENTITY_ID.put(npcUuid, entityId);
    }

    public static void clearAll() {
        ACTIVE_STATES.clear();
        NPC_ENTITY_ID.clear();
    }

    public static void remove(UUID npcUuid) {
        ACTIVE_STATES.remove(npcUuid);
        NPC_ENTITY_ID.remove(npcUuid);
    }

    public void play(UUID emoteId, long gameTime) {
        this.currentEmoteId = emoteId;
        this.startedAtGameTime = gameTime;
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
}
