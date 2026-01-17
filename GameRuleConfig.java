import java.util.*;

public class GameRuleConfig {
    public enum MCVersion {
        V1_20("1.20.x", 20, false),
        V1_21_10("1.21.10及以前", 21, false),
        V1_21_11("1.21.11+", 21, true);

        private final String displayName;
        private final int majorVersion;
        private final boolean useUnderscore;

        MCVersion(String displayName, int majorVersion, boolean useUnderscore) {
            this.displayName = displayName;
            this.majorVersion = majorVersion;
            this.useUnderscore = useUnderscore;
        }

        public String getDisplayName() { return displayName; }
        public int getMajorVersion() { return majorVersion; }
        public boolean useUnderscoreFormat() { return useUnderscore; }

        @Override
        public String toString() { return displayName; }

        public static MCVersion fromMajorVersion(int majorVersion) {
            for (MCVersion v : values()) {
                if (v.majorVersion == majorVersion) return v;
            }
            return V1_21_10;
        }

        public static MCVersion fromVersionString(String versionStr) {
            if (versionStr == null) return V1_21_10;
            try {
                String[] parts = versionStr.split("\\.");
                int major = Integer.parseInt(parts[0]);
                int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

                if (major == 1 && minor == 21) {
                    if (patch >= 11) return V1_21_11;
                    return V1_21_10;
                } else if (major == 1 && minor == 20) {
                    return V1_20;
                }
            } catch (Exception e) {}
            return V1_21_10;
        }

        public static String toJsonVersion(MCVersion version) {
            if (version == V1_21_11) return "1.21.11";
            if (version == V1_21_10) return "1.21.10";
            if (version == V1_20) return "1.20";
            return "1.21.10";
        }
    }

    private static final Map<MCVersion, List<GameRule>> VERSION_RULES = new LinkedHashMap<>();
    private static final Map<String, JsonGameRuleLoader.JsonGameRuleSet> JSON_CONFIG_CACHE = new LinkedHashMap<>();
    private static MCVersion currentVersion = MCVersion.V1_21_10;
    private static boolean useJsonConfig = true;
    private static String detectedMcVersion = null;
    private static String activeJsonVersion = null;

    static {
        initializeHardcodedRules();
    }

