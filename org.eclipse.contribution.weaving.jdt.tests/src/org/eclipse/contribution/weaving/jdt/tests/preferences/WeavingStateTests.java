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

package org.eclipse.contribution.weaving.jdt.tests.preferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import org.eclipse.contribution.jdt.preferences.WeavingStateConfigurer;
import org.eclipse.contribution.weaving.jdt.tests.WeavingTestCase;

/**
 * @author Andrew Eisenberg
 * @created Jan 19, 2009
 *
 * tests that weaving state is properly configured and unconfigured
 */
public class WeavingStateTests extends WeavingTestCase {

    private static class MockWeavingStateConfigurer extends WeavingStateConfigurer {
        @Override
        protected String internalChangeWeavingState(boolean becomeEnabled,
                BufferedReader br) throws IOException {
            return super.internalChangeWeavingState(becomeEnabled, br);
        }

        @Override
        protected boolean internalCurrentConfigStateIsWeaving(BufferedReader br)
                throws IOException {
            return super.internalCurrentConfigStateIsWeaving(br);
        }

        boolean checkWeaving(String configContents) throws IOException {
            BufferedReader br = new BufferedReader(new StringReader(configContents));
            return internalCurrentConfigStateIsWeaving(br);
        }

        String changeState(boolean becomeEnabled, String configContents) throws IOException {
            BufferedReader br = new BufferedReader(new StringReader(configContents));
            return internalChangeWeavingState(becomeEnabled, br);
        }
    }

    MockWeavingStateConfigurer mock = new MockWeavingStateConfigurer();

    public void testAddHook() throws Exception {
        String initialContents = "blah\nblah\n";
        String reorgContents = "blah\nblah\n";
        String expectedContents = "blah\nblah\nosgi.framework.extensions=org.eclipse.equinox.weaving.hook\n";
        addTest(initialContents, reorgContents, expectedContents);
    }
    public void testRemoveHookAtEnd() throws Exception {
        String initialContents = "blah\nblah\nosgi.framework.extensions=org.eclipse.equinox.weaving.hook\n";
        String reorgContents = "blah\nblah\nosgi.framework.extensions=org.eclipse.equinox.weaving.hook\n";
        String expectedContents = "blah\nblah\n";
        removeTest(initialContents, reorgContents, expectedContents);
    }
    public void testRemoveHookAtStart() throws Exception {
        String initialContents = "osgi.framework.extensions=org.eclipse.equinox.weaving.hook\nblah\nblah\n";
        String reorgContents = "blah\nblah\nosgi.framework.extensions=org.eclipse.equinox.weaving.hook\n";
        String expectedContents = "blah\nblah\n";
        removeTest(initialContents, reorgContents, expectedContents);
    }
    public void testRemoveHookAtMiddle() throws Exception {
        String initialContents = "blah\nosgi.framework.extensions=org.eclipse.equinox.weaving.hook\nblah\n";
        String reorgContents = "blah\nblah\nosgi.framework.extensions=org.eclipse.equinox.weaving.hook\n";
        String expectedContents = "blah\nblah\n";
        removeTest(initialContents, reorgContents, expectedContents);
    }

    public void testAddHookWithSecondHookAtEnd() throws Exception {
        String initialContents = "blah\nblah\nosgi.framework.extensions=lll\n";
        String reorgContents = "blah\nblah\nosgi.framework.extensions=lll\n";
        String expectedContents = "blah\nblah\nosgi.framework.extensions=lll,org.eclipse.equinox.weaving.hook\n";
        addTest(initialContents, reorgContents, expectedContents);
    }
    public void testAddHookWithSecondHookAtStart() throws Exception {
        String initialContents = "osgi.framework.extensions=lll\nblah\nblah\n";
        String reorgContents = "osgi.framework.extensions=lll\nblah\nblah\n";
        String expectedContents = "osgi.framework.extensions=lll,org.eclipse.equinox.weaving.hook\nblah\nblah\n";
        addTest(initialContents, reorgContents, expectedContents);
    }
    public void testAddHookWithSecondHookAtMiddle() throws Exception {
        String initialContents = "blah\nosgi.framework.extensions=lll\nblah\n";
        String reorgContents = "blah\nosgi.framework.extensions=lll\nblah\n";
        String expectedContents = "blah\nosgi.framework.extensions=lll,org.eclipse.equinox.weaving.hook\nblah\n";
        addTest(initialContents, reorgContents, expectedContents);
    }
    public void testRemoveHookWithSecondHookAtEnd() throws Exception {
        String initialContents = "blah\nblah\nosgi.framework.extensions=lll,org.eclipse.equinox.weaving.hook\n";
        String reorgContents = "blah\nblah\nosgi.framework.extensions=lll,org.eclipse.equinox.weaving.hook\n";
        String expectedContents = "blah\nblah\nosgi.framework.extensions=lll\n";
        removeTest(initialContents, reorgContents, expectedContents);
    }
    public void testRemoveHookWithSecondHookAtStart() throws Exception {
        String initialContents = "osgi.framework.extensions=lll,org.eclipse.equinox.weaving.hook\nblah\nblah\n";
        String reorgContents = "osgi.framework.extensions=lll,org.eclipse.equinox.weaving.hook\nblah\nblah\n";
        String expectedContents = "osgi.framework.extensions=lll\nblah\nblah\n";
        removeTest(initialContents, reorgContents, expectedContents);
    }
    public void testRemoveHookWithSecondHookAtMiddle() throws Exception {
        String initialContents = "blah\nosgi.framework.extensions=lll,org.eclipse.equinox.weaving.hook\nblah\n";
        String reorgContents = "blah\nosgi.framework.extensions=lll,org.eclipse.equinox.weaving.hook\nblah\n";
        String expectedContents = "blah\nosgi.framework.extensions=lll\nblah\n";
        removeTest(initialContents, reorgContents, expectedContents);
    }

    private void removeTest(String initialContents, String reorgContents,
            String expectedContents) throws IOException {
        assertTrue("Weaving should be on", mock.checkWeaving(initialContents));

        String newContents = mock.changeState(true, initialContents);
        assertTrue("Weaving should be on", mock.checkWeaving(reorgContents));
        assertEquals("Config should not have changed", reorgContents, newContents);

        newContents = mock.changeState(false, initialContents);
        assertFalse("Weaving should not be on", mock.checkWeaving(newContents));
        assertEquals("Config should have changed", expectedContents, newContents);
    }

    private void addTest(String initialContents, String reorgContents,
            String expectedContents) throws IOException {
        assertFalse("Weaving should not be on", mock.checkWeaving(initialContents));

        String newContents = mock.changeState(false, initialContents);
        assertFalse("Weaving should not be on", mock.checkWeaving(reorgContents));
        assertEquals("Config should not have changed", reorgContents, newContents);

        newContents = mock.changeState(true, initialContents);
        assertTrue("Weaving should be on", mock.checkWeaving(newContents));
        assertEquals("Config should have changed", expectedContents, newContents);
    }

}
