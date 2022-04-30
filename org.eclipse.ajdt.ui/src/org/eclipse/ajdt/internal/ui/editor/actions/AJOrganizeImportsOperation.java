/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.editor.actions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.ajdt.core.javaelements.SourceRange;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.parserbridge.AJCompilationUnitProblemFinder;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.manipulation.ImportReferencesCollector;
import org.eclipse.jdt.core.manipulation.TypeKinds;
import org.eclipse.jdt.core.manipulation.TypeNameMatchCollector;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.internal.core.manipulation.JavaManipulationMessages;
//import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.Strings;
import org.eclipse.jdt.internal.ui.text.correction.ASTResolving;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.text.edits.TextEdit;

/**
 * @author Sian
 * Mostly copied from OrganizeImportsOperation - changes marked with // AspectJ Change
 */
public class AJOrganizeImportsOperation implements IWorkspaceRunnable {

	public interface IChooseImportQuery {
		/**
		 * Selects imports from a list of choices.
		 * @param openChoices From each array, a type reference has to be selected
		 * @param ranges For each choice the range of the corresponding  type reference.
		 * @return Returns <code>null</code> to cancel the operation, or the
		 *         selected imports.
		 */
		TypeNameMatch[] chooseImports(TypeNameMatch[][] openChoices, ISourceRange[] ranges);
	}


	private static class TypeReferenceProcessor {

		private static class UnresolvedTypeData {
			final SimpleName ref;
			final int typeKinds;
			final List<TypeNameMatch> foundInfos;

			public UnresolvedTypeData(SimpleName ref) {
				this.ref= ref;
				this.typeKinds= ASTResolving.getPossibleTypeKinds(ref, true);
				this.foundInfos= new ArrayList<>(3);
			}

			public void addInfo(TypeNameMatch info) {
				for (int i= this.foundInfos.size() - 1; i >= 0; i--) {
					TypeNameMatch curr= this.foundInfos.get(i);
					if (curr.getTypeContainerName().equals(info.getTypeContainerName())) {
						return; // not added. already contains type with same name
					}
				}
				foundInfos.add(info);
			}
		}

		private final Set<String> fOldSingleImports;
		private final Set<String> fOldDemandImports;

		private final Set<String> fImplicitImports;

		private final ImportRewrite fImpStructure;

		private final boolean fDoIgnoreLowerCaseNames;

		private final IPackageFragment fCurrPackage;

		private final ScopeAnalyzer fAnalyzer;
		private final boolean fAllowDefaultPackageImports;

		private final Map<String, UnresolvedTypeData> fUnresolvedTypes;
		private final Set<String> fImportsAdded;
		private TypeNameMatch[][] fOpenChoices;
		private SourceRange[] fSourceRanges;


		public TypeReferenceProcessor(Set<String> oldSingleImports, Set<String> oldDemandImports, CompilationUnit root, ImportRewrite impStructure, boolean ignoreLowerCaseNames) {
			fOldSingleImports= oldSingleImports;
			fOldDemandImports= oldDemandImports;
			fImpStructure= impStructure;
			fDoIgnoreLowerCaseNames= ignoreLowerCaseNames;

			ICompilationUnit cu= impStructure.getCompilationUnit();

			fImplicitImports= new HashSet<>(3);
			fImplicitImports.add(""); //$NON-NLS-1$
			fImplicitImports.add("java.lang"); //$NON-NLS-1$
			fImplicitImports.add(cu.getParent().getElementName());

			fAnalyzer= new ScopeAnalyzer(root);

			fCurrPackage= (IPackageFragment) cu.getParent();

			fAllowDefaultPackageImports= cu.getJavaProject().getOption(JavaCore.COMPILER_COMPLIANCE, true).equals(JavaCore.VERSION_1_3);

			fImportsAdded= new HashSet<>();
			fUnresolvedTypes= new HashMap<>();
		}

