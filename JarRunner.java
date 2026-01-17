import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class JarRunner {
    public interface GameRuleCallback {
        void onGameRuleValue(String ruleName, String value);
    }
    
    public interface BackupCallback {
        void onBackupComplete(String zipPath, boolean success);
    }
    
    public enum Status {
        STOPPED, RUNNING, STARTING, STOPPING
    }
    private String jarPath;
    private String customName;
    private ColorOutputPanel outputPanel;
    private Process process;
    private Thread stdoutThread;
    private Thread stderrThread;
    private Thread processMonitorThread;
    private volatile Status status;
    private OutputStream processInput;
    private PrintWriter commandWriter;
    private GameRuleCallback gameRuleCallback;
    
    private boolean autoRestartEnabled;
    private boolean forceKeepAlive;
    private int maxHourlyAttempts;
    private int restartInterval;
    private AtomicInteger currentHourlyAttempts;
    private volatile long lastRestartTimestamp;
    private Thread hourlyResetThread;
    private volatile boolean isNormalStop = false;
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private static final int MAX_HISTORY_SIZE = 50;
    
    private boolean backupEnabled;
    private int backupIntervalMinutes;
    private int maxBackupCount;
    private int autoDeleteDays;
    private Thread backupTimerThread;
    private volatile boolean stopBackupTimer;
    private BackupCallback backupCallback;
    private volatile long lastBackupTime;
    private volatile boolean isBackingUp = false;
    private boolean useNoGui;
    private String serverVersion;
    private String lastError;
    private volatile long lastAccessTime;
    private Thread lockMonitorThread;
    private volatile boolean stopLockMonitor;
    private static final long LOCK_DETECTION_INTERVAL = 30000;
    private static final long IDLE_THRESHOLD = 600000;
    private volatile boolean lockDialogShown = false;
    
    public JarRunner(String jarPath, ColorOutputPanel outputPanel) {
        this.jarPath = jarPath;
        this.customName = null;
        this.outputPanel = outputPanel;
        this.status = Status.STOPPED;
        this.autoRestartEnabled = false;
        this.forceKeepAlive = false;
        this.maxHourlyAttempts = 3;
        this.restartInterval = 10;
        this.currentHourlyAttempts = new AtomicInteger(0);
        this.lastRestartTimestamp = 0;
        this.backupEnabled = false;
        this.backupIntervalMinutes = 60;
        this.maxBackupCount = 10;
        this.autoDeleteDays = 30;
        this.lastBackupTime = 0;
        this.stopBackupTimer = false;
        this.useNoGui = true;
        this.serverVersion = null;
        this.lastError = null;
        this.lastAccessTime = System.currentTimeMillis();
        startLockMonitorThread();
        startHourlyResetThread();
    }

    private void startHourlyResetThread() {
        hourlyResetThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000);
                    long now = System.currentTimeMillis();
                    if (lastRestartTimestamp > 0 && now - lastRestartTimestamp >= 3600000) {
                        int oldValue = currentHourlyAttempts.getAndSet(0);
                        if (oldValue > 0) {
                            Logger.info("Hourly restart counter has been reset", "JarRunner");
                            outputPanel.append("[MSH] Hourly restart counter has been reset\n");
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        hourlyResetThread.setDaemon(true);
        hourlyResetThread.start();
    }

    private void startLockMonitorThread() {
        stopLockMonitor = false;
        lockMonitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !stopLockMonitor) {
                try {
                    Thread.sleep(LOCK_DETECTION_INTERVAL);
                    if (stopLockMonitor) break;
                    checkFileLockStatus();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        lockMonitorThread.setDaemon(true);
        lockMonitorThread.setName("lock-monitor-" + jarPath);
        lockMonitorThread.start();
    }

    private void checkFileLockStatus() {
        if (status != Status.RUNNING && status != Status.STARTING) {
            lastAccessTime = System.currentTimeMillis();
            return;
        }

        File jarFile = new File(jarPath);
        if (!jarFile.exists()) {
            return;
        }

        long idleTime = System.currentTimeMillis() - lastAccessTime;
        if (idleTime < IDLE_THRESHOLD) {
            return;
        }

        if (lockDialogShown) {
            return;
        }

        if (!isFileLockedByProcess()) {
            lastAccessTime = System.currentTimeMillis();
            lockDialogShown = false;
            return;
        }

        lockDialogShown = true;
        Logger.info("Detected idle file lock, showing dialog: " + jarPath, "JarRunner");

        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(outputPanel);
            if (frame == null) {
                lockDialogShown = false;
                return;
            }

            String serverName = getDisplayName();
            int option = JOptionPane.showOptionDialog(
                frame,
                "服务器 " + serverName + " 文件已被锁定超过 " + (idleTime / 60000) + " 分钟未活动。\n是否尝试自动解除锁定并重启服务器？",
                "文件锁定提醒",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"自动处理（解除锁定并重启）", "暂不处理"},
                "暂不处理"
            );

            if (option == JOptionPane.YES_OPTION) {
                Logger.info("User confirmed auto-release lock for: " + jarPath, "JarRunner");
                outputPanel.append("[MSH] 用户选择自动解除文件锁定...\n");
                forceUnlockAndRestart();
            } else {
                Logger.info("User deferred lock release for: " + jarPath, "JarRunner");
                outputPanel.append("[MSH] 用户选择暂不处理文件锁定\n");
            }
            lockDialogShown = false;
            lastAccessTime = System.currentTimeMillis();
        });
    }

    private boolean isFileLockedByProcess() {
        File jarFile = new File(jarPath);
        if (!jarFile.exists()) {
            return false;
        }

        try (FileInputStream fis = new FileInputStream(jarFile)) {
            return false;
        } catch (IOException e) {
            return isFileLocked(e);
        }
    }

    public void updateLastAccessTime() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    private void checkAndResetHourlyCounter() {
        long now = System.currentTimeMillis();
        if (lastRestartTimestamp > 0 && now - lastRestartTimestamp >= 3600000) {
            currentHourlyAttempts.set(0);
        }
    }

    public void cleanup() {
        if (hourlyResetThread != null) {
            hourlyResetThread.interrupt();
        }
        stopLockMonitorThread();
        stopBackupTimerThread();
    }

    private void stopLockMonitorThread() {
        stopLockMonitor = true;
        if (lockMonitorThread != null && lockMonitorThread.isAlive()) {
            lockMonitorThread.interrupt();
            try {
                lockMonitorThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public boolean isBackupEnabled() {
        return backupEnabled;
    }
    
    public void setBackupEnabled(boolean enabled) {
        this.backupEnabled = enabled;
        if (enabled) {
            startBackupTimerThread();
        } else {
            stopBackupTimerThread();
        }
    }
    
    public int getBackupIntervalMinutes() {
        return backupIntervalMinutes;
    }
    
    public void setBackupIntervalMinutes(int minutes) {
        this.backupIntervalMinutes = Math.max(1, minutes);
        if (backupEnabled) {
            stopBackupTimerThread();
            startBackupTimerThread();
        }
    }
    
    public int getMaxBackupCount() {
        return maxBackupCount;
    }
    
    public void setMaxBackupCount(int count) {
        this.maxBackupCount = Math.max(1, count);
    }
    
    public int getAutoDeleteDays() {
        return autoDeleteDays;
    }
    
    public void setAutoDeleteDays(int days) {
        this.autoDeleteDays = Math.max(0, days);
    }
    
    public void setBackupCallback(BackupCallback callback) {
        this.backupCallback = callback;
    }
    
    private void startBackupTimerThread() {
        stopBackupTimer = false;
        if (backupTimerThread != null && backupTimerThread.isAlive()) {
            return;
        }
        backupTimerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !stopBackupTimer) {
                try {
                    Thread.sleep(backupIntervalMinutes * 60 * 1000);
                    if (stopBackupTimer) break;
                    if (status == JarRunner.Status.RUNNING) {
                        performBackup();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        backupTimerThread.setDaemon(true);
        backupTimerThread.setName("backup-timer-" + jarPath);
        backupTimerThread.start();
    }
    
    public void stopBackupTimerThread() {
        stopBackupTimer = true;
        if (backupTimerThread != null && backupTimerThread.isAlive()) {
            backupTimerThread.interrupt();
            try {
                backupTimerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void performBackup() {
        if (isBackingUp) {
            return;
        }
        isBackingUp = true;
        
        try {
            File jarFile = new File(jarPath);
            if (!jarFile.exists() || !jarFile.canRead()) {
                isBackingUp = false;
                outputPanel.append("[MSH] 备份失败: 服务端文件不存在或无法读取\n");
                Logger.error("Jar file does not exist or cannot be read: " + jarPath, "JarRunner");
                if (backupCallback != null) {
                    backupCallback.onBackupComplete(null, false);
                }
                return;
            }
            
            File serverDir = jarFile.getParentFile();
            if (serverDir == null || !serverDir.exists() || !serverDir.isDirectory()) {
                isBackingUp = false;
                outputPanel.append("[MSH] 备份失败: 服务端目录不存在\n");
                Logger.error("Server directory does not exist", "JarRunner");
                if (backupCallback != null) {
                    backupCallback.onBackupComplete(null, false);
                }
                return;
            }
            
            String safeServerName = sanitizeFileName(jarFile.getName());
            File backupDir = new File("MSH/backup", safeServerName);
            if (!backupDir.exists()) {
                if (!backupDir.mkdirs()) {
                    isBackingUp = false;
                    outputPanel.append("[MSH] 备份失败: 无法创建备份目录\n");
                    Logger.error("Failed to create backup directory: " + backupDir.getAbsolutePath(), "JarRunner");
                    if (backupCallback != null) {
                        backupCallback.onBackupComplete(null, false);
                    }
                    return;
                }
            }
            
            String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
            String timeStr = new java.text.SimpleDateFormat("HH-mm-ss").format(new java.util.Date());
            String sizeInfo = getServerDirSize(serverDir);
            String zipName = safeServerName + "_backup_" + dateStr + "_" + timeStr + "_" + sizeInfo + ".zip";
            File zipFile = new File(backupDir, zipName);
            
            outputPanel.append("[MSH] 正在创建备份...\n");
            Logger.info("Starting backup for server: " + jarPath, "JarRunner");
            
            Thread backupThread = new Thread(() -> {
                try {
                    zipDirectory(serverDir.toPath(), zipFile.toPath());
                    lastBackupTime = System.currentTimeMillis();
                    if (!zipFile.exists() || zipFile.length() == 0) {
                        throw new IOException("Backup file was not created or is empty");
                    }
                    isBackingUp = false;
                    outputPanel.append("[MSH] 备份已完成: " + zipFile.getName() + "\n");
                    Logger.info("Backup completed: " + zipFile.getAbsolutePath(), "JarRunner");
                    if (backupCallback != null) {
                        backupCallback.onBackupComplete(zipFile.getAbsolutePath(), true);
                    }
                } catch (Exception e) {
                    isBackingUp = false;
                    outputPanel.append("[MSH] 备份失败: " + e.getMessage() + "\n");
                    Logger.error("Backup failed for server " + jarPath + ": " + e.getMessage(), "JarRunner");
                    if (backupCallback != null) {
                        backupCallback.onBackupComplete(null, false);
                    }
                }
            });
            backupThread.setDaemon(true);
            backupThread.setName("backup-thread-" + safeServerName);
            backupThread.start();
        } catch (Exception e) {
            isBackingUp = false;
            outputPanel.append("[MSH] 备份失败: " + e.getMessage() + "\n");
            Logger.error("Backup failed with exception: " + e.getMessage(), "JarRunner");
            if (backupCallback != null) {
                backupCallback.onBackupComplete(null, false);
            }
        }
    }
    
    public boolean isBackingUp() {
        return isBackingUp;
    }
    
    private static final String[] BACKUP_EXCLUDE_PATTERNS = {
        ".log", ".tmp", ".temp", "cache/", "logs/", "backup/", 
        "session/", "crash-reports/", "forwarding/", "plugins/translations"
    };
    
    private String getServerDirSize(File dir) {
        long size = 0;
        try {
            size = Files.walk(dir.toPath())
                .filter(p -> p.toFile().isFile())
                .filter(p -> !isExcluded(p, dir.toPath()))
                .mapToLong(p -> p.toFile().length())
                .sum();
        } catch (IOException e) {
        }
        return formatSize(size);
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return Math.round(bytes) + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.2fGB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private void zipDirectory(Path sourceDir, Path targetZip) throws IOException {
        List<Path> allPaths = Files.walk(sourceDir)
            .filter(path -> !path.equals(sourceDir))
            .filter(path -> !isExcluded(path, sourceDir))
            .collect(java.util.stream.Collectors.toList());

        List<ZipEntryData> entries = allPaths.parallelStream()
            .map(path -> {
                String relativePath = sourceDir.relativize(path).toString().replace("\\", "/");
                if (path.toFile().isDirectory()) {
                    relativePath += "/";
                    return new ZipEntryData(relativePath, path.toFile().lastModified(), null, true);
                }
                try {
                    byte[] content = null;
                    if (!isAlreadyCompressed(path.toFile())) {
                        content = readFileWithLock(path.toFile());
                    }
                    return new ZipEntryData(relativePath, path.toFile().lastModified(), content, false);
                } catch (IOException e) {
                    Logger.warn("Skipping locked file during backup: " + relativePath, "JarRunner");
                    return null;
                }
            })
            .filter(data -> data != null)
            .collect(java.util.stream.Collectors.toList());

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(targetZip.toFile()), 131072))) {
            zos.setLevel(Deflater.NO_COMPRESSION);
            for (ZipEntryData entry : entries) {
                try {
                    ZipEntry zipEntry = new ZipEntry(entry.relativePath);
                    zipEntry.setTime(entry.lastModified);
                    zos.putNextEntry(zipEntry);
                    if (!entry.isDirectory && entry.content != null) {
                        zos.write(entry.content);
                    } else if (!entry.isDirectory && isAlreadyCompressed(new File(sourceDir.toFile(), entry.relativePath))) {
                        try (InputStream is = new BufferedInputStream(
                                new FileInputStream(new File(sourceDir.toFile(), entry.relativePath)), 131072)) {
                            byte[] buffer = new byte[131072];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);
                            }
                        }
                    }
                    zos.closeEntry();
                } catch (IOException e) {
                    Logger.error("Failed to add entry to zip: " + entry.relativePath, "JarRunner");
                }
            }
        }
    }
    
    private static class ZipEntryData {
        final String relativePath;
        final long lastModified;
        final byte[] content;
        final boolean isDirectory;
        
        ZipEntryData(String relativePath, long lastModified, byte[] content, boolean isDirectory) {
            this.relativePath = relativePath;
            this.lastModified = lastModified;
            this.content = content;
            this.isDirectory = isDirectory;
        }
    }
    
    private boolean isExcluded(Path path, Path sourceDir) {
        String relativePath = sourceDir.relativize(path).toString().replace("\\", "/").toLowerCase();
        for (String pattern : BACKUP_EXCLUDE_PATTERNS) {
            if (pattern.endsWith("/")) {
                if (relativePath.startsWith(pattern) || relativePath.contains(pattern)) {
                    return true;
                }
            } else if (relativePath.endsWith(pattern)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isAlreadyCompressed(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".png") || 
               name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") ||
               name.endsWith(".mp3") || name.endsWith(".mp4") || name.endsWith(".ogg") ||
               name.endsWith(".webm") || name.endsWith(".gz") || name.endsWith(".bz2");
    }
    
    private byte[] readFileWithLock(File file) throws IOException {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            if (isFileLocked(e)) {
                return new byte[0];
            }
            throw e;
        }
    }
    
    private boolean isFileLocked(IOException e) {
        String message = e.getMessage();
        return message != null && (
            message.contains("locked") ||
            message.contains("另一个程序") ||
            message.contains("being used") ||
            message.contains("The process cannot access")
        );
    }
    
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "server";
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
    
    public void triggerBackup() {
        performBackup();
    }
    
    public long getLastBackupTime() {
        return lastBackupTime;
    }
    public Status getStatus() {
        return status;
    }
    
    public String getJarPath() {
        return jarPath;
    }
    
    public String getCustomName() {
        return customName;
    }
    
    public void setCustomName(String name) {
        this.customName = name;
    }
    
    public String getDisplayName() {
        if (customName != null && !customName.trim().isEmpty()) {
            return customName;
        }
        File jarFile = new File(jarPath);
        return jarFile.getName();
    }
    
    public boolean isAutoRestartEnabled() {
        return autoRestartEnabled;
    }
    
    public void setAutoRestartEnabled(boolean enabled) {
        this.autoRestartEnabled = enabled;
    }
    
    public boolean isForceKeepAlive() {
        return forceKeepAlive;
    }
    
    public void setForceKeepAlive(boolean enabled) {
        this.forceKeepAlive = enabled;
    }
    
    public void setRestartSettings(int maxAttempts, int intervalSeconds) {
        this.maxHourlyAttempts = maxAttempts;
        this.restartInterval = intervalSeconds;
    }
    
    public int[] getRestartSettings() {
        return new int[]{maxHourlyAttempts, restartInterval};
    }

    public int getCurrentHourlyAttempts() {
        checkAndResetHourlyCounter();
        return currentHourlyAttempts.get();
    }
    
    public ColorOutputPanel getOutputPanel() {
        return outputPanel;
    }
    
    public void setGameRuleCallback(GameRuleCallback callback) {
        this.gameRuleCallback = callback;
    }
    
    public boolean isUseNoGui() {
        return useNoGui;
    }
    
    public void setUseNoGui(boolean useNoGui) {
        this.useNoGui = useNoGui;
    }
    
    public String getServerVersion() {
        return serverVersion;
    }
    
    public void setServerVersion(String version) {
        this.serverVersion = version;
    }
    
    public String getLastError() {
        return lastError;
    }
    
    public void setLastError(String error) {
        this.lastError = error;
    }
    
    public void clearLastError() {
        this.lastError = null;
    }
    
    public GameRuleCallback getGameRuleCallback() {
        return gameRuleCallback;
    }
    
    public void onGameRuleValue(String ruleName, String value) {
        if (gameRuleCallback != null) {
            gameRuleCallback.onGameRuleValue(ruleName, value);
        }
    }
    
    public Process getProcess() {
        return process;
    }
    
    public boolean isProcessAlive() {
        return process != null && process.isAlive();
    }
    
    private volatile boolean isTerminated = false;
    
    public void onProcessTerminated() {
        if (isTerminated) {
            return;
        }
        isTerminated = true;
        
        if (status == Status.STOPPED) {
            return;
        }
        
        int exitCode = -1;
        try {
            exitCode = process.exitValue();
        } catch (IllegalThreadStateException e) {
        }
        
        Logger.warn("Detected server process terminated: " + jarPath + ", exit code: " + exitCode, "JarRunner");
        outputPanel.append("[MSH] 检测到服务器进程已终止，退出代码: " + exitCode + "\n");
        
        if (isNormalStop && !forceKeepAlive) {
            Logger.info("Server normal shutdown, skipping auto-restart: " + jarPath, "JarRunner");
            outputPanel.append("[MSH] 服务器正常关闭，跳过自动重启\n");
            status = Status.STOPPED;
            cleanupProcess();
            cleanupLockFilesDelayed();
            return;
        }
        
        String terminationReason = detectTerminationReason(exitCode);
        if (!terminationReason.isEmpty()) {
            outputPanel.append("[MSH] 可能原因: " + terminationReason + "\n");
            showTerminationDialog(terminationReason, exitCode);
        } else if (exitCode != 0 && exitCode != -1) {
            String exitCodeReason = getExitCodeReason(exitCode);
            outputPanel.append("[MSH] 异常退出: " + exitCodeReason + "\n");
            showTerminationDialog(exitCodeReason, exitCode);
        } else {
            checkAndResetHourlyCounter();
        }
    }
    
    private String getExitCodeReason(int exitCode) {
        switch (exitCode) {
            case 1:
                return "一般性错误，服务器启动失败";
            case 2:
                return "命令行参数错误";
            case 3:
                return "启动前检查失败";
            case 4:
                return "内存分配失败";
            case 130:
                return "进程被信号中断 (Ctrl+C)";
            case 137:
                return "进程被强制终止 (OOM Killer或手动kill)";
            case 139:
                return "段错误，JVM崩溃";
            case 143:
                return "进程被终止 (TERM信号)";
            case 255:
                return "未知错误，服务器异常退出";
            default:
                if (exitCode < 0 && exitCode > -128) {
                    return "进程被信号 " + Math.abs(exitCode) + " 终止";
                }
                return "未知退出代码: " + exitCode;
        }
    }
    
    private void cleanupLockFilesDelayed() {
        Thread cleanupThread = new Thread(() -> {
            try {
                Thread.sleep(2000);
                cleanupLockFiles();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("lock-cleanup-" + jarPath);
        cleanupThread.start();
    }
    
    private String detectTerminationReason(int exitCode) {
        File serverDir = new File(jarPath).getParentFile();
        if (serverDir == null) {
            return "";
        }
        
        File[] lockFiles = {
            new File(serverDir, "session.lock"),
            new File(serverDir, "world/session.lock"),
            new File(serverDir, "logs/latest.log.lck"),
            new File(serverDir, "usercache.json.lock"),
            new File(serverDir, "players/stats/*.json.lock")
        };
        
        for (File lockFile : lockFiles) {
            if (lockFile.exists()) {
                return "文件被锁定: " + lockFile.getName();
            }
        }
        
        File worldDir = new File(serverDir, "world");
        if (worldDir.exists()) {
            File[] files = worldDir.listFiles((dir, name) -> 
                name.endsWith(".lock") || name.equals("session.lock")
            );
            if (files != null && files.length > 0) {
                return "世界文件夹被锁定: " + files[0].getName();
            }
        }
        
        File logsDir = new File(serverDir, "logs");
        if (logsDir.exists()) {
            File[] files = logsDir.listFiles((dir, name) -> name.endsWith(".lck"));
            if (files != null && files.length > 0) {
                return "日志文件被锁定: " + files[0].getName();
            }
        }
        
        if (exitCode == 137) {
            return "内存不足导致进程被系统终止";
        }
        
        if (exitCode == 139) {
            return "JVM发生段错误，可能内存损坏或库冲突";
        }
        
        if (lastError != null && !lastError.isEmpty()) {
            if (lastError.contains("内存不足") || lastError.contains("OutOfMemory")) {
                return "内存不足错误";
            }
            if (lastError.contains("端口") || lastError.contains("Bind")) {
                return "端口被占用或绑定失败";
            }
            if (lastError.contains("权限") || lastError.contains("Access denied")) {
                return "文件权限不足";
            }
            if (lastError.contains("锁定") || lastError.contains("locked")) {
                return "文件被其他程序锁定";
            }
            return "错误: " + lastError;
        }
        
        return "";
    }
    
    private void cleanupLockFiles() {
        File serverDir = new File(jarPath).getParentFile();
        if (serverDir == null) {
            Logger.warn("Server directory is null, skipping lock file cleanup", "JarRunner");
            return;
        }
        
        try {
            File[] lockFiles = {
                new File(serverDir, "session.lock"),
                new File(serverDir, "world/session.lock"),
                new File(serverDir, "logs/latest.log.lck")
            };
            
            for (File lockFile : lockFiles) {
                if (lockFile.exists()) {
                    try {
                        boolean deleted = deleteLockFileWithRetry(lockFile, 3);
                        if (deleted) {
                            outputPanel.append("[MSH] 已清理锁定文件: " + lockFile.getName() + "\n");
                            Logger.info("Successfully cleaned up lock file: " + lockFile.getAbsolutePath(), "JarRunner");
                        }
                    } catch (Exception e) {
                        outputPanel.append("[MSH] 无法清理 " + lockFile.getName() + ": " + e.getMessage() + "\n");
                        Logger.warn("Failed to cleanup lock file " + lockFile.getAbsolutePath() + ": " + e.getMessage(), "JarRunner");
                    }
                }
            }
            
            File worldDir = new File(serverDir, "world");
            if (worldDir.exists()) {
                File[] files = worldDir.listFiles((dir, name) -> 
                    name.endsWith(".lock") || name.equals("session.lock")
                );
                if (files != null && files.length > 0) {
                    outputPanel.append("[MSH] 发现 " + files.length + " 个世界锁定文件，正在清理...\n");
                    Logger.info("Found " + files.length + " world lock files, cleaning up", "JarRunner");
                    for (File file : files) {
                        try {
                            boolean deleted = deleteLockFileWithRetry(file, 3);
                            if (deleted) {
                                outputPanel.append("[MSH] 已清理锁定文件: " + file.getName() + "\n");
                                Logger.info("Successfully cleaned up world lock file: " + file.getAbsolutePath(), "JarRunner");
                            }
                        } catch (Exception e) {
                            outputPanel.append("[MSH] 无法清理 " + file.getName() + ": " + e.getMessage() + "\n");
                        }
                    }
                }
            } else {
                Logger.debug("World directory does not exist, skipping world lock file cleanup", "JarRunner");
            }
        } catch (Exception e) {
            Logger.error("Failed to cleanup lock files: " + e.getMessage(), "JarRunner");
        }
    }
    
    private void showTerminationDialog(String reason, int exitCode) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(outputPanel);
            if (frame == null) {
                return;
            }
            
            String serverName = getDisplayName();
            StringBuilder message = new StringBuilder();
            message.append("服务器 ").append(serverName).append(" 异常关闭！\n\n");
            message.append("原因: ").append(reason);
            if (exitCode != -1 && exitCode != 0) {
                message.append("\n退出代码: ").append(exitCode);
            }
            message.append("\n\n是否尝试自动处理并重启服务器？");
            
            int option = JOptionPane.showOptionDialog(
                frame,
                message.toString(),
                "服务器异常关闭",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"自动处理并重启", "手动处理"},
                "手动处理"
            );
            
            if (option == JOptionPane.YES_OPTION) {
                Logger.info("User confirmed auto-handle for: " + jarPath, "JarRunner");
                outputPanel.append("[MSH] 用户选择自动处理...\n");
                handleTerminationAuto(action -> {
                    if (action == 1) {
                        forceUnlockAndRestart();
                    } else if (action == 2) {
                        cleanupLockFiles();
                        start();
                    } else if (action == 3) {
                        forceStopChildren();
                        forceUnlockAndRestart();
                    }
                });
            } else {
                Logger.info("User chose manual handling for: " + jarPath, "JarRunner");
                outputPanel.append("[MSH] 用户选择手动处理\n");
            }
            checkAndResetHourlyCounter();
        });
    }
    
    private void handleTerminationAuto(java.util.function.Consumer<Integer> actionCallback) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(outputPanel);
            if (frame == null) {
                actionCallback.accept(0);
                return;
            }
            
            String[] options = {"解除锁定后重启", "清理文件后重启", "强制终止后重启"};
            int choice = JOptionPane.showOptionDialog(
                frame,
                "请选择自动处理方式:",
                "选择处理方式",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
            );
            
            actionCallback.accept(choice + 1);
        });
    }
    
    private void forceStopChildren() {
        File serverDir = new File(jarPath).getParentFile();
        if (serverDir == null) return;
        
        try {
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/T", "/IM", "java.exe");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();
            outputPanel.append("[MSH] 已尝试终止所有Java子进程\n");
            Thread.sleep(1000);
        } catch (Exception e) {
            outputPanel.append("[MSH] 无法终止子进程: " + e.getMessage() + "\n");
        }
    }
    
    public String getTerminationReason() {
        int exitCode = -1;
        try {
            if (process != null) {
                exitCode = process.exitValue();
            }
        } catch (IllegalThreadStateException e) {
        }
        return detectTerminationReason(exitCode);
    }
    
    public void forceUnlockAndRestart() {
        cleanupProcess();
        
        File serverDir = new File(jarPath).getParentFile();
        if (serverDir != null) {
            outputPanel.append("[MSH] 正在解除文件锁定...\n");
            Logger.info("Attempting to release file locks for: " + jarPath, "JarRunner");
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            File[] lockFiles = {
                new File(serverDir, "session.lock"),
                new File(serverDir, "world/session.lock"),
                new File(serverDir, "logs/latest.log.lck")
            };
            
            for (File lockFile : lockFiles) {
                if (lockFile.exists()) {
                    boolean deleted = deleteLockFileWithRetry(lockFile, 3);
                    if (deleted) {
                        outputPanel.append("[MSH] 已删除锁定文件: " + lockFile.getName() + "\n");
                    }
                }
            }
            
            File worldDir = new File(serverDir, "world");
            if (worldDir.exists()) {
                File[] files = worldDir.listFiles((dir, name) -> 
                    name.endsWith(".lock") || name.equals("session.lock")
                );
                if (files != null && files.length > 0) {
                    outputPanel.append("[MSH] 发现 " + files.length + " 个世界锁定文件，正在删除...\n");
                    for (File file : files) {
                        boolean deleted = deleteLockFileWithRetry(file, 3);
                        if (deleted) {
                            outputPanel.append("[MSH] 已删除锁定文件: " + file.getName() + "\n");
                        }
                    }
                }
            }
            
            outputPanel.append("[MSH] 等待文件锁释放...\n");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        status = Status.STOPPED;
        isTerminated = false;
        isNormalStop = false;
        
        outputPanel.append("[MSH] 正在重新启动服务器...\n");
        start();
    }
    
    private boolean deleteLockFileWithRetry(File file, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (!file.exists()) {
                    return true;
                }
                
                boolean deleted = Files.deleteIfExists(file.toPath());
                if (deleted) {
                    Logger.info("Successfully deleted lock file: " + file.getAbsolutePath() + " (attempt " + attempt + ")", "JarRunner");
                    return true;
                }
                
                if (attempt < maxRetries) {
                    outputPanel.append("[MSH] 第 " + attempt + " 次尝试删除 " + file.getName() + " 失败，等待重试...\n");
                    Thread.sleep(1000);
                }
            } catch (IOException e) {
                Logger.warn("Failed to delete lock file " + file.getAbsolutePath() + " (attempt " + attempt + "): " + e.getMessage(), "JarRunner");
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        outputPanel.append("[MSH] 无法删除锁定文件: " + file.getName() + "\n");
        return false;
    }

    public void start() {
        if (status == Status.RUNNING || status == Status.STARTING) {
            Logger.warn("Server already running or starting, skipping start request: " + jarPath, "JarRunner");
            return;
        }
        Logger.info("Initiating server startup: " + jarPath, "JarRunner");
        currentHourlyAttempts.set(0);
        lastRestartTimestamp = 0;
        isTerminated = false;
        isNormalStop = false;
        status = Status.STARTING;
        try {
            startServer();
        } catch (IOException e) {
            status = Status.STOPPED;
            Logger.error("Server startup failed: " + e.getMessage(), "JarRunner");
            outputPanel.append("[MSH] Server startup failed: " + e.getMessage() + "\n");
        }
    }
    private void startServer() throws IOException {
        File jarFile = new File(jarPath);
        if (!jarFile.exists()) {
            throw new IOException("Server JAR file does not exist: " + jarPath);
        }
        
        if (!jarFile.canRead()) {
            throw new IOException("Cannot read server JAR file (permission denied): " + jarPath);
        }
        
        File serverDir = jarFile.getParentFile();
        if (serverDir == null) {
            throw new IOException("Invalid server directory path: " + jarPath);
        }
        
        if (!serverDir.exists() || !serverDir.isDirectory()) {
            throw new IOException("Server directory does not exist or is not a directory: " + serverDir.getAbsolutePath());
        }
        
        try {
            String javaCmd = System.getProperty("os.name").toLowerCase().contains("windows") ? "javaw" : "java";
            List<String> command = new ArrayList<>();
            command.add(javaCmd);
            command.add("-jar");
            command.add(jarPath);
            if (useNoGui) {
                command.add("--nogui");
            }
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(serverDir);
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
        } catch (SecurityException e) {
            throw new IOException("Security exception while starting Java process: " + e.getMessage(), e);
        } catch (OutOfMemoryError e) {
            Logger.error("Out of memory error while starting server process", "JarRunner");
            throw new IOException("Insufficient memory to start server process", e);
        }
        processInput = process.getOutputStream();
        commandWriter = new PrintWriter(new OutputStreamWriter(processInput, EncodingUtils.getOptimalCharset()), true);
        InputStream inputStream = process.getInputStream();
        stdoutThread = new Thread(new OutputHandler(inputStream, outputPanel, this, jarPath));
        try {
            stdoutThread.setName("stdout-handler-" + jarPath);
            stdoutThread.setDaemon(true);
            stdoutThread.start();
            if (!stdoutThread.isAlive()) {
                throw new IOException("Failed to start stdout thread - thread died immediately");
            }
        } catch (OutOfMemoryError e) {
            Logger.error("FATAL: Out of memory while creating stdout thread for: " + jarPath, "JarRunner");
            throw new IOException("Insufficient memory to create output handler thread", e);
        } catch (VirtualMachineError e) {
            Logger.error("FATAL: JVM internal error while creating stdout thread for: " + jarPath, "JarRunner");
            throw new IOException("JVM internal error during thread creation", e);
        } catch (Exception e) {
            Logger.error("FATAL: Failed to start stdout thread for: " + jarPath + " - " + e.getMessage(), "JarRunner");
            Logger.error("ERROR: Process startup failed - thread creation error for JAR: " + jarPath, "JarRunner");
            throw e;
        }
        processMonitorThread = new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                Logger.info("Server process terminated with exit code: " + exitCode, "JarRunner");
                outputPanel.append("[MSH] Server process terminated, exit code: " + exitCode + "\n");
                onProcessTerminated();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logger.error("FATAL: Process monitor thread interrupted unexpectedly - possible system instability", "JarRunner");
            } catch (OutOfMemoryError e) {
                Logger.error("CRITICAL: Out of memory in process monitor thread - JVM may be unstable", "JarRunner");
                try {
                    onProcessTerminated();
                } catch (Throwable t) {
                    Logger.error("FATAL: Critical failure during process termination cleanup after OOM", "JarRunner");
                }
            } catch (VirtualMachineError e) {
                Logger.error("CRITICAL: JVM internal error in process monitor thread: " + e.getClass().getSimpleName(), "JarRunner");
                try {
                    onProcessTerminated();
                } catch (Throwable t) {
                    Logger.error("FATAL: Critical failure during process termination cleanup after VM error", "JarRunner");
                }
            } catch (Exception e) {
                Logger.error("FATAL: Unexpected exception in process monitor thread: " + e.getClass().getSimpleName() + " - " + e.getMessage(), "JarRunner");
                Logger.error("ERROR: Process monitoring thread crashed unexpectedly", "JarRunner");
                try {
                    onProcessTerminated();
                } catch (Throwable t) {
                    Logger.error("FATAL: Critical failure during exception handling cleanup", "JarRunner");
                }
            } finally {
                try {
                    cleanupProcess();
                } catch (Throwable t) {
                    Logger.error("CRITICAL: Failed to cleanup process after monitor thread failure: " + t.getClass().getSimpleName(), "JarRunner");
                }
            }
        });
        processMonitorThread.setDaemon(true);
        processMonitorThread.start();
        try {
            Thread.sleep(1000);
            if (!process.isAlive()) {
                status = Status.STOPPED;
                Logger.error("Server startup failed: process failed to start successfully", "JarRunner");
                outputPanel.append("[MSH] Server startup failed: process failed to start\n");
                return;
            }
            Logger.info("Server process started successfully, PID: " + process.pid(), "JarRunner");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void onServerFullyStarted() {
        if (status == Status.STARTING) {
            status = Status.RUNNING;
            lastAccessTime = System.currentTimeMillis();
            Logger.info("Server startup completed successfully: " + jarPath, "JarRunner");
            outputPanel.append("[MSH] Server startup completed: " + jarPath + "\n");
        }
    }
    
    public void onServerStopping() {
        if (status == Status.RUNNING) {
            status = Status.STOPPING;
            isNormalStop = true;
            Logger.info("Server is stopping: " + jarPath, "JarRunner");
            outputPanel.append("[MSH] Server is stopping...\n");
        }
    }
    public void stop() {
        if (status == Status.STOPPED || status == Status.STOPPING) {
            Logger.warn("Server already stopped or stopping, skipping stop request: " + jarPath, "JarRunner");
            return;
        }
        Logger.info("Stopping server: " + jarPath, "JarRunner");
        status = Status.STOPPING;
        isNormalStop = true;
        isTerminated = false;
        if (commandWriter != null) {
            commandWriter.println("stop");
            commandWriter.flush();
            outputPanel.append("[Command] stop\n");
        }
    }
    
    public void forceStop() {
        if (status == Status.STOPPED || status == Status.STOPPING) {
            Logger.warn("Server already stopped or stopping, skipping force stop request: " + jarPath, "JarRunner");
            return;
        }
        Logger.warn("Force stopping server: " + jarPath, "JarRunner");
        isNormalStop = false;
        isTerminated = false;
        if (process != null) {
            process.destroyForcibly();
        }
    }
    private void cleanupProcess() {
        if (commandWriter != null) {
            try {
                commandWriter.close();
            } catch (Exception e) {
                Logger.error("Failed to close command writer: " + e.getMessage(), "JarRunner");
            }
            commandWriter = null;
        }
        if (processInput != null) {
            try {
                processInput.close();
            } catch (IOException e) {
                Logger.error("Failed to close process input stream: " + e.getMessage(), "JarRunner");
            }
            processInput = null;
        }
        if (stdoutThread != null && stdoutThread.isAlive()) {
            try {
                stdoutThread.interrupt();
                stdoutThread.join(5000);
            } catch (InterruptedException e) {
                Logger.error("Failed to properly stop stdout thread: " + e.getMessage(), "JarRunner");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Logger.error("Unexpected error while stopping stdout thread: " + e.getMessage(), "JarRunner");
            }
        }
        if (stderrThread != null && stderrThread.isAlive()) {
            try {
                stderrThread.interrupt();
                stderrThread.join(5000);
            } catch (InterruptedException e) {
                Logger.error("Failed to properly stop stderr thread: " + e.getMessage(), "JarRunner");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Logger.error("Unexpected error while stopping stderr thread: " + e.getMessage(), "JarRunner");
            }
        }
        if (processMonitorThread != null && processMonitorThread.isAlive()) {
            processMonitorThread.interrupt();
        }
        process = null;
        if (status != Status.STOPPED) {
            status = Status.STOPPED;
            Logger.info("Server cleanup completed", "JarRunner");
            outputPanel.append("[MSH] Server stopped: " + jarPath + "\n");
        }
    }
    

    
    public void sendCommand(String command) {
        if ((status == Status.RUNNING || status == Status.STOPPING) && commandWriter != null) {
            Logger.debug("Sending server command: " + command, "JarRunner");
            commandWriter.println(command);
            commandWriter.flush();
            lastAccessTime = System.currentTimeMillis();
            outputPanel.append("[Command] " + command + "\n");
            String cmdLower = command.toLowerCase().trim();
            if (cmdLower.equals("stop") || cmdLower.equals("/stop")) {
                isNormalStop = true;
                isTerminated = false;
                status = Status.STOPPING;
            }
        } else {
            Logger.warn("Server not running, cannot send command: " + command, "JarRunner");
        }
    }
    public void restart() {
        if (status == Status.STARTING || status == Status.STOPPING) {
            Logger.warn("Server is starting or stopping, skipping restart request: " + jarPath, "JarRunner");
            return;
        }
        Logger.info("Restarting server: " + jarPath, "JarRunner");
        outputPanel.append("[MSH] Restarting server: " + jarPath + "\n");
        status = Status.STOPPING;
        stop();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Logger.error("Restart interrupted: " + e.getMessage(), "JarRunner");
            Thread.currentThread().interrupt();
        }
        start();
    }
    
    public void addToHistory(String command) {
        if (command != null && !command.trim().isEmpty()) {
            if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
                commandHistory.add(command);
                if (commandHistory.size() > MAX_HISTORY_SIZE) {
                    commandHistory.remove(0);
                }
            }
            historyIndex = -1;
        }
    }
    
    public String getPreviousCommand() {
        if (commandHistory.isEmpty()) {
            return null;
        }
        if (historyIndex == -1) {
            historyIndex = commandHistory.size() - 1;
        } else if (historyIndex > 0) {
            historyIndex--;
        }
        return commandHistory.get(historyIndex);
    }
    
    public String getNextCommand() {
        if (commandHistory.isEmpty() || historyIndex == -1) {
            return null;
        }
        if (historyIndex < commandHistory.size() - 1) {
            historyIndex++;
            return commandHistory.get(historyIndex);
        } else {
            historyIndex = -1;
            return null;
        }
    }
}
