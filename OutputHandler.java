import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.swing.JOptionPane;
public class OutputHandler implements Runnable {
    private BufferedReader reader;
    private ColorOutputPanel outputPanel;
    private JarRunner jarRunner;
    private String jarPath;
    private boolean eulaChecked = false;
    public OutputHandler(InputStream inputStream, ColorOutputPanel outputPanel, JarRunner jarRunner, String jarPath) {
        this.reader = new BufferedReader(new InputStreamReader(inputStream));
        this.outputPanel = outputPanel;
        this.jarRunner = jarRunner;
        this.jarPath = jarPath;
    }
    @Override
    public void run() {
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                outputPanel.appendColorText(line + "\n");
                if (!eulaChecked && (line.contains("EULA") || line.contains("eula.txt"))) {
                    eulaChecked = true;
                    handleEula();
                }
                if (line.contains("Running Java")) {
                    String javaInfo = line.substring(line.indexOf("Running Java") + 13);
                    outputPanel.append("[MSH] 服务器正在使用: " + javaInfo);
                } else if (line.contains("Loading Paper")) {
                    String paperInfo = line.substring(line.indexOf("Loading Paper") + 13);
                    outputPanel.append("[MSH] 服务器版本: " + paperInfo);
                } else if (line.contains("Starting Minecraft server on")) {
                    String portInfo = line.substring(line.indexOf("on") + 3).trim();
                    outputPanel.append("[MSH] 服务器已在端口 " + portInfo + " 上启动");
                } else if (line.contains("YOU ARE RUNNING THIS SERVER AS AN ADMINISTRATIVE OR ROOT USER")) {
                    outputPanel.append("[MSH] 警告: 您正在以管理员身份运行服务器，存在安全风险！");
                    outputPanel.append("[MSH] 建议：创建普通用户并以普通用户身份运行服务器，以提高安全性。");
                    outputPanel.append("[MSH] 安全文档：https://madelinemiller.dev/blog/root-minecraft-server/");
                } else if (line.contains("Initializing plugins")) {
                    outputPanel.append("[MSH] 正在初始化插件...");
                } else if (line.contains("Initialized ") && line.contains(" plugins")) {
                    String pluginCount = line.substring(line.indexOf("Initialized") + 13, line.indexOf("plugins")).trim();
                    outputPanel.append("[MSH] 已加载 " + pluginCount + " 个插件");
                } else if (line.contains("Default game type:")) {
                    String gamemode = line.substring(line.indexOf(":") + 2).trim();
                    outputPanel.append("[MSH] 默认游戏模式: " + gamemode);
                } else if (line.contains("Preparing level")) {
                    String worldName = line.substring(line.indexOf("level") + 6).trim();
                    outputPanel.append("[MSH] 正在准备世界: " + worldName);
                } else if (line.contains("Preparing spawn area: 100%")) {
                    outputPanel.append("[MSH] 生成区域准备完成");
                } else if (line.contains("Done (")) {
                    outputPanel.append("[MSH] 服务器已成功启动！");
                } else if (line.contains("This is the first time you're starting this server")) {
                    outputPanel.append("[MSH] 首次启动服务器，建议阅读官方文档: https://docs.papermc.io/paper/next-steps");
                } else if (line.contains("There are") && line.contains("players online")) {
                    try {
                        int playerCount = Integer.parseInt(line.substring(line.indexOf("There are") + 10, line.indexOf(" of a max")));
                        int maxPlayers = Integer.parseInt(line.substring(line.indexOf("of a max of") + 13, line.indexOf(" players online")));
                        String playersList = line.substring(line.indexOf(":") + 2).trim();
                        outputPanel.append("[MSH] 在线玩家: " + playerCount + "/" + maxPlayers + " | 玩家列表: " + (playersList.isEmpty() ? "无" : playersList));
                    } catch (Exception e) {
                        outputPanel.append("[MSH] 在线玩家: " + line);
                    }
                } else if (line.contains("No existing world data, creating new world")) {
                    outputPanel.append("[MSH] 正在创建新的世界数据...");
                } else if (line.contains("Loaded ") && line.contains(" recipes")) {
                    String recipeCount = line.substring(line.indexOf("Loaded") + 7, line.indexOf("recipes")).trim();
                    outputPanel.append("[MSH] 已加载 " + recipeCount + " 个配方");
                } else if (line.contains("Loaded ") && line.contains(" advancements")) {
                    String advancementCount = line.substring(line.indexOf("Loaded") + 7, line.indexOf("advancements")).trim();
                    outputPanel.append("[MSH] 已加载 " + advancementCount + " 个成就");
                } else if (line.contains("Starting minecraft server version")) {
                    String mcVersion = line.substring(line.indexOf("version") + 8).trim();
                    outputPanel.append("[MSH] Minecraft版本: " + mcVersion);
                } else if (line.contains("Server Ping Player Sample Count:")) {
                    String sampleCount = line.substring(line.indexOf(":") + 2).trim();
                    outputPanel.append("[MSH] 服务器Ping样本数: " + sampleCount);
                } else if (line.contains("Using ") && line.contains(" threads for Netty based IO")) {
                    String threadCount = line.substring(line.indexOf("Using") + 6, line.indexOf("threads")).trim();
                    outputPanel.append("[MSH] 使用 " + threadCount + " 个线程处理网络IO");
                } else if (line.contains("Paper is using ") && line.contains(" worker threads")) {
                    outputPanel.append("[MSH] 服务器线程配置已完成");
                } else if (line.contains("Generating keypair")) {
                    outputPanel.append("[MSH] 正在生成密钥对...");
                } else if (line.contains("Selecting spawn point for world")) {
                    outputPanel.append("[MSH] 正在为世界选择出生点...");
                } else if (line.contains("Loading ") && line.contains(" persistent chunks for world")) {
                    outputPanel.append("[MSH] 正在加载世界区块...");
                } else if (line.contains("Prepared spawn area in")) {
                    outputPanel.append("[MSH] 出生点区域准备完成");
                } else if (line.contains("Running delayed init tasks")) {
                    outputPanel.append("[MSH] 正在执行延迟初始化任务...");
                } else if (line.contains("Stopping server")) {
                    outputPanel.append("[MSH] 正在停止服务器...");
                } else if (line.contains("Saving players")) {
                    outputPanel.append("[MSH] 正在保存玩家数据...");
                } else if (line.contains("Saving worlds")) {
                    outputPanel.append("[MSH] 正在保存世界数据...");
                } else if (line.contains("Saving chunks for level")) {
                    outputPanel.append("[MSH] 正在保存区块数据...");
                } else if (line.contains("All chunks are saved")) {
                    outputPanel.append("[MSH] 所有区块已保存");
                } else if (line.contains("Done saving")) {
                    outputPanel.append("[MSH] 数据保存完成");
                } else if (line.contains("Seed:")) {
                    String seed = line.substring(line.indexOf("Seed:")).trim();
                    outputPanel.append("[MSH] " + seed.replace("Seed:", "世界种子:"));
                } else if (line.contains("Checking version, please wait...")) {
                    outputPanel.append("[MSH] 正在检查版本，请稍候...");
                } else if (line.contains("This server is running Paper version")) {
                    outputPanel.append("[MSH] 服务器正在运行 Paper 版本: " + line.substring(line.indexOf("Paper version") + 14).trim());
                } else if (line.contains("You are running the latest version")) {
                    outputPanel.append("[MSH] 您正在运行最新版本");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
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
                        BufferedReader reader = new BufferedReader(new FileReader(eulaFile));
                        String content = reader.lines().collect(java.util.stream.Collectors.joining(System.lineSeparator()));
                        reader.close();
                        content = content.replace("eula=false", "eula=true");
                        if (!content.contains("eula=true")) {
                            content += System.lineSeparator() + "eula=true";
                        }
                        BufferedWriter writer = new BufferedWriter(new FileWriter(eulaFile));
                        writer.write(content);
                        writer.close();
                        outputPanel.append("[MSH] 已自动修改eula.txt，同意EULA");
                        jarRunner.restart();
                    } catch (IOException e) {
                        e.printStackTrace();
                        outputPanel.append("[MSH] 修改eula.txt失败: " + e.getMessage());
                    }
                }
            });
        }
    }
}
