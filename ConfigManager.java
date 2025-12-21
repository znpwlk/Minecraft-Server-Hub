import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import com.google.gson.*;
public class ConfigManager {
    private File configFile;
    private String fileType;
    private Map<String, Object> configData;
    public ConfigManager(File configFile) {
        this.configFile = configFile;
        this.configData = new HashMap<>();
        this.fileType = getFileType(configFile);
        if (configFile.exists()) {
            loadConfig();
        }
    }
    private String getFileType(File file) {
        String name = file.getName();
        if (name.endsWith(".properties")) {
            return "properties";
        } else if (name.endsWith(".json")) {
            return "json";
        }
        return "unknown";
    }
    public void loadConfig() {
        if (!configFile.exists()) {
            Logger.warn("Configuration file does not exist, using default configuration: " + configFile.getAbsolutePath(), "ConfigManager");
            configData.clear();
            return;
        }
        try {
            if ("properties".equals(fileType)) {
                loadProperties();
            } else if ("json".equals(fileType)) {
                loadJson();
            }
        } catch (IOException e) {
            Logger.error("Failed to load configuration file: " + configFile.getAbsolutePath() + " - " + e.getMessage(), "ConfigManager");
            configData.clear();
        }
    }
    private void loadProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(configFile);
             InputStreamReader reader = new InputStreamReader(input, java.nio.charset.Charset.defaultCharset())) {
            properties.load(reader);
            properties.forEach((key, value) -> {
                configData.put(key.toString(), value.toString());
            });
        }
    }
    private void loadJson() throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), java.nio.charset.Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        }
        Gson gson = new Gson();
        JsonElement element = gson.fromJson(content.toString(), JsonElement.class);
        if (element.isJsonObject()) {
            JsonObject jsonObject = element.getAsJsonObject();
            jsonObject.entrySet().forEach(entry -> {
                configData.put(entry.getKey(), jsonObject.get(entry.getKey()));
            });
        } else if (element.isJsonArray()) {
            JsonArray jsonArray = element.getAsJsonArray();
            for (int i = 0; i < jsonArray.size(); i++) {
                configData.put(String.valueOf(i), jsonArray.get(i));
            }
        }
    }
    public void saveConfig() {
        if (!configFile.exists()) {
            Logger.warn("Configuration file does not exist, cannot save: " + configFile.getAbsolutePath(), "ConfigManager");
            return;
        }
        try {
            if ("properties".equals(fileType)) {
                saveProperties();
            } else if ("json".equals(fileType)) {
                saveJson();
            }
        } catch (IOException e) {
            Logger.error("Failed to save configuration file: " + configFile.getAbsolutePath() + " - " + e.getMessage(), "ConfigManager");
        }
    }
    private void saveProperties() throws IOException {
        Properties properties = new Properties();
        configData.forEach((key, value) -> {
            properties.put(key, value.toString());
        });
        try (OutputStream output = new FileOutputStream(configFile);
             OutputStreamWriter writer = new OutputStreamWriter(output, java.nio.charset.Charset.defaultCharset())) {
            properties.store(writer, "Server Configuration");
        }
    }
    private void saveJson() throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonString;
        if (configData.values().stream().allMatch(value -> value instanceof Number || value instanceof String || value instanceof Boolean)) {
            jsonString = gson.toJson(configData);
        } else {
            Object firstValue = configData.values().iterator().next();
            if (firstValue instanceof JsonElement) {
                JsonElement element = (JsonElement) firstValue;
                if (element.isJsonObject()) {
                    jsonString = gson.toJson(configData);
                } else {
                    JsonArray jsonArray = new JsonArray();
                    configData.values().forEach(value -> {
                        jsonArray.add((JsonElement) value);
                    });
                    jsonString = gson.toJson(jsonArray);
                }
            } else {
                jsonString = gson.toJson(configData);
            }
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(configFile), java.nio.charset.Charset.defaultCharset())) {
            writer.write(jsonString);
        }
    }
    public Map<String, Object> getConfigData() {
        return configData;
    }
    public void setConfigData(Map<String, Object> configData) {
        this.configData = configData;
    }
    public void setProperty(String key, Object value) {
        configData.put(key, value);
    }
    public Object getProperty(String key) {
        return configData.get(key);
    }
    public String getFileType() {
        return fileType;
    }
    public File getConfigFile() {
        return configFile;
    }
}
