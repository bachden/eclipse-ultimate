package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.jdt.core.ISourceRange;

/** Renders a slice of source text as line-numbered output, `cat -n` style. */
final class SourceFormatting {

    private SourceFormatting() {
    }

    static String numbered(String fullSource, ISourceRange range) {
        if (fullSource == null) {
            return "";
        }
        int start = range.getOffset();
        int end = start + range.getLength();
        String before = fullSource.substring(0, start);
        int startLine = before.isEmpty() ? 1 : (int) before.lines().count() + (before.endsWith("\n") ? 1 : 0);
        if (startLine == 0) {
            startLine = 1;
        }
        String slice = fullSource.substring(start, Math.min(end, fullSource.length()));
        return numbered(slice, startLine);
    }

    static String numbered(String text, int startLine) {
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n", -1);
        int lineNo = startLine;
        for (int i = 0; i < lines.length; i++) {
            if (i == lines.length - 1 && lines[i].isEmpty()) {
                break;
            }
            sb.append(lineNo).append('\t').append(lines[i]).append('\n');
            lineNo++;
        }
        return sb.toString();
    }
}
