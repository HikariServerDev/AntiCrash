package com.atsukimc.anticrash.audit;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline structural audit of the actual mapped game; not a Mixin application/runtime test. */
class MixinTargetAuditTest {
    private final Map<String, ClassNode> classes = new HashMap<>();
    private ClassNode read(String name) throws IOException {
        if (classes.containsKey(name)) return classes.get(name);
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new IOException("Missing target class: " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            classes.put(name, node);
            return node;
        }
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
        List<AnnotationNode> result = new ArrayList<>();
        if (a != null) result.addAll(a);
        if (b != null) result.addAll(b);
        return result;
    }
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }
    private static List<?> list(Object value) {
        return value == null ? List.of() : value instanceof List<?> values ? values : List.of(value);
    }
    private boolean method(String owner, String selector) throws IOException {
        ClassNode node = read(owner);
        int open = selector.indexOf('(');
        String name = open < 0 ? selector : selector.substring(0, open);
        String desc = open < 0 ? null : selector.substring(open);
        for (MethodNode candidate : node.methods) {
            if (candidate.name.equals(name) && (desc == null || desc.equals(candidate.desc))) return true;
        }
        if (node.superName != null && method(node.superName, selector)) return true;
        for (String parent : node.interfaces) if (method(parent, selector)) return true;
        return false;
    }
    private boolean field(String owner, String name) throws IOException {
        ClassNode node = read(owner);
        if (node.fields.stream().anyMatch(candidate -> candidate.name.equals(name))) return true;
        return node.superName != null && field(node.superName, name);
    }
    private List<MethodNode> selectedMethods(String owner, String selector) throws IOException {
        ClassNode node = read(owner);
        int open = selector.indexOf('(');
        String name = open < 0 ? selector : selector.substring(0, open);
        String desc = open < 0 ? null : selector.substring(open);
        List<MethodNode> matches = node.methods.stream()
                .filter(m -> m.name.equals(name) && (desc == null || desc.equals(m.desc))).toList();
        return !matches.isEmpty() || node.superName == null ? matches : selectedMethods(node.superName, selector);
    }
    private void callbackSignature(MethodNode handler, MethodNode target, List<String> problems, String context) {
        Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
        int callback = -1;
        for (int i = 0; i < handlerArgs.length; i++) {
            if (handlerArgs[i].getClassName().startsWith("org.spongepowered.asm.mixin.injection.callback.CallbackInfo")) {
                callback = i; break;
            }
        }
        if (callback < 0) { problems.add(context + " missing callback argument"); return; }
        Type[] targetArgs = Type.getArgumentTypes(target.desc);
        // Zero captured arguments is supported; otherwise the target's full argument list must match.
        if (callback != 0 && (!Arrays.equals(Arrays.copyOf(handlerArgs, callback), targetArgs))) {
            problems.add(context + " callback arguments differ from " + target.name + target.desc);
        }
        boolean returnsVoid = Type.getReturnType(target.desc).equals(Type.VOID_TYPE);
        if (returnsVoid != handlerArgs[callback].getClassName().endsWith(".CallbackInfo")) {
            problems.add(context + " callback return type differs from " + target.name + target.desc);
        }
        if (((handler.access ^ target.access) & org.objectweb.asm.Opcodes.ACC_STATIC) != 0) {
            problems.add(context + " callback staticness differs from " + target.name + target.desc);
        }
    }
    private void injectionPoints(Object value, List<String> problems, String context) throws IOException {
        for (Object item : list(value)) {
            if (!(item instanceof AnnotationNode annotation)) continue;
            Object target = value(annotation, "target");
            if (target instanceof String descriptor && descriptor.startsWith("L") && descriptor.contains(";")) {
                int end = descriptor.indexOf(';');
                String owner = descriptor.substring(1, end);
                String member = descriptor.substring(end + 1);
                if (member.contains("(") && !method(owner, member)) problems.add(context + " At missing: " + descriptor);
            }
        }
    }
    private void wrapMethodSignature(MethodNode handler, MethodNode target, List<String> problems, String context) {
        Type[] args = Type.getArgumentTypes(handler.desc);
        if (args.length == 0 || !args[args.length - 1].getClassName().equals(
                "com.llamalad7.mixinextras.injector.wrapoperation.Operation")
                || !Arrays.equals(Arrays.copyOf(args, args.length - 1), Type.getArgumentTypes(target.desc))
                || !Type.getReturnType(handler.desc).equals(Type.getReturnType(target.desc))
                || ((handler.access ^ target.access) & org.objectweb.asm.Opcodes.ACC_STATIC) != 0)
            problems.add(context + " WrapMethod signature differs from " + target.name + target.desc);
    }
    private void invokeOccursInTarget(MethodNode selected, Object atValue, List<String> problems, String context) {
        for (Object item : list(atValue)) {
            if (!(item instanceof AnnotationNode at) || !"INVOKE".equals(value(at,"value"))) continue;
            Object raw=value(at,"target");
            if (!(raw instanceof String descriptor) || !descriptor.startsWith("L") || !descriptor.contains(";")) continue;
            int end=descriptor.indexOf(';'), open=descriptor.indexOf('(',end);
            if(open<0) continue;
            String owner=descriptor.substring(1,end), name=descriptor.substring(end+1,open), desc=descriptor.substring(open);
            int count=0;
            for(var instruction:selected.instructions)
                if(instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) count++;
            int ordinal=value(at,"ordinal") instanceof Integer number ? number : -1;
            if(count==0 || ordinal>=count) problems.add(context+" At invocation absent/ordinal invalid in "+selected.name+selected.desc+": "+descriptor);
        }
    }
    @Test void configuredMixinSelectorsExistInThisMinecraftJar() throws Exception {
        Path root = Path.of("build/classes/java/main/com/atsukimc/anticrash/mixin");
        assertTrue(Files.isDirectory(root));
        List<String> problems = new ArrayList<>();
        int audited = 0;
        try (var paths = Files.list(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                ClassNode mixin = new ClassNode();
                new ClassReader(Files.readAllBytes(path)).accept(mixin, ClassReader.SKIP_CODE);
                AnnotationNode marker = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                        .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElse(null);
                if (marker == null) continue;
                List<String> targets = new ArrayList<>();
                for (Object target : list(value(marker, "value"))) targets.add(((Type) target).getInternalName());
                for (Object target : list(value(marker, "targets"))) targets.add(target.toString().replace('.', '/'));
                for (String target : targets) {
                    // Optional external adapters are absent in the game-only offline classpath.
                    // Only @Pseudo Carpet targets may be absent; mandatory Minecraft targets
                    // remain required. This is not Carpet runtime coverage.
                    boolean pseudo = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)
                            .stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;"));
                    if (pseudo && target.startsWith("carpet/")
                            && getClass().getClassLoader().getResource(target + ".class") == null) continue;
                    read(target);
                    audited++;
                    for (FieldNode member : mixin.fields) {
                        for (AnnotationNode a : annotations(member.visibleAnnotations, member.invisibleAnnotations)) {
                            if (a.desc.endsWith("/Shadow;") && !field(target, member.name)) {
                                problems.add(mixin.name + " Shadow field missing: " + member.name);
                            }
                        }
                    }
                    for (MethodNode member : mixin.methods) {
                        String context = mixin.name + "." + member.name;
                        for (AnnotationNode a : annotations(member.visibleAnnotations, member.invisibleAnnotations)) {
                            Object accessor = value(a, "value");
                            if (a.desc.endsWith("/Accessor;") && accessor instanceof String name && !field(target, name)) {
                                problems.add(context + " Accessor missing: " + name);
                            }
                            if (a.desc.endsWith("/Invoker;") && accessor instanceof String name && !method(target, name)) {
                                problems.add(context + " Invoker missing: " + name);
                            }
                            if (a.desc.endsWith("/Shadow;") && !method(target, member.name + member.desc)) {
                                problems.add(context + " Shadow method missing: " + member.desc);
                            }
                            for (Object selector : list(value(a, "method"))) {
                                if(selector instanceof String name)
                                    for(MethodNode selected:selectedMethods(target,name))
                                        invokeOccursInTarget(selected,value(a,"at"),problems,context);
                                if (selector instanceof String name && !method(target, name)) {
                                    problems.add(context + " method missing: " + name);
                                }
                                if (selector instanceof String name && a.desc.endsWith("/Inject;")) {
                                    for (MethodNode selected : selectedMethods(target, name)) {
                                        callbackSignature(member, selected, problems, context);
                                    }
                                }
                                if (selector instanceof String name && a.desc.endsWith("/WrapMethod;")) {
                                    for (MethodNode selected : selectedMethods(target, name))
                                        wrapMethodSignature(member, selected, problems, context);
                                }
                            }
                            injectionPoints(value(a, "at"), problems, context);
                        }
                    }
                }
            }
        }
        assertTrue(audited >= 60, "mixin targets audited: " + audited);
        assertEquals(List.of(), problems);
    }
}
