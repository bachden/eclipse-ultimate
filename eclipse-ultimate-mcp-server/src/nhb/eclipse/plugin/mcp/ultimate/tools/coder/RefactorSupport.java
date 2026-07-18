package nhb.eclipse.plugin.mcp.ultimate.tools.coder;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.PerformRefactoringOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/**
 * Runs a JDT refactoring descriptor headlessly via LTK's {@link PerformRefactoringOperation} —
 * the same mechanism the "Refactoring history" replay feature uses, so it works without any UI
 * wizard.
 */
final class RefactorSupport {

    private RefactorSupport() {
    }

    static void run(RefactoringDescriptor descriptor) throws CoreException {
        RefactoringStatus creationStatus = new RefactoringStatus();
        Refactoring refactoring = descriptor.createRefactoring(creationStatus);
        if (refactoring == null) {
            throw new IllegalStateException(
                    "Could not create refactoring: " + creationStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL));
        }
        if (creationStatus.hasFatalError()) {
            throw new IllegalStateException(
                    "Refactoring precondition failed: " + creationStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL));
        }

        PerformRefactoringOperation operation = new PerformRefactoringOperation(refactoring,
                CheckConditionsOperation.ALL_CONDITIONS);
        IProgressMonitor monitor = new NullProgressMonitor();
        operation.run(monitor);

        RefactoringStatus conditionStatus = operation.getConditionStatus();
        if (conditionStatus != null && conditionStatus.hasFatalError()) {
            throw new IllegalStateException(
                    "Refactoring conditions failed: " + conditionStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL));
        }
        RefactoringStatus validationStatus = operation.getValidationStatus();
        if (validationStatus != null && validationStatus.hasFatalError()) {
            throw new IllegalStateException("Refactoring validation failed: "
                    + validationStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL));
        }
    }
}
