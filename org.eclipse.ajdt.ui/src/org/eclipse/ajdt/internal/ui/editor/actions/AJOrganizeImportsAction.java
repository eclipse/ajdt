/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matt Chapman - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.editor.actions;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.ajdt.internal.ui.editor.actions.AJOrganizeImportsOperation.IChooseImportQuery;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.QualifiedTypeNameHistory;
import org.eclipse.jdt.internal.corext.util.ValidateEditException;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.browsing.LogicalPackage;
import org.eclipse.jdt.internal.ui.dialogs.MultiElementListSelectionDialog;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.TypeNameMatchLabelProvider;
import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IEditingSupport;
import org.eclipse.jface.text.IEditingSupportRegistry;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IEditorActionBarContributor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.EditorActionBarContributor;
import org.eclipse.ui.progress.IProgressService;

import com.ibm.icu.text.Collator;


/**
 * Copied from OrganizeImportsAction - all occurrences of OrganizeImportsOperation
 * replaced with AJOrganizeImportsOperation
 *
 */
public class AJOrganizeImportsAction extends SelectionDispatchAction {

	private static final OrganizeImportComparator ORGANIZE_IMPORT_COMPARATOR= new OrganizeImportComparator();

	private JavaEditor fEditor;
	/** <code>true</code> if the query dialog is showing. */
	private boolean fIsQueryShowing= false;

	/* (non-Javadoc)
	 * Class implements IObjectActionDelegate
	 */
	public static class ObjectDelegate implements IObjectActionDelegate {
		private AJOrganizeImportsAction fAction;
		public void setActivePart(IAction action, IWorkbenchPart targetPart) {
			fAction= new AJOrganizeImportsAction(targetPart.getSite());
		}
		public void run(IAction action) {
			fAction.run();
		}
		public void selectionChanged(IAction action, ISelection selection) {
			if (fAction == null)
				action.setEnabled(false);
		}
	}

	/**
	 * {@link QualifiedTypeNameHistory} extends {@code History<String, String>}, see below.
   * Therefore, we implement {@code Comparator<String>} here.
	 */
	private static final class OrganizeImportComparator
		implements Comparator<String>, Serializable
	{
		private static final long serialVersionUID = -4396300340833288667L;

		@Override
		public int compare(String o1, String o2) {
			if (o1.equals(o2))
				return 0;
			QualifiedTypeNameHistory history = QualifiedTypeNameHistory.getDefault();
			int pos1 = history.getPosition(o1);
			int pos2 = history.getPosition(o2);
			if (pos1 == pos2)
				return Collator.getInstance().compare(o1, o2);
			return pos1 > pos2 ? -1 : 1;
		}
	}

