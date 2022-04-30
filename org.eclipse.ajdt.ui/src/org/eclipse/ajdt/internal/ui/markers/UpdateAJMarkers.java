/*******************************************************************************
 * Copyright (c) 2004, 2007 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Sian January - initial version
 *               Matt Chapman - add source of advice markers
 ******************************************************************************/
package org.eclipse.ajdt.internal.ui.markers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.aspectj.asm.IProgramElement;
import org.aspectj.asm.IRelationship;
import org.aspectj.weaver.AdviceKind;
import org.aspectj.weaver.model.AsmRelationshipProvider;
import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.internal.ui.preferences.AspectJPreferences;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.ui.IAJModelMarker;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

/**
 * Class responsible for advice and declaration markers. Updates the markers for
 * a given project when it is built.
 */
public class UpdateAJMarkers {


    private final AJProjectModelFacade model;
	private final IProject project;
	private final IFile[] sourceFiles;
    private int fileCount;
    private int markerCount;

	/**
	 * To update markers for the entire project
	 *
     * @param project the poject that needs updating
	 */
    public UpdateAJMarkers(IProject project) {
        this.model = AJProjectModelFactory.getInstance().getModelForProject(project);
        this.project = project;
        this.sourceFiles = null;
        this.fileCount = 0;
        this.markerCount = 0;
    }
    /**
     * to update markers for the given files only.
     *
     * @param project the poject that needs updating
     *
     * @param sourceFiles List of Strings of absolute paths of files that
     * need updating
     *
     * @deprecated Use {@link UpdateAJMarkers#UpdateAJMarkers(IProject, IFile[])} instead
     */
    public UpdateAJMarkers(IProject project, File[] sourceFiles) {
        this.model = AJProjectModelFactory.getInstance().getModelForProject(project);
        this.project = project;
        this.sourceFiles = DeleteAndUpdateAJMarkersJob.javaFileToIFile(sourceFiles, project);
    }

    /**
     * to update markers for the given files only.
     *
     * @param project the poject that needs updating
     *
     * @param sourceFiles List of Strings of absolute paths of files that
     * need updating
     *
     */
    public UpdateAJMarkers(IProject project, IFile[] sourceFiles) {
        this.model = AJProjectModelFactory.getInstance().getModelForProject(project);
        this.project = project;
        this.sourceFiles = sourceFiles;
    }

	protected IStatus run(IProgressMonitor monitor) {
        AJLog.logStart("Create markers: " + project.getName());
        if (sourceFiles != null) {
            addMarkersForFiles(monitor);
        } else {
            addMarkersForProject(monitor);
        }
        AJLog.logEnd(AJLog.BUILDER, "Create markers: " + project.getName(), "Finished creating markers for " + project.getName());
        AJLog.log(AJLog.BUILDER, "Created " + markerCount + " markers in " + fileCount + " files");
        return Status.OK_STATUS;
    }


    /**
	 * creates new markers for an entire project
	 */
	private void addMarkersForProject(IProgressMonitor monitor) {
	    if (! model.hasModel()) {
	        return;
	    }
	    try {
            IJavaProject jProject = JavaCore.create(project);
            IPackageFragmentRoot[] fragRoots = jProject.getPackageFragmentRoots();
            SubProgressMonitor subMonitor = new SubProgressMonitor(monitor, fragRoots.length);
            subMonitor.beginTask("Add markers for " + project.getName(), fragRoots.length);
        for (IPackageFragmentRoot fragRoot : fragRoots) {
          if (fragRoot.getKind() == IPackageFragmentRoot.K_SOURCE) {
            IJavaElement[] frags = fragRoot.getChildren();
            for (IJavaElement frag : frags) {
              Set<String> completedCUNames = new HashSet<>(frags.length, 1.0f);
              IJavaElement[] cus = ((IPackageFragment) frag).getChildren();
              for (IJavaElement iJavaElement : cus) {
                // ignore any class files in the source folder (Bug 258698)
                if (iJavaElement.getElementType() == IJavaElement.COMPILATION_UNIT) {
                  // ignore duplicate compilation units
                  IResource resource = iJavaElement.getResource();
                  if (!completedCUNames.contains(resource.getName())) {
                    subMonitor.subTask("Add markers for " + iJavaElement.getElementName());
                    addMarkersForFile((ICompilationUnit) iJavaElement, iJavaElement.getResource());
                    completedCUNames.add(resource.getName());
                    fileCount++;
                  }
                  if (subMonitor.isCanceled()) {
                    throw new OperationCanceledException();
                  }
                }
              }
            }
            subMonitor.worked(1);
          }
        }
        } catch (JavaModelException ignored) {
        }

	}


