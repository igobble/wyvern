package wyvern.tools.parsing.parselang;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.tools.JavaCompiler;
import javax.tools.StandardLocation;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.CheckMethodAdapter;

import edu.umn.cs.melt.copper.compiletime.logging.CompilerLogMessage;
import edu.umn.cs.melt.copper.compiletime.logging.CompilerLogger;
import edu.umn.cs.melt.copper.compiletime.logging.PrintCompilerLogHandler;
import edu.umn.cs.melt.copper.compiletime.logging.messages.GrammarSyntaxError;
import edu.umn.cs.melt.copper.compiletime.spec.grammarbeans.CopperElementName;
import edu.umn.cs.melt.copper.compiletime.spec.grammarbeans.CopperElementType;
import edu.umn.cs.melt.copper.compiletime.spec.grammarbeans.DisambiguationFunction;
import edu.umn.cs.melt.copper.compiletime.spec.grammarbeans.GrammarElement;
import edu.umn.cs.melt.copper.compiletime.spec.grammarbeans.NonTerminal;
import edu.umn.cs.melt.copper.compiletime.spec.grammarbeans.ParserBean;
import edu.umn.cs.melt.copper.compiletime.spec.grammarbeans.Production;
import edu.umn.cs.melt.copper.compiletime.spec.grammarbeans.Terminal;
import edu.umn.cs.melt.copper.main.CopperDumpControl;
import edu.umn.cs.melt.copper.main.CopperDumpType;
import edu.umn.cs.melt.copper.main.CopperIOType;
import edu.umn.cs.melt.copper.main.ParserCompiler;
import edu.umn.cs.melt.copper.main.ParserCompilerParameters;
import edu.umn.cs.melt.copper.runtime.auxiliary.Pair;
import edu.umn.cs.melt.copper.runtime.engines.single.SingleDFAEngine;
import edu.umn.cs.melt.copper.runtime.logging.CopperException;
import wyvern.tools.errors.FileLocation;
import wyvern.tools.parsing.ExtParser;
import wyvern.tools.parsing.ParseBuffer;
import wyvern.tools.parsing.parselang.java.StoringClassLoader;
import wyvern.tools.parsing.parselang.java.StoringFileManager;
import wyvern.tools.parsing.parselang.java.StringFileObject;
import wyvern.tools.typedAST.core.Sequence;
import wyvern.tools.typedAST.core.binding.NameBinding;
import wyvern.tools.typedAST.core.binding.NameBindingImpl;
import wyvern.tools.typedAST.core.declarations.ClassDeclaration;
import wyvern.tools.typedAST.core.declarations.DeclSequence;
import wyvern.tools.typedAST.core.declarations.DefDeclaration;
import wyvern.tools.typedAST.core.declarations.ValDeclaration;
import wyvern.tools.typedAST.core.expressions.Application;
import wyvern.tools.typedAST.core.expressions.Invocation;
import wyvern.tools.typedAST.core.expressions.New;
import wyvern.tools.typedAST.core.expressions.TupleObject;
import wyvern.tools.typedAST.core.expressions.Variable;
import wyvern.tools.typedAST.core.values.StringConstant;
import wyvern.tools.typedAST.core.values.UnitVal;
import wyvern.tools.typedAST.extensions.ExternalFunction;
import wyvern.tools.typedAST.extensions.SpliceBindExn;
import wyvern.tools.typedAST.extensions.interop.java.Util;
import wyvern.tools.typedAST.extensions.interop.java.typedAST.JavaClassDecl;
import wyvern.tools.typedAST.interfaces.ExpressionAST;
import wyvern.tools.typedAST.interfaces.TypedAST;
import wyvern.tools.typedAST.interfaces.Value;
import wyvern.tools.types.Environment;
import wyvern.tools.types.Type;
import wyvern.tools.types.UnresolvedType;
import wyvern.tools.types.extensions.Arrow;
import wyvern.tools.types.extensions.ClassType;
import wyvern.tools.types.extensions.Int;
import wyvern.tools.types.extensions.Str;
import wyvern.tools.types.extensions.Tuple;
import wyvern.tools.types.extensions.Unit;
import wyvern.tools.util.LangUtil;
import wyvern.tools.util.Reference;

public class CopperTSL implements ExtParser {
	private int foo;
	public CopperTSL(int k) {
		foo = 0;
	}
	public CopperTSL() {

	}

	private static final String PAIRED_OBJECT_NAME = "innerObj$wyv";

