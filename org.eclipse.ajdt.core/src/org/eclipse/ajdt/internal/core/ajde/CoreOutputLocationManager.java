/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
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
package org.eclipse.ajdt.internal.core.ajde;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.aspectj.ajde.core.IOutputLocationManager;
import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.builder.State;
import org.eclipse.jdt.internal.core.builder.StringSet;
import org.eclipse.jdt.internal.core.util.Util;

/**
 * IOutputLocationManager implementation which uses the methods on IJavaProject
 * to work out where the output should be sent.
 *
 * Important note about paths:
 * Use Path.toOSString when describing a file on the filesystem
 * Use Path.toPortableString when describing a resource in Eclipse's workspace.
 */
public class CoreOutputLocationManager implements IOutputLocationManager {

    /**
     *
     * @author Andrew Eisenberg
     * @created Apr 9, 2009
     * use this to ensure that the longest strings are looked at first
     *
     * so, if src and src2 are both source folders, src2 will be
     * examined first
     */
    static class StringLengthComparator implements Comparator<String> {
        public int compare(String s1, String s2) {
            if (s1 == null) {
                if (s2 == null) {
                    return 0;
                }
                return -1;
            }
            if (s2 == null) {
                return 1;
            }
            int len1 = s1.length();
            int len2 = s2.length();
            if (len1 > len2) {  // a larger string should come first
                return -1;
            } else if (len1 == len2) {
                // then compare by text:
                return s1.compareTo(s2);
            } else {
                return 1;
            }
        }

    }

    private static final StringLengthComparator comparator = new StringLengthComparator();

	private final IProject project;
	private final IJavaProject jProject;

	// if there is more than one output directory then the default output
	// location to use is recorded in the 'defaultOutput' field
	private File defaultOutput;

	private final Map<String, File> srcFolderToOutput = new TreeMap<>(comparator);

	private Map<File, IProject> binFolderToProject;

	// maps files in the file system to IFolders in the workspace
	// this keeps track of output locations
    private final Map<String, IContainer> fileSystemPathToIContainer = new TreeMap<>(comparator);

	private final List<File> allOutputFolders = new ArrayList<>();

	// maps file system location to a path within the eclipse workspace
	// needs to take into account linked sources, where the actual
	// file system location may be different from the workspace location
	private Map<String, String> allSourceFolders;

	private boolean outputIsRoot;
	// if there is only one output directory then this is recorded in the
	// 'commonOutputDir' field.
	private File commonOutputDir;
    private final IWorkspaceRoot workspaceRoot;

    // Gather all of the files that are touched by this compilation
    // and use it to determine which files need to have their
    // Relationship maps updated.
    private Set<File> compiledSourceFiles;

    private final FileURICache fileCache;

	public CoreOutputLocationManager(IProject project, FileURICache fileCache) {
		this.project = project;
        this.fileCache = fileCache;
		this.workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		jProject = JavaCore.create(project);
        initSourceFolders();
		if (!isUsingSeparateOutputFolders(jProject)) {
			// using the same output directory therefore record this one
			setCommonOutputDir();
			allOutputFolders.add(commonOutputDir);

			if (commonOutputDir != null) {
    			try {
    			    if (outputIsRoot) {
                        fileSystemPathToIContainer.put(commonOutputDir.getAbsolutePath(),
                                project);
    			    } else {
                        fileSystemPathToIContainer.put(commonOutputDir.getAbsolutePath(),
                                workspaceRoot.getFolder(jProject.getOutputLocation()));
    			    }
                } catch (JavaModelException ignored) {
                }
			}
		} else {
			// need to record all possible output directories
			init();
		}

	}

	public void buildStarting() {
	    compiledSourceFiles = new HashSet<>();
	}

	public void buildComplete() {
	    compiledSourceFiles = null;
	}

