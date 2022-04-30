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

package org.eclipse.ajdt.internal.ui;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;

/**
 * @author Andrew Eisenberg
 * @created Mar 30, 2009
 *
 * Ensures that AJ Projects have an AJBuilder and
 * no JavaBuilder.
 *
 * This class should be in core, but it references {@link AspectJProjectNature}.
 * That class should be in core as well, but for historical reasons, it isn't.
 * Can we refactor?
 */
public class EnsureAJBuilder implements IResourceChangeListener {

    public void resourceChanged(IResourceChangeEvent event) {
        if (event.getType() == IResourceChangeEvent.PRE_BUILD) {
            IResourceDeltaVisitor visitor = delta -> {
                if (delta.getResource().getType() == IResource.PROJECT && delta.getKind() == IResourceDelta.ADDED) {
                    IProject project = (IProject) delta.getResource();
                    if (AspectJPlugin.isAJProject(project)) {
                        ensureNoJavaBuilder(project);
                        ensureAJBuilder(project);
                    }
                    return false;
                }
                return true;
            };
            try {
                event.getDelta().accept(visitor);
            } catch (CoreException ignored) {
            }
        }
    }

    private void ensureNoJavaBuilder(IProject project) throws CoreException {
        // do not remove javaBuilder if it is set to not generate class files
        // see bug 278535
    	if (AspectJProjectNature.hasJavaBuilder(project)) {
    		if (!AspectJProjectNature.isClassGenerationDisabled(project)) {
    			AspectJProjectNature.removeJavaBuilder(project);
    		}
    	}
    }

    private void ensureAJBuilder(IProject project) throws CoreException {
        AspectJProjectNature.addNewBuilder(project);
    }



}
