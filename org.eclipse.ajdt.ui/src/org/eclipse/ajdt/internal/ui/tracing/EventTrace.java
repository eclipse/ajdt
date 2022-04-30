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
package org.eclipse.ajdt.internal.ui.tracing;

import java.util.ArrayList;
import java.util.Date;

public class EventTrace {

	public interface EventListener {
		void ajdtEvent(String msg, int category, Date time);
	}

  private static final ArrayList listeners = new ArrayList();

	public static void postEvent(String msg, int category) {
		Date time = new Date();
		if (!listeners.isEmpty()) {
      for (Object listener : listeners) {
        ((EventListener) listener).ajdtEvent(msg, category, time);
      }
		}
	}

	public static void addListener(EventListener l) {
		listeners.add(l);
	}

	public static void removeListener(EventListener l) {
		listeners.remove(l);
	}
}
