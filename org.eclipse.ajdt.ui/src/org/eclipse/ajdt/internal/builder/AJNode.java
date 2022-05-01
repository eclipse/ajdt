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
package org.eclipse.ajdt.internal.builder;

import org.eclipse.contribution.xref.core.IXReferenceNode;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.ui.model.IWorkbenchAdapter;

/**
 * A wrapper class for IAspectJElements that enables them to have
 * a more detailed label and be understood by the Cross reference
 * view (so that we can navigate to them).
 */
public class AJNode implements IAdaptable, IXReferenceNode {

  private final String label;
  private final IJavaElement javaElement;

  public AJNode(IJavaElement javaElement, String label) {
    this.label = label;
    this.javaElement = javaElement;
  }

  /**
   * @see IAdaptable#getAdapter(Class)
   */
  public Object getAdapter(Class adapter) {
    if (adapter == IWorkbenchAdapter.class)
      return AJNodeAdapter.getDefault();
    else if (adapter == IJavaElement.class)
      return javaElement;
    return null;
  }

  public String getLabel() {
    return label;
  }

  /* (non-Javadoc)
   * @see org.eclipse.contribution.xref.core.IXReferenceNode#getJavaElement()
   */
  public IJavaElement getJavaElement() {
    return javaElement;
  }

}
