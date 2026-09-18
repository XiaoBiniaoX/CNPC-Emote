package bin.cnpcemotes.mixin;

import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 彻底禁用 emotecraft 的玩家 Emote 按键（打开快速菜单 B 键 / 停止键）。
 * <p>
 * 两条路径都要禁（emotecraft {@code io.github.kosmx.emotes.forge.ClientInit}）：
 * <ol>
 *   <li>{@code keyBindingRegister(RegisterKeyMappingsEvent)}：阻止把 openMenuKey/stopEmote 注册进
 *       options.keyMappings，使玩家在按键配置里看不到这两个按键。</li>
 *   <li>{@code keyListenerEvent(InputEvent.Key)}：阻止消费按键。否则 openMenuKey 虽未注册，
 *       但仍通过全局 KeyMapping.click(B) 被设为 isDown，consumeClick() 仍会打开面板。</li>
 * </ol>
 * 两者都禁用后，玩家既找不到按键、按 B 也不会打开面板。仅客户端生效（client 段）。
 */
@Mixin(value = io.github.kosmx.emotes.forge.ClientInit.class, remap = false)
public abstract class EmoteKeyDisableMixin {

    @Inject(method = "keyBindingRegister", at = @At("HEAD"), cancellable = true)
    private void cnpcemotes$disableEmoteKeyRegister(RegisterKeyMappingsEvent event, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "keyListenerEvent", at = @At("HEAD"), cancellable = true)
    private static void cnpcemotes$disableEmoteKeyTrigger(InputEvent.Key event, CallbackInfo ci) {
        ci.cancel();
    }
}