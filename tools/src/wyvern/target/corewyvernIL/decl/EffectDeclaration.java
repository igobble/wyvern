package wyvern.target.corewyvernIL.decl;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import wyvern.target.corewyvernIL.astvisitor.ASTVisitor;
import wyvern.target.corewyvernIL.decltype.ConcreteTypeMember;
import wyvern.target.corewyvernIL.decltype.DeclType;
import wyvern.target.corewyvernIL.decltype.EffectDeclType;
import wyvern.target.corewyvernIL.expression.Effect;
import wyvern.target.corewyvernIL.expression.IExpr;
import wyvern.target.corewyvernIL.support.TypeContext;
import wyvern.target.corewyvernIL.type.Type;
import wyvern.target.corewyvernIL.type.ValueType;
import wyvern.tools.errors.ErrorMessage;
import wyvern.tools.errors.FileLocation;
import wyvern.tools.errors.ToolError;

public class EffectDeclaration extends NamedDeclaration {
	private Set<Effect> effectSet;
	
	public EffectDeclaration(String name, Set<Effect> effectSet, FileLocation loc) {
		super(name, loc);
		this.effectSet = effectSet;
	}
	
	@Override
	public <S, T> T acceptVisitor(ASTVisitor <S, T> emitILVisitor,
			S state) {
//		return null; 
		return emitILVisitor.visit(state, this);
	}

	public Set<Effect> getEffectSet() {
		return effectSet;
	}
	
	@Override
	public DeclType getDeclType() {
		return new EffectDeclType(getName(), getEffectSet(), getLocation());
	}

	@Override
	public DeclType typeCheck(TypeContext ctx, TypeContext thisCtx) { // technically "effectCheck"
		for (Effect e : effectSet) { // ex. "fio.read"
			String ePathName = e.getPath().getName(); // "fio"
			ValueType vt = ctx.lookupTypeOf(ePathName);
			if (vt == null){
//				throw new RuntimeException("Path not found.");
				ToolError.reportError(ErrorMessage.VARIABLE_NOT_DECLARED, this, ePathName);
			} else {
				String eName = e.getName(); // "read"
				DeclType eDT = vt.findDecl(eName, ctx); // the effect definition as appeared in the type (ex. "effect receive = ")
//				DeclType actualEDT = e.getDeclType(getEffectSet()); // not necessary??
				if (eDT==null) {//||	(!eDT.equals(actualEDT))) {
//					throw new RuntimeException("Effect name not found in path.");
					ToolError.reportError(ErrorMessage.EFFECT_NOT_FOUND, this, eName, ePathName);
				}
			}
		}
		return getDeclType();
	}

	@Override
	public void doPrettyPrint(Appendable dest, String indent) throws IOException {
		dest.append(indent).append("effect ").append(getName()).append(" = ");
		if (effectSet != null)
			dest.append(effectSet.toString());
		dest.append('\n');
	}
	
	
	@Override
	public Set<String> getFreeVariables() {
		// TODO Auto-generated method stub
		return new HashSet<String>(); // this should either be an empty HashSet, or the entire effectSet...
//		throw new RuntimeException("getFreeVars");
	}
}