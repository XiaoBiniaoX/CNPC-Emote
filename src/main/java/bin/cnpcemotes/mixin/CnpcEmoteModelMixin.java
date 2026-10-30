package bin.cnpcemotes.mixin;

import bin.cnpcemotes.client.CnpcEmoteClientHandler;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class CnpcEmoteModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void cnpcemotes$applyEmoteToNpc(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if (!(model instanceof PlayerModel<?>)) CnpcEmoteClientHandler.applyEmote(model, entity);
    }
}
