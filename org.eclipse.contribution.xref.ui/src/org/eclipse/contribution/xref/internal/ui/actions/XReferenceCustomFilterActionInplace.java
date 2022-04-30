/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ben Dalziel     - initial version
 *******************************************************************************/
package org.eclipse.contribution.xref.internal.ui.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.contribution.xref.core.XReferenceProviderDefinition;
import org.eclipse.contribution.xref.core.XReferenceProviderManager;
import org.eclipse.contribution.xref.internal.ui.inplace.XReferenceInplaceDialog;
import org.eclipse.contribution.xref.internal.ui.text.XRefMessages;
import org.eclipse.contribution.xref.ui.filters.CustomFilterDialog;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.Shell;

/**
 *
 * Class which creates a CustomFilterDialog for the XRef inplace view
 * and handles the returned List.
 *
 * The difference between XReferenceCustomFilterActionInplace and
 * XReferenceCustomFilterAction is in the view is refreshed
 *
 */
public class XReferenceCustomFilterActionInplace extends Action {

	private List /* XReferenceProviderDefinition */ providerDefns;

	private List /* String */ populatingList;
	private List /* String */ checkedList;
	private List /* String */ defaultCheckedList;
	private String dialogTitle;
	private String dialogMessage;
	private Shell parentShell;

	public XReferenceCustomFilterActionInplace(Shell shell) {

		setText(XRefMessages.OpenCustomFiltersDialogAction_text);
		setImageDescriptor(JavaPluginImages.DESC_ELCL_FILTER);
		setDisabledImageDescriptor(JavaPluginImages.DESC_DLCL_FILTER);

		populatingList = new ArrayList();
		checkedList = new ArrayList();
		defaultCheckedList = new ArrayList();
		parentShell = shell;

		providerDefns = XReferenceProviderManager.getManager().getRegisteredProviders();

    for (Object providerDefn : providerDefns) {
      XReferenceProviderDefinition provider = (XReferenceProviderDefinition) providerDefn;

      List providerFilters = provider.getAllFilters();
      if (providerFilters != null) {
        for (Object providerFilter : providerFilters) {
          String filter = (String) providerFilter;
          if (!populatingList.contains(filter)) {
            populatingList.add(filter);
          }
        }
      }

      List providerCheckedInplace = provider.getCheckedInplaceFilters();
      if (providerCheckedInplace != null) {
        for (Object o : providerCheckedInplace) {
          String filter = (String) o;
          checkedList.add(filter);
        }
      }

      List providerDefault = provider.getDefaultFilters();
      if (providerDefault != null) {
        for (Object o : providerDefault) {
          String filter = (String) o;
          defaultCheckedList.add(filter);
        }
      }
      dialogTitle = XRefMessages.CustomFilterDialog_title;
      dialogMessage = XRefMessages.CustomFilterDialog_message;
    }
	}

	public void run() {
		checkedList = CustomFilterDialog.showDialog(parentShell,
				populatingList, checkedList, defaultCheckedList,
				dialogTitle, dialogMessage);

    for (Object providerDefn : providerDefns) {
      XReferenceProviderDefinition provider = (XReferenceProviderDefinition) providerDefn;
      provider.setCheckedInplaceFilters(checkedList);
    }
		XReferenceProviderManager.getManager().setIsInplace(true);
		// Refresh Inplace View
		XReferenceInplaceDialog.getInplaceDialog().refresh();

	}

	// ----------------- This is for testing ----------------------

    /**
     * Returns the List of XReferenceProviderDefinition for the Action - this
     * method is for testing purposes and not part of the
     * published API.
     */
	public List /* XReferenceProviderDefinition */ getProviderDefns() {
		return providerDefns;
	}

    /**
     * Returns the populating List for the Action - this
     * method is for testing purposes and not part of the
     * published API.
     */
	public List /* String */ getPopulatingList() {
		return populatingList;
	}
}
