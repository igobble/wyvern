package wyvern.tools.typedAST.core.binding;

import wyvern.tools.errors.FileLocation;
import wyvern.tools.typedAST.core.expressions.Variable;
import wyvern.tools.typedAST.interfaces.TypedAST;
import wyvern.tools.types.Type;

public class NameBindingImpl extends AbstractBinding implements NameBinding {
	public NameBindingImpl(String name, Type type) {
		super(name, type);
	}

	public TypedAST getUse() {
		// throw new RuntimeException("this method should not be needed!!!");
		return new Variable(this, FileLocation.UNKNOWN); // FIXME: !!! Cannot replicate its use from outside!
	}

	@Override
	public String toString() {
		return "{" + getName() + " : " + getType() + "}";
	}

}