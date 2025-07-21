package ch.usi.dag.disl.preprocessor;

import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import ch.usi.dag.disl.preprocessor.Exceptions.IllegalFileFormatException;
import ch.usi.dag.disl.preprocessor.Exceptions.TypeCreationException;

/** 
 * Class containing helper methods to import and export several entities related to a TypeGraph, 
 * such as the blacklist, the whitelist, and the TypeGraph itself. 
 * <p>
 * While exporting can be performed using methods implemented direcly by this class, importing is delegated to a subclass. 
 * The reason is that the conversion from a String representing the type (specificed by the users by means 
 * of textual files) to the subtype of AbstractType representing the type is 
 * dependent on which subtype of AbstractType is used. For example, class IOReflectionHelper (subtype of this class) 
 * leverages the Java Reflection API in the creation of a ReflectionType. Other user-defined classes can subtype this class to 
 * convert textual files to AbstractTypes in different ways.             
 * 
 * @author Andrea Rosa'
 *
 * @param <T> the subtype of AbstractType that this class supports 
 */

public abstract class IOAbstractHelper<T extends AbstractType> {

	public TypeParserConfig config; 

	protected IOAbstractHelper() {
		config = TypeParserConfig.getInstance();
	}

	private void __printTypeNodeSet(final BufferedWriter out, final Set<TypeNode> set) throws IOException {
		for (TypeNode cn: set) {
			out.write(cn.getType().getName()+"@"+cn.getType().getClassloader()+"#"+cn);
			out.newLine();
		}
	}

	private  void __printNodeAttributes(final BufferedWriter out, final TypeNode node) throws IOException {

		for (String s: node.getAttributes()) {
			out.write(node.getType().getName()+"@"+node.getType().getClassloader()+"#"+node+","+s);
			out.newLine();
		}
	}

	private  void __createOrOverwriteFile(final String pathToFile) throws IOException {

		Path path = Paths.get(pathToFile);
		Files.createDirectories(path.getParent());		
		Files.deleteIfExists(path);
		Files.createFile(path);


	}

	/**
	 * Creates a textual file containing all the types inside graph marked as black, one by line. 
	 * The file is created in the path specified by the Config singleton.    
	 * 
	 * @param graph the graph whose black types are printed 
	 * @throws IOException if an IO operation fails
	 */

	public void exportBlackTypes(final TypeGraph<? extends AbstractType> graph) throws IOException {

		Set<TypeNode> blackTypeNodes = graph.getBlackTypeNodes();

		__createOrOverwriteFile(config.getBlackTypesFilePath());

		BufferedWriter out = new BufferedWriter( new FileWriter(config.getBlackTypesFilePath()));

		__printTypeNodeSet(out, blackTypeNodes);

		out.flush();
		out.close();

	}

	/**
	 * Creates a textual file containing all the types inside graph marked as white, one by line. 
	 * The file is created in the path specified by the Config singleton.    
	 * 
	 * @param graph the graph whose white types are printed 
	 * @throws IOException if an IO operation fails
	 */


	public  void exportWhiteTypes(final TypeGraph<? extends AbstractType> graph) throws IOException {

		Set<TypeNode> whiteTypeNodes = graph.getWhiteTypeNodes();

		__createOrOverwriteFile(config.getWhiteTypesFilePath());

		BufferedWriter out = new BufferedWriter( new FileWriter(config.getWhiteTypesFilePath()));

		__printTypeNodeSet(out, whiteTypeNodes);

		out.flush();
		out.close();

	}


	/**
	 * Creates a textual file containing all the types inside graph, one by line.
	 * Subtypes of each type are also shown.  
	 * The file is created in the path specified by the Config singleton.    
	 * 
	 * @param graph the graph whose types are printed 
	 * @throws IOException if an IO operation fails
	 */
	public void exportTextualTypeGraph(final TypeGraph<? extends AbstractType> graph) throws IOException {

		TypeNode rootNode = graph.getRootNode();

		__createOrOverwriteFile(config.getTypeGraphTextFilePath());

		BufferedWriter out = new BufferedWriter( new FileWriter(config.getTypeGraphTextFilePath()));

		__printTypeGraph(out, rootNode);

		out.flush();
		out.close();



	}


