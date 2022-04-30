/**********************************************************************
Copyright (c) 2000, 2007 IBM Corp. and others.
All rights reserved. This program and the accompanying materials
are made available under the terms of the Eclipse Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/epl-v10.html

Contributors:
    IBM Corporation - Initial implementation
    Andy Clement, 1st Version, 7th October 2002
    Matt Chapman - add support for Go To Related Location entries
                 - add support for Advises entries
    Sian January - support for "aspect declarations", "annotates",
    				"declared by" and "annotated by" menus
    Helen Hawkins - updated for new ajde interface (bug 148190)

**********************************************************************/
package org.eclipse.ajdt.internal.ui.editor;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.aspectj.asm.IProgramElement;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.builder.AJBuildJob;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.ajdt.core.model.AJRelationshipType;
import org.eclipse.ajdt.internal.core.ajde.CoreCompilerConfiguration;
import org.eclipse.ajdt.internal.core.ajde.FileURICache;
import org.eclipse.ajdt.internal.ui.ajde.AJDTErrorHandler;
import org.eclipse.ajdt.internal.ui.markers.AJMarkersDialog;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.internal.utils.AJDTUtils;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.DecoratingJavaLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.ImageImageDescriptor;
import org.eclipse.jdt.ui.ISharedImages;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.source.IVerticalRulerInfo;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.AbstractRulerActionDelegate;
import org.eclipse.ui.texteditor.IEditorStatusLine;
import org.eclipse.ui.texteditor.ITextEditor;

public class AdviceActionDelegate extends AbstractRulerActionDelegate {


	IEditorPart editor;
	IVerticalRulerInfo rulerInfo;

	public AdviceActionDelegate() {
		editor    = null;
		rulerInfo = null;
	}


	/**
	 * @see IEditorActionDelegate#setActiveEditor(bIAction, IEditorPart)
	 */
	public void setActiveEditor(IAction callerAction,IEditorPart targetEditor) {
		// We only care about compilation unit and class file editors
		if (targetEditor != null) {
			String id = targetEditor.getSite().getId();

			if (!id.equals(JavaUI.ID_CU_EDITOR) && !id.equals(JavaUI.ID_CF_EDITOR)
				&& !id.equals(AspectJEditor.ASPECTJ_EDITOR_ID)) // The AspectJ editor
				targetEditor = null;
		}
		editor = targetEditor; // Remember the editor
		super.setActiveEditor(callerAction, targetEditor);
	}



	/**
	 * @see AbstractRulerActionDelegate#createAction()
	 */
	protected IAction createAction(ITextEditor editor,IVerticalRulerInfo rulerInfo) {
		this.rulerInfo = rulerInfo;
		return null;
	}


