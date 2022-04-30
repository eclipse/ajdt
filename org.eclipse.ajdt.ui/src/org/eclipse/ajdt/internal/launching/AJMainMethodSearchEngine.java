/**********************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Sian January - initial version
 * ...
 **********************************************************************/

package org.eclipse.ajdt.internal.launching;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.ajdt.core.AspectJCore;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.BuildConfig;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.internal.debug.ui.launcher.LauncherMessages;
import org.eclipse.jdt.internal.debug.ui.launcher.MainMethodSearchEngine;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;

/**
 * Search Engine to search for main methods and include Aspects in that search
 */
public class AJMainMethodSearchEngine extends MainMethodSearchEngine {

	/**
	 * Searches for all main methods in the given scope. Also searches
	 * for Aspects that have main methods.
	 */
	public IType[] searchMainMethodsIncludingAspects(IProgressMonitor pm,
			IJavaSearchScope scope, boolean includeSubtypes)
			throws JavaModelException {

		pm.beginTask(LauncherMessages.MainMethodSearchEngine_1, 100);
		IProgressMonitor javaSearchMonitor = new SubProgressMonitor(pm, 100);
		IType[] mainTypes = super.searchMainMethods(javaSearchMonitor, scope, includeSubtypes);
		IProject[] projects = AspectJPlugin.getWorkspace().getRoot()
				.getProjects();

		Set<IType> mainSet = new HashSet<>();
    Collections.addAll(mainSet, mainTypes);

		// Bug 261745 --- must search for aspect types in Java files.
		// these are not found through JDT Weaving
		IProgressMonitor ajSearchMonitor = new SubProgressMonitor(pm, 100);
		ajSearchMonitor.beginTask(LauncherMessages.MainMethodSearchEngine_1, 100);
		double ticksPerProject = Math.floor(100F / (float) projects.length);
		if (ticksPerProject < 1) {
			ticksPerProject = 1;
		}
		IPath[] paths = scope.enclosingProjectsAndJars();
		Set<IPath> pathsSet = new HashSet<>(paths.length * 2);
    Collections.addAll(pathsSet, paths);

    for (IProject project : projects) {
      try {
        if (project.hasNature("org.eclipse.ajdt.ui.ajnature")) { //$NON-NLS-1$
          IJavaProject jp = JavaCore.create(project);
          if (jp != null) {
            if (pathsSet.contains(jp.getResource().getFullPath())) {
              Set<IFile> includedFiles = BuildConfig.getIncludedSourceFiles(project);
              Set<IType> mains = getAllAspectsWithMain(scope, includedFiles);
              mainSet.addAll(mains);
            }
          }
        }
      }
      catch (Exception ignored) {
      }
      ajSearchMonitor.internalWorked(ticksPerProject);
      ajSearchMonitor.done();
    }
		pm.done();
		return mainSet.toArray(new IType[0]);
	}


	/**
	 * Searches for all main methods in the given scope. Also searches
     * for Aspects that have main methods.
	 */
	public IType[] searchMainMethodsIncludingAspects(IRunnableContext context,
			final IJavaSearchScope scope,
			final boolean includeSubtypes) throws InvocationTargetException,
			InterruptedException {

		final IType[][] res = new IType[1][];

		IRunnableWithProgress runnable = pm -> {
      try {
        res[0] = searchMainMethodsIncludingAspects(pm, scope,
             includeSubtypes);
      } catch (JavaModelException e) {
        throw new InvocationTargetException(e);
      }
    };
		context.run(true, true, runnable);

		return res[0];
	}

	// only care about aspects in java files
	// and even here, we cannot get all main methods
	private Set<IType> getAllAspectsWithMain(IJavaSearchScope scope, Collection<IFile> includedFiles) throws JavaModelException {
        Set<IType> mainTypes = new HashSet<>();
        for (IFile file : includedFiles) {
            if (file.getFileExtension().equals("java")) {
                ICompilationUnit unit = (ICompilationUnit) AspectJCore.create(file);
                if (unit != null && unit.exists() && scope.encloses(unit)) {
                    IType[] types = unit.getAllTypes();
                  for (IType type : types) {
                    IMethod[] methods = type.getMethods();
                    for (IMethod method : methods) {
                      if (method.isMainMethod()) {
                        mainTypes.add(type);
                        break;
                      }
                    }
                  }
                }
            }
        }
        return mainTypes;
    }


