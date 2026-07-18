package nhb.eclipse.plugin.mcp.ultimate.tools.coder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;

import com.google.gson.JsonObject;

import nhb.eclipse.plugin.mcp.ultimate.mcp.McpTool;
import nhb.eclipse.plugin.mcp.ultimate.tools.Schemas;

/** Applies a unified-diff patch (single file, one or more @@ hunks) to a file. */
public class ApplyPatchTool implements McpTool {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$");

    @Override
    public String name() {
        return "apply_patch";
    }

    @Override
    public String description() {
        return "Apply a unified diff patch (standard @@ hunk format) to a single file. Context lines are matched "
                + "exactly against the current file content; use replace_string for a single simple edit instead.";
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
        String content = TextFiles.read(file);
        List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
        boolean trailingNewline = content.endsWith("\n");
        if (trailingNewline && !lines.isEmpty()) {
            lines.remove(lines.size() - 1);
        }

        String[] patchLines = patch.split("\n", -1);
        List<String> result = new ArrayList<>();
        int sourceIndex = 0;
        int i = 0;
        while (i < patchLines.length) {
            String pline = patchLines[i];
            Matcher m = HUNK_HEADER.matcher(pline);
            if (m.matches()) {
                int hunkStart = Integer.parseInt(m.group(1)) - 1;
                while (sourceIndex < hunkStart) {
                    result.add(lines.get(sourceIndex));
                    sourceIndex++;
                }
                i++;
                while (i < patchLines.length && !HUNK_HEADER.matcher(patchLines[i]).matches()) {
                    String hline = patchLines[i];
                    if (hline.isEmpty()) {
                        i++;
                        continue;
                    }
                    char marker = hline.charAt(0);
                    String text = hline.length() > 1 ? hline.substring(1) : "";
                    if (marker == ' ') {
                        expect(lines, sourceIndex, text, filePath);
                        result.add(lines.get(sourceIndex));
                        sourceIndex++;
                    } else if (marker == '-') {
                        expect(lines, sourceIndex, text, filePath);
                        sourceIndex++;
                    } else if (marker == '+') {
                        result.add(text);
                    } else if (marker == '\\') {
                        // "\ No newline at end of file" — ignore.
                    } else {
                        throw new IllegalArgumentException("Unrecognised patch line: " + hline);
                    }
                    i++;
                }
                continue;
            }
            i++;
        }
        while (sourceIndex < lines.size()) {
            result.add(lines.get(sourceIndex));
            sourceIndex++;
        }

        String updated = String.join("\n", result) + (trailingNewline ? "\n" : "");
        TextFiles.write(file, updated);
        return "Patched " + filePath;
    }

    private void expect(List<String> lines, int index, String expected, String filePath) {
        if (index >= lines.size() || !lines.get(index).equals(expected)) {
            throw new IllegalArgumentException(
                    "Patch context mismatch in " + filePath + " at line " + (index + 1) + ": expected \"" + expected
                            + "\" but file has \"" + (index < lines.size() ? lines.get(index) : "<eof>") + "\"");
        }
    }
}
