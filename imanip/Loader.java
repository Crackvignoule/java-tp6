package org.ema.imanip;

import java.lang.reflect.Method;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public class Loader {

    /*
     * Principe:
     *  - lire le fichier MethodCall.class, puis lui appliquer une modification de la methode Test en memoire
     *  - ensuite demander le chargement du buffer représantant la classe modifiée par le classloader.
     */

    public static void instrumentTest(String[] args) throws Exception {

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        MethodReplacer mr = new MethodReplacer(cw,
                "Test",
                "(Ljava/lang/String;ZLjava/lang/String;)Z");

        ClassReader reader = new ClassReader(
                ClassLoader.getSystemResourceAsStream("org/ema/imanip/MethodCall.class"));

        reader.accept(mr, ClassReader.EXPAND_FRAMES);

        loadClass(cw.toByteArray());

    }

    public static  Class<?> loadClass(byte[] bytecode)
            throws Exception {
        ClassLoader scl = ClassLoader.getSystemClassLoader();

        Class<?>[] types = new Class<?>[] {
                String.class, byte[].class, int.class, int.class
        };
        Object[] args = new Object[] {
                null, bytecode, 0, bytecode.length
        };

        Method m = ClassLoader.class.getDeclaredMethod("defineClass", types);
        m.setAccessible(true);
        return (Class<?>) m.invoke(scl, args);

    }

}
