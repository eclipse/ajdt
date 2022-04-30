/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman  - initial version
 *******************************************************************************/
package org.eclipse.ajdt.core.tests.codeconversion;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ajdt.core.CoreUtils;
import org.eclipse.ajdt.core.codeconversion.CodeChecker;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

public class CodeCheckerTest extends AJDTCoreTestCase {

	/**
	 * Check that CodeChecker.containsAspectJConstructs() returns true for every
	 * type in the given list, and false for everything else.
	 *
	 * @throws Exception
	 */
	public void testContainsAspectJConstructs() throws Exception {
		IProject project = createPredefinedProject("Spacewar Example"); //$NON-NLS-1$

		final List<String> trueList = new ArrayList<>();
		trueList.add("Ship"); // pointcut in a class //$NON-NLS-1$
		trueList.add("RegistrySynchronization"); // aspect //$NON-NLS-1$
		trueList.add("Registry"); // inner aspect in a class //$NON-NLS-1$
		trueList.add("GameSynchronization"); // aspect //$NON-NLS-1$
		trueList.add("EnsureShipIsAlive"); // aspect //$NON-NLS-1$
		trueList.add("Display2"); // inner aspect in a class //$NON-NLS-1$
		trueList.add("Display1"); // inner aspect in a class //$NON-NLS-1$
		trueList.add("Display"); // inner aspect in a class //$NON-NLS-1$
		trueList.add("Debug"); // aspect and class //$NON-NLS-1$
		trueList.add("Coordinator"); // abstract aspect with inner classes //$NON-NLS-1$

		project.accept(resource -> {
      if (resource.getType() == IResource.FILE) {
        String name = resource.getName();
        if (CoreUtils.ASPECTJ_SOURCE_FILTER.accept(name)) {
          boolean ret = CodeChecker
              .containsAspectJConstructs((IFile) resource);
          int index = name.lastIndexOf('.');
          if (index != -1) {
            name = name.substring(0, index);
          }
          name = name.intern();
          if (trueList.contains(name)) {
            if (ret) {
              trueList.remove(name);
            } else {
              fail("Returned false from CodeChecker.containsAspectJConstructs for " //$NON-NLS-1$
                  + name + ", expected true"); //$NON-NLS-1$
            }
          } else if (ret) {
            fail("Returned true from CodeChecker.containsAspectJConstructs for " //$NON-NLS-1$
                + name + ", expected false"); //$NON-NLS-1$
          }
        }
      }
      return true;
    });
    assertEquals("Didn't correctly identify all resources. Missed: " //$NON-NLS-1$
                 + trueList, 0, trueList.size());
	}

	/**
	 * Check that CodeChecker.containsAspectJConstructs() returns false for all
	 * the given source files, despite them containing "pointcut" and "aspect"
	 * used as identifiers (instead of keywords)
	 *
	 * @throws Exception
	 */
	public void testContainsAspectJConstructs2() throws Exception {
		IProject project = createPredefinedProject("bug95370"); //$NON-NLS-1$
		project.accept(resource -> {
      if (resource.getType() == IResource.FILE) {
        String name = resource.getName();
        if (CoreUtils.ASPECTJ_SOURCE_FILTER.accept(name)) {
          boolean ret = CodeChecker
              .containsAspectJConstructs((IFile) resource);
          assertFalse(
              "Returned true from CodeChecker.containsAspectJConstructs for " //$NON-NLS-1$
                  + name + ", expected false", ret); //$NON-NLS-1$
        }
      }
      return true;
    });
	}
}
