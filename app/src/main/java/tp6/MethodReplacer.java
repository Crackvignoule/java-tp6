package tp6;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MethodReplacer extends ClassVisitor implements Opcodes {
    private String mname;
    private String mdesc;
    private String cname;

    public MethodReplacer(ClassVisitor cv, String mname, String mdesc) {
        super(Opcodes.ASM4, cv);
        this.mname = mname;
        this.mdesc = mdesc;
    }

    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        this.cname = name;
        cv.visit(version, access, name, signature, superName, interfaces);
    }

    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        String newName = name;
        if (name.equals(mname) && desc.equals(mdesc)) {
            newName = "orig$" + name;
            generateNewBody(access, desc, signature, exceptions, name, newName);
            System.out.println("Replacing");
        }
        return super.visitMethod(access, newName, desc, signature, exceptions);
    }

    private void generateNewBody(int access, String desc, String signature, String[] exceptions,
            String name, String newName) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "Test",
                "(Ljava/lang/String;ZLjava/lang/String;)Z", null, null);
        mv.visitCode();
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitFieldInsn(GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;");
        mv.visitLdcInsn("GOTit");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");
        Label l2 = new Label();
        mv.visitLabel(l2);
        mv.visitInsn(ICONST_0);
        mv.visitInsn(IRETURN);
        Label l3 = new Label();
        mv.visitLabel(l3);
        mv.visitLocalVariable("this", "LcheckASM/MethodCall;", null, l1, l3, 0);
        mv.visitLocalVariable("a", "Ljava/lang/String;", null, l1, l3, 1);
        mv.visitLocalVariable("b", "Z", null, l1, l3, 2);
        mv.visitLocalVariable("c", "Ljava/lang/String;", null, l1, l3, 3);
        mv.visitMaxs(4, 4);
        mv.visitEnd();

    }
}
