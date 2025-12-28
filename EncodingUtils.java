import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.UnsupportedEncodingException;

public class EncodingUtils {
    private static final String DEFAULT_CHARSET = "UTF-8";
    private static String detectedCharset = null;
    private static String serverProcessCharset = null;
    
    public static String getOptimalCharset() {
        if (detectedCharset != null) {
            return detectedCharset;
        }
        
        String osName = System.getProperty("os.name", "").toLowerCase();
        String fileEncoding = System.getProperty("file.encoding", DEFAULT_CHARSET);
        String consoleEncoding = System.getProperty("console.encoding", fileEncoding);
        
        detectedCharset = determineBestCharset(osName, fileEncoding, consoleEncoding);
        
        return detectedCharset;
    }
    
    public static String getServerProcessCharset() {
        if (serverProcessCharset != null) {
            return serverProcessCharset;
        }
        
        String osName = System.getProperty("os.name", "").toLowerCase();
        
        if (osName.contains("windows")) {
            serverProcessCharset = "GBK";
        } else {
            serverProcessCharset = "UTF-8";
        }
        
        return serverProcessCharset;
    }
    
    private static String determineBestCharset(String osName, String fileEncoding, String consoleEncoding) {
        if (osName.contains("windows")) {
            return handleWindowsEncoding(fileEncoding, consoleEncoding);
        } else if (osName.contains("mac")) {
            return handleMacEncoding();
        } else if (osName.contains("linux")) {
            return handleLinuxEncoding(fileEncoding);
        }
        
        return DEFAULT_CHARSET;
    }
    
    private static String handleWindowsEncoding(String fileEncoding, String consoleEncoding) {
        String encoding = fileEncoding.toUpperCase();
        
        if (encoding.contains("UTF-8") || encoding.contains("UTF8")) {
            return DEFAULT_CHARSET;
        }
        
        if (encoding.contains("GB") || encoding.contains("GBK") || encoding.contains("BIG5")) {
            return DEFAULT_CHARSET;
        }
        
        if (consoleEncoding != null && !consoleEncoding.equals(fileEncoding)) {
            String consoleEnc = consoleEncoding.toUpperCase();
            if (consoleEnc.contains("UTF") || consoleEnc.contains("GB")) {
                return DEFAULT_CHARSET;
            }
        }
        
        return DEFAULT_CHARSET;
    }
    
    private static String handleMacEncoding() {
        return DEFAULT_CHARSET;
    }
    
    private static String handleLinuxEncoding(String fileEncoding) {
        String encoding = fileEncoding.toUpperCase();
        
        if (encoding.contains("UTF") || encoding.contains("GB")) {
            return DEFAULT_CHARSET;
        }
        
        return DEFAULT_CHARSET;
    }
    
    public static byte[] convertToBytes(String text, String charset) {
        if (charset == null || charset.trim().isEmpty()) {
            charset = DEFAULT_CHARSET;
        }
        
        try {
            return text.getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            Logger.error("Unsupported charset: " + charset + ", falling back to UTF-8: " + e.getMessage(), "EncodingUtils");
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }
    
    public static String convertFromBytes(byte[] bytes, String charset) {
        if (charset == null || charset.trim().isEmpty()) {
            charset = DEFAULT_CHARSET;
        }
        
        try {
            return new String(bytes, charset);
        } catch (UnsupportedEncodingException e) {
            Logger.error("Unsupported charset: " + charset + ", falling back to UTF-8: " + e.getMessage(), "EncodingUtils");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
    
    public static Charset getCharsetForName(String charsetName) {
        if (charsetName == null || charsetName.trim().isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        
        try {
            return Charset.forName(charsetName);
        } catch (Exception e) {
            Logger.error("Invalid charset: " + charsetName + ", falling back to UTF-8: " + e.getMessage(), "EncodingUtils");
            return StandardCharsets.UTF_8;
        }
    }
    
    public static boolean isCharsetSupported(String charset) {
        if (charset == null || charset.trim().isEmpty()) {
            return false;
        }
        
        try {
            Charset.forName(charset);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public static String sanitizeCharsetName(String charset) {
        if (charset == null) {
            return DEFAULT_CHARSET;
        }
        
        String sanitized = charset.trim().toUpperCase();
        
        if (sanitized.equals("UTF8") || sanitized.equals("UTF-8")) {
            return DEFAULT_CHARSET;
        }
        
        if (sanitized.equals("GBK") || sanitized.equals("GB2312") || sanitized.equals("GB18030")) {
            return DEFAULT_CHARSET;
        }
        
        if (sanitized.equals("BIG5") || sanitized.equals("BIG5-HKSCS")) {
            return DEFAULT_CHARSET;
        }
        
        return DEFAULT_CHARSET;
    }
}