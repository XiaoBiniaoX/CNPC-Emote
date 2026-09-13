package bin.cnpcemotes;

import bin.cnpcemotes.state.NpcEmoteState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Mod.EventBusSubscriber(modid = "cnpcemotes", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EmoteWorldInitializer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String EMOTE_DIR_NAME = "emote";
    private static final String DEFAULT_EMOTE_FILE = "assets/cnpcemotes/emotes/default/wave.emote";

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        initEmoteDirectory(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            initEmoteDirectoryForLevel(level);
        }
    }

    private static void initEmoteDirectory(net.minecraft.server.MinecraftServer server) {
        try {
            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            initEmoteFolder(worldDir);
        } catch (IOException e) {
            LOGGER.error("Failed to initialize emote directory", e);
        }
    }

    private static void initEmoteDirectoryForLevel(ServerLevel level) {
        try {
            Path worldDir = level.getServer().getWorldPath(LevelResource.ROOT);
            initEmoteFolder(worldDir);
        } catch (IOException e) {
            LOGGER.error("Failed to initialize emote directory for level", e);
        }
    }

    private static void initEmoteFolder(Path worldDir) throws IOException {
        Path cnpcDir = worldDir.resolve("customnpcs");
        Path emoteDir = cnpcDir.resolve(EMOTE_DIR_NAME);

        Files.createDirectories(emoteDir);

        if (isEmpty(emoteDir)) {
            copyDefaultEmotes(emoteDir);
        }
    }

    private static boolean isEmpty(Path dir) {
        try {
            return Files.list(dir).findFirst().isEmpty();
        } catch (IOException e) {
            return true;
        }
    }

    private static void copyDefaultEmotes(Path emoteDir) {
        try {
            InputStream defaultEmote = CnpcEmoteMod.class.getClassLoader().getResourceAsStream(DEFAULT_EMOTE_FILE);
            if (defaultEmote != null) {
                Path targetFile = emoteDir.resolve("wave.emote");
                Files.copy(defaultEmote, targetFile, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Copied default emote to {}", targetFile);
                defaultEmote.close();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to copy default emotes", e);
        }
    }
}
