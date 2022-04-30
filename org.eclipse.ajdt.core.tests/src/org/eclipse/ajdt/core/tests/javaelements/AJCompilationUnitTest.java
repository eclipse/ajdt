/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Luzius Meisser - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.core.tests.javaelements;

import java.util.Set;

import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.SourceType;

/**
 *
 * @author Luzius Meisser
 */
public class AJCompilationUnitTest extends AbstractTestCase {

	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testGetChildren() throws JavaModelException{
		IJavaElement[] children = unit.getChildren();

		if (children == null)
			fail("Could not get children of CompilationUnit"); //$NON-NLS-1$

		if (children.length != 1)
			fail("Wrong number of children in CompilationUnit. (" + children.length + ")"); //$NON-NLS-1$ //$NON-NLS-2$

	}

	public void testGetBuffer() throws JavaModelException{
		IBuffer fake = unit.getBuffer();
		unit.requestOriginalContentMode();
		IBuffer orig = unit.getBuffer();
		unit.discardOriginalContentMode();
		if (fake == orig)
			fail("CompilationUnit did not provide different buffers in different modes."); //$NON-NLS-1$


	}

	public void testOriginalContentMode() throws JavaModelException {
		if (unit.isInOriginalContentMode())
			fail("AJCompilationUnit should not be in original mode at startup."); //$NON-NLS-1$

		unit.requestOriginalContentMode();

		if (!unit.isInOriginalContentMode())
			fail("Request originalContentMode did not succeed."); //$NON-NLS-1$

		unit.discardOriginalContentMode();

		if (unit.isInOriginalContentMode())
			fail("Discard originalContentMode did not succeed."); //$NON-NLS-1$

	}


	public void testFindPrimaryType() {
		IType type = unit.findPrimaryType();

		if (!(type instanceof SourceType))
			fail("Incorrect class of main type."); //$NON-NLS-1$

		if (!"Aspect".equals(type.getElementName())) //$NON-NLS-1$
			fail("Incorrect main type."); //$NON-NLS-1$

	}

	public void testGetMainTypeName() {
		if (!"Aspect".equals(new String(unit.getMainTypeName()))) //$NON-NLS-1$
			fail("Could not get correct main type name."); //$NON-NLS-1$
	}

    public void testGetAllAspects() throws Exception {
        AJCompilationUnit unit2 = AJCompilationUnitManager.INSTANCE.getAJCompilationUnit(this.myProject.getFile("src/Aspect2.aj"));
        IType[] aspects = unit2.getAllAspects();

        assertEquals("Should have found 4 aspects in " + unit2, 4, aspects.length);
        assertEquals("Aspect2 should be first aspect in " + unit2, "Aspect2", aspects[0].getElementName());
        assertEquals("AtAspect1 should be second aspect in " + unit2, "AtAspect1", aspects[1].getElementName());
        assertEquals("AtAspect2 should be third aspect in " + unit2, "AtAspect2", aspects[2].getElementName());
        assertEquals("Aspect3 should be fourth aspect in " + unit2, "Aspect3", aspects[3].getElementName());
    }

    public void testGetAllAspectsJava() {
        ICompilationUnit unit2 = JavaCore
            .createCompilationUnitFrom(this.myProject.getFile("src/Aspect3.java"));
        Set<IType> /*IJavaElement*/ aspects = AJProjectModelFactory.getInstance().getModelForJavaElement(unit2)
            .aspectsForFile(unit2);

        assertEquals("Should have found 4 aspects in " + unit2, 4, aspects.size());
        boolean found = false;
      for (IType type : aspects) {
        if (type.getElementName().equals("Aspect3")) {
          found = true;
        }
      }
        assertTrue("Aspect3 should be in " + unit2, found);

        foundAspect("Aspect3", unit2, aspects);
        foundAspect("AtAspect1", unit2, aspects);
        foundAspect("AtAspect2", unit2, aspects);
        foundAspect("Aspect4", unit2, aspects);
    }

    private void foundAspect(String name, ICompilationUnit unit2, Set<IType> aspects) {
        boolean found;
        found = false;
      for (IType type : aspects) {
        if (type.getElementName().equals(name)) {
          found = true;
        }
      }
        assertTrue(name + " should be in " + unit2, found);
    }

}