	/**
	 * initialize the source folder locations only
	 */
	private void initSourceFolders() {
	    allSourceFolders = new TreeMap<>(comparator);
	    try {
            IClasspathEntry[] cpe = jProject.getRawClasspath();
        for (IClasspathEntry iClasspathEntry : cpe) {
          if (iClasspathEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
            IPath path = iClasspathEntry.getPath();
            IPath rawPath;
            path = path.removeFirstSegments(1).makeRelative();
            if (path.segmentCount() > 0) {
              IFolder folder = project.getFolder(path);
              rawPath = folder.getLocation();
            }
            else {
              rawPath = project.getLocation();
            }
            allSourceFolders.put(rawPath.toOSString(), path.toPortableString());
          }
        }
        } catch (JavaModelException ignored) {
        }
	}

	/**
	 * Calculate all the output locations
	 */
	private void init() {
		outputIsRoot = false;
		String inpathOutFolderString = getInpathOutputFolder();
		boolean isUsingNonDefaultInpathOutfolder = inpathOutFolderString != null;

		try {
			IPath outputLocationPath = jProject.getOutputLocation();
            defaultOutput = workspacePathToFile(outputLocationPath);
			allOutputFolders.add(defaultOutput);


			fileSystemPathToIContainer.put(defaultOutput.getAbsolutePath(),
			        project.getFullPath().equals(outputLocationPath)
			                ? project
			                : workspaceRoot.getFolder(outputLocationPath));


			IClasspathEntry[] cpe = jProject.getRawClasspath();


			// store separate output folders in map
      for (IClasspathEntry classpathEntry : cpe) {
        if (classpathEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
          IPath output = classpathEntry.getOutputLocation();
          if (output != null) {
            IPath path = classpathEntry.getPath();

            String srcFolder = path.removeFirstSegments(1).toPortableString();
            if (path.segmentCount() == 1) { // output folder is project
              srcFolder = path.toPortableString();
            }
            File out = workspacePathToFile(output);
            srcFolderToOutput.put(srcFolder, out);
            if (!allOutputFolders.contains(out)) {
              allOutputFolders.add(out);

              fileSystemPathToIContainer.put(out.getAbsolutePath(),
                workspaceRoot.getFolder(output));
            }
            if (outputIsRoot) {
              // bug 153682: if the project is the source folder
              // then this output folder will always apply
              defaultOutput = out;
            }
          }
        }
      }

            // check to see if on inpath and need a special out folder for it.
            if (isUsingNonDefaultInpathOutfolder) {
                // first add the inpath out folder to the list of out folders
                IPath inpathOutFolderPath = new Path(inpathOutFolderString);
                IFolder inpathOutFolder = workspaceRoot.getFolder(inpathOutFolderPath);
                File out = inpathOutFolder.getLocation().toFile();
                fileSystemPathToIContainer.put(out.getAbsolutePath(),
                        workspaceRoot.getFolder(inpathOutFolderPath));

                // now map everything coming from the in path to this out folder
              for (IClasspathEntry iClasspathEntry : cpe) {
                if (AspectJCorePreferences.isOnInpath(iClasspathEntry)) {
                  // now must resolve the entry so that all jars contained in it
                  // are mapped.
                  List<IClasspathEntry> containerEntries = AspectJCorePreferences.resolveClasspath(iClasspathEntry, project);
                  for (IClasspathEntry containerEntry : containerEntries) {
                    IPath path = containerEntry.getPath();
                    File f = workspacePathToFile(path);
                    if (f != null && f.exists()) {
                      // use full path
                      String srcFolder = new Path(f.getPath()).toPortableString();
                      srcFolderToOutput.put(srcFolder, out);
                    }
                    else {
                      // outfolder does not exist
                      // probably because Project has been renamed
                      // and inpath output location has not been updated.
                      // this is handled with a message to the user
                    }
                  }
                }
              }
			}
		} catch (JavaModelException ignored) {
		}
	}

	private boolean isComputingXmlFile() {
		StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
		int max = stacktrace.length;
		if (max > 6) max = 6;
		for (int i=(max-1);i>0;i--) {
			String s = stacktrace[i].toString();
			// org.aspectj.ajdt.internal.core.builder.AjBuildManager.writeOutxmlFile(AjBuildManager.java:659) probably at i==4
			if (s.startsWith("org.aspectj.ajdt.internal.core.builder.AjBuildManager.writeOutxmlFile")) {
				return true;
			}
		}
		return false;
	}

