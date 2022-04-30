/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *******************************************************************************/
package org.eclipse.ajdt.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

public class AJProperties {

	public static final String EXTENSION = "ajproperties"; //$NON-NLS-1$

	/**
	 * Returns all of the currently included source files in a project
	 * @param project
	 * @return a list of IFiles
	 */
	public static List<IFile> getAJPropertiesFiles(IProject project) {
		final List<IFile> files = new ArrayList<>();
		try {
			project.accept(res -> {
        if (
          res.getType() == IResource.FILE &&
          res.getFileExtension() != null &&
          res.getFileExtension().equals(EXTENSION)
        ) {
          files.add((IFile) res);
        }
        return true;
      });
		} catch (CoreException ignored) {
		}
		return files;
	}

}
