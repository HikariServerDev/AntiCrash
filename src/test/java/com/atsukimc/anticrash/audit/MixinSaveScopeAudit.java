package com.atsukimc.anticrash.audit;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/** Parses complete effective sources; comments/formatting cannot hide a missing save guard. */
public final class MixinSaveScopeAudit {
    private MixinSaveScopeAudit() {}

    public static int verifyFromProperty(Set<String> saveMethods) throws Exception {
        String sources = System.getProperty("anticrash.mixinSources");
        if (sources == null || sources.isBlank()) {
            throw new AssertionError(
                    "Gradle must provide the complete effective mixin source list");
        }
        var paths =
                java.util.Arrays.stream(sources.split(java.io.File.pathSeparator))
                        .map(Path::of)
                        .toList();
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new AssertionError("A JDK is required for source audits");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        int[] count = {0};
        try (var manager =
                compiler.getStandardFileManager(
                        diagnostics, null, java.nio.charset.StandardCharsets.UTF_8)) {
            JavacTask task =
                    (JavacTask)
                            compiler.getTask(
                                    null,
                                    manager,
                                    diagnostics,
                                    List.of("-proc:none"),
                                    null,
                                    manager.getJavaFileObjectsFromPaths(paths));
            for (var unit : task.parse()) {
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitMethod(MethodTree method, Void unused) {
                        for (var annotation : method.getModifiers().getAnnotations()) {
                            String name = annotation.getAnnotationType().toString();
                            if (!name.equals("Inject") && !name.endsWith(".Inject")) continue;
                            for (var argument : annotation.getArguments()) {
                                if (!(argument instanceof AssignmentTree assignment)
                                        || !assignment.getVariable().toString().equals("method"))
                                    continue;
                                if (selectors(assignment.getExpression()).stream()
                                        .anyMatch(saveMethods::contains)) {
                                    count[0]++;
                                    if (!guarded(method))
                                        throw new AssertionError(
                                                unit.getSourceFile().getName()
                                                        + ": "
                                                        + method.getName()
                                                        + " must start with a save-scope guard");
                                }
                            }
                        }
                        return super.visitMethod(method, unused);
                    }
                }.scan(unit, null);
            }
        }
        if (diagnostics.getDiagnostics().stream()
                .anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) {
            throw new AssertionError("Mixin source parse failed: " + diagnostics.getDiagnostics());
        }
        return count[0];
    }

    private static List<String> selectors(ExpressionTree expression) {
        var result = new ArrayList<String>();
        if (expression instanceof LiteralTree literal
                && literal.getValue() instanceof String name) {
            int descriptor = name.indexOf('(');
            result.add(descriptor < 0 ? name : name.substring(0, descriptor));
        } else if (expression instanceof NewArrayTree array && array.getInitializers() != null) {
            array.getInitializers().forEach(item -> result.addAll(selectors(item)));
        }
        return result;
    }

    static boolean guarded(MethodTree method) {
        if (method.getBody() == null
                || method.getBody().getStatements().isEmpty()
                || !(method.getBody().getStatements().get(0) instanceof IfTree condition)
                || condition.getElseStatement() != null) return false;
        ExpressionTree expression = unwrap(condition.getCondition());
        if (!(expression instanceof UnaryTree not)
                || not.getKind() != Tree.Kind.LOGICAL_COMPLEMENT
                || !(unwrap(not.getExpression()) instanceof MethodInvocationTree call)
                || !call.getArguments().isEmpty()
                || !(call.getMethodSelect() instanceof MemberSelectTree selected)) return false;
        String owner = selected.getExpression().toString();
        String name = selected.getIdentifier().toString();
        if (!(owner.equals("RuntimeExtensionNbt") || owner.endsWith(".RuntimeExtensionNbt"))
                || !(name.equals("savingLoadedChunk") || name.equals("savingLoadedEntities")))
            return false;
        StatementTree body = condition.getThenStatement();
        if (body instanceof BlockTree block && block.getStatements().size() == 1)
            body = block.getStatements().get(0);
        return body instanceof ReturnTree returned && returned.getExpression() == null;
    }

    private static ExpressionTree unwrap(ExpressionTree expression) {
        while (expression instanceof ParenthesizedTree parentheses)
            expression = parentheses.getExpression();
        return expression;
    }
}
