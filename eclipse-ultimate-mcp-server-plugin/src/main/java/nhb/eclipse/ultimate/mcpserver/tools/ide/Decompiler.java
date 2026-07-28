package nhb.eclipse.ultimate.mcpserver.tools.ide;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaModelException;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.editor.DecompilerSourceMapper;
import io.github.nbauma109.decompiler.editor.DecompilerType;

/**
 * Thin bridge to the Enhanced Class Decompiler (ECD++) plugin's programmatic API. Kept in its own
 * class so the optional compile-time dependency on {@code io.github.nbauma109.decompiler} stays
 * isolated; callers must first confirm the bundle is installed via {@code Platform.getBundle}.
 */
final class Decompiler {

    private Decompiler() {
    }

    static String decompilerType() {
        return DecompilerType.getDefault();
    }

    @SuppressWarnings("deprecation")
    static char[] decompile(IClassFile classFile) throws JavaModelException {
        DecompilerSourceMapper sourceMapper = JavaDecompilerPlugin.getDefault().getSourceMapper(decompilerType());
        return sourceMapper.findSource(classFile.getType());
    }
}
