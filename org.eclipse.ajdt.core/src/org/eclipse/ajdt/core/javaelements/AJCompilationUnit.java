/*******************************************************************************
 * Copyright (c) 2004, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Luzius Meisser - initial implementation
 *     Sian January - added eager parsing support
 *     Andrew Eisenberg - changes for AJDT 2.0
 *******************************************************************************/
package org.eclipse.ajdt.core.javaelements;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aspectj.asm.IProgramElement;
import org.aspectj.lang.annotation.Aspect;
import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.AJMementoTokenizer;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.codeconversion.ConversionOptions;
import org.eclipse.ajdt.core.codeconversion.ITDAwareNameEnvironment;
import org.eclipse.ajdt.core.codeconversion.JavaCompatibleBuffer;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.parserbridge.AJCompilationUnitStructureRequestor;
import org.eclipse.ajdt.core.parserbridge.AJSourceElementParser;
import org.eclipse.ajdt.core.reconcile.AJReconcileWorkingCopyOperation;
import org.eclipse.ajdt.internal.core.AJWorkingCopyOwner;
import org.eclipse.ajdt.internal.core.contentassist.ProposalRequestorFilter;
import org.eclipse.ajdt.internal.core.contentassist.ProposalRequestorWrapper;
import org.eclipse.ajdt.internal.core.parserbridge.AJCompilationUnitDeclarationWrapper;
import org.eclipse.ajdt.internal.core.ras.NoFFDC;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.PerformanceStats;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.internal.codeassist.CompletionEngine;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.core.BecomeWorkingCopyOperation;
import org.eclipse.jdt.internal.core.BufferManager;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.JavaModelStatus;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.NameLookup;
import org.eclipse.jdt.internal.core.Openable;
import org.eclipse.jdt.internal.core.OpenableElementInfo;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jdt.internal.core.ReconcileWorkingCopyOperation;
import org.eclipse.jdt.internal.core.SearchableEnvironment;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.internal.core.util.MementoTokenizer;


/**
 * An ICompilationUnit for .aj files.
 *
 * In order to obtain better interoperability with jdt, AJCompilationUnits pretend
 * to have java syntax compatible contents. To get the real contents,
 * requestOriginalContentMode()
 * must be called before getting the Buffer. Please make sure to call
 * discardOriginalContentMode()
 * afterwards to get back into non-original mode.
 *
 * @author Luzius Meisser
 */
public class AJCompilationUnit extends CompilationUnit implements NoFFDC{

	int originalContentMode = 0;
	private IFile ajFile;
	protected JavaCompatibleBuffer javaCompBuffer;


	private final Object contentModeLock = new Object();

	public boolean isInOriginalContentMode() throws JavaModelException {
	    synchronized (contentModeLock) {
            Object info = getElementInfo();
            if (info instanceof AJCompilationUnitInfo) {
                return ((AJCompilationUnitInfo) info).originalContentMode > 0;
            }
        }
	    return false;
	}

	/**
	 * ensure that the next time the buffer is asked for,
	 * the actual AJ contents are returned (not the
	 * converted contents)
	 */
	public void requestOriginalContentMode() throws JavaModelException {
        synchronized (contentModeLock) {
            Object info = getElementInfo();
            if (info instanceof AJCompilationUnitInfo) {
                ((AJCompilationUnitInfo) info).originalContentMode++;
            }
        }
	}

	/**
	 * discard this request for original contents
	 */
	public void discardOriginalContentMode() throws JavaModelException {
        synchronized (contentModeLock) {
            Object info = getElementInfo();
            if (info instanceof AJCompilationUnitInfo) {
                ((AJCompilationUnitInfo) info).originalContentMode--;
            }
        }
	}

	public AJCompilationUnit(IFile ajFile) {
		super(CompilationUnitTools.getParentPackage(ajFile), ajFile.getName(), AJWorkingCopyOwner.INSTANCE);
		this.ajFile = ajFile;
	}

	/**
	 * @param fragment
	 * @param elementName
	 * @param workingCopyOwner
	 */
	public AJCompilationUnit(PackageFragment fragment, String elementName, WorkingCopyOwner workingCopyOwner) {
		super(fragment, elementName, workingCopyOwner);
		if(fragment.getResource() instanceof IProject) {
			IProject p = (IProject)fragment.getResource();
			this.ajFile = (IFile)p.findMember(elementName);
		} else {
			IFolder f = (IFolder)fragment.getResource();
			this.ajFile = (IFile)f.findMember(elementName);
		}
	}

	public char[] getMainTypeName(){
		if (AspectJPlugin.USING_CU_PROVIDER) {
			return super.getMainTypeName();
		}
		String elementName = name;
		//remove the .aj
		elementName = elementName.substring(0, elementName.length() - ".aj".length()); //$NON-NLS-1$
		return elementName.toCharArray();
	}

