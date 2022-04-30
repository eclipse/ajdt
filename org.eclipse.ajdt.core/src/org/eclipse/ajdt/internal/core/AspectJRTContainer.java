/*******************************************************************************
 * Copyright (c) 2006, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *******************************************************************************/
package org.eclipse.ajdt.internal.core;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.CoreUtils;
import org.eclipse.ajdt.core.text.CoreMessages;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;

public class AspectJRTContainer implements IClasspathContainer {

	private IClasspathEntry[] fClasspathEntries;

	public IClasspathEntry[] getClasspathEntries() {
		if (fClasspathEntries == null) {
      String path = CoreUtils.getAspectjrtClasspath();
      String sourcePath = CoreUtils.getAspectjrtSourcePath();
      fClasspathEntries = new IClasspathEntry[1];
      IPath p = new Path(path);
      IPath sp;
      if (sourcePath != null) {
        sp = new Path(sourcePath);
      } else {
          sp = null;
      }
      fClasspathEntries[0] = JavaCore.newLibraryEntry(p, sp, null, false);
    }
		return fClasspathEntries;
	}

	public String getDescription() {
		return CoreMessages.ajRuntimeContainerName;
	}

	public int getKind() {
		return IClasspathContainer.K_APPLICATION;
	}

	public IPath getPath() {
		return new Path(AspectJPlugin.ASPECTJRT_CONTAINER);
	}
}
