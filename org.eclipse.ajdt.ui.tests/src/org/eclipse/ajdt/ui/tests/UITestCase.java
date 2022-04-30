/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sian January  - initial version
 *******************************************************************************/

package org.eclipse.ajdt.ui.tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.ajdt.internal.ui.editor.AspectJEditor;
import org.eclipse.ajdt.internal.ui.preferences.AspectJPreferences;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.ajdt.ui.tests.testutils.SynchronizationUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.internal.console.ConsoleView;
import org.eclipse.ui.internal.console.IOConsolePage;
import org.eclipse.ui.internal.views.markers.ExtendedMarkersView;
import org.eclipse.ui.internal.views.markers.ProblemsView;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.TextEditorAction;


/**
 * Superclass for all the UI tests, with several utility methods
 */
public abstract class UITestCase extends AJDTCoreTestCase {

	public static final String TEST_PROJECTS_FOLDER = "/workspace"; //$NON-NLS-1$
    protected Display display = Display.getCurrent();

	public UITestCase(String name) {
		super(name);
	}

	public UITestCase() {
		super();
	}

	protected void setUp() throws Exception {
		super.setUp();
		AllUITests.setupAJDTPlugin();
	}

	protected void tearDown() throws Exception {
	    closeAllEditors();
	    waitForJobsToComplete();
	    ILaunchConfiguration[] launchConfigurations = DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurations();
    for (ILaunchConfiguration configuration : launchConfigurations) {
      configuration.delete();
    }
		super.tearDown();
	}



	/**
	 * Opens a file in its associated editor.
	 */
	protected IEditorPart openFileInDefaultEditor(IFile file, boolean activate){
		if (file != null) {
			IWorkbenchPage p= JavaPlugin.getActivePage();
			if (p != null) {
				try {
					IEditorPart editorPart= IDE.openEditor(p, file, activate);
					initializeHighlightRange(editorPart);
					return editorPart;
				} catch (PartInitException ignored) {
				}
			}
		}
		return null;
	}

	/**
	 * Opens a file in the AspectJ editor
	 */
	protected IEditorPart openFileInAspectJEditor(IFile file, boolean activate){
		if (file != null) {
			IWorkbenchPage p= JavaPlugin.getActivePage();
			if (p != null) {
				try {
					IEditorPart editorPart= IDE.openEditor(p, file, AspectJEditor.ASPECTJ_EDITOR_ID, activate);
					initializeHighlightRange(editorPart);
					return editorPart;
				} catch (PartInitException ignored) {
				}
			}
		}
		return null;
	}

	private void initializeHighlightRange(IEditorPart editorPart) {
		if (editorPart instanceof ITextEditor) {
			IAction toggleAction= editorPart.getEditorSite().getActionBars().getGlobalActionHandler(ITextEditorActionDefinitionIds.TOGGLE_SHOW_SELECTED_ELEMENT_ONLY);
			if (toggleAction != null && toggleAction.isEnabled() && toggleAction.isChecked()) {
				if (toggleAction instanceof TextEditorAction) {
					// Reset the action
					((TextEditorAction)toggleAction).setEditor(null);
					// Restore the action
					((TextEditorAction)toggleAction).setEditor((ITextEditor)editorPart);
				} else {
					// Uncheck
					toggleAction.run();
					// Check
					toggleAction.run();
				}
			}
		}
	}


	protected void waitForJobsToComplete(){
		SynchronizationUtils.joinBackgroudActivities();
	}

	protected void setUpPluginEnvironment() {
		// set the project up so that when asked, the pde plugin
		// is added automatically and the preference configurations
		// have all been set up (therefore don't need user
		// interaction.
		AspectJPreferences.setAskPDEAutoImport(false);
		AspectJPreferences.setDoPDEAutoImport(true);
		AspectJPreferences.setPDEAutoImportConfigDone(true);
		AspectJPreferences.setAskPDEAutoRemoveImport(false);
		AspectJPreferences.setDoPDEAutoRemoveImport(true);
		AspectJPreferences.setPDEAutoRemoveImportConfigDone(true);
	}

	protected void resetPluginEnvironment() {
		IPreferenceStore ps = AspectJUIPlugin.getDefault().getPreferenceStore();
		ps.setToDefault(AspectJPreferences.PDE_AUTO_IMPORT_CONFIG_DONE);
		ps.setToDefault(AspectJPreferences.ASK_PDE_AUTO_IMPORT);
		ps.setToDefault(AspectJPreferences.DO_PDE_AUTO_IMPORT);
		ps.setToDefault(AspectJPreferences.PDE_AUTO_REMOVE_IMPORT_CONFIG_DONE);
		ps.setToDefault(AspectJPreferences.ASK_PDE_AUTO_REMOVE_IMPORT);
		ps.setToDefault(AspectJPreferences.DO_PDE_AUTO_REMOVE_IMPORT);
	}