	public File getOutputLocationForClass(File compilationUnit) {
	    // remember that this file has been asked for
	    // presumably it is being recompiled
	    if (Util.isJavaLikeFileName(compilationUnit.getName()) && !isComputingXmlFile()) {
	    	if (compiledSourceFiles == null) {
	    		// pr540403
	    		AJLog.log(AJLog.BUILDER,"Unexpectedly buildStarted() has not been called yet, unable to record: "+compilationUnit.getName());
	    	} else {
	    		compiledSourceFiles.add(compilationUnit);
	    	}
	    }

		return getOutputLocationForResource(compilationUnit);
	}

	public File getOutputLocationForResource(File resource) {
		if (!isUsingSeparateOutputFolders(jProject)) {
			return commonOutputDir;
		}
		if (resource == null || resource.toString() == null) {
			return defaultOutput;
		}

		// due to linked files, there may be multiple IResource relating to a single File
		IResource[] resources;
		IFile[] files = fileCache.findFilesForURI(resource.toURI());
		if (files != null && files.length > 0) {
		    resources = new IResource[files.length];
      System.arraycopy(files, 0, resources, 0, files.length);
		} else {
	        IContainer[] containers = fileCache.findContainersForURI(resource.toURI());
	        if (containers != null && containers.length > 0) {
	            resources = new IResource[containers.length];
            System.arraycopy(containers, 0, resources, 0, containers.length);
	        } else {
	            resources = null;
	        }
		}

		String pathStr = null;
		if (resources != null && resources.length > 0) {
		    // check if there is another version of the file
		    // that is linked to this project
		    // since the IResources are returned in an
		    // arbitrary order, doesn't matter which one we choose
		    IProject project = jProject.getProject();
      for (IResource iResource : resources) {
        if (iResource.getProject().equals(project)) {
          pathStr = iResource.getFullPath().removeFirstSegments(1).toPortableString();
          break;
        }
      }
		    if (pathStr == null) {
		        // this is from a location outside of the current project,
		        // use full path
		        pathStr = resources[0].getLocation().toPortableString();
		    }
		}

		if (pathStr == null) {
		    // if still null at this point, then assume that we have a fully qualified path
		    // coming from the inpath going to the inpath out folder
		    pathStr = new Path(resource.getPath()).toPortableString();
		}

		for (String src : srcFolderToOutput.keySet()) {
            if (pathStr.startsWith(src)) {
              return srcFolderToOutput.get(src);
            }
	    }

		// couldn't find anything
		return defaultOutput;
	}

	/**
	 * @return true if there is more than one output directory being used by
	 *         this project and false otherwise
	 */
	private boolean isUsingSeparateOutputFolders(IJavaProject jp) {
		if (getInpathOutputFolder() != null) {
		    return true;
		}
	    try {
			IClasspathEntry[] cpe = jp.getRawClasspath();
        for (IClasspathEntry iClasspathEntry : cpe) {
          if (iClasspathEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
            if (iClasspathEntry.getOutputLocation() != null) {
              return true;
            }
          }
        }
		} catch (JavaModelException ignored) {
		}
		return false;
	}

	/**
	 * Record the 'common output directory', namely the one where all the output
	 * goes
	 */
	private void setCommonOutputDir() {
		IJavaProject jProject = JavaCore.create(project);
		IPath workspaceRelativeOutputPath;
		try {
			workspaceRelativeOutputPath = jProject.getOutputLocation();
		} catch (JavaModelException e) {
			commonOutputDir = project.getLocation().toFile();
            outputIsRoot = true;
			return;
		}
		if (workspaceRelativeOutputPath.segmentCount() == 1) {
			commonOutputDir = jProject.getResource().getLocation().toFile();
			outputIsRoot = true;
			return;
		}
		IFolder out = ResourcesPlugin.getWorkspace().getRoot().getFolder(workspaceRelativeOutputPath);
		commonOutputDir = out.getLocation().toFile();
	}

