package ch.usi.dag.disl.preprocessor;

import java.io.Serializable;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import ch.usi.dag.disl.preprocessor.Exceptions.DuplicateTypeException;
import ch.usi.dag.disl.preprocessor.Exceptions.MissingTypeException;
import ch.usi.dag.disl.preprocessor.Exceptions.UnknownSupertypeException;

/* TODO with the working version of force-loading, TypeGraph is now
 * useful only to SPEEDUP precomputation of the Java class library. 
 * Revise this class to support this feature.  
 * 
 */

/** A graph representing the type hierarchy of a Java application, represented by instances of AbstractType.  
 *    
 *  The purpose of this class is to ease the process of determining all the known subtypes of a given type, by linking 
 *  them in a graph. Outbound edges from type A to type B in a TypeGraph represents a subtype relationship between B and A (i.e., 
 *  B is subtype o A). Maintaining the graph allows to propagate properties of the parent to the children (i.e., being a "white"
 *  or a "black" class, see below). 
 *  <p>
 *  While the graph links different AbstractTypes, it uses TypeNode object to wrap AbstractTypes and support graph algorithm over them.
 *  <p>  
 *  <b>White or black types</b>
 *  Optionally, a TypeGraph can mark the enclosing types as BLACK or WHITE. Such colors are inherit from the 
 *  parents, with BLACK having precedence over WHITE. In order to color a graph, the user must specify, during the initialization phase, 
 *  a WHITELIST and a BLACKLIST, containing the types that will be colored as WHITE or BLACK, respectively. The TypeGraph propagates
 *  the color from the types specified in the lists to all their subtypes. Note that a type can be either BLACK or WHITE. If a type has
 *  both WHITE and BLACK parents, it will be colored to BLACK. 
 *  <p>
 *  WHITE types can also be associated with attributes, in the form of a String. The initial set of attributes must be specified 
 *  for each type in the WHITELIST during the initialization phase. WHITE types inherit the attributes from all the WHITE parents they 
 *  inherit the color FROM. If a type inherit the WHITE color from two WHITE parents with different attributes, the type will
 *  inherit the union of the two sets of attributes. 
 *  <p>
 *  <b>Subtype relationships</b>
 *  In a TypeGraph, it exists only one root, being the AbstractType representing java.lang.Object. Only such type does not 
 *  have any supertype. 
 *  As a consequence, an interface that does not extend any other interface will always have java.lang.Object as supertype.   
 *  <p>
 *  <b>Initialization</b>
 *  A TypeGraph must be initialized with three entities:
 *  - A initial set of types to be inserted.
 *  - A blacklist (optional)
 *  - A whitelist (optional)
 *  Upon initialization, all classes in the provided set will be linked and colored according to the properties of their parents. 
 *  <p>
 *  TypeGraph also supports adding new classes to the graph after initialization. In such case, new classes inherit the color 
 *  of their parents immediately. 
 *  
 *  
 * @author Andrea Rosa'
 *
 * @param <T> The type used to represent types in this graph.   
 */

public class TypeGraph<T extends AbstractType> implements Serializable {

	private static final long serialVersionUID = 954048790404051048L;

	private Map<T, TypeNode> nodes;
	private Set<TypeNode> blackTypes;
	private Set<TypeNode> whiteTypes;
	private TypeNode rootNode;
	private Blacklist<T> blacklist;
	private Whitelist<T> whitelist;


	private TypeGraph() {
		rootNode = null;
		this.nodes = new HashMap<>();
		this.blackTypes = new HashSet<>();
		this.whiteTypes = new HashSet<>();
	}


	/**
	 * Creates and initializes a new TypeGraph, linking all the provided types. 
	 * If the blacklist and whitelist provided are not empty, the types will be colored according to the lists.   
	 * 
	 * @param types the types to insert in the graph
	 * @param blacklist the blacklist to use for coloring 
	 * @param whitelist the whitelist to use for coloring
	 * @throws MissingTypeException if a type appears in the blacklist or whitelist, but is not included in types
	 * @throws UnknownSupertypeException if it was impossible to fully determine all supertypes of a type
	 */
	public TypeGraph(final Set<T> types, final Blacklist<T> blacklist, final Whitelist<T> whitelist) throws MissingTypeException, UnknownSupertypeException {
		this();
		if (types == null || blacklist == null || whitelist == null) {
			throw new NullPointerException();
		}
		__populateGraph(types);
		__determineBlackTypes(blacklist);
		__determineWhiteTypes(whitelist);
	}


	/**
	 * Creates and initializes a new TypeGraph, linking all the provided types. 
	 *    
	 * 
	 * @param types the types to insert in the graph
	 * @throws UnknownSupertypeException if it was impossible to fully determine all supertypes of a type
	 */