    private static void initializeHardcodedRules() {
        List<GameRule> rules1_20 = Arrays.asList(
            new GameRule("keepInventory", "死亡不掉落", "是否保留玩家死亡后背包物品", true, 20),
            new GameRule("mobGriefing", "生物破坏", "生物是否能破坏环境和放置方块", true, 20),
            new GameRule("doMobSpawning", "生物生成", "是否生成敌对生物", true, 20),
            new GameRule("doTileDrops", "方块掉落", "方块被破坏时是否掉落物品", true, 20),
            new GameRule("doEntityDrops", "实体掉落", "生物死亡时是否掉落物品", true, 20),
            new GameRule("commandBlockOutput", "命令方块输出", "命令方块执行时是否输出日志", true, 20),
            new GameRule("logAdminCommands", "管理命令日志", "是否记录管理员执行的命令", true, 20),
            new GameRule("broadcastCommandDebug", "调试命令广播", "调试命令是否广播给所有玩家", false, 20),
            new GameRule("showDeathMessages", "死亡消息", "玩家死亡时是否显示消息", true, 20),
            new GameRule("naturalRegeneration", "自然恢复", "玩家是否自然恢复生命值", true, 20),
            new GameRule("doWeatherCycle", "天气循环", "天气是否会变化", true, 20),
            new GameRule("doDaylightCycle", "日夜循环", "时间是否会流逝", true, 20),
            new GameRule("disableElytraMovementCheck", "鞘翅检查", "是否禁用鞘翅碰撞检查", false, 20),
            new GameRule("spawnRadius", "生成半径", "玩家重生半径(仅创造模式)", 1, 20),
            new GameRule("maxEntityCramming", "实体挤压", "生物最大挤压数", 24, 20),
            new GameRule("fireSpreads", "火焰蔓延", "火焰是否会蔓延", true, 20),
            new GameRule("tntExplodes", "TNT爆炸", "TNT是否会爆炸", true, 20),
            new GameRule("mobExplosionDropBlock", "生物爆炸掉落", "生物爆炸时是否掉落方块", true, 20),
            new GameRule("zombieAggressiveTowardsVillagers", "僵尸攻击村民", "僵尸是否主动攻击村民", true, 20),
            new GameRule("doWardenSpawning", "监守者生成", "是否生成监守者", true, 19),
            new GameRule("announceAdvancements", "进度公告", "是否在聊天框显示玩家进度", true, 12),
            new GameRule("blockExplosionDropDecay", "方块爆炸衰减", "方块爆炸时是否有概率不掉落", true, 14),
            new GameRule("playersSleepingPercentage", "玩家睡觉比例", "跳过夜晚所需睡觉玩家百分比", 100, 15),
            new GameRule("universalAnger", "全局愤怒", "流浪生物是否无差别攻击", false, 16)
        );

        List<GameRule> rules1_21_10 = new ArrayList<>(rules1_20);
        rules1_21_10.add(new GameRule("endCrystalSpawn", "末影水晶生成", "是否在末地生成末影水晶", true, 21));

        List<GameRule> rules1_21_11 = Arrays.asList(
            new GameRule("keep_inventory", "死亡不掉落", "是否保留玩家死亡后背包物品", true, 20),
            new GameRule("mob_griefing", "生物破坏", "生物是否能破坏环境和放置方块", true, 20),
            new GameRule("spawn_mobs", "生物生成", "是否生成敌对生物", true, 20),
            new GameRule("block_drops", "方块掉落", "方块被破坏时是否掉落物品", true, 20),
            new GameRule("entity_drops", "实体掉落", "生物死亡时是否掉落物品", true, 20),
            new GameRule("command_blocks_work", "命令方块输出", "命令方块执行时是否输出日志", true, 20),
            new GameRule("log_admin_commands", "管理命令日志", "是否记录管理员执行的命令", true, 20),
            new GameRule("reduced_debug_info", "调试命令广播", "调试命令是否广播给所有玩家", false, 20),
            new GameRule("show_death_messages", "死亡消息", "玩家死亡时是否显示消息", true, 20),
            new GameRule("natural_health_regeneration", "自然恢复", "玩家是否自然恢复生命值", true, 20),
            new GameRule("advance_weather", "天气循环", "天气是否会变化", true, 20),
            new GameRule("do_daylight_cycle", "日夜循环", "时间是否会流逝", true, 20),
            new GameRule("elytra_movement_check", "鞘翅检查", "是否禁用鞘翅碰撞检查", false, 20),
            new GameRule("respawn_radius", "生成半径", "玩家重生半径(仅创造模式)", 1, 20),
            new GameRule("max_entity_cramming", "实体挤压", "生物最大挤压数", 24, 20),
            new GameRule("fire_spreads", "火焰蔓延", "火焰是否会蔓延", true, 20),
            new GameRule("tnt_explodes", "TNT爆炸", "TNT是否会爆炸", true, 20),
            new GameRule("mob_explosion_drop_decay", "生物爆炸衰减", "生物爆炸时是否有概率不掉落", true, 20),
            new GameRule("forgive_dead_players", "僵尸攻击村民", "僵尸是否主动攻击村民", true, 20),
            new GameRule("spawn_wardens", "监守者生成", "是否生成监守者", true, 19),
            new GameRule("show_advancement_messages", "进度公告", "是否在聊天框显示玩家进度", true, 12),
            new GameRule("block_explosion_drop_decay", "方块爆炸衰减", "方块爆炸时是否有概率不掉落", true, 14),
            new GameRule("players_sleeping_percentage", "玩家睡觉比例", "跳过夜晚所需睡觉玩家百分比", 100, 15),
            new GameRule("universal_anger", "全局愤怒", "流浪生物是否无差别攻击", false, 16),
            new GameRule("water_source_conversion", "水源转换", "水是否会自动流动和转换", true, 21),
            new GameRule("player_movement_check", "玩家移动检查", "是否检查玩家移动合法性", true, 21),
            new GameRule("players_nether_portal_creative_delay", "下界传送门创造延迟", "创造模式玩家使用下界传送门的延迟", 1, 21),
            new GameRule("players_nether_portal_default_delay", "下界传送门默认延迟", "普通玩家使用下界传送门的默认延迟", 1, 21),
            new GameRule("projectiles_can_break_blocks", "抛射物破坏方块", "抛射物是否可以破坏方块", true, 21),
            new GameRule("raids", "袭击", "是否生成袭击事件", true, 21),
            new GameRule("random_tick_speed", "随机刻速度", "随机Tick的速度", 3, 21),
            new GameRule("send_command_feedback", "命令反馈", "执行命令时是否显示反馈", true, 21),
            new GameRule("spawn_monsters", "生成怪物", "是否生成怪物", true, 21),
            new GameRule("spawn_patrols", "生成巡逻队", "是否生成袭击巡逻队", true, 21),
            new GameRule("spawn_phantoms", "生成幻翼", "是否生成幻翼", true, 21),
            new GameRule("spawn_wandering_traders", "生成流浪商人", "是否生成流浪商人", true, 21),
            new GameRule("spawner_blocks_work", "刷怪笼工作", "刷怪笼是否正常工作", true, 21),
            new GameRule("spectators_generate_chunks", "旁观者生成区块", "旁观者模式是否生成区块", true, 21),
            new GameRule("tnt_explosion_drop_decay", "TNT爆炸衰减", "TNT爆炸时是否有概率不掉落物品", true, 21)
        );

        VERSION_RULES.put(MCVersion.V1_20, rules1_20);
        VERSION_RULES.put(MCVersion.V1_21_10, rules1_21_10);
        VERSION_RULES.put(MCVersion.V1_21_11, rules1_21_11);
    }

