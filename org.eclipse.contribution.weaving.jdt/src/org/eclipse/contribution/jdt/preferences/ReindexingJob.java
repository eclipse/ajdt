package org.eclipse.contribution.jdt.preferences;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.search.indexing.IndexManager;
import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.jdt.ui.JavaUI;

/**
 *
 * @author andrew
 * @created Dec 10, 2008
 *
 * Reindexes all projects so that Java-like compilation units
 * can be added to the index.
 */
public class ReindexingJob extends WorkspaceJob {

    private static final Object LOCK = new Object();

    private static boolean isRunning = false;
    public ReindexingJob() {
        super("Reindexing for JDT Weaving");
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor monitor)
            throws CoreException {
        // to avoid risk of deadlock and wait for JDT to be fully initialized
        waitForJobFamily(JavaUI.ID_PLUGIN, monitor);

        // be careful not to double schedule
        synchronized (LOCK) {
            if (isRunning) {
                return Status.OK_STATUS;
            }
        }

        isRunning = true;
        IndexManager indexer = JavaModelManager.getIndexManager();
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        monitor.beginTask("Reindexing", projects == null ? 0 : projects.length);
        if (projects == null) {
            return Status.OK_STATUS;
        }
      for (IProject project : projects) {
        if (WeavableProjectListener.getInstance().isWeavableProject(project)) {
          project.accept(new TouchJavaLikeResourceVisitor(monitor));
          indexer.indexAll(project);
        }
        monitor.worked(1);
        if (monitor.isCanceled()) {
          throw new OperationCanceledException();
        }
      }
        synchronized (LOCK) {
            isRunning = false;
        }
        return Status.OK_STATUS;
    }


    @Override
    public boolean belongsTo(Object family) {
        return ReindexingJob.class == family;
    }

    public static void waitForJobFamily(java.io.Serializable jobFamily, IProgressMonitor monitor) {
        boolean wasInterrupted = false;
        do {
            try {
                Job.getJobManager().join(jobFamily, monitor);
                wasInterrupted = false;
            }
            catch (OperationCanceledException e) {
                e.printStackTrace();
            }
            catch (InterruptedException e) {
                wasInterrupted = true;
            }
        } while (wasInterrupted);
    }

    private static class TouchJavaLikeResourceVisitor implements IResourceVisitor {
        IProgressMonitor monitor;

        TouchJavaLikeResourceVisitor(IProgressMonitor monitor) {
            this.monitor = monitor;
        }

        public boolean visit(IResource resource) throws CoreException {
            if (resource.getType() == IResource.FILE && Util.isJavaLikeFileName(resource.getName())) {
                resource.touch(monitor);
            }
            return true;
        }
    }
}
