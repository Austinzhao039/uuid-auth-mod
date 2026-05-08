package top.austin666.mods.uuid_auth_mod.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "uuid-auth-server.json";
    private static ServerConfig INSTANCE;

    public List<String> serverUUIDs = new ArrayList<>();

    public static ServerConfig getInstance() {
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
                Type configType = new TypeToken<ServerConfig>(){}.getType();
                INSTANCE = GSON.fromJson(reader, configType);
                return;
            } catch (IOException e) {
                System.err.println("[UUID Auth Server] 配置文件读取失败，使用默认值。");
                e.printStackTrace();
            }
        }

        INSTANCE = new ServerConfig();
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
            System.out.println("[UUID Auth Server] 默认配置文件已创建: " + file);
        } catch (IOException e) {
            System.err.println("[UUID Auth Server] 创建配置文件失败。");
            e.printStackTrace();
        }
    }
}