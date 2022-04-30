/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sian January - adapted from AJProjectModelTest
 *******************************************************************************/
package org.eclipse.ajdt.core.tests.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aspectj.asm.AsmManager;
import org.aspectj.asm.IProgramElement;
import org.aspectj.asm.IRelationship;
import org.aspectj.bridge.ISourceLocation;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.javaelements.AJCodeElement;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.ajdt.core.model.AJRelationshipType;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaElement;

/**
 * More project model tests
 */
public class AJProjectModelTest2 extends AJDTCoreTestCase {

	IProject project;
    AJProjectModelFacade model;

	AJCodeElement[] ajCodeElements;

	private static final int LINE1 = 23;

	private static final int LINE2 = 24;

	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();

		project = createPredefinedProject("AJProject83082Second"); //$NON-NLS-1$
        model = AJProjectModelFactory.getInstance().getModelForProject(project);

		IFolder src = project.getFolder("src"); //$NON-NLS-1$
		IFolder wpstest = src.getFolder("wpstest"); //$NON-NLS-1$
		IFolder aspectjPackage = wpstest.getFolder("aspectj"); //$NON-NLS-1$
		IFile main = aspectjPackage.getFile("Main.java"); //$NON-NLS-1$

		AsmManager asm = AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project.getProject()).getModel();
		Map<? extends Integer, ? extends List<IProgramElement>> annotationsMap = asm.getInlineAnnotations(
				main.getRawLocation().toOSString(), true, true);
		ajCodeElements = createAJCodeElements(annotationsMap);
	}

	public void testIsAdvised() {
		IJavaElement parent = ajCodeElements[0].getParent();
		assertFalse(
				"Parent shouldn't be an AJCodeElement", (parent instanceof AJCodeElement)); //$NON-NLS-1$
		assertTrue(
				"parent is advised because subelement is advised", model.isAdvised(parent)); //$NON-NLS-1$
	}

	public void testGetLineNumber() {
		IJavaElement je1 = ajCodeElements[0];
		IJavaElement je2 = ajCodeElements[1];
		int line1 = model.getJavaElementLineNumber(je1);
		int line2 = model.getJavaElementLineNumber(je2);
    assertEquals("The first IJavaElement should be located at line " + LINE1 //$NON-NLS-1$
                 + " got: " + line1, LINE1, line1); //$NON-NLS-1$
    assertEquals("The second IJavaElement should be located at line " + LINE2 //$NON-NLS-1$
                 + " got: " + line2, LINE2, line2); //$NON-NLS-1$
	}

	public void testGetAllRelationships() {
		AJRelationshipType[] rels = new AJRelationshipType[] { AJRelationshipManager.ADVISES };
		List<IRelationship> allRels = model.getRelationshipsForProject(rels);

		IJavaElement je1 = ajCodeElements[0];
		IJavaElement je2 = ajCodeElements[1];
		int advisedCount1 = 0;
		int advisedCount2 = 0;
    for (IRelationship rel : allRels) {
      for (String ipeHandle : rel.getTargets()) {
        IJavaElement target = model.programElementToJavaElement(ipeHandle);
        if (target.equals(je1)) {
          advisedCount1++;
        }
        else if (target.equals(je2)) {
          advisedCount2++;
        }
      }
    }
    assertEquals("The first IJavaElement should be advised twice", 2, advisedCount1); //$NON-NLS-1$
    assertEquals("The second IJavaElement should be advised twice", 2, advisedCount2); //$NON-NLS-1$

		rels = new AJRelationshipType[] { AJRelationshipManager.DECLARED_ON };
		allRels = model.getRelationshipsForProject(rels);
		if (allRels != null && allRels.size() > 0) {
			fail("There should be no DECLARED_ON relationships"); //$NON-NLS-1$
		}
	}

	private AJCodeElement[] createAJCodeElements(Map<?, ? extends List<? extends IProgramElement>> annotationsMap) {
		AJCodeElement[] arrayOfajce = new AJCodeElement[2];
		Set<?> keys = annotationsMap.keySet();
    for (Object key : keys) {
      List<? extends IProgramElement> annotations = annotationsMap.get(key);
      for (IProgramElement node : annotations) {
        ISourceLocation sl = node.getSourceLocation();
        if (node
              .toLinkLabelString(false)
              .equals(
                "Main: method-call(void java.io.PrintStream.println(java.lang.String))") //$NON-NLS-1$
            && (sl.getLine() == LINE1))
        {

          IJavaElement ije = model.programElementToJavaElement(node);
          if (ije instanceof AJCodeElement) {
            arrayOfajce[0] = (AJCodeElement) ije;
          }
        }
        else if (node
                   .toLinkLabelString(false)
                   .equals("Main: method-call(void java.io.PrintStream.println(java.lang.String))") //$NON-NLS-1$
                 && (sl.getLine() == LINE2))
        {

          IJavaElement ije = model.programElementToJavaElement(node);
          if (ije instanceof AJCodeElement) {
            arrayOfajce[1] = (AJCodeElement) ije;
          }
        }
      }
    }
		return arrayOfajce;
	}
}
