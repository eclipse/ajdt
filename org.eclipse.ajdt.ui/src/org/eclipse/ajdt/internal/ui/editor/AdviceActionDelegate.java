/**********************************************************************
Copyright (c) 2000, 2005 IBM Corp. and others.
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.core.model.AJModel;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.ajdt.core.model.AJRelationshipType;
import org.eclipse.ajdt.internal.ui.ajde.AJDTErrorHandler;
import org.eclipse.ajdt.internal.ui.markers.AJMarkersDialog;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.internal.utils.AJDTUtils;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.DecoratingJavaLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.ImageImageDescriptor;
import org.eclipse.jdt.ui.ISharedImages;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.text.source.IVerticalRulerInfo;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
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
			IFileEditorInput ifep =	(IFileEditorInput) this.editor.getEditorInput();
			IFile ifile = ifep.getFile();
			
			// Which line was right clicked in the ruler?
			int linenumber = rulerInfo.getLineOfLastMouseButtonActivity();
			Integer clickedLine = new Integer(linenumber+1);
			ICompilationUnit cu;
			if (ifile.getFileExtension().equals("aj")) { //$NON-NLS-1$
				cu = AJCompilationUnitManager.INSTANCE.getAJCompilationUnit(ifile);
			} else {
				cu = (ICompilationUnit)JavaCore.create(ifile);
			}
			
			List javaElementsForLine = getJavaElementsForLine(cu, clickedLine.intValue());
			boolean addedMenu = false;
			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.ADVISES);
			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.ADVISED_BY);
			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.ANNOTATES);
			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.ANNOTATED_BY);
			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.DECLARED_ON);
			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.ASPECT_DECLARATIONS);
			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.SOFTENS);
			addedMenu = createMenuForRelationshipType(javaElementsForLine, manager, addedMenu, AJRelationshipManager.SOFTENED_BY);
			if(addedMenu) {
				createAJToolsMenu(manager);
			}
			
			// Go through the problem markers 
			IMarker probMarkers[] = ifile.findMarkers(IMarker.PROBLEM, true, 2);
            MenuManager problemSubmenu = null;
            boolean problemSubmenuInitialized = false;
            if (probMarkers != null && probMarkers.length != 0) {
                 for (int j = 0; j < probMarkers.length; j++) {
                    IMarker m = probMarkers[j];
                    if (m.getAttribute(IMarker.LINE_NUMBER).equals(clickedLine)) {
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
	
	
	private void createAJToolsMenu(IMenuManager manager) {
		MenuManager menu = new MenuManager(UIMessages.AdviceActionDelegate_ajtools);
		manager.add(menu);			
		menu.add(new Action(){
			
			public String getText() {
				return UIMessages.AdviceActionDelegate_configure_markers;
			}
			
			public void run() {
				IResource resource = (IResource) ((IFileEditorInput)editor.getEditorInput()).getFile();
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
	private boolean createMenuForRelationshipType(List javaElements, IMenuManager manager, boolean addedMenu, AJRelationshipType relationshipType) {
		boolean menuInitialized = false;
		MenuManager menu = null;
		for (Iterator iter = javaElements.iterator(); iter.hasNext();) {
			IJavaElement element = (IJavaElement) iter.next();
		
			List relationships = AJModel.getInstance().getRelatedElements(relationshipType, element);
			if(relationships != null) {
				addedMenu = true;
				for (Iterator iterator = relationships.iterator(); iterator
						.hasNext();) {
					IJavaElement el = (IJavaElement) iterator.next();
					if(!menuInitialized) {
						menu = new MenuManager(relationshipType.getMenuName());
						manager.add(menu);			
						menuInitialized = true; 
					}
					menu.add(new MenuAction(el));
				}
			}
		}		
		return addedMenu;
	}


	/**
	 * @param cu
	 * @param clickedLine
	 * @return
	 */
	private List getJavaElementsForLine(IJavaElement je, int clickedLine) {
		AJModel model = AJModel.getInstance();
		List toReturn = new ArrayList();
		List extraChildren = model.getExtraChildren(je);
		if(extraChildren != null) {
			for (Iterator iter = extraChildren.iterator(); iter.hasNext();) {
				IJavaElement element = (IJavaElement) iter.next();
				if(model.getJavaElementLineNumber(element) == clickedLine) {
					toReturn.add(element);
				}
				toReturn.addAll(getJavaElementsForLine(element, clickedLine));
			}
		}
		if(je instanceof ICompilationUnit) {
			try {
				IJavaElement[] children = ((ICompilationUnit)je).getChildren();
				for (int i = 0; i < children.length; i++) {
					IJavaElement element = children[i];
					if(model.getJavaElementLineNumber(element) == clickedLine) {
						toReturn.add(element);
					}
					toReturn.addAll(getJavaElementsForLine(element, clickedLine));
				}
			} catch (JavaModelException e) {
			}
		} else if (je instanceof IType) {
			try {
				IJavaElement[] children = ((IType)je).getChildren();
				for (int i = 0; i < children.length; i++) {
					IJavaElement element = children[i];
					if(model.getJavaElementLineNumber(element) == clickedLine) {
						toReturn.add(element);
					}
					toReturn.addAll(getJavaElementsForLine(element, clickedLine));
				}
			} catch (JavaModelException e) {
			}
		} else if (je instanceof IParent) {
			try {
				IJavaElement[] children = ((IParent) je).getChildren();
				for (int i = 0; i < children.length; i++) {
					IJavaElement element = children[i];
					if (model.getJavaElementLineNumber(element) == clickedLine) {
						toReturn.add(element);
					}
					toReturn
							.addAll(getJavaElementsForLine(element, clickedLine));
				}
			} catch (JavaModelException e) {
			}
		}
		return toReturn;
	}

	/**
	 * Inner class that represent an entry on the submenu for "Advised By >" 
	 * or "Aspect Declarations >" or "Go To Related Location >"
	 * - each Menu Action is a piece of advice or an ITD in affect on the current line.
	 */
	private static class MenuAction extends Action {
	    private static ILabelProvider labelProvider =
			new DecoratingJavaLabelProvider(new AppearanceAwareLabelProvider());

	    private IJavaElement jumpLocation;
		
        /**
		 * @param el
		 */
		public MenuAction(IJavaElement el) {
			super (AJModel.getInstance().getJavaElementLinkName(el));
			Image image = labelProvider.getImage(el);
			if (image != null) {
				setImageDescriptor(new ImageImageDescriptor(image));
			}
			jumpLocation = el;
		}
		
        public void run() {
        	IJavaElement parentCU = jumpLocation.getAncestor(IJavaElement.COMPILATION_UNIT);
        	if(parentCU != null) {
	        	IResource res = parentCU.getResource();
	        	try {
		        	IMarker marker = res.createMarker(IMarker.MARKER);
		        	int lineNumber = AJModel.getInstance().getJavaElementLineNumber(jumpLocation);
					if(lineNumber>=0){
						marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
					}
		        	IDE.openEditor(AspectJUIPlugin.getDefault().getActiveWorkbenchWindow().getActivePage(),
		        			marker);
	        	} catch (CoreException ce){}
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
		
		private IProject project;
		
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

			IResource r = AJDTUtils.findResource(filepath);
			if (r == null) {
				r = AJDTUtils.findResource(filepath,project);
			}
			final IResource ir = r;

			IMarker jumpMarker = null;

			if ((ir != null) && (ir.exists())) {
				try {
					jumpMarker = ir.createMarker(IMarker.TEXT);
					/*
					 * GOTCHA: If setting LINE_NUMBER for a marker, you *have*
					 * to call the version of setAttribute that takes an int and
					 * not the version that takes a string (even if your line
					 * number is in a string) - it won't give you an error but
					 * will *not* be interpreted correctly.
					 */
					jumpMarker.setAttribute(IMarker.LINE_NUMBER, new Integer(
							linenumber).intValue());

				} catch (CoreException ce) {
					AJDTErrorHandler.handleAJDTError(
									UIMessages.AdviceActionDelegate_unable_to_create_marker,
									ce);
				}

				try {
					IDE.openEditor(AspectJUIPlugin.getDefault()
							.getActiveWorkbenchWindow().getActivePage(),
							jumpMarker, true);
				} catch (CoreException e) {
					AJDTErrorHandler.handleAJDTError(
							UIMessages.AdviceActionDelegate_exception_jumping,
							e);

				}
			} else {
				report(UIMessages.AdviceActionDelegate_resource_not_found);
			}
		}
    }
	
	class RelatedLocationMenuAction extends BaseAJDTMenuAction {
	    private String jumpLocation;
	    
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
		JDIDebugUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
			public void run() {
				IEditorStatusLine fStatusLine = (IEditorStatusLine) editor.getAdapter(IEditorStatusLine.class);
				if (fStatusLine != null) {
					fStatusLine.setMessage(true, message, null);
				}
				if (message != null
						&& JDIDebugUIPlugin.getActiveWorkbenchShell() != null) {
					Display.getCurrent().beep();
				}
			}
		});
	}

}
