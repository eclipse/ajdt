package org.eclipse.ajdt.ui;

import java.util.HashMap;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.codeconversion.AspectsConvertingParser;
import org.eclipse.ajdt.core.codeconversion.ConversionOptions;
import org.eclipse.ajdt.core.codeconversion.ITDAwareNameEnvironment;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.ITDAwareSourceTypeInfo;
import org.eclipse.ajdt.core.parserbridge.AJCompilationUnitProblemFinder;
import org.eclipse.ajdt.internal.ui.editor.AspectJEditor;
import org.eclipse.contribution.jdt.itdawareness.INameEnvironmentProvider;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.internal.compiler.SourceElementParser;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.env.ISourceType;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jdt.internal.core.SearchableEnvironment;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.internal.core.SourceTypeElementInfo;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.ui.IEditorInput;

/**
 * @author Andrew Eisenberg
 * @created Feb 26, 2009
 *
 * Provides problem finding support for CompilationUnits
 *
 */
public final class AJDTNameEnvironmentProvider implements
        INameEnvironmentProvider {
    public boolean shouldFindProblems(CompilationUnit unitElement) {
        return unitElement.exists() && AspectJPlugin.isAJProject(unitElement.getJavaProject().getProject());
    }

    public SearchableEnvironment getNameEnvironment(
            JavaProject project, WorkingCopyOwner owner) {
        try {
            return new ITDAwareNameEnvironment(project, owner, null);
        } catch (JavaModelException e) {
            return null;
        }
    }

    public SearchableEnvironment getNameEnvironment(
            JavaProject project, ICompilationUnit[] workingCopies) {
        try {
            return new ITDAwareNameEnvironment(project, workingCopies);
        } catch (JavaModelException e) {
            return null;
        }
    }

    public ISourceType transformSourceTypeInfo(ISourceType info) {
        return new ITDAwareSourceTypeInfo(info,
                (SourceType) ((SourceTypeElementInfo) info).getHandle());
    }

    public CompilationUnitDeclaration problemFind(
            CompilationUnit unitElement, SourceElementParser parser,
            WorkingCopyOwner workingCopyOwner, HashMap problems,
            boolean creatingAST, int reconcileFlags,
            IProgressMonitor monitor) throws JavaModelException {
        CompilationUnit newUnit;
        if (shouldTransform(unitElement)) {
            newUnit = transformUnit(unitElement);
            reconcileFlags |= AJCompilationUnitProblemFinder.JAVA_FILE_IN_AJ_EDITOR;
        } else {
            newUnit = unitElement;
        }

        return AJCompilationUnitProblemFinder.processAJ(newUnit, parser, workingCopyOwner, problems, creatingAST, reconcileFlags, monitor);
    }

    /**
     * Java CompilationUnits in AJ editors should be transformed through the
     * AJConvertingParser before being sent off to problem finder.
     */
    private boolean shouldTransform(CompilationUnit unit) {
        if (unit instanceof AJCompilationUnit) {
            return false;
        }
        IEditorInput input = EditorUtility.getEditorInput(unit);
      return AspectJEditor.isInActiveEditor(input);
    }

    private CompilationUnit transformUnit(CompilationUnit unit) {
        return new TransformedCompilationUnit(unit);
    }

    private static class TransformedCompilationUnit extends CompilationUnit {
        private char[] transformedContents = null;
        private final char[] origContents;
        public TransformedCompilationUnit(CompilationUnit orig) {
            super((PackageFragment) orig.getParent(), orig.getElementName(), orig.owner);
            origContents = orig.getContents();
        }

        public char[] getContents() {
            if (transformedContents == null) {
                AspectsConvertingParser conversion = new AspectsConvertingParser(origContents);
                conversion.convert(ConversionOptions.STANDARD);
                transformedContents = conversion.content;
            }
            return transformedContents;
        }

        public CompilationUnit cloneCachingContents() {
            return this;
        }
    }
}
