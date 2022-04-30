/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *     Helen Hawkins - updated for new ajde interface (bug 148190)
 *******************************************************************************/
package org.eclipse.ajdt.core;

import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.internal.core.CompilerConfigResourceChangeListener;
import org.eclipse.ajdt.internal.core.ajde.CoreCompilerFactory;
import org.eclipse.ajdt.internal.core.ajde.ICompilerFactory;
import org.eclipse.ajdt.internal.core.ras.NoFFDC;
import org.eclipse.contribution.jdt.IsWovenTester;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.framework.BundleContext;

/**
 * The main plugin class to be used in the desktop.
 */
public class AspectJPlugin extends Plugin implements NoFFDC {
	//The shared instance.
	private static AspectJPlugin plugin;

	// id of this plugin
	public static final String PLUGIN_ID = "org.eclipse.ajdt.core";

	// plugin containing aspectjtools.jar, or the contents thereof
    public static final String TOOLS_PLUGIN_ID = "org.aspectj.ajde";

	// plugin containing aspectjweaver.jar, or the contents thereof
    public static final String WEAVER_PLUGIN_ID = "org.aspectj.weaver";

	// the plugin containing aspectjrt.jar
	public static final String RUNTIME_PLUGIN_ID = "org.aspectj.runtime";

	public static final String ID_BUILDER = PLUGIN_ID + ".ajbuilder";

	/**
	 * The name of the default build config file for an AspectJ project
	 */
	public static final String DEFAULT_CONFIG_FILE = ".generated.lst";

	public static final String UI_PLUGIN_ID = "org.eclipse.ajdt.ui";
	public static final String ID_NATURE = UI_PLUGIN_ID + ".ajnature";

	public static final String JAVA_NATURE_ID = "org.eclipse.jdt.core.javanature";

	public static final String AJ_FILE_EXT = "aj";

	public static final String ASPECTJRT_CONTAINER = PLUGIN_ID + ".ASPECTJRT_CONTAINER";

	// AspectJ keywords
    public static final String[] ajKeywords = { "aspect", "pointcut", "privileged",
		// Pointcut designators: methods and constructora
		"call", "execution", "initialization", "preinitialization" ,
		// Pointcut designators: exception handlers
		"handler",
		// Pointcut designators: fields
		"get", "set",
		// Pointcut designators: static initialization
		"staticinitialization",
		// Pointcut designators: object
		// (this already a Java keyword)
		"target", "args",
		// Pointcut designators: lexical extents
		"within", "withincode",
		// Pointcut designators: control flow
		"cflow", "cflowbelow",
		// Pointcut Designators for annotations
		"annotation",
		// Advice
		"before", "after", "around", "proceed", "throwing" , "returning" ,
		"adviceexecution" ,
		// Declarations
		"declare", "parents" , "warning" , "error", "soft" , "precedence" ,
		// variables
		"thisJoinPoint" , "thisJoinPointStaticPart" , "thisEnclosingJoinPointStaticPart" ,
		// Associations
		"issingleton", "perthis", "pertarget", "percflow", "percflowbelow", "pertypewithin",
		// Declare annotation
		"@type", "@method", "@field", "@constructor",

		// Optional keywords
		// See https://bugs.eclipse.org/bugs/show_bug.cgi?id=373584
		"lock", "unlock", "thisAspectInstance"
    };


    public static final String[] declareAnnotationKeywords = { "type", "method", "field", "constructor" };

	/**
	 * Folder separator used by Eclipse in paths irrespective if on Windows or
	 * *nix.
	 */
	public static final String NON_OS_SPECIFIC_SEPARATOR = "/";

	public static final boolean USING_CU_PROVIDER = checkForCUprovider();


	/**
	 * The compiler factory
	 */
	private ICompilerFactory compilerFactory;

	/**
	 * Is true if running with no UI.
	 */
	private boolean isHeadless;

	/**
	 * The constructor.
	 */
	public AspectJPlugin() {
		super();
		AspectJPlugin.plugin = this;
	}

	/**
	 * This method is called upon plug-in activation
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		getWorkspace().addResourceChangeListener(
				new CompilerConfigResourceChangeListener(),
				IResourceChangeEvent.POST_CHANGE | IResourceChangeEvent.PRE_DELETE);
		setCompilerFactory(new CoreCompilerFactory());

		try {
		    initializeContentAssistProvider();
		} catch (Throwable t) {
		    // ignore, likely that JDT weaving plugin is not available
		}

		AJProjectModelFacade.installListener();
	}

    private void initializeContentAssistProvider() {
//        ITDAwarenessAspect.contentAssistProvider = new ContentAssistProvider();
    }

	/**
	 * Sets the usingCUprovider flag if the experimental JDT extension is available
	 *
	 */
	private static boolean checkForCUprovider() {
	    try {
	        return IsWovenTester.isWeavingActive();
	    } catch (Throwable t) {
	        return false;
	    }
	}

	/**
	 * This method is called when the plug-in is stopped
	 */
	public void stop(BundleContext context) throws Exception {
		super.stop(context);
	}

	/**
	 * Returns the shared instance.
	 */
	public static AspectJPlugin getDefault() {
		return plugin;
	}

	/**
	 * Returns the workspace instance.
	 */
	public static IWorkspace getWorkspace() {
		return ResourcesPlugin.getWorkspace();
	}

	/**
	 * Returns true if the given project has the AspectJ nature. Returns
	 * false otherwise, or if the nature could not be determined (e.g. the
	 * project is closed).
	 * @param project
	 * @return
	 */
	public static boolean isAJProject(IProject project) {
		// Fix for 106707 - check that project is open
		if(project != null && project.isAccessible()) {
			try {
				if (project.hasNature(ID_NATURE)) {
					return true;
				}
			} catch (CoreException ignored) {
			}
		}
		return false;
	}

	public void setAJLogger(IAJLogger logger) {
		AJLog.setLogger(logger);
	}

	public ICompilerFactory getCompilerFactory() {
		return compilerFactory;
	}

	public void setCompilerFactory(ICompilerFactory compilerFactory) {
		this.compilerFactory = compilerFactory;
	}

	public IEclipsePreferences getPreferences() {
        return InstanceScope.INSTANCE.getNode(PLUGIN_ID);
	}

	public void setHeadless(boolean isHeadless) {
        this.isHeadless = isHeadless;
    }

	public boolean isHeadless() {
        return isHeadless;
    }
}