	private void addMarkersForFiles(IProgressMonitor monitor) {
	    SubProgressMonitor subMonitor = new SubProgressMonitor(monitor, sourceFiles.length);
    for (IFile sourceFile : sourceFiles) {
      IJavaElement unit = JavaCore.create(sourceFile);
      if (unit != null && unit.exists() && unit instanceof ICompilationUnit) {
        subMonitor.subTask("Add markers for " + unit.getElementName());
        addMarkersForFile((ICompilationUnit) unit, sourceFile);
        fileCount++;
      }
      if (subMonitor.isCanceled()) {
        throw new OperationCanceledException();
      }
      subMonitor.worked(1);
    }
    }



    private void addMarkersForFile(ICompilationUnit cu, IResource resource) {
	    Map<Integer,List<IRelationship>> annotationMap =
	        model.getRelationshipsForFile(cu);
	    for (Entry<Integer,List<IRelationship>> entry : annotationMap.entrySet()) {
            createMarker(resource, entry.getKey(), entry.getValue());
            markerCount++;
        }
    }

	private void createMarker(IResource resource, int lineNumber, List<IRelationship> relationships) {
	    String markerType = null;
	    boolean hasRuntime = false;
        for (IRelationship relationship : relationships) {
            hasRuntime |= relationship.hasRuntimeTest();
            String customMarkerType = getCustomMarker(relationship);
            if (customMarkerType != null) {
                // Create a marker of the saved type or don't create one.
                // user has configured a custom marker
                createCustomMarker(resource, customMarkerType, lineNumber, relationship);

            } else {
                // must repeat for each target since
                // each target may be of a different type
                List<String> targets = relationship.getTargets();
                for (String target : targets) {
                    String markerTypeForRelationship =
                        getMarkerTypeForRelationship(relationship, target);
                    if (markerTypeForRelationship != null) {
                        if (markerType == null) {
                            markerType = markerTypeForRelationship;
                        } else if (!markerType.equals(markerTypeForRelationship)) {
                            markerType = getCombinedMarkerType(markerType,
                                    markerTypeForRelationship, hasRuntime);
                        }
                    }
                }
            }
        } // end for

        // Create the marker
        if (markerType != null) {
            try {
                IMarker marker = resource.createMarker(markerType);
                marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
                String label;
                int numTargets = getNumTargets(relationships);
                if (numTargets == 1) {
                    label = getMarkerLabel(relationships);
                } else {
                    label = getMultipleMarkersLabel(numTargets);
                }
                marker.setAttribute(IMarker.MESSAGE, label);
                marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
            } catch (CoreException ignored) {
            }
        }

	}

    private int getNumTargets(List<IRelationship> relationships) {
        int numTargets = 0;
        for (IRelationship rel : relationships) {
            for (String target : rel.getTargets()) {
                if (!rel.getName().equals(AsmRelationshipProvider.MATCHES_DECLARE)) {
                    numTargets++;
                }
            }
        }
        return numTargets;
    }

	private void createCustomMarker(IResource resource, String customMarkerType,
	        int lineNumber, IRelationship relationship) {
        if (! customMarkerType.equals(AJMarkersDialog.NO_MARKERS)) {
            try {
                IMarker marker = resource
                        .createMarker(IAJModelMarker.CUSTOM_MARKER);
                marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
                String label;
                label = getMarkerLabel(relationship);
                marker.setAttribute(IMarker.MESSAGE, label);
                marker.setAttribute(IMarker.PRIORITY,
                        IMarker.PRIORITY_HIGH);
                marker.setAttribute(
                        CustomMarkerImageProvider.IMAGE_LOCATION_ATTRIBUTE,
                        customMarkerType);
            } catch (CoreException ignored) {
            }
        }
    }