	/* Eclipse 3.1M3: prior to this we overrode isValidCompilationUnit, but now we need to
	 * override validateCompilationUnit, otherwise the check for valid name will fail on
	 * .aj files
	 */
//	protected IStatus validateCompilationUnit(IResource resource) {
//		IPackageFragmentRoot root = getPackageFragmentRoot();
//		try {
//			if (!(resource.getProject().exists()) || root.getKind() != IPackageFragmentRoot.K_SOURCE)
//				return new JavaModelStatus(IJavaModelStatusConstants.INVALID_ELEMENT_TYPES, root);
//		} catch (JavaModelException e) {
//			return e.getJavaModelStatus();
//		}
//		return JavaModelStatus.OK_STATUS;
//	}

	/* Eclipse 3.2M6: bypass buffer cache to ensure fake buffer is used
	 */
	/**
	 * @see org.eclipse.jdt.internal.compiler.env.ICompilationUnit#getContents()
	 */
	public char[] getContents() {
		try {
			IBuffer buffer = this.getBuffer();
			return buffer == null ? CharOperation.NO_CHAR : buffer.getCharacters();
		} catch (JavaModelException e) {
            AspectJPlugin.getDefault().getLog().log(e.getStatus());
			return CharOperation.NO_CHAR;
		}
	}

	public IResource getResource(){
		if (AspectJPlugin.USING_CU_PROVIDER) {
			return super.getResource();
		}
		return ajFile;
	}

	/*
	 * needs to return real path for organize imports
	 */
	public IPath getPath() {
		if (AspectJPlugin.USING_CU_PROVIDER || ajFile == null) {
			return super.getPath();
		}
		return ajFile.getFullPath();
	}

	public IResource getUnderlyingResource() throws JavaModelException {
		if (AspectJPlugin.USING_CU_PROVIDER) {
			return super.getUnderlyingResource();
		}
		return ajFile;
	}

	protected void generateInfos(Object info, HashMap newElements, IProgressMonitor monitor) throws JavaModelException {
		if (!(info instanceof AJCompilationUnitInfo)){
			info = new AJCompilationUnitInfo();
		}
		// only generate infos if on build path of the project.
		if (getJavaProject().isOnClasspath(this)) {
		    super.generateInfos(info, newElements, monitor);
		}
	}


	/**
	 * return the type as an aspect if it exists
	 */
	public IType getType(String typeName) {
	    IType maybeType = findAspectType(typeName);
	    if (maybeType != null && maybeType.exists()) {
	        return maybeType;
	    }
        return super.getType(typeName);
    }

	/**
	 * return null if doesn't exist or an error
	 * return type otherwise Might be an aspect
	 */
	public IType findAspectType(String typeName) {
	    try {
            IJavaElement[] children = getChildren();
        for (IJavaElement child : children) {
          if (child.getElementType() == TYPE) {
            if (child.getElementName().equals(typeName)) {
              return (IType) child;  // might be an aspect
            }
          }
        }
        } catch (JavaModelException e) {
            AspectJPlugin.getDefault().getLog().log(e.getStatus());
        }
        return null;
	}



	/**
	 * return as aspect if it is an aspect, or class/interface if it is that
	 * performs a deep search
	 * returns null if doesn't exist
	 */
	public IType maybeConvertToAspect(IType maybeAspect) {
	    IJavaElement[] elts = maybeAspect.getCompilationUnit().findElements(maybeAspect);
	    if (elts != null && elts.length > 0 && elts[0] instanceof AspectElement) {
	        return (IType) elts[0];
	    }
	    return maybeAspect;
    }



