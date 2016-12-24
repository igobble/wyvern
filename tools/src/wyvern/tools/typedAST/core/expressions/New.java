package wyvern.tools.typedAST.core.expressions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

import wyvern.target.corewyvernIL.expression.Expression;
import wyvern.target.corewyvernIL.modules.TypedModuleSpec;
import wyvern.target.corewyvernIL.support.GenContext;
import wyvern.target.corewyvernIL.type.ValueType;
import wyvern.tools.errors.ErrorMessage;
import wyvern.tools.errors.FileLocation;
import wyvern.tools.errors.ToolError;
import wyvern.tools.typedAST.abs.CachingTypedAST;
import wyvern.tools.typedAST.abs.Declaration;
import wyvern.tools.typedAST.core.binding.NameBindingImpl;
import wyvern.tools.typedAST.core.binding.evaluation.HackForArtifactTaggedInfoBinding;
import wyvern.tools.typedAST.core.binding.evaluation.LateValueBinding;
import wyvern.tools.typedAST.core.binding.evaluation.ValueBinding;
import wyvern.tools.typedAST.core.binding.objects.ClassBinding;
import wyvern.tools.typedAST.core.declarations.ClassDeclaration;
import wyvern.tools.typedAST.core.declarations.DeclSequence;
import wyvern.tools.typedAST.core.declarations.DefDeclaration;
import wyvern.tools.typedAST.core.declarations.VarDeclaration;
import wyvern.tools.typedAST.core.values.Obj;
import wyvern.tools.typedAST.interfaces.CoreAST;
import wyvern.tools.typedAST.interfaces.ExpressionAST;
import wyvern.tools.typedAST.interfaces.TypedAST;
import wyvern.tools.typedAST.interfaces.Value;
import wyvern.tools.types.Environment;
import wyvern.tools.types.RecordType;
import wyvern.tools.types.Type;
import wyvern.tools.types.extensions.ClassType;
import wyvern.tools.types.extensions.TypeDeclUtils;
import wyvern.tools.util.EvaluationEnvironment;
import wyvern.tools.util.Reference;

public class New extends CachingTypedAST implements CoreAST {

    private static int generic_num = 0;
    private static int uniqueCounter = 0;
    private static Map<String, Expression> variables = new HashMap<>();

    private FileLocation location = FileLocation.UNKNOWN;
    private ClassDeclaration cls;
    private Map<String, TypedAST> args = new HashMap<String, TypedAST>();
    private boolean isGeneric = false;
    private DeclSequence seq;
    private Type ct;
    private String selfName;

    /**
      * Makes a New expression with the provided mapping, file location, and self name.
      *
      * @param args The mapping from arg name to Expression.
      * @param fileLocation the location in the file where the New expression occurs 
      * @param selfName the name of the object created by this expression, like 'this' in Java
      */
    public New(Map<String, TypedAST> args, FileLocation fileLocation, String selfName) {
        this.args = args;
        this.location = fileLocation;
        this.selfName = selfName;
    }

    /**
      * Makes a New expression with the provided mapping and file location.
      *
      * @param args The mapping from arg name to Expression.
      * @param fileLocation the location in the file where the New expression occurs 
      */
    public New(Map<String, TypedAST> args, FileLocation fileLocation) {
        this.args = args;
        this.location = fileLocation;
        this.selfName = null;
    }

    /**
      * This constructor makes a New expression with the provided declaration sequence.
      *
      * @param seq the list of declaration internal to the object created by this expression
      * @param fileLocation the location in the file where the New expression occurs 
      */
    public New(DeclSequence seq, FileLocation fileLocation) {
        this.seq = seq;
        this.location = fileLocation;
    }

    public void setBody(DeclSequence seq) {
        this.seq = seq;
    }

    public DeclSequence getDecls() {
        return seq;
    }

    /**
     * Resets the count of generics.
     */
    public static void resetGenNum() {
        generic_num = 0;
    }

    private String self() {
        return (this.selfName == null) ? "this" : this.selfName;
    }

