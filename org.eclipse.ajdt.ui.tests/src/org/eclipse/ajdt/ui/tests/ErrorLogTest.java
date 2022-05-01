/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sian January  - initial version
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.internal.views.log.AbstractEntry;
import org.eclipse.ui.internal.views.log.LogEntry;
import org.eclipse.ui.internal.views.log.LogView;

/**
 * Test that we don't get any spurious errors or warnings in the log on startup
 * (E.g. bug 106707)
 */
public class ErrorLogTest extends UITestCase {

	private static final String KNOWN_MSG1 = "org.eclipse.contribution.xref.core.tests.unknownprovider"; //$NON-NLS-1$
    private static final String KNOWN_MSG2 = "The following is a complete list"; //$NON-NLS-1$
    private static final String KNOWN_MSG3 = "One or more bundles"; //$NON-NLS-1$
    private static final String KNOWN_MSG4 = "Could not locate the running profile instance."; //$NON-NLS-1$
    private static final String KNOWN_MSG5 = "ITDInserter"; //$NON-NLS-1$


    public boolean matchesMsg1(String msg) {
        return msg.toLowerCase().contains(KNOWN_MSG1);
    }

    public boolean matchesMsg2(String msg) {
        return msg.startsWith(KNOWN_MSG2);
    }
    public boolean matchesMsg3(String msg) {
        return msg.startsWith(KNOWN_MSG3);
    }
    public boolean matchesMsg4(String msg) {
        return msg.startsWith(KNOWN_MSG4);
    }
    public boolean matchesMsg5(String msg) {
        return msg.contains(KNOWN_MSG5);
    }


    public void testDisabled() {
        System.out.println("Tests in this class have been disabled");
    }

	public void _testNoWarningsOnStartup() throws Exception {
		IViewPart view = Workbench.getInstance().getActiveWorkbenchWindow()
				.getActivePage().getActivePart().getSite().getPage().showView(
						"org.eclipse.pde.runtime.LogView"); //$NON-NLS-1$
		if (view instanceof LogView) {
			LogView logView = (LogView) view;
			AbstractEntry[] logs = logView.getElements();
			// Ignore information entries in the log
			List<AbstractEntry> errorsAndWarnings = new ArrayList<>();
      for (AbstractEntry log : logs) {
        LogEntry entry = (LogEntry) log;
        if (entry.getSeverity() == IStatus.ERROR || entry.getSeverity() == IStatus.WARNING)
        {
          String msg = entry.getMessage();
          if (!matchesMsg1(msg) &&
              !matchesMsg2(msg) &&
              !matchesMsg3(msg) &&
              !matchesMsg4(msg) &&
              !matchesMsg5(msg))
          {
            // ignore messages about missing bundles that are not from AJDT
            errorsAndWarnings.add(log);
          }
        }
      }
			if (errorsAndWarnings.size() > 0) {
				StringBuilder errors = new StringBuilder();
				boolean ignore = false;
        for (Object errorsAndWarning : errorsAndWarnings) {
          LogEntry element = (LogEntry) errorsAndWarning;
          errors.append(element.getMessage());
          errors.append(" (").append(element.getPluginId()).append(")\n"); //$NON-NLS-1$ //$NON-NLS-2$
          if (element.hasChildren()) {
            Object[] sub = element.getChildren(null);
            for (Object o : sub) {
              if (o instanceof LogEntry) {
                LogEntry s = (LogEntry) o;
                String msg = s.getMessage();
                errors.append("    ").append(msg); //$NON-NLS-1$
                errors.append(" (").append(s.getPluginId()).append(")\n"); //$NON-NLS-1$ //$NON-NLS-2$
                // ignore if all child warnings are related to
                // unresolved jdt plugins (probably caused by
                // missing java6 constraints)
                //$NON-NLS-1$//$NON-NLS-2$
                ignore = (element.getSeverity() == IStatus.WARNING)
                         && (msg.contains("org.eclipse.jdt")) && (msg.contains("was not resolved"));
              }
            }
          }
        }
				if (!ignore) {
					fail("There should be no unexpected entries in the error log. Found:\n" + errors); //$NON-NLS-1$
				}
			}
		} else {
			fail("Could not find the Error log."); //$NON-NLS-1$
		}
	}

}
