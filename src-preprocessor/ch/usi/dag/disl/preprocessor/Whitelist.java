package ch.usi.dag.disl.preprocessor;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ch.usi.dag.disl.preprocessor.Exceptions.MissingTypeException;

/**
 * Class representing the whitelist of a TypeGraph.     
 * 
 * @author Andrea Rosa'
 *
 * @param <T> the type used to represent types in this whitelist
 */

public class Whitelist<T extends AbstractType> implements Serializable {
	
	private static final long serialVersionUID = -5328714931528858541L;
	private Map<T, Set<String>> typesAndAttributes;
	
	/**
	 * Create a new whitelist, inserting types and attributes into it.
	 * The input is a map, linking each type in the whitelist to the corrisponding set of attributes. 
	 * If a type has no attribute defined, the map must link the type to an empty set.   
	 * 
	 * @param typesAndAttributes the types and attributes to insert into the whitelist
	 */
	
	public Whitelist(final Map<T, Set<String>> typesAndAttributes) {
		this.typesAndAttributes = typesAndAttributes;
	}
	
	/**
	 * Returns the types inside the whitelist
	 * @return the types inside the whitelist
	 */
	
	public Set<T> getTypes() {
		return typesAndAttributes.keySet();
	}
	
	/**
	 * Returns the attributes associated to a type in the whitelist.
	 * If a type is in the whitelist but has no attribute defined, an empty set is returned  
	 * 
	 * @param type the type whose attribute are requested
	 * @throws MissingTypeException if type is not in the whitelist 
	 * @return the attributed associated to type
	 */
	
	public Set<String> getAttributes(final T type) throws MissingTypeException {
		if (typesAndAttributes.containsKey(type)) {
			return typesAndAttributes.get(type);
		} else {
			throw new MissingTypeException(type);
		}
	}
	
	
	private static Set<String> __flattenToSet(Collection<Set<String>> collection) {
		Set<String> result = new HashSet<>();

		for (Set<String> source : collection) {
			result.addAll(source);
		}

		return result;

	}
	
	/**
	 * Returns a set containing the union of all attributes associated to any type in the whitelist.
	 * If all types in the whitelist have no attribute defined, an empty set is returned  
	 * 
	 * @return all the attributes associated to any type in the whitelist.
	 */
	public Set<String> getAllAttributes() {
		
		Collection<Set<String>> attributes = typesAndAttributes.values();		
		return __flattenToSet(attributes);	
		
	}
	
	/**
	 * Returns a map linking each type in the whitelist to the corresponding set of attributes. 
	 * If a type has no attribute defined, the corresponding entry in the map links to an empty set.   
	 *   
	 * @return the types and attributes in the whitelist
	 */
	
	public Map<T, Set<String>> getRawWhitelist() {
		return typesAndAttributes;
	}
	
	
	/** 
	 * Determines whether the whitelist is empty.
	 * @return true if the whitelist is empty, false otherwise 
	 */
	
	
	public boolean isEmpty() {
		return typesAndAttributes.size()==0;
	}
	
	
	/** 
	 * Returns an empty whitelist, i.e., a whitelist with no types within it. 
	 * @return an empty whitelist
	 */

	public static <T extends AbstractType> Whitelist<T> empty() {
		return new Whitelist<>(new HashMap<>());
	}
	
	
}
