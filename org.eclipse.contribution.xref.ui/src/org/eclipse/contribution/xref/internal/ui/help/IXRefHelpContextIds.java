/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sian January - inital version
 *******************************************************************************/
package org.eclipse.contribution.xref.internal.ui.help;

import org.eclipse.contribution.xref.ui.XReferenceUIPlugin;

/**
 * Help context ids for the Cross References UI Plug-in.
 * <p>
 * This interface contains constants only; it is not intended to be implemented
 * or extended.
 * </p>
 *
 */
public interface IXRefHelpContextIds {

	String PREFIX= XReferenceUIPlugin.PLUGIN_ID + '.';

	String XREF_VIEW= PREFIX + "xref_view_context"; //$NON-NLS-1$

}
