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
 *     Andrew Eisenberg - fix for Bug 345883
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.wizards;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;

import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.internal.ui.preferences.AspectJPreferences;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.internal.ui.wizards.NewTypeWizardPage.ImportsManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogFieldGroup;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.ui.dialogs.SelectionStatusDialog;

/**
 * The New Aspect wizard is implemented where possible by NewAspectWizardPage.java
 * by subclassing NewTypeWizardPage. This aspect adds the required aspect awareness
 * to our copy of NewTypeWizardPage.
 */
privileged aspect AJNewTypeWizardPage {

    public static int NewTypeWizardPage.F_PRIVILEGED = 0x8000;

    private static final int NewTypeWizardPage.PRIVILEGED_INDEX = 3;

    private IFile NewTypeWizardPage.fCreatedFile;

    int around() : execution(int NewTypeWizardPage.getModifiers()) {
        int mdf = proceed();
        if (((NewTypeWizardPage)thisJoinPoint.getThis()).fOtherMdfButtons.isSelected(NewTypeWizardPage.PRIVILEGED_INDEX)) {
            mdf+= NewTypeWizardPage.F_PRIVILEGED;
        }
        return mdf;
    }

    String around(int modifiers) : call(String Flags.toString(..))
        && args(modifiers) && withincode(String NewTypeWizardPage.constructTypeStub(..)) {
        String s = proceed(modifiers);
        if ((modifiers & NewTypeWizardPage.F_PRIVILEGED) != 0) {
            if (s.length()>0) {
                s += ' ';
            }
            s += ("privileged"); //$NON-NLS-1$
        }
        return s;
    }

    after(int modifiers) : execution(* NewTypeWizardPage.setModifiers(..))
        && args(modifiers, ..) {
        if ((modifiers & NewTypeWizardPage.F_PRIVILEGED) != 0) {
            NewTypeWizardPage page = (NewTypeWizardPage)thisJoinPoint.getThis();
            page.fOtherMdfButtons.setSelection(NewTypeWizardPage.PRIVILEGED_INDEX, true);
        }
    }

    after(String label) : call(* *.setLabelText(..)) && args(label) && within(NewTypeWizardPage) {
        if (label.equals(NewWizardMessages.NewTypeWizardPage_superclass_label)) {
            NewTypeWizardPage page = (NewTypeWizardPage)thisJoinPoint.getThis();
            page.fSuperClassDialogField.setLabelText(UIMessages.NewAspectCreationWizardPage_supertype_label);
        }
    }

    after() : set(* NewTypeWizardPage.fOtherMdfButtons) && within(NewTypeWizardPage) {
        NewTypeWizardPage page = (NewTypeWizardPage)thisJoinPoint.getThis();
        String[] buttonNames= new String[] {
            NewWizardMessages.NewTypeWizardPage_modifiers_abstract,
            NewWizardMessages.NewTypeWizardPage_modifiers_final,
            NewWizardMessages.NewTypeWizardPage_modifiers_static,
            "privileged" //$NON-NLS-1$
        };
        page.fOtherMdfButtons = new SelectionButtonDialogFieldGroup(SWT.CHECK, buttonNames, 4);
    }

    IType around() : execution(* NewTypeWizardPage.chooseSuperClass(..)) {
        NewTypeWizardPage page = (NewTypeWizardPage) thisJoinPoint.getThis();
        IPackageFragmentRoot root = page.getPackageFragmentRoot();
        if (root == null) {
            return null;
        }

        IJavaElement[] elements = new IJavaElement[] { root.getJavaProject() };
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(elements);

        SelectionStatusDialog dialog = new org.eclipse.ajdt.internal.ui.dialogs.TypeSelectionDialog2(
                page.getShell(), false, page.getWizard().getContainer(), scope,
                IJavaSearchConstants.CLASS);
        dialog.setTitle(UIMessages.NewTypeWizardPage_SuperClassDialog_title);
        dialog
                .setMessage(UIMessages.NewTypeWizardPage_SuperClassDialog_message);
        if (dialog.open() == Window.OK) {
            return (IType) dialog.getFirstResult();
        }
        return null;
    }

    after(String fieldName) : execution(* NewTypeWizardPage.handleFieldChanged(..))
        && args(fieldName) && within(NewTypeWizardPage) {
        if (fieldName.equals(NewTypeWizardPage.ENCLOSINGSELECTION)) {
            NewTypeWizardPage page = (NewTypeWizardPage) thisJoinPoint.getThis();
            boolean isEnclosedType= page.isEnclosingTypeSelected();
            if (isEnclosedType) {
                // inner aspects must be static
                page.fOtherMdfButtons.setSelection(page.STATIC_INDEX, true);
            }
        }
    }

    after(StringBuffer buf) : call(* writeSuperInterfaces(..)) && args(buf, ..)
        && withincode(String NewTypeWizardPage.constructTypeStub(..)) {
        if (thisJoinPoint.getThis() instanceof NewAspectWizardPage) {
            ((NewAspectWizardPage)thisJoinPoint.getThis()).writePerClause(buf);
        }
    }

    Object around(String txt) : call(* StringBuffer.append(..)) && args(txt)
        && withincode(String NewTypeWizardPage.constructTypeStub(..)) {
        if (txt.equals("class ")) { //$NON-NLS-1$
            return proceed("aspect "); //$NON-NLS-1$
        }
        return proceed(txt);
    }

    private IFile NewTypeWizardPage.createNewFile(String sourceFolder, String packName) {
        IPath path = new Path(sourceFolder);
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IResource res = workspaceRoot.findMember(path);
        IProject proj = res.getProject();
        IResource pack;

        if (res.getType() == IResource.FOLDER) {
            IFolder folder = (IFolder) res;
            pack = folder.findMember(packName);
        } else if (res.getType() == IResource.PROJECT) {
            pack = ((IProject) res).findMember(packName);
        } else {
            return null;
        }

        if (pack == null) { // need to create a new package
            IJavaProject jproject = JavaCore.create(proj);
            IPackageFragmentRoot root = jproject.getPackageFragmentRoot(res);
            try {
                IPackageFragment frag = root.createPackageFragment(packName,
                        true, null);
                pack = frag.getResource();
            } catch (JavaModelException ignored) {
            }
        }

        String aspectName = getTypeName();
        String extName = AspectJPreferences.getFileExt();
        IPath newpath = pack.getFullPath().append(aspectName + extName);
      return workspaceRoot.getFile(newpath);
    }


    IResource around() : execution(*  NewTypeWizardPage.getModifiedResource()) {
        NewTypeWizardPage page = (NewTypeWizardPage) thisJoinPoint.getThis();
        IType enclosing= page.getEnclosingType();
        if (enclosing != null) {
            return enclosing.getResource();
        }
        if (page.fCreatedFile != null) {
            return page.fCreatedFile;
        }
        return null;
    }

    private void NewTypeWizardPage.createAJType(IProgressMonitor monitor) throws CoreException, InterruptedException {
        if (monitor == null) {
            monitor= new NullProgressMonitor();
        }

        monitor.beginTask(NewWizardMessages.NewTypeWizardPage_operationdesc, 8);

        IPackageFragmentRoot root= getPackageFragmentRoot();
        IPackageFragment pack= getPackageFragment();
        if (pack == null) {
            pack= root.getPackageFragment(""); //$NON-NLS-1$
        }

        if (!pack.exists()) {
            String packName= pack.getElementName();
            pack= root.createPackageFragment(packName, true, new SubProgressMonitor(monitor, 1));
        } else {
            monitor.worked(1);
        }

        boolean needsSave;
        ICompilationUnit connectedCU= null;
        fCreatedFile = null; // AspectJ change
        try {
            String typeName= getTypeNameWithoutParameters();

            boolean isInnerClass= isEnclosingTypeSelected();

            IType createdType;
            ImportsManager imports;

            Set<String> existingImports;

            String lineDelimiter;
            if (!isInnerClass) {
                lineDelimiter= StubUtility.getLineDelimiterUsed(pack.getJavaProject());

                // AspectJ change begin
                fCreatedFile = createNewFile(getPackageFragmentRootText(), pack.getElementName());
                InputStream is = new ByteArrayInputStream("".getBytes()); //$NON-NLS-1$
                fCreatedFile.create(is, false, monitor);
                AJCompilationUnit parentCU = AJCompilationUnitManager.INSTANCE.getAJCompilationUnit(fCreatedFile);
                // AspectJ change end

                //String cuName= getCompilationUnitName(typeName);
                //ICompilationUnit parentCU= pack.createCompilationUnit(cuName, "", false, new SubProgressMonitor(monitor, 2)); //$NON-NLS-1$
                // create a working copy with a new owner

                needsSave= true;
                parentCU.becomeWorkingCopy(new SubProgressMonitor(monitor, 1)); // cu is now a (primary) working copy
                connectedCU= parentCU;

                IBuffer buffer= parentCU.getBuffer();

                String cuContent= constructCUContent(parentCU, constructSimpleTypeStub(), lineDelimiter);
                buffer.setContents(cuContent);

                CompilationUnit astRoot= createASTForImports(parentCU);
                existingImports= getExistingImports(astRoot);

                imports= new ImportsManager(astRoot);
                // add an import that will be removed again. Having this import solves 14661
                imports.addImport(JavaModelUtil.concatenateName(pack.getElementName(), typeName));

                String typeContent= constructTypeStub(parentCU, imports, lineDelimiter);

                AbstractTypeDeclaration typeNode= (AbstractTypeDeclaration) astRoot.types().get(0);
                int start= ((ASTNode) typeNode.modifiers().get(0)).getStartPosition();
                int end= typeNode.getStartPosition() + typeNode.getLength();

                buffer.replace(start, end - start, typeContent);

                createdType= parentCU.getType(typeName);
            } else {
                IType enclosingType= getEnclosingType();

                ICompilationUnit parentCU= enclosingType.getCompilationUnit();

                needsSave= !parentCU.isWorkingCopy();
                parentCU.becomeWorkingCopy(new SubProgressMonitor(monitor, 1)); // cu is now for sure (primary) a working copy
                connectedCU= parentCU;

                CompilationUnit astRoot= createASTForImports(parentCU);
                imports= new ImportsManager(astRoot);
                existingImports= getExistingImports(astRoot);


                // add imports that will be removed again. Having the imports solves 14661
                IType[] topLevelTypes= parentCU.getTypes();
              for (IType topLevelType : topLevelTypes) {
                imports.addImport(topLevelType.getFullyQualifiedName('.'));
              }

                lineDelimiter= StubUtility.getLineDelimiterUsed(enclosingType);
                StringBuilder content= new StringBuilder();

                String comment= getTypeComment(parentCU, lineDelimiter);
                if (comment != null) {
                    content.append(comment);
                    content.append(lineDelimiter);
                }

                content.append(constructTypeStub(parentCU, imports, lineDelimiter));
                IJavaElement sibling= null;
                if (enclosingType.isEnum()) {
                    IField[] fields = enclosingType.getFields();
                    if (fields.length > 0) {
                      for (IField field : fields) {
                        if (!field.isEnumConstant()) {
                          sibling = field;
                          break;
                        }
                      }
                    }
                } else {
                    IJavaElement[] elems= enclosingType.getChildren();
                    sibling = elems.length > 0 ? elems[0] : null;
                }

//              // AspectJ change begin
//              int ind = content.indexOf("aspect"); //$NON-NLS-1$
//              if (ind != -1) {
//                  // rewrite to class, otherwise createType will think the contents are invalid
//                  content.replace(ind,ind+"aspect".length(),"class");  //$NON-NLS-1$//$NON-NLS-2$
//              }
//              // AspectJ change end
                createdType= enclosingType.createType(content.toString(), sibling, false, new SubProgressMonitor(monitor, 2));

            }
            if (monitor.isCanceled()) {
                throw new InterruptedException();
            }

            // add imports for superclass/interfaces, so types can be resolved correctly

            // AspectJ change begin
            AJCompilationUnit cu= (AJCompilationUnit) createdType.getCompilationUnit();
            cu.requestOriginalContentMode();
            // AspectJ change end

            if (!isInnerClass) { // AspectJ change
                imports.create(false, new SubProgressMonitor(monitor, 1));
            }

            JavaModelUtil.reconcile(cu);

            if (monitor.isCanceled()) {
                throw new InterruptedException();
            }

            // set up again
            CompilationUnit astRoot= createASTForImports(imports.getCompilationUnit());
            imports= new ImportsManager(astRoot);

            // AspectJ change begin
            cu.discardOriginalContentMode();
            // AspectJ change end

            createTypeMembers(createdType, imports, new SubProgressMonitor(monitor, 1));
            // AspectJ change begin
            cu.requestOriginalContentMode();
            // AspectJ change end

            // add imports
            if (!isInnerClass) { // AspectJ change
                imports.create(false, new SubProgressMonitor(monitor, 1));
            }

            removeUnusedImports(cu, existingImports, false);

            JavaModelUtil.reconcile(cu);

            ISourceRange range= createdType.getSourceRange();

            IBuffer buf= cu.getBuffer();
            String originalContent= buf.getText(range.getOffset(), range.getLength());

//          // AspectJ change begin
//          String repl = originalContent;
//          int ind = originalContent.indexOf("aspect"); //$NON-NLS-1$
//          if (ind != -1) {
//              repl = originalContent.substring(0,ind) + "class" //$NON-NLS-1$
//                  + originalContent.substring(ind+"aspect".length()); //$NON-NLS-1$
//          }
//          String formattedContent= CodeFormatterUtil.format(
//                  CodeFormatter.K_CLASS_BODY_DECLARATIONS, repl, indent, lineDelimiter, pack.getJavaProject());
//          formattedContent= Strings.trimLeadingTabsAndSpaces(formattedContent);
//          ind = formattedContent.indexOf("class"); //$NON-NLS-1$
//          if (ind != -1) {
//              formattedContent = formattedContent.substring(0,ind) + "aspect" //$NON-NLS-1$
//                  + formattedContent.substring(ind+"class".length()); //$NON-NLS-1$
//          }
//          buf.replace(range.getOffset(), range.getLength(), formattedContent);
//          // AspectJ change end

//          buf.replace(range.getOffset(), range.getLength(), originalContent);
            if (!isInnerClass) {
                String fileComment= getFileComment(cu);
                if (fileComment != null && fileComment.length() > 0) {
                    buf.replace(0, 0, fileComment + lineDelimiter);
                }
            }
            fCreatedType= createdType;

            if (needsSave) {
                cu.commitWorkingCopy(true, new SubProgressMonitor(monitor, 1));
            } else {
                monitor.worked(1);
            }

            // AspectJ change begin
            if (cu instanceof AJCompilationUnit) {
                cu.discardOriginalContentMode();
            }
            // AspectJ change end
        } finally {
            if (connectedCU != null) {
                connectedCU.discardWorkingCopy();
            }
            monitor.done();
        }
    }

    void around(IProgressMonitor monitor) throws CoreException, InterruptedException : execution(void NewTypeWizardPage.createType(..)) && args(monitor) {
        NewTypeWizardPage page = (NewTypeWizardPage) thisJoinPoint.getThis();
        page.createAJType(monitor);
    }
}