	private static class IParseBuffer extends ParseBuffer {
		IParseBuffer(String str) {
			super(str);
		}
	}

	private static class CopperGrammarException extends RuntimeException {
		private GrammarSyntaxError gse;

		public CopperGrammarException(GrammarSyntaxError gse) {

			this.gse = gse;
		}


		public GrammarSyntaxError getGse() {
			return gse;
		}
	}

	@Override
    @Deprecated
	public TypedAST parse(ParseBuffer input) throws Exception {
		StringReader isr = new StringReader(input.getSrcString());
		ArrayList<edu.umn.cs.melt.copper.runtime.auxiliary.Pair<String,Reader>> inp = new ArrayList<>();
		inp.add(new Pair<String, Reader>("TSL grammar", isr));


		CompilerLogger logger = new CompilerLogger(new PrintCompilerLogHandler(System.out) {
			@Override
			public void handleMessage(CompilerLogMessage message) {
				if (message instanceof GrammarSyntaxError)
					throw new CopperGrammarException((GrammarSyntaxError)message);
				super.handleMessage(message);
			}
		});

		ParserBean res = null;
		try {
			res = CupSkinParser.parseGrammar(inp, logger);
		} catch (CopperGrammarException cge) {
			long rci = cge.getGse().getSyntaxError().getRealCharIndex();
			cge.getGse().getSyntaxError();
			throw new RuntimeException("Copper grammar error\n"+cge.getGse().toString()+"\n refers to: "+input.getSrcString().substring((int)rci,(int)rci+20));
		}
		if (res == null) {
			throw new RuntimeException("Parser parse failed");
		}

		res.getGrammars().stream().map(res::getGrammar)
				.flatMap(grm -> grm.getElementsOfType(CopperElementType.TERMINAL).stream().map(grm::getGrammarElement).<Terminal>map(term->(Terminal)term))
				.filter(term->Optional.ofNullable(term.getCode()).map(String::isEmpty).orElseGet(() -> true)
						&& Optional.ofNullable(term.getReturnType()).map(String::isEmpty).orElseGet(() -> true) )
				.forEach(term -> {
					term.setCode("()");
					term.setReturnType("Unit");
				});

		Environment ntEnv = res.getGrammars().stream().map(res::getGrammar)
				.flatMap(grm -> grm.getElementsOfType(CopperElementType.NON_TERMINAL).stream().map(grm::getGrammarElement))
				.map(this::parseType).map(pair->(Pair<String, Type>)pair)
				.collect(() -> new Reference<Environment>(Environment.getEmptyEnvironment()),
						(env, elem) -> env.set(env.get().extend(new NameBindingImpl(elem.first(), elem.second()))),
						(a, b) -> a.set(a.get().extend(b.get()))).get();
		
		final Environment savedNtEnv = ntEnv;
		ntEnv = res.getGrammars().stream().map(res::getGrammar)
				.flatMap(grm -> grm.getElementsOfType(CopperElementType.TERMINAL).stream().map(grm::getGrammarElement))
				.map(this::parseType).map(pair -> (Pair<String, Type>) pair)
				.collect(() -> new Reference<Environment>(savedNtEnv),
						(env, elem) -> env.set(env.get().extend(new NameBindingImpl(elem.first(), elem.second()))),
						(a, b) -> a.set(a.get().extend(b.get()))).get();


		HashMap<String, Pair<Type,SpliceBindExn>> toGen = new HashMap<>();
		HashMap<String, TypedAST> toGenDefs = new HashMap<>();
		Reference<Integer> methNum = new Reference<>(0);

		String wyvClassName = res.getClassName();
		String javaClassName = wyvClassName + "$java";

		final Environment savedNtEnv2 = ntEnv;
		res.getGrammars().stream().map(res::getGrammar)
				.flatMap(grm->grm.getElementsOfType(CopperElementType.PRODUCTION).stream().map(grm::getGrammarElement).<Production>map(el->(Production)el))
				.<Pair<Production, List<NameBinding>>>map(prod->new Pair<Production, List<NameBinding>>(prod, CopperTSL.<Type,String,Optional<NameBinding>>
						zip(prod.getRhs().stream().map(cer->savedNtEnv2.lookup(cer.getName().toString()).getType()), prod.getRhsVarNames().stream(),
						(type, name) -> (name == null)?Optional.empty():Optional.of(new NameBindingImpl(name, type)))
						.<NameBinding>flatMap(o -> o.isPresent() ? Stream.of(o.get()) : Stream.empty())
						.collect(Collectors.<NameBinding>toList()))
				).forEach(updateCode(toGen,ntEnv,methNum, res.getClassName()));

		LinkedList<BiConsumer<Type,Type>> splicers = new LinkedList<>();

		res.getGrammars().stream().map(res::getGrammar)
				.flatMap(grm->grm.getElementsOfType(CopperElementType.TERMINAL).stream().map(grm::getGrammarElement)
						.<Terminal>map(el->(Terminal)el)).forEach(this.updateTerminalCode(toGen,ntEnv,methNum, res.getClassName(), splicers));

		res.getGrammars().stream().map(res::getGrammar).flatMap(grm->grm.getElementsOfType(CopperElementType.DISAMBIGUATION_FUNCTION)
				.stream().map(grm::getGrammarElement).<DisambiguationFunction>map(el->(DisambiguationFunction)el))
				.forEach(this.updateDisambiguationCode(toGen,ntEnv,methNum));

		res.setClassName(javaClassName);

		String pic = res.getParserInitCode();
		TypedAST parserInitAST;
		if (pic == null)
			parserInitAST = new Sequence();
		else
			parserInitAST = LangUtil.splice(new IParseBuffer(pic), "parser init");
		String defNamePIA = "initGEN" + methNum.get();
		toGenDefs.put(defNamePIA, parserInitAST);
		methNum.set(methNum.get()+1);
		res.setParserInitCode(String.format("Util.invokeValueVarargs(%s, \"%s\");\n", PAIRED_OBJECT_NAME, defNamePIA));

		String ppc = res.getPostParseCode();
		TypedAST postParseAST;
		if (ppc == null)
			postParseAST = new Sequence();
		else
			postParseAST = LangUtil.splice(new IParseBuffer(ppc), "post parse");
		String defNameP = "postGEN" + methNum.get();
		toGenDefs.put(defNameP, postParseAST);
		methNum.set(methNum.get() + 1);
		res.setPostParseCode(String.format("Util.invokeValueVarargs(%s, \"%s\");\n", PAIRED_OBJECT_NAME, defNameP));

		res.setPreambleCode("import wyvern.tools.typedAST.interfaces.Value;\n" +
				"import wyvern.tools.typedAST.core.values.StringConstant;\n" +
				"import wyvern.tools.typedAST.extensions.interop.java.Util;\n" +
				"import wyvern.tools.errors.FileLocation;\n" +
				"import wyvern.tools.typedAST.core.values.IntegerConstant;\n" +
				"import wyvern.tools.typedAST.core.values.TupleValue;\n" +
				"import wyvern.tools.typedAST.core.values.UnitVal;\n" +
				"import wyvern.tools.typedAST.extensions.interop.java.objects.JavaObj;\n" +
				"import wyvern.tools.typedAST.extensions.ExternalFunction;\n" +
				"import wyvern.tools.types.extensions.*;" +
				"");

		res.setParserClassAuxCode(
				"Value "+PAIRED_OBJECT_NAME+" = null;\n" +
				"ExternalFunction pushTokenV = new ExternalFunction(new Arrow(new Tuple(Util.javaToWyvType(Terminals.class),new Str()), new Unit()), (ee,v)->{\n" +
						"\tpushToken((Terminals)(((JavaObj)((TupleValue)v).getValue(0)).getObj()), ((StringConstant)((TupleValue)v).getValue(1)).getValue());\n" +
						"\treturn UnitVal.getInstance(FileLocation.UNKNOWN);\n" +
						"});\n" +
				"Value terminals;");

		res.setParserInitCode("pushTokenV = new ExternalFunction(new Arrow(new Tuple(Util.javaToWyvType(Terminals.class),new Str()), new Unit()), (ee,v)->{\n" +
				"\tpushToken((Terminals)(((JavaObj)((TupleValue)v).getValue(0)).getObj()), ((StringConstant)((TupleValue)v).getValue(1)).getValue());\n" +
				"\treturn UnitVal.getInstance(FileLocation.UNKNOWN);\n" +
				"});\n" +
				"terminals = Util.javaToWyvDecl("+javaClassName+".Terminals.class).getClassObj();");

		FileLocation unkLoc = FileLocation.UNKNOWN;


		ParserCompilerParameters pcp = new ParserCompilerParameters();

		ByteArrayOutputStream target = new ByteArrayOutputStream();
		pcp.setOutputStream(new PrintStream(target));
		pcp.setOutputType(CopperIOType.STREAM);

		ByteArrayOutputStream dump = new ByteArrayOutputStream();
		pcp.setDumpOutputType(CopperIOType.STREAM);
		pcp.setDumpFormat(CopperDumpType.PLAIN);
		pcp.setDump(CopperDumpControl.ON);
		pcp.setDumpStream(new PrintStream(dump));


		try {
			ParserCompiler.compile(res, pcp);
		} catch (CopperException e) {
			throw new RuntimeException(e);
		}

		if (target.toString().isEmpty() ) {
			System.out.println("Parser error! Parser debug dump");
			System.out.println(dump.toString());
			throw new RuntimeException();
		}
		System.out.println(dump.toString());

		JavaCompiler jc = javax.tools.ToolProvider.getSystemJavaCompiler();

		List<StringFileObject> compilationUnits = Arrays.asList(new StringFileObject(javaClassName, target.toString()));
		StoringClassLoader loader = new StoringClassLoader(this.getClass().getClassLoader());
		StoringFileManager sfm = new StoringFileManager(jc.getStandardFileManager(null, null, null),
				loader);

		StringFileObject sfo = new StringFileObject(javaClassName, target.toString());
		sfm.putFileForInput(StandardLocation.SOURCE_PATH, "", javaClassName, sfo);
		JavaCompiler.CompilationTask ct = jc.getTask(null, sfm, null, null, null, Arrays.asList(sfo));

		if (!ct.call())
			throw new RuntimeException();

		loader.applyTransformer(name->name.equals(javaClassName), cw -> new ClassVisitor(Opcodes.ASM5, new CheckClassAdapter(cw)) {


			@Override
            @Deprecated
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				if (!name.equals("<init>"))
					return super.visitMethod(access, name, desc, signature, exceptions);


				String ndesc = org.objectweb.asm.Type.getMethodDescriptor(org.objectweb.asm.Type.VOID_TYPE,
						org.objectweb.asm.Type.getType(Value.class));
				org.objectweb.asm.Type thisType = org.objectweb.asm.Type.getType("L" + javaClassName + ";");

				MethodVisitor res = new CheckMethodAdapter(super.visitMethod(access, name, ndesc, null, exceptions));
				GeneratorAdapter generator = new GeneratorAdapter(
						res,
						Opcodes.ASM5,
						"<init>",
						ndesc);
				generator.visitCode();
				generator.loadThis();
				generator.invokeConstructor(org.objectweb.asm.Type.getType(SingleDFAEngine.class), Method.getMethod("void <init>()"));
				generator.loadThis();
				generator.loadArg(0);
				generator.putField(thisType, PAIRED_OBJECT_NAME,
						org.objectweb.asm.Type.getType(Value.class));
				generator.returnValue();
				generator.visitMaxs(2, 2);
				generator.visitEnd();

				return new MethodVisitor(Opcodes.ASM5) {};
			}
		});

