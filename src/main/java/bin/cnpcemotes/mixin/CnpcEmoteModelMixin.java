package bin.cnpcemotes.mixin;

import bin.cnpcemotes.state.NpcEmoteClientState;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(HumanoidModel.class)
public abstract class CnpcEmoteModelMixin<T extends LivingEntity> {
    @Shadow
    public ModelPart head;
    @Shadow
    public ModelPart body;
    @Shadow
    public ModelPart rightArm;
    @Shadow
    public ModelPart leftArm;
    @Shadow
    public ModelPart rightLeg;
    @Shadow
    public ModelPart leftLeg;

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void cnpcemotes$applyEmoteToNpc(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof EntityCustomNpc)) return;

        UUID npcId = entity.getUUID();
        AnimationApplier applier = NpcEmoteClientState.getApplier(npcId);
        if (applier == null || !applier.isActive()) return;

        applier.updatePart("head", this.head);
        applier.updatePart("torso", this.body);
        applier.updatePart("rightArm", this.rightArm);
        applier.updatePart("leftArm", this.leftArm);
        applier.updatePart("rightLeg", this.rightLeg);
        applier.updatePart("leftLeg", this.leftLeg);
    }
}
