package wyvern.target.corewyvernIL.support;

import wyvern.target.corewyvernIL.type.ValueType;

public abstract class TypeContext {
	public TypeContext extend(String var, ValueType type) {
		return new VarBindingContext(var, type, this);
	}

	/*
	 * Returns the type of this variable in the context
	 */
	public abstract ValueType lookupTypeOf(String varName);

	public boolean isPresent(String varName, boolean isValue) {
		if (getNext() == null) {
			return false;
		} else {
			return getNext().isPresent(varName, isValue);
		}
	}

	public static TypeContext empty() {
		return theEmpty;
	}

	private static TypeContext theEmpty = new EmptyTypeContext();

	protected abstract TypeContext getNext();
	protected abstract String endToString();
}