		Class javaClass = sfm.getClassLoader().loadClass(javaClassName);

		JavaClassDecl jcd = Util.javaToWyvDecl(javaClass);


		JavaClassDecl terminalsDecl = StreamSupport.stream(jcd.getDecls().getDeclIterator().spliterator(), false)
				.filter(decl -> decl instanceof JavaClassDecl)
				.<JavaClassDecl>map(decl -> (JavaClassDecl) decl)
				.filter(decl -> decl.getName().equals("Terminals"))
				.findFirst().orElseThrow(() -> new RuntimeException("Cannot find terminals class"));
		Type terminalClassType = terminalsDecl
				.extend(Environment.getEmptyEnvironment(), Environment.getEmptyEnvironment())
				.lookup("Terminals").getType();
		Type terminalObjType = terminalsDecl
				.extend(Environment.getEmptyEnvironment(), Environment.getEmptyEnvironment())
				.lookupType("Terminals").getType();

		splicers.forEach(splicer -> splicer.accept(terminalClassType,terminalObjType));


		AtomicInteger cdIdx = new AtomicInteger();
		TypedAST[] classDecls = new TypedAST[toGen.size() + toGenDefs.size() + 1];
		toGen.entrySet().stream().forEach(entry->classDecls[cdIdx.getAndIncrement()]
				= new ValDeclaration(entry.getKey(), DefDeclaration.getMethodType(entry.getValue().second().getArgBindings(), entry.getValue().first()), entry.getValue().second(), unkLoc));

