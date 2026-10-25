package com.yang458.customstoryline.manager;

import com.google.gson.*;
import com.yang458.customstoryline.CustomstorylineMod;
import com.yang458.customstoryline.data.PerspectiveConfig;
import com.yang458.customstoryline.data.StorylineData;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StorylineManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, StorylineData> STORYLINES = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> PLAYER_TRIGGERED = new ConcurrentHashMap<>();

    public static File getConfigDir(MinecraftServer server) {
        File configDir = new File("config", "Customstoryline");
        if (!configDir.exists()) {
            if (configDir.mkdirs()) {
                CustomstorylineMod.LOGGER.info("[Customstoryline] Created config directory: {}", configDir.getAbsolutePath());
            } else {
                CustomstorylineMod.LOGGER.error("[Customstoryline] Failed to create config directory: {}", configDir.getAbsolutePath());
            }
        }
        return configDir;
    }

    public static List<String> loadAll(MinecraftServer server) {
        List<String> errors = new ArrayList<>();
        STORYLINES.clear();
        File dir = getConfigDir(server);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));

        if (files == null || files.length == 0) {
            CustomstorylineMod.LOGGER.warn("[Customstoryline] No storyline JSON files found in config directory.");
            return errors;
        }

        int successCount = 0;
        for (File file : files) {
            String namespace = file.getName().substring(0, file.getName().length() - 5);
            try (FileReader reader = new FileReader(file)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) throw new JsonSyntaxException("Root must be a JSON object");

                JsonObject normalizedJson = convertKeysToLowerCase(element.getAsJsonObject());

                if (normalizedJson.has("action") && normalizedJson.get("action").isJsonArray()) {
                    JsonArray actions = normalizedJson.getAsJsonArray("action");
                    for (JsonElement actionEl : actions) {
                        if (actionEl.isJsonObject()) {
                            JsonObject actionObj = actionEl.getAsJsonObject();
                            if (actionObj.has("delay_trigger")) {
                                actionObj.addProperty("delay_trigger", actionObj.get("delay_trigger").getAsString());
                            }
                        }
                    }
                }

                StorylineData data = GSON.fromJson(normalizedJson, StorylineData.class);

                if (data != null) {
                    if (normalizedJson.has("config")) {
                        JsonObject cfg = normalizedJson.getAsJsonObject("config");
                        if (cfg.has("block_perspective")) {
                            JsonElement bp = cfg.get("block_perspective");
                            if (bp.isJsonPrimitive()) {
                                data.config.block_perspective = new PerspectiveConfig(bp.getAsBoolean(), 0, 0);
                            } else if (bp.isJsonArray()) {
                                JsonArray arr = bp.getAsJsonArray();
                                boolean enabled = arr.size() > 0 && arr.get(0).getAsBoolean();
                                float yaw = arr.size() > 1 ? arr.get(1).getAsFloat() : 0;
                                float pitch = arr.size() > 2 ? arr.get(2).getAsFloat() : 0;
                                data.config.block_perspective = new PerspectiveConfig(enabled, yaw, pitch);
                            }
                        }
                    }
                    STORYLINES.put(namespace, data);
                    successCount++;
                    CustomstorylineMod.LOGGER.info("[Customstoryline] Successfully loaded storyline: {}", file.getName());
                }
            } catch (Exception e) {
                String errorMsg = "[" + file.getName() + "]: " + e.getMessage();
                errors.add(errorMsg);
                CustomstorylineMod.LOGGER.error("[Customstoryline] Failed to load storyline {}: {}", file.getName(), e.getMessage());
            }
        }
        CustomstorylineMod.LOGGER.info("[Customstoryline] Reload complete. Loaded: {}, Errors: {}", successCount, errors.size());
        return errors;
    }

    private static JsonObject convertKeysToLowerCase(JsonObject object) {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey().toLowerCase();
            JsonElement element = entry.getValue();
            if (element.isJsonObject()) {
                result.add(key, convertKeysToLowerCase(element.getAsJsonObject()));
            } else if (element.isJsonArray()) {
                JsonArray array = new JsonArray();
                for (JsonElement arrElement : element.getAsJsonArray()) {
                    if (arrElement.isJsonObject()) array.add(convertKeysToLowerCase(arrElement.getAsJsonObject()));
                    else array.add(arrElement);
                }
                result.add(key, array);
            } else {
                result.add(key, element);
            }
        }
        return result;
    }

    public static void saveStoryline(MinecraftServer server, String namespace, StorylineData data) {
        File file = new File(getConfigDir(server), namespace + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
            STORYLINES.put(namespace, data);
            CustomstorylineMod.LOGGER.info("[Customstoryline] Successfully created/saved storyline: {}.json", namespace);
        } catch (Exception e) {
            CustomstorylineMod.LOGGER.error("[Customstoryline] Failed to save storyline file: {}.json", namespace, e);
        }
    }

    public static void removeStoryline(MinecraftServer server, String namespace) {
        File file = new File(getConfigDir(server), namespace + ".json");
        if (file.exists()) {
            if (file.delete()) {
                STORYLINES.remove(namespace);
                CustomstorylineMod.LOGGER.info("[Customstoryline] Successfully deleted storyline file: {}.json", namespace);
            } else {
                CustomstorylineMod.LOGGER.error("[Customstoryline] Failed to delete storyline file: {}.json", namespace);
            }
        }
    }

    public static Map<String, StorylineData> getStorylines() {
        return STORYLINES;
    }

    public static boolean hasPlayerTriggered(UUID uuid, String namespace) {
        return PLAYER_TRIGGERED.getOrDefault(uuid, Collections.emptySet()).contains(namespace);
    }

    public static void setPlayerTriggered(UUID uuid, String namespace, boolean triggered) {
        PLAYER_TRIGGERED.computeIfAbsent(uuid, k -> new HashSet<>());
        if (triggered) PLAYER_TRIGGERED.get(uuid).add(namespace);
        else PLAYER_TRIGGERED.get(uuid).remove(namespace);
    }
}