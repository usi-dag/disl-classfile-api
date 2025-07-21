package ch.usi.dag.disl.preprocessor;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Class representing the blacklist of a TypeGraph.     
 * 
 * @author Andrea Rosa'
 *
 * @param <T> the type used to represent types in this blacklist
 */

public class Blacklist<T extends AbstractType> implements Serializable {
	
	private static final long serialVersionUID = -4390206958440489573L;
	private Set<T> types;

	/**
	 * Create a new blacklist, inserting types into it.  
	 * 
	 * @param types the types to insert into the blacklist
	 */
	
	public Blacklist(final Set<T> types) {
		this.types = types;
	}
	
	/**
	 * Returns the types inside the blacklist 
	 * @return the types inside the blacklist
	 */
	
	public Set<T> getTypes() {
		return types;
	}
	
	/** 
	 * Returns an empty blacklist, i.e., a blacklist with no types within it. 
	 * @return an empty blacklist
	 */
	
	public static <T extends AbstractType> Blacklist<T> empty() {
		return new Blacklist<>(new HashSet<>());
	}
	
	/** 
	 * Determines whether the blacklist is empty.
	 * @return true if the blacklist is empty, false otherwise 
	 */
	
	public boolean isEmpty() {
		return types.size()==0;
	}

}
