package bin.cnpcemotes.client;

import bin.cnpcemotes.animation.EmoteAnimation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = "cnpcemotes", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CnpcEmoteClientHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            bin.cnpcemotes.state.NpcEmoteClientState.tickAll();
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        boolean isNpc = entity instanceof noppes.npcs.entity.EntityNPCInterface;
        LOGGER.info("[CNPC-Emote] Client EntityLeaveLevel: isNpc={}, entityClass={}", isNpc, entity.getClass().getName());
        if (isNpc) {
            bin.cnpcemotes.state.NpcEmoteClientState.clearAll();
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof noppes.npcs.entity.EntityNPCInterface npc) {
            bin.cnpcemotes.state.NpcEmoteClientState.trackEntity(npc.getUUID(), entity.getId());
            LOGGER.info("[CNPC-Emote] Client EntityJoinLevel: NPC tracked uuid={}, entityId={}", npc.getUUID(), entity.getId());
        }
    }

    @Mod.EventBusSubscriber(modid = "cnpcemotes", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {
        private static final Logger LOGGER = LogManager.getLogger();

        @SubscribeEvent
        public static void onFMLCommonSetup(FMLCommonSetupEvent event) {
            String[] defaultEmotes = {"wave", "clap", "point", "here", "palm"};
            for (String emote : defaultEmotes) {
                EmoteAnimation.registerEmote(emote);
            }
        }
    }
}

