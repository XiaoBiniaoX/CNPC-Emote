package bin.cnpcemotes.mixin;

import bin.cnpcemotes.CnpcEmoteMod;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityNPCInterface.reset() 是原地复活（unsetRemoved + revive），entityId/UUID 都不变，
 * 因此不会触发 EntityLeaveLevelEvent。直接注入 reset() 才能命中所有重置路径：
 * 魔杖「重置」/「全部重置」、关闭编辑菜单、/noppes npc reset、脚本 npc.reset()、死亡重生。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class CnpcEmoteResetMixin {

    @Inject(method = "reset", at = @At("RETURN"))
    private void cnpcemotes$stopEmoteOnReset(CallbackInfo ci) {
        CnpcEmoteMod.onNpcReset((EntityNPCInterface) (Object) this);
    }
}
