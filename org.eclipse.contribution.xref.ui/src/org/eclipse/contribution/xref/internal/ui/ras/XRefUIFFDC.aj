/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation
 * 				 Helen Hawkins   - iniital version
 ******************************************************************************/
package org.eclipse.contribution.xref.internal.ui.ras;

import org.eclipse.contribution.xref.ui.XReferenceUIPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.contribution.xref.ras.PluginFFDC;

public aspect XRefUIFFDC extends PluginFFDC {

	public pointcut ffdcScope () :
		within(org.eclipse.contribution.xref..*)
		&& !within(XReferenceInplaceDialog); // Ignore programming by exception cases to avoid spurious errors in the log

    protected String getPluginId () {
    	return XReferenceUIPlugin.PLUGIN_ID;
    }

    protected void log (IStatus status) {
    	XReferenceUIPlugin.getDefault().getLog().log(status);
    }

    /* XXX Move to FFDC/PluginFFDC when 78615 fixed */
    declare warning : call(void Throwable.printStackTrace(..)) :
    	"Don't dump stack trace"; //$NON-NLS-1$
}
