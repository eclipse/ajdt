/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Sian January - initial version
 ******************************************************************************/
package org.eclipse.ajdt.internal.ui.visualiser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.aspectj.asm.IProgramElement;
import org.aspectj.asm.IRelationship;
import org.eclipse.ajdt.core.javaelements.IAJCodeElement;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.ajdt.core.model.AJRelationshipType;
import org.eclipse.ajdt.internal.ui.resources.AspectJImages;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.contribution.visualiser.VisualiserPlugin;
import org.eclipse.contribution.visualiser.core.ProviderManager;
import org.eclipse.contribution.visualiser.core.Stripe;
import org.eclipse.contribution.visualiser.interfaces.IMarkupKind;
import org.eclipse.contribution.visualiser.interfaces.IMember;
import org.eclipse.contribution.visualiser.jdtImpl.JDTMember;
import org.eclipse.contribution.visualiser.markerImpl.ResourceMember;
import org.eclipse.contribution.visualiser.simpleImpl.SimpleMarkupKind;
import org.eclipse.contribution.visualiser.simpleImpl.SimpleMarkupProvider;
import org.eclipse.contribution.visualiser.simpleImpl.StealthMarkupKind;
import org.eclipse.contribution.visualiser.utils.JDTUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.ui.IActionBars;

/**
 * Markup provider for AJDT
 */
public class AJDTMarkupProvider extends SimpleMarkupProvider {

	private IAction hideErrorsAction;
	private Action hideWarningsAction;
	private boolean hideErrors;
	private boolean hideWarnings;
	private Map<String, IMarkupKind> kindMap;
	protected static final String aspectJErrorKind = "declare error"; //$NON-NLS-1$
	protected static final String aspectJWarningKind = "declare warning"; //$NON-NLS-1$
	protected Map<String, String> savedColours;

	private static final Color aspectJErrorColor = new Color(null, new RGB(228, 5, 64));
	private static final Color aspectJWarningColor = new Color(null, new RGB(255, 206, 90));
	private Action resetColorMemoryAction;
	private static final String resetColorMemoryID = "ResetColorMemoryUniqueID32dfnio239";  //$NON-NLS-1$
	private final IPreferenceStore prefs = AspectJUIPlugin.getDefault().getPreferenceStore();
	private static final String allPrefereceKeys = "AJDTVisualiserMarkupProvider.allPrefereceKeys"; //$NON-NLS-1$

	/**
	 * Get a List of Stripes for the given member, which are its markups.
	 */
	public List<Stripe> getMemberMarkups(IMember member) {
		if (kindMap == null)
			updateModel();
		List<Stripe> markupList = super.getMemberMarkups(member);
		if (markupList != null)
			return checkErrorsAndWarnings(markupList);
		return null;
	}

