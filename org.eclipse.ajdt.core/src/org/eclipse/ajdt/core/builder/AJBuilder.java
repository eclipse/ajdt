/*******************************************************************************
 * Copyright (c) 2002, 2010 IBM Corporation, SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Adrian Colyer, Andy Clement, Tracy Gardner - initial version
 *     Matt Chapman - moved and refactored from ui plugin to core
 *     Helen Hawkins - updated for new ajde interface (bug 148190)
 *     Andrew Eisenberg - changes for AJDT 2.0
 *******************************************************************************/
package org.eclipse.ajdt.core.builder;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aspectj.ajde.core.AjCompiler;
import org.aspectj.ajdt.internal.core.builder.AjState;
import org.aspectj.ajdt.internal.core.builder.CompilerConfigurationChangeFlags;
import org.aspectj.ajdt.internal.core.builder.IStateListener;
import org.aspectj.ajdt.internal.core.builder.IncrementalStateManager;
import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.BuildConfig;
import org.eclipse.ajdt.core.CoreUtils;
import org.eclipse.ajdt.core.TimerLogEvent;
import org.eclipse.ajdt.core.lazystart.IAdviceChangedListener;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.text.CoreMessages;
import org.eclipse.ajdt.internal.core.AspectJRTInitializer;
import org.eclipse.ajdt.internal.core.ajde.CoreCompilerConfiguration;
import org.eclipse.ajdt.internal.core.ajde.FileURICache;
import org.eclipse.core.internal.resources.ResourceException;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.BuildContext;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.CompilationParticipant;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.builder.CompilationParticipantResult;
import org.eclipse.jdt.internal.core.builder.State;
import org.eclipse.jdt.internal.core.util.Messages;
import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.osgi.util.NLS;
import org.osgi.service.prefs.BackingStoreException;

/**
 *
 */
public class AJBuilder extends IncrementalProjectBuilder {

    private static IStateListener isl = null;

    private static final List<IAJBuildListener> buildListeners = new ArrayList<>();

    /**
     * keeps track of the last workbench preference
     * (for workaround for bug 73435)
     */
    private String lastWorkbenchPreference = JavaCore.ABORT;

    public AJBuilder() {
    }

    /* (non-Javadoc)
     * @see org.eclipse.core.resources.IncrementalProjectBuilder#build(int, java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
     */
    protected IProject[] build(int kind, Map args, IProgressMonitor progressMonitor) throws CoreException {
        IProject project = getProject();
        AjCompiler compiler = AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project);

        CoreCompilerConfiguration compilerConfig = (CoreCompilerConfiguration)
                compiler.getCompilerConfiguration();

        // 100 ticks for the compiler, 1 for the pre-build actions, 1 for the post-build actions
        progressMonitor.beginTask(CoreMessages.builder_taskname, 102);

        AJLog.logStart(TimerLogEvent.TIME_IN_BUILD);
        AJLog.logStart("Pre compile");
        AJLog.log(AJLog.BUILDER,"==========================================================================================="); //$NON-NLS-1$
        AJLog.log(AJLog.BUILDER,"Build kind = " + buildKindString(kind)); //$NON-NLS-1$

        IProject[] requiredProjects = getRequiredProjects(project,true);

        // perform all pre-build actions
        CompilationParticipant[] participants = prebuild(kind, project, requiredProjects, compilerConfig);
        progressMonitor.worked(1);

        String mode;  //$NON-NLS-1$
        if (kind!=IncrementalProjectBuilder.FULL_BUILD) {
            mode = "Incremental AspectJ compilation"; //$NON-NLS-1$
        } else {
            mode = "Full AspectJ compilation"; //$NON-NLS-1$
        }
        AJLog.log(AJLog.BUILDER,"Project=" //$NON-NLS-1$
                + project.getName() + ", kind of build requested=" + mode); //$NON-NLS-1$

        // bug 270554 augment the aspect path with builder arguments
        augmentAspectPath(project, args);

        if (!isWorthBuilding(project)) {
            postBuild(kind, true, participants, compiler);
            AJLog.log(AJLog.BUILDER,
                    "build: Abort due to missing classpath/inpath/aspectpath entries"); //$NON-NLS-1$
            AJLog.logEnd(AJLog.BUILDER, TimerLogEvent.TIME_IN_BUILD);
            progressMonitor.done();
            return requiredProjects;
        }

        // workaround for bug 73435
        IProject[] dependingProjects = getDependingProjects(project);
        IJavaProject javaProject = JavaCore.create(project);
        if (!javaProject.hasBuildState() && dependingProjects.length > 0) {
            updateJavaCompilerPreferences(dependingProjects);
        }
        // end of workaround

        AJLog.logStart("Flush included source file cache");
        // Flush the list of included source files stored for this project
        BuildConfig.flushIncludedSourceFileCache(project);
        AJLog.logEnd(AJLog.BUILDER, "Flush included source file cache");

        AJLog.logStart("Check delta");
        // Check the delta - we only want to proceed if something relevant
        // in this project has changed (a .java file, a .aj file or a
        // .lst file)
        IResourceDelta delta = getDelta(getProject());
        // copy over any new resources (bug 78579)
        if(delta != null) {
            copyResources(javaProject,delta);
        }
        AJLog.logEnd(AJLog.BUILDER, "Check delta");

        if (kind != FULL_BUILD) {
            AJLog.logStart("Look for source/resource changes");
            if (!hasChangesAndMark(delta, project)) {

                AJLog.log(AJLog.BUILDER,"build: Examined delta - no source file or classpath changes for project "  //$NON-NLS-1$
                                + project.getName() );

                // if the source files of any projects which the current
                // project depends on have changed, then need
                // also to build the current project
                boolean continueToBuild = false;
                for (int i = 0; !continueToBuild && i < requiredProjects.length; i++) {
                    IResourceDelta otherProjDelta = getDelta(requiredProjects[i]);
                    continueToBuild = otherProjDelta != null &&
                                     (hasChangesAndMark(otherProjDelta, requiredProjects[i]));
                }

                // no changes found!  end the compilation.
                if (!continueToBuild) {
                    // bug 107027
                    compilerConfig.flushClasspathCache();
                    compilerConfig.configurationRead();  // reset config
                    postBuild(kind, true, participants, compiler);
                    AJLog.logEnd(AJLog.BUILDER, "Look for source/resource changes");
                    AJLog.log(AJLog.BUILDER, "No source/resource changes found, exiting build");
                    AJLog.logEnd(AJLog.BUILDER, TimerLogEvent.TIME_IN_BUILD);
                    progressMonitor.done();
                    return requiredProjects;
                }
            }
            AJLog.logEnd(AJLog.BUILDER, "Look for source/resource changes");
        }

        migrateToRTContainerIfNecessary(javaProject);

        IAJCompilerMonitor compilerMonitor = (IAJCompilerMonitor) compiler.getBuildProgressMonitor();


        // Bug 43711 must do a clean and rebuild if we can't
        // find a buildConfig file from a previous compilation
        if (kind == FULL_BUILD ||
            !hasValidPreviousBuildConfig(compiler.getId())) {

            cleanOutputFolders(javaProject,false);
            AJProjectModelFactory.getInstance().removeModelForProject(project);
            copyResources(javaProject);

        } else {
            // doing an incremental build
            if (AspectJCorePreferences.isIncrementalCompilationOptimizationsEnabled()) {
                // Bug 245566:
                // facilitate incremental compilation by checking
                // classpath for projects that have changed since the last build
                long timestamp = getLastBuildTimeStamp(compiler);
                compilerConfig.setClasspathElementsWithModifiedContents(getChangedRequiredProjects(timestamp));
            }
        }

        // bug 270335 -- if output locations have changed, then
        // need a new output location manager.
        compilerConfig.flushOutputLocationManagerIfNecessary(kind);
        compilerConfig.buildStarting();

        compilerMonitor.prepare(new SubProgressMonitor(progressMonitor,100));

        AJLog.log(AJLog.BUILDER_CLASSPATH,"Classpath = " + compilerConfig.getClasspath()); //$NON-NLS-1$
        AJLog.logEnd(AJLog.BUILDER,"Pre compile");

        // ----------------------------------------
        // Do the compilation
        AJLog.logStart(TimerLogEvent.TIME_IN_AJDE);
        if (kind == FULL_BUILD) {
            compiler.buildFresh();
        } else {
            compiler.build();
        }
        AJLog.logEnd(AJLog.BUILDER, TimerLogEvent.TIME_IN_AJDE);
        // compilation is done
        // ----------------------------------------

        doRefreshAfterBuild(project, dependingProjects, javaProject);

        // do the cleanup
        // bug 107027
        compilerConfig.flushClasspathCache();


