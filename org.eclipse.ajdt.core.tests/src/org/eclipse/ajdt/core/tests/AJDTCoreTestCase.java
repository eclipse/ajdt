/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman  - initial version
 *******************************************************************************/
package org.eclipse.ajdt.core.tests;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.CoreUtils;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.ajdt.core.tests.testutils.DefaultLogger;
import org.eclipse.ajdt.core.tests.testutils.Utils;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

/**
 * Mainly copied from AbstractJavaModelTests in org.eclipse.jdt.core.tests.model
 */
public class AJDTCoreTestCase extends TestCase {

    public static final String DEFAULT_PROJECT_NAME = "DefaultEmptyProject";
    DefaultLogger defaultLogger = new DefaultLogger();
    {
        try {
            AspectJPlugin.getDefault().setAJLogger(defaultLogger);
        } catch (Exception e) {
            // do nothing, plugin is probably not needed for this test
        }
    }

    public AJDTCoreTestCase(String name) {
        super(name);
    }

    public AJDTCoreTestCase() {
        super();
    }


    protected void setUp() throws Exception {
        super.setUp();
        System.out.println("------------------------\nStarting " + this.getName());
    }


    protected void tearDown() throws Exception {
        super.tearDown();
        cleanWorkspace(false);
    }

    protected void cleanWorkspace(boolean complete) throws CoreException {
        try {
            // don't autobuild while we are deleting the projects
            Utils.setAutobuilding(false);
            getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, null);
            IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
          for (IProject project : allProjects) {
            // keep the default project around on non-complete cleans
            if (!complete && project.getName().equals(DEFAULT_PROJECT_NAME)) {
              IJavaProject defaultProject = JavaCore.create(project);
              defaultProject.setOptions(null);
              project.getFile(".classpath").delete(true, null);
              project.getFile(".classpath.COPY").copy(new Path(".classpath"), true, null);
              project.getFolder("src").delete(true, null);
              project.getFolder("bin").delete(true, null);
              createDefaultSourceFolder(defaultProject);
            }
            else {
              deleteProject(project, false);
            }
          }
            allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
          for (IProject project : allProjects) {
            // keep the default project around on non-complete cleans
            if (complete || !project.getName().equals(DEFAULT_PROJECT_NAME)) {
              deleteProject(project, true);
            }
          }
        } finally {
            // ensure we use default logger for next test
            AspectJPlugin.getDefault().setAJLogger(defaultLogger);

            Utils.setAutobuilding(true);
            try {
                getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, null);
            } catch (CoreException e) {
                AspectJCoreTestPlugin.log(e);
            }
            AJCompilationUnitManager.INSTANCE.clearCache();
        }
    }


    protected void setJava7SourceLevel(IJavaProject javaProject) {
        javaProject.setOption(CompilerOptions.OPTION_Compliance, "1.7");
        javaProject.setOption(CompilerOptions.OPTION_Source, "1.7");
    }

    /**
     * Returns the OS path to the directory that contains this plugin.
     */
    protected String getPluginDirectoryPath() {
        try {
            URL platformURL = Platform.getBundle(getTestBundleName()).getEntry("/"); //$NON-NLS-1$ //$NON-NLS-2$
            return new File(FileLocator.toFileURL(platformURL).getFile()).getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected String getTestBundleName() {
        return "org.eclipse.ajdt.core.tests";
    }

    public String getSourceWorkspacePath() {
        return getPluginDirectoryPath() +  java.io.File.separator + "workspace"; //$NON-NLS-1$
    }

    /**
     * Returns the IWorkspace this test suite is running on.
     */
    public IWorkspace getWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    public IWorkspaceRoot getWorkspaceRoot() {
        return getWorkspace().getRoot();
    }

    protected IProject createPredefinedProject14(final String projectName) throws CoreException,IOException {
        IJavaProject jp = setUpJavaProject(projectName);
        jp.setOption("org.eclipse.jdt.core.compiler.problem.missingSerialVersion", "ignore"); //$NON-NLS-1$ //$NON-NLS-2$
        jp.setOption("org.eclipse.jdt.core.compiler.source", "1.4");  //$NON-NLS-1$//$NON-NLS-2$
        jp.setOption("org.eclipse.jdt.core.compiler.target", "1.4"); //$NON-NLS-1$ //$NON-NLS-2$
        jp.getProject().build(IncrementalProjectBuilder.FULL_BUILD,null);
        return jp.getProject();
    }

    protected IProject createPredefinedProject(final String projectName) throws CoreException, RuntimeException {
        IJavaProject jp;
        try {
            jp = setUpJavaProject(projectName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (jp == null) {
            // project was not found
            return null;
        }

        // New in Eclipse 4.6 - substring proposals. With this turned on we will get
        // extra matches that disturb the tests
        jp.setOption(JavaCore.CODEASSIST_SUBSTRING_MATCH, "disabled");

        try {
            jp.setOption("org.eclipse.jdt.core.compiler.problem.missingSerialVersion", "ignore"); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (NullPointerException ignored) {
        }
        // if not autobuilding, then test is completely in charge of building
        if (isAutobuilding()) {
            jp.getProject().build(IncrementalProjectBuilder.FULL_BUILD,null);
        }
        return jp.getProject();
    }

    /**
     * Create a named project, optionally turn of some irritating options that can clog up the output and then build it
     */
    protected IProject createPredefinedProject(final String projectName,boolean turnOffIrritatingOptions) throws CoreException, IOException {
        IJavaProject jp = setUpJavaProject(projectName);
        if (turnOffIrritatingOptions) {
            jp.setOption("org.eclipse.jdt.core.compiler.problem.missingSerialVersion", "ignore");//$NON-NLS-1$ //$NON-NLS-2$ // $NON-NLS-2$
            jp.setOption("org.eclipse.jdt.core.compiler.problem.rawTypeReference","ignore");//$NON-NLS-1$ //$NON-NLS-2$ // $NON-NLS-2$
            jp.setOption("org.eclipse.jdt.core.compiler.taskTags","");//$NON-NLS-1$ //$NON-NLS-2$ // $NON-NLS-2$
        }
        jp.getProject().build(IncrementalProjectBuilder.FULL_BUILD,null);
        return jp.getProject();
    }


    protected IJavaProject setUpJavaProject(final String projectName) throws CoreException, IOException {
        return setUpJavaProject(projectName, "1.4"); //$NON-NLS-1$
    }

    protected IJavaProject setUpJavaProject(final String projectName, String compliance) throws CoreException, IOException {
        // copy files in project from source workspace to target workspace
        String sourceWorkspacePath = getSourceWorkspacePath();
        String targetWorkspacePath = getWorkspaceRoot().getLocation().toFile().getCanonicalPath();

        // return null if source directory does not exist
        if (! copyDirectory(new File(sourceWorkspacePath, projectName), new File(targetWorkspacePath, projectName))) {
            return null;
        }

        // create project
        final IProject project = getWorkspaceRoot().getProject(projectName);
        if (! project.exists()) {
            IWorkspaceRunnable populate = monitor -> project.create(null);
            getWorkspace().run(populate, null);
        }
        // ensure open
        project.open(null);
        AJCompilationUnitManager.INSTANCE.initCompilationUnits(project);

      return JavaCore.create(project);
    }

    protected static class Requestor extends TypeNameRequestor { }


    protected void waitForIndexes() {
        joinBackgroudActivities();
        Job[] jobs = Job.getJobManager().find(null);
      for (Job job : jobs) {
        if (job.getName().startsWith("Java indexing")) {
          boolean wasInterrupted = true;
          while (wasInterrupted) {
            try {
              wasInterrupted = false;
              job.join();
            }
            catch (InterruptedException e) {
              wasInterrupted = true;
            }
          }
        }
      }
    }

    public static void waitForAutoBuild() {
        waitForJobFamily(ResourcesPlugin.FAMILY_AUTO_BUILD);
    }
    public static void waitForManualBuild() {
        waitForJobFamily(ResourcesPlugin.FAMILY_MANUAL_BUILD);
    }
    public static void waitForAutoRefresh() {
        waitForJobFamily(ResourcesPlugin.FAMILY_AUTO_REFRESH);
    }
    public static void waitForManualRefresh() {
        waitForJobFamily(ResourcesPlugin.FAMILY_MANUAL_REFRESH);
    }

    public static void waitForJobFamily(Object family) {
        boolean wasInterrupted = false;
        do {
            try {
                Job.getJobManager().join(family, new NullProgressMonitor());
                wasInterrupted = false;
            } catch (OperationCanceledException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                wasInterrupted = true;
            }
        } while (wasInterrupted);

    }

    private static final long TWO_MINUTES = 1000 * 60 * 2;
       public static void joinBackgroudActivities()  {
            waitForAutoBuild();
            waitForManualBuild();
            waitForAutoRefresh();
            waitForManualRefresh();

        long startTime = System.currentTimeMillis();
        long endTime = startTime + TWO_MINUTES;
        while (! allJobsQuiet()) {
            waitForJobs();
            if (System.currentTimeMillis() > endTime) {
                fail("Waited too long for jobs to finish.  All jobs:\n" + printJobs());
            }
        }
    }

    public static String printJobs() {
        IJobManager jobManager= Job.getJobManager();
        Job[] jobs= jobManager.find(null);
        StringBuilder sb = new StringBuilder();
        sb.append("------------------------");
        sb.append("Printing jobs");
      for (Job job : jobs) {
        sb.append(job);
      }
        sb.append("------------------------");
        return sb.toString();
    }


    private static boolean allJobsQuiet() {
        IJobManager jobManager= Job.getJobManager();
        Job[] jobs= jobManager.find(null);
      for (Job job : jobs) {
        int state = job.getState();
        if ((job.getName().startsWith("Java indexing") ||
             job.getName().startsWith("Searching for markers")) &&
            (state == Job.RUNNING || state == Job.WAITING))
        {
          return false;
        }
//            int state= job.getState();
//            //ignore jobs we don't care about
//            if (!job.getName().equals("Flush Cache Job") &&  //$NON-NLS-1$
//                    !job.getName().equals("Usage Data Event consumer") &&  //$NON-NLS-1$
//                    (state == Job.RUNNING || state == Job.WAITING)) {
//                return false;
//            }
      }
        return true;
    }


    private static void waitForJobs() {
        IJobManager jobManager= Job.getJobManager();
        Job[] jobs= jobManager.find(null);
      for (Job job : jobs) {
        int state = job.getState();
        if ((job.getName().startsWith("Java indexing") ||
             job.getName().startsWith("Searching for markers")) &&
            (state == Job.RUNNING || state == Job.WAITING))
        {
          try {
            job.join();
          }
          catch (InterruptedException ignored) {
          }

        }

//            //ignore jobs we don't care about
//            if (!job.getName().equals("Flush Cache Job") &&  //$NON-NLS-1$
//                    !job.getName().equals("Usage Data Event consumer") &&
//                    !job.getName().equals("Animation start" ) &&  //$NON-NLS-1$
//                    (state == Job.RUNNING || state == Job.WAITING)) {
//                try {
//                    job.join();
//                } catch (InterruptedException e) {
//                }
//            }
      }
    }


    /**
     * Copy the given source directory (and all its contents) to the given target directory.
     */
    protected boolean copyDirectory(File source, File target) throws IOException {
        if (! source.exists()) {
            return false;
        }
        if (!target.exists()) {
            target.mkdirs();
        }
        File[] files = source.listFiles();
        if (files == null) return true;
      for (File sourceChild : files) {
        String name = sourceChild.getName();
        if (name.equals("CVS"))
          continue; //$NON-NLS-1$
        File targetChild = new File(target, name);
        if (sourceChild.isDirectory()) {
          copyDirectory(sourceChild, targetChild);
        }
        else {
          copy(sourceChild, targetChild);
        }
      }
        return true;
    }

    /**
     * Copy file from src (path to the original file) to dest (path to the destination file).
     */
    public void copy(File src, File dest) throws IOException {
        // read source bytes
        byte[] srcBytes = this.read(src);

        if (convertToIndependantLineDelimiter(src)) {
            String contents = new String(srcBytes);
            contents = convertToIndependantLineDelimiter(contents);
            srcBytes = contents.getBytes();
        }

        // write bytes to dest
        FileOutputStream out = new FileOutputStream(dest);
        out.write(srcBytes);
        out.close();
    }

    public byte[] read(java.io.File file) throws java.io.IOException {
        int fileLength;
        byte[] fileBytes = new byte[fileLength = (int) file.length()];
        java.io.FileInputStream stream = new java.io.FileInputStream(file);
        int bytesRead = 0;
        int lastReadSize = 0;
        while ((lastReadSize != -1) && (bytesRead != fileLength)) {
            lastReadSize = stream.read(fileBytes, bytesRead, fileLength - bytesRead);
            bytesRead += lastReadSize;
        }
        stream.close();
        return fileBytes;
    }
    public boolean convertToIndependantLineDelimiter(File file) {
        return CoreUtils.ASPECTJ_SOURCE_FILTER.accept(file.getName());
    }

    /**
     * Force indexes to be populated
     */
    public static void performDummySearch(IJavaElement element) throws CoreException {
        new SearchEngine().searchAllTypeNames(
            null,
            SearchPattern.R_EXACT_MATCH,
            "XXXXXXXXX".toCharArray(), // make sure we search a concrete name. This is faster according to Kent
            SearchPattern.R_EXACT_MATCH,
            IJavaSearchConstants.CLASS,
            SearchEngine.createJavaSearchScope(new IJavaElement[]{element}),
            new Requestor(),
            IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
            null);
    }


    public static String convertToIndependantLineDelimiter(String source) {
        if (source.indexOf('\n') == -1 && source.indexOf('\r') == -1) return source;
        StringBuilder buffer = new StringBuilder();
        for (int i = 0, length = source.length(); i < length; i++) {
            char car = source.charAt(i);
            if (car == '\r') {
                buffer.append('\n');
                if (i < length-1 && source.charAt(i+1) == '\n') {
                    i++; // skip \n after \r
                }
            } else {
                buffer.append(car);
            }
        }
        return buffer.toString();
    }

    protected IProject getProject(String project) {
        return getWorkspaceRoot().getProject(project);
    }

    protected void deleteProject(IProject project) throws CoreException {
        deleteProject(project,true);
    }
    protected void deleteProject(IProject project, boolean force) throws CoreException {
        if (project.exists() && !project.isOpen()) { // force opening so that project can be deleted without logging (see bug 23629)
            project.open(null);
        }
        deleteResource(project,force);
    }

    protected void deleteProject(String projectName) throws CoreException {
        deleteProject(this.getProject(projectName),true);
    }

    /**
     * Delete this resource.
     */
    public void deleteResource(IResource resource, boolean force) throws CoreException {
        waitForManualBuild();
        waitForAutoBuild();
        CoreException lastException = null;
        try {
//          resource.refreshLocal(IResource.DEPTH_INFINITE, null);
            resource.delete(false, null);
        } catch (CoreException e) {
            lastException = e;
            // just print for info
            System.out.println("(CoreException): " + e.getMessage() + " Resource " + resource.getFullPath()); //$NON-NLS-1$ //$NON-NLS-2$
            e.printStackTrace();
        } catch (IllegalArgumentException iae) {
            // just print for info
            System.out.println("(IllegalArgumentException): " + iae.getMessage() + ", resource " + resource.getFullPath()); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (!force) {
            return;
        }
        int retryCount = 10; // wait 1 minute at most
        while (resource.isAccessible() && --retryCount >= 0) {
            waitForAutoBuild();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            try {
                resource.delete(true, null);
            } catch (CoreException e) {
                lastException = e;
                // just print for info
                System.out.println("(CoreException) Retry "+retryCount+": "+ e.getMessage() + ", resource " + resource.getFullPath()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            } catch (IllegalArgumentException iae) {
                // just print for info
                System.out.println("(IllegalArgumentException) Retry "+retryCount+": "+ iae.getMessage() + ", resource " + resource.getFullPath()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
        }
        if (!resource.isAccessible()) return;
        System.err.println("Failed to delete " + resource.getFullPath()); //$NON-NLS-1$
        if (lastException != null) {
            throw lastException;
        }
    }

    private void ensureExists(IFolder folder) throws CoreException {
        if (folder.getParent().getType() == IResource.FOLDER && !folder.getParent().exists()) {
            ensureExists((IFolder) folder.getParent());
        }
        folder.create(false, true, null);
    }


    private IPackageFragmentRoot createDefaultSourceFolder(IJavaProject javaProject) throws CoreException {
        IProject project = javaProject.getProject();
        IFolder folder = project.getFolder("src");
        if (!folder.exists())
            ensureExists(folder);

        // if already exists, do nothing
        final IClasspathEntry[] entries = javaProject
                .getResolvedClasspath(false);
        final IPackageFragmentRoot root = javaProject
                .getPackageFragmentRoot(folder);
      for (final IClasspathEntry entry : entries) {
        if (entry.getPath().equals(folder.getFullPath())) {
          return root;
        }
      }


        // else, remove old source folders and add this new one
        IClasspathEntry[] oldEntries = javaProject.getRawClasspath();
        List<IClasspathEntry> oldEntriesList = new ArrayList<>();
        oldEntriesList.add(JavaCore.newSourceEntry(root.getPath()));
        for (IClasspathEntry entry : oldEntries) {
            if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                oldEntriesList.add(entry);
            }
        }

        IClasspathEntry[] newEntries = oldEntriesList.toArray(new IClasspathEntry[0]);
        javaProject.setRawClasspath(newEntries, null);
        return root;
    }

    protected ICompilationUnit[] createUnits(String[] packages, String[] cuNames, String[] cuContents, IJavaProject project) throws CoreException {

        boolean oldAutoBuilding = isAutobuilding();
        setAutobuilding(false);

        try {
            ICompilationUnit[] units = new ICompilationUnit[cuNames.length];
            for (int i = 0; i < units.length; i++) {
                units[i] = createCompilationUnitAndPackage(packages[i], cuNames[i], cuContents[i], project);
            }
            project.getProject().build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
            waitForManualBuild();
            waitForAutoBuild();
            assertNoProblems(project.getProject());
            return units;
        } finally {
            setAutobuilding(oldAutoBuilding);
        }
    }

    protected ICompilationUnit createUnit(String pkg, String cuName, String cuContents, IJavaProject project) throws CoreException {
        ICompilationUnit unit = createCompilationUnitAndPackage(pkg, cuName, cuContents, project);
        project.getProject().build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
        waitForManualBuild();
        waitForAutoBuild();
        assertNoProblems(project.getProject());
        return unit;
    }


    protected IField getFirstField(ICompilationUnit[] units)
    throws JavaModelException {
        return (IField) units[0].getTypes()[0].getChildren()[0];
    }
    protected IMethod getFirstMethod(ICompilationUnit unit)
    throws JavaModelException {
        return (IMethod) unit.getTypes()[0].getChildren()[0];
    }

    protected IntertypeElement getFirstIntertypeElement(ICompilationUnit unit) throws JavaModelException {
        return (IntertypeElement) unit.getTypes()[0].getChildren()[0];
    }
    protected IntertypeElement getFirstIntertypeElement(ICompilationUnit[] units) throws JavaModelException {
        return (IntertypeElement) units[0].getTypes()[0].getChildren()[0];
    }
    protected IntertypeElement getLastIntertypeElement(ICompilationUnit unit) throws JavaModelException {
        IJavaElement[] children = unit.getTypes()[0].getChildren();
        return (IntertypeElement) children[children.length-1];
    }




    public IPackageFragment createPackage(String name, IJavaProject javaProject) throws CoreException {
        return createPackage(name, null, javaProject);
    }
    public IPackageFragment createPackage(String name, IPackageFragmentRoot sourceFolder, IJavaProject javaProject) throws CoreException {
        if (sourceFolder == null)
            sourceFolder = createDefaultSourceFolder(javaProject);
        return sourceFolder.createPackageFragment(name, false, null);
    }

    public ICompilationUnit createCompilationUnit(IPackageFragment pack, String cuName,
            String source) throws JavaModelException {
      ICompilationUnit unit = pack.createCompilationUnit(cuName,
        source, false, null);
        waitForManualBuild();
        waitForAutoBuild();
        return unit;
    }

    public ICompilationUnit createCompilationUnitAndPackage(String packageName, String fileName,
            String source, IJavaProject javaProject) throws CoreException {
        return createCompilationUnit(createPackage(packageName, javaProject), fileName, source);
    }


    protected void buildProject(IJavaProject javaProject) throws CoreException {
        javaProject.getProject().build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
        assertNoProblems(javaProject.getProject());
        performDummySearch(javaProject);
    }


    public void assertNoProblems(IProject project) throws CoreException {
        String problems = getProblems(project);
        if (problems != null) {
            fail("Expecting no problems for project " + project.getName() + ", but found:\n\n" + problems);
        }
    }

    public String getProblems(IProject project) throws CoreException {
        IMarker[] markers = project.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
        StringBuilder sb = new StringBuilder();
        if (markers == null || markers.length == 0) {
            return null;
        }
        boolean errorFound = false;
        sb.append("Problems:\n");
      for (IMarker marker : markers) {
        if ((Integer) marker.getAttribute(IMarker.SEVERITY) == IMarker.SEVERITY_ERROR) {
          sb.append("  ");
          sb.append(marker.getResource().getName()).append(" : ");
          sb.append(marker.getAttribute(IMarker.LINE_NUMBER)).append(" : ");
          sb.append(marker.getAttribute(IMarker.MESSAGE)).append("\n");
          if (!((String) marker.getAttribute(IMarker.MESSAGE)).contains("can't determine modifiers of missing type")) {
            errorFound = true;
          }
        }
      }
        return errorFound ? sb.toString() : null;
    }

    public void setAutobuilding(boolean autobuild) throws CoreException {
        IWorkspaceDescription workspaceDesc = AspectJPlugin.getWorkspace().getDescription();
        workspaceDesc.setAutoBuilding(autobuild);
        AspectJPlugin.getWorkspace().setDescription(workspaceDesc);

    }

    public boolean isAutobuilding() {
        return AspectJPlugin.getWorkspace().getDescription().isAutoBuilding();
    }
}
