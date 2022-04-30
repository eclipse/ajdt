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

package org.eclipse.contribution.weaving.jdt.tests.itdawareness;


import java.util.HashMap;

import org.eclipse.contribution.jdt.itdawareness.ContentAssistAdapter;
import org.eclipse.contribution.jdt.itdawareness.IJavaContentAssistProvider;
import org.eclipse.contribution.jdt.itdawareness.INameEnvironmentProvider;
import org.eclipse.contribution.jdt.itdawareness.NameEnvironmentAdapter;
import org.eclipse.contribution.weaving.jdt.tests.WeavingTestCase;
import org.eclipse.core.internal.registry.osgi.OSGIUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.CompilationUnitProblemFinder;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.text.java.ContentAssistProcessor;
import org.eclipse.jdt.internal.ui.text.java.JavaCompletionProcessor;
import org.eclipse.jdt.internal.ui.text.javadoc.JavadocCompletionProcessor;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;

/**
 * Tests that ITDAwarenessAspect is disabled for Java projects
 * and enabled for interesting projects
 *
 * @author Andrew Eisenberg
 * @created Jan 30, 2009
 *
 */
public class ITDAwarenessTests extends WeavingTestCase {
    MockNameEnvironmentProvider nameEnvironmentProvider;
    MockContentAssistProvider contentAssistProvider;

    INameEnvironmentProvider origNameEnvironmentProvider;
    IJavaContentAssistProvider origContentAssistProvider;

    IJavaProject mockNatureProject;
    IJavaProject javaNatureProject;

    IProject mock;
    IProject java;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // ensure the ajdt bundles are started if they exist
        try {
            OSGIUtils.getDefault().getBundle("org.eclipse.ajdt.core").start();
        } catch (NullPointerException e) {
            // ignore, bundle doesn't exist
        }
        try {
            OSGIUtils.getDefault().getBundle("org.eclipse.ajdt.ui").start();
        } catch (NullPointerException e) {
            // ignore, bundle doesn't exist
        }

        origNameEnvironmentProvider = NameEnvironmentAdapter.getInstance().getProvider();
        origContentAssistProvider = ContentAssistAdapter.getInstance().getProvider();

        nameEnvironmentProvider = new MockNameEnvironmentProvider();
        contentAssistProvider = new MockContentAssistProvider();
        NameEnvironmentAdapter.getInstance().setProvider(nameEnvironmentProvider);
        ContentAssistAdapter.getInstance().setProvider(contentAssistProvider);