		private boolean needsImport(ITypeBinding typeBinding, SimpleName ref) {
			if (!typeBinding.isTopLevel() && !typeBinding.isMember()) {
				return false; // no imports for anonymous, local, primitive types or parameters types
			}
			int modifiers= typeBinding.getModifiers();
			if (Modifier.isPrivate(modifiers)) {
				return false; // imports for privates are not required
			}
			ITypeBinding currTypeBinding= Bindings.getBindingOfParentType(ref);
			if (currTypeBinding == null) {
				return false; // not in a type
			}
			if (!Modifier.isPublic(modifiers)) {
				if (!currTypeBinding.getPackage().getName().equals(typeBinding.getPackage().getName())) {
					return false; // not visible
				}
			}

			ASTNode parent= ref.getParent();
			if (parent instanceof Type) {
				parent= parent.getParent();
			}
			if (parent instanceof AbstractTypeDeclaration && parent.getParent() instanceof CompilationUnit) {
				return true;
			}

			if (typeBinding.isMember()) {
				IBinding[] visibleTypes= fAnalyzer.getDeclarationsInScope(ref, ScopeAnalyzer.TYPES);
        for (IBinding visibleType : visibleTypes) {
          ITypeBinding curr = ((ITypeBinding) visibleType).getTypeDeclaration();
          if (curr == typeBinding) {
            return false;
          }
        }
			}
			return true;
		}


		/**
		 * Tries to find the given type name and add it to the import structure.
		 */
		public void add(SimpleName ref) throws CoreException {
		    String typeName= ref.getIdentifier();

		    if (fImportsAdded.contains(typeName)) {
		        return;
		    }

		    IBinding binding= ref.resolveBinding();
		    if (binding != null) {
		        if (binding.getKind() != IBinding.TYPE) {
		            return;
		        }
		        ITypeBinding typeBinding= (ITypeBinding) binding;
		        if (typeBinding.isArray()) {
		            typeBinding= typeBinding.getElementType();
		        }
		        typeBinding= typeBinding.getTypeDeclaration();
		        if (!typeBinding.isRecovered()) {
		            if (needsImport(typeBinding, ref)) {
		                fImpStructure.addImport(typeBinding);
		                fImportsAdded.add(typeName);
		            }
		            return;
		        }
		    } else {
		        if (fDoIgnoreLowerCaseNames && typeName.length() > 0) {
		            char ch= typeName.charAt(0);
		            if (Strings.isLowerCase(ch) && Character.isLetter(ch)) {
		                return;
		            }
		        }
		    }
		    fImportsAdded.add(typeName);
		    fUnresolvedTypes.put(typeName, new UnresolvedTypeData(ref));
		}

