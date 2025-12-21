import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class ColorOutputPanel extends JScrollPane {
    private JTextPane textPane;
    private StyledDocument document;
    private SimpleAttributeSet normalAttr;
    private SimpleAttributeSet currentAttr;
    public ColorOutputPanel() {
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textPane.setBackground(Color.BLACK);
        document = textPane.getStyledDocument();
        normalAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(normalAttr, Color.WHITE);
        StyleConstants.setBackground(normalAttr, Color.BLACK);
        currentAttr = new SimpleAttributeSet(normalAttr);
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("复制");
        copyItem.addActionListener(e -> textPane.copy());
        popupMenu.add(copyItem);
        JMenuItem selectAllItem = new JMenuItem("全选");
        selectAllItem.addActionListener(e -> textPane.selectAll());
        popupMenu.add(selectAllItem);
        JMenuItem clearItem = new JMenuItem("清空输出");
        clearItem.addActionListener(e -> {
            try {
                document.remove(0, document.getLength());
            } catch (BadLocationException ex) {
                Logger.error("Failed to clear output panel: " + ex.getMessage(), "ColorOutputPanel");
            }
        });
        popupMenu.add(clearItem);
        JMenuItem fontItem = new JMenuItem("调整字体大小");
        fontItem.addActionListener(e -> {
            Font currentFont = textPane.getFont();
            String[] fontSizes = {"10", "12", "14", "16", "18", "20"};
            String selectedSize = (String) JOptionPane.showInputDialog(
                this, "选择字体大小:", "调整字体",
                JOptionPane.PLAIN_MESSAGE, null, fontSizes,
                String.valueOf(currentFont.getSize()));
            if (selectedSize != null) {
                int size = Integer.parseInt(selectedSize);
                textPane.setFont(new Font("Monospaced", Font.PLAIN, size));
            }
        });
        popupMenu.add(fontItem);
        textPane.setComponentPopupMenu(popupMenu);
        setViewportView(textPane);
        setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }
    public void append(String text) {
        appendColorText(text);
    }
    public void appendColorText(String text) {
        try {
            Pattern pattern = Pattern.compile("\u001B\\[(\\d+(?:;\\d+)*)m");
            Matcher matcher = pattern.matcher(text);
            int lastIndex = 0;
            while (matcher.find()) {
                String plainText = text.substring(lastIndex, matcher.start());
                if (!plainText.isEmpty()) {
                    document.insertString(document.getLength(), plainText, currentAttr);
                }
                String colorCode = matcher.group(1);
                if (colorCode != null && !colorCode.trim().isEmpty()) {
                    applyColorCode(colorCode.trim());
                }
                lastIndex = matcher.end();
            }
            String remainingText = text.substring(lastIndex);
            if (!remainingText.isEmpty()) {
                document.insertString(document.getLength(), remainingText, currentAttr);
            }
            textPane.setCaretPosition(document.getLength());
        } catch (BadLocationException e) {
            Logger.error("Failed to append color text: " + e.getMessage(), "ColorOutputPanel");
        }
    }
    private void applyColorCode(String colorCode) {
        currentAttr = new SimpleAttributeSet(normalAttr);
        String[] codes = colorCode.split(";");
        for (String code : codes) {
            switch (code) {
                case "0":
                    currentAttr = new SimpleAttributeSet(normalAttr);
                    break;
                case "30":
                    StyleConstants.setForeground(currentAttr, Color.BLACK);
                    break;
                case "31":
                    StyleConstants.setForeground(currentAttr, Color.RED);
                    break;
                case "32":
                    StyleConstants.setForeground(currentAttr, Color.GREEN);
                    break;
                case "33":
                    StyleConstants.setForeground(currentAttr, Color.YELLOW);
                    break;
                case "34":
                    StyleConstants.setForeground(currentAttr, Color.BLUE);
                    break;
                case "35":
                    StyleConstants.setForeground(currentAttr, Color.MAGENTA);
                    break;
                case "36":
                    StyleConstants.setForeground(currentAttr, Color.CYAN);
                    break;
                case "37":
                    StyleConstants.setForeground(currentAttr, Color.WHITE);
                    break;
                case "90":
                    StyleConstants.setForeground(currentAttr, Color.GRAY);
                    break;
                case "91":
                    StyleConstants.setForeground(currentAttr, new Color(255, 85, 85));
                    break;
                case "92":
                    StyleConstants.setForeground(currentAttr, new Color(85, 255, 85));
                    break;
                case "93":
                    StyleConstants.setForeground(currentAttr, new Color(255, 255, 85));
                    break;
                case "94":
                    StyleConstants.setForeground(currentAttr, new Color(85, 85, 255));
                    break;
                case "95":
                    StyleConstants.setForeground(currentAttr, new Color(255, 85, 255));
                    break;
                case "96":
                    StyleConstants.setForeground(currentAttr, new Color(85, 255, 255));
                    break;
                case "97":
                    StyleConstants.setForeground(currentAttr, new Color(255, 255, 255));
                    break;
                case "40":
                    StyleConstants.setBackground(currentAttr, Color.BLACK);
                    break;
                case "41":
                    StyleConstants.setBackground(currentAttr, Color.RED);
                    break;
                case "42":
                    StyleConstants.setBackground(currentAttr, Color.GREEN);
                    break;
                case "43":
                    StyleConstants.setBackground(currentAttr, Color.YELLOW);
                    break;
                case "44":
                    StyleConstants.setBackground(currentAttr, Color.BLUE);
                    break;
                case "45":
                    StyleConstants.setBackground(currentAttr, Color.MAGENTA);
                    break;
                case "46":
                    StyleConstants.setBackground(currentAttr, Color.CYAN);
                    break;
                case "47":
                    StyleConstants.setBackground(currentAttr, Color.WHITE);
                    break;
            }
        }
    }
}
