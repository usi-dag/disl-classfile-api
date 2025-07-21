package ch.usi.dag.disl.preprocessor;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import ch.usi.dag.disl.preprocessor.Exceptions.NodeNotWhiteException;

/**
 * 
 * A node inside a TypeGraph. The main purpose of this class is to wrap an AbstractType before it is 
 * inserted into a TypeGraph, such that the AbstractType could be linked with other AbstractTypes, and 
 * operations over AbstractTypes can be supported.     
 * 
 * @author Andrea Rosa'
 *
 */

 class TypeNode implements Serializable {

	private static final long serialVersionUID = 6197187111316259650L;

	private AbstractType type;
	private Set<TypeNode> subtypes;	
	private boolean black;
	private boolean white;
	private Set<String> attributes;
	
	/**
	 * Create a new TypeNode encapsulating the given AbstractType.
	 * 
	 * @param type the AbstractType to encapsulate
	 */
	
	 TypeNode(final AbstractType type) {
		this.type = type;
		this.subtypes = new HashSet<>();
		this.black = false;
		this.white = false;		
	}
	
	/**
	 * Returns the AbstractType encapsulated by this class 
	 * 
	 * @return the encapsulated AbstractType
	 */
	
	 AbstractType getType() {
		return this.type;
	}
	
	/**
	 * Returns a set of TypeNodes, each of them encapsulating a subtype of the type encapsulated by this class.  
	 * 
	 * @return the subtypes of this type, encapsulated by TypeNodes 
	 */
	
	 Set<TypeNode> getSubtypes() {
		return this.subtypes;
	}
	
	/**
	 * Register a type (encapsulated by the given TypeNode) as a subtype of the type encapsulated by this node. 
	 * 
	 *  
	 * @param typeNode the subtype to register, encapsulated by a TypeNode 
	 */
	
	 void addSubtype(final TypeNode typeNode) {
		subtypes.add(typeNode);
	}
		
	
	/**
	 * Mark this node as black.
	 * 
	 */
	
	 void markAsBlack() {
		this.black = true;
	}
	
	 /**
	  * Determines whether this node is black.
	  * @return true if this node is black, false otherwise
	  */
	 
	 boolean isBlack() {
		return this.black;
	}
	
	 
	 /**
	  * Mark this node as white. If this node already black, the operation fails, and a NodeNotWhiteException is thrown.  
	  * 
	  */
	 
	 void markAsWhite() {
		if (black) {
			throw new NodeNotWhiteException(this);
		}
		
		this.white = true;
	}
	
	 /**
	  * Determines whether this node is white.
	  * @return true if this node is white, false otherwise
	  */
	  
	 
	 boolean isWhite() {
		return this.white;
	}
	
	 /**
	  * If this node is white, adds a set of attributes to this node. <br>
	  * If this node is black, the operation fails, and a NodeNotWhiteException is thrown.  
	  * 
	  */

	 
	 void addAttributes(final Set<String> attrs) {
		
		if (!isWhite()) {
			throw new NodeNotWhiteException(this);			
		}
		
		if (attributes == null) {
			attributes = new HashSet<>();
		}
		
		attributes.addAll(attrs);
	}
	 
	 /** 
	  * Retrieves the attributes registered to this node. <br>
	  * If no attributes have been registered (either because the node is black, or the attributes have not been set yet) returns null. 
	  * 
	  * @return the set of attributes registered to this node, or null if no attributes has been set
	  */
	
	 Set<String> getAttributes() {		
		return attributes;
		
	}

	
	
}
