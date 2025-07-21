package ch.usi.dag.disl.dislcontroller;

public class DiSLController {

	public static native void deploy(String snippetName);
	public static native void undeploy(String snippetName);
	public static native void retransformClass(Class<?> klass);

}