	private File workspacePathToFile(IPath path) {
		if (path.segmentCount() == 1) {
			// bug 153682: getFolder fails when the path is a project
			IResource res = workspaceRoot.findMember(path);
			outputIsRoot = true;
			return res.getLocation().toFile();
		}

		IPath outPath;
		IResource out;
		String fileExtension = path.getFileExtension();
        if (fileExtension != null) {
            // assume a file
		    out = workspaceRoot.getFile(path);
		} else {
		    out = workspaceRoot.getFolder(path);
		}
		if (out.getProject().exists()) {
		    outPath = out.getLocation();
		} else {
		    // maybe this is a fully qualified path
		    outPath = path;
		}

		if (outPath != null) {
			return outPath.toFile();
		} else {
			return null;
		}
	}


	/**
	 * return all output directories used by this project
	 */
	public List<File> getAllOutputLocations() {
		return allOutputFolders;
	}

	public String getInpathOutputFolder() {
    // assume that the folder is valid...
		// null means that the default out folder is used
		return AspectJCorePreferences.getProjectInpathOutFolder(project);
	}


	/**
     * Called when build is completed.  Respond with all source files compiled
     * for this build
     * @return all source files compiled for this build
     */
    public File[] getCompiledSourceFiles() {
        return compiledSourceFiles == null ? new File[0] : compiledSourceFiles.toArray(new File[0]);
    }


	/**
	 * If there's only one output directory return this one, otherwise return
	 * the one marked as default
	 */
	public File getDefaultOutputLocation() {
		if (!isUsingSeparateOutputFolders(jProject)) {
			return commonOutputDir;
		} else {
			return defaultOutput;
		}
	}

	public String getSourceFolderForFile(File sourceFile) {
		String sourceFilePath = sourceFile.getAbsolutePath();
		for (Entry<String, String> sourceFolderMapping : allSourceFolders.entrySet()) {
			if (sourceFilePath.startsWith(sourceFolderMapping.getKey())) {
				return sourceFolderMapping.getValue();
			}
		}

		// might be a linked folder in a source folder
		IFile[] files = fileCache.findFilesForURI(sourceFile.toURI());
		try {
            IPackageFragmentRoot[] roots = jProject.getPackageFragmentRoots();
            for (IPackageFragmentRoot root : roots) {
                if (!root.isReadOnly()) {
                    IContainer container = (IContainer) root.getResource();
            	    for (IFile file : files) {
                        if (container.getFullPath().isPrefixOf(file.getFullPath())) {
                            return allSourceFolders.get(container.getLocation().toOSString());
                        }
                    }
                }
            }
        } catch (JavaModelException ignored) {
        }


		return null;
	}

	public void reportFileRemove(String outFileStr, int fileType) {
	    for (Entry<String, IContainer> entry : fileSystemPathToIContainer.entrySet()) {
            String outFolderStr = entry.getKey();
            if (outFileStr.startsWith(outFolderStr)) {
                IContainer outFolder = entry.getValue();
                IFile outFile = outFolder.getFile(new Path(outFileStr.substring(outFolderStr.length())));
                try {
                    outFile.refreshLocal(IResource.DEPTH_ZERO, null);
                    return;
                } catch (CoreException ignored) {
                }
            }
        }

	}


	public Map<File,String> getInpathMap() {
		return Collections.emptyMap();
	}


