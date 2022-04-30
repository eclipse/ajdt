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

package org.eclipse.ajdt.core.tests.refactoring;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor;
import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;

/**
 * @author Andrew Eisenberg
 * @created Apr 23, 2010
 *
 */
public class ITDRenameParticipantRefactoringTest extends
        AbstractAJDTRefactoringTest {


    public void testSimpleRename1() throws Exception {
        performRefactoringAndUndo("xxx",
        new String[] {
                "F.java"
        },
        new String[] {
                "class F {\n int x; }"
        },
        new String[] {
                "class F {\n int xxx; }"
        },
        true, true
        );
    }
    public void testSimpleRename2() throws Exception {
        performRefactoringAndUndo("xxx",
                new String[] {
                "F.java"
        },
        new String[] {
                "class F {\n int x; public int getX() { return x; } public void setX(int x) { this.x = x; } }"
        },
        new String[] {
                "class F {\n int xxx; public int getXxx() { return xxx; } public void setXxx(int x) { this.xxx = x; } }"
        },
        true, true
        );
    }

    public void testITDRename1() throws Exception {
        performRefactoringAndUndo("xxx",
                new String[] {
                "F.java",
                "A.aj"
        },
        new String[] {
                "class F {\n int x; }",
                "aspect A {\n public int F.getX() { return x; }\n public void F.setX(int x) { this.x = x; } }"
        },
        new String[] {
                "class F {\n int xxx; }",
                "aspect A {\n public int F.getXxx() { return xxx; }\n public void F.setXxx(int x) { this.xxx = x; } }"
        },
        true, true
        );
    }
    public void testITDRename2() throws Exception {
        performRefactoringAndUndo("xxx",
                new String[] {
                "F.java",
                "A.aj",
                "Other.java"
        },
        new String[] {
                "class F {\n int x; }",
                "aspect A {\n public int F.getX() { return x; }\n public void F.setX(int x) { this.x = x; } }",
                "class Other { void foo() {\n new F().setX(new F().getX()); new F().x++; } }"
        },
        new String[] {
                "class F {\n int xxx; }",
                "aspect A {\n public int F.getXxx() { return xxx; }\n public void F.setXxx(int x) { this.xxx = x; } }",
                "class Other { void foo() {\n new F().setXxx(new F().getXxx()); new F().xxx++; } }"
        },
        true, true
        );
    }
    public void testITDRename3() throws Exception {
        performRefactoringAndUndo("xxx",
                new String[] {
                "F.java",
                "A.aj",
                "Other.java"
        },
        new String[] {
                "class F {\n String x; }",
                "aspect A {\n public String F.getX() { return x; }\n public void F.setX(String x) { this.x = x; } }",
                "class Other {\n void foo() {\n new F().setX(new F().getX()); new F().x = \"\"; } }"
        },
        new String[] {
                "class F {\n String xxx; }",
                "aspect A {\n public String F.getXxx() { return xxx; }\n public void F.setXxx(String x) { this.xxx = x; } }",
                "class Other {\n void foo() {\n new F().setXxx(new F().getXxx()); new F().xxx = \"\"; } }"
        },
        true, true
        );
    }
    public void testITDRename4() throws Exception {
        performRefactoringAndUndo("xxx",
                new String[] {
                "a",
                "b",
                "c"
        },
        new String[] {
                "F.java",
                "A.aj",
                "Other.java"
        },
        new String[] {
                "package a;\n\npublic class F {\n public String x; }",
                "package b;\n\nimport a.F; privileged aspect A {\n public String F.getX() { return x; }\n public void F.setX(String x) { this.x = x; } }",
                "package c;\n\nimport a.F; class Other {\n void foo() {\n new F().setX(new F().getX()); new F().x = \"\"; } }"
        },
        new String[] {
                "package a;\n\npublic class F {\n public String xxx; }",
                "package b;\n\nimport a.F; privileged aspect A {\n public String F.getXxx() { return xxx; }\n public void F.setXxx(String x) { this.xxx = x; } }",
                "package c;\n\nimport a.F; class Other {\n void foo() {\n new F().setXxx(new F().getXxx()); new F().xxx = \"\"; } }"
        },
        true, true
        );
    }
    public void testITDRenamePrivileged1() throws Exception {
        performRefactoringAndUndo("xxx",
                new String[] {
                "a",
                "b",
                "c"
        },
        new String[] {
                "F.java",
                "A.aj",
                "Other.java"
        },
        new String[] {
                "package a;\n\npublic class F {\n private String x; }",
                "package b;\n\nimport a.F; privileged aspect A {\n public String F.getX() { return x; }\n public void F.setX(String x) { this.x = x; } }",
                "package c;\n\nimport a.F; class Other {\n void foo() {\n new F().setX(new F().getX()); } }"
        },
        new String[] {
                "package a;\n\npublic class F {\n private String xxx; }",
                "package b;\n\nimport a.F; privileged aspect A {\n public String F.getXxx() { return xxx; }\n public void F.setXxx(String x) { this.xxx = x; } }",
                "package c;\n\nimport a.F; class Other {\n void foo() {\n new F().setXxx(new F().getXxx()); } }"
        },
        true, true
        );
    }

    // rename not working for private members in privileged aspects
    // outside of ITDs.
    public void _testITDRenamePrivileged2() throws Exception {
        performRefactoringAndUndo("xxx",
                new String[] {
                "a",
                "b"
        },
        new String[] {
                "F.java",
                "A.aj"
        },
        new String[] {
                "package a;\n\npublic class F {\n private String x; }",
                "package b;\n\nimport a.F; privileged aspect A {\n public String getX() { return new F().x; }\n public void setX(String x) { new F().x = x; } }"
        },
        new String[] {
                "package a;\n\npublic class F {\n private String xxx; }",
                "package b;\n\nimport a.F; privileged aspect A {\n public String getX() { return new F().xxx; }\n public void setX(String x) { new F().xxx = x; } }"
        },
        true, true
        );
    }
    public void testITDRenameGetterOnly1() throws Exception {
        performRefactoringAndUndo("xxx",
                new String[] {
                "F.java",
                "A.aj"
        },
        new String[] {
                "class F {\n int x; }",
                "aspect A {\n public int F.getX() { return x; }\n public void F.setX(int x) { this.x = x; } }"
        },
        new String[] {
                "class F {\n int xxx; }",
                "aspect A {\n public int F.getXxx() { return xxx; }\n public void F.setX(int x) { this.xxx = x; } }"
        },
        true, false
        );
    }
    public void testITDRenameGetterOnly2() throws Exception {
        performRefactoringAndUndo("xxx",
                new String[] {
                "F.java",
                "A.aj",
                "Other.java"
        },
        new String[] {
                "class F {\n int x; }",
                "aspect A {\n public int F.getX() { return x; }\n public void F.setX(int x) { this.x = x; } }",
                "class Other {\n void foo() {\n new F().setX(new F().getX()); new F().x++; } }"
        },
        new String[] {
                "class F {\n int xxx; }",
                "aspect A {\n public int F.getXxx() { return xxx; }\n public void F.setX(int x) { this.xxx = x; } }",
                "class Other {\n void foo() {\n new F().setX(new F().getXxx()); new F().xxx++; } }"
        },
        true, false
        );
    }
    public void testITDRenameSetterOnly1() throws Exception {
        performRefactoringAndUndo("xxx",
                new String[] {
                "F.java",
                "A.aj"
        },
        new String[] {
                "class F {\n int x; }",
                "aspect A {\n public int F.getX() { return x; }\n public void F.setX(int x) { this.x = x; } }"
        },
        new String[] {
                "class F {\n int xxx; }",
                "aspect A {\n public int F.getX() { return xxx; }\n public void F.setXxx(int x) { this.xxx = x; } }"
        },
        false, true
        );
    }
    public void testITDRenameSetterOnly2() throws Exception {
        performRefactoringAndUndo("xxx",
                new String[] {
                "F.java",
                "A.aj",
                "Other.java"
        },
        new String[] {
                "class F {\n int x; }",
                "aspect A {\n public int F.getX() { return x; }\n public void F.setX(int x) { this.x = x; } }",
                "class Other {\n void foo() {\n new F().setX(new F().getX()); new F().x++; } }"
        },
        new String[] {
                "class F {\n int xxx; }",
                "aspect A {\n public int F.getX() { return xxx; }\n public void F.setXxx(int x) { this.xxx = x; } }",
                "class Other {\n void foo() {\n new F().setXxx(new F().getX()); new F().xxx++; } }"
        },
        false, true
        );
    }



    /**
     * Assumes that we are renaming the first field of the first CU
     * @throws Exception
     */
    private void performRefactoringAndUndo(String newName, String[] cuNames, String[] initialContents, String[] finalContents, boolean renameGetter, boolean renameSetter) throws Exception {
        String[] packNames = new String[cuNames.length];
        Arrays.fill(packNames, "");
        performRefactoringAndUndo(newName, packNames, cuNames, initialContents, finalContents, renameGetter, renameSetter);
    }
    private void performRefactoringAndUndo(String newName, String[] packNames, String[] cuNames, String[] initialContents, String[] finalContents, boolean renameGetter, boolean renameSetter) throws Exception {
        ICompilationUnit[] units = createUnits(packNames, cuNames, initialContents);

        IField field = getFirstField(units);

        RenameJavaElementDescriptor descriptor = RefactoringSignatureDescriptorFactory
                .createRenameJavaElementDescriptor(IJavaRefactorings.RENAME_FIELD);
        descriptor.setUpdateReferences(true);
        descriptor.setJavaElement(field);
        descriptor.setNewName(newName);

        RenameRefactoring refactoring = (RenameRefactoring) createRefactoring(descriptor);
        RefactoringProcessor processor = refactoring.getProcessor();
        if (renameGetter) {
            setRename(processor, true);
        }
        if (renameSetter) {
            setRename(processor, false);
        }
        RefactoringStatus result = performRefactoring(refactoring, true, true);

        result = ignoreKnownErrors(result);

        assertTrue("Refactoring produced an error: " + result, result.isOK());

        assertContents(units, finalContents);

        // undo
        assertTrue("anythingToUndo", RefactoringCore.getUndoManager()
                .anythingToUndo());
      assertFalse("! anythingToRedo", RefactoringCore.getUndoManager()
        .anythingToRedo());

        RefactoringCore.getUndoManager().performUndo(null,
                new NullProgressMonitor());
        assertContents(units, initialContents);

        // redo
      assertFalse("! anythingToUndo", RefactoringCore.getUndoManager()
        .anythingToUndo());
        assertTrue("anythingToRedo", RefactoringCore.getUndoManager()
                .anythingToRedo());
        RefactoringCore.getUndoManager().performRedo(null,
                new NullProgressMonitor());
        assertContents(units, finalContents);
    }

    private Field fRenameGetterField;
    private Field fRenameSetterField;
    private void setRename(RefactoringProcessor processor, boolean getter) {
        try {
            Field thisField;
            if (getter) {
                if (fRenameGetterField == null) {
                    fRenameGetterField = processor.getClass().getDeclaredField("fRenameGetter");
                    fRenameGetterField.setAccessible(true);
                }
                thisField = fRenameGetterField;
            } else {
                if (fRenameSetterField == null) {
                    fRenameSetterField = processor.getClass().getDeclaredField("fRenameSetter");
                    fRenameSetterField.setAccessible(true);
                }
                thisField = fRenameSetterField;
            }
            thisField.setBoolean(processor, true);
        } catch (Exception ignored) {
        }
    }

}
