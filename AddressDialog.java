import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.Component;
import java.awt.datatransfer.StringSelection;
import javax.swing.Box;
import java.io.*;
import java.util.List;
import java.util.Properties;

public class AddressDialog {
    private JDialog dialog;
    private JPanel addressPanel;
    private JLabel statusLabel;
    private JarRunner jarRunner;
    private JScrollPane mainScrollPane;
    
    public AddressDialog(JFrame parent, JarRunner jarRunner) {
        this.jarRunner = jarRunner;
        createDialog(parent);
    }
    
    private void resetScrollPosition() {
        if (mainScrollPane != null) {
            mainScrollPane.getVerticalScrollBar().setValue(0);
            mainScrollPane.getHorizontalScrollBar().setValue(0);
            
            SwingUtilities.invokeLater(() -> {
                if (mainScrollPane != null) {
                    mainScrollPane.getVerticalScrollBar().setValue(0);
                    mainScrollPane.getHorizontalScrollBar().setValue(0);
                    mainScrollPane.getViewport().setViewPosition(new Point(0, 0));
                }
            });
        }
    }
    
    private void createDialog(JFrame parent) {
        dialog = new JDialog(parent, "服务器地址", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(600, 400);
        dialog.setLocationRelativeTo(parent);
        dialog.setResizable(true);
        
        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("服务器地址信息");
        titleLabel.setFont(new Font(null, Font.BOLD, 16));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        topPanel.add(titleLabel, BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        JPanel contentPanel = new JPanel(new BorderLayout(8, 8));
        addressPanel = new JPanel();
        addressPanel.setLayout(new BoxLayout(addressPanel, BoxLayout.Y_AXIS));
        addressPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        
        statusLabel = new JLabel("正在获取地址信息...");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        contentPanel.add(statusLabel, BorderLayout.NORTH);
        
        mainScrollPane = new JScrollPane(addressPanel);
        contentPanel.add(mainScrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton refreshButton = new JButton("刷新");
        JButton closeButton = new JButton("关闭");
        
        refreshButton.addActionListener(e -> {
            Logger.info("User clicked refresh button", "AddressDialog");
            addressPanel.removeAll();
            statusLabel.setText("正在获取地址信息...");
            dialog.revalidate();
            dialog.repaint();
            resetScrollPosition();
            loadNetworkAddresses();
        });
        
        closeButton.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);
        
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel);
    }
    
    private void loadNetworkAddresses() {
        Logger.info("Starting to load network addresses", "AddressDialog");
        int actualPort = getServerPort();
        Logger.debug("Server port determined: " + actualPort, "AddressDialog");
        
        List<NetworkUtils.NetworkAddress> localAddresses = NetworkUtils.getServerAddresses(actualPort);
        Logger.debug("Local addresses found: " + localAddresses.size(), "AddressDialog");
        
        for (NetworkUtils.NetworkAddress address : localAddresses) {
            JPanel addressItemPanel = new JPanel(new BorderLayout(8, 3));
            addressItemPanel.setBorder(BorderFactory.createEtchedBorder());
            
            JPanel infoPanel = new JPanel(new GridLayout(0, 1));
            infoPanel.setBackground(new Color(240, 255, 240));
            
            JLabel typeLabel = new JLabel(address.type);
            typeLabel.setFont(new Font(null, Font.BOLD, 11));
            typeLabel.setForeground(new Color(0, 128, 0));
            
            JLabel addressLabel = new JLabel(address.address);
            addressLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
            
            JButton copyButton = new JButton("复制");
            copyButton.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(address.address), null);
                JOptionPane.showMessageDialog(addressPanel, "地址已复制到剪贴板！", "提示", JOptionPane.INFORMATION_MESSAGE);
            });
            
            infoPanel.add(typeLabel);
            infoPanel.add(addressLabel);
            
            addressItemPanel.add(infoPanel, BorderLayout.CENTER);
            addressItemPanel.add(copyButton, BorderLayout.EAST);
            
            addressItemPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            addressPanel.add(addressItemPanel);
            addressPanel.add(Box.createVerticalStrut(8));
        }
        
        NetworkUtils.getPublicIP().thenAccept(publicIP -> {
            SwingUtilities.invokeLater(() -> {
                boolean hasPublicIP = !publicIP.contains("失败") && !publicIP.contains("无法获取") && !publicIP.equals("获取失败");
                
                if (hasPublicIP) {
                    JPanel publicAddressHiddenPanel = new JPanel(new BorderLayout(8, 3));
                    publicAddressHiddenPanel.setBorder(BorderFactory.createEtchedBorder());
                    publicAddressHiddenPanel.setBackground(new Color(255, 245, 245));
                    
                    JPanel publicInfoPanel = new JPanel(new GridLayout(0, 1));
                    publicInfoPanel.setBackground(new Color(255, 245, 245));
                    
                    JLabel publicTypeLabel = new JLabel("🌍 公网地址（已隐藏）");
                    publicTypeLabel.setFont(new Font(null, Font.BOLD, 12));
                    publicTypeLabel.setForeground(new Color(178, 34, 34));
                    
                    JLabel publicAddressLabel = new JLabel("点击下方按钮并同意免责声明后显示");
                    publicAddressLabel.setFont(new Font(null, Font.ITALIC, 10));
                    publicAddressLabel.setForeground(new Color(139, 69, 19));
                    
                    JButton showPublicButton = new JButton("显示公网地址（需同意免责声明）");
                    showPublicButton.setBackground(new Color(220, 20, 60));
                    showPublicButton.setForeground(Color.WHITE);
                    showPublicButton.setFont(new Font(null, Font.BOLD, 10));
                    
                    showPublicButton.addActionListener(e -> {
                        showDisclaimerDialog(publicIP, actualPort);
                    });
                    
                    publicInfoPanel.add(publicTypeLabel);
                    publicInfoPanel.add(publicAddressLabel);
                    
                    publicAddressHiddenPanel.add(publicInfoPanel, BorderLayout.CENTER);
                    publicAddressHiddenPanel.add(showPublicButton, BorderLayout.EAST);
                    
                    publicAddressHiddenPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    addressPanel.add(publicAddressHiddenPanel);
                    addressPanel.add(Box.createVerticalStrut(8));
                }
                
                statusLabel.setText("地址获取完成");
                statusLabel.setForeground(new Color(0, 128, 0));
                
                JPanel notePanel = new JPanel(new BorderLayout());
                notePanel.setBorder(new EmptyBorder(8, 8, 8, 8));
                
                JTextArea noteArea = new JTextArea();
                noteArea.setEditable(false);
                noteArea.setWrapStyleWord(true);
                noteArea.setLineWrap(true);
                noteArea.setBackground(Color.WHITE);
                
                String noteText = "连接说明：\n\n";
                noteText += "📍 本地回环地址\n" +
                    "   只能在自己这台电脑上连接\n" +
                    "   适合测试服务器或单机游戏\n\n" +
                    "🌐 内网地址\n" +
                    "   允许同一WiFi网络内的其他设备连接\n" +
                    "   家人朋友可以用手机/电脑连接\n\n";
                
                if (hasPublicIP) {
                    noteText += "🌍 公网地址（高风险，默认隐藏）\n" +
                        "   允许互联网上任何地方的人连接\n" +
                        "   需要在路由器中设置端口映射\n" +
                        "   默认隐藏，需要同意免责声明才能显示\n" +
                        "   建议只分享给信任的朋友\n\n";
                }
                
                noteText += "🔧 端口开放设置（重要）：\n" +
                    "   1. 路由器端口映射设置：\n" +
                    "      登录路由器管理页面（通常是192.168.1.1或192.168.0.1）\n" +
                    "      找到\"端口映射\"、\"端口转发\"或\"虚拟服务器\"设置\n" +
                    "      将" + actualPort + "端口转发到你的电脑内网IP\n" +
                    "      协议选择：TCP（部分路由器需要UDP）\n" +
                    "      保存设置并重启路由器\n" +
                    "   2. 防火墙放行端口：\n" +
                    "      打开Windows\"控制面板 > 系统和安全 > Windows Defender防火墙\"\n" +
                    "      点击\"允许应用或功能通过Windows Defender防火墙\"\n" +
                    "      找到Java程序，允许其通过防火墙\n" +
                    "      或手动添加" + actualPort + "端口到例外列表\n" +
                    "      关闭不必要的安全软件干扰\n\n" +
                    "🔴🔴🔴 极危险警告 - 公网连接风险：\n" +
                    "   服务器将直接暴露在互联网上，任何人都能访问\n" +
                    "   黑客可能扫描并发现你的服务器\n" +
                    "   可能遭受DDoS攻击，瞬间流量激增\n" +
                    "   恶意用户可能尝试破解服务器或植入病毒\n" +
                    "   你的个人电脑面临严重安全威胁\n" +
                    "   可能导致个人信息泄露或电脑被控制\n\n" +
                    "⚠️⚠️⚠️ 必要安全措施（必须执行）：\n" +
                    "   立即修改服务器管理员密码为强密码\n" +
                    "   安装并配置服务器安全插件（如AuthMe）\n" +
                    "   定期更新服务器版本和所有插件\n" +
                    "   监控服务器日志，查看异常登录活动\n" +
                    "   定期备份服务器存档和数据\n" +
                    "   不要在公共网络环境开放服务器\n" +
                    "   考虑使用VPN或代理服务器";
                
                noteArea.setText(noteText);
                
                notePanel.add(new JLabel("连接说明:"), BorderLayout.NORTH);
                notePanel.add(noteArea, BorderLayout.CENTER);
                
                notePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                addressPanel.add(notePanel);
                
                addressPanel.revalidate();
                addressPanel.repaint();
                
                resetScrollPosition();
            });
        });
    }
    
    private int getServerPort() {
        if (jarRunner == null) {
            return 25565;
        }
        
        try {
            File jarFile = new File(jarRunner.getJarPath());
            File serverDir = jarFile.getParentFile();
            if (serverDir == null) {
                return 25565;
            }
            
            File serverProperties = new File(serverDir, "server.properties");
            if (serverProperties.exists()) {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(serverProperties)) {
                    props.load(fis);
                    String portStr = props.getProperty("server-port", "25565");
                    return Integer.parseInt(portStr.trim());
                }
            }
        } catch (Exception e) {
            Logger.warn("Failed to read server port from server.properties: " + e.getMessage(), "AddressDialog");
        }
        
        return 25565;
    }
    
    public void show() {
        loadNetworkAddresses();
        dialog.setVisible(true);
        resetScrollPosition();
    }
    
    private void showDisclaimerDialog(String publicIP, int port) {
        JDialog disclaimerDialog = new JDialog(dialog, "公网地址安全警告", true);
        disclaimerDialog.setLayout(new BorderLayout(10, 10));
        disclaimerDialog.setSize(500, 400);
        disclaimerDialog.setLocationRelativeTo(dialog);
        
        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        
        JPanel warningPanel = new JPanel(new BorderLayout());
        JLabel warningTitle = new JLabel("🔴 极危险警告");
        warningTitle.setFont(new Font(null, Font.BOLD, 16));
        warningTitle.setForeground(new Color(178, 34, 34));
        warningTitle.setHorizontalAlignment(SwingConstants.CENTER);
        warningPanel.add(warningTitle, BorderLayout.NORTH);
        
        JTextArea disclaimerText = new JTextArea();
        disclaimerText.setEditable(false);
        disclaimerText.setWrapStyleWord(true);
        disclaimerText.setLineWrap(true);
        disclaimerText.setBackground(new Color(255, 245, 245));
        disclaimerText.setForeground(new Color(139, 69, 19));
        disclaimerText.setFont(new Font(null, Font.PLAIN, 12));
        
        String warningText = "在显示公网地址之前，请务必了解以下风险：\n\n" +
            "⚠️  安全风险：\n" +
            "   服务器将暴露在互联网上，任何人都可能访问\n" +
            "   可能遭受黑客攻击、恶意入侵或数据窃取\n" +
            "   你的个人电脑面临严重安全威胁\n" +
            "   可能导致个人隐私泄露或电脑被远程控制\n\n" +
            "⚠️  网络风险：\n" +
            "   可能遭受DDoS攻击，导致网络瘫痪\n" +
            "   流量激增可能产生高额网络费用\n" +
            "   路由器可能被恶意配置或攻击\n\n" +
            "⚠️  法律风险：\n" +
            "   你需要为服务器的所有活动负责\n" +
            "   如果服务器被用于非法活动，你可能承担法律责任\n" +
            "   需要确保服务器使用符合当地法律法规\n\n" +
            "⚠️  责任声明：\n" +
            "   开发者不对任何因开放公网访问造成的损失承担责任\n" +
            "   请确保你已经采取了必要的安全措施\n" +
            "   你需要定期监控和维护服务器安全\n\n" +
            "⚠️  使用建议：\n" +
            "   仅在信任的网络环境中开放服务器\n" +
            "   立即修改所有默认密码\n" +
            "   安装最新的安全补丁和插件\n" +
            "   定期备份重要数据\n\n" +
            "如果你完全理解并同意承担以上所有风险，请点击\"我同意\"。\n" +
            "如果你有任何疑虑，请点击\"取消\"关闭此对话框。";
        
        disclaimerText.setText(warningText);
        
        JScrollPane scrollPane = new JScrollPane(disclaimerText);
        scrollPane.getVerticalScrollBar().setValue(0);
        scrollPane.getHorizontalScrollBar().setValue(0);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        SwingUtilities.invokeLater(() -> {
            scrollPane.getVerticalScrollBar().setValue(0);
            scrollPane.getHorizontalScrollBar().setValue(0);
        });
        
        warningPanel.add(scrollPane, BorderLayout.CENTER);
        
        JCheckBox agreeCheckBox = new JCheckBox("我已阅读并完全理解上述所有风险和警告，自愿承担所有责任");
        agreeCheckBox.setFont(new Font(null, Font.PLAIN, 11));
        agreeCheckBox.setForeground(new Color(139, 69, 19));
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton agreeButton = new JButton("我同意");
        JButton cancelButton = new JButton("取消");
        
        agreeButton.setEnabled(false);
        agreeButton.setBackground(new Color(220, 20, 60));
        agreeButton.setForeground(Color.WHITE);
        
        cancelButton.setBackground(Color.LIGHT_GRAY);
        
        agreeCheckBox.addActionListener(e -> {
            agreeButton.setEnabled(agreeCheckBox.isSelected());
        });
        
        agreeButton.addActionListener(e -> {
            try {
                Logger.info("User clicked 'I agree' button in disclaimer dialog", "AddressDialog");
                Logger.debug("Proceeding to show public address after disclaimer agreement", "AddressDialog");
                
                JPanel publicAddressPanel = new JPanel(new BorderLayout(8, 3));
                publicAddressPanel.setBorder(BorderFactory.createEtchedBorder());
                
                JPanel publicInfoPanel = new JPanel(new GridLayout(0, 1));
                publicInfoPanel.setBackground(new Color(240, 255, 240));
                
                JLabel publicTypeLabel = new JLabel("🌍 公网地址（已显示）");
                publicTypeLabel.setFont(new Font(null, Font.BOLD, 12));
                publicTypeLabel.setForeground(new Color(0, 128, 0));
                
                String publicAddressWithPort;
                if (publicIP.contains(":")) {
                    publicAddressWithPort = publicIP.replace(":25565", ":" + port);
                } else {
                    publicAddressWithPort = publicIP + ":" + port;
                }
                
                JLabel publicAddressLabel = new JLabel(publicAddressWithPort);
                publicAddressLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
                
                JButton publicCopyButton = new JButton("复制");
                publicCopyButton.addActionListener(actionEvent -> {
                    try {
                        Logger.info("User clicked copy button for public address", "AddressDialog");
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(publicAddressWithPort), null);
                        JOptionPane.showMessageDialog(disclaimerDialog, "公网地址已复制到剪贴板！", "提示", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        Logger.error("Error copying public address to clipboard: " + ex.getMessage(), "AddressDialog");
                        JOptionPane.showMessageDialog(disclaimerDialog, "复制失败，请重试", "错误", JOptionPane.ERROR_MESSAGE);
                    }
                });
                
                publicInfoPanel.add(publicTypeLabel);
                publicInfoPanel.add(publicAddressLabel);
                
                publicAddressPanel.add(publicInfoPanel, BorderLayout.CENTER);
                publicAddressPanel.add(publicCopyButton, BorderLayout.EAST);
                
                publicAddressPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                
                disclaimerDialog.getContentPane().add(publicAddressPanel, BorderLayout.SOUTH);
                disclaimerDialog.revalidate();
                disclaimerDialog.repaint();
                
                Logger.info("Public address panel successfully added to disclaimer dialog", "AddressDialog");
            } catch (Exception ex) {
                Logger.error("Error occurred when user clicked agree button: " + ex.getMessage(), "AddressDialog");
                Logger.error("Stack trace: " + ex.toString(), "AddressDialog");
                JOptionPane.showMessageDialog(disclaimerDialog, 
                    "Error occurred when processing agreement. Please try again.", 
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        cancelButton.addActionListener(e -> {
            disclaimerDialog.dispose();
        });
        
        buttonPanel.add(agreeButton);
        buttonPanel.add(cancelButton);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(agreeCheckBox, BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        mainPanel.add(warningPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        disclaimerDialog.add(mainPanel);
        disclaimerDialog.setVisible(true);
    }
}