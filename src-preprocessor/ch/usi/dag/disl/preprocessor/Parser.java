package ch.usi.dag.disl.preprocessor;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import ch.usi.dag.disl.preprocessor.Exceptions.IllegalFileFormatException;
import ch.usi.dag.disl.preprocessor.Exceptions.MissingTypeException;
import ch.usi.dag.disl.preprocessor.Exceptions.TypeCreationException;
import ch.usi.dag.disl.preprocessor.Exceptions.UnknownSupertypeException;

/**
 * Class responsible to construct the initial type graph and export it to disk. 
 * 
 * @author Andrea Rosa'
 * @author Matteo Basso
 *
 */

public class Parser {
	
	static IOAbstractHelper<ReflectionType> helper = new IOReflectionHelper();

	static {
		// Method to use to attempt to circumvent encapsulation in JDK 16+,
		// in order to get access to a classloader's private classpath.
		ClassGraph.CIRCUMVENT_ENCAPSULATION = ClassGraph.CircumventEncapsulationMethod.NARCISSUS;
	}
	
	private static Set<ReflectionType> __getClassesToParse() {
		
		__log("Loading types to parse...");

		final Set<ReflectionType> refTypes = new HashSet<>();

		try (
			final ScanResult scanResult = new ClassGraph()
													.enableClassInfo()
													.enableSystemJarsAndModules()
													// .enableExternalClasses()
													.enableInterClassDependencies()
													.rejectPackages(
														// Prevent the scanning of the specific classes used by the preprocessor
														"org.kohsuke.args4j",
														"io.github.classgraph",
														"nonapi.io.github.classgraph",
														"io.github.toolfactory.narcissus",
														"ch.usi.dag.disl.preprocessor"
													)
													.scan()
		) {
			// Set the bootstrap class loader for every class loaded for them		
			for (Class<?> cl : scanResult.getAllClasses().loadClasses()) {
				refTypes.add(new ReflectionType(cl, AbstractType.BOOTSTRAP_CLASSLOADER));
			}
		}

		// Object is not included in classes
		refTypes.add(new ReflectionType(Object.class, AbstractType.BOOTSTRAP_CLASSLOADER));
		
		__log("Types loaded successfully!");

		return refTypes;

	}

	private static void __log(String string) {
		
		Logger.println(string);
		
	}

	private static TypeGraph<ReflectionType> __createLinkedGraph(Set<ReflectionType> classes) {
		
		__log("Creating graph...");
		
		TypeGraph<ReflectionType> graph = null;
				
			try {
				graph = new TypeGraph<>(classes, helper.importBlacklist(), helper.importWhitelist());
			} catch (MissingTypeException e) {
				System.err.println("Error: type " + e.getType().getName() + " appears in the blacklist or whitelist, but is not included" + 
						" in the set of classes to import.");
				e.printStackTrace();
			} catch (UnknownSupertypeException e) {
				System.err.println("Error: unable to determine supertypes of " + e.getType().getName());
				e.printStackTrace();
			} catch (TypeCreationException e) {
				System.err.println("Error: unable to create ReflectionType for " + e.getTypeName());
				e.printStackTrace();
			} catch (IOException e) {
				System.err.println("Error: exception while reading file.");
				System.err.println(e.getMessage());
				e.printStackTrace();
			} catch (IllegalFileFormatException e) {
				System.err.println("Error: format of whitelist file " + e.getFileName() + " is invalid. ");
				e.printStackTrace();
			}
				
		__log("Graph created.");
		
		return graph;

	}
	

	private static void __exportAll(TypeGraph<ReflectionType> graph) {

		__log("Exporting files...");
		
		try {
			helper.exportAll(graph);			
		} catch (IOException e) {
			System.err.println("Error: exception while writing file.");
			System.err.println(e.getMessage());
			e.printStackTrace();
			}
		
		__log("Export finished!");
		
	}


	private static void __parseArgument(String... args) {
		CmdLineParser argParser = new CmdLineParser(TypeParserConfig.getInstance()); 

		try {
			argParser.parseArgument(args);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println("Valid options:");
			argParser.printUsage(System.err);
			System.err.println();
		}
	}

	public static void main(String[] args) {
		__parseArgument(args);
		
		__log("Starting parser...");
		
		Set<ReflectionType> classes = __getClassesToParse();		
		TypeGraph<ReflectionType> graph = __createLinkedGraph(classes);		
		__exportAll(graph);

		__log("Done.");
	}

}
