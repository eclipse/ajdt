/********************************************************************
 * Copyright (c) 2007 Contributors. All rights reserved.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution and is available at
 * http://eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation
 * 				 Helen Hawkins   - initial version (bug 148190)
 *******************************************************************/
package org.eclipse.ajdt.internal.ui.ajde;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.aspectj.ajde.core.IBuildMessageHandler;
import org.aspectj.bridge.IMessage;
import org.aspectj.bridge.IMessage.Kind;
import org.aspectj.bridge.ISourceLocation;
import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.internal.core.ajde.CoreCompilerConfiguration;
import org.eclipse.ajdt.internal.core.ajde.FileURICache;
import org.eclipse.ajdt.internal.ui.editor.AspectJEditor;
import org.eclipse.ajdt.internal.ui.preferences.AspectJPreferences;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.internal.ui.tracing.DebugTracing;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.ajdt.ui.IAJModelMarker;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Preferences;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaModelManager;

/**
 * IBuildMessageHandler implementation which records warnings in the Problems
 * View. All Errors with stack traces and ABORT's are displayed in an error
 * dialog. By default it ignores INFO messages and checks whether the user
 * has selected to ignore WEAVEINFO messages.
 */
public class UIMessageHandler implements IBuildMessageHandler {

	// --------------- impl on top of IMessageHandler ----------

    /**
     * resources that were affected by the compilation.
     */
    private static final Set<IResource> affectedResources = new HashSet<>();
    /**
     * Markers created in projects other than the one under compilation, which
     * should be cleared next time the compiled project is rebuilt
     */
    private static final Map<String, List<IMarker>> otherProjectMarkers = new HashMap<>();
    /**
     * Indicates whether the most recent build was full or incremental
     */
    private static boolean lastBuildWasFull;
    private final List<Kind> ignoring;
	private final List<ProblemTracker> problems = new ArrayList<>();

	public UIMessageHandler(IProject project) {
        ignoring = new ArrayList<>();

        if (!AspectJPreferences.getBooleanPrefValue(project, AspectJPreferences.OPTION_verbose)) {
            ignore(IMessage.INFO);
        }
        if (!AspectJPreferences.getShowWeaveMessagesOption(project)) {
        	ignore(IMessage.WEAVEINFO);
		}
	}

	public boolean handleMessage(IMessage message) {
        IMessage.Kind kind = message.getKind();
        if (kind == IMessage.ABORT || message.getThrown() != null) {
        	// an exception has been thrown by AspectJ, therefore
        	// want to create an error dialog containing the information
        	// and display it to the user
        	AJDTErrorHandler.handleInternalError(UIMessages.ajErrorDialogTitle,
    				message.getMessage(), message.getThrown());
        	return true;
        }
        if (isIgnoring(kind)) {
            return true;
        }
		if (message.getSourceLocation() == null) {
			AJLog.log(AJLog.COMPILER_MESSAGES, message.getMessage()); //$NON-NLS-1$
			problems.add(new ProblemTracker(message.getMessage(),
					null,message.getKind()));
		} else {
			if (DebugTracing.DEBUG_COMPILER_MESSAGES) {
				// avoid constructing log string if trace is not active
				AJLog.log(AJLog.COMPILER_MESSAGES, "addSourcelineTask message=" //$NON-NLS-1$
						+ message.getMessage() + " file=" //$NON-NLS-1$
						+ message.getSourceLocation().getSourceFile().getPath()
						+ " line=" + message.getSourceLocation().getLine()); //$NON-NLS-1$
			} else {
			    AJLog.log(AJLog.COMPILER_MESSAGES,message.getMessage());
			}
			problems.add(new ProblemTracker(message.getMessage(),
					message.getSourceLocation(),
					message.getKind(),
					message.getDeclared(),
					message.getExtraSourceLocations(),
					message.getID(),
					message.getSourceStart(),
					message.getSourceEnd(),
					message.getThrown()));
		}
		return true;
	}

	public void dontIgnore(Kind kind) {
	    if (null != kind) {
	        ignoring.remove(kind);
	    }
	}

