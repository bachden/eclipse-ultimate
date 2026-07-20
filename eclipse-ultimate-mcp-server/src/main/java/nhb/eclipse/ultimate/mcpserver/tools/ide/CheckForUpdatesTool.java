package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.engine.ProvisioningContext;
import org.eclipse.equinox.p2.operations.ProvisioningJob;
import org.eclipse.equinox.p2.operations.ProvisioningSession;
import org.eclipse.equinox.p2.operations.UpdateOperation;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.IRepositoryManager;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import com.google.gson.JsonObject;

import nhb.eclipse.ultimate.mcpserver.mcp.McpTool;
import nhb.eclipse.ultimate.mcpserver.tools.Schemas;

/**
 * Programmatic equivalent of Help &gt; Check for Updates: resolves an {@link UpdateOperation}
 * against every configured p2 repository (including any Local... repository added for this
 * plugin's own dist/ update site), applies it if updates are found, then prompts the user with a
 * confirm dialog to restart — restart is never triggered silently, since it would drop this HTTP
 * connection immediately.
 *
 * <p>Repositories are force-refreshed first: p2 caches the metadata/artifact index it last read
 * from each repository URI, so re-publishing an updated dist/ at the same location (as this
 * plugin's own build does on every {@code mvn clean verify}) would otherwise still report "no
 * updates" against the stale cache. Refreshes run in parallel with a short per-repository
 * timeout, since a workspace can have many repositories configured (including slow/unreachable
 * remote ones) and this must not block on the slowest one.
 *
 * <p>An optional {@code siteFilter} narrows both the refresh and the resolve to only repositories
 * whose display name (the nickname given when it was added) or URL contains that text — useful to
 * check just this plugin's own dist/ site without touching every other configured repository
 * (Eclipse Marketplace, other update sites).
 */
public class CheckForUpdatesTool implements McpTool {

    private static final int REFRESH_TIMEOUT_SECONDS = 8;

    @Override
    public String name() {
        return "check_for_updates";
    }

    @Override
    public String description() {
        return "Check p2 repositories for updates (same as Help > Check for Updates), and apply them if found. "
                + "Optionally pass siteFilter to only check repositories whose name or URL contains that text "
                + "(e.g. this plugin's own dist/ site), instead of every configured repository. Blocks until the "
                + "check (and install, if updates exist) finishes. Never restarts automatically — shows a confirm "
                + "dialog so the user chooses when to restart.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "siteFilter", "string",
                "Optional substring to filter which repositories are checked, matched against each "
                        + "repository's display name (nickname) or URL. Omit to check every configured repository.");
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String siteFilter = Schemas.optString(arguments, "siteFilter", null);

        IProvisioningAgent agent = getProvisioningAgent();
        if (agent == null) {
            throw new IllegalStateException("p2 provisioning agent is not available");
        }

        List<URI> matchingMetadataUris = refreshKnownRepositories(agent, siteFilter);
        if (siteFilter != null && matchingMetadataUris.isEmpty()) {
            throw new IllegalArgumentException("No known repository name or URL contains: " + siteFilter);
        }

        ProvisioningSession session = new ProvisioningSession(agent);
        UpdateOperation operation = new UpdateOperation(session);
        if (siteFilter != null) {
            ProvisioningContext context = new ProvisioningContext(agent);
            URI[] uris = matchingMetadataUris.toArray(new URI[0]);
            context.setMetadataRepositories(uris);
            context.setArtifactRepositories(uris);
            operation.setProvisioningContext(context);
        }

        IStatus resolveStatus = operation.resolveModal(new NullProgressMonitor());
        if (resolveStatus.getSeverity() == IStatus.CANCEL) {
            return "Update check cancelled";
        }
        if (!resolveStatus.isOK() && operation.getPossibleUpdates().length == 0) {
            return "No updates found (" + resolveStatus.getMessage() + ")";
        }
        if (operation.getPossibleUpdates().length == 0) {
            return "No updates found";
        }

        ProvisioningJob job = operation.getProvisioningJob(null);
        if (job == null) {
            return "Updates were found but could not be applied automatically (phased/restart-only operation). "
                    + "Use Help > Check for Updates manually.";
        }

        IStatus installStatus = runJobModal(job);
        if (!installStatus.isOK()) {
            throw new IllegalStateException("Failed to apply updates: " + installStatus.getMessage());
        }

