import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
public class OutputHandler implements Runnable {
    private static final Pattern GAMERULE_PATTERN = Pattern.compile("\\[\\d{2}:\\d{2}:\\d{2} (?:INFO|WARN|ERROR)\\]: Gamerule (.+?) is currently set to: (true|false|\\d+)");
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+\\.?\\d*)");
    private static final Pattern ERROR_PATTERN = Pattern.compile("(Exception|Error|FAILED|Caused by)");
    private BufferedReader reader;
    private ColorOutputPanel outputPanel;
    private JarRunner jarRunner;
    private String jarPath;
    private boolean eulaChecked = false;
    private String lastErrorInfo = "";
    private static volatile GameRuleConfig.MCVersion detectedVersion = null;
    public OutputHandler(InputStream inputStream, ColorOutputPanel outputPanel, JarRunner jarRunner, String jarPath) {
        String charset = EncodingUtils.getServerProcessCharset();
        try {
            this.reader = new BufferedReader(new InputStreamReader(inputStream, charset));
        } catch (Exception e) {
            Logger.error("Failed to use charset " + charset + ", falling back to UTF-8: " + e.getMessage(), "OutputHandler");
            this.reader = new BufferedReader(new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8));
        }

        this.outputPanel = outputPanel;
        this.jarRunner = jarRunner;
        this.jarPath = jarPath;
    }

    @Override
    public void run() {
        String line;
        try {
            Logger.error("CRITICAL: Output processing thread started for: " + jarPath, "OutputHandler");
            while ((line = reader.readLine()) != null) {
                if (jarRunner != null && !jarRunner.canOutputHandlerWrite()) {
                    break;
                }
                try {
                    outputPanel.appendColorText(line + "\n");
                    if (!eulaChecked && (line.contains("EULA") || line.contains("eula.txt"))) {
                        eulaChecked = true;
                        handleEula();
                    }
                    if (line.contains("Running Java")) {
                        String javaInfo = line.substring(line.indexOf("Running Java") + 13);
                        outputPanel.append("[MSH] 服务器正在使用: " + javaInfo + "\n");
                    } else if (line.contains("Loading Paper")) {
                        String paperInfo = line.substring(line.indexOf("Loading Paper") + 13);
                        outputPanel.append("[MSH] 服务器版本: " + paperInfo + "\n");
                    } else if (line.contains("Starting Minecraft server on")) {
                        String portInfo = line.substring(line.indexOf("on") + 3).trim();
                        outputPanel.append("[MSH] 服务器已在端口 " + portInfo + " 上启动\n");
                    } else if (line.contains("YOU ARE RUNNING THIS SERVER AS AN ADMINISTRATIVE OR ROOT USER")) {
                        outputPanel.append("[MSH] 警告: 您正在以管理员身份运行服务器，存在安全风险！\n");
                        outputPanel.append("[MSH] 建议：创建普通用户并以普通用户身份运行服务器，以提高安全性。\n");
                        outputPanel.append("[MSH] 安全文档：https://madelinemiller.dev/blog/root-minecraft-server/\n");
                    } else if (line.contains("Initializing plugins")) {
                        outputPanel.append("[MSH] 正在初始化插件...\n");
                    } else if (line.contains("Initialized ") && line.contains(" plugins")) {
                        String pluginCount = line.substring(line.indexOf("Initialized") + 13, line.indexOf("plugins")).trim();
                        outputPanel.append("[MSH] 已加载 " + pluginCount + " 个插件\n");
                    } else if (line.contains("Default game type:")) {
                        String gamemode = line.substring(line.indexOf(":") + 2).trim();
                        outputPanel.append("[MSH] 默认游戏模式: " + gamemode + "\n");
                    } else if (line.contains("Preparing level")) {
                        String worldName = line.substring(line.indexOf("level") + 6).trim();
                        outputPanel.append("[MSH] 正在准备世界: " + worldName + "\n");
                    } else if (line.contains("Preparing spawn area: 100%")) {
                        outputPanel.append("[MSH] 生成区域准备完成\n");
                    } else if (line.contains("Done (")) {
                        outputPanel.append("[MSH] 服务器已成功启动！\n");
                        jarRunner.onServerFullyStarted();
                    } else if (line.contains("This is the first time you're starting this server")) {
                        outputPanel.append("[MSH] 首次启动服务器，建议阅读官方文档: https://docs.papermc.io/paper/next-steps\n");
                    } else if (line.contains("There are") && line.contains("players online")) {
                        try {
                            int playerCount = Integer.parseInt(line.substring(line.indexOf("There are") + 10, line.indexOf(" of a max")));
                            int maxPlayers = Integer.parseInt(line.substring(line.indexOf("of a max of") + 13, line.indexOf(" players online")));
                            String playersList = line.substring(line.indexOf(":") + 2).trim();
                            outputPanel.append("[MSH] 在线玩家: " + playerCount + "/" + maxPlayers + " | 玩家列表: " + (playersList.isEmpty() ? "无" : playersList) + "\n");
                        } catch (Exception e) {
                            Logger.error("Failed to parse player count information: " + e.getMessage(), "OutputHandler");
                            outputPanel.append("[MSH] 在线玩家: " + line + "\n");
                        }
                    } else if (line.contains("No existing world data, creating new world")) {
                        outputPanel.append("[MSH] 正在创建新的世界数据...\n");
                    } else if (line.contains("Loaded ") && line.contains(" recipes")) {
                        String recipeCount = line.substring(line.indexOf("Loaded") + 7, line.indexOf("recipes")).trim();
                        outputPanel.append("[MSH] 已加载 " + recipeCount + " 个配方\n");
                    } else if (line.contains("Loaded ") && line.contains(" advancements")) {
                        String advancementCount = line.substring(line.indexOf("Loaded") + 7, line.indexOf("advancements")).trim();
                        outputPanel.append("[MSH] 已加载 " + advancementCount + " 个成就\n");
                    } else if (line.contains("Starting minecraft server version")) {
                        String mcVersion = line.substring(line.indexOf("version") + 8).trim();
                        outputPanel.append("[MSH] Minecraft版本: " + mcVersion + "\n");
                        detectMcVersion(mcVersion);
                    } else if (line.contains("Server Ping Player Sample Count:")) {
                        String sampleCount = line.substring(line.indexOf(":") + 2).trim();
                        outputPanel.append("[MSH] 服务器Ping样本数: " + sampleCount + "\n");
                    } else if (line.contains("Using ") && line.contains(" threads for Netty based IO")) {
                        String threadCount = line.substring(line.indexOf("Using") + 6, line.indexOf("threads")).trim();
                        outputPanel.append("[MSH] 使用 " + threadCount + " 个线程处理网络IO\n");
                    } else if (line.contains("Paper is using ") && line.contains(" worker threads")) {
                        outputPanel.append("[MSH] 服务器线程配置已完成\n");
                    } else if (line.contains("Generating keypair")) {
                        outputPanel.append("[MSH] 正在生成密钥对...\n");
                    } else if (line.contains("Selecting spawn point for world")) {
                        outputPanel.append("[MSH] 正在为世界选择出生点...\n");
                    } else if (line.contains("Loading ") && line.contains(" persistent chunks for world")) {
                        outputPanel.append("[MSH] 正在加载世界区块...\n");
                    } else if (line.contains("Prepared spawn area in")) {
                        outputPanel.append("[MSH] 出生点区域准备完成\n");
                    } else if (line.contains("Running delayed init tasks")) {
                        outputPanel.append("[MSH] 正在执行延迟初始化任务...\n");
                    } else if (line.contains("Stopping the server") || line.contains("Stopping server")) {
                        outputPanel.append("[MSH] 正在停止服务器...\n");
                        jarRunner.onServerStopping();
                    } else if (line.contains("Saving players")) {
                        outputPanel.append("[MSH] 正在保存玩家数据...\n");
                        jarRunner.onServerStopping();
                    } else if (line.contains("Saving worlds")) {
                        outputPanel.append("[MSH] 正在保存世界数据...\n");
                        jarRunner.onServerStopping();
                    } else if (line.contains("Saving chunks for level")) {
                        outputPanel.append("[MSH] 正在保存区块数据...\n");
                        jarRunner.onServerStopping();
                    } else if (line.contains("All chunks are saved")) {
                        outputPanel.append("[MSH] 所有区块已保存\n");
                        jarRunner.onServerStopping();
                    } else if (line.contains("Done saving")) {
                        outputPanel.append("[MSH] 数据保存完成\n");
                        jarRunner.onServerStopping();
                    } else if (line.contains("Server stopped")) {
                        jarRunner.onServerStopping();
                    } else if (line.contains("All RegionFile I/O tasks to complete")) {
                        jarRunner.onServerStopping();
                    } else if (line.contains("Shutting down") || line.contains("shutting down")) {
                        jarRunner.onServerStopping();
                    } else if (line.contains("Stopping all worlds")) {
                        jarRunner.onServerStopping();
                    } else if (line.contains("Unloading world") || line.contains("Unloading level")) {
                        jarRunner.onServerStopping();
                    } else if (line.contains("Closing level") || line.contains("Closing world")) {
                        jarRunner.onServerStopping();
                    } else if (line.contains("Stopping Rcon connection")) {
                        jarRunner.onServerStopping();
                    } else if (line.contains("Seed:")) {
                        String seed = line.substring(line.indexOf("Seed:")).trim();
                        outputPanel.append("[MSH] " + seed.replace("Seed:", "世界种子:") + "\n");
                    } else if (line.contains("Checking version, please wait...")) {
                        outputPanel.append("[MSH] 正在检查版本，请稍候...\n");
                    } else if (line.contains("This server is running Paper version")) {
                        String versionInfo = line.substring(line.indexOf("Paper version") + 14).trim();
                        outputPanel.append("[MSH] 服务器正在运行 Paper 版本: " + versionInfo + "\n");
                        detectMcVersion(versionInfo);
                    } else if (line.contains("You are running the latest version")) {
                        outputPanel.append("[MSH] 您正在运行最新版本\n");
                    }
                    
                    if (ERROR_PATTERN.matcher(line).find()) {
                        lastErrorInfo = line;
                        if (line.contains("FileSystemException") || line.contains("另一个程序")) {
                            lastErrorInfo = "文件被其他程序锁定";
                        } else if (line.contains("IOException") && line.contains("locked")) {
                            lastErrorInfo = "文件被锁定";
                        } else if (line.contains("OutOfMemoryError") || line.contains("java.lang.OutOfMemoryError")) {
                            lastErrorInfo = "内存不足";
                        } else if (line.contains("Access denied") || line.contains("权限")) {
                            lastErrorInfo = "权限不足";
                        } else if (line.contains("Port") && line.contains("in use")) {
                            lastErrorInfo = "端口已被占用";
                        } else if (line.contains("BindException")) {
                            lastErrorInfo = "端口绑定失败";
                        } else if (line.contains("Failed to start")) {
                            lastErrorInfo = "启动失败";
                        }
                        
                        if (jarRunner != null && lastErrorInfo != null) {
                            jarRunner.setLastError(lastErrorInfo);
                        }
                    }
                    
                    Matcher gameruleMatcher = GAMERULE_PATTERN.matcher(line);
                    if (gameruleMatcher.find()) {
                        String ruleName = gameruleMatcher.group(1).trim();
                        String ruleValue = gameruleMatcher.group(2).trim();
                        Logger.info("DEBUG: 解析到游戏规则 - " + ruleName + " = " + ruleValue, "OutputHandler");
                        jarRunner.onGameRuleValue(ruleName, ruleValue);
                    } else if (line.contains("Gamerule") && line.contains("is currently set to")) {
                        Logger.info("DEBUG: 包含Gamerule但未匹配, 原始行: " + line, "OutputHandler");
                        Logger.info("DEBUG: 正则模式: " + GAMERULE_PATTERN.pattern(), "OutputHandler");
                    }
                } catch (OutOfMemoryError e) {
                    Logger.error("FATAL: Out of memory during output processing for: " + jarPath + " - " + e.getMessage(), "OutputHandler");
                    System.err.println("FATAL ERROR: Out of memory during output processing. Server may be unstable.");
                    break;
                } catch (StackOverflowError e) {
                    Logger.error("FATAL: Stack overflow during output processing for: " + jarPath + " - " + e.getMessage(), "OutputHandler");
                    System.err.println("FATAL ERROR: Stack overflow during output processing. JVM is unstable.");
                    break;
                } catch (VirtualMachineError e) {
                    Logger.error("FATAL: JVM internal error during output processing for: " + jarPath + " - " + e.getClass().getSimpleName(), "OutputHandler");
                    System.err.println("FATAL ERROR: JVM internal error. Application cannot continue.");
                    break;
                } catch (ExceptionInInitializerError e) {
                    Logger.error("FATAL: Class initialization error during output processing: " + e.getMessage(), "OutputHandler");
                    System.err.println("FATAL ERROR: Class initialization failed during output processing.");
                    break;
                } catch (NoClassDefFoundError e) {
                    Logger.error("FATAL: Required class not found during output processing: " + e.getMessage(), "OutputHandler");
                    System.err.println("FATAL ERROR: Missing required classes during output processing.");
                    break;
                } catch (SecurityException e) {
                    Logger.error("FATAL: Security violation during output processing: " + e.getMessage(), "OutputHandler");
                    System.err.println("FATAL ERROR: Security violation during output processing.");
                    break;
                } catch (Exception e) {
                    Logger.error("CRITICAL: Unexpected exception during output processing for: " + jarPath + " - " + e.getClass().getSimpleName() + " - " + e.getMessage(), "OutputHandler");
                }
            }
        } catch (IOException e) {
            Logger.error("CRITICAL: Failed to process server output stream for: " + jarPath + " - " + e.getMessage(), "OutputHandler");
        } catch (OutOfMemoryError e) {
            Logger.error("FATAL: Out of memory in main output processing loop for: " + jarPath + " - " + e.getMessage(), "OutputHandler");
            System.err.println("FATAL ERROR: Out of memory in output processing. Application cannot continue.");
        } catch (StackOverflowError e) {
            Logger.error("FATAL: Stack overflow in main output processing loop for: " + jarPath + " - " + e.getMessage(), "OutputHandler");
            System.err.println("FATAL ERROR: Stack overflow in output processing. JVM is unstable.");
        } catch (VirtualMachineError e) {
            Logger.error("FATAL: JVM internal error in main output processing loop: " + e.getClass().getSimpleName(), "OutputHandler");
            System.err.println("FATAL ERROR: JVM internal error. Application cannot continue.");
        } catch (ExceptionInInitializerError e) {
            Logger.error("FATAL: Class initialization error in main output processing loop: " + e.getMessage(), "OutputHandler");
            System.err.println("FATAL ERROR: Class initialization failed in output processing.");
        } catch (NoClassDefFoundError e) {
            Logger.error("FATAL: Required class not found in main output processing loop: " + e.getMessage(), "OutputHandler");
            System.err.println("FATAL ERROR: Missing required classes in output processing.");
        } catch (SecurityException e) {
            Logger.error("FATAL: Security violation in main output processing loop: " + e.getMessage(), "OutputHandler");
            System.err.println("FATAL ERROR: Security violation in output processing.");
        } catch (Throwable t) {
            Logger.error("FATAL: Unexpected error in main output processing loop for: " + jarPath + " - " + t.getClass().getSimpleName() + " - " + t.getMessage(), "OutputHandler");
            System.err.println("FATAL ERROR: Unexpected error in output processing. Shutting down.");
        } finally {
            try {
                Logger.error("CRITICAL: Output processing thread ending for: " + jarPath, "OutputHandler");
                reader.close();
            } catch (IOException e) {
                Logger.error("CRITICAL: Failed to close output stream reader for: " + jarPath + " - " + e.getMessage(), "OutputHandler");
            } catch (Exception e) {
                Logger.error("FATAL: Unexpected error closing output stream reader: " + e.getMessage(), "OutputHandler");
            }
        }
    }

    
    private void handleEula() {
        File jarFile = new File(jarPath);
        File serverDir = jarFile.getParentFile();
        File eulaFile = new File(serverDir, "eula.txt");
        if (eulaFile.exists()) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                int option = JOptionPane.showConfirmDialog(
                    null,
                    "Minecraft服务器需要您同意最终用户许可协议(EULA)才能运行。\n是否自动修改eula.txt文件以同意EULA？",
                    "EULA同意确认",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                );
                if (option == JOptionPane.YES_OPTION) {
                    try {
                        String charset = EncodingUtils.getOptimalCharset();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(eulaFile), charset));
                        String content = reader.lines().collect(java.util.stream.Collectors.joining(System.lineSeparator()));
                        reader.close();
                        content = content.replace("eula=false", "eula=true");
                        if (!content.contains("eula=true")) {
                            content += System.lineSeparator() + "eula=true";
                        }
                        BufferedWriter writer = new BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(eulaFile), charset));
                        writer.write(content);
                        writer.close();
                        outputPanel.append("[MSH] 已自动修改eula.txt，同意EULA\n");
                        jarRunner.restart();
                    } catch (IOException e) {
                        Logger.error("Failed to modify eula.txt: " + e.getMessage(), "OutputHandler");
                        outputPanel.append("[MSH] 修改eula.txt失败: " + e.getMessage() + "\n");
                    }
                } else if (option == JOptionPane.NO_OPTION) {
                    jarRunner.setEulaExit(true);
                }
            });
        }
    }

    private void detectMcVersion(String versionInfo) {
        try {
            Matcher matcher = VERSION_PATTERN.matcher(versionInfo);
            if (matcher.find()) {
                String version = matcher.group(1);
                String[] parts = version.split("\\.");
                if (parts.length >= 2) {
                    int minor = Integer.parseInt(parts[1]);
                    int patch = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;

                    String displayVersion = "1." + minor;
                    if (patch > 0) {
                        displayVersion += "." + patch;
                    }

                    if (jarRunner != null) {
                        jarRunner.setServerVersion(displayVersion);
                    }

                    GameRuleConfig.setDetectedVersion(displayVersion);

                    if (detectedVersion != null) {
                        outputPanel.append("[MSH] 自动检测到MC版本: " + GameRuleConfig.getCurrentVersion().getDisplayName() + "\n");
                        if (GameRuleConfig.isUsingJsonConfig()) {
                            outputPanel.append("[MSH] 使用JSON配置文件: " + GameRuleConfig.getActiveJsonVersion() + ".json\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to detect MC version: " + e.getMessage(), "OutputHandler");
        }
    }
}
