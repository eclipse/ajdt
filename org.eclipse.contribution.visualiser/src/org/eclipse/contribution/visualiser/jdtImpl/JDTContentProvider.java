/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andy Clement - initial version
 *     Matt Chapman - add lookForData method to initialise the visualiser
 *******************************************************************************/
package org.eclipse.contribution.visualiser.jdtImpl;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.contribution.visualiser.VisualiserPlugin;
import org.eclipse.contribution.visualiser.core.ProviderManager;
import org.eclipse.contribution.visualiser.core.resources.VisualiserImages;
import org.eclipse.contribution.visualiser.interfaces.IContentProvider;
import org.eclipse.contribution.visualiser.interfaces.IGroup;
import org.eclipse.contribution.visualiser.interfaces.IMember;
import org.eclipse.contribution.visualiser.text.VisualiserMessages;
import org.eclipse.contribution.visualiser.utils.JDTUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;

public class JDTContentProvider implements IContentProvider, ISelectionListener {

	protected IJavaProject currentProject;
	private IResource currentlySelectedResource;
	protected IJavaElement currentlySelectedJE;


	/**
	 * Given a compilation unit, work out the number of lines in its source.
	 *
	 * @param element Compilation unit to investigate
	 * @return number of lines in the compilation unit
	 */
	protected int getLength(ICompilationUnit element) {
		String srccode;

		int lines = 1;
		try {
			srccode = element.getSource();

			while (srccode.contains("\n")) { //$NON-NLS-1$
				lines++;
				srccode = srccode.substring(srccode.indexOf("\n") + 1); //$NON-NLS-1$
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return lines;

	}


	/**
	 * Simple trace routine - we can turn trace on and off by commenting out the body.
	 */
	public void trace(String string) {
		System.err.println(string);
	}


	/**
	 * Keeps the currentResource and currentProject information up to date
	 * in this class, as this method is called whenever a user changes
	 * their selection in the workspace.
	 */
	public void selectionChanged(IWorkbenchPart workbenchPart, ISelection selection) {
		if(!(ProviderManager.getContentProvider().equals(this))){
			return;
		}

		boolean updateRequired = false;

		try {
			if (selection instanceof IStructuredSelection) {
				IStructuredSelection structuredSelection =
					(IStructuredSelection) selection;
				Object firstElement = structuredSelection.getFirstElement();

				if (firstElement != null) {
					if (firstElement instanceof IResource) {
						currentlySelectedResource = (IResource) firstElement;
					} else if (firstElement instanceof IJavaElement) {
						IJavaElement javaElement = (IJavaElement) firstElement;
						currentlySelectedJE = javaElement;
						if(javaElement.getUnderlyingResource() != null){
							if (!javaElement.getUnderlyingResource().equals(currentlySelectedResource)) updateRequired=true;
							currentlySelectedResource = javaElement.getUnderlyingResource();
							// Might be null!
						}
						if (javaElement.getJavaProject() != null) {
							setCurrentProject(javaElement.getJavaProject());
						}
					}
				}
			} else if (selection instanceof ITextSelection) {
			}
			if (updateRequired) {
				VisualiserPlugin.refresh();
			}
		} catch (JavaModelException jme) {
		    VisualiserPlugin.logException(jme);
		}
	}

	/**
	 * Get all members for the given group
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#getAllMembers(org.eclipse.contribution.visualiser.interfaces.IGroup)
	 */
	public List getAllMembers(IGroup group) {
		return group.getMembers();
	}


	/**
	 * Get all members
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#getAllMembers()
	 */
	public List getAllMembers() {

		List retval = null;

		// Depending on what is selected, the members are different things...

		// (1) Project is currently selected
		if (currentlySelectedResource instanceof IProject
			&& !(currentlySelectedJE instanceof IPackageFragment)) {
			retval = new ArrayList();
			if(getCurrentProject()!=null){
				List pkgfrags = getAllJDTGroups(getCurrentProject());
        for (Object pkgfrag : pkgfrags) {
          IGroup grp = (IGroup) pkgfrag;
          retval.addAll(grp.getMembers());
        }
			}
		} else if (currentlySelectedJE instanceof IPackageFragment) {
			retval = new ArrayList();
			JDTGroup group =
				getGroupForFragment((IPackageFragment) currentlySelectedJE);
			if(group != null) {
				retval.addAll(group.getMembers());
			}

		} else {
			retval = new ArrayList();
			List pkgfrags = getAllJDTGroups(getCurrentProject());
      for (Object pkgfrag : pkgfrags) {
        IGroup grp = (IGroup) pkgfrag;
        List mems = grp.getMembers();
        for (Object mem : mems) {
          JDTMember element = (JDTMember) mem;
          if (element.getResource().equals(currentlySelectedJE)) {
            retval.add(element);
            break;
          }

        }
      }
		}
		return retval;
	}


	/**
	 * Initialise
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#initialise()
	 */
	public void initialise() {
		if (VisualiserPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow() != null) {
			VisualiserPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow()
				.getSelectionService().addSelectionListener(this);
		}
	}

	/**
	 * Attempts to find some data to display by looking for selections in each
	 * of the packages view, projects view, and package explorer (in turn). The
	 * first selection found is used to refresh the visualiser. It is called by
	 * the visualiser if it receives a paint request, but hasn't been given any
	 * data. Selections are generally preserved by Eclipse across sessions, so
	 * this should result in the visualisation from a previous session being
	 * restored. This method should not be used when the provider is
	 * initialised, because that may occur before the other views have been
	 * created.
	 *
	 */
	public void lookForData() {
		IWorkbenchWindow iww = VisualiserPlugin.getActiveWorkbenchWindow();
		if (iww != null) {
			IWorkbenchPage iwp = iww.getActivePage();
			if (iwp != null) {
				String[] views = new String[] { JavaUI.ID_PACKAGES_VIEW,
						JavaUI.ID_PROJECTS_VIEW, JavaUI.ID_PACKAGES };
        for (String view : views) {
          IViewPart ivp = iwp.findView(view);
          if (ivp != null) {
            ISelectionProvider isp = ivp.getViewSite()
              .getSelectionProvider();
            if (isp != null) {
              ISelection is = isp.getSelection();
              if (is instanceof IStructuredSelection) {
                IStructuredSelection ss = (IStructuredSelection) is;
                Object o = ss.getFirstElement();
                if ((o != null) && (o instanceof IJavaElement)) {
                  IJavaElement je = (IJavaElement) o;
                  currentlySelectedJE = je;
                  if (je.getJavaProject() != null) {
                    setCurrentProject(je.getJavaProject());
                  }
                  VisualiserPlugin.refresh();
                  return;
                }
              }
            }
          }
        }
			}
		}
	}

	/**
	 * Process a mouse click on a member
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#processMouseclick(IMember, boolean, int)
	 */
	public boolean processMouseclick(IMember member, boolean markupWasClicked, int buttonClicked) {

		if(buttonClicked != 1){
			return true;
		}
		if(markupWasClicked) {
			return false;
		}
		if(member instanceof JDTMember) {
			IJavaElement javaElement = ((JDTMember)member).getResource();
			if(javaElement != null) {
				JDTUtils.openInEditor(javaElement.getResource(), JDTUtils.getClassDeclLineNum(javaElement));
			}
		}

		return false;
	}


	/**
	 * Get all groups
	 */
	public List getAllGroups() {

		List groupList = null;
		// Depending on what is selected, the groups are different things...

		// (1) Project is currently selected
		if (currentlySelectedResource instanceof IProject
		    && !(currentlySelectedJE instanceof IPackageFragment)) {
			groupList = getAllJDTGroups(getCurrentProject());
		} else if (currentlySelectedJE instanceof IPackageFragment) {
			groupList = new ArrayList();
			JDTGroup oneGroup =
				getGroupForFragment((IPackageFragment) currentlySelectedJE);
			groupList.add(oneGroup);
		} else {
		    groupList = new ArrayList();
		    List pkgfrags = getAllJDTGroups(getCurrentProject());
      for (Object pkgfrag : pkgfrags) {
        IGroup grp = (IGroup) pkgfrag;
        List mems = grp.getMembers();
        for (Object mem : mems) {
          JDTMember element = (JDTMember) mem;
          if (element.getResource().equals(currentlySelectedJE)) {
            groupList.add(element.getContainingGroup());
            break;
          }
        }
      }
	    }
		return groupList;
	}


	/**
	 * Get a JDTGroup to represent the give IPackageFragment (Java package)
	 * @param ipf
	 * @return the JDTGroup created
	 */
	public JDTGroup getGroupForFragment(IPackageFragment ipf) {
		JDTGroup jdtg = null;
		try {
			if (ipf.getKind() != IPackageFragmentRoot.K_BINARY) {
				if (ipf.isDefaultPackage()) {
					if (ipf.containsJavaResources()) {
						jdtg = new JDTGroup("[" + VisualiserMessages.Default + "]"); //$NON-NLS-1$ //$NON-NLS-2$
					}
				} else {
					jdtg = new JDTGroup(ipf.getElementName()/*resource.getName()*/);
				}
				if (jdtg!=null) {

					List members = getMembersForPackage(ipf);
					if(members.size() == 0){
						return null;
					}
					jdtg.addMembers(members);
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return jdtg;
	}


	/**
	 * Get all package fragments for the given Java project
	 * @param JP
	 * @return List of JDTGroups
	 */
	public List getAllJDTGroups(IJavaProject JP) {
		List returningPackages = new LinkedList();
		if(JP != null) {
			try {
				IPackageFragment[] fragments = JP.getPackageFragments();
        for (IPackageFragment fragment : fragments) {
          JDTGroup group = getGroupForFragment(fragment);
          if (group != null) {
            returningPackages.add(group);
          }
        }
			} catch (JavaModelException e) {
				e.printStackTrace();
			}
		}
		return returningPackages;
	}


	/**
	 * Get all JDT members for the given IPackageFragment (Java package)
	 * @param PF
	 * @return List of JDTMembers
	 */
	public List getMembersForPackage(IPackageFragment PF) {
		List returningClasses = new LinkedList();
		try {
			if (containsUsefulStuff(PF)) {
				IJavaElement[] ijes = PF.getChildren();
        for (IJavaElement ije : ijes) {
          if (ije.getElementType() == IJavaElement.COMPILATION_UNIT) {
            String memberName = ije.getElementName();
            if (memberName.endsWith(".java")) { //$NON-NLS-1$
              memberName = memberName.substring(0, memberName.length() - 5);
            }
            JDTMember member = new JDTMember(memberName, ije);
            member.setSize(getLength((ICompilationUnit) ije));
            returningClasses.add(member);
          }
        }
			}
		} catch (JavaModelException jme) {
			System.err.println(jme);
		}
		return returningClasses;
	}


	/**
	 * Returns true if this package fragment has Java classes in it.
	 * @param fragment
	 * @return true if the package fragment contains Java classes
	 */
	protected boolean containsUsefulStuff(IPackageFragment fragment) {
		try {
			if (fragment.getKind() == IPackageFragment.PACKAGE_FRAGMENT_ROOT) {
				IPackageFragmentRoot ipfr = (IPackageFragmentRoot) fragment;
				if (ipfr.getKind() == IPackageFragmentRoot.K_BINARY) {
					return false;
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return true;
	}


	/**
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#getMemberViewIcon()
	 */
	public ImageDescriptor getMemberViewIcon() {
		return VisualiserImages.CLASS_VIEW;
	}


	/**
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#getGroupViewIcon()
	 */
	public ImageDescriptor getGroupViewIcon() {
		return VisualiserImages.PACKAGE_VIEW;
	}


	/**
	 * Set the current project
	 * @param currentProject - the current IJavaProject
	 */
	protected void setCurrentProject(IJavaProject currentProject) {
		this.currentProject = currentProject;
	}


	/**
	 * Get the current project
	 * @return current IJavaProject
	 */
	public IJavaProject getCurrentProject() {
		return currentProject;
	}


	/**
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#activate()
	 */
	public void activate() {
	}


	/**
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#deactivate()
	 */
	public void deactivate() {
	}

}