	public boolean isIgnoring(Kind kind) {
		return ((null != kind) && (ignoring.contains(kind)));
	}

	public void ignore(Kind kind) {
	    if ((null != kind) && (!ignoring.contains(kind))) {
	        ignoring.add(kind);
	    }
	}

	// --------------- impl on top of IMessageHandler ----------

    protected void addAffectedResource(IResource res) {
    	affectedResources.add(res);
    }

    /**
     * Inner class used to track problems found during compilation Values of -1
     * are used to indicate no line or column number available.
     */
    static class ProblemTracker {

        public ISourceLocation location;
        public String message;
        public IMessage.Kind kind;
        public boolean declaredErrorOrWarning;
        public List<ISourceLocation> extraLocs;
        public Throwable thrown;

        public int id;
        public int start;
        public int end;

        public ProblemTracker(String m, ISourceLocation l, IMessage.Kind k) {
            this(m, l, k, false, null, -1, -1, -1,null);
        }

        public ProblemTracker(String m, ISourceLocation l, IMessage.Kind k,
                boolean deow, List<ISourceLocation> extraLocs, int id,
				int start, int end, Throwable thrown) {
            location = l;
            message = m;
            kind = k;
            declaredErrorOrWarning = deow;
            this.extraLocs = extraLocs;
            this.id = id;
            this.start = start;
            this.end = end;
            this.thrown = thrown;
        }
    }

    public List<ProblemTracker> getErrors() {
    	List<ProblemTracker> errors = new ArrayList<>();
      for (ProblemTracker prob : problems) {
        if (prob.kind.equals(IMessage.ERROR)) {
          errors.add(prob);
        }
      }
    	return errors;
    }

    /**
     * Callable from anywhere in the plugin, will put any unreported problems
     * onto the task bar. This is currently used by the model builder for build
     * configuration files. Ajde builds the model and reports errors through
     * this class - BuildConfigurationEditor then asks this helper method to
     * report them. We need to move this error reporting stuff out of here if it
     * is going to be used by more than just the compiler.
     */
    public void showOutstandingProblems(IProject project) {
        if (problems.size() > 0 || affectedResources.size() > 0) {
            showMessages(project);
        }
    }

