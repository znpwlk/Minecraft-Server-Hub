import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        stopBackupTimerThread();
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
        
        Logger.warn("Detected server process terminated: " + jarPath, "JarRunner");
        outputPanel.append("[MSH] Detected server process terminated\n");
        if (isNormalStop && !forceKeepAlive) {
            Logger.info("Server normal shutdown, skipping auto-restart: " + jarPath, "JarRunner");
            outputPanel.append("[MSH] Server normal shutdown, skipping auto-restart\n");
            status = Status.STOPPED;
            cleanupProcess();
            return;
        }
        
        checkAndResetHourlyCounter();

        
        boolean withinLimit = maxHourlyAttempts == -1 || currentHourlyAttempts.get() < maxHourlyAttempts;
        boolean shouldRestart = forceKeepAlive || (autoRestartEnabled && withinLimit);
        
        if (shouldRestart) {
            if (!forceKeepAlive) {
                int attempts = currentHourlyAttempts.incrementAndGet();
                lastRestartTimestamp = System.currentTimeMillis();
                String attemptInfo = maxHourlyAttempts == -1 
                    ? String.format(" (attempt %d this hour)", attempts)
                    : String.format(" (attempt %d/%d this hour)", attempts, maxHourlyAttempts);
                Logger.warn("Server abnormal termination detected, auto-restart in " + restartInterval + " seconds" + attemptInfo, "JarRunner");
                Logger.warn("WARN: Server process terminated unexpectedly - initiating auto-restart sequence", "JarRunner");
                outputPanel.append("[MSH] Server abnormal termination detected, auto-restart in " + restartInterval + " seconds" + attemptInfo + "\n");
            } else {
                lastRestartTimestamp = System.currentTimeMillis();
                Logger.warn(String.format("Force keep alive mode: auto-restart in %d seconds", restartInterval), "JarRunner");
                outputPanel.append(String.format("[MSH] Force keep alive mode: auto-restart in %d seconds\n", restartInterval) + "\n");
            }
            
            Thread restartThread = new Thread(() -> {
                try {
                    if (restartInterval > 0) {
                        Thread.sleep(restartInterval * 1000L);
                    }
                    checkAndResetHourlyCounter();
                    if (status == Status.STOPPED && shouldRestart) {
                        Logger.info("Executing auto-restart", "JarRunner");
                        outputPanel.append("[MSH] Executing auto-restart...\n");
                        JarRunner.this.start();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            restartThread.setDaemon(true);
            restartThread.start();
        } else if (autoRestartEnabled && !forceKeepAlive && maxHourlyAttempts != -1 && currentHourlyAttempts.get() >= maxHourlyAttempts) {
            Logger.error("Auto-restart attempts exhausted for this hour, stopping restart attempts: " + jarPath, "JarRunner");
            outputPanel.append("[MSH] Auto-restart attempts exhausted for this hour, stopping restart attempts\n");
        }
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
