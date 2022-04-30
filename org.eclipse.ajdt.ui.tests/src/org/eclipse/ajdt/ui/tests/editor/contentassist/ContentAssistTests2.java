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
package org.eclipse.ajdt.ui.tests.editor.contentassist;

import org.eclipse.ajdt.core.AspectJCore;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.internal.core.AJWorkingCopyOwner;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.internal.core.JavaModelManager;

/**
 * Tests a whole bunch of ways that ITDs can show up in content assist
 * @author Andrew Eisenberg
 *
 */
public class ContentAssistTests2 extends UITestCase {
    AJCompilationUnit hasITDsUnit;
    AJCompilationUnit usesITDsUnit;

    String hasITDsContents;
    String usesITDsContents;

    protected void setUp() throws Exception {
        super.setUp();
        IProject proj = createPredefinedProject("ITDContentAssist");

        hasITDsUnit = (AJCompilationUnit) AspectJCore.create(proj.getFile("src/hasitds/HasITDs.aj"));
        hasITDsUnit.becomeWorkingCopy(null);
        JavaModelManager.getJavaModelManager().getPerWorkingCopyInfo(hasITDsUnit, true, false, null);

        usesITDsUnit = (AJCompilationUnit) AspectJCore.create(proj.getFile("src/uses/UsesITDs.aj"));
        usesITDsUnit.becomeWorkingCopy(null);
        JavaModelManager.getJavaModelManager().getPerWorkingCopyInfo(usesITDsUnit, true, false, null);

        hasITDsUnit.requestOriginalContentMode();
        hasITDsContents = new String(hasITDsUnit.getContents());
        hasITDsUnit.discardOriginalContentMode();

        usesITDsUnit.requestOriginalContentMode();
        usesITDsContents = new String(usesITDsUnit.getContents());
        usesITDsUnit.discardOriginalContentMode();
    }

    public void testITDField() throws Exception {
        MockCompletionRequestor requestor = new MockCompletionRequestor();
        int offset = hasITDsContents.indexOf("this.lis") + "this.lis".length();
        hasITDsUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

        assertEquals("Should have 1 proposal, but found:\n" + requestor, 1, requestor.accepted.size());
        assertEquals("Proposal should have been the 'list' field",
                "list", new String(requestor.accepted.get(0).getName()));
    }

    public void testITDFieldInField() throws Exception {
        MockCompletionRequestor requestor = new MockCompletionRequestor();
        int offset = hasITDsContents.indexOf("this.list.addAll") + "this.list.addAll".length();
        hasITDsUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

        assertEquals("Should have 2 proposals, but found:\n" + requestor, 2, requestor.accepted.size());
        assertEquals("Proposal should have been the 'addAll' method\n" + requestor.accepted.get(0),
                "addAll", new String(requestor.accepted.get(0).getName()));
        assertEquals("Proposal should have been the 'addAll' method\n" + requestor.accepted.get(1),
                "addAll", new String(requestor.accepted.get(1).getName()));
    }

    public void testITDMethod() throws Exception {
        MockCompletionRequestor requestor = new MockCompletionRequestor();
        int offset = hasITDsContents.indexOf("this.makeL") + "this.makeL".length();
        hasITDsUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

        assertEquals("Should have 1 proposal, but found:\n" + requestor, 1, requestor.accepted.size());
        assertEquals("Proposal should have been the 'makeList' method\n" + requestor.accepted.get(0),
                "makeList", new String(requestor.accepted.get(0).getName()));
    }

    public void testITDConstructor() throws Exception {
        MockCompletionRequestor requestor = new MockCompletionRequestor();
        int offset = hasITDsContents.indexOf("new HasITDs(") + "new HasITDs(".length();
        hasITDsUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

        assertEquals("Should have 2 proposals, but found:\n" + requestor, 2, requestor.accepted.size());

        // the anonymous class decl
        assertEquals("Signature of proposal should have been the 'HasITDs' constructor\n" + requestor.accepted.get(0),
                "HasITDs", new String(requestor.accepted.get(0).getName()));
        // the constructor
        assertEquals("Proposal should have been the 'HasITDs' constructor\n" + requestor.accepted.get(1),
                "Lhasitds.HasITDs;", new String(requestor.accepted.get(1).getDeclarationSignature()));
    }

