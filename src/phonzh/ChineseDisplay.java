package phonzh;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableCellRenderer;

/** Display translations only. Application getters and editable documents retain original data. */
public final class ChineseDisplay {
 private static final Map<String,String> WORDS = new HashMap<>();
 private static final Map<String,String> CACHE = new ConcurrentHashMap<>();
 private static final Map<Font,Font> FONTS = new ConcurrentHashMap<>();
 private static final Set<String> UNKNOWN = ConcurrentHashMap.newKeySet();
 private static final StackWalker WALKER = StackWalker.getInstance();
 private static final Pattern HTML = Pattern.compile("(<[^>]+>)|([^<]+)");
 static {
  try (var in=Files.newInputStream(Path.of(System.getProperty("phon.zh.dictionary")))) {
   if(in==null) throw new IOException("Missing zh_CN.tsv");
   try(var r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
    for(String line;(line=r.readLine())!=null;) {
     if(line.isBlank()||line.startsWith("#")) continue;
     String[] kv=line.split("\t",2);
     if(kv.length!=2) throw new IOException("Invalid dictionary line: "+line);
     WORDS.put(kv[0],kv[1]);
     WORDS.putIfAbsent(kv[0].toLowerCase(Locale.ROOT),kv[1]);
    }
   }
  } catch(IOException e) { throw new ExceptionInInitializerError(e); }
 }
 private ChineseDisplay() {}
 private static boolean displayCaller() {
  return WALKER.walk(s->s.filter(f-> {
   String c=f.getClassName(), m=f.getMethodName();
   return !c.startsWith("phonzh.") && !(c.startsWith("org.jdesktop.swingx.")&&m.equals("getText")) && !((c.equals("javax.swing.JLabel")||c.equals("javax.swing.AbstractButton")||c.equals("javax.swing.JTabbedPane")||c.equals("javax.swing.JComponent")||c.equals("javax.swing.JToolTip")||c.equals("javax.swing.border.TitledBorder")||c.startsWith("ca.phon.ui.nativedialogs.")&&!c.contains(".spi.")||c.equals("java.awt.Component")||c.equals("java.awt.Frame")||c.equals("java.awt.Dialog")) && m.startsWith("get"));
  }).findFirst().map(f->{
   String c=f.getClassName();
   String m=f.getMethodName();
   return m.startsWith("paint")||m.equals("getPreferredSize")||m.equals("getMinimumSize")||c.startsWith("javax.swing.plaf.")||c.startsWith("org.jdesktop.swingx.plaf.")||c.startsWith("com.formdev.flatlaf.")||c.startsWith("sun.swing.")||c.startsWith("sun.awt.windows.")||c.startsWith("ca.phon.ui.nativedialogs.spi.")||c.startsWith("ca.phon.ui.nativedialogs.NativeDialogs$")||c.startsWith("javax.swing.border.")||c.contains("$Accessible")||c.equals("javax.swing.ToolTipManager")||c.equals("javax.swing.SwingUtilities")||c.startsWith("javax.swing.text.html.");
  }).orElse(false));
 }
 public static String display(String text,Object owner) {
  if(text==null||text.isEmpty()||!displayCaller()) return text;
  // Transcript/table cell values are research data, even if a word resembles a command.
  if(owner instanceof TableCellRenderer && !owner.getClass().getName().toLowerCase(Locale.ROOT).contains("header")) return text;
  String out=translate(text);
  if(out.equals(text)) audit(text,owner);
  return out;
 }
 public static Font font(Font original,Object owner) {
  if(original==null) return original;
  if(owner instanceof TableCellRenderer && !owner.getClass().getName().toLowerCase(Locale.ROOT).contains("header")) return original;
  String text=null;
  if(owner instanceof JLabel l) text=l.getText();
  else if(owner instanceof AbstractButton b) text=b.getText();
  else if(owner instanceof JTabbedPane) text="中文";
  else if(owner instanceof TitledBorder t)text=t.getTitle();
  else if(owner instanceof JTextField f) {if(f.getDocument()==null)return original;text=f.getText();if(text.isEmpty()&&f.getClientProperty("JTextField.placeholderText") instanceof String p)text=p;}
  if(text==null||!translate(text).codePoints().anyMatch(c->c>=0x3400&&c<=0x9fff)||original.getFamily().equals("Microsoft YaHei UI")) return original;
  return FONTS.computeIfAbsent(original,f->new Font("Microsoft YaHei UI",f.getStyle(),f.getSize()).deriveFont(f.getSize2D()));
 }
 public static Object clientProperty(Object value,Object key) {
  return "JTextField.placeholderText".equals(key)&&value instanceof String s?translate(s):value;
 }
 public static String[] options(String[] original) {
  if(original==null||!displayCaller())return original;
  String[] result=original.clone();for(int i=0;i<result.length;i++)result[i]=translate(result[i]);return result;
 }
 public static String translate(String s) {
  if(s==null||s.isEmpty()) return s;
  String old=CACHE.get(s);if(old!=null)return old;
  String out=translateUncached(s);if(!out.equals(s))CACHE.put(s,out);
  return out;
 }
 private static String translateUncached(String s) {
  String hit=WORDS.get(s);
  if(hit!=null) return hit;
  if(s.stripLeading().toLowerCase(Locale.ROOT).startsWith("<html")) {
   Matcher m=HTML.matcher(s);StringBuilder b=new StringBuilder();
   while(m.find()) b.append(m.group(1)!=null?m.group(1):plain(m.group(2)));
   return b.toString();
  }
  return plain(s);
 }
 private static String plain(String s) {
  String v=WORDS.get(s); if(v==null)v=WORDS.get(s.toLowerCase(Locale.ROOT)); if(v!=null)return v;
  String trim=s.strip();v=WORDS.get(trim);if(v==null)v=WORDS.get(trim.toLowerCase(Locale.ROOT));
  if(v!=null)return s.substring(0,s.indexOf(trim))+v+s.substring(s.indexOf(trim)+trim.length());
  // Punctuation variants are common across menu items and dialog captions.
  for(String suffix:new String[]{"...","…",":",": ","?"}) if(trim.endsWith(suffix)) {
   String stem=trim.substring(0,trim.length()-suffix.length()).stripTrailing();
   v=plain(stem);if(!v.equals(stem))return v+(suffix.equals("...")?"…":suffix.equals(":")||suffix.equals(": ")?"：":suffix.equals("?")?"？":suffix);
  }
  Matcher n=Pattern.compile("Welcome to Phon (.+)").matcher(trim);
  if(n.matches())return "欢迎使用 Phon "+n.group(1);
  n=Pattern.compile("of (\\d+)").matcher(trim);
  if(n.matches())return "共 "+n.group(1)+" 条";
  n=Pattern.compile("(\\d+) (errors?|warnings?)").matcher(trim);
  if(n.matches())return n.group(1)+(n.group(2).startsWith("error")?" 个错误":" 个警告");
  n=Pattern.compile("(Document position|Transcript element index|Record index|Character): (\\d+)").matcher(trim);
  if(n.matches())return WORDS.get(n.group(1).toLowerCase(Locale.ROOT))+"："+n.group(2);
  n=Pattern.compile("Set (volume|playback rate) to (\\d+%)").matcher(trim);
  if(n.matches())return (n.group(1).equals("volume")?"将音量设为 ":"将播放速率设为 ")+n.group(2);
  n=Pattern.compile("Every (\\d+) minutes").matcher(trim);
  if(n.matches())return "每 "+n.group(1)+" 分钟";
  n=Pattern.compile("(\\d+) (records?|sessions?|results?|groups?|words?|phones?)",Pattern.CASE_INSENSITIVE).matcher(trim);
  if(n.matches()) {
   String unit=switch(n.group(2).toLowerCase(Locale.ROOT).replaceAll("s$","")){case "record"->"条记录";case "session"->"个会话";case "result"->"项结果";case "group"->"组";case "word"->"个词";default->"个音素";};
   return n.group(1)+" "+unit;
  }
  // Translate known UI captions around dynamic counts, paths and project names.
  for(String sep:new String[]{" : ",": "," - "," — "," ("}) {
   int i=s.indexOf(sep);
   if(i>0) {
    String a=WORDS.get(s.substring(0,i)), b=WORDS.get(s.substring(i+sep.length()));
    if(a!=null||b!=null)return (a==null?s.substring(0,i):a)+sep+(b==null?s.substring(i+sep.length()):b);
   }
  }
  String[][] verbs={{"New ","新建"},{"Create ","创建"},{"Open ","打开"},{"Show ","显示"},{"Hide ","隐藏"},{"Edit ","编辑"},{"Delete ","删除"},{"Remove ","移除"},{"Add ","添加"},{"Reset ","重置"},{"Export ","导出"},{"Import ","导入"},{"Save ","保存"},{"Select ","选择"},{"Copy ","复制"},{"Paste ","粘贴"},{"Duplicate ","复制"},{"Clear ","清空"},{"Load ","加载"},{"Stop ","停止"},{"Run ","运行"},{"Validate ","校验"}};
  for(String[] pair:verbs)if(trim.regionMatches(true,0,pair[0],0,pair[0].length())) {
   String object=WORDS.get(trim.substring(pair[0].length()).toLowerCase(Locale.ROOT));
   if(object!=null)return pair[1]+object;
  }
  return s;
 }
 private static void audit(String s,Object owner) {
  String file=System.getProperty("phon.zh.audit");
  if(file==null||s.length()>600||!s.matches("(?s).*[A-Za-z]{3}.*")||s.contains(":\\")||!UNKNOWN.add(s))return;
  try {Files.writeString(Path.of(file),owner.getClass().getName()+"\t"+s.replace("\n","\\n")+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(IOException ignored){}
 }
}