	/**
	 * builds the structure of this Compilation unit.  We need to use an aspect-aware parser for this (in the org.aspectj.org.eclipse... world, which
	 * makes things a little messy
	 */
	protected boolean buildStructure(OpenableElementInfo info, final IProgressMonitor pm, Map newElements, IResource underlyingResource) throws JavaModelException {
	    AJCompilationUnitInfo unitInfo = (AJCompilationUnitInfo) info;

       if(ajFile == null) {
           return false;
       }

	    // ensure buffer is opened
	    IBuffer buffer = getBufferManager().getBuffer(this);
	    if (buffer == null) {
	        openBuffer(pm, unitInfo); // open buffer independently from the info, since we are building the info
	    }

	    // generate structure and compute syntax problems if needed
	    AJCompilationUnitStructureRequestor requestor = new AJCompilationUnitStructureRequestor(this, unitInfo, newElements);
	    JavaModelManager.PerWorkingCopyInfo perWorkingCopyInfo = getPerWorkingCopyInfo();
	    IJavaProject project = getJavaProject();

		boolean createAST;
        boolean resolveBindings;
        int reconcileFlags;
//        HashMap problems;
        AJCompilationUnitInfo astHolder = (AJCompilationUnitInfo) info;
        createAST = astHolder.getASTLevel() != NO_AST;
        resolveBindings = astHolder.doResolveBindings();
        reconcileFlags = astHolder.getReconcileFlags();
//        problems = astHolder.getProblems();

        boolean computeProblems = perWorkingCopyInfo != null && perWorkingCopyInfo.isActive() && project != null &&
                AspectJPlugin.isAJProject(project.getProject());
        org.aspectj.org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory problemFactory =
            new org.aspectj.org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory();
        Map<String, String> options = project == null ? JavaCore.getOptions() : project.getOptions(true);
        if (!computeProblems) {
            // disable task tags checking to speed up parsing
            options.put(JavaCore.COMPILER_TASK_TAGS, ""); //$NON-NLS-1$
        }

        // ensure parser sees the real contents (not the fake java buffer)
        this.requestOriginalContentMode();

        // use an aspectj aware source parser
        AJSourceElementParser ajdtParser = new AJSourceElementParser(
                requestor,
                problemFactory,
                new org.aspectj.org.eclipse.jdt.internal.compiler.impl.CompilerOptions(options),
                true/*report local declarations*/,
                !createAST /*optimize string literals only if not creating a DOM AST*/);

        ajdtParser.reportOnlyOneSyntaxError = !computeProblems;
        ajdtParser.setMethodsFullRecovery(true);
        ajdtParser.setStatementsRecovery((reconcileFlags & ICompilationUnit.ENABLE_STATEMENTS_RECOVERY) != 0);

        if (!computeProblems && !resolveBindings && !createAST) {
            // disable javadoc parsing if not computing problems, not resolving and not creating ast
            ajdtParser.javadocParser.checkDocComment = false;
        }
        requestor.setParser(ajdtParser);

        // update timestamp (might be IResource.NULL_STAMP if original does not exist)
        if (underlyingResource == null) {
            underlyingResource = getResource();
        }
        // underlying resource is null in the case of a working copy on a class file in a jar
        if (underlyingResource != null)
            unitInfo.setTimestamp(underlyingResource.getModificationStamp());

        // compute other problems if needed
        CompilationUnitDeclaration compilationUnitDeclaration = null;
        final AJCompilationUnit source = ajCloneCachingContents();
        requestor.setSource(source.getContents());
        try {
            if (false) {
                // for now, don't go here
                // the problem is that we can't find problems and build structure at the same time
                // they require difference kinds of parsers.
//            if (computeProblems) {
//                if (problems == null) {
//                    // report problems to the problem requestor
//                    problems = new HashMap();
//                    compilationUnitDeclaration = AJCompilationUnitProblemFinder.processAJ(
//                            source, ajdtParser, this.owner, problems, createAST, reconcileFlags, pm);
//                    try {
//                        perWorkingCopyInfo.beginReporting();
//                        for (Iterator iteraror = problems.values().iterator(); iteraror.hasNext();) {
//                            CategorizedProblem[] categorizedProblems = (CategorizedProblem[]) iteraror.next();
//                            if (categorizedProblems == null) continue;
//                            for (int i = 0, length = categorizedProblems.length; i < length; i++) {
//                                perWorkingCopyInfo.acceptProblem(categorizedProblems[i]);
//                            }
//                        }
//                    } finally {
//                        perWorkingCopyInfo.endReporting();
//                    }
//                } else {
//                    // collect problems
//                    compilationUnitDeclaration = AJCompilationUnitProblemFinder.processAJ(source, ajdtParser, this.owner, problems, createAST, reconcileFlags, pm);
//                }
            } else {
                // since we are doing n aspectj aware parse with the AJ parser
                // need to wrap the results in a JDT CompilationUnitDeclaration
                org.aspectj.org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
            		  ajDeclaration = ajdtParser.parseCompilationUnit(new org.aspectj.org.eclipse.jdt.internal.compiler.env.ICompilationUnit() {
					public char[] getContents() {
						return source.getContents();
					}
					public char[] getMainTypeName() {
						return source.getMainTypeName();
					}
					public char[][] getPackageName() {
						return source.getPackageName();
					}
					public char[] getFileName() {
						return source.getFileName();
					}
					public boolean ignoreOptionalProblems() {
						return false;
					}
				}, true /*full parse to find local elements*/);
            	compilationUnitDeclaration = new AJCompilationUnitDeclarationWrapper(ajDeclaration, source);
            }

            if (createAST) {
                // XXX hmmmm...may not work
                int astLevel = unitInfo.getASTLevel();
                org.eclipse.jdt.core.dom.CompilationUnit cu = AST.convertCompilationUnit(astLevel, compilationUnitDeclaration, options, computeProblems, source, reconcileFlags, pm);
                unitInfo.setAST(cu);
            }
        } finally {
            discardOriginalContentMode();
            if (compilationUnitDeclaration != null) {
                compilationUnitDeclaration.cleanUp();
            }
        }

        return unitInfo.isStructureKnown();
	}

	public boolean isPrimary() {
		return this.owner == AJWorkingCopyOwner.INSTANCE;
	}

	protected Object createElementInfo() {
		return new AJCompilationUnitInfo();
	}


	public org.eclipse.jdt.core.dom.CompilationUnit makeConsistent(int astLevel, boolean resolveBindings, int reconcileFlags, HashMap problems, IProgressMonitor monitor) throws JavaModelException {
		if (isConsistent()) return null;

		// create a new info and make it the current info
		// (this will remove the info and its children just before storing the new infos)
		if (astLevel != NO_AST || problems != null) {
			ASTHolderAJCUInfo info = new ASTHolderAJCUInfo();
			info.astLevel = astLevel;
			info.resolveBindings = resolveBindings;
			info.reconcileFlags = reconcileFlags;
			info.problems = problems;
			openWhenClosed(info,true,  monitor);
			org.eclipse.jdt.core.dom.CompilationUnit result = info.ast;
			info.ast = null;
			return result;
		} else {
			openWhenClosed(createElementInfo(), true, monitor);
			return null;
		}
	}

