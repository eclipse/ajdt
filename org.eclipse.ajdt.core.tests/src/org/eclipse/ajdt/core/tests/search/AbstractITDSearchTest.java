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

package org.eclipse.ajdt.core.tests.search;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.ajdt.core.tests.AspectJCoreTestPlugin;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.search.JavaSearchScope;

/**
 * @author Andrew Eisenberg
 * @created Apr 20, 2010
 *
 */
public class AbstractITDSearchTest extends AJDTCoreTestCase {
    static class ITDAwareSearchRequestor extends SearchRequestor {
        List<SearchMatch> matches = new ArrayList<>();
        @Override
		public void acceptSearchMatch(SearchMatch match) throws CoreException {
            // order matches by offset
            for (int i = 0; i < matches.size(); i++) {
                if (matches.get(i).getOffset() > match.getOffset()) {
                    matches.add(i, match);
                    return;
                }
            }
            matches.add(match);
        }

        public List<SearchMatch> getMatches() {
            return matches;
        }
    }

    public IJavaProject javaProject;

    @Override
	protected void setUp() throws Exception {
        super.setUp();
        javaProject = JavaCore.create(createPredefinedProject("DefaultEmptyProject"));
    }

    protected IntertypeElement findFirstITD(ICompilationUnit unit) throws Exception {
        AJCompilationUnit ajUnit = (AJCompilationUnit) unit;
        IType type = ajUnit.getTypes()[0];
        IJavaElement[] children = type.getChildren();
      for (IJavaElement child : children) {
        if (child instanceof IntertypeElement) {
          return (IntertypeElement) child;
        }
      }
        return null;
    }

    protected IntertypeElement findLastITD(ICompilationUnit unit) throws Exception {
        AJCompilationUnit ajUnit = (AJCompilationUnit) unit;
        IType type = ajUnit.getTypes()[0];
        IJavaElement[] children = type.getChildren();
        for (int i = children.length-1; i >= 0 ; i--) {
            if (children[i] instanceof IntertypeElement) {
                return (IntertypeElement) children[i];
            }
        }
        return null;
    }

    protected IMember findFirstChild(ICompilationUnit unit) throws Exception {
        IType type = unit.getTypes()[0];
        IJavaElement[] children = type.getChildren();
        return (IMember) children[0];
    }

    protected List<SearchMatch> findSearchMatches(IJavaElement elt, String name) throws Exception {
        return findSearchMatches(elt, name, IJavaSearchConstants.REFERENCES);
    }
    protected List<SearchMatch> findSearchMatches(IJavaElement elt, String name, int flags) throws Exception {
        javaProject.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
        waitForManualBuild();
        assertNoProblems(javaProject.getProject());

        AspectJCoreTestPlugin.logInfo("About to create Search pattern in " + name);
        SearchPattern pattern = SearchPattern.createPattern(elt, flags);
        SearchEngine engine = new SearchEngine();
        JavaSearchScope scope = new JavaSearchScope(false);
        scope.add(javaProject);

        AspectJCoreTestPlugin.logInfo("About to perform search in " + name);
        ITDAwareSearchRequestor requestor = new ITDAwareSearchRequestor();
        engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                scope, requestor, new NullProgressMonitor());
        return requestor.getMatches();
    }

    protected ICompilationUnit createCU(String name, String contents) throws CoreException {
        return createCompilationUnitAndPackage("", name, contents, javaProject);
    }

    protected ICompilationUnit createCU(String pack, String name, String contents) throws CoreException {
        return createCompilationUnitAndPackage(pack, name, contents, javaProject);
    }

    protected void assertNoMatch(String contents, List<SearchMatch> matches) {
        assertEquals("Should not have found any matches, but instead found matches in:\n" + contents + "\n\nMatches were:" + printMatches(matches), 0, matches.size());
    }

    private String printMatches(List<SearchMatch> matches) {
        StringBuilder sb = new StringBuilder();
      for (SearchMatch match : matches) {
        sb.append("\n\n").append(match);

      }
        return sb.toString();
    }

    protected void assertMatch(String matchName, String contents, List<SearchMatch> matches) {

        // remove matches from inside import statements
        int matchStart = 0;
        if (matches.size() > 1) {
            SearchMatch match = matches.get(0);
            if (match.getElement() instanceof IImportDeclaration) {
                matches.remove(match);
                matchStart = match.getOffset() + match.getLength();
            }
        }


        assertEquals("Should have found exactly 1 match, but instead found " + printMatches(matches), 1, matches.size());
        SearchMatch match = matches.get(0);
        assertEquals("Wrong match location", contents.indexOf(matchName, matchStart), match.getOffset());
        assertEquals("Wrong match length", matchName.length(), match.getLength());
        assertTrue("Match enclosing element does not exist", ((IJavaElement) match.getElement()).exists());

            // disabled because we can't get this right right now.
    //        assertEquals("Expected exact match, but was potential", SearchMatch.A_ACCURATE, match.getAccuracy());
    }
    protected void assertTwoMatches(String matchName, String contents, List<SearchMatch> matches) {
        assertEquals("Should have found exactly 2 matches, but instead found " + printMatches(matches), 2, matches.size());
        SearchMatch match = matches.get(0);
        assertEquals("Wrong match location", contents.indexOf(matchName), match.getOffset());
        assertEquals("Wrong match length", matchName.length(), match.getLength());

        match = matches.get(1);
        assertEquals("Wrong match location", contents.lastIndexOf(matchName), match.getOffset());
        assertEquals("Wrong match length", matchName.length(), match.getLength());

        // disabled because we can't get this right right now.
        //        assertEquals("Expected exact match, but was potential", SearchMatch.A_ACCURATE, match.getAccuracy());
    }


    protected void assertDeclarationMatches(IMember declaration, List<SearchMatch> matches) throws JavaModelException {
        boolean found = false;
      for (SearchMatch match : matches) {
        if (match.getElement().equals(declaration)) {
          found = true;
          assertDeclarationMatch(declaration, match);
          break;
        }
      }
        if (!found) {
            fail("Expected to find " + declaration + " in matches, but did not.\n" + printMatches(matches));
        }
    }
    protected void assertDeclarationMatch(IMember declaration, SearchMatch match) throws JavaModelException {
        assertEquals("Should have found the declaration.", declaration, match.getElement());
        ISourceRange nameRange = declaration.getNameRange();
        assertEquals("Incorrect match offset", nameRange.getOffset(), match.getOffset());
        assertEquals("Incorrect match length", nameRange.getLength(), match.getLength());
    }

    protected void assertExpectedNumberOfMatches(int expected, List<SearchMatch> matches) {
        assertEquals("Wrong number of matches found:\n" + printMatches(matches), expected, matches.size());
    }

    protected void buildProject() throws CoreException {
        super.buildProject(javaProject);
    }
}
