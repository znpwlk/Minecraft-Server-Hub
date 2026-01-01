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
    private int loadedRulesCount = 0;
    private int totalRulesToLoad = 0;
    private volatile boolean serverAvailable = false;
    private final Map<String, String> currentRuleValues = new HashMap<>();
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);
    private final AtomicBoolean isLoading = new AtomicBoolean(false);

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
            setSize(500, 650);
            setLocationRelativeTo(getParent());
            setResizable(true);

            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

            JPanel titlePanel = new JPanel(new BorderLayout());
            JLabel titleLabel = new JLabel("游戏规则调整");
            titleLabel.setFont(new Font(null, Font.BOLD, 18));
            titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
            titlePanel.add(titleLabel, BorderLayout.CENTER);

            JPanel statusPanel = new JPanel(new BorderLayout());
            statusPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
            statusLabel = new JLabel("正在查询服务器...");
            statusLabel.setFont(new Font(null, Font.PLAIN, 12));
            statusPanel.add(statusLabel, BorderLayout.CENTER);
            titlePanel.add(statusPanel, BorderLayout.SOUTH);

            mainPanel.add(titlePanel, BorderLayout.NORTH);

            JPanel rulesPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;

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
                    } catch (Exception e) {
                    }
                }
                totalRulesToLoad = row;
            }

            JScrollPane scrollPane = new JScrollPane(rulesPanel);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            mainPanel.add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton refreshButton = new JButton("刷新状态");
            JButton closeButton = new JButton("关闭");

            refreshButton.addActionListener(e -> {
                if (!isDisposed.get() && !isLoading.getAndSet(true)) {
                    checkServerAndLoadRules();
                    isLoading.set(false);
                }
            });
            closeButton.addActionListener(e -> dispose());

            buttonPanel.add(refreshButton);
            buttonPanel.add(closeButton);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);

            add(mainPanel);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "初始化界面失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            dispose();
        }
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
                    jarRunner.sendCommand("gamerule " + rule.getName() + " " + newValue);
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
                    jarRunner.sendCommand("gamerule " + rule.getName() + " " + newValue);
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
        jarRunner.getOutputPanel().append("[MSH] 正在查询游戏规则...\n");
        for (GameRuleConfig.GameRule rule : GameRuleConfig.getGameRules()) {
            jarRunner.sendCommand("gamerule " + rule.getName());
        }
    }

    public void updateRuleValue(String ruleName, String value) {
        GameRuleConfig.GameRule rule = rulesMap.get(ruleName);
        if (rule == null) return;

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
