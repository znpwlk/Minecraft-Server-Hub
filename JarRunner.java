import java.io.*;
import javax.swing.JOptionPane;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class JarRunner {
    public enum Status {
        STOPPED, RUNNING, STARTING, STOPPING
    }
    private String jarPath;
    private ColorOutputPanel outputPanel;
    private Process process;
    private Thread stdoutThread;
    private Thread stderrThread;
    private volatile Status status;
    private OutputStream processInput;
    private PrintWriter commandWriter;
    public JarRunner(String jarPath, ColorOutputPanel outputPanel) {
        this.jarPath = jarPath;
        this.outputPanel = outputPanel;
        this.status = Status.STOPPED;
    }
    public Status getStatus() {
        return status;
    }
    
    public String getJarPath() {
        return jarPath;
    }
    public void start() {
        if (status == Status.RUNNING || status == Status.STARTING) {
            return;
        }
        status = Status.STARTING;
        try {
            startServer();
        } catch (IOException e) {
            status = Status.STOPPED;
            e.printStackTrace();
            outputPanel.append("[MSH] 服务器启动失败: " + e.getMessage());
        }
    }
    private void startServer() throws IOException {
        File jarFile = new File(jarPath);
        File serverDir = jarFile.getParentFile();
        ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", jarPath);
        processBuilder.directory(serverDir);
        processBuilder.redirectErrorStream(true);
        process = processBuilder.start();
        processInput = process.getOutputStream();
        commandWriter = new PrintWriter(processInput, true);
        InputStream inputStream = process.getInputStream();
        stdoutThread = new Thread(new OutputHandler(inputStream, outputPanel, this, jarPath));
        stdoutThread.start();
        status = Status.RUNNING;
        outputPanel.append("[MSH] 服务器启动成功: " + jarPath);
    }
    public void stop() {
        if (status == Status.STOPPED || status == Status.STOPPING) {
            return;
        }
        status = Status.STOPPING;
        if (commandWriter != null) {
            commandWriter.close();
        }
        if (processInput != null) {
            try {
                processInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (process != null) {
            process.destroy();
            outputPanel.append("[MSH] 服务器已停止: " + jarPath);
        }
        if (stdoutThread != null && stdoutThread.isAlive()) {
            stdoutThread.interrupt();
        }
        if (stderrThread != null && stderrThread.isAlive()) {
            stderrThread.interrupt();
        }
        status = Status.STOPPED;
    }
    public void sendCommand(String command) {
        if (status == Status.RUNNING && commandWriter != null) {
            commandWriter.println(command);
            commandWriter.flush();
            outputPanel.append("[命令] " + command);
        }
    }
    public void restart() {
        if (status == Status.STARTING || status == Status.STOPPING) {
            return;
        }
        outputPanel.append("[MSH] 正在重启服务器: " + jarPath);
        stop();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        start();
    }
}
