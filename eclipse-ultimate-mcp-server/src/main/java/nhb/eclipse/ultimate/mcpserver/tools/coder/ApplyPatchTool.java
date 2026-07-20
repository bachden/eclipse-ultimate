package nhb.eclipse.ultimate.mcpserver.tools.coder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Applies a unified-diff patch (single file, one or more @@ hunks) to a file.
 */
public class ApplyPatchTool implements McpTool {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");
    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|\\n|\\r");
    private static final String NO_NEWLINE_MARKER = "\\ No newline at end of file";

    private record LogicalFile(List<String> lines, String delimiter, boolean trailingNewline) {
    }

    @Override
    public String name() {
        return "apply_patch";
    }

    @Override
    public String description() {
        return "Apply a unified diff patch (standard @@ hunk format) to a single file. Reads and commits through "
                + "Eclipse's shared text buffer, validates hunk counts/context, and fails if the patch makes no change.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "projectName", "string", "The project containing the file");
        Schemas.prop(schema, "filePath", "string", "Path to the file, relative to the project root");
        Schemas.prop(schema, "patch", "string", "Unified diff content with @@ hunk headers");
        return Schemas.required(schema, "projectName", "filePath", "patch");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String projectName = Schemas.requireString(arguments, "projectName");
        String filePath = Schemas.requireString(arguments, "filePath");
        String patch = Schemas.requireString(arguments, "patch");

        IFile file = TextFiles.file(projectName, filePath);
        TextFiles.EditResult edit = TextFiles.edit(file, content -> applyPatch(content, patch, filePath));
        return "Patched " + filePath + " (modification stamp " + edit.beforeModificationStamp() + " -> "
                + edit.afterModificationStamp() + ", editorWasDirty=" + edit.editorWasDirty() + ")";
    }

