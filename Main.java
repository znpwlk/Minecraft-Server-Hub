import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
public class Main {
    private static final String VERSION = "1.0.5";
    private static final String AUTHOR = "znpwlk";
    private static final String APP_NAME = "Minecraft Server Hub";
    private static final String APP_SHORT_NAME = "MSH";
    private static Main instance;
    private JFrame frame;
    private JTabbedPane tabbedPane;
    private List<JarRunner> jarRunners;
    private Properties config;
    private File configFile;
    private JTextArea logTextArea;
    private List<TabLabel> tabLabels = new ArrayList<>();
    
    private static class TabLabel extends JPanel {
        private JLabel label;
        private static final int MAX_WIDTH = 100;
        
        public TabLabel(String text, Font font) {
            setOpaque(false);
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(MAX_WIDTH, 25));
            setMaximumSize(new Dimension(MAX_WIDTH, 25));
            
            label = new JLabel(text == null ? "" : text);
            label.setFont(font);
            if (text != null && text.length() > 12) {
                setToolTipText(text);
            }
            add(label, BorderLayout.WEST);
            
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (e.getButton() == java.awt.event.MouseEvent.BUTTON3) {
                        e.consume();
                        handleRightClick(e);
                    } else if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                        Container parent = getParent();
                        while (parent != null) {
                            if (parent instanceof JTabbedPane) {
                                JTabbedPane tabPane = (JTabbedPane) parent;
                                for (int i = 0; i < tabPane.getTabCount(); i++) {
                                    if (tabPane.getTabComponentAt(i) == TabLabel.this) {
                                        tabPane.setSelectedIndex(i);
                                        break;
                                    }
                                }
                                break;
                            }
                            parent = parent.getParent();
                        }
                    }
                }
                
                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (e.getButton() == java.awt.event.MouseEvent.BUTTON3) {
                        e.consume();
                        handleRightClick(e);
                    }
                }
            });
        }
        
        private void handleRightClick(java.awt.event.MouseEvent e) {
             int index = -1;
             Container parent = getParent();
             while (parent != null) {
                 if (parent instanceof JTabbedPane) {
                     JTabbedPane tabbedPane = (JTabbedPane) parent;
                     for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                         if (tabbedPane.getTabComponentAt(i) == TabLabel.this) {
                             index = i;
                             break;
                         }
                     }
                     break;
                 }
                 parent = parent.getParent();
             }
             if (index >= 0) {
                 Point screenPoint = SwingUtilities.convertPoint(TabLabel.this, e.getX(), e.getY(), Main.getInstance().getFrame());
                 Main.getInstance().showTabContextMenu(index, screenPoint.x, screenPoint.y);
             }
         }
        
        public void setText(String text) {
            label.setText(text == null ? "" : text);
            if (text != null && text.length() > 12) {
                setToolTipText(text);
            } else {
                setToolTipText(null);
            }
        }
    }
    
    public static Main getInstance() {
        return instance;
    }
    
    public JFrame getFrame() {
        return frame;
    }
    
    public JarRunner findJarRunner(String jarPath) {
        for (JarRunner runner : jarRunners) {
            if (runner.getJarPath().equals(jarPath)) {
                return runner;
            }
        }
        return null;
    }
    
    public static void main(String[] args) {
        String osName = System.getProperty("os.name").toLowerCase();
        String charset = "UTF-8";
        
        if (osName.contains("windows")) {
            charset = "UTF-8";
        }
        
        System.setProperty("file.encoding", charset);
        System.setProperty("sun.jnu.encoding", charset);
        System.setProperty("console.encoding", charset);
        
        try {
            System.setOut(new java.io.PrintStream(System.out, true, charset));
            System.setErr(new java.io.PrintStream(System.err, true, charset));
        } catch (java.io.UnsupportedEncodingException e) {
            Logger.error("Failed to set console encoding: " + e.getMessage(), "Main");
        }
        
        try {
            Logger.error("CRITICAL: Application startup initiated - JVM may be unstable", "Main");
            SwingUtilities.invokeLater(() -> {
                try {
                    new Main().createAndShowGUI();
                } catch (OutOfMemoryError e) {
                    Logger.error("FATAL: Out of memory during GUI initialization - application cannot continue", "Main");
                    System.err.println("FATAL ERROR: Out of memory. Application cannot continue.");
                    System.exit(1);
                } catch (StackOverflowError e) {
                    Logger.error("FATAL: Stack overflow during GUI initialization - application cannot continue", "Main");
                    System.err.println("FATAL ERROR: Stack overflow. Application cannot continue.");
                    System.exit(1);
                } catch (ExceptionInInitializerError e) {
                    Logger.error("FATAL: Class initialization error during startup: " + e.getMessage(), "Main");
                    System.err.println("FATAL ERROR: Class initialization failed. Application cannot start.");
                    System.exit(1);
                } catch (NoClassDefFoundError e) {
                    Logger.error("FATAL: Required class not found during startup: " + e.getMessage(), "Main");
                    System.err.println("FATAL ERROR: Missing required classes. Application cannot start.");
                    System.exit(1);
                } catch (SecurityException e) {
                    Logger.error("FATAL: Security violation during application startup: " + e.getMessage(), "Main");
                    System.err.println("FATAL ERROR: Security violation. Application cannot start.");
                    System.exit(1);
                } catch (VirtualMachineError e) {
                    Logger.error("FATAL: JVM internal error during startup: " + e.getClass().getSimpleName() + " - " + e.getMessage(), "Main");
                    System.err.println("FATAL ERROR: JVM internal error. Application cannot continue.");
                    System.exit(1);
                } catch (Exception e) {
                    Logger.error("FATAL: Unexpected exception during GUI initialization: " + e.getClass().getSimpleName() + " - " + e.getMessage(), "Main");
                    System.err.println("FATAL ERROR: Application startup failed.");
                    System.exit(1);
                }
            });
        } catch (Throwable t) {
            Logger.error("CRITICAL: Fatal error in main method - application cannot start: " + t.getClass().getSimpleName() + " - " + t.getMessage(), "Main");
            System.err.println("CRITICAL ERROR: Application cannot start. Shutting down.");
            System.exit(1);
        }
    }
    private void createAndShowGUI() {
        instance = this;
        Logger.info("Application started", "Main");
        config = new Properties();
        File mshDir = new File("MSH");
        if (!mshDir.exists()) {
            mshDir.mkdirs();
        }
        configFile = new File(mshDir, "server_manager_config.properties");
        loadConfig();
        Logger.info("Configuration loaded successfully", "Main");
        frame = new JFrame(APP_NAME + " (" + APP_SHORT_NAME + ")");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                boolean hasRunningServer = !jarRunners.isEmpty() && jarRunners.stream().anyMatch(runner -> 
                    runner.getStatus() == JarRunner.Status.RUNNING || runner.getStatus() == JarRunner.Status.STARTING);
                if (hasRunningServer) {
                    String[] options = {"关闭服务器并退出", "让服务器在后台运行", "取消"};
                    int choice = JOptionPane.showOptionDialog(
                        frame,
                        "服务器正在运行中，您希望：\n1. 关闭服务器并退出程序\n2. 让服务器在后台运行，仅关闭窗口\n3. 取消关闭操作",
                        "服务器运行状态",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[0]
                    );
                    switch (choice) {
                        case 0: 
                            handleWindowClosing();
                            break;
                        case 1: 
                            System.exit(0);
                            break;
                        case 2: 
                            break;
                    }
                } else {
                    handleWindowClosing();
                }
            }
            @Override
            public void windowDeiconified(WindowEvent e) {
                if (!jarRunners.isEmpty() && jarRunners.stream().anyMatch(runner -> runner.getStatus() == JarRunner.Status.RUNNING)) {
                    JOptionPane.showMessageDialog(frame, "服务器正在运行中，请不要关闭此窗口！", "提示", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });
        frame.setSize(1200, 800);
        frame.setLayout(new BorderLayout());
        jarRunners = new ArrayList<>();
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addServerButton = new JButton("添加服务器");
        addServerButton.addActionListener(this::addServer);
        topPanel.add(addServerButton);
        
        JButton aboutButton = new JButton("关于");
        aboutButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(frame, 
                APP_NAME + " (" + APP_SHORT_NAME + ")\n\n" +
                "版本: " + VERSION + "\n" +
                "作者: " + AUTHOR + "\n" +
                "官网: https://msh.znpwlk.vip/\n" +
                "GitHub: https://github.com/znpwlk/Minecraft-Server-Hub\n" +
                "功能: 管理Minecraft服务器", 
                "关于", JOptionPane.INFORMATION_MESSAGE);
        });
        topPanel.add(aboutButton);
        tabbedPane = new JTabbedPane();
        tabbedPane.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int tabIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
                    showTabContextMenu(tabIndex, e.getX(), e.getY());
                }
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int tabIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
                    showTabContextMenu(tabIndex, e.getX(), e.getY());
                }
            }
        });
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(tabbedPane, BorderLayout.CENTER);
        
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logTextArea.setBackground(Color.BLACK);
        logTextArea.setForeground(Color.WHITE);
        JScrollPane logScrollPane = new JScrollPane(logTextArea);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        logScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        Logger.getInstance().setLogTextArea(logTextArea);
        Logger.info("Logging system initialized", "Main");
        
        JPanel logPanel = new JPanel(new BorderLayout());
        JPanel logButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clearLogButton = new JButton("清空日志");
        clearLogButton.addActionListener(e -> {
            Logger.getInstance().clearLogDisplay();
        });
        logButtonPanel.add(clearLogButton);
        logPanel.add(logButtonPanel, BorderLayout.NORTH);
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        
        tabbedPane.addTab("程序日志", logPanel);
        String lastServerPath = config.getProperty("last_server_path");
        if (lastServerPath != null && !lastServerPath.isEmpty()) {
            File lastServer = new File(lastServerPath);
            if (lastServer.exists()) {
                SwingUtilities.invokeLater(() -> {
                    addServerFromPath(lastServerPath);
                });
            } else {
                Logger.warn("Last server path from configuration no longer exists: " + lastServerPath, "Main");
            }
        }
        frame.setVisible(true);
    }
    private void loadConfig() {
        try (InputStream input = new FileInputStream(configFile)) {
            config.load(input);
        } catch (FileNotFoundException e) {
            config.setProperty("last_server_path", "");
            saveConfig();
        } catch (IOException e) {
            Logger.error("Failed to load configuration file: " + e.getMessage(), "Main");
        }
    }
    private void saveConfig() {
        try (OutputStream output = new FileOutputStream(configFile)) {
            config.store(output, APP_NAME + " Configuration");
        } catch (IOException e) {
            Logger.error("Failed to save configuration file: " + e.getMessage(), "Main");
        }
    }

    private void saveCustomName(String jarPath, String customName) {
        String key = "customname." + jarPath;
        if (customName == null || customName.trim().isEmpty()) {
            config.remove(key);
        } else {
            config.setProperty(key, customName);
        }
        saveConfig();
    }

    private String loadCustomName(String jarPath) {
        String key = "customname." + jarPath;
        return config.getProperty(key);
    }

    private void saveGuardConfig(String jarPath, boolean enabled, int maxAttempts, int interval) {
        String keyPrefix = "guard." + jarPath + ".";
        config.setProperty(keyPrefix + "enabled", String.valueOf(enabled));
        config.setProperty(keyPrefix + "maxAttempts", String.valueOf(maxAttempts));
        config.setProperty(keyPrefix + "interval", String.valueOf(interval));
        saveConfig();
    }

    private Object[] loadGuardConfig(String jarPath) {
        String keyPrefix = "guard." + jarPath + ".";
        String enabledStr = config.getProperty(keyPrefix + "enabled");
        String maxAttemptsStr = config.getProperty(keyPrefix + "maxAttempts");
        String intervalStr = config.getProperty(keyPrefix + "interval");

        if (enabledStr == null || maxAttemptsStr == null || intervalStr == null) {
            return null;
        }

        return new Object[]{
            Boolean.parseBoolean(enabledStr),
            Integer.parseInt(maxAttemptsStr),
            Integer.parseInt(intervalStr)
        };
    }
    
    private void showGuardSettingsDialog(JarRunner jarRunner) {
        JDialog dialog = new JDialog(frame, "进程守护设置", true);
        dialog.setLayout(new BorderLayout(20, 20));
        dialog.setSize(500, 350);
        dialog.setLocationRelativeTo(frame);
        dialog.setResizable(true);
        
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("进程守护设置");
        titleLabel.setFont(new Font(null, Font.BOLD, 18));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        topPanel.add(titleLabel, BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        JLabel enableLabel = new JLabel("启用进程守护:");
        JCheckBox enableCheckBox = new JCheckBox();
        enableCheckBox.setSelected(jarRunner.isAutoRestartEnabled());
        
        gbc.gridx = 0; gbc.gridy = 0;
        settingsPanel.add(enableLabel, gbc);
        gbc.gridx = 1;
        settingsPanel.add(enableCheckBox, gbc);
        
        JLabel maxAttemptsLabel = new JLabel("每小时最大重启次数:");
        JSpinner maxAttemptsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
        int[] currentSettings = jarRunner.getRestartSettings();
        int currentAttempts = jarRunner.getCurrentHourlyAttempts();
        maxAttemptsSpinner.setValue(currentSettings[0]);
        
        gbc.gridx = 0; gbc.gridy = 1;
        settingsPanel.add(maxAttemptsLabel, gbc);
        gbc.gridx = 1;
        settingsPanel.add(maxAttemptsSpinner, gbc);
        
        JLabel currentAttemptsLabel = new JLabel("本小时已重启: " + currentAttempts + " 次");
        gbc.gridx = 0; gbc.gridy = 2;
        settingsPanel.add(currentAttemptsLabel, gbc);
        
        JLabel intervalLabel = new JLabel("重启间隔(秒):");
        JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(10, 5, 300, 5));
        intervalSpinner.setValue(currentSettings[1]);
        
        gbc.gridx = 0; gbc.gridy = 3;
        settingsPanel.add(intervalLabel, gbc);
        gbc.gridx = 1;
        settingsPanel.add(intervalSpinner, gbc);
        
        contentPanel.add(settingsPanel, BorderLayout.NORTH);
        
        JTextArea infoText = new JTextArea("进程守护功能会在服务器进程意外终止时自动尝试重启。\n\n" +
            "每小时最大重启次数: 防止无限重启，每小时达到次数后将停止尝试\n" +
            "重启间隔: 每次重启尝试之间的等待时间\n" +
            "建议设置合理的间隔时间，避免频繁重启");
        infoText.setEditable(false);
        infoText.setOpaque(true);
        infoText.setBackground(new Color(240, 240, 240));
        infoText.setFont(new Font(null, Font.PLAIN, 12));
        infoText.setWrapStyleWord(true);
        infoText.setLineWrap(true);
        
        JScrollPane scrollPane = new JScrollPane(infoText);
        scrollPane.setBorder(new EmptyBorder(10, 0, 0, 0));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setMinimumSize(new Dimension(400, 120));
        
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("确定");
        JButton cancelButton = new JButton("取消");
        JButton applyButton = new JButton("应用");
        
        okButton.addActionListener(e -> {
            boolean enabled = enableCheckBox.isSelected();
            int maxAttempts = (Integer) maxAttemptsSpinner.getValue();
            int interval = (Integer) intervalSpinner.getValue();
            
            jarRunner.setAutoRestartEnabled(enabled);
            jarRunner.setRestartSettings(maxAttempts, interval);
            saveGuardConfig(jarRunner.getJarPath(), enabled, maxAttempts, interval);
            jarRunner.getOutputPanel().append(String.format("[MSH] 进程守护设置已更新 - 启用: %s, 每小时最大重启: %d次, 重启间隔: %d秒\n", 
                enabled ? "是" : "否", maxAttempts, interval));
            dialog.dispose();
        });
        
        applyButton.addActionListener(e -> {
            boolean enabled = enableCheckBox.isSelected();
            int maxAttempts = (Integer) maxAttemptsSpinner.getValue();
            int interval = (Integer) intervalSpinner.getValue();
            
            jarRunner.setAutoRestartEnabled(enabled);
            jarRunner.setRestartSettings(maxAttempts, interval);
            saveGuardConfig(jarRunner.getJarPath(), enabled, maxAttempts, interval);
            jarRunner.getOutputPanel().append(String.format("[MSH] 进程守护设置已应用 - 启用: %s, 每小时最大重启: %d次, 重启间隔: %d秒\n", 
                enabled ? "是" : "否", maxAttempts, interval));
        });
        
        cancelButton.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(applyButton);
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }
    
    private void handleWindowClosing() {
        Logger.info("Application shutdown initiated", "Main");
        Logger.info("Stop signal sent to all servers", "Main");
        try {
            for (JarRunner jarRunner : jarRunners) {
                if (jarRunner.getStatus() == JarRunner.Status.RUNNING || jarRunner.getStatus() == JarRunner.Status.STARTING) {
                    jarRunner.stop();
                }
            }
            int waitCount = 0;
            boolean allStopped = false;
            while (waitCount < 30) {
                allStopped = true;
                for (JarRunner jarRunner : jarRunners) {
                    if (jarRunner.getStatus() != JarRunner.Status.STOPPED) {
                        allStopped = false;
                        break;
                    }
                }
                if (allStopped) break;
                Thread.sleep(1000);
                waitCount++;
            }
        } catch (InterruptedException e) {
            Logger.error("Shutdown interrupted: " + e.getMessage(), "Main");
            Thread.currentThread().interrupt();
        }
        if (!jarRunners.isEmpty()) {
            boolean hasRunning = jarRunners.stream().anyMatch(r -> r.getStatus() != JarRunner.Status.STOPPED);
            if (hasRunning) {
                Logger.warn("Some servers failed to shutdown gracefully", "Main");
            }
        }
        Logger.info("Application exiting", "Main");
        Runtime.getRuntime().exit(0);
    }

    private void showTabContextMenu(int tabIndex, int x, int y) {
        if (tabIndex <= 0) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem renameItem = new JMenuItem("重命名");
        renameItem.addActionListener(a -> renameServerTab(tabIndex));
        menu.add(renameItem);
        
        JMenuItem closeItem = new JMenuItem("关闭标签页");
        closeItem.addActionListener(a -> closeServerTab(tabIndex));
        menu.add(closeItem);
        
        menu.show(frame, x, y);
    }

    private void renameServerTab(int tabIndex) {
        if (tabIndex <= 0) {
            return;
        }
        JarRunner jarRunner = jarRunners.get(tabIndex - 1);
        String currentName = jarRunner.getDisplayName();
        String jarFileName = new File(jarRunner.getJarPath()).getName();
        
        JDialog renameDialog = new JDialog(frame, "重命名标签页", true);
        renameDialog.setLayout(new BorderLayout(15, 15));
        renameDialog.setSize(400, 180);
        renameDialog.setLocationRelativeTo(frame);
        
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        JPanel labelPanel = new JPanel(new BorderLayout());
        JLabel noteLabel = new JLabel("<html>设置服务端显示名称（不会修改真实文件名）<br>当前文件名为: " + jarFileName + "</html>");
        noteLabel.setFont(new Font(null, Font.PLAIN, 11));
        labelPanel.add(noteLabel, BorderLayout.CENTER);
        mainPanel.add(labelPanel, BorderLayout.NORTH);
        
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        JTextField nameField = new JTextField(currentName);
        nameField.selectAll();
        inputPanel.add(nameField, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("确定");
        JButton cancelButton = new JButton("取消");
        JButton resetButton = new JButton("恢复默认名称");
        
        okButton.addActionListener(a -> {
            String newName = nameField.getText();
            if (newName != null && !newName.trim().isEmpty()) {
                jarRunner.setCustomName(newName.trim());
                saveCustomName(jarRunner.getJarPath(), newName.trim());
                if (tabIndex > 0 && tabIndex <= tabLabels.size()) {
                    tabLabels.get(tabIndex - 1).setText(newName.trim());
                }
                jarRunner.getOutputPanel().append("[MS] 标签页已重命名为: " + newName.trim() + "\n");
            } else {
                jarRunner.setCustomName(null);
                saveCustomName(jarRunner.getJarPath(), null);
                if (tabIndex > 0 && tabIndex <= tabLabels.size()) {
                    tabLabels.get(tabIndex - 1).setText(jarFileName);
                }
                jarRunner.getOutputPanel().append("[MS] 标签页名称已恢复为默认\n");
            }
            renameDialog.dispose();
        });
        
        cancelButton.addActionListener(a -> renameDialog.dispose());
        
        resetButton.addActionListener(a -> {
            nameField.setText(jarFileName);
        });
        
        buttonPanel.add(resetButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        renameDialog.add(mainPanel);
        renameDialog.setVisible(true);
    }

    private void closeServerTab(int tabIndex) {
        if (tabIndex <= 0) {
            return;
        }
        JarRunner jarRunner = jarRunners.get(tabIndex - 1);
        if (jarRunner.getStatus() == JarRunner.Status.RUNNING || 
            jarRunner.getStatus() == JarRunner.Status.STARTING) {
            int choice = JOptionPane.showConfirmDialog(
                frame,
                "服务器正在运行中，是否先关闭服务器？\n选择\"是\"将关闭服务器并移除标签页\n选择\"否\"将直接移除标签页（服务器继续在后台运行）",
                "关闭标签页",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            if (choice == JOptionPane.YES_OPTION) {
                jarRunner.stop();
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        SwingUtilities.invokeLater(() -> removeTab(tabIndex));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
                return;
            } else if (choice == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }
        removeTab(tabIndex);
    }

    private void removeTab(int tabIndex) {
        if (tabIndex <= 0) {
            return;
        }
        JarRunner jarRunner = jarRunners.get(tabIndex - 1);
        jarRunner.cleanup();
        jarRunners.remove(tabIndex - 1);
        if (tabIndex - 1 < tabLabels.size()) {
            tabLabels.remove(tabIndex - 1);
        }
        tabbedPane.removeTabAt(tabIndex);
    }
    private void addServer(ActionEvent e) {
        Logger.info("User clicked add server button", "Main");
        try {
            String selfPath = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File selfFile = new File(selfPath);
            JFileChooser fileChooser = new JFileChooser(System.getProperty("user.dir"));
            fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                public boolean accept(File f) {
                    if (f.isDirectory()) {
                        return true;
                    }
                    if (f.getName().toLowerCase().endsWith(".jar")) {
                        try {
                            String fPath = f.getCanonicalPath();
                            String selfCanonicalPath = selfFile.getCanonicalPath();
                            return !fPath.equals(selfCanonicalPath);
                        } catch (IOException ex) {
                            return true;
                        }
                    }
                    return false;
                }
                public String getDescription() {
                    return "服务器文件 (*.jar)";
                }
            });
            int returnValue = fileChooser.showOpenDialog(frame);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                String jarPath = selectedFile.getAbsolutePath();
                Logger.info("User selected server file: " + jarPath, "Main");

                for (JarRunner existingRunner : jarRunners) {
                    if (existingRunner.getJarPath().equals(jarPath)) {
                        Logger.warn("Attempted to add existing server file: " + jarPath, "Main");
                        JOptionPane.showMessageDialog(frame, "该服务器文件已经被打开！", "提示", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }
                
                config.setProperty("last_server_path", jarPath);
                saveConfig();
                Logger.info("Configuration saved successfully", "Main");
                addServerFromPath(jarPath);
            } else {
                Logger.info("User cancelled add server", "Main");
            }
        } catch (Exception ex) {
            Logger.error("Error adding server: " + ex.getMessage(), "Main");
        }
    }
    

    private void addServerFromPath(String jarPath) {
        if (jarPath == null || jarPath.trim().isEmpty()) {
            Logger.warn("Attempted to add server with null or empty jar path", "Main");
            return;
        }
        
        File jarFile = new File(jarPath);
        if (!jarFile.exists()) {
            Logger.warn("Attempted to add non-existent server file: " + jarPath, "Main");
            return;
        }
        
        if (!jarFile.isFile()) {
            Logger.warn("Path is not a valid file: " + jarPath, "Main");
            return;
        }
        
        File serverDir = jarFile.getParentFile();
        if (serverDir == null) {
            Logger.warn("Could not determine server directory for: " + jarPath, "Main");
            return;
        }
        
        ColorOutputPanel outputPanel = new ColorOutputPanel();
        JarRunner jarRunner = new JarRunner(jarPath, outputPanel);

        Object[] guardConfig = loadGuardConfig(jarPath);
        if (guardConfig != null) {
            jarRunner.setAutoRestartEnabled((Boolean) guardConfig[0]);
            jarRunner.setRestartSettings((Integer) guardConfig[1], (Integer) guardConfig[2]);
        }

        jarRunners.add(jarRunner);
        
        String customName = loadCustomName(jarPath);
        if (customName != null) {
            jarRunner.setCustomName(customName);
        }
        
        String displayName = jarRunner.getDisplayName();
        
        JPanel serverPanel = new JPanel(new BorderLayout());
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel statusLabel = new JLabel("服务器状态: 已停止");
        statusLabel.setForeground(Color.RED);
        statusPanel.add(statusLabel);
        serverPanel.add(statusPanel, BorderLayout.NORTH);
        serverPanel.add(outputPanel, BorderLayout.CENTER);
        JPanel commandPanel = new JPanel(new BorderLayout(5, 5));
        JTextField commandField = new JTextField();
        JButton sendCommandButton = new JButton("发送命令");
        JComboBox<String> quickCommandComboBox = new JComboBox<>();
        quickCommandComboBox.setEditable(false);
        String[] quickCommands = {
            "选择快捷指令",
            "--- OP管理 ---",
            "设置OP",
            "移除OP",
            "--- 服务器管理 ---",
            "保存地图",
            "更改难度",
            "更改时间",
            "更改天气",
            "--- 玩家管理 ---",
            "列出在线玩家",
            "获取种子",
            "获得服务器版本",
            "--- 封禁管理 ---",
            "封禁玩家",
            "解禁玩家",
            "封禁IP",
            "封禁列表",
            "解禁IP",
            "--- 其他命令 ---",
            "直接发言",
            "停止服务器"
        };
        for (String cmd : quickCommands) {
            quickCommandComboBox.addItem(cmd);
        }
        quickCommandComboBox.addActionListener(e -> {
            String selectedCmd = (String) quickCommandComboBox.getSelectedItem();
            if (selectedCmd == null || selectedCmd.equals("选择快捷指令") || selectedCmd.startsWith("---")) {
                return;
            }
            if (jarRunner.getStatus() != JarRunner.Status.RUNNING) {
                JOptionPane.showMessageDialog(serverPanel, "服务器未运行，请先启动服务器！", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String finalCmd = "";
            switch (selectedCmd) {
                case "设置OP":
                    String playerName = JOptionPane.showInputDialog(serverPanel, "请输入要设置为OP的玩家名称:", "设置OP", JOptionPane.QUESTION_MESSAGE);
                    if (playerName != null && !playerName.trim().isEmpty()) {
                        finalCmd = "op " + playerName.trim();
                    }
                    break;
                case "移除OP":
                    String deopPlayer = JOptionPane.showInputDialog(serverPanel, "请输入要移除OP权限的玩家名称:", "移除OP", JOptionPane.QUESTION_MESSAGE);
                    if (deopPlayer != null && !deopPlayer.trim().isEmpty()) {
                        finalCmd = "deop " + deopPlayer.trim();
                    }
                    break;
                case "更改难度":
                    String[] difficulties = {"peaceful", "easy", "normal", "hard"};
                    String difficulty = (String) JOptionPane.showInputDialog(
                        serverPanel,
                        "请选择游戏难度:",
                        "更改难度",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        difficulties,
                        difficulties[2]
                    );
                    if (difficulty != null) {
                        finalCmd = "difficulty " + difficulty;
                    }
                    break;
                case "更改时间":
                    String time = JOptionPane.showInputDialog(serverPanel, "请输入时间（0-24000，0=日出，12000=日落）:", "更改时间", JOptionPane.QUESTION_MESSAGE);
                    if (time != null && !time.trim().isEmpty()) {
                        finalCmd = "time set " + time.trim();
                    }
                    break;
                case "更改天气":
                    String[] weathers = {"clear", "rain", "thunder"};
                    String weather = (String) JOptionPane.showInputDialog(
                        serverPanel,
                        "请选择天气:",
                        "更改天气",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        weathers,
                        weathers[0]
                    );
                    if (weather != null) {
                        finalCmd = "weather " + weather;
                    }
                    break;
                case "封禁玩家":
                    String banPlayer = JOptionPane.showInputDialog(serverPanel, "请输入要封禁的玩家名称:", "封禁玩家", JOptionPane.QUESTION_MESSAGE);
                    if (banPlayer != null && !banPlayer.trim().isEmpty()) {
                        String banReason = JOptionPane.showInputDialog(serverPanel, "请输入封禁原因:", "封禁原因", JOptionPane.QUESTION_MESSAGE);
                        if (banReason == null) banReason = "";
                        finalCmd = "ban " + banPlayer.trim() + " " + banReason.trim();
                    }
                    break;
                case "解禁玩家":
                    String pardonPlayer = JOptionPane.showInputDialog(serverPanel, "请输入要解禁的玩家名称:", "解禁玩家", JOptionPane.QUESTION_MESSAGE);
                    if (pardonPlayer != null && !pardonPlayer.trim().isEmpty()) {
                        finalCmd = "pardon " + pardonPlayer.trim();
                    }
                    break;
                case "封禁IP":
                    String banIp = JOptionPane.showInputDialog(serverPanel, "请输入要封禁的IP地址:", "封禁IP", JOptionPane.QUESTION_MESSAGE);
                    if (banIp != null && !banIp.trim().isEmpty()) {
                        String banIpReason = JOptionPane.showInputDialog(serverPanel, "请输入封禁原因:", "封禁原因", JOptionPane.QUESTION_MESSAGE);
                        if (banIpReason == null) banIpReason = "";
                        finalCmd = "ban-ip " + banIp.trim() + " " + banIpReason.trim();
                    }
                    break;
                case "解禁IP":
                    String pardonIp = JOptionPane.showInputDialog(serverPanel, "请输入要解禁的IP地址:", "解禁IP", JOptionPane.QUESTION_MESSAGE);
                    if (pardonIp != null && !pardonIp.trim().isEmpty()) {
                        finalCmd = "pardon-ip " + pardonIp.trim();
                    }
                    break;
                case "直接发言":
                    String message = JOptionPane.showInputDialog(serverPanel, "请输入要发送的消息:", "直接发言", JOptionPane.QUESTION_MESSAGE);
                    if (message != null && !message.trim().isEmpty()) {
                        finalCmd = "say " + message.trim();
                    }
                    break;
                case "保存地图":
                    finalCmd = "save-all";
                    break;
                case "列出在线玩家":
                    finalCmd = "list";
                    break;
                case "获取种子":
                    finalCmd = "seed";
                    break;
                case "获得服务器版本":
                    finalCmd = "version";
                    break;
                case "封禁列表":
                    finalCmd = "banlist";
                    break;
                case "重载服务器":
                    finalCmd = "reload";
                    break;
                case "停止服务器":
                    finalCmd = "stop";
                    break;
                default:
                    break;
            }
            if (!finalCmd.isEmpty()) {
                jarRunner.sendCommand(finalCmd);
            }
        });
        sendCommandButton.addActionListener(e -> {
            String command = commandField.getText().trim();
            if (!command.isEmpty()) {
                jarRunner.sendCommand(command);
                commandField.setText("");
            }
        });
        commandField.addActionListener(e -> {
            String command = commandField.getText().trim();
            if (!command.isEmpty()) {
                jarRunner.sendCommand(command);
                commandField.setText("");
            }
        });
        JPanel commandInputPanel = new JPanel(new BorderLayout(5, 5));
        commandInputPanel.add(quickCommandComboBox, BorderLayout.NORTH);
        commandInputPanel.add(commandField, BorderLayout.CENTER);
        commandPanel.add(commandInputPanel, BorderLayout.CENTER);
        commandPanel.add(sendCommandButton, BorderLayout.EAST);
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton startButton = new JButton("启动服务器");
        JButton stopButton = new JButton("关闭服务器");
        JButton forceStopButton = new JButton("强制关闭");
        JButton restartButton = new JButton("重启服务器");
        JButton reloadButton = new JButton("重载服务器");
        JButton configButton = new JButton("配置管理");
        JButton guardSettingsButton = new JButton("进程守护设置");
        JButton networkAddressButton = new JButton("查看地址");
        JButton gameRuleButton = new JButton("游戏规则");
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        forceStopButton.setEnabled(false);
        restartButton.setEnabled(false);
        reloadButton.setEnabled(false);
        startButton.addActionListener(a -> {
            jarRunner.start();
        });
        stopButton.addActionListener(a -> {
            int confirm = JOptionPane.showConfirmDialog(
                frame,
                "确定要关闭服务器吗？这将中断所有玩家的游戏连接。",
                "确认关闭服务器",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                jarRunner.stop();
            }
        });
        forceStopButton.addActionListener(a -> {
            int confirm = JOptionPane.showConfirmDialog(
                frame,
                "确定要强制关闭服务器吗？这将立即终止服务器进程，可能导致数据丢失。",
                "确认强制关闭服务器",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                jarRunner.forceStop();
            }
        });
        restartButton.addActionListener(a -> {
            int confirm = JOptionPane.showConfirmDialog(
                frame,
                "确定要重启服务器吗？这将中断所有玩家的游戏连接。",
                "确认重启服务器",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                jarRunner.restart();
            }
        });
        reloadButton.addActionListener(a -> jarRunner.sendCommand("reload"));
        configButton.addActionListener(a -> {
            ConfigEditor configEditor = new ConfigEditor(frame, serverDir);
            configEditor.setVisible(true);
        });
        guardSettingsButton.addActionListener(a -> {
            showGuardSettingsDialog(jarRunner);
        });
        networkAddressButton.addActionListener(a -> {
            new AddressDialog(frame, jarRunner).show();
        });
        gameRuleButton.addActionListener(a -> {
            GameRuleDialog.showDialog(frame, jarRunner);
        });
        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(forceStopButton);
        controlPanel.add(restartButton);
        controlPanel.add(reloadButton);
        controlPanel.add(configButton);
        controlPanel.add(guardSettingsButton);
        controlPanel.add(networkAddressButton);
        controlPanel.add(gameRuleButton);
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(controlPanel, BorderLayout.NORTH);
        bottomPanel.add(commandPanel, BorderLayout.CENTER);
        serverPanel.add(bottomPanel, BorderLayout.SOUTH);
        int tabIndex = tabbedPane.getTabCount();
        tabbedPane.addTab("placeholder", serverPanel);
        TabLabel tabLabel = new TabLabel(displayName, tabbedPane.getFont());
        tabLabels.add(tabLabel);
        tabbedPane.setTabComponentAt(tabIndex, tabLabel);
        tabbedPane.setSelectedIndex(tabIndex);
        Thread statusThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    JarRunner.Status currentStatus = jarRunner.getStatus();
                    
                    if (currentStatus == JarRunner.Status.RUNNING && !jarRunner.isProcessAlive()) {
                        
                        jarRunner.onProcessTerminated();
                        currentStatus = jarRunner.getStatus(); 
                    }
                    final JarRunner.Status finalStatus = currentStatus;
                    SwingUtilities.invokeLater(() -> {
                        switch (finalStatus) {
                            case STOPPED:
                                statusLabel.setText("服务器状态: 已停止");
                                statusLabel.setForeground(Color.RED);
                                startButton.setEnabled(true);
                                stopButton.setEnabled(false);
                                forceStopButton.setEnabled(false);
                                restartButton.setEnabled(false);
                                reloadButton.setEnabled(false);
                                break;
                            case RUNNING:
                                statusLabel.setText("服务器状态: 运行中");
                                statusLabel.setForeground(Color.GREEN);
                                startButton.setEnabled(false);
                                stopButton.setEnabled(true);
                                forceStopButton.setEnabled(true);
                                restartButton.setEnabled(true);
                                reloadButton.setEnabled(true);
                                break;
                            case STARTING:
                                statusLabel.setText("服务器状态: 启动中");
                                statusLabel.setForeground(Color.ORANGE);
                                startButton.setEnabled(false);
                                stopButton.setEnabled(true);
                                forceStopButton.setEnabled(true);
                                restartButton.setEnabled(true);
                                reloadButton.setEnabled(false);
                                break;
                            case STOPPING:
                                statusLabel.setText("服务器状态: 停止中");
                                statusLabel.setForeground(Color.ORANGE);
                                startButton.setEnabled(false);
                                stopButton.setEnabled(false);
                                forceStopButton.setEnabled(false);
                                restartButton.setEnabled(false);
                                reloadButton.setEnabled(false);
                                break;
                        }
                    });
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        statusThread.setDaemon(true);
        statusThread.start();
    }
    

}
