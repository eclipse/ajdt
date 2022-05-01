/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation
 * 				 Helen Hawkins   - initial version
 *               Helen Hawkins   - updated for new ajde interface (bug 148190)
 ******************************************************************************/
package org.eclipse.ajdt.ui.tests.ajde;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.internal.ui.preferences.AJCompilerPreferencePage;
import org.eclipse.ajdt.internal.ui.preferences.AspectJPreferences;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.preference.IPreferenceStore;

/**
 * This class tests BuildOptionsAdapter. In particular, there are two mechanisms
 * through which options can be set, one via Window > Preferences and the other
 * via right click > preferences. Where appropriate, the methods in
 * BuildOptionsAdapter are tested when options are set in both these ways.
 */
public class BuildOptionsAdapterTest extends UITestCase {

	IProject project;

	IJavaProject jp;

	IPreferenceStore prefStore;

	IEclipsePreferences projectNode;

	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		project = createPredefinedProject("Simple AJ Project"); //$NON-NLS-1$
		jp = JavaCore.create(project);

		prefStore = AspectJUIPlugin.getDefault().getPreferenceStore();
		AJCompilerPreferencePage.initDefaults(prefStore);

		IScopeContext projectScope = new ProjectScope(project);
		projectNode = projectScope.getNode(AspectJPlugin.PLUGIN_ID);

	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		prefStore = null;
		projectNode = null;
	}

	public void testGetNonStandardOptionsViaWorkbenchPreferences()
  {
		String nonStandard = AspectJUIPlugin.getDefault()
				.getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration().getNonStandardOptions();
		String[] nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should only have -Xlintfile option set", 2, //$NON-NLS-1$
				nonStandardOptions.length);
		assertEquals("first element should be -Xlintfile", "-Xlintfile", //$NON-NLS-1$ //$NON-NLS-2$
				nonStandardOptions[0]);

		prefStore
				.setValue(AspectJPreferences.OPTION_XSerializableAspects, true);
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should have set -XserializableAspects option", //$NON-NLS-1$
				"-XserializableAspects", nonStandardOptions[2]); //$NON-NLS-1$

		prefStore.setValue(AspectJPreferences.OPTION_XSerializableAspects,
				false);
		nonStandardOptions = disectOptions(AspectJUIPlugin.getDefault()
				.getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration().getNonStandardOptions());
		assertEquals("should only have -Xlintfile option set", 2, //$NON-NLS-1$
				nonStandardOptions.length);

		prefStore.setValue(AspectJPreferences.OPTION_XNoInline, true);
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should have set -XnoInline option", "-XnoInline", //$NON-NLS-1$ //$NON-NLS-2$
				nonStandardOptions[2]);

		prefStore.setValue(AspectJPreferences.OPTION_XNotReweavable, true);
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should have set -XnotReweavable", "-XnotReweavable", //$NON-NLS-1$ //$NON-NLS-2$
				nonStandardOptions[3]);

		prefStore.setValue(AspectJPreferences.OPTION_XNotReweavable, false);
		prefStore.setValue(AspectJPreferences.OPTION_XHasMember, true);
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should have set -XhasMember", "-XhasMember", //$NON-NLS-1$ //$NON-NLS-2$
				nonStandardOptions[3]);

		prefStore.setValue(AspectJPreferences.OPTION_XHasMember, false);
		prefStore.setValue(AspectJPreferences.OPTION_XNoInline, false);
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should only have -Xlintfile option set", 2, //$NON-NLS-1$
				nonStandardOptions.length);

		AspectJPreferences.setShowWeaveMessagesOption(project,true);
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should have set -showWeaveInfo", "-showWeaveInfo", //$NON-NLS-1$ //$NON-NLS-2$
				nonStandardOptions[2]);

		AspectJPreferences.setShowWeaveMessagesOption(project,false);
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should only have -Xlintfile option set", 2, //$NON-NLS-1$
				nonStandardOptions.length);

	}

	public void testGetNonStandardOptionsViaProjectPreferences()
  {
		AspectJPreferences.setUsingProjectSettings(project, true);
		assertTrue("should be using project settings", AspectJPreferences //$NON-NLS-1$
				.isUsingProjectSettings(project));

		String nonStandard = AspectJUIPlugin.getDefault()
				.getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration().getNonStandardOptions();
		String[] nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should only have -Xlintfile option set", 2, //$NON-NLS-1$
				nonStandardOptions.length);
		assertEquals("first element should be -Xlintfile", "-Xlintfile", //$NON-NLS-1$ //$NON-NLS-2$
				nonStandardOptions[0]);

		projectNode.put(AspectJPreferences.OPTION_XSerializableAspects, "true"); //$NON-NLS-1$
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should have set -XserializableAspects option", //$NON-NLS-1$
				"-XserializableAspects", nonStandardOptions[2]); //$NON-NLS-1$


		projectNode
				.put(AspectJPreferences.OPTION_XSerializableAspects, "false"); //$NON-NLS-1$
		nonStandardOptions = disectOptions(AspectJUIPlugin.getDefault()
				.getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration().getNonStandardOptions());
		assertEquals("should only have -Xlintfile option set", 2, //$NON-NLS-1$
				nonStandardOptions.length);

		projectNode.put(AspectJPreferences.OPTION_XNoInline, "true"); //$NON-NLS-1$
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should have set -XnoInline option", "-XnoInline", //$NON-NLS-1$ //$NON-NLS-2$
				nonStandardOptions[2]);

		projectNode.put(AspectJPreferences.OPTION_XNotReweavable, "true"); //$NON-NLS-1$
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should have set -XnotReweavable", "-XnotReweavable", //$NON-NLS-1$ //$NON-NLS-2$
				nonStandardOptions[3]);

		projectNode.put(AspectJPreferences.OPTION_XNotReweavable, "false"); //$NON-NLS-1$
		projectNode.put(AspectJPreferences.OPTION_XHasMember, "true"); //$NON-NLS-1$
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should have set -XhasMember", "-XhasMember", //$NON-NLS-1$ //$NON-NLS-2$
				nonStandardOptions[3]);

		projectNode.put(AspectJPreferences.OPTION_XHasMember, "false"); //$NON-NLS-1$
				projectNode.put(AspectJPreferences.OPTION_XNoInline, "false"); //$NON-NLS-1$
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should only have -Xlintfile option set", 2, //$NON-NLS-1$
				nonStandardOptions.length);

		AspectJPreferences.setShowWeaveMessagesOption(project,true);
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should have set -showWeaveInfo", "-showWeaveInfo", //$NON-NLS-1$ //$NON-NLS-2$
				nonStandardOptions[2]);

		AspectJPreferences.setShowWeaveMessagesOption(project,false);
		nonStandard = AspectJUIPlugin.getDefault().getCompilerFactory().getCompilerForProject(project).getCompilerConfiguration()
				.getNonStandardOptions();
		nonStandardOptions = disectOptions(nonStandard);
		assertEquals("should only have -Xlintfile option set", 2, //$NON-NLS-1$
				nonStandardOptions.length);

		AspectJPreferences.setUsingProjectSettings(project, false);

	}

	/**
	 * There are two ways that this can be set: one via Window > Preferences and
	 * one via right click > preferences. In both these cases we need to know
	 * that the option is being passed through. Therefore, in this test are
	 * following the mechanism used by AJCompilerPreferencePage, i.e. using the
	 * preference store
	 */
	public void testGetShowWeaveMessagesViaWorkbenchPreferences()
  {
		assertFalse("default setting is not to show weave info", //$NON-NLS-1$
				AspectJPreferences.getShowWeaveMessagesOption(project));
		// know that when "show weave messages" is selected in the preference
		// page, then set this store value to true because use the
		// getSelection() call on the button to see whether it
		// is selected (weave messages on) or not (weave messages off)
		//prefStore.setValue(AspectJPreferences.OPTION_WeaveMessages, true);
		AspectJPreferences.setShowWeaveMessagesOption(project,true);
		assertTrue("have chosen to show weave info",  //$NON-NLS-1$
				AspectJPreferences.getShowWeaveMessagesOption(project));

		AspectJPreferences.setShowWeaveMessagesOption(project,false);
		assertFalse("have chosen not to show weave info", //$NON-NLS-1$
				AspectJPreferences.getShowWeaveMessagesOption(project));
	}

	/**
	 * There are two ways that this can be set: one via Window > Preferences and
	 * one via right click > preferences. In both these cases we need to know
	 * that the option is being passed through. Therefore, in this test are
	 * following the mechanism used by CompilerPropertyPage i.e. using the
	 * projectNode
	 */
	public void testGetShowWeaveMessagesViaProjectPreferences()
  {
		AspectJPreferences.setUsingProjectSettings(project, true);

		assertFalse("default setting is not to show weave info", //$NON-NLS-1$
				AspectJPreferences.getShowWeaveMessagesOption(project));
		// know that when "show weave messages" is selected in the preference
		// page, then set this store value to true because use the
		// following :
		// String stringValue = curr.getSelection() ? "true" : "false";
		// to see whether it is selected (weave messages on) or not (weave
		// messages off)
		AspectJPreferences.setShowWeaveMessagesOption(project,true);
		assertTrue("have chosen to show weave info", //$NON-NLS-1$
				AspectJPreferences.getShowWeaveMessagesOption(project));

		AspectJPreferences.setShowWeaveMessagesOption(project,false);
		assertFalse("have chosen not to show weave info", //$NON-NLS-1$
				AspectJPreferences.getShowWeaveMessagesOption(project));
	}

	private String[] disectOptions(String nonStdOptions) {
		// Break a string into a string array of non-standard options.
		// Allows for one option to include a ' '. i.e. assuming it has been
		// quoted, it
		// won't accidentally get treated as a pair of options (can be needed
		// for xlint props file option)
		List<String> tokens = new ArrayList<>();
		int ind = nonStdOptions.indexOf('\"');
		int ind2 = nonStdOptions.indexOf('\"', ind + 1);
		if ((ind > -1) && (ind2 > -1)) { // dont tokenize within double quotes
			String pre = nonStdOptions.substring(0, ind);
			String quoted = nonStdOptions.substring(ind + 1, ind2);
			String post = nonStdOptions.substring(ind2 + 1);
			tokens.addAll(tokenizeString(pre));
			tokens.add(quoted);
			tokens.addAll(tokenizeString(post));
		} else {
			tokens.addAll(tokenizeString(nonStdOptions));
		}
    return tokens.toArray(new String[0]);
	}

	/** Local helper method for splitting option strings */
	private static List<String> tokenizeString(String str) {
		List<String> tokens = new ArrayList<>();
		StringTokenizer tok = new StringTokenizer(str);
		while (tok.hasMoreTokens()) {
			tokens.add(tok.nextToken());
		}
		return tokens;
	}
}
