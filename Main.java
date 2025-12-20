import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
public class Main {
    private static final String VERSION = "1.0.1";
    private static final String AUTHOR = "znpwlk";
    private static final String APP_NAME = "Minecraft Server Hub";
    private static final String APP_SHORT_NAME = "MSH";
    private JFrame frame;
    private JTabbedPane tabbedPane;
    private List<JarRunner> jarRunners;
    private Properties config;
    private File configFile;
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new Main().createAndShowGUI();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    private void createAndShowGUI() {
        config = new Properties();
        configFile = new File("server_manager_config.properties");
        loadConfig();
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
        frame.setSize(1000, 700);
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
                "功能: 管理Minecraft服务器", 
                "关于", JOptionPane.INFORMATION_MESSAGE);
        });
        topPanel.add(aboutButton);
        tabbedPane = new JTabbedPane();
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(tabbedPane, BorderLayout.CENTER);
        String lastServerPath = config.getProperty("last_server_path");
        if (lastServerPath != null && !lastServerPath.isEmpty()) {
            File lastServer = new File(lastServerPath);
            if (lastServer.exists()) {
                SwingUtilities.invokeLater(() -> {
                    addServerFromPath(lastServerPath);
                });
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
            e.printStackTrace();
        }
    }
    private void saveConfig() {
        try (OutputStream output = new FileOutputStream(configFile)) {
            config.store(output, APP_NAME + " Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void handleWindowClosing() {
        jarRunners.forEach(JarRunner::stop);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        boolean hasRunningServer = jarRunners.stream()
            .anyMatch(runner -> runner.getStatus() != JarRunner.Status.STOPPED);
        if (hasRunningServer) {
            JOptionPane.showMessageDialog(
                frame,
                "部分服务器无法正常关闭，可能需要手动关闭进程。",
                "关闭警告",
                JOptionPane.WARNING_MESSAGE
            );
        }
        System.exit(0);
    }
    private void addServer(ActionEvent e) {
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
                

                for (JarRunner existingRunner : jarRunners) {
                    if (existingRunner.getJarPath().equals(jarPath)) {
                        JOptionPane.showMessageDialog(frame, "该服务器文件已经被打开！", "提示", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }
                
                config.setProperty("last_server_path", jarPath);
                saveConfig();
                addServerFromPath(jarPath);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    

    private void addServerFromPath(String jarPath) {
        File jarFile = new File(jarPath);
        String serverName = jarFile.getName();
        File serverDir = jarFile.getParentFile();
        ColorOutputPanel outputPanel = new ColorOutputPanel();
        JarRunner jarRunner = new JarRunner(jarPath, outputPanel);
        jarRunners.add(jarRunner);
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
        JButton stopButton = new JButton("停止服务器");
        JButton restartButton = new JButton("重启服务器");
        JButton reloadButton = new JButton("重载服务器");
        JButton configButton = new JButton("配置管理");
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        restartButton.setEnabled(false);
        reloadButton.setEnabled(false);
        startButton.addActionListener(a -> {
            jarRunner.start();
        });
        stopButton.addActionListener(a -> {
            int confirm = JOptionPane.showConfirmDialog(
                frame,
                "确定要停止服务器吗？这将中断所有玩家的游戏连接。",
                "确认停止服务器",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                jarRunner.stop();
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
        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(restartButton);
        controlPanel.add(reloadButton);
        controlPanel.add(configButton);
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(controlPanel, BorderLayout.NORTH);
        bottomPanel.add(commandPanel, BorderLayout.CENTER);
        serverPanel.add(bottomPanel, BorderLayout.SOUTH);
        int tabIndex = tabbedPane.getTabCount();
        tabbedPane.addTab(serverName, serverPanel);
        tabbedPane.setSelectedIndex(tabIndex);
        Thread statusThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    JarRunner.Status currentStatus = jarRunner.getStatus();
                    SwingUtilities.invokeLater(() -> {
                        switch (currentStatus) {
                            case STOPPED:
                                statusLabel.setText("服务器状态: 已停止");
                                statusLabel.setForeground(Color.RED);
                                startButton.setEnabled(true);
                                stopButton.setEnabled(false);
                                restartButton.setEnabled(false);
                                reloadButton.setEnabled(false);
                                break;
                            case RUNNING:
                                statusLabel.setText("服务器状态: 运行中");
                                statusLabel.setForeground(Color.GREEN);
                                startButton.setEnabled(false);
                                stopButton.setEnabled(true);
                                restartButton.setEnabled(true);
                                reloadButton.setEnabled(true);
                                break;
                            case STARTING:
                                statusLabel.setText("服务器状态: 启动中");
                                statusLabel.setForeground(Color.ORANGE);
                                startButton.setEnabled(false);
                                stopButton.setEnabled(true);
                                restartButton.setEnabled(true);
                                reloadButton.setEnabled(false);
                                break;
                            case STOPPING:
                                statusLabel.setText("服务器状态: 停止中");
                                statusLabel.setForeground(Color.ORANGE);
                                startButton.setEnabled(false);
                                stopButton.setEnabled(false);
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
