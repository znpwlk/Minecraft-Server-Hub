import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NetworkUtils {
    public static class NetworkAddress {
        public String type;
        public String address;
        public boolean isReachable;
        
        public NetworkAddress(String type, String address, boolean isReachable) {
            this.type = type;
            this.address = address;
            this.isReachable = isReachable;
        }
    }
    
    public static List<NetworkAddress> getServerAddresses(int port) {
        List<NetworkAddress> addresses = new ArrayList<>();
        
        addresses.add(new NetworkAddress("本地回环", "127.0.0.1:" + port, true));
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                
                if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    
                    if (inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress() || 
                        inetAddress.isMulticastAddress()) {
                        continue;
                    }
                    
                    String ip = inetAddress.getHostAddress();
                    if (ip != null && !ip.isEmpty() && !ip.contains(":")) {
                        addresses.add(new NetworkAddress("内网地址", ip + ":" + port, true));
                        Logger.info("Found internal IP: " + ip + ":" + port, "NetworkUtils");
                        return addresses;
                    }
                }
            }
        } catch (SocketException e) {
            Logger.error("Failed to get network interfaces: " + e.getMessage(), "NetworkUtils");
            Logger.error("ERROR: Network interface enumeration failed - no network interfaces accessible", "NetworkUtils");
        }
        
        Logger.warn("No internal IP found, only returning loopback address", "NetworkUtils");
        return addresses;
    }
    
    public static List<NetworkAddress> getServerAddresses() {
        return getServerAddresses(25565);
    }
    
    public static CompletableFuture<String> getPublicIP() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String[] services = {
                    "https://api.ipify.org",
                    "https://icanhazip.com",
                    "https://ifconfig.me/ip",
                    "https://checkip.amazonaws.com"
                };
                
                for (String service : services) {
                    try {
                        URL url = new URI(service).toURL();
                        URLConnection conn = url.openConnection();
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        conn.setRequestProperty("User-Agent", "Minecraft-Server-Hub/1.0");
                        
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                            String ip = reader.readLine().trim();
                            if (ip.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
                                return ip + ":25565";
                            }
                        }
                    } catch (Exception e) {
                        Logger.warn("Failed to get public IP from " + service + ": " + e.getMessage(), "NetworkUtils");
                        Logger.warn("WARN: Public IP service unavailable - " + service + " failed", "NetworkUtils");
                    }
                }
                return "无法获取公网IP";
            } catch (Exception e) {
                Logger.error("Failed to get public IP: " + e.getMessage(), "NetworkUtils");
                return "获取失败";
            }
        });
    }
    
    public static boolean isPortOpen(String host, int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 1000);
            return true;
        } catch (Exception e) {
            Logger.warn("Failed to check port " + port + " on " + host + ": " + e.getMessage(), "NetworkUtils");
            Logger.warn("WARN: Port connectivity test failed - " + host + ":" + port + " is not reachable", "NetworkUtils");
        }
        return false;
    }
}