    protected String getTestBundleName() {
        return "org.eclipse.ajdt.ui.tests";
    }

	/**
	 * Closes all open editors without saving
	 */
	protected void closeAllEditors() {
		IEditorReference[] editors = AspectJUIPlugin.getDefault().getActiveWorkbenchWindow().getActivePage().getEditorReferences();
    for (IEditorReference iEditorReference : editors) {
      IEditorPart editor = iEditorReference.getEditor(false);
      editor.getEditorSite().getPage().closeEditor(editor, false);
    }
	}

	/**
	 * Recursively delete the given directory and all its children
	 * @param f
	 */
	protected void deleteDir(File f) {
        try {
			if (!f.getCanonicalFile().equals(f.getAbsoluteFile())) {
			    // dont follow symbolic links
			    return;
			}
		} catch (IOException e) {
			return;
		}
		File[] files = f.listFiles();
    for (File file : files) {
      if (file.isDirectory()) {
        deleteDir(file);
      }
      else {
        file.delete();
      }
    }
		f.delete();
	}


	protected String readFile(IFile file) throws Exception {
		StringBuilder contents = new StringBuilder();
		BufferedReader br = new BufferedReader(new InputStreamReader(file
				.getContents()));
		String line = br.readLine();
		while (line != null) {
			contents.append(line);
			line = br.readLine();
		}
		br.close();
		return contents.toString();
	}

	protected IMarker[] getAllProblemViewMarkers() {
		try {
			ProblemsView problemsView = (ProblemsView) AspectJUIPlugin.getDefault().getActiveWorkbenchWindow().getActivePage().showView("org.eclipse.ui.views.ProblemView");        //$NON-NLS-1$
			Method getAllMarkersMethod = ExtendedMarkersView.class.getDeclaredMethod("getAllMarkers"); //$NON-NLS-1$
			getAllMarkersMethod.setAccessible(true);
			return (IMarker[]) getAllMarkersMethod.invoke(problemsView, new Object[]{});
		} catch(Exception e) {
			fail("Could not get problem view markers: " + e.getMessage()); //$NON-NLS-1$
			return null;
		}

	}

    protected static String getConsoleViewContents() {
        ConsoleView cview = null;
        IViewReference[] views = AspectJUIPlugin.getDefault().getWorkbench()
                .getActiveWorkbenchWindow().getActivePage().getViewReferences();
      for (IViewReference view : views) {
        if (view.getView(false) instanceof ConsoleView) {
          cview = (ConsoleView) view.getView(false);
        }
      }
        assertNotNull("Console view should be open", cview); //$NON-NLS-1$
        IOConsolePage page = (IOConsolePage) cview.getCurrentPage();
        TextViewer viewer = page.getViewer();
        return viewer.getDocument().get();
    }

    /**
     * Post a key event (equivalent to posting a key down event then a key up
     * event)
     *
     * @param c -
     *            the character to post
     */
    protected void postKey(char c) {
        postKeyDown(c);
        postKeyUp(c);
    }

    /**
     * Post a key down event
     *
     * @param c -
     *            the character to post
     */
    protected void postKeyDown(char c) {
        Event event = new Event();
        event.type = SWT.KeyDown;
        event.character = c;
        display.post(event);
        sleep(10);
    }

    /**
     * Post a key up event
     *
     * @param c -
     *            the character to post
     */
    protected void postKeyUp(char c) {
        Event event = new Event();
        event.type = SWT.KeyUp;
        event.character = c;
        display.post(event);
        sleep(10);
    }

    /**
     * Post a key event (equivalent to posting a key down event then a key up
     * event)
     *
     * @param keyCode -
     *            one of the key codes defined int he SWT class
     */
    protected void postKey(int keyCode) {
        postKeyDown(keyCode);
        postKeyUp(keyCode);
    }

    /**
     * Post a key down event
     *
     * @param keyCode -
     *            one of the key codes defined int he SWT class
     */
    protected void postKeyDown(int keyCode) {
        Event event = new Event();
        event.type = SWT.KeyDown;
        event.keyCode = keyCode;
        display.post(event);
        sleep(10);
    }

    /**
     * Post a key up event
     *
     * @param keyCode -
     *            one of the key codes defined int he SWT class
     */
    protected void postKeyUp(int keyCode) {
        Event event = new Event();
        event.type = SWT.KeyUp;
        event.keyCode = keyCode;
        display.post(event);
        sleep(10);
    }


    protected void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
    }

    protected void sleep(int delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {
        }
    }
}
