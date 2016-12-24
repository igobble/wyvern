package wyvern.tools.typedAST.core.declarations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import wyvern.stdlib.Globals;
import wyvern.target.corewyvernIL.decltype.ConcreteTypeMember;
import wyvern.target.corewyvernIL.decltype.DeclType;
import wyvern.target.corewyvernIL.decltype.DefDeclType;
import wyvern.target.corewyvernIL.decltype.VarDeclType;
import wyvern.target.corewyvernIL.expression.Expression;
import wyvern.target.corewyvernIL.expression.IExpr;
import wyvern.target.corewyvernIL.expression.New;
import wyvern.target.corewyvernIL.expression.ObjectValue;
import wyvern.target.corewyvernIL.expression.Variable;
import wyvern.target.corewyvernIL.modules.TypedModuleSpec;
import wyvern.target.corewyvernIL.support.GenContext;
import wyvern.target.corewyvernIL.support.TopLevelContext;
import wyvern.target.corewyvernIL.type.StructuralType;
import wyvern.tools.errors.FileLocation;
import wyvern.tools.errors.WyvernException;
import wyvern.tools.typedAST.abs.Declaration;
import wyvern.tools.typedAST.core.binding.compiler.MetadataInnerBinding;
import wyvern.tools.typedAST.core.binding.typechecking.LateNameBinding;
import wyvern.tools.typedAST.core.binding.typechecking.TypeBinding;
import wyvern.tools.typedAST.core.declarations.DeclSequence;
import wyvern.tools.typedAST.core.declarations.TypeDeclaration;
import wyvern.tools.typedAST.core.expressions.TaggedInfo;
import wyvern.tools.typedAST.core.values.Obj;
import wyvern.tools.typedAST.core.values.UnitVal;
import wyvern.tools.typedAST.interfaces.EnvironmentExtender;
import wyvern.tools.typedAST.interfaces.ExpressionAST;
import wyvern.tools.typedAST.interfaces.TypedAST;
import wyvern.tools.typedAST.interfaces.Value;
import wyvern.tools.typedAST.transformers.ExpressionWriter;
import wyvern.tools.typedAST.transformers.GenerationEnvironment;
import wyvern.tools.typedAST.transformers.ILWriter;
import wyvern.tools.types.Environment;
import wyvern.tools.types.Type;
import wyvern.tools.types.TypeResolver;
import wyvern.tools.util.AbstractTreeWritable;
import wyvern.tools.util.EvaluationEnvironment;
import wyvern.tools.util.Reference;
import wyvern.tools.util.TreeWriter;

public class TypeVarDecl extends Declaration {
	private final String name;
	private final EnvironmentExtender body;
	private final FileLocation fileLocation;
	private final Reference<Optional<TypedAST>> metadata;
	private final Reference<Value> metadataObj;
	private TaggedInfo taggedInfo = null;
	private boolean resourceFlag = false;
    private final String defaultSelfName = "this";
    private String activeSelfName;
    private IExpr metadataExp = null;

	/**
	 * Helper class to allow easy variation of bound types
	 */
	private abstract class EnvironmentExtInner extends AbstractTreeWritable implements EnvironmentExtender {

		private final FileLocation loc;

		public EnvironmentExtInner(FileLocation loc) {
			this.loc = loc;
		}

		@Override
		public Environment extendName(Environment env, Environment against) {
			return env;
		}

		@Override
		public Environment extend(Environment env, Environment against) {
			return env;
		}

		@Override
		public EvaluationEnvironment evalDecl(EvaluationEnvironment env) {
			return env;
		}
		@Override
		public Type typecheck(Environment env, Optional<Type> expected) {
			return getType();
		}

		@Override
        @Deprecated
		public Value evaluate(EvaluationEnvironment env) {
			return UnitVal.getInstance(loc);
		}

		@Override
		public Map<String, TypedAST> getChildren() {
			return new HashMap<>();
		}