		public boolean process(IProgressMonitor monitor) throws JavaModelException {
			try {
				int nUnresolved= fUnresolvedTypes.size();
				if (nUnresolved == 0) {
					return false;
				}
				char[][] allTypes= new char[nUnresolved][];
				int i= 0;
        for (String s : fUnresolvedTypes.keySet()) {
          allTypes[i++] = s.toCharArray();
        }
				ArrayList<TypeNameMatch> typesFound= new ArrayList<>();
				IJavaProject project= fCurrPackage.getJavaProject();
				IJavaSearchScope scope= SearchEngine.createJavaSearchScope(new IJavaElement[] { project });
				TypeNameMatchCollector collector= new TypeNameMatchCollector(typesFound);
				new SearchEngine().searchAllTypeNames(null, allTypes, scope, collector, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, monitor);

				boolean is50OrHigher= 	JavaModelUtil.is50OrHigher(project);

				for (i= 0; i < typesFound.size(); i++) {
					TypeNameMatch curr= typesFound.get(i);
					UnresolvedTypeData data= fUnresolvedTypes.get(curr.getSimpleTypeName());
					if (data != null && isVisible(curr) && isOfKind(curr, data.typeKinds, is50OrHigher)) {
						if (fAllowDefaultPackageImports || curr.getPackageName().length() > 0) {
							data.addInfo(curr);
						}
					}
				}

				ArrayList<TypeNameMatch[]> openChoices= new ArrayList<>(nUnresolved);
				ArrayList<SourceRange> sourceRanges= new ArrayList<>(nUnresolved);
        for (UnresolvedTypeData data : fUnresolvedTypes.values()) {
          TypeNameMatch[] openChoice = processTypeInfo(data.foundInfos);
          if (openChoice != null) {
            openChoices.add(openChoice);
            sourceRanges.add(new SourceRange(data.ref.getStartPosition(), data.ref.getLength()));
          }
        }
				if (openChoices.isEmpty()) {
					return false;
				}
				fOpenChoices= openChoices.toArray(new TypeNameMatch[openChoices.size()][]);
				fSourceRanges= sourceRanges.toArray(new SourceRange[0]);
				return true;
			} finally {
				monitor.done();
			}
		}

//		private static List getAspectJTypes(IJavaSearchScope scope) {
//			List ajTypes = new ArrayList();
//			IProject[] projects = AspectJPlugin.getWorkspace().getRoot()
//					.getProjects();
//			for (int i = 0; i < projects.length; i++) {
//				try {
//					if(projects[i].hasNature("org.eclipse.ajdt.ui.ajnature")) { //$NON-NLS-1$
//						IJavaProject jp = JavaCore.create(projects[i]);
//						if (jp != null) {
//							IPath[] paths = scope.enclosingProjectsAndJars();
//							for (int a = 0; a < paths.length; a++) {
//								if (paths[a].equals(jp.getPath())) {
//									List ajCus = AJCompilationUnitManager.INSTANCE.getAJCompilationUnits(jp);
//									for (Iterator iter = ajCus.iterator(); iter
//											.hasNext();) {
//										AJCompilationUnit unit = (AJCompilationUnit) iter.next();
//										IType[] types = unit.getAllTypes();
//										for (int j = 0; j < types.length; j++) {
//											IType type = types[j];
//											char[][] enclosingTypes = AJDTUtils.getEnclosingTypes(type);
//											int kind = type.getFlags(); // 103131 - pass in correct flags
//											if (type instanceof AspectElement) { // 3.2 - Classes in .aj files are found
//												//TODO: 3.3M3
//												AJCUTypeInfo info = new AJCUTypeInfo(
//														type.getPackageFragment().getElementName(),
//														type.getElementName(),
//														enclosingTypes,
//														kind,
//														type instanceof AspectElement,
//														jp.getElementName(),
//														unit.getPackageFragmentRoot().getElementName(),
//														unit.getElementName().substring(0, unit.getElementName().lastIndexOf('.')),
//														"aj", //$NON-NLS-1$
//														unit);
//												ajTypes.add(info);
//											}
//
//										}
//									}
//								}
//							}
//						}
//					}
//				} catch (JavaModelException e) {
//				} catch (CoreException e) {
//				}
//			}
//			return ajTypes;
//		}


		private TypeNameMatch[] processTypeInfo(List<TypeNameMatch> typeRefsFound) {
			int nFound= typeRefsFound.size();
			if (nFound == 0) {
				// nothing found
				return null;
			} else if (nFound == 1) {
				TypeNameMatch typeRef= typeRefsFound.get(0);
				fImpStructure.addImport(typeRef.getFullyQualifiedName());
				return null;
			} else {
				String typeToImport= null;
				boolean ambiguousImports= false;

				// multiple found, use old imports to find an entry
        for (TypeNameMatch typeRef : typeRefsFound) {
          String fullName = typeRef.getFullyQualifiedName();
          String containerName = typeRef.getTypeContainerName();
          if (fOldSingleImports.contains(fullName)) {
            // was single-imported
            fImpStructure.addImport(fullName);
            return null;
          }
          else if (fOldDemandImports.contains(containerName) || fImplicitImports.contains(containerName)) {
            if (typeToImport == null) {
              typeToImport = fullName;
            }
            else {  // more than one import-on-demand
              ambiguousImports = true;
            }
          }
        }

				if (typeToImport != null && !ambiguousImports) {
					fImpStructure.addImport(typeToImport);
					return null;
				}
				// return the open choices
				return typeRefsFound.toArray(new TypeNameMatch[nFound]);
			}
		}

