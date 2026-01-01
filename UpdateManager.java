import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateManager {
    private static final String UPDATE_JSON_URL = "https://znpwlk.github.io/Minecraft-Server-Hub-API/version.json";
    private static final String UPDATE_DIR = "MSH";
    private final Main main;
    private final PreferenceManager preferenceManager;

    public UpdateManager(Main main) {
        this.main = main;
        this.preferenceManager = new PreferenceManager();
    }

    public void checkForUpdates() {
        checkForUpdates(true);
    }
    
    public void checkForUpdates(boolean showDialog) {
        if (!preferenceManager.shouldCheckForUpdates()) {
            Logger.info("Update checking is disabled in preferences", "UpdateManager");
            return;
        }

        Logger.info("Starting update check, showDialog=" + showDialog, "UpdateManager");
        
        if (showDialog) {
            showUpdateCheckDialog();
        } else {
            performSilentUpdateCheck();
        }
    }
    
    private void showUpdateCheckDialog() {
        if (main.getFrame() == null) {
            Logger.warn("Main frame is null, cannot show update check dialog", "UpdateManager");
            return;
        }
        
        JDialog loadingDialog = new JDialog(main.getFrame(), "检查更新", false);
        loadingDialog.setSize(220, 90);
        loadingDialog.setLocationRelativeTo(main.getFrame());
        loadingDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel loadingLabel = new JLabel("正在检查更新...", SwingConstants.CENTER);
        panel.add(loadingLabel, BorderLayout.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(150, 20));
        panel.add(progressBar, BorderLayout.SOUTH);

        loadingDialog.add(panel);
        loadingDialog.setVisible(true);

        final JDialog finalLoadingDialog = loadingDialog;

        Thread updateThread = new Thread(() -> {
            performUpdateCheck(finalLoadingDialog);
        });
        updateThread.setDaemon(true);
        updateThread.start();
    }
    
    private void performSilentUpdateCheck() {
        Logger.info("Performing silent update check on startup", "UpdateManager");
        Thread updateThread = new Thread(() -> {
            performUpdateCheck(null);
        });
        updateThread.setDaemon(true);
        updateThread.start();
    }
    
    private void performUpdateCheck(JDialog loadingDialog) {
        JFrame frame = main.getFrame();
        boolean hasFrame = frame != null;
        
        try {
            Logger.info("Checking for updates...", "UpdateManager");
            URI uri = URI.create(UPDATE_JSON_URL);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Logger.warn("Update check failed, HTTP code: " + responseCode, "UpdateManager");
                return;
            }

            StringBuilder jsonResponse = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonResponse.append(line);
                }
            }

            conn.disconnect();

            String jsonContent = jsonResponse.toString();
            Logger.info("JSON response content: " + jsonContent, "UpdateManager");

            String version = extractJsonString(jsonContent, "version");
            String updateDate = extractJsonString(jsonContent, "updateDate");
            String downloadUrl = extractJsonString(jsonContent, "downloadUrl");
            String sha256 = extractJsonString(jsonContent, "sha256");
            boolean forceUpdate = extractJsonBoolean(jsonContent, "forceUpdate");
            String updateContent = extractJsonArrayToString(jsonContent, "updateContent");

            if (version == null || version.isEmpty() || downloadUrl == null || downloadUrl.isEmpty() || sha256 == null || sha256.isEmpty()) {
                Logger.error("Invalid update JSON format - version: " + version + ", downloadUrl: " + downloadUrl + ", sha256: " + sha256, "UpdateManager");
                return;
            }

            Logger.info("Current version: " + Main.VERSION + ", Remote version: " + version, "UpdateManager");
            boolean hasUpdate = !version.equals(Main.VERSION);
            Logger.info("Has newer version: " + hasUpdate, "UpdateManager");

            if (hasUpdate) {
                Logger.info("New version available: " + version, "UpdateManager");
                preferenceManager.setLastUpdateCheck(System.currentTimeMillis());

                if (loadingDialog != null) {
                    SwingUtilities.invokeLater(() -> {
                        loadingDialog.dispose();
                        if (hasFrame) {
                            showUpdateDialog(version, updateContent, updateDate, downloadUrl, sha256, forceUpdate);
                        }
                    });
                } else {
                    if (preferenceManager.shouldShowUpdateDialog() || forceUpdate) {
                        Logger.info("Showing update dialog for version " + version, "UpdateManager");
                        SwingUtilities.invokeLater(() -> {
                            if (hasFrame) {
                                showUpdateDialog(version, updateContent, updateDate, downloadUrl, sha256, forceUpdate);
                            }
                        });
                    } else {
                        Logger.info("User chose not to show update dialog, skipping notification", "UpdateManager");
                    }
                }
            } else {
                Logger.info("Current version is up to date: " + Main.VERSION, "UpdateManager");
                preferenceManager.setLastUpdateCheck(System.currentTimeMillis());
                
                if (loadingDialog != null) {
                    SwingUtilities.invokeLater(() -> {
                        loadingDialog.dispose();
                        if (hasFrame) {
                            JOptionPane.showMessageDialog(frame,
                                    "当前已是最新版本 " + Main.VERSION,
                                    "更新检查", JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                }
            }

        } catch (Exception e) {
            Logger.error("Update check failed: " + e.getMessage(), "UpdateManager");
            if (loadingDialog != null) {
                SwingUtilities.invokeLater(() -> {
                    loadingDialog.dispose();
                    if (hasFrame) {
                        JOptionPane.showMessageDialog(frame,
                                "检查更新失败: " + e.getMessage(),
                                "更新检查", JOptionPane.WARNING_MESSAGE);
                    }
                });
            }
        }
    }

    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyStart = json.indexOf(searchKey);
        if (keyStart == -1) {
            Logger.warn("Key not found in JSON: " + key, "UpdateManager");
            return null;
        }

        int colonPos = json.indexOf(":", keyStart);
        if (colonPos == -1) {
            Logger.warn("Colon not found after key: " + key, "UpdateManager");
            return null;
        }

        int valueStart = json.indexOf("\"", colonPos);
        if (valueStart == -1) {
            Logger.warn("Value start quote not found for key: " + key, "UpdateManager");
            return null;
        }

        valueStart++;
        int valueEnd = valueStart;
        boolean escaped = false;

        while (valueEnd < json.length()) {
            char c = json.charAt(valueEnd);
            if (c == '\\' && !escaped) {
                escaped = true;
                valueEnd++;
                continue;
            }
            if (c == '"' && !escaped) {
                break;
            }
            escaped = false;
            valueEnd++;
        }

        if (valueEnd >= json.length()) {
            Logger.warn("Value end quote not found for key: " + key, "UpdateManager");
            return null;
        }

        String value = json.substring(valueStart, valueEnd);
        return value;
    }

    private String extractJsonArrayToString(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyStart = json.indexOf(searchKey);
        if (keyStart == -1) {
            Logger.warn("Array key not found in JSON: " + key, "UpdateManager");
            return null;
        }

        int colonPos = json.indexOf(":", keyStart);
        if (colonPos == -1) {
            Logger.warn("Colon not found after array key: " + key, "UpdateManager");
            return null;
        }

        int arrayStart = json.indexOf("[", colonPos);
        if (arrayStart == -1) {
            Logger.warn("Array start bracket not found for key: " + key, "UpdateManager");
            return null;
        }

        int arrayEnd = arrayStart;
        int bracketCount = 1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = arrayStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && !escaped) {
                inString = !inString;
            } else if (c == '\\' && !escaped) {
                escaped = true;
                continue;
            } else if (!inString) {
                if (c == '[') {
                    bracketCount++;
                } else if (c == ']') {
                    bracketCount--;
                    if (bracketCount == 0) {
                        arrayEnd = i;
                        break;
                    }
                }
            }
            escaped = false;
        }

        if (bracketCount != 0) {
            Logger.warn("Array not properly closed for key: " + key, "UpdateManager");
            return null;
        }

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        StringBuilder result = new StringBuilder();
        
        Pattern stringPattern = Pattern.compile("\"([^\"]*(?:\\\\.[^\"]*)*)\"");
        Matcher matcher = stringPattern.matcher(arrayContent);
        
        boolean first = true;
        while (matcher.find()) {
            if (!first) {
                result.append(", ");
            }
            result.append(matcher.group(1));
            first = false;
        }

        return result.length() > 0 ? result.toString() : null;
    }

    private boolean extractJsonBoolean(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
        Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(json);
        if (matcher.find()) {
            return Boolean.parseBoolean(matcher.group(1));
        }
        return false;
    }

    private void showUpdateDialog(String version, String updateContent, String updateDate,
                                   String downloadUrl, String sha256, boolean forceUpdate) {
        JFrame frame = main.getFrame();
        if (frame == null) {
            Logger.error("Main frame is null, cannot show update dialog", "UpdateManager");
            return;
        }
        
        String sanitizedVersion = sanitizeVersion(version);
        String safeVersion = escapeHtml(sanitizedVersion);
        
        List<JarRunner> runners = main.getJarRunners();
        boolean hasRunningServer = runners != null && !runners.isEmpty() &&
                runners.stream().anyMatch(runner -> {
                    JarRunner.Status status = runner.getStatus();
                    return status == JarRunner.Status.RUNNING || status == JarRunner.Status.STARTING;
                });

        if (hasRunningServer && forceUpdate) {
            JOptionPane.showMessageDialog(frame,
                    "检测到新版本 " + safeVersion + "，但服务器正在运行中。\n请先停止服务器后再进行更新。",
                    "无法更新", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(frame, "发现新版本 - " + safeVersion, true);
        dialog.setSize(420, 380);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout());

        JPanel infoPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("发现新版本");
        titleLabel.setFont(new Font(null, Font.BOLD, 16));
        infoPanel.add(titleLabel);

        JPanel versionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        versionPanel.add(new JLabel("当前版本: " + Main.VERSION));
        versionPanel.add(Box.createHorizontalStrut(15));
        versionPanel.add(new JLabel("→"));
        versionPanel.add(Box.createHorizontalStrut(5));
        versionPanel.add(new JLabel("新版本: " + version));
        infoPanel.add(versionPanel);

        infoPanel.add(new JLabel("更新日期: " + (updateDate != null ? updateDate : "未知")));

        JLabel contentLabel = new JLabel("更新内容:");
        contentLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        infoPanel.add(contentLabel);

        String[] updates;
        if (updateContent != null && !updateContent.isEmpty()) {
            updates = updateContent.split(", ");
        } else {
            updates = new String[]{"暂无更新内容"};
        }
        JList<String> updateList = new JList<>(updates);
        updateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        updateList.setBackground(Color.WHITE);
        updateList.setEnabled(false);
        JScrollPane listScrollPane = new JScrollPane(updateList);
        listScrollPane.setPreferredSize(new Dimension(0, 100));
        infoPanel.add(listScrollPane);

        if (hasRunningServer) {
            JLabel warnLabel = new JLabel("注意: 服务器正在运行中");
            warnLabel.setForeground(Color.ORANGE);
            warnLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
            infoPanel.add(warnLabel);
        }

        if (forceUpdate) {
            JLabel forceLabel = new JLabel("⚠️ 此为强制更新，必须更新后才能继续使用");
            forceLabel.setForeground(Color.RED);
            forceLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
            infoPanel.add(forceLabel);
        }

        JCheckBox skipCheckBox = new JCheckBox("永久不再启动时提示更新");
        if (forceUpdate) {
            skipCheckBox.setEnabled(false);
            skipCheckBox.setText("永久不再启动时提示更新 (强制更新无法跳过)");
        }
        infoPanel.add(skipCheckBox);

        dialog.add(infoPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        if (hasRunningServer) {
            JButton confirmBtn = new JButton("是 (将停止服务器)");
            confirmBtn.addActionListener(e -> {
                dialog.dispose();
                stopAllServersAndUpdate(downloadUrl, sha256, version);
            });
            JButton cancelBtn;
            if (forceUpdate) {
                cancelBtn = new JButton("退出程序");
                cancelBtn.addActionListener(e -> {
                    dialog.dispose();
                    Logger.info("User refused forced update, exiting application", "UpdateManager");
                    System.exit(0);
                });
            } else {
                cancelBtn = new JButton("否，稍后更新");
                cancelBtn.addActionListener(e -> {
                    if (skipCheckBox.isSelected()) {
                        Logger.info("User selected to never show update dialog on startup", "UpdateManager");
                        preferenceManager.setShowUpdateDialog(false);
                    }
                    dialog.dispose();
                });
            }
            buttonPanel.add(confirmBtn);
            buttonPanel.add(cancelBtn);
        } else {
            JButton confirmBtn = new JButton("立即更新");
            confirmBtn.addActionListener(e -> {
                dialog.dispose();
                downloadAndUpdate(downloadUrl, sha256, sanitizedVersion);
            });
            JButton cancelBtn;
            if (forceUpdate) {
                cancelBtn = new JButton("退出程序");
                cancelBtn.addActionListener(e -> {
                    dialog.dispose();
                    Logger.info("User refused forced update, exiting application", "UpdateManager");
                    System.exit(0);
                });
            } else {
                cancelBtn = new JButton("稍后提醒我");
                cancelBtn.addActionListener(e -> {
                    if (skipCheckBox.isSelected()) {
                        Logger.info("User selected to never show update dialog on startup", "UpdateManager");
                        preferenceManager.setShowUpdateDialog(false);
                    }
                    dialog.dispose();
                });
            }
            buttonPanel.add(confirmBtn);
            buttonPanel.add(cancelBtn);
        }

        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void stopAllServersAndUpdate(String downloadUrl, String sha256, String version) {
        Thread stopThread = new Thread(() -> {
            Logger.info("Stopping all servers before update", "UpdateManager");

            List<JarRunner> runners = main.getJarRunners();
            if (runners != null) {
                runners.forEach(runner -> {
                    if (runner != null) {
                        JarRunner.Status status = runner.getStatus();
                        if (status == JarRunner.Status.RUNNING || status == JarRunner.Status.STARTING) {
                            String displayName = runner.getDisplayName();
                            Logger.info("Stopping server: " + (displayName != null ? displayName : "unknown"), "UpdateManager");
                            runner.stop();
                        }
                    }
                });
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            boolean[] allStopped = {false};
            int waitCount = 0;
            while (!allStopped[0] && waitCount < 30) {
                List<JarRunner> currentRunners = main.getJarRunners();
                allStopped[0] = currentRunners == null || currentRunners.stream().noneMatch(runner -> {
                    if (runner == null) return false;
                    JarRunner.Status status = runner.getStatus();
                    return status == JarRunner.Status.RUNNING || status == JarRunner.Status.STARTING;
                });
                if (!allStopped[0]) {
                    try {
                        Thread.sleep(1000);
                        waitCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            SwingUtilities.invokeLater(() -> {
                if (allStopped[0]) {
                    downloadAndUpdate(downloadUrl, sha256, version);
                } else {
                    JFrame frame = main.getFrame();
                    if (frame != null) {
                        JOptionPane.showMessageDialog(frame,
                                "等待服务器停止超时，无法完成更新。",
                                "更新失败", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
        });
        stopThread.setDaemon(true);
        stopThread.start();
    }

    private void downloadAndUpdate(String downloadUrl, String sha256, String version) {
        Logger.info("Starting download for version " + version, "UpdateManager");

        String currentJarPath = getCurrentJarPath();
        boolean isDevEnvironment = currentJarPath != null && currentJarPath.endsWith(".java");

        File currentJarFile = null;
        final File[] targetDir = new File[1];
        if (!isDevEnvironment && currentJarPath != null) {
            currentJarFile = new File(currentJarPath);
            targetDir[0] = currentJarFile.getParentFile();
        }

        JFrame frame = main.getFrame();
        if (frame == null) {
            Logger.error("Main frame is null, cannot show download progress dialog", "UpdateManager");
            JOptionPane.showMessageDialog(null, "更新失败：无法显示下载窗口", "更新错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!isDevEnvironment && (targetDir[0] == null || !targetDir[0].exists())) {
            Logger.error("Target directory does not exist: " + (targetDir[0] != null ? targetDir[0].getAbsolutePath() : "null"), "UpdateManager");
            JOptionPane.showMessageDialog(frame, "更新失败：无法找到目标目录", "更新错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog progressDialog = new JDialog(frame, "下载更新中", false);
        progressDialog.setSize(350, 120);
        progressDialog.setLocationRelativeTo(frame);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel statusLabel = new JLabel("正在连接服务器...");
        panel.add(statusLabel, BorderLayout.NORTH);

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(280, 20));
        panel.add(progressBar, BorderLayout.CENTER);

        JLabel detailLabel = new JLabel("");
        panel.add(detailLabel, BorderLayout.SOUTH);

        progressDialog.add(panel);
        progressDialog.setVisible(true);

        final JLabel finalStatusLabel = statusLabel;
        final JProgressBar finalProgressBar = progressBar;
        final JLabel finalDetailLabel = detailLabel;
        final JDialog finalProgressDialog = progressDialog;

        Thread downloadThread = new Thread(() -> {
            File tempFile = null;
            boolean downloadSuccess = false;
            final AtomicLong downloadedBytes = new AtomicLong(0);
            long startTime = System.currentTimeMillis();
            long lastUpdateTime = startTime;
            long lastDownloadedBytes = 0;
            final JFrame dialogFrame = frame;

            try {
                File downloadDir = isDevEnvironment ? new File(UPDATE_DIR) : targetDir[0];
                if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                    Logger.error("Failed to create download directory: " + downloadDir.getAbsolutePath(), "UpdateManager");
                    throw new IOException("Failed to create download directory");
                }

                String fileName;
                int lastSlash = downloadUrl.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < downloadUrl.length() - 1) {
                    fileName = downloadUrl.substring(lastSlash + 1);
                } else {
                    fileName = "Minecraft-Server-Hub-" + version + ".jar";
                }

                fileName = sanitizeFileName(fileName);

                if (!fileName.toLowerCase().endsWith(".jar")) {
                    fileName = fileName + ".jar";
                }
                Logger.info("Download file name: " + fileName, "UpdateManager");

                tempFile = new File(downloadDir, "temp_update_" + fileName);
                Logger.info("Temp file path: " + tempFile.getAbsolutePath(), "UpdateManager");

                URI uri = URI.create(downloadUrl);
                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                    throw new SecurityException("Invalid URL scheme: " + scheme);
                }
                Logger.info("Connecting to download server: " + downloadUrl, "UpdateManager");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);

                int responseCode = conn.getResponseCode();
                Logger.info("HTTP response code: " + responseCode, "UpdateManager");
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Download failed, HTTP code: " + responseCode);
                }

                long totalSize = conn.getContentLength();
                Logger.info("Total file size: " + (totalSize > 0 ? (totalSize / 1024) + " KB" : "unknown"), "UpdateManager");

                SwingUtilities.invokeLater(() -> {
                    finalStatusLabel.setText("正在下载: " + version);
                    finalProgressBar.setIndeterminate(totalSize <= 0);
                });

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tempFile)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long bytesLogged = 0;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        downloadedBytes.addAndGet(bytesRead);

                        long currentTime = System.currentTimeMillis();
                        if (totalSize > 0) {
                            int percent = (int) ((downloadedBytes.get() * 100) / totalSize);
                            long cur = downloadedBytes.get();
                            final long finalTotal = totalSize;

                            if (currentTime - lastUpdateTime >= 1000) {
                                long speedBytes = cur - lastDownloadedBytes;
                                double speedKB = speedBytes / 1024.0;
                                String speedText = String.format("%.1f KB/s", speedKB);
                                
                                lastUpdateTime = currentTime;
                                lastDownloadedBytes = cur;

                                if (percent % 10 == 0 && bytesLogged != percent) {
                                    Logger.info("Download progress: " + percent + "% (" + formatSize(cur) + " / " + formatSize(finalTotal) + "), Speed: " + speedText, "UpdateManager");
                                    bytesLogged = percent;
                                }

                                SwingUtilities.invokeLater(() -> {
                                    finalProgressBar.setValue(percent);
                                    finalProgressBar.setString(percent + "%");
                                    finalDetailLabel.setText(formatSize(cur) + " / " + formatSize(finalTotal) + " (" + speedText + ")");
                                });
                            }
                        } else {
                            if (currentTime - lastUpdateTime >= 1000) {
                                long speedBytes = downloadedBytes.get() - lastDownloadedBytes;
                                double speedKB = speedBytes / 1024.0;
                                String speedText = String.format("%.1f KB/s", speedKB);
                                
                                lastUpdateTime = currentTime;
                                lastDownloadedBytes = downloadedBytes.get();

                                if (downloadedBytes.get() % (1024 * 1024) < 8192 && bytesLogged != downloadedBytes.get()) {
                                    Logger.info("Downloaded: " + formatSize(downloadedBytes.get()) + ", Speed: " + speedText, "UpdateManager");
                                    bytesLogged = downloadedBytes.get();
                                }

                                SwingUtilities.invokeLater(() -> {
                                    finalDetailLabel.setText(formatSize(downloadedBytes.get()) + " (" + speedText + ")");
                                });
                            }
                        }
                    }

                    long totalTime = System.currentTimeMillis() - startTime;
                    double avgSpeed = totalTime > 0 ? (downloadedBytes.get() / 1024.0) / (totalTime / 1000.0) : 0;
                    Logger.info("Download completed: " + formatSize(downloadedBytes.get()) + ", Total time: " + (totalTime / 1000) + "s, Avg speed: " + String.format("%.1f KB/s", avgSpeed), "UpdateManager");
                }

                conn.disconnect();
                Logger.info("Connection closed", "UpdateManager");

                SwingUtilities.invokeLater(() -> {
                    finalStatusLabel.setText("正在验证文件完整性...");
                    finalProgressBar.setIndeterminate(true);
                });

                Logger.info("Download completed, verifying SHA256...", "UpdateManager");
                if (!verifySHA256(tempFile, sha256)) {
                    tempFile.delete();
                    throw new IOException("SHA256 verification failed");
                }
                Logger.info("SHA256 verification passed", "UpdateManager");
                downloadSuccess = true;

                SwingUtilities.invokeLater(() -> {
                    finalStatusLabel.setText("正在移动文件...");
                });

                Logger.info("Download completed, verifying SHA256...", "UpdateManager");
                if (!verifySHA256(tempFile, sha256)) {
                    tempFile.delete();
                    throw new IOException("SHA256 verification failed");
                }
                Logger.info("SHA256 verification passed", "UpdateManager");
                downloadSuccess = true;

                File finalFile = new File(downloadDir, fileName);
                if (finalFile.exists()) {
                    finalFile.delete();
                }
                Files.move(tempFile.toPath(), finalFile.toPath());

                Logger.info("Update completed, file moved to: " + finalFile.getAbsolutePath(), "UpdateManager");

                if (isDevEnvironment) {
                    Logger.info("Development environment detected, keeping old version files", "UpdateManager");
                    SwingUtilities.invokeLater(() -> {
                        finalProgressDialog.dispose();
                        JOptionPane.showMessageDialog(dialogFrame,
                                "更新完成！\n\n开发环境下运行，请重新编译运行项目。\n\n文件位置: " + finalFile.getAbsolutePath(),
                                "更新完成", JOptionPane.INFORMATION_MESSAGE);
                    });
                } else {
                    String oldJarPath = currentJarPath;
                    SwingUtilities.invokeLater(() -> {
                        finalProgressDialog.dispose();
                        askUserForRestart(finalFile, version, oldJarPath);
                    });
                }

            } catch (Exception e) {
                Logger.error("Update failed: " + e.getMessage(), "UpdateManager");
                SwingUtilities.invokeLater(() -> {
                    finalProgressDialog.dispose();
                    JOptionPane.showMessageDialog(dialogFrame,
                            "更新失败: " + e.getMessage(),
                            "更新错误", JOptionPane.ERROR_MESSAGE);
                });

                if (!downloadSuccess && tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        });
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private String getCurrentJarPath() {
        try {
            java.net.URL codeSourceUrl = Main.class.getProtectionDomain().getCodeSource().getLocation();
            java.net.URI uri = codeSourceUrl.toURI();
            return new File(uri).getAbsolutePath();
        } catch (Exception e) {
            Logger.error("Failed to get current JAR path: " + e.getMessage(), "UpdateManager");
            return null;
        }
    }

    private boolean verifySHA256(File file, String expectedHash) {
        if (file == null || !file.exists() || !file.isFile()) {
            Logger.error("SHA256 verification failed: file does not exist or is not a file", "UpdateManager");
            return false;
        }

        if (expectedHash == null || expectedHash.trim().isEmpty()) {
            Logger.error("SHA256 verification failed: expected hash is null or empty", "UpdateManager");
            return false;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }

            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            String actualHash = hexString.toString().toLowerCase();
            String expectedLower = expectedHash.toLowerCase().trim();

            Logger.info("SHA256 - Expected: " + expectedLower + ", Actual: " + actualHash, "UpdateManager");

            boolean verified = actualHash.equals(expectedLower);
            if (!verified) {
                Logger.error("SHA256 verification failed: hash mismatch", "UpdateManager");
            }
            return verified;
        } catch (NoSuchAlgorithmException e) {
            Logger.error("SHA256 verification failed: SHA-256 algorithm not available - " + e.getMessage(), "UpdateManager");
            return false;
        } catch (FileNotFoundException e) {
            Logger.error("SHA256 verification failed: file not found - " + e.getMessage(), "UpdateManager");
            return false;
        } catch (IOException e) {
            Logger.error("SHA256 verification failed: I/O error - " + e.getMessage(), "UpdateManager");
            return false;
        } catch (Exception e) {
            Logger.error("SHA256 verification failed: unexpected error - " + e.getMessage(), "UpdateManager");
            return false;
        }
    }

    private void askUserForRestart(File newJarFile, String version, String oldJarPath) {
        JFrame frame = main.getFrame();
        if (frame == null) {
            Logger.error("Main frame is null, cannot show restart dialog", "UpdateManager");
            return;
        }
        
        boolean hasOldJar = oldJarPath != null && !oldJarPath.endsWith(".java") && !oldJarPath.equals(newJarFile.getAbsolutePath());
        String message;
        if (hasOldJar) {
            message = "更新完成！\n\n版本: " + version + "\n新文件: " + newJarFile.getName() + "\n\n是否立即重启并删除旧版本？";
        } else {
            message = "更新完成！\n\n版本: " + version + "\n文件: " + newJarFile.getName() + "\n\n是否立即重启应用？";
        }

        int result = JOptionPane.showConfirmDialog(frame,
                message,
                "更新完成", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            openNewVersion(newJarFile, version, oldJarPath);
        } else {
            if (hasOldJar) {
                JOptionPane.showMessageDialog(frame,
                        "新版本文件已准备好，旧版本文件位于:\n" + escapeHtml(oldJarPath),
                        "更新完成", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame,
                        "新版本文件已准备好，您可以稍后手动启动。",
                        "更新完成", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private String escapeHtml(String text) {
         if (text == null) return "";
         return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
     }
 
     private String sanitizeVersion(String version) {
         if (version == null) return "unknown";
         return version.replaceAll("[^a-zA-Z0-9._-]", "_");
     }
 
     private String sanitizeFileName(String fileName) {
         if (fileName == null || fileName.isEmpty()) {
             return "Minecraft-Server-Hub-update.jar";
         }
         
         fileName = fileName.replace("\\", "_")
                           .replace("/", "_")
                           .replace("..", "_")
                           .replace("?", "_")
                           .replace("#", "_")
                           .replace(":", "_")
                           .replace("*", "_")
                           .replace("\"", "_")
                           .replace("<", "_")
                           .replace(">", "_")
                           .replace("|", "_");
         
         fileName = fileName.replaceAll("[\\x00-\\x1F\\x7F]", "_");
         
         if (fileName.isEmpty() || fileName.equals("_")) {
             return "Minecraft-Server-Hub-update.jar";
         }
         
         return fileName;
     }

    private void openNewVersion(File newJarFile, String version, String oldJarPath) {
        JFrame frame = main.getFrame();
        
        try {
            Logger.info("=== Open New Version ===", "UpdateManager");
            Logger.info("New JAR file: " + newJarFile.getAbsolutePath(), "UpdateManager");
            Logger.info("Old JAR file: " + oldJarPath, "UpdateManager");

            String canonicalNewPath = newJarFile.getCanonicalPath();
            File newJarDir = newJarFile.getParentFile();
            
            if (!canonicalNewPath.toLowerCase().endsWith(".jar")) {
                throw new SecurityException("Invalid file type for update");
            }
            
            if (newJarDir != null) {
                String canonicalDirPath = newJarDir.getCanonicalPath();
                if (!canonicalNewPath.toLowerCase().startsWith(canonicalDirPath.toLowerCase() + File.separator)) {
                    throw new SecurityException("Path traversal attempt detected");
                }
            }

            if (oldJarPath != null && !oldJarPath.endsWith(".java")) {
                preferenceManager.setPendingDeleteOldVersion(oldJarPath);
            }
            
            String javaHome = System.getProperty("java.home");
            File javaBinDir = new File(javaHome, "bin");
            File javaFile = new File(javaBinDir, "java");
            
            String javaPath;
            String canonicalJavaPath = null;
            try {
                if (javaFile.exists()) {
                    canonicalJavaPath = javaFile.getCanonicalPath();
                    javaPath = canonicalJavaPath;
                } else {
                    javaPath = "java";
                }
            } catch (IOException e) {
                Logger.warn("Could not get canonical path for java: " + e.getMessage(), "UpdateManager");
                javaPath = "java";
            }
            
            File canonicalNewJarFile = newJarFile.getCanonicalFile();
            File canonicalDir = canonicalNewJarFile.getCanonicalFile().getParentFile();
            
            if (canonicalJavaPath != null) {
                File canonicalJavaDir = new File(canonicalJavaPath).getCanonicalFile().getParentFile();
                String javaDirPath = canonicalJavaDir != null ? canonicalJavaDir.getPath() : "";
                if (javaDirPath.isEmpty() || !canonicalJavaPath.toLowerCase().startsWith(javaDirPath.toLowerCase() + File.separator)) {
                    throw new SecurityException("Invalid java executable path");
                }
            }

            Logger.info("Java command: " + javaPath, "UpdateManager");
            Logger.info("Starting new process with command: " + javaPath + " -jar " + canonicalNewJarFile.getAbsolutePath(), "UpdateManager");

            ProcessBuilder pb = new ProcessBuilder(javaPath, "-jar", canonicalNewJarFile.getAbsolutePath());
            pb.directory(canonicalDir);
            pb.start();

            final JFrame finalFrame = frame;
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(finalFrame,
                        "新版本正在启动...",
                        "启动中", JOptionPane.INFORMATION_MESSAGE);
            });

            Timer exitTimer = new Timer(3000, e -> System.exit(0));
            exitTimer.setRepeats(false);
            exitTimer.start();

        } catch (SecurityException e) {
            Logger.error("Security violation in openNewVersion: " + e.getMessage(), "UpdateManager");
            final JFrame finalFrame = frame;
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(finalFrame,
                        "更新失败：检测到不安全文件路径",
                        "安全错误", JOptionPane.ERROR_MESSAGE);
            });
        } catch (Exception e) {
            Logger.error("Failed to launch new version: " + e.getMessage(), "UpdateManager");
            final JFrame finalFrame = frame;
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(finalFrame,
                        "新版本下载成功，但启动失败。请手动运行: " + escapeHtml(newJarFile.getAbsolutePath()),
                        "启动失败", JOptionPane.WARNING_MESSAGE);
            });
        }
    }
}
