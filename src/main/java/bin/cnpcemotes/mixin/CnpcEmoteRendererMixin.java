package bin.cnpcemotes.mixin;

import bin.cnpcemotes.state.NpcEmoteClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(LivingEntityRenderer.class)
public abstract class CnpcEmoteRendererMixin<T extends LivingEntity> {
    @Inject(method = "setupRotations", at = @At("RETURN"))
    private void cnpcemotes$applyEmoteRootTransform(T entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, CallbackInfo ci) {
        if (!(entity instanceof EntityCustomNpc)) return;

        UUID npcId = entity.getUUID();
        AnimationApplier applier = NpcEmoteClientState.getApplier(npcId);
        if (applier == null || !applier.isActive()) return;

        String rendererName = this.getClass().getName();
        if (!rendererName.contains("RenderCustomNpc")) return;

        Vec3f posVec = applier.get3DTransform("body", TransformType.POSITION, new Vec3f(0.0f, 0.0f, 0.0f));
        poseStack.translate((double)((Float)posVec.getX()).floatValue(), (double)((Float)posVec.getY()).floatValue() + 0.7, (double)((Float)posVec.getZ()).floatValue());
        Vec3f rotVec = applier.get3DTransform("body", TransformType.ROTATION, new Vec3f(0.0f, 0.0f, 0.0f));
        poseStack.mulPose(Axis.ZP.rotation(((Float)rotVec.getZ()).floatValue()));
        poseStack.mulPose(Axis.YP.rotation(((Float)rotVec.getY()).floatValue()));
        poseStack.mulPose(Axis.XP.rotation(((Float)rotVec.getX()).floatValue()));
        poseStack.translate(0.0, -0.7, 0.0);
    }
}