    /**
	 * Subsidiary method for 'searchMainMethods...' to search an array of
	 * IJavaElements for runnable aspects.
	 *
	 * @param scope -
	 *            Java search scope
	 * @param children -
	 *            elements to search
	 * @throws JavaModelException
	 */
//	private List searchJavaElements(IJavaSearchScope scope,
//			IJavaElement[] children) throws JavaModelException {
//		List aspectsFound = new ArrayList();
//		for (int i = 0; i < children.length; i++) {
//			if (children[i] instanceof IPackageFragment) {
//				aspectsFound.addAll(searchPackage(scope,
//						(IPackageFragment) children[i]));
//			} else if (children[i] instanceof IPackageFragmentRoot) {
//				IJavaElement[] grandchildren = ((IPackageFragmentRoot) children[i])
//						.getChildren();
//				aspectsFound.addAll(searchJavaElements(scope, grandchildren));
//			}
//		}
//		return aspectsFound;
//	}
//
//	/**
//	 * Search a JDT IPackageFragment for runnable aspects if included in the
//	 * scope.
//	 *
//	 * @param scope -
//	 *            search scope
//	 * @param packageFragment -
//	 *            the IPackageFragment
//	 * @throws JavaModelException
//	 */
//	private List searchPackage(IJavaSearchScope scope,
//			IPackageFragment packageFragment) throws JavaModelException {
//		List aspectsFound = new ArrayList();
//		if (scope.encloses(packageFragment)) {
//			Set aspects = getAllAspects(packageFragment);
//			aspectsFound.addAll(aspects);
//		} else {
//			aspectsFound.addAll(searchUnitsInPackage(scope, packageFragment));
//		}
//		return aspectsFound;
//	}
//
//	/**
//	 * Search the individual compilation units of an IPackageFragment for
//	 * runnable aspects if they are included in the search scope.
//	 *
//	 * @param scope -
//	 *            the search scope
//	 * @param packageFragment -
//	 *            the IPackageFragment
//	 * @throws JavaModelException
//	 */
//	private List searchUnitsInPackage(IJavaSearchScope scope,
//			IPackageFragment packageFragment) throws JavaModelException {
//		List units = new ArrayList();
//		Set allAspects = getAllAspects(packageFragment);
//		for (Iterator iter = allAspects.iterator(); iter.hasNext();) {
//			IType type = (IType) iter.next();
//			if(scope.encloses(type)) {
//				units.add(type);
//			}
//		}
//		return units;
//	}
//
//	/**
//	 * Iterates through all the packages in a project and returns a Set
//	 * containing all the Aspects in a project that are currently active.
//	 *
//	 * @param JP
//	 *            the project
//	 * @return Set of AJCompilationUnits
//	 */
//	private Set getAllAspects(IJavaProject jp) {
////		try {
////		    IPackageFragment[] frags = jp.getPackageFragments();
////		    frags[0].get
//		    return new HashSet();
////			return new HashSet(getActiveMainTypesFromAJCompilationUnits());
////		} catch (CoreException e) {
////			return new HashSet();
////		}
//	}
//
//	/**
//	 * Returns a Set containing all the Aspects in a package that are currently
//	 * active and contain main types.
//	 *
//	 * @param packageElement
//	 * @return Set of AJCompilationUnits
//	 */
//	private Set getAllAspects(IPackageFragment packageElement) {
//		List aspects = new ArrayList();
//		try {
//			aspects = AJCompilationUnitManager.INSTANCE.getAJCompilationUnitsForPackage(packageElement);
//		} catch (Exception e) {
//		}
//
//		return new HashSet(getActiveMainTypesFromAJCompilationUnits(aspects));
//
//	}
//
//	/**
//	 * Get a List of the active (included in current build configuration) main types (IType)
//	 * from the List of AJCompilationUnits passed in
//	 * @param aspects
//	 * @return
//	 */
//	private List getActiveMainTypesFromAJCompilationUnits(List aspects) {
//		List mainTypes = new ArrayList();
//		try {
//			for (Iterator iter = aspects.iterator(); iter.hasNext();) {
//				AJCompilationUnit element = (AJCompilationUnit) iter.next();
//				if (includedFiles.contains(element.getResource())) {
//					IType[] types = element.getAllTypes();
//					for (int i = 0; i < types.length; i++) {
//						IType type = types[i];
//						IMethod[] methods = type.getMethods();
//						for (int j = 0; j < methods.length; j++) {
//							if(methods[j].isMainMethod()) {
//								mainTypes.add(type);
//								break;
//							}
//						}
//					}
//				}
//			}
//		} catch (Exception e) {
//		}
//		return mainTypes;
//	}

}
