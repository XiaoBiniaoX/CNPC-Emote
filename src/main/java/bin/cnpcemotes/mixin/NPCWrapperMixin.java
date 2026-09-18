package bin.cnpcemotes.mixin;

import bin.cnpcemotes.CnpcEmoteMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.wrapper.NPCWrapper;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;
import java.util.UUID;

@Mixin(value = NPCWrapper.class, remap = false)
public abstract class NPCWrapperMixin {

    /**
     * NPC 脚本方法：播放指定名称的 emote 动画。
     * <p>
     * 用法示例（在 NPC 脚本中）：
     * <pre>
     * npc.playEmote("wave");  // 播放 customnpcs/emote/wave.emote
     * </pre>
     *
     * @param emoteName emote 文件名（不含扩展名），例如 "wave" 对应 customnpcs/emote/wave.emote
     * @throws CustomNPCsException 如果 emoteName 为空、动画不存在、或不在服务端调用
     */
    public void playEmote(String emoteName) {
        Entity mcEntity = getMCEntity();
        if (emoteName == null || emoteName.trim().isEmpty()) {
            throw new CustomNPCsException("Emote name cannot be null or empty", new Object[0]);
        }
        if (!(mcEntity.level() instanceof ServerLevel serverLevel)) {
            throw new CustomNPCsException("playEmote can only be called on the server side", new Object[0]);
        }
        if (!(mcEntity instanceof EntityNPCInterface npc)) {
            throw new CustomNPCsException("Entity is not a CustomNPC", new Object[0]);
        }

        UUID emoteId = findEmoteByName(emoteName.trim());
        if (emoteId == null) {
            throw new CustomNPCsException("Emote not found: " + emoteName + ". Make sure the file exists in the world's customnpcs/emote/ folder", new Object[0]);
        }

        CnpcEmoteMod.startEmote(serverLevel, npc, emoteId, serverLevel.getGameTime());
    }

    /**
     * NPC 脚本方法：停止当前正在播放的 emote 动画。
     * <p>
     * 用法示例（在 NPC 脚本中）：
     * <pre>
     * npc.stopEmote();  // 停止动画
     * </pre>
     *
     * @throws CustomNPCsException 如果不在服务端调用
     */
    public void stopEmote() {
        Entity mcEntity = getMCEntity();
        if (!(mcEntity.level() instanceof ServerLevel serverLevel)) {
            throw new CustomNPCsException("stopEmote can only be called on the server side", new Object[0]);
        }
        if (!(mcEntity instanceof EntityNPCInterface npc)) {
            throw new CustomNPCsException("Entity is not a CustomNPC", new Object[0]);
        }

        CnpcEmoteMod.stopEmote(serverLevel, npc);
    }

    /**
     * 通过 IEntity 接口获取底层 MC 实体，规避 @Shadow 无法解析父类继承字段的问题。
     */
    private Entity getMCEntity() {
        return (Entity) ((IEntity<?>) this).getMCEntity();
    }

    /**
     * 根据 emote 名称查找其 UUID。名称可能带 JSON 组件格式（{"translate":...,"fallback":...}），
     * 取 fallback / translate / 裸字符串 与 emoteName 忽略大小写比较。
     */
    private UUID findEmoteByName(String emoteName) {
        String lowerName = emoteName.toLowerCase();
        for (Map.Entry<UUID, KeyframeAnimation> entry : UniversalEmoteSerializer.hiddenServerEmotes.entrySet()) {
            KeyframeAnimation anim = entry.getValue();
            if (anim.extraData == null) continue;
            Object nameObj = anim.extraData.get("name");
            String animName = extractEmoteName(nameObj);
            if (animName != null && animName.equalsIgnoreCase(lowerName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String extractEmoteName(Object nameObj) {
        if (nameObj == null) return null;
        if (nameObj instanceof String) {
            String s = ((String) nameObj).trim();
            if (s.startsWith("{") && s.endsWith("}")) {
                try {
                    JsonElement je = new JsonParser().parse(s);
                    if (je.isJsonObject()) {
                        if (je.getAsJsonObject().has("fallback")) {
                            return je.getAsJsonObject().get("fallback").getAsString();
                        }
                        if (je.getAsJsonObject().has("translate")) {
                            return je.getAsJsonObject().get("translate").getAsString();
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            return s;
        }
        if (nameObj instanceof JsonElement) {
            JsonElement json = (JsonElement) nameObj;
            if (json.isJsonObject()) {
                if (json.getAsJsonObject().has("fallback")) {
                    return json.getAsJsonObject().get("fallback").getAsString();
                }
                if (json.getAsJsonObject().has("translate")) {
                    return json.getAsJsonObject().get("translate").getAsString();
                }
            }
            return json.getAsString();
        }
        return nameObj.toString();
    }
}