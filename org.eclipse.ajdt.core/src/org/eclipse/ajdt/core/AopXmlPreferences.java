/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

package org.eclipse.ajdt.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.osgi.service.prefs.BackingStoreException;

/**
 * @author Andrew Eisenberg
 * @created Aug 24, 2009
 *
 * Manages the aop.xml preferences for a project
 */
public class AopXmlPreferences {

    public final static String AOP_XML_FILES_FOR_PROJECT = "org.eclipse.ajdt.aopxml";


    private final IEclipsePreferences projectNode;
    public AopXmlPreferences(IProject project) {
        IScopeContext projectScope = new ProjectScope(project);
        projectNode = projectScope.getNode(AspectJPlugin.UI_PLUGIN_ID);
    }

    public void setAopXmlFiles(IPath[] paths) {
        if (paths != null && paths.length > 0) {
            StringBuilder sb = new StringBuilder();
          for (IPath path : paths) {
            String pathName = path.toPortableString();
            if (pathName.endsWith(".xml")) {
              if (sb.length() > 0) {
                sb.append(File.pathSeparatorChar);
              }
              sb.append(pathName);
            }
          }
            projectNode.put(AOP_XML_FILES_FOR_PROJECT, sb.toString());
        } else {
            projectNode.remove(AOP_XML_FILES_FOR_PROJECT);
        }
        try {
            projectNode.flush();
        } catch (BackingStoreException ignored) {
        }
    }

    public IPath[] getAopXmlFiles() {
        String pathStr = projectNode.get(AOP_XML_FILES_FOR_PROJECT, null);
        if (pathStr == null || pathStr.length() == 0) {
            return new IPath[0];
        }
        String[] pathArr = pathStr.split(File.pathSeparator);
        IPath[] paths = new IPath[pathArr.length];
        for (int i = 0; i < pathArr.length; i++) {
            paths[i] = new Path(pathArr[i]);
        }
        return paths;
    }

    public String getAopXmlFilesAsStrings() {
        IPath[] paths = getAopXmlFiles();
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paths.length; i++) {
            IFile file = root.getFile(paths[i]);
            sb.append(file.getLocation().toOSString());
            if (i < paths.length -1) {
                sb.append(File.pathSeparatorChar);
            }
        }
        return sb.toString();
    }
    public List<String> getAopXmlFilesAsListOfStrings() {
        IPath[] paths = getAopXmlFiles();
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        List<String> strings = new ArrayList<>(paths.length);
      for (IPath path : paths) {
        try {
          IFile file = root.getFile(path);
          if (file != null && file.getLocation() != null) {
            strings.add(file.getLocation().toOSString());
          }
        }
        catch (Exception e) {
          // print the error and continue
        }
      }
        return strings;
    }

    public boolean isAopXml(IFile file) {
        if (! file.getFileExtension().equals("xml")) {
            return false;
        }
        IPath path = file.getFullPath();
        IPath[] paths = getAopXmlFiles();
      for (IPath iPath : paths) {
        if (iPath.equals(path)) {
          return true;
        }
      }
        return false;
    }
}
