/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Helen Hawkins   - iniital version
 *******************************************************************************/
package org.eclipse.ajdt.core.tests.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aspectj.asm.AsmManager;
import org.aspectj.asm.IProgramElement;
import org.aspectj.bridge.ISourceLocation;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.javaelements.AJCodeElement;
import org.eclipse.ajdt.core.model.AJComparator;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IJavaElement;

/**
 * @author hawkinsh
 */
public class AJComparatorTest extends AJDTCoreTestCase {

	IProject project;
	IFolder aspectjPackage;
	AJProjectModelFacade model;

	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		project = createPredefinedProject("AJProject83082");
		model = AJProjectModelFactory.getInstance().getModelForProject(project);
		IFolder src = project.getFolder("src");
		IFolder wpstest = src.getFolder("wpstest");
		aspectjPackage = wpstest.getFolder("aspectj");
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testCompareTwoAJCodeElements() {

		// get the class file and create the map for the file (the underlying one)
		IFile main = aspectjPackage.getFile("Main.java");

		AsmManager asm = AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project.getProject()).getModel();
		Map<Integer, List<IProgramElement>> annotationsMap = asm.getInlineAnnotations(main.getRawLocation().toOSString(), true, true);
		assertNotNull("annotation map should not be null for Main.java", annotationsMap);
		// for the two IProgramElements which correspond to the two calls
		// in main - create the IJavaElements (or AJCodeElements)
		AJCodeElement ajce1 = null;
		AJCodeElement ajce2 = null;
		Set<Integer> keys = annotationsMap.keySet();
		for (Integer key : keys) {
			List<IProgramElement> annotations = annotationsMap.get(key);
			for (IProgramElement node : annotations) {
				ISourceLocation sl = node.getSourceLocation();
				if (node.toLinkLabelString(false)
							.equals("Main: method-call(void java.io.PrintStream.println(java.lang.String))")
						&& (sl.getLine() == 23))
				{

					IJavaElement ije = model.programElementToJavaElement(node);
					if (ije instanceof AJCodeElement) {
						ajce1 = (AJCodeElement) ije;
					}
				}
				else if (node.toLinkLabelString(false)
									 .equals("Main: method-call(void java.io.PrintStream.println(java.lang.String))")
								 && (sl.getLine() == 24))
				{

					IJavaElement ije = model.programElementToJavaElement(node);
					if (ije instanceof AJCodeElement) {
						ajce2 = (AJCodeElement) ije;
					}
				}
			}
		}
		assertNotNull("AJCodeElement shouldn't be null", ajce1);
		assertNotNull("AJCodeElement shouldn't be null", ajce2);