		toGenDefs.entrySet().stream().forEach(entry->classDecls[cdIdx.getAndIncrement()]
				= new DefDeclaration(entry.getKey(), new Arrow(new Unit(), new Unit()), new LinkedList<>(), entry.getValue(), false));

		New decls = new New(new DeclSequence(), unkLoc);
		classDecls[cdIdx.getAndIncrement()] = new DefDeclaration("create", new Arrow(new Unit(),
				new UnresolvedType(wyvClassName, unkLoc)),
				Arrays.asList(),
				decls, true);


		ArrayList<TypedAST> pairedObjDecls = new ArrayList<>();
		pairedObjDecls.addAll(Arrays.asList(classDecls));
		TypedAST pairedObj = new ClassDeclaration(wyvClassName, "", "", new DeclSequence(pairedObjDecls), unkLoc);

		Type parseBufferType = Util.javaToWyvType(ParseBuffer.class);


		Type javaClassType = Util.javaToWyvType(javaClass);
		ExpressionAST bufGet = new Application(
				new Invocation(new Variable(new NameBindingImpl("buf", null), unkLoc), "getSrcString", null, unkLoc),
				UnitVal.getInstance(unkLoc),
				unkLoc);
		ClassType emptyType =
				new ClassType(new Reference<>(Environment.getEmptyEnvironment()), new Reference<>(Environment.getEmptyEnvironment()), new LinkedList<>(), null, "empty");
		ExpressionAST javaObjInit = new Application(new ExternalFunction(new Arrow(emptyType, Util.javaToWyvType(Value.class)), (env,arg)->{
			return Util.toWyvObj(arg);
		}),
				new Application(
						new Invocation(new Variable(new NameBindingImpl(wyvClassName, null), unkLoc), "create", null, unkLoc),
						UnitVal.getInstance(unkLoc), unkLoc), unkLoc);
		TypedAST body = new Application(new ExternalFunction(new Arrow(Util.javaToWyvType(Object.class), Util.javaToWyvType(TypedAST.class)),
					(env,arg)-> (Value)Util.toJavaObject(arg,Value.class)),
				new Application(new Invocation(new Application(
					new Invocation(new Variable(new NameBindingImpl(javaClassName, null), unkLoc), "create", null, unkLoc),
					javaObjInit, unkLoc), "parse", null, unkLoc),
					new TupleObject(new ExpressionAST[] {bufGet, new StringConstant("TSL code")}, unkLoc), unkLoc), unkLoc);

