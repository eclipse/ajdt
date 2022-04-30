/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *     Helen Hawkins - updated for new ajde interface (bug 148190)
 *******************************************************************************/
package org.eclipse.ajdt.internal.builder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.aspectj.ajde.core.IBuildMessageHandler;
import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.CoreUtils;
import org.eclipse.ajdt.core.builder.IAJBuildListener;
import org.eclipse.ajdt.core.builder.IAJCompilerMonitor;
import org.eclipse.ajdt.core.lazystart.IAdviceChangedListener;
import org.eclipse.ajdt.core.model.AJModelChecker;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.internal.core.ajde.CoreCompilerConfiguration;
import org.eclipse.ajdt.internal.ui.ajde.UIMessageHandler;
import org.eclipse.ajdt.internal.ui.markers.DeleteAndUpdateAJMarkersJob;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.internal.ui.tracing.DebugTracing;
import org.eclipse.ajdt.internal.ui.visualiser.AJDTContentProvider;
import org.eclipse.ajdt.internal.utils.AJDTUtils;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.contribution.visualiser.VisualiserPlugin;
import org.eclipse.contribution.visualiser.core.ProviderManager;
import org.eclipse.contribution.xref.ui.XReferenceUIPlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.core.builder.AbstractImageBuilder;
import org.eclipse.jdt.internal.core.builder.JavaBuilder;
import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Bundle;

/**
 *
 */
public class UIBuildListener implements IAJBuildListener {

    /**
     * Map of projects with the IClasspathEntry corresponding
     * to their outjar
     */
    private HashMap<IProject, IClasspathEntry> outjars = null;

    private final ListenerList<IAdviceChangedListener> fListeners = new ListenerList<>();

    /* (non-Javadoc)
     * @see org.eclipse.ajdt.core.builder.AJBuildListener#preAJBuild(org.eclipse.core.resources.IProject)
     */
    public void preAJBuild(int kind, IProject project, IProject[] requiredProjects) {
        // checking to see if the current project has been marked as needing
        // a required project to be rebuilt.
        boolean haveClearedMarkers = false;
      for (IProject requiredProject : requiredProjects) {
        String referencedMessage = NLS.bind(UIMessages.buildPrereqsMessage,
          requiredProject.getName());

        if (projectAlreadyMarked(project, referencedMessage)) {
          if (kind == IncrementalProjectBuilder.FULL_BUILD) {
            AJDTUtils.clearProjectMarkers(project, true);
            UIMessageHandler.clearOtherProjectMarkers(project);
          }
          else {
            AJDTUtils.clearProjectMarkers(project, false);
          }
          markProject(project, referencedMessage);
          haveClearedMarkers = true;
        }
      }
        if (!(haveClearedMarkers)) {
          AJDTUtils.clearProjectMarkers(project, kind == IncrementalProjectBuilder.FULL_BUILD);
            UIMessageHandler.clearOtherProjectMarkers(project);
        }
    }


    /*
     * Check the inpath out folder to see if it exists.
     * If not, notify the user that the default out folder will be used instead
     * The out folder may not exist if the project has been renamed and the
     * outfolder not updated after that.
     */
    private void checkInpathOutFolder(IProject project) {
        String outFolder = AspectJCorePreferences.getProjectInpathOutFolder(project);
        if (outFolder == null || outFolder.equals("")) { //$NON-NLS-1$
            // using default outfolder
            return;
        }

        if (!pathExists(outFolder)) {
            try {
                IMarker errorMarker = project.createMarker(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER);
                errorMarker.setAttribute(IMarker.MESSAGE, UIMessages.UIBuildListener_InvalidInpathOutFolderText + outFolder);
                errorMarker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
                errorMarker.setAttribute(IMarker.LOCATION, "Inpath"); //$NON-NLS-1$
            } catch (CoreException e) {
                AJLog.log(AJLog.BUILDER,"build: Problem occured creating the error marker for project " //$NON-NLS-1$
                                + project.getName() + ": " + e); //$NON-NLS-1$
            }
        }
    }

