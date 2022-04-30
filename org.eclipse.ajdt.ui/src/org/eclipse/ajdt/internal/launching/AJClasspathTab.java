/**********************************************************************
 * Copyright (c) 2004 IBM Corporation and others. All rights reserved. This program and the
 * accompanying materials are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Sian January - initial version ...
 **********************************************************************/
package org.eclipse.ajdt.internal.launching;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ajdt.core.AJLog;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaClasspathTab;
import org.eclipse.jdt.internal.debug.ui.IJavaDebugHelpContextIds;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jdt.internal.debug.ui.JavaDebugImages;
import org.eclipse.jdt.internal.debug.ui.actions.AddAdvancedAction;
import org.eclipse.jdt.internal.debug.ui.actions.AddExternalFolderAction;
import org.eclipse.jdt.internal.debug.ui.actions.AddExternalJarAction;
import org.eclipse.jdt.internal.debug.ui.actions.AddFolderAction;
import org.eclipse.jdt.internal.debug.ui.actions.AddJarAction;
import org.eclipse.jdt.internal.debug.ui.actions.AddLibraryAction;
import org.eclipse.jdt.internal.debug.ui.actions.AddProjectAction;
import org.eclipse.jdt.internal.debug.ui.actions.AddVariableAction;
import org.eclipse.jdt.internal.debug.ui.actions.AttachSourceAction;
import org.eclipse.jdt.internal.debug.ui.actions.MoveDownAction;
import org.eclipse.jdt.internal.debug.ui.actions.MoveUpAction;
import org.eclipse.jdt.internal.debug.ui.actions.RemoveAction;
import org.eclipse.jdt.internal.debug.ui.actions.RestoreDefaultEntriesAction;
import org.eclipse.jdt.internal.debug.ui.actions.RuntimeClasspathAction;
import org.eclipse.jdt.internal.debug.ui.classpath.BootpathFilter;
import org.eclipse.jdt.internal.debug.ui.classpath.ClasspathContentProvider;
import org.eclipse.jdt.internal.debug.ui.classpath.ClasspathEntry;
import org.eclipse.jdt.internal.debug.ui.classpath.ClasspathLabelProvider;
import org.eclipse.jdt.internal.debug.ui.classpath.ClasspathModel;
import org.eclipse.jdt.internal.debug.ui.classpath.IClasspathEntry;
import org.eclipse.jdt.internal.debug.ui.classpath.RuntimeClasspathViewer;
import org.eclipse.jdt.internal.debug.ui.launcher.LauncherMessages;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.action.IAction;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.PlatformUI;

/**
 * A launch configuration tab that displays and edits the user and bootstrap classes comprising the
 * classpath launch configuration attribute.
 * <p>
 * This class may be instantiated. This class is not intended to be subclassed.
 * </p>
 * @since 2.0
 */
public class AJClasspathTab extends JavaClasspathTab {

    protected RuntimeClasspathViewer fClasspathViewer;

    private AJClasspathModel fModel;

    protected static final String DIALOG_SETTINGS_PREFIX = "JavaClasspathTab"; //$NON-NLS-1$

    /**
     * The last launch config this tab was initialized from
     */
    protected ILaunchConfiguration fLaunchConfiguration;

    /*
     * (non-Javadoc)
     *
     * @see
     * org.eclipse.debug.ui.ILaunchConfigurationTab#createControl(org.eclipse.swt.widgets.Composite)
     */
    public void createControl(Composite parent) {
        Font font = parent.getFont();

        Composite comp = new Composite(parent, SWT.NONE);
        setControl(comp);
        PlatformUI
                .getWorkbench()
                .getHelpSystem()
                .setHelp(
                        getControl(),
                        IJavaDebugHelpContextIds.LAUNCH_CONFIGURATION_DIALOG_CLASSPATH_TAB);
        GridLayout topLayout = new GridLayout();
        topLayout.numColumns = 2;
        comp.setLayout(topLayout);
        GridData gd;

        Label label = new Label(comp, SWT.NONE);
        label.setText(LauncherMessages.JavaClasspathTab_0);
        gd = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
        gd.horizontalSpan = 2;
        label.setLayoutData(gd);

        fClasspathViewer = new RuntimeClasspathViewer(comp);
        fClasspathViewer.addEntriesChangedListener(this);
        fClasspathViewer.getTreeViewer().getControl().setFont(font);
        fClasspathViewer.getTreeViewer().setLabelProvider(new ClasspathLabelProvider());
        fClasspathViewer.getTreeViewer().setContentProvider(new ClasspathContentProvider(this));
        if (!isShowBootpath()) {
            fClasspathViewer.getTreeViewer().addFilter(new BootpathFilter());
        }

        Composite pathButtonComp = new Composite(comp, SWT.NONE);
        GridLayout pathButtonLayout = new GridLayout();
        pathButtonLayout.marginHeight = 0;
        pathButtonLayout.marginWidth = 0;
        pathButtonComp.setLayout(pathButtonLayout);
        gd = new GridData(GridData.VERTICAL_ALIGN_BEGINNING
                | GridData.HORIZONTAL_ALIGN_FILL);
        pathButtonComp.setLayoutData(gd);
        pathButtonComp.setFont(font);

        createPathButtons(pathButtonComp);
    }

