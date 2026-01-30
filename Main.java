import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Properties;

import javax.swing.JLabel;


public class Main {
    public static final String VERSION = "1.1.9";
    private static final String AUTHOR = "znpwlk";
    private static final String APP_NAME = "Minecraft Server Hub";
    private static final String APP_SHORT_NAME = "MSH";
    private static Main instance;
    private static volatile boolean shuttingDown = false;
    private JFrame frame;
    private JTabbedPane tabbedPane;
    private List<JarRunner> jarRunners;
    private Properties config;
    private File configFile;
    private JTextArea logTextArea;
    private List<TabLabel> tabLabels = new ArrayList<>();
    private UpdateManager updateManager;
    private File mshDir;
    
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
    
    public List<JarRunner> getJarRunners() {
        return jarRunners;
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
        
        JsonGameRuleLoader.setIndexUrl("https://msh.znpwlk.vip/gamerules/index.json");
        
        try {
            System.setOut(new java.io.PrintStream(System.out, true, charset));
            System.setErr(new java.io.PrintStream(System.err, true, charset));
        } catch (java.io.UnsupportedEncodingException e) {
            Logger.error("Failed to set console encoding: " + e.getMessage(), "Main");
        }
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (shuttingDown) return;
            shuttingDown = true;
            Logger.info("System shutdown hook triggered", "Main");
            if (instance != null) {
                for (JarRunner jarRunner : instance.getJarRunners()) {
                    try {
                        if (jarRunner.getStatus() == JarRunner.Status.RUNNING) {
                            jarRunner.stop();
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to stop server: " + e.getMessage());
                    }
                }
                
                int waitCount = 0;
                while (waitCount < 60) {
                    boolean allStopped = true;
                    for (JarRunner jarRunner : instance.getJarRunners()) {
                        if (jarRunner.getStatus() != JarRunner.Status.STOPPED) {
                            allStopped = false;
                            break;
                        }
                    }
                    if (allStopped) break;
                    
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    waitCount++;
                }
                
                for (JarRunner jarRunner : instance.getJarRunners()) {
                    try {
                        if (jarRunner.getStatus() != JarRunner.Status.STOPPED) {
                            jarRunner.forceStop();
                        }
                        jarRunner.cleanup();
                    } catch (Exception e) {
                        System.err.println("Failed to cleanup server: " + e.getMessage());
                    }
                }
            }
            Logger.shutdown();
        }, "shutdown-hook"));
        
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
        mshDir = new File("MSH");
        if (!mshDir.exists()) {
            mshDir.mkdirs();
        }
        configFile = new File(mshDir, "server_manager_config.properties");
        loadConfig();
        Logger.info("Configuration loaded successfully", "Main");
        updateManager = new UpdateManager(this);
        frame = new JFrame(APP_NAME + " (" + APP_SHORT_NAME + ")");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                boolean hasRunningServer = !jarRunners.isEmpty() && jarRunners.stream().anyMatch(runner -> 
                    runner.getStatus() == JarRunner.Status.RUNNING || runner.getStatus() == JarRunner.Status.STARTING);
                boolean isStopping = !jarRunners.isEmpty() && jarRunners.stream().anyMatch(runner -> 
                    runner.getStatus() == JarRunner.Status.STOPPING);
                if (isStopping) {
                    handleWindowClosing();
                } else if (hasRunningServer) {
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
        
        JButton checkUpdateButton = new JButton("检查更新");
        checkUpdateButton.addActionListener(e -> updateManager.checkForUpdates());
        topPanel.add(checkUpdateButton);
        
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
        JPanel logButtonPanel = new JPanel(new BorderLayout());
        JPanel leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clearLogButton = new JButton("清空日志");
        clearLogButton.addActionListener(e -> {
            Logger.getInstance().clearLogDisplay();
        });
        leftButtonPanel.add(clearLogButton);
        logButtonPanel.add(leftButtonPanel, BorderLayout.WEST);
        
        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportLogButton = new JButton("导出日志");
        exportLogButton.addActionListener(e -> exportLogs());
        rightButtonPanel.add(exportLogButton);
        logButtonPanel.add(rightButtonPanel, BorderLayout.EAST);
        
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
        checkAndDeleteOldVersion();
        deleteOldVersionAfterStartup();
        updateManager.checkForUpdates(false);
    }

    private void deleteOldVersionAfterStartup() {
        javax.swing.Timer deleteTimer = new javax.swing.Timer(3000, e -> {
            PreferenceManager prefManager = new PreferenceManager();
            String oldVersionPath = prefManager.getPendingDeleteOldVersion();
            if (oldVersionPath != null && !oldVersionPath.isEmpty()) {
                File oldFile = new File(oldVersionPath);
                if (oldFile.exists() && oldFile.isFile()) {
                    boolean deleted = oldFile.delete();
                    if (deleted) {
                        Logger.info("Old version file deleted: " + oldVersionPath, "Main");
                    } else {
                        Logger.warn("Failed to delete old version file: " + oldVersionPath, "Main");
                    }
                }
                prefManager.clearPendingDeleteOldVersion();
            }
        });
        deleteTimer.setRepeats(false);
        deleteTimer.start();
    }

    private void checkAndDeleteOldVersion() {
        PreferenceManager prefManager = new PreferenceManager();
        String oldVersionPath = prefManager.getPendingDeleteOldVersion();
        if (oldVersionPath != null && !oldVersionPath.isEmpty()) {
            File oldFile = new File(oldVersionPath);
            if (oldFile.exists() && oldFile.isFile()) {
                if (oldFile.delete()) {
                    Logger.info("Successfully deleted old version file: " + oldVersionPath, "Main");
                } else {
                    Logger.warn("Failed to delete old version file: " + oldVersionPath, "Main");
                }
            } else {
                Logger.info("Old version file does not exist or cannot be deleted: " + oldVersionPath, "Main");
            }
            prefManager.clearPendingDeleteOldVersion();
        }
    }
    
    private void exportLogs() {
        String logContent = logTextArea.getText();
        if (logContent == null || logContent.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "无日志内容可导出", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        String[] formats = {"TXT 文本文件 (*.txt)", "CSV 逗号分隔文件 (*.csv)", "HTML 网页文件 (*.html)"};
        int formatChoice = JOptionPane.showOptionDialog(
            frame,
            "选择导出格式:",
            "导出程序日志",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            formats,
            formats[0]
        );
        
        if (formatChoice < 0) {
            return;
        }
        
        String[] extensions = {".txt", ".csv", ".html"};
        String prefix = "app_log_" + System.currentTimeMillis();
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("保存日志文件");
        fileChooser.setSelectedFile(new File(prefix + extensions[formatChoice]));
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            formats[formatChoice].split(" \\*")[0],
            extensions[formatChoice].substring(1)
        ));
        
        int result = fileChooser.showSaveDialog(frame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File selectedFile = fileChooser.getSelectedFile();
        String filePath = selectedFile.getAbsolutePath();
        if (!filePath.toLowerCase().endsWith(extensions[formatChoice])) {
            selectedFile = new File(filePath + extensions[formatChoice]);
        }
        
        try {
            String exportContent;
            int logLineCount = logContent.split("\n").length;
            String exportTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            String osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")";
            String javaVersion = System.getProperty("java.version");
            
            if (formatChoice == 0) {
                StringBuilder txt = new StringBuilder();
                txt.append("================================================================================\n");
                txt.append("                              APPLICATION LOG EXPORT\n");
                txt.append("================================================================================\n\n");
                txt.append("Application: ").append(APP_NAME).append(" (").append(APP_SHORT_NAME).append(")\n");
                txt.append("Version: ").append(VERSION).append("\n");
                txt.append("Export Time: ").append(exportTime).append("\n");
                txt.append("Format: ").append(formats[formatChoice].split(" \\*")[0]).append("\n");
                txt.append("Operating System: ").append(osInfo).append("\n");
                txt.append("Java Version: ").append(javaVersion).append("\n");
                txt.append("Total Lines: ").append(logLineCount).append("\n");
                txt.append("Total Characters: ").append(logContent.length()).append("\n");
                txt.append("\n--------------------------------------------------------------------------------\n");
                txt.append("                               LOG CONTENT\n");
                txt.append("--------------------------------------------------------------------------------\n\n");
                txt.append(logContent);
                txt.append("\n================================================================================\n");
                txt.append("                           END OF LOG FILE\n");
                txt.append("================================================================================\n");
                exportContent = txt.toString();
            } else if (formatChoice == 1) {
                StringBuilder csv = new StringBuilder();
                csv.append("\"Application\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"").append(APP_NAME).append(" (").append(APP_SHORT_NAME).append(")\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"Version\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"").append(VERSION).append("\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"Export Time\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"").append(exportTime).append("\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"Format\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"").append(formats[formatChoice].split(" \\*")[0]).append("\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"Operating System\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"").append(osInfo).append("\",\"\",\"\",\"\"\n");
                csv.append("\"Java Version\",\"\",\"\",\"\"\n");
                csv.append("\"").append(javaVersion).append("\",\"\",\"\"\n");
                csv.append("\"Total Lines\",\"\",\"\"\n");
                csv.append("\"").append(logLineCount).append("\",\"\"\n");
                csv.append("\"Total Characters\",\"\"\n");
                csv.append("\"").append(logContent.length()).append("\"\n");
                csv.append("\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"LOG CONTENT\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                String[] lines = logContent.split("\n");
                for (String line : lines) {
                    if (line.contains(",") || line.contains("\"") || line.contains("\n")) {
                        line = "\"" + line.replace("\"", "\"\"") + "\"";
                    }
                    csv.append("\"").append(line).append("\"\n");
                }
                exportContent = csv.toString();
            } else {
                StringBuilder html = new StringBuilder();
                html.append("<!DOCTYPE html>\n");
                html.append("<html><head>\n");
                html.append("<meta charset=\"UTF-8\">\n");
                html.append("<title>Application Log Export - ").append(APP_NAME).append("</title>\n");
                html.append("<style>\n");
                html.append("body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }\n");
                html.append(".header { background-color: #2d5a7b; color: white; padding: 20px; border-radius: 8px 8px 0 0; }\n");
                html.append("h1 { margin: 0 0 10px 0; }\n");
                html.append(".info { background-color: #e8f4f8; padding: 15px; border: 1px solid #b8d4e3; }\n");
                html.append(".info p { margin: 5px 0; }\n");
                html.append(".label { font-weight: bold; color: #2d5a7b; }\n");
                html.append(".content { background-color: #1e1e1e; color: #d4d4d4; padding: 15px; border-radius: 0 0 8px 8px; overflow-x: auto; }\n");
                html.append("pre { margin: 0; white-space: pre-wrap; font-family: 'Consolas', 'Monaco', monospace; font-size: 12px; }\n");
                html.append(".footer { margin-top: 20px; text-align: center; color: #666; font-size: 12px; }\n");
                html.append("</style>\n");
                html.append("</head><body>\n");
                html.append("<div class=\"header\">\n");
                html.append("<h1>Application Log Export</h1>\n");
                html.append("</div>\n");
                html.append("<div class=\"info\">\n");
                html.append("<p><span class=\"label\">Application:</span> ").append(escapeHtml(APP_NAME)).append(" (").append(escapeHtml(APP_SHORT_NAME)).append(")</p>\n");
                html.append("<p><span class=\"label\">Version:</span> ").append(escapeHtml(VERSION)).append("</p>\n");
                html.append("<p><span class=\"label\">Export Time:</span> ").append(escapeHtml(exportTime)).append("</p>\n");
                html.append("<p><span class=\"label\">Format:</span> ").append(escapeHtml(formats[formatChoice].split(" \\*")[0])).append("</p>\n");
                html.append("<p><span class=\"label\">Operating System:</span> ").append(escapeHtml(osInfo)).append("</p>\n");
                html.append("<p><span class=\"label\">Java Version:</span> ").append(escapeHtml(javaVersion)).append("</p>\n");
                html.append("<p><span class=\"label\">Total Lines:</span> ").append(logLineCount).append("</p>\n");
                html.append("<p><span class=\"label\">Total Characters:</span> ").append(logContent.length()).append("</p>\n");
                html.append("</div>\n");
                html.append("<div class=\"content\">\n");
                html.append("<pre>").append(escapeHtml(logContent)).append("\n</pre>\n");
                html.append("</div>\n");
                html.append("<div class=\"footer\">\n");
                html.append("<p>Generated by ").append(escapeHtml(APP_NAME)).append(" v").append(escapeHtml(VERSION)).append("</p>\n");
                html.append("<p>Export Time: ").append(escapeHtml(exportTime)).append("</p>\n");
                html.append("</div>\n");
                html.append("</body></html>");
                exportContent = html.toString();
            }
            
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(selectedFile), "UTF-8")) {
                writer.write(exportContent);
            }
            
            Logger.info("Application log exported successfully: " + selectedFile.getName(), "Main");
            JOptionPane.showMessageDialog(frame, "Log exported successfully to:\n" + selectedFile.getAbsolutePath(), "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            Logger.error("Failed to export application log: " + ex.getMessage(), "Main");
            JOptionPane.showMessageDialog(frame, "Failed to export log:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    private void exportServerLog(JarRunner jarRunner, String displayName) {
        String logContent = jarRunner.getOutputPanel().getText();
        if (logContent == null || logContent.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "无日志内容可导出", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        String[] formats = {"TXT 文本文件 (*.txt)", "CSV 逗号分隔文件 (*.csv)", "HTML 网页文件 (*.html)"};
        int formatChoice = JOptionPane.showOptionDialog(
            frame,
            "选择导出格式:",
            "导出服务器日志",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            formats,
            formats[0]
        );
        
        if (formatChoice < 0) {
            return;
        }
        
        String[] extensions = {".txt", ".csv", ".html"};
        String prefix = sanitizeFileName(displayName) + "_" + System.currentTimeMillis();
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("保存服务器日志文件");
        fileChooser.setSelectedFile(new File(prefix + extensions[formatChoice]));
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            formats[formatChoice].split(" \\*")[0],
            extensions[formatChoice].substring(1)
        ));
        
        int result = fileChooser.showSaveDialog(frame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File selectedFile = fileChooser.getSelectedFile();
        String filePath = selectedFile.getAbsolutePath();
        if (!filePath.toLowerCase().endsWith(extensions[formatChoice])) {
            selectedFile = new File(filePath + extensions[formatChoice]);
        }
        
        try {
            String exportContent;
            int logLineCount = logContent.split("\n").length;
            String exportTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            String osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")";
            String javaVersion = System.getProperty("java.version");
            
            if (formatChoice == 0) {
                StringBuilder txt = new StringBuilder();
                txt.append("================================================================================\n");
                txt.append("                              SERVER LOG EXPORT\n");
                txt.append("================================================================================\n\n");
                txt.append("Application: ").append(APP_NAME).append(" (").append(APP_SHORT_NAME).append(")\n");
                txt.append("Version: ").append(VERSION).append("\n");
                txt.append("Server Name: ").append(displayName).append("\n");
                txt.append("Export Time: ").append(exportTime).append("\n");
                txt.append("Format: ").append(formats[formatChoice].split(" \\*")[0]).append("\n");
                txt.append("Operating System: ").append(osInfo).append("\n");
                txt.append("Java Version: ").append(javaVersion).append("\n");
                txt.append("Total Lines: ").append(logLineCount).append("\n");
                txt.append("Total Characters: ").append(logContent.length()).append("\n");
                txt.append("\n--------------------------------------------------------------------------------\n");
                txt.append("                               LOG CONTENT\n");
                txt.append("--------------------------------------------------------------------------------\n\n");
                txt.append(logContent);
                txt.append("\n================================================================================\n");
                txt.append("                           END OF LOG FILE\n");
                txt.append("================================================================================\n");
                exportContent = txt.toString();
            } else if (formatChoice == 1) {
                StringBuilder csv = new StringBuilder();
                csv.append("\"Application\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"").append(APP_NAME).append(" (").append(APP_SHORT_NAME).append(")\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"Version\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"").append(VERSION).append("\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"Server Name\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"").append(escapeCsv(displayName)).append("\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"Export Time\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"").append(exportTime).append("\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"Format\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"").append(formats[formatChoice].split(" \\*")[0]).append("\",\"\",\"\",\"\"\n");
                csv.append("\"Operating System\",\"\",\"\",\"\"\n");
                csv.append("\"").append(osInfo).append("\",\"\",\"\"\n");
                csv.append("\"Java Version\",\"\",\"\"\n");
                csv.append("\"").append(javaVersion).append("\",\"\"\n");
                csv.append("\"Total Lines\",\"\"\n");
                csv.append("\"").append(logLineCount).append("\"\n");
                csv.append("\"Total Characters\",\"\"\n");
                csv.append("\"").append(logContent.length()).append("\"\n");
                csv.append("\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                csv.append("\"LOG CONTENT\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n");
                String[] lines = logContent.split("\n");
                for (String line : lines) {
                    String escapedLine = escapeCsv(line);
                    csv.append("\"").append(escapedLine).append("\"\n");
                }
                exportContent = csv.toString();
            } else {
                StringBuilder html = new StringBuilder();
                html.append("<!DOCTYPE html>\n");
                html.append("<html><head>\n");
                html.append("<meta charset=\"UTF-8\">\n");
                html.append("<title>Server Log Export - ").append(escapeHtml(displayName)).append("</title>\n");
                html.append("<style>\n");
                html.append("body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }\n");
                html.append(".header { background-color: #2d5a7b; color: white; padding: 20px; border-radius: 8px 8px 0 0; }\n");
                html.append("h1 { margin: 0 0 10px 0; }\n");
                html.append(".info { background-color: #e8f4f8; padding: 15px; border: 1px solid #b8d4e3; }\n");
                html.append(".info p { margin: 5px 0; }\n");
                html.append(".label { font-weight: bold; color: #2d5a7b; }\n");
                html.append(".content { background-color: #1e1e1e; color: #d4d4d4; padding: 15px; border-radius: 0 0 8px 8px; overflow-x: auto; }\n");
                html.append("pre { margin: 0; white-space: pre-wrap; font-family: 'Consolas', 'Monaco', monospace; font-size: 12px; }\n");
                html.append(".footer { margin-top: 20px; text-align: center; color: #666; font-size: 12px; }\n");
                html.append("</style>\n");
                html.append("</head><body>\n");
                html.append("<div class=\"header\">\n");
                html.append("<h1>Server Log Export</h1>\n");
                html.append("</div>\n");
                html.append("<div class=\"info\">\n");
                html.append("<p><span class=\"label\">Application:</span> ").append(escapeHtml(APP_NAME)).append(" (").append(escapeHtml(APP_SHORT_NAME)).append(")</p>\n");
                html.append("<p><span class=\"label\">Version:</span> ").append(escapeHtml(VERSION)).append("</p>\n");
                html.append("<p><span class=\"label\">Server Name:</span> ").append(escapeHtml(displayName)).append("</p>\n");
                html.append("<p><span class=\"label\">Export Time:</span> ").append(escapeHtml(exportTime)).append("</p>\n");
                html.append("<p><span class=\"label\">Format:</span> ").append(escapeHtml(formats[formatChoice].split(" \\*")[0])).append("</p>\n");
                html.append("<p><span class=\"label\">Operating System:</span> ").append(escapeHtml(osInfo)).append("</p>\n");
                html.append("<p><span class=\"label\">Java Version:</span> ").append(escapeHtml(javaVersion)).append("</p>\n");
                html.append("<p><span class=\"label\">Total Lines:</span> ").append(logLineCount).append("</p>\n");
                html.append("<p><span class=\"label\">Total Characters:</span> ").append(logContent.length()).append("</p>\n");
                html.append("</div>\n");
                html.append("<div class=\"content\">\n");
                html.append("<pre>").append(escapeHtml(logContent)).append("\n</pre>\n");
                html.append("</div>\n");
                html.append("<div class=\"footer\">\n");
                html.append("<p>Generated by ").append(escapeHtml(APP_NAME)).append(" v").append(escapeHtml(VERSION)).append("</p>\n");
                html.append("<p>Export Time: ").append(escapeHtml(exportTime)).append("</p>\n");
                html.append("</div>\n");
                html.append("</body></html>");
                exportContent = html.toString();
            }
            
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(selectedFile), "UTF-8")) {
                writer.write(exportContent);
            }
            
            Logger.info("Server log exported successfully: " + selectedFile.getName() + " (Server: " + displayName + ")", "Main");
            JOptionPane.showMessageDialog(frame, "Server log exported successfully to:\n" + selectedFile.getAbsolutePath(), "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            Logger.error("Failed to export server log: " + ex.getMessage() + " (Server: " + displayName + ")", "Main");
            JOptionPane.showMessageDialog(frame, "Failed to export server log:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private String escapeCsv(String text) {
        if (text == null) return "";
        return text.replace("\"", "\"\"");
    }
    
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "server";
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
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

    private void saveGuardConfig(String jarPath, boolean enabled, boolean forceKeepAlive, int maxAttempts, int interval) {
        String keyPrefix = "guard." + jarPath + ".";
        config.setProperty(keyPrefix + "enabled", String.valueOf(enabled));
        config.setProperty(keyPrefix + "forceKeepAlive", String.valueOf(forceKeepAlive));
        config.setProperty(keyPrefix + "maxAttempts", String.valueOf(maxAttempts));
        config.setProperty(keyPrefix + "interval", String.valueOf(interval));
        saveConfig();
    }

    private Object[] loadGuardConfig(String jarPath) {
        String keyPrefix = "guard." + jarPath + ".";
        String enabledStr = config.getProperty(keyPrefix + "enabled");
        String forceKeepAliveStr = config.getProperty(keyPrefix + "forceKeepAlive");
        String maxAttemptsStr = config.getProperty(keyPrefix + "maxAttempts");
        String intervalStr = config.getProperty(keyPrefix + "interval");

        if (enabledStr == null || maxAttemptsStr == null || intervalStr == null) {
            return null;
        }

        return new Object[]{
            Boolean.parseBoolean(enabledStr),
            Boolean.parseBoolean(forceKeepAliveStr != null ? forceKeepAliveStr : "false"),
            Integer.parseInt(maxAttemptsStr),
            Integer.parseInt(intervalStr)
        };
    }
    
    private void saveBackupConfig(String jarPath, boolean enabled, int intervalMinutes, int maxCount, int deleteDays) {
        String keyPrefix = "backup." + jarPath + ".";
        config.setProperty(keyPrefix + "enabled", String.valueOf(enabled));
        config.setProperty(keyPrefix + "interval", String.valueOf(intervalMinutes));
        config.setProperty(keyPrefix + "maxCount", String.valueOf(maxCount));
        config.setProperty(keyPrefix + "deleteDays", String.valueOf(deleteDays));
        saveConfig();
    }
    
    private Object[] loadBackupConfig(String jarPath) {
        String keyPrefix = "backup." + jarPath + ".";
        String enabledStr = config.getProperty(keyPrefix + "enabled");
        String intervalStr = config.getProperty(keyPrefix + "interval");
        String maxCountStr = config.getProperty(keyPrefix + "maxCount");
        String deleteDaysStr = config.getProperty(keyPrefix + "deleteDays");

        if (enabledStr == null || intervalStr == null) {
            return null;
        }

        return new Object[]{
            Boolean.parseBoolean(enabledStr),
            Integer.parseInt(intervalStr),
            maxCountStr != null ? Integer.parseInt(maxCountStr) : 10,
            deleteDaysStr != null ? Integer.parseInt(deleteDaysStr) : 30
        };
    }
    
    private void showGuardSettingsDialog(JarRunner jarRunner) {
        JDialog dialog = new JDialog(frame, "进程守护设置", false);
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
        
        JLabel forceKeepAliveLabel = new JLabel("强制保持运行:");
        JCheckBox forceKeepAliveCheckBox = new JCheckBox();
        forceKeepAliveCheckBox.setSelected(jarRunner.isForceKeepAlive());
        
        gbc.gridx = 0; gbc.gridy = 1;
        settingsPanel.add(forceKeepAliveLabel, gbc);
        gbc.gridx = 1;
        settingsPanel.add(forceKeepAliveCheckBox, gbc);
        
        JLabel maxAttemptsLabel = new JLabel("每小时最大重启次数(-1无限制):");
        int[] currentSettings = jarRunner.getRestartSettings();
        int currentAttempts = jarRunner.getCurrentHourlyAttempts();
        JSpinner maxAttemptsSpinner = new JSpinner(new SpinnerNumberModel(currentSettings[0], -1, 999, 1));
        
        gbc.gridx = 0; gbc.gridy = 2;
        settingsPanel.add(maxAttemptsLabel, gbc);
        gbc.gridx = 1;
        settingsPanel.add(maxAttemptsSpinner, gbc);
        
        JLabel currentAttemptsLabel = new JLabel("本小时已重启: " + currentAttempts + " 次");
        gbc.gridx = 0; gbc.gridy = 3;
        settingsPanel.add(currentAttemptsLabel, gbc);
        
        JLabel intervalLabel = new JLabel("重启间隔(秒，0表示立即重启):");
        JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(currentSettings[1], 0, 300, 1));
        
        gbc.gridx = 0; gbc.gridy = 4;
        settingsPanel.add(intervalLabel, gbc);
        gbc.gridx = 1;
        settingsPanel.add(intervalSpinner, gbc);
        
        contentPanel.add(settingsPanel, BorderLayout.NORTH);
        
        JTextArea infoText = new JTextArea("进程守护功能会在服务器进程意外终止时自动尝试重启。\n\n" +
            "强制保持运行: 无论正常还是异常关闭都会自动重启\n" +
            "每小时最大重启次数: 防止无限重启，每小时达到次数后将停止尝试 (填-1表示无限制)\n" +
            "重启间隔: 每次重启尝试之间的等待时间 (填0表示立即重启)\n" +
            "建议设置合理的间隔时间，避免频繁重启");
        infoText.setEditable(false);
        infoText.setOpaque(false);
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
            boolean forceKeepAlive = forceKeepAliveCheckBox.isSelected();
            int maxAttempts = (Integer) maxAttemptsSpinner.getValue();
            int interval = (Integer) intervalSpinner.getValue();
            
            jarRunner.setAutoRestartEnabled(enabled);
            jarRunner.setForceKeepAlive(forceKeepAlive);
            jarRunner.setRestartSettings(maxAttempts, interval);
            saveGuardConfig(jarRunner.getJarPath(), enabled, forceKeepAlive, maxAttempts, interval);
            jarRunner.getOutputPanel().append(String.format("[MSH] 进程守护设置已更新 - 启用: %s, 强制保持运行: %s, 每小时最大重启: %d次, 重启间隔: %d秒\n", 
                enabled ? "是" : "否", forceKeepAlive ? "是" : "否", maxAttempts, interval));
            dialog.dispose();
        });
        
        applyButton.addActionListener(e -> {
            boolean enabled = enableCheckBox.isSelected();
            boolean forceKeepAlive = forceKeepAliveCheckBox.isSelected();
            int maxAttempts = (Integer) maxAttemptsSpinner.getValue();
            int interval = (Integer) intervalSpinner.getValue();
            
            jarRunner.setAutoRestartEnabled(enabled);
            jarRunner.setForceKeepAlive(forceKeepAlive);
            jarRunner.setRestartSettings(maxAttempts, interval);
            saveGuardConfig(jarRunner.getJarPath(), enabled, forceKeepAlive, maxAttempts, interval);
            jarRunner.getOutputPanel().append(String.format("[MSH] 进程守护设置已应用 - 启用: %s, 强制保持运行: %s, 每小时最大重启: %d次, 重启间隔: %d秒\n", 
                enabled ? "是" : "否", forceKeepAlive ? "是" : "否", maxAttempts, interval));
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
    
    private void showBackupSettingsDialog(JarRunner jarRunner) {
        JDialog dialog = new JDialog(frame, "备份管理 - " + new File(jarRunner.getJarPath()).getName(), false);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(750, 550);
        dialog.setLocationRelativeTo(frame);
        dialog.setResizable(true);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        DefaultListModel<String> backupListModel = new DefaultListModel<>();
        JTextArea backupInfoArea = new JTextArea();

        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        JLabel titleLabel = new JLabel("备份管理");
        titleLabel.setFont(new Font(null, Font.BOLD, 16));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        leftPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel settingsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        JCheckBox enableCheckBox = new JCheckBox("启用定时备份", jarRunner.isBackupEnabled());
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        settingsPanel.add(enableCheckBox, gbc);

        JLabel intervalLabel = new JLabel("备份间隔:");
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        settingsPanel.add(intervalLabel, gbc);

        JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(jarRunner.getBackupIntervalMinutes(), 1, 1440, 1));
        gbc.gridx = 1; gbc.gridy = 1;
        settingsPanel.add(intervalSpinner, gbc);

        JLabel minuteLabel = new JLabel("分钟");
        gbc.gridx = 2; gbc.gridy = 1;
        settingsPanel.add(minuteLabel, gbc);

        JLabel maxCountLabel = new JLabel("最大备份条数:");
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        settingsPanel.add(maxCountLabel, gbc);

        JSpinner maxCountSpinner = new JSpinner(new SpinnerNumberModel(jarRunner.getMaxBackupCount(), 1, 1000, 1));
        gbc.gridx = 1; gbc.gridy = 2;
        settingsPanel.add(maxCountSpinner, gbc);

        JLabel countLabel = new JLabel("条");
        gbc.gridx = 2; gbc.gridy = 2;
        settingsPanel.add(countLabel, gbc);

        JLabel deleteDaysLabel = new JLabel("自动删除多少天前的:");
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        settingsPanel.add(deleteDaysLabel, gbc);

        JSpinner deleteDaysSpinner = new JSpinner(new SpinnerNumberModel(jarRunner.getAutoDeleteDays(), 0, 3650, 1));
        gbc.gridx = 1; gbc.gridy = 3;
        settingsPanel.add(deleteDaysSpinner, gbc);

        JLabel daysLabel = new JLabel("天 (0表示不自动删除)");
        gbc.gridx = 2; gbc.gridy = 3;
        settingsPanel.add(daysLabel, gbc);

        JLabel maxCountHintLabel = new JLabel("超过最大条数自动删除旧的");
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3;
        settingsPanel.add(maxCountHintLabel, gbc);

        JButton backupNowButton = new JButton("立即备份");
        backupNowButton.addActionListener(e -> {
            if (jarRunner.isBackingUp()) {
                return;
            }
            if (jarRunner.getStatus() == JarRunner.Status.RUNNING || 
                jarRunner.getStatus() == JarRunner.Status.STARTING ||
                jarRunner.getStatus() == JarRunner.Status.STOPPING) {
                JOptionPane.showMessageDialog(dialog, 
                    "服务器正在运行中，无法进行备份。\n请先停止服务器后再进行备份操作。", 
                    "无法备份", JOptionPane.WARNING_MESSAGE);
                return;
            }
            showBackupProgressDialog(dialog, jarRunner, backupListModel);
        });
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 1;
        settingsPanel.add(backupNowButton, gbc);

        long lastBackup = jarRunner.getLastBackupTime();
        if (lastBackup > 0) {
            String lastBackupStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(lastBackup));
            JLabel lastBackupLabel = new JLabel("上次: " + lastBackupStr);
            gbc.gridx = 1; gbc.gridy = 5; gbc.gridwidth = 2;
            settingsPanel.add(lastBackupLabel, gbc);
        }

        leftPanel.add(settingsPanel, BorderLayout.CENTER);

        JPanel infoPanel = new JPanel(new BorderLayout(5, 5));
        infoPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        JTextArea infoText = new JTextArea("备份目录: MSH/backup/服务端名称/\n备份包含服务端文件夹内的所有文件\n\n注意: 由于技术限制，服务器运行时无法正常备份\n请先停止服务器后再进行备份操作");
        infoText.setEditable(false);
        infoText.setOpaque(false);
        infoText.setFont(new Font(null, Font.PLAIN, 11));
        infoPanel.add(infoText, BorderLayout.NORTH);
        leftPanel.add(infoPanel, BorderLayout.SOUTH);

        mainPanel.add(leftPanel, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        JLabel listTitle = new JLabel("备份列表");
        listTitle.setFont(new Font(null, Font.BOLD, 14));
        
        JPanel sortPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        sortPanel.add(new JLabel("排序:"));
        ButtonGroup sortGroup = new ButtonGroup();
        JRadioButton sortByTime = new JRadioButton("时间", true);
        JRadioButton sortBySize = new JRadioButton("大小", false);
        JRadioButton sortByName = new JRadioButton("名称", false);
        sortGroup.add(sortByTime);
        sortGroup.add(sortBySize);
        sortGroup.add(sortByName);
        sortPanel.add(sortByTime);
        sortPanel.add(sortBySize);
        sortPanel.add(sortByName);
        
        sortByTime.addActionListener(e -> loadBackupList(backupListModel, jarRunner, "time"));
        sortBySize.addActionListener(e -> loadBackupList(backupListModel, jarRunner, "size"));
        sortByName.addActionListener(e -> loadBackupList(backupListModel, jarRunner, "name"));
        
        JPanel titlePanel = new JPanel(new BorderLayout(5, 5));
        titlePanel.add(listTitle, BorderLayout.WEST);
        titlePanel.add(sortPanel, BorderLayout.CENTER);
        rightPanel.add(titlePanel, BorderLayout.NORTH);

        JList<String> backupList = new JList<>(backupListModel);
        backupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        backupList.setFont(new Font(null, Font.PLAIN, 12));

        JScrollPane listScrollPane = new JScrollPane(backupList);
        listScrollPane.setPreferredSize(new Dimension(400, 280));
        rightPanel.add(listScrollPane, BorderLayout.CENTER);

        JPanel backupInfoPanel = new JPanel(new BorderLayout(5, 5));
        backupInfoPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        backupInfoArea.setEditable(false);
        backupInfoArea.setFont(new Font(null, Font.PLAIN, 11));
        backupInfoArea.setLineWrap(true);
        backupInfoArea.setWrapStyleWord(true);
        JScrollPane infoScrollPane = new JScrollPane(backupInfoArea);
        infoScrollPane.setPreferredSize(new Dimension(400, 80));
        infoScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        backupInfoPanel.add(infoScrollPane, BorderLayout.CENTER);
        rightPanel.add(backupInfoPanel, BorderLayout.SOUTH);

        backupList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = backupList.getSelectedValue();
                if (selected != null && !selected.isEmpty()) {
                    if (selected.contains("..") || selected.contains("/") || selected.contains("\\") || 
                        selected.contains(":") || selected.contains("*") || selected.contains("?") || 
                        selected.contains("\"") || selected.contains("<") || selected.contains(">") || 
                        selected.contains("|")) {
                        backupInfoArea.setText("无效的文件名");
                        return;
                    }
                    
                    String serverName;
                    try {
                        serverName = sanitizeFileName(new File(jarRunner.getJarPath()).getName());
                    } catch (Exception ex) {
                        backupInfoArea.setText("无法获取服务端名称");
                        return;
                    }
                    
                    String backupDirPath = "MSH/backup/" + serverName;
                    File backupDir = new File(backupDirPath);
                    
                    File zipFile = new File(backupDir, selected);
                    
                    try {
                        String canonicalZipPath = zipFile.getCanonicalPath();
                        String canonicalDirPath = backupDir.getCanonicalPath();
                        if (!canonicalZipPath.startsWith(canonicalDirPath + File.separator) && 
                            !canonicalZipPath.equals(canonicalDirPath)) {
                            backupInfoArea.setText("无效的备份文件");
                            return;
                        }
                    } catch (IOException ex) {
                        backupInfoArea.setText("无法验证文件路径");
                        return;
                    }
                    
                    if (zipFile.exists() && zipFile.canRead() && zipFile.length() > 0) {
                        StringBuilder info = new StringBuilder();
                        info.append("文件: ").append(selected).append("\n");
                        info.append("大小: ").append(formatFileSize(zipFile.length())).append("\n");
                        info.append("备份内容:\n");
                        try (ZipFile zip = new ZipFile(zipFile)) {
                            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                            int count = 0;
                            int totalEntries = 0;
                            while (entries.hasMoreElements()) {
                                ZipEntry entry = entries.nextElement();
                                totalEntries++;
                                if (count < 20) {
                                    String entryName = entry.getName();
                                    if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                                        info.append("  - [无效条目]\n");
                                    } else {
                                        info.append("  - ").append(entryName);
                                        if (entry.getSize() > 0) {
                                            info.append(" (").append(formatFileSize(entry.getSize())).append(")");
                                        }
                                        info.append("\n");
                                        count++;
                                    }
                                }
                            }
                            if (totalEntries > 20) {
                                info.append("  ... 共 ").append(totalEntries).append(" 个文件/文件夹");
                            }
                        } catch (IOException ex) {
                            info.append("无法读取备份信息: ").append(ex.getMessage());
                        }
                        backupInfoArea.setText(info.toString());
                    } else {
                        backupInfoArea.setText("备份文件不存在或无法读取");
                    }
                }
            }
        });

        mainPanel.add(rightPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton restoreButton = new JButton("恢复备份");
        JButton deleteButton = new JButton("删除");
        JButton okButton = new JButton("确定");
        JButton applyButton = new JButton("应用");
        JButton cancelButton = new JButton("取消");

        restoreButton.addActionListener(e -> {
            String selected = backupList.getSelectedValue();
            if (selected == null || selected.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请先选择一个备份", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            if (selected.contains("..") || selected.contains("/") || selected.contains("\\") || 
                selected.contains(":") || selected.contains("*") || selected.contains("?") || 
                selected.contains("\"") || selected.contains("<") || selected.contains(">") || 
                selected.contains("|")) {
                JOptionPane.showMessageDialog(dialog, "无效的备份选择", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            int confirm = JOptionPane.showConfirmDialog(dialog,
                "确定要恢复备份 '" + selected + "' 吗？\n这将覆盖当前服务端的所有文件！",
                "确认恢复", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                String serverName = sanitizeFileName(new File(jarRunner.getJarPath()).getName());
                String backupDirPath = "MSH/backup/" + serverName;
                int parenIndex = selected.lastIndexOf(" (");
                String actualFileName = parenIndex > 0 ? selected.substring(0, parenIndex) : selected;
                
                if (actualFileName.contains("..") || actualFileName.contains("/") || actualFileName.contains("\\")) {
                    JOptionPane.showMessageDialog(dialog, "无效的文件名", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                File backupDir = new File(backupDirPath);
                File zipFile = new File(backupDir, actualFileName);
                
                try {
                    String canonicalZipPath = zipFile.getCanonicalPath();
                    String canonicalBackupDir = backupDir.getCanonicalPath();
                    
                    if (!canonicalZipPath.startsWith(canonicalBackupDir + File.separator) && 
                        !canonicalZipPath.equals(canonicalBackupDir)) {
                        JOptionPane.showMessageDialog(dialog, "无效的备份文件路径", "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    
                    File serverDir = new File(jarRunner.getJarPath()).getParentFile();
                    if (serverDir == null || !serverDir.exists() || !serverDir.isDirectory()) {
                        JOptionPane.showMessageDialog(dialog, "服务端目录不存在", "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    
                    if (jarRunner.getStatus() == JarRunner.Status.RUNNING) {
                        JOptionPane.showMessageDialog(dialog, "请先停止服务器再恢复备份", "提示", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    
                    showRestoreProgressDialog(dialog, jarRunner, actualFileName);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(dialog, "恢复失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    Logger.error("Backup restore failed: " + ex.getMessage(), "Main");
                }
            }
        });

        deleteButton.addActionListener(e -> {
            String selected = backupList.getSelectedValue();
            if (selected == null || selected.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请先选择一个备份", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            if (selected.contains("..") || selected.contains("/") || selected.contains("\\") || 
                selected.contains(":") || selected.contains("*") || selected.contains("?") || 
                selected.contains("\"") || selected.contains("<") || selected.contains(">") || 
                selected.contains("|")) {
                JOptionPane.showMessageDialog(dialog, "无效的备份选择", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            int confirm = JOptionPane.showConfirmDialog(dialog,
                "确定要删除备份 '" + selected + "' 吗？", "确认删除", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                String serverName = sanitizeFileName(new File(jarRunner.getJarPath()).getName());
                String backupDirPath = "MSH/backup/" + serverName;
                int parenIndex = selected.lastIndexOf(" (");
                String actualFileName = parenIndex > 0 ? selected.substring(0, parenIndex) : selected;
                
                if (actualFileName.contains("..") || actualFileName.contains("/") || actualFileName.contains("\\")) {
                    JOptionPane.showMessageDialog(dialog, "无效的文件名", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                File backupDir = new File(backupDirPath);
                File zipFile = new File(backupDir, actualFileName);
                
                try {
                    String canonicalZipPath = zipFile.getCanonicalPath();
                    String canonicalBackupDir = backupDir.getCanonicalPath();
                    
                    if (!canonicalZipPath.startsWith(canonicalBackupDir + File.separator) && 
                        !canonicalZipPath.equals(canonicalBackupDir)) {
                        JOptionPane.showMessageDialog(dialog, "无效的备份文件路径", "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    
                    if (!zipFile.exists()) {
                        JOptionPane.showMessageDialog(dialog, "备份文件不存在", "错误", JOptionPane.ERROR_MESSAGE);
                        loadBackupList(backupListModel, jarRunner, "time");
                        return;
                    }
                    
                    if (zipFile.delete()) {
                        loadBackupList(backupListModel, jarRunner, "time");
                        backupInfoArea.setText("");
                        jarRunner.getOutputPanel().append("[MSH] 备份已删除: " + actualFileName + "\n");
                        Logger.info("Backup deleted: " + actualFileName, "Main");
                    } else {
                        JOptionPane.showMessageDialog(dialog, "删除失败，请检查文件权限", "错误", JOptionPane.ERROR_MESSAGE);
                        Logger.error("Failed to delete backup: " + actualFileName, "Main");
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(dialog, "删除失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    Logger.error("Failed to delete backup: " + ex.getMessage(), "Main");
                }
            }
        });

        okButton.addActionListener(e -> {
            boolean enabled = enableCheckBox.isSelected();
            int interval = (Integer) intervalSpinner.getValue();
            int maxCount = (Integer) maxCountSpinner.getValue();
            int deleteDays = (Integer) deleteDaysSpinner.getValue();

            jarRunner.setBackupEnabled(enabled);
            jarRunner.setBackupIntervalMinutes(interval);
            jarRunner.setMaxBackupCount(maxCount);
            jarRunner.setAutoDeleteDays(deleteDays);
            saveBackupConfig(jarRunner.getJarPath(), enabled, interval, maxCount, deleteDays);
            jarRunner.getOutputPanel().append(String.format("[MSH] 备份设置已更新 - 启用: %s, 间隔: %d分钟, 最大条数: %d, 删除天数: %d\n",
                enabled ? "是" : "否", interval, maxCount, deleteDays));
            dialog.dispose();
        });

        applyButton.addActionListener(e -> {
            boolean enabled = enableCheckBox.isSelected();
            int interval = (Integer) intervalSpinner.getValue();
            int maxCount = (Integer) maxCountSpinner.getValue();
            int deleteDays = (Integer) deleteDaysSpinner.getValue();

            jarRunner.setBackupEnabled(enabled);
            jarRunner.setBackupIntervalMinutes(interval);
            jarRunner.setMaxBackupCount(maxCount);
            jarRunner.setAutoDeleteDays(deleteDays);
            saveBackupConfig(jarRunner.getJarPath(), enabled, interval, maxCount, deleteDays);
            jarRunner.getOutputPanel().append(String.format("[MSH] 备份设置已应用 - 启用: %s, 间隔: %d分钟, 最大条数: %d, 删除天数: %d\n",
                enabled ? "是" : "否", interval, maxCount, deleteDays));
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(restoreButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(applyButton);
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel);
        loadBackupList(backupListModel, jarRunner, "time");
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }
    
    private void showOtherSettingsDialog(JarRunner jarRunner) {
        JDialog dialog = new JDialog(frame, "其他设置 - " + new File(jarRunner.getJarPath()).getName(), false);
        dialog.setLayout(new BorderLayout(20, 20));
        dialog.setSize(480, 380);
        dialog.setLocationRelativeTo(frame);
        dialog.setResizable(true);
        
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("其他设置");
        titleLabel.setFont(new Font(null, Font.BOLD, 18));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        topPanel.add(titleLabel, BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        JLabel useNoGuiLabel = new JLabel("是否没有图形化启动，如果低版本其中有问题，尝试关闭这个选项:");
        JCheckBox useNoGuiCheckBox = new JCheckBox();
        useNoGuiCheckBox.setSelected(jarRunner.isUseNoGui());
        
        gbc.gridx = 0; gbc.gridy = 0;
        settingsPanel.add(useNoGuiLabel, gbc);
        gbc.gridx = 1;
        settingsPanel.add(useNoGuiCheckBox, gbc);
        
        JTextArea infoText = new JTextArea("设置说明：\n\n" +
            "--nogui参数: 用于禁用服务器的图形界面。部分旧版本服务端不支持此参数。");
        infoText.setEditable(false);
        infoText.setOpaque(false);
        infoText.setFont(new Font(null, Font.PLAIN, 12));
        infoText.setWrapStyleWord(true);
        infoText.setLineWrap(true);
        
        JScrollPane scrollPane = new JScrollPane(infoText);
        scrollPane.setBorder(new EmptyBorder(10, 0, 0, 0));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setMinimumSize(new Dimension(400, 120));
        
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.add(settingsPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("确定");
        JButton cancelButton = new JButton("取消");
        JButton applyButton = new JButton("应用");
        
        okButton.addActionListener(e -> {
            boolean useNoGui = useNoGuiCheckBox.isSelected();
            jarRunner.setUseNoGui(useNoGui);
            saveOtherConfig(jarRunner.getJarPath(), useNoGui);
            jarRunner.getOutputPanel().append(String.format("[MSH] 其他设置已更新 - 使用--nogui参数: %s\n", 
                useNoGui ? "是" : "否"));
            dialog.dispose();
        });
        
        applyButton.addActionListener(e -> {
            boolean useNoGui = useNoGuiCheckBox.isSelected();
            jarRunner.setUseNoGui(useNoGui);
            saveOtherConfig(jarRunner.getJarPath(), useNoGui);
            jarRunner.getOutputPanel().append(String.format("[MSH] 其他设置已应用 - 使用--nogui参数: %s\n", 
                useNoGui ? "是" : "否"));
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
    
    private void saveOtherConfig(String jarPath, boolean useNoGui) {
        try {
            Properties props = new Properties();
            File configFile = new File(mshDir, "server_manager_config.properties");
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    props.load(fis);
                }
            }
            
            String safeJarName = sanitizeFileName(new File(jarPath).getName());
            props.setProperty("other.useNoGui." + safeJarName, String.valueOf(useNoGui));
            
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                props.store(fos, "Server Manager Config");
            }
        } catch (IOException e) {
            Logger.error("Failed to save other config: " + e.getMessage(), "Main");
        }
    }
    
    private boolean loadOtherConfig(String jarPath) {
        boolean useNoGui = true;
        try {
            Properties props = new Properties();
            File configFile = new File(mshDir, "server_manager_config.properties");
            if (!configFile.exists()) {
                return useNoGui;
            }
            
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            }
            
            String safeJarName = sanitizeFileName(new File(jarPath).getName());
            String useNoGuiStr = props.getProperty("other.useNoGui." + safeJarName);
            if (useNoGuiStr != null) {
                useNoGui = Boolean.parseBoolean(useNoGuiStr);
            }
        } catch (IOException e) {
            Logger.error("Failed to load other config: " + e.getMessage(), "Main");
        }
        return useNoGui;
    }

    private void loadBackupList(DefaultListModel<String> model, JarRunner jarRunner, String sortBy) {
        model.clear();
        try {
            String jarPath = jarRunner.getJarPath();
            if (jarPath == null || jarPath.isEmpty()) {
                return;
            }
            
            File jarFile = new File(jarPath);
            if (!jarFile.exists()) {
                return;
            }
            
            String safeServerName = sanitizeFileName(jarFile.getName());
            String backupDirPath = "MSH/backup/" + safeServerName;
            File backupDir = new File(backupDirPath);
            
            if (!backupDir.exists() || !backupDir.isDirectory()) {
                return;
            }
            
            String canonicalBackupDir = backupDir.getCanonicalPath();
            File[] files = backupDir.listFiles((d, name) -> {
                if (name == null || name.isEmpty()) return false;
                if (name.contains("..") || name.contains("/") || name.contains("\\")) return false;
                return name.toLowerCase().endsWith(".zip");
            });
            
            if (files == null || files.length == 0) {
                return;
            }
            
            switch (sortBy) {
                case "size":
                    Arrays.sort(files, (f1, f2) -> Long.compare(f2.length(), f1.length()));
                    break;
                case "name":
                    Arrays.sort(files, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                    break;
                case "time":
                default:
                    Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                    break;
            }
            
            int maxCount = jarRunner.getMaxBackupCount();
            int autoDeleteDays = jarRunner.getAutoDeleteDays();
            long currentTime = System.currentTimeMillis();
            long deleteTimeThreshold = autoDeleteDays > 0 ? currentTime - (autoDeleteDays * 24L * 60 * 60 * 1000) : 0;
            
            List<File> filesToDelete = new ArrayList<>();
            if (autoDeleteDays > 0) {
                for (File file : files) {
                    if (file.lastModified() < deleteTimeThreshold) {
                        filesToDelete.add(file);
                    }
                }
            }
            
            if (files.length > maxCount) {
                File[] sortedByTime = Arrays.copyOf(files, files.length);
                Arrays.sort(sortedByTime, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                for (int i = maxCount; i < sortedByTime.length; i++) {
                    if (!filesToDelete.contains(sortedByTime[i])) {
                        filesToDelete.add(sortedByTime[i]);
                    }
                }
            }
            
            for (File fileToDelete : filesToDelete) {
                try {
                    String canonicalPath = fileToDelete.getCanonicalPath();
                    if (canonicalPath.startsWith(canonicalBackupDir + File.separator) && 
                        fileToDelete.exists() && fileToDelete.canWrite()) {
                        if (fileToDelete.delete()) {
                            Logger.info("Auto-deleted old backup: " + fileToDelete.getName(), "Main");
                        }
                    }
                } catch (IOException e) {
                    Logger.error("Failed to delete backup file: " + fileToDelete.getName(), "Main");
                }
            }
            
            File[] validFiles = backupDir.listFiles((d, name) -> {
                if (name == null || name.isEmpty()) return false;
                if (name.contains("..") || name.contains("/") || name.contains("\\")) return false;
                return name.toLowerCase().endsWith(".zip");
            });
            
            if (validFiles == null || validFiles.length == 0) {
                return;
            }
            
            Arrays.sort(validFiles, (f1, f2) -> {
                switch (sortBy) {
                    case "size":
                        return Long.compare(f2.length(), f1.length());
                    case "name":
                        return f1.getName().compareToIgnoreCase(f2.getName());
                    case "time":
                    default:
                        return Long.compare(f2.lastModified(), f1.lastModified());
                }
            });
            
            for (File file : validFiles) {
                try {
                    String fileName = file.getName();
                    if (fileName == null || fileName.isEmpty()) continue;
                    
                    String canonicalPath = file.getCanonicalPath();
                    if (!canonicalPath.startsWith(canonicalBackupDir + File.separator) && 
                        !canonicalPath.equals(canonicalBackupDir)) {
                        continue;
                    }
                    
                    if (!file.canRead() || file.length() == 0) {
                        continue;
                    }
                    
                    String info = fileName + " (" + formatFileSize(file.length()) + ")";
                    model.addElement(info);
                } catch (IOException e) {
                    Logger.error("Failed to validate backup file: " + file.getName(), "Main");
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to load backup list: " + e.getMessage(), "Main");
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void extractZipWithProgress(File zipFile, File destDir, JProgressBar progressBar, JLabel statusLabel) throws IOException {
        if (!zipFile.exists() || !zipFile.canRead()) {
            throw new IOException("Backup file does not exist or cannot be read");
        }
        
        if (destDir == null || !destDir.exists()) {
            throw new IOException("Destination directory does not exist");
        }
        
        try (ZipFile zip = new ZipFile(zipFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            java.util.List<ZipEntry> allEntries = new java.util.ArrayList<>();
            while (entries.hasMoreElements()) {
                allEntries.add(entries.nextElement());
            }
            
            int totalEntries = allEntries.size();
            java.util.concurrent.atomic.AtomicInteger processedEntries = new java.util.concurrent.atomic.AtomicInteger(0);
            
            java.util.List<ZipEntry> dirEntries = new java.util.ArrayList<>();
            java.util.List<ZipEntry> fileEntries = new java.util.ArrayList<>();
            
            for (ZipEntry entry : allEntries) {
                String entryName = entry.getName();
                if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                    continue;
                }
                
                if (entry.isDirectory()) {
                    dirEntries.add(entry);
                } else {
                    fileEntries.add(entry);
                }
            }
            
            for (ZipEntry dirEntry : dirEntries) {
                String dirName = dirEntry.getName();
                File dirFile = new File(destDir, dirName);
                if (!dirFile.getCanonicalPath().startsWith(destDir.getCanonicalPath())) {
                    continue;
                }
                dirFile.mkdirs();
                int currentProgress = processedEntries.incrementAndGet();
                int progress = (int) ((currentProgress * 100) / totalEntries);
                progressBar.setValue(Math.min(progress, 100));
                statusLabel.setText("创建目录: " + dirName);
            }
            
            fileEntries.parallelStream().forEach(entry -> {
                String entryName = null;
                synchronized (fileEntries) {
                    entryName = entry.getName();
                }
                try {
                    File file = new File(destDir, entryName);
                    String canonicalDestDir = destDir.getCanonicalPath();
                    String canonicalFile = file.getCanonicalPath();
                    
                    if (!canonicalFile.startsWith(canonicalDestDir + File.separator) && 
                        !canonicalFile.equals(canonicalDestDir)) {
                        return;
                    }
                    
                    file.getParentFile().mkdirs();
                    try (InputStream is = new BufferedInputStream(zip.getInputStream(entry), 131072);
                         FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[131072];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    synchronized (Main.this) {
                        int currentProgress = processedEntries.incrementAndGet();
                        int progress = (int) ((currentProgress * 100) / totalEntries);
                        final int finalProgress = progress;
                        final String currentEntryName = entryName;
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(Math.min(finalProgress, 100));
                            statusLabel.setText("解压文件: " + currentEntryName);
                        });
                    }
                } catch (IOException e) {
                    Logger.error("Failed to extract file: " + entryName + " - " + e.getMessage(), "Main");
                }
            });
        }
    }
    
    private void showBackupProgressDialog(JDialog parentDialog, JarRunner jarRunner, DefaultListModel<String> backupListModel) {
        JDialog progressDialog = new JDialog(frame, "备份中...", true);
        progressDialog.setLayout(new BorderLayout(15, 15));
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(frame);
        progressDialog.setResizable(false);

        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel statusLabel = new JLabel("正在备份...");
        statusLabel.setFont(new Font(null, Font.PLAIN, 12));

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setIndeterminate(true);

        contentPanel.add(statusLabel, BorderLayout.NORTH);
        contentPanel.add(progressBar, BorderLayout.CENTER);

        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> {
            jarRunner.stopBackupTimerThread();
            progressDialog.dispose();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        progressDialog.add(contentPanel);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        jarRunner.setBackupCallback(new JarRunner.BackupCallback() {
            @Override
            public void onBackupComplete(String zipPath, boolean success) {
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    loadBackupList(backupListModel, jarRunner, "time");
                    if (parentDialog.isVisible()) {
                        parentDialog.toFront();
                        parentDialog.repaint();
                    }
                });
            }
        });

        Thread backupThread = new Thread(() -> {
            jarRunner.triggerBackup();
        });
        backupThread.start();

        progressDialog.setVisible(true);
    }
    
    private void showRestoreProgressDialog(JDialog parentDialog, JarRunner jarRunner, String backupFileName) {
        JDialog progressDialog = new JDialog(frame, "恢复备份中...", true);
        progressDialog.setLayout(new BorderLayout(15, 15));
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(frame);
        progressDialog.setResizable(false);

        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel statusLabel = new JLabel("正在解压文件...");
        statusLabel.setFont(new Font(null, Font.PLAIN, 12));

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setIndeterminate(true);

        contentPanel.add(statusLabel, BorderLayout.NORTH);
        contentPanel.add(progressBar, BorderLayout.CENTER);

        JButton cancelButton = new JButton("取消");
        final boolean[] cancelled = {false};
        cancelButton.addActionListener(e -> {
            cancelled[0] = true;
            progressDialog.dispose();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        progressDialog.add(contentPanel);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        Thread restoreThread = new Thread(() -> {
            try {
                String serverName = sanitizeFileName(new File(jarRunner.getJarPath()).getName());
                String backupDirPath = "MSH/backup/" + serverName;
                File backupDir = new File(backupDirPath);
                File zipFile = new File(backupDir, backupFileName);
                File serverDir = new File(jarRunner.getJarPath()).getParentFile();

                extractZipWithProgress(zipFile, serverDir, progressBar, statusLabel);

                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    jarRunner.getOutputPanel().append("[MSH] 备份已恢复: " + backupFileName + "\n");
                    Logger.info("Backup restored successfully: " + backupFileName, "Main");
                    JOptionPane.showMessageDialog(parentDialog, "备份恢复成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
                    if (parentDialog.isVisible()) {
                        parentDialog.toFront();
                        parentDialog.repaint();
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(parentDialog, "恢复失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    Logger.error("Backup restore failed: " + ex.getMessage(), "Main");
                });
            }
        });
        restoreThread.start();

        progressDialog.setVisible(true);
    }
    
    private void handleWindowClosing() {
        if (shuttingDown) return;
        shuttingDown = true;
        Logger.info("Application shutdown initiated", "Main");
        
        JDialog progressDialog = new JDialog(frame, "正在关闭服务器", true);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        JLabel statusLabel = new JLabel("正在发送停止命令...");
        progressBar.setPreferredSize(new Dimension(300, 25));
        progressDialog.add(BorderLayout.CENTER, progressBar);
        progressDialog.add(BorderLayout.SOUTH, statusLabel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(frame);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() {
                for (JarRunner jarRunner : jarRunners) {
                    if (jarRunner.getStatus() == JarRunner.Status.RUNNING) {
                        jarRunner.stop();
                    }
                }
                
                int waitCount = 0;
                int maxWait = 30;
                while (waitCount < maxWait) {
                    boolean allStopped = true;
                    for (JarRunner jarRunner : jarRunners) {
                        if (jarRunner.getStatus() != JarRunner.Status.STOPPED) {
                            allStopped = false;
                            break;
                        }
                    }
                    if (allStopped) return true;
                    
                    try {
                        Thread.sleep(500);
                        publish("等待服务器停止... " + (waitCount * 500 / 1000) + "/" + maxWait + " 秒");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    waitCount++;
                }
                
                for (JarRunner jarRunner : jarRunners) {
                    if (jarRunner.getStatus() == JarRunner.Status.RUNNING) {
                        jarRunner.stop();
                    }
                }
                
                waitCount = 0;
                while (waitCount < 10) {
                    boolean allStopped = true;
                    for (JarRunner jarRunner : jarRunners) {
                        if (jarRunner.getStatus() != JarRunner.Status.STOPPED) {
                            allStopped = false;
                            break;
                        }
                    }
                    if (allStopped) return true;
                    
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    waitCount++;
                }
                
                boolean stillRunning = false;
                for (JarRunner jarRunner : jarRunners) {
                    if (jarRunner.getStatus() != JarRunner.Status.STOPPED) {
                        stillRunning = true;
                        jarRunner.forceStop();
                    }
                }
                
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                return !stillRunning;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusLabel.setText(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                    progressDialog.dispose();
                    Logger.info("Application exiting", "Main");
                    Runtime.getRuntime().exit(0);
                } catch (Exception e) {
                    Logger.error("Error during shutdown: " + e.getMessage(), "Main");
                    Runtime.getRuntime().exit(0);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
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
        
        JMenuItem killProcessItem = new JMenuItem("强制终止进程");
        killProcessItem.addActionListener(a -> killServerProcess(tabIndex));
        menu.add(killProcessItem);
        
        menu.show(frame, x, y);
    }
    
    private void killServerProcess(int tabIndex) {
        if (tabIndex <= 0) {
            return;
        }
        JarRunner jarRunner = jarRunners.get(tabIndex - 1);
        String serverName = jarRunner.getDisplayName();
        
        int confirm = JOptionPane.showConfirmDialog(
            frame,
            "确定要强制终止服务器 '" + serverName + "' 的所有相关进程吗？\n这将立即停止服务器且不会发送停止命令。",
            "确认终止进程",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        jarRunner.forceStopWithWait();
        jarRunner.checkAndKillLeftoverProcesses();
        
        JOptionPane.showMessageDialog(
            frame,
            "进程终止完成。请重新启动服务器。",
            "操作完成",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void renameServerTab(int tabIndex) {
        if (tabIndex <= 0) {
            return;
        }
        JarRunner jarRunner = jarRunners.get(tabIndex - 1);
        String currentName = jarRunner.getDisplayName();
        String jarFileName = new File(jarRunner.getJarPath()).getName();
        
        JDialog renameDialog = new JDialog(frame, "重命名标签页", false);
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
            jarRunner.setForceKeepAlive((Boolean) guardConfig[1]);
            jarRunner.setRestartSettings((Integer) guardConfig[2], (Integer) guardConfig[3]);
        }

        Object[] backupConfig = loadBackupConfig(jarPath);
        if (backupConfig != null) {
            jarRunner.setBackupEnabled((Boolean) backupConfig[0]);
            jarRunner.setBackupIntervalMinutes((Integer) backupConfig[1]);
            jarRunner.setMaxBackupCount((Integer) backupConfig[2]);
            jarRunner.setAutoDeleteDays((Integer) backupConfig[3]);
        }
        
        boolean useNoGui = loadOtherConfig(jarPath);
        jarRunner.setUseNoGui(useNoGui);
        
        jarRunners.add(jarRunner);
        
        String customName = loadCustomName(jarPath);
        if (customName != null) {
            jarRunner.setCustomName(customName);
        }
        
        String displayName = jarRunner.getDisplayName();
        
        JPanel serverPanel = new JPanel(new BorderLayout());
        JPanel statusPanel = new JPanel(new BorderLayout());
        JPanel statusLeftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel statusLabel = new JLabel("服务器状态: 已停止");
        statusLabel.setForeground(Color.RED);
        statusLeftPanel.add(statusLabel);
        statusPanel.add(statusLeftPanel, BorderLayout.WEST);
        JPanel statusRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportServerLogButton = new JButton("导出日志");
        exportServerLogButton.addActionListener(e -> exportServerLog(jarRunner, displayName));
        statusRightPanel.add(exportServerLogButton);
        JButton clearOutputButton = new JButton("清空输出");
        clearOutputButton.addActionListener(e -> outputPanel.clearOutput());
        statusRightPanel.add(clearOutputButton);
        statusPanel.add(statusRightPanel, BorderLayout.EAST);
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
                jarRunner.addToHistory(finalCmd);
            }
        });
        sendCommandButton.addActionListener(e -> {
            String command = commandField.getText().trim();
            if (!command.isEmpty()) {
                jarRunner.sendCommand(command);
                jarRunner.addToHistory(command);
                commandField.setText("");
            }
        });
        commandField.addActionListener(e -> {
            String command = commandField.getText().trim();
            if (!command.isEmpty()) {
                jarRunner.sendCommand(command);
                jarRunner.addToHistory(command);
                commandField.setText("");
            }
        });
        commandField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    String prevCmd = jarRunner.getPreviousCommand();
                    if (prevCmd != null) {
                        commandField.setText(prevCmd);
                        commandField.setCaretPosition(prevCmd.length());
                    }
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    String nextCmd = jarRunner.getNextCommand();
                    if (nextCmd != null) {
                        commandField.setText(nextCmd);
                        commandField.setCaretPosition(nextCmd.length());
                    } else {
                        commandField.setText("");
                    }
                    e.consume();
                }
            }
        });
        JPanel commandInputPanel = new JPanel(new BorderLayout(5, 5));
        commandInputPanel.add(quickCommandComboBox, BorderLayout.NORTH);
        commandInputPanel.add(commandField, BorderLayout.CENTER);
        commandPanel.add(commandInputPanel, BorderLayout.CENTER);
        commandPanel.add(sendCommandButton, BorderLayout.EAST);
        JPanel controlPanel = new JPanel(new GridLayout(2, 5, 5, 2));
        JButton startButton = new JButton("启动服务器");
        startButton.setMaximumSize(new Dimension(120, 25));
        JButton stopButton = new JButton("关闭服务器");
        stopButton.setMaximumSize(new Dimension(120, 25));
        JButton forceStopButton = new JButton("强制关闭");
        forceStopButton.setMaximumSize(new Dimension(120, 25));
        JButton restartButton = new JButton("重启服务器");
        restartButton.setMaximumSize(new Dimension(120, 25));
        JButton reloadButton = new JButton("重载服务器");
        reloadButton.setMaximumSize(new Dimension(120, 25));
        JButton configButton = new JButton("配置管理");
        configButton.setMaximumSize(new Dimension(120, 25));
        JButton guardSettingsButton = new JButton("进程守护设置");
        guardSettingsButton.setMaximumSize(new Dimension(120, 25));
        JButton networkAddressButton = new JButton("查看地址");
        networkAddressButton.setMaximumSize(new Dimension(120, 25));
        JButton gameRuleButton = new JButton("游戏规则");
        gameRuleButton.setMaximumSize(new Dimension(120, 25));
        JButton backupSettingsButton = new JButton("备份设置");
        backupSettingsButton.setMaximumSize(new Dimension(120, 25));
        backupSettingsButton.addActionListener(a -> {
            showBackupSettingsDialog(jarRunner);
        });
        JButton otherSettingsButton = new JButton("其他设置");
        otherSettingsButton.setMaximumSize(new Dimension(120, 25));
        otherSettingsButton.addActionListener(a -> {
            showOtherSettingsDialog(jarRunner);
        });
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
        controlPanel.add(backupSettingsButton);
        controlPanel.add(otherSettingsButton);
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
        
        java.util.concurrent.atomic.AtomicBoolean shownLockDialog = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        Thread statusThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    JarRunner.Status currentStatus = jarRunner.getStatus();
                    
                    if (currentStatus == JarRunner.Status.RUNNING && !jarRunner.isProcessAlive()) {
                        
                        jarRunner.onProcessTerminated();
                        currentStatus = jarRunner.getStatus();
                        
                        if (currentStatus == JarRunner.Status.STOPPED && shownLockDialog.compareAndSet(false, true)) {
                             String reason = jarRunner.getTerminationReason();
                             String error = jarRunner.getLastError();
                             
                             javax.swing.SwingUtilities.invokeLater(() -> {
                                 StringBuilder message = new StringBuilder();
                                 message.append("<html>服务器启动失败!");
                                 
                                 if (!reason.isEmpty()) {
                                     message.append("<br><br><b>检测到文件锁定</b>");
                                     message.append("<br>原因: ").append(reason);
                                 }
                                 
                                 if (error != null && !error.isEmpty()) {
                                     message.append("<br><br><b>错误信息</b>");
                                     message.append("<br>").append(error);
                                 }
                                 
                                 message.append("<br><br>是否尝试强制解除锁定并重新启动?</html>");
                                 
                                 int choice = JOptionPane.showConfirmDialog(
                                     frame,
                                     message.toString(),
                                     "服务器启动失败",
                                     JOptionPane.YES_NO_OPTION,
                                     JOptionPane.WARNING_MESSAGE
                                 );
                                 
                                 if (choice == JOptionPane.YES_OPTION) {
                                     jarRunner.clearLastError();
                                     jarRunner.forceUnlockAndRestart();
                                 } else {
                                     jarRunner.clearLastError();
                                 }
                             });
                         }
                    }
                    
                    if (currentStatus == JarRunner.Status.RUNNING) {
                        shownLockDialog.set(false);
                    }
                    
                    final JarRunner.Status finalStatus = currentStatus;
                    final String serverVersion = jarRunner.getServerVersion();
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
                                if (serverVersion != null && !serverVersion.isEmpty()) {
                                    statusLabel.setText("服务器状态: 运行中 (MC " + serverVersion + ")");
                                } else {
                                    statusLabel.setText("服务器状态: 运行中");
                                }
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
