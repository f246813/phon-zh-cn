package phonzh.agent;

import java.lang.instrument.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.*;
import groovyjarjarasm.asm.*;

/** Instruments Swing and Apache native-dialogs presentation accessors, never Phon application/data classes. */
public final class ChineseAgent {
 private static final Set<String> TARGETS=Set.of("javax/swing/JLabel","javax/swing/AbstractButton","javax/swing/JTabbedPane","javax/swing/JComponent","javax/swing/JToolTip","javax/swing/border/TitledBorder","java/awt/Component","java/awt/Frame","java/awt/Dialog","ca/phon/ui/nativedialogs/NativeDialogProperties","ca/phon/ui/nativedialogs/MessageDialogProperties","ca/phon/ui/nativedialogs/SaveDialogProperties","ca/phon/ui/nativedialogs/FileFilter");
 public static void premain(String args,Instrumentation inst) throws Exception {start(inst);}
 private static void start(Instrumentation inst) throws Exception {
  Path base=Path.of(ChineseAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
  inst.appendToBootstrapClassLoaderSearch(new JarFile(base.resolve("phon-zh-display.jar").toFile()));
  System.setProperty("phon.zh.dictionary",base.resolve("resources/zh_CN.tsv").toString());
  Class<?> helper=Class.forName("phonzh.ChineseDisplay",true,null);
  inst.redefineModule(java.awt.Component.class.getModule(),Set.of(helper.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
  inst.addTransformer(new Transformer(),true);
  for(Class<?> c:inst.getAllLoadedClasses()) if(TARGETS.contains(c.getName().replace('.','/'))&&inst.isModifiableClass(c))inst.retransformClasses(c);
  System.err.println("[Phon zh-CN] display module loaded");
 }
 private static class Transformer implements ClassFileTransformer {
  @Override public byte[] transform(Module module,ClassLoader loader,String name,Class<?> cls,ProtectionDomain domain,byte[] bytes) {
   if(!TARGETS.contains(name))return null;
   ClassReader r=new ClassReader(bytes);ClassWriter w=new ClassWriter(r,ClassWriter.COMPUTE_MAXS);
   r.accept(new ClassVisitor(Opcodes.ASM9,w){
    @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions) {
     MethodVisitor parent=super.visitMethod(access,method,desc,signature,exceptions);
     boolean get=(name.equals("javax/swing/JLabel")||name.equals("javax/swing/AbstractButton"))&&method.equals("getText")&&desc.equals("()Ljava/lang/String;")
      ||name.equals("javax/swing/JTabbedPane")&&method.equals("getTitleAt")&&desc.equals("(I)Ljava/lang/String;")
      ||name.equals("javax/swing/JComponent")&&method.equals("getToolTipText")&&desc.equals("()Ljava/lang/String;")
      ||name.equals("javax/swing/JToolTip")&&method.equals("getTipText")&&desc.equals("()Ljava/lang/String;")
      ||name.equals("javax/swing/border/TitledBorder")&&method.equals("getTitle")&&desc.equals("()Ljava/lang/String;")
      ||(name.equals("java/awt/Frame")||name.equals("java/awt/Dialog"))&&method.equals("getTitle")&&desc.equals("()Ljava/lang/String;")
      ||name.startsWith("ca/phon/ui/nativedialogs/")&&Set.of("getTitle","getMessage","getDescription").contains(method)&&desc.equals("()Ljava/lang/String;");
     boolean font=(name.equals("java/awt/Component")&&method.equals("getFont")||name.equals("javax/swing/border/TitledBorder")&&method.equals("getTitleFont"))&&desc.equals("()Ljava/awt/Font;");
     boolean property=name.equals("javax/swing/JComponent")&&method.equals("getClientProperty")&&desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;");
     boolean options=name.equals("ca/phon/ui/nativedialogs/MessageDialogProperties")&&method.equals("getOptions")&&desc.equals("()[Ljava/lang/String;");
     if(!get&&!font&&!property&&!options)return parent;
     return new MethodVisitor(Opcodes.ASM9,parent){
      @Override public void visitInsn(int opcode){
       if(opcode==Opcodes.ARETURN&&options)super.visitMethodInsn(Opcodes.INVOKESTATIC,"phonzh/ChineseDisplay","options","([Ljava/lang/String;)[Ljava/lang/String;",false);
       if(opcode==Opcodes.ARETURN&&property) {
        super.visitVarInsn(Opcodes.ALOAD,1);
        super.visitMethodInsn(Opcodes.INVOKESTATIC,"phonzh/ChineseDisplay","clientProperty","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false);
       }
       if(opcode==Opcodes.ARETURN&&(get||font)) {
        super.visitVarInsn(Opcodes.ALOAD,0);
        super.visitMethodInsn(Opcodes.INVOKESTATIC,"phonzh/ChineseDisplay",font?"font":"display",font?"(Ljava/awt/Font;Ljava/lang/Object;)Ljava/awt/Font;":"(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;",false);
       }
       super.visitInsn(opcode);
      }
     };
    }
   },0);
   return w.toByteArray();
  }
 }
}