	/**
	 * Creates a new <code>AJOrganizeImportsAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public AJOrganizeImportsAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.OrganizeImportsAction_label);
		setToolTipText(ActionMessages.OrganizeImportsAction_tooltip);
		setDescription(ActionMessages.OrganizeImportsAction_description);

		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.ORGANIZE_IMPORTS_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 */
	public AJOrganizeImportsAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(getCompilationUnit(fEditor) != null);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(getCompilationUnit(fEditor) != null);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		setEnabled(isEnabled(selection));
	}

	private ICompilationUnit[] getCompilationUnits(IStructuredSelection selection) {
		Set<ICompilationUnit> result = new HashSet<>();
		Object[] selectedElements = selection.toArray();
		for (Object selectedElement : selectedElements) {
			try {
				if (selectedElement instanceof IJavaElement) {
					IJavaElement javaElement = (IJavaElement) selectedElement;
					if (javaElement.exists()) {
						switch (javaElement.getElementType()) {
							case IJavaElement.TYPE:
								if (javaElement.getParent().getElementType() == IJavaElement.COMPILATION_UNIT)
									result.add((ICompilationUnit) javaElement.getParent());
								break;
							case IJavaElement.COMPILATION_UNIT:
								result.add((ICompilationUnit) javaElement);
								break;
							case IJavaElement.IMPORT_CONTAINER:
								result.add((ICompilationUnit) javaElement.getParent());
								break;
							case IJavaElement.PACKAGE_FRAGMENT:
								collectCompilationUnits((IPackageFragment) javaElement, result);
								break;
							case IJavaElement.PACKAGE_FRAGMENT_ROOT:
								collectCompilationUnits((IPackageFragmentRoot) javaElement, result);
								break;
							case IJavaElement.JAVA_PROJECT:
								IPackageFragmentRoot[] roots = ((IJavaProject) javaElement).getPackageFragmentRoots();
								for (IPackageFragmentRoot root : roots)
									collectCompilationUnits(root, result);
								break;
						}
					}
				}
				else if (selectedElement instanceof LogicalPackage) {
					IPackageFragment[] packageFragments = ((LogicalPackage) selectedElement).getFragments();
					for (IPackageFragment pack : packageFragments) {
						if (pack.exists())
							collectCompilationUnits(pack, result);
					}
				}
			}
			catch (JavaModelException e) {
				if (JavaModelUtil.isExceptionToBeLogged(e))
					JavaPlugin.log(e);
			}
		}
		return result.toArray(new ICompilationUnit[0]);
	}

	private void collectCompilationUnits(IPackageFragment pack, Collection<ICompilationUnit> result) throws JavaModelException {
		result.addAll(Arrays.asList(pack.getCompilationUnits()));
	}

	private void collectCompilationUnits(IPackageFragmentRoot root, Collection<ICompilationUnit> result) throws JavaModelException {
		if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
			IJavaElement[] children = root.getChildren();
			for (IJavaElement child : children)
				collectCompilationUnits((IPackageFragment) child, result);
		}
	}

	private boolean isEnabled(IStructuredSelection selection) {
    for (Object selectedElement : selection.toArray()) {
			try {
				if (selectedElement instanceof IJavaElement) {
					IJavaElement javaElement = (IJavaElement) selectedElement;
					if (javaElement.exists()) {
						switch (javaElement.getElementType()) {

							case IJavaElement.TYPE:
								// for browsing perspective
								return javaElement.getParent().getElementType() == IJavaElement.COMPILATION_UNIT;

							case IJavaElement.COMPILATION_UNIT:
							case IJavaElement.IMPORT_CONTAINER:
							case IJavaElement.JAVA_PROJECT: // https://bugs.eclipse.org/bugs/show_bug.cgi?id=65638
								return true;

							case IJavaElement.PACKAGE_FRAGMENT:
							case IJavaElement.PACKAGE_FRAGMENT_ROOT:
								IPackageFragmentRoot root =
									(IPackageFragmentRoot) javaElement.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
								return root.getKind() == IPackageFragmentRoot.K_SOURCE;
						}
					}
				}
				else if (selectedElement instanceof LogicalPackage)
					return true;
			}
			catch (JavaModelException e) {
				if (!e.isDoesNotExist())
					JavaPlugin.log(e);
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(ITextSelection selection) {
		ICompilationUnit cu= getCompilationUnit(fEditor);
		if (cu != null) {
			run(cu);
		}
	}

	private static ICompilationUnit getCompilationUnit(JavaEditor editor) {
		IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
    return manager.getWorkingCopy(editor.getEditorInput());
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(IStructuredSelection selection) {
		ICompilationUnit[] cus= getCompilationUnits(selection);
		if (cus.length == 0)
			return;
		if (cus.length == 1) {
			run(cus[0]);
		} else {
			runOnMultiple(cus);
		}
	}

	/**
	 * Perform organize import on multiple compilation units. No editors are opened.
	 * @param cus The compilation units to run on
	 */
	public void runOnMultiple(final ICompilationUnit[] cus) {
		try {
			String message= "Problems while organizing imports on some compilation units. See 'Details' for more information.";
			final MultiStatus status= new MultiStatus(JavaUI.ID_PLUGIN, IStatus.OK, message, null);

			IProgressService progressService= PlatformUI.getWorkbench().getProgressService();
			progressService.run(true, true, new WorkbenchRunnableAdapter(monitor -> doRunOnMultiple(cus, status, monitor))); // workspace lock
			if (!status.isOK()) {
				String title= "Organize Imports";
				ErrorDialog.openError(getShell(), title, null, status);
			}
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), ActionMessages.OrganizeImportsAction_error_title, ActionMessages.OrganizeImportsAction_error_message);
		} catch (InterruptedException e) {
			// Canceled by user
		}

	}

	static final class OrganizeImportError extends RuntimeException {
		private static final long serialVersionUID= 1L;
	}

	private void doRunOnMultiple(ICompilationUnit[] cus, MultiStatus status, IProgressMonitor monitor) throws OperationCanceledException {
		if (monitor == null) {
			monitor= new NullProgressMonitor();
		}
		monitor.setTaskName("Organizing imports...");

		monitor.beginTask("", cus.length); //$NON-NLS-1$
		try {
			IChooseImportQuery query= (openChoices, ranges) -> {
        throw new OrganizeImportError();
      };
			IJavaProject lastProject= null;

      for (ICompilationUnit cu : cus) {
        if (testOnBuildPath(cu, status)) {
          if (lastProject == null || !lastProject.equals(cu.getJavaProject())) {
            lastProject = cu.getJavaProject();
          }
          CodeGenerationSettings settings = JavaPreferencesSettings.getCodeGenerationSettings(lastProject);

          String cuLocation = cu.getPath().makeRelative().toString();

          monitor.subTask(cuLocation);

          try {
            boolean save = !cu.isWorkingCopy();
            if (!save) {
              ITextFileBuffer textFileBuffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(cu.getPath());
              save = textFileBuffer != null && !textFileBuffer.isDirty(); // save when not dirty
            }

            AJOrganizeImportsOperation op = new AJOrganizeImportsOperation(cu, null, settings.importIgnoreLowercase, save, true, query);
            runInSync(op, cuLocation, status, monitor);

            IProblem parseError = op.getParseError();
            if (parseError != null) {
              String message = Messages.format(ActionMessages.OrganizeImportsAction_multi_error_parse, cuLocation);
              status.add(new Status(IStatus.INFO, JavaUI.ID_PLUGIN, IStatus.ERROR, message, null));
            }
          }
          catch (CoreException e) {
            JavaPlugin.log(e);
            String message = Messages.format("{0}: Unexpected error. See log for details.", e.getStatus().getMessage());
            status.add(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.ERROR, message, null));
          }

          if (monitor.isCanceled()) {
            throw new OperationCanceledException();
          }
        }
      }
		} finally {
			monitor.done();
		}
	}

	private boolean testOnBuildPath(ICompilationUnit cu, MultiStatus status) {
		IJavaProject project= cu.getJavaProject();
		if (!project.isOnClasspath(cu)) {
			String cuLocation= cu.getPath().makeRelative().toString();
			String message= Messages.format("{0}: Compilation unit not on build path. No changes applied.", cuLocation);
			status.add(new Status(IStatus.INFO, JavaUI.ID_PLUGIN, IStatus.ERROR, message, null));
			return false;
		}
		return true;
	}


	private void runInSync(final AJOrganizeImportsOperation op, final String cuLocation, final MultiStatus status, final IProgressMonitor monitor) {
		Runnable runnable= () -> {
      try {
        op.run(new SubProgressMonitor(monitor, 1));
      } catch (ValidateEditException e) {
        status.add(e.getStatus());
      } catch (CoreException e) {
        JavaPlugin.log(e);
        String message= Messages.format("{0}: Unexpected error. See log for details.", e.getStatus().getMessage());
        status.add(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.ERROR, message, null));
      } catch (OrganizeImportError e) {
        String message= Messages.format(ActionMessages.OrganizeImportsAction_multi_error_unresolvable, cuLocation);
        status.add(new Status(IStatus.INFO, JavaUI.ID_PLUGIN, IStatus.ERROR, message, null));
      } catch (OperationCanceledException e) {
        // Canceled
        monitor.setCanceled(true);
      }
    };
		getShell().getDisplay().syncExec(runnable);
	}


	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 * @param cu The compilation unit to process
	 */
	public void run(ICompilationUnit cu) {
		if (!ElementValidator.check(cu, getShell(), ActionMessages.OrganizeImportsAction_error_title, fEditor != null))
			return;
		if (!ActionUtil.isProcessable(getShell(), cu))
			return;

		IEditingSupport helper= createViewerHelper();
		try {
			CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings(cu.getJavaProject());

			if (fEditor == null && EditorUtility.isOpenInEditor(cu) == null) {
				IEditorPart editor= EditorUtility.openInEditor(cu);
				if (editor instanceof JavaEditor) {
					fEditor= (JavaEditor) editor;
				}
			}
			CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_ACTIVE_ONLY, null);
			AJOrganizeImportsOperation op= new AJOrganizeImportsOperation(cu, astRoot, settings.importIgnoreLowercase, !cu.isWorkingCopy(), true, createChooseImportQuery());

			IRewriteTarget target= null;
			if (fEditor != null) {
				target= fEditor.getAdapter(IRewriteTarget.class);
				if (target != null) {
					target.beginCompoundChange();
				}
			}

			IProgressService progressService= PlatformUI.getWorkbench().getProgressService();
			IRunnableContext context= getSite().getWorkbenchWindow();
			if (context == null) {
				context= progressService;
			}
			try {
				registerHelper(helper);
				progressService.runInUI(context, new WorkbenchRunnableAdapter(op, op.getScheduleRule()), op.getScheduleRule());
				IProblem parseError= op.getParseError();
				if (parseError != null) {
					String message= Messages.format(ActionMessages.OrganizeImportsAction_single_error_parse, parseError.getMessage());
					MessageDialog.openInformation(getShell(), ActionMessages.OrganizeImportsAction_error_title, message);
					if (fEditor != null && parseError.getSourceStart() != -1) {
						fEditor.selectAndReveal(parseError.getSourceStart(), parseError.getSourceEnd() - parseError.getSourceStart() + 1);
					}
				} else {
					if (fEditor != null) {
						setStatusBarMessage(getOrganizeInfo(op));
					}
				}
			} catch (InvocationTargetException e) {
				ExceptionHandler.handle(e, getShell(), ActionMessages.OrganizeImportsAction_error_title, ActionMessages.OrganizeImportsAction_error_message);
			} catch (InterruptedException ignored) {
			} finally {
				deregisterHelper(helper);
				if (target != null) {
					target.endCompoundChange();
				}
			}
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), ActionMessages.OrganizeImportsAction_error_title, ActionMessages.OrganizeImportsAction_error_message);
		}
	}

	private String getOrganizeInfo(AJOrganizeImportsOperation op) {
		int nImportsAdded= op.getNumberOfImportsAdded();
		if (nImportsAdded >= 0) {
			return Messages.format(ActionMessages.OrganizeImportsAction_summary_added, String.valueOf(nImportsAdded));
		} else {
			return Messages.format(ActionMessages.OrganizeImportsAction_summary_removed, String.valueOf(-nImportsAdded));
		}
	}

	private IChooseImportQuery createChooseImportQuery() {
		return this::doChooseImports;
	}

	private TypeNameMatch[] doChooseImports(TypeNameMatch[][] openChoices, final ISourceRange[] ranges) {
		// remember selection
		ISelection sel= fEditor != null ? fEditor.getSelectionProvider().getSelection() : null;
		TypeNameMatch[] result= null;
		ILabelProvider labelProvider= new TypeNameMatchLabelProvider(TypeNameMatchLabelProvider.SHOW_FULLYQUALIFIED);

		MultiElementListSelectionDialog dialog= new MultiElementListSelectionDialog(getShell(), labelProvider) {
			@Override
			protected void handleSelectionChanged() {
				super.handleSelectionChanged();
				// show choices in editor
				doListSelectionChanged(getCurrentPage(), ranges);
			}
		};
		fIsQueryShowing= true;
		dialog.setTitle(ActionMessages.OrganizeImportsAction_selectiondialog_title);
		dialog.setMessage(ActionMessages.OrganizeImportsAction_selectiondialog_message);
		dialog.setElements(openChoices);
		dialog.setComparator(ORGANIZE_IMPORT_COMPARATOR);
		if (dialog.open() == Window.OK) {
			Object[] res= dialog.getResult();
			result= new TypeNameMatch[res.length];
			for (int i= 0; i < res.length; i++) {
				Object[] array= (Object[]) res[i];
				if (array.length > 0) {
					result[i]= (TypeNameMatch) array[0];
					QualifiedTypeNameHistory.remember(result[i].getFullyQualifiedName());
				}
			}
		}
		// restore selection
		if (sel instanceof ITextSelection) {
			ITextSelection textSelection= (ITextSelection) sel;
			fEditor.selectAndReveal(textSelection.getOffset(), textSelection.getLength());
		}
		fIsQueryShowing= false;
		return result;
	}

	private void doListSelectionChanged(int page, ISourceRange[] ranges) {
		if (fEditor != null && ranges != null && page >= 0 && page < ranges.length) {
			ISourceRange range= ranges[page];
			fEditor.selectAndReveal(range.getOffset(), range.getLength());
		}
	}

	private void setStatusBarMessage(String message) {
		IEditorActionBarContributor contributor= fEditor.getEditorSite().getActionBarContributor();
		if (contributor instanceof EditorActionBarContributor) {
			IStatusLineManager manager= ((EditorActionBarContributor) contributor).getActionBars().getStatusLineManager();
			manager.setMessage(message);
		}
	}

	private IEditingSupport createViewerHelper() {
		return new IEditingSupport() {
			public boolean isOriginator(DocumentEvent event, IRegion subjectRegion) {
				return true; // assume true, since we only register while we are active
			}
			public boolean ownsFocusShell() {
				return fIsQueryShowing;
			}

		};
	}

	private void registerHelper(IEditingSupport helper) {
		if (fEditor == null)
			return;
		ISourceViewer viewer= fEditor.getViewer();
		if (viewer instanceof IEditingSupportRegistry) {
			IEditingSupportRegistry registry= (IEditingSupportRegistry) viewer;
			registry.register(helper);
		}
	}

	private void deregisterHelper(IEditingSupport helper) {
		if (fEditor == null)
			return;
		ISourceViewer viewer= fEditor.getViewer();
		if (viewer instanceof IEditingSupportRegistry) {
			IEditingSupportRegistry registry= (IEditingSupportRegistry) viewer;
			registry.unregister(helper);
		}
	}
}