    public static void setCurrentVersion(MCVersion version) {
        if (version != null) {
            currentVersion = version;
            if (useJsonConfig) {
                loadJsonConfigForVersion(version);
            }
        }
    }

    public static MCVersion getCurrentVersion() {
        return currentVersion;
    }

    public static void setDetectedVersion(String mcVersion) {
        detectedMcVersion = mcVersion;
        if (mcVersion != null && !mcVersion.isEmpty()) {
            String matchedJsonVersion = JsonGameRuleLoader.findMatchingJsonVersion(mcVersion);
            if (matchedJsonVersion != null) {
                activeJsonVersion = matchedJsonVersion;
                MCVersion enumVersion = MCVersion.fromVersionString(matchedJsonVersion);
                currentVersion = enumVersion;
            } else {
                activeJsonVersion = null;
                currentVersion = MCVersion.fromVersionString(mcVersion);
            }
        }
    }

    public static String getDetectedMcVersion() {
        return detectedMcVersion;
    }

    public static String getActiveJsonVersion() {
        return activeJsonVersion;
    }

    public static void setUseJsonConfig(boolean useJson) {
        useJsonConfig = useJson;
    }

    public static boolean isUseJsonConfig() {
        return useJsonConfig;
    }

    public static boolean isUsingJsonConfig() {
        return useJsonConfig && activeJsonVersion != null;
    }

    private static void loadJsonConfigForVersion(MCVersion version) {
        String jsonVersion = MCVersion.toJsonVersion(version);
        if (JSON_CONFIG_CACHE.containsKey(jsonVersion)) {
            return;
        }

        JsonGameRuleLoader.JsonGameRuleSet config = JsonGameRuleLoader.loadFromJson(jsonVersion);
        if (config != null) {
            JSON_CONFIG_CACHE.put(jsonVersion, config);
        }
    }

    public static List<MCVersion> getAvailableVersions() {
        return new ArrayList<>(VERSION_RULES.keySet());
    }

    public static String[] getAvailableJsonVersions() {
        return JsonGameRuleLoader.getAvailableVersions();
    }

    public static List<GameRule> getGameRules() {
        return getGameRules(currentVersion);
    }