        promptRestart();
        return "Updates applied. A restart prompt has been shown; restart Eclipse when ready to load the new version.";
    }

    /**
     * Bypasses p2's cached index for every known repository (or only those matching
     * {@code siteFilter} by name or URL, if given), so a re-published dist/ at the same URI is
     * seen. Runs all refreshes concurrently, each bounded by {@link #REFRESH_TIMEOUT_SECONDS}, so
     * one slow or unreachable repository (e.g. an online update site with no network access)
     * cannot stall the whole check.
     *
     * @return the metadata repository URIs that matched {@code siteFilter} (empty if no filter given)
     */
    private List<URI> refreshKnownRepositories(IProvisioningAgent agent, String siteFilter) {
        IMetadataRepositoryManager metadataManager = (IMetadataRepositoryManager) agent
                .getService(IMetadataRepositoryManager.SERVICE_NAME);
        IArtifactRepositoryManager artifactManager = (IArtifactRepositoryManager) agent
                .getService(IArtifactRepositoryManager.SERVICE_NAME);

        List<URI> matched = new ArrayList<>();
        List<Callable<Void>> tasks = new ArrayList<>();
        if (metadataManager != null) {
            for (URI uri : metadataManager.getKnownRepositories(IRepositoryManager.REPOSITORIES_ALL)) {
                if (siteFilter != null && !matchesSite(metadataManager, uri, siteFilter)) {
                    continue;
                }
                if (siteFilter != null) {
                    matched.add(uri);
                }
                tasks.add(() -> {
                    metadataManager.refreshRepository(uri, new NullProgressMonitor());
                    return null;
                });
            }
        }
        if (artifactManager != null) {
            for (URI uri : artifactManager.getKnownRepositories(IRepositoryManager.REPOSITORIES_ALL)) {
                if (siteFilter != null && !matchesSite(artifactManager, uri, siteFilter)) {
                    continue;
                }
                tasks.add(() -> {
                    artifactManager.refreshRepository(uri, new NullProgressMonitor());
                    return null;
                });
            }
        }
        if (!tasks.isEmpty()) {
            runRefreshTasks(tasks);
        }
        return matched;
    }

    /** Matches a repository by its nickname/name (as shown in Available Software Sites) or its URL. */
    private boolean matchesSite(IRepositoryManager<?> manager, URI uri, String siteFilter) {
        if (uri.toString().contains(siteFilter)) {
            return true;
        }
        String nickname = manager.getRepositoryProperty(uri, IRepository.PROP_NICKNAME);
        if (nickname != null && nickname.contains(siteFilter)) {
            return true;
        }
        String name = manager.getRepositoryProperty(uri, IRepository.PROP_NAME);
        return name != null && name.contains(siteFilter);
    }

    private void runRefreshTasks(List<Callable<Void>> tasks) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(16, tasks.size()));
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(executor.submit(task));
            }
            for (Future<Void> future : futures) {
                try {
                    future.get(REFRESH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Unreachable/removed/slow repository — leave it as-is, resolveModal will
                    // surface it if it actually matters for the update.
                    future.cancel(true);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private IStatus runJobModal(ProvisioningJob job) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<IStatus> result = new AtomicReference<>();
        job.addJobChangeListener(new org.eclipse.core.runtime.jobs.JobChangeAdapter() {
            @Override
            public void done(org.eclipse.core.runtime.jobs.IJobChangeEvent event) {
                result.set(event.getResult());
                latch.countDown();
            }
        });
        job.schedule();
        if (!latch.await(5, TimeUnit.MINUTES)) {
            throw new IllegalStateException("Timed out applying updates");
        }
        return result.get();
    }

    private void promptRestart() {
        Display display = PlatformUI.getWorkbench().getDisplay();
        display.asyncExec(() -> {
            boolean restart = MessageDialog.openQuestion(display.getActiveShell(), "Eclipse Ultimate MCP Server",
                    "Updates have been installed. Restart Eclipse now to load the new version?");
            if (restart) {
                PlatformUI.getWorkbench().restart();
            }
        });
    }

    private IProvisioningAgent getProvisioningAgent() {
        var bundle = FrameworkUtil.getBundle(CheckForUpdatesTool.class);
        if (bundle == null) {
            return null;
        }
        var context = bundle.getBundleContext();
        ServiceReference<IProvisioningAgent> ref = context.getServiceReference(IProvisioningAgent.class);
        return ref != null ? context.getService(ref) : null;
    }
}

