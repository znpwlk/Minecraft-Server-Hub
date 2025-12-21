import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
public class JarRunner {
    public enum Status {
        STOPPED, RUNNING, STARTING, STOPPING
    }
    private String jarPath;
    private ColorOutputPanel outputPanel;
    private Process process;
    private Thread stdoutThread;
    private Thread stderrThread;
    private Thread processMonitorThread;
    private volatile Status status;
    private OutputStream processInput;
    private PrintWriter commandWriter;
    
    private boolean autoRestartEnabled;
    private int maxRestartAttempts;
    private int restartInterval;
    private AtomicInteger currentRestartAttempts;
    private volatile boolean isNormalStop = false;
    
    public JarRunner(String jarPath, ColorOutputPanel outputPanel) {
        this.jarPath = jarPath;
        this.outputPanel = outputPanel;
        this.status = Status.STOPPED;
        this.autoRestartEnabled = false;
        this.maxRestartAttempts = 3;
        this.restartInterval = 10;
        this.currentRestartAttempts = new AtomicInteger(0);
    }
    public Status getStatus() {
        return status;
    }
    
    public String getJarPath() {
        return jarPath;
    }
    
    public boolean isAutoRestartEnabled() {
        return autoRestartEnabled;
    }
    
    public void setAutoRestartEnabled(boolean enabled) {
        this.autoRestartEnabled = enabled;
    }
    
    public void setRestartSettings(int maxAttempts, int intervalSeconds) {
        this.maxRestartAttempts = maxAttempts;
        this.restartInterval = intervalSeconds;
    }
    
    public int[] getRestartSettings() {
        return new int[]{maxRestartAttempts, restartInterval};
    }
    
    public ColorOutputPanel getOutputPanel() {
        return outputPanel;
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
        outputPanel.append("[MSH] 检测到服务器进程终止");
        
        if (isNormalStop) {
            Logger.info("服务器正常停止，不进行自动重启: " + jarPath, "JarRunner");
            outputPanel.append("[MSH] 服务器正常停止，不进行自动重启");
            return;
        }
        
        if (autoRestartEnabled && currentRestartAttempts.get() < maxRestartAttempts) {
            int attempts = currentRestartAttempts.incrementAndGet();
            Logger.warn(String.format("检测到服务器异常终止，将在 %d 秒后尝试自动重启 (第 %d/%d 次尝试)", 
                restartInterval, attempts, maxRestartAttempts), "JarRunner");
            outputPanel.append(String.format("[MSH] 检测到服务器异常终止，将在 %d 秒后尝试自动重启 (第 %d/%d 次尝试)", 
                restartInterval, attempts, maxRestartAttempts));
            
            Thread restartThread = new Thread(() -> {
                try {
                    Thread.sleep(restartInterval * 1000);
                    if (status == Status.STOPPED && autoRestartEnabled) {
                        Logger.info(String.format("正在执行第 %d 次自动重启", attempts), "JarRunner");
                        outputPanel.append(String.format("[MSH] 正在执行第 %d 次自动重启...", attempts));
                        start();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            restartThread.setDaemon(true);
            restartThread.start();
        } else if (currentRestartAttempts.get() >= maxRestartAttempts) {
            Logger.error("自动重启已达到最大尝试次数，停止重启尝试: " + jarPath, "JarRunner");
            outputPanel.append("[MSH] 自动重启已达到最大尝试次数，停止重启尝试");
        }
    }
    public void start() {
        if (status == Status.RUNNING || status == Status.STARTING) {
            Logger.warn("服务器已在运行或启动中，跳过启动请求: " + jarPath, "JarRunner");
            return;
        }
        Logger.info("开始启动服务器: " + jarPath, "JarRunner");
        currentRestartAttempts.set(0);
        isTerminated = false;
        isNormalStop = false;
        status = Status.STARTING;
        try {
            startServer();
        } catch (IOException e) {
            status = Status.STOPPED;
            Logger.error("Server startup failed: " + e.getMessage(), "JarRunner");
            outputPanel.append("[MSH] 服务器启动失败: " + e.getMessage());
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
        commandWriter = new PrintWriter(new OutputStreamWriter(processInput, java.nio.charset.StandardCharsets.UTF_8), true);
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
            throw e;
        }
        processMonitorThread = new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                outputPanel.append("[MSH] 服务器进程已终止，退出码: " + exitCode);
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
                outputPanel.append("[MSH] 服务器启动失败: 进程未能成功启动");
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        status = Status.RUNNING;
        Logger.info("服务器启动成功: " + jarPath, "JarRunner");
        outputPanel.append("[MSH] 服务器启动成功: " + jarPath);
    }
    public void stop() {
        if (status == Status.STOPPED || status == Status.STOPPING) {
            Logger.warn("服务器已停止或正在停止，跳过停止请求: " + jarPath, "JarRunner");
            return;
        }
        Logger.info("停止服务器: " + jarPath, "JarRunner");
        if (process != null) {
            process.destroy();
        }
        cleanupProcess();
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
        cleanupProcess();
    }
    private void cleanupProcess() {
        if (status != Status.STOPPED) {
            status = Status.STOPPING;
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
            status = Status.STOPPED;
            outputPanel.append("[MSH] 服务器已停止: " + jarPath);
        }
    }
    

    
    public void stopGracefully() {
        if (status == Status.STOPPED || status == Status.STOPPING) {
            Logger.warn("服务器已停止或正在停止，跳过优雅停止请求: " + jarPath, "JarRunner");
            return;
        }
        Logger.info("优雅停止服务器: " + jarPath, "JarRunner");
        isNormalStop = true;
        isTerminated = false;
        sendCommand("stop");
    }
    
    public void sendCommand(String command) {
        if (status == Status.RUNNING && commandWriter != null) {
            Logger.debug("发送服务器命令: " + command, "JarRunner");
            commandWriter.println(command);
            commandWriter.flush();
            outputPanel.append("[命令] " + command);
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
        outputPanel.append("[MSH] 正在重启服务器: " + jarPath);
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
