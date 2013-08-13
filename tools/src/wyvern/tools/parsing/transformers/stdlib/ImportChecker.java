package wyvern.tools.parsing.transformers.stdlib;

import wyvern.tools.errors.ErrorMessage;
import wyvern.tools.errors.ToolError;
import wyvern.tools.parsing.transformers.TransformerBase;
import wyvern.tools.parsing.transformers.TypedASTTransformer;
import wyvern.tools.typedAST.core.Sequence;
import wyvern.tools.typedAST.core.declarations.ImportDeclaration;
import wyvern.tools.typedAST.interfaces.TypedAST;

import java.util.*;

/**
 * Created by Ben Chung on 8/12/13.
 */
public class ImportChecker extends TransformerBase<TypedAST> {
	public <T extends TypedAST> ImportChecker(TypedASTTransformer<T> base) {
		super(base);
	}

	private HashSet<TypedAST> visited = new HashSet<>();
	private static class Node {
		private LinkedList<Node> links;
		private TypedAST ast;

		private Node(TypedAST ast, LinkedList<Node> links) {
			this.links = links;
			this.ast = ast;
		}
	}

	@Override
	protected TypedAST doTransform(TypedAST transform) {
		if (visited.contains(transform))
			ToolError.reportError(ErrorMessage.MUTUALLY_RECURSIVE_IMPORTS, transform.toString(), transform);
		visited.add(transform);
		findDeclarations(transform);
		return transform;
	}

	private void findDeclarations(TypedAST root) {
		if (root instanceof Sequence) {
			for (TypedAST ast : (Sequence)root)
				findDeclarations(ast);
		}
		if (root instanceof ImportDeclaration) {
			doTransform(((ImportDeclaration) root).getAST());
		}
	}
}
