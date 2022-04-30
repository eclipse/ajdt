/*******************************************************************************
 * Copyright (c) 2003, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Helen Hawkins - initial version
 *     Kris De Volder - PullOutRefactoringTests
 *     Andrew Eisenberg
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.ajdt.ui.tests.actions.AddAJNatureActionTest;
import org.eclipse.ajdt.ui.tests.actions.RemoveAJNatureActionTest;
import org.eclipse.ajdt.ui.tests.ajde.AJDTErrorHandlerTest;
import org.eclipse.ajdt.ui.tests.ajde.BuildOptionsAdapterTest;
import org.eclipse.ajdt.ui.tests.ajde.ClasspathOrderTest;
import org.eclipse.ajdt.ui.tests.ajde.ProjectPropertiesTest;
import org.eclipse.ajdt.ui.tests.ajde.UIMessageHandlerTest;
import org.eclipse.ajdt.ui.tests.builder.AdviceMarkersTest;
import org.eclipse.ajdt.ui.tests.builder.AdviceMarkersTest2;
import org.eclipse.ajdt.ui.tests.builder.AdviceMarkersTest3;
import org.eclipse.ajdt.ui.tests.builder.AdviceMarkersTest4;
import org.eclipse.ajdt.ui.tests.builder.AdviceMarkersTest5;
import org.eclipse.ajdt.ui.tests.builder.AdviceMarkersTest6;
import org.eclipse.ajdt.ui.tests.builder.AdviceMarkersTest7;
import org.eclipse.ajdt.ui.tests.builder.Bug128803Test;
import org.eclipse.ajdt.ui.tests.builder.Bug151818Test;
import org.eclipse.ajdt.ui.tests.builder.Bug243376Test;
import org.eclipse.ajdt.ui.tests.builder.Bug285188DecErrorTests;
import org.eclipse.ajdt.ui.tests.builder.BuildPathTests;
import org.eclipse.ajdt.ui.tests.builder.BuilderTest;
import org.eclipse.ajdt.ui.tests.builder.ChangingMarkersTest;
import org.eclipse.ajdt.ui.tests.builder.CustomMarkersTest;
import org.eclipse.ajdt.ui.tests.builder.EnsureAJBuilderTests;
import org.eclipse.ajdt.ui.tests.builder.ITDinSeparateProjects;
import org.eclipse.ajdt.ui.tests.builder.InpathOutFolderTest;
import org.eclipse.ajdt.ui.tests.builder.InpathOutFolderTest2;
import org.eclipse.ajdt.ui.tests.builder.ProblemMarkerTest;
import org.eclipse.ajdt.ui.tests.builder.ProjectDependenciesTest;
import org.eclipse.ajdt.ui.tests.builder.ProjectDependenciesWithJarFilesTest;
import org.eclipse.ajdt.ui.tests.debug.DebugBreakpointsTests;
import org.eclipse.ajdt.ui.tests.debug.JavaConsoleHyperlinkTest;
import org.eclipse.ajdt.ui.tests.editor.AJOrganizeImportsTests;
import org.eclipse.ajdt.ui.tests.editor.AspectJBreakpointRulerActionTest;
import org.eclipse.ajdt.ui.tests.editor.AspectJEditorIconTest;
import org.eclipse.ajdt.ui.tests.editor.AspectJEditorTest;
import org.eclipse.ajdt.ui.tests.editor.codeformat.CodeFormatTest;
import org.eclipse.ajdt.ui.tests.editor.contentassist.Bug273691ContentAssist;
import org.eclipse.ajdt.ui.tests.editor.contentassist.Bug280508ContentAssist;
import org.eclipse.ajdt.ui.tests.editor.contentassist.ContentAssistTests;
import org.eclipse.ajdt.ui.tests.editor.contentassist.ContentAssistTests2;
import org.eclipse.ajdt.ui.tests.editor.contentassist.ContentAssistTests3;
import org.eclipse.ajdt.ui.tests.editor.contentassist.ContentAssistTests4;
import org.eclipse.ajdt.ui.tests.editor.contentassist.ContentAssistTests5;
import org.eclipse.ajdt.ui.tests.editor.contentassist.ITITContentAssistTests;
import org.eclipse.ajdt.ui.tests.editor.contentassist.PetClinicTests;
import org.eclipse.ajdt.ui.tests.editor.quickfix.AspectJQuickFixTest;
import org.eclipse.ajdt.ui.tests.hierarchy.ITDAwareHierarchyTests;
import org.eclipse.ajdt.ui.tests.hierarchy.ITDAwareHierarchyTests2;
import org.eclipse.ajdt.ui.tests.javamodel.Bug117327Test;
import org.eclipse.ajdt.ui.tests.javamodel.Bug154339Test;
import org.eclipse.ajdt.ui.tests.javamodel.elements.AJCompilationUnitTest2;
import org.eclipse.ajdt.ui.tests.launching.AJMainMethodSearchEngineTest;
import org.eclipse.ajdt.ui.tests.launching.LTWUtilsTest;
import org.eclipse.ajdt.ui.tests.launching.LTWUtilsTest2;
import org.eclipse.ajdt.ui.tests.lazystart.ImageDecoratorTest;
import org.eclipse.ajdt.ui.tests.model.BinaryWeavingSupportTest;
import org.eclipse.ajdt.ui.tests.preferences.AJCompilerPreferencePageTest;
import org.eclipse.ajdt.ui.tests.preferences.AJCompilerPreferencePageTest2;
import org.eclipse.ajdt.ui.tests.preferences.AJCompilerPreferencePageWorkbenchTest;
import org.eclipse.ajdt.ui.tests.preferences.AspectJFilterPreferencesTest;
import org.eclipse.ajdt.ui.tests.preferences.AspectJPreferencesTest;
import org.eclipse.ajdt.ui.tests.preferences.AspectJProjectPropertiesPageTest;
import org.eclipse.ajdt.ui.tests.preferences.Bug162211Test;
import org.eclipse.ajdt.ui.tests.ras.PluginFFDCTest;
import org.eclipse.ajdt.ui.tests.reconciling.Bug279439Reconciling;
import org.eclipse.ajdt.ui.tests.reconciling.ProblemFinderTests10;
import org.eclipse.ajdt.ui.tests.reconciling.ProblemFinderTests11;
import org.eclipse.ajdt.ui.tests.reconciling.ProblemFinderTests12;
import org.eclipse.ajdt.ui.tests.reconciling.ProblemFinderTests4;
import org.eclipse.ajdt.ui.tests.reconciling.ProblemFinderTests5;
import org.eclipse.ajdt.ui.tests.reconciling.ProblemFinderTests6;
import org.eclipse.ajdt.ui.tests.reconciling.ProblemFinderTests8;
import org.eclipse.ajdt.ui.tests.refactoring.ConvertLocalToFieldTests;
import org.eclipse.ajdt.ui.tests.refactoring.CopyPasteAJTest;
import org.eclipse.ajdt.ui.tests.refactoring.ExtractConstantTests;
import org.eclipse.ajdt.ui.tests.refactoring.ExtractLocalTests;
import org.eclipse.ajdt.ui.tests.refactoring.ITDAwareRippleSearchTests;
import org.eclipse.ajdt.ui.tests.refactoring.ITDRenameProcessorTests;
import org.eclipse.ajdt.ui.tests.refactoring.MoveTypeIntoAspectRefactoringTests;
import org.eclipse.ajdt.ui.tests.refactoring.OrganizeImportsTest;
import org.eclipse.ajdt.ui.tests.refactoring.PullOutRefactoringTests;
import org.eclipse.ajdt.ui.tests.refactoring.PushInRefactoringDeclareParentsTests;
import org.eclipse.ajdt.ui.tests.refactoring.PushInRefactoringITITTests;
import org.eclipse.ajdt.ui.tests.refactoring.PushInRefactoringRemoveAnnotationTests;
import org.eclipse.ajdt.ui.tests.refactoring.PushinRefactoringTests;
import org.eclipse.ajdt.ui.tests.refactoring.RenamePackageTest;
import org.eclipse.ajdt.ui.tests.testutils.SynchronizationUtils;
import org.eclipse.ajdt.ui.tests.testutils.TestForPredefinedProjectsTool;
import org.eclipse.ajdt.ui.tests.utils.AJDTUtilsTest;
import org.eclipse.ajdt.ui.tests.wizards.AspectJProjectWizardTest;
import org.eclipse.contribution.xref.ui.views.XReferenceView;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.intro.IIntroPart;

public class AllAJDTUITests {

    public static Test suite() {
        TestSuite suite = new TestSuite(AllAJDTUITests.class.getName());
        //$JUnit-BEGIN$

        boolean is50 = System.getProperty("java.version").startsWith("1.5"); //$NON-NLS-1$ //$NON-NLS-2$

        // internal.ui.actions tests
        suite.addTest(new TestSuite(AddAJNatureActionTest.class));
        suite.addTest(new TestSuite(RemoveAJNatureActionTest.class));

        // internal.ui.preferences tests
        suite.addTest(new TestSuite(AJCompilerPreferencePageTest.class));
        suite.addTest(new TestSuite(AJCompilerPreferencePageTest2.class));
        suite.addTest(new TestSuite(AJCompilerPreferencePageWorkbenchTest.class));
        suite.addTest(new TestSuite(AspectJFilterPreferencesTest.class));
        suite.addTest(new TestSuite(AspectJPreferencesTest.class));
        suite.addTest(new TestSuite(AspectJProjectPropertiesPageTest.class));
        suite.addTest(new TestSuite(Bug162211Test.class));

        // internal.ui.editor tests
        suite.addTest(new TestSuite(AspectJEditorTest.class));
        suite.addTest(new TestSuite(AspectJBreakpointRulerActionTest.class));
        suite.addTest(new TestSuite(AspectJEditorIconTest.class));
        suite.addTest(new TestSuite(AJOrganizeImportsTests.class));

        // code format tests
        suite.addTest(new TestSuite(CodeFormatTest.class));

        // internal.ui.ajde tests
        suite.addTest(new TestSuite(BuildOptionsAdapterTest.class));
        suite.addTest(new TestSuite(ClasspathOrderTest.class));
        suite.addTest(new TestSuite(ProjectPropertiesTest.class));
        suite.addTest(new TestSuite(AJDTErrorHandlerTest.class));
        suite.addTest(new TestSuite(UIMessageHandlerTest.class));

        // internal.ui.editor.quickfix tests
        suite.addTest(new TestSuite(AspectJQuickFixTest.class));

        // launching tests
        suite.addTest(new TestSuite(AJMainMethodSearchEngineTest.class));
        suite.addTest(new TestSuite(LTWUtilsTest.class));
        suite.addTest(new TestSuite(LTWUtilsTest2.class));

        // test classes in lazystart package
        suite.addTest(new TestSuite(ImageDecoratorTest.class));

        // ui model tests
        suite.addTest(new TestSuite(BinaryWeavingSupportTest.class));

        // content assist tests
        suite.addTest(new TestSuite(CodeTemplatesTest.class));
        suite.addTest(new TestSuite(ContentAssistTests.class));
        suite.addTest(new TestSuite(ContentAssistTests2.class));
        suite.addTest(new TestSuite(ContentAssistTests3.class));
        suite.addTest(new TestSuite(ContentAssistTests4.class));
        suite.addTest(new TestSuite(ContentAssistTests5.class));
        suite.addTest(new TestSuite(Bug273691ContentAssist.class));
        suite.addTest(new TestSuite(Bug280508ContentAssist.class));
        suite.addTest(new TestSuite(ITITContentAssistTests.class));

        // test the roo petclinic project
        suite.addTest(new TestSuite(PetClinicTests.class));

        // new aspectJ project wizard
        suite.addTest(new TestSuite(AspectJProjectWizardTest.class));

        // internal.builder tests
        suite.addTest(new TestSuite(ProjectDependenciesTest.class));
        suite.addTest(new TestSuite(ProjectDependenciesWithJarFilesTest.class));
        suite.addTest(new TestSuite(AdviceMarkersTest.class));
        suite.addTest(new TestSuite(AdviceMarkersTest2.class));
        suite.addTest(new TestSuite(AdviceMarkersTest3.class));
        suite.addTest(new TestSuite(AdviceMarkersTest4.class));
        suite.addTest(new TestSuite(AdviceMarkersTest5.class));
        suite.addTest(new TestSuite(AdviceMarkersTest6.class));
        suite.addTest(new TestSuite(AdviceMarkersTest7.class));
        suite.addTest(new TestSuite(Bug285188DecErrorTests.class));
        suite.addTest(new TestSuite(Bug128803Test.class));
        suite.addTest(new TestSuite(Bug151818Test.class));
        suite.addTest(new TestSuite(Bug243376Test.class));
        suite.addTest(new TestSuite(BuilderTest.class));
        suite.addTest(new TestSuite(BuildPathTests.class));
        suite.addTest(new TestSuite(ChangingMarkersTest.class));
        suite.addTest(new TestSuite(CustomMarkersTest.class));
        suite.addTest(new TestSuite(InpathOutFolderTest.class));
        suite.addTest(new TestSuite(InpathOutFolderTest2.class));
        suite.addTest(new TestSuite(ITDinSeparateProjects.class));
        suite.addTest(new TestSuite(ProblemMarkerTest.class));
        suite.addTest(new TestSuite(EnsureAJBuilderTests.class));

        // javamodel tests
        suite.addTest(new TestSuite(AJCompilationUnitTest2.class));
        suite.addTest(new TestSuite(Bug154339Test.class));
        if(is50) {
            suite.addTest(new TestSuite(Bug117327Test.class));
        }

        // reconciling
        // these are the reconciling tests that depend on UI components.
        // the rest have been moved to the core tests plugin
        suite.addTest(new TestSuite(ProblemFinderTests4.class));
        suite.addTest(new TestSuite(ProblemFinderTests10.class));
        suite.addTest(new TestSuite(ProblemFinderTests5.class));
        suite.addTest(new TestSuite(ProblemFinderTests6.class));
        suite.addTest(new TestSuite(ProblemFinderTests8.class));
        suite.addTest(new TestSuite(ProblemFinderTests11.class));
        suite.addTest(new TestSuite(ProblemFinderTests12.class));
        suite.addTest(new TestSuite(Bug279439Reconciling.class));


        // debug
        suite.addTest(new TestSuite(JavaConsoleHyperlinkTest.class));
        suite.addTest(new TestSuite(DebugBreakpointsTests.class));

        // ras tests
        suite.addTest(new TestSuite(PluginFFDCTest.class));

        // Refactoring
        suite.addTest(new TestSuite(RenamePackageTest.class));
        suite.addTest(new TestSuite(OrganizeImportsTest.class));
        suite.addTest(new TestSuite(CopyPasteAJTest.class));
        suite.addTest(new TestSuite(PushinRefactoringTests.class));
        suite.addTest(new TestSuite(PushInRefactoringRemoveAnnotationTests.class));
        suite.addTest(new TestSuite(PushInRefactoringITITTests.class));
        suite.addTest(new TestSuite(PushInRefactoringDeclareParentsTests.class));
        suite.addTest(new TestSuite(PullOutRefactoringTests.class));
        suite.addTest(new TestSuite(ITDAwareRippleSearchTests.class));
        suite.addTest(new TestSuite(ITDRenameProcessorTests.class));
        suite.addTest(new TestSuite(ExtractLocalTests.class));
        suite.addTest(new TestSuite(ExtractConstantTests.class));
        suite.addTest(new TestSuite(ConvertLocalToFieldTests.class));
        suite.addTest(new TestSuite(MoveTypeIntoAspectRefactoringTests.class));

        // Hierarchies
        suite.addTest(new TestSuite(ITDAwareHierarchyTests.class));
        suite.addTest(new TestSuite(ITDAwareHierarchyTests2.class));


        suite.addTest(new TestSuite(ErrorLogTest.class));
        suite.addTest(new TestSuite(VerificationTest.class));
        suite.addTest(new TestSuite(Bug106813Test.class));


        // test the predefined project tool
        suite.addTest(new TestSuite(TestForPredefinedProjectsTool.class));


        // internal.core tests
        suite.addTest(new TestSuite(AJDTUtilsTest.class));



        //$JUnit-END$
        return suite;
    }

    /**
     * Prevents AJDTPrefWizard from popping up during tests and simulates normal
     * usage by closing the welcome page, and opening the java perspective
     */
    public static synchronized void setupAJDTPlugin() {
        if (setupDone) {
            return;
        }

        IWorkbenchWindow window = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow();

        // close welcome page
        IIntroPart intro = PlatformUI.getWorkbench().getIntroManager()
                .getIntro();
        if (intro != null) {
            try {
                PlatformUI.getWorkbench().getIntroManager().setIntroStandby(intro, true);
            } catch (NullPointerException npe) {
                // don't care about this
            }
        }

        // open Java perspective
        try {
            PlatformUI.getWorkbench().showPerspective(JavaUI.ID_PERSPECTIVE,
                    window);
        } catch (WorkbenchException ignored) {
        }

        // open Cross Ref view
        try {
            window.getActivePage().showView(XReferenceView.ID);
        } catch (PartInitException ignored) {
        }



        // open Console view
        try {
            window.getActivePage().showView("org.eclipse.ui.console.ConsoleView"); //$NON-NLS-1$
        } catch (PartInitException ignored) {
        }

        waitForJobsToComplete();
        setupDone = true;
    }

    private static void waitForJobsToComplete() {
        SynchronizationUtils.joinBackgroudActivities();
    }

    private static boolean setupDone = false;
}