	/**
	 * @see ICompilationUnit#getWorkingCopy(WorkingCopyOwner, IProblemRequestor, IProgressMonitor)
	 */
	public ICompilationUnit getWorkingCopy(WorkingCopyOwner workingCopyOwner, IProblemRequestor problemRequestor, IProgressMonitor monitor) throws JavaModelException {
		if (!isPrimary()) return this;

		JavaModelManager manager = JavaModelManager.getJavaModelManager();

		CompilationUnit workingCopy = new AJCompilationUnit((PackageFragment)getParent(), getElementName(), workingCopyOwner);
		JavaModelManager.PerWorkingCopyInfo perWorkingCopyInfo =
			manager.getPerWorkingCopyInfo(workingCopy, false/*don't create*/, true/*record usage*/, null/*not used since don't create*/);
		if (perWorkingCopyInfo != null) {
			return perWorkingCopyInfo.getWorkingCopy(); // return existing handle instead of the one created above
		}
		BecomeWorkingCopyOperation op = new BecomeWorkingCopyOperation(workingCopy, problemRequestor);
		op.runOperation(monitor);
		return workingCopy;
	}

	public IBuffer getBuffer() throws JavaModelException {
		return convertBuffer(super.getBuffer());
	}


	public IBuffer convertBuffer(IBuffer buf) {
		try {
            if (isInOriginalContentMode() || (buf == null)) {
            	return buf;
            }
        } catch (JavaModelException ignored) {
        }

		if (javaCompBuffer == null){
			IBuffer myBuffer = BufferManager.createBuffer(this);
			javaCompBuffer = new JavaCompatibleBuffer(buf, myBuffer);
		} else {
			if (buf != javaCompBuffer)
				javaCompBuffer.reinitialize(buf);
		}

		return javaCompBuffer;
	}

	// copied from super, but changed to use an AJReconcileWorkingCopyOperation
	public org.eclipse.jdt.core.dom.CompilationUnit reconcile(
			int astLevel,
			int reconcileFlags,
			WorkingCopyOwner workingCopyOwner,
			IProgressMonitor monitor)
			throws JavaModelException {
		if (!isWorkingCopy()) return null; // Reconciling is not supported on non working copies
		if (workingCopyOwner == null) workingCopyOwner = AJWorkingCopyOwner.INSTANCE;

		PerformanceStats stats = null;
		if(ReconcileWorkingCopyOperation.PERF) {
		    stats = PerformanceStats.getStats(JavaModelManager.RECONCILE_PERF, this);
		    stats.startRun(new String(this.getFileName()));
		}

		AJReconcileWorkingCopyOperation op = new AJReconcileWorkingCopyOperation(this, astLevel, reconcileFlags, workingCopyOwner);
	    try {
	        // Eclipse 3.5.1 and 3.5.2 have different signatures for this method
	        cacheZipFiles(); // cache zip files for performance (see https://bugs.eclipse.org/bugs/show_bug.cgi?id=134172)
	        op.runOperation(monitor);
	    } finally {
	        flushZipFiles();
	    }
	    if(ReconcileWorkingCopyOperation.PERF) {
	        stats.endRun();
	    }
		return op.ast;
	}

    private void flushZipFiles() throws JavaModelException {
        Method flushZipFilesMethod;
        JavaModelManager manager = JavaModelManager.getJavaModelManager();
        try {
            try {
                // Eclipse 3.5.1
                //noinspection JavaReflectionMemberAccess
                flushZipFilesMethod = JavaModelManager.class.getMethod("flushZipFiles");
                flushZipFilesMethod.invoke(manager);
            } catch (NoSuchMethodException e) {
                // Eclipse 3.5.2
                try {
                    flushZipFilesMethod = JavaModelManager.class.getMethod("flushZipFiles", Object.class);
                    flushZipFilesMethod.invoke(manager, this);
                } catch (NoSuchMethodException e1) {
                    throw new JavaModelException(e1, IJavaModelStatusConstants.CORE_EXCEPTION);
                }
            }
        } catch (SecurityException | InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
            throw new JavaModelException(e, IJavaModelStatusConstants.CORE_EXCEPTION);
        }
    }

    private void cacheZipFiles() throws JavaModelException {
        Method cacheZipFilesMethod;
        JavaModelManager manager = JavaModelManager.getJavaModelManager();
        try {
            try {
                // Eclipse 3.5.1
                //noinspection JavaReflectionMemberAccess
                cacheZipFilesMethod = JavaModelManager.class.getMethod("cacheZipFiles");
                cacheZipFilesMethod.invoke(manager);
            } catch (NoSuchMethodException e) {
                // Eclipse 3.5.2
                try {
                    cacheZipFilesMethod = JavaModelManager.class.getMethod("cacheZipFiles", Object.class);
                    cacheZipFilesMethod.invoke(manager, this);
                } catch (NoSuchMethodException e1) {
                    throw new JavaModelException(e1, IJavaModelStatusConstants.CORE_EXCEPTION);
                }
            }
        } catch (SecurityException | InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
            throw new JavaModelException(e, IJavaModelStatusConstants.CORE_EXCEPTION);
        }
    }

