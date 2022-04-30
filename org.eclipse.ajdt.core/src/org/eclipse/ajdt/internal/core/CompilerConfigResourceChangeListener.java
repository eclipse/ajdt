/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Luzius Meisser - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.core;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;

/**
 * This listener will add or remove compilers for projects when they are removed or closed
 *
 */
public class CompilerConfigResourceChangeListener implements IResourceChangeListener {

	private final CompilerConfigResourceDeltaVisitor myDeltaVisitor;

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.core.resources.IResourceChangeListener#resourceChanged(org.eclipse.core.resources.IResourceChangeEvent)
	 */
	public CompilerConfigResourceChangeListener() {
		myDeltaVisitor = new CompilerConfigResourceDeltaVisitor();
	}

	public void resourceChanged(IResourceChangeEvent event) {
		if (event.getType()==IResourceChangeEvent.PRE_DELETE) {
			// Before an AJ project is deleted we must tell the compiler to tidy up - so that any jar locks are released, otherwise
			// the delete may not succeed
			if (event.getResource()!=null && event.getResource() instanceof IProject) {
				IProject project = (IProject)event.getResource();
				if (!project.isAccessible() || AspectJPlugin.isAJProject(project)) {
					AspectJPlugin.getDefault().getCompilerFactory().removeCompilerForProject(project);
					AJProjectModelFactory.getInstance().removeModelForProject(project);
				}
			}
		} else if (event.getType() == IResourceChangeEvent.POST_CHANGE) {
			IResourceDelta delta = event.getDelta();
			// avoid processing deltas for non-AspectJ projects,
			if (delta != null) {
				IResourceDelta[] cd = delta.getAffectedChildren();
				if (cd == null) {
					try {
						delta.accept(myDeltaVisitor);
					} catch (CoreException ignored) {
					}
				} else {
          for (IResourceDelta iResourceDelta : cd) {
            try {
              IResource res = iResourceDelta.getResource();
              if (res == null) {
                iResourceDelta.accept(myDeltaVisitor);
              }
              else {
                IProject proj = res.getProject();
                // if we don't know the project, or it is
                // no longer accessible, we'd better process
                // the delta. Otherwise we only process it
                // if it is an AspectJ project.
                if ((proj == null) || !proj.isAccessible()
                    || AspectJPlugin.isAJProject(proj))
                {
                  iResourceDelta.accept(myDeltaVisitor);
                }
              }
            }
            catch (CoreException ignored) {
            }
          }
				}
			}
		}
	}
}