        mock = this.createPredefinedProject("MockCUProject");
        java = this.createPredefinedProject("RealJavaProject");
    }

    @Override
    public void tearDown() throws Exception {
        try {
            super.tearDown();
        } finally {
            NameEnvironmentAdapter.getInstance().setProvider(origNameEnvironmentProvider);
            ContentAssistAdapter.getInstance().setProvider(origContentAssistProvider);
        }
    }


    public void testFindProblemsInJavaProject() throws Exception {
        IFile nothingJava = java.getFile("src/nothing/Nothing.java");
        ICompilationUnit nothingCU = (ICompilationUnit) JavaCore.create(nothingJava);
        CompilationUnitProblemFinder.process((CompilationUnit) nothingCU, null,
                DefaultWorkingCopyOwner.PRIMARY, new HashMap<>(), true,
                ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | ICompilationUnit.FORCE_PROBLEM_DETECTION, null);

        assertFalse("Should not have triggered problem finding through the aspects", nameEnvironmentProvider.problemFindingDone);
    }

    public void testFindProblemsInMockProject() throws Exception {
        IFile nothingMock = mock.getFile("src/nothing/Nothing.java");
        ICompilationUnit nothingCU = (ICompilationUnit) JavaCore.create(nothingMock);
        CompilationUnitProblemFinder.process((CompilationUnit) nothingCU, null,
                DefaultWorkingCopyOwner.PRIMARY, new HashMap<>(), true,
                ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | ICompilationUnit.FORCE_PROBLEM_DETECTION, null);

        assertTrue("Should have triggered problem finding through the aspects", nameEnvironmentProvider.problemFindingDone);
    }

    /**
     * should not trigger the mock content assist provider
     */
    public void testContentAssistInJavaProject() throws Exception {
        IFile nothingJava = java.getFile("src/nothing/Nothing.java");
        ICompletionProposal[] completions = getCompletionProposals(nothingJava, "Nothing();");

        // seems like depending on which completion processors are currently installed, the
        // result can be 1 or 2 proposals.
        // Looks like the important processors are the JavaNoTypeCompletionProposalComputer and the JavaAllCompletionProposalComputer
        if (completions.length < 1 || completions.length > 3) {
            StringBuilder sb = new StringBuilder();
          for (ICompletionProposal completion : completions) {
            sb.append("\n").append(completion.getDisplayString());
          }
            fail("Should have found 1, 2, or 3 completion proposals, but instead found: " + completions.length + sb);
        }
        assertFalse("Should not have triggered the content assist through the aspect", contentAssistProvider.contentAssistDone);
    }

    /**
     * should trigger the mock content assist provider
     */
    public void testContentAssistInMockProject() throws Exception {
        IFile nothingJava = mock.getFile("src/nothing/Nothing.java");
        ICompletionProposal[] completions = getCompletionProposals(nothingJava, "Nothing();");
        assertEquals("Should have found no completion proposals", 0, completions.length);
        assertTrue("Should have triggered the content assist through the aspect", contentAssistProvider.contentAssistDone);
    }

    /**
     * Should not trigger the advice
     */
    public void testCodeSelectInJavaProject() throws Exception {
        IFile nothingJava = java.getFile("src/nothing/Nothing.java");
        ICompilationUnit unit = JavaCore.createCompilationUnitFrom(nothingJava);
        int offset = 0;
        int length = 1;
        IJavaElement[] selection = unit.codeSelect(offset, length);
        assertEquals("Should have found 0 completion proposals", 0, selection.length);
        assertFalse("Should not have triggered the content assist through the aspect", contentAssistProvider.codeSelectDone);
    }

    /**
     * Should trigger the advice
     */
    public void testCodeSelectInMockProject() throws Exception {
        IFile nothingJava = mock.getFile("src/nothing/Nothing.java");
        ICompilationUnit unit = JavaCore.createCompilationUnitFrom(nothingJava);
        int offset = 0;
        int length = 1;
        IJavaElement[] selection = unit.codeSelect(offset, length);
        assertEquals("Should have found 0 completion proposal", 0, selection.length);
        assertTrue("Should have triggered the content assist through the aspect", contentAssistProvider.codeSelectDone);
    }

    private ICompletionProposal[] getCompletionProposals(IFile file, String marker) throws Exception {
        JavaEditor editor = (JavaEditor) EditorUtility.openInEditor(file);
        JavaCompletionProcessor proc =
            new JavaCompletionProcessor(editor,
            getContentAssistant(editor), IDocument.DEFAULT_CONTENT_TYPE);
        CompilationUnit javaunit = (CompilationUnit)JavaCore.create(file);
        String content = javaunit.getSource();
        int offset = content.indexOf(marker);
        return proc.computeCompletionProposals(editor.getViewer(), offset);
    }

    private ContentAssistant getContentAssistant(JavaEditor editor) {
        ContentAssistant assistant= new ContentAssistant();
        IContentAssistProcessor javaProcessor= new JavaCompletionProcessor(editor, assistant, IDocument.DEFAULT_CONTENT_TYPE);
        assistant.setContentAssistProcessor(javaProcessor, IDocument.DEFAULT_CONTENT_TYPE);

        ContentAssistProcessor singleLineProcessor= new JavaCompletionProcessor(editor, assistant, IJavaPartitions.JAVA_SINGLE_LINE_COMMENT);
        assistant.setContentAssistProcessor(singleLineProcessor, IJavaPartitions.JAVA_SINGLE_LINE_COMMENT);

        ContentAssistProcessor stringProcessor= new JavaCompletionProcessor(editor, assistant, IJavaPartitions.JAVA_STRING);
        assistant.setContentAssistProcessor(stringProcessor, IJavaPartitions.JAVA_STRING);

        ContentAssistProcessor multiLineProcessor= new JavaCompletionProcessor(editor, assistant, IJavaPartitions.JAVA_MULTI_LINE_COMMENT);
        assistant.setContentAssistProcessor(multiLineProcessor, IJavaPartitions.JAVA_MULTI_LINE_COMMENT);

        ContentAssistProcessor javadocProcessor= new JavadocCompletionProcessor(editor, assistant);
        assistant.setContentAssistProcessor(javadocProcessor, IJavaPartitions.JAVA_DOC);

        assistant.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
        return assistant;
    }


}