    /**
     * Called to see if this action delegate wants to influence the menu before it
     * is displayed - in the case of AJDT we have to check if there is an advice
     * marker in affect on the line in which the user has right clicked.  If there
     * is then we add an 'Advised By' line to the context submenu that
     * will appear.  By going through the submenu and selecting advice, we force
     * the editor to jump to a particular file and location - selecting the
     * advice that is in effect.
     */
	public void menuAboutToShow(IMenuManager manager) {

		try {
			// Work out which file is currently being edited
		    IEditorInput input = this.editor.getEditorInput();
		    if (! (input instanceof IFileEditorInput)) {
		        return;
		    }
			IFileEditorInput ifep =	(IFileEditorInput) input;
			IFile ifile = ifep.getFile();

			// Which line was right clicked in the ruler?
			int linenumber = rulerInfo.getLineOfLastMouseButtonActivity();
			Integer clickedLine = linenumber + 1;
			ICompilationUnit cu;
			if (ifile.getFileExtension().equals("aj")) { //$NON-NLS-1$
				cu = AJCompilationUnitManager.INSTANCE.getAJCompilationUnit(ifile);
			} else {
				cu = (ICompilationUnit)JavaCore.create(ifile);
			}

			if (cu == null) {
			    // happens if the underlying resource has been deleted
			    return;
			}
			AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(cu);

			boolean addedMenu = false;
			if (model.hasModel()) {
	            List<IJavaElement> javaElementsForLine = model
                    .getJavaElementsForLine(cu, clickedLine);

	            addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.ADVISES, model);
    			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.ADVISED_BY, model);
    			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.ANNOTATES, model);
    			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.ANNOTATED_BY, model);
    			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.DECLARED_ON, model);
    			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.ASPECT_DECLARATIONS, model);
    			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.SOFTENS, model);
                addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.SOFTENED_BY, model);
                addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.MATCHED_BY, model);
    			// note---don't do matches declare because these are provided by error and warning markers
			} else {
		        IProject project = cu.getJavaProject().getProject();
			    createBuildMenu(manager, project);
			}
			if(addedMenu) {
			    createAJToolsMenu(manager);
			}

			// This next part of the method is nasty.  For one thing, should be using
			// handle identifiers, not source locations.
			// Go through the problem markers
			IMarker[] probMarkers = ifile.findMarkers(IMarker.MARKER, true, 2);
            MenuManager problemSubmenu = null;
            boolean problemSubmenuInitialized = false;
            if (probMarkers != null && probMarkers.length != 0) {
              for (IMarker m : probMarkers) {
                Object markerLine = m.getAttribute(IMarker.LINE_NUMBER);
                if (markerLine != null && markerLine.equals(clickedLine)) {
                  int relCount = 0;
                  String loc = (String) m
                    .getAttribute(AspectJUIPlugin.RELATED_LOCATIONS_ATTRIBUTE_PREFIX
                                  + (relCount++));
                  if (loc != null) {
                    IProject project = ifile.getProject();
                    // Build a new action for our menu for each extra
                    // source location
                    while (loc != null) {
                      // decode the source location
                      String[] s = loc.split(":::"); //$NON-NLS-1$
                      String resName = s[0].substring(s[0]
                                                        .lastIndexOf(File.separator) + 1);
                      String textLabel = NLS.bind(UIMessages.EditorRulerContextMenu_relatedLocation_message,
                        new String[] { resName, s[1] });
                      RelatedLocationMenuAction ama = new RelatedLocationMenuAction(
                        textLabel, loc, project);
                      // Initialize the submenu if we haven't done it
                      // already.
                      if (!problemSubmenuInitialized) {
                        problemSubmenu = new MenuManager(UIMessages.EditorRulerContextMenu_relatedLocations);
                        manager.add(problemSubmenu);
                        problemSubmenuInitialized = true;
                      }

                      // Add our new action to the submenu
                      problemSubmenu.add(ama);

                      loc = (String) m
                        .getAttribute(AspectJUIPlugin.RELATED_LOCATIONS_ATTRIBUTE_PREFIX
                                      + (relCount++));
                    }
                  }
                }
              }
            }
        } catch (CoreException ce) {
        	AJDTErrorHandler.handleAJDTError(
                            UIMessages.AdviceActionDelegate_exception_adding_advice_to_context_menu,
                            ce);
        }
    }


	/*
	 * called when the project model has not been initialized
	 */
    private void createBuildMenu(IMenuManager manager, final IProject project) {
        // cannot find any references because project has not been built
        MenuManager emptyAJrefs = new MenuManager("AspectJ References");
        emptyAJrefs.add(new Action() {
            public String getText() {
                return "Build project to generate references...";
            }
            public void run() {
                // force a full build
                AJBuildJob job = new AJBuildJob(project, IncrementalProjectBuilder.FULL_BUILD);
                job.schedule();
            }
        });
        manager.add(emptyAJrefs);
    }

	private void createAJToolsMenu(IMenuManager manager) {
		MenuManager menu = new MenuManager(UIMessages.AdviceActionDelegate_ajtools);
		manager.add(menu);
		menu.add(new Action() {
			public String getText() {
				return UIMessages.AdviceActionDelegate_configure_markers;
			}

			public void run() {
				IResource resource = ((IFileEditorInput)editor.getEditorInput()).getFile();
				if(resource != null) {
					Shell shell = AspectJUIPlugin.getDefault().getActiveWorkbenchWindow().getShell();
					IProject project = resource.getProject();
					AJMarkersDialog dialog = new AJMarkersDialog(shell, project);
					dialog.open();
				}
			}
		});
	}

	/**
	 *
	 * @param javaElements
	 * @param manager
	 * @param addedSeparator
	 * @param relationshipType
	 * @return
	 */
	private boolean createMenuForRelationshipType(List<IJavaElement> javaElements, IMenuManager manager, boolean addedMenu, AJRelationshipType relationshipType, AJProjectModelFacade model) {
		boolean menuInitialized = false;
		MenuManager menu = null;
		for (IJavaElement element : javaElements) {
			List<IJavaElement> relationships = model
                    .getRelationshipsForElement(element, relationshipType);
			if(relationships != null) {
				addedMenu = true;
				for (IJavaElement el : relationships) {
					if(!menuInitialized) {
						menu = new MenuManager(relationshipType.getMenuName());
						manager.add(menu);
						menuInitialized = true;
					}
					// link might be in a different project
					String linkName = model.getJavaElementLinkName(el);
					StringBuilder extra = new StringBuilder();
					// might be a declare parents instantiated in a concrete aspect
					if (relationshipType == AJRelationshipManager.ASPECT_DECLARATIONS &&
					        el instanceof AspectElement && element instanceof IType) {
					    IProgramElement ipe = model.javaElementToProgramElement(el);
					    Map<String, List<String>> parentsMap = ipe.getDeclareParentsMap();
					    if (parentsMap != null) {
					        List<String> parents = parentsMap.get(((IType) element).getFullyQualifiedName());
					        if (parents != null && parents.size() > 0) {
					            extra = new StringBuilder("declare parents: ");
					            for (String parent : parents) {
                                    extra.append(parent);
                                    extra.append(", ");
                                }
					            extra.append("instantiated in ");
					        }
					    }
					}
					linkName = extra + linkName;

					menu.add(new MenuAction(el, linkName));
				}
			}
		}
		return addedMenu;
	}


	/**
	 * Inner class that represent an entry on the submenu for "Advised By >"
	 * or "Aspect Declarations >" or "Go To Related Location >"
	 * - each Menu Action is a piece of advice or an ITD in affect on the current line.
	 */
	private static class MenuAction extends Action {
	    private static final ILabelProvider labelProvider =
			new DecoratingJavaLabelProvider(new AppearanceAwareLabelProvider());

	    private final IJavaElement jumpLocation;

        /**
		 * @param el
		 */
		public MenuAction(IJavaElement el, String linkName) {
			super(linkName);
			Image image = labelProvider.getImage(el);
			if (image != null) {
				setImageDescriptor(new ImageImageDescriptor(image));
			}
			jumpLocation = el;
		}

        public void run() {
            try {
                JavaUI.openInEditor(jumpLocation);
            } catch (PartInitException | JavaModelException ignored) {
            }
        }
	}



	/**
	 * Inner classes that represent an entry on the submenu for "Advised By >"
	 * or "Aspect Declarations >" or "Go To Related Location >"
	 * - each AJDTMenuAction is a piece of advice or an ITD in affect on the current line.
	 * When each AJDTMenuAction is created, it is given a name (the advice in affect)
	 * and a marker.  This is the advice marker attached to the line.  Both advice markers
	 * and ITD markers are like normal markers but have an extra attribute:
	 * AspectJPlugin.SOURCE_LOCATION_ATTRIBUTE
	 * This attribute has the format FFFF:::NNNN:::NNNN:::NNNN
	 * - The FFFF is the file which contains the source of the advice or ITD in affect
	 * - The other three NNNN fields are integers indicating (in order) the
	 *   start line number of the advice in that file, the end line number of the
	 *   advice in that file and the column number for the advice.
	 *
	 * I had to code it this way because you can't set arbitrary object values for
	 * attributes.  Using the value of this attribute, the run() method for the
	 * action can create a jump marker that points to the real advice definition
	 * and jump to it.
	 */
	abstract class BaseAJDTMenuAction extends Action {

		private final IProject project;

        BaseAJDTMenuAction(String s, IProject project) {
            super(s);
            this.project = project;
        }

        abstract String getJumpLocation();

        public void run() {

			// Fetch the real advice marker from the marker that is attached to
			// affected sites.

			// Take jumpLocation apart. It is initially:
			// FFFF:::NNNN:::NNNN:::NNNN
			String[] s = getJumpLocation().split(":::"); //$NON-NLS-1$
			final String filepath = s[0];
			final String linenumber = s[1];
			// System.err.println("FilePath=" + filepath);
			// System.err.println("linenum=" + linenumber);
	        FileURICache fileCache = ((CoreCompilerConfiguration) AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()).getFileCache();

			IResource r = fileCache.findResource(filepath,project);
			if (r == null) {
			    r = fileCache.findResource(filepath);
			}

			// 159867: not able to navigate to a binary aspect
			if (!r.exists()) {
			    revealBinaryAspect(filepath, linenumber);
			} else {
	            revealSourceAspect(linenumber, r);
			}
		}

        private void revealSourceAspect(final String linenumber, final IResource resource) {
            IMarker jumpMarker = null;
            if ((resource != null) && (resource.exists())) {
                try {
                    jumpMarker = resource.createMarker(IMarker.TEXT);
                    /*
                     * GOTCHA: If setting LINE_NUMBER for a marker, you *have*
                     * to call the version of setAttribute that takes an int and
                     * not the version that takes a string (even if your line
                     * number is in a string) - it won't give you an error but
                     * will *not* be interpreted correctly.
                     */
                    jumpMarker.setAttribute(IMarker.LINE_NUMBER, Integer.valueOf(linenumber).intValue());

                    try {
                        IDE.openEditor(AspectJUIPlugin.getDefault()
                                .getActiveWorkbenchWindow().getActivePage(),
                                jumpMarker, true);
                    } catch (CoreException e) {
                        AJDTErrorHandler.handleAJDTError(
                                UIMessages.AdviceActionDelegate_exception_jumping,
                                e);
                    }

                } catch (CoreException ce) {
                    AJDTErrorHandler.handleAJDTError(
                                    UIMessages.AdviceActionDelegate_unable_to_create_marker,
                                    ce);
                } finally {
                    if (jumpMarker != null) {
                        try {
                            jumpMarker.delete();
                        } catch (CoreException ce) {
                            AJDTErrorHandler.handleAJDTError(
                                    UIMessages.AdviceActionDelegate_unable_to_create_marker,
                                    ce);
                        }
                    }
                }

            } else {
                report(UIMessages.AdviceActionDelegate_resource_not_found);
            }
        }

        private void revealBinaryAspect(final String filepath,
                final String linenumber) {
            // 167121: might be a binary file in a directory, which uses ! as a separator
            //  - see org.aspectj.weaver.ShadowMunger.getBinaryFile()
            String qualifiedName = AJDTUtils.extractQualifiedName(filepath);
            IJavaProject javaProject = JavaCore.create(project);
            if (javaProject != null) {
                try {
                    IType type = javaProject.findType(qualifiedName);
                    IEditorPart part= EditorUtility.openInEditor(type, true);
                    if (part instanceof ITextEditor) {
                        ITextEditor editor = (ITextEditor) part;
                        IRegion region = getOffsetOfLine(linenumber, editor);
                        editor.selectAndReveal(region.getOffset(), region.getLength());
                        return;
                    }
                } catch (JavaModelException | PartInitException ignored) {
                }
            }
            report(UIMessages.AdviceActionDelegate_resource_not_found);
        }

        private IRegion getOffsetOfLine(String linenumber, ITextEditor editor) {
            IDocument doc = editor.getDocumentProvider().getDocument(editor.getEditorInput());
            try {
                return doc.getLineInformation(Integer.parseInt(linenumber)-1);
            } catch (NumberFormatException | BadLocationException ignored) {
            }
          return null;
        }
    }

	class RelatedLocationMenuAction extends BaseAJDTMenuAction {
	    private final String jumpLocation;

	    RelatedLocationMenuAction(String s, String jumpLocation, IProject project) {
	        super(s,project);
	        this.jumpLocation = jumpLocation;
	        setImageDescriptor(JavaUI.getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJS_CUNIT));
	    }

	       String getJumpLocation() {
	           return jumpLocation;
	       }
	}


	protected void report(final String message) {
		JDIDebugUIPlugin.getStandardDisplay().asyncExec(() -> {
      IEditorStatusLine fStatusLine = editor.getAdapter(IEditorStatusLine.class);
      if (fStatusLine != null) {
        fStatusLine.setMessage(true, message, null);
      }
      if (message != null
          && JDIDebugUIPlugin.getActiveWorkbenchShell() != null) {
        Display.getCurrent().beep();
      }
    });
	}

}
