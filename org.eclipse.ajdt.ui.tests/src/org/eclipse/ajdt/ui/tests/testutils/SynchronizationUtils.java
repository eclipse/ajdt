/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ajdt.ui.tests.testutils;

import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * This class should be moved to core tests
 */
// Adapted from org.eclipse.jdt.ui.tests.performance.JdtPerformanceTestCase
public class SynchronizationUtils {


    /**
     * This method should be removed and the
     * version in AJDTCoreTestCase used instead
     */
	public static void joinBackgroudActivities()  {
	    AJDTCoreTestCase.waitForAutoBuild();
	    AJDTCoreTestCase.waitForManualBuild();
	    AJDTCoreTestCase.waitForAutoRefresh();
	    AJDTCoreTestCase.waitForManualRefresh();

//	    printJobs();
		// Join other jobs
		joinJobs(100, 0, 500);
	}

    public static void printJobs() {
        IJobManager jobManager= Job.getJobManager();
        Job[] jobs= jobManager.find(null);
        System.out.println("------------------------");
        System.out.println("Printing jobs");
      for (Job job : jobs) {
        System.out.println(job);
      }
        System.out.println("------------------------");
    }

    private static boolean joinJobs(long minTime, long maxTime, long intervalTime) {
		long startTime= System.currentTimeMillis() + minTime;
		runEventQueue();
		while (System.currentTimeMillis() < startTime)
			runEventQueue(intervalTime);

		long endTime= maxTime > 0  && maxTime < Long.MAX_VALUE ? System.currentTimeMillis() + maxTime : Long.MAX_VALUE;
		boolean calm= allJobsQuiet();
		while (!calm && System.currentTimeMillis() < endTime) {
			runEventQueue(intervalTime);
			calm= allJobsQuiet();
		}
		return calm;
	}

	public static void sleep(int intervalTime) {
		try {
			Thread.sleep(intervalTime);
		} catch (InterruptedException ignored) {
		}
	}

	private static boolean allJobsQuiet() {
		IJobManager jobManager= Job.getJobManager();
		Job[] jobs= jobManager.find(null);
    for (Job job : jobs) {
      int state = job.getState();
      //ignore jobs we don't care about
      if (!job.getName().equals("Flush Cache Job") &&  //$NON-NLS-1$
          !job.getName().equals("Usage Data Event consumer") &&  //$NON-NLS-1$
          (state == Job.RUNNING || state == Job.WAITING))
      {
        return false;
      }
    }
		return true;
	}

	private static void runEventQueue() {
		IWorkbenchWindow window= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window != null)
			runEventQueue(window.getShell());
	}

	private static void runEventQueue(Shell shell) {
		try {
			while (shell.getDisplay().readAndDispatch()) {
				// do nothing
			}
		} catch (SWTException e) {
			System.err.println(e);
		}
	}

	private static void runEventQueue(long minTime) {
		long nextCheck= System.currentTimeMillis() + minTime;
		while (System.currentTimeMillis() < nextCheck) {
			runEventQueue();
			sleep(1);
		}
	}
}
