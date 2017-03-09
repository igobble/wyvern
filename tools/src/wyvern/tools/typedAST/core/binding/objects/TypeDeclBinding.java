package wyvern.tools.typedAST.core.binding.objects;

import wyvern.tools.typedAST.core.binding.Binding;
import wyvern.tools.typedAST.core.declarations.TypeDeclaration;
import wyvern.tools.types.Type;

public class TypeDeclBinding implements Binding {
	private final String name;
	private final TypeDeclaration td;

	public TypeDeclBinding(String name, TypeDeclaration td) {
		this.name = name;
		this.td = td;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Type getType() {
		return td.getType();
	}

	public TypeDeclaration getTypeDecl() {
		return td;
	}

	@Override
	public String toString() {
		return "{" + name + " as type  " + td + "}";
	}
}