    public static List<GameRule> getGameRules(MCVersion version) {
        if (useJsonConfig && version != null) {
            String jsonVersion = MCVersion.toJsonVersion(version);
            JsonGameRuleLoader.JsonGameRuleSet jsonConfig = JSON_CONFIG_CACHE.get(jsonVersion);

            if (jsonConfig == null || jsonConfig.gameRules == null || jsonConfig.gameRules.isEmpty()) {
                if (activeJsonVersion != null) {
                    jsonConfig = JSON_CONFIG_CACHE.get(activeJsonVersion);
                }
                if (jsonConfig == null && detectedMcVersion != null) {
                    String matched = JsonGameRuleLoader.findMatchingJsonVersion(detectedMcVersion);
                    if (matched != null) {
                        jsonConfig = JsonGameRuleLoader.loadFromJson(matched);
                        if (jsonConfig != null) {
                            JSON_CONFIG_CACHE.put(matched, jsonConfig);
                            activeJsonVersion = matched;
                        }
                    }
                }
            }

            if (jsonConfig != null && jsonConfig.gameRules != null && !jsonConfig.gameRules.isEmpty()) {
                List<GameRule> convertedRules = convertJsonRules(jsonConfig.gameRules);
                if (!convertedRules.isEmpty()) {
                    return convertedRules;
                }
            }
        }

        return VERSION_RULES.getOrDefault(version, Collections.emptyList());
    }

    private static List<GameRule> convertJsonRules(List<JsonGameRuleLoader.JsonGameRule> jsonRules) {
        List<GameRule> rules = new ArrayList<>();
        for (JsonGameRuleLoader.JsonGameRule jsonRule : jsonRules) {
            GameRule rule;
            if (jsonRule.isBoolean()) {
                rule = new GameRule(jsonRule.name, jsonRule.displayName, jsonRule.description, jsonRule.defaultBoolean, jsonRule.introducedVersion);
            } else {
                rule = new GameRule(jsonRule.name, jsonRule.displayName, jsonRule.description, jsonRule.defaultInt, jsonRule.introducedVersion);
            }
            rules.add(rule);
        }
        return rules;
    }

    public static GameRule findByName(String name) {
        return getGameRules().stream()
            .filter(rule -> rule.getName().equals(name))
            .findFirst()
            .orElse(null);
    }

    public static GameRule findByNameInAllVersions(String name) {
        for (List<GameRule> rules : VERSION_RULES.values()) {
            for (GameRule rule : rules) {
                if (rule.getName().equals(name)) {
                    return rule;
                }
            }
        }
        return null;
    }

    public static String getFormattedRuleName(String ruleName, MCVersion version) {
        return ruleName;
    }

    public static void clearCache() {
        JSON_CONFIG_CACHE.clear();
        JsonGameRuleLoader.clearCache();
    }

    public static class GameRule {
        private final String name;
        private final String displayName;
        private final String description;
        private final boolean defaultBoolean;
        private final int defaultInt;
        private final int introducedVersion;

        public GameRule(String name, String displayName, String description, boolean defaultBoolean, int introducedVersion) {
            this.name = name;
            this.displayName = displayName;
            this.description = description;
            this.defaultBoolean = defaultBoolean;
            this.defaultInt = 0;
            this.introducedVersion = introducedVersion;
        }

        public GameRule(String name, String displayName, String description, int defaultInt, int introducedVersion) {
            this.name = name;
            this.displayName = displayName;
            this.description = description;
            this.defaultBoolean = true;
            this.defaultInt = defaultInt;
            this.introducedVersion = introducedVersion;
        }

        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public boolean getDefaultBoolean() { return defaultBoolean; }
        public int getDefaultInt() { return defaultInt; }
        public int getIntroducedVersion() { return introducedVersion; }
        public boolean isBoolean() { return defaultInt == 0; }
        public String getDefaultValue() { return isBoolean() ? String.valueOf(defaultBoolean) : String.valueOf(defaultInt); }
        public boolean isAvailableIn(MCVersion version) { return version.majorVersion >= introducedVersion; }
    }
}
