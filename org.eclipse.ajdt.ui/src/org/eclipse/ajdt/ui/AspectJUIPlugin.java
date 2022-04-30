/**********************************************************************
 Copyright (c) 2002, 2005 IBM Corporation and others.
 All rights reserved. This program and the accompanying materials
 are made available under the terms of the Eclipse Public License v1.0
 which accompanies this distribution, and is available at
 http://www.eclipse.org/legal/epl-v10.html
 Contributors:
 Adrian Colyer, Andy Clement, Tracy Gardner - initial version
 Helen Hawkins - updated for new ajde interface (bug 148190)
 ...
 **********************************************************************/
package org.eclipse.ajdt.ui;

// --- imports ---

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.builder.AJBuilder;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.internal.builder.UIBuildListener;
import org.eclipse.ajdt.internal.core.ajde.ICompilerFactory;
import org.eclipse.ajdt.internal.javamodel.AJCompilationUnitResourceChangeListener;
import org.eclipse.ajdt.internal.ui.EnsureAJBuilder;
import org.eclipse.ajdt.internal.ui.ajde.UICompilerFactory;
import org.eclipse.ajdt.internal.ui.editor.AspectJTextTools;
import org.eclipse.ajdt.internal.ui.lazystart.Utils;
import org.eclipse.ajdt.internal.ui.preferences.AJCompilerPreferencePage;
import org.eclipse.ajdt.internal.ui.preferences.AspectJPreferences;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.internal.ui.tracing.EventTraceLogger;
import org.eclipse.ajdt.internal.utils.AJDTUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.persistence.TemplatePersistenceData;
import org.eclipse.jface.text.templates.persistence.TemplateReaderWriter;
import org.eclipse.jface.text.templates.persistence.TemplateStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.UIPlugin;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

// --- end imports ---
/**
 * The main plugin class used in the desktop for AspectJ integration.
 * <p>
 * The AspectJUIPlugin (org.eclipse.ajdt.ui) provides the user interface and build
 * integration to enable use of Aspect-Oriented Software Development (AOSD)
 * using AspectJ within Eclipse.
 * </p>
 * <p>
 * This plugin depends on the org.aspectj.ajde (AJDE) plugin for the AspectJ
 * compiler and tools. The AJTools plugin is available from <a
 * href="http://www.aspectj.org">aspectj.org </a>
 */
public class AspectJUIPlugin extends org.eclipse.ui.plugin.AbstractUIPlugin {

	// the id of this plugin
	public static final String PLUGIN_ID = Utils.PLUGIN_ID;

	public static final String ID_OUTLINE = PLUGIN_ID + ".ajoutlineview"; //$NON-NLS-1$

	private static final String AJDE_VERSION_KEY_PREVIOUS = "ajde.version.at.previous.startup"; //$NON-NLS-1$

	public static final String VISUALISER_ID = "org.eclipse.contribution.visualiser";  //$NON-NLS-1$

	private static final String XREF_CORE_ID = "org.eclipse.contribution.xref.core";  //$NON-NLS-1$

	private static final String XREF_UI_ID = "org.eclipse.contribution.xref.ui";  //$NON-NLS-1$

	/**
	 * Whether to use the Visualiser component
	 */
	public static boolean usingVisualiser = true;

	/**
	 * Whether to use the Cross References component
	 */
	public static boolean usingXref = true;

	/**
	 * shared single instance of the plugin
	 */
	private static AspectJUIPlugin plugin;

	/**
	 * The text tools to use for AspectJ aware editing
	 */
	private AspectJTextTools aspectJTextTools;

	/**
	 * Listens for AspectJ builds
	 */
	private UIBuildListener buildListener;

	public static final String RELATED_LOCATIONS_ATTRIBUTE_PREFIX = "relatedLocations-"; //$NON-NLS-1$

	public static final int PROGRESS_MONITOR_MAX = 100;

    private EnsureAJBuilder ajProjectListener;

    /**
     * Creates an AspectJPlugin instance and initializes the supporting Ajde
     * tools - Compatible with Eclipse 3.0. Note the rest of the contents of the
     * 2.x constructor now resides in the start(BundleContext) method.
     */
    public AspectJUIPlugin() {
        super();
        AspectJUIPlugin.plugin = this;
    }

