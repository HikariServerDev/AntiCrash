import dev.kikugie.semver.data.Version;
import dev.kikugie.stitcher.EntrypointKt;
import dev.kikugie.stitcher.antlr.scanner.SlashStyleScanner;
import dev.kikugie.stitcher.parser.adapter.ScannerAdapter;
import dev.kikugie.stitcher.transformer.TransformParameters;
import dev.kikugie.stitcher.transformer.impl.SlashStarCommentStrategy;
import dev.kikugie.stitcher.transformer.impl.StarRemoveCommentStrategy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compares selected Java tokens using the project's actual Stonecutter processor, without Loom. */
public final class ConditionParity {
    private static final List<String> OPERATORS =
            List.of(
                    ">>>=", ">>>", ">>=", ">>", "<<=", "<<", "==", "!=", "<=", ">=", "&&", "||",
                    "++", "--", "->", "::", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=");

    private static String select(Path file, String source, String version) {
        var parameters =
                new TransformParameters(
                        (input, problems) ->
                                new ScannerAdapter(
                                        new SlashStyleScanner(input),
                                        new int[] {
                                            SlashStyleScanner.SLASH_COMMENT_START,
                                            SlashStyleScanner.STAR_COMMENT_START
                                        },
                                        new int[] {
                                            SlashStyleScanner.SLASH_COMMENT_END,
                                            SlashStyleScanner.STAR_COMMENT_END
                                        },
                                        problems),
                        new SlashStarCommentStrategy(true),
                        StarRemoveCommentStrategy.INSTANCE,
                        (original, replacement) -> {
                            throw new IllegalStateException("Unexpected swap");
                        },
                        Map.of(),
                        Map.of(),
                        Map.of("", Version.Companion.parse(version, 0, version.length())),
                        List.of());
        return EntrypointKt.process(
                file,
                source,
                parameters,
                report -> {
                    throw new IllegalStateException(
                            file
                                    + " ["
                                    + version
                                    + "]: "
                                    + report.getLabel()
                                    + " "
                                    + report.getDetails());
                });
    }

    private static List<String> tokens(String source) {
        var result = new ArrayList<String>();
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (source.startsWith("//", index)) {
                index = lineCommentEnd(source, index + 2);
                continue;
            }
            if (source.startsWith("/*", index)) {
                index = blockCommentEnd(source, index);
                continue;
            }
            if (source.startsWith("\"\"\"", index)) {
                int end = quotedEnd(source, index, '"', true);
                result.add(source.substring(index, end));
                index = end;
                continue;
            }
            if (current == '"' || current == '\'') {
                int end = quotedEnd(source, index, current, false);
                result.add(source.substring(index, end));
                index = end;
                continue;
            }
            if (Character.isJavaIdentifierPart(current)) {
                int end = index + 1;
                while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end)))
                    end++;
                result.add(source.substring(index, end));
                index = end;
                continue;
            }

            String operator = matchingOperator(source, index);
            result.add(operator);
            index += operator.length();
        }
        return result;
    }

    private static int lineCommentEnd(String source, int index) {
        int end = source.indexOf('\n', index);
        return end < 0 ? source.length() : end + 1;
    }

    private static int blockCommentEnd(String source, int start) {
        int end = source.indexOf("*/", start + 2);
        if (end < 0) throw new IllegalArgumentException("Unclosed block comment at " + start);
        return end + 2;
    }

    private static int quotedEnd(String source, int start, char quote, boolean textBlock) {
        int delimiterLength = textBlock ? 3 : 1;
        int index = start + delimiterLength;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '\\') {
                index = Math.min(source.length(), index + 2);
                continue;
            }
            if (textBlock ? source.startsWith("\"\"\"", index) : current == quote) {
                return index + delimiterLength;
            }
            index++;
        }
        throw new IllegalArgumentException("Unclosed literal at " + start);
    }

    private static String matchingOperator(String source, int index) {
        for (String operator : OPERATORS) {
            if (source.startsWith(operator, index)) return operator;
        }
        return source.substring(index, index + 1);
    }

    private static void requireSame(Path file, String before, String after, String version) {
        var expected = tokens(select(file, before, version));
        var actual = tokens(select(file, after, version));
        if (!expected.equals(actual)) {
            int first = 0;
            while (first < Math.min(expected.size(), actual.size())
                    && expected.get(first).equals(actual.get(first))) first++;
            throw new IllegalStateException(
                    file + " [" + version + "]: token mismatch at " + first);
        }
    }

    private static void selfTest() {
        var file = Path.of("Fixture.java");
        String before = "//? if <=1.18.2 {\nold();\n//?}\n//? if >=1.19 {\n/*newer();\n*///?}\n";
        String after = before.replace("//?}\n//? if", "//?} else if");
        for (String version : List.of("1.18.2", "1.19", "26.3")) {
            requireSame(file, before, after, version);
        }
        try {
            requireSame(file, before, after.replace("newer()", "wrong()"), "1.19");
            throw new AssertionError("Changed inactive branch escaped detection");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("token mismatch")) throw expected;
        }
        if (tokens("String s = \"/* literal */\"; // comment\n")
                .equals(tokens("String s = \"different\";")))
            throw new AssertionError("Literal lost");
        if (tokens("a++; ").equals(tokens("a + +;"))) throw new AssertionError("Operator lost");
    }

    public static void main(String[] args) throws Exception {
        selfTest();
        if (args.length == 1 && args[0].equals("--self-test")) {
            System.out.println("Condition parity self-tests passed.");
            return;
        }
        if (args.length < 3)
            throw new IllegalArgumentException("before-directory after-directory versions...");
        Path before = Path.of(args[0]);
        Path after = Path.of(args[1]);
        List<Path> files;
        try (var stream = Files.walk(before.resolve("src"))) {
            files = stream.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        if (files.isEmpty())
            throw new IllegalArgumentException("Baseline contains no Java sources");
        for (int i = 2; i < args.length; i++) {
            for (Path file : files) {
                var relative = before.relativize(file);
                requireSame(
                        relative,
                        Files.readString(file),
                        Files.readString(after.resolve(relative)),
                        args[i]);
            }
            System.out.println(
                    args[i] + ": " + files.size() + " files, selected Java tokens match");
        }
    }
}