    private void showMessages(final IProject project) {

        // THIS MUST STAY IN A SEPARATE THREAD - This is because we need
        // to create and setup the marker in an atomic operation. See
        // AMC or ASC.
        IWorkspaceRunnable r = monitor -> {

            try {

                Iterator<IResource> affectedResourceIterator = affectedResources
                        .iterator();
                AJLog.log(AJLog.COMPILER,"Types affected during build = "+affectedResources.size()); //$NON-NLS-1$
                IResource ir;
                while (affectedResourceIterator.hasNext()) {
                    ir = affectedResourceIterator.next();
                    try {
                        if (ir.exists()) {
                            ir.deleteMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, false,
                                    IResource.DEPTH_INFINITE);
                            ir.deleteMarkers(IAJModelMarker.AJDT_PROBLEM_MARKER, true,
                                    IResource.DEPTH_INFINITE);
                            ir.deleteMarkers(IMarker.TASK, true,
                                    IResource.DEPTH_INFINITE);
                            // now removed markers from compilation participants
                            HashSet<String> managedMarkers = JavaModelManager.getJavaModelManager().compilationParticipants.managedMarkerTypes();
                            for (String managedMarker : managedMarkers) {
                                ir.deleteMarkers(managedMarker, true, IResource.DEPTH_INFINITE);
                            }
                        }
                    } catch (CoreException re) {
                      AJLog.log("Failed marker deletion: resource=" //$NON-NLS-1$
                                        + ir.getLocation());
                        throw re;
                    }
                }

                Iterator<ProblemTracker> problemIterator = problems.iterator();
                ProblemTracker p;
                while (problemIterator.hasNext()) {
                    p = problemIterator.next();
                  IMarker marker = null;
                    try {
                      if (p.location != null) {
                            ir = locationToResource(p.location, project);
            if ((ir != null) && ir.exists()) {
              // 128803 - only add problems to affected resources
              if (lastBuildWasFull
                  || affectedResources.contains(ir)
                  || ir.getProject() != project) {
                int prio = getTaskPriority(p);
                if (prio != -1) {
                  marker = ir.createMarker(IMarker.TASK);
                  marker.setAttribute(IMarker.PRIORITY, prio);
                } else {
                  if (p.declaredErrorOrWarning) {
                    marker = ir.createMarker(IAJModelMarker.AJDT_PROBLEM_MARKER);
                  } else {
                    // create Java marker with problem id so
                    // that quick fix is available
                    marker = ir.createMarker(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER);
                    marker.setAttribute(IJavaModelMarker.ID,p.id);
                  }
                }
                if ((p.start >= 0) && (p.end >= 0)) {
                    marker.setAttribute(IMarker.CHAR_START, Integer.valueOf(p.start));
                    marker.setAttribute(IMarker.CHAR_END, Integer.valueOf(p.end + 1));
                }
                if (!ir.getProject().equals(project)) {
                  addOtherProjectMarker(project,marker);
                }
                if (p.location.getLine() > 0) {
                  marker.setAttribute(IMarker.LINE_NUMBER,
                    Integer.valueOf(p.location.getLine()));
                }
              } else {
                AJLog.log(AJLog.COMPILER_MESSAGES,
                        "Not adding marker for problem because it's " //$NON-NLS-1$
                            + "against a resource which is not in the list of affected resources" //$NON-NLS-1$
                            + " provided by the compiler. Resource=" + ir + " Problem message=" //$NON-NLS-1$ //$NON-NLS-2$
                            + p.message + " line=" + p.location.getLine()); //$NON-NLS-1$
              }
            }
                        } else {
                            marker = project.createMarker(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER);
                        }
                        if(marker != null) {
                          setSeverity(marker, p.kind);

                          if ((p.extraLocs != null) && (p.extraLocs.size() > 0)) { // multiple
                                          // part
                                          // message
                              int relCount=0;
                            for (ISourceLocation sLoc : p.extraLocs) {
                              String attrData = sLoc.getSourceFile().getAbsolutePath() +
                                                ":::" + //$NON-NLS-1$
                                                sLoc.getLine() +
                                                ":::" + //$NON-NLS-1$
                                                sLoc.getEndLine() +
                                                ":::" + //$NON-NLS-1$
                                                sLoc.getColumn();
                              marker.setAttribute(AspectJUIPlugin.RELATED_LOCATIONS_ATTRIBUTE_PREFIX
                                                  + (relCount++), attrData);
                            }
                          }

                          setMessage(marker, p.message);
                        }
                    } catch (CoreException re) {
                      AJLog.log("Failed marker creation: resource=" //$NON-NLS-1$
                                        + p.location.getSourceFile()
                                                .getPath()
                                        + " line=" //$NON-NLS-1$
                                        + p.location.getLine()
                                        + " message=" + p.message); //$NON-NLS-1$
                        throw re;
                    }
                }
                clearMessages();
            } catch (CoreException e) {
              AJDTErrorHandler.handleAJDTError(
                        UIMessages.CompilerTaskListManager_Error_creating_marker, e);
            }
        };

