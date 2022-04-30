/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation Sebastian
 * Davids <sdavids@gmx.de>bug 38692
 * Luzius Meisser - adjusted for ajdoc
 * Helen Hawkins - updated for Eclipse 3.1 (bug 109484)
 * Arturo Salazar , Jason Naylor - Adjust for projects with large # of files.
 ******************************************************************************/
package org.eclipse.ajdt.internal.ui.ajdocexport;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.CoreUtils;
import org.eclipse.ajdt.internal.ui.resources.AspectJImages;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.internal.utils.AJDTUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.ILaunchesListener2;
import org.eclipse.debug.core.Launch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.OpenBrowserUtil;
import org.eclipse.jdt.internal.ui.javadocexport.JavadocExportMessages;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.refactoring.RefactoringSaveHelper;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Copied from org.eclipse.jdt.internal.ui.javadocexport.JavadocWizard
 * Updated for Eclipse 3.1 - bug 109484
 * Changes marked with // AspectJ Extension
 */
 /* AJDT 1.7 lots of changes.  Do not sync */
public class AJdocWizard extends Wizard implements IExportWizard {

//	 AspectJ Extension - using AJDoc pages rather than javadoc
	private AJdocTreeWizardPage fJTWPage;
	private AJdocSpecificsWizardPage fJSWPage;
	private AJdocStandardWizardPage fJSpWPage;

	private IPath fDestination;

	private final boolean fWriteCustom;
	private boolean fOpenInBrowser;

	private final String TREE_PAGE_DESC = "JavadocTreePage"; //$NON-NLS-1$
	private final String SPECIFICS_PAGE_DESC = "JavadocSpecificsPage"; //$NON-NLS-1$
	private final String STANDARD_PAGE_DESC = "JavadocStandardPage"; //$NON-NLS-1$

	private final int YES = 0;
	private final int YES_TO_ALL = 1;
	private final int NO = 2;
	private final int NO_TO_ALL = 3;
	//private final String JAVADOC_ANT_INFORMATION_DIALOG = "javadocAntInformationDialog";//$NON-NLS-1$

	private AJdocOptionsManager fStore;
	private final IWorkspaceRoot fRoot;
	private final IFile fXmlJavadocFile;

	private static final String ID_JAVADOC_PROCESS_TYPE = "org.eclipse.jdt.ui.javadocProcess"; //$NON-NLS-1$

	public static void openJavadocWizard(AJdocWizard wizard, Shell shell,
			IStructuredSelection selection) {
		wizard.init(PlatformUI.getWorkbench(), selection);

		PixelConverter converter = new PixelConverter(shell);

		WizardDialog dialog = new WizardDialog(shell, wizard);
		dialog.setMinimumPageSize(converter.convertWidthInCharsToPixels(100),
				converter.convertHeightInCharsToPixels(20));
		dialog.open();
	}

	public AJdocWizard() {
		this(null);
	}

	public AJdocWizard(IFile xmlJavadocFile) {
		super();
		setDefaultPageImageDescriptor(AspectJImages.W_EXPORT_AJDOC.getImageDescriptor());
//		 AspectJ Extension - message
		setWindowTitle(UIMessages.ajdocWizard_javadocwizard_title);
		setDialogSettings(JavaPlugin.getDefault().getDialogSettings());

		fRoot = ResourcesPlugin.getWorkspace().getRoot();
		fXmlJavadocFile = xmlJavadocFile;

		fWriteCustom = false;
	}

