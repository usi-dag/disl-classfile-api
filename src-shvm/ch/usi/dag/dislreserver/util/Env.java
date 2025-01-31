package ch.usi.dag.dislreserver.util;


public final class Env {

    /**
     * Java version of the running JVM.
     */
    private static final int javaVersion;

    static {
        String version = System.getProperty("java.version");
        if(version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            final int dot = version.indexOf(".");
            if(dot != -1) {
                version = version.substring(0, dot);
            }
        }

        javaVersion = Integer.parseInt(version);
    }

    public static int getJavaVersion() {
        return javaVersion;
    }

}
