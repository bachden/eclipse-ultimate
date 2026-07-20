package nhb.eclipse.ultimate.mcpserver.tools.coder;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;

/** Read/write helpers shared by the text-editing coder tools. */
final class TextFiles {

    @FunctionalInterface
    interface ContentEdit {
        String apply(String content) throws Exception;
    }

    record EditResult(boolean changed, boolean editorWasDirty, long beforeModificationStamp,
            long afterModificationStamp) {
    }

    private static final Map<String, ReentrantLock> FILE_LOCKS = new ConcurrentHashMap<>();

    private TextFiles() {
    }

    static IFile file(String projectName, String filePath) {
        IProject project = CoderResources.project(projectName);
        IResource resource = CoderResources.resource(project, filePath);
        if (!(resource instanceof IFile)) {
            throw new IllegalArgumentException(filePath + " is not a file");
        }
        return (IFile) resource;
    }

    static EditResult edit(IFile file, ContentEdit edit) throws Exception {
        ReentrantLock lock = FILE_LOCKS.computeIfAbsent(lockKey(file), ignored -> new ReentrantLock());
        lock.lockInterruptibly();
        try {
            return editLocked(file, edit);
        } finally {
            lock.unlock();
        }
    }

    static EditResult write(IFile file, String content) throws Exception {
        return edit(file, ignored -> content);
    }

    private static EditResult editLocked(IFile file, ContentEdit edit) throws Exception {
        ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
        IPath path = file.getFullPath();
        NullProgressMonitor monitor = new NullProgressMonitor();

        if (manager.getTextFileBuffer(path, LocationKind.IFILE) == null && !file.isSynchronized(IResource.DEPTH_ZERO)) {
            file.refreshLocal(IResource.DEPTH_ZERO, monitor);
        }

        manager.connect(path, LocationKind.IFILE, monitor);
        try {
            ITextFileBuffer buffer = manager.getTextFileBuffer(path, LocationKind.IFILE);
            if (buffer == null) {
                throw new IllegalStateException("Eclipse did not provide a text buffer for " + path);
            }
            if (!buffer.isCommitable()) {
                throw new IllegalStateException("Text buffer is not committable: " + path);
            }

            IDocument document = buffer.getDocument();
            String before = document.get();
            boolean wasDirty = buffer.isDirty();
            long beforeStamp = buffer.getModificationStamp();
            String after = edit.apply(before);
            if (after == null) {
                throw new IllegalStateException("Text edit returned null content for " + path);
            }
            if (after.equals(before)) {
                return new EditResult(false, wasDirty, beforeStamp, buffer.getModificationStamp());
            }

            document.set(after);
            try {
                buffer.commit(monitor, false);
            } catch (Exception e) {
                document.set(before);
                buffer.setDirty(wasDirty);
                throw e;
            }

            if (!after.equals(document.get()) || buffer.isDirty()) {
                throw new IllegalStateException("Text buffer commit did not persist the requested content for " + path);
            }
            return new EditResult(true, wasDirty, beforeStamp, buffer.getModificationStamp());
        } finally {
            manager.disconnect(path, LocationKind.IFILE, monitor);
        }
    }

    private static String lockKey(IFile file) {
        URI location = file.getLocationURI();
        return location != null ? location.normalize().toString() : file.getFullPath().toString();
    }
}
