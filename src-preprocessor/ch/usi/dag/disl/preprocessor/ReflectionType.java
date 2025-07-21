package ch.usi.dag.disl.preprocessor;

import java.util.HashSet;
import java.util.Set;

import ch.usi.dag.disl.preprocessor.Exceptions.TypeCreationException;
import ch.usi.dag.disl.preprocessor.Exceptions.UnknownSupertypeException;

/**
 * Class representing a type into a TypeGraph. 
 * ReflectionTypes use the Java Reflection API to determine the supertypes of type they represent.    
 * 
 * @author Andrea Rosa'
 *
 */

public class ReflectionType extends AbstractType {

	private static final long serialVersionUID = 1716345368162940552L;

	// Once deserialized in another VM, reference to klass 
	// are lost. Avoid any dependence on klass as much as possible
	private transient Class<?> klass = null;
	private ReflectionType superClass = null;
	private boolean superClassHasBeenChecked = false;
	private Set<ReflectionType> interfaces = null;
	
	private static String __replaceDotsWithSlashes(String oldString) {
		return oldString.replace('.', '/');
	}
	
	
	/**
	 * Creates a new ReflectionType, representing type with the given name and loader. 
	 * <p>
	 * This method can throw a TypeCreationException if a Class<?> instance could not be loaded for the given type.
	 * 
	 * @param className the type name
	 * @param classloader the class loader
	 */
	public ReflectionType(final String className, final int classloader) {
		super(__replaceDotsWithSlashes(className), classloader);		
		try {
			this.klass = Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new TypeCreationException(className);
		}					
	}


	
	/**
	 * Creates a new ReflectionType, representing the given class loaded by the given classloader.  
	 * 
	 * 
	 * @param klass the Class<?> object representing the type 
	 * @param classloader the class loader
	 */
	
	public ReflectionType(final Class<?> klass, final int classloader) {
		super(__replaceDotsWithSlashes(klass.getName()), classloader);
		this.klass = klass;
	}
	
	/** 
	 * Determines the superclass of this type, according to the following contract: 
	 * <p>
	 *    a) If this type represents java.lang.Object, null is returned <br>
	 *    b) If this type represents a class different then java.lang.Object, its superclass is returned<br>
	 *    c) If this type represents an interface, java.lang.Object is returned<br>
	 *    d) If this type represents a primitive type (including void), java.lang.Object is returned<br>
	 *    e) If this type represents an array, java.lang.Object is returned<br>
	 *<p>
	 *		
	 *     If, for any reason, it is impossible to determine the superclass of this type, 
	 *     the method throws UnknownSupertypeException. Note that currently this situation can only happen if 
	 *     no Class<?> object corresponding to this type can be created.    
	 *     
	 *     The loader of the superclass is set equal to the loader of this class.  
	 * 
	 * @throws UnknownSupertypeException if the superclass of this type cannot be determined
	 * @return a ReflectionType representing the superclass of this type
	 * 
	 */

	public ReflectionType getSuperclass() throws UnknownSupertypeException {		

		if (klass == null) {
			throw new UnknownSupertypeException(this);
		}


		if (superClassHasBeenChecked) {
			return superClass;
		}
				
		superClassHasBeenChecked = true;

		Class<?> sc = klass.getSuperclass();
		if (sc == null) {			
			if (getName().equals("java/lang/Object")) {
				return null;
			}  else {
				sc = Object.class;
			}
		}

		superClass = new ReflectionType(sc, getClassloader());
		return superClass;

	}


	
	/**
	 * 
	 * Determine the interfaces implemented or extended by this type.  
	 * The contract for this method is the following:  <br>
	 *    a) If this type represents java.lang.Object, an empty set is returned<br>
	 *    b) If this type represents a class different from java.lang.Object, all interfaces 
	 *    implemented by the class (if any) are returned<br>
	 *    c) If this type represents an interface, all interfaces extended by the interface (if any) 
	 *    are returned<br>
	 *    d) If this type represents a primitive type (including void), the empty set is returned<br>
	 *    e) If this type represents an array, java.lang.Cloneable and java.io.Serializable are returned<br>
	 * <p>
	 *     If, for any reason, it is impossible to determine the FULL set of interfaces implemented or extended by this type, 
	 *     the method throws UnknownSupertypeException. Note that currently this situation can only happen if 
	 *     no Class<?> object corresponding to this type can be created. 
	 *     
	 *     The loader of the superclass is set equal to the loader of this class. 
	 *     
	 * @throws UnknownSupertypeException if any of the interfaces extended/implemented by this type cannot be determined     
	 * @return a Set of ReflectionTypes, each of them representing an interface implemented/extended by this type
	 */
	 
	
	public Set<ReflectionType> getInterfaces() throws UnknownSupertypeException {		

		
		if (klass == null) {
			throw new UnknownSupertypeException(this);
		}
			
		if (interfaces == null) {
			interfaces = new HashSet<>();
			
			Class<?>[] ints = klass.getInterfaces();
			 
			for (Class<?> cl : ints) {
				interfaces.add(new ReflectionType(cl, getClassloader()));
			}
						
		}
		
		return interfaces;
	}

	
	
	@Override
	public Set<ReflectionType> getSupertypes() throws UnknownSupertypeException {
		
		Set<ReflectionType> result = new HashSet<>();
		
		ReflectionType superClass = getSuperclass();
		
		if (superClass != null) { 
			result.add(superClass);
		}
		result.addAll(getInterfaces());
		
		return result;
		
		
	}

	
}
