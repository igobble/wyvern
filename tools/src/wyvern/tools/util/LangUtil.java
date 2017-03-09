package wyvern.tools.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import edu.umn.cs.melt.copper.runtime.logging.CopperParserException;
import wyvern.tools.parsing.ParseBuffer;
import wyvern.tools.parsing.Wyvern;
import wyvern.tools.typedAST.core.binding.NameBinding;
import wyvern.tools.typedAST.extensions.SpliceBindExn;
import wyvern.tools.typedAST.extensions.SpliceExn;
import wyvern.tools.typedAST.extensions.interop.java.Util;
import wyvern.tools.typedAST.interfaces.TypedAST;
import wyvern.tools.types.Type;

public class LangUtil {

	public static Type getType(Object src) {
		return Util.javaToWyvType(src.getClass());
	}

	public static boolean isSubtype(Type check, Type against) {
		return check.subtype(against);
	}

	//Java: why did you have to erase generics?
	public static String castString(Object src) {
		return (String) src;
	}

	public static int castInt(Object src) { return (Integer)src; }


	public static int strCharInt(String src) {
		if (src.length() != 1) {
			throw new RuntimeException();
		}
		return src.charAt(0);
	}

	public static int doubleToInt(double dble) {
		return (int)dble;
	}

	public static String intToStr(int charCode) {
		return new String(new char[] {(char)charCode});
	}

	public static TypedAST splice(ParseBuffer buffer) {
		try {
			TypedAST res = (TypedAST)new Wyvern().parse(new StringReader(buffer.getSrcString()), "inner");
			return new SpliceExn(res);
		} catch (IOException | CopperParserException e) {
			throw new RuntimeException(e);
		}
	}

	public static TypedAST spliceUnsafe(String str) {
		try {
			TypedAST res = (TypedAST)new Wyvern().parse(new StringReader(str), "inner");
			return new SpliceExn(res);
		} catch (IOException | CopperParserException e) {
			throw new RuntimeException(e);
		}
	}

	public static TypedAST splice(ParseBuffer buffer, String filename) {
		try {
			TypedAST res = (TypedAST)new Wyvern().parse(new StringReader(buffer.getSrcString()), "inner");
			return new SpliceExn(res);
		} catch (IOException | CopperParserException e) {
			throw new RuntimeException(e);
		}
	}

	public static SpliceBindExn spliceBinding(ParseBuffer buffer, List<NameBinding> bindings) {
		try {
			TypedAST res = (TypedAST)new Wyvern().parse(new StringReader(buffer.getSrcString()), "inner");
			return new SpliceBindExn(res, bindings);
		} catch (IOException | CopperParserException e) {
			throw new RuntimeException(e);
		}
	}

	public static SpliceBindExn spliceBinding(ParseBuffer buffer, List<NameBinding> bindings, String filename) {
		try {
			TypedAST res = (TypedAST)new Wyvern().parse(new StringReader(buffer.getSrcString()), filename);
			return new SpliceBindExn(res, bindings);
		} catch (IOException | CopperParserException e) {
			throw new RuntimeException(e);
		}
	}
}