    /**
	 * Return the single default instance of this plugin
	 */
	public static AspectJUIPlugin getDefault() {
		return plugin;
	}

	/**
	 * Converts the given project to be an AspectJ project
	 * @param project
	 * @throws CoreException
	 */
	public static void convertToAspectJProject(IProject project) throws CoreException {
		AJDTUtils.addAspectJNature(project, false);
	}

	/**
	 * Removes the AspectJ capability from a project
	 * @param project
	 * @throws CoreException
	 */
	public static void convertFromAspectJProject(IProject project) throws CoreException {
		AJDTUtils.removeAspectJNature(project);
	}


	/**
	 * This function checks to see if the workbench is starting with a new
	 * version of AJDE (it persists the version previously used by the workbench
	 * in a preference) - if a new version of AJDE is in use then it goes
	 * through the projects and for any AspectJ project, it tries to ensure the
	 * path to any aspectjrt.jar that it references is correct. This fixes a
	 * migration issue we have where the path includes the AJDE version -
	 * without this method you have to do it manually by either editing the
	 * aspectjrt.jar entry or removing/readding the nature. We also add the
	 * AspectJ code templates here.
	 *
	 */
	private void checkAspectJVersion() {
		IPreferenceStore store = AspectJUIPlugin.getDefault()
				.getPreferenceStore();

		// Version of AJDE now installed.
		String currentAjdeVersion = this.getBundle().getVersion().toString();

		// Version that the projects in this workspace used on the previous
		// execution of eclipse.
		String previousAjdeVersion = store.getString(AJDE_VERSION_KEY_PREVIOUS);
		if (previousAjdeVersion == null
				|| !currentAjdeVersion.equals(previousAjdeVersion)
				|| previousAjdeVersion.equals("@AJDEVERSION@")) {
			checkTemplatesInstalled();
			checkProblemMarkersVisible();
			store.putValue(AJDE_VERSION_KEY_PREVIOUS, currentAjdeVersion);
		}
	}

	private void checkProblemMarkersVisible() {
		String TAG_DIALOG_SECTION = "org.eclipse.ui.views.problem"; //$NON-NLS-1$
		String problemMarker = "org.eclipse.ajdt.ui.problemmarker:"; //$NON-NLS-1$
		AbstractUIPlugin plugin = UIPlugin.getDefault();
		IDialogSettings workbenchSettings = plugin.getDialogSettings();
		IDialogSettings settings = workbenchSettings
				.getSection(TAG_DIALOG_SECTION);
		if (settings != null) {
			IDialogSettings filterSettings = settings.getSection("filter"); //$NON-NLS-1$
			if (filterSettings != null) {
				String enabledMarkers = filterSettings.get("selectedType"); //$NON-NLS-1$
				if ((enabledMarkers != null)
            && !enabledMarkers.contains(problemMarker)) {
					enabledMarkers = enabledMarkers + problemMarker;
					filterSettings.put("selectedType", enabledMarkers); //$NON-NLS-1$
				}
			}
		}
	}

	/**
	 * Install the AspectJ code templates. We'd like to do this by an extension
	 * point, but there doesn't seem to be one.
	 */
	private void checkTemplatesInstalled() {
	    TemplateStore codeTemplates;
	    try {
    		codeTemplates = JavaPlugin.getDefault()
    				.getTemplateStore();
	    } catch (Exception e) {
	        // a problem occurred while loading templates (Bug 259033)
	        // just ignore and try the next time.
	        return;
	    }
		// bug 90791: don't add templates if they are already there
		// bug 125998: using pertypewithin because it was the most recently added
	    // bug 245265: also look for the the "aspectj" context
		Template template = codeTemplates.findTemplate("pertypewithin");
        if (template == null || !template.getContextTypeId().equals("aspectj")) { //$NON-NLS-1$
			try {
				URL loc = getBundle().getEntry("/aspectj_code_templates.xml"); //$NON-NLS-1$
				TemplateReaderWriter trw = new TemplateReaderWriter();
				TemplatePersistenceData[] templates = trw.read(loc.openStream(), null);
				if ((templates == null) || (templates.length == 0)) {
					AJLog.log(UIMessages.codeTemplates_couldNotLoad);
				} else {
				    TemplatePersistenceData[] existingTemplates = codeTemplates.getTemplateData(true);
          for (TemplatePersistenceData templatePersistenceData : templates) {
            // Check that the individual template has not already been added
            // would have been nice if templates used the ID tag, but they don't, so have to iterate through all
            TemplatePersistenceData existing = null;
            for (TemplatePersistenceData maybeExisting : existingTemplates) {
              if (maybeExisting.getTemplate().getName().equals(templatePersistenceData.getTemplate().getName())) {
                existing = maybeExisting;
                break;
              }
            }
            // also look for the the "aspectj" context
            if (existing == null || !existing.getTemplate().getContextTypeId().equals("aspectj")) {
              codeTemplates.add(templatePersistenceData);
              if (existing != null) {
                existing.setDeleted(true);
              }
            }
          }
					codeTemplates.save();
				}
			} catch (IOException fnf) {
				AJLog.log(UIMessages.codeTemplates_couldNotLoad);
			}
		}
	}