	private void __printTypeGraph(final BufferedWriter out, final TypeNode rootNode) throws IOException {

		// Temporary data for BFS algorithm
		Set<TypeNode> visited = new HashSet<>();
		Deque<TypeNode> toVisit = new LinkedList<>();

		toVisit.addLast(rootNode);


		while (!toVisit.isEmpty()) {

			TypeNode node = toVisit.pollFirst();	

			if (visited.contains(node)) {
				continue;
			}

			for (TypeNode cn : node.getSubtypes()) {			
				toVisit.addLast(cn);			
			}

			__doPrint(out, node);

			visited.add(node);

		}

	}


	private void __doPrint(final BufferedWriter out, final TypeNode node) throws IOException {

		out.write("T:"+ node.getType().getName() + "@" + node.getType().getClassloader()+"#"+node+'\n');
		for (TypeNode cn: node.getSubtypes()) {
			out.write("S:" + cn.getType().getName() + "@" + cn.getType().getClassloader()+"#"+cn+'\n');
		}


	}

	/**
	 * Creates a textual file containing all the types inside graph marked as white and, for each of them, 
	 * all the attributes inherited by their white parents. The format of this file is the same as the one
	 * used by method importWhitelist.  
	 * The file is created in the path specified by the Config singleton.    
	 * 
	 * @param graph the graph whose white types and corresponding methods are printed 
	 * @throws IOException if an IO operation fails
	 */


	public void exportAttributesList(final TypeGraph<? extends AbstractType> graph) throws IOException {

		Set<TypeNode> whiteTypeNodes = graph.getWhiteTypeNodes();

		__createOrOverwriteFile(config.getAttributesFilePath());

		BufferedWriter out = new BufferedWriter( new FileWriter(config.getAttributesFilePath()));

		for (TypeNode cn : whiteTypeNodes) {			
			__printNodeAttributes(out, cn);
		}

		out.flush();
		out.close();
	}

	/**
	 * Shortcut to invoke exportBlackTypes(graph), exportWhiteTypes(graph), exportAttributesList(graph), and 
	 * exportTypeGraph(graph) at once. 
	 * 
	 * @param graph the graph whose attributes are printed
	 * @throws IOException if an IO operation fails
	 */

	public  void exportAll(final TypeGraph<? extends AbstractType> graph) throws IOException {

		exportBlackTypes(graph);
		exportWhiteTypes(graph);
		exportAttributesList(graph);
		exportTypeGraph(graph);
		exportTextualTypeGraph(graph);

	}

	/**
	 * Creates a file containing the serialized version of graph. 
	 * The graph can be imported in another application by calling importTypeGraph. 
	 * The file is created in the path specified by the Config singleton.
	 * 
	 * @param graph the graph to be exported
	 * @throws IOException if an IO operation fails
	 */


	public  void exportTypeGraph(final TypeGraph<? extends AbstractType> graph) throws IOException {

		__createOrOverwriteFile(config.getTypeGraphOutputFilePath());

		FileOutputStream fileOut = new FileOutputStream(config.getTypeGraphOutputFilePath());
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(graph);
		out.close();
		fileOut.close();

	}

	/**
	 * Imports a previously deserialized TypeGraph from a file. 
	 * The file containing the graph is specified by the Config singleton.
	 * 
	 * @throws IOException if an IO operation fails
	 * @throws ClassNotFoundException if class TypeGraph is not found by the JVM. This should never happen in a correctly working environment. 
	 */


