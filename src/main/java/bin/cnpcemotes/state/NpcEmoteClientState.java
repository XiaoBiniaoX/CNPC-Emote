package bin.cnpcemotes.state;

import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.AnimationBinary;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import io.github.kosmx.emotes.api.events.server.ServerEmoteAPI;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NpcEmoteClientState {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<UUID, NpcEmoteClientState> ACTIVE = new ConcurrentHashMap<>();

    private AnimationApplier applier;
    private UUID currentEmoteId;
    private int trackedEntityId = -1;
    private long startedAtGameTime;

    private NpcEmoteClientState() {
    }

    public static NpcEmoteClientState getOrCreate(UUID npcId) {
        return ACTIVE.computeIfAbsent(npcId, ignored -> new NpcEmoteClientState());
    }

    public static void trackEntity(UUID npcUuid, int entityId) {
        NpcEmoteClientState state = ACTIVE.get(npcUuid);
        if (state != null) {
            if (state.trackedEntityId != -1 && state.trackedEntityId != entityId) {
                remove(npcUuid);
            } else {
                state.trackedEntityId = entityId;
            }
        }
    }

    public static AnimationApplier getApplier(UUID npcId) {
        NpcEmoteClientState state = ACTIVE.get(npcId);
        return state == null ? null : state.applier;
    }

    public static void clearAll() {
        ACTIVE.values().forEach(NpcEmoteClientState::stop);
        ACTIVE.clear();
    }

    public static void remove(UUID npcId) {
        NpcEmoteClientState state = ACTIVE.remove(npcId);
        if (state != null) {
            state.stop();
        }
    }

    public static int ACTIVE_SIZE() {
        return ACTIVE.size();
    }

    public static void tickAll() {
        Iterator<Map.Entry<UUID, NpcEmoteClientState>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, NpcEmoteClientState> entry = it.next();
            NpcEmoteClientState state = entry.getValue();
            if (state.applier == null) {
                it.remove();
                continue;
            }

            state.applier.tick();
            if (state.applier.isActive()) {
                continue;
            }
            state.stop();
            it.remove();
        }
    }

    public void play(UUID emoteId, long startGameTime, int entityId) {
        play(emoteId, startGameTime, entityId, new byte[0]);
    }

    public void play(UUID emoteId, long startGameTime) {
        play(emoteId, startGameTime, -1);
    }

    public void play(UUID emoteId, long startGameTime, int entityId, byte[] animationData) {
        if (emoteId.equals(currentEmoteId) && startedAtGameTime == startGameTime && trackedEntityId == entityId && applier != null && applier.isActive()) return;
        stop();
        this.trackedEntityId = entityId;
        this.startedAtGameTime = startGameTime;
        try {
            // 远端存档动画由 S2C 包携带；不将数据塞进全局注册表或写入个人目录。
            KeyframeAnimation emote;
            if (animationData.length == 0) {
                emote = ServerEmoteAPI.getEmote(emoteId);
            } else {
                ByteBuffer data = ByteBuffer.wrap(animationData);
                emote = AnimationBinary.read(data, 2);
                if (data.hasRemaining() || !emoteId.equals(emote.getUuid())) {
                    throw new IllegalArgumentException("Invalid NPC animation payload");
                }
            }
            if (emote == null) return;
            var level = Minecraft.getInstance().level;
            int elapsed = level == null ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(0L, level.getGameTime() - startGameTime));
            if (!emote.isInfinite && elapsed >= emote.stopTick) return;
            // NPC 不修改关键帧，直接共享不可变数据，避免为每个 NPC 再复制整份动画。
            this.applier = new AnimationApplier(new KeyframeAnimationPlayer(emote, elapsed));
            this.currentEmoteId = emoteId;
        } catch (Exception e) {
            LOGGER.error("[CNPC-Emote] Failed to load emote: {}", emoteId, e);
        }
    }

    public void stop() {
        this.applier = null;
        this.currentEmoteId = null;
    }
}
