/**********************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Sian January - initial version
 * ...
 **********************************************************************/

package org.eclipse.ajdt.internal.launching;

import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.jdt.internal.debug.ui.classpath.ClasspathEntry;
import org.eclipse.jdt.internal.debug.ui.classpath.ClasspathGroup;
import org.eclipse.jdt.internal.debug.ui.classpath.ClasspathMessages;
import org.eclipse.jdt.internal.debug.ui.classpath.ClasspathModel;
import org.eclipse.jdt.internal.debug.ui.classpath.IClasspathEntry;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;

/**
 * Mostly copied from ClasspathModel, but extends to include an 'Aspect Path'
 * and an 'Output Jar' node.
 */
public class AJClasspathModel extends ClasspathModel {

	public static final int ASPECTPATH = 2;

	public static final int OUTJAR = 3;

	private ClasspathGroup bootstrapEntries;

	private ClasspathGroup userEntries;

	private ClasspathGroup aspectPathEntries;

	private ClasspathGroup outJarEntries;

	/**
	 * Constructs a new aj classpath model with root entries
	 */
	public AJClasspathModel() {
		super();
		getBootstrapEntry();
		getUserEntry();
		getAspectPathEntry();
		getOutJarEntry();
	}

	/**
	 * Add a classpath entry
	 */
	public Object addEntry(int entryType, IRuntimeClasspathEntry entry) {
		IClasspathEntry entryParent = null;
		switch (entryType) {
		case BOOTSTRAP:
			entryParent = getBootstrapEntry();
			break;
		case USER:
			entryParent = getUserEntry();
			break;
		case ASPECTPATH:
			entryParent = getAspectPathEntry();
			break;
		case OUTJAR:
			entryParent = getOutJarEntry();
		default:
			break;
		}

		ClasspathEntry newEntry = createEntry(entry, entryParent);
    for (Object element : childEntries) {
      if (element instanceof ClasspathGroup) {
        if (((ClasspathGroup) element).contains(newEntry)) {
          return null;
        }
      }
      else if (element.equals(newEntry)) {
        return null;
      }
    }
		if (entryParent != null) {
			((ClasspathGroup) entryParent).addEntry(newEntry,null);
		} else {
			childEntries.add(newEntry);
		}
		return newEntry;
	}

	/**
	 * Remove all entries
	 */
	public void removeAll() {
		if (bootstrapEntries != null) {
			bootstrapEntries.removeAll();
		}
		if (userEntries != null) {
			userEntries.removeAll();
		}
		if (aspectPathEntries != null) {
			aspectPathEntries.removeAll();
		}
		if (outJarEntries != null) {
			outJarEntries.removeAll();
		}
	}

	/**
	 * Copied from super - set bootstrap entries
	 */
	public void setBootstrapEntries(IRuntimeClasspathEntry[] entries) {
		if (bootstrapEntries == null) {
			getBootstrapEntry();
		}
		bootstrapEntries.removeAll();
    for (IRuntimeClasspathEntry entry : entries) {
      bootstrapEntries.addEntry(new ClasspathEntry(entry,
        bootstrapEntries), null);
    }
	}

	/**
	 * Copied from super - set user entries
	 */
	public void setUserEntries(IRuntimeClasspathEntry[] entries) {
		if (userEntries == null) {
			getUserEntry();
		}
		userEntries.removeAll();
    for (IRuntimeClasspathEntry entry : entries) {
      userEntries.addEntry(new ClasspathEntry(entry, userEntries), null);
    }
	}

	/**
	 * Set the AspectPath entries
	 *
	 * @param entries -
	 *            classpath entries
	 */
	public void setAspectPathEntries(IRuntimeClasspathEntry[] entries) {
		if (aspectPathEntries == null) {
			getAspectPathEntry();
		}
		aspectPathEntries.removeAll();
    for (IRuntimeClasspathEntry entry : entries) {
      aspectPathEntries.addEntry(new ClasspathEntry(entry,
        aspectPathEntries), null);
    }
	}

	private IClasspathEntry getAspectPathEntry() {
		if (aspectPathEntries == null) {
			String name = UIMessages.Launcher_aspectPath;
			aspectPathEntries = createGroupEntry(new IRuntimeClasspathEntry[0],
					null, name, false, true);
		}
		return aspectPathEntries;
	}

	private IClasspathEntry getOutJarEntry() {
		if (outJarEntries == null) {
			String name = UIMessages.Launcher_outJar;
			outJarEntries = createGroupEntry(new IRuntimeClasspathEntry[0],
					null, name, false, true);
		}
		return outJarEntries;
	}

	public IClasspathEntry getBootstrapEntry() {
		if (bootstrapEntries == null) {
			String name = ClasspathMessages.ClasspathModel_0;
			bootstrapEntries = createGroupEntry(new IRuntimeClasspathEntry[0],
					null, name, false, true);
		}
		return bootstrapEntries;
	}

	public IClasspathEntry getUserEntry() {
		if (userEntries == null) {
			String name = ClasspathMessages.ClasspathModel_1;
			userEntries = createGroupEntry(new IRuntimeClasspathEntry[0], null,
					name, false, true);
		}
		return userEntries;
	}

	/**
	 * Copied from super class - create an entry
	 */
	public ClasspathGroup createGroupEntry(IRuntimeClasspathEntry[] entries,
			ClasspathGroup entryParent, String name, boolean canBeRemoved,
			boolean addEntry) {

		ClasspathGroup group = new ClasspathGroup(name, entryParent,
				canBeRemoved);

    for (IRuntimeClasspathEntry entry : entries) {
      group.addEntry(new ClasspathEntry(entry, group), null);
    }

		if (addEntry) {
			addEntry(group);
		}
		return group;
	}

	/**
	 * Returns the entries of the given type, or an empty collection if none.
	 *
	 * @param entryType
	 * @return the entries of the given type, or an empty collection if none
	 */
	public IClasspathEntry[] getEntries(int entryType) {
		switch (entryType) {
		case BOOTSTRAP:
			if (bootstrapEntries != null) {
				return bootstrapEntries.getEntries();
			}
			break;
		case USER:
			if (userEntries != null) {
				return userEntries.getEntries();
			}
			break;
		case ASPECTPATH:
			if (aspectPathEntries != null) {
				return aspectPathEntries.getEntries();
			}
			break;
		case OUTJAR:
			if(outJarEntries != null) {
				return outJarEntries.getEntries();
			}
		}
		return new IClasspathEntry[0];
	}
}