	public void start(BundleContext context) throws Exception {
		super.start(context);

		// listen for builds of AJ projects
		buildListener = new UIBuildListener();
        AJBuilder.addAJBuildListener(buildListener);

		// BUG 249045 and BUG 261045 don't do this any more
//		if (!AspectJPlugin.USING_CU_PROVIDER) {
//		    insertAJCompilationUnitDocumentProvider();
//		}


		// Create and register the resource change listener if necessary, it
		// will be notified if resources are added/deleted or their content changed.

		// listener for aspectj model
		AspectJPlugin.getWorkspace().addResourceChangeListener(
				new AJCompilationUnitResourceChangeListener(),
				IResourceChangeEvent.PRE_CLOSE
						| IResourceChangeEvent.PRE_DELETE
						| IResourceChangeEvent.POST_CHANGE
						| IResourceChangeEvent.PRE_BUILD);

		if (!AspectJPlugin.getDefault().isHeadless()) {
    		// set the UI version of core operations
    		AspectJPlugin.getDefault().setAJLogger(new EventTraceLogger());
		}
		// set the compiler factory to be the ui one
		setCompilerFactory(new UICompilerFactory());

        ajProjectListener = new EnsureAJBuilder();
        AspectJPlugin.getWorkspace().addResourceChangeListener(ajProjectListener, IResourceChangeEvent.PRE_BUILD);

		// 126728: don't try to use the Visualiser / Xref components if the
		// required plugins are not available
		if (Platform.getBundle(VISUALISER_ID)==null) {
			usingVisualiser = false;
		}
		if ((Platform.getBundle(XREF_CORE_ID)==null)
				|| (Platform.getBundle(XREF_UI_ID)==null)) {
			usingXref = false;
		}

		checkAspectJVersion();

	    Job cuInitJob = new Job("Initialize CompilationUnit Manager") {
            protected IStatus run(IProgressMonitor monitor) {
                // bug 278425 --- see if m2eclipse exists and ensure it is started before continuing
                startM2Eclipse();
                AJCompilationUnitManager.INSTANCE.initCompilationUnits(AspectJPlugin.getWorkspace());
                AJDTUtils.refreshPackageExplorer();
                return Status.OK_STATUS;
            }
        };

        cuInitJob.setPriority(Job.SHORT);
        cuInitJob.schedule();
	}

	// this method may be outdated since the name of the bundle has moved
	private void startM2Eclipse() {
	    Bundle m2eclipseBundle = Platform.getBundle("org.maven.ide.eclipse");
	    if (m2eclipseBundle != null) {
	        try {
	            // start bundle, but don't force bundle to be restarted next time
	            m2eclipseBundle.start(Bundle.START_TRANSIENT);
	        } catch (BundleException e) {
	            // bundle couldn't be started for some reason
	        }
	    }
	}

    public void stop(BundleContext context) throws Exception {
	    super.stop(context);
	    AspectJPlugin.getWorkspace().removeResourceChangeListener(ajProjectListener);
	    AJBuilder.removeAJBuildListener(buildListener);
	}

	/**
	 * get the active window in the workbench, or null if no window is active
	 */
	public IWorkbenchWindow getActiveWorkbenchWindow() {
		return plugin.getWorkbench().getActiveWorkbenchWindow();

	}