	public TypeGraph(Set<T> types) throws UnknownSupertypeException {
		this(types, Blacklist.empty(), Whitelist.empty()); 
	}


	/**
	 * Returns the blacklist of this graph.
	 * 
	 * @return the blacklist
	 */

	public Blacklist<T> getBlacklist() {
		return blacklist;
	}

	/**
	 * Returns the whitelist of this graph.
	 * 
	 * @return the whitelist
	 */

	public Whitelist<T> getWhiteList() {
		return whitelist;
	}

	/** 
	 * Returns a map containing all the types included in this graph, each of them linked to the corresponding TypeNode used by this graph.  
	 * 
	 * @return a map of all types included in this graph and the corresponding TypeNode
	 */

	public Map<T, TypeNode> getTypeAndNodes() {
		return this.nodes;
	}

	/**
	 * Returns the set of all black types (including those in the blacklist) encapsulated by the corresponding TypeNode.
	 * @return the set of encapsulated black types
	 */

	public Set<TypeNode> getBlackTypeNodes() {
		return blackTypes;
	}


	/**
	 * Returns the set of all white types (including those in the whitelist) encapsulated by the corresponding TypeNode.
	 * @return the set of encapsulated white types
	 */
	public Set<TypeNode> getWhiteTypeNodes() {
		return whiteTypes;

	}

	private void __addType(final T type) {
		if (nodes.containsKey(type)) {
			throw new DuplicateTypeException(type);
		}

		Logger.println("Adding " + type.getName() + " to graph.");
		nodes.put(type, new TypeNode(type));
	}

	private void __registerSubtype(final T type, final T subtype) {

		TypeNode typeNode = nodes.get(type);		
		TypeNode subtypeNode = nodes.get(subtype);

		if (typeNode == null) {
			throw new MissingTypeException(type);
		}

		if (subtypeNode == null) {
			throw new MissingTypeException(subtype);
		}

		typeNode.addSubtype(subtypeNode);

	}

	private void __addTypes(final Set<T> types) {

		for (T type : types) {
			__addType(type);
		}
	}

	/**
	 * Returns the attributes associated to type is type is a white node of this graph. <br>
	 * If type has no attributes, returns an empty set. <br>
	 * If type is not white, returns null. <br>
	 * If type is not part of this graph, returns MissingTypeException  
	 * 
	 * @param type the type whose attributes are requested
	 * @throws MissingTypeException if type is not part of the graph
	 * @return the attributes of types
	 */

	public Set<String> getAttributesIfWhite(final T type) throws MissingTypeException {

		TypeNode node = nodes.get(type);

		if (node == null) {
			throw new MissingTypeException(type);
		}

		if (node.isWhite()) {
			return node.getAttributes();
		}

		return null;


	}

	@SuppressWarnings("unchecked")
	private void __populateGraph(final Set<T> types) throws UnknownSupertypeException  {

		__addTypes(types);

		for (T type : types) {

			Set<T> superTypes = (Set<T>) type.getSupertypes();

			for (T superType : superTypes) {
				__registerSubtype(superType, type);				
			}

		}

	}


	/**
	 * Add type to the graph, marking its color.  
	 * 
	 * @param type the type to add
	 * @throws UnknownSupertypeException if any of the supertypes of type cannot be determined.  
	 */



	@SuppressWarnings("unchecked")
	public void addType(final T type) throws UnknownSupertypeException {

		Logger.println("Start parsing: " + type.getName());


		__addType(type);

		Set<T> superTypes = null;
		try { 
			superTypes = (Set<T>) type.getSupertypes();				
		} catch( UnknownSupertypeException e ) {
			__removeType(type);
			throw e;
		}

		for (T superType : superTypes) {
			if (superType != null) {
				if (! isPresent(superType)) {				
					try {
						addType(superType);
					} catch( UnknownSupertypeException e ) {
						__removeType(type);
						throw e;
					}
				} 
				__registerSubtype(superType, type);
			}
		}

		__determineChildState(type, superTypes);

		Logger.println("End parsing - Type inserted into graph: " + type.getName());


	}

	private void __removeType(final T type) {
		if (nodes.containsKey(type)) {
			nodes.remove(type);
			Logger.println("Removing " + type.getName());
		} else {
			throw new MissingTypeException(type);
		}


	}

	private void __determineChildState(final T type, final Set<T> superTypes) {

		boolean blacklist = false;
		boolean whitelist = false;
		Set<String> attributes = null;

		for (T st : superTypes) {

			if (st != null) {
				TypeNode cn = nodes.get(st);
				if (cn.isBlack()) {
					blacklist = true;
					break;
				}
				if (cn.isWhite()) {
					whitelist = true;
					if (attributes == null) {
						attributes = new HashSet<>();					
					}
					attributes.addAll(cn.getAttributes());				
				}
			}
		}

		TypeNode  node = nodes.get(type);

		if (blacklist) {
			__markAsBlack(node);
		}
		else if (whitelist) {
			__markAsWhite(node, attributes);
		}



	}


