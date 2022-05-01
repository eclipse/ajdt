/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andy Clement - initial version
 * 	   Sian January - added additional methods and refactored
 *******************************************************************************/
package org.eclipse.contribution.visualiser.simpleImpl;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.contribution.visualiser.interfaces.IContentProvider;
import org.eclipse.contribution.visualiser.interfaces.IGroup;
import org.eclipse.contribution.visualiser.interfaces.IMember;
import org.eclipse.jface.resource.ImageDescriptor;

/**
 * Simple implementation of a content provider
 */
public class SimpleContentProvider implements IContentProvider {

	private List<IGroup> groups = null;

	/**
	 * Returns all registered groups
	 *
	 * @return List of IGroups
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#getAllGroups()
	 */
	public List<IGroup> getAllGroups() {
		return groups;
	}

	/**
   * Returns all IMembers contained in the given IGroup
   *
   * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#getAllMembers(IGroup)
   */
	public List<IMember> getAllMembers(IGroup group) {
		return group.getMembers();
	}

	/**
   * Returns the List of all IMembers in all registered groups
   */
	public List<IMember> getAllMembers() {
		List<IGroup> grps = getAllGroups();
		List<IMember> members = new ArrayList<>();
		if (grps == null)
			return members;
		for (IGroup group : grps) {
			List<IMember> membersInGroup = getAllMembers(group);
			members.addAll(membersInGroup);
		}
		return members;
	}

	/**
	 * Initialise the content provider.  This is a default imploementation and
	 * so does nothing.
	 *
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#initialise()
	 */
	public void initialise() {}

	/**
	 * Register a group
	 *
	 * @param grp
	 */
	public void addGroup(IGroup grp) {
		if (groups == null)
			groups = new ArrayList<>();
		groups.add(grp);
	}

	/**
	 * Get the total number of groups registered
	 *
	 * @return the number of groups
	 */
	public int numberOfGroupsDefined() {
		return (groups == null ? 0 : groups.size());
	}

	/**
	 * Process a mouse click on a member belonging to this provider.  This is a default
	 * implementation and so does nothing and returns true to allow the visualiser
	 * to perform it's default mouse click operation.
	 *
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#processMouseclick(IMember, boolean, int)
	 */
	public boolean processMouseclick(IMember member, boolean markupWasClicked, int buttonClicked) {
		return true;
	}

	/**
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#getMemberViewIcon()
	 */
	public ImageDescriptor getMemberViewIcon() {
		return null;
	}

	/**
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#getGroupViewIcon()
	 */
	public ImageDescriptor getGroupViewIcon() {
		return null;
	}

	/**
	 * Empties the data structure that contains the added groups
	 */
	protected void resetModel() {
		groups = null;
	}

	/**
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#activate()
	 */
	public void activate() {
	}

	/**
	 * @see org.eclipse.contribution.visualiser.interfaces.IContentProvider#deactivate()
	 */
	public void deactivate() {
	}
}
