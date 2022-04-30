/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

package org.eclipse.ajdt.internal.ui.refactoring;

import java.util.ArrayList;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.ReflectionUtils;
import org.eclipse.ajdt.core.codeconversion.AspectsConvertingParser;
import org.eclipse.ajdt.core.codeconversion.AspectsConvertingParser.Replacement;
import org.eclipse.ajdt.core.codeconversion.ConversionOptions;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.contribution.jdt.refactoring.IRefactoringProvider;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.refactoring.rename.JavaRenameProcessor;
import org.eclipse.jdt.internal.ui.refactoring.UserInterfaceManager;
import org.eclipse.jdt.internal.ui.refactoring.reorg.RenameUserInterfaceManager;
import org.eclipse.jdt.internal.ui.refactoring.reorg.RenameUserInterfaceStarter;
import org.eclipse.jdt.ui.refactoring.RenameSupport;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.internal.Workbench;

/**
 * This is a UI class that ensures that the proper Refactoring
 * and wizard are invoked when rename is selected on an ITD.
 *
 * @author Andrew Eisenberg
 * @created May 21, 2010
 */
public class ITDRenameRefactoringProvider implements IRefactoringProvider {

    static {
        // ensure the user interface manager is properly initialized
        ReflectionUtils.executePrivateMethod(UserInterfaceManager.class, "put",
                new Class<?>[] { Class.class, Class.class, Class.class },
                RenameUserInterfaceManager.getDefault(), new Object[] {
                ITDRenameRefactoringProcessor.class, RenameUserInterfaceStarter.class, RenameITDWizard.class });
    }

    public boolean isInterestingElement(IJavaElement element) {
        return element instanceof IntertypeElement;
    }

    public void performRefactoring(IJavaElement element, boolean lightweight) throws CoreException {
        RefactoringStatus status = new RefactoringStatus();
        JavaRenameProcessor processor = new ITDRenameRefactoringProcessor((IntertypeElement) element, status);

        if (status.isOK()) {
            final RenameSupport support = ReflectionUtils.executePrivateConstructor(RenameSupport.class, new Class[] { JavaRenameProcessor.class, String.class, int.class },
                    new Object[] { processor, null, RenameSupport.UPDATE_REFERENCES });
            if (support != null && support.preCheck().isOK()) {
                support.openDialog(getShell());
            }
        } else {
            IStatusLineManager manager = getStatusLineManager();
            if (manager != null) {
                manager.setErrorMessage(status.toString());
            }
        }
    }

    /**
     * Lightweight rename refactoring is often broken inside of {@link AJCompilationUnit}s,
     * so just disable it.
     * @param elt
     * @return true if the element is inside an {@link AJCompilationUnit}
     */
    public boolean belongsToInterestingCompilationUnit(IJavaElement elt) {
        return elt.getAncestor(IJavaElement.COMPILATION_UNIT) instanceof AJCompilationUnit;
    }

    private IStatusLineManager getStatusLineManager() {
        try {
            return Workbench.getInstance().getActiveWorkbenchWindow().getActivePage().getActiveEditor().getEditorSite().getActionBars().getStatusLineManager();
        } catch (Exception e) {
            // null for some reason, maybe workbench not fully initialized
            return null;
        }
    }

    private Shell getShell() {
        try {
            return Workbench.getInstance().getActiveWorkbenchWindow().getShell();
        } catch (Exception e) {
            // null for some reason, maybe workbench not fully initialized
            return null;
        }
    }

    /**
     * Do not check results for problems if this is an {@link AJCompilationUnit}
     * since the result checking uses the actual file contents and will always
     * produce errors.
     */
    public boolean shouldCheckResultForCompileProblems(ICompilationUnit unit) {
        return ! (unit instanceof AJCompilationUnit);
    }

    public CompilationUnit createASTForRefactoring(ITypeRoot root) {
        if (root instanceof AJCompilationUnit) {
            AJCompilationUnit ajUnit = (AJCompilationUnit) root;

            // create a clone of the original ajUnit so that
            // an ast and its bindings can be created on the constant sized source
            // code
            try {
                ajUnit.requestOriginalContentMode();
                char[] contents = ((AJCompilationUnit) root).getContents();
                ajUnit.discardOriginalContentMode();
                AspectsConvertingParser acp = new AspectsConvertingParser(contents);
                acp.convert(ConversionOptions.CONSTANT_SIZE);
                AJCompilationUnit clone = ajUnit.ajCloneCachingContents(acp.content);
                ASTParser parser = ASTParser.newParser(AST.JLS8);
                parser.setBindingsRecovery(true);
                parser.setResolveBindings(true);
                parser.setSource(clone);
                ASTNode result = parser.createAST(null);
                return result instanceof CompilationUnit ? (CompilationUnit) result : null;
            } catch (JavaModelException ignored) {
            }
        }
        return null;
    }

    public boolean inInterestingProject(ICompilationUnit unit) {
        IProject project = unit.getJavaProject().getProject();
        return AspectJPlugin.isAJProject(project);
    }

    /**
     * Creates an AST for the given compilation unit with translated code.
     * The source locations of the created AST have been converted back so that they reflect the source locations
     * of the original, non-translated source.
     */
    public CompilationUnit createSourceConvertedAST(String contents, ICompilationUnit unit, boolean resolveBindings, boolean statementsRecovery, boolean bindingsRecovery, IProgressMonitor monitor) {
        AspectsConvertingParser converter = new AspectsConvertingParser(contents.toCharArray());
        converter.setUnit(unit);
        final ArrayList<Replacement> replacements = converter.convert(ConversionOptions.CODE_COMPLETION);

        ASTParser fParser = ASTParser.newParser(AST.JLS4);
        fParser.setResolveBindings(resolveBindings);
        fParser.setStatementsRecovery(statementsRecovery);
        fParser.setBindingsRecovery(bindingsRecovery);
        fParser.setProject(unit.getJavaProject());
        fParser.setSource(converter.content);
        fParser.setUnitName(unit.getElementName());
        if (unit.getOwner() != null) {
            fParser.setWorkingCopyOwner(unit.getOwner());
        }
        CompilationUnit result= (CompilationUnit) fParser.createAST(monitor);
        result.accept(new ASTVisitor() {
        	@Override
        	public void preVisit(ASTNode node) {
        		int newStart = AspectsConvertingParser.translatePositionToBeforeChanges(node.getStartPosition(), replacements);
        		int newEnd = AspectsConvertingParser.translatePositionToBeforeChanges(node.getStartPosition() + node.getLength(), replacements);
        	    node.setSourceRange(newStart, newEnd - newStart);
        	}
        });
        return result;
    }
}