    static String applyPatch(String content, String patch, String filePath) {
        LogicalFile source = splitContent(content);
        List<String> lines = source.lines();
        String[] patchLines = LINE_BREAK.split(patch, -1);
        List<String> result = new ArrayList<>();
        int sourceIndex = 0;
        int hunkCount = 0;
        boolean sawChange = false;
        boolean resultTrailingNewline = source.trailingNewline();

        int i = 0;
        while (i < patchLines.length) {
            Matcher header = HUNK_HEADER.matcher(patchLines[i]);
            if (!header.matches()) {
                i++;
                continue;
            }

            hunkCount++;
            int oldStart = Integer.parseInt(header.group(1));
            int oldCount = count(header.group(2));
            int newStart = Integer.parseInt(header.group(3));
            int newCount = count(header.group(4));
            validateRangeStart(oldStart, oldCount, "old", filePath);
            validateRangeStart(newStart, newCount, "new", filePath);

            int hunkStart = rangeStartIndex(oldStart, oldCount);
            if (hunkStart < sourceIndex) {
                throw new IllegalArgumentException(
                        "Patch hunks overlap or are out of order in " + filePath + " at old line " + oldStart);
            }
            while (sourceIndex < hunkStart) {
                if (sourceIndex >= lines.size()) {
                    throw new IllegalArgumentException(
                            "Patch hunk starts beyond end of " + filePath + " at old line " + oldStart);
                }
                result.add(lines.get(sourceIndex++));
            }

            int expectedNewIndex = rangeStartIndex(newStart, newCount);
            if (result.size() != expectedNewIndex) {
                throw new IllegalArgumentException("Patch new-file hunk position is inconsistent in " + filePath
                        + ": header starts at " + newStart + " but current output line is " + (result.size() + 1));
            }

            i++;
            int oldSeen = 0;
            int newSeen = 0;
            char previousMarker = 0;
            boolean hunkHasNoNewlineMarker = false;
            while (i < patchLines.length) {
                String hline = patchLines[i];

                if (oldSeen == oldCount && newSeen == newCount) {
                    if (NO_NEWLINE_MARKER.equals(hline)) {
                        resultTrailingNewline = trailingNewlineAfterMarker(previousMarker);
                        hunkHasNoNewlineMarker = true;
                        i++;
                    } else if (!hline.isEmpty() && isPatchBodyMarker(hline.charAt(0))
                            && !HUNK_HEADER.matcher(hline).matches() && !isFileHeader(hline)) {
                        throw new IllegalArgumentException(
                                "Patch hunk contains more lines than declared by its header in " + filePath);
                    }
                    break;
                }
                if (HUNK_HEADER.matcher(hline).matches()) {
                    break;
                }
                if (NO_NEWLINE_MARKER.equals(hline)) {
                    if (previousMarker == 0) {
                        throw new IllegalArgumentException(
                                "No-newline marker has no preceding patch line in " + filePath);
                    }
                    resultTrailingNewline = trailingNewlineAfterMarker(previousMarker);
                    hunkHasNoNewlineMarker = true;
                    previousMarker = 0;
                    i++;
                    continue;
                }
                if (hline.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Malformed empty patch line inside hunk for " + filePath + "; prefix it with a marker");
                }

                char marker = hline.charAt(0);
                String text = hline.substring(1);
                switch (marker) {
                case ' ':
                    expect(lines, sourceIndex, text, filePath);
                    result.add(lines.get(sourceIndex++));
                    oldSeen++;
                    newSeen++;
                    break;
                case '-':
                    expect(lines, sourceIndex, text, filePath);
                    sourceIndex++;
                    oldSeen++;
                    sawChange = true;
                    break;
                case '+':
                    result.add(text);
                    newSeen++;
                    sawChange = true;
                    break;
                default:
                    throw new IllegalArgumentException("Unrecognised patch line: " + hline);
                }
                if (oldSeen > oldCount || newSeen > newCount) {
                    throw new IllegalArgumentException("Patch hunk line counts exceed its header in " + filePath);
                }
                previousMarker = marker;
                i++;
            }

            if (oldSeen != oldCount || newSeen != newCount) {
                throw new IllegalArgumentException("Patch hunk count mismatch in " + filePath + ": expected old/new "
                        + oldCount + "/" + newCount + " lines but found " + oldSeen + "/" + newSeen);
            }
            if (sourceIndex == lines.size() && !hunkHasNoNewlineMarker) {
                resultTrailingNewline = true;
            }
        }

        if (hunkCount == 0) {
            throw new IllegalArgumentException("Patch contains no valid @@ hunks for " + filePath);
        }
        if (!sawChange) {
            throw new IllegalArgumentException("Patch contains no additions or deletions for " + filePath);
        }

        while (sourceIndex < lines.size()) {
            result.add(lines.get(sourceIndex++));
        }

        String updated = String.join(source.delimiter(), result);
        if (resultTrailingNewline && !result.isEmpty()) {
            updated += source.delimiter();
        }
        if (updated.equals(content)) {
            throw new IllegalArgumentException("Patch produced no content change in " + filePath);
        }
        return updated;
    }

    private static LogicalFile splitContent(String content) {
        String delimiter = content.contains("\r\n") ? "\r\n"
                : content.contains("\n") ? "\n" : content.contains("\r") ? "\r" : "\n";
        boolean trailingNewline = content.endsWith("\n") || content.endsWith("\r");
        List<String> lines = content.isEmpty() ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(LINE_BREAK.split(content, -1)));
        if (trailingNewline && !lines.isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return new LogicalFile(lines, delimiter, trailingNewline);
    }

    private static int count(String value) {
        return value == null ? 1 : Integer.parseInt(value);
    }

    private static int rangeStartIndex(int start, int count) {
        return count == 0 ? start : start - 1;
    }

    private static void validateRangeStart(int start, int count, String side, String filePath) {
        if (start < 0 || count < 0 || start == 0 && count != 0) {
            throw new IllegalArgumentException(
                    "Invalid " + side + " hunk range in " + filePath + ": start=" + start + ", count=" + count);
        }
    }

    private static boolean trailingNewlineAfterMarker(char previousMarker) {
        return previousMarker == '-';
    }

    private static boolean isPatchBodyMarker(char marker) {
        return marker == ' ' || marker == '+' || marker == '-' || marker == '\\';
    }

    private static boolean isFileHeader(String line) {
        return line.startsWith("--- ") || line.startsWith("+++ ");
    }

    private static void expect(List<String> lines, int index, String expected, String filePath) {
        if (index >= lines.size() || !lines.get(index).equals(expected)) {
            throw new IllegalArgumentException(
                    "Patch context mismatch in " + filePath + " at line " + (index + 1) + ": expected \"" + expected
                            + "\" but file has \"" + (index < lines.size() ? lines.get(index) : "<eof>") + "\"");
        }
    }
}
