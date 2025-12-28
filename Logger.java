import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;

public class Logger {
    private static final String LOG_DIR = "MSH/log";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    
    private static Logger instance;
    private final ConcurrentLinkedQueue<LogEntry> logQueue;
    private final ExecutorService logExecutor;
    private PrintWriter fileWriter;
    private JTextArea logTextArea;
    private volatile boolean isRunning;
    private int maxLogEntries;
    
    public enum LogLevel {
        INFO("[INFO]", "#00FF00"),
        WARN("[WARN]", "#FFFF00"),
        ERROR("[ERROR]", "#FF0000"),
        DEBUG("[DEBUG]", "#00FFFF");
        
        private final String prefix;
        private final String color;
        
        LogLevel(String prefix, String color) {
            this.prefix = prefix;
            this.color = color;
        }
        
        public String getPrefix() { return prefix; }
        public String getColor() { return color; }
    }
    
    private static class LogEntry {
        private final LogLevel level;
        private final String message;
        private final long timestamp;
        private final String source;
        
        public LogEntry(LogLevel level, String message, String source) {
            this.level = level;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.source = source;
        }
        
        public LogLevel getLevel() { return level; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
        public String getSource() { return source; }
        
        public String getFormattedMessage() {
            String time = DATE_FORMAT.format(new Date(timestamp));
            return String.format("%s %s [%s] %s", time, level.getPrefix(), source, message);
        }
    }
    
    private Logger() {
        this.logQueue = new ConcurrentLinkedQueue<>();
        this.logExecutor = Executors.newSingleThreadExecutor();
        this.maxLogEntries = 1000;
        this.isRunning = true;
        initializeLogFile();
        startLogProcessor();
    }
    
    public static synchronized Logger getInstance() {
        if (instance == null) {
            instance = new Logger();
        }
        return instance;
    }
    
    private void initializeLogFile() {
        try {
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            String fileName = String.format("%s/mshlog_%s.log", LOG_DIR, FILE_DATE_FORMAT.format(new Date()));
            String charset = EncodingUtils.getOptimalCharset();
            fileWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName, true), charset)));
            
        } catch (IOException e) {
            System.err.println("Failed to initialize logger file writer: " + e.getMessage());
        }
    }
    
    private void startLogProcessor() {
        logExecutor.submit(() -> {
            while (isRunning) {
                try {
                    LogEntry entry = logQueue.poll();
                    if (entry != null) {
                        processLogEntry(entry);
                    } else {
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Unexpected error in log processor: " + e.getMessage());
                }
            }
        });
    }
    
    private void processLogEntry(LogEntry entry) {
        String formattedMessage = entry.getFormattedMessage();
        
        if (fileWriter != null) {
            fileWriter.println(formattedMessage);
            fileWriter.flush();
        }
        
        if (logTextArea != null) {
            SwingUtilities.invokeLater(() -> {
                appendToTextArea(entry);
            });
        }
    }
    
    private void appendToTextArea(LogEntry entry) {
        if (logTextArea != null) {
            String timestamp = DATE_FORMAT.format(new Date(entry.getTimestamp()));
            String logLine = String.format("%s %s [%s] %s\n", 
                timestamp, entry.getLevel().getPrefix(), entry.getSource(), entry.getMessage());
            
            logTextArea.append(logLine);
            
            int lineCount = logTextArea.getLineCount();
            if (lineCount > maxLogEntries) {
                try {
                    int linesToRemove = lineCount - maxLogEntries;
                    int endOffset = logTextArea.getLineStartOffset(linesToRemove);
                    logTextArea.getDocument().remove(0, endOffset);
                } catch (Exception e) {
                    logTextArea.setText("");
                }
            }
            
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        }
    }
    
    public void setLogTextArea(JTextArea textArea) {
        this.logTextArea = textArea;
    }
    
    public static void info(String message, String source) {
        getInstance().log(LogLevel.INFO, message, source);
    }
    
    public static void warn(String message, String source) {
        getInstance().log(LogLevel.WARN, message, source);
    }
    
    public static void error(String message, String source) {
        getInstance().log(LogLevel.ERROR, message, source);
    }
    
    public static void debug(String message, String source) {
        getInstance().log(LogLevel.DEBUG, message, source);
    }
    
    private void log(LogLevel level, String message, String source) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        
        LogEntry entry = new LogEntry(level, message.trim(), source);
        logQueue.offer(entry);
    }
    
    public void shutdown() {
        isRunning = false;
        logExecutor.shutdown();
        
        if (fileWriter != null) {
            fileWriter.close();
        }
    }
    
    public void clearLogDisplay() {
        if (logTextArea != null) {
            SwingUtilities.invokeLater(() -> {
                logTextArea.setText("");
            });
        }
    }
}