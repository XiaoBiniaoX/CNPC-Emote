package bin.cnpcemotes.state;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import io.github.kosmx.emotes.api.events.server.ServerEmoteAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NpcEmoteClientState {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<UUID, NpcEmoteClientState> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> NPC_ENTITY_ID = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    private AnimationApplier applier;
    private UUID currentEmoteId;
    private UUID pendingEmoteId;
    private int trackedEntityId = -1;

    private NpcEmoteClientState() {
    }

    public static NpcEmoteClientState getOrCreate(UUID npcId) {
        return ACTIVE.computeIfAbsent(npcId, ignored -> new NpcEmoteClientState());
    }

    public static void trackEntity(UUID npcUuid, int entityId) {
        NPC_ENTITY_ID.put(npcUuid, entityId);
        NpcEmoteClientState state = ACTIVE.get(npcUuid);
        if (state != null) {
            state.trackedEntityId = entityId;
        }
        LOGGER.info("[CNPC-Emote] Client trackEntity: uuid={}, entityId={}", npcUuid, entityId);
    }

    public static AnimationApplier getApplier(UUID npcId) {
        NpcEmoteClientState state = ACTIVE.get(npcId);
        return state == null ? null : state.applier;
    }

    public static void clearAll() {
        LOGGER.info("[CNPC-Emote] Client clearAll called, active={}", ACTIVE.size());
        ACTIVE.values().forEach(NpcEmoteClientState::stop);
        ACTIVE.clear();
        NPC_ENTITY_ID.clear();
    }

    public static void remove(UUID npcId) {
        LOGGER.info("[CNPC-Emote] Client remove: uuid={}, existed={}, applierActive={}",
                npcId, ACTIVE.containsKey(npcId),
                ACTIVE.get(npcId) != null && ACTIVE.get(npcId).applier != null);
        NpcEmoteClientState state = ACTIVE.get(npcId);
        if (state != null) {
            state.stop();
        }
        ACTIVE.remove(npcId);
        NPC_ENTITY_ID.remove(npcId);
    }

    public static int ACTIVE_SIZE() {
        return ACTIVE.size();
    }

    public static void tickAll() {
        tickCounter++;
        Iterator<Map.Entry<UUID, NpcEmoteClientState>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, NpcEmoteClientState> entry = it.next();
            NpcEmoteClientState state = entry.getValue();
            UUID npcUuid = entry.getKey();

            if (state.applier == null && state.pendingEmoteId != null) {
                state.tryStartPending();
            }
            if (state.applier == null) {
                continue;
            }

            // Check entity ID mismatch every tick
            Integer storedId = NPC_ENTITY_ID.get(npcUuid);
            if (storedId != null && storedId != state.trackedEntityId) {
                LOGGER.info("[CNPC-Emote] Client entity ID changed: uuid={}, old={}, new={}, applierActive={}",
                        npcUuid, state.trackedEntityId, storedId,
                        state.applier != null && state.applier.isActive());
                state.stop();
                it.remove();
                continue;
            }

            state.applier.tick();
            if (state.applier.isActive()) {
                continue;
            }
            LOGGER.info("[CNPC-Emote] Client applier inactive: uuid={}, removing", npcUuid);
            state.applier = null;
            state.currentEmoteId = null;
            state.pendingEmoteId = null;
            it.remove();
        }
    }

    public void play(UUID emoteId, long startGameTime, int entityId) {
        this.trackedEntityId = entityId;
        if (emoteId.equals(this.currentEmoteId) && this.applier != null && this.applier.isActive()) {
            return;
        }
        this.pendingEmoteId = emoteId;
        this.applier = null;
        this.currentEmoteId = null;
        this.tryStartPending();
    }

    public void play(UUID emoteId, long startGameTime) {
        play(emoteId, startGameTime, -1);
    }

    private void tryStartPending() {
        if (this.pendingEmoteId == null) {
            return;
        }
        Map<UUID, KeyframeAnimation> loaded = ServerEmoteAPI.getLoadedEmotes();
        KeyframeAnimation emote = loaded.get(this.pendingEmoteId);
        if (emote == null) {
            this.pendingEmoteId = null;
            return;
        }
        try {
            KeyframeAnimationPlayer playing = new KeyframeAnimationPlayer(emote, 1, true);
            this.applier = new AnimationApplier((IAnimation) playing);
            this.currentEmoteId = this.pendingEmoteId;
            this.pendingEmoteId = null;
        } catch (Exception e) {
            LOGGER.error("[CNPC-Emote] Failed to load emote: {}", this.pendingEmoteId, e);
            this.pendingEmoteId = null;
        }
    }

    public void stop() {
        this.applier = null;
        this.currentEmoteId = null;
        this.pendingEmoteId = null;
    }
}