    @Override
    protected Type doTypecheck(Environment env, Optional<Type> expected) {
        // TODO check arg types
        // Type argTypes = args.typecheck();

        ClassBinding classVarTypeBinding = (ClassBinding) env.lookupBinding(
            "class", ClassBinding.class
        ).orElse(null);


        if (classVarTypeBinding != null) { //In a class method
            Environment declEnv = classVarTypeBinding.getClassDecl()
                .getInstanceMembersEnv();

            Environment innerEnv = seq.extendName(Environment.getEmptyEnvironment(), env)
                .extend(declEnv);

            innerEnv = env.extend(new NameBindingImpl(this.self(),
                        new ClassType(
                            new Reference<>(innerEnv),
                            new Reference<>(innerEnv),
                            new LinkedList<>(),
                            classVarTypeBinding.getClassDecl().getTaggedInfo(),
                            classVarTypeBinding.getClassDecl().getName()
            )));

            seq.typecheck(innerEnv, Optional.empty());


            Environment environment = seq.extendType(declEnv, declEnv.extend(env));
            environment = seq.extendName(environment, environment.extend(env));
            Environment nnames = environment;//seq.extend(environment, environment);

            Environment objTee = TypeDeclUtils.getTypeEquivalentEnvironment(
                    nnames.extend(declEnv)
            );
            Type classVarType = new ClassType(
                    new Reference<>(nnames.extend(declEnv)), 
                    new Reference<>(objTee), 
                    new LinkedList<>(),
                    classVarTypeBinding.getClassDecl().getTaggedInfo(), 
                    classVarTypeBinding.getClassDecl().getName()
            );
            if (!(classVarType instanceof ClassType)) {
                ToolError.reportError(
                        ErrorMessage.MUST_BE_LITERAL_CLASS, 
                        this, 
                        classVarType.toString()
                );
            }

            // TODO SMELL: do I really need to store this?  Can get it any time from the type
            cls = classVarTypeBinding.getClassDecl();
            ct = classVarType;

            return classVarType;
        } else { // Standalone

            isGeneric = true;
            Environment innerEnv = seq.extendType(Environment.getEmptyEnvironment(), env);
            Environment savedInner = env.extend(innerEnv);
            innerEnv = seq.extendName(innerEnv, savedInner);

            // compute tag info
            TaggedInfo tagInfo = null;
            if (expected.isPresent()) {
                Type t = expected.get();
                if (t instanceof RecordType) {
                    tagInfo = ((RecordType)t).getTaggedInfo();
                }
            }
            
            Environment declEnv = env.extend(
                new NameBindingImpl(
                    this.self(), 
                    new ClassType(
                        new Reference<>(innerEnv), 
                        new Reference<>(innerEnv), 
                        new LinkedList<>(), 
                        tagInfo, null
                    )
                )
            );
            final Environment ideclEnv = StreamSupport.stream(
                seq.getDeclIterator().spliterator(), 
                false
            ).reduce(
                declEnv, 
                (oenv,decl) -> (decl instanceof ClassDeclaration)
                        ? decl.extend(oenv, savedInner)
                        : oenv,(a,b) -> a.extend(b)
            );
            seq.getDeclIterator().forEach(
                decl -> decl.typecheck(
                    ideclEnv, Optional.<Type>empty()
                )
            );

            Environment mockEnv = Environment.getEmptyEnvironment();

            LinkedList<Declaration> decls = new LinkedList<>();

            Environment nnames = (seq.extendType(mockEnv, mockEnv.extend(env)));
            nnames = (seq.extendName(nnames,mockEnv.extend(env)));
            //nnames = seq.extend(nnames, mockEnv.extend(env));

            ClassDeclaration classDeclaration = new ClassDeclaration(
                "generic" + generic_num++,
                "",
                "",
                new DeclSequence(decls), 
                mockEnv, 
                new LinkedList<String>(), 
                getLocation()
            );
            cls = classDeclaration;
            Environment tee = TypeDeclUtils.getTypeEquivalentEnvironment(
                    nnames.extend(mockEnv)
            );

            ct = new ClassType(
                    new Reference<>(
                        nnames.extend(mockEnv)
                    ), 
                    new Reference<>(tee), 
                    new LinkedList<String>(), 
                    tagInfo, 
                    null
            );
            return ct;
        }
    }

    private EvaluationEnvironment getGenericDecls(
            EvaluationEnvironment env, 
            EvaluationEnvironment mockEnv, 
            LinkedList<Declaration> decls
    ) {
        return mockEnv;
    }