	private void updateModel() {
		if (ProviderManager.getContentProvider() instanceof AJDTContentProvider) {
			IJavaProject jp = ((AJDTContentProvider) ProviderManager.getContentProvider()).getCurrentProject();
			if (jp != null) {
				AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForProject(jp.getProject());

				Collection<IRelationship> allRelationships = model.getRelationshipsForProject(
					new AJRelationshipType[] { AJRelationshipManager.ADVISED_BY, AJRelationshipManager.ANNOTATED_BY,
						AJRelationshipManager.ASPECT_DECLARATIONS, AJRelationshipManager.MATCHES_DECLARE });
				if (allRelationships != null) {
					for (IRelationship relationship : allRelationships) {
						List<IMarkupKind> kinds = new ArrayList<>();
						IProgramElement sourceIpe = model.getProgramElement(relationship.getSourceHandle());
						if (sourceIpe != null) {
							List<String> targets = relationship.getTargets();
							for (String targetStr : targets) {
								IJavaElement target = model.programElementToJavaElement(targetStr);
								String simpleName;
								String qualifiedName;

								if (!(target instanceof IAJCodeElement)) {
									IJavaElement enclosingType = target.getAncestor(IJavaElement.TYPE);
									if (enclosingType == null) {
										// Bug 324706  I don't know why the sloc is null.  Log the bug and
										// continue on.
										VisualiserPlugin.log(IStatus.WARNING,
											"Bug 324706: null containing type found for " + target.getElementName() +
											"\nHandle identifier is: " + target.getHandleIdentifier());
										// avoid an npe
										continue;
									}

									simpleName = enclosingType.getElementName();
									qualifiedName = ((IType) enclosingType).getFullyQualifiedName('.');

								}
								else { // It's an injar aspect so we wno't be able to find the parents
									qualifiedName = target.getElementName();
									String[] parts = qualifiedName.split(" "); //$NON-NLS-1$
									String aNameWithExtension = parts[parts.length - 1];
									if (aNameWithExtension.indexOf('.') != -1) { // $NON-NLS-1$
										simpleName = aNameWithExtension.substring(0, aNameWithExtension.lastIndexOf('.')); // $NON-NLS-1$
									}
									else {
										simpleName = aNameWithExtension;
									}
								}

								if (sourceIpe.getSourceLocation() == null) {
									// Bug 324706  I don't know why the sloc is null.  Log the bug and
									// continue on.
									VisualiserPlugin.log(IStatus.WARNING,
										"Bug 324706: Warning, null source location found in " + sourceIpe.getName() +
										"\nHandle identifier is: " + sourceIpe.getHandleIdentifier());
									// avoid an npe
									continue;
								}

								int lineNum = sourceIpe.getSourceLocation().getLine();
								IJavaElement sourceJe = model.programElementToJavaElement(relationship.getSourceHandle());
								if (sourceJe != null) {
									IJavaElement compilationUnitAncestor = sourceJe.getAncestor(IJavaElement.COMPILATION_UNIT);
									if (compilationUnitAncestor != null) {
										String memberName = compilationUnitAncestor.getElementName();
										memberName = memberName.substring(0, memberName.lastIndexOf(".")); //$NON-NLS-1$
										String packageName = sourceJe.getAncestor(IJavaElement.PACKAGE_FRAGMENT).getElementName();
										if (!(packageName.equals(""))) { //$NON-NLS-1$
											memberName = packageName + "." + memberName; //$NON-NLS-1$
										}
										IMarkupKind markupKind;
										if (kindMap == null) {
											kindMap = new HashMap<>();
										}
										if (relationship.getName().equals(AJRelationshipManager.MATCHES_DECLARE.getDisplayName())) {
											String sourceName = target.getElementName();
											boolean errorKind = sourceName.startsWith(aspectJErrorKind);
											if (kindMap.get(sourceName + ":::" + qualifiedName) != null) { //$NON-NLS-1$
												markupKind = kindMap.get(sourceName + ":::" + qualifiedName); //$NON-NLS-1$
											}
											else {
												markupKind = new ErrorOrWarningMarkupKind(sourceName + ":::" + simpleName, errorKind); //$NON-NLS-1$
												kindMap.put(sourceName + ":::" + qualifiedName, markupKind); //$NON-NLS-1$
											}
										}
										else {
											if (kindMap.get(qualifiedName) != null) {
												markupKind = kindMap.get(qualifiedName);
											}
											else {
												markupKind = new SimpleMarkupKind(simpleName, qualifiedName);
												kindMap.put(qualifiedName, markupKind);
											}
										}
										kinds.add(markupKind);
										Stripe stripe = new Stripe(kinds, lineNum, 1);
										addMarkup(memberName, stripe);
									}
								}
							}
						}
					}
				}
			}
		}
		processMarkups();
	}

	/**
	 * @param kinds
	 * @return
	 */
	private List<Stripe> checkErrorsAndWarnings(List<Stripe> stripes) {
		List<Stripe> returningStripes = new ArrayList<>();
		for (Stripe stripe : stripes) {
			List<IMarkupKind> kinds = new ArrayList<>(stripe.getKinds());
			for (Iterator<IMarkupKind> iterator = kinds.iterator(); iterator.hasNext(); ) {
				IMarkupKind kind = iterator.next();
				if (kind instanceof StealthMarkupKind) {
					String name = kind.getName();
					String[] parts = name.split(":::"); //$NON-NLS-1$
					if (parts.length > 1) {
						String eOrWKind = parts[0];
						if (eOrWKind.startsWith(aspectJErrorKind) && hideErrors) {
							iterator.remove();
							continue;
						}
						if (eOrWKind.startsWith(aspectJWarningKind) && hideWarnings) {
							iterator.remove();
							continue;
						}
						String aspectName = parts[1];
						if (VisualiserPlugin.menu != null) {
							if (!VisualiserPlugin.menu.getActive(aspectName))
								iterator.remove();
						}
					}
				}
			}
			if (kinds.size() > 0) {
				Stripe newStripe = new Stripe(kinds, stripe.getOffset(), stripe.getDepth());
				returningStripes.add(newStripe);
			}
		}
		return returningStripes;
	}

