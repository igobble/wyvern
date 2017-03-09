package wyvern.tools.reflection;

import java.util.ArrayList;
import java.util.List;

import wyvern.target.corewyvernIL.decl.Declaration;
import wyvern.target.corewyvernIL.decl.DeclarationWithRHS;
import wyvern.target.corewyvernIL.expression.BooleanLiteral;
import wyvern.target.corewyvernIL.expression.IntegerLiteral;
import wyvern.target.corewyvernIL.expression.JavaValue;
import wyvern.target.corewyvernIL.expression.ObjectValue;
import wyvern.target.corewyvernIL.expression.Value;
import wyvern.target.corewyvernIL.support.EvalContext;
import wyvern.target.corewyvernIL.type.NominalType;
import wyvern.target.corewyvernIL.type.StructuralType;
import wyvern.target.corewyvernIL.type.ValueType;
import wyvern.tools.interop.JObject;

/**
 * Created by ewang on 2/16/16.
 */
public class Mirror {

	private boolean valueEquals(Value v1, Value v2) {
		// TODO: JavaValue, RationalLiteral, StringLiteral
		if (v1 instanceof BooleanLiteral && v2 instanceof BooleanLiteral) {
			return ((BooleanLiteral) v1).getValue() == ((BooleanLiteral) v2).getValue();
		}
		if (v1 instanceof IntegerLiteral && v2 instanceof IntegerLiteral) {
			return ((IntegerLiteral) v1).getValue() == ((IntegerLiteral) v2).getValue();
		}
		if (v1 instanceof ObjectValue && v2 instanceof ObjectValue) {
			return (1 == equals((ObjectValue) v1, (ObjectValue) v2));
		}
		return false;
	}

	public int equals(ObjectValue o1, ObjectValue o2) {
		EvalContext evalCtx = o1.getEvalCtx();
		// o2 is an ObjectMirror
		Value obj = o2.getField("original");
		if (!o1.getType().equalsInContext(obj.getType(), evalCtx)) {
			return 0;
		}
		if (obj instanceof ObjectValue) {
			List<? extends Declaration> objDecls = ((ObjectValue) obj).getDecls();
			for (Declaration decl : objDecls) {
				if (decl instanceof DeclarationWithRHS) {
					Declaration o1Decl = o1.findDecl(decl.getName());
					if (o1Decl == null || !(o1Decl instanceof DeclarationWithRHS)) {
						return 0;
					}
					Value declVal = ((DeclarationWithRHS) decl)
							.getDefinition().interpret(evalCtx);
					Value o1DeclVal = ((DeclarationWithRHS) o1Decl)
							.getDefinition().interpret(evalCtx);
					if (!valueEquals(declVal, o1DeclVal)) {
						return 0;
					}
				}
			}
		}
		return 1;
	}

	public StructuralType getObjectType(ObjectValue o) {
		return o.getType().getStructuralType(o.getEvalCtx());
	}

	public Value invoke(ObjectValue o, String methodName, List<Value> argList) {
		return o.invoke(methodName, argList);
	}

	public int equalTypes(ObjectValue type1, ObjectValue type2) {
		EvalContext evalCtx = type1.getEvalCtx();
		List<Object> args = new ArrayList<>();
		args.add(evalCtx);
		args.add(null);

		JavaValue typeOrig1 = (JavaValue) (type1.getField("valType"));
		JavaValue typeOrig2 = (JavaValue) (type1.getField("valType"));
		try {
			StructuralType structType1 = (StructuralType)
					((JObject) (typeOrig1.getFObject())).invokeMethod("getStructuralType", args);
			StructuralType structType2 = (StructuralType)
					((JObject) (typeOrig2.getFObject())).invokeMethod("getStructuralType", args);
			if (structType1.equalsInContext(structType2, evalCtx)) {
				return 1;
			}
			return 0;
		} catch (ReflectiveOperationException e) {
			return 0;
		}
	}

	public List<String> getFieldNames(JavaValue type) {
		// TODO
		return new ArrayList<>();
	}

	public ValueType getFieldType(JavaValue type, String fieldName) {
		// TODO
		return new StructuralType("Implement Later", new ArrayList<>());
	}

	public List<String> getMethodArgNames(JavaValue type) {
		// TODO
		return new ArrayList<>();
	}

	public ValueType getMethodRetType(JavaValue type, String methodName) {
		// TODO
		return new StructuralType("Implement Later", new ArrayList<>());
	}

	public List<String> getMethodNames(JavaValue type) {
		// TODO
		return new ArrayList<>();
	}

	public String typeName(ObjectValue obj) throws Exception {
		ValueType type = obj.getType();
		if (type instanceof NominalType) {
			return ((NominalType) type).getTypeMember();
		}
		throw new Exception("Error: Requested name of a structural type");
		// return obj.getType().toString();
	}

	public int equalMethods(ObjectValue m1, ObjectValue m2) {
		// TODO
		return 0;
	}

	public int equalFields(ObjectValue m1, ObjectValue m2) {
		// TODO
		return 0;
	}
}
