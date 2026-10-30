package bin.cnpcemotes;

import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class EmoteWorldInitializer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String EMOTE_DIR_NAME = "emote";
    private static final String DEFAULT_EMOTE_FILE = "assets/cnpcemotes/emotes/default/wave.emote";

    static void initEmoteDirectory(net.minecraft.server.MinecraftServer server) {
        try {
            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            initEmoteFolder(worldDir);
        } catch (IOException e) {
            LOGGER.error("Failed to initialize emote directory", e);
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
        try (var files = Files.list(dir)) {
            return files.findFirst().isEmpty();
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
