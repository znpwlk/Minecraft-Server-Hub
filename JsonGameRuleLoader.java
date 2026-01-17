import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class JsonGameRuleLoader {
    private static String indexUrl = null;
    private static final String CACHE_DIR = "MSH/gameRules";
    private static final Map<String, JsonGameRuleSet> CACHE = new LinkedHashMap<String, JsonGameRuleSet>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, JsonGameRuleSet> eldest) {
            return size() > 10;
        }
    };
    private static IndexData indexCache = null;
    private static long indexLoadTime = 0;
    private static final long INDEX_CACHE_DURATION = 30000;
    private static boolean loadedFromRemote = false;
    private static long lastRemoteLoadTime = 0;

    public static void setIndexUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            indexUrl = url.trim();
            clearCache();
        }
    }

    public static String getIndexUrl() {
        return indexUrl;
    }

    private static String getBaseUrl() {
        if (indexUrl == null || indexUrl.isEmpty()) {
            return null;
        }
        int lastSlash = indexUrl.lastIndexOf('/');
        if (lastSlash > 0) {
            return indexUrl.substring(0, lastSlash + 1);
        }
        return null;
    }

    public static class JsonGameRule {
        public String name;
        public String displayName;
        public String description;
        public String type;
        public boolean defaultBoolean;
        public int defaultInt;
        public int introducedVersion;
        public String commandTemplate;

        public boolean isBoolean() {
            return "boolean".equalsIgnoreCase(type);
        }

        public String getDefaultValue() {
            return isBoolean() ? String.valueOf(defaultBoolean) : String.valueOf(defaultInt);
        }
    }

    public static class JsonGameRuleSet {
        public String version;
        public String updateDate;
        public String description;
        public String format;
        public List<JsonGameRule> gameRules;
        public Metadata metadata;

        public static class Metadata {
            public int totalRules;
            public int minVersionSupport;
            public String formatType;
            public String gameVersion;
        }
    }

    public static class IndexData {
        public String indexVersion;
        public String lastUpdated;
        public String description;
        public List<VersionInfo> versions;
        public Map<String, String> versionMapping;
        public String defaultVersion;

        public static class VersionInfo {
            public String version;
            public String file;
            public String displayName;
            public String format;
            public String description;
        }
    }

    private static String fetchFromUrl(String urlStr) {
        try {
            URI uri = new URI(urlStr);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setUseCaches(true);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    return content.toString();
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    private static String readFromFile(String path) {
        try {
            Path filePath = Paths.get(path);
            if (Files.exists(filePath)) {
                return new String(Files.readAllBytes(filePath), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
        }
        return null;
    }

    public static IndexData loadIndex() {
        long now = System.currentTimeMillis();
        if (indexCache != null && (now - indexLoadTime) < INDEX_CACHE_DURATION) {
            return indexCache;
        }

        IndexData index = null;

        if (indexUrl != null) {
            String content = fetchFromUrl(indexUrl);
            if (content != null) {
                index = parseIndexJson(content);
            }
        }

        if (index != null) {
            indexCache = index;
            indexLoadTime = now;
        }

        return index;
    }

    private static IndexData parseIndexJson(String json) {
        IndexData index = new IndexData();
        index.versionMapping = new HashMap<>();

        try {
            int defaultStart = json.indexOf("\"defaultVersion\"");
            if (defaultStart != -1) {
                int colon = json.indexOf(":", defaultStart);
                int quoteStart = json.indexOf("\"", colon);
                int quoteEnd = json.indexOf("\"", quoteStart + 1);
                if (quoteStart != -1 && quoteEnd != -1) {
                    index.defaultVersion = json.substring(quoteStart + 1, quoteEnd);
                }
            }

            int mappingStart = json.indexOf("\"versionMapping\"");
            if (mappingStart != -1) {
                int braceStart = json.indexOf("{", mappingStart);
                int braceEnd = json.indexOf("}", braceStart);
                if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
                    String mappingJson = json.substring(braceStart + 1, braceEnd);
                    parseVersionMapping(index, mappingJson);
                }
            }
        } catch (Exception e) {
            return null;
        }

        return index;
    }

    private static void parseVersionMapping(IndexData index, String mappingJson) {
        int start = 0;
        while (start < mappingJson.length()) {
            int keyStart = mappingJson.indexOf("\"", start);
            if (keyStart == -1) break;
            int keyEnd = mappingJson.indexOf("\"", keyStart + 1);
            if (keyEnd == -1) break;
            String key = mappingJson.substring(keyStart + 1, keyEnd);

            int colon = mappingJson.indexOf(":", keyEnd);
            if (colon == -1) break;
            int valueStart = mappingJson.indexOf("\"", colon);
            int valueEnd = mappingJson.indexOf("\"", valueStart + 1);
            if (valueStart == -1 || valueEnd == -1) break;
            String value = mappingJson.substring(valueStart + 1, valueEnd);

            index.versionMapping.put(key, value);
            start = valueEnd + 1;
        }
    }

    public static JsonGameRuleSet loadFromJson(String version) {
        if (CACHE.containsKey(version)) {
            return CACHE.get(version);
        }

        JsonGameRuleSet ruleSet = null;
        String fileName = null;
        boolean fromRemote = false;

        IndexData index = loadIndex();
        if (index != null && index.versionMapping != null) {
            String mappedVersion = index.versionMapping.get(version);
            if (mappedVersion != null) {
                fileName = mappedVersion;
            }
        }

        if (fileName == null) {
            fileName = version;
        }

        if (!fileName.toLowerCase().endsWith(".json")) {
            fileName = fileName + ".json";
        }

        String baseUrl = getBaseUrl();
        if (baseUrl != null) {
            String remoteUrl = baseUrl + "d/" + fileName;
            String content = fetchFromUrl(remoteUrl);
            if (content != null) {
                ruleSet = parseJson(content, version);
                if (ruleSet != null) {
                    saveToCache(fileName, content);
                    fromRemote = true;
                }
            }
        }

        if (ruleSet == null) {
            Path cachePath = Paths.get(CACHE_DIR, fileName);
            String content = readFromFile(cachePath.toString());
            if (content != null) {
                ruleSet = parseJson(content, version);
            }
        }

        if (ruleSet != null && ruleSet.gameRules != null && !ruleSet.gameRules.isEmpty()) {
            CACHE.put(version, ruleSet);
            if (fromRemote) {
                loadedFromRemote = true;
                lastRemoteLoadTime = System.currentTimeMillis();
            }
        }

        return ruleSet;
    }

    private static void saveToCache(String fileName, String content) {
        try {
            Path cacheDirPath = Paths.get(CACHE_DIR);
            if (!Files.exists(cacheDirPath)) {
                Files.createDirectories(cacheDirPath);
            }
            Path cachePath = cacheDirPath.resolve(fileName);
            Files.write(cachePath, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
        }
    }

    public static boolean isLoadedFromRemote() {
        return loadedFromRemote;
    }

    public static long getLastRemoteLoadTime() {
        return lastRemoteLoadTime;
    }

    public static void refreshFromRemote() {
        clearCache();
        loadedFromRemote = false;
        lastRemoteLoadTime = 0;
        loadFromJson("1.21.11");
    }

    private static JsonGameRuleSet parseJson(String json, String version) {
        JsonGameRuleSet ruleSet = new JsonGameRuleSet();
        ruleSet.version = version;
        ruleSet.gameRules = new ArrayList<>();

        try {
            int rulesStart = json.indexOf("\"gameRules\"");
            if (rulesStart != -1) {
                int braceStart = json.indexOf("[", rulesStart);
                int braceEnd = json.lastIndexOf("]");
                if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
                    String rulesArray = json.substring(braceStart + 1, braceEnd);
                    parseRulesArray(ruleSet, rulesArray);
                }
            }
        } catch (Exception e) {
            return null;
        }

        return ruleSet;
    }

    private static void parseRulesArray(JsonGameRuleSet ruleSet, String rulesArray) {
        int depth = 0;
        int start = -1;

        for (int i = 0; i < rulesArray.length(); i++) {
            char c = rulesArray.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    String ruleJson = rulesArray.substring(start, i + 1);
                    JsonGameRule rule = parseRule(ruleJson);
                    if (rule != null) {
                        ruleSet.gameRules.add(rule);
                    }
                    start = -1;
                }
            }
        }
    }

    private static JsonGameRule parseRule(String ruleJson) {
        JsonGameRule rule = new JsonGameRule();

        extractStringValue(ruleJson, "\"name\"", rule, "name");
        extractStringValue(ruleJson, "\"displayName\"", rule, "displayName");
        extractStringValue(ruleJson, "\"description\"", rule, "description");
        extractStringValue(ruleJson, "\"type\"", rule, "type");
        extractStringValue(ruleJson, "\"commandTemplate\"", rule, "commandTemplate");

        if (rule.name == null || rule.type == null) {
            return null;
        }

        int introStart = ruleJson.indexOf("\"introducedVersion\"");
        if (introStart != -1) {
            int colon = ruleJson.indexOf(":", introStart);
            int valueEnd = findValueEnd(ruleJson, colon);
            String value = ruleJson.substring(colon + 1, valueEnd).trim();
            try {
                rule.introducedVersion = Integer.parseInt(value.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                rule.introducedVersion = 21;
            }
        } else {
            rule.introducedVersion = 21;
        }

        if ("boolean".equalsIgnoreCase(rule.type)) {
            int defaultBoolStart = ruleJson.indexOf("\"defaultValue\"");
            if (defaultBoolStart != -1) {
                int colon = ruleJson.indexOf(":", defaultBoolStart);
                int valueEnd = findValueEnd(ruleJson, colon);
                String value = ruleJson.substring(colon + 1, valueEnd).trim().toLowerCase();
                rule.defaultBoolean = value.contains("true");
            } else {
                rule.defaultBoolean = true;
            }
            rule.defaultInt = 0;
        } else {
            int defaultIntStart = ruleJson.indexOf("\"defaultValue\"");
            if (defaultIntStart != -1) {
                int colon = ruleJson.indexOf(":", defaultIntStart);
                int valueEnd = findValueEnd(ruleJson, colon);
                String value = ruleJson.substring(colon + 1, valueEnd).trim();
                try {
                    rule.defaultInt = Integer.parseInt(value.replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    rule.defaultInt = 0;
                }
            } else {
                rule.defaultInt = 0;
            }
            rule.defaultBoolean = true;
        }

        return rule;
    }

    private static void extractStringValue(String json, String key, JsonGameRule rule, String field) {
        int keyStart = json.indexOf(key);
        if (keyStart != -1) {
            int colon = json.indexOf(":", keyStart);
            int quoteStart = json.indexOf("\"", colon);
            int quoteEnd = json.indexOf("\"", quoteStart + 1);
            if (quoteStart != -1 && quoteEnd != -1 && quoteEnd > quoteStart) {
                String value = json.substring(quoteStart + 1, quoteEnd);
                switch (field) {
                    case "name": rule.name = value; break;
                    case "displayName": rule.displayName = value; break;
                    case "description": rule.description = value; break;
                    case "type": rule.type = value; break;
                    case "commandTemplate": rule.commandTemplate = value; break;
                }
            }
        }
    }

    private static int findValueEnd(String json, int start) {
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ',' || c == '}' || c == '\n') break;
            i++;
        }
        return i;
    }

    public static void clearCache() {
        CACHE.clear();
        indexCache = null;
        indexLoadTime = 0;
        loadedFromRemote = false;
        lastRemoteLoadTime = 0;
    }

    public static String[] getAvailableVersions() {
        IndexData index = loadIndex();
        if (index != null && index.versionMapping != null) {
            Collection<String> versionValues = index.versionMapping.values();
            List<String> sorted = new ArrayList<>(versionValues);
            Collections.sort(sorted, (a, b) -> {
                try {
                    double va = Double.parseDouble(a.replace(".", "").substring(0, Math.min(4, a.length())));
                    double vb = Double.parseDouble(b.replace(".", "").substring(0, Math.min(4, b.length())));
                    return Double.compare(va, vb);
                } catch (Exception e) {
                    return a.compareTo(b);
                }
            });
            return sorted.toArray(new String[0]);
        }

        return new String[0];
    }

    public static String findMatchingJsonVersion(String mcVersion) {
        if (mcVersion == null || mcVersion.isEmpty()) {
            return null;
        }

        IndexData index = loadIndex();
        if (index != null && index.versionMapping != null) {
            String mapped = index.versionMapping.get(mcVersion.trim());
            if (mapped != null) {
                return mapped;
            }
        }

        String[] availableVersions = getAvailableVersions();
        if (availableVersions.length == 0) {
            return null;
        }

        Arrays.sort(availableVersions, (a, b) -> {
            int cmp = compareVersions(a, b);
            return cmp;
        });

        int serverMajor = getMajorVersion(mcVersion);
        int serverMinor = getMinorVersion(mcVersion);

        for (String jsonVersion : availableVersions) {
            int jsonMajor = getMajorVersion(jsonVersion);
            int jsonMinor = getMinorVersion(jsonVersion);

            if (jsonMajor == serverMajor && jsonMinor == serverMinor) {
                return jsonVersion;
            }
        }

        for (String jsonVersion : availableVersions) {
            int jsonMajor = getMajorVersion(jsonVersion);
            int jsonMinor = getMinorVersion(jsonVersion);

            if (jsonMajor < serverMajor || (jsonMajor == serverMajor && jsonMinor < serverMinor)) {
                return jsonVersion;
            }
        }

        return availableVersions[0];
    }

    private static int getMajorVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            return Integer.parseInt(parts[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int getMinorVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            if (parts.length >= 2) {
                return Integer.parseInt(parts[1]);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int getPatchVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            if (parts.length >= 3) {
                return Integer.parseInt(parts[2]);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int compareVersions(String a, String b) {
        int majorA = getMajorVersion(a);
        int majorB = getMajorVersion(b);
        if (majorA != majorB) return Integer.compare(majorA, majorB);

        int minorA = getMinorVersion(a);
        int minorB = getMinorVersion(b);
        if (minorA != minorB) return Integer.compare(minorA, minorB);

        int patchA = getPatchVersion(a);
        int patchB = getPatchVersion(b);
        return Integer.compare(patchA, patchB);
    }

    public static String getDefaultVersion() {
        IndexData index = loadIndex();
        if (index != null && index.defaultVersion != null) {
            return index.defaultVersion;
        }
        return "1.21.11";
    }

    public static boolean isRemoteAvailable() {
        if (indexUrl == null) {
            return false;
        }
        String content = fetchFromUrl(indexUrl);
        return content != null && !content.isEmpty();
    }

    public static boolean indexExists() {
        if (indexUrl != null) {
            String content = fetchFromUrl(indexUrl);
            return content != null && !content.isEmpty();
        }
        return false;
    }
}