	@SuppressWarnings("unchecked")
	public  TypeGraph<? extends AbstractType> importTypeGraph() throws IOException, ClassNotFoundException {

		TypeGraph<? extends AbstractType> typeGraph = null;		
		FileInputStream fileIn = new FileInputStream(config.getTypeGraphInputFilePath());
		ObjectInputStream in = new ObjectInputStream(fileIn);
		typeGraph = (TypeGraph<? extends AbstractType>) in.readObject();
		in.close();
		fileIn.close();

		return typeGraph;
	}


	/**
	 * 
	 * Reads a textual file containing the specification of a blacklist, and 
	 * returns a Blacklist object containing the types specified in the file. 
	 * <p>
	 * This method must be implemented by subtypes of IOAbstractHelper, which are responsible
	 * to create instances of AbstrctTypes corresponding to the types specified in the blacklist, as well as inserting 
	 * them in the Blacklist. 
	 * <p>
	 * The textual file is specified by the Config singleton, and must adhere to the following specification:<br>
	 * a) the file must contain the name of one type per line.<br>
	 * b) the type name is the name of the corresponding Java class or interface, including the package. 
	 * Subpackages must be separated by dots.
	 * <p>
	 * If two lines of the file are identical, the second line is ignored. This means that a single AbstractType 
	 * corresponding to the duplicated type must be created and inserted into the blacklist.  
	 * If the textual file is empty, an empty instance of Blacklist is returned. 
	 * If the textual file is not found, {@link IOException} is thrown.   
	 * 
	 * 
	 *    
	 * 
	 * @return an instance of Blacklist, corresponding to the specification in the textual file
	 * @throws TypeCreationException if an error occurred during the creation of an AbstractType, such that the 
	 * Blacklist instance could not be fully populated.  
	 * @throws IOException if an IO operation fails
	 */

	public abstract Blacklist<T> importBlacklist() throws TypeCreationException, IOException;



	/**
	 * 
	 * Reads a textual file containing the specification of a whitelist, and 
	 * returns a Whitelist object containing the types and attributes specified in the file. 
	 * <br>
	 * This method must be implemented by subtypes of IOAbstractHelper, which are responsible
	 * to create instances of AbstrctTypes corresponding to the types specified in the whitelist, as 
	 * well as inserting them in the Whitelist.    
	 * <br>
	 * The textual file is specified by the Config singleton, and must adhere to the following specification:<br>
	 * a) each line of the file must be composed of two parts, separated by a comma.<br> 
	 * b) The first part specifies a type name (i.e., the name of the corresponding Java class or interface, including the package;  
	 * subpackages must be separated by dots).<br>
	 * c) The second part specifies a textual attribute, corresponding to the type specified in the first part.<br>
	 * <p> 
	 *    
	 * In case two lines have identical first parts, but different second parts, it means that the type specified in the first part has 
	 * multiple attributes. Users can use this mechanism (i.e., write multiple lines corresponding to the same type) to specify multiple
	 * attributes for a given type. A single AbstractType corresponding to the duplicated type must be created and inserted into 
	 * the whitelist.  
	 * <p> 
	 * If two lines of the file are identical, the second line must be ignored. This means that a single AbstractType 
	 * corresponding to the duplicated type must be created and inserted into the whitelist, and the duplicated
	 * attribute must be linked to the type only once.  
	 * <p>
	 * If the textual file is empty, an empty instance of Whitelist is returned. 
	 * If the textual file is not found, {@link IOException} is thrown.   
	 * If a line is malformed (i.e., no comma separator is found, or multiple of them are found), IllegalFileFormatException is thrown.     
	 *
	 *    
	 * 
	 * @return an instance of Whitelist, corresponding to the specification in the textual file
	 * @throws TypeCreationException if an error occurred during the creation of an AbstractType, such that the 
	 * Whitelist instance could not be fully populated.  
	 * @throws IOException if an IO operation fails
	 * @throws IllegalFileFormatException if a line is malformed
	 */

	public abstract Whitelist<T> importWhitelist() throws TypeCreationException, IOException, IllegalFileFormatException;


}