        try {
            AspectJPlugin.getWorkspace().run(r, null);
        } catch (CoreException cEx) {
        	AJDTErrorHandler.handleAJDTError(
                    UIMessages.CompilerTaskListManager_Error_adding_problem_markers, cEx);
        }
 		 // Part of the fix for bug 89793 - editor image is not updated
        Collection<AspectJEditor> activeEditorList = AspectJEditor.getActiveEditorList();
        synchronized(activeEditorList) {
	        for(AspectJEditor editor : activeEditorList) {
	        	editor.resetTitleImage();
	        }
	    }
    }

    private void clearMessages() {
        affectedResources.clear();
        problems.clear();
    }

    /**
     * Try to map a source location in a project to an IResource
     *
     * @param sloc
     *            the source location
     * @param project
     *            the project to look in first
     * @return the IResource if a match was found, null otherwise
     */
    private IResource locationToResource(ISourceLocation sloc, IProject project) {
        IResource resource;
		File file = sloc.getSourceFile();
		String loc = file.getPath();
		if (!file.exists()) {
			// 167121: might be a binary file in a directory, which uses ! as a separator
			//  - see org.aspectj.weaver.ShadowMunger.getBinaryFile()
			loc = loc.replace('!', File.separatorChar);
		}
        // try this project
		FileURICache fileCache = ((CoreCompilerConfiguration) AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()).getFileCache();

        resource = fileCache.findResource(loc, project);

        if (resource == null) {
            // try any project
            resource = fileCache.findResource(loc);
            if (resource == null) {
                // fix for declare
                // warning/error bug which
                // returns only file name
                // (unqualified)
                resource = tryToFindResource(loc,project);
            }
            // At least warn that you are going to
            // blow up with an event trace ...
            if (resource == null) {
            	AJLog.log(AJLog.COMPILER,"Whilst adding post compilation markers to resources, cannot locate valid eclipse resource for file " //$NON-NLS-1$
                                + loc);
            }
        }

        return resource;
    }

    private IResource tryToFindResource(String fileName, IProject project) {
        IResource ret = null;
        String toFind = fileName.replace('\\', '/');
        IJavaProject jProject = JavaCore.create(project);
        try {
            IClasspathEntry[] classpathEntries = jProject
                    .getResolvedClasspath(false);
          for (IClasspathEntry cpEntry : classpathEntries) {
            if (cpEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
              IPath sourcePath = cpEntry.getPath();
              // remove the first segment because the findMember call
              // following always adds it back in under the covers (doh!)
              // and we end up with two first segments otherwise!
              sourcePath = sourcePath.removeFirstSegments(1);

              IResource memberResource = project.findMember(sourcePath);
              if (memberResource != null) {
                IResource[] srcContainer = new IResource[] { memberResource };
                ret = findFile(srcContainer, toFind);
              }
            }
            else if (cpEntry.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
              IPath projPath = cpEntry.getPath();
              IResource projResource = AspectJPlugin.getWorkspace().getRoot().findMember(projPath);
              if (projResource != null) {
                ret = findFile(new IResource[] { projResource }, toFind);
              }
            }
            if (ret != null) {
              break;
            }
          }
        } catch (JavaModelException jmEx) {
        	AJDTErrorHandler.handleAJDTError(UIMessages.jmCoreException, jmEx);
        }

        if (ret == null)
            ret = project;
        return ret;
    }

    private IResource findFile(IResource[] srcContainer, String name) {
        IResource ret = null;
        try {
          for (IResource ir : srcContainer) {
            if (ir != null) {
              if (ir.getFullPath().toString().endsWith(name)) {
                ret = ir;
                break;
              }
              if (ir instanceof IContainer) {
                ret = findFile(((IContainer) ir).members(), name);
                if (ret != null)
                  break;
              }
            }
          }
        } catch (Exception ignored) {
        }
        return ret;
    }

    /**
     * returns -1 if problem is not a task and the tasks priority otherwise
     * takes case sensitivity into account though this does not seem to
     * supported by the current compiler (AJDT 1.1.10)
     */
    private int getTaskPriority(ProblemTracker p) {
        if (p == null)
            return -1;

        String message = p.message;

        Preferences pref = JavaCore.getPlugin().getPluginPreferences();
        String tags = pref.getString("org.eclipse.jdt.core.compiler.taskTags"); //$NON-NLS-1$
        String caseSens = pref
                .getString("org.eclipse.jdt.core.compiler.taskCaseSensitive"); //$NON-NLS-1$
        String priorities = pref
                .getString("org.eclipse.jdt.core.compiler.taskPriorities"); //$NON-NLS-1$

        boolean caseSensitive;
      //$NON-NLS-1$
      caseSensitive = !caseSens.equals("disabled");

        StringTokenizer tagTokens = new StringTokenizer(tags, ","); //$NON-NLS-1$
        StringTokenizer priorityTokens = new StringTokenizer(priorities, ","); //$NON-NLS-1$
        while (tagTokens.hasMoreTokens()) {
            String prio = priorityTokens.nextToken();
            String token = tagTokens.nextToken();
            if (caseSensitive) {
                if (message.startsWith(token))
                    return getPrioritiyFlag(prio);
            } else {
                if (token.length() <= message.length()) {
                    String temp = message.substring(0, token.length());
                    if (token.compareToIgnoreCase(temp) == 0)
                        return getPrioritiyFlag(prio);
                }
            }

        }
        return -1;
    }

    private int getPrioritiyFlag(String prio) {
        if (prio.equals("NORMAL")) //$NON-NLS-1$
            return IMarker.PRIORITY_NORMAL;
        if (prio.equals("HIGH")) //$NON-NLS-1$
            return IMarker.PRIORITY_HIGH;
        return IMarker.PRIORITY_LOW;
    }

    private void addOtherProjectMarker(IProject p, IMarker m) {
        if (!otherProjectMarkers.containsKey(p.getName())) {
            otherProjectMarkers.put(p.getName(), new ArrayList<>());
        }
        List<IMarker> l = otherProjectMarkers.get(p.getName());
        l.add(m);
    }

    /**
     * Sets the given marker to have hte appropriate severity, according to the
     * kind.
     *
     * @param marker
     *            the marker to set the message for
     * @param kind
     *            used to determine the appropriate severity
     * @throws CoreException
     */
    private void setSeverity(IMarker marker, IMessage.Kind kind)
            throws CoreException {
        if (kind == IMessage.ERROR) {
            marker.setAttribute(IMarker.SEVERITY, Integer.valueOf(IMarker.SEVERITY_ERROR));
        } else if (kind == IMessage.WARNING) {
            marker.setAttribute(IMarker.SEVERITY, Integer.valueOf(IMarker.SEVERITY_WARNING));
        } else {
            marker.setAttribute(IMarker.SEVERITY, Integer.valueOf(IMarker.SEVERITY_INFO));
        }

    }

    private final static int MAX_MESSAGE_LENGTH = (int) Math.pow(2, 16);
    /**
     * Sets the given marker to have the appropriate message.
     *
     * @param marker
     *            the marker to set the message for
     * @param message
     *            the raw message which may require manipulation
     * @param id
     *            the number of this message, which may be an element of a
     *            multipart message
     * @param count
     *            the number of parts to this message (most messages are single
     *            part)
     * @throws CoreException
     */
    private void setMessage(IMarker marker, String message)
            throws CoreException {
        if (message == null) {
            return;
        }
        // FIXME: Remove this horrid hack.
        // Hack the filename off the front and the line number
        // off the end
        if (message.contains("\":") && message.contains(", at line")) { //$NON-NLS-1$ //$NON-NLS-2$
            String hackedMessage = message
                    .substring(message.indexOf("\":") + 2); //$NON-NLS-1$
            message = hackedMessage.substring(0, hackedMessage
                    .indexOf(", at line")); //$NON-NLS-1$
        }
        // bug 318150
        // https://bugs.eclipse.org/bugs/show_bug.cgi?id=318150
        // Can't have more than 2^16 chars in a message
        if (message.length() >= MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH-1);
        }
        marker.setAttribute(IMarker.MESSAGE, message);
    }

    // -------------- other AJDT things -------------------


    protected void setLastBuildType(boolean wasFullBuild) {
    	lastBuildWasFull = wasFullBuild;
    }

    /**
     * clear problems made from a previous compilation stage, but
     * keep any project markers.
     */
    void clearProblems() {
      problems.removeIf(problem -> problem.location != null);
    }

    public static void clearOtherProjectMarkers(IProject p) {
		List<?> l = otherProjectMarkers.get(p.getName());
		if (l != null) {
      for (Object o : l) {
        IMarker m = (IMarker) o;
        try {
          m.delete();
        }
        catch (CoreException ce) {
          // can be ignored
        } // not the end of the world.
      }
			l.clear();
		}
	}
}
