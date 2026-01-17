import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameRuleDialog extends JDialog {
    private final JarRunner jarRunner;
    private final Map<String, JComponent> ruleComponents = new HashMap<>();
    private final Map<String, GameRuleConfig.GameRule> rulesMap = new HashMap<>();
    private JLabel statusLabel;
    private JRadioButton versionOldButton;
    private JRadioButton versionNewButton;
    private ButtonGroup versionButtonGroup;
    private int loadedRulesCount = 0;
    private int totalRulesToLoad = 0;
    private volatile boolean serverAvailable = false;
    private final Map<String, String> currentRuleValues = new HashMap<>();
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);
    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private JLabel configSourceLabel;
    private static final int DIALOG_WIDTH = 540;
    private static final int DIALOG_HEIGHT = 700;

    public static boolean showDialog(JFrame parent, JarRunner jarRunner) {
        if (jarRunner == null || jarRunner.getStatus() != JarRunner.Status.RUNNING) {
            JOptionPane.showMessageDialog(parent,
                "服务器未运行，无法调整游戏规则",
                "无法操作",
                JOptionPane.WARNING_MESSAGE);
            return false;
        }

        try {
            GameRuleDialog dialog = new GameRuleDialog(parent, jarRunner);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setVisible(true);
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent,
                "打开游戏规则窗口失败: " + e.getMessage(),
                "错误",
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public GameRuleDialog(JFrame parent, JarRunner jarRunner) {
        super(parent, "游戏规则调整", true);
        if (jarRunner == null) {
            throw new IllegalArgumentException("JarRunner cannot be null");
        }
        this.jarRunner = jarRunner;
        try {
            jarRunner.setGameRuleCallback(this::handleGameRuleValue);
        } catch (Exception e) {
        }
        initUI();
        checkServerAndLoadRules();
    }

    @Override
    public void dispose() {
        if (isDisposed.getAndSet(true)) {
            return;
        }
        try {
            if (jarRunner != null) {
                jarRunner.setGameRuleCallback(null);
            }
            ruleComponents.clear();
            rulesMap.clear();
            currentRuleValues.clear();
        } catch (Exception e) {
        }
        super.dispose();
    }

    private void handleGameRuleValue(String ruleName, String value) {
        if (isDisposed.get() || ruleName == null || value == null) {
            return;
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            updateRuleValue(ruleName, value);
        });
    }

    private void initUI() {
        try {
            setLayout(new BorderLayout());
            setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
            setLocationRelativeTo(getParent());
            setResizable(true);

            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

            JPanel titlePanel = new JPanel(new BorderLayout(10, 10));

            JPanel titleLabelPanel = new JPanel(new BorderLayout());
            JLabel titleLabel = new JLabel("游戏规则调整");
            titleLabel.setFont(new Font(null, Font.BOLD, 18));
            titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
            titleLabelPanel.add(titleLabel, BorderLayout.CENTER);

            JPanel versionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            JLabel versionLabel = new JLabel("MC版本:");
            versionLabel.setFont(new Font(null, Font.PLAIN, 12));
            versionPanel.add(versionLabel);

            versionButtonGroup = new ButtonGroup();
            versionOldButton = new JRadioButton("1.21.10及以前", GameRuleConfig.getCurrentVersion() == GameRuleConfig.MCVersion.V1_21_10);
            versionOldButton.setFont(new Font(null, Font.PLAIN, 12));
            versionNewButton = new JRadioButton("1.21.11+", GameRuleConfig.getCurrentVersion() == GameRuleConfig.MCVersion.V1_21_11);
            versionNewButton.setFont(new Font(null, Font.PLAIN, 12));

            versionButtonGroup.add(versionOldButton);
            versionButtonGroup.add(versionNewButton);
            versionPanel.add(versionOldButton);
            versionPanel.add(versionNewButton);

            versionOldButton.addActionListener(e -> {
                if (versionOldButton.isSelected()) {
                    GameRuleConfig.setCurrentVersion(GameRuleConfig.MCVersion.V1_21_10);
                    reloadRules();
                }
            });

            versionNewButton.addActionListener(e -> {
                if (versionNewButton.isSelected()) {
                    GameRuleConfig.setCurrentVersion(GameRuleConfig.MCVersion.V1_21_11);
                    reloadRules();
                }
            });

            versionPanel.add(versionNewButton);

            titlePanel.add(titleLabelPanel, BorderLayout.NORTH);
            titlePanel.add(versionPanel, BorderLayout.SOUTH);

            JPanel statusPanel = new JPanel(new BorderLayout());
            statusPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
            statusLabel = new JLabel("正在查询服务器...");
            statusLabel.setFont(new Font(null, Font.PLAIN, 12));
            statusPanel.add(statusLabel, BorderLayout.CENTER);

            configSourceLabel = new JLabel();
            configSourceLabel.setFont(new Font(null, Font.PLAIN, 11));
            configSourceLabel.setForeground(new Color(100, 100, 100));
            statusPanel.add(configSourceLabel, BorderLayout.EAST);

            titlePanel.add(statusPanel, BorderLayout.SOUTH);

            mainPanel.add(titlePanel, BorderLayout.NORTH);

            JPanel rulesPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;

            updateConfigSource();
            List<GameRuleConfig.GameRule> rules = GameRuleConfig.getGameRules();
            if (rules != null) {
                int row = 0;
                for (GameRuleConfig.GameRule rule : rules) {
                    if (rule == null || rule.getName() == null) continue;
                    try {
                        JPanel rulePanel = createRulePanel(rule);
                        if (rulePanel != null) {
                            gbc.gridx = 0;
                            gbc.gridy = row;
                            rulesPanel.add(rulePanel, gbc);
                            row++;
                            rulesMap.put(rule.getName(), rule);
                        }
                    } catch (Exception ex) {
                    }
                }
                totalRulesToLoad = row;
            }

            JScrollPane scrollPane = new JScrollPane(rulesPanel);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            mainPanel.add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton refreshButton = new JButton("刷新状态");
            JButton refreshConfigButton = new JButton("刷新配置");
            JButton closeButton = new JButton("关闭");

            if (JsonGameRuleLoader.isLoadedFromRemote()) {
                refreshConfigButton.setEnabled(true);
            } else {
                refreshConfigButton.setEnabled(false);
            }

            refreshButton.addActionListener(e -> {
                if (!isDisposed.get() && !isLoading.getAndSet(true)) {
                    checkServerAndLoadRules();
                    isLoading.set(false);
                }
            });

            refreshConfigButton.addActionListener(e -> {
                if (!isDisposed.get()) {
                    JsonGameRuleLoader.refreshFromRemote();
                    GameRuleConfig.clearCache();
                    jarRunner.getOutputPanel().append("[MSH] 正在从云端刷新配置...\n");
                    reloadRules();
                    jarRunner.getOutputPanel().append("[MSH] 配置已刷新\n");
                }
            });

            closeButton.addActionListener(e -> dispose());

            buttonPanel.add(refreshButton);
            buttonPanel.add(refreshConfigButton);
            buttonPanel.add(closeButton);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);

            add(mainPanel);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "初始化界面失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            dispose();
        }
    }

    private void updateConfigSource() {
        if (GameRuleConfig.isUsingJsonConfig()) {
            configSourceLabel.setText("[JSON:" + GameRuleConfig.getActiveJsonVersion() + "]");
        } else if (GameRuleConfig.isUseJsonConfig()) {
            configSourceLabel.setText("[内置配置]");
        } else {
            configSourceLabel.setText("[内置配置]");
        }
    }

    private void reloadRules() {
        ruleComponents.clear();
        rulesMap.clear();
        currentRuleValues.clear();
        getContentPane().removeAll();
        loadedRulesCount = 0;
        updateConfigSource();
        initUI();
        revalidate();
        repaint();
        checkServerAndLoadRules();
    }

    private JPanel createRulePanel(GameRuleConfig.GameRule rule) {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        JPanel labelPanel = new JPanel(new BorderLayout());
        JLabel nameLabel = new JLabel(rule.getDisplayName());
        nameLabel.setFont(new Font(null, Font.BOLD, 13));

        JLabel descLabel = new JLabel(rule.getDescription());
        descLabel.setFont(new Font(null, Font.PLAIN, 11));
        descLabel.setForeground(Color.GRAY);

        labelPanel.add(nameLabel, BorderLayout.NORTH);
        labelPanel.add(descLabel, BorderLayout.SOUTH);
        panel.add(labelPanel, BorderLayout.WEST);

        JComponent valueComponent;
        if (rule.isBoolean()) {
            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(rule.getDefaultBoolean());
            checkBox.setEnabled(false);
            checkBox.addActionListener(e -> {
                if (serverAvailable) {
                    boolean newValue = checkBox.isSelected();
                    String formattedName = GameRuleConfig.getFormattedRuleName(rule.getName(), GameRuleConfig.getCurrentVersion());
                    jarRunner.sendCommand("gamerule " + formattedName + " " + newValue);
                }
            });
            valueComponent = checkBox;
        } else {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                rule.getDefaultInt(), 0, 1000000, 1
            ));
            spinner.setEnabled(false);
            spinner.addChangeListener(e -> {
                if (serverAvailable) {
                    int newValue = (Integer) spinner.getValue();
                    String formattedName = GameRuleConfig.getFormattedRuleName(rule.getName(), GameRuleConfig.getCurrentVersion());
                    jarRunner.sendCommand("gamerule " + formattedName + " " + newValue);
                }
            });
            valueComponent = spinner;
        }
        ruleComponents.put(rule.getName(), valueComponent);
        panel.add(valueComponent, BorderLayout.EAST);

        return panel;
    }

    private void checkServerAndLoadRules() {
        serverAvailable = true;
        totalRulesToLoad = GameRuleConfig.getGameRules().size();
        loadedRulesCount = 0;
        statusLabel.setText("正在查询服务器...");
        jarRunner.getOutputPanel().append("[MSH] 正在查询游戏规则 (MC " + GameRuleConfig.getCurrentVersion().getDisplayName() + ")...\n");
        for (GameRuleConfig.GameRule rule : GameRuleConfig.getGameRules()) {
            String formattedName = GameRuleConfig.getFormattedRuleName(rule.getName(), GameRuleConfig.getCurrentVersion());
            jarRunner.sendCommand("gamerule " + formattedName);
        }
    }

    public void updateRuleValue(String ruleName, String value) {
        GameRuleConfig.GameRule rule = rulesMap.get(ruleName);
        if (rule == null) {
            GameRuleConfig.GameRule allVersionRule = GameRuleConfig.findByNameInAllVersions(ruleName);
            if (allVersionRule != null) {
                rule = allVersionRule;
                rulesMap.put(ruleName, rule);
                JPanel rulesParent = (JPanel) ((JScrollPane) getContentPane().getComponent(0)).getViewport().getView();
                if (rulesParent instanceof JPanel) {
                    int row = rulesMap.size() - 1;
                    JPanel rulePanel = createRulePanel(rule);
                    if (rulePanel != null) {
                        GridBagConstraints gbc = new GridBagConstraints();
                        gbc.insets = new Insets(4, 4, 4, 4);
                        gbc.anchor = GridBagConstraints.WEST;
                        gbc.fill = GridBagConstraints.HORIZONTAL;
                        gbc.weightx = 1.0;
                        gbc.gridx = 0;
                        gbc.gridy = row;
                        rulesParent.add(rulePanel, gbc);
                        totalRulesToLoad++;
                        rulesParent.revalidate();
                        rulesParent.repaint();
                    }
                }
            } else {
                return;
            }
        }

        JComponent component = ruleComponents.get(ruleName);
        if (component == null) return;

        currentRuleValues.put(ruleName, value);
        component.setEnabled(true);
        component.setForeground(Color.BLACK);

        if (rule.isBoolean()) {
            ((JCheckBox) component).setSelected(Boolean.parseBoolean(value));
        } else {
            try {
                ((JSpinner) component).setValue(Integer.parseInt(value));
            } catch (NumberFormatException e) {
            }
        }

        loadedRulesCount++;
        if (statusLabel != null) {
            statusLabel.setText("已加载 " + loadedRulesCount + "/" + totalRulesToLoad + " 条规则");
        }
    }

    public boolean isServerAvailable() {
        return serverAvailable;
    }
}