    @Deprecated
    @Override
    public Value evaluate(EvaluationEnvironment env) {
        EvaluationEnvironment argValEnv = EvaluationEnvironment.EMPTY;
        for (Entry<String, TypedAST> elem : args.entrySet()) {
            argValEnv = argValEnv.extend(
                    new ValueBinding(
                        elem.getKey(),
                        elem.getValue().evaluate(env)
                    )
            );
        }

        ClassBinding classVarTypeBinding = (ClassBinding) env.lookupValueBinding(
                "class", 
                ClassBinding.class
        ).orElse(null);
        ClassDeclaration classDecl;

        if (classVarTypeBinding != null) {
            classDecl = classVarTypeBinding.getClassDecl();
        } else {

            Environment mockEnv = Environment.getEmptyEnvironment();

            classDecl = new ClassDeclaration(
                    "generic" + generic_num++,
                    "",
                    "",
                    new DeclSequence(),
                    mockEnv,
                    new LinkedList<String>(),
                    getLocation()
            );
        }

        AtomicReference<Value> objRef = new AtomicReference<>();
        EvaluationEnvironment evalEnv = env.extend(
                new LateValueBinding(
                    this.self(),
                    objRef,
                    ct)
        );
        classDecl.evalDecl(
                evalEnv,
                classDecl.extendWithValue(EvaluationEnvironment.EMPTY)
        );
        final EvaluationEnvironment ideclEnv = StreamSupport.stream(
            seq.getDeclIterator().spliterator(), false)
            .reduce(evalEnv,
                    ((oenv,decl) -> 
                        (decl instanceof ClassDeclaration)
                        ? decl.evalDecl(oenv)
                        : oenv),
                    EvaluationEnvironment::extend
            );
        EvaluationEnvironment objenv = seq.bindDecls(
                ideclEnv,
                seq.extendWithDecls(classDecl.getFilledBody(objRef))
        );

        TaggedInfo goodTI = env.lookupBinding(
                this.self(),
                HackForArtifactTaggedInfoBinding.class
        )
            .map(binding -> binding.getTaggedInfo())
            .orElse(classDecl.getTaggedInfo());

        Obj obj = new Obj(objenv.extend(argValEnv), goodTI);

        //FIXME: Record new tag!
        if (classDecl.isTagged()) {
            TaggedInfo ti = classDecl.getTaggedInfo();
            // System.out.println("Processing ti = " + ti);
            // System.out.println("obj.getType = " + obj.getType());
            ti.associateWithObject(obj);
        }

        objRef.set(obj);

        // System.out.println("Finished evaluating new: " + this);

        return objRef.get();
    }

    @Override
    public Map<String, TypedAST> getChildren() {
        HashMap<String,TypedAST> outMap = new HashMap<>();
        outMap.put(
                "seq",
                (seq == null) ? new DeclSequence(Arrays.asList()) : seq
        );
        return outMap;
    }

    /**
      * addNewFile evaluates the expression and adds that expression 
      * to the field generated by this New expression
      *
      * @param value the Expression which should be evaluated as a new field.
      */
    public static String addNewField(Expression value) {
        String name = "field " + uniqueCounter++;
        variables.put(name, value);
        return name;
    }

    @Override
    public ExpressionAST doClone(Map<String, TypedAST> newChildren) {

        New aNew = new New(new HashMap<>(), location);
        aNew.setBody((DeclSequence) newChildren.get("seq"));
        aNew.cls = cls;
        return aNew;
    }

    public ClassDeclaration getClassDecl() {
        return cls;
    }

    public Map<String, TypedAST> getArgs() {
        return args;
    }

    @Override
    public FileLocation getLocation() {
        return location;
    }

    public boolean isGeneric() {
        return isGeneric;
    }

    @Override
    public Expression generateIL(
            GenContext ctx,
            ValueType expectedType,
            List<TypedModuleSpec> dependencies
    ) {

        ValueType type = seq.inferStructuralType(ctx, this.self());
        
        // Translate the declarations.
        GenContext thisContext = ctx.extend(
                this.self(),
                new wyvern.target.corewyvernIL.expression.Variable(this.self()),
                type
        );
        List<wyvern.target.corewyvernIL.decl.Declaration> decls = 
            new LinkedList<wyvern.target.corewyvernIL.decl.Declaration>();

        for (TypedAST d : seq) {            
            wyvern.target.corewyvernIL.decl.Declaration decl = ((Declaration) d)
                .generateDecl(ctx, thisContext);
            if (decl == null) {
                throw new NullPointerException();
            }
            decls.add(decl);
            
            // A VarDeclaration also generates declarations for 
            // the getter and setter to the var field.
            // TODO: is the best place for this to happen?
            if (d instanceof VarDeclaration) {
                VarDeclaration varDecl = (VarDeclaration) d;
                String varName = varDecl.getName();
                Type varType = varDecl.getType();
                
                // Create references to "this" for the generated methods.
                wyvern.tools.typedAST.core.expressions.Variable receiver1;
                wyvern.tools.typedAST.core.expressions.Variable receiver2;

                receiver1 = new wyvern.tools.typedAST.core.expressions.Variable(
                        new NameBindingImpl(this.self(), null),
                        null
                );
                receiver2 = new wyvern.tools.typedAST.core.expressions.Variable(
                        new NameBindingImpl(this.self(), null),
                        null
                );
                
                // Generate getter and setter; add to the declarations.
                wyvern.target.corewyvernIL.decl.Declaration getter;
                wyvern.target.corewyvernIL.decl.Declaration setter;
                getter = DefDeclaration.generateGetter(ctx, receiver1, varName, varType)
                    .generateDecl(thisContext, thisContext);
                setter = DefDeclaration.generateSetter(ctx, receiver2, varName, varType)
                    .generateDecl(thisContext, thisContext);
                decls.add(getter);
                decls.add(setter);  
            }
        }
        // if type is not specified, infer
        return new wyvern.target.corewyvernIL.expression.New(
                decls,
                this.self(),
                type,
                getLocation()
        );
    }
    
    public void setSelfName(String n) {
        this.selfName = n;
    }
}
