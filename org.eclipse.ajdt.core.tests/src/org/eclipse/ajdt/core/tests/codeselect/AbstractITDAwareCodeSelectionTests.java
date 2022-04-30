/*******************************************************************************
 * Copyright (c) 2010 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Kris De Volder - initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.core.tests.codeselect;

import java.util.HashMap;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.contribution.jdt.itdawareness.INameEnvironmentProvider;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.internal.compiler.SourceElementParser;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.env.ISourceType;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.SearchableEnvironment;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

/**
 * An abstract superclass for Several ITDAwareCodeSelectionTests, which seem to
 * share some code in common. We can pull-up some of that shared code to here as
 * needed.
 *
 * @author kdvolder
 */
public abstract class AbstractITDAwareCodeSelectionTests extends
        AJDTCoreTestCase {

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTestSuite(ITDAwareCodeSelectionTests4.class);
        suite.addTestSuite(ITDAwareCodeSelectionTests.class);
        suite.addTestSuite(ITDAwareCodeSelectionTests2.class);
        suite.addTestSuite(ITDAwareCodeSelectionTests3.class);
        suite.addTestSuite(ITITAwareCodeSelectionTests.class);
        return suite;
    }


    protected static final class MockNameEnvironmentProvider implements
            INameEnvironmentProvider {
        public ISourceType transformSourceTypeInfo(ISourceType info) {
            // don't need
            return null;
        }

        public boolean shouldFindProblems(CompilationUnit unitElement) {
            return true;
        }

        public CompilationUnitDeclaration problemFind(
                CompilationUnit unitElement, SourceElementParser parer,
                WorkingCopyOwner workingCopyOwner, @SuppressWarnings("rawtypes") HashMap problems,
                boolean creatingAST, int reconcileFlags,
                IProgressMonitor monitor) throws JavaModelException {
            // don't need
            return null;
        }

        public SearchableEnvironment getNameEnvironment(JavaProject project,
                ICompilationUnit[] workingCopies) {
            // don't need
            return null;
        }

        public SearchableEnvironment getNameEnvironment(JavaProject project,
                WorkingCopyOwner owner) {
            // don't need
            return null;
        }
    }

    protected final IRegion findRegion(ICompilationUnit unit, String string,
            int occurrence) {
        String contents = new String(((CompilationUnit) unit).getContents());
        int start = 0;
        while (occurrence-- > 0) {
            start = contents.indexOf(string, start + 1);
            if (start < 0)
                fail("Too few occurrences of '" + string + "' where found");
        }
        return new Region(start, string.length());
    }
    protected void validateCodeSelect(ICompilationUnit unit, IRegion region,
            String expected) throws Exception {
        validateCodeSelect(unit, region, expected, false);
    }
    protected void validateCodeSelect(ICompilationUnit unit, IRegion region,
            String expected, boolean expectingProblems) throws Exception {
        validateCodeSelect(unit, region, expected, expectingProblems, -1);
    }

    protected void validateCodeSelect(ICompilationUnit unit, IRegion region,
            String expected, boolean expectingProblems, int numParams) throws Exception {
        if (!expectingProblems) {
            this.assertNoProblems(unit.getJavaProject().getProject());
        }
        performDummySearch(unit.getJavaProject());
        IJavaElement[] result = unit.codeSelect(region.getOffset(),
                    region.getLength());
        assertEquals("Should have found exactly one hyperlink", 1,
                result.length);
        IJavaElement elt = result[0];
        assertTrue("Java element " + elt.getHandleIdentifier()
                + " should exist", elt.exists());
        assertEquals(expected, elt.getElementName());

        if (numParams >= 0 && elt instanceof IMethod) {
            assertEquals("Wrong number of parameters for " + elt, numParams, ((IMethod) elt).getNumberOfParameters());
        }
    }

    protected void validateCodeSelect(ICompilationUnit unit,
            IRegion region, String expectedSrcFile, String expectedSignature) throws Exception {
        IJavaElement[] result = unit.codeSelect(region.getOffset(), region.getLength());
        assertEquals("Should have found exactly one hyperlink", 1, result.length);
        IJavaElement elt = result[0];
        assertTrue("Java element " + elt.getHandleIdentifier() + " should exist", elt.exists());
        String actualSrcFile = elt.getResource().getFullPath().toString();
        assertTrue("Element found is in the wrong source file:\n" +
                   "   expected: "+expectedSrcFile+"\n" +
                   "   found:    "+actualSrcFile,
                actualSrcFile.endsWith(expectedSrcFile));
        assertEquals("Element found has wrong signature",
                expectedSignature, getSignature(elt));
    }

    /**
     * Convenience method to get a String that can be used to test whether you found what you
     * expected to find. It returns a method signature in following format:
     *
     *   <DeclaringType>.<methodName>(<ParamType>, ...)
     *
     * For ITDs the declaring type will be the Aspect, and the method name will include the
     * target type:
     *
     *   <AspectType>.<TargetType>.<methodName>(<ParamType>, ...)
     *
     * Type names are "simple" (i.e. don't include package name)
     * <p>
     * The method name for a constructor is the name of the class, for a regular constructor.
     * It is <ClassName>.<ClassName_new> for an intertype constructor..
     */
    private String getSignature(IJavaElement elt) {
        if (elt instanceof IMethod) {
            IMethod method = (IMethod) elt;
            StringBuilder sig = new StringBuilder(method.getDeclaringType().getFullyQualifiedName());
            sig.append(".").append(method.getElementName()).append("(");
            String[] paramTypeSigs = method.getParameterTypes();
            for (int i = 0; i < paramTypeSigs.length; i++) {
                if (i>0) sig.append(", ");
                sig.append(Signature.getSignatureSimpleName(paramTypeSigs[i]));
            }
            sig.append(")");
            return sig.toString();
        }
        else {
            throw new Error("Not implemented, if you have tests with fields... implement this :-)");
        }
    }
}
