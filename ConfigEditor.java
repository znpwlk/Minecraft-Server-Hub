import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.util.Map;
import java.util.Set;
import com.google.gson.*;
import javax.swing.table.TableCellEditor;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.text.NumberFormat;
import java.util.List;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
public class ConfigEditor extends JDialog {
    private enum ConfigType {
        STRING, BOOLEAN, NUMBER, SELECT
    }
    private ConfigManager configManager;
    private DefaultTableModel tableModel;
    private JTable configTable;
    private DefaultTableModel listTableModel;
    private JTable listTable;
    private JButton saveButton;
    private JButton cancelButton;
    private JButton addButton;
    private JButton removeButton;
    private JComboBox<String> configFileComboBox;
    private JLabel statusLabel;
    private JTextArea explanationLabel;
    private File serverDir;
    private File selectedConfigFile;
    private CardLayout contentCardLayout;
    private JPanel contentPanel;
    private JTextArea jsonTextArea;
    private JTextField playerNameField;
    private Gson gson;
    private JLabel iconPreviewLabel;
    private JButton changeIconButton;
    private JButton removeIconButton;
    private BufferedImage currentIconImage;
    private Map<String, String> propertiesChineseMap;
    private Map<String, String> propertiesExplanationMap;
    private Map<String, String> originalKeysMap;
    private Map<String, ConfigType> propertiesTypeMap;
    private Map<String, List<String>> propertiesOptionsMap;
    public ConfigEditor(JFrame parent, File serverDir) {
        super(parent, "服务器配置管理", false);
        this.serverDir = serverDir;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        initUI();
        loadConfigFiles();
    }
    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setSize(900, 650);
        setLocationRelativeTo(null);
        gson = new GsonBuilder().setPrettyPrinting().create();
        initPropertiesChineseMap();
        initPropertiesExplanationMap();
        initPropertiesTypeMap();
        initPropertiesOptionsMap();
        originalKeysMap = new java.util.HashMap<>();
        JPanel northPanel = new JPanel(new BorderLayout(10, 5));
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));
        JLabel fileLabel = new JLabel("选择配置文件:");
        configFileComboBox = new JComboBox<>();
        configFileComboBox.addActionListener(e -> loadSelectedConfig());
        topPanel.add(fileLabel, BorderLayout.WEST);
        topPanel.add(configFileComboBox, BorderLayout.CENTER);
        northPanel.add(topPanel, BorderLayout.NORTH);
        JPanel explanationPanel = new JPanel(new BorderLayout(10, 5));
        explanationLabel = new JTextArea("选择配置项查看解释");
        explanationLabel.setForeground(Color.BLUE);
        explanationLabel.setBackground(explanationPanel.getBackground());
        explanationLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        explanationLabel.setLineWrap(true);
        explanationLabel.setWrapStyleWord(true);
        explanationLabel.setEditable(false);
        explanationLabel.setOpaque(false);
        explanationLabel.setAutoscrolls(true);
        explanationLabel.setFocusable(false);
        JScrollPane explanationScrollPane = new JScrollPane(explanationLabel);
        explanationScrollPane.setBorder(BorderFactory.createEmptyBorder());
        explanationScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        explanationScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        explanationScrollPane.setPreferredSize(new Dimension(0, 80));
        explanationPanel.add(explanationScrollPane, BorderLayout.CENTER);
        northPanel.add(explanationPanel, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);
        contentCardLayout = new CardLayout();
        contentPanel = new JPanel(contentCardLayout);
        String[] columnNames = {"键", "值"};
        tableModel = new DefaultTableModel(columnNames, 0);
        configTable = new ConfigTable(tableModel) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1; 
            }
        };
        configTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = configTable.getSelectedRow();
                if (selectedRow != -1) {
                    String displayKey = (String) tableModel.getValueAt(selectedRow, 0);
                    String originalKey = originalKeysMap.containsKey(displayKey) ? originalKeysMap.get(displayKey) : displayKey;
                    showConfigExplanation(originalKey);
                }
            }
        });
        configTable.getColumnModel().getColumn(0).setPreferredWidth(250);
        configTable.getColumnModel().getColumn(1).setPreferredWidth(550);
        JScrollPane tableScrollPane = new JScrollPane(configTable);
        tableScrollPane.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        contentPanel.add(tableScrollPane, "table");
        JPanel listEditorPanel = new JPanel(new BorderLayout(10, 10));
        String[] listColumnNames = {"玩家名称", "UUID", "创建时间", "来源", "权限等级"};
        listTableModel = new DefaultTableModel(listColumnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        listTable = new JTable(listTableModel);
        listTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScrollPane = new JScrollPane(listTable);
        listEditorPanel.add(listScrollPane, BorderLayout.CENTER);
        JPanel listActionPanel = new JPanel(new BorderLayout(10, 5));
        JPanel addPlayerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel playerNameLabel = new JLabel("玩家名称:");
        playerNameField = new JTextField(20);
        addButton = new JButton("添加");
        addButton.addActionListener(e -> addPlayer());
        addPlayerPanel.add(playerNameLabel);
        addPlayerPanel.add(playerNameField);
        addPlayerPanel.add(addButton);
        listActionPanel.add(addPlayerPanel, BorderLayout.WEST);
        removeButton = new JButton("删除所选");
        removeButton.addActionListener(e -> removeSelectedPlayer());
        listActionPanel.add(removeButton, BorderLayout.EAST);
        listEditorPanel.add(listActionPanel, BorderLayout.SOUTH);
        contentPanel.add(listEditorPanel, "list");
        jsonTextArea = new JTextArea();
        jsonTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane jsonScrollPane = new JScrollPane(jsonTextArea);
        contentPanel.add(jsonScrollPane, "json");
        
        JPanel iconPanel = new JPanel(new BorderLayout(10, 10));
        JPanel iconInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel iconInfoLabel = new JLabel("服务器图标 (PNG格式, 64x64像素)");
        iconInfoPanel.add(iconInfoLabel);
        iconPanel.add(iconInfoPanel, BorderLayout.NORTH);
        
        JPanel iconDisplayPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        iconPreviewLabel = new JLabel("暂无图标");
        iconPreviewLabel.setPreferredSize(new Dimension(64, 64));
        iconPreviewLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        iconPreviewLabel.setHorizontalAlignment(JLabel.CENTER);
        iconPreviewLabel.setVerticalAlignment(JLabel.CENTER);
        gbc.gridx = 0; gbc.gridy = 0;
        iconDisplayPanel.add(iconPreviewLabel, gbc);
        iconPanel.add(iconDisplayPanel, BorderLayout.CENTER);
        
        JPanel iconButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        changeIconButton = new JButton("更换图标");
        changeIconButton.addActionListener(e -> changeIcon());
        removeIconButton = new JButton("删除图标");
        removeIconButton.addActionListener(e -> removeIcon());
        iconButtonPanel.add(changeIconButton);
        iconButtonPanel.add(removeIconButton);
        iconPanel.add(iconButtonPanel, BorderLayout.SOUTH);
        
        contentPanel.add(iconPanel, "icon");
        add(contentPanel, BorderLayout.CENTER);
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 5));
        statusLabel = new JLabel("选择配置文件开始编辑");
        bottomPanel.add(statusLabel, BorderLayout.WEST);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveButton = new JButton("保存");
        saveButton.addActionListener(e -> saveConfig());
        saveButton.setEnabled(false);
        cancelButton = new JButton("关闭");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }
    private void loadConfigFiles() {
        configFileComboBox.removeAllItems();
        Map<String, String> configFileNames = new java.util.HashMap<>();
        configFileNames.put("server.properties", "服务器配置");
        configFileNames.put("ops.json", "OP配置");
        configFileNames.put("whitelist.json", "白名单配置");
        configFileNames.put("banned-players.json", "封禁玩家配置");
        configFileNames.put("banned-ips.json", "封禁IP配置");
        configFileNames.put("usercache.json", "用户缓存配置");
        configFileNames.put("eula.txt", "EULA协议");
        configFileNames.put("server-icon.png", "服务器图标");
        String[] commonConfigFiles = {
            "server.properties",
            "ops.json",
            "whitelist.json",
            "banned-players.json",
            "banned-ips.json",
            "usercache.json"
        };
        for (String fileName : commonConfigFiles) {
            File file = new File(serverDir, fileName);
            if (file.exists()) {
                String displayName = configFileNames.getOrDefault(fileName, fileName);
                configFileComboBox.addItem(displayName + " (" + fileName + ")");
            } else {
                File currentDirFile = new File(fileName);
                if (currentDirFile.exists()) {
                    String displayName = configFileNames.getOrDefault(fileName, fileName);
                    configFileComboBox.addItem(displayName + " (" + fileName + ")");
                }
            }
        }
        File[] files = serverDir.listFiles((dir, name) -> {
            boolean isConfigFile = name.endsWith(".properties") || name.endsWith(".json") || name.equals("eula.txt") || name.equals("server-icon.png");
            for (int i = 0; i < configFileComboBox.getItemCount(); i++) {
                String comboItem = configFileComboBox.getItemAt(i);
                if (comboItem.contains(name)) {
                    return false;
                }
            }
            return isConfigFile;
        });
        if (files != null) {
            for (File file : files) {
                if (file.exists()) {
                    String fileName = file.getName();
                    String displayName = configFileNames.getOrDefault(fileName, fileName);
                    configFileComboBox.addItem(displayName + " (" + fileName + ")");
                }
            }
        }
        if (configFileComboBox.getItemCount() == 0) {
            File currentDir = new File(".");
            File[] currentDirFiles = currentDir.listFiles((dir, name) -> {
                return name.endsWith(".properties") || name.endsWith(".json") || name.equals("eula.txt") || name.equals("server-icon.png");
            });
            if (currentDirFiles != null && currentDirFiles.length > 0) {
                for (File file : currentDirFiles) {
                    if (file.exists()) {
                        String fileName = file.getName();
                        String displayName = configFileNames.getOrDefault(fileName, fileName);
                        configFileComboBox.addItem(displayName + " (" + fileName + ")");
                    }
                }
            }
        }
        if (configFileComboBox.getItemCount() == 0) {
            statusLabel.setText("未找到任何配置文件! 请先启动服务器生成配置文件");
            saveButton.setEnabled(false);
        }
    }
    private void loadSelectedConfig() {
        String selectedDisplayName = (String) configFileComboBox.getSelectedItem();
        if (selectedDisplayName != null) {
            String actualFileName;
            if (selectedDisplayName.contains(" (")) {
                actualFileName = selectedDisplayName.substring(selectedDisplayName.lastIndexOf(" (") + 2, selectedDisplayName.lastIndexOf(")"));
            } else {
                actualFileName = selectedDisplayName;
            }
            selectedConfigFile = new File(serverDir, actualFileName);
            if (!selectedConfigFile.exists()) {
                File currentDirFile = new File(actualFileName);
                if (currentDirFile.exists()) {
                    selectedConfigFile = currentDirFile;
                }
            }
            configManager = new ConfigManager(selectedConfigFile);
            if (actualFileName.equals("server-icon.png")) {
                loadIconToPreview();
                contentCardLayout.show(contentPanel, "icon");
            } else if (actualFileName.endsWith(".json")) {
                Map<String, Object> configData = configManager.getConfigData();
                if (isListTypeConfig(actualFileName)) {
                    loadListToTable();
                    contentCardLayout.show(contentPanel, "list");
                } else if (!configData.isEmpty()) {
                    Object firstValue = configData.values().iterator().next();
                    if (firstValue instanceof com.google.gson.JsonArray) {
                        loadJsonToTextArea();
                        contentCardLayout.show(contentPanel, "json");
                    } else {
                        loadConfigToTable();
                        contentCardLayout.show(contentPanel, "table");
                    }
                } else {
                    loadJsonToTextArea();
                    contentCardLayout.show(contentPanel, "json");
                }
            } else if (actualFileName.endsWith(".properties")) {
                loadConfigToTable();
                contentCardLayout.show(contentPanel, "table");
            } else {
                loadJsonToTextArea();
                contentCardLayout.show(contentPanel, "json");
            }
            saveButton.setEnabled(true);
            statusLabel.setText("正在编辑: " + selectedDisplayName);
        }
    }
    private boolean isListTypeConfig(String fileName) {
        return fileName.equals("banned-players.json") || fileName.equals("ops.json") || 
               fileName.equals("whitelist.json") || fileName.equals("banned-ips.json");
    }
    private void initPropertiesChineseMap() {
        propertiesChineseMap = new java.util.HashMap<>();
        propertiesChineseMap.put("level-name", "世界名称");
        propertiesChineseMap.put("level-seed", "世界种子");
        propertiesChineseMap.put("level-type", "世界类型");
        propertiesChineseMap.put("generator-settings", "生成器设置");
        propertiesChineseMap.put("generate-structures", "生成结构");
        propertiesChineseMap.put("use-vanilla-world-seed", "使用原版世界种子");
        propertiesChineseMap.put("spawn-npcs", "生成NPC");
        propertiesChineseMap.put("spawn-animals", "生成动物");
        propertiesChineseMap.put("spawn-monsters", "生成怪物");
        propertiesChineseMap.put("allow-nether", "允许下界");
        propertiesChineseMap.put("max-build-height", "最大建造高度");
        propertiesChineseMap.put("max-world-size", "最大世界大小");
        propertiesChineseMap.put("use-native-transport", "使用原生传输");
        propertiesChineseMap.put("region-file-compression", "区块文件压缩");
        propertiesChineseMap.put("gamemode", "游戏模式");
        propertiesChineseMap.put("difficulty", "难度");
        propertiesChineseMap.put("hardcore", "极限模式");
        propertiesChineseMap.put("pvp", "玩家对战");
        propertiesChineseMap.put("allow-flight", "允许飞行");
        propertiesChineseMap.put("force-gamemode", "强制游戏模式");
        propertiesChineseMap.put("spawn-protection", "出生点保护范围");
        propertiesChineseMap.put("enable-code-of-conduct", "启用行为准则");
        propertiesChineseMap.put("server-port", "服务器端口");
        propertiesChineseMap.put("server-ip", "服务器IP");
        propertiesChineseMap.put("max-players", "最大玩家数");
        propertiesChineseMap.put("online-mode", "正版验证");
        propertiesChineseMap.put("white-list", "启用白名单");
        propertiesChineseMap.put("enforce-whitelist", "强制白名单");
        propertiesChineseMap.put("motd", "服务器描述");
        propertiesChineseMap.put("bug-report-link", "错误报告链接");
        propertiesChineseMap.put("hide-online-players", "隐藏在线玩家");
        propertiesChineseMap.put("prevent-proxy-connections", "防止代理连接");
        propertiesChineseMap.put("accepts-transfers", "接受传输");
        propertiesChineseMap.put("log-ips", "记录IP");
        propertiesChineseMap.put("rate-limit", "速率限制");
        propertiesChineseMap.put("status-heartbeat-interval", "状态心跳间隔");
        propertiesChineseMap.put("resource-pack", "资源包URL");
        propertiesChineseMap.put("resource-pack-sha1", "资源包SHA1");
        propertiesChineseMap.put("require-resource-pack", "强制资源包");
        propertiesChineseMap.put("resource-pack-prompt", "资源包提示");
        propertiesChineseMap.put("resource-pack-id", "资源包ID");
        propertiesChineseMap.put("initial-enabled-packs", "初始启用数据包");
        propertiesChineseMap.put("initial-disabled-packs", "初始禁用数据包");
        propertiesChineseMap.put("max-tick-time", "最大tick时间");
        propertiesChineseMap.put("max-chained-neighbor-updates", "最大连锁邻居更新");
        propertiesChineseMap.put("network-compression-threshold", "网络压缩阈值");
        propertiesChineseMap.put("sync-chunk-writes", "同步区块写入");
        propertiesChineseMap.put("view-distance", "视野距离");
        propertiesChineseMap.put("simulation-distance", "模拟距离");
        propertiesChineseMap.put("entity-broadcast-range-percentage", "实体广播范围百分比");
        propertiesChineseMap.put("player-idle-timeout", "玩家闲置超时");
        propertiesChineseMap.put("pause-when-empty-seconds", "空服暂停时间");
        propertiesChineseMap.put("enable-rcon", "启用RCON");
        propertiesChineseMap.put("rcon.port", "RCON端口");
        propertiesChineseMap.put("rcon.password", "RCON密码");
        propertiesChineseMap.put("broadcast-rcon-to-ops", "向OP广播RCON消息");
        propertiesChineseMap.put("enable-query", "启用查询");
        propertiesChineseMap.put("query.port", "查询端口");
        propertiesChineseMap.put("enable-status", "启用状态");
        propertiesChineseMap.put("enable-jmx-monitoring", "启用JMX监控");
        propertiesChineseMap.put("management-server-enabled", "启用管理服务器");
        propertiesChineseMap.put("management-server-host", "管理服务器地址");
        propertiesChineseMap.put("management-server-port", "管理服务器端口");
        propertiesChineseMap.put("management-server-secret", "管理服务器密钥");
        propertiesChineseMap.put("management-server-tls-enabled", "启用管理服务器TLS");
        propertiesChineseMap.put("management-server-tls-keystore", "管理服务器TLS密钥库");
        propertiesChineseMap.put("management-server-tls-keystore-password", "管理服务器TLS密钥库密码");
        propertiesChineseMap.put("op-permission-level", "OP权限等级");
        propertiesChineseMap.put("function-permission-level", "函数权限等级");
        propertiesChineseMap.put("broadcast-console-to-ops", "向OP广播控制台消息");
        propertiesChineseMap.put("text-filtering-version", "文本过滤版本");
        propertiesChineseMap.put("text-filtering-config", "文本过滤配置");
        propertiesChineseMap.put("enforce-secure-profile", "强制安全配置文件");
        propertiesChineseMap.put("debug", "调试模式");
        propertiesChineseMap.put("enable-command-block", "启用命令方块");
    }
    private void initPropertiesExplanationMap() {
        propertiesExplanationMap = new java.util.HashMap<>();
        propertiesExplanationMap.put("level-name", "服务器生成的世界文件夹名称，默认为'world'。修改后会生成新的世界文件夹，不会覆盖原有世界");
        propertiesExplanationMap.put("level-seed", "世界种子，留空则随机生成，支持数值和文本格式。相同种子生成相同的世界地形");
        propertiesExplanationMap.put("level-type", "世界类型，决定世界的生成方式：minecraft:normal（普通生存世界）、minecraft:flat（超平坦，无起伏地形）、minecraft:largebiomes（大型生物群系）、minecraft:amplified（放大化，地形起伏大）等");
        propertiesExplanationMap.put("generator-settings", "自定义生成器设置，用于超平坦等特殊世界，JSON格式。可自定义地形高度、方块类型等");
        propertiesExplanationMap.put("generate-structures", "是否生成村庄、寺庙、地牢等自然结构：true（生成）、false（不生成）。false时世界将没有任何自然建筑");
        propertiesExplanationMap.put("use-vanilla-world-seed", "是否使用原版世界种子生成器：true（使用原版算法）、false（使用服务器自定义算法）。影响世界地形生成");
        propertiesExplanationMap.put("spawn-npcs", "是否生成村民等NPC：true（生成）、false（不生成）。仅影响新生成的村庄，已有的NPC不会消失");
        propertiesExplanationMap.put("spawn-animals", "是否生成和平生物（如牛、羊、猪等）：true（生成）、false（不生成）。false时世界将没有任何和平生物");
        propertiesExplanationMap.put("spawn-monsters", "是否生成敌对生物（如僵尸、骷髅、苦力怕等）：true（生成）、false（不生成）。false时世界将没有任何敌对生物");
        propertiesExplanationMap.put("allow-nether", "是否允许玩家进入下界维度：true（允许）、false（禁止）。false时玩家无法通过传送门进入下界，下界维度将被禁用");
        propertiesExplanationMap.put("max-build-height", "玩家可以建造的最大高度，单位为方块，默认为256。修改后影响玩家的建筑上限，过高可能影响服务器性能");
        propertiesExplanationMap.put("max-world-size", "世界最大半径，单位为方块，默认为29999984。影响世界边界大小，修改后会改变玩家可探索的范围");
        propertiesExplanationMap.put("use-native-transport", "是否使用原生网络传输库：true（使用，提高网络性能）、false（不使用，兼容性更好）。建议设置为true以提高服务器网络处理能力");
        propertiesExplanationMap.put("region-file-compression", "区块文件压缩方式：deflate（压缩，节省磁盘空间，但读取稍慢）、none（不压缩，读取更快，但占用更多磁盘空间）。根据服务器硬件选择，SSD建议使用none，HDD建议使用deflate");
        propertiesExplanationMap.put("gamemode", "默认游戏模式：survival（生存，需要收集资源、饥饿值）、creative（创造，无限资源、飞行）、adventure（冒险，无法破坏方块）、spectator（旁观者，透明、飞行、穿过方块）。影响新玩家的初始游戏模式");
        propertiesExplanationMap.put("difficulty", "游戏难度：peaceful（和平，无怪物、饥饿值不下降）、easy（简单，怪物少、伤害低）、normal（普通，正常怪物数量和伤害）、hard（困难，怪物多、伤害高、会破坏方块）。影响世界的整体难度");
        propertiesExplanationMap.put("hardcore", "是否启用极限模式：true（启用）、false（禁用）。true时死亡后无法复活，自动切换到旁观者模式，世界将被标记为极限模式");
        propertiesExplanationMap.put("pvp", "是否启用玩家对战：true（允许玩家互相攻击）、false（禁止玩家互相攻击）。false时玩家无法造成对其他玩家的伤害");
        propertiesExplanationMap.put("allow-flight", "是否允许玩家在生存模式下飞行：true（允许）、false（禁止）。true时玩家需要使用特定客户端或插件才能飞行，false时任何模式下都无法飞行（创造模式除外）");
        propertiesExplanationMap.put("force-gamemode", "是否强制所有玩家使用默认游戏模式：true（强制，登录时自动切换）、false（不强制，保留玩家上次的游戏模式）。true时所有玩家都会被强制使用默认gamemode");
        propertiesExplanationMap.put("spawn-protection", "出生点保护范围，单位为方块，默认为16。保护范围内只有OP玩家可以破坏或放置方块，0表示关闭保护");
        propertiesExplanationMap.put("enable-code-of-conduct", "是否启用Mojang行为准则系统：true（启用）、false（禁用）。启用后玩家可以举报违规行为，服务器会收到举报信息");
        propertiesExplanationMap.put("server-port", "服务器端口，默认为25565。需确保防火墙开放此端口，否则玩家无法连接");
        propertiesExplanationMap.put("server-ip", "服务器绑定的IP地址：留空或0.0.0.0表示绑定所有网卡（允许公网访问，需要公网IP和端口转发），127.0.0.1表示仅本地连接。自定义IP可以指定特定网络接口。公网开放请确保防火墙和路由器已开放端口");
        propertiesExplanationMap.put("max-players", "服务器最大玩家数量，默认为20。根据服务器硬件性能调整，过高可能导致服务器卡顿");
        propertiesExplanationMap.put("online-mode", "是否启用正版验证：true（验证，仅正版玩家可进入）、false（离线模式，所有玩家可进入）。正版服务器建议设为true");
        propertiesExplanationMap.put("white-list", "是否启用白名单：true（启用，只有白名单玩家可以进入）、false（禁用，所有玩家都可以进入）。启用后需要手动添加玩家到白名单");
        propertiesExplanationMap.put("enforce-whitelist", "是否强制启用白名单：true（强制，忽略OP权限，只有白名单玩家可进入）、false（不强制，OP玩家不受白名单限制）。true时即使是OP玩家也必须在白名单中才能进入");
        propertiesExplanationMap.put("motd", "服务器描述，显示在服务器列表中，支持颜色代码。用于吸引玩家加入，建议简洁明了");
        propertiesExplanationMap.put("bug-report-link", "玩家报告错误时使用的链接，URL格式。用于收集玩家反馈的问题");
        propertiesExplanationMap.put("hide-online-players", "是否在服务器列表中隐藏在线玩家数量：true（隐藏，显示为???）、false（显示真实数量）。true时玩家无法通过服务器列表看到在线人数");
        propertiesExplanationMap.put("prevent-proxy-connections", "是否防止代理连接：true（防止）、false（允许）。true时可以减少恶意玩家使用代理IP进入服务器");
        propertiesExplanationMap.put("accepts-transfers", "是否接受来自其他服务器的玩家传输请求：true（接受）、false（拒绝）。用于服务器网络之间的玩家转移");
        propertiesExplanationMap.put("log-ips", "是否记录玩家IP地址到服务器日志：true（记录）、false（不记录）。true时便于管理和排查问题，但涉及玩家隐私");
        propertiesExplanationMap.put("rate-limit", "玩家连接速率限制，单位为毫秒，默认为0（无限制）。用于防止DoS攻击，建议设置为1000-5000之间");
        propertiesExplanationMap.put("status-heartbeat-interval", "服务器状态心跳间隔，单位为毫秒，默认为15000。影响服务器向Mojang状态服务器发送心跳的频率");
        propertiesExplanationMap.put("resource-pack", "资源包URL，玩家进入服务器时会自动下载，支持HTTP/HTTPS。用于提供自定义纹理、音效等");
        propertiesExplanationMap.put("resource-pack-sha1", "资源包SHA1校验码，用于验证资源包完整性，可选。填写后玩家客户端会验证资源包是否被篡改");
        propertiesExplanationMap.put("require-resource-pack", "是否强制玩家使用资源包：true（强制，拒绝则无法进入）、false（不强制，玩家可选择是否使用）。true时所有玩家必须使用指定资源包才能进入");
        propertiesExplanationMap.put("resource-pack-prompt", "资源包提示信息，显示在玩家接受资源包时。用于向玩家说明资源包的内容和作用");
        propertiesExplanationMap.put("resource-pack-id", "资源包唯一ID，用于资源包管理系统。便于服务器管理多个资源包");
        propertiesExplanationMap.put("initial-enabled-packs", "初始启用的数据包，默认为'vanilla'，多个包用逗号分隔。影响世界的游戏规则和功能");
        propertiesExplanationMap.put("initial-disabled-packs", "初始禁用的数据包，多个包用逗号分隔。禁用不需要的数据包可以提高服务器性能");
        propertiesExplanationMap.put("max-tick-time", "单个tick的最大执行时间，单位为毫秒，默认为60000。超过此时间服务器会自动关闭以防止卡顿，建议保持默认或适当提高");
        propertiesExplanationMap.put("max-chained-neighbor-updates", "最大连锁邻居更新数，默认为1000000。防止TNT、水流等造成的服务器崩溃，降低此值可减少连锁反应的影响");
        propertiesExplanationMap.put("network-compression-threshold", "网络压缩阈值，单位为字节，默认为256。小于此大小的数据包不压缩，大于则压缩。提高此值可减少CPU使用，但增加网络流量");
        propertiesExplanationMap.put("sync-chunk-writes", "是否同步写入区块数据到磁盘：true（同步，数据安全但性能较低）、false（异步，性能较高但可能导致数据丢失）。建议设置为true以保证数据安全");
        propertiesExplanationMap.put("view-distance", "玩家视野距离，单位为区块（1区块=16×16方块），默认为10。影响玩家能看到的范围，降低可提高服务器性能，建议根据服务器性能调整为6-12");
        propertiesExplanationMap.put("simulation-distance", "服务器模拟距离，单位为区块，默认为8。影响服务器处理区块的范围，降低可显著提高服务器性能，建议设置为4-8");
        propertiesExplanationMap.put("entity-broadcast-range-percentage", "实体广播范围百分比，默认为100。影响实体可见距离，降低可减少网络流量和服务器负载，建议设置为70-100");
        propertiesExplanationMap.put("player-idle-timeout", "玩家闲置超时时间，单位为分钟，0表示不超时。超时后玩家会被自动踢出服务器，建议设置为5-30分钟以释放资源");
        propertiesExplanationMap.put("pause-when-empty-seconds", "服务器空服后暂停时间，单位为秒，-1表示不暂停。暂停后停止世界模拟，可节省服务器资源，建议设置为60-300秒");
        propertiesExplanationMap.put("enable-rcon", "是否启用RCON远程管理功能：true（启用）、false（禁用）。启用后可通过命令行远程控制服务器，建议启用并设置强密码");
        propertiesExplanationMap.put("rcon.port", "RCON端口，默认为25575。需确保防火墙开放此端口，否则无法远程连接");
        propertiesExplanationMap.put("rcon.password", "RCON密码，用于远程管理服务器。建议设置复杂密码，防止未授权访问");
        propertiesExplanationMap.put("broadcast-rcon-to-ops", "是否将RCON命令广播给在线OP玩家：true（广播）、false（不广播）。true时便于OP玩家监控远程操作");
        propertiesExplanationMap.put("enable-query", "是否启用服务器查询功能：true（启用）、false（禁用）。启用后可通过外部工具查询服务器状态");
        propertiesExplanationMap.put("query.port", "查询端口，默认为25565。需确保防火墙开放此端口，否则无法查询服务器状态");
        propertiesExplanationMap.put("enable-status", "是否启用服务器状态查询：true（启用）、false（禁用）。false时服务器将不会在服务器列表中显示");
        propertiesExplanationMap.put("enable-jmx-monitoring", "是否启用JMX监控：true（启用）、false（禁用）。启用后可通过JMX工具监控服务器性能");
        propertiesExplanationMap.put("management-server-enabled", "是否启用内置管理服务器：true（启用）、false（禁用）。用于远程管理服务器配置");
        propertiesExplanationMap.put("management-server-host", "管理服务器绑定的IP地址，默认为0.0.0.0。填写特定IP可限制管理服务器的访问");
        propertiesExplanationMap.put("management-server-port", "管理服务器端口，默认为25575。用于远程管理服务器的端口");
        propertiesExplanationMap.put("management-server-secret", "管理服务器密钥，用于身份验证。建议设置复杂密钥，防止未授权访问");
        propertiesExplanationMap.put("management-server-tls-enabled", "是否启用管理服务器TLS加密：true（启用，提高安全性）、false（禁用，兼容性更好）。建议启用以提高管理服务器的安全性");
        propertiesExplanationMap.put("management-server-tls-keystore", "管理服务器TLS密钥库路径，用于存储证书。需要有效的TLS证书才能启用TLS加密");
        propertiesExplanationMap.put("management-server-tls-keystore-password", "管理服务器TLS密钥库密码。用于保护TLS证书");
        propertiesExplanationMap.put("op-permission-level", "OP玩家的权限等级，1-4：1（基本命令，如踢人、传送）、2（管理员，如封禁、设置权限）、3（服务器所有者，如OP管理）、4（控制台权限，最高权限）。影响OP玩家可以使用的命令范围");
        propertiesExplanationMap.put("function-permission-level", "函数执行的权限等级，0-4。决定谁可以执行函数，0表示所有人，4表示只有控制台");
        propertiesExplanationMap.put("broadcast-console-to-ops", "是否将控制台命令广播给在线OP玩家：true（广播）、false（不广播）。true时便于OP玩家了解控制台操作");
        propertiesExplanationMap.put("text-filtering-version", "文本过滤系统版本。用于匹配不同版本的过滤规则，影响聊天内容的过滤效果");
        propertiesExplanationMap.put("text-filtering-config", "文本过滤配置文件路径。自定义聊天内容的过滤规则");
        propertiesExplanationMap.put("enforce-secure-profile", "是否强制使用安全配置文件：true（强制，要求客户端使用安全配置）、false（不强制，允许使用旧版客户端）。false时允许使用旧版客户端连接");
        propertiesExplanationMap.put("debug", "是否启用调试模式：true（启用，输出详细日志）、false（禁用，正常日志）。仅用于问题排查，建议平时设置为false");
        propertiesExplanationMap.put("enable-command-block", "是否启用命令方块：true（启用，允许执行命令）、false（禁用，禁止使用命令方块）。启用后可能存在安全风险，建议仅信任的服务器使用");
    }
    private void showConfigExplanation(String originalKey) {
        String explanation = propertiesExplanationMap.get(originalKey);
        if (explanation != null) {
            explanationLabel.setText("解释: " + explanation);
        } else {
            explanationLabel.setText("无解释可用");
        }
    }
    private void initPropertiesTypeMap() {
        propertiesTypeMap = new java.util.HashMap<>();
        propertiesTypeMap.put("online-mode", ConfigType.BOOLEAN);
        propertiesTypeMap.put("white-list", ConfigType.BOOLEAN);
        propertiesTypeMap.put("enforce-whitelist", ConfigType.BOOLEAN);
        propertiesTypeMap.put("pvp", ConfigType.BOOLEAN);
        propertiesTypeMap.put("allow-flight", ConfigType.BOOLEAN);
        propertiesTypeMap.put("hardcore", ConfigType.BOOLEAN);
        propertiesTypeMap.put("force-gamemode", ConfigType.BOOLEAN);
        propertiesTypeMap.put("generate-structures", ConfigType.BOOLEAN);
        propertiesTypeMap.put("spawn-npcs", ConfigType.BOOLEAN);
        propertiesTypeMap.put("spawn-animals", ConfigType.BOOLEAN);
        propertiesTypeMap.put("spawn-monsters", ConfigType.BOOLEAN);
        propertiesTypeMap.put("allow-nether", ConfigType.BOOLEAN);
        propertiesTypeMap.put("enable-rcon", ConfigType.BOOLEAN);
        propertiesTypeMap.put("enable-query", ConfigType.BOOLEAN);
        propertiesTypeMap.put("enable-status", ConfigType.BOOLEAN);
        propertiesTypeMap.put("enable-jmx-monitoring", ConfigType.BOOLEAN);
        propertiesTypeMap.put("sync-chunk-writes", ConfigType.BOOLEAN);
        propertiesTypeMap.put("hide-online-players", ConfigType.BOOLEAN);
        propertiesTypeMap.put("prevent-proxy-connections", ConfigType.BOOLEAN);
        propertiesTypeMap.put("accepts-transfers", ConfigType.BOOLEAN);
        propertiesTypeMap.put("require-resource-pack", ConfigType.BOOLEAN);
        propertiesTypeMap.put("enforce-secure-profile", ConfigType.BOOLEAN);
        propertiesTypeMap.put("enable-code-of-conduct", ConfigType.BOOLEAN);
        propertiesTypeMap.put("debug", ConfigType.BOOLEAN);
        propertiesTypeMap.put("log-ips", ConfigType.BOOLEAN);
        propertiesTypeMap.put("enable-command-block", ConfigType.BOOLEAN);
        propertiesTypeMap.put("broadcast-console-to-ops", ConfigType.BOOLEAN);
        propertiesTypeMap.put("broadcast-rcon-to-ops", ConfigType.BOOLEAN);
        propertiesTypeMap.put("management-server-enabled", ConfigType.BOOLEAN);
        propertiesTypeMap.put("management-server-tls-enabled", ConfigType.BOOLEAN);
        propertiesTypeMap.put("use-native-transport", ConfigType.BOOLEAN);
        propertiesTypeMap.put("use-vanilla-world-seed", ConfigType.BOOLEAN);
        propertiesTypeMap.put("max-players", ConfigType.NUMBER);
        propertiesTypeMap.put("server-port", ConfigType.NUMBER);
        propertiesTypeMap.put("server-ip", ConfigType.SELECT);
        propertiesTypeMap.put("rcon.port", ConfigType.NUMBER);
        propertiesTypeMap.put("query.port", ConfigType.NUMBER);
        propertiesTypeMap.put("max-build-height", ConfigType.NUMBER);
        propertiesTypeMap.put("max-world-size", ConfigType.NUMBER);
        propertiesTypeMap.put("spawn-protection", ConfigType.NUMBER);
        propertiesTypeMap.put("max-tick-time", ConfigType.NUMBER);
        propertiesTypeMap.put("max-chained-neighbor-updates", ConfigType.NUMBER);
        propertiesTypeMap.put("network-compression-threshold", ConfigType.NUMBER);
        propertiesTypeMap.put("view-distance", ConfigType.NUMBER);
        propertiesTypeMap.put("simulation-distance", ConfigType.NUMBER);
        propertiesTypeMap.put("entity-broadcast-range-percentage", ConfigType.NUMBER);
        propertiesTypeMap.put("player-idle-timeout", ConfigType.NUMBER);
        propertiesTypeMap.put("pause-when-empty-seconds", ConfigType.NUMBER);
        propertiesTypeMap.put("rate-limit", ConfigType.NUMBER);
        propertiesTypeMap.put("op-permission-level", ConfigType.NUMBER);
        propertiesTypeMap.put("function-permission-level", ConfigType.NUMBER);
        propertiesTypeMap.put("status-heartbeat-interval", ConfigType.NUMBER);
        propertiesTypeMap.put("management-server-port", ConfigType.NUMBER);
        propertiesTypeMap.put("gamemode", ConfigType.SELECT);
        propertiesTypeMap.put("difficulty", ConfigType.SELECT);
        propertiesTypeMap.put("level-type", ConfigType.SELECT);
        propertiesTypeMap.put("region-file-compression", ConfigType.SELECT);
        propertiesTypeMap.put("force-gamemode", ConfigType.SELECT);
        propertiesTypeMap.put("allow-flight", ConfigType.SELECT);
        propertiesTypeMap.put("pvp", ConfigType.SELECT);
        propertiesTypeMap.put("hardcore", ConfigType.SELECT);
        propertiesTypeMap.put("generate-structures", ConfigType.SELECT);
        propertiesTypeMap.put("spawn-npcs", ConfigType.SELECT);
        propertiesTypeMap.put("spawn-animals", ConfigType.SELECT);
        propertiesTypeMap.put("spawn-monsters", ConfigType.SELECT);
        propertiesTypeMap.put("allow-nether", ConfigType.SELECT);
        propertiesTypeMap.put("use-vanilla-world-seed", ConfigType.SELECT);
        propertiesTypeMap.put("enable-query", ConfigType.SELECT);
        propertiesTypeMap.put("enable-status", ConfigType.SELECT);
        propertiesTypeMap.put("enable-rcon", ConfigType.SELECT);
        propertiesTypeMap.put("enable-command-block", ConfigType.SELECT);
        propertiesTypeMap.put("enable-jmx-monitoring", ConfigType.SELECT);
        propertiesTypeMap.put("enable-code-of-conduct", ConfigType.SELECT);
        propertiesTypeMap.put("sync-chunk-writes", ConfigType.SELECT);
        propertiesTypeMap.put("log-ips", ConfigType.SELECT);
        propertiesTypeMap.put("broadcast-console-to-ops", ConfigType.SELECT);
        propertiesTypeMap.put("broadcast-rcon-to-ops", ConfigType.SELECT);
        propertiesTypeMap.put("management-server-enabled", ConfigType.SELECT);
        propertiesTypeMap.put("management-server-tls-enabled", ConfigType.SELECT);
        propertiesTypeMap.put("use-native-transport", ConfigType.SELECT);
        propertiesTypeMap.put("accepts-transfers", ConfigType.SELECT);
        propertiesTypeMap.put("online-mode", ConfigType.SELECT);
        propertiesTypeMap.put("white-list", ConfigType.SELECT);
        propertiesTypeMap.put("enforce-whitelist", ConfigType.SELECT);
        propertiesTypeMap.put("hide-online-players", ConfigType.SELECT);
        propertiesTypeMap.put("prevent-proxy-connections", ConfigType.SELECT);
        propertiesTypeMap.put("require-resource-pack", ConfigType.SELECT);
        propertiesTypeMap.put("enforce-secure-profile", ConfigType.SELECT);
        propertiesTypeMap.put("debug", ConfigType.SELECT);
    }
    private void initPropertiesOptionsMap() {
        propertiesOptionsMap = new java.util.HashMap<>();
        List<String> gamemodeOptions = new ArrayList<>();
        gamemodeOptions.add("survival");
        gamemodeOptions.add("creative");
        gamemodeOptions.add("adventure");
        gamemodeOptions.add("spectator");
        propertiesOptionsMap.put("gamemode", gamemodeOptions);
        List<String> difficultyOptions = new ArrayList<>();
        difficultyOptions.add("peaceful");
        difficultyOptions.add("easy");
        difficultyOptions.add("normal");
        difficultyOptions.add("hard");
        propertiesOptionsMap.put("difficulty", difficultyOptions);
        List<String> levelTypeOptions = new ArrayList<>();
        levelTypeOptions.add("minecraft:normal");
        levelTypeOptions.add("minecraft:flat");
        levelTypeOptions.add("minecraft:largebiomes");
        levelTypeOptions.add("minecraft:amplified");
        levelTypeOptions.add("minecraft:single_biome_surface");
        propertiesOptionsMap.put("level-type", levelTypeOptions);
        List<String> compressionOptions = new ArrayList<>();
        compressionOptions.add("deflate");
        compressionOptions.add("none");
        propertiesOptionsMap.put("region-file-compression", compressionOptions);
        List<String> serverIpOptions = new ArrayList<>();
        serverIpOptions.add("0.0.0.0 (所有网络接口)");
        serverIpOptions.add("127.0.0.1 (仅本地)");
        serverIpOptions.add("自定义");
        propertiesOptionsMap.put("server-ip", serverIpOptions);
        List<String> booleanOptions = new ArrayList<>();
        booleanOptions.add("true");
        booleanOptions.add("false");
        propertiesOptionsMap.put("force-gamemode", booleanOptions);
        propertiesOptionsMap.put("allow-flight", booleanOptions);
        propertiesOptionsMap.put("pvp", booleanOptions);
        propertiesOptionsMap.put("hardcore", booleanOptions);
        propertiesOptionsMap.put("force-gamemode", booleanOptions);
        propertiesOptionsMap.put("allow-flight", booleanOptions);
        propertiesOptionsMap.put("pvp", booleanOptions);
        propertiesOptionsMap.put("hardcore", booleanOptions);
        propertiesOptionsMap.put("generate-structures", booleanOptions);
        propertiesOptionsMap.put("spawn-npcs", booleanOptions);
        propertiesOptionsMap.put("spawn-animals", booleanOptions);
        propertiesOptionsMap.put("spawn-monsters", booleanOptions);
        propertiesOptionsMap.put("allow-nether", booleanOptions);
        propertiesOptionsMap.put("use-vanilla-world-seed", booleanOptions);
        propertiesOptionsMap.put("enable-query", booleanOptions);
        propertiesOptionsMap.put("enable-status", booleanOptions);
        propertiesOptionsMap.put("enable-rcon", booleanOptions);
        propertiesOptionsMap.put("enable-command-block", booleanOptions);
        propertiesOptionsMap.put("enable-jmx-monitoring", booleanOptions);
        propertiesOptionsMap.put("enable-code-of-conduct", booleanOptions);
        propertiesOptionsMap.put("sync-chunk-writes", booleanOptions);
        propertiesOptionsMap.put("log-ips", booleanOptions);
        propertiesOptionsMap.put("broadcast-console-to-ops", booleanOptions);
        propertiesOptionsMap.put("broadcast-rcon-to-ops", booleanOptions);
        propertiesOptionsMap.put("management-server-enabled", booleanOptions);
        propertiesOptionsMap.put("management-server-tls-enabled", booleanOptions);
        propertiesOptionsMap.put("use-native-transport", booleanOptions);
        propertiesOptionsMap.put("accepts-transfers", booleanOptions);
        propertiesOptionsMap.put("online-mode", booleanOptions);
        propertiesOptionsMap.put("white-list", booleanOptions);
        propertiesOptionsMap.put("enforce-whitelist", booleanOptions);
        propertiesOptionsMap.put("hide-online-players", booleanOptions);
        propertiesOptionsMap.put("prevent-proxy-connections", booleanOptions);
        propertiesOptionsMap.put("require-resource-pack", booleanOptions);
        propertiesOptionsMap.put("enforce-secure-profile", booleanOptions);
        propertiesOptionsMap.put("debug", booleanOptions);
    }
    private class ConfigTable extends JTable {
        public ConfigTable(DefaultTableModel model) {
            super(model);
        }
        @Override
        public TableCellEditor getCellEditor(int row, int column) {
            if (column == 1) {
                String displayKey = (String) getValueAt(row, 0);
                String originalKey = originalKeysMap.containsKey(displayKey) ? originalKeysMap.get(displayKey) : displayKey;
                ConfigType type = propertiesTypeMap.getOrDefault(originalKey, ConfigType.STRING);
                switch (type) {
                    case BOOLEAN:
                        return new BooleanCellEditor();
                    case NUMBER:
                        return new NumberCellEditor();
                    case SELECT:
                        List<String> options = propertiesOptionsMap.get(originalKey);
                        if (options != null) {
                            return new ComboBoxCellEditor(options);
                        }
                        break;
                    default:
                        break;
                }
            }
            return super.getCellEditor(row, column);
        }
    }
    private class BooleanCellEditor extends DefaultCellEditor {
        public BooleanCellEditor() {
            super(new JComboBox<>(new String[]{"true", "false"}));
        }
    }
    private class NumberCellEditor extends DefaultCellEditor {
        public NumberCellEditor() {
            super(new JFormattedTextField());
            JFormattedTextField textField = (JFormattedTextField) getComponent();
            NumberFormat format = NumberFormat.getIntegerInstance();
            format.setGroupingUsed(false);
            NumberFormatter formatter = new NumberFormatter(format);
            formatter.setAllowsInvalid(false);
            textField.setFormatterFactory(new DefaultFormatterFactory(formatter));
        }
    }
    @SuppressWarnings("unchecked")
    private class ComboBoxCellEditor extends DefaultCellEditor {
        private JComboBox<String> comboBox;
        private Map<String, String> displayToRealMap;
        private Map<String, String> realToDisplayMap;
        
        public ComboBoxCellEditor(List<String> displayOptions) {
            super(new JComboBox<>(displayOptions.toArray(new String[0])));
            comboBox = (JComboBox<String>) getComponent();
            displayToRealMap = new java.util.HashMap<>();
            realToDisplayMap = new java.util.HashMap<>();
            for (String option : displayOptions) {
                String realValue = extractRealValue(option);
                displayToRealMap.put(option, realValue);
                realToDisplayMap.put(realValue, option);
            }
        }
        
        private String extractRealValue(String displayText) {
            if (displayText.contains(" (")) {
                return displayText.substring(0, displayText.indexOf(" ("));
            }
            return displayText;
        }
        
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            comboBox = (JComboBox<String>) getComponent();
            if (value != null) {
                String displayText = realToDisplayMap.get(value.toString());
                if (displayText != null) {
                    comboBox.setSelectedItem(displayText);
                } else if (displayToRealMap.containsKey(value.toString())) {
                    comboBox.setSelectedItem(value.toString());
                }
            }
            return getComponent();
        }
        
        @Override
        public Object getCellEditorValue() {
            String selectedDisplay = (String) comboBox.getSelectedItem();
            if (selectedDisplay != null) {
                if (selectedDisplay.equals("自定义")) {
                    String customIp = (String) JOptionPane.showInputDialog(
                        comboBox,
                        "请输入自定义IP地址:",
                        "自定义服务器IP",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        null,
                        ""
                    );
                    if (customIp != null && !customIp.trim().isEmpty()) {
                        return customIp.trim();
                    }
                    return "";
                }
                String realValue = displayToRealMap.get(selectedDisplay);
                if (realValue != null) {
                    return realValue;
                }
            }
            return comboBox.getSelectedItem();
        }
    }
    private void loadConfigToTable() {
        tableModel.setRowCount(0); 
        originalKeysMap.clear(); 
        Map<String, Object> configData = configManager.getConfigData();
        
        java.util.List<String> priorityKeys = new ArrayList<>();
        priorityKeys.add("server-ip");
        priorityKeys.add("server-port");
        priorityKeys.add("motd");
        priorityKeys.add("max-players");
        priorityKeys.add("online-mode");
        priorityKeys.add("white-list");
        priorityKeys.add("enforce-whitelist");
        priorityKeys.add("pvp");
        priorityKeys.add("gamemode");
        priorityKeys.add("difficulty");
        priorityKeys.add("hardcore");
        priorityKeys.add("force-gamemode");
        priorityKeys.add("allow-flight");
        priorityKeys.add("level-name");
        priorityKeys.add("level-seed");
        priorityKeys.add("level-type");
        priorityKeys.add("spawn-protection");
        priorityKeys.add("spawn-npcs");
        priorityKeys.add("spawn-animals");
        priorityKeys.add("spawn-monsters");
        priorityKeys.add("allow-nether");
        priorityKeys.add("view-distance");
        priorityKeys.add("simulation-distance");
        priorityKeys.add("generate-structures");
        priorityKeys.add("max-build-height");
        priorityKeys.add("max-world-size");
        priorityKeys.add("enable-query");
        priorityKeys.add("enable-status");
        priorityKeys.add("enable-rcon");
        priorityKeys.add("rcon.port");
        priorityKeys.add("rcon.password");
        priorityKeys.add("resource-pack");
        priorityKeys.add("player-idle-timeout");
        priorityKeys.add("broadcast-console-to-ops");
        priorityKeys.add("broadcast-rcon-to-ops");
        priorityKeys.add("hide-online-players");
        priorityKeys.add("log-ips");
        priorityKeys.add("prevent-proxy-connections");
        priorityKeys.add("rate-limit");
        priorityKeys.add("use-native-transport");
        priorityKeys.add("network-compression-threshold");
        priorityKeys.add("sync-chunk-writes");
        priorityKeys.add("region-file-compression");
        priorityKeys.add("entity-broadcast-range-percentage");
        priorityKeys.add("pause-when-empty-seconds");
        priorityKeys.add("max-tick-time");
        priorityKeys.add("max-chained-neighbor-updates");
        priorityKeys.add("use-vanilla-world-seed");
        priorityKeys.add("generator-settings");
        priorityKeys.add("enable-command-block");
        priorityKeys.add("bug-report-link");
        priorityKeys.add("enforce-secure-profile");
        priorityKeys.add("enable-code-of-conduct");
        priorityKeys.add("text-filtering-version");
        priorityKeys.add("text-filtering-config");
        priorityKeys.add("accepts-transfers");
        priorityKeys.add("resource-pack-sha1");
        priorityKeys.add("resource-pack-prompt");
        priorityKeys.add("resource-pack-id");
        priorityKeys.add("initial-enabled-packs");
        priorityKeys.add("initial-disabled-packs");
        priorityKeys.add("function-permission-level");
        priorityKeys.add("op-permission-level");
        priorityKeys.add("enable-jmx-monitoring");
        priorityKeys.add("management-server-enabled");
        priorityKeys.add("management-server-host");
        priorityKeys.add("management-server-port");
        priorityKeys.add("management-server-secret");
        priorityKeys.add("management-server-tls-enabled");
        priorityKeys.add("management-server-tls-keystore");
        priorityKeys.add("management-server-tls-keystore-password");
        priorityKeys.add("status-heartbeat-interval");
        priorityKeys.add("require-resource-pack");
        
        Set<Map.Entry<String, Object>> entries = configData.entrySet();
        List<Map.Entry<String, Object>> sortedEntries = new ArrayList<>(entries);
        sortedEntries.sort((a, b) -> {
            int priorityA = priorityKeys.indexOf(a.getKey());
            int priorityB = priorityKeys.indexOf(b.getKey());
            if (priorityA >= 0 && priorityB >= 0) {
                return Integer.compare(priorityA, priorityB);
            } else if (priorityA >= 0) {
                return -1;
            } else if (priorityB >= 0) {
                return 1;
            }
            return a.getKey().compareTo(b.getKey());
        });
        
        for (Map.Entry<String, Object> entry : sortedEntries) {
            String originalKey = entry.getKey();
            String displayKey = originalKey;
            if (selectedConfigFile.getName().equals("server.properties") && propertiesChineseMap.containsKey(originalKey)) {
                displayKey = propertiesChineseMap.get(originalKey);
                originalKeysMap.put(displayKey, originalKey);
            }
            Object value = entry.getValue();
            tableModel.addRow(new Object[]{displayKey, value != null ? value.toString() : ""});
        }
    }
    private void loadJsonToTextArea() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(selectedConfigFile), java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
            reader.close();
            jsonTextArea.setText(content.toString());
        } catch (IOException e) {
            Logger.error("Failed to load JSON configuration to text area: " + e.getMessage(), "ConfigEditor");
        }
    }
    private void loadListToTable() {
        listTableModel.setRowCount(0); 
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(selectedConfigFile), java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            reader.close();
            JsonArray jsonArray = JsonParser.parseString(content.toString()).getAsJsonArray();
            for (JsonElement element : jsonArray) {
                JsonObject jsonObject = element.getAsJsonObject();
                String name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : "";
                String uuid = jsonObject.has("uuid") ? jsonObject.get("uuid").getAsString() : "";
                String createdAt = jsonObject.has("created") ? jsonObject.get("created").getAsString() : "";
                String source = jsonObject.has("source") ? jsonObject.get("source").getAsString() : "";
                String level = jsonObject.has("level") ? jsonObject.get("level").getAsString() : "";
                listTableModel.addRow(new Object[]{name, uuid, createdAt, source, level});
            }
        } catch (IOException e) {
            Logger.error("Failed to load list configuration to table: " + e.getMessage(), "ConfigEditor");
        }
    }
    private void saveConfig() {
        if (selectedConfigFile.getName().equals("server-icon.png")) {
            saveIcon();
        } else if (selectedConfigFile.getName().endsWith(".json")) {
            if (isListTypeConfig(selectedConfigFile.getName())) {
                saveListConfig();
            } else {
                Map<String, Object> configData = configManager.getConfigData();
                if (!configData.isEmpty()) {
                    Object firstValue = configData.values().iterator().next();
                    if (firstValue instanceof com.google.gson.JsonArray) {
                        saveJsonText();
                    } else {
                        saveTableConfig();
                    }
                } else {
                    saveJsonText();
                }
            }
        } else {
            saveTableConfig();
        }
        statusLabel.setText("配置已保存: " + selectedConfigFile.getName());
        JOptionPane.showMessageDialog(this, "配置已成功保存!");
    }
    private void saveTableConfig() {
        if (!selectedConfigFile.exists()) {
            statusLabel.setText("配置文件不存在，无法保存!");
            JOptionPane.showMessageDialog(this, "配置文件不存在，无法保存!");
            return;
        }
        Map<String, Object> configData = configManager.getConfigData();
        configData.clear();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String displayKey = (String) tableModel.getValueAt(i, 0);
            String value = (String) tableModel.getValueAt(i, 1);
            String originalKey = originalKeysMap.containsKey(displayKey) ? originalKeysMap.get(displayKey) : displayKey;
            configData.put(originalKey, value);
        }
        configManager.saveConfig();
    }
    private void addPlayer() {
        String playerName = playerNameField.getText().trim();
        if (playerName.isEmpty()) {
            Logger.warn("Attempted to add empty player name to configuration", "ConfigEditor");
            JOptionPane.showMessageDialog(this, "请输入玩家名称!");
            return;
        }
        for (int i = 0; i < listTableModel.getRowCount(); i++) {
            String existingName = (String) listTableModel.getValueAt(i, 0);
            if (existingName.equalsIgnoreCase(playerName)) {
                Logger.warn("Attempted to add duplicate player: " + playerName, "ConfigEditor");
                JOptionPane.showMessageDialog(this, "玩家已存在!");
                return;
            }
        }
        String defaultLevel = selectedConfigFile.getName().equals("ops.json") ? "4" : "";
        listTableModel.addRow(new Object[]{playerName, "", "", "控制台", defaultLevel});
        playerNameField.setText("");
    }
    private void removeSelectedPlayer() {
        int selectedRow = listTable.getSelectedRow();
        if (selectedRow == -1) {
            Logger.warn("Attempted to remove player without selecting any row", "ConfigEditor");
            JOptionPane.showMessageDialog(this, "请先选择要删除的玩家!");
            return;
        }
        Logger.info("Removing player from configuration", "ConfigEditor");
        listTableModel.removeRow(selectedRow);
    }
    private void saveListConfig() {
        if (!selectedConfigFile.exists()) {
            statusLabel.setText("配置文件不存在，无法保存!");
            JOptionPane.showMessageDialog(this, "配置文件不存在，无法保存!");
            return;
        }
        try {
            JsonArray jsonArray = new JsonArray();
            for (int i = 0; i < listTableModel.getRowCount(); i++) {
                JsonObject jsonObject = new JsonObject();
                String name = (String) listTableModel.getValueAt(i, 0);
                String uuid = (String) listTableModel.getValueAt(i, 1);
                String createdAt = (String) listTableModel.getValueAt(i, 2);
                String source = (String) listTableModel.getValueAt(i, 3);
                String level = (String) listTableModel.getValueAt(i, 4);
                jsonObject.addProperty("name", name);
                if (!uuid.isEmpty()) {
                    jsonObject.addProperty("uuid", uuid);
                }
                if (!createdAt.isEmpty()) {
                    jsonObject.addProperty("created", createdAt);
                } else {
                    jsonObject.addProperty("created", System.currentTimeMillis());
                }
                if (!source.isEmpty()) {
                    jsonObject.addProperty("source", source);
                }
                if (selectedConfigFile.getName().equals("ops.json")) {
                    int opLevel = 4; 
                    if (!level.isEmpty()) {
                        try {
                            opLevel = Integer.parseInt(level);
                        } catch (NumberFormatException e) {
                            Logger.error("Invalid OP level format in configuration, using default: " + level, "ConfigEditor");
                        }
                    }
                    jsonObject.addProperty("level", opLevel);
                    jsonObject.addProperty("bypassesPlayerLimit", false);
                } else if (!level.isEmpty()) {
                    jsonObject.addProperty("level", level);
                }
                jsonArray.add(jsonObject);
            }
            String jsonString = gson.toJson(jsonArray);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(selectedConfigFile), StandardCharsets.UTF_8));
            writer.write(jsonString);
            writer.close();
        } catch (IOException e) {
            Logger.error("Failed to save list configuration: " + e.getMessage(), "ConfigEditor");
            JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage());
        }
    }
    private void saveJsonText() {
        if (!selectedConfigFile.exists()) {
            statusLabel.setText("配置文件不存在，无法保存!");
            JOptionPane.showMessageDialog(this, "配置文件不存在，无法保存!");
            return;
        }
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(selectedConfigFile), StandardCharsets.UTF_8));
            writer.write(jsonTextArea.getText());
            writer.close();
        } catch (IOException e) {
            Logger.error("Failed to save JSON text configuration: " + e.getMessage(), "ConfigEditor");
            JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage());
        }
    }
    private void loadIconToPreview() {
        try {
            if (selectedConfigFile.exists()) {
                currentIconImage = ImageIO.read(selectedConfigFile);
                ImageIcon icon = new ImageIcon(currentIconImage.getScaledInstance(64, 64, Image.SCALE_SMOOTH));
                iconPreviewLabel.setIcon(icon);
                iconPreviewLabel.setText("");
            } else {
                iconPreviewLabel.setIcon(null);
                iconPreviewLabel.setText("暂无图标");
                currentIconImage = null;
            }
        } catch (IOException e) {
            Logger.error("Failed to load icon preview: " + e.getMessage(), "ConfigEditor");
            iconPreviewLabel.setIcon(null);
            iconPreviewLabel.setText("图标加载失败");
            currentIconImage = null;
        }
    }
    private void changeIcon() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG 图片", "png"));
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                BufferedImage newImage = ImageIO.read(selectedFile);
                if (newImage != null) {
                    currentIconImage = newImage;
                    ImageIcon icon = new ImageIcon(newImage.getScaledInstance(64, 64, Image.SCALE_SMOOTH));
                    iconPreviewLabel.setIcon(icon);
                    iconPreviewLabel.setText("");
                    statusLabel.setText("已选择新图标: " + selectedFile.getName());
                } else {
                    JOptionPane.showMessageDialog(this, "无法读取图片文件!");
                }
            } catch (IOException e) {
                Logger.error("Failed to change icon: " + e.getMessage(), "ConfigEditor");
                JOptionPane.showMessageDialog(this, "图片加载失败: " + e.getMessage());
            }
        }
    }
    private void removeIcon() {
        int result = JOptionPane.showConfirmDialog(this, "确定要删除服务器图标吗?", "确认删除", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            if (selectedConfigFile.exists()) {
                selectedConfigFile.delete();
            }
            currentIconImage = null;
            iconPreviewLabel.setIcon(null);
            iconPreviewLabel.setText("暂无图标");
            statusLabel.setText("图标已删除");
        }
    }
    private void saveIcon() {
        if (currentIconImage != null) {
            try {
                ImageIO.write(currentIconImage, "png", selectedConfigFile);
                statusLabel.setText("图标已保存");
            } catch (IOException e) {
                Logger.error("Failed to save icon: " + e.getMessage(), "ConfigEditor");
                JOptionPane.showMessageDialog(this, "图标保存失败: " + e.getMessage());
            }
        }
    }
}