		// check that when call compare on them, that the one with
		// the lowest line number is first in the list
		AJComparator comp = new AJComparator();
		assertTrue("ajce1 should be less than ajce2", comp.compare(ajce1, ajce2) < 0);
		assertTrue("ajce2 should be greater than ajce1", comp.compare(ajce2, ajce1) > 0);
		//noinspection EqualsWithItself
		assertEquals("ajce1 should be equal to ajce1", 0, comp.compare(ajce1, ajce1));
	}

	public void testCompareTwoIJavaElements() {

		// get the aspect and create the map for the file (the underlying one)
		IFile aspect = aspectjPackage.getFile("A.aj");

		// for the two IProgramElements which correspond to the two calls
		// in main - create the IJavaElements

		AsmManager asm = AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project.getProject()).getModel();
		Map<Integer, List<IProgramElement>> annotationsMap = asm.getInlineAnnotations(aspect.getRawLocation().toOSString(), true, true);
		assertNotNull("annotation map should not be null for Main.java", annotationsMap);
		// for the two IProgramElements which correspond to the two pieces
		// of advice (before and after) in A.aj - create the IJavaElements
		IJavaElement ije1 = null;
		IJavaElement ije2 = null;
		Set<Integer> keys = annotationsMap.keySet();
		for (Integer key : keys) {
			List<IProgramElement> annotations = annotationsMap.get(key);
			for (IProgramElement node : annotations) {
				ISourceLocation sl = node.getSourceLocation();
				if (node.toLinkLabelString(false)
							.equals("A.after(String): tracedPrint..")
						&& (sl.getLine() == 30))
				{

					IJavaElement ije = model.programElementToJavaElement(node);
					if (!(ije instanceof AJCodeElement)) {
						ije1 = ije;
					}
				}
				else if (node.toLinkLabelString(false)
									 .equals("A.before(String): tracedPrint..")
								 && (sl.getLine() == 26))
				{

					IJavaElement ije = model.programElementToJavaElement(node);
					if (!(ije instanceof AJCodeElement)) {
						ije2 = ije;
					}
				}
			}
		}
		assertNotNull("IJavaElement shouldn't be null", ije1);
		assertNotNull("IJavaElement shouldn't be null", ije2);

		// check that when call compare on them, that the one that comes first
		// alphabetically is the first in the list
		AJComparator comp = new AJComparator();
		assertTrue("ije1 should be less than ije2", comp.compare(ije1, ije2) < 0);
		assertTrue("ije2 should be greater than ije1", comp.compare(ije2, ije1) > 0);
		//noinspection EqualsWithItself
		assertEquals("ije1 should be equal to ije1", 0, comp.compare(ije1, ije1));

	}

	public void testCompareAJCodeElementAndIJavaElement() {
		AJCodeElement ajce = null;
		IJavaElement ije = null;

		// get the aspect and create the map for the file (the underlying one)
		IFile main = aspectjPackage.getFile("Main.java");

		AsmManager asm = AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project.getProject()).getModel();
		Map<Integer, List<IProgramElement>> annotationsMap = asm.getInlineAnnotations(main.getRawLocation().toOSString(), true, true);
		assertNotNull("annotation map should not be null for Main.java", annotationsMap);
		Set<Integer> keys = annotationsMap.keySet();
		for (Integer key : keys) {
			List<IProgramElement> annotations = annotationsMap.get(key);
			for (IProgramElement node : annotations) {
				ISourceLocation sl = node.getSourceLocation();
				if (node.toLinkLabelString(false)
							.equals("Main: method-call(void java.io.PrintStream.println(java.lang.String))")
						&& (sl.getLine() == 23))
				{

					IJavaElement je = model.programElementToJavaElement(node);
					if (je instanceof AJCodeElement) {
						ajce = (AJCodeElement) je;
						break;
					}
				}
			}
		}

		IFile aspect = aspectjPackage.getFile("A.aj");

		asm = AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(project.getProject()).getModel();
		Map<Integer, List<IProgramElement>> annotationsMap2 = asm.getInlineAnnotations(aspect.getRawLocation().toOSString(), true, true);
		assertNotNull("annotation map should not be null for Main.java", annotationsMap2);

		Set<Integer> keys2 = annotationsMap2.keySet();
		for (Integer key : keys2) {
			List<IProgramElement> annotations = annotationsMap2.get(key);
			for (IProgramElement node : annotations) {
				ISourceLocation sl = node.getSourceLocation();
				if (node.toLinkLabelString(false)
							.equals("A.after(String): tracedPrint..")
						&& (sl.getLine() == 30))
				{

					IJavaElement je = model.programElementToJavaElement(node);
					if (!(je instanceof AJCodeElement)) {
						ije = je;
						break;
					}
				}
			}
		}

		assertNotNull("AJCodeElement shouldn't be null", ajce);
		assertNotNull("IJavaElement shouldn't be null", ije);

		// check that when call compare on them, that the one that comes first
		// alphabetically is the first in the list
		AJComparator comp = new AJComparator();
		assertTrue("ije should be less than ajce", comp.compare(ije, ajce) < 0);
		assertTrue("ajce should be greater than ije", comp.compare(ajce, ije) > 0);

	}

	static class AdaptableString implements IAdaptable {

		private final String val;

		public AdaptableString() {
			this.val = "";
		}

		public AdaptableString(String val) {
			this.val = val;
		}

		@Override
		public <T> T getAdapter(Class<T> adapter) {
			if (String.class == adapter)
				return adapter.cast(val);
			return null;
		}

	}

	public void testCompareTwoStrings() {
		AdaptableString s1 = new AdaptableString("hello");
		AdaptableString s2 = new AdaptableString("goodbye");
		AJComparator comp = new AJComparator();
		assertEquals("comparing things which aren't adaptable to AJCodeElements or IJavaElements should return 0", 0, comp.compare(s1, s2));
	}
}