    /**
     * Creates the buttons to manipulate the classpath.
     *
     * @param pathButtonComp composite buttons are contained in
     * @since 3.0
     */
    protected void createPathButtons(Composite pathButtonComp) {
        List<RuntimeClasspathAction> advancedActions = new ArrayList<>(5);

        createButton(pathButtonComp, new MoveUpAction(fClasspathViewer));
        createButton(pathButtonComp, new MoveDownAction(fClasspathViewer));
        createButton(pathButtonComp, new RemoveAction(fClasspathViewer));
        createButton(pathButtonComp, new AddProjectAction(fClasspathViewer));
        createButton(pathButtonComp, new AddJarAction(fClasspathViewer));
        createButton(pathButtonComp, new AddExternalJarAction(fClasspathViewer,
                DIALOG_SETTINGS_PREFIX));

        RuntimeClasspathAction action = new AddFolderAction(null);
        advancedActions.add(action);

        action = new AddExternalFolderAction(null, DIALOG_SETTINGS_PREFIX);
        advancedActions.add(action);

        action = new AddVariableAction(null);
        advancedActions.add(action);

        action = new AddLibraryAction(null);
        advancedActions.add(action);

        action = new AttachSourceAction(null, SWT.RADIO);
        advancedActions.add(action);

        IAction[] adv = advancedActions.toArray(new IAction[0]);
        createButton(pathButtonComp, new AddAdvancedAction(fClasspathViewer, adv));

        action = new RestoreDefaultEntriesAction(fClasspathViewer, this);
        createButton(pathButtonComp, action);
        action.setEnabled(true);
    }

