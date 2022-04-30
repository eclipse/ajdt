/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation
 * 				 Helen Hawkins   - iniital version
 ******************************************************************************/
package org.eclipse.ajdt.internal.ui.refactoring;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.eclipse.ajdt.core.AJProperties;
import org.eclipse.ajdt.internal.ui.ajde.AJDTErrorHandler;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ltk.core.refactoring.resource.RenameResourceChange;

public class RenamingUtils {

	/**
	 * Utility method - Rename a single file's extension. Add the old and new
	 * names to the map supplied.
	 *
	 * @param newExtensionIsAJ
	 * @param file
	 * @param monitor
	 * @param oldToNewNames -
	 *            Map of old to new names augmented by this method
	 */
	public static void renameFile(boolean newExtensionIsAJ, IResource file,
			IProgressMonitor monitor, Map oldToNewNames) {
		if (!file.exists()) { // nothing to do
			return;
		}
		String oldName = file.getName();
		String nameWithoutExtension = oldName
				.substring(0, oldName.indexOf('.'));
		String newExtension = newExtensionIsAJ ? ".aj" : ".java"; //$NON-NLS-1$ //$NON-NLS-2$
		RenameResourceChange change = new RenameResourceChange(file.getFullPath(),
				nameWithoutExtension + newExtension);
		try {
			change.perform(monitor);
			oldToNewNames.put(oldName, nameWithoutExtension + newExtension);
		} catch (CoreException e) {
			AJDTErrorHandler.handleAJDTError(UIMessages.Refactoring_ErrorRenamingResource, e);
		}
	}

	public static void updateBuildConfigurations(Map oldNamesToNewNames,
			IProject project, IProgressMonitor monitor) {
		List<IFile> buildConfigs = AJProperties.getAJPropertiesFiles(project);
    for (IFile config : buildConfigs) {
      IFile buildConfig = config;
      BufferedReader br;
      try {
        br = new BufferedReader(new InputStreamReader(buildConfig
          .getContents()));
      }
      catch (CoreException e) {
        continue;
      }
      StringBuilder sb = new StringBuilder();
      try {
        String line = br.readLine();
        while (line != null) {
          for (Object o : oldNamesToNewNames.keySet()) {
            String oldName = (String) o;
            String newName = (String) oldNamesToNewNames
              .get(oldName);
            line = line.replaceAll(oldName, newName);
          }
          sb.append(line);
          sb.append(System.getProperty("line.separator")); //$NON-NLS-1$
          line = br.readLine();
        }
        StringReader reader = new StringReader(sb.toString());
        buildConfig.setContents(new ReaderInputStream(reader), true,
          true, monitor);
      }
      catch (IOException | CoreException ignored) {
      }
      finally {
        try {
          br.close();
        }
        catch (IOException ignored) {
        }
      }
    }
	}

}