        postBuild(kind, false, participants, compiler);
        progressMonitor.worked(1);
        progressMonitor.done();

        AJLog.logEnd(AJLog.BUILDER, TimerLogEvent.TIME_IN_BUILD);
        return requiredProjects;
    }

    /**
     * Performs post build actions including calling listeners and handling compilation participants
     *
     * returns any extra problems to be applied for this build
     */
    private void postBuild(int kind, boolean noSourceChanges, CompilationParticipant[] participants, AjCompiler compiler) {

        final IJavaProject javaProject = JavaCore.create(getProject());
        Map<IFile, List<CategorizedProblem>> newProblems = Collections.emptyMap();

        // now handle participants
        if (participants != null) {
            if (noSourceChanges) {
                for (final CompilationParticipant participant : participants) {
                    // from ReconcileWorkingCopyOperation
                    SafeRunner.run(new ISafeRunnable() {
                        public void handleException(Throwable exception) {
                            if (exception instanceof Error) {
                                throw (Error) exception; // errors are not supposed to be caught
                            } else if (exception instanceof OperationCanceledException)
                                throw (OperationCanceledException) exception;
                            else if (exception instanceof UnsupportedOperationException) {
                                // might want to disable participant as it tried to modify the buffer of the working copy being reconciled
                                Util.log(exception, "Reconcile participant attempted to modify the buffer of the working copy being reconciled"); //$NON-NLS-1$
                            } else
                                Util.log(exception, "Exception occurred in reconcile participant"); //$NON-NLS-1$
                        }
                        public void run() {
                            participant.buildStarting(new CompilationParticipantResult[0], false);
                            participant.buildFinished(javaProject);
                        }
                    });
                }
            } else {
                // first calculate the CompilationParticipantResults
                final BuildContext[] results = calculateCompilationParticipantResults((CoreCompilerConfiguration) compiler.getCompilerConfiguration());
                for (final CompilationParticipant participant : participants) {
                    SafeRunner.run(new ISafeRunnable() {
                        public void handleException(Throwable exception) {
                            if (exception instanceof Error) {
                                throw (Error) exception; // errors are not supposed to be caught
                            } else if (exception instanceof OperationCanceledException)
                                throw (OperationCanceledException) exception;
                            else if (exception instanceof UnsupportedOperationException) {
                                // might want to disable participant as it tried to modify the buffer of the working copy being reconciled
                                Util.log(exception, "Reconcile participant attempted to modify the buffer of the working copy being reconciled"); //$NON-NLS-1$
                            } else
                                Util.log(exception, "Exception occurred in reconcile participant"); //$NON-NLS-1$
                        }
                        public void run() {
                            participant.buildStarting(results, false);
                            if (participant.isAnnotationProcessor()) {
                                participant.processAnnotations(results);
                            }
                            participant.buildFinished(javaProject);
                        }
                    });
                }

                // extra problems and new dependencies
                newProblems = new HashMap<>();
              for (BuildContext buildContext : results) {
                AJCompilationParticipantResult result = (AJCompilationParticipantResult) buildContext;
                // Bug 349963---why is result null?
                if (result == null) {
                  continue;
                }
                List<CategorizedProblem> problems = result.getProblems();
                if (problems != null) {
                  newProblems.put(result.getFile(), problems);
                }

                String[] dependencies = result.getDependencies();
                if (dependencies != null && dependencies.length > 0) {
                  compiler.addDependencies(result.getFile().getLocation().toFile(), dependencies);
                }
              }
            }
        }
        postCallListeners(kind, noSourceChanges, newProblems);
        ((CoreCompilerConfiguration) compiler.getCompilerConfiguration()).buildComplete();
    }

    /**
     * @param project
     * @return
     */
    private BuildContext[] calculateCompilationParticipantResults(
            CoreCompilerConfiguration compilerConfig) {
        File[] allCompiled = compilerConfig.getCompiledSourceFiles();
        BuildContext[] results = new BuildContext[allCompiled.length];
        boolean errorFound = false;

        // create the name environment only so that we can get the ClasspathMultiDirectories
        for (int i = 0; i < results.length; i++) {
            IFile ifile = findWorkspaceFile(allCompiled[i], compilerConfig.getFileCache());

            if (ifile != null) {
                results[i] = new AJCompilationParticipantResult(ifile);
            } else {
                errorFound = true;
            }
        }

        if (errorFound) {
            // there is a null value in the array. must squash the array
            List<BuildContext> resultsList = new ArrayList<>(results.length);
            for (BuildContext buildContext : results) {
                if (buildContext != null) {
                    resultsList.add(buildContext);
                }
            }
            results = resultsList.toArray(new BuildContext[0]);
        }
        return results;
    }

    private IFile findWorkspaceFile(File file, FileURICache fileCache) {
        IFile ifile = null;
        try {
            IFile[] maybeFiles = fileCache.findFilesForURI(file.toURI());
            if (maybeFiles.length == 0) {
                // uh oh....shouldn't happen
                throw new CoreException(new Status(IStatus.ERROR, AspectJPlugin.PLUGIN_ID, "File outside of project is being compiled: " + file.getName()));
            } else if (maybeFiles.length == 1) {
                ifile = maybeFiles[0];
            } else {
                for (IFile maybeFile : maybeFiles) {
                    if (maybeFile.getProject().equals(getProject())) {
                        ifile = maybeFile;
                        break;
                    }
                }
            }
        } catch (CoreException ignored) {
        }
        return ifile;
    }

    private CompilationParticipant[] prebuild(int kind, IProject project,
            IProject[] requiredProjects, CoreCompilerConfiguration compilerConfig) {
        // must call this after checking whether a full build has been requested,
        // otherwise the listeners are called with a different build kind than
        // is actually carried out. In the case of the ui, then this means that
        // the markers may not be cleared properly.
        preCallListeners(kind, project, requiredProjects);

        // now calculate compilation participants
        return calculateParticipants(project);
    }

    /**
     * Calculates the {@link CompilationParticipant}s for project.  Returns null
     * if none are registered
     * @param project project to calculate for
     * @return participants or null if none
     */
    private CompilationParticipant[] calculateParticipants(IProject project) {
        final IJavaProject javaProject = JavaCore.create(project);
        CompilationParticipant[] participants = JavaModelManager.getJavaModelManager().compilationParticipants.getCompilationParticipants(javaProject);
        if (participants != null && participants.length > 0) {
            for (final CompilationParticipant participant : participants) {
                // from ReconcileWorkingCopyOperation
                SafeRunner.run(new ISafeRunnable() {
                    public void handleException(Throwable exception) {
                        if (exception instanceof Error) {
                            throw (Error) exception; // errors are not supposed to be caught
                        } else if (exception instanceof OperationCanceledException)
                            throw (OperationCanceledException) exception;
                        else if (exception instanceof UnsupportedOperationException) {
                            // might want to disable participant as it tried to modify the buffer of the working copy being reconciled
                            Util.log(exception, "Reconcile participant attempted to modify the buffer of the working copy being reconciled"); //$NON-NLS-1$
                        } else
                            Util.log(exception, "Exception occurred in reconcile participant"); //$NON-NLS-1$
                    }
                    public void run() {
                        participant.aboutToBuild(javaProject);
                    }
                });
            }
        } else {
            participants = null;
        }
        return participants;
    }

    private void augmentAspectPath(IProject project, Map args) {
        if (args.containsKey("aspectPath")) {
            AJLog.logStart("Augmenting aspect path with args from builder");
            String toAugment = (String) args.get("aspectPath");
            String[] toAugmentArr = toAugment.split(",");
            for (int i = 0; i < toAugmentArr.length; i++) {
                toAugmentArr[i] = toAugmentArr[i].trim();
            }
            AspectJCorePreferences.augmentAspectPath(project, toAugmentArr);

            try {
                IProjectDescription desc = project.getDescription();
                ICommand[] commands = desc.getBuildSpec();
              for (ICommand command : commands) {
                if (command.getBuilderName().equals(AspectJPlugin.ID_BUILDER)) {
                  Map<String, String> oldArgs = command.getArguments();
                  oldArgs.remove("aspectPath");
                  command.setArguments(oldArgs);
                  break;
                }
              }
                desc.setBuildSpec(commands);
                project.setDescription(desc, null);
            } catch (CoreException ignored) {

            }
            AJLog.logEnd(AJLog.BUILDER, "Augmenting aspect path with args from builder");

        }
    }

    /**
     * Check to see if the class paths are valid
     * @param progressMonitor
     * @param project
     * @param requiredProjects
     * @return true if aspect, in, and class paths are valid.  False if there is a problem
     * @throws CoreException
     */
    private boolean isWorthBuilding(IProject project) throws CoreException {
        // bug 159197: check inpath and aspectpath
        // and classpath
        if (!validateInpathAspectPath(project) ||
                isClasspathBroken(JavaCore.create(project).getRawClasspath(), project)) {
            AJLog.log(AJLog.BUILDER,
                    "build: Abort due to missing inpath/aspectpath/classpath entries"); //$NON-NLS-1$
            AJLog.logEnd(AJLog.BUILDER, TimerLogEvent.TIME_IN_BUILD);
            removeProblemsAndTasksFor(project);
            // make this the only problem for this project
            markProject(project, Messages.bind(Messages.build_prereqProjectHasClasspathProblems,
                    project.getName()));


            // Bug 288395---log all problem markers to the AJDT Event log
            IMarker[] markers = ResourcesPlugin.getWorkspace().getRoot().findMarkers(IJavaModelMarker.BUILDPATH_PROBLEM_MARKER,
                    false, IResource.DEPTH_INFINITE);
            if (markers.length > 0) {
                AJLog.log("Bug 288395---logging build path problems, found " + markers.length);
              for (IMarker marker : markers) {
                AJLog.log("  " + marker.getResource().getFullPath() + " : " + marker.getAttribute(IMarker.MESSAGE, "<no message>"));
              }
            }

            markers = ResourcesPlugin.getWorkspace().getRoot().findMarkers(IJavaModelMarker.CYCLE_DETECTED,
                    false, IResource.DEPTH_INFINITE);
            if (markers.length > 0) {
                AJLog.log("Bug 288395---logging build path cycles, found " + markers.length);
              for (IMarker marker : markers) {
                AJLog.log("  " + marker.getResource().getFullPath() + " : " + marker.getAttribute(IMarker.MESSAGE, "<no message>"));
              }
            }

            markers = ResourcesPlugin.getWorkspace().getRoot().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER,
                    false, IResource.DEPTH_INFINITE);
            if (markers.length > 0) {
                AJLog.log("Bug 288395---logging build path problems, found " + markers.length);
              for (IMarker marker : markers) {
                AJLog.log("  " + marker.getResource().getFullPath() + " : " + marker.getAttribute(IMarker.MESSAGE, "<no message>"));
              }
            }

            return false;
        }
        return true;
    }

    private long getLastBuildTimeStamp(AjCompiler compiler) {
        AjState state = IncrementalStateManager.retrieveStateFor(compiler.getId());
        if (state != null) {
            return state.getLastBuildTime();
        } else {
            return 0;
        }
    }

    private String buildKindString(int kind) {
        switch(kind) {
            case IncrementalProjectBuilder.AUTO_BUILD:
                return "AUTOBUILD";  //$NON-NLS-1$;
            case IncrementalProjectBuilder.INCREMENTAL_BUILD:
                return "INCREMENTALBUILD";  //$NON-NLS-1$
            case IncrementalProjectBuilder.FULL_BUILD:
                return "FULLBUILD";  //$NON-NLS-1$;
            case IncrementalProjectBuilder.CLEAN_BUILD:
                return "CLEANBUILD";  //$NON-NLS-1$
            default:
                return "UNKNOWN"; //$NON-NLS-1$
        }
    }

    /**
     * returns a list of fully qualified names of entries on the classpath
     * that have been rebuilt since last build
     *
     * @return
     */
    private List<String> getChangedRequiredProjects(long lastBuildTimestamp) {
        try {
            // first find all the projects that have changed since last build
            IProject[] projectsOnClasspath = getRequiredProjects(getProject(), true);
            List<IProject> changedProjects = new ArrayList<>();
          for (IProject project : projectsOnClasspath) {
            // get timestamp of last build for this project
            long otherTimestamp = -1;
            if (AspectJPlugin.isAJProject(project)) {
              AjCompiler compiler = AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project);
              otherTimestamp = getLastBuildTimeStamp(compiler);
            }
            else if (project.hasNature(JavaCore.NATURE_ID)) {
              Object s = JavaModelManager.getJavaModelManager().getLastBuiltState(project, null);
              if (s != null && s instanceof State) {
                State state = (State) s;
                // need to use reflection to get at the last build time
                otherTimestamp = getLastBuildTime(state);
              }
            }
            else {
              otherTimestamp = -1;
            }
            if (lastBuildTimestamp <= otherTimestamp) {
              changedProjects.add(project);
            }
          }
            List<String> changedEntries = new ArrayList<>();
            Set<String> noDups = new HashSet<>();  // used to ensure there are no dups

            // now that we have all the projects, need to find out what they contribute to
            // this project's path.  could be itself, a jar, or a class folder
            if (changedProjects.size() > 0) {
                IClasspathEntry[] thisClasspath = JavaCore.create(getProject()).getResolvedClasspath(true);
              for (IProject changedProject : changedProjects) {
                for (IClasspathEntry classpathEntry : thisClasspath) {
                  switch (classpathEntry.getEntryKind()) {
                    case IClasspathEntry.CPE_PROJECT:
                      if (changedProject.getFullPath().equals(classpathEntry.getPath())) {
                        // resolve project and add all entries
                        List<String> toAdd = listOfClassPathEntriesToListOfString(AspectJCorePreferences.resolveDependentProjectClasspath(
                          classpathEntry, changedProject));
                        for (String pathStr : toAdd) {
                          if (!noDups.contains(pathStr)) {
                            changedEntries.add(pathStr);
                            noDups.add(pathStr);
                          }
                        }
                      }
                      break;
                    case IClasspathEntry.CPE_LIBRARY:
                      if (changedProject.getFullPath().isPrefixOf(classpathEntry.getPath())) {
                        // only add if this path exists
                        IWorkspaceRoot root = getProject().getWorkspace().getRoot();
                        IFile onPath = root.getFile(classpathEntry.getPath());
                        if (onPath.exists() ||
                            root.getFolder(onPath.getFullPath()).exists())
                        {  // may be a folder
                          String pathStr = onPath.getLocation().toPortableString();
                          if (!noDups.contains(pathStr)) {
                            changedEntries.add(pathStr);
                            noDups.add(pathStr);
                          }
                        }
                      }
                  }
                }
              }
            }
            // if all else went well, also add the inpath to the list of changed projects.
            // Adding the inpath always is just a conservative estimate of what has changed.
            //
            // For Java projects, we only know the last structural build time.  Usually this is
            // fine, but if the Java project is on the inpath, then we care about the last
            // build of any kind, which we can't be sure of.
            // (Actually, we need to know this for Aspect path projects, but aspectj can give us
            // precise time of the last build.
            //
            // So, as a conservative estimate, put all inpath entries onto the list.
            Set<File> inPathFiles = CoreCompilerConfiguration.getCompilerConfigurationForProject(getProject()).getInpath();
            if (inPathFiles != null) {
              for (File inpathFile : inPathFiles) {
                Path path = new Path(inpathFile.getAbsolutePath());
                String pathStr = path.toPortableString();
                if (!noDups.contains(pathStr)) {
                  changedEntries.add(pathStr);
                  noDups.add(pathStr);
                }
              }
            }
            return changedEntries;
        } catch (Exception e) {
            // something went wrong.
            // return null to imply everything's changed
            AspectJPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, AspectJPlugin.PLUGIN_ID,
                    "Error determining list of entries on classpath that have changed.", e));
            return null;
        }
    }

    private List<String> listOfClassPathEntriesToListOfString(
            List<IClasspathEntry> entries) {
        IWorkspaceRoot root = getProject().getWorkspace().getRoot();
        List<String> strings = new ArrayList<>(entries.size());
        for (IClasspathEntry entry : entries) {
            IPath path = entry.getPath();
            boolean exists;
            // only add if exists
            IResource onPath;
            if (path.segmentCount() > 1) {
                onPath = root.getFile(path);
                if (onPath.exists()) {
                    exists = true;
                } else {
                    // might be a class folder
                    onPath = root.getFolder(path);
                    exists = onPath.exists();
                }
            } else {
                onPath = root.getProject(path.makeRelative().toOSString());
                exists = onPath.exists();
            }

            if (exists) {
                strings.add(onPath.getLocation().toPortableString());
            }
        }
        return strings;
    }

    private static Field state_lastStructuralBuildTime = null;
    private static long getLastBuildTime(State state) throws Exception {
        if (state_lastStructuralBuildTime == null) {
            state_lastStructuralBuildTime = State.class.getDeclaredField("lastStructuralBuildTime");
            state_lastStructuralBuildTime.setAccessible(true);
        }
        return state_lastStructuralBuildTime.getLong(state);
    }

    /**
     * need to refresh the outjar and the inpath outjar
     */
    // bug 269604---refresh after build is greatly reduced in scope
    private void doRefreshAfterBuild(IProject project,
            IProject[] dependingProjects, IJavaProject javaProject) {
        AJLog.logStart("Refresh after build");
        try {
            String outjarStr = AspectJCorePreferences.getProjectOutJar(project);
            if (outjarStr != null && outjarStr.length() > 0) {
                IFile file = project.getFile(outjarStr);
                file.refreshLocal(IResource.DEPTH_ZERO, null);
            }

            String inpathOutFolderStr = AspectJCorePreferences.getProjectInpathOutFolder(project);
            if (inpathOutFolderStr != null && inpathOutFolderStr.length() > 0) {
                // bug 269999 doesn't seem like inpath out folders are refreshed on write
                IFolder folder = project.getWorkspace().getRoot().getFolder(new Path(inpathOutFolderStr));
                folder.refreshLocal(IResource.DEPTH_INFINITE, null);
            }
        } catch (CoreException ignored) {
        }
        AJLog.logEnd(AJLog.BUILDER, "Refresh after build");
    }

    private boolean hasValidPreviousBuildConfig(String configId) {
        AjState state = IncrementalStateManager.retrieveStateFor(configId);
        return  state != null && state.getBuildConfig() != null;
    }

    /**
     * Check the inpath and aspect path entries exist. Creates problem markers
     * for missing entries
     *
     * @param project
     * @return false if there are missing entries
     */
    private boolean validateInpathAspectPath(IProject project) {
        CoreCompilerConfiguration compilerConfig = (CoreCompilerConfiguration)
            AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).
            getCompilerConfiguration();
        boolean success = true;
        Set<File> inpath = compilerConfig.getInpath();
        if (inpath != null) {
            for (File f : inpath) {
                if (!f.exists()) {
                    String missingMessage = NLS.bind(
                            CoreMessages.BuilderMissingInpathEntry, project
                                    .getName(), f.getName());
                    markProject(project, missingMessage);
                    success = false;
                }
            }
        }
        Set<File> aspectpath = compilerConfig.getAspectPath();
        if (aspectpath != null) {
            for (File f : aspectpath) {
                if (!f.exists()) {
                    String missingMessage = NLS.bind(
                            CoreMessages.BuilderMissingAspectpathEntry, project
                                    .getName(), f.getName());
                    markProject(project, missingMessage);
                    success = false;
                }
            }
        }
        return success;
    }


    private boolean isClasspathBroken(IClasspathEntry[] classpath, IProject p) throws CoreException {
        IMarker[] markers = p.findMarkers(IJavaModelMarker.BUILDPATH_PROBLEM_MARKER,
                false, IResource.DEPTH_ZERO);
      for (IMarker marker : markers) {
        if (marker.getAttribute(IMarker.SEVERITY, -1) == IMarker.SEVERITY_ERROR) {
          return true;
        }
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

    /**
     * This is taken straight from the JavaBuilder - and is what is returned
     * from the build method
     */
    private IProject[] getRequiredProjects(IProject project,
            boolean includeBinaryPrerequisites) {

        JavaProject javaProject = (JavaProject) JavaCore.create(project);
        IWorkspaceRoot workspaceRoot = project.getWorkspace().getRoot();

        if (javaProject == null || workspaceRoot == null)
            return new IProject[0];

        ArrayList<IProject> projects = new ArrayList<>();
        try {
            IClasspathEntry[] entries = javaProject.getExpandedClasspath();
          for (IClasspathEntry entry : entries) {
            IPath path = entry.getPath();
            IProject p = null;
            switch (entry.getEntryKind()) {
              case IClasspathEntry.CPE_PROJECT:
                // missing projects are considered too
                p = workspaceRoot.getProject(path.lastSegment());
                break;
              case IClasspathEntry.CPE_LIBRARY:
                if (includeBinaryPrerequisites && path.segmentCount() > 1) {
                  // some binary resources on the class path can come from
                  // projects that are not included in the project
                  // references
                  IResource resource = workspaceRoot.findMember(path
                    .segment(0));
                  if (resource instanceof IProject)
                    p = (IProject) resource;
                }
            }
            if (p != null && !projects.contains(p))
              projects.add(p);
          }
        } catch (JavaModelException e) {
            return new IProject[0];
        }
        IProject[] result = new IProject[projects.size()];
        projects.toArray(result);
        return result;
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

    /**
     * Returns the CPE_SOURCE classpath entries for the given IJavaProject
     *
     * @param IJavaProject
     */
    private IClasspathEntry[] getSrcClasspathEntry(IJavaProject javaProject) throws JavaModelException {
        List<IClasspathEntry> srcEntries = new ArrayList<>();
        if (javaProject == null) {
            return new IClasspathEntry[0];
        }
        IClasspathEntry[] cpEntry = javaProject.getRawClasspath();
      for (IClasspathEntry entry : cpEntry) {
        if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
          srcEntries.add(entry);
        }
      }
        return srcEntries.toArray(new IClasspathEntry[0]);
    }

    /**
     * Copies over all non-excluded resources into the out folders.
     *
     * Called during a full build
     *
     * @param javaProject
     */
    private void copyResources(IJavaProject project) throws CoreException {
        IClasspathEntry[] srcEntries = getSrcClasspathEntry(project);

      for (IClasspathEntry srcEntry : srcEntries) {
        IPath srcPath = srcEntry.getPath().removeFirstSegments(1);
        IPath outPath = srcEntry.getOutputLocation();
        if (outPath == null) {
          outPath = project.getOutputLocation();
        }
        outPath = outPath.removeFirstSegments(1);
        if (!srcPath.equals(outPath)) {
          final char[][] inclusionPatterns = ((ClasspathEntry) srcEntry)
            .fullInclusionPatternChars();
          final char[][] exclusionPatterns = ((ClasspathEntry) srcEntry)
            .fullExclusionPatternChars();

          final IContainer srcContainer = getContainerForGivenPath(srcPath, project.getProject());
          if (!srcContainer.exists()) {
            continue;
          }
          final int segmentsToRemove = srcContainer.getLocation().segmentCount();
          final IContainer outContainer = getContainerForGivenPath(outPath, project.getProject());
          if (outContainer.getType() == IResource.FOLDER && (!outContainer.exists())) {
            // also ensure parent folders exist
            createFolder(outPath, getProject(), false);
          }
          IResourceVisitor copyVisitor = resource -> {
            if (Util.isExcluded(resource, inclusionPatterns, exclusionPatterns)) {
              return false;
            }
            else if (resource.getType() == IResource.PROJECT) {
              return true;
            }

            if (resource.getType() == IResource.FOLDER || !isSourceFile(resource)) {
              // refresh to ensure that resource has not been deleted from file system
              resource.refreshLocal(IResource.DEPTH_ZERO, null);

              if (resource.exists()) {
                switch (resource.getType()) {
                  case IResource.FOLDER:
                    // ensure folder exists and is derived
                    IPath outPath1 = resource.getLocation().removeFirstSegments(segmentsToRemove);
                    IFolder outFolder = (IFolder) createFolder(outPath1, outContainer, true);

                    // outfolder itself should not be derived
                    if (outFolder.equals(outContainer)) {
                      outFolder.setDerived(false, null);
                    }
                    break;

                  case IResource.FILE:
                    // if this is not a CU, then copy over and mark as derived
                    if (!isSourceFile(resource)) {
                      outPath1 = resource.getLocation().removeFirstSegments(segmentsToRemove);
                      IFile outFile = outContainer.getFile(outPath1);
                      // check to make sure that resource has not been deleted from the file
                      // system without a refresh
                      if (!outFile.exists()) {
                        try {
                          resource.copy(outFile.getFullPath(), IResource.DERIVED | IResource.FORCE, null);
                          Util.setReadOnly(outFile, false);
                        }
                        catch (ResourceException e) {
                          resource.refreshLocal(IResource.DEPTH_ZERO, null);
                          if (resource.exists()) {
                            // probably hit https://bugs.eclipse.org/bugs/show_bug.cgi?id=331036
                            // We just checked to see if the outfile exists, but we get this exception
                            // anyway.  It might be that it has not been refreshed.
                            if (e.getStatus().getCode() == IResourceStatus.FAILED_WRITE_LOCAL) {
                              AJLog.log(AJLog.BUILDER, "Could not write to resource '" + resource + "'.  " +
                                                       "It probbly already exists on disk.  Try a clean build.");
                              outFile.refreshLocal(IResource.DEPTH_ZERO, null);
                            }
                            else {
                              throw e;
                            }
                          }
                          else {
                            // resource was deleted in the middle of the build.  Can safely ignore this
                          }
                        }
                      }
                    }
                    break;
                }
                return true;
              }
            }
            return false;
          };

          srcContainer.accept(copyVisitor);

        }
      }
    }

    boolean isSourceFile(IResource resource) {
        String extension = resource.getFileExtension();
        return extension != null && (
                extension.equals("java") ||
                extension.equals("aj"));
    }



    /**
     * Copies non-src resources to the output directory (bug 78579). The main
     * part of this method was taken from
     * {@link org.eclipse.jdt.internal.core.builder.IncrementalImageBuilder.findSourceFiles(IResourceDelta)}
     *
     * @param IJavaProject - the project which is being built
     * @param IResourceDelta - the projects delta
     * @throws CoreException
     */
    private boolean copyResources(IJavaProject project, IResourceDelta delta) throws CoreException {
        IClasspathEntry[] srcEntries = getSrcClasspathEntry(project);

      for (IClasspathEntry srcEntry : srcEntries) {
        IPath srcPath = srcEntry.getPath().removeFirstSegments(1);
        IContainer srcContainer = getContainerForGivenPath(srcPath, project.getProject());

        // handle case where project root is a source folder
        if (srcContainer.equals(project.getProject())) {
          int segmentCount = delta.getFullPath().segmentCount();
          IResourceDelta[] children = delta.getAffectedChildren();
          for (IResourceDelta child : children) {
            if (!isExcludedFromProject(project, child.getFullPath(), srcEntries)) {
              copyResources(project, child, srcEntry, segmentCount);
            }
          }
        }
        else {
          IPath projectRelativePath = srcEntry.getPath().removeFirstSegments(1);
          projectRelativePath.makeRelative();

          IResourceDelta sourceDelta = delta.findMember(projectRelativePath);
          if (sourceDelta != null) {
            if (sourceDelta.getKind() == IResourceDelta.REMOVED) {
              return false; // removed source folder should not make it here, but handle anyways (ADDED is supported)
            }
            int segmentCount = sourceDelta.getFullPath().segmentCount();
            IResourceDelta[] children = sourceDelta.getAffectedChildren();
            try {
              for (IResourceDelta child : children) {
                copyResources(project, child, srcEntry, segmentCount);
              }
            }
            catch (ResourceException e) {
              // catch the case that a package has been renamed and collides on disk with an as-yet-to-be-deleted package
              if (e.getStatus().getCode() == IResourceStatus.CASE_VARIANT_EXISTS) {
                return false;
              }
              throw e; // rethrow
            }
          }
        }
      }
        return true;
    }


    /**
     * Copies non-src resources to the output directory (bug 78579). The main
     * part of this method was taken from
     * org.eclipse.jdt.internal.core.builder.IncrementalImageBuilder.findSourceFiles(IResourceDelta,ClasspathMultiDirectory,int)
     *
     * @param IJavaProject - the project which is being built
     * @param IResourceDelta - the projects delta
     * @param IClasspathEntry - the src entry on the classpath
     * @param int - the segment count
     *
     * @throws CoreException
     */
    private void copyResources(IJavaProject javaProject, IResourceDelta sourceDelta, IClasspathEntry srcEntry, int segmentCount) throws CoreException {
        IResource resource = sourceDelta.getResource();
        // bug 161739: skip excluded resources
        char[][] inclusionPatterns = ((ClasspathEntry) srcEntry)
                .fullInclusionPatternChars();
        char[][] exclusionPatterns = ((ClasspathEntry) srcEntry)
                .fullExclusionPatternChars();
        if (Util.isExcluded(resource, inclusionPatterns, exclusionPatterns)) {
            return;
        }

        IPath outputPath = srcEntry.getOutputLocation();
        if (outputPath == null) {
            outputPath = javaProject.getOutputLocation();
        }
        outputPath = outputPath.removeFirstSegments(1).makeRelative();

        IContainer outputFolder = getContainerForGivenPath(outputPath,javaProject.getProject());
        IContainer srcContainer = getContainerForGivenPath(srcEntry.getPath().removeFirstSegments(1),javaProject.getProject());

        IPath deltaPath = resource.getFullPath().removeFirstSegments(segmentCount);

        switch(resource.getType()) {
            case IResource.FOLDER :
                IContainer folderToRefresh = outputFolder.getFolder(deltaPath);
                switch (sourceDelta.getKind()) {
                    case IResourceDelta.ADDED :
                        createFolder(deltaPath, outputFolder, true); // ensure package exists in the output folder
                        // fall through & collect all the resource files
                    case IResourceDelta.CHANGED :
                        IResourceDelta[] children = sourceDelta.getAffectedChildren();
                      for (IResourceDelta child : children) {
                        copyResources(javaProject, child, srcEntry, segmentCount);
                      }
                        break;

                    case IResourceDelta.REMOVED :
                        IClasspathEntry[] srcEntries = getSrcClasspathEntry(javaProject);
                        if (srcEntries.length > 1) {
                          for (IClasspathEntry entry : srcEntries) {
                            IPath srcPath = entry.getPath().removeFirstSegments(1);
                            IFolder srcFolder = javaProject.getProject().getFolder(srcPath);
                            if (srcFolder.getFolder(deltaPath).exists()) {
                              // only a package fragment was removed, same as removing multiple source files
                              // ensure package exists in the output folder
                              // ADE---wait...why are we doing this???  why not just delete and be done with it?
                              // not going to change this because I don't know the ramifications.
                              createFolder(deltaPath, outputFolder, true);
                              IResourceDelta[] removedChildren = sourceDelta.getAffectedChildren();
                              for (IResourceDelta removedChild : removedChildren) {
                                copyResources(javaProject, removedChild, srcEntry, segmentCount);
                              }
                              folderToRefresh.refreshLocal(IResource.DEPTH_ZERO, null);
                              return;
                            }
                          }
                        }
                        IFolder removedPackageFolder = outputFolder.getFolder(deltaPath);
                        if (removedPackageFolder.exists()) {
                            removedPackageFolder.delete(IResource.FORCE, null);
                        }
                        break;
                } // switch(sourceDelta.getKind())
                folderToRefresh.refreshLocal(IResource.DEPTH_ZERO, null);
                break;

            case IResource.FILE :
                // only do something if the output folder is different to the src folder
                if (!outputFolder.equals(srcContainer)) {
                    // copy all resource deltas to the output folder
                    if (deltaPath == null) return;
                    // don't want to copy over .aj or .java files
                    if (deltaPath.getFileExtension() != null
                            && (deltaPath.getFileExtension().equals("aj") //$NON-NLS-1$
                                    || deltaPath.getFileExtension().equals("java"))) { //$NON-NLS-1$
                        break;
                    }

                    IResource fileToRefresh = outputFolder.getFile(deltaPath);
                    switch (sourceDelta.getKind()) {
                        case IResourceDelta.ADDED :
                            if (fileToRefresh.exists()) {
                                AJLog.log(AJLog.BUILDER,"Deleting existing file " + deltaPath);//$NON-NLS-1$
                                fileToRefresh.delete(IResource.FORCE, null);
                            }
                            AJLog.log(AJLog.BUILDER,"Copying added file " + deltaPath);//$NON-NLS-1$
                            createFolder(deltaPath.removeLastSegments(1), outputFolder, true);
                            resource.copy(fileToRefresh.getFullPath(), IResource.FORCE | IResource.DERIVED, null);
                            Util.setReadOnly(fileToRefresh, false); // just in case the original was read only
                            fileToRefresh.refreshLocal(IResource.DEPTH_ZERO,null);
                            // mark this change so compiler knows about it.
                            CoreCompilerConfiguration.getCompilerConfigurationForProject(getProject())
                                    .configurationChanged(
                                            CompilerConfigurationChangeFlags.PROJECTSOURCERESOURCES_CHANGED);
                            break;
                        case IResourceDelta.REMOVED :
                            if (fileToRefresh.exists()) {
                                AJLog.log(AJLog.BUILDER,"Deleting removed file " + deltaPath);//$NON-NLS-1$
                                fileToRefresh.delete(IResource.FORCE, null);
                            }
                            // mark this change so compiler knows about it.
                            CoreCompilerConfiguration.getCompilerConfigurationForProject(getProject())
                                    .configurationChanged(
                                            CompilerConfigurationChangeFlags.PROJECTSOURCERESOURCES_CHANGED);
                            break;
                        case IResourceDelta.CHANGED :
                            if ((sourceDelta.getFlags() & IResourceDelta.CONTENT) == 0
                                    && (sourceDelta.getFlags() & IResourceDelta.ENCODING) == 0) {
                                return; // skip it since it really isn't changed
                            }
                            if (fileToRefresh.exists()) {
                                AJLog.log(AJLog.BUILDER,"Deleting existing file " + deltaPath);//$NON-NLS-1$
                                fileToRefresh.delete(IResource.FORCE, null);
                            }
                            AJLog.log(AJLog.BUILDER,"Copying changed file " + deltaPath);//$NON-NLS-1$
                            createFolder(deltaPath.removeLastSegments(1), outputFolder, true);
                            resource.copy(fileToRefresh.getFullPath(), IResource.FORCE | IResource.DERIVED, null);
                            Util.setReadOnly(fileToRefresh, false); // just in case the original was read only
                            break;
                    }
                    fileToRefresh.refreshLocal(IResource.DEPTH_ZERO,null);
                }  // switch (sourceDelta.getKind())
                break;
        }  // switch(resource.getType())
    }

    /**
     * Returns the IContainer for the given path in the given project
     *
     * @param path
     * @param project
     * @return
     */
    private IContainer getContainerForGivenPath(IPath path, IProject project) {
        if (path.toOSString().equals("")) { //$NON-NLS-1$
            return project;
        }
        return project.getFolder(path);
    }

    /**
     * Creates folder with the given path in the given output folder. This method is taken
     * from org.eclipse.jdt.internal.core.builder.AbstractImageBuilder.createFolder(..)
     */
    private IContainer createFolder(IPath packagePath, IContainer outputFolder, boolean isDerived) throws CoreException {
        // Fix for 98663 - create the bin folder if it doesn't exist
        if(!outputFolder.exists() && outputFolder instanceof IFolder) {
            ((IFolder)outputFolder).create(true, true, null);
        }
        if (packagePath.isEmpty()) return outputFolder;
        IFolder folder = outputFolder.getFolder(packagePath);
        if (!folder.exists()) {
            createFolder(packagePath.removeLastSegments(1), outputFolder, isDerived);
            int flags = isDerived ? IResource.FORCE | IResource.DERIVED : IResource.FORCE;
            folder.create(flags, true, null);
        }
        return folder;
    }

    /**
     * This method is taken
     * from org.eclipse.jdt.internal.core.builder.AbstractImageBuilder.isExcludedFromProject(IPath)
     */
    private boolean isExcludedFromProject(IJavaProject javaProject, IPath childPath, IClasspathEntry[] srcEntries) throws JavaModelException {
        // answer whether the folder should be ignored when walking the project as a source folder
        if (childPath.segmentCount() > 2) return false; // is a subfolder of a package

      for (IClasspathEntry srcEntry : srcEntries) {
        IPath outputPath = srcEntry.getOutputLocation();
        if (outputPath == null) {
          outputPath = javaProject.getOutputLocation();
        }
        outputPath = outputPath.removeFirstSegments(1).makeRelative();
        if (childPath.equals(getContainerForGivenPath(outputPath, javaProject.getProject()).getFullPath()))
          return true;

        IPath srcPath = srcEntry.getPath().removeFirstSegments(1);
        if (childPath.equals(getContainerForGivenPath(srcPath, javaProject.getProject()).getFullPath()))
          return true;
      }
        // skip default output folder which may not be used by any source folder
        return childPath.equals(javaProject.getOutputLocation());
    }

    /**
     * Tidies up the output folder before a build. JDT does this by going
     * through the source and deleting the relevant .class files. That works ok
     * if you are also listening to things like resource deletes so that you
     * tidy up some .class files as you go along. AJDT does not do this, so we
     * use a different approach here. We go through the output directory and
     * recursively delete all .class files. This, of course, doesn't cope with
     * resources that might be in the output directory - but I can't delete
     * everything because some people have the output directory set to the top
     * level of their project.
     *
     * There is a subtlety with linked folders being used as output folders, but
     * it does work, I added an AJDTUtils helper method which attempts IPath
     * dereferencing. if the IPath is a 'linked folder' then the helper method
     * returns the dereferenced value.
     */
    protected void cleanOutputFolders(IJavaProject project, boolean refresh)
            throws CoreException {
        // Check the project property
        boolean deleteAll = JavaCore.CLEAN.equals(project.getOption(
                JavaCore.CORE_JAVA_BUILD_CLEAN_OUTPUT_FOLDER, true));
        if (deleteAll) {
            int numberDeleted = 0;
            IPath[] paths = CoreUtils.getOutputFolders(project);
          for (IPath path : paths) {
            numberDeleted += cleanFolder(project, path, refresh);
          }

            // clean inpath out folder
            String inpathOut = AspectJCorePreferences.getProjectInpathOutFolder(project.getProject());
            if (inpathOut != null && !inpathOut.equals("")) { //$NON-NLS-1$
                IPath inpathOutfolder = new Path(inpathOut);
                numberDeleted += cleanFolder(project, inpathOutfolder, refresh);
            }

            AJLog.log(AJLog.BUILDER,"Builder: Tidied output folder(s), removed class files and derived resources"); //$NON-NLS-1$
        }
    }

    private int cleanFolder(IJavaProject project, IPath outputFolder, boolean refresh) throws CoreException {
        IResource outputResource;
        if (outputFolder.segmentCount() == 1) {
            // project root
            outputResource = project.getProject();
        } else {
            outputResource = ResourcesPlugin.getWorkspace().getRoot()
                    .getFolder(outputFolder);
            if (!outputResource.exists()) {
                return 0;
            }
        }

        int numberDeleted = wipeFiles(outputResource); //$NON-NLS-1$


        if (refresh) {
            outputResource.refreshLocal(IResource.DEPTH_INFINITE, null);
        }
        return numberDeleted;
    }

    public static void cleanAJFilesFromOutputFolder(
            IPath workspaceRelativeOutputPath) throws CoreException {
        IFolder out = ResourcesPlugin.getWorkspace().getRoot().getFolder(
                workspaceRelativeOutputPath);
        int numberDeleted = wipeFilesOfKind(out, "aj"); //$NON-NLS-1$
        out.refreshLocal(IResource.DEPTH_INFINITE, null);
        AJLog.log(AJLog.BUILDER,"Builder: Tidied output folder, deleted " //$NON-NLS-1$
                + numberDeleted
                + " .aj files from " //$NON-NLS-1$
                + out.getFullPath()
                + (out.isLinked() ? " (Linked output folder from " //$NON-NLS-1$
                        + workspaceRelativeOutputPath.toOSString() + ")" : "")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Recursively calling function. Given some set of files (which might be
     * dirs) it deletes any files with the given extension from the filesystem and then for any
     * directories, recursively calls itself.
     *
     * BUG 101489---also delete files marked as derived
     * BUG 253528---all folders below the output folder are marked as derived.
     * so entire out folder is wiped.
     */
    private static int wipeFiles(IResource outputResource) {
       class WipeResources implements IResourceVisitor {
            int numDeleted = 0;
            public boolean visit(IResource resource) throws CoreException {
                if (resource.isDerived()) {
                    try {
                        // non-class file
                        resource.delete(true, null);
                        // num deleted will not include counts from children of deleted folders
                        numDeleted++;
                    } catch(ResourceException ignored) {
                    }
                    return false;
                }
                // continue visit to children
                return true;
            }
        }
      WipeResources visitor = new WipeResources();
        try {
            outputResource.accept(visitor);
        } catch (CoreException ignored) {
        }
        return visitor.numDeleted;
    }

    /**
     * Delete all files in the output folder with a certain
     * file extension
     */
    private static int wipeFilesOfKind(IResource outputResource,
            final String fileExtension) {
        class WipeResources implements IResourceVisitor {
            int numDeleted = 0;

            public boolean visit(IResource resource) throws CoreException {
                if (resource.isDerived()) {
                    try {
                        // non-class file
                        resource.delete(true, null);
                        numDeleted++;
                    } catch (ResourceException ignored) {}
                    return false;
                } else if (resource.getFileExtension() != null
                        && resource.getFileExtension().equals(fileExtension)) {
                    try {
                        // class file
                        resource.delete(true, null);
                        numDeleted++;
                    } catch (ResourceException ignored) {}
                }
                // continue visit to children
                return true;
            }
        }
      WipeResources visitor = new WipeResources();
        try {
            outputResource.accept(visitor);
        } catch (CoreException ignored) {}
        return visitor.numDeleted;
     }


    /**
     * This is the workaround discussed in bug 73435 for the case when projects are
     * checked out from CVS, the AJ projects have no valid build state and projects
     * depend on them.
     */
    private void updateJavaCompilerPreferences(IProject[] dependingProjects) {
        boolean setWorkbenchPref = false;
      for (IProject dependingProject : dependingProjects) {
        try {
          // Skip over any dependents that are themselves
          // AspectJ projects
          if (AspectJPlugin.isAJProject(dependingProject)) {
            continue;
          }

          // Only update dependent projects that have Java natures.
          // These could be ordinary Java projects or if we running inside
          // other Eclipse-based tools, they could be J2EE projects like dynamic
          // web projects.
          // Note that if the project does not have a Java nature then
          // the JavaCore.create() call appears to return a null.
          if (dependingProject.hasNature(JavaCore.NATURE_ID)) {
            JavaProject jp = (JavaProject) JavaCore.create(dependingProject);
            // Bug 91131 - In Eclipse 3.1 need to use IEclipsePreferences
            IEclipsePreferences projectPreferences = jp.getEclipsePreferences();
            String[] keys = projectPreferences.keys();

            if (keys.length == 0 && !setWorkbenchPref) {
              Hashtable<String, String> options = JavaCore.getOptions();
              String workbenchSetting = options.get(JavaCore.CORE_JAVA_BUILD_INVALID_CLASSPATH);
              if (lastWorkbenchPreference.equals(JavaCore.ABORT)
                  && workbenchSetting.equals(JavaCore.IGNORE))
              {
                lastWorkbenchPreference = JavaCore.IGNORE;
              }
              else if (lastWorkbenchPreference.equals(JavaCore.ABORT)
                       && workbenchSetting.equals(JavaCore.ABORT))
              {
                if (!setWorkbenchPref) {
                  options.put(JavaCore.CORE_JAVA_BUILD_INVALID_CLASSPATH, JavaCore.IGNORE);
                  JavaCore.setOptions(options);
                  setWorkbenchPref = true;
                  lastWorkbenchPreference = JavaCore.IGNORE;
                }
              }
              else if (lastWorkbenchPreference.equals(JavaCore.IGNORE)
                       && workbenchSetting.equals(JavaCore.ABORT))
              {
                if (!setWorkbenchPref) {
                  options.put(JavaCore.CORE_JAVA_BUILD_INVALID_CLASSPATH, JavaCore.IGNORE);
                  JavaCore.setOptions(options);
                  setWorkbenchPref = true;
                }
                else {
                  lastWorkbenchPreference = JavaCore.ABORT;
                }
              }
            }
            else if (keys.length > 0 && usingProjectBuildingOptions(keys)) {
              projectPreferences.put(JavaCore.CORE_JAVA_BUILD_INVALID_CLASSPATH, JavaCore.IGNORE);
              try {
                projectPreferences.flush();
              }
              catch (BackingStoreException e) {
                // problem with pref store - quietly ignore
              }
              lastWorkbenchPreference = JavaCore.getOptions().get(JavaCore.CORE_JAVA_BUILD_INVALID_CLASSPATH);
            }
          }// end if dependent has a Java nature
        }
        catch (CoreException | BackingStoreException ignored) {
        }
      }
    }

    /**
     * Bug 91131 - Checking to see if the user has selected to use project
     * setting for building. Unfortunately, there is no way of checking
     * whether the user has selected to use project settings other than to
     * see whether the options contained on the building page are in the
     * IEclipsePreferences. There is also the need for this extra check,
     * rather than just whether there are any IEclipsePreferences,
     * in Eclipse 3.1 because there are several property pages for the
     * different compiler options.
     */
    private boolean usingProjectBuildingOptions(String[] keys) {
        List<String> listOfKeys = Arrays.asList(keys);
        return (listOfKeys.contains(JavaCore.COMPILER_PB_MAX_PER_UNIT)
                || listOfKeys.contains(JavaCore.CORE_JAVA_BUILD_DUPLICATE_RESOURCE)
                || listOfKeys.contains(JavaCore.CORE_JAVA_BUILD_INVALID_CLASSPATH)
                || listOfKeys.contains(JavaCore.CORE_JAVA_BUILD_RESOURCE_COPY_FILTER)
                || listOfKeys.contains(JavaCore.CORE_ENABLE_CLASSPATH_MULTIPLE_OUTPUT_LOCATIONS)
                || listOfKeys.contains(JavaCore.CORE_CIRCULAR_CLASSPATH)
                || listOfKeys.contains(JavaCore.CORE_INCOMPLETE_CLASSPATH)
                || listOfKeys.contains(JavaCore.CORE_INCOMPATIBLE_JDK_LEVEL));
    }


    public static void addAJBuildListener(IAJBuildListener listener) {
        // bug 281687 --- synchronize access to listener list
        synchronized (buildListeners) {
            if (!buildListeners.contains(listener)) {
                buildListeners.add(listener);
            }
        }
    }

    public static void removeAJBuildListener(IAJBuildListener listener) {
        // bug 281687 --- synchronize access to listener list
        synchronized (buildListeners) {
            buildListeners.remove(listener);
        }
    }

    public static void addAdviceListener(IAdviceChangedListener adviceListener) {
        // bug 281687 --- synchronize access to listener list
        synchronized (buildListeners) {
            for (IAJBuildListener listener : buildListeners) {
                listener.addAdviceListener(adviceListener);
            }
        }
    }

    public static void removeAdviceListener(IAdviceChangedListener adviceListener) {
        // bug 281687 --- synchronize access to listener list
        synchronized (buildListeners) {
            for (IAJBuildListener listener : buildListeners) {
                listener.removeAdviceListener(adviceListener);
            }
        }
    }

    private void preCallListeners(int kind, IProject project, IProject[] requiredProjects) {
        // bug 281687 --- synchronize access to listener list
        synchronized (buildListeners) {
            for (IAJBuildListener listener : buildListeners) {
                listener.preAJBuild(kind, project, requiredProjects);
            }
        }
    }

    private void postCallListeners(int kind, boolean noSourceChanges, Map<IFile, List<CategorizedProblem>> newProblems) {
        // bug 281687 --- synchronize access to listener list
        synchronized (buildListeners) {
            for (IAJBuildListener listener : buildListeners) {
                listener.postAJBuild(kind, getProject(), noSourceChanges, newProblems);
            }
        }
    }

    private void postCleanCallListeners() {
        // bug 281687 --- synchronize access to listener list
        synchronized (buildListeners) {
            for (IAJBuildListener listener : buildListeners) {
                listener.postAJClean(getProject());
            }
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.core.resources.IncrementalProjectBuilder#clean(org.eclipse.core.runtime.IProgressMonitor)
     */
    protected void clean(IProgressMonitor monitor) throws CoreException {
        // implemented as part of bug 101481
        IProject project = getProject();
        // Remove the compiler instance associated with this project
        // from the factory
        AspectJPlugin.getDefault().getCompilerFactory().removeCompilerForProject(project);
        AJProjectModelFactory.getInstance().removeModelForProject(project);


        // handle participants
        final IJavaProject javaProject = JavaCore.create(project);
        CompilationParticipant[] participants = JavaModelManager.getJavaModelManager().compilationParticipants.getCompilationParticipants(javaProject);
        if (participants != null) {
            for (final CompilationParticipant participant : participants) {
                // from ReconcileWorkingCopyOperation
                SafeRunner.run(new ISafeRunnable() {
                    public void handleException(Throwable exception) {
                        if (exception instanceof Error) {
                            throw (Error) exception; // errors are not supposed to be caught
                        } else if (exception instanceof OperationCanceledException)
                            throw (OperationCanceledException) exception;
                        else if (exception instanceof UnsupportedOperationException) {
                            // might want to disable participant as it tried to modify the buffer of the working copy being reconciled
                            Util.log(exception, "Reconcile participant attempted to modify the buffer of the working copy being reconciled"); //$NON-NLS-1$
                        } else
                            Util.log(exception, "Exception occurred in reconcile participant"); //$NON-NLS-1$
                    }
                    public void run() {
                        participant.cleanStarting(javaProject);
                    }
                });
            }
        }


        removeProblemsAndTasksFor(project);
        // clean the output folders and do a refresh if not
        // automatically building (so that output dir reflects the
        // changes)
        cleanOutputFolders(JavaCore.create(project),
                !AspectJPlugin.getWorkspace().getDescription().isAutoBuilding());


        postCleanCallListeners();
    }

    private void removeProblemsAndTasksFor(IResource resource) {
        try {
            if (resource != null && resource.exists()) {
                resource.deleteMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
                resource.deleteMarkers(IJavaModelMarker.TASK_MARKER, false, IResource.DEPTH_INFINITE);

                // now removed markers from compilation participants
                HashSet<String> managedMarkers = JavaModelManager.getJavaModelManager().compilationParticipants.managedMarkerTypes();
                for (String managedMarker : managedMarkers) {
                    resource.deleteMarkers(managedMarker, true, IResource.DEPTH_INFINITE);
                }
            }
        } catch (CoreException ignored) {
        }
        if (resource != null) {
            AJLog.log(AJLog.BUILDER,"Removed problems and tasks for project "+resource.getName()); //$NON-NLS-1$
        }
    }

    /**
     * Looks for changes of any relevant kind in this project and marks them in
     * the compiler configuration
     */
    public boolean hasChangesAndMark(IResourceDelta delta, IProject project) {
        AJLog.logStart("Looking for and marking configuration changes in " + project.getName());
        CoreCompilerConfiguration compilerConfiguration = CoreCompilerConfiguration.getCompilerConfigurationForProject(project);
        boolean hasChanges = sourceFilesChanged(delta, project, compilerConfiguration);
        hasChanges |= classpathChanged(delta, compilerConfiguration);
        hasChanges |= manifestChanged(delta, compilerConfiguration);
        hasChanges |= projectSpecificSettingsChanged(delta, compilerConfiguration);
        AJLog.logEnd(AJLog.BUILDER, "Looking for and marking configuration changes in " + project.getName());
        AJLog.log(AJLog.BUILDER, "\tConfiguration changes found: " + hasChanges);
        return hasChanges;
    }

    private boolean projectSpecificSettingsChanged(IResourceDelta delta, CoreCompilerConfiguration compilerConfiguration) {
        IResourceDelta settingsDelta = delta.findMember(new Path(".settings"));
        // assume that if something in this folder has changed, then
        // it is a project specific setting
        if (settingsDelta != null &&
                   settingsDelta.getAffectedChildren().length > 0) {
            compilerConfiguration.configurationChanged(
                    CompilerConfigurationChangeFlags.JAVAOPTIONS_CHANGED |
                    CompilerConfigurationChangeFlags.NONSTANDARDOPTIONS_CHANGED |
                    CompilerConfigurationChangeFlags.OUTJAR_CHANGED);
            return true;
        } else {
            return false;
        }
    }

    private boolean classpathChanged(IResourceDelta delta, CoreCompilerConfiguration compilerConfiguration) {
        if (delta.findMember(new Path(".classpath")) != null) {
            // we don't know exactly what has changed, so be conservative
            compilerConfiguration.configurationChanged(
                CompilerConfigurationChangeFlags.CLASSPATH_CHANGED |
                CompilerConfigurationChangeFlags.ASPECTPATH_CHANGED |
                CompilerConfigurationChangeFlags.INPATH_CHANGED |
                CompilerConfigurationChangeFlags.OUTPUTDESTINATIONS_CHANGED);
            return true;
        } else {
            return false;
        }
    }

    private boolean manifestChanged(IResourceDelta delta, CoreCompilerConfiguration compilerConfiguration) {
        // we make an assumption here that the project actually cares about
        // the manifest file (ie- it is a plugin project or an OSGi project
        if (delta.findMember(new Path("META-INF/MANIFEST.MF")) != null) {
            compilerConfiguration.configurationChanged(
                    CompilerConfigurationChangeFlags.CLASSPATH_CHANGED);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Determine if any source files have changed and if so record it in the compiler configuration for the
     * project
     * @param delta
     * @param project
     * @return true if a source file has changed.  False otherwise
     */
    private boolean sourceFilesChanged(IResourceDelta delta, IProject project, CoreCompilerConfiguration compilerConfiguration) {
        if (delta != null && delta.getAffectedChildren().length != 0) {

            Set<IFile> includedFileNames = BuildConfig.getIncludedSourceFilesSet(project);

            IJavaProject javaProject = JavaCore.create(project);
            if (javaProject == null) {
                return true;
            }
            try {
                SourceFilesChangedVisitor visitor = new SourceFilesChangedVisitor(project, includedFileNames);
                delta.accept(visitor);

                if (visitor.hasChanges()) {
                    AJLog.log(AJLog.BUILDER,"build: Examined delta - " + visitor.getNumberChanged() + //$NON-NLS-1$
                            " changed, " + visitor.getNumberAdded() + " added, and " + //$NON-NLS-1$ //$NON-NLS-2$
                            visitor.getNumberRemoved() + " deleted source files in " //$NON-NLS-1$
                            + "required project " + project.getName() ); //$NON-NLS-1$
                    return true;
                } else {
                    return false;
                }
            } catch (CoreException e) {
                AspectJPlugin.getDefault().getLog().log(
                        new Status(IStatus.ERROR,
                                AspectJPlugin.PLUGIN_ID, "Error finding source file changes", e));
            }
        }
        return false;
    }

    private static class SourceFilesChangedVisitor implements IResourceDeltaVisitor {
        private final Set<IFile> includedFileNames;
        private final CoreCompilerConfiguration compilerConfiguration;
        private int numberChanged;
        private int numberAdded;
        private int numberRemoved;

        private SourceFilesChangedVisitor(IProject affectedProject,
                Set<IFile> includedFileNames) {
            this.includedFileNames = includedFileNames;
            compilerConfiguration = CoreCompilerConfiguration.getCompilerConfigurationForProject(affectedProject);
            numberChanged = 0;
            numberAdded = 0;
            numberRemoved = 0;
        }

        /**
         * Look for changed resources that we care about.
         */
        public boolean visit(IResourceDelta delta) throws CoreException {
            String resname = delta.getFullPath().toString();

            if (delta.getResource().getType() == IResource.FILE) {
                if (CoreUtils.ASPECTJ_SOURCE_FILTER.accept(resname)) {

                    switch (delta.getKind()) {
                    case IResourceDelta.REMOVED:
                    case IResourceDelta.REMOVED_PHANTOM:
                        // don't check to see if these files are on the list of included
                        // files because they have already been removed and we won't find
                        // them on the list.
                        // be conservative and set the flag regardless.
                        compilerConfiguration.configurationChanged(
                                CompilerConfigurationChangeFlags.PROJECTSOURCEFILES_CHANGED);
                        numberRemoved++;
                        break;

                    case IResourceDelta.ADDED:
                    case IResourceDelta.ADDED_PHANTOM:
                        if (includedFileNames.contains(delta.getResource())) {
                            compilerConfiguration.configurationChanged(
                                    CompilerConfigurationChangeFlags.PROJECTSOURCEFILES_CHANGED);
                            numberAdded++;
                            break;
                        }
                    case IResourceDelta.CHANGED:
                        if (includedFileNames.contains(delta.getResource())) {
                            compilerConfiguration.addModifiedFile(new File(delta.getResource()
                                    .getLocation().toPortableString()));
                            numberChanged++;
                        }
                    }
                }
                return false;
            } else {
                // want to fully traverse this delta if not
                // a leaf node
                return true;
            }
        }
        public int getNumberChanged() {
            return numberChanged;
        }
        public int getNumberAdded() {
            return numberAdded;
        }
        public int getNumberRemoved() {
            return numberRemoved;
        }

        public boolean hasChanges() {
            return numberAdded + numberChanged + numberRemoved > 0;
        }
    }

    public static void addStateListener() {
        if (isl == null) {
            // Uses secret API in state to get callbacks on useful events
            isl = new IStateListener() {
                public void detectedClassChangeInThisDir(File f) {
                }

                public void aboutToCompareClasspaths(List<String> oldClasspath, List<String> newClasspath) {
                }

                public void pathChangeDetected() {
                }

                public void buildSuccessful(boolean wasFull) {
                    AJLog.log(AJLog.COMPILER,"AspectJ reports build successful, build was: " + //$NON-NLS-1$
                            (wasFull ? "FULL" : "INCREMENTAL")); //$NON-NLS-1$ //$NON-NLS-2$
                }

                public void detectedAspectDeleted(File f) {
                }

                public void recordDecision(String decision) {
                    AJLog.log(AJLog.COMPILER,decision);
                }

                public void recordInformation(String info) {
                    AJLog.log(AJLog.COMPILER,info);
                }};
        }
        AjState.stateListener = isl;
    }

    public static void removeStateListener() {
        AjState.stateListener = null;
    }

    private void migrateToRTContainerIfNecessary(IJavaProject javaProject) {
        if (!AspectJRTInitializer.hasBeenUsed) {
            // if the old ASPECTJRT_LIB var hasn't been initialized
            // then there definitely won't be anything to migrate
            return;
        }
        try {
            IClasspathEntry[] entries = javaProject.getRawClasspath();
            for (int i = 0; i < entries.length; i++) {
                if (entries[i].getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
                    String var = entries[i].getPath().segment(0);
                    if (var.equals("ASPECTJRT_LIB")) { //$NON-NLS-1$
                        // replace with AspectJRT container
                        entries[i] = JavaCore.newContainerEntry(
                                new Path(AspectJPlugin.ASPECTJRT_CONTAINER), false);
                        javaProject.setRawClasspath(entries, new NullProgressMonitor());
                        return;
                    }
                }
            }
        } catch (JavaModelException ignored) {
        }
    }

}
