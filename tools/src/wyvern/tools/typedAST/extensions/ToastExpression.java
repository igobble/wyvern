package wyvern.tools.typedAST.extensions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import wyvern.target.corewyvernIL.expression.Expression;
import wyvern.target.corewyvernIL.modules.TypedModuleSpec;
import wyvern.target.corewyvernIL.support.GenContext;
import wyvern.target.corewyvernIL.type.ValueType;
import wyvern.tools.errors.FileLocation;
import wyvern.tools.typedAST.abs.AbstractExpressionAST;
import wyvern.tools.typedAST.extensions.interop.java.Util;
import wyvern.tools.typedAST.interfaces.ExpressionAST;
import wyvern.tools.typedAST.interfaces.TypedAST;
import wyvern.tools.typedAST.interfaces.Value;
import wyvern.tools.types.Environment;
import wyvern.tools.types.Type;
import wyvern.tools.util.EvaluationEnvironment;

public class ToastExpression extends AbstractExpressionAST implements ExpressionAST {
	private TypedAST exn;
	public ToastExpression(TypedAST exn) {
		this.exn = exn;
	}

	@Override
	public Type getType() {
		return Util.javaToWyvType(TypedAST.class);
	}

	@Override
	public Type typecheck(Environment env, Optional<Type> expected) {
		exn.typecheck(env, expected);
		return Util.javaToWyvType(TypedAST.class);
	}

	@Override
    @Deprecated
	public Value evaluate(EvaluationEnvironment env) {
		Value res = exn.evaluate(env);

		return Util.toWyvObj(res);
	}

	@Override
	public Map<String, TypedAST> getChildren() {
		Map<String, TypedAST> result = new HashMap<>(1);
		result.put("exn", exn);
		return result;
	}

	@Override
	public TypedAST cloneWithChildren(Map<String, TypedAST> newChildren) {
		return new ToastExpression(newChildren.get("exn"));
	}

    @Override
	public FileLocation getLocation() {
		return null;
	}

	@Override
	public Expression generateIL(GenContext ctx, ValueType expectedType, List<TypedModuleSpec> dependencies) {
		// TODO Auto-generated method stub
		return null;
	}
}