    /**
     * Get the marker type that should be used for the given relationship
     *
     * @param relationship
     * @param target
     * @return
     */
    private String getMarkerTypeForRelationship(IRelationship relationship, String target) {
        String name = relationship.getName();
        boolean runtimeTest = relationship.hasRuntimeTest();
        String markerType;
      switch (name) {
        case AsmRelationshipProvider.ADVISED_BY: {
          IProgramElement advice = model.getProgramElement(target);
          AdviceKind ak;
          if (advice.getExtraInfo() != null) {
            ak = AdviceKind.stringToKind(advice.getExtraInfo().getExtraAdviceInformation());
          }
          else {
            ak = null;
          }
          if (runtimeTest) {
            if (ak == null) {
              markerType = IAJModelMarker.DYNAMIC_ADVICE_MARKER;
            }
            else if (ak == AdviceKind.Before) {
              markerType = IAJModelMarker.DYNAMIC_BEFORE_ADVICE_MARKER;
            }
            else if (ak == AdviceKind.After ||
                     ak == AdviceKind.AfterReturning ||
                     ak == AdviceKind.AfterThrowing)
            {
              markerType = IAJModelMarker.DYNAMIC_AFTER_ADVICE_MARKER;
            }
            else if (ak == AdviceKind.Around) {
              markerType = IAJModelMarker.DYNAMIC_AROUND_ADVICE_MARKER;
            }
            else {
              markerType = IAJModelMarker.DYNAMIC_ADVICE_MARKER;
            }
          }
          else { // no runtime test
            if (ak == null) {
              markerType = IAJModelMarker.ADVICE_MARKER;
            }
            else if (ak == AdviceKind.Before) {
              markerType = IAJModelMarker.BEFORE_ADVICE_MARKER;
            }
            else if (ak == AdviceKind.After ||
                     ak == AdviceKind.AfterReturning ||
                     ak == AdviceKind.AfterThrowing)
            {
              markerType = IAJModelMarker.AFTER_ADVICE_MARKER;
            }
            else if (ak == AdviceKind.Around) {
              markerType = IAJModelMarker.AROUND_ADVICE_MARKER;
            }
            else {
              markerType = IAJModelMarker.ADVICE_MARKER;
            }
          }
          break;
        }
        case AsmRelationshipProvider.ADVISES: {
          IProgramElement advice = model.getProgramElement(relationship.getSourceHandle());
          AdviceKind ak;
          if (advice.getExtraInfo() != null) {
            ak = AdviceKind.stringToKind(advice.getExtraInfo().getExtraAdviceInformation());
          }
          else {
            ak = null;
            // hmmm...sometmes ExtradviceInformtion is null.
            // try to get the advice kind by the name
            if (advice.getName().startsWith("before")) {
              ak = AdviceKind.Before;
            }
            else if (advice.getName().startsWith("after")) {
              ak = AdviceKind.After;
            }
            else if (advice.getName().startsWith("around")) {
              ak = AdviceKind.Around;
            }
          }
          if (runtimeTest) {
            if (ak == null) {
              markerType = IAJModelMarker.SOURCE_DYNAMIC_ADVICE_MARKER;
            }
            else if (ak == AdviceKind.Before) {
              markerType = IAJModelMarker.SOURCE_DYNAMIC_BEFORE_ADVICE_MARKER;
            }
            else if (ak == AdviceKind.After ||
                     ak == AdviceKind.AfterReturning ||
                     ak == AdviceKind.AfterThrowing)
            {
              markerType = IAJModelMarker.SOURCE_DYNAMIC_AFTER_ADVICE_MARKER;
            }
            else if (ak == AdviceKind.Around) {
              markerType = IAJModelMarker.SOURCE_DYNAMIC_AROUND_ADVICE_MARKER;
            }
            else {
              markerType = IAJModelMarker.SOURCE_DYNAMIC_ADVICE_MARKER;
            }

          }
          else { // no runtime test
            if (ak == null) {
              markerType = IAJModelMarker.SOURCE_ADVICE_MARKER;
            }
            else if (ak == AdviceKind.Before) {
              markerType = IAJModelMarker.SOURCE_BEFORE_ADVICE_MARKER;
            }
            else if (ak == AdviceKind.After ||
                     ak == AdviceKind.AfterReturning ||
                     ak == AdviceKind.AfterThrowing)
            {
              markerType = IAJModelMarker.SOURCE_AFTER_ADVICE_MARKER;
            }
            else if (ak == AdviceKind.Around) {
              markerType = IAJModelMarker.SOURCE_AROUND_ADVICE_MARKER;
            }
            else {
              markerType = IAJModelMarker.SOURCE_ADVICE_MARKER;
            }
          }
          break;
        }
        case AsmRelationshipProvider.ANNOTATED_BY:
        case AsmRelationshipProvider.SOFTENED_BY:
        case AsmRelationshipProvider.INTER_TYPE_DECLARED_BY:
          // note that we ignore MATCHES_DECLARE because that is taken care of
          // by error and warning markers
          markerType = IAJModelMarker.ITD_MARKER;
          break;
        case AsmRelationshipProvider.ANNOTATES:
        case AsmRelationshipProvider.INTER_TYPE_DECLARES:
        case AsmRelationshipProvider.SOFTENS:
        case AsmRelationshipProvider.MATCHED_BY:
          markerType = IAJModelMarker.SOURCE_ITD_MARKER;

          break;
        default:
          markerType = null;
          break;
      }
        return markerType;
    }


