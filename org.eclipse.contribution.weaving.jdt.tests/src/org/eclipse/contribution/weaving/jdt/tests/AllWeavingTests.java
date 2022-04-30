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

package org.eclipse.contribution.weaving.jdt.tests;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.contribution.jdt.JDTWeavingPlugin;
import org.eclipse.contribution.weaving.jdt.tests.cuprovider.CompilationUnitProviderTests;
import org.eclipse.contribution.weaving.jdt.tests.imagedescriptor.ImageDescriptorSelectorTests;
import org.eclipse.contribution.weaving.jdt.tests.itdawareness.ITDAwarenessTests;
import org.eclipse.contribution.weaving.jdt.tests.preferences.WeavingServiceEnablementTests;
import org.eclipse.contribution.weaving.jdt.tests.preferences.WeavingStateTests;
import org.eclipse.contribution.weaving.jdt.tests.refactoring.RefactoringHooksTests;
import org.eclipse.contribution.weaving.jdt.tests.sourceprovider.SourceTransformerTests;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.JavaCore;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * @author Andrew Eisenberg
 * @created Jan 5, 2009
 *
 */
public class AllWeavingTests {
    public static junit.framework.Test suite() {
        // force early loading of the jdt weaving bundle
        // avoid deadlock when starting tests.
        // ensure that jdt core is already started before we try
        // loading the jdt weaving bundle
        // AJDT UI Tests
        try {
            Bundle jdtBundle = Platform.getBundle(JavaCore.PLUGIN_ID);
            jdtBundle.start(Bundle.START_TRANSIENT);
            waitForIt(JavaCore.PLUGIN_ID);
            Bundle jdtWeavingBundle = Platform.getBundle(JDTWeavingPlugin.ID);
            jdtWeavingBundle.start(Bundle.START_TRANSIENT);
            waitForIt(JDTWeavingPlugin.ID);
        } catch (BundleException e) {
            e.printStackTrace();
            TestCase.fail("Could not start jdt weaving bundle because of: " + e.getMessage());
        }

        TestSuite suite = new TestSuite(AllWeavingTests.class.getName());
        suite.addTestSuite(CompilationUnitProviderTests.class);
        suite.addTestSuite(SourceTransformerTests.class);
        suite.addTestSuite(ImageDescriptorSelectorTests.class);
        suite.addTestSuite(WeavingStateTests.class);
        suite.addTestSuite(WeavingServiceEnablementTests.class);
        suite.addTestSuite(ITDAwarenessTests.class);
        suite.addTestSuite(RefactoringHooksTests.class);
        return suite;
    }

    private static void waitForIt(String jdtCore) {
        Bundle b = Platform.getBundle(jdtCore);
        synchronized (AllWeavingTests.class) {
            while(b.getState() != Bundle.ACTIVE) {
                try {
                    System.out.println("Waiting for " + jdtCore + " to activate");
                    AllWeavingTests.class.wait(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }


}
