import com.google.googlejavaformat.java.Main;
import dev.kikugie.semver.data.Version;
import dev.kikugie.stitcher.EntrypointKt;
import dev.kikugie.stitcher.antlr.scanner.SlashStyleScanner;
import dev.kikugie.stitcher.parser.adapter.ScannerAdapter;
import dev.kikugie.stitcher.transformer.TransformParameters;
import dev.kikugie.stitcher.transformer.impl.SlashStarCommentStrategy;
import dev.kikugie.stitcher.transformer.impl.StarRemoveCommentStrategy;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats canonical Stonecutter sources so that the code of every target is google-java-format
 * output once Stonecutter switches to it.
 *
 * <p>The targets of a file are grouped by the code they activate. Each group is rendered with only
 * its active code, the directives replaced by placeholder comments, and formatted. Every piece of
 * code between two directives then takes its layout from the newest group that activates it, and
 * the project's own Stonecutter processor comments out what is inactive at the current target.
 * Directives are indented like the code they open, and a closing directive like its opening one.
 *
 * <p>Every result is checked for each target: switching there and back reproduces it exactly, and
 * the selected Java tokens equal those of the input.
 */
public final class StonecutterFormat {
    private static final String SUPERSCRIPTS =
            "\u00b9\u00b2\u00b3\u2074\u2075\u2076\u2077\u2078\u2079";
    // A directive may carry the end of the branch before it and, when it is the first line of an
    // inactive branch, that branch's opening comment.
    private static final Pattern MARKER =
            Pattern.compile(
                    "^([ \\t]*)((?:\\*/|["
                            + SUPERSCRIPTS
                            + "]?\\^/)?(?:/\\*|/\\^["
                            + SUPERSCRIPTS
                            + "]?)?)//\\?(.*)$");
    private static final Pattern OPEN = Pattern.compile("^\\s*if\\s+(.+?)\\s*\\{\\s*$");
    private static final Pattern ELSE_IF =
            Pattern.compile("^\\s*}\\s*else\\s+if\\s+(.+?)\\s*\\{\\s*$");
    private static final Pattern ELSE = Pattern.compile("^\\s*}\\s*else\\s*\\{\\s*$");
    private static final Pattern CLOSE = Pattern.compile("^\\s*}\\s*$");
    // Split so that this file, which is formatted too, does not contain the placeholder itself.
    private static final String TAG_NAME = "@@" + "stonecutter-format";
    private static final Pattern TAG = Pattern.compile("^[ \\t]*// " + TAG_NAME + " ([0-9]+)@@$");
    private static final Pattern CONDITION_TOKEN =
            Pattern.compile("\\s*(\\(|\\)|&&|\\|\\||!|>=|<=|>|<|=|[0-9]+(?:\\.[0-9]+)*)");
    private static final List<String> FORMATTER_FLAGS =
            List.of(
                    "--aosp",
                    "--skip-sorting-imports",
                    "--skip-removing-unused-imports",
                    "--skip-reflowing-long-strings",
                    "--skip-javadoc-formatting",
                    "--skip-reordering-modifiers",
                    "--cleanup=false");
    private static final List<String> OPERATORS =
            List.of(
                    ">>>=", ">>>", ">>=", ">>", "<<=", "<<", "==", "!=", "<=", ">=", "&&", "||",
                    "++", "--", "->", "::", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=");

    private enum Kind {
        OPEN,
        ELSE_IF,
        ELSE,
        CLOSE
    }

    private record Marker(int line, String prefix, String body, Kind kind, String condition) {}

    /** Lines of a file and its directives. Segment k lies between directive k - 1 and k. */
    private record Layout(List<String> lines, List<Marker> markers) {
        int segments() {
            return markers.size() + 1;
        }

        int start(int segment) {
            return segment == 0 ? 0 : markers.get(segment - 1).line() + 1;
        }

        int end(int segment) {
            return segment == markers.size() ? lines.size() : markers.get(segment).line();
        }

        List<String> segment(int segment) {
            return lines.subList(start(segment), end(segment));
        }
    }

    private record Group(int representative, BitSet targets) {}

    private final List<String> targets;
    private final String active;

    private StonecutterFormat(List<String> targets, String active) {
        this.targets = targets.stream().sorted(StonecutterFormat::compareVersions).toList();
        if (!this.targets.contains(active))
            throw new IllegalArgumentException("Active version is not a target: " + active);
        this.active = active;
    }

    // Parsing

    private static List<String> lines(String text) {
        String normalized = text.replace("\r\n", "\n");
        var lines = new ArrayList<>(List.of(normalized.split("\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty())
            lines.remove(lines.size() - 1);
        return lines;
    }

    private static String text(List<String> lines) {
        var builder = new StringBuilder();
        for (String line : lines) builder.append(line).append('\n');
        return builder.toString();
    }

    private static Layout parse(Path file, String text) {
        var lines = lines(text);
        var markers = new ArrayList<Marker>();
        for (int index = 0; index < lines.size(); index++) {
            Matcher matcher = MARKER.matcher(lines.get(index));
            if (!matcher.matches()) continue;
            String body = matcher.group(3);
            Matcher part;
            Marker marker;
            if ((part = ELSE_IF.matcher(body)).matches())
                marker = new Marker(index, matcher.group(2), body, Kind.ELSE_IF, part.group(1));
            else if ((part = OPEN.matcher(body)).matches())
                marker = new Marker(index, matcher.group(2), body, Kind.OPEN, part.group(1));
            else if (ELSE.matcher(body).matches())
                marker = new Marker(index, matcher.group(2), body, Kind.ELSE, null);
            else if (CLOSE.matcher(body).matches())
                marker = new Marker(index, matcher.group(2), body, Kind.CLOSE, null);
            else
                throw new IllegalArgumentException(
                        file + ":" + (index + 1) + ": unsupported directive //?" + body);
            markers.add(marker);
        }
        return new Layout(lines, markers);
    }

    // Conditions

    private static int compareVersions(String left, String right) {
        String[] a = left.split("\\."), b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private BitSet all() {
        var set = new BitSet();
        set.set(0, targets.size());
        return set;
    }

    private BitSet evaluate(String condition) {
        var tokens = new ArrayList<String>();
        Matcher matcher = CONDITION_TOKEN.matcher(condition);
        int position = 0;
        while (position < condition.length() && !condition.substring(position).isBlank()) {
            if (!matcher.find(position) || matcher.start() != position)
                throw new IllegalArgumentException("Unsupported condition: " + condition);
            tokens.add(matcher.group(1));
            position = matcher.end();
        }
        int[] next = {0};
        BitSet result = or(tokens, next, condition);
        if (next[0] != tokens.size())
            throw new IllegalArgumentException("Unsupported condition: " + condition);
        return result;
    }

    private BitSet or(List<String> tokens, int[] next, String condition) {
        BitSet result = and(tokens, next, condition);
        while (next[0] < tokens.size() && tokens.get(next[0]).equals("||")) {
            next[0]++;
            result.or(and(tokens, next, condition));
        }
        return result;
    }

    private BitSet and(List<String> tokens, int[] next, String condition) {
        BitSet result = unary(tokens, next, condition);
        while (next[0] < tokens.size() && tokens.get(next[0]).equals("&&")) {
            next[0]++;
            result.and(unary(tokens, next, condition));
        }
        return result;
    }

    private BitSet unary(List<String> tokens, int[] next, String condition) {
        if (next[0] >= tokens.size())
            throw new IllegalArgumentException("Unsupported condition: " + condition);
        String token = tokens.get(next[0]++);
        if (token.equals("!")) {
            BitSet result = all();
            result.andNot(unary(tokens, next, condition));
            return result;
        }
        if (token.equals("(")) {
            BitSet result = or(tokens, next, condition);
            if (next[0] >= tokens.size() || !tokens.get(next[0]++).equals(")"))
                throw new IllegalArgumentException("Unsupported condition: " + condition);
            return result;
        }
        String operator = "=";
        if (List.of(">=", "<=", ">", "<", "=").contains(token)) {
            operator = token;
            if (next[0] >= tokens.size())
                throw new IllegalArgumentException("Unsupported condition: " + condition);
            token = tokens.get(next[0]++);
        }
        if (!token.matches("[0-9]+(?:\\.[0-9]+)*"))
            throw new IllegalArgumentException("Unsupported condition: " + condition);
        var result = new BitSet();
        for (int i = 0; i < targets.size(); i++) {
            int order = compareVersions(targets.get(i), token);
            boolean matches =
                    switch (operator) {
                        case ">=" -> order >= 0;
                        case "<=" -> order <= 0;
                        case ">" -> order > 0;
                        case "<" -> order < 0;
                        default -> order == 0;
                    };
            if (matches) result.set(i);
        }
        return result;
    }

    /** The targets that compile each segment. */
    private BitSet[] segmentTargets(Path file, Layout layout) {
        var result = new BitSet[layout.segments()];
        record Open(BitSet parent, BitSet remaining, BitSet branch, int line) {}
        var stack = new ArrayList<Open>();
        result[0] = all();
        for (int index = 0; index < layout.markers().size(); index++) {
            Marker marker = layout.markers().get(index);
            Open top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
            switch (marker.kind()) {
                case OPEN -> {
                    BitSet parent = top == null ? all() : (BitSet) top.branch().clone();
                    BitSet branch = evaluate(marker.condition());
                    branch.and(parent);
                    BitSet remaining = (BitSet) parent.clone();
                    remaining.andNot(branch);
                    stack.add(new Open(parent, remaining, branch, marker.line()));
                }
                case ELSE_IF, ELSE -> {
                    if (top == null)
                        throw new IllegalArgumentException(
                                file + ":" + (marker.line() + 1) + ": else without if");
                    BitSet branch =
                            marker.kind() == Kind.ELSE ? all() : evaluate(marker.condition());
                    branch.and(top.remaining());
                    BitSet remaining = (BitSet) top.remaining().clone();
                    remaining.andNot(branch);
                    stack.set(
                            stack.size() - 1,
                            new Open(top.parent(), remaining, branch, top.line()));
                }
                case CLOSE -> {
                    if (top == null)
                        throw new IllegalArgumentException(
                                file + ":" + (marker.line() + 1) + ": unmatched //?}");
                    stack.remove(stack.size() - 1);
                }
            }
            result[index + 1] =
                    stack.isEmpty() ? all() : (BitSet) stack.get(stack.size() - 1).branch().clone();
        }
        if (!stack.isEmpty())
            throw new IllegalArgumentException(
                    file + ":" + (stack.get(stack.size() - 1).line() + 1) + ": unclosed //? if");
        return result;
    }

    // Stonecutter

    private static String process(Path file, String source, String version) {
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

    private static Layout sameShape(Path file, Layout expected, String text, String version) {
        Layout actual = parse(file, text);
        if (actual.markers().size() != expected.markers().size())
            throw new IllegalStateException(file + " [" + version + "]: directives changed");
        for (int i = 0; i < actual.markers().size(); i++) {
            if (actual.markers().get(i).kind() != expected.markers().get(i).kind())
                throw new IllegalStateException(file + " [" + version + "]: directives changed");
        }
        return actual;
    }

    // Formatting

    /** One file being formatted: its groups and, once formatted, each group's segments. */
    private final class Job {
        final Path file;
        final String original;
        final Layout layout;
        final BitSet[] segmentTargets;
        final List<Group> groups = new ArrayList<>();
        final List<String> views = new ArrayList<>();
        final List<List<List<String>>> formatted = new ArrayList<>();

        Job(Path file, String original) {
            this.file = file;
            this.original = original;
            if (original.contains(TAG_NAME))
                throw new IllegalArgumentException(file + ": contains the reserved " + TAG_NAME);
            this.layout = parse(file, original);
            this.segmentTargets = segmentTargets(file, layout);
            var byActivation = new LinkedHashMap<BitSet, BitSet>();
            for (int target = 0; target < targets.size(); target++) {
                var activation = new BitSet();
                for (int k = 0; k < layout.segments(); k++)
                    if (segmentTargets[k].get(target)) activation.set(k);
                byActivation.computeIfAbsent(activation, key -> new BitSet()).set(target);
            }
            byActivation.values().stream()
                    .map(set -> new Group(set.length() - 1, set))
                    .sorted(Comparator.comparingInt(Group::representative))
                    .forEach(groups::add);
            for (Group group : groups) views.add(view(group.representative()));
        }

        boolean activeAt(int segment, int target) {
            return segmentTargets[segment].get(target);
        }

        /** The code active at a target, with each directive replaced by a placeholder. */
        String view(int target) {
            String version = targets.get(target);
            Layout at =
                    layout.markers().isEmpty()
                            ? layout
                            : sameShape(file, layout, process(file, original, version), version);
            var lines = new ArrayList<String>();
            for (int k = 0; k < at.segments(); k++) {
                if (activeAt(k, target)) lines.addAll(at.segment(k));
                if (k < at.markers().size()) lines.add("// " + TAG_NAME + " " + k + "@@");
            }
            return text(lines);
        }

        void accept(int group, String output) {
            var lines = lines(output);
            var segments = new ArrayList<List<String>>();
            int start = 0;
            for (int index = 0; index < lines.size(); index++) {
                Matcher matcher = TAG.matcher(lines.get(index));
                if (!matcher.matches()) continue;
                if (Integer.parseInt(matcher.group(1)) != segments.size())
                    throw new IllegalStateException(file + ": formatter reordered directives");
                segments.add(List.copyOf(lines.subList(start, index)));
                start = index + 1;
            }
            segments.add(List.copyOf(lines.subList(start, lines.size())));
            if (segments.size() != layout.segments())
                throw new IllegalStateException(file + ": formatter dropped a directive");
            formatted.add(segments);
        }

        String result() {
            if (layout.markers().isEmpty()) return text(formatted.get(0).get(0));
            // Each segment takes the layout of the newest group that compiles it.
            var chosen = new ArrayList<List<String>>();
            for (int k = 0; k < layout.segments(); k++) {
                List<String> lines = null;
                for (int g = 0; g < groups.size(); g++)
                    if (activeAt(k, groups.get(g).representative()))
                        lines = formatted.get(g).get(k);
                chosen.add(lines);
            }
            // Inactive code keeps the comment depth it has. Stonecutter leaves a branch alone
            // while it stays inactive, so that depth depends on earlier switches, and changing it
            // here would make the result depend on the target that is active.
            var markers = layout.markers();
            int[] levels = commentLevels();
            int[] columns = markerColumns(layout, chosen);
            int current = targets.indexOf(active);
            var lines = new ArrayList<String>();
            for (int k = 0; k < layout.segments(); k++) {
                List<String> before = layout.segment(k);
                List<String> after = chosen.get(k);
                if (after == null) {
                    lines.addAll(before);
                } else if (activeAt(k, current) || after.stream().allMatch(String::isBlank)) {
                    if (levels[k] != 0 && activeAt(k, current))
                        throw inconsistent(k, "code active at " + active + " is commented out");
                    lines.addAll(after);
                } else {
                    if (levels[k] == 0)
                        throw inconsistent(
                                k, "code inactive at " + active + " is not commented out");
                    boolean opens = k > 0 && wrapped(k - 1);
                    if (opens && !startsWithOpener(before, levels[k] - 1))
                        throw inconsistent(k, "the branch's opening comment is missing");
                    lines.addAll(deepen(after, levels[k], opens));
                }
                if (k < markers.size()) {
                    Marker marker = markers.get(k);
                    lines.add(" ".repeat(columns[k]) + marker.prefix() + "//?" + marker.body());
                }
            }
            String text = text(lines);
            verify(text);
            return text;
        }

        private IllegalStateException inconsistent(int segment, String reason) {
            return new IllegalStateException(
                    file
                            + ":"
                            + (layout.start(segment) + 1)
                            + ": "
                            + reason
                            + "; switch with Stonecutter before formatting");
        }

        /** The directive that ends the branch a directive opens. */
        private int branchEnd(int marker) {
            var markers = layout.markers();
            int depth = 0;
            for (int i = marker + 1; i < markers.size(); i++) {
                Kind kind = markers.get(i).kind();
                if (depth == 0 && kind != Kind.OPEN) return i;
                if (kind == Kind.OPEN) depth++;
                else if (kind == Kind.CLOSE) depth--;
            }
            throw new IllegalStateException(file + ": unclosed directive");
        }

        /** Whether the branch a directive opens is commented out, at what depth, else -1. */
        private int wrapperLevel(int marker) {
            return closingLevel(layout.markers().get(branchEnd(marker)).prefix());
        }

        private boolean wrapped(int marker) {
            return layout.markers().get(marker).kind() != Kind.CLOSE && wrapperLevel(marker) >= 0;
        }

        /** How many comments enclose each segment: 0 for code, n inside n inactive branches. */
        private int[] commentLevels() {
            var markers = layout.markers();
            int[] levels = new int[layout.segments()];
            var parents = new ArrayList<Integer>();
            int level = 0;
            for (int i = 0; i < markers.size(); i++) {
                Kind kind = markers.get(i).kind();
                if (kind == Kind.OPEN) parents.add(level);
                if (kind == Kind.CLOSE) {
                    level = parents.remove(parents.size() - 1);
                } else {
                    int parent = parents.get(parents.size() - 1);
                    int wrapper = wrapperLevel(i);
                    if (wrapper >= 0 && wrapper != parent)
                        throw inconsistent(i + 1, "comment depth does not match the nesting");
                    level = wrapper >= 0 ? wrapper + 1 : parent;
                }
                levels[i + 1] = level;
            }
            return levels;
        }

        private static boolean startsWithOpener(List<String> lines, int level) {
            for (String line : lines)
                if (!line.isBlank()) return line.substring(indent(line)).startsWith(opener(level));
            return false;
        }

        /**
         * Code as Stonecutter writes it inside {@code level} inactive branches: every comment
         * delimiter, wherever it is, moves that many levels deeper, and the branch that starts here
         * opens its own comment on the first line.
         */
        private static List<String> deepen(List<String> lines, int level, boolean opens) {
            var result = new ArrayList<String>();
            boolean first = opens;
            for (String line : lines) {
                var builder = new StringBuilder();
                int index = 0;
                while (index < line.length()) {
                    int[] delimiter = delimiter(line, index);
                    if (delimiter == null) {
                        builder.append(line.charAt(index++));
                        continue;
                    }
                    int shifted = delimiter[1] + level;
                    builder.append(delimiter[2] == 1 ? opener(shifted) : closer(shifted));
                    index += delimiter[0];
                }
                String text = builder.toString();
                if (first && !text.isBlank()) {
                    int column = indent(text);
                    text = text.substring(0, column) + opener(level - 1) + text.substring(column);
                    first = false;
                }
                result.add(text);
            }
            return result;
        }

        /** Length, level and direction (1 opens) of a comment delimiter at an index, else null. */
        private static int[] delimiter(String line, int index) {
            if (line.startsWith("/*", index)) return new int[] {2, 0, 1};
            if (line.startsWith("*/", index)) return new int[] {2, 0, 0};
            if (line.startsWith("/^", index)) {
                int level =
                        index + 2 < line.length()
                                ? SUPERSCRIPTS.indexOf(line.charAt(index + 2))
                                : -1;
                return level < 0 ? new int[] {2, 1, 1} : new int[] {3, level + 2, 1};
            }
            int level = SUPERSCRIPTS.indexOf(line.charAt(index));
            if (level >= 0 && line.startsWith("^/", index + 1)) return new int[] {3, level + 2, 0};
            if (line.startsWith("^/", index)) return new int[] {2, 1, 0};
            return null;
        }

        private static String opener(int level) {
            return level == 0 ? "/*" : "/^" + superscript(level);
        }

        private static String closer(int level) {
            return level == 0 ? "*/" : superscript(level) + "^/";
        }

        private static String superscript(int level) {
            return level == 1 ? "" : String.valueOf(SUPERSCRIPTS.charAt(level - 2));
        }

        private static int closingLevel(String prefix) {
            if (prefix.startsWith("*/")) return 0;
            if (prefix.startsWith("^/")) return 1;
            if (prefix.length() >= 3 && prefix.startsWith("^/", 1))
                return SUPERSCRIPTS.indexOf(prefix.charAt(0)) + 2;
            return -1;
        }

        private static int indent(String line) {
            int column = 0;
            while (column < line.length() && line.charAt(column) == ' ') column++;
            return column;
        }

        private static Integer firstIndent(List<String> lines) {
            if (lines == null) return null;
            for (String line : lines) if (!line.isBlank()) return indent(line);
            return null;
        }

        /** Opening directives sit at the column of the code they open, closing ones at the if's. */
        private int[] markerColumns(Layout layout, List<List<String>> chosen) {
            var markers = layout.markers();
            int count = markers.size();
            Integer[] own = new Integer[count];
            for (int i = count - 1; i >= 0; i--) {
                if (markers.get(i).kind() == Kind.CLOSE) continue;
                List<String> after = chosen.get(i + 1);
                if (after == null) after = stripWrapper(layout.segment(i + 1));
                own[i] = firstIndent(after);
                if (own[i] == null && i + 1 < count && markers.get(i + 1).kind() == Kind.OPEN)
                    own[i] = own[i + 1];
            }
            int[] columns = new int[count];
            var open = new ArrayList<List<Integer>>();
            for (int i = 0; i < count; i++) {
                if (markers.get(i).kind() == Kind.OPEN) open.add(new ArrayList<>());
                open.get(open.size() - 1).add(i);
                if (markers.get(i).kind() != Kind.CLOSE) continue;
                List<Integer> group = open.remove(open.size() - 1);
                Integer base = null;
                for (int member : group)
                    if (base == null && own[member] != null) base = own[member];
                if (base == null) base = fallbackColumn(layout, chosen, group);
                for (int member : group)
                    columns[member] =
                            markers.get(member).kind() == Kind.CLOSE || own[member] == null
                                    ? base
                                    : own[member];
            }
            return columns;
        }

        private int fallbackColumn(Layout layout, List<List<String>> chosen, List<Integer> group) {
            int close = group.get(group.size() - 1);
            for (int k = close + 1; k < layout.segments(); k++) {
                List<String> lines = chosen.get(k) == null ? layout.segment(k) : chosen.get(k);
                for (String line : lines) {
                    if (line.isBlank()) continue;
                    return indent(line) + (line.strip().startsWith("}") ? 4 : 0);
                }
            }
            for (int k = group.get(0); k >= 0; k--) {
                List<String> lines = chosen.get(k) == null ? layout.segment(k) : chosen.get(k);
                for (int i = lines.size() - 1; i >= 0; i--)
                    if (!lines.get(i).isBlank()) return indent(lines.get(i));
            }
            return 0;
        }

        private static List<String> stripWrapper(List<String> lines) {
            var result = new ArrayList<String>();
            boolean first = true;
            for (String line : lines) {
                if (first && !line.isBlank()) {
                    int column = indent(line);
                    String rest = line.substring(column);
                    if (rest.startsWith("/*") || rest.startsWith("/^"))
                        line = " ".repeat(column) + rest.substring(2);
                    first = false;
                }
                result.add(line);
            }
            return result;
        }

        /**
         * Stonecutter accepts the result as it is at the active target, and every target keeps the
         * original's Java tokens. A plain round trip is not required: Stonecutter itself rewrites
         * a branch that stayed inactive since an older switch once some target activates it.
         */
        private void verify(String result) {
            if (layout.markers().isEmpty()) {
                if (!tokens(original).equals(tokens(result)))
                    throw new IllegalStateException(file + ": formatting changed Java tokens");
                return;
            }
            if (!process(file, result, active).equals(result))
                throw new IllegalStateException(
                        file + " [" + active + "]: Stonecutter would rewrite the result");
            for (String version : targets) {
                String switched = process(file, result, version);
                if (!tokens(process(file, original, version)).equals(tokens(switched)))
                    throw new IllegalStateException(
                            file + " [" + version + "]: formatting changed Java tokens");
            }
        }
    }

    private Map<Path, String> format(Map<Path, String> sources) throws Exception {
        var jobs = new ArrayList<Job>();
        for (var entry : sources.entrySet()) jobs.add(new Job(entry.getKey(), entry.getValue()));
        Path work = Files.createTempDirectory("anticrash-format-");
        try {
            var files = new ArrayList<Path>();
            for (Job job : jobs) {
                for (String view : job.views) {
                    Path path = work.resolve("View" + files.size() + ".java");
                    Files.writeString(path, view, StandardCharsets.UTF_8);
                    files.add(path);
                }
            }
            var arguments = new ArrayList<>(FORMATTER_FLAGS);
            arguments.add("--replace");
            files.forEach(path -> arguments.add(path.toString()));
            var errors = new StringWriter();
            int status =
                    new Main(
                                    new PrintWriter(System.out, true),
                                    new PrintWriter(errors, true),
                                    new ByteArrayInputStream(new byte[0]))
                            .format(arguments.toArray(String[]::new));
            if (status != 0) {
                var message = new StringBuilder("google-java-format failed:\n");
                int index = 0;
                for (Job job : jobs)
                    for (Group group : job.groups)
                        message.append(files.get(index++).getFileName())
                                .append(" = ")
                                .append(job.file)
                                .append(" [")
                                .append(targets.get(group.representative()))
                                .append("]\n");
                throw new IllegalStateException(message.append(errors).toString());
            }
            int index = 0;
            for (Job job : jobs)
                for (int g = 0; g < job.groups.size(); g++)
                    job.accept(g, Files.readString(files.get(index++), StandardCharsets.UTF_8));
        } finally {
            try (var stream = Files.walk(work)) {
                for (Path path : stream.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
        var results = new LinkedHashMap<Path, String>();
        for (Job job : jobs) results.put(job.file, job.result());
        return results;
    }

    // Tokens, as in ConditionParity

    static List<String> tokens(String source) {
        var result = new ArrayList<String>();
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
            } else if (source.startsWith("//", index)) {
                int end = source.indexOf('\n', index);
                index = end < 0 ? source.length() : end + 1;
            } else if (source.startsWith("/*", index)) {
                int end = source.indexOf("*/", index + 2);
                if (end < 0) throw new IllegalArgumentException("Unclosed block comment");
                index = end + 2;
            } else if (source.startsWith("\"\"\"", index) || current == '"' || current == '\'') {
                boolean block = source.startsWith("\"\"\"", index);
                int length = block ? 3 : 1;
                int end = index + length;
                while (true) {
                    if (end >= source.length())
                        throw new IllegalArgumentException("Unclosed literal");
                    if (source.charAt(end) == '\\') end += 2;
                    else if (block
                            ? source.startsWith("\"\"\"", end)
                            : source.charAt(end) == current) break;
                    else end++;
                }
                result.add(source.substring(index, end + length));
                index = end + length;
            } else if (Character.isJavaIdentifierPart(current)) {
                int end = index + 1;
                while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end)))
                    end++;
                result.add(source.substring(index, end));
                index = end;
            } else {
                String operator = source.substring(index, index + 1);
                for (String candidate : OPERATORS)
                    if (source.startsWith(candidate, index)) {
                        operator = candidate;
                        break;
                    }
                result.add(operator);
                index += operator.length();
            }
        }
        return result;
    }

    // Self-test

    private static void expect(String name, String expected, String actual) {
        if (!expected.equals(actual))
            throw new AssertionError(
                    name + ":\n--- expected\n" + expected + "--- actual\n" + actual);
    }

    private static void selfTest() throws Exception {
        var targets = List.of("1.18", "1.20", "1.21");
        var file = Path.of("Fixture.java");
        // A signature split by version, its directives left at stale columns.
        String split =
                "class A {\n"
                        + "    //? if <=1.18 {\n"
                        + "    /*@Deprecated\n"
                        + "    void f(int a) {\n"
                        + "            *///?} else {\n"
                        + "    void f(long a) {\n"
                        + "                //?}\n"
                        + "      g();\n"
                        + "    }\n"
                        + "}\n";
        expect(
                "split signature",
                "class A {\n"
                        + "    //? if <=1.18 {\n"
                        + "    /*@Deprecated\n"
                        + "    void f(int a) {\n"
                        + "    *///?} else {\n"
                        + "    void f(long a) {\n"
                        + "    //?}\n"
                        + "        g();\n"
                        + "    }\n"
                        + "}\n",
                new StonecutterFormat(targets, "1.21").format(Map.of(file, split)).get(file));
        // Inactive and nested code is formatted as it would be when active.
        String nested =
                "class B {\n"
                        + "    void f() {\n"
                        + "        //? if >=1.20 {\n"
                        + "                g(  ); /* note */\n"
                        + "              //? if >=1.21 {\n"
                        + "        /*if (x)\n"
                        + "    h(1,2);\n"
                        + "  *///?}\n"
                        + "        //?}\n"
                        + "    }\n"
                        + "}\n";
        expect(
                "nested inactive",
                "class B {\n"
                        + "    void f() {\n"
                        + "        //? if >=1.20 {\n"
                        + "        g(); /* note */\n"
                        + "        //? if >=1.21 {\n"
                        + "        /*if (x) h(1, 2);\n"
                        + "        *///?}\n"
                        + "        //?}\n"
                        + "    }\n"
                        + "}\n",
                new StonecutterFormat(targets, "1.20").format(Map.of(file, nested)).get(file));
        // Stonecutter leaves a branch alone while it stays inactive, so the comment depth inside
        // depends on the targets visited before. Formatting keeps that depth.
        String inner =
                "class D {\n"
                        + "    void f() {\n"
                        + "        //? if 1.20 {\n"
                        + "        //? if <=1.20 {\n"
                        + "           a(  );\n"
                        + "        //?} else {\n"
                        + "        /*b();\n"
                        + "        *///?}\n"
                        + "        //?}\n"
                        + "    }\n"
                        + "}\n";
        String history = process(file, process(file, inner, "1.18"), "1.21");
        // The result is the same fixed point whichever target is active.
        for (String source : List.of(split, nested, history)) {
            String at = process(file, source, "1.21");
            String formatted =
                    new StonecutterFormat(targets, "1.21").format(Map.of(file, at)).get(file);
            for (String version : targets) {
                String switched = process(file, formatted, version);
                String again =
                        new StonecutterFormat(targets, version)
                                .format(Map.of(file, switched))
                                .get(file);
                expect("stable at " + version, switched, again);
            }
        }
        try {
            new StonecutterFormat(targets, "1.21")
                    .format(Map.of(file, "class C {\n//? if >=1.20 {\nint a;\n}\n"));
            throw new AssertionError("Unclosed directive escaped detection");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("unclosed")) throw expected;
        }
    }

    // Entry point

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) {
            selfTest();
            System.out.println("Stonecutter formatter self-tests passed.");
            return;
        }
        if (args.length != 5 || !List.of("--check", "--apply").contains(args[0]))
            throw new IllegalArgumentException(
                    "(--check|--apply) root active comma-separated-targets source-list");
        boolean check = args[0].equals("--check");
        Path root = Path.of(args[1]);
        var formatter = new StonecutterFormat(List.of(args[3].split(",")), args[2]);
        var sources = new LinkedHashMap<Path, String>();
        for (String name : Files.readAllLines(Path.of(args[4]), StandardCharsets.UTF_8)) {
            if (name.isBlank()) continue;
            Path path = Path.of(name);
            sources.put(path, Files.readString(path, StandardCharsets.UTF_8));
        }
        var changed = new ArrayList<String>();
        for (var entry : formatter.format(sources).entrySet()) {
            if (entry.getValue().equals(sources.get(entry.getKey()))) continue;
            changed.add(root.relativize(entry.getKey()).toString().replace('\\', '/'));
            if (!check) Files.writeString(entry.getKey(), entry.getValue(), StandardCharsets.UTF_8);
        }
        if (check && !changed.isEmpty()) {
            changed.forEach(System.out::println);
            System.exit(1);
        }
        System.err.println(
                sources.size()
                        + " Java files checked, "
                        + changed.size()
                        + " "
                        + (check ? "need formatting" : "formatted"));
    }
}