	/**
	 * This method must do two things.  First, it performs a refreshLocal on
	 * the newly changed file.  Then it marks the file and all of its parent
	 * folders as derived (up to, but not including the output folder itself).
	 * Folders are marked as derived only if the output folder is not the same
	 * as the source folder.
	 */
	public void reportFileWrite(String outFileStr, int fileType) {
	    try {
	        outer:
	        for (Entry<String, IContainer> entry : fileSystemPathToIContainer.entrySet()) {
	            String outFolderStr = entry.getKey();
	            if (outFileStr.startsWith(outFolderStr)) {
	                IContainer outFolder = entry.getValue();
	                IFile outFile = outFolder.getFile(new Path(outFileStr.substring(outFolderStr.length())));

	                outFile.refreshLocal(IResource.DEPTH_ZERO, null);

	                if (outFile.exists()) {

	                    // if this is a resource whose source folder and out folder are the same,
	                    // do not mark as derived
	                    boolean outputIsSourceFolder = isOutFolderASourceFolder(outFolder);
	                    if (! isResourceInSourceFolder(outFile, outputIsSourceFolder)) {
	                        outFile.setDerived(true, null);
	                    }

	                    // only do this if output is not a source folder
	                    if (!outputIsSourceFolder) {
	                        IContainer parent = outFile.getParent();
	                        inner:
	                        while (!parent.equals(outFolder) ) {
	                            if (!parent.isDerived()) {
    	                            parent.setDerived(true, null);
	                            } else {
	                                // no need to continnue
	                                // assume that all folders are derived all the way up
	                                break;
	                            }
	                            parent = parent.getParent();
	                            if (parent == null) {
	                                // shouldn't happen
	                                break;
	                            }
	                        }
	                    }
	                    break;
	                }
	            }

	        }
	    } catch (CoreException ignored) {
	    }
	}

    private boolean isResourceInSourceFolder(IFile outFile,
            boolean outputIsSourceFolder) {
        return !(outFile.getFileExtension() != null && outFile.getFileExtension().equals("class"))
                && outputIsSourceFolder;
    }

    private boolean isOutFolderASourceFolder(IContainer outFolder) {
        return outputIsRoot || srcFolderToOutput.containsKey(outFolder.getFullPath().removeFirstSegments(1).makeRelative().toPortableString());
    }

	/**
	 * Return the Java project that has outputFolder as an output location, or null if it is
	 * not recognized.
	 *
	 * This method can return null if outputFolder is not found
	 * in any declaring project
	 *
	 */
	protected IProject findDeclaringProject(File outputFolder) {
	    if (binFolderToProject == null) {
	        initDeclaringProjectsMap();
	    }
	    return binFolderToProject.get(outputFolder);
	}


	/**
	 * the field binFolderToProject must be refreshed before each build
	 * because we are not sure if any bin folders in downstream projects
	 * have changed.
	 *
	 * See bug 270335
	 */
	protected void zapBinFolderToProjectMap() {
	    binFolderToProject = null;
	}

	/**
	 * Initialize the binFolderToProject map so that the map contains
	 * java.io.File -> IProject  where the file is an output location
	 * and the project is where this output location is defined
	 */
	private void initDeclaringProjectsMap() {

	    AJLog.logStart("OutputLocationManager: binary folder to declaring project map creation: " + project);
	    binFolderToProject = new HashMap<>();
    try {
            mapProject(jProject);
        } catch (JavaModelException ignored) {
        }
        AJLog.logEnd(AJLog.BUILDER_CLASSPATH, "OutputLocationManager: binary folder to declaring project map creation: " + project);
    }

    private void mapProject(IJavaProject jp) throws JavaModelException {
        IClasspathEntry[] cpes = jp.getRawClasspath();
      for (IClasspathEntry cpe : cpes) {
        if (cpe.isExported() ||
            cpe.getEntryKind() == IClasspathEntry.CPE_SOURCE ||
            jp == jProject)
        {
          handleClassPathEntry(jp, cpe);
        }
      }
    }

