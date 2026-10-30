package bin.cnpcemotes.mixin;

import bin.cnpcemotes.client.CnpcEmoteClientHandler;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class CnpcEmotePlayerModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void cnpcemotes$applyAfterPlayerReset(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        CnpcEmoteClientHandler.applyEmote((PlayerModel<?>) (Object) this, entity);
    }
}