		private boolean isOfKind(TypeNameMatch curr, int typeKinds, boolean is50OrHigher) {
			int flags= curr.getModifiers();
			if (Flags.isAnnotation(flags)) {
				return is50OrHigher && ((typeKinds & TypeKinds.ANNOTATIONS) != 0);
			}
			if (Flags.isEnum(flags)) {
				return is50OrHigher && ((typeKinds & TypeKinds.ENUMS) != 0);
			}
			if (Flags.isInterface(flags)) {
				return (typeKinds & TypeKinds.INTERFACES) != 0;
			}
			return (typeKinds & TypeKinds.CLASSES) != 0;
		}

		private boolean isVisible(TypeNameMatch curr) {
			int flags= curr.getModifiers();
			if (Flags.isPrivate(flags)) {
				return false;
			}
			if (Flags.isPublic(flags) || Flags.isProtected(flags)) {
				return true;
			}
			return curr.getPackageName().equals(fCurrPackage.getElementName());
		}

		public TypeNameMatch[][] getChoices() {
			return fOpenChoices;
		}

		public ISourceRange[] getChoicesSourceRanges() {
			return fSourceRanges;
		}
	}

	private final boolean fDoSave;

	private final boolean fIgnoreLowerCaseNames;

	private final IChooseImportQuery fChooseImportQuery;

	private int fNumberOfImportsAdded;
	private int fNumberOfImportsRemoved;

	private IProblem fParsingError;
	private final ICompilationUnit fCompilationUnit;

	private final CompilationUnit fASTRoot;

	public AJOrganizeImportsOperation(ICompilationUnit cu, CompilationUnit astRoot, boolean ignoreLowerCaseNames, boolean save, boolean doResolve, IChooseImportQuery chooseImportQuery) throws CoreException {
		fCompilationUnit= cu;
		fASTRoot= astRoot;

		fDoSave= save;
		fIgnoreLowerCaseNames= ignoreLowerCaseNames;
		fChooseImportQuery= chooseImportQuery;

		fNumberOfImportsAdded= 0;
		fNumberOfImportsRemoved= 0;

		fParsingError= null;
	}

	/**
	 * Runs the operation.
	 * @throws OperationCanceledException Runtime error thrown when operation is canceled.
	 */
	public void run(IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		if (monitor == null) {
			monitor= new NullProgressMonitor();
		}
		try {
			fNumberOfImportsAdded= 0;
			fNumberOfImportsRemoved= 0;

			monitor.beginTask(Messages.format(JavaManipulationMessages.OrganizeImportsOperation_description, fCompilationUnit.getElementName()), 10);

			CompilationUnit astRoot= fASTRoot;
			if (astRoot == null) {
				astRoot= SharedASTProvider.getAST(fCompilationUnit, SharedASTProvider.WAIT_YES, new SubProgressMonitor(monitor, 2));
			} else {
				monitor.worked(2);
			}

			ImportRewrite importsRewrite= StubUtility.createImportRewrite(astRoot, false);

			Set<String> oldSingleImports= new HashSet<>();
			Set<String>  oldDemandImports= new HashSet<>();
			List<SimpleName> typeReferences= new ArrayList<>();
			List<SimpleName> staticReferences= new ArrayList<>();

			if (!collectReferences(astRoot, typeReferences, staticReferences, oldSingleImports, oldDemandImports)) {
				return;
			}

			monitor.worked(1);

			TypeReferenceProcessor processor= new TypeReferenceProcessor(oldSingleImports, oldDemandImports, astRoot, importsRewrite, fIgnoreLowerCaseNames);

      for (SimpleName typeRef : typeReferences) {
        processor.add(typeRef);
      }

			boolean hasOpenChoices= processor.process(new SubProgressMonitor(monitor, 3));
			addStaticImports(staticReferences, importsRewrite);

			if (hasOpenChoices && fChooseImportQuery != null) {
				TypeNameMatch[][] choices= processor.getChoices();
				ISourceRange[] ranges= processor.getChoicesSourceRanges();
				TypeNameMatch[] chosen= fChooseImportQuery.chooseImports(choices, ranges);
				if (chosen == null) {
					// cancel pressed by the user
					throw new OperationCanceledException();
				}
        for (TypeNameMatch typeInfo : chosen) {
          importsRewrite.addImport(typeInfo.getFullyQualifiedName());
        }
			}

			TextEdit edit= importsRewrite.rewriteImports(new SubProgressMonitor(monitor, 3));

			JavaModelUtil.applyEdit(fCompilationUnit, edit, fDoSave, new SubProgressMonitor(monitor, 1));

			determineImportDifferences(importsRewrite, oldSingleImports, oldDemandImports);
    } finally {
			monitor.done();
		}
	}

