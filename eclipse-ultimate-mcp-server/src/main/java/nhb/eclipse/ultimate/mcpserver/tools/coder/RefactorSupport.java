package nhb.eclipse.ultimate.mcpserver.tools.coder;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.PerformRefactoringOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/**
 * Runs a JDT refactoring descriptor headlessly via LTK's
 * {@link PerformRefactoringOperation} — the same mechanism the "Refactoring
 * history" replay feature uses, so it works without any UI wizard.
 */
final class RefactorSupport {

    private RefactorSupport() {
    }

    static void run(RefactoringDescriptor descriptor) throws CoreException {
        RefactoringStatus creationStatus = new RefactoringStatus();
        Refactoring refactoring = descriptor.createRefactoring(creationStatus);
        if (refactoring == null) {
            throw new IllegalStateException("Could not create refactoring: "
                    + creationStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL));
        }
        if (creationStatus.hasFatalError()) {
            throw new IllegalStateException("Refactoring precondition failed: "
                    + creationStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL));
        }

        run(refactoring);
    }

    static void run(Refactoring refactoring) throws CoreException {
        PerformRefactoringOperation operation = new PerformRefactoringOperation(refactoring,
                CheckConditionsOperation.ALL_CONDITIONS);
        operation.run(new NullProgressMonitor());

        RefactoringStatus conditionStatus = operation.getConditionStatus();
        if (conditionStatus != null && conditionStatus.hasFatalError()) {
            throw new IllegalStateException("Refactoring conditions failed: "
                    + conditionStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL));
        }
        RefactoringStatus validationStatus = operation.getValidationStatus();
        if (validationStatus != null && validationStatus.hasFatalError()) {
            throw new IllegalStateException("Refactoring validation failed: "
                    + validationStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL));
        }
    }

    static void run(Change change) throws CoreException {
        if (change == null) {
            throw new IllegalStateException("Quick fix did not provide an applicable change");
        }

        PerformChangeOperation operation = new PerformChangeOperation(change);
        IProgressMonitor monitor = new NullProgressMonitor();
        operation.run(monitor);

        RefactoringStatus conditionStatus = operation.getConditionCheckingStatus();
        if (conditionStatus != null && conditionStatus.hasFatalError()) {
            throw new IllegalStateException(
                    "Change conditions failed: " + conditionStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL));
        }
        RefactoringStatus validationStatus = operation.getValidationStatus();
        if (operation.changeExecutionFailed() || validationStatus != null && validationStatus.hasFatalError()) {
            String message = validationStatus == null ? "change execution failed"
                    : validationStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL);
            throw new IllegalStateException("Change validation failed: " + message);
        }
    }
}
