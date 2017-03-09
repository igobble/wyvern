package wyvern.tools.types;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;

import wyvern.tools.typedAST.extensions.TSLBlock;
import wyvern.tools.types.extensions.SpliceType;
import wyvern.tools.types.extensions.TypeInv;

//Sigh...
public class TypeResolver {
	public interface Resolvable {
		public Map<String, Type> getTypes();
		public Type setTypes(Map<String, Type> newTypes);
	}

	public static Type resolve(Type input, Environment ctx) {
		try {
			return resolve(input, ctx, new HashSet<Type>());
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	public static Type resolve(Type input, Environment ctx, HashSet<Type> visited) throws IllegalAccessException {
		Type result = iresolve(input, ctx, visited);
		return result;
	}


	private static Type iresolve(Type input, Environment ctx, HashSet<Type> visited) throws IllegalAccessException {
		// System.out.println("Resolving: " + input + " of class " + input.getClass());
		
		if (input instanceof UnresolvedType) {
			return ((UnresolvedType) input).resolve(ctx);
		}

		if (input instanceof SpliceType) {
			return resolve(((SpliceType) input).getInner(),
					ctx.lookupBinding("oev", TSLBlock.OuterTypecheckBinding.class).orElseThrow(RuntimeException::new)
							.getStore(), visited);
		}

		if (input instanceof Resolvable) {
			Map<String, Type> toResolve = ((Resolvable) input).getTypes();
			toResolve.replaceAll((key, type) -> {
				try {
					visited.add(type);
					return resolve(type, ctx, visited);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			});
			return ((Resolvable) input).setTypes(toResolve);
		}


		for (Field f : input.getClass().getDeclaredFields()) {
			f.setAccessible(true);
			if (Type[].class.isAssignableFrom(f.getType())) {
				Type[] inner = (Type[])f.get(input);
				if (inner == null) {
					continue;
				}
				for (int i = 0; i < inner.length; i++) {
					if (inner[i] != null && !(visited.contains(inner[i]))) {
						visited.add(inner[i]);
						inner[i] = resolve(inner[i], ctx, visited);
					}
				}

			}
			if (!Type.class.isAssignableFrom(f.getType())) {
				continue;
			}
			Type inner = (Type)f.get(input);
			if (inner != null && !(visited.contains(inner))) {
				visited.add(inner);
				Type resolved = resolve(inner, ctx, visited);
				f.set(input, resolved);
			}
		}

		if (input instanceof TypeInv) { // This might be a variable!
			return ((TypeInv)input).resolve(ctx);
		}
		
		return input;
	}
}
