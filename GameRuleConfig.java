import java.util.*;
import java.util.stream.Collectors;

public class GameRuleConfig {
    public enum MCVersion {
        V1_20("1.20.x", 20),
        V1_21("1.21.x", 21),
        V1_22("1.22.x", 22);

        private final String displayName;
        private final int majorVersion;

        MCVersion(String displayName, int majorVersion) {
            this.displayName = displayName;
            this.majorVersion = majorVersion;
        }

        public String getDisplayName() { return displayName; }
        public int getMajorVersion() { return majorVersion; }

        @Override
        public String toString() { return displayName; }

        public static MCVersion fromMajorVersion(int majorVersion) {
            for (MCVersion v : values()) {
                if (v.majorVersion == majorVersion) return v;
            }
            return V1_20;
        }
    }

    private static final Map<MCVersion, List<GameRule>> VERSION_RULES = new LinkedHashMap<>();

    static {
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

        List<GameRule> rules1_21 = new ArrayList<>(rules1_20);
        rules1_21.add(new GameRule("endCrystalSpawn", "末影水晶生成", "是否在末地生成末影水晶", true, 21));

        List<GameRule> rules1_22 = new ArrayList<>(rules1_21);

        VERSION_RULES.put(MCVersion.V1_20, rules1_20);
        VERSION_RULES.put(MCVersion.V1_21, rules1_21);
        VERSION_RULES.put(MCVersion.V1_22, rules1_22);
    }

    private static MCVersion currentVersion = MCVersion.V1_21;

    public static void setCurrentVersion(MCVersion version) {
        if (version != null) {
            currentVersion = version;
        }
    }

    public static MCVersion getCurrentVersion() {
        return currentVersion;
    }

    public static List<MCVersion> getAvailableVersions() {
        return new ArrayList<>(VERSION_RULES.keySet());
    }

    public static List<GameRule> getGameRules() {
        return VERSION_RULES.getOrDefault(currentVersion, VERSION_RULES.get(MCVersion.V1_20));
    }

    public static List<GameRule> getGameRules(MCVersion version) {
        return VERSION_RULES.getOrDefault(version, Collections.emptyList());
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
