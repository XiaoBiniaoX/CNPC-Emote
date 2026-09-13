package bin.cnpcemotes.animation;

import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import io.github.kosmx.emotes.api.events.server.ServerEmoteAPI;
import noppes.npcs.client.model.animation.AnimationBase;
import noppes.npcs.client.model.animation.AnimationHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmoteAnimation implements AnimationBase {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<String, Integer> EMOTE_ANIM_MAP = new ConcurrentHashMap<>();
    private static int nextAnimId = 100;

    private final String emoteName;
    private KeyframeAnimationPlayer player;
    private AnimationApplier applier;

    public EmoteAnimation(String emoteName, UUID npcId) {
        this.emoteName = emoteName;
    }

    public static int registerEmote(String emoteName) {
        if (EMOTE_ANIM_MAP.containsKey(emoteName)) {
            return EMOTE_ANIM_MAP.get(emoteName);
        }
        int animId = nextAnimId++;
        EMOTE_ANIM_MAP.put(emoteName, animId);
        EmoteAnimation animInstance = new EmoteAnimation(emoteName, null);
        AnimationHandler.addAnimation(animId, animInstance);
        return animId;
    }

    public static int getAnimationId(String emoteName) {
        Integer id = EMOTE_ANIM_MAP.get(emoteName);
        return id != null ? id : -1;
    }

    @Override
    public void animatePre(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, net.minecraft.world.entity.Entity entity, net.minecraft.client.model.HumanoidModel model, int animationStart) {
        if (player == null) {
            Map<UUID, KeyframeAnimation> loaded = ServerEmoteAPI.getLoadedEmotes();
            KeyframeAnimation emote = null;
            for (KeyframeAnimation ke : loaded.values()) {
                Object nameObj = ke.extraData.get("name");
                String name = nameObj != null ? nameObj.toString() : "";
                if (name.equalsIgnoreCase(emoteName)) {
                    emote = ke;
                    break;
                }
            }
            if (emote != null) {
                try {
                    player = new KeyframeAnimationPlayer(emote);
                    applier = new AnimationApplier(player);
                } catch (Exception e) {
                    LOGGER.error("Failed to create animation player for '{}'", emoteName, e);
                    player = null;
                    applier = null;
                }
            }
        }

        if (applier != null && player != null) {
            player.tick();
            if (!player.isActive()) {
                player = null;
                applier = null;
            }
        }
    }

    @Override
    public void animatePost(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, net.minecraft.world.entity.Entity entity, net.minecraft.client.model.HumanoidModel model, int animationStart) {
        if (applier == null || player == null) {
            return;
        }
        try {
            applier.updatePart("head", getPart(model, "head"));
            applier.updatePart("torso", getPart(model, "body"));
            applier.updatePart("rightArm", getPart(model, "rightArm"));
            applier.updatePart("leftArm", getPart(model, "leftArm"));
            applier.updatePart("rightLeg", getPart(model, "rightLeg"));
            applier.updatePart("leftLeg", getPart(model, "leftLeg"));
        } catch (Exception e) {
            LOGGER.error("Failed to apply emote animation '{}'", emoteName, e);
        }
    }

    private net.minecraft.client.model.geom.ModelPart getPart(net.minecraft.client.model.HumanoidModel<?> model, String partName) {
        try {
            java.lang.reflect.Field field = net.minecraft.client.model.HumanoidModel.class.getDeclaredField(partName);
            field.setAccessible(true);
            return (net.minecraft.client.model.geom.ModelPart) field.get(model);
        } catch (Exception e) {
            return null;
        }
    }
}