	private void __markAsBlack(final TypeNode node) {
		node.markAsBlack();
		blackTypes.add(node);
	}

	private void __markAsWhite(final TypeNode node, final Set<String> attributes) {
		node.markAsWhite();
		whiteTypes.add(node);
		node.addAttributes(attributes);
	}

	/**
	 * Determines whether type is part of the graph.
	 * @param type the type to check
	 * @return true is part of the graph, false otherwise 
	 */

	public boolean isPresent(final T type) {
		return nodes.containsKey(type);
	}

	/**
	 * Determines whether type is white.<br> 
	 * 
	 * Throws MissingTypeException if type is not part of the graph.
	 * 
	 * @param type the type to check 
	 * @return true is white, false otherwise 
	 */

	public boolean isWhite(final T type) {
		TypeNode node = nodes.get(type);
		if (node == null) {
			throw new MissingTypeException(type);
		}

		if (whiteTypes.contains(node)) {
			return true;
		}

		return false;

	}

	/**
	 * Get the TypeNode denoting the root of this graph (i.e., java/lang/Object)
	 * @return the root TypeNode
	 */
	
	public TypeNode getRootNode() {
		if (rootNode == null) {
			for (T key : nodes.keySet()) {
				if (key.isRoot()) {
					rootNode = nodes.get(key);
					break;
				}
			}
		}

		return rootNode;
	}


	/**
	 * Logs debug information on this graph. <br>
	 * Information includes the properties (i.e., subtypes, color, and attributes) of every node in this graph.  
	 */

	public void print() {

		TypeNode rootNode = getRootNode();

		// Temporary data for BFS algorithm
		Set<TypeNode> visited = new HashSet<>();
		Deque<TypeNode> toVisit = new LinkedList<>();

		toVisit.addLast(rootNode);
		

		Logger.println("Total number of types: " + nodes.size());

		Logger.println("------------------------");
		Logger.println("Black types: " + blackTypes.size());
		for (TypeNode cn: blackTypes) {
			Logger.println("BLACK: " + cn.getType().getName() + " Classloader: " + cn.getType().getClassloader());
		}


		Logger.println("------------------------");
		Logger.println("White types: " + whiteTypes.size());
		for (TypeNode cn: whiteTypes) {
			Logger.println("WHITE: " + cn.getType().getName() + " Classloader: " + cn.getType().getClassloader() + " - Attributes: ");
			for (String s : cn.getAttributes()) {
				Logger.println("WHITE: "+  s);
			}			
		}



		Logger.println("------------------------");
		Logger.println("Beginning printing graph...");

		while (!toVisit.isEmpty()) {

			TypeNode node = toVisit.pollFirst();	

			if (visited.contains(node)) {
				continue;
			}

			for (TypeNode cn : node.getSubtypes()) {			
				toVisit.addLast(cn);			
			}

			__doPrint(node);

			visited.add(node);

		}

		

		Logger.println("------------------------");



	}

	private void __doPrint(final TypeNode node) {

		Logger.println("GRAPH: Type: " + node.getType().getName() + " Classloader: " + node.getType().getClassloader());
		for (TypeNode cn: node.getSubtypes()) {
			Logger.println("GRAPH: Subtype: " + cn.getType().getName() + " Classloader: " + cn.getType().getClassloader());
		}


	}


	private void __determineBlackTypes(final Blacklist<T> blacklist) throws MissingTypeException {

		this.blacklist = blacklist;

		for (T type : blacklist.getTypes()) {
			TypeNode rootNode = nodes.get(type);
			if (rootNode == null) {
				throw new MissingTypeException(type);
			}

			__propagateBlack(rootNode);

		}

	}

	// Implements a Depth-first search
	private void __propagateBlack(final TypeNode root) {

		if (root.isBlack()) {
			return;
		}

		__markAsBlack(root);

		for (TypeNode cn : root.getSubtypes()) {
			__propagateBlack(cn);
		}


	}


	private void __determineWhiteTypes(final Whitelist<T> whitelist) throws MissingTypeException {

		this.whitelist = whitelist;


		for (T type : whitelist.getTypes()) {
			TypeNode rootNode = nodes.get(type);

			if (rootNode == null) {
				throw new MissingTypeException(type);
			}

			Set<String> attributes = whitelist.getAttributes(type);

			__propagateWhite(rootNode, attributes);

		}



	}

	// Implements a Depth-first search
	private void __propagateWhite(final TypeNode root, final Set<String> attributes) {

		if (root.isBlack() || root.isWhite()) {
			return;
		}

		__markAsWhite(root, attributes);



		for (TypeNode cn : root.getSubtypes()) {
			__propagateWhite(cn, attributes);
		}


	}


}
