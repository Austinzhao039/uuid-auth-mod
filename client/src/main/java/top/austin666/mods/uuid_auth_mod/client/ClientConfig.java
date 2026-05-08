package top.austin666.mods.uuid_auth_mod.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "uuid-auth-client.json";
    private static ClientConfig INSTANCE;

    public String clientUUID = "your-uuid-here";

    public static ClientConfig getInstance() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    private static void load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path file = configDir.resolve(FILE_NAME);

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                INSTANCE = GSON.fromJson(reader, ClientConfig.class);
                return;
            } catch (IOException e) {
                System.err.println("[UUID Auth Client] 配置文件读取失败，将使用默认值。");
                e.printStackTrace();
            }
        }

        INSTANCE = new ClientConfig();
        save(configDir, file);
    }

    private static void save(Path configDir, Path file) {
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(INSTANCE, writer);
            }
            System.out.println("[UUID Auth Client] 默认配置文件已创建: " + file);
        } catch (IOException e) {
            System.err.println("[UUID Auth Client] 创建配置文件失败。");
            e.printStackTrace();
        }
    }
}