	private String getMultipleMarkersLabel(int number) {
		return number + " " + UIMessages.AspectJMarkersAtLine; //$NON-NLS-1$
	}

    private String getMarkerLabel(List<IRelationship> relationships) {
        // find first non-matches declare relationship
        for (IRelationship rel : relationships) {
            if (!rel.getName().equals(AsmRelationshipProvider.MATCHES_DECLARE)) {
                return getMarkerLabel(rel);
            }
        }
        return "<none>";
    }

    /**
     * Get a label for the given relationship
     *
     * @param relationship
     * @return
     */
    private String getMarkerLabel(IRelationship relationship) {
        IProgramElement target = model.getProgramElement(
          relationship.getTargets().get(0));
        return relationship.getName()
                + " " //$NON-NLS-1$
                + (target != null ? target.toLinkLabelString(false) : "null")
                + (relationship.hasRuntimeTest() ? " " + //$NON-NLS-1$
                        UIMessages.AspectJEditor_runtimetest
                        : ""); //$NON-NLS-1$
    }

	/**
	 * check if this relationship comes from an aspect that has a custom marker
	 * @see AJMarkersDialog
	 */
	private String getCustomMarker(IRelationship relationship) {
	    // get the element in the aspect, it is source or target depending
	    // on the kind of relationship
        List<IJavaElement> aspectEntities = new ArrayList<>();
        if (relationship.isAffects()) {
            aspectEntities.add(model.programElementToJavaElement(relationship.getSourceHandle()));
        } else {
            // all targets are from the same
            for (String target : relationship.getTargets()) {
                aspectEntities.add(model.programElementToJavaElement(target));
            }
        }

        for (IJavaElement elt : aspectEntities) {
            if (elt != null) {  // will be null if the referent is not found.  Should only be in error cases
                IType typeElement = (IType) elt.getAncestor(IJavaElement.TYPE);
                if (typeElement != null) {
                    String customImage = AspectJPreferences.getSavedIcon(typeElement.getJavaProject()
                            .getProject(), AJMarkersDialog
                            .getFullyQualifiedAspectName(typeElement));
                    if (customImage != null) {
                        return customImage;
                    }
                }
            }
        }
        return null;
    }


   /**
	 * Two or more markers on the same line - get the most approriate marker
	 * type to display
	 *
	 * @param firstMarkerType
	 * @param secondMarkerType
	 * @param runtimeTest
	 * @return
	 */
	private String getCombinedMarkerType(String firstMarkerType,
			String secondMarkerType, boolean runtimeTest) {

	    if (firstMarkerType.contains("source") && secondMarkerType.contains("source")) { //$NON-NLS-1$ //$NON-NLS-2$
	        // around advice trumps before and after
	        if (firstMarkerType.contains("around") || secondMarkerType.contains("around")) { //$NON-NLS-1$ //$NON-NLS-2$
	            return runtimeTest ? IAJModelMarker.SOURCE_DYNAMIC_AROUND_ADVICE_MARKER
                        : IAJModelMarker.SOURCE_AROUND_ADVICE_MARKER;
	        } else {
	            return runtimeTest ? IAJModelMarker.SOURCE_DYNAMIC_ADVICE_MARKER
	                    : IAJModelMarker.SOURCE_ADVICE_MARKER;
	        }
		} else if (firstMarkerType.contains("source") || secondMarkerType.contains("source")) { //$NON-NLS-1$ //$NON-NLS-2$
            // being source and target trumps around, before, and after
			return runtimeTest ? IAJModelMarker.DYNAMIC_SOURCE_AND_TARGET_MARKER
					: IAJModelMarker.SOURCE_AND_TARGET_MARKER;
		} else {
            // around advice trumps before and after
            if (firstMarkerType.contains("around") || secondMarkerType.contains("around")) { //$NON-NLS-1$ //$NON-NLS-2$
                return runtimeTest ? IAJModelMarker.DYNAMIC_AROUND_ADVICE_MARKER
                        : IAJModelMarker.AROUND_ADVICE_MARKER;
            } else {
                return runtimeTest ? IAJModelMarker.DYNAMIC_ADVICE_MARKER
                        : IAJModelMarker.ADVICE_MARKER;
            }
		}
	}
}
