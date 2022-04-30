/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *******************************************************************************/
package org.eclipse.ajdt.internal.builder;

import java.util.Map;

import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.internal.ui.AspectJProjectNature;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;

/**
 * This is now the "old" builder, purely for compatibility with existing
 * projects which have the old builder id defined in their .project files. When
 * a build is requested, it first checks if the new builder id is also present.
 * If it is, no action is required. If not, the new builder is added, and the
 * old one removed
 */
public class Builder extends IncrementalProjectBuilder {

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.core.resources.IncrementalProjectBuilder#build(int,
	 *      java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
			throws CoreException {
		final IProject project = getProject();

		if (AspectJProjectNature.hasNewBuilder(project)) {
			// don't need to do anything, the new builder will do the actual
			// build
			return null;
		}

		// add new builder id
		AspectJProjectNature.addNewBuilder(project);

		if (!AspectJProjectNature.hasNewBuilder(project)) {
			// addition of new builder failed for some reason
			AJLog.log(AJLog.BUILDER,"AJDT migration builder: addition of new builder failed!"); //$NON-NLS-1$
			Display.getDefault().syncExec(() -> MessageDialog
          .openError(
              null,
              UIMessages.Builder_migration_failed_title,
              NLS.bind(UIMessages.Builder_migration_failed_message,
                  project.getName())));
			return null;
		}

		AspectJProjectNature.removeOldBuilder(project);

		// request a full build, which will then call the new builder.
		// we don't simply call the new builder directly, because the
		// eclipse build manager caches the build state against the builder,
		// so it would cache the state from the new builder against this
		// old builder.
		String jobName = NLS.bind(UIMessages.Builder_migration_build, project.getName());
		Job buildJob = new Job(jobName) {
			public IStatus run(IProgressMonitor monitor) {
				try {
					project
							.build(IncrementalProjectBuilder.FULL_BUILD,
									monitor);
				} catch (CoreException ignored) {
				}
				return Status.OK_STATUS;
			}
		};
		buildJob.schedule();

		return null;
	}

}
