package org.ema.imanip;


public class MethodCall {

    public boolean Test(String a, boolean b, String c) {
        System.out.println("Classe d'origine non instrumentee");
        return false;
    }
}