    private void handleClassPathEntry(IJavaProject jp, IClasspathEntry cpe) throws JavaModelException {
        switch (cpe.getEntryKind()) {
            case IClasspathEntry.CPE_CONTAINER:
                IClasspathContainer container =
                    JavaCore.getClasspathContainer(cpe.getPath(), jp);
                if (container != null && container.getKind() != IClasspathContainer.K_DEFAULT_SYSTEM) {
                    IClasspathEntry[] cpes = container.getClasspathEntries();
                  for (IClasspathEntry iClasspathEntry : cpes) {
                    handleClassPathEntry(jp, iClasspathEntry);
                  }
                }
                break;
            case IClasspathEntry.CPE_LIBRARY:
                File libFile = pathToFile(cpe.getPath());
                if (libFile.isDirectory()) {  // ignore jar files
                    if (libFile != null && !binFolderToProject.containsKey(libFile)) {
                        binFolderToProject.put(libFile, jp.getProject());
                    }
                }
                break;
            case IClasspathEntry.CPE_PROJECT:
                IJavaProject jpClasspath = pathToProject(cpe.getPath());
                if (jpClasspath != null) {
                    mapProject(jpClasspath);
                }
                break;

            case IClasspathEntry.CPE_SOURCE:
                File outFile = pathToFile(cpe.getOutputLocation() == null ? jp.getOutputLocation() : cpe.getOutputLocation());
                if (outFile != null && ! binFolderToProject.containsKey(outFile)) {
                    binFolderToProject.put(outFile, jp.getProject());
                }
                break;
            case IClasspathEntry.CPE_VARIABLE:
                IClasspathEntry cpeResolved = JavaCore.getResolvedClasspathEntry(cpe);
                if (cpeResolved != null) {
                    handleClassPathEntry(jp, cpeResolved);
                }
                break;
        }
    }

    private IJavaProject pathToProject(IPath path) {
        if (path != null && path.segmentCount() > 0) {
            IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(path.segments()[0]);
            return JavaCore.create(p);
        } else {
            return null;
        }
    }

    private File pathToFile(IPath path) {
        // Bug 279497 if path is only one segment, then assume it is a project

        IPath locPath = path.segmentCount() > 1 ?
                ResourcesPlugin.getWorkspace().getRoot().getFolder(path).getLocation() :
                ResourcesPlugin.getWorkspace().getRoot().getProject(path.makeRelative().toOSString()).getLocation();
        File f;
        if (locPath != null) {
            f = locPath.toFile();
        } else {
            f = path.toFile();
        }
        return f;
    }

    /**
	 * Aim of this callback from the compiler is to ask Eclipse if it knows which project has the
	 * supplied directory as an output folder, and if that can be determined then look at the
	 * last structural build time of that project and any structurally changed types since that
	 * build time.  If it doesn't look like anything has changed since the supplied buildtime then
	 * we assume that means nothing changed in the directory and so do not need to check the time
	 * stamp of each file within it.
	 *
	 * This method does nothing more than a rudimentary analysis - if there are changes then it does
	 * not currently attempt to determine if they are interesting (ie. whether they are changes to
	 * types that the compiler asking the question depends upon).
	 */
	public int discoverChangesSince(File dir, long buildtime) {
		IProject project = findDeclaringProject(dir);
		try {
			if (project!=null) {
	            Object s = JavaModelManager.getJavaModelManager().getLastBuiltState(project, null);
	            if (s != null && s instanceof State) {
	                State state = (State) s;
	                long dependeeTime = getLastStructuralBuildTime(state);
	                if (dependeeTime < buildtime) {
	                	StringSet changes = getStructurallyChangedTypes(state);
	                	// this test isn't quite right... but it basically works
	                    if (changes==null || changes.elementSize==0) {
	                    	return 1; // no changes at all (doesnt determine whether they are of interest)
	                    }
	                }
	            }
			}
		} catch (Exception ignored) {
		}
		return 0; // DONTKNOW - this will cause the caller to do the .class modtime tests
	}


    private static long getLastStructuralBuildTime(State state)
            throws Exception {
        if (lastStructuralBuildTimeField == null) {
            lastStructuralBuildTimeField = State.class.getDeclaredField("lastStructuralBuildTime");
            lastStructuralBuildTimeField.setAccessible(true);
        }
        return lastStructuralBuildTimeField.getLong(state);
    }

    private static StringSet getStructurallyChangedTypes(State state)
            throws Exception {
        if (structurallyChangedTypesField == null) {
            structurallyChangedTypesField = State.class.getDeclaredField("structurallyChangedTypes");
            structurallyChangedTypesField.setAccessible(true);
        }
        return (StringSet)structurallyChangedTypesField.get(state);
    }

	// Cached for performance reasons
	private static java.lang.reflect.Field lastStructuralBuildTimeField = null;
	private static java.lang.reflect.Field structurallyChangedTypesField = null;

}
