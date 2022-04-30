/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Sian January - initial version
 * ...
 ******************************************************************************/
package org.eclipse.ajdt.internal.debug.ui.actions;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.ui.actions.IToggleBreakpointsTargetExtension;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.internal.debug.ui.BreakpointUtils;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jdt.internal.debug.ui.actions.ActionDelegateHelper;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.IEditorStatusLine;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Toggles a line breakpoint in a Java editor.
 * Based on org.eclipse.jdt.internal.debug.ui.actions.ToggleBreakpointAdapter, but uses
 * BreakpointLocationVerifierJob and ValidBreakpointLocationLocator from this package and does not
 * support method breakpoints or field watchpoints.
 */
public class ToggleBreakpointAdapter implements IToggleBreakpointsTargetExtension {

	public ToggleBreakpointAdapter() {
		// init helper in UI thread
		ActionDelegateHelper.getDefault();
	}

    protected void report(final String message, final IWorkbenchPart part) {
        JDIDebugUIPlugin.getStandardDisplay().asyncExec(() -> {
            IEditorStatusLine statusLine = part.getAdapter(IEditorStatusLine.class);
            if (statusLine != null) {
                if (message != null) {
                    statusLine.setMessage(true, message, null);
                } else {
                    statusLine.setMessage(true, null, null);
                }
            }
            if (message != null && JDIDebugUIPlugin.getActiveWorkbenchShell() != null) {
                JDIDebugUIPlugin.getActiveWorkbenchShell().getDisplay().beep();
            }
        });
    }

    protected IType getType(ITextSelection selection) {
        IMember member = ActionDelegateHelper.getDefault().getCurrentMember(selection);
        IType type = null;
        if (member instanceof IType) {
            type = (IType) member;
        } else if (member != null) {
            type = member.getDeclaringType();
        }
        // bug 52385: we don't want local and anonymous types from compilation
        // unit,
        // we are getting 'not-always-correct' names for them.
        try {
            while (type != null && !type.isBinary() && type.isLocal()) {
                type = type.getDeclaringType();
            }
        } catch (JavaModelException e) {
            JDIDebugUIPlugin.log(e);
        }
        return type;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.debug.ui.actions.IToggleBreakpointsTarget#toggleLineBreakpoints(IWorkbenchPart,
     *      ISelection)
     */
    public void toggleLineBreakpoints(IWorkbenchPart part, ISelection selection) throws CoreException {
        toggleLineBreakpoints(part, selection, false);
    }

    public void toggleLineBreakpoints(final IWorkbenchPart part, final ISelection selection, final boolean bestMatch) {
        Job job = new Job("Toggle Line Breakpoint") { //$NON-NLS-1$
            protected IStatus run(IProgressMonitor monitor) {
                if (selection instanceof ITextSelection) {
                    if (monitor.isCanceled()) {
                        return Status.CANCEL_STATUS;
                    }
                    report(null, part);
                    IEditorPart editorPart = (IEditorPart) part;
                    ITextSelection textSelection = (ITextSelection) selection;
                    IType type = getType(textSelection);
                    IEditorInput editorInput = editorPart.getEditorInput();
                    IDocumentProvider documentProvider = ((ITextEditor) editorPart).getDocumentProvider();
                    if (documentProvider == null) {
                        return Status.CANCEL_STATUS;
                    }
                    IDocument document = documentProvider.getDocument(editorInput);
                    int lineNumber = textSelection.getStartLine() + 1;
                    int offset = textSelection.getOffset();
                    try {
                        if (type == null) {
                            IClassFile classFile = editorInput.getAdapter(IClassFile.class);
                            if (classFile != null) {
                                type = classFile.getType();
                                // bug 34856 - if this is an inner type, ensure
                                // the breakpoint is not
                                // being added to the outer type
                                if (type.getDeclaringType() != null) {
                                    ISourceRange sourceRange = type.getSourceRange();
                                    int start = sourceRange.getOffset();
                                    int end = start + sourceRange.getLength();
                                    if (offset < start || offset > end) {
                                        // not in the inner type
                                        IStatusLineManager statusLine = editorPart.getEditorSite().getActionBars().getStatusLineManager();
                                        statusLine.setErrorMessage(NLS.bind(UIMessages.ManageBreakpointRulerAction_Breakpoints_can_only_be_created_within_the_type_associated_with_the_editor___0___1, new String[] { type.getTypeQualifiedName() }));
                                        Display.getCurrent().beep();
                                        return Status.OK_STATUS;
                                    }
                                }
                            }
                        }

                        String typeName = null;
                        IResource resource;
                      Map<String, Object> attributes = new HashMap<>(10);
                        if (type == null) {
                            resource = getResource(editorPart);
                            if (editorPart instanceof ITextEditor) {
                                CompilationUnit unit = parseCompilationUnit((ITextEditor) editorPart);
                              for (Object o : unit.types()) {
                                TypeDeclaration declaration = (TypeDeclaration) o;
                                int begin = declaration.getStartPosition();
                                int end = begin + declaration.getLength();
                                if (offset >= begin && offset <= end && !declaration.isInterface()) {
                                  typeName = ValidBreakpointLocationLocator.computeTypeName(declaration);
                                  break;
                                }
                              }
                            }
                            if(typeName == null) {
                            	AJCompilationUnit ajcu = AJCompilationUnitManager.INSTANCE.getAJCompilationUnit((IFile)resource);
                            	if(ajcu != null) {
	                            	IType[] types = ajcu.getAllTypes();
                                for (IType iType : types) {
                                  int begin = iType.getSourceRange().getOffset();
                                  int end = begin + iType.getSourceRange().getLength();
                                  if (offset >= begin && offset <= end && !iType.isInterface()) {
                                    typeName = iType.getPackageFragment().getElementName() + "." + iType.getTypeQualifiedName(); //$NON-NLS-1$
                                    break;
                                  }
                                }
                            	}
                            }
                        } else {
                            typeName = type.getFullyQualifiedName();
                            int index = typeName.indexOf('$');
                            if (index >= 0) {
                                typeName = typeName.substring(0, index);
                            }
                            resource = BreakpointUtils.getBreakpointResource(type);
                            try {
                                IRegion line = document.getLineInformation(lineNumber - 1);
                                int start = line.getOffset();
                                int end = start + line.getLength() - 1;
                                BreakpointUtils.addJavaBreakpointAttributesWithMemberDetails(attributes, type, start, end);
                            } catch (BadLocationException ble) {
                                JDIDebugUIPlugin.log(ble);
                            }
                        }
                        if (typeName != null && resource != null) {
                            IJavaLineBreakpoint existingBreakpoint = JDIDebugModel.lineBreakpointExists(resource, typeName, lineNumber);
                            if (existingBreakpoint != null) {
                                removeBreakpoint(existingBreakpoint, true);
                                return Status.OK_STATUS;
                            }
                            createLineBreakpoint(resource, typeName, offset, lineNumber, -1, -1, 0, true, attributes, document, bestMatch, type, editorPart);
                        }
                    } catch (CoreException ce) {
                        return ce.getStatus();
                    }
                }
                return Status.OK_STATUS;
            }
        };
        job.setSystem(true);
        job.schedule();
    }