	public IJavaElement[] codeSelect(int offset, int length,
			WorkingCopyOwner workingCopyOwner) throws JavaModelException {
    return super.codeSelect(offset, length, workingCopyOwner);
	}

	protected void closeBuffer() {
		if (javaCompBuffer != null){
			javaCompBuffer.close();
			javaCompBuffer = null;
		}
		super.closeBuffer();
	}

	private static final String moveCuUpdateCreator = "org.eclipse.jdt.internal.corext.refactoring.reorg.MoveCuUpdateCreator"; //$NON-NLS-1$
	private static final int lenOfMoveCuUpdateCreator = moveCuUpdateCreator.length();

	public IType[] getAllTypes() throws JavaModelException {
	    if (!AspectJPlugin.USING_CU_PROVIDER) {
	        //tell MoveCuUpdateCreator that we do not contain any Types, otherwise it tries to find
	        //them using java search which will cause an ugly exception
    		String caller = (new RuntimeException()).getStackTrace()[1].getClassName();
    		if ((lenOfMoveCuUpdateCreator == caller.length()) && moveCuUpdateCreator.equals(caller)) {
    			return new IType[0];
    		}
	    }
		return super.getAllTypes();
	}

	/**
	 * Returns all aspect types in this compilation unit in the same order
	 * that {@link #getAllTypes()} works.
	 *
	 * This returns types defined with the <em>aspect</em> keyword and
	 * classes annotated with @{@link Aspect}.  This means that not all
	 * Aspects returned will have a type {@link AspectElement}.  The types
	 * marked with   @{@link Aspect} will be of type {@link SourceType}.
	 */
	public IType[] getAllAspects() throws JavaModelException {
	    IType[] allTypes = getAllTypes();
	    List<IType> aspects = new ArrayList<>(allTypes.length);
	    AJProjectModelFacade model = null;
    for (IType allType : allTypes) {
      if (allType instanceof AspectElement) {
        aspects.add(allType);
      }
      else {
        // bug 270396---annotations are not stored in the model
        // this method always returns an empty array
        // ask the AspectJ model instead
//	            IAnnotation[] annotations = allTypes[i].getAnnotations();
//	            for (int j = 0; j < annotations.length; j++) {
//	                String annName = annotations[j].getElementName();
//	                // no need to check fully qualified name
//	                // just assume that any annotation with a name of Aspect
//	                // is the one we want
//                    if (annName != null &&
//                            (annName.equals("Aspect") || annName.endsWith(".Aspect"))) {
//                        aspects.add(allTypes[i]);
//                        break;
//                    }
//                }
        if (model == null) {
          model = AJProjectModelFactory.getInstance().getModelForJavaElement(this);
        }
        IProgramElement maybeAspect = model.javaElementToProgramElement(allType);
        if (maybeAspect.getKind() == IProgramElement.Kind.ASPECT) {
          aspects.add(allType);
        }
      }
    }
	    return aspects.toArray(new IType[0]);
	}

	/**
	 * Hook for code completion support for AspectJ content.
	 *
     * A description of how code completion works in AJDT can be found in bug 74419.
     *
	 * @see org.eclipse.jdt.internal.core.Openable#codeComplete(org.eclipse.jdt.internal.compiler.env.ICompilationUnit, org.eclipse.jdt.internal.compiler.env.ICompilationUnit, int, org.eclipse.jdt.core.CompletionRequestor, org.eclipse.jdt.core.WorkingCopyOwner)
	 */
	protected void codeComplete(
			org.eclipse.jdt.internal.compiler.env.ICompilationUnit cu,
			org.eclipse.jdt.internal.compiler.env.ICompilationUnit unitToSkip,
			int position, CompletionRequestor requestor,
			WorkingCopyOwner owner,
			ITypeRoot typeRoot,
			/* AJDT 1.7 */
            IProgressMonitor monitor) throws JavaModelException {
	    // Bug 76146
	    // if we are not editing in an AspectJ editor
	    // (i.e., we are editing in a Java editor),
	    // then we do not have access to a proper parser
	    // and we cannot perform code completion requests.
	    if (!isEditingInAspectJEditor()) return;

	    int transformedPos;

		if(javaCompBuffer == null) {
			convertBuffer(super.getBuffer());
		}
		if (javaCompBuffer == null) {
		    // if still null here, then some horrendous syntax error occurred and
		    // we can't do anything, so just exit
		    return;
		}
		ConversionOptions optionsBefore = javaCompBuffer.getConversionOptions();

		//check if inside intertype method declaration
		IntertypeElement itd = itdMethodOrNull(position);
        if (itd != null){
			// we are inside an intertype method declaration
            // perform content assist twice.  once with the context switch (ie- pretend to be in the ITD target type
            // and once in the context of the aspect.
            char[] targetType = itd.getTargetType();
            boolean doExtraITDFiltering = !positionIsAtDottedExpression(itd, position);

            // simulate context switch to target class
			javaCompBuffer.setConversionOptions(ConversionOptions.getCodeCompletionOptionWithContextSwitch(position, targetType));
			transformedPos = javaCompBuffer.translatePositionToFake(position);

			CompletionRequestor wrappedRequestor = new ProposalRequestorWrapper(requestor, this, javaCompBuffer, "");
			/* AJDT 1.7 */
			internalCodeComplete(cu, unitToSkip, transformedPos, wrappedRequestor, owner, this, monitor);

            // now set up for the regular code completion
            javaCompBuffer.setConversionOptions(ConversionOptions.CODE_COMPLETION);

            //set up proposal filter to filter away all the proposals that would be wrong because of context switch
			requestor = new ProposalRequestorFilter(requestor, this, javaCompBuffer, doExtraITDFiltering);
		} else {
		    javaCompBuffer.setConversionOptions(ConversionOptions.CODE_COMPLETION);
		    requestor = new ProposalRequestorWrapper(requestor, this, javaCompBuffer, "");
		}
        transformedPos = javaCompBuffer.translatePositionToFake(position);

		/* AJDT 1.7 */
		internalCodeComplete(cu, unitToSkip, transformedPos, requestor, owner, this, monitor);
		javaCompBuffer.setConversionOptions(optionsBefore);

	}