		@Override
		public TypedAST cloneWithChildren(Map<String, TypedAST> newChildren) {
			return this;
		}

		@Override
		public FileLocation getLocation() {
			return loc;
		}
	}

	public TypeVarDecl(String name, DeclSequence body, TypedAST metadata, FileLocation fileLocation) {
		this.metadata = new Reference<Optional<TypedAST>>(Optional.ofNullable(metadata));
		this.name = name;
		this.metadataObj = new Reference<>(new Obj(EvaluationEnvironment.EMPTY, null));
		this.body = new TypeDeclaration(name, body, this.metadataObj, fileLocation);
		this.fileLocation = fileLocation;
	}

	public TypeVarDecl(String name, DeclSequence body, TaggedInfo taggedInfo, TypedAST metadata, FileLocation fileLocation, boolean isResource, String selfName ) {
		this.metadata = new Reference<Optional<TypedAST>>(Optional.ofNullable(metadata));
		this.name = name;
		this.metadataObj = new Reference<>(new Obj(EvaluationEnvironment.EMPTY, null));
		this.body = new TypeDeclaration(name, body, this.metadataObj, taggedInfo, fileLocation);
		this.fileLocation = fileLocation;
		this.taggedInfo = taggedInfo;
		this.resourceFlag = isResource;
        this.activeSelfName = selfName;
	}

	public TypeVarDecl(String name, DeclSequence body, TaggedInfo taggedInfo, TypedAST metadata, FileLocation fileLocation){
		this.metadata = new Reference<Optional<TypedAST>>(Optional.ofNullable(metadata));
		this.name = name;
		this.metadataObj = new Reference<>(new Obj(EvaluationEnvironment.EMPTY, null));
		this.body = new TypeDeclaration(name, body, this.metadataObj, taggedInfo, fileLocation);
		this.fileLocation = fileLocation;
	}
	
	private TypeVarDecl(String name, EnvironmentExtender body, Reference<Optional<TypedAST>> metadata, Reference<Value> metadataObj, FileLocation location) {
		this.name = name;
		this.body = body;
		this.metadata = metadata;
		this.metadataObj = metadataObj;
		fileLocation = location;
	}

	public TypeVarDecl(String name, Type body, TypedAST metadata, FileLocation fileLocation) {
		this.name = name;
		this.body = new EnvironmentExtInner(fileLocation) {
			@Override
			public Environment extendType(Environment env, Environment against) {
				Type type = TypeResolver.resolve(body, against);
				return env.extend(new TypeBinding(name, type, metadataObj))
						.extend(new LateNameBinding(name, () -> metadataObj.get().getType()));
			}



			@Override
			public Type getType() {
				return body;
			}


		};

		this.fileLocation = fileLocation;
		this.metadata = new Reference<>(Optional.ofNullable(metadata));
		this.metadataObj = new Reference<>(new Obj(EvaluationEnvironment.EMPTY, null));
	}

