import ch.usi.dag.disl.annotation.After;
import ch.usi.dag.disl.annotation.AfterReturning;
import ch.usi.dag.disl.annotation.Before;
import ch.usi.dag.disl.annotation.SyntheticLocal;
import ch.usi.dag.disl.marker.BodyMarker;

public class DiSLClass {

	@SyntheticLocal
	public static long timeBefore;

	@Before(marker = BodyMarker.class, scope = "Main.main")
	public static void beforemain() {

		System.out.println("Instrumentation: Before method main");
		timeBefore = System.nanoTime ();
	}

	@After(marker = BodyMarker.class, scope = "Main.main")
	public static void aftermain() {

		long timeAfter = System.nanoTime ();
		System.out.println("Instrumentation: After method main which took " + (timeAfter - timeBefore) + "ns");
	}
}
