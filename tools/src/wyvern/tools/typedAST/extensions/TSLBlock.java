package wyvern.tools.typedAST.extensions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import wyvern.stdlib.Globals;
import wyvern.target.corewyvernIL.expression.Expression;
import wyvern.target.corewyvernIL.modules.TypedModuleSpec;
import wyvern.target.corewyvernIL.support.GenContext;
import wyvern.target.corewyvernIL.type.ValueType;
import wyvern.tools.errors.FileLocation;
import wyvern.tools.typedAST.abs.AbstractExpressionAST;
import wyvern.tools.typedAST.core.binding.Binding;
import wyvern.tools.typedAST.core.binding.evaluation.EvaluationBinding;
import wyvern.tools.typedAST.interfaces.ExpressionAST;
import wyvern.tools.typedAST.interfaces.TypedAST;
import wyvern.tools.typedAST.interfaces.Value;
import wyvern.tools.types.Environment;
import wyvern.tools.types.Type;
import wyvern.tools.util.EvaluationEnvironment;

public class TSLBlock extends AbstractExpressionAST implements ExpressionAST {
	private final TypedAST inner;

	public static class OuterEnviromentBinding implements EvaluationBinding {
		private final EvaluationEnvironment store;

		public OuterEnviromentBinding(EvaluationEnvironment store) {
			this.store = store;
		}

		@Override
		public String getName() {
			return "oev";
		}

		@Override
		public Type getType() {
			return null;
		}

		public EvaluationEnvironment getStore() {
			return store;
		}
	}

	public static class OuterTypecheckBinding implements Binding {
		private final Environment store;

		public OuterTypecheckBinding(Environment store) {
			this.store = store;
		}

		@Override
		public String getName() {
			return "oev";
		}

		@Override
		public Type getType() {
			return null;
		}

		public Environment getStore() {
			return store;
		}
	}

	public TSLBlock(TypedAST inner) {
		this.inner = inner;
	}

	@Override
	public Type getType() {
		return inner.getType();
	}

	@Override
	public Type typecheck(Environment env, Optional<Type> expected) {
		return inner.typecheck(Globals.getStandardEnv().extend(new OuterTypecheckBinding(env)), expected);
	}

	@Override
    @Deprecated
	public Value evaluate(EvaluationEnvironment env) {
		return inner.evaluate(EvaluationEnvironment.EMPTY.extend(new OuterEnviromentBinding(env)));
	}

	@Override
	public Map<String, TypedAST> getChildren() {
		Map<String, TypedAST> result = new HashMap<>(1);
		result.put("inner", inner);
		return result;
	}

	@Override
	public TypedAST cloneWithChildren(Map<String, TypedAST> newChildren) {
		return new TSLBlock(newChildren.get("inner"));
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
