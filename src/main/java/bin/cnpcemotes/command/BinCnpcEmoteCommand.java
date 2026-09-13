package bin.cnpcemotes.command;

import bin.cnpcemotes.CnpcEmoteMod;
import bin.cnpcemotes.animation.EmoteAnimation;
import bin.cnpcemotes.state.NpcEmoteState;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import io.github.kosmx.emotes.api.events.server.ServerEmoteAPI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

public final class BinCnpcEmoteCommand {
    private static final String NPC_ARG = "npc";
    private static final String EMOTE_ARG = "emote";
    private static final Map<String, UUID> NAME_TO_UUID = new ConcurrentHashMap<>();
    private static final Map<String, Integer> EMOTE_ANIM_CACHE = new ConcurrentHashMap<>();

    private BinCnpcEmoteCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("bincnpcemote")
                .requires(source -> source.hasPermission(0))
                .then(Commands.literal("play")
                        .then(Commands.argument(NPC_ARG, StringArgumentType.string())
                                .suggests(BinCnpcEmoteCommand::suggestNpcs)
                                .then(Commands.argument(EMOTE_ARG, StringArgumentType.greedyString())
                                        .suggests(BinCnpcEmoteCommand::suggestEmotes)
                                        .executes(BinCnpcEmoteCommand::runPlay)))
                )
                .then(Commands.literal("stop")
                        .then(Commands.argument(NPC_ARG, StringArgumentType.string())
                                .suggests(BinCnpcEmoteCommand::suggestNpcs)
                                .executes(BinCnpcEmoteCommand::runStop))
                        .executes(context -> runStopAll(context, null))
                );
    }

    private static int runPlay(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String npcNameInput = StringArgumentType.getString(context, NPC_ARG);
        String emoteInput = StringArgumentType.getString(context, EMOTE_ARG);
        String emoteName = extractEmoteName(emoteInput);
        if (emoteName == null || emoteName.isBlank()) {
            source.sendFailure(Component.translatable("cnpcemotes.error.invalid_emote_name"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        if (level == null) {
            source.sendFailure(Component.translatable("cnpcemotes.error.no_world"));
            return 0;
        }

        long gameTime = level.getGameTime();

        HashMap<UUID, KeyframeAnimation> loadedEmotes = ServerEmoteAPI.getLoadedEmotes();
        UUID emoteId = resolveEmoteId(emoteName, loadedEmotes);

        if (emoteId == null) {
            source.sendFailure(Component.translatable("cnpcemotes.error.unknown_emote", emoteName));
            return 0;
        }

        KeyframeAnimation animation = loadedEmotes.get(emoteId);
        if (animation == null) {
            source.sendFailure(Component.translatable("cnpcemotes.error.animation_not_loaded", emoteName));
            return 0;
        }

        List<EntityNPCInterface> npcs = findNpcsByName(level, npcNameInput);
        if (npcs.isEmpty()) {
            source.sendFailure(Component.translatable("cnpcemotes.error.no_npc_found", npcNameInput));
            return 0;
        }

        int animId = EMOTE_ANIM_CACHE.getOrDefault(emoteName, -1);
        if (animId < 0) {
            animId = EmoteAnimation.registerEmote(emoteName);
            EMOTE_ANIM_CACHE.put(emoteName, animId);
        }

        for (EntityNPCInterface npc : npcs) {
            npc.currentAnimation = animId;
            npc.animationStart = (int) level.getGameTime();
            NpcEmoteState state = NpcEmoteState.getOrCreate(npc.getUUID());
            state.play(emoteId, gameTime);
            NpcEmoteState.trackEntityId(npc.getUUID(), npc.getId());
            CnpcEmoteMod.broadcastEmoteStart(level, npc.getUUID(), emoteId, gameTime, npc.getId());
        }

        final String finalEmoteName = emoteName;
        final int finalCount = npcs.size();
        source.sendSuccess(() -> Component.translatable("cnpcemotes.command.success", finalCount, finalEmoteName), true);
        return npcs.size();
    }

    private static int runStop(CommandContext<CommandSourceStack> context) {
        String npcNameInput = StringArgumentType.getString(context, NPC_ARG);
        return runStopAll(context, npcNameInput);
    }

    private static int runStopAll(CommandContext<CommandSourceStack> context, String nameFilter) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        if (level == null) {
            source.sendFailure(Component.translatable("cnpcemotes.error.no_world"));
            return 0;
        }

        List<EntityNPCInterface> npcs = findNpcsByName(level, nameFilter);
        if (npcs.isEmpty()) {
            source.sendFailure(Component.translatable("cnpcemotes.error.no_npc_found", nameFilter != null ? nameFilter : "*"));
            return 0;
        }

        for (EntityNPCInterface npc : npcs) {
            CnpcEmoteMod.broadcastEmoteStop(level, npc.getUUID());
            NpcEmoteState.remove(npc.getUUID());
            npc.currentAnimation = 0;
            npc.animationStart = 0;
        }

        source.sendSuccess(() -> Component.translatable("cnpcemotes.command.stopped", npcs.size()), true);
        return npcs.size();
    }

    private static List<EntityNPCInterface> findNpcsByName(ServerLevel level, String nameInput) {
        List<EntityNPCInterface> result = new ArrayList<>();
        if (nameInput == null || nameInput.isEmpty()) {
            AABB searchBox = new AABB(-1000, -1000, -1000, 1000, 1000, 1000);
            for (Entity entity : level.getEntities(null, searchBox)) {
                if (entity instanceof EntityNPCInterface npc) {
                    result.add(npc);
                }
            }
            return result;
        }
        if (nameInput.startsWith("@")) {
            AABB searchBox = new AABB(-1000, -1000, -1000, 1000, 1000, 1000);
            for (Entity entity : level.getEntities(null, searchBox)) {
                if (entity instanceof EntityNPCInterface) {
                    result.add((EntityNPCInterface) entity);
                }
            }
        }
        if (result.isEmpty()) {
            AABB searchBox = new AABB(-1000, -1000, -1000, 1000, 1000, 1000);
            for (Entity entity : level.getEntities(null, searchBox)) {
                if (entity instanceof EntityNPCInterface npc) {
                    String npcName = npc.display.getName();
                    if (npcName.equalsIgnoreCase(nameInput)) {
                        result.add(npc);
                    }
                }
            }
        }
        return result;
    }

    private static UUID resolveEmoteId(String input, Map<UUID, KeyframeAnimation> loaded) {
        try {
            return UUID.fromString(input);
        } catch (Exception exception) {
            Map.Entry<UUID, KeyframeAnimation> entry;
            UUID cached = NAME_TO_UUID.get(input.toLowerCase(Locale.ROOT));
            if (cached != null) {
                return cached;
            }
            Iterator<Map.Entry<UUID, KeyframeAnimation>> iterator = loaded.entrySet().iterator();
            do {
                if (!iterator.hasNext()) return null;
            } while (!matchesName((entry = iterator.next()).getValue(), input));
            return entry.getKey();
        }
    }

    private static String sanitizeName(String value) {
        if (value == null) return null;
        String stripped = value.replaceAll("\\u00a7.", "");
        if (stripped == null) return null;
        StringBuilder safe = new StringBuilder(stripped.length());
        boolean previousWasSpace = false;
        int i = 0;
        while (i < stripped.length()) {
            char character = stripped.charAt(i);
            if (Character.isWhitespace(character)) {
                if (previousWasSpace || safe.length() == 0) {
                    i++;
                    continue;
                }
                safe.append(' ');
                previousWasSpace = true;
            } else {
                safe.append(character);
                previousWasSpace = false;
            }
            i++;
        }
        return safe.toString().trim();
    }

    private static boolean matchesName(KeyframeAnimation animation, String input) {
        try {
            Object nameObj = animation.extraData.get("name");
            String name = extractEmoteName(nameObj);
            return name != null && name.equalsIgnoreCase(input);
        } catch (Exception e) {
            return false;
        }
    }

    private static String extractEmoteName(Object nameObj) {
        if (nameObj == null) return null;
        if (nameObj instanceof String) {
            String s = (String) nameObj;
            s = s.trim();
            if (s.startsWith("{") && s.endsWith("}")) {
                try {
                    com.google.gson.JsonElement je = new com.google.gson.JsonParser().parse(s);
                    if (je.isJsonObject()) {
                        com.google.gson.JsonObject obj = je.getAsJsonObject();
                        if (obj.has("fallback") && !obj.get("fallback").getAsString().isEmpty()) {
                            return sanitizeName(obj.get("fallback").getAsString());
                        }
                        if (obj.has("translate") && !obj.get("translate").getAsString().isEmpty()) {
                            return sanitizeName(obj.get("translate").getAsString());
                        }
                    }
                } catch (Exception ignored) {}
            }
            return sanitizeName(s);
        }
        if (nameObj instanceof com.google.gson.JsonElement) {
            com.google.gson.JsonElement json = (com.google.gson.JsonElement) nameObj;
            if (json.isJsonObject()) {
                com.google.gson.JsonObject obj = json.getAsJsonObject();
                if (obj.has("fallback") && !obj.get("fallback").getAsString().isEmpty()) {
                    return sanitizeName(obj.get("fallback").getAsString());
                }
                if (obj.has("translate") && !obj.get("translate").getAsString().isEmpty()) {
                    return sanitizeName(obj.get("translate").getAsString());
                }
            }
            return sanitizeName(json.toString());
        }
        // Handle emotecraft Text interface
        String className = nameObj.getClass().getName();
        if (className.contains("Text") || className.contains("text")) {
            try {
                java.lang.reflect.Method getStringMethod = nameObj.getClass().getMethod("getString");
                Object result = getStringMethod.invoke(nameObj);
                if (result instanceof String) return sanitizeName((String) result);
            } catch (Exception ignored) {}
            try {
                java.lang.reflect.Method toJsonTreeMethod = nameObj.getClass().getMethod("toJsonTree");
                Object jsonElement = toJsonTreeMethod.invoke(nameObj);
                if (jsonElement instanceof com.google.gson.JsonElement) {
                    return extractEmoteName((com.google.gson.JsonElement) jsonElement);
                }
            } catch (Exception ignored) {}
        }
        return sanitizeName(nameObj.toString());
    }

    private static CompletableFuture<Suggestions> suggestNpcs(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>();
        ServerLevel level = context.getSource().getLevel();
        if (level != null) {
            suggestions.add("@e[type=noppes.npcs.entity.EntityNPCInterface,r=10]");
            suggestions.add("@e[type=noppes.npcs.entity.EntityNPCInterface]");
            net.minecraft.world.phys.Vec3 pos = context.getSource().getPosition();
            AABB searchBox = new AABB(pos.x - 10, pos.y - 10, pos.z - 10, pos.x + 10, pos.y + 10, pos.z + 10);
            for (Entity entity : level.getEntities(null, searchBox)) {
                if (entity instanceof EntityNPCInterface npc) {
                    String name = npc.display.getName();
                    if (name != null && !name.isBlank()) {
                        suggestions.add("\"" + name + "\"");
                    }
                }
            }
        }
        return SharedSuggestionProvider.suggest(suggestions.toArray(new String[0]), builder);
    }

    private static CompletableFuture<Suggestions> suggestEmotes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        NAME_TO_UUID.clear();
        List<String> suggestions = new ArrayList<>();
        HashMap<UUID, KeyframeAnimation> loaded = ServerEmoteAPI.getLoadedEmotes();
        Iterator<Map.Entry<UUID, KeyframeAnimation>> iterator = loaded.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, KeyframeAnimation> entry = iterator.next();
            try {
                String name = extractEmoteName(entry.getValue().extraData.get("name"));
                if (name == null || name.isBlank()) continue;
                suggestions.add(name);
                NAME_TO_UUID.put(name.toLowerCase(Locale.ROOT), entry.getKey());
            } catch (Exception exception) {
            }
        }
        return SharedSuggestionProvider.suggest(suggestions.toArray(new String[0]), builder);
    }
}
