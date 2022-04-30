/*******************************************************************************
 * Copyright (c) 2005, 2008 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation  - initial API and implementation
 * 				 Helen Hawkins    - iniital version
 *               Andrew Eisenberg
 ******************************************************************************/
package org.eclipse.ajdt.core.tests.testutils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.ajdt.core.IAJLogger;
import org.eclipse.ajdt.core.builder.AJBuilder;

/**
 * Logger to help with builder tests
 */
public class TestLogger implements IAJLogger {

    private final Object lock = new Object();

    private List<String> log;
    private List<List<String>> buildLog;
    private List<String> tempBuildLog;
    private boolean loggingBuildEvent = false;
    private boolean foundSplit = false;

    public TestLogger() {
        // need to register state listener to get feedback about builds
        AJBuilder.addStateListener();
    }

    public void log(int category, String msg) {
        log(msg);
    }

    /* (non-Javadoc)
     * @see org.eclipse.ajdt.core.IAJLogger#log(java.lang.String)
     */
    public void log(String msg) {
        synchronized (lock) {
            if (log == null) {
                log = new ArrayList<>();
            }
            msg = msg.replaceAll("\\\\","/");
            log.add(msg);
        }

        if (buildLog == null) {
            buildLog = new ArrayList<>();
        }
        if (tempBuildLog == null) {
            tempBuildLog = new ArrayList<>();
        }

        StringBuilder sb = new StringBuilder(msg);
        if (sb.indexOf("=======================") != -1) { //$NON-NLS-1$
            // we think we might be starting a build
            foundSplit = true;
        } else if (foundSplit && (sb.indexOf("Build kind =") != -1)) { //$NON-NLS-1$
            // this is the first line in the log after the "===="
            // when we do a build, therefore, we know we're doing
            // a build and want to start creating the buildLog.
            foundSplit = false;
            loggingBuildEvent = true;
            tempBuildLog.add(msg);
        } else if (sb.indexOf("Total time spent in AJBuilder.build()") != -1) { //$NON-NLS-1$
            // this is the last log line when we build.
            loggingBuildEvent = false;
            tempBuildLog.add(msg);
            buildLog.add(new ArrayList<>(tempBuildLog));
            tempBuildLog.clear();
        } else if (loggingBuildEvent) {
            // we're in a build, therefore add msg to tempBuildLog.
            tempBuildLog.add(msg);
        }
    }

    /**
     * Returns whether or not the log contains the given string
     */
    public boolean containsMessage(String msg) {
        synchronized (lock) {
            if (log == null) {
                return false;
            }
          for (String logEntry : log) {
            if (logEntry.contains(msg)) {
              return true;
            }
          }
        }
        return false;
    }

    /**
     * Return the last line in the log that contains the given string
     * @param msg
     * @return
     */
    public String getMostRecentMatchingMessage(String msg) {
        synchronized (lock) {
            if (log == null) {
                return null;
            }
            for (int i = log.size() - 1; i >= 0; i--) {
                String logEntry = log.get(i);
                if (logEntry.contains(msg)) {
                    return logEntry;
                }
            }
        }
        return null;
    }

    /**
     * Returns the number of times the given string appears
     * in the log
     */
    public int numberOfEntriesForMessage(String msg) {
        int occurances = 0;
        synchronized (lock) {
          for (String logEntry : log) {
            StringBuilder sb = new StringBuilder(logEntry);
            if (sb.indexOf(msg) != -1) {
              occurances++;
            }
          }
        }
        return occurances;
    }

    /**
     * Returns the number of times the given strings appear
     * in the log on the same line
     */
    public int numberOfEntriesForMessage(String[] msgs) {
        int occurances = 0;
        synchronized (lock) {
          for (String logEntry : log) {
            StringBuilder sb = new StringBuilder(logEntry);
            boolean allFound = true;
            for (String msg : msgs) {
              if (sb.indexOf(msg) == -1) {
                allFound = false;
              }
            }
            if (allFound) {
              occurances++;
            }
          }
        }
        return occurances;
    }

    /**
     * Returns the last given number of lines of the log
     */
    public List<String> getMostRecentEntries(int numberOfLines) {
        synchronized (lock) {
            if (log == null) {
                return Collections.emptyList();
            }
            return log.subList(log.size() - numberOfLines, log.size());
        }
    }

    /**
     * Clears the log
     */
    public void clearLog() {
        synchronized (lock) {
            if (log != null) {
                log.clear();
            }
        }
        if (buildLog != null) {
            buildLog.clear();
        }
    }

    public boolean isEmpty() {
        synchronized (lock) {
            return log != null && log.isEmpty();
        }
    }

    /**
     * Prints the contents of the log to the screen - useful
     * in testcase development
     */
    public void printLog() {
        System.out.println(); //$NON-NLS-1$
        System.out.println("Printing log begin ------------------------------------"); //$NON-NLS-1$
        synchronized (lock) {
            if (log != null) {
              for (String element : log) {
                System.out.println("LOG: " + element); //$NON-NLS-1$
              }
            }
        }
        System.out.println("-------------------------------------- Printing log end"); //$NON-NLS-1$
    }

    public String getLog() {
        StringBuilder sb = new StringBuilder();
        synchronized (lock) {
            if (log != null) {
              for (String element : log) {
                sb.append(element).append("\n"); //$NON-NLS-1$
              }
            }
        }
        return sb.toString();
    }

    public List<String> getPreviousBuildEntry(int i) {
        return buildLog.get(buildLog.size() - i);
    }

    public int getNumberOfBuildsRun() {
        return buildLog.size();
    }

}
