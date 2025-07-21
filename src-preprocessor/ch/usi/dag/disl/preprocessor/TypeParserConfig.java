package ch.usi.dag.disl.preprocessor;

import java.io.PrintStream;

import org.kohsuke.args4j.Option;

public class TypeParserConfig {

	/**
	 * Class containing the default configuration for the parser. 
	 * <p>
	 * To change the configuration, individual fields can be changed by running class 
	 * Parser passing the flags specified in the @Option annotations below.
	 * Alternatively, developers can manually change the value of the fields after having 
	 * obtained the singleton instance (via getInstance).   
	 * <p>   
	 * Getter methods are defined for convenience, though not necessary. 
	 * 
	 */

	@Option(name="-blackTypeFile", usage="path to output textual file containing the black types")
	public String blackTypeFile = "output/blackTypes.txt";

	@Option(name="-whiteTypeFile", usage="path to output textual file containing the white types")
	public String whiteTypeFile = "output/whiteTypes.txt";

	@Option(name="-attributeFile", usage="path to output textual file containing the white types and the corresponding attributes")
	public String attributeFile = "output/whiteTypesWithAttributes.txt";


	@Option(name="-blacklistFile", usage="path to input textual file containing the types defining the blacklist")
	public String blacklistFile = "input/blacklist.txt";

	@Option(name="-whitelistFile", usage="path to input textual file containing the types and attributes defining the whitelist")
	public String whitelistFile = "input/whitelist.txt";


	@Option(name="-outputTypeGraphFile", usage="path to output file containing the serialized type graph")
	public String outputTypeGraphFile = "output/typeGraph.ser";

	@Option(name="-outputTypeGraphTextFile", usage="path to output file containing the textual representation of the type graph")
	public String outputTypeGraphTextFile = "output/typeGraph.txt";
	
	@Option(name="-inputTypeGraphFile", usage="path to input file containing the type graph to deserialize")
	public String inputTypeGraphFile = "input/typeGraph.ser";

	@Option(name="-log", usage="enable/disables logging to standard error")
	public boolean loggingEnabled = false; 


	PrintStream logger = System.err;

	private static TypeParserConfig instance = null;

	protected TypeParserConfig() {
		//Prevents instantiation
	}

	public static TypeParserConfig getInstance() {
		if (instance == null) {
			instance = new TypeParserConfig();
		}

		return instance;
	}

	public String getBlackTypesFilePath() {
		return blackTypeFile;
	}

	public String getBlacklistFilePath() {
		return blacklistFile;
	}

	public String getWhiteTypesFilePath() {
		return whiteTypeFile;
	}

	public String getWhitelistFilePath() {
		return whitelistFile;
	}

	public String getAttributesFilePath() {
		return attributeFile;
	}

	public String getTypeGraphInputFilePath() {
		return inputTypeGraphFile;
	}

	public String getTypeGraphOutputFilePath() {
		return outputTypeGraphFile;
	}

	public String getTypeGraphTextFilePath() {		
		return outputTypeGraphTextFile;
	}
}



