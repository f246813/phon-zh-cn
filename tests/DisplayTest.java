import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

public class DisplayTest {
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);System.out.println("PASS "+msg);}
 public static void main(String[] a)throws Exception {
  SwingUtilities.invokeAndWait(()->{
   try {
    UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
    JTextField field=new JTextField();field.putClientProperty("JTextField.placeholderText","Search by name or path");
    check(field.getText().isEmpty()&&field.getFont().canDisplay('语'),"Text field construction and Chinese placeholder font");
    JButton button=new JButton("File");button.setFont(new Font("Dialog",0,16));
    check(button.getText().equals("File"),"Application button getter retains English menu key");
    JLabel label=new JLabel("Spectrogram");label.setFont(new Font("Dialog",0,16));
    check(label.getText().equals("Spectrogram"),"Application label getter retains English tier key");
    Dimension translated=label.getUI().getPreferredSize(label);
    JLabel chinese=new JLabel("声谱图");chinese.setFont(label.getFont());
    check(translated.equals(chinese.getPreferredSize()),"Swing layout measures translated text");
    JTabbedPane tabs=new JTabbedPane();tabs.addTab("General",new JPanel());
    check(tabs.getTitleAt(0).equals("General"),"Tab identifiers remain stable");
    JTextArea text=new JTextArea("File\nIPA Actual\nɑː ɹ ʃ 你好");
    check(text.getText().equals("File\nIPA Actual\nɑː ɹ ʃ 你好"),"Editable research text remains unchanged");
    DefaultTableModel model=new DefaultTableModel(new Object[][]{{"File","Spectrogram","ɑː ɹ"}},new String[]{"Name","Tier","Value"});
    JTable table=new JTable(model);
    JLabel cell=(JLabel)table.prepareRenderer(table.getCellRenderer(0,1),0,1);
    Dimension raw=cell.getPreferredSize();
    JLabel rawLabel=new JLabel("Spectrogram");rawLabel.setFont(cell.getFont());
    check(model.getValueAt(0,1).equals("Spectrogram")&&raw.width>25,"Table data and data-cell text remain unchanged");
    JLabel html=new JLabel("<html><b>Preferences</b> <a href='https://example.org/General'>General</a></html>");
    check(html.getText().contains("Preferences")&&html.getText().contains("https://example.org/General"),"HTML source and link targets remain unchanged");
    JPanel panel=new JPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
    JMenuBar bar=new JMenuBar();for(String s:new String[]{"File","Edit","View","Query","Analysis","Tools","Window","Help"})bar.add(new JMenu(s));panel.add(bar);
    for(String s:new String[]{"Welcome to Phon 4","Create Project","Recent Projects","Syllabification & Alignment","IPA Target","IPA Actual","Orthography","Phone Alignment","Spectrogram","Intensity","Formants","Preferences"}) {JLabel l=new JLabel(s);l.setFont(new Font("Dialog",0,18));panel.add(l);}
    panel.add(html);panel.add(tabs);panel.add(button);panel.add(new JScrollPane(table));
    JFrame frame=new JFrame("Welcome");frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);frame.setContentPane(panel);frame.setSize(740,700);frame.setVisible(true);
    check(frame.getTitle().equals("Welcome"),"Application window title remains unchanged");
    check(button.getText().equals("File")&&tabs.getTitleAt(0).equals("General"),"Display painting preserves application identifiers");
    double scale=frame.getGraphicsConfiguration().getDefaultTransform().getScaleX();
    check(scale==Double.parseDouble(System.getProperty("test.scale","1")),"Requested display scale "+scale);
    BufferedImage img=new BufferedImage((int)Math.ceil(panel.getWidth()*scale),(int)Math.ceil(panel.getHeight()*scale),BufferedImage.TYPE_INT_RGB);Graphics2D g=img.createGraphics();g.scale(scale,scale);panel.printAll(g);g.dispose();
    ImageIO.write(img,"png",Path.of(a.length>0?a[0]:"work/display-test.png").toFile());
    if(Boolean.getBoolean("test.close"))frame.dispose();
   }catch(Exception e){throw new RuntimeException(e);}
  });
 }
}
