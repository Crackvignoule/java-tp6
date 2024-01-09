package tp6;

public class Main {
    public static void main(String[] args) {
        try {
            // enlever le  commentaire pour le test.
            // ne pas oublier --add-opens java.base/java.lang=ALL-UNNAMED" en arg de la jvm

            Loader.instrumentTest(args);

            MethodCall t = new MethodCall();
            t.Test(null, false, null);

        } catch (Exception e) {
            System.err.println("Ooops");
            e.printStackTrace();
        }
    }
}