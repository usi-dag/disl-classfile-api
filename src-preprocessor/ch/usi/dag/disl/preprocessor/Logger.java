package ch.usi.dag.disl.preprocessor;

class Logger {

	private static TypeParserConfig config = TypeParserConfig.getInstance(); 
	
	static void println(String s) {
		if (config.loggingEnabled) {
			config.logger.println(s);
		}
	}
	
}