	/**
	 * get the main workbench display
	 */
	public Display getDisplay() {
		return PlatformUI.getWorkbench().getDisplay();
	}

	/**
	 * get the text tools to be used by the AspectJ editor
	 */
	public AspectJTextTools getAspectJTextTools() {
		IPreferenceStore textToolPreferences;

		if (aspectJTextTools == null) {
			// text tools deliberately use the JavaPlugin settings
			textToolPreferences = JavaPlugin.getDefault().getPreferenceStore();
			aspectJTextTools = new AspectJTextTools(textToolPreferences);
		}

		return aspectJTextTools;
	}

	/**
	 * initialize the default preferences for this plugin
	 */
	protected void initializeDefaultPreferences(IPreferenceStore store) {
		AspectJPreferences.initDefaults(store);
		AJCompilerPreferencePage.initDefaults(store);
	}

	/**
	 * initialize the plug-in image registry
	 */
	protected void initializeImageRegistry(ImageRegistry reg) {
	}

	// Implementation of ISelectionListener follows

	/**
	 * Attempt to update the project's build classpath with the AspectJ runtime
	 * library.
	 *
	 * @param project
	 */
	public static void addAjrtToBuildPath(IProject project) {
		IJavaProject javaProject = JavaCore.create(project);
		try {
			IClasspathEntry[] originalCP = javaProject.getRawClasspath();
			IPath ajrtPath = new Path(AspectJPlugin.ASPECTJRT_CONTAINER);
			boolean found = false;
      for (IClasspathEntry iClasspathEntry : originalCP) {
        if (iClasspathEntry.getPath().equals(ajrtPath)) {
          found = true;
          break;
        }
      }
			if (!found) {
    			IClasspathEntry ajrtLIB = JavaCore.newContainerEntry(
    					ajrtPath, false);
    			// Update the raw classpath with the new ajrtCP entry.
    			int originalCPLength = originalCP.length;
    			IClasspathEntry[] newCP = new IClasspathEntry[originalCPLength + 1];
    			System.arraycopy(originalCP, 0, newCP, 0, originalCPLength);
    			newCP[originalCPLength] = ajrtLIB;
    			javaProject.setRawClasspath(newCP, new NullProgressMonitor());
			}
		} catch (JavaModelException ignored) {
		}
	}

	/**
	 * Attempt to update the project's build classpath by removing any occurance
	 * of the AspectJ runtime library.
	 *
	 * @param project
	 */
	public static void removeAjrtFromBuildPath(IProject project) {
		IJavaProject javaProject = JavaCore.create(project);
		try {
			IClasspathEntry[] originalCP = javaProject.getRawClasspath();
			ArrayList tempCP = new ArrayList();

			// Go through each current classpath entry one at a time. If it
			// is not a reference to the aspectjrt.jar then do not add it
			// to the collection of new classpath entries.
      for (IClasspathEntry iClasspathEntry : originalCP) {
        IPath path = iClasspathEntry.getPath();
        boolean keep = true;
        if (path.toOSString().endsWith("ASPECTJRT_LIB") //$NON-NLS-1$
            || path.toOSString().endsWith("aspectjrt.jar"))
        { //$NON-NLS-1$
          keep = false;
        }
        if (iClasspathEntry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
          if (path.segment(0).equals(AspectJPlugin.ASPECTJRT_CONTAINER)) {
            keep = false;
          }
        }
        if (keep) {
          tempCP.add(iClasspathEntry);
        }
      }// end for

			// Set the classpath with only those elements that survived the
			// above filtration process.
			if (originalCP.length != tempCP.size()) {
				IClasspathEntry[] newCP = (IClasspathEntry[]) tempCP
						.toArray(new IClasspathEntry[0]);
				javaProject.setRawClasspath(newCP, new NullProgressMonitor());
			}// end if at least one classpath element removed
		} catch (JavaModelException ignored) {
		}
	}

	public ICompilerFactory getCompilerFactory() {
		return AspectJPlugin.getDefault().getCompilerFactory();
	}

	public void setCompilerFactory(ICompilerFactory compilerFactory) {
		AspectJPlugin.getDefault().setCompilerFactory(compilerFactory);
	}

}