    /**
     * Only want to mark referencing projects once, therefore need to check
     * whether they've been marked already. Also, if a project has been marked
     * dont want to build it until its prerequisites have been rebuilt.
     */
    private boolean projectAlreadyMarked(IProject project, String errorMessage) {
        try {
            IMarker[] problemMarkers = project.findMarkers(
                    IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER,
                    false, IResource.DEPTH_ZERO);
            if (problemMarkers.length > 0) {
              for (IMarker marker : problemMarkers) {
                int markerSeverity = marker.getAttribute(IMarker.SEVERITY, -1);
                String markerMessage = marker.getAttribute(IMarker.MESSAGE, "no message"); //$NON-NLS-1$
                if (markerSeverity == IMarker.SEVERITY_ERROR) {
                  if (markerMessage.equals(errorMessage)) {
                    return true;
                  }
                }
              }
            }
        } catch (CoreException e) {
            AJLog.log(AJLog.BUILDER,"build: Problem occured finding the markers for project " //$NON-NLS-1$
                            + project.getName() + ": " + e); //$NON-NLS-1$
        }
        return false;
    }

    private void markProject(IProject project, String errorMessage) {
        try {
            IMarker errorMarker = project.createMarker(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER);
            errorMarker.setAttribute(IMarker.MESSAGE, errorMessage);
            errorMarker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
        } catch (CoreException e) {
            AJLog.log(AJLog.BUILDER,"build: Problem occured creating the error marker for project " //$NON-NLS-1$
                            + project.getName() + ": " + e); //$NON-NLS-1$
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.ajdt.core.builder.AJBuildListener#postAJBuild(org.eclipse.core.resources.IProject)
     */
    public void postAJBuild(int kind, final IProject project, boolean noSourceChanges,  Map<IFile, List<CategorizedProblem>> newProblems) {
        if (noSourceChanges) {
            return;
        }

        // The message to feature in the problems view of depending projects
        String buildPrereqsMessage = NLS.bind(UIMessages.buildPrereqsMessage,
                project.getName());
        boolean buildCancelled = ((IAJCompilerMonitor)AspectJPlugin.getDefault().
                getCompilerFactory().getCompilerForProject(project).getBuildProgressMonitor()).
                buildWasCancelled();
        if (buildCancelled) {
            markReferencingProjects(project, buildPrereqsMessage);
        } else {
            removeMarkerOnReferencingProjects(project, buildPrereqsMessage);
        }

        // Bug22258: Get the compiler monitor to display any issues with
        // that compile.
        IBuildMessageHandler messageHandler = AspectJPlugin.getDefault().getCompilerFactory()
                .getCompilerForProject(project).getMessageHandler();
        if (messageHandler instanceof UIMessageHandler) {
            ((UIMessageHandler) messageHandler).showOutstandingProblems(project);
        }

        // before returning, check to see if the project sent its output
        // to an outjar and if so, then update any depending projects
        checkOutJarEntry(project);

        checkInpathOutFolder(project);


        // update the markers on files, but only the ones that have changed
        DeleteAndUpdateAJMarkersJob deleteUpdateMarkers;
        CoreCompilerConfiguration compilerConfig = getCompilerConfiguration(project);
        switch (kind) {
            case IncrementalProjectBuilder.CLEAN_BUILD:
                deleteUpdateMarkers = new DeleteAndUpdateAJMarkersJob(project);
                deleteUpdateMarkers.doDeleteOnly(true);
                deleteUpdateMarkers.setPriority(Job.BUILD);
                deleteUpdateMarkers.schedule();
                break;

            case IncrementalProjectBuilder.FULL_BUILD:
                deleteUpdateMarkers = new DeleteAndUpdateAJMarkersJob(project);
                deleteUpdateMarkers.setPriority(Job.BUILD);
                deleteUpdateMarkers.schedule();
                break;

            case IncrementalProjectBuilder.AUTO_BUILD:
            case IncrementalProjectBuilder.INCREMENTAL_BUILD:
                File[] touchedFiles = compilerConfig.getCompiledSourceFiles();
                if (touchedFiles == null /* recreate all markers */ ||
                        touchedFiles.length > 0) {

                    deleteUpdateMarkers = new DeleteAndUpdateAJMarkersJob(project, touchedFiles);
                    deleteUpdateMarkers.schedule();
                }
        }

        // sanity check the model if the event trace viewer is open
        if (DebugTracing.DEBUG_MODEL) {
            AJModelChecker.doModelCheckIfRequired(
                    AJProjectModelFactory.getInstance().getModelForProject(project));
        }

        if (AspectJUIPlugin.getDefault().getDisplay().isDisposed()) {
            AJLog.log("Not updating vis, xref, or changes views as display is disposed!"); //$NON-NLS-1$
        } else {
            AspectJUIPlugin.getDefault().getDisplay().asyncExec(
              () -> {
                  AJLog.logStart("Update visualizer, xref, advice listeners for (separate thread): " + project.getName());

                  // TODO: can we determine whether there were
                  // actually changes to the set of advised elements?
                  Object[] listeners= fListeners.getListeners();
                for (Object listener : listeners) {
                  ((IAdviceChangedListener) listener).adviceChanged();
                }

                  // refresh Cross References
                  if (AspectJUIPlugin.usingXref) {
                      XReferenceUIPlugin.refresh();
                  }

                  // refresh Visualiser
                  if (AspectJUIPlugin.usingVisualiser) {
                      Bundle vis = Platform
                              .getBundle(AspectJUIPlugin.VISUALISER_ID);
                      // avoid activating the bundle if it's not active already
                      if ((vis != null) && (vis.getState() == Bundle.ACTIVE)) {
                          if (ProviderManager.getContentProvider() instanceof AJDTContentProvider) {
                              AJDTContentProvider provider = (AJDTContentProvider) ProviderManager
                                      .getContentProvider();
                              provider.reset();
                              VisualiserPlugin.refresh();
                          }
                      }
                  }
                  AJLog.logEnd(AJLog.BUILDER, "Update visualizer, xref, advice listeners for (separate thread): " + project.getName());
              });
        }

        // finally, create markers for extra problems coming from compilation participants
        for (Entry<IFile, List<CategorizedProblem>> problemsForFile : newProblems.entrySet()) {
            try {
                IFile file = problemsForFile.getKey();
                for(CategorizedProblem problem : problemsForFile.getValue()) {
                    String markerType = problem.getMarkerType();
                    IMarker marker = file.createMarker(markerType);

                    String[] attributeNames = AbstractImageBuilder.JAVA_PROBLEM_MARKER_ATTRIBUTE_NAMES;
                    String[] extraAttributeNames = problem.getExtraMarkerAttributeNames();
                    int extraLength = extraAttributeNames == null ? 0 : extraAttributeNames.length;

                    int standardLength = attributeNames.length + 1;
                    String[] allNames = new String[standardLength];
                    System.arraycopy(attributeNames, 0, allNames, 0,standardLength-1);
                    allNames[standardLength-1] = IMarker.SOURCE_ID;
                    if (extraLength > 0) {
                        allNames = new String[standardLength + extraLength];
                        System.arraycopy(extraAttributeNames, 0, allNames, standardLength + 1, extraLength);
                    }

                    Object[] allValues = new Object[allNames.length];
                    // standard attributes
                    int index = 0;
                    allValues[index++] = problem.getMessage(); // message
                    allValues[index++] = problem.isError() ? IMarker.SEVERITY_ERROR : IMarker.SEVERITY_WARNING; // severity
                    allValues[index++] = problem.getID(); // ID
                    allValues[index++] = problem.getSourceStart(); // start
                    int end = problem.getSourceEnd();
                    allValues[index++] = end > 0 ? end + 1 : end; // end
                    allValues[index++] = problem.getSourceLineNumber(); // line
                    allValues[index++] = Util.getProblemArgumentsForMarker(problem.getArguments()); // arguments
                    allValues[index++] = problem.getCategoryID(); // category ID
                    // SOURCE_ID attribute for JDT problems
                    allValues[index++] = JavaBuilder.SOURCE_ID;

                    // optional extra attributes
                    if (extraLength > 0)
                        System.arraycopy(problem.getExtraMarkerAttributeValues(), 0, allValues, index, extraLength);

                    marker.setAttributes(allNames, allValues);
                }
            } catch (CoreException ignored) {
            }
        }
    }


    public void postAJClean(IProject project) {
        DeleteAndUpdateAJMarkersJob job = new DeleteAndUpdateAJMarkersJob(project);
        job.doDeleteOnly(true);
        job.schedule();
    }


    private CoreCompilerConfiguration getCompilerConfiguration(
            final IProject project) {
        return  (CoreCompilerConfiguration)
                AspectJPlugin.getDefault().getCompilerFactory()
                .getCompilerForProject(project).getCompilerConfiguration();
    }

    public void addAdviceListener(IAdviceChangedListener adviceListener) {
        fListeners.add(adviceListener);
    }

    public void removeAdviceListener(IAdviceChangedListener adviceListener) {
        fListeners.remove(adviceListener);
    }

    /**
     * If the build has been aborted then mark any referencing projects with a
     * marker saying so
     */
    private void markReferencingProjects(IProject project, String errorMessage) {
        IProject[] referencingProjects = getDependingProjects(project);
      for (IProject referencingProject : referencingProjects) {
        if (!(projectAlreadyMarked(referencingProject, errorMessage))) {
          markProject(referencingProject, errorMessage);
        }
      }
    }

    /**
     * The build wasn't cancelled therefore need to remove any markers on
     * referencing projects indicating that the current project needs to be
     * built
     */
    private void removeMarkerOnReferencingProjects(IProject project,
            String errorMessage) {
        try {
            IProject[] referencingProjects = getDependingProjects(project);
          for (IProject referencingProject : referencingProjects) {
            IMarker[] problemMarkers = referencingProject.findMarkers(
              IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER,
              false, IResource.DEPTH_ZERO);
            if (problemMarkers.length > 0) {
              for (IMarker marker : problemMarkers) {
                int markerSeverity = marker.getAttribute(
                  IMarker.SEVERITY, -1);
                if (markerSeverity == IMarker.SEVERITY_ERROR) {
                  String markerMessage = marker.getAttribute(
                    IMarker.MESSAGE, "no message"); //$NON-NLS-1$
                  if (markerMessage.equals(errorMessage)) {
                    marker.delete();
                  }
                }
              }
            }
          }
        } catch (CoreException e) {
            AJLog.log(AJLog.BUILDER,"build: Problem occured either finding the markers for project " //$NON-NLS-1$
                            + project.getName()
                            + ", or deleting the error marker: " //$NON-NLS-1$
                            + e);
        }
    }

    /**
     * If a project has specified an outjar then update the classpath of
     * depending projects to include this outjar (unless the classpath already
     * contains it). If the project hasn't specified an outjar then check
     * whether it did last time it was built. In this case, remove the oujar
     * from the classpath of depending projects.
     */
    private void checkOutJarEntry(IProject project) {
        String outJar = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration().getOutJar();
        if (outJar != null && !(outJar.equals(""))) {  //$NON-NLS-1$
            if (outjars == null)
              outjars = new HashMap<>();
            IPath newPath = getRelativePath(project, outJar);
            IClasspathEntry newEntry = JavaCore.newLibraryEntry(newPath.makeAbsolute(), null, null);
            if (outjars.containsKey(project))  {
                if (!(outjars.get(project).equals(newEntry))) {
                    IClasspathEntry oldEntry = outjars.get(project);
                    outjars.remove(project);
                    removeOutjarFromDependingProjects(project,oldEntry);
                    outjars.put(project, newEntry);
                    updateDependingProjectsWithJar(project,newEntry);
                }
            } else {
                outjars.put(project,newEntry);
                updateDependingProjectsWithJar(project,newEntry);
            }
        } else {
            if (outjars != null && outjars.containsKey(project)) {
                IClasspathEntry oldEntry = outjars.get(project);
                outjars.remove(project);
                if (outjars.size() == 0)
                  outjars = null;
                removeOutjarFromDependingProjects(project, oldEntry);
            }
        }
    }

    private void removeOutjarFromDependingProjects(IProject project, IClasspathEntry unwantedEntry) {
        IProject[] dependingProjects = getDependingProjects(project);

      for (IProject dependingProject : dependingProjects) {
        IJavaProject javaProject = JavaCore.create(dependingProject);
        if (javaProject == null)
          continue;
        try {
          IClasspathEntry[] cpEntry = javaProject.getRawClasspath();
          List<IClasspathEntry> newEntries = new ArrayList<>();
          for (IClasspathEntry iClasspathEntry : cpEntry) {
            if (!iClasspathEntry.equals(unwantedEntry))
              newEntries.add(iClasspathEntry);
          }
          IClasspathEntry[] newCP = newEntries.toArray(new IClasspathEntry[0]);
          javaProject.setRawClasspath(newCP, new NullProgressMonitor());
        }
        catch (CoreException ignored) {
        }
      }
    }

    private void updateDependingProjectsWithJar(IProject project, IClasspathEntry newEntry) {
        IProject[] dependingProjects = getDependingProjects(project);

        goThroughProjects:
        for (IProject dependingProject : dependingProjects) {
          IJavaProject javaProject = JavaCore.create(dependingProject);
          if (javaProject == null)
            continue;
          try {
            IClasspathEntry[] cpEntry = javaProject.getRawClasspath();
            List<IClasspathEntry> newEntries = new ArrayList<>();
            for (IClasspathEntry iClasspathEntry : cpEntry) {
              if (iClasspathEntry.equals(newEntry))
                continue goThroughProjects;
              else
                newEntries.add(iClasspathEntry);
            }
            newEntries.add(newEntry);
            IClasspathEntry[] newCP = newEntries.toArray(new IClasspathEntry[0]);
            javaProject.setRawClasspath(newCP, new NullProgressMonitor());
          }
          catch (CoreException ignored) {
          }
        }
    }

    /**
     * Get all the projects that depend on this project. This includes both
     * project and class folder dependencies.
     */
    private IProject[] getDependingProjects(IProject project) {
        IProject[] referencingProjects = project.getReferencingProjects();
        // this only gets the class folder depending projects
        IProject[] classFolderReferences = CoreUtils
                .getDependingProjects(project).get(0);
        IProject[] dependingProjects = new IProject[referencingProjects.length
                + classFolderReferences.length];
      System.arraycopy(referencingProjects, 0, dependingProjects, 0, referencingProjects.length);
      System.arraycopy(classFolderReferences, 0, dependingProjects, referencingProjects.length, classFolderReferences.length);
        return dependingProjects;
    }

    private IPath getRelativePath(IProject project, String outJar) {
        StringBuilder sb = new StringBuilder(outJar);
        int index = sb.lastIndexOf(project.getName());
        IPath path;
        if (index > 0) {
            path = new Path(sb.substring(sb.lastIndexOf(project.getName())));
        } else {
            path = new Path(outJar);
        }
        return path.makeAbsolute();
    }

    private boolean pathExists(String pathStr) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IPath path = new Path(pathStr);
        if (path.segmentCount() == 1) {
            // bug 153682: getFolder fails when the path is a project
            return root.findMember(path).exists();
        } else {
            return root.getFolder(path).exists();
        }
    }

}
