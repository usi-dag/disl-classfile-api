package ch.usi.dag.disl.preprocessor;

public class Exceptions {

	private Exceptions() {
		//Prevents instantiation. 
	}
	
	public static class TypeException extends RuntimeException {
		/**
		 * 
		 */
		private static final long serialVersionUID = 2142233393957686303L;
		private AbstractType type;
		
		public TypeException(final AbstractType type) {
			this.type = type;
		}
		
		public AbstractType getType() {
			return this.type;
		}
	}
	
	public static class DuplicateTypeException extends TypeException {
		
		private static final long serialVersionUID = -8780796561673296207L;

		public DuplicateTypeException(final AbstractType type) {
			super(type);			
		}
				
	}
	
	public static class MissingTypeException extends TypeException {

		private static final long serialVersionUID = 4586472081875243318L;

		public MissingTypeException(final AbstractType type) {
			super(type);			
		}

		
	}
	
	public static class UnknownSupertypeException extends TypeException {

		private static final long serialVersionUID = 3976136729893397096L;

		public UnknownSupertypeException(final AbstractType type) {
			super(type);			
		}
		
	}
	
	public static class TypeCreationException extends RuntimeException {
		
		private static final long serialVersionUID = 5779634495976979498L;
		private String typeName;
		
		public TypeCreationException(final String typeName) {
			this.typeName = typeName;
		}
		
		public String getTypeName() {
			return typeName;
		}
		
	}
	
	public static class IllegalFileFormatException extends Exception {

		private static final long serialVersionUID = 4279278378119447590L;
		private String fileName;
		
		public IllegalFileFormatException(final String fileName) {
			this.fileName = fileName;
		}
		
		public String getFileName() {
			return fileName;
		}

		
	}
	
//	public static class SuperTypeNotAvailableException extends Exception {
//
//		private static final long serialVersionUID = -8072076157290146450L;
//
//	}
	
	public static class TypeNodeException extends RuntimeException {
		
		private static final long serialVersionUID = 2656195767876138358L;
		private TypeNode typeNode;
		
		public TypeNodeException(final TypeNode typeNode) {
			this.typeNode = typeNode;
		}
		
		public TypeNode getNode() {
			return typeNode;
		}
		
	}
	
	public static class NodeNotWhiteException extends TypeNodeException {

		public NodeNotWhiteException(final TypeNode typeNode) {
			super(typeNode);			
		}

		private static final long serialVersionUID = -1198558182510947172L;
		
	}
	
}