	// bug 279974: determine if the position is inside a dotted expression
	// and the expression is not 'this'
	// eg- this.foo.b<here> ==> true
	//     fo<here>         ==> false
	//     this.f<here>     ==> false
	protected boolean positionIsAtDottedExpression(IntertypeElement itd, int pos)
	            throws JavaModelException {
	    String source = itd.getSource();
	    int posInSource = pos - itd.getSourceRange().getOffset();
        return positionIsAtDottedExpression(source, posInSource);
    }

	// make static for easier testing
	static protected boolean positionIsAtDottedExpression(String source, int posInSource) {
	    if (posInSource <= 0) {
	        return false;
	    }
	    // iterate backwards over chars
	    // first stage:
	    // '.'               ---> no filtering
	    // any non-word char ---> do filtering
	    // whitespace        ---> go to next stage

	    char[] sourceArr = source.toCharArray();
	    int currPos = posInSource-1;
	    char currChar = sourceArr[currPos];

	    boolean dotFound = false;
	    boolean nonWordFound = false;
	    while (currPos > 0) {
	        if (currChar == '.') {
	            dotFound = true;
	            break;
	        } else if (Character.isWhitespace(currChar)) {
	            break;
	        } else if (Character.isJavaIdentifierPart(currChar)) {
	            // fall through
	        } else {
	            nonWordFound = true;
	            break;
	        }
	        currPos--;
	        currChar = sourceArr[currPos];
	    }

	    if (nonWordFound || currPos < 0) {
	        return false;
	    }

	    // second stage:
	    // follow whitespace backwards until:
	    // '.'           ---> no filtering
	    // anything else ---> do filtering
	    if (!dotFound) {
            currPos--;
            boolean somethingElseFound = false;
	        while (currPos >= 0) {
	            currChar = sourceArr[currPos];
	            if (Character.isWhitespace(currChar)) {
	                // fall through
	            } else if (currChar == '.') {
	                dotFound = true;
	                break;
	            } else {
	                somethingElseFound = true;
	                break;
	            }
	            currPos--;
	        }
	        if (somethingElseFound) {
	            return false;
	        }
	    }


	    // third stage:
	    // check to see if previous word is "this"
	    // if so, then do filtering, it is not considered a dotted expression
	    if (dotFound) {
	        currPos--;
	        while (currPos >= 0) {
                currChar = sourceArr[currPos];
                if (!Character.isWhitespace(currChar)) {
                    break;
                }
                currPos--;
	        }

        //noinspection PointlessArithmeticExpression
        return currPos < 3 ||
                     !(sourceArr[currPos-3] == 't' &&
                       sourceArr[currPos-2] == 'h' &&
                       sourceArr[currPos-1] == 'i' &&
                       sourceArr[currPos-0] == 's');
	    }

	    return false;
	}

