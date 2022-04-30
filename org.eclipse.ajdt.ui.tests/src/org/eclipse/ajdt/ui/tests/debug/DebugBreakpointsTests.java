/*******************************************************************************
 * Copyright (c) 2010 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial version
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests.debug;

import java.io.InputStream;

import org.eclipse.ajdt.core.ReflectionUtils;
import org.eclipse.ajdt.internal.debug.ui.actions.ToggleBreakpointAdapter;
import org.eclipse.ajdt.internal.ui.editor.AspectJEditor;
import org.eclipse.ajdt.ui.tests.StringInputStream;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.ajdt.ui.tests.testutils.SynchronizationUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.debug.ui.actions.ActionDelegateHelper;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;

/**
 * Tests the setting of breakpoints in AJ files
 * @author Andrew Eisenberg
 * @created Jul 12, 2010
 */
public class DebugBreakpointsTests extends UITestCase {

    private static final String BREAKPOINT_CLASS_NAME = "DebugBreakpointsData.aj";

    private ToggleBreakpointAdapter adapter;

    private ICompilationUnit unit;

    private AspectJEditor editor;

    private String text;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        IProject project = createPredefinedProject("DefaultEmptyProject");
        IFile file = project.getFile("src/" + BREAKPOINT_CLASS_NAME);
      try (InputStream input = new StringInputStream(DATA)) {
        file.create(input, true, null);
        unit = JavaCore.createCompilationUnitFrom(file);
      }
      //ignore

      text = DATA;
        adapter = new ToggleBreakpointAdapter();

        editor = (AspectJEditor) EditorUtility.openInEditor(unit);

        ReflectionUtils.setPrivateField(ActionDelegateHelper.class, "fTextEditor", ActionDelegateHelper.getDefault(), editor);

        unit.becomeWorkingCopy(null);
        unit.makeConsistent(null);
        SynchronizationUtils.joinBackgroudActivities();
    }

    public void testBreakpointInAJFile1() throws Exception {
        doBreakpointTest(1);
    }

    public void testBreakpointInAJFile2() throws Exception {
        doBreakpointTest(2);
    }

    public void testBreakpointInAJFile3() throws Exception {
        doBreakpointTest(3);
    }

    public void testBreakpointInAJFile4() throws Exception {
        doBreakpointTest(4);
    }

    public void testBreakpointInAJFile5() throws Exception {
        doBreakpointTest(5);
    }

    public void testBreakpointInAJFile6() throws Exception {
        doBreakpointTest(6);
    }

    public void testBreakpointInAJFile7() throws Exception {
        doBreakpointTest(7);
    }

    public void testBreakpointInAJFile8() throws Exception {
        doBreakpointTest(8);
    }

    public void testBreakpointInAJFile9() throws Exception {
        doBreakpointTest(9);
    }

    private void doBreakpointTest(int i) throws Exception {
        ITextSelection selection = new TextSelection(new Document(text), text.indexOf("// " + i)-3, 3);
        boolean canToggle = adapter.canToggleLineBreakpoints(editor, selection);
        assertTrue("Should be able to toggle breakpoint at section " + i, canToggle);

        int initialNumBreakpoints;
        IBreakpointManager breakpointManager = DebugPlugin.getDefault().getBreakpointManager();
        IBreakpoint[] breakpoints = breakpointManager.getBreakpoints();
        initialNumBreakpoints = breakpoints.length;
        try {
            adapter.toggleLineBreakpoints(editor, selection);
            SynchronizationUtils.joinBackgroudActivities();

        } finally {
            IBreakpoint[] newBreakpoints = breakpointManager.getBreakpoints();
            assertEquals("Unexpected number of breakpoints", initialNumBreakpoints+1, newBreakpoints.length);
            for (IBreakpoint breakpoint : newBreakpoints) {
                breakpointManager.removeBreakpoint(breakpoint, true);
            }
            assertEquals("Should have deleted all breakpoints", 0, breakpointManager.getBreakpoints().length);
        }
    }

    private final static String DATA =
        "public aspect DebugBreakpointsData {\n\n" +
        "\n\n" +
        "    // test AJ-specific locations\n\n" +
        "    before() : execution(void I.f1()) {\n\n" +
        "        int x = 9; // 1\n\n" +
        "        x++;\n\n" +
        "    }\n\n" +
        "    after() : execution(void I.f2()){\n\n" +
        "        int x = 9; // 2\n\n" +
        "        x++;\n\n" +
        "    }\n\n" +
        "    void around() : execution(void I.f3()) {\n\n" +
        "        int x = 9; // 3\n\n" +
        "        x++;\n\n" +
        "    }\n" +
        "    void I.p() {\n" +
        "        int x = 9; // 4\n" +
        "        x++;\n" +
        "    }\n" +
        "    String I.p = \"xxx\" + \n" +
        "        \"xxx\" + // 5\n" +
        "        \"xxx\";\n" +
        "    \n" +
        "    I.new() {\n" +
        "        this();\n" +
        "        int x = 9; // 6\n" +
        "        x++;\n" +
        "    }\n" +
        "    \n" +
        "    // now test some normal Java breakpoint locations\n" +
        "    void foo() {\n" +
        "        int x = 9; // 7\n" +
        "        x++;\n" +
        "    }\n" +
        "    String foo = \"xxx\" + \n" +
        "    \"xxx\" + // 8\n" +
        "    \"xxx\";\n" +
        "    \n" +
        "    {\n" +
        "        int x = 9; // 8\n" +
        "        x++;\n" +
        "    }\n" +
        "    static {\n" +
        "        int x = 9; // 9\n" +
        "        x++;\n" +
        "    }\n" +
        "}\n" +
        "class I { \n" +
        "    void f1() { }\n" +
        "    void f2() { }\n" +
        "    void f3() { }\n" +
        "}";
}