	/**
	 * Get the colour for a given kind
	 *
	 * @param kind - the kind
	 * @return the Color for that kind
	 */
	public Color getColorFor(IMarkupKind kind) {
		if (kind.getName().startsWith(aspectJErrorKind))
			return aspectJErrorColor;
		else if (kind.getName().startsWith(aspectJWarningKind))
			return aspectJWarningColor;
		else {
			Color savedColor = getSavedColour(kind.getFullName());
			if (savedColor == null)
				return super.getColorFor(kind);
			else
				return savedColor;
		}
	}

	/**
	 * Set the color for a kind.
	 *
	 * @param string - the kind
	 * @param color  - the Color
	 */
	public void setColorFor(IMarkupKind kind, Color color) {
		super.setColorFor(kind, color);
		saveColourForAspect(kind.getFullName(), color.getRed(), color.getGreen(), color.getBlue());
	}

	/**
	 * Get all the markup kinds.
	 *
	 * @return a Set of IMarkupKinds
	 */
	public SortedSet<? extends IMarkupKind> getAllMarkupKinds() {
		if (kindMap == null) {
			updateModel();
		}
		// FIXME: IMarkupKind is not Comparable, only SimpleMarkupKind is
		TreeSet<IMarkupKind> kinds = new TreeSet<>();
		if (kindMap != null)
			kinds.addAll(kindMap.values());
		return kinds;
	}

	/**
	 * Process a mouse click on a stripe.  This method opens the editor at the line of the stripe clicked.
	 *
	 * @see org.eclipse.contribution.visualiser.interfaces.IMarkupProvider#processMouseclick(java.lang.String, org.eclipse.contribution.visualiser.core.Stripe, int)
	 */
	public boolean processMouseclick(IMember member, Stripe stripe, int buttonClicked) {
		if (buttonClicked == 1) {
			if (member instanceof JDTMember) {
				IJavaElement jEl = ((JDTMember) member).getResource();
				if (jEl != null)
					JDTUtils.openInEditor(jEl.getResource(), stripe.getOffset());
			}
			else if (member instanceof ResourceMember) {
				IResource res = ((ResourceMember) member).getResource();
				if (res != null)
					JDTUtils.openInEditor(res, stripe.getOffset());
			}
			return false;
		}
		else
			return true;
	}

	protected void saveColourForAspect(String aspectName, int r, int g, int b) {
		if (ProviderManager.getContentProvider() instanceof AJDTContentProvider) {
			IProject currentProject = ((AJDTContentProvider) ProviderManager.getContentProvider()).getCurrentProject().getProject();
			String key = currentProject + ":::" + aspectName; //$NON-NLS-1$
			if (prefs.getString(key) == null)
				prefs.setDefault(key, ""); //$NON-NLS-1$

			// Save all keys we add in order that they can be reset.
			String allColourKeys = prefs.getString(allPrefereceKeys);
			if (allColourKeys == null) {
				prefs.putValue(allPrefereceKeys, key);
				prefs.setDefault(allPrefereceKeys, ""); //$NON-NLS-1$
			}
			else {
				allColourKeys += "," + key; //$NON-NLS-1$
				prefs.putValue(allPrefereceKeys, allColourKeys);
			}

			String value = r + "," + g + "," + b; //$NON-NLS-1$ //$NON-NLS-2$
			prefs.setValue(key, value);
			if (savedColours == null)
				savedColours = new HashMap<>();
			savedColours.remove(key);
			savedColours.put(key, value);
		}
	}

	/**
	 * Empty the data structures that contain the stripe and kind information
	 */
	public void resetMarkupsAndKinds() {
		super.resetMarkupsAndKinds();
		kindMap = null;
	}

