import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

public class JarRunner {
    public interface GameRuleCallback {
        void onGameRuleValue(String ruleName, String value);
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
    private int maxHourlyAttempts;
    private int restartInterval;
    private AtomicInteger currentHourlyAttempts;
    private volatile long lastRestartTimestamp;
    private Thread hourlyResetThread;
    private volatile boolean isNormalStop = false;
    
    public JarRunner(String jarPath, ColorOutputPanel outputPanel) {
        this.jarPath = jarPath;
        this.customName = null;
        this.outputPanel = outputPanel;
        this.status = Status.STOPPED;
        this.autoRestartEnabled = false;
        this.maxHourlyAttempts = 3;
        this.restartInterval = 10;
        this.currentHourlyAttempts = new AtomicInteger(0);
        this.lastRestartTimestamp = 0;
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
                            Logger.info("已重置每小时重启计数器", "JarRunner");
                            outputPanel.append("[MSH] 已重置每小时重启计数器\n");
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
        
        Logger.warn("检测到服务器进程终止: " + jarPath, "JarRunner");
        outputPanel.append("[MSH] 检测到服务器进程终止\n");
        if (isNormalStop) {
            Logger.info("服务器正常停止，不进行自动重启: " + jarPath, "JarRunner");
            outputPanel.append("[MSH] 服务器正常停止，不进行自动重启\n");
            status = Status.STOPPED;
            cleanupProcess();
            return;
        }
        
        checkAndResetHourlyCounter();
        
        if (autoRestartEnabled && currentHourlyAttempts.get() < maxHourlyAttempts) {
            int attempts = currentHourlyAttempts.incrementAndGet();
            lastRestartTimestamp = System.currentTimeMillis();
            Logger.warn(String.format("检测到服务器异常终止，将在 %d 秒后尝试自动重启 (本小时第 %d/%d 次尝试)", 
                restartInterval, attempts, maxHourlyAttempts), "JarRunner");
            Logger.warn("WARN: Server process terminated unexpectedly - initiating auto-restart sequence", "JarRunner");
            outputPanel.append(String.format("[MSH] 检测到服务器异常终止，将在 %d 秒后尝试自动重启 (本小时第 %d/%d 次尝试)", 
                restartInterval, attempts, maxHourlyAttempts) + "\n");
            
            Thread restartThread = new Thread(() -> {
                try {
                    Thread.sleep(restartInterval * 1000);
                    checkAndResetHourlyCounter();
                    if (status == Status.STOPPED && autoRestartEnabled && currentHourlyAttempts.get() <= maxHourlyAttempts) {
                        Logger.info(String.format("正在执行第 %d 次自动重启", attempts), "JarRunner");
                        outputPanel.append(String.format("[MSH] 正在执行第 %d 次自动重启...", attempts) + "\n");
                        JarRunner.this.start();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            restartThread.setDaemon(true);
            restartThread.start();
        } else if (currentHourlyAttempts.get() >= maxHourlyAttempts) {
            Logger.error("本小时内自动重启已达到最大尝试次数，停止重启尝试: " + jarPath, "JarRunner");
            outputPanel.append("[MSH] 本小时内自动重启已达到最大尝试次数，停止重启尝试\n");
        }
    }
    public void start() {
        if (status == Status.RUNNING || status == Status.STARTING) {
            Logger.warn("服务器已在运行或启动中，跳过启动请求: " + jarPath, "JarRunner");
            return;
        }
        Logger.info("开始启动服务器: " + jarPath, "JarRunner");
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
            outputPanel.append("[MSH] 服务器启动失败: " + e.getMessage() + "\n");
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
            ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", jarPath);
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
                outputPanel.append("[MSH] 服务器进程已终止，退出码: " + exitCode + "\n");
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
                Logger.error("服务器启动失败: 进程未能成功启动", "JarRunner");
                outputPanel.append("[MSH] 服务器启动失败: 进程未能成功启动\n");
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void onServerFullyStarted() {
        if (status == Status.STARTING) {
            status = Status.RUNNING;
            Logger.info("服务器启动成功: " + jarPath, "JarRunner");
            outputPanel.append("[MSH] 服务器启动成功: " + jarPath + "\n");
        }
    }
    
    public void onServerStopping() {
        if (status == Status.RUNNING) {
            status = Status.STOPPING;
            isNormalStop = true;
            Logger.info("服务器正在停止: " + jarPath, "JarRunner");
            outputPanel.append("[MSH] 服务器正在停止...\n");
        }
    }
    public void stop() {
        if (status == Status.STOPPED || status == Status.STOPPING) {
            Logger.warn("服务器已停止或正在停止，跳过停止请求: " + jarPath, "JarRunner");
            return;
        }
        Logger.info("停止服务器: " + jarPath, "JarRunner");
        status = Status.STOPPING;
        isNormalStop = true;
        isTerminated = false;
        if (commandWriter != null) {
            commandWriter.println("stop");
            commandWriter.flush();
            outputPanel.append("[命令] stop\n");
        }
    }
    
    public void forceStop() {
        if (status == Status.STOPPED || status == Status.STOPPING) {
            Logger.warn("服务器已停止或正在停止，跳过强制停止请求: " + jarPath, "JarRunner");
            return;
        }
        Logger.warn("强制停止服务器: " + jarPath, "JarRunner");
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
            outputPanel.append("[MSH] 服务器已停止: " + jarPath + "\n");
        }
    }
    

    
    public void sendCommand(String command) {
        if ((status == Status.RUNNING || status == Status.STOPPING) && commandWriter != null) {
            Logger.debug("发送服务器命令: " + command, "JarRunner");
            commandWriter.println(command);
            commandWriter.flush();
            outputPanel.append("[命令] " + command + "\n");
            String cmdLower = command.toLowerCase().trim();
            if (cmdLower.equals("stop") || cmdLower.equals("/stop")) {
                isNormalStop = true;
                isTerminated = false;
                status = Status.STOPPING;
            }
        } else {
            Logger.warn("服务器未运行，无法发送命令: " + command, "JarRunner");
        }
    }
    public void restart() {
        if (status == Status.STARTING || status == Status.STOPPING) {
            Logger.warn("服务器正在启动或停止，跳过重启请求: " + jarPath, "JarRunner");
            return;
        }
        Logger.info("重启服务器: " + jarPath, "JarRunner");
        outputPanel.append("[MSH] 正在重启服务器: " + jarPath + "\n");
        stop();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Logger.error("重启服务器时发生中断: " + e.getMessage(), "JarRunner");
            Thread.currentThread().interrupt();
        }
        start();
    }
}
