/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ben Dalziel     - initial version
 *******************************************************************************/
package org.eclipse.contribution.xref.ui.tests;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import org.eclipse.contribution.xref.core.XReferenceProviderDefinition;
import org.eclipse.contribution.xref.core.XReferenceProviderManager;

public class AJXReferenceProviderTest extends TestCase {

	XReferenceProviderDefinition providerDef = null;
	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() {
		XReferenceProviderManager manager = XReferenceProviderManager.getManager();
		List<XReferenceProviderDefinition> providers = manager.getRegisteredProviders();
    for (XReferenceProviderDefinition element : providers) {
      if (element.getProvider().getProviderDescription().equals("Provides AspectJ crosscutting structure references")) { //$NON-NLS-1$
        providerDef = element;
      }
    }
		if (providerDef == null) {
			providerDef = providers.get(0);

        }
	}

	public void setAndGetCheckedFilters(List<String> listToSet, String identifier) {
		List<String> checkedListReturned;
		providerDef.getProvider().setCheckedFilters(listToSet);
		checkedListReturned = providerDef.getProvider().getFilterCheckedList();
    assertEquals("Returned List, (" + identifier + ") size Expected <" + listToSet.size() + "> Actual <" + checkedListReturned.size() + ">", checkedListReturned.size(), listToSet.size()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		if (listToSet.size() != 0) {
			for (int i = 0; i < listToSet.size(); i++) {
				assertEquals("List item " + i + " from (" + identifier + "). Expected <" + listToSet.get(i) + ">, Recieved <" + checkedListReturned.get(i) + ">", listToSet.get(i), checkedListReturned.get(i)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			}
		}
	}

	public void setAndGetCheckedInplaceFilters(List<String> listToSet, String identifier) {
		List<String> checkedListReturned;
		providerDef.getProvider().setCheckedInplaceFilters(listToSet);
		checkedListReturned = providerDef.getProvider().getFilterCheckedInplaceList();
    assertEquals("Returned List, (" + identifier + ") size Expected <" + listToSet.size() + "> Actual <" + checkedListReturned.size() + ">", checkedListReturned.size(), listToSet.size()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		if (listToSet.size() != 0) {
			for (int i = 0; i < listToSet.size(); i++) {
				assertEquals("List item " + i + " from (" + identifier + "). Expected <" + listToSet.get(i) + ">, Recieved <" + checkedListReturned.get(i) + ">", listToSet.get(i), checkedListReturned.get(i)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			}
		}
	}

	public void testSetCheckedFilters() {

		// Valid List (String) checkedList
		List<String> checkedListToSetString = new ArrayList<>();
		checkedListToSetString.add("advises"); //$NON-NLS-1$
		setAndGetCheckedFilters(checkedListToSetString, "Valid String"); //$NON-NLS-1$
		setAndGetCheckedInplaceFilters(checkedListToSetString, "Valid String"); //$NON-NLS-1$

		// Valid List (String) checkedList
		List<String> checkedListToSetStrings = new ArrayList<>();
		checkedListToSetStrings.add("declared on"); //$NON-NLS-1$
		checkedListToSetStrings.add("matched by"); //$NON-NLS-1$
		setAndGetCheckedFilters(checkedListToSetStrings, "Two Valid Strings"); //$NON-NLS-1$
		setAndGetCheckedInplaceFilters(checkedListToSetStrings, "Two Valid Strings"); //$NON-NLS-1$

		// Valid List (String) checkedList, (not in populatingList of provider but could be in populatingList of another provider, so accept it)
		List<String> checkedListToSetStringX = new ArrayList<>();
		checkedListToSetStringX.add("NOT IN POPULATING LIST"); //$NON-NLS-1$
		setAndGetCheckedFilters(checkedListToSetStringX, "Not in populatingList"); //$NON-NLS-1$
		setAndGetCheckedInplaceFilters(checkedListToSetStringX, "Not in populatingList"); //$NON-NLS-1$

		// This will also reset the checked lists to be empty
		// Valid List (empty) checkedList
		List<String> checkedListToSetEmpty = new ArrayList<>();
		setAndGetCheckedFilters(checkedListToSetEmpty, "Empty List"); //$NON-NLS-1$
		setAndGetCheckedInplaceFilters(checkedListToSetEmpty, "Empty List"); //$NON-NLS-1$
	}
}
