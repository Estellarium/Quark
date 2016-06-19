/**
 * This class was created by <Vazkii>. It's distributed as
 * part of the Quark Mod. Get the Source Code in github:
 * https://github.com/Vazkii/Quark
 *
 * Quark is Open Source and distributed under the
 * [ADD-LICENSE-HERE]
 *
 * File Created @ [26/03/2016, 21:31:04 (GMT)]
 */
package vazkii.quark.base.asm;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.FMLLog;

public class ClassTransformer implements IClassTransformer {

	private static final Map<String, Transformer> transformers = new HashMap();

	static {
		transformers.put("net.minecraft.client.model.ModelBiped", ClassTransformer::transformModelBiped);
		transformers.put("micdoodle8.mods.galacticraft.core.client.model.ModelPlayerGC", ClassTransformer::transformModelBiped);
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if(transformers.containsKey(transformedName))
			return transformers.get(transformedName).apply(basicClass);

		return basicClass;
	}

	private static byte[] transformModelBiped(byte[] basicClass) {
		MethodSignature sig = new MethodSignature("setRotationAngles", "func_78087_a", "a", "(FFFFFFLnet/minecraft/entity/Entity;)V", "(FFFFFFLrr;)V");

		return transform(basicClass, Pair.of(sig, combine(
				(AbstractInsnNode node) -> { // Filter
					return node.getOpcode() == Opcodes.RETURN;
				}, 
				(MethodNode method, AbstractInsnNode node) -> { // Action
					InsnList newInstructions = new InsnList();

					newInstructions.add(new VarInsnNode(Opcodes.ALOAD, 7));
					newInstructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "vazkii/quark/vanity/client/emotes/base/EmoteHandler", "updateEmotes", "(Lnet/minecraft/entity/Entity;)V"));

					method.instructions.insertBefore(node, newInstructions);
					return true;
				})));
	}

	private static byte[] transform(byte[] basicClass, Pair<MethodSignature, MethodAction>... methods) {
		ClassReader reader = new ClassReader(basicClass);
		ClassNode node = new ClassNode();
		reader.accept(node, 0);

		boolean didAnything = false;

		for(Pair<MethodSignature, MethodAction> pair : methods)
			didAnything |= findMethodAndTransform(node, pair.getLeft(), pair.getRight());

		if(didAnything) {
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
			node.accept(writer);
			return writer.toByteArray();
		}

		return basicClass;
	}

	public static boolean findMethodAndTransform(ClassNode node, MethodSignature sig, MethodAction pred) {
		String funcName = sig.funcName;
		if(LoadingPlugin.runtimeDeobfEnabled)
			funcName = sig.srgName;

		for(MethodNode method : node.methods)
			if((method.name.equals(funcName)|| method.name.equals(sig.obfName)) && (method.desc.equals(sig.funcDesc) || method.desc.equals(sig.obfDesc)))
				return pred.test(method);

		return false;
	}

	public static MethodAction combine(NodeFilter filter, NodeAction action) {
		return (MethodNode mnode) -> applyOnNode(mnode, filter, action);
	}

	public static boolean applyOnNode(MethodNode method, NodeFilter filter, NodeAction action) {
		Iterator<AbstractInsnNode> iterator = method.instructions.iterator();

		boolean didAny = false;
		while(iterator.hasNext()) {
			AbstractInsnNode anode = iterator.next();
			if(filter.test(anode)) {
				didAny = true;
				if(action.test(method, anode))
					return true;	
			}
		}

		return false;
	}

	private static void log(String str) {
		FMLLog.info("[Quark ASM] %s", str);
	}

	private static class MethodSignature {
		String funcName, srgName, obfName, funcDesc, obfDesc;

		public MethodSignature(String funcName, String srgName, String obfName, String funcDesc, String obfDesc) {
			this.funcName = funcName;
			this.srgName = srgName;
			this.obfName = obfName;
			this.funcDesc = funcDesc;
			this.obfDesc = obfName;
		}

	}
	
	// Basic interface aliases to not have to clutter up the code with generics over and over again
	private static interface Transformer extends Function<byte[], byte[]> { }
	private static interface MethodAction extends Predicate<MethodNode> { }
	private static interface NodeFilter extends Predicate<AbstractInsnNode> { }
	private static interface NodeAction extends BiPredicate<MethodNode, AbstractInsnNode> { }
	
}