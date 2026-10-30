package bin.cnpcemotes.client;

import bin.cnpcemotes.animation.EmoteAnimation;
import bin.cnpcemotes.state.NpcEmoteClientState;
import dev.kosmx.playerAnim.impl.IMutableModel;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import dev.kosmx.playerAnim.impl.animation.IBendHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = "cnpcemotes", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CnpcEmoteClientHandler {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level != null && !Minecraft.getInstance().isPaused()) {
            NpcEmoteClientState.tickAll();
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (entity instanceof noppes.npcs.entity.EntityNPCInterface) {
            NpcEmoteClientState.remove(entity.getUUID());
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (entity instanceof noppes.npcs.entity.EntityNPCInterface npc) {
            NpcEmoteClientState.trackEntity(npc.getUUID(), entity.getId());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) NpcEmoteClientState.clearAll();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        NpcEmoteClientState.clearAll();
    }

    /** 在模型自身 setupAnim 结束后应用，避免 PlayerModel 的非玩家分支抹掉弯曲。 */
    public static void applyEmote(HumanoidModel<?> model, LivingEntity entity) {
        if (!(entity instanceof noppes.npcs.entity.EntityCustomNpc)) return;
        AnimationApplier applier = NpcEmoteClientState.getApplier(entity.getUUID());
        if (model instanceof IMutableModel mutable) {
            mutable.getEmoteSupplier().set(applier);
        }
        if (applier == null || !applier.isActive()) {
            IBendHelper.INSTANCE.bend(model.body, null);
            IBendHelper.INSTANCE.bend(model.rightArm, null);
            IBendHelper.INSTANCE.bend(model.leftArm, null);
            IBendHelper.INSTANCE.bend(model.rightLeg, null);
            IBendHelper.INSTANCE.bend(model.leftLeg, null);
            return;
        }
        applier.setTickDelta(Minecraft.getInstance().getFrameTime());
        applier.updatePart("head", model.head);
        applier.updatePart("torso", model.body);
        applier.updatePart("rightArm", model.rightArm);
        applier.updatePart("leftArm", model.leftArm);
        applier.updatePart("rightLeg", model.rightLeg);
        applier.updatePart("leftLeg", model.leftLeg);
        model.hat.copyFrom(model.head);
        if (model instanceof PlayerModel<?> player) {
            player.jacket.copyFrom(model.body);
            player.rightSleeve.copyFrom(model.rightArm);
            player.leftSleeve.copyFrom(model.leftArm);
            player.rightPants.copyFrom(model.rightLeg);
            player.leftPants.copyFrom(model.leftLeg);
        }
    }

    @Mod.EventBusSubscriber(modid = "cnpcemotes", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void onFMLCommonSetup(FMLCommonSetupEvent event) {
            String[] defaultEmotes = {"wave", "clap", "point", "here", "palm"};
            for (String emote : defaultEmotes) {
                EmoteAnimation.registerEmote(emote);
            }
        }
    }
}