	/*
	 * @see IWizard#performFinish()
	 */
	public boolean performFinish() {
		IJavaProject[] checkedProjects = fJTWPage.getCheckedProjects();
		updateStore(checkedProjects);

		//If the wizard was not launched from an ant file store the setttings
		if (fXmlJavadocFile == null)
			fStore.updateDialogSettings(getDialogSettings(), checkedProjects);

		// Wizard should not run with dirty editors
		if (!new RefactoringSaveHelper(RefactoringSaveHelper.SAVE_ALL_ALWAYS_ASK).saveEditors(getShell()))
			return false;

		fDestination = new Path(fStore.getDestination());
		fDestination.toFile().mkdirs();

		fOpenInBrowser = fStore.doOpenInBrowser();

		//Ask if you wish to set the javadoc location for the projects (all) to
		//the location of the newly generated javadoc
		if (fStore.isFromStandard()) {
			try {

				URL newURL = fDestination.toFile().toURL();
				List<IJavaProject> projs = new ArrayList<>();
				//get javadoc locations for all projects
				for (IJavaProject curr : checkedProjects) {
					URL currURL = JavaUI.getProjectJavadocLocation(curr);
					if (!newURL.equals(currURL)) { // currURL can be null
						//if not all projects have the same javadoc location
						// ask if you want to change
						//them to have the same javadoc location
						projs.add(curr);
					}
				}
				if (!projs.isEmpty())
					setAllJavadocLocations(projs.toArray(new IJavaProject[0]), newURL);
			}
			catch (MalformedURLException e) {
				JavaPlugin.log(e);
			}
		}

//		 AspectJ Extension - commenting out unused code
//		if (fJSWPage.generateAnt()) {
//			//@Improve: make a better message
//			OptionalMessageDialog
//					.open(
//							JAVADOC_ANT_INFORMATION_DIALOG,
//							getShell(),
//							JavadocExportMessages
//									.getString("AJdocWizard.antInformationDialog.title"), null, JavadocExportMessages.getString("AJdocWizard.antInformationDialog.message"), MessageDialog.INFORMATION, new String[] { IDialogConstants.OK_LABEL }, 0); //$NON-NLS-1$ //$NON-NLS-2$
//			try {
//				fStore.createXML(checkedProjects);
//			} catch (CoreException e) {
//				ExceptionHandler
//						.handle(
//								e,
//								getShell(),
//								JavadocExportMessages
//										.getString("AJdocWizard.error.writeANT.title"), JavadocExportMessages.getString("AJdocWizard.error.writeANT.message")); //$NON-NLS-1$ //$NON-NLS-2$
//			}
//		}

		return executeJavadocGeneration();
	}

