/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andy Clement - initial version
 *******************************************************************************/
package org.eclipse.contribution.visualiser.simpleImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.contribution.visualiser.core.PaletteManager;
import org.eclipse.contribution.visualiser.core.Stripe;
import org.eclipse.contribution.visualiser.interfaces.IGroup;
import org.eclipse.contribution.visualiser.interfaces.IMarkupKind;
import org.eclipse.contribution.visualiser.interfaces.IMarkupProvider;
import org.eclipse.contribution.visualiser.interfaces.IMember;
import org.eclipse.contribution.visualiser.utils.MarkupUtils;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

/**
 * Example implementation of a markup provider.  Loads its information about the markups from
 * a file.  Manages the list of markups in a hashtable - the hashtable maps the member ID to
 * a list of 'Stripe instances' (markups).
 */
public class SimpleMarkupProvider implements IMarkupProvider {

	private Map<String, Color> colourMemory = new HashMap<>();
	private Map<String, List<RGB>> availableColours = new HashMap<>();

	private Map<RGB, Color> allocatedColours = new HashMap<>();

	// Indexed by FULL membername, each entry is a List of Stripe objects.
	private Hashtable<String, List<Stripe>> markups = null;

	private SortedSet<IMarkupKind> markupKinds;

	/**
	 * Initialise the markup provider.  This simple implementation does nothing here.
	 */
	public void initialise() {
	}

	/**
	 * Get a List of Stripes for the given member, which are its markups.
	 */
	public List<Stripe> getMemberMarkups(IMember member) {
		if (member != null && markups != null) {
			List<Stripe> stripes = markups.get(member.getFullname());
			if (stripes != null)
				return markups.get(member.getFullname());
		}
		return null;
	}

	/**
	 * Add a Stripe to the member with the given name.
	 *
	 * @param membername
	 * @param s
	 */
	public void addMarkup(String membername, Stripe s) {
		if (markups == null)
			markups = new Hashtable<>();
		List<Stripe> stripes = markups.get(membername);
		if (stripes == null) {
			stripes = new ArrayList<>();
			stripes.add(s);
			markups.put(membername, stripes);
		}
		else
			stripes.add(s);
	}

	/**
	 * Add a markup kind
	 *
	 * @param kind
	 */
	public void addMarkupKind(IMarkupKind kind) {
		markupKinds.add(kind);
	}

	/**
	 * Process all the Stripes that have been added to deal with the overlapping cases
	 */
	public void processMarkups() {
		Enumeration<String> memkeys = markups.keys();
		while (memkeys.hasMoreElements()) {
			String memberID = memkeys.nextElement();
			List<Stripe> unprocessedListOfStripes = markups.get(memberID);
			MarkupUtils.processStripes(unprocessedListOfStripes);
		}
	}

	/**
	 * Get the markups for a group. Group markups are a stacked set of member markups.
	 */
	public List<Stripe> getGroupMarkups(IGroup group) {
		List<Stripe> stripes = new ArrayList<>();
		List<IMember> kids = group.getMembers();
		int accumulatedOffset = 0;

		// Go through all the children of the group
		for (IMember kid : kids) {
			List<Stripe> kidStripes = getMemberMarkups(kid);
			if (kidStripes != null) {
				for (Stripe kidStripe : kidStripes) {
					stripes.add(
						new Stripe(
							kidStripe.getKinds(),
							kidStripe.getOffset() + accumulatedOffset,
							kidStripe.getDepth()
						)
					);
				}
			}
			accumulatedOffset += kid.getSize();
		}
		return stripes;
	}

	/**
	 * Get all the markup kinds.
	 *
	 * @return a Set of IMarkupKinds
	 */
	public SortedSet<? extends IMarkupKind> getAllMarkupKinds() {
		// Created sorted list of markups
		if (markups == null)
			return null;
		if (markupKinds != null)
			return markupKinds;
		// FIXME: IMarkupKind is not Comparable, only SimpleMarkupKind is
		markupKinds = new TreeSet<>();

		Enumeration<List<Stripe>> stripeLists = markups.elements();
		while (stripeLists.hasMoreElements()) {
			List<Stripe> stripes = stripeLists.nextElement();
			for (Stripe stripe : stripes)
        markupKinds.addAll(stripe.getKinds());
		}
		return markupKinds;
	}

// Color management

	/**
	 * Get the colour for a given kind
	 *
	 * @param kind - the kind
	 * @return the Color for that kind
	 */
	public Color getColorFor(IMarkupKind kind) {
		Color stripeColour;
		String p = "not unique"; //Note: String not displayed externally //$NON-NLS-1$
		String key = p + ":" + kind.getFullName(); //$NON-NLS-1$
		if (colourMemory.containsKey(key))
			stripeColour = colourMemory.get(key);
		else {
			stripeColour = getNextColourFor(p);
			colourMemory.put(key, stripeColour);
		}
		return stripeColour;
	}

	/**
	 * Set the color for a kind.
	 *
	 * @param kind  - the kind
	 * @param color - the Color
	 */
	public void setColorFor(IMarkupKind kind, Color color) {
		colourMemory.put("not unique:" + kind.getName(), color); //Note: String not displayed externally //$NON-NLS-1$
	}

	/**
	 * Get the next assignable colour and assign it to the String argument.
	 *
	 * @param p - the kind
	 * @return new Color
	 */
	protected Color getNextColourFor(String p) {
		if (!availableColours.containsKey(p)) {
			RGB[] rgb = PaletteManager.getCurrentPalette().getPalette().getRGBValues();
			List<RGB> colourList = Arrays.asList(rgb);
			availableColours.put(p, colourList);
		}

		List<RGB> colours = availableColours.get(p);
		RGB rgb;
		if (!colours.isEmpty()) {
			rgb = colours.get(0);
			colours.remove(0);
		}
		else
			rgb = PaletteManager.getCurrentPalette().getPalette().getRandomRGBValue();

		Color color = allocatedColours.get(rgb);
		if (color == null) {
			color = new Color(Display.getDefault(), rgb);
			allocatedColours.put(rgb, color);
		}
		return color;
	}

	/**
	 * Empty the data structures that contain the stripe and kind information
	 */
	public void resetMarkupsAndKinds() {
		markups = new Hashtable<>();
    // FIXME: IMarkupKind is not Comparable, only SimpleMarkupKind is
		markupKinds = new TreeSet<>();
	}

	/**
	 * Reset the color memory
	 */
	public void resetColours() {
		availableColours = new HashMap<>();
		colourMemory = new HashMap<>();
	}

	/**
	 * Reset the color memory
	 */
	private void disposeColors() {
		for (RGB rgb : allocatedColours.keySet()) {
			Color color = allocatedColours.get(rgb);
			color.dispose();
		}
		allocatedColours = new HashMap<>();
	}

	/**
	 * Process a mouse click on a stripe.  This implementation does nothing and returns
	 * true to allow the visualiser to perform the default operations.
	 *
	 * @see org.eclipse.contribution.visualiser.interfaces.IMarkupProvider#processMouseclick(IMember, Stripe, int)
	 */
	public boolean processMouseclick(IMember member, Stripe stripe, int buttonClicked) {
		return true;
	}

	/**
	 * Activate the provider
	 */
	public void activate() { }

	/**
	 * Deactivate the provider
	 */
	public void deactivate() {
		resetColours();
		disposeColors();
	}

}
