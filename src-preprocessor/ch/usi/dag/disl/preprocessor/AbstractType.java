package ch.usi.dag.disl.preprocessor;

import java.io.Serializable;
import java.util.Set;

import ch.usi.dag.disl.preprocessor.Exceptions.UnknownSupertypeException;

/** 
 * Class representing a type into a TypeGraph. 
 * The purpose of an AbstractType is to represent a type just by using its name and classloader.
 * The mechanism to define its supertypes is demanded to subclasses. For example, a ReflectionType (subtype of AbstractClass)
 * retrieves the supertypes by using the Java Reflection API. As another example, an user could define his own  
 * ASMType, using bytecode manipulation to retrieve the supertypes. In this way, the details on how to determine the 
 * supertypes of a type are transparent to a TypeGraph.
 * <p>
 * AbstractTypes and their subtypes use only the type name to define a type. As a consequence, two AbstractTypes will be 
 * deemed as equal if the name of the type they represent is the same. Subtypes cannot change this mechanism. This 
 * ensures (e.g.) that if one adds a ASMType to a TypeGraph populated with ReflectionTypes, the TypeGraph can detect the duplicate.   
 * 
 * 
 * 
 * @author Andrea Rosa'
 *
 */

public abstract class AbstractType implements Serializable {

	public static int UNKNOWN_CLASSLOADER = -1;
	public static int BOOTSTRAP_CLASSLOADER = 0;
	
	private static final long serialVersionUID = -1511080600220501239L;  

	private String name;
	
	private int classloader;

	
	/**
	 * Creates a new instance of AbstractType, representing typeName loaded with the given classloader. 
	 * 
	 * @param classloader the classloader that loaded this class
	 * @param typeName the name of the type represented by this instance
	 */
	
	
	protected AbstractType(final String typeName, final int classloader) {
		this.name = typeName;
		this.classloader = classloader;
	}
	
	/**
	 * Returns the type name
	 * @return the type name
	 */
	
	public String getName() {
		return name;
	}

	
	/**
	 * Returns the classloader of this class
	 * @return the classloader
	 */
	
	public int getClassloader() {
		return classloader;
	}
	
	/** 
	 * Returns the supertypes of the type represented by this class. This method must be implemented by subclasses, which are responsible to 
	 * determine all the supertypes of this type. 
	 * <p>
	 * The contract for this method is the following:  <br>
	 *    a) If this type represents java.lang.Object, an empty set must be returned <br>
	 *    b) If this type represents a class different then java.lang.Object:<br>
	 *    b.1) the superclass of the class must be included in the returned set<br>
	 *    b.2) all interfaces implemented by the class (if any) must be included in the returned set<br>
	 *    c) If this type represents an interface:<br>
	 *    c.1) java.lang.Object must be included in the returned set<br>
	 *    c.2) all interfaces extended by the interface (if any) must be included in the returned set<br>
	 *    d) If this type represents a primitive type (including void):<br>
	 *    d.1) java.lang.Object must be included in the returned set<br>
	 *    e) If this type represents an array:<br>
	 *    e.1) java.lang.Object must be included in the returned set<br>
	 *    e.2) java.lang.Cloneable and java.io.Serializable must be included in the returned set<br>
	 * <p>
	 *     If, for any reason, it is impossible to determine the FULL set of supertypes of this type, 
	 *     the method must throw UnknownSuperTypeException 
	 * 
	 * @return the complete set of supertypes of this type, represented by instances of AbstractType
	 * @throws UnknownSupertypeException if it was impossible to determine the FULL set of supertypes of this type
	 */
	
	public abstract Set<? extends AbstractType> getSupertypes() throws UnknownSupertypeException;

	/**
	 * Determine whether this type represents java.lang.Object. 
	 * @return true if this type represents java.lang.Object, false otherwise
	 */
	
	public boolean isRoot() {
		if (name.equals("java/lang/Object")) {
			return true;
		}

		return false;
	}



	@Override
	public final int hashCode() {
		int result = 17;
		result = 31 * result + name.hashCode();
		result = 31 * result + classloader;
		return result;
	}

	@Override
	public final boolean equals(Object o) {

		if (o == this) return true;
		if (!(o instanceof AbstractType)) {
			return false;
		}

		AbstractType ac = (AbstractType) o;

		return ac.getName().equals(name) && ac.getClassloader() == classloader;
	}


}
