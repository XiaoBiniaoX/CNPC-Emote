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

import java.util.Locale;
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
     * 根据 emote 名称查找其 UUID。
     * 1) 优先按文件名匹配（存档 customnpcs/emote/ 下加载的文件名，去扩展名，忽略大小写）。
     * 2) 找不到时回退按动画内部 name 字段匹配（兼容 JSON 组件格式）。
     */
    private UUID findEmoteByName(String emoteName) {
        String lowerName = emoteName.toLowerCase(Locale.ROOT);
        // 1) 按文件名匹配
        UUID byFile = CnpcEmoteMod.EMOTE_BY_NAME.get(lowerName);
        if (byFile != null) return byFile;
        // 2) 回退：按动画内部 name 匹配
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
        String result;
        if (nameObj instanceof JsonElement) {
            String fromJson = extractNameFromJson((JsonElement) nameObj);
            result = fromJson != null ? fromJson : nameObj.toString();
        } else {
            String s = nameObj.toString().trim();
            if (s.startsWith("{") || s.startsWith("[")) {
                try {
                    String fromJson = extractNameFromJson(new JsonParser().parse(s));
                    result = fromJson != null ? fromJson : s;
                } catch (Exception ignored) {
                    result = s;
                }
            } else {
                result = s;
            }
        }
        // 剥离 Minecraft 颜色代码（§x），对齐命令 Tab 补全的 sanitizeName 行为
        return result.replaceAll("\\u00a7.", "").trim();
    }

    /**
     * 从 Minecraft TextComponent 的 JSON（对象或数组）里提取可读文本。
     * 优先取 fallback（实际显示名），其次取 text，最后取 translate key。
     */
    private String extractNameFromJson(JsonElement je) {
        if (je.isJsonObject()) {
            com.google.gson.JsonObject o = je.getAsJsonObject();
            if (o.has("fallback") && o.get("fallback").isJsonPrimitive()) {
                String fb = o.get("fallback").getAsString();
                if (!fb.isEmpty()) return fb;
            }
            if (o.has("text") && o.get("text").isJsonPrimitive()) {
                String t = o.get("text").getAsString();
                if (!t.isEmpty()) return t;
            }
            if (o.has("translate") && o.get("translate").isJsonPrimitive()) {
                return o.get("translate").getAsString();
            }
        } else if (je.isJsonArray()) {
            for (JsonElement e : je.getAsJsonArray()) {
                String r = extractNameFromJson(e);
                if (r != null) return r;
            }
        }
        return null;
    }
}