	private void updateStore(IJavaProject[] checkedProjects) {
		//writes the new settings to store
		fJTWPage.updateStore(checkedProjects);
		fJSpWPage.updateStore();
		fJSWPage.updateStore();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.jface.wizard.IWizard#performCancel()
	 */
	public boolean performCancel() {

		IJavaProject[] checkedProjects = fJTWPage.getCheckedProjects();
		updateStore(checkedProjects);

		//If the wizard was not launched from an ant file store the setttings
		if (fXmlJavadocFile == null) {
			fStore.updateDialogSettings(getDialogSettings(), checkedProjects);
		}
		return super.performCancel();
	}

	private void setAllJavadocLocations(IJavaProject[] projects, URL newURL) {
		Shell shell = getShell();
		Image image = shell == null ? null : shell.getDisplay().getSystemImage(
				SWT.ICON_QUESTION);
		String[] buttonlabels = new String[] { IDialogConstants.YES_LABEL,
				IDialogConstants.YES_TO_ALL_LABEL, IDialogConstants.NO_LABEL,
				IDialogConstants.NO_TO_ALL_LABEL };

		for (int j = 0; j < projects.length; j++) {
			IJavaProject iJavaProject = projects[j];
//			 AspectJ Extension - message
			String message= Messages.format(JavadocExportMessages.JavadocWizard_updatejavadoclocation_message, new String[] { iJavaProject.getElementName(), fDestination.toOSString()});
			MessageDialog dialog= new MessageDialog(shell, JavadocExportMessages.JavadocWizard_updatejavadocdialog_label, image, message, 4, buttonlabels, 1);

			switch (dialog.open()) {
			case YES:
				JavaUI.setProjectJavadocLocation(iJavaProject, newURL);
				break;
			case YES_TO_ALL:
				for (int i = j; i < projects.length; i++) {
					iJavaProject = projects[i];
					JavaUI.setProjectJavadocLocation(iJavaProject, newURL);
					j++;
				}
				break;
			case NO_TO_ALL:
				j = projects.length;
				break;
			case NO:
			default:
				break;
			}
		}
	}

	// AspectJ Extension - this method has been changed to get the arguments
	// required for generating ajdoc i.e. location of tools.jar, ajde.jar etc.
	private boolean executeJavadocGeneration() {
		Process process;
		try {
			List<String> userVmArgs = new ArrayList<>();
			List<String> progArgs = new ArrayList<>();

			fStore.getArgumentArray(userVmArgs, progArgs);

			File file = File.createTempFile("javadoc-arguments", ".tmp"); //$NON-NLS-1$//$NON-NLS-2$

			String jreDir = JavaRuntime.getDefaultVMInstall().getInstallLocation().getAbsolutePath();
			String aspectjrtDir = CoreUtils.getAspectjrtClasspath();
			String aspectjtoolsDir = ""; //$NON-NLS-1$
			URL ajdeURL = Platform.getBundle(AspectJPlugin.TOOLS_PLUGIN_ID).getEntry("ajde.jar"); //$NON-NLS-1$
			URL weaverURL = Platform.getBundle(AspectJPlugin.WEAVER_PLUGIN_ID).getEntry("aspectjweaver.jar"); //$NON-NLS-1$
			// From Eclipse 3.2M4 onwards we need equinox.common instead of core.runtime
			URL coreURL = Platform.getBundle("org.eclipse.equinox.common").getEntry("/"); //$NON-NLS-1$ //$NON-NLS-2$

			try {
				File ajdeFile = new File(FileLocator.toFileURL(ajdeURL).getFile());
				if (ajdeFile.exists()) {
					aspectjtoolsDir += "\"" + ajdeFile.getAbsolutePath() + "\"";
				}
				else {
					int lengthOfPath = ajdeFile.getAbsolutePath().length();
					MessageDialog.openError(getShell(),
						UIMessages.ajdocWizard_error_title,
						NLS.bind(UIMessages.ajdocWizard_error_cant_find_ajde_jar,
							ajdeFile.getAbsolutePath().substring(0, (lengthOfPath / 2)),
							ajdeFile.getAbsolutePath().substring((lengthOfPath / 2))));
					return true;
				}
				File weaverFile = new File(FileLocator.toFileURL(weaverURL).getFile());
				if (weaverFile.exists()) {
					aspectjtoolsDir += File.pathSeparator + "\"" + weaverFile.getAbsolutePath() + "\"";
				}
				else {
					int lengthOfPath = weaverFile.getAbsolutePath().length();
					MessageDialog.openError(getShell(),
						UIMessages.ajdocWizard_error_title,
						NLS.bind(UIMessages.ajdocWizard_error_cant_find_weaver_jar,
							weaverFile.getAbsolutePath().substring(0, (lengthOfPath / 2)),
							weaverFile.getAbsolutePath().substring((lengthOfPath / 2))));
					return true;
				}
				File coreFile = new File(FileLocator.toFileURL(coreURL).getFile());
				// need to check that have found a jar or the correct bundle. In the case when
				// you're running ajdoc in a runtime workbench and you have imported org.eclipse.core.runtime
				// into your workbench coreFile is just this directory and ajdoc doesn't run
				// (and it's not immediately obvious why).
				if (
					coreFile.exists() &&
					(coreFile.getName().endsWith("jar")  || coreFile.getName().endsWith("cp")) //$NON-NLS-1$ //$NON-NLS-2$
				) {
					aspectjtoolsDir += File.pathSeparator + "\"" + coreFile.getAbsolutePath() + "\"";
				}
				else {
					int lengthOfPath = coreFile.getAbsolutePath().length();
					MessageDialog.openError(getShell(),
						UIMessages.ajdocWizard_error_title,
						NLS.bind(UIMessages.ajdocWizard_error_cant_find_runtime_jar,
							coreFile.getAbsolutePath().substring(0, (lengthOfPath / 2)),
							coreFile.getAbsolutePath().substring((lengthOfPath / 2))));
					return true;
				}
			}
			catch (IOException ignored) { }

			List<String> vmArgs = new ArrayList<>();
			String[] contentsOfJREDir = JavaRuntime.getDefaultVMInstall().getInstallLocation().list();
			boolean foundJavaCmd = false;
			for (String jreDirEntry : contentsOfJREDir) {
				if (jreDirEntry.equals("bin")) { //$NON-NLS-1$
					vmArgs.add(jreDir + File.separator + "bin" + File.separator + "java"); //$NON-NLS-1$ //$NON-NLS-2$
					foundJavaCmd = true;
					break;
				}
			}
			if (!foundJavaCmd) {
				vmArgs.add(jreDir + File.separator + "jre" + File.separator + "bin" + File.separator + "java"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			// Bug 119853: Mac OS uses different paths
			String javadocJarDir;
			String javadocJarName;
			if (!AJDTUtils.isMacOS()) {
				javadocJarName = "tools.jar"; //$NON-NLS-1$
				javadocJarDir = jreDir + File.separator + "lib" + File.separator + "tools.jar"; //$NON-NLS-1$ //$NON-NLS-2$
			}
			else {
				javadocJarName = "classes.jar"; //$NON-NLS-1$
				javadocJarDir = jreDir + File.separator + ".." + File.separator + "Classes" + File.separator + "classes.jar"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			boolean noXmx = true;
			for (String userVmArg : userVmArgs) {
				if (userVmArg.startsWith("-Xmx")) //$NON-NLS-1$
					noXmx = false;
				if (userVmArg.contains(javadocJarName))
					javadocJarDir = userVmArg;
				else
					vmArgs.add(userVmArg);
			}
			if (noXmx) {
				// user didn't specify max heap size, so provide our own setting (from ajdoc.bat)
				vmArgs.add("-Xmx64M"); //$NON-NLS-1$
			}
			vmArgs.add("-classpath"); //$NON-NLS-1$

			vmArgs.add(checkForSpaces(aspectjtoolsDir + File.pathSeparator + "\"" + javadocJarDir + "\"" + File.pathSeparator + "\"" + aspectjrtDir + "\""));
			vmArgs.add("org.aspectj.tools.ajdoc.Main"); //$NON-NLS-1$

			// Condense arguments into a file so that a poject
			// with a large amount of source files doesn't pass too many
			// arguments causing a System error 87 on windows when ajdoc is called.
			int sourceIndex = 0; // get index of where the source files start

			for (int i = 0; i < progArgs.size(); i++) {
				// Keep adding arguments till we reach the source files.
				String progArg = progArgs.get(i);
        // TODO: Why 2x ".java"? Duplicate or typo (e.g. ".aj")?
				if (progArg.endsWith(".java") || progArg.endsWith(".java")) {
					sourceIndex = i;
					break;
				}
				if (progArg.equals("-classpath")) {
					// skip the classpath since it is already set
					i++;
				}
				else {
					vmArgs.add(checkForSpaces(progArg));
				}
			}

			// Create a temporary file containing a list of
			// all the source files to use as an argument file.
			File tempFile = File.createTempFile("filelist", ".lst");
			tempFile.deleteOnExit();
			vmArgs.add("@" + tempFile.getPath());
			java.io.BufferedWriter out = new java.io.BufferedWriter(new java.io.FileWriter(tempFile));
			for (int i = sourceIndex; i < progArgs.size(); i++) {
				out.write(progArgs.get(i));
				out.write("\n");
			}
			out.close();

			String[] args = vmArgs.toArray(new String[0]);

			process = Runtime.getRuntime().exec(args);
			if (process != null) {
				// construct a formatted command line for the process properties
				StringBuilder buf = new StringBuilder();
				for (String arg : args)
					buf.append(arg).append(' ');

				/* AJDT 1.7 begin */
				try {
					ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
					ILaunchConfigurationType lcType = launchManager.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);

					String name = JavadocExportMessages.JavadocWizard_launchconfig_name;
					ILaunchConfigurationWorkingCopy wc = lcType.newInstance(null, name);
					wc.setAttribute(IDebugUIConstants.ATTR_PRIVATE, true);

					ILaunch newLaunch = new Launch(wc, ILaunchManager.RUN_MODE, null);
					// AspectJ Extension - message
					IProcess iprocess = DebugPlugin
						.newProcess(
							newLaunch,
							process,
							UIMessages.ajdocWizard_ajdocprocess_label);
					iprocess.setAttribute(IProcess.ATTR_CMDLINE, buf.toString());
					iprocess.setAttribute(IProcess.ATTR_PROCESS_TYPE, ID_JAVADOC_PROCESS_TYPE);

					launchManager.addLaunch(newLaunch);
					JavadocLaunchListener listener = new JavadocLaunchListener(getShell().getDisplay(), newLaunch, file);
					launchManager.addLaunchListener(listener);
					if (newLaunch.isTerminated())
						listener.onTerminated();
				}
				catch (CoreException e) {
					// AspectJ Extension - message
					String title = UIMessages.ajdocWizard_error_title;
					String message = UIMessages.ajdocWizard_launch_error_message;
					ExceptionHandler.handle(e, getShell(), title, message);
				}
				/* AJDT 1.7 end */

				return true;

			}
		}
		catch (IOException e) {
			// AspectJ Extension - message
			String title = UIMessages.ajdocWizard_error_title;
			String message = UIMessages.ajdocWizard_exec_error_message;
			IStatus status = new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.ERROR, e.getMessage(), e);
			ExceptionHandler.handle(new CoreException(status), getShell(), title, message);
			return false;
		}
		return false;

	}

	private String checkForSpaces(String curr) {
		if (curr.indexOf(' ') == -1) {
			return curr;
		}
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < curr.length(); i++) {
			char ch = curr.charAt(i);
			if (ch == '\\' || ch == '\'' /*|| ch == ' '*/) {
				buf.append('\\');
			}
			buf.append(ch);
		}
		return buf.toString();
	}

	/*
	 * @see IWizard#addPages()
	 */
	public void addPages() {

		fJTWPage = new AJdocTreeWizardPage(TREE_PAGE_DESC, fStore);
		fJSWPage = new AJdocSpecificsWizardPage(SPECIFICS_PAGE_DESC,fJTWPage, fStore);
		fJSpWPage= new AJdocStandardWizardPage(STANDARD_PAGE_DESC, fJTWPage, fStore);

		super.addPage(fJTWPage);
		super.addPage(fJSWPage);
		super.addPage(fJSpWPage);

		fJTWPage.init();
		fJSWPage.init();
		fJSpWPage.init();

	}

	public void init(IWorkbench workbench, IStructuredSelection structuredSelection) {
    // TODO: Why is method parameter 'structuredSelection' not used here?
		IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
		List<IJavaElement> selected = Collections.emptyList();
		if (window != null) {
			ISelection selection = window.getSelectionService().getSelection();
			if (selection instanceof IStructuredSelection) {
        // TODO: Can this be anything else than a List<IJavaElement>?
				selected = ((IStructuredSelection) selection).toList();
			} else {
				IJavaElement element = EditorUtility.getActiveEditorJavaInput();
				if (element != null) {
					selected = new ArrayList<>();
					selected.add(element);
				}
			}
		}
		fStore = new AJdocOptionsManager(fXmlJavadocFile, getDialogSettings(), selected);
	}

	private void refresh(IPath path) {
		if (fRoot.findContainersForLocation(path).length > 0) {
			try {
				fRoot.refreshLocal(IResource.DEPTH_INFINITE, null);
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}
	}

	private void spawnInBrowser(Display display) {
		if (fOpenInBrowser) {
			try {
				IPath indexFile = fDestination.append("index.html"); //$NON-NLS-1$
				URL url = indexFile.toFile().toURL();
				OpenBrowserUtil.open(url, display);  // AJDT 3.6
			} catch (MalformedURLException e) {
				JavaPlugin.log(e);
			}
		}
	}

    /* AJDT 1.7 begin */
    private class JavadocLaunchListener implements ILaunchesListener2 {
        private final Display fDisplay;
        private volatile ILaunch fLaunch;
        private final File fFile;

        public JavadocLaunchListener(Display display, ILaunch launch, File file) {
            fDisplay= display;
            fLaunch= launch;
            fFile= file;
        }

        public void launchesTerminated(ILaunch[] launches) {
          for (ILaunch launch : launches) {
            if (launch == fLaunch) {
              onTerminated();
              return;
            }
          }
        }

        public void onTerminated() {
            try {
                if (fLaunch != null) {
                    fFile.delete();
                    spawnInBrowser(fDisplay);
                    refresh(fDestination);
                    fLaunch= null;
                }
            } finally {
                DebugPlugin.getDefault().getLaunchManager().removeLaunchListener(this);
            }
        }

        public void launchesAdded(ILaunch[] launches) { }
        public void launchesChanged(ILaunch[] launches) { }
        public void launchesRemoved(ILaunch[] launches) { }
    }
    /* AJDT 1.7 end */

	public IWizardPage getNextPage(IWizardPage page) {
		if (page instanceof AJdocTreeWizardPage) {
			return fJSWPage;
		} else if (page instanceof AJdocSpecificsWizardPage) {
			return null;
		} else
			return null;
	}

	public IWizardPage getPreviousPage(IWizardPage page) {
		if (page instanceof AJdocSpecificsWizardPage) {
			return fJSWPage;
		} else if (page instanceof AJdocTreeWizardPage) {
			return null;
		} else
			return null;
	}
}