    /**
	 * this method is a copy of {@link Openable#codeComplete(org.eclipse.jdt.internal.compiler.env.ICompilationUnit, org.eclipse.jdt.internal.compiler.env.ICompilationUnit, int, CompletionRequestor, WorkingCopyOwner, ITypeRoot)}
	 * The only change is that we need to create an {@link ITDAwareNameEnvironment}, not  standard {@link SearchableEnvironment}.
     *
	 * @param cu
	 * @param unitToSkip
	 * @param position
	 * @param requestor
	 * @param owner
	 * @param typeRoot
	 * @throws JavaModelException
	 */
	private void internalCodeComplete(
            org.eclipse.jdt.internal.compiler.env.ICompilationUnit cu,
            org.eclipse.jdt.internal.compiler.env.ICompilationUnit unitToSkip,
            int position, CompletionRequestor requestor,
            WorkingCopyOwner owner,
            ITypeRoot typeRoot,
			/* AJDT 1.7 */
            IProgressMonitor monitor) throws JavaModelException {

	    if (requestor == null) {
	        throw new IllegalArgumentException("Completion requestor cannot be null"); //$NON-NLS-1$
	    }
	    PerformanceStats performanceStats = CompletionEngine.PERF
	        ? PerformanceStats.getStats(JavaModelManager.COMPLETION_PERF, this)
	        : null;
	    if(performanceStats != null) {
	        performanceStats.startRun(new String(cu.getFileName()) + " at " + position); //$NON-NLS-1$
	    }
	    IBuffer buffer = getBuffer();
	    if (buffer == null) {
	        return;
	    }
	    if (position < -1 || position > buffer.getLength()) {
	        throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.INDEX_OUT_OF_BOUNDS));
	    }
	    JavaProject project = getJavaProject();
		/* AJDT 1.7 */
	    ITDAwareNameEnvironment environment = new ITDAwareNameEnvironment(project, owner, monitor);

	    environment.setUnitToSkip(unitToSkip);

	    // code complete
	    /* AJDT 1.7 */
	    CompletionEngine engine = new CompletionEngine(environment, requestor, project.getOptions(true), project, owner, monitor);
	    engine.complete(cu, position, 0, typeRoot);
	    if(performanceStats != null) {
	        performanceStats.endRun();
	    }
	    if (NameLookup.VERBOSE) {
	        AJLog.log(Thread.currentThread() + " TIME SPENT in NameLoopkup#seekTypesInSourcePackage: " + environment.nameLookup.timeSpentInSeekTypesInSourcePackage + "ms");  //$NON-NLS-1$ //$NON-NLS-2$
	        AJLog.log(Thread.currentThread() + " TIME SPENT in NameLoopkup#seekTypesInBinaryPackage: " + environment.nameLookup.timeSpentInSeekTypesInBinaryPackage + "ms");  //$NON-NLS-1$ //$NON-NLS-2$
	    }

	}


    /**
     * As per Bug 76146
     * check to see if editing in Java Editor or AspectJ editor
     */
    private boolean isEditingInAspectJEditor() {
        // This is a bit kludgy.
        // when perWorkingCopyInfo is null
        // then we are editing in Java editor
        return getPerWorkingCopyInfo() != null;
    }

	/**
	 * @param pos
	 * @return null if outside intertype method declaration or the name of the target type otherwise
	 * @throws JavaModelException
	 */
	public IntertypeElement itdMethodOrNull(int pos) throws JavaModelException{
	    IJavaElement elt = this.getElementAt(pos);
	    if (elt instanceof IntertypeElement) {
            IntertypeElement itd = (IntertypeElement) elt;
            if (itd.getAJKind() == IProgramElement.Kind.INTER_TYPE_METHOD ||
                itd.getAJKind() == IProgramElement.Kind.INTER_TYPE_CONSTRUCTOR) {

                return itd;
            }
        }
	    return null;
	}


	// hack: need to use protected constructor in SourceType
	private JavaElement getType(JavaElement type, String typeName) {
	    try {
    		try {
    			Constructor<SourceType> cons = SourceType.class.getDeclaredConstructor(JavaElement.class,String.class);
    			cons.setAccessible(true);
          return cons.newInstance(type,typeName);
    		} catch (SecurityException | InvocationTargetException | IllegalAccessException | InstantiationException |
                 IllegalArgumentException | NoSuchMethodException e) {
    		    throw new JavaModelException(e, IJavaModelStatusConstants.CORE_EXCEPTION);
    		}
      } catch (JavaModelException jme) {
            AspectJPlugin.getDefault().getLog().log(jme.getStatus());
	    }
		return null;
	}


	public IJavaElement getHandleFromMemento(MementoTokenizer memento, WorkingCopyOwner owner) {
	    // if not an AJMementoTokenizer, the tokenizer may have read too far
	    // create an AJMementoTokenizer and ensure to backtrack to the end of the compilation unit name
	    if (! (memento instanceof AJMementoTokenizer)) {
	        memento = new AJMementoTokenizer(memento, name);
	    }
	    return super.getHandleFromMemento(memento, owner);
	}


	/*
	 * @see JavaElement
	 */
	public IJavaElement getHandleFromMemento(String token, MementoTokenizer memento, WorkingCopyOwner workingCopyOwner) {
		JavaElement type = this;
		if (! (memento instanceof AJMementoTokenizer)) {
		    memento = new AJMementoTokenizer(memento, name);
		}
		if ((token.charAt(0) == JavaElement.JEM_IMPORTDECLARATION) ||
		        (token.charAt(0) == JavaElement.JEM_PACKAGEDECLARATION)) {
		    return super.getHandleFromMemento(token, memento, workingCopyOwner);
		}

        // need to handle types ourselves, because they may contain inner aspects
		// (or inner classes containing inner aspects etc)
		while ((token.charAt(0) == AspectElement.JEM_ASPECT_TYPE) ||
				(token.charAt(0) == JavaElement.JEM_TYPE) ||
				(token.charAt(0) == JavaElement.JEM_ANNOTATION)) {
		    // note that we are also testing for JEM_ANNOTATION here.
		    // this is because when the type handle identifiers were changed prior to 2.1.1,
		    // JEM_ASPECT_TYPE and JEM_ANNOTATION were the same.
		    // Some people have had problems in that the old handles were cached.
		    // this can probably be safely removed post 2.1.2.
			if (!memento.hasMoreTokens()) return type;
			String typeName = memento.nextToken();
			if (token.charAt(0) == AspectElement.JEM_ASPECT_TYPE ||
			        token.charAt(0) == JavaElement.JEM_ANNOTATION) {
				type = new AspectElement(type, typeName);
			} else if (token.charAt(0) == JavaElement.JEM_TYPE) {
				type = getType(type,typeName);
				if (type == null) type = (JavaElement)getType(typeName);
			}
			if (!memento.hasMoreTokens()) return type;
			token = memento.nextToken();
		}
		// handle pointcuts in a class (bug 124992)
		if (!(type instanceof AspectElement)
				&& (token.charAt(0) == AspectElement.JEM_POINTCUT)) {
			String name = memento.nextToken();
			ArrayList<String> params = new ArrayList<>();
			nextParam: while (memento.hasMoreTokens()) {
				token = memento.nextToken();
				switch (token.charAt(0)) {
					case JEM_TYPE:
					case JEM_TYPE_PARAMETER:
						break nextParam;
					case AspectElement.JEM_POINTCUT:
						if (!memento.hasMoreTokens()) return this;
						String param = memento.nextToken();
						StringBuilder buffer = new StringBuilder();
						while (param.length() == 1 && Signature.C_ARRAY == param.charAt(0)) { // backward compatible with 3.0 mementos
							buffer.append(Signature.C_ARRAY);
							if (!memento.hasMoreTokens()) return this;
							param = memento.nextToken();
						}
						params.add(buffer + param);
						break;
					default:
						break nextParam;
				}
			}
			String[] parameters = new String[params.size()];
			params.toArray(parameters);
			JavaElement pointcut = new PointcutElement(type, name, parameters);
			return pointcut.getHandleFromMemento(memento, workingCopyOwner);
		}

		return type.getHandleFromMemento(token, memento, workingCopyOwner);
	}

	/**
	 * @see JavaElement#getHandleMementoDelimiter()
	 */
	protected char getHandleMementoDelimiter() {
		if (AspectJPlugin.USING_CU_PROVIDER) {
			return super.getHandleMementoDelimiter();
		}
		return AspectElement.JEM_ASPECT_CU;
	}

	public String getHandleIdentifier() {
		if (AspectJPlugin.USING_CU_PROVIDER) {
			return super.getHandleIdentifier();
		}

		// this horrid code only exists so that when we are not using the weaving service,
		// we don't get exceptions on refactoring
		String callerName = (new RuntimeException()).getStackTrace()[1].getClassName();
		final String deletionClass = "org.eclipse.jdt.internal.corext.refactoring.changes.DeleteSourceManipulationChange"; //$NON-NLS-1$
		// are we being called in the context of a delete operation?
		if (callerName.equals(deletionClass)) {
			AJCompilationUnitManager.INSTANCE.removeFileFromModel((IFile) getResource());
			// need to return a handle identifier that JDT can use (bug 74426)
			String handleIdentifier = JavaCore.create(ajFile).getHandleIdentifier();
			ajFile = null;
			return handleIdentifier;
		}

		// are we being called in the context of a move/DnD operation?
		final String moveClass = "org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitReorgChange"; //$NON-NLS-1$
		if (callerName.equals(moveClass)) {
			// need to return a handle identifier that JDT can use (bug 121533)
      return super.getHandleIdentifier().replace(
          AspectElement.JEM_ASPECT_CU,
          JavaElement.JEM_COMPILATIONUNIT);
		}
		return super.getHandleIdentifier();
	}


	/*
	 * Clone this handle so that it caches its contents in memory.
	 * DO NOT PASS TO CLIENTS
	 */
	public AJCompilationUnit ajCloneCachingContents() {
	    return new AJCompilationUnit((PackageFragment) this.getParent(), this.name, this.owner) {
	        private char[] cachedContents;
	        public char[] getContents() {
	            if (this.cachedContents == null)
	                this.cachedContents = AJCompilationUnit.this.getContents();
	            return this.cachedContents;
	        }
	        public CompilationUnit originalFromClone() {
	            return AJCompilationUnit.this;
	        }
	    };
	}
	public AJCompilationUnit ajCloneCachingContents(final char[] contents) {
	    return new AJCompilationUnit((PackageFragment) this.getParent(), this.name, this.owner) {
	        public char[] getContents() {
	            return contents;
	        }
	        public CompilationUnit originalFromClone() {
	            return AJCompilationUnit.this;
	        }
	    };
	}

}
