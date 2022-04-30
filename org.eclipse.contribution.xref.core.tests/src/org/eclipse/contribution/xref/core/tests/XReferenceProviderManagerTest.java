/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Helen Hawkins   - iniital version
 *******************************************************************************/
package org.eclipse.contribution.xref.core.tests;

import java.util.List;

import org.eclipse.contribution.xref.core.IXReferenceProvider;
import org.eclipse.contribution.xref.core.XReferenceProviderDefinition;
import org.eclipse.contribution.xref.core.XReferenceProviderManager;

import junit.framework.TestCase;

/**
 * @author hawkinsh
 *
 */
public class XReferenceProviderManagerTest extends TestCase {

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

	public void testGetManager() {
		XReferenceProviderManager manager =
			XReferenceProviderManager.getManager();
		assertNotNull(manager);
		XReferenceProviderManager manager2 =
			XReferenceProviderManager.getManager();
		assertSame(manager, manager2);
	}

	public void testGetProvidersFor() {
		XReferenceProviderManager manager =
			XReferenceProviderManager.getManager();
		List<IXReferenceProvider> providers = manager.getProvidersFor(new AdaptableObject());
		assertEquals(0, providers.size());
		providers = manager.getProvidersFor(new AdaptableString());
		assertEquals(1, providers.size());
		assertTrue(providers.get(0) instanceof TestProvider);
	}

	public void testGetRegisteredProviders() {
		XReferenceProviderManager manager =
			XReferenceProviderManager.getManager();
		List<? extends XReferenceProviderDefinition> providers = manager.getRegisteredProviders();
		// know should have 3 providers registered:
		// TestProvider (in core.test), TestXRefProvider (in ui.test)
		// and TestXRefProviderWithEntities (in ui.test),
		// check that all three are actually registered
		// (note haven't hard coded in that expect exactly 3 providers
		// to cope with the case where there are others)
		assertTrue(containsProvider(providers, "My Label")); //$NON-NLS-1$
		assertTrue(
			containsProvider(providers, "Test XReference Provider Label")); //$NON-NLS-1$
		assertTrue(
			containsProvider(
				providers,
				"Test XReference Provider With Entities Label")); //$NON-NLS-1$
		// TODO: HELEN: put this back in!
		//assertTrue(ProviderExceptionLoggingTest.exceptionLoggedOnRegistration);
	}

	private boolean containsProvider(List<? extends XReferenceProviderDefinition> providers, String label) {
		boolean contains = false;
    for (XReferenceProviderDefinition xrdef : providers) {
      if (xrdef.getLabel().compareTo(label) == 0) {
        contains = true;
      }
    }
		return contains;
	}

	public void testSettingOfIsInplace() {
		XReferenceProviderManager manager =
			XReferenceProviderManager.getManager();
		boolean currentValue = manager.getIsInplace();
		if (currentValue) {
			manager.setIsInplace(false);
			assertFalse("isInplace has not been set correctly", manager.getIsInplace()); //$NON-NLS-1$
		} else {
			manager.setIsInplace(true);
			assertTrue("isInplace has not been set correctly", manager.getIsInplace()); //$NON-NLS-1$
		}
		// Reset the value
		manager.setIsInplace(currentValue);
	}
}