	protected Color getSavedColour(String aspectName) {
		if (ProviderManager.getContentProvider() instanceof AJDTContentProvider) {
			IProject currentProject = ((AJDTContentProvider) ProviderManager.getContentProvider()).getCurrentProject().getProject();
			String key = currentProject + ":::" + aspectName; //$NON-NLS-1$
			if (savedColours == null)
				savedColours = new HashMap<>();
			String value = savedColours.get(key);
			if (value == null) {
				IPreferenceStore prefs = AspectJUIPlugin.getDefault().getPreferenceStore();
				value = prefs.getString(key);
				savedColours.put(key, value);
			}
			if (value != null && !value.equals("")) { //$NON-NLS-1$
				String[] rgb = value.split(","); //$NON-NLS-1$
				if (rgb.length != 3)
					return null;
				else {
					int r = Integer.parseInt(rgb[0]);
					int g = Integer.parseInt(rgb[1]);
					int b = Integer.parseInt(rgb[2]);
					return new Color(null, new RGB(r, g, b));
				}
			}
		}
		return null;
	}

	/**
	 * Activate the markup provider.
	 */
	public void activate() {
		if (VisualiserPlugin.menu != null) {
			resetColorMemoryAction = new Action() {
				public void run() {
					resetSavedColours();
					VisualiserPlugin.refresh();
				}

			};
			resetColorMemoryAction.setImageDescriptor(AspectJImages.RESET_COLOURS.getImageDescriptor());
			resetColorMemoryAction.setText(UIMessages.ResetColorMemory);
			resetColorMemoryAction.setId(resetColorMemoryID);

			hideErrorsAction = new Action() {
				public int getStyle() {
					return IAction.AS_CHECK_BOX;
				}

				public void run() {
					hideErrors = hideErrorsAction.isChecked();
					VisualiserPlugin.refresh();
				}
			};

			hideErrorsAction.setImageDescriptor(AspectJImages.HIDE_ERRORS.getImageDescriptor());
			hideErrorsAction.setToolTipText(UIMessages.HideErrors);

			hideWarningsAction = new Action() {
				public int getStyle() {
					return IAction.AS_CHECK_BOX;
				}

				public void run() {
					hideWarnings = hideWarningsAction.isChecked();
					VisualiserPlugin.refresh();
				}
			};

			hideWarningsAction.setImageDescriptor(AspectJImages.HIDE_WARNINGS.getImageDescriptor());
			hideWarningsAction.setToolTipText(UIMessages.HideWarnings);
			IActionBars menuActionBars = VisualiserPlugin.menu.getViewSite().getActionBars();
			IToolBarManager toolBarManager = menuActionBars.getToolBarManager();
			toolBarManager.add(hideErrorsAction);
			toolBarManager.add(hideWarningsAction);
			toolBarManager.update(true);
			IMenuManager menuManager = menuActionBars.getMenuManager();
			menuManager.add(new Separator());
			menuManager.add(resetColorMemoryAction);
		}
	}

	/**
	 * Reset all the saved colours
	 */
	protected void resetSavedColours() {
		String colourKeys = prefs.getString(allPrefereceKeys);
		if (colourKeys != null && !colourKeys.equals("")) { //$NON-NLS-1$
			String[] keys = colourKeys.split(","); //$NON-NLS-1$
			for (String key : keys)
				prefs.setToDefault(key);
			prefs.setToDefault(allPrefereceKeys);
		}
		savedColours = new HashMap<>();
		super.resetColours();
	}

	/**
	 * Deactivate
	 */
	public void deactivate() {
		super.deactivate();
		if (VisualiserPlugin.menu != null) {
			IActionBars menuActionBars = VisualiserPlugin.menu.getViewSite().getActionBars();
			IToolBarManager toolBarManager = menuActionBars.getToolBarManager();
			toolBarManager.removeAll();
			toolBarManager.update(true);
			IMenuManager menuManager = menuActionBars.getMenuManager();
			menuManager.remove(resetColorMemoryID);
			menuManager.update(true);
		}
	}

	private static class ErrorOrWarningMarkupKind extends StealthMarkupKind {

		private final boolean errorKind;
		private String declaringAspect;

		/**
		 * @param name
		 */
		public ErrorOrWarningMarkupKind(String name, boolean errorKind) {
			super(name);
			this.errorKind = errorKind;
			String[] nameParts = name.split(":::"); //$NON-NLS-1$
			if (nameParts.length > 1)
				declaringAspect = nameParts[1];
		}

		public String toString() {
			if (errorKind)
				return UIMessages.AspectJError + ": " + declaringAspect; //$NON-NLS-1$
			else
				return UIMessages.AspectJWarning + ": " + declaringAspect; //$NON-NLS-1$
		}
	}
}
