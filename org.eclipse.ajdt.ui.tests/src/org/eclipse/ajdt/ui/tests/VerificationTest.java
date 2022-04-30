/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matt Chapman - initial version
 *******************************************************************************/

package org.eclipse.ajdt.ui.tests;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

/**
 */
public class VerificationTest extends UITestCase {

	/**
	 * The Structure Model view is defined in the plugin.xml but it should be
	 * commented out as it only for development or debugging purposes. This test
	 * checks in the registry that the view is not defined.
	 *
	 */
	public void testAbsentStructureModelView() {
		assertFalse(
				"Structure Model view should be commented out in plugin.xml", //$NON-NLS-1$
				isViewDefined("org.eclipse.ajdt.ui.ajde.StructureModelView")); //$NON-NLS-1$
	}

	/**
	 * The Pointcut Navigator view is defined in the plugin.xml but it should be
	 * commented out as it is still under development. This test checks in the
	 * registry that the view is not defined.
	 *
	 */
	public void testAbsentNavigatorView() {
		assertFalse(
				"Pointcut Navigator view should be commented out in plugin.xml", //$NON-NLS-1$
				isViewDefined("org.eclipse.ajdt.ui.navigator.PointcutNavigatorView")); //$NON-NLS-1$
	}

	public void testViewsAreValid() {
		List /* IConfigurationElement */extToTest = new ArrayList();
		IExtensionPoint[] allExP = Platform.getExtensionRegistry()
				.getExtensionPoints();
    for (IExtensionPoint exP : allExP) {
      IExtension[] exs = exP.getExtensions();
      for (IExtension ex : exs) {
        IConfigurationElement[] ces = ex.getConfigurationElements();
        for (IConfigurationElement ce : ces) {
          String className = ce.getAttribute("class"); //$NON-NLS-1$
          if ((className != null)
              && className.startsWith("org.eclipse.ajdt"))
          { //$NON-NLS-1$
            extToTest.add(ce);
          }
          IConfigurationElement[] sub = ce.getChildren();
          for (IConfigurationElement iConfigurationElement : sub) {
            String subClassName = iConfigurationElement.getAttribute("class"); //$NON-NLS-1$
            if ((subClassName != null)
                && subClassName.startsWith("org.eclipse.ajdt"))
            { //$NON-NLS-1$
              extToTest.add(iConfigurationElement);
            }
          }
        }
      }
    }
    for (Object o : extToTest) {
      IConfigurationElement elem = (IConfigurationElement) o;
      String className = elem.getAttribute("class"); //$NON-NLS-1$
      IExtension decl = elem.getDeclaringExtension();
      // only attempt to resolve classes defined in ajdt.ui plugin
      if (decl.getNamespace().equals(AspectJUIPlugin.PLUGIN_ID)) {
        try {
          Class.forName(className);
        }
        catch (ClassNotFoundException e) {
          e.printStackTrace();
          fail("Failed to resolve class: " + elem.getAttribute("class") //$NON-NLS-1$//$NON-NLS-2$
               + " declared under extension point: " + decl.getExtensionPointUniqueIdentifier()); //$NON-NLS-1$

        }
      }
    }
	}

	private boolean isViewDefined(String viewID) {
		String viewExtension = "org.eclipse.ui.views"; //$NON-NLS-1$

		IExtensionPoint exP = Platform.getExtensionRegistry()
				.getExtensionPoint(viewExtension);
		IExtension[] exs = exP.getExtensions();

    for (IExtension ex : exs) {
      IConfigurationElement[] ces = ex.getConfigurationElements();
      for (IConfigurationElement ce : ces) {
        String id = ce.getAttribute("id"); //$NON-NLS-1$
        if (id.equals(viewID)) {
          return true;
        }
      }
    }
		return false;
	}
}
