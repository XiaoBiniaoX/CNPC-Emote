package bin.cnpcemotes.event;

import bin.cnpcemotes.state.NpcEmoteState;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.api.wrapper.WrapperNpcAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CnpcEmoteNpcEventListener {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void register() {
        WrapperNpcAPI.EVENT_BUS.register(new CnpcEmoteNpcEventListener());
        LOGGER.info("[CNPC-Emote] Registered NpcEvent listener on CNPC bus");
    }

    public void onNpcUpdate(NpcEvent.UpdateEvent event) {
        try {
            noppes.npcs.api.entity.ICustomNpc npc = event.npc;
            if (!(npc instanceof noppes.npcs.entity.EntityNPCInterface entityNPC)) return;

            java.util.UUID npcUuid = entityNPC.getUUID();
            int entityId = entityNPC.getId();

            if (NpcEmoteState.getEntityIdMap().containsKey(npcUuid)) {
                int storedId = NpcEmoteState.getEntityIdMap().get(npcUuid);
                if (storedId != entityId) {
                    LOGGER.info("[CNPC-Emote] NpcEvent: ID changed {}->{} for NPC {}, stopping",
                            storedId, entityId, npcUuid);
                    if (entityNPC.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        bin.cnpcemotes.CnpcEmoteMod.broadcastEmoteStop(serverLevel, npcUuid);
                    }
                    NpcEmoteState.remove(npcUuid);
                } else if (entityNPC.currentAnimation == 0) {
                    LOGGER.info("[CNPC-Emote] NpcEvent: animation=0 for NPC {} (id={}), requesting stop", npcUuid, entityId);
                    NpcEmoteState.requestStop(npcUuid);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[CNPC-Emote] Error in NpcEvent listener", e);
        }
    }
}
