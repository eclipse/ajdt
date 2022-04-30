/*******************************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sian January - adapted from AJModelTest
 *******************************************************************************/

package org.eclipse.ajdt.core.tests.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aspectj.asm.AsmManager;
import org.aspectj.asm.IProgramElement;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

/**
 * Tests for mapping between IProgramElement and IJavaElements where aspects are
 * contained in .java files
 */
public class AJModelTest3 extends AJDTCoreTestCase {

	public void testProgramElementToJavaElementDemo() throws Exception {
		IProject project = createPredefinedProject("MarkersTestWithAspectsInJavaFiles"); //$NON-NLS-1$
		String filename = "src/tjp/Demo.java"; //$NON-NLS-1$
		String[][] results = {
				{ "Demo", "Demo" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "main(String[])", "main" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "go()", "go" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "field-set(int tjp.Demo.x)", "field-set(int tjp.Demo.x)" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "foo(int,Object)", "foo" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "exception-handler(void tjp.Demo.<catch>(tjp.DemoException))", "exception-handler(void tjp.Demo.<catch>(tjp.DemoException))" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "bar(Integer)", "bar" } //$NON-NLS-1$ //$NON-NLS-2$
		};
		mappingTestForFile(project, filename, results);
	}

	public void testProgramElementToJavaElementGetInfo() throws Exception {
		IProject project = createPredefinedProject("MarkersTestWithAspectsInJavaFiles"); //$NON-NLS-1$
		String filename = "src/tjp/GetInfo.java"; //$NON-NLS-1$
		String[][] results = {
				{ "declare warning: \"field set\"", "declare warning" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "declare parents: implements Serializable", "declare parents" }, // declare statemens always have counts after them //$NON-NLS-1$ //$NON-NLS-2$
				{ "declare soft: tjp.DemoException", "declare soft" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "Demo.itd(int)", "Demo.itd" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "Demo.f", "Demo.f" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "before(): <anonymous pointcut>", "before" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "before(): demoExecs..", "before" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "before(): <anonymous pointcut>..", "before" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "after(): fieldSet..", "after" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "around(): demoExecs()..", "around" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "after(): <anonymous pointcut>", "after" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "printParameters(JoinPoint)", "printParameters" } //$NON-NLS-1$ //$NON-NLS-2$
		};
		mappingTestForFile(project, filename, results);

	}

	private void mappingTestForFile(IProject project, String filename,
			String[][] results) {
		IFile file = (IFile) project.findMember(filename);
		if (file == null)
			fail("Required file not found: " + filename); //$NON-NLS-1$

		String path = file.getRawLocation().toOSString();

		AsmManager asm = AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project.getProject()).getModel();
		Map<Integer, List<IProgramElement>> annotationsMap = asm.getInlineAnnotations(path,
				true, true);
		AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForProject(project);


		assertNotNull(
				"Didn't get annotations map for file: " + path, annotationsMap); //$NON-NLS-1$

		ICompilationUnit unit = AJCompilationUnitManager.INSTANCE
				.getAJCompilationUnit(file);
		if (unit == null) {
			unit = JavaCore.createCompilationUnitFrom(file);
		}

		assertNotNull("Didn't get a compilation unit from file: " + path, unit); //$NON-NLS-1$

		List<String> toFind = new ArrayList<>();
		List<String> toMatch = new ArrayList<>();
    for (String[] result : results) {
      toFind.add(result[0].intern());
      toMatch.add(result[1].intern());
    }

		Set<Integer> keys = annotationsMap.keySet();
    for (Integer key : keys) {
      List<IProgramElement> annotations = annotationsMap.get(key);
      for (IProgramElement pe : annotations) {
        String peName = pe.toLabelString(false).intern();

        IJavaElement je = model.programElementToJavaElement(pe);
        if (je == null) {
          fail("je is null"); //$NON-NLS-1$
        }
        String jaName = je.getElementName();
        int index = toFind.indexOf(peName);
        if (index == -1) {
          fail("Unexpected additional IProgramElement name found: " + peName); //$NON-NLS-1$
        }
        else {
          String expected = toMatch.get(index);
          if (expected.equals(jaName)) {
            toFind.remove(index);
            toMatch.remove(index);
          }
          else {
            fail("Incorrect corresponding Java element. Found: " + jaName + " Expected: " + expected); //$NON-NLS-1$ //$NON-NLS-2$
          }
        }
      }
    }

		// check that we found everything we were looking for
		if (toFind.size() > 0) {
			StringBuilder missing = new StringBuilder(); //$NON-NLS-1$
      for (String s : toFind) {
        missing.append(System.getProperty("line.separator")); //$NON-NLS-1$
        missing.append(s);
      }
			fail("Did not find all expected IProgramElement names. Missing: " + missing); //$NON-NLS-1$
		}
	}
}