    /**
     * Creates a button for the given action.
     *
     * @param pathButtonComp parent composite for the button
     * @param action the action triggered by the button
     * @return the button that was created
     */
    protected Button createButton(Composite pathButtonComp,
            RuntimeClasspathAction action) {
        Button button = createPushButton(pathButtonComp, action.getText(), null);
        action.setButton(button);
        return button;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#setDefaults(org.eclipse.debug.core.
     * ILaunchConfigurationWorkingCopy)
     */
    public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {}

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#initializeFrom(org.eclipse.debug.core.
     * ILaunchConfiguration)
     */
    public void initializeFrom(ILaunchConfiguration configuration) {
        refresh(configuration);
        updateClassPathWithAspectPathAndOutJar(configuration);
        fClasspathViewer.getTreeViewer().expandToLevel(2);
    }

    /**
     * Update the launch configuration runtime classpath to contain the contents of the aspect path
     * and the outjar, and save the configuration.
     */
    private void updateClassPathWithAspectPathAndOutJar(
            ILaunchConfiguration configuration) {
        if (fModel == null) {
            return;
        }
        ILaunchConfigurationWorkingCopy wc;
        try {
            if (configuration instanceof ILaunchConfigurationWorkingCopy) {
                wc = (ILaunchConfigurationWorkingCopy) configuration;
            } else {
                wc = configuration.getWorkingCopy();
            }

            IRuntimeClasspathEntry[] classpath = LaunchConfigurationClasspathUtils
                    .getCurrentClasspath(fModel);
            boolean def = LaunchConfigurationClasspathUtils.isDefaultClasspath(
                    classpath, wc);
            if (def) {
                wc.setAttribute(
                        IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH,
                        (String) null);
                wc.setAttribute(
                        IJavaLaunchConfigurationConstants.ATTR_CLASSPATH,
                        (String) null);
            } else {
                wc.setAttribute(
                        IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH,
                        false);
                try {
                    List<String> mementos = new ArrayList<>(classpath.length);
                  for (IRuntimeClasspathEntry entry : classpath) {
                    mementos.add(entry.getMemento());
                  }
                    wc.setAttribute(
                            IJavaLaunchConfigurationConstants.ATTR_CLASSPATH,
                            mementos);
                } catch (CoreException e) {
                    AJLog.log(e.getMessage());
                }
                wc.doSave();
            }
        } catch (CoreException e1) {
            AJLog.log(e1.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#activated(org.eclipse.debug.core.
     * ILaunchConfigurationWorkingCopy)
     */
    public void activated(ILaunchConfigurationWorkingCopy workingCopy) {
        try {
            boolean useDefault = workingCopy.getAttribute(
                    IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH,
                    true);
            if (useDefault) {
                if (!isDefaultClasspath(getCurrentClasspath(), workingCopy)) {
                    initializeFrom(workingCopy);
                    return;
                }
            }
            fClasspathViewer.getTreeViewer().refresh();
        } catch (CoreException ignored) {}
    }

    /**
     * Refreshes the classpath entries based on the current state of the given launch configuration.
     */
    private void refresh(ILaunchConfiguration configuration) {
        boolean useDefault = true;
        setErrorMessage(null);
        try {
            useDefault = configuration.getAttribute(
                    IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH,
                    true);
        } catch (CoreException e) {
            JDIDebugUIPlugin.log(e);
        }

        if (configuration == getLaunchConfiguration()) {
            // no need to update if an explicit path is being used and this setting
            // has not changed (and viewing the same config as last time)
            if (!useDefault) {
                setDirty(false);
                return;
            }
        }

        setLaunchConfiguration(configuration);
        try {
            // AspectJ Change - use our own classpath model
            fModel = LaunchConfigurationClasspathUtils
                    .createClasspathModel(configuration);
        } catch (CoreException e) {
            setErrorMessage(e.getMessage());
        }

        fClasspathViewer.setLaunchConfiguration(configuration);
        fClasspathViewer.getTreeViewer().setInput(fModel);
        setDirty(false);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#performApply(org.eclipse.debug.core.
     * ILaunchConfigurationWorkingCopy)
     */
    public void performApply(ILaunchConfigurationWorkingCopy configuration) {
        if (isDirty()) {
            IRuntimeClasspathEntry[] classpath = getCurrentClasspath();
            boolean def = isDefaultClasspath(classpath,
                    configuration.getOriginal());
            if (def) {
                configuration
                        .setAttribute(
                                IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH,
                                (String) null);
                configuration.setAttribute(
                        IJavaLaunchConfigurationConstants.ATTR_CLASSPATH,
                        (String) null);
            } else {
                configuration
                        .setAttribute(
                                IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH,
                                false);
                try {
                    List<String> mementos = new ArrayList<>(classpath.length);
                  for (IRuntimeClasspathEntry entry : classpath) {
                    mementos.add(entry.getMemento());
                  }
                    configuration.setAttribute(
                            IJavaLaunchConfigurationConstants.ATTR_CLASSPATH,
                            mementos);
                } catch (CoreException e) {
                    JDIDebugUIPlugin
                            .errorDialog(
                                    LauncherMessages.JavaClasspathTab_Unable_to_save_classpath_1,
                                    e);
                }
            }
        }
    }

    /**
     * Returns the classpath entries currently specified by this tab.
     *
     * @return the classpath entries currently specified by this tab
     */
    private IRuntimeClasspathEntry[] getCurrentClasspath() {
        IClasspathEntry[] boot = fModel.getEntries(ClasspathModel.BOOTSTRAP);
        IClasspathEntry[] user = fModel.getEntries(ClasspathModel.USER);
        List<IRuntimeClasspathEntry> entries = new ArrayList<>(boot.length + user.length);
        IClasspathEntry bootEntry;
        IRuntimeClasspathEntry entry;
      for (IClasspathEntry classpathEntry : boot) {
        bootEntry = classpathEntry;
        entry = null;
        if (bootEntry instanceof ClasspathEntry) {
          entry = ((ClasspathEntry) bootEntry).getDelegate();
        }
        else if (bootEntry instanceof IRuntimeClasspathEntry) {
          entry = (IRuntimeClasspathEntry) classpathEntry;
        }
        if (entry != null) {
          if (entry.getClasspathProperty() == IRuntimeClasspathEntry.USER_CLASSES) {
            entry.setClasspathProperty(IRuntimeClasspathEntry.BOOTSTRAP_CLASSES);
          }
          entries.add(entry);
        }
      }
        IClasspathEntry userEntry;
      for (IClasspathEntry iClasspathEntry : user) {
        userEntry = iClasspathEntry;
        entry = null;
        if (userEntry instanceof ClasspathEntry) {
          entry = ((ClasspathEntry) userEntry).getDelegate();
        }
        else if (userEntry instanceof IRuntimeClasspathEntry) {
          entry = (IRuntimeClasspathEntry) iClasspathEntry;
        }
        if (entry != null) {
          entry.setClasspathProperty(IRuntimeClasspathEntry.USER_CLASSES);
          entries.add(entry);
        }
      }
        return entries.toArray(new IRuntimeClasspathEntry[0]);
    }

    /**
     * Returns whether the specified classpath is equivalent to the default classpath for this
     * configuration.
     *
     * @param classpath classpath to compare to default
     * @param configuration original configuration
     * @return whether the specified classpath is equivalent to the default classpath for this
     *         configuration
     */
    private boolean isDefaultClasspath(IRuntimeClasspathEntry[] classpath,
            ILaunchConfiguration configuration) {
        try {
            ILaunchConfigurationWorkingCopy wc = configuration.getWorkingCopy();
            wc.setAttribute(
                    IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH,
                    true);
            IRuntimeClasspathEntry[] entries = JavaRuntime
                    .computeUnresolvedRuntimeClasspath(wc);
            if (classpath.length == entries.length) {
                for (int i = 0; i < entries.length; i++) {
                    IRuntimeClasspathEntry entry = entries[i];
                    if (!entry.equals(classpath[i])) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        } catch (CoreException e) {
            return false;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#getName()
     */
    public String getName() {
        return LauncherMessages.JavaClasspathTab_Cla_ss_path_3;
    }

    /**
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#getImage()
     */
    public static Image getClasspathImage() {
        return JavaDebugImages.get(JavaDebugImages.IMG_OBJS_CLASSPATH);
    }

    /**
     * Sets the launch configuration for this classpath tab
     */
    private void setLaunchConfiguration(ILaunchConfiguration config) {
        fLaunchConfiguration = config;
    }

    /**
     * Returns the current launch configuration
     */
    public ILaunchConfiguration getLaunchConfiguration() {
        return fLaunchConfiguration;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#dispose()
     */
    public void dispose() {
        if (fClasspathViewer != null) {
            fClasspathViewer.removeEntriesChangedListener(this);
        }
        super.dispose();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#getImage()
     */
    public Image getImage() {
        return getClasspathImage();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.eclipse.debug.ui.ILaunchConfigurationTab#isValid(org.eclipse.debug.core.ILaunchConfiguration
     * )
     */
    public boolean isValid(ILaunchConfiguration launchConfig) {
        setErrorMessage(null);
        setMessage(null);
        String projectName;
        try {
            projectName = launchConfig.getAttribute(
                    IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, ""); //$NON-NLS-1$
        } catch (CoreException e) {
            return false;
        }
        if (projectName.length() > 0) {
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IStatus status = workspace.validateName(projectName,
                    IResource.PROJECT);
            if (status.isOK()) {
                IProject project = ResourcesPlugin.getWorkspace().getRoot()
                        .getProject(projectName);
                if (!project.exists()) {
                    setErrorMessage(NLS.bind(LauncherMessages.JavaMainTab_20,
                            new String[] { projectName }));
                    return false;
                }
                if (!project.isOpen()) {
                    setErrorMessage(NLS.bind(LauncherMessages.JavaMainTab_21,
                            new String[] { projectName }));
                    return false;
                }
            } else {
                setErrorMessage(NLS.bind(LauncherMessages.JavaMainTab_19,
                        new String[] { status.getMessage() }));
                return false;
            }
        }

        IRuntimeClasspathEntry[] entries = fModel.getAllEntries();
      for (IRuntimeClasspathEntry entry : entries) {
        if (entry.getType() == IRuntimeClasspathEntry.ARCHIVE
            && (!entry.getPath().isAbsolute()))
        {
          setErrorMessage(NLS
            .bind(LauncherMessages.JavaClasspathTab_Invalid_runtime_classpath_1,
              new String[] { entry.getPath().toString() }));
          return false;
        }
      }

        return true;
    }

    /**
     * Returns whether the bootpath should be displayed.
     *
     * @return whether the bootpath should be displayed
     * @since 3.0
     */
    public boolean isShowBootpath() {
        return true;
    }

    /**
     * @return Returns the classpath model.
     */
    protected ClasspathModel getModel() {
        return fModel;
    }
}
