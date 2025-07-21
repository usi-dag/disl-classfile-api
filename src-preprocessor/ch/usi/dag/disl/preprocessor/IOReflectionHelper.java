package ch.usi.dag.disl.preprocessor;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;

import ch.usi.dag.disl.preprocessor.Exceptions.IllegalFileFormatException;
import ch.usi.dag.disl.preprocessor.Exceptions.TypeCreationException;

/** 
 * Class containing helper methods to import a blacklist/whitelist containing ReflectionTypes. 
 * 
 * @author Andrea Rosa'
 *
 */



public class IOReflectionHelper extends IOAbstractHelper<ReflectionType> {

	


	private  ReflectionType __obtainClassViaReflection(final String typeName) throws TypeCreationException {
		try {
			return new ReflectionType(Class.forName(typeName), AbstractType.BOOTSTRAP_CLASSLOADER);
		} catch (ClassNotFoundException e) {
			throw new TypeCreationException(typeName);
		}
	}

	private  void __parseWhiteListLineReflection(final String entry, final Map<ReflectionType, Set<String>> whiteList) throws IllegalFileFormatException, IOException {
		
		StringTokenizer tokenizer = new StringTokenizer(entry, ",");

		//There should be only two tokens per line 
		
		String typeName, attribute;
		
		try {
		
			typeName  = tokenizer.nextToken();
			attribute = tokenizer.nextToken();
		} catch (NoSuchElementException e) {
			throw new IllegalFileFormatException(config.getWhitelistFilePath());
		}

		if (tokenizer.hasMoreTokens()) {
			throw new IllegalFileFormatException(config.getWhitelistFilePath());
		}

		ReflectionType rt = __obtainClassViaReflection(typeName);
		__addToWhitelist(rt, attribute, whiteList);


	}

	private  void __addToWhitelist(final ReflectionType type, final String attribute, final Map<ReflectionType, Set<String>> whiteList) {

		Set<String> set;

		if (!whiteList.containsKey(type)) {
			set = new HashSet<>();		
			whiteList.put(type, set);
		} else {
			set = whiteList.get(type);
		}

		set.add(attribute); //Since this is a set, no duplicated elements can be inserted.

	}

	
	 
	@Override
	public Whitelist<ReflectionType> importWhitelist() throws TypeCreationException, IOException, IllegalFileFormatException {

		Scanner s = new Scanner(new File(config.getWhitelistFilePath())); //Throws a subclass of IOException if the file is not found

		Map<ReflectionType, Set<String>> typesAndAttribute = new HashMap<>();

		while (s.hasNext()) {
			__parseWhiteListLineReflection(s.next(), typesAndAttribute);
		}

		s.close();
		return new Whitelist<ReflectionType>(typesAndAttribute);
		
		
	}


	public Blacklist<ReflectionType> importBlacklist() throws TypeCreationException, IOException {

		Scanner s = new Scanner(new File(config.getBlacklistFilePath())); //Throws a subclass of IOException if the file is not found

		Set<ReflectionType> blacklist = new HashSet<>();

		while (s.hasNext()) {
			ReflectionType type;
			type = __obtainClassViaReflection(s.next());			
			blacklist.add(type); //Since blacklist is a set, no duplicated elements can be inserted. Remember that instances of 
								 //AbstractTypes use only their name to define equality! 
		}

		s.close();

		return new Blacklist<ReflectionType>(blacklist);


	}



}