	private void determineImportDifferences(ImportRewrite importsStructure, Set<String> oldSingleImports, Set<String> oldDemandImports) {
  		ArrayList<String> importsAdded= new ArrayList<>();
  		importsAdded.addAll(Arrays.asList(importsStructure.getCreatedImports()));
  		importsAdded.addAll(Arrays.asList(importsStructure.getCreatedStaticImports()));

  		String[] content= oldSingleImports.toArray(new String[0]);
    for (String importName : content) {
      if (importsAdded.remove(importName))
        oldSingleImports.remove(importName);
    }
	    content= oldDemandImports.toArray(new String[0]);
    for (String importName : content) {
      if (importsAdded.remove(importName + ".*")) //$NON-NLS-1$
        oldDemandImports.remove(importName);
    }
	    fNumberOfImportsAdded= importsAdded.size();
	    fNumberOfImportsRemoved= oldSingleImports.size() + oldDemandImports.size();
	}


	private void addStaticImports(List<SimpleName> staticReferences, ImportRewrite importsStructure) {
    for (Name name : staticReferences) {
      IBinding binding = name.resolveBinding();
      if (binding != null) { // paranoia check
        importsStructure.addStaticImport(binding);
      }
    }
	}


	// find type references in a compilation unit
	protected boolean collectReferences(CompilationUnit astRoot, List<SimpleName> typeReferences, List<SimpleName> staticReferences, Set<String> oldSingleImports, Set<String> oldDemandImports) {
	    AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(fCompilationUnit);
		IProblem[] problems= astRoot.getProblems();
    for (IProblem curr : problems) {
      if (curr instanceof CategorizedProblem) {
        CategorizedProblem cat = (CategorizedProblem) curr;
        if (curr.isError() && (curr.getID() & IProblem.Syntax) != 0 &&
            isARealProblem(model, cat))
        {
          fParsingError = curr;
          return false;
        }
      }
    }
		@SuppressWarnings("unchecked")
        List<ImportDeclaration> imports= astRoot.imports();
    for (ImportDeclaration curr : imports) {
      String id = ASTResolving.getFullName(curr.getName());
      if (curr.isOnDemand()) {
        oldDemandImports.add(id);
      }
      else {
        oldSingleImports.add(id);
      }
    }

		IJavaProject project= fCompilationUnit.getJavaProject();
		ImportReferencesCollector.collect(astRoot, project, null, typeReferences, staticReferences);

		return true;
	}

    /**
     * @param model
     * @param cat
     * @return
     */
    private boolean isARealProblem(AJProjectModelFacade model,
            CategorizedProblem cat) {
        return AJCompilationUnitProblemFinder.isARealProblem(cat,
        (org.eclipse.jdt.internal.core.CompilationUnit) fCompilationUnit, model, model.hasModel(), false);
    }

	/**
	 * After executing the operation, returns <code>null</code> if the operation has been executed successfully or
	 * the range where parsing failed.
	 */
	public IProblem getParseError() {
		return fParsingError;
	}

	public int getNumberOfImportsAdded() {
		return fNumberOfImportsAdded;
	}

	public int getNumberOfImportsRemoved() {
		return fNumberOfImportsRemoved;
	}

	/**
	 * @return Returns the scheduling rule for this operation
	 */
	public ISchedulingRule getScheduleRule() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

}