    private void createLineBreakpoint(IResource resource, String typeName, int offset, int lineNumber, int charStart, int charEnd, int hitCount, boolean register, Map<String, Object> attributes, IDocument document, boolean bestMatch, IType type, IEditorPart editorPart) throws CoreException {
        IJavaLineBreakpoint breakpoint = JDIDebugModel.createLineBreakpoint(resource, typeName, lineNumber, charStart, charEnd, hitCount, register, attributes);
        new BreakpointLocationVerifierJob(document, breakpoint, offset, lineNumber, typeName, type, resource, editorPart).schedule();
    }

    public boolean canToggleLineBreakpoints(IWorkbenchPart part, ISelection selection) {
        return selection instanceof ITextSelection;
    }

    public void toggleMethodBreakpoints(final IWorkbenchPart part, final ISelection finalSelection) {
    }

    private void removeBreakpoint(IBreakpoint breakpoint, boolean delete) throws CoreException {
        DebugPlugin.getDefault().getBreakpointManager().removeBreakpoint(breakpoint, delete);
    }

    public boolean canToggleMethodBreakpoints(IWorkbenchPart part, ISelection selection) {
    	return false;
    }

    protected CompilationUnit parseCompilationUnit(ITextEditor editor) throws CoreException {
        IEditorInput editorInput = editor.getEditorInput();
        IDocumentProvider documentProvider = editor.getDocumentProvider();
        if (documentProvider == null) {
            throw new CoreException(Status.CANCEL_STATUS);
        }
        IDocument document = documentProvider.getDocument(editorInput);
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(document.get().toCharArray());
        return (CompilationUnit) parser.createAST(null);
    }

    public void toggleWatchpoints(final IWorkbenchPart part, final ISelection finalSelection) {
    }

    protected static IResource getResource(IEditorPart editor) {
        IEditorInput editorInput = editor.getEditorInput();
        IResource resource = editorInput.getAdapter(IFile.class);
        if (resource == null) {
            resource = ResourcesPlugin.getWorkspace().getRoot();
        }
        return resource;
    }

    public boolean canToggleWatchpoints(IWorkbenchPart part, ISelection selection) {
    	return false;
    }

    public void toggleBreakpoints(IWorkbenchPart part, ISelection selection) throws CoreException {
        toggleLineBreakpoints(part, selection, true);
    }

    public boolean canToggleBreakpoints(IWorkbenchPart part, ISelection selection) {
        return canToggleLineBreakpoints(part, selection);
    }
}
