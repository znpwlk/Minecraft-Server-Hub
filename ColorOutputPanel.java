import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
public class ColorOutputPanel extends JScrollPane {
    private JTextPane textPane;
    private StyledDocument document;
    private SimpleAttributeSet normalAttr;
    public ColorOutputPanel() {
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textPane.setBackground(Color.BLACK);
        document = textPane.getStyledDocument();
        normalAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(normalAttr, Color.WHITE);
        StyleConstants.setBackground(normalAttr, Color.BLACK);
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("复制");
        copyItem.addActionListener(e -> {
            textPane.copy();
        });
        popupMenu.add(copyItem);
        JMenuItem selectAllItem = new JMenuItem("全选");
        selectAllItem.addActionListener(e -> {
            textPane.selectAll();
        });
        popupMenu.add(selectAllItem);
        JMenuItem clearItem = new JMenuItem("清空输出");
        clearItem.addActionListener(e -> {
            try {
                document.remove(0, document.getLength());
            } catch (BadLocationException ex) {
                ex.printStackTrace();
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
            document.insertString(document.getLength(), text + "\n", normalAttr);
            textPane.setCaretPosition(document.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
}