		DefDeclaration parseDef =
				new DefDeclaration("parse",
						new Arrow(parseBufferType, Util.javaToWyvType(TypedAST.class)),
						Arrays.asList(new NameBindingImpl("buf", parseBufferType)),
						body,
						false);

		return new New(new DeclSequence(Arrays.asList(pairedObj, jcd, parseDef)), unkLoc);
	}

	private Consumer<? super DisambiguationFunction> updateDisambiguationCode(HashMap<String, Pair<Type, SpliceBindExn>> toGen,
																			  Environment ntEnv, Reference<Integer> methNum) {
		return (dis) -> {
			String disambiguationCode = dis.getCode();

			List<NameBinding> argNames = dis.getMembers().stream().map(cer->cer.getName().toString())
					.map(name -> new NameBindingImpl(name, new Int())).collect(Collectors.toList());

			argNames.add(new NameBindingImpl("lexeme", new Str()));

			SpliceBindExn spliced = LangUtil.spliceBinding(new IParseBuffer(disambiguationCode), argNames, dis.getDisplayName());

			CopperElementName newName = dis.getName();
			String nextName = getNextName(methNum, newName);
			toGen.put(nextName, new Pair<>(new Int(), spliced));
			dis.setCode(String.format("return ((IntegerConstant)Util.invokeValueVarargs(%s, \"%s\", %s)).getValue();", PAIRED_OBJECT_NAME, nextName,
					argNames.stream().map(str -> (str.getType() instanceof Int) ? "new IntegerConstant(" + str.getName() + ")" : "new StringConstant(" + str.getName() + ")").reduce((a, b) -> a + ", " + b).get()));
		};
	}

	private Pair<String, Type> parseType(GrammarElement elem) {
		if (elem instanceof Terminal)
			return this.parseType((Terminal)elem);
		if (elem instanceof NonTerminal)
			return this.parseType((NonTerminal)elem);
		throw new RuntimeException();
	}

	private Consumer<Terminal> updateTerminalCode(HashMap<String, Pair<Type,SpliceBindExn>> toGen, Environment lhsEnv, Reference<Integer> methNum, String javaTypeName, List<BiConsumer<Type,Type>> splicers) {
		return (term) -> {
			String oCode = term.getCode();

			CopperElementName termName = term.getName();
			String newName = getNextName(methNum, termName);

			Type resType = lhsEnv.lookup(term.getName().toString()).getType();

			splicers.add((termClassType,termObjType) -> {
						SpliceBindExn spliced = LangUtil.spliceBinding(new IParseBuffer(oCode), Arrays.asList(new NameBinding[]{
								new NameBindingImpl("lexeme", new Str()),
								new NameBindingImpl("pushToken", new Arrow(new Tuple(termObjType, new Str()), new Unit())),
								new NameBindingImpl("Terminals", termClassType)}), term.getDisplayName());

						toGen.put(newName, new Pair<>(resType, spliced));
					});

			String newCode = String.format("RESULT = Util.invokeValueVarargs(%s, \"%s\", %s);", PAIRED_OBJECT_NAME, newName, "new StringConstant(lexeme), pushTokenV, terminals");
			term.setCode(newCode);
		};
	}

	private String getNextName(Reference<Integer> methNum, CopperElementName termName) {
		String newName = termName + "GEN" + methNum.get();
		methNum.set(methNum.get() + 1);
		return newName;
	}

	private Consumer<Pair<Production, List<NameBinding>>> updateCode(HashMap<String, Pair<Type,SpliceBindExn>> toGen, Environment lhsEnv, Reference<Integer> methNum, String thisTypeName) {
		return (Pair<Production, List<NameBinding>> inp) -> {
			Production prod = inp.first();
			List<NameBinding> bindings = inp.second();
			Util.javaToWyvDecl(CupSkinParser.Terminals.class);
			//Generate the new Wyvern method name
			String newName = getNextName(methNum, prod.getName());

			//Parse the input code
			SpliceBindExn spliced = LangUtil.spliceBinding(new IParseBuffer(prod.getCode()), bindings);

			Type resType = lhsEnv.lookup(prod.getLhs().getName().toString()).getType();

			//Save it to the external dict
			toGen.put(newName, new Pair<>(resType, spliced));

			//Code to invoke the equivalent function
			String argsStr = bindings.stream().map(nb->nb.getName()).reduce((a,b)->a+", "+b)
					.map(arg -> ", " + arg).orElseGet(() -> "");
			String newCode = "RESULT = Util.invokeValueVarargs(" + PAIRED_OBJECT_NAME + ", \"" + newName +"\"" + argsStr + ");";

			prod.setCode(newCode);
		};
	}

	//Via stackoverflow and the old Java zip
	private static<A, B, C> Stream<C> zip(Stream<? extends A> a,
										 Stream<? extends B> b,
										 BiFunction<? super A, ? super B, ? extends C> zipper) {
		Objects.requireNonNull(zipper);
		@SuppressWarnings("unchecked")
		Spliterator<A> aSpliterator = (Spliterator<A>) Objects.requireNonNull(a).spliterator();
		@SuppressWarnings("unchecked")
		Spliterator<B> bSpliterator = (Spliterator<B>) Objects.requireNonNull(b).spliterator();

		// Zipping looses DISTINCT and SORTED characteristics
		int both = aSpliterator.characteristics() & bSpliterator.characteristics() &
				~(Spliterator.DISTINCT | Spliterator.SORTED);
		int characteristics = both;

		long zipSize = ((characteristics & Spliterator.SIZED) != 0)
				? Math.min(aSpliterator.getExactSizeIfKnown(), bSpliterator.getExactSizeIfKnown())
				: -1;

		Iterator<A> aIterator = Spliterators.iterator(aSpliterator);
		Iterator<B> bIterator = Spliterators.iterator(bSpliterator);
		Iterator<C> cIterator = new Iterator<C>() {
			@Override
			public boolean hasNext() {
				return aIterator.hasNext() && bIterator.hasNext();
			}

			@Override
			public C next() {
				return zipper.apply(aIterator.next(), bIterator.next());
			}
		};

		Spliterator<C> split = Spliterators.spliterator(cIterator, zipSize, characteristics);
		return (a.isParallel() || b.isParallel())
				? StreamSupport.stream(split, true)
				: StreamSupport.stream(split, false);
	}

}