	public TypeVarDecl(String name, EnvironmentExtender body, FileLocation fileLocation) {
		this.body = body;
		this.name = name;
		this.fileLocation = fileLocation;
		metadata = new Reference<Optional<TypedAST>>(Optional.empty());
		this.metadataObj = new Reference<>(new Obj(EvaluationEnvironment.EMPTY, null));
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	protected Type doTypecheck(Environment env) {
		evalMeta(env);
		return body.typecheck(env, Optional.<Type>empty());
	}

	@Override
	protected Environment doExtend(Environment old, Environment against) {
		return body.extend(old, against);
	}

	@Override
	public EvaluationEnvironment extendWithValue(EvaluationEnvironment old) {
		return body.evalDecl(old);
	}

	@Override
	public void evalDecl(EvaluationEnvironment evalEnv, EvaluationEnvironment declEnv) {
		body.evalDecl(declEnv);
	}

	@Override
	public Environment extendType(Environment env, Environment against) {
		return body.extendType(env, against);
	}

	@Override
	public Environment extendName(Environment env, Environment against) {
		return body.extendName(env, against);
	}

    @Deprecated
	private void evalMeta(Environment evalEnv) {
		MetadataInnerBinding extMetaEnv = evalEnv
				.lookupBinding("metaEnv", MetadataInnerBinding.class).orElseGet(() -> MetadataInnerBinding.EMPTY);

		Environment metaTcEnv = Globals.getStandardEnv().extend(extMetaEnv.getInnerEnv());
		EvaluationEnvironment metaEnv = Globals.getStandardEvalEnv().extend(TypeDeclaration.attrEvalEnv).extend(extMetaEnv.getInnerEvalEnv());
		metadata.get().map(obj->obj.typecheck(metaTcEnv, Optional.<Type>empty()));

		metadataObj.set(metadata.get().map(obj -> obj.evaluate(metaEnv)).orElse(new Obj(EvaluationEnvironment.EMPTY, null)));
	}

	@Override
	public Type getType() {
		return null;
	}

	@Override
	public Map<String, TypedAST> getChildren() {
		HashMap<String,TypedAST> out = new HashMap<>();
		out.put("body", body);
		if (metadata.get().isPresent())
			out.put("metadata", metadata.get().get());
		return out;
	}

	@Override
	public TypedAST cloneWithChildren(Map<String, TypedAST> newChildren) {
		metadata.set(Optional.ofNullable(newChildren.get("metadata")));
		return new TypeVarDecl(name, (EnvironmentExtender)newChildren.get("body"), metadata, metadataObj, fileLocation);
	}

    @Override
	public FileLocation getLocation() {
		return fileLocation;
	}

	/*@Override
	public Expression generateIL(GenContext ctx) {
		return body.generateIL(ctx);
	}*/

    private String getSelfName() {
        String s = defaultSelfName;
        if (this.activeSelfName != null && this.activeSelfName.length() != 0) {
            s = this.activeSelfName;
        }
        return s;
    }

	private StructuralType computeInternalILType(GenContext ctx) {
		TypeDeclaration td = (TypeDeclaration) this.body;
		GenContext localCtx = ctx.extend(getSelfName(), new Variable(getSelfName()), null);
		return new StructuralType(getSelfName(), td.genDeclTypeSeq(localCtx), this.resourceFlag);
	}
	
	@Override
	public DeclType genILType(GenContext ctx) {
		StructuralType type = computeInternalILType(ctx);
		return new ConcreteTypeMember(getName(), type, getMetadata(ctx));
	}

	@Override
	public wyvern.target.corewyvernIL.decl.Declaration generateDecl(GenContext ctx, GenContext thisContext) {
		return computeInternalDecl(thisContext);
	}
	
	private IExpr getMetadata(GenContext ctx) {
		if (metadata.get().isPresent()) {
			if (metadataExp == null)
				metadataExp = ((ExpressionAST)metadata.get().get()).generateIL(ctx, null, null);
			return metadataExp;
		} else {
			return null;
		}
	}

	private wyvern.target.corewyvernIL.decl.Declaration computeInternalDecl(GenContext ctx) {
		StructuralType type = computeInternalILType(ctx);
		return new wyvern.target.corewyvernIL.decl.TypeDeclaration(getName(), type, getMetadata(ctx), getLocation());
	}
	
	public boolean isResource() {
		return this.resourceFlag;
	}

	@Override
	public wyvern.target.corewyvernIL.decl.Declaration topLevelGen(GenContext ctx, List<TypedModuleSpec> dependencies) {
		return computeInternalDecl(ctx);
	}
	
	@Override
	public void addModuleDecl(TopLevelContext tlc) {
		wyvern.target.corewyvernIL.decl.Declaration decl = computeInternalDecl(tlc.getContext());
		DeclType dt = genILType(tlc.getContext());
		tlc.addModuleDecl(decl,dt);
	}
}
