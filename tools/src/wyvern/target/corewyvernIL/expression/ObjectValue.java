package wyvern.target.corewyvernIL.expression;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import wyvern.target.corewyvernIL.decl.Declaration;
import wyvern.target.corewyvernIL.decl.DeclarationWithRHS;
import wyvern.target.corewyvernIL.decl.DefDeclaration;
import wyvern.target.corewyvernIL.decl.DelegateDeclaration;
import wyvern.target.corewyvernIL.support.EvalContext;
import wyvern.target.corewyvernIL.type.ValueType;
import wyvern.tools.errors.ErrorMessage;
import wyvern.tools.errors.FileLocation;
import wyvern.tools.errors.ToolError;

public class ObjectValue extends New implements Invokable {
    private final EvalContext evalCtx; // captured eval context
    private final boolean hasDelegate;
    private ObjectValue delegateTarget;

    /** Precondition: the decls argument must be unique.
     * It is owned by this ObjectValue after the constructor call.
     */
    public ObjectValue(List<Declaration> decls, String selfName, ValueType exprType, DelegateDeclaration delegateDecl, FileLocation loc, EvalContext ctx) {
        super(decls, selfName, exprType, loc);

        if (selfName == null || selfName.length() == 0) {
            throw new RuntimeException("selfName invariant violated");
        }
        evalCtx = ctx.extend(selfName, this);
        hasDelegate = (delegateDecl != null);
        if (hasDelegate) {
            delegateTarget = (ObjectValue) ctx.lookupValue(delegateDecl.getFieldName());
        }
        // assert that this ObjectValue is well-formed
        checkWellFormed();
    }

    /** already a value */
    @Override
    public Value interpret(EvalContext ctx) {
        return this;
    }

    @Override
    public Value invoke(String methodName, List<Value> args) {
        EvalContext methodCtx = evalCtx;
        DefDeclaration dd = (DefDeclaration) findDecl(methodName);
        if (dd != null) {
            if (args.size() != dd.getFormalArgs().size()) {
                throw new RuntimeException("invoke called on " + methodName + " with " + args.size() + " arguments, "
                        + "but " + dd.getFormalArgs().size() + " were expected");
            }
            for (int i = 0; i < args.size(); ++i) {
                methodCtx = methodCtx.extend(dd.getFormalArgs().get(i).getName(), args.get(i));
            }
            return dd.getBody().interpret(methodCtx);
        } else if (hasDelegate) {
            return delegateTarget.invoke(methodName, args);
        } else {
            ToolError.reportError(ErrorMessage.DYNAMIC_METHOD_ERROR, this, methodName);
            throw new RuntimeException("can't reach here");
        }
    }

    @Override
    public Value getField(String fieldName) {
        DeclarationWithRHS decl = (DeclarationWithRHS) findDecl(fieldName);
        if (decl != null) {
            return (Value) decl.getDefinition();
        } else if (delegateTarget != null && delegateTarget.findDecl(fieldName) != null) {
            return delegateTarget.getField(fieldName);
        }

        throw new RuntimeException("can't find field: " + fieldName);
    }

    public void setDecl(Declaration decl) {
        List<Declaration> decls = this.getDecls();
        for (int i = 0; i < decls.size(); ++i) {
            if (decl.getName().equals(decls.get(i).getName())) {
                decls.set(i, decl);
                return;
            }
        }
        throw new RuntimeException("cannot set decl " + decl.getName());
    }

    public EvalContext getEvalCtx() {
        return this.evalCtx;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ObjectValue other = (ObjectValue) obj;

        // Other ObjectValue needs the same declarations, in the same order.
        if (this.getDecls().size() != other.getDecls().size()) {
            return false;
        }
        return this.getDecls().equals(other.getDecls());

    }

    @Override
    public int hashCode() {
        return evalCtx.hashCode() + delegateTarget.hashCode();
    }

    /** make sure all free variables are captured in the evalCtx */
    public void checkWellFormed() {
        Set<String> freeVars = this.getFreeVariables();
        for (String varName : freeVars) {
            evalCtx.lookupValue(varName);
        }
    }

    /** no free variables because each ObjectValue closes over its environment */
    @Override
    public Set<String> getFreeVariables() {
        return (Set<String>) Collections.EMPTY_SET;
    }
}