    public void testFromDeclareParent() throws Exception {
        MockCompletionRequestor requestor = new MockCompletionRequestor();
        int offset = hasITDsContents.indexOf("this.insid") + "this.insid".length();
        hasITDsUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

        assertEquals("Should have 1 proposal, but found:\n" + requestor, 1, requestor.accepted.size());

        assertEquals("Signature of proposal should have been the 'Foo.inside' field\n" + requestor.accepted.get(0),
                "inside", new String(requestor.accepted.get(0).getName()));
    }

    public void testFromITDOnDeclareParent() throws Exception {
        MockCompletionRequestor requestor = new MockCompletionRequestor();
        int offset = hasITDsContents.indexOf("this.valu") + "this.valu".length();
        hasITDsUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

        assertEquals("Should have 1 proposal, but found:\n" + requestor, 1, requestor.accepted.size());

        assertEquals("Signature of proposal should have been the 'Foo.value' field\n" + requestor.accepted.get(0),
                "value", new String(requestor.accepted.get(0).getName()));
    }


    // the remaining tests try the same thing, but in a different class

    public void testITDFieldInOtherClass() throws Exception {
        MockCompletionRequestor requestor = new MockCompletionRequestor();
        int offset = usesITDsContents.indexOf("h.lis") + "h.lis".length();
        usesITDsUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

        assertEquals("Should have 1 proposal, but found:\n" + requestor, 1, requestor.accepted.size());
        assertEquals("Proposal should have been the 'list' field",
                "list", new String(requestor.accepted.get(0).getName()));
    }

    public void testITDFieldInFieldInOtherClass() throws Exception {
        MockCompletionRequestor requestor = new MockCompletionRequestor();
        int offset = usesITDsContents.indexOf("h.list.addAl") + "h.list.addAl".length();
        usesITDsUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

        assertEquals("Should have 2 proposals, but found:\n" + requestor, 2, requestor.accepted.size());
        assertEquals("Proposal should have been the 'addAll' method\n" + requestor.accepted.get(0),
                "addAll", new String(requestor.accepted.get(0).getName()));
        assertEquals("Proposal should have been the 'addAll' method\n" + requestor.accepted.get(1),
                "addAll", new String(requestor.accepted.get(1).getName()));
    }

    public void testITDMethodInOtherClass() throws Exception {
        MockCompletionRequestor requestor = new MockCompletionRequestor();
        int offset = usesITDsContents.indexOf("h.makeL") + "h.makeL".length();
        usesITDsUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

        assertEquals("Should have 1 proposal, but found:\n" + requestor, 1, requestor.accepted.size());
        assertEquals("Proposal should have been the 'makeList' method\n" + requestor.accepted.get(0),
                "makeList", new String(requestor.accepted.get(0).getName()));
    }

    public void testITDConstructorInOtherClass() throws Exception {
        MockCompletionRequestor requestor = new MockCompletionRequestor();
        int offset = usesITDsContents.indexOf("new HasITDs(") + "new HasITDs(".length();
        usesITDsUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

        assertEquals("Should have 2 proposals, but found:\n" + requestor, 2, requestor.accepted.size());

        // the anonymous class decl
        assertEquals("Signature of proposal should have been the 'HasITDs' constructor\n" + requestor.accepted.get(0),
                "HasITDs", new String(requestor.accepted.get(0).getName()));
        // the constructor
        assertEquals("Proposal should have been the 'HasITDs' constructor\n" + requestor.accepted.get(1),
                "Lhasitds.HasITDs;", new String(requestor.accepted.get(1).getDeclarationSignature()));
    }

    public void testFromDeclareParentInOtherClass() throws Exception {
        MockCompletionRequestor requestor = new MockCompletionRequestor();
        int offset = usesITDsContents.indexOf("h.insid") + "h.insid".length();
        usesITDsUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

        assertEquals("Should have 1 proposal, but found:\n" + requestor, 1, requestor.accepted.size());

        assertEquals("Signature of proposal should have been the 'Foo.inside' field\n" + requestor.accepted.get(0),
                "inside", new String(requestor.accepted.get(0).getName()));
    }

    public void testFromITDOnDeclareParentInOtherClass() throws Exception {
        MockCompletionRequestor requestor = new MockCompletionRequestor();
        int offset = usesITDsContents.indexOf("h.valu") + "h.valu".length();
        usesITDsUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

        assertEquals("Should have 1 proposal, but found:\n" + requestor, 1, requestor.accepted.size());

        assertEquals("Signature of proposal should have been the 'Foo.value' field\n" + requestor.accepted.get(0),
                "value", new String(requestor.accepted.get(0).getName()));
    }

}
