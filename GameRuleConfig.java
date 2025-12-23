import java.util.Arrays;
import java.util.List;

public class GameRuleConfig {
    private static final List<GameRule> GAME_RULES = Arrays.asList(
        new GameRule("keepInventory", "死亡不掉落", "是否保留玩家死亡后背包物品", true),
        new GameRule("mobGriefing", "生物破坏", "生物是否能破坏环境和放置方块", true),
        new GameRule("doMobSpawning", "生物生成", "是否生成敌对生物", true),
        new GameRule("doTileDrops", "方块掉落", "方块被破坏时是否掉落物品", true),
        new GameRule("doEntityDrops", "实体掉落", "生物死亡时是否掉落物品", true),
        new GameRule("commandBlockOutput", "命令方块输出", "命令方块执行时是否输出日志", true),
        new GameRule("logAdminCommands", "管理命令日志", "是否记录管理员执行的命令", true),
        new GameRule("broadcastCommandDebug", "调试命令广播", "调试命令是否广播给所有玩家", false),
        new GameRule("showDeathMessages", "死亡消息", "玩家死亡时是否显示消息", true),
        new GameRule("naturalRegeneration", "自然恢复", "玩家是否自然恢复生命值", true),
        new GameRule("doWeatherCycle", "天气循环", "天气是否会变化", true),
        new GameRule("doDaylightCycle", "日夜循环", "时间是否会流逝", true),
        new GameRule("disableElytraMovementCheck", "鞘翅检查", "是否禁用鞘翅碰撞检查", false),
        new GameRule("spawnRadius", "生成半径", "玩家重生半径(仅创造模式)", 1),
        new GameRule("maxEntityCramming", "实体挤压", "生物最大挤压数", 24),
        new GameRule("fireSpreads", "火焰蔓延", "火焰是否会蔓延", true),
        new GameRule("tntExplodes", "TNT爆炸", "TNT是否会爆炸", true),
        new GameRule("mobExplosionDropBlock", "生物爆炸掉落", "生物爆炸时是否掉落方块", true),
        new GameRule("zombieAggressiveTowardsVillagers", "僵尸攻击村民", "僵尸是否主动攻击村民", true),
        new GameRule("doWardenSpawning", "监守者生成", "是否生成监守者", true),
        new GameRule("endCrystalSpawn", "末影水晶生成", "是否在末地生成末影水晶", true)
    );

    public static List<GameRule> getGameRules() {
        return GAME_RULES;
    }

    public static GameRule findByName(String name) {
        return GAME_RULES.stream()
            .filter(rule -> rule.getName().equals(name))
            .findFirst()
            .orElse(null);
    }

    public static class GameRule {
        private final String name;
        private final String displayName;
        private final String description;
        private final boolean defaultBoolean;
        private final int defaultInt;

        public GameRule(String name, String displayName, String description, boolean defaultBoolean) {
            this.name = name;
            this.displayName = displayName;
            this.description = description;
            this.defaultBoolean = defaultBoolean;
            this.defaultInt = 0;
        }

        public GameRule(String name, String displayName, String description, int defaultInt) {
            this.name = name;
            this.displayName = displayName;
            this.description = description;
            this.defaultBoolean = true;
            this.defaultInt = defaultInt;
        }

        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public boolean getDefaultBoolean() { return defaultBoolean; }
        public int getDefaultInt() { return defaultInt; }
        public boolean isBoolean() { return defaultInt == 0; }
        public String getDefaultValue() { return isBoolean() ? String.valueOf(defaultBoolean) : String.valueOf(defaultInt); }
    }
}
