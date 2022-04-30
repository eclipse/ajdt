/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

package org.eclipse.ajdt.internal.ui.refactoring;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.aspectj.asm.IProgramElement;
import org.aspectj.asm.IProgramElement.Kind;
import org.eclipse.ajdt.core.AspectJCore;
import org.eclipse.ajdt.core.codeconversion.AspectsConvertingParser;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.DeclareElement;
import org.eclipse.ajdt.core.javaelements.DeclareElementInfo;
import org.eclipse.ajdt.core.javaelements.IAspectJElement;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.ajdt.core.model.AJRelationshipType;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.manipulation.ImportReferencesCollector;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.NameLookup;
import org.eclipse.jdt.internal.core.NameLookup.Answer;
import org.eclipse.jface.text.Region;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.ChangeDescriptor;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringChangeDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.core.refactoring.resource.DeleteResourceChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * @author Andrew Eisenberg
 * @author Andy Clement
 */
public class PushInRefactoring extends Refactoring {

    public static final String ALL_ITDS = "all.itds";
    public static final String DELETE_EMPTY = "delete.empty";

    /**
     * Since each class is visited multiple times, and each visit may introduce
     * new imports, we must delay import rewriting
     * until each class has its ITDs already pushed in.
     *
     */
    private class PerUnitInformation {
        final Set<SimpleName> staticImports;
        final Set<SimpleName> typeImports;
        final Set<String> extraImports;
        final ICompilationUnit unit;

        // all declare parents (extends) to insert
        final Map<IType, Set<String>> declareParents;

        PerUnitInformation(ICompilationUnit unit) {
            staticImports = new HashSet<>();
            typeImports = new HashSet<>();
            extraImports = new HashSet<>();
            this.unit = unit;
            declareParents = new HashMap<>();
        }

        // does not handle imports for declare parents
        void rewriteImports() throws CoreException {
            // first check to see if this unit has been deleted.
            Change change = allChanges.get(unit);
            if (! (change instanceof TextFileChange)) {
                return;
            }

            ImportRewrite rewrite = ImportRewrite.create(unit, true);
            for (Name name : typeImports) {
                ITypeBinding binding = name.resolveTypeBinding();
                if (binding != null) {
                    rewrite.addImport(binding);
                }
            }
            for (Name name : staticImports) {
            	 ITypeBinding binding = name.resolveTypeBinding();
            	 if (binding != null) {
            		 rewrite.addImport(name.resolveTypeBinding());
            	 }
            }

            for (String qualName : extraImports) {
                rewrite.addImport(qualName);
            }
            TextEdit importEdit = rewrite.rewriteImports(new NullProgressMonitor());

            TextFileChange textChange = (TextFileChange) change;
            textChange.getEdit().addChild(importEdit);
        }

        void computeImports(List<IMember> itds, ICompilationUnit ajUnit, IProgressMonitor monitor)
                throws JavaModelException {
            IJavaProject project = ajUnit.getJavaProject();
            ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setProject(project);
            parser.setResolveBindings(true);
            parser.setSource(ajUnit);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            CompilationUnit ajAST = (CompilationUnit) parser.createAST(monitor);

            for (IMember itd : itds) {
                ISourceRange range = itd.getSourceRange();
                ImportReferencesCollector.collect(ajAST, project, new Region(
                        range.getOffset(), range.getLength()),
                        typeImports, staticImports);
                if (itd instanceof DeclareElement) {
                    DeclareElement declare = (DeclareElement) itd;
                    // no types added for declare removers
                    if (!((DeclareElementInfo) declare.getElementInfo()).isAnnotationRemover()) {
                        String qualType = getQualifiedTypeForDeclareAnnotation(declare);
                        if (qualType != null && qualType.length() > 0) {
                            extraImports.add(qualType);
                        }
                        List<Type> types = getExtraImportsFromDeclareElement(declare, ajAST);
                        for (Type type : types) {
                            ImportReferencesCollector.collect(type, project, new Region(
                                    type.getStartPosition(), type.getLength()),
                                    typeImports, staticImports);
                        }
                    }
                }
            }
        }

        // This method finds the extra imports required by a declare element
        // (eg- used inside of a declare @annotation's annotation)
        // We take advantage of the format of the converted source from aspectj to java
        // the last fields of the last type when following a particular naming convention,
        // are around to force import statements to exist.  We can read them and use them
        // to seed the extra imports list
        private List<Type> getExtraImportsFromDeclareElement(DeclareElement itd,
                CompilationUnit ajAST) {
            int numTypes = ajAST.types().size();
            if (numTypes == 0) {
                return Collections.emptyList();
            }

            String details = null;
            AbstractTypeDeclaration lastType = (AbstractTypeDeclaration) ajAST.types().get(numTypes-1);
            @SuppressWarnings("unchecked")
            List<BodyDeclaration> bodyDecls = lastType.bodyDeclarations();
            List<Type> extraSimpleNames = new LinkedList<>();
            for (int i = bodyDecls.size()-1; i >= 0; i--) {
                BodyDeclaration decl = bodyDecls.get(i);
                if (decl.getNodeType() == ASTNode.FIELD_DECLARATION) {
                    FieldDeclaration fDecl = (FieldDeclaration) decl;
                    if (fDecl.fragments().size() == 1) {
                        VariableDeclarationFragment frag = (VariableDeclarationFragment) fDecl.fragments().get(0);
                        if (frag.getName().toString().startsWith(AspectsConvertingParser.ITD_INSERTED_IDENTIFIER)) {
                            if (details == null) {
                                IProgramElement ipe = getModel(itd).javaElementToProgramElement(itd);
                                details = ipe.getDetails();
                            }
                            Type type = fDecl.getType();
                            // only add if this type exists in the declare @annotation
                            if (details.contains(type.toString())) {
                                 extraSimpleNames.add(type);
                            }

                            continue;
                        }
                    }
                }
                // break on the first body declaration that does not conform
                break;
            }
            return extraSimpleNames;
        }

        public void addDeclarParents(IType type, List<String> parentTypes) {
          Set<String> set = declareParents.computeIfAbsent(type, k -> new LinkedHashSet<>());
          set.addAll(parentTypes);
            for (String parent : parentTypes) {
                String[] split = parent.split("[<>,]");
                for (String name : split) {
                    extraImports.add(name.trim());
                }
            }
        }
    }

    private final boolean deleteEmpty = true;

    private Map<ICompilationUnit, Change> allChanges = null;

    private List<IMember> itds = null;

    private final Map<IProject, AJProjectModelFacade> allModels = new HashMap<>();
    private AJProjectModelFacade getModel(IJavaElement elt) {
        IProject project = elt.getJavaProject().getProject();
        AJProjectModelFacade model = allModels.get(project);
        if (model == null) {
            model = AJProjectModelFactory.getInstance().getModelForProject(project);
            allModels.put(project, model);
        }
        return model;
    }

    private final Map<IJavaProject, NameLookup> allLookups = new HashMap<>();
    private NameLookup getLookup(IJavaElement elt) throws JavaModelException {
        IJavaProject javaProject = elt.getJavaProject();
        NameLookup nameLookup = allLookups.get(javaProject);
        if (nameLookup == null) {
            nameLookup = ((JavaProject) javaProject).newNameLookup(DefaultWorkingCopyOwner.PRIMARY);
            allLookups.put(javaProject, nameLookup);
        }
        return nameLookup;
    }


    @Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor monitor)
            throws CoreException, OperationCanceledException {
        final RefactoringStatus status = new RefactoringStatus();
        try {
            monitor.beginTask("Checking final conditions...", 2);
            allChanges = new LinkedHashMap<>();
            // map from AJCUs to contained ITDs that will be pushed in
            Map<ICompilationUnit, List<IMember>> unitToITDs = new HashMap<>();
            Map<ICompilationUnit, PerUnitInformation> importsMap = new HashMap<>();

            for (IMember itd : itds) {
                if (itd instanceof IAspectJElement && ((IAspectJElement) itd).getAJKind() == Kind.DECLARE_PARENTS) {
                    AJProjectModelFacade model = getModel(itd);
                    // rememebr the types pushed in and associate them with the target types
                    List<IJavaElement> elts = model.getRelationshipsForElement(itd, AJRelationshipManager.DECLARED_ON);
                    IProgramElement ipe = model.javaElementToProgramElement(itd);
                    List<String> parentTypes = ipe.getParentTypes();
                    for (IJavaElement elt : elts) {
                        if (elt.getElementType() == IJavaElement.TYPE) {
                            IType type = (IType) elt;
                            ICompilationUnit owningUnit = type.getCompilationUnit();
                            PerUnitInformation holder = importsMap.get(owningUnit);
                            if (holder == null) {
                                holder = new PerUnitInformation(owningUnit);
                                importsMap.put(owningUnit, holder);
                            }
                            holder.addDeclarParents(type, parentTypes);
                        }
                    }
                }

                // remember the ITDs per compilation unit so that we can remove them later
                ICompilationUnit unit = itd.getCompilationUnit();
              List<IMember> itdList = unitToITDs.computeIfAbsent(unit, k -> new LinkedList<>());
              itdList.add(itd);
            }

            // now do the work ITDs and declare annotation
            for (Map.Entry<ICompilationUnit,List<IMember>> entry :  unitToITDs.entrySet()) {
                status.merge(checkFinalConditionsForITD(
                        entry.getKey(), entry.getValue(), importsMap,
                        monitor));
            }

            // now go through and create the import edits
            for (PerUnitInformation holder : importsMap.values()) {
                holder.rewriteImports();
            }

        } finally {
            allLookups.clear();
            allModels.clear();
            monitor.done();
        }
        return status;
    }

    /**
     * Checks the conditions for a single {@link AJCompilationUnit}
     * @param ajUnit the unit to check
     * @param itdsForUnit all itds in this unit
     * @param imports a map from target {@link ICompilationUnit} to imports that need to be added
     * initially empty, but populated in this method
     * @param monitor
     * @return
     * @throws JavaModelException
     */
    private RefactoringStatus checkFinalConditionsForITD(final ICompilationUnit ajUnit,
            final List<IMember> itdsForUnit,
            final Map<ICompilationUnit, PerUnitInformation> imports,
            final IProgressMonitor monitor) throws JavaModelException {

        final RefactoringStatus status = new RefactoringStatus();

        // group all of the ITD targets by the ICompilationUnit that they are in
        final Map<ICompilationUnit, Set<IMember>> unitsToTypes = getUnitTypeMap(getTargets(itdsForUnit));

        // group all of the ICompilationUnits by project
        final Map<IJavaProject, Collection<ICompilationUnit>> projects= new HashMap<>();
        for (ICompilationUnit targetUnit : unitsToTypes.keySet()) {
            IJavaProject project= targetUnit.getJavaProject();
            if (project != null) {
              Collection<ICompilationUnit> collection = projects.computeIfAbsent(project, k -> new ArrayList<>());
              collection.add(targetUnit);
            }
        }

        // also add the ajunit to the collection of affected units
        Collection<ICompilationUnit> units;
        IJavaProject aspectProject = ajUnit.getJavaProject();
        if (projects.containsKey(aspectProject)) {
            units = projects.get(aspectProject);
        } else {
            units = new ArrayList<>();
            projects.put(aspectProject, units);
        }
        units.add(ajUnit);

        // this requestor performs the real work
        ASTRequestor requestors = new ASTRequestor() {
            @Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
                try {

                    // compute the imports that this itd adds to this unit
                    PerUnitInformation holder;
                    if (imports.containsKey(source)) {
                        holder = imports.get(source);
                    } else {
                        holder = new PerUnitInformation(source);
                        imports.put(source, holder);
                    }
                    holder.computeImports(itdsForUnit, ajUnit, monitor);

                    for (Entry<IType, Set<String>> entry : holder.declareParents.entrySet()) {
                        rewriteDeclareParents(entry.getKey(), ast, entry.getValue(), source);
                    }

                    // make the simplifying assumption that the CU that contains the
                    // ITD does not also contain a target type
                    // Bug 310020
                    if (isCUnitContainingITD(source, itdsForUnit.get(0))) {
                        // this is an AJCU.
                        rewriteAspectType(itdsForUnit, source, ast);
                    } else {
                        // this is a regular CU

                        for (IMember itd : itdsForUnit) {

                            // filter out the types not affected by itd
                          Collection<IMember> members = new ArrayList<>(unitsToTypes.get(source));
                            AJProjectModelFacade model = getModel(itd);
                            List<IJavaElement> realTargets;
                            if (itd instanceof IAspectJElement && ((IAspectJElement) itd).getAJKind().isDeclareAnnotation()) {
                                realTargets = model
                                        .getRelationshipsForElement(
                                                itd,
                                                AJRelationshipManager.ANNOTATES);
                            } else {
                                // regular ITD or an ITIT
                                realTargets = model
                                        .getRelationshipsForElement(
                                                itd,
                                                AJRelationshipManager.DECLARED_ON);
                            }
                          members.removeIf(member -> !realTargets.contains(member));

                            if (members.size() > 0) {
                                // if declare parents, store until later
                                if (itd instanceof IAspectJElement &&
                                        ((IAspectJElement) itd).getAJKind() == Kind.DECLARE_PARENTS) {
                                    // already taken care of
                                }
                                applyTargetTypeEdits(itd, source, members);
                            }
                        }  // for (Iterator itdIter = itdsForUnit.iterator(); itdIter.hasNext();) {
                    }
                }
                catch (CoreException ignored) {
                }
            }
        };
        IProgressMonitor subMonitor= new SubProgressMonitor(monitor, 1);
        try {
            try {
                final Set<IJavaProject> set= projects.keySet();
                subMonitor.beginTask("Compiling source...", set.size());
                for (IJavaProject project : projects.keySet()) {
                    ASTParser parser= ASTParser.newParser(AST.JLS8);
                    parser.setProject(project);
                    parser.setResolveBindings(true);
                    Collection<ICompilationUnit> collection= projects.get(project);
                    parser.createASTs(collection.toArray(
                        new ICompilationUnit[0]), new String[0],
                            requestors, new SubProgressMonitor(subMonitor, 1));
                }

            } finally {
                subMonitor.done();
            }

        } finally {
            subMonitor.done();
        }
        return status;
    }


    /**
     * Removes all ITDs in the given compilation unit.
     * Will delete an aspect if it has no more members.
     * Will delete an {@link AJCompilationUnit} if all
     * of its types are deleted.
     * @param itdsForUnit the ITDs for the given unit
     * @param source
     * @param ast used to calculate imports
     * @throws JavaModelException
     * @throws CoreException
     */
    protected void rewriteAspectType(List<IMember> itdsForUnit,
            ICompilationUnit source, CompilationUnit ast) throws JavaModelException, CoreException {

        // used to keep track of aspect types that are deleted.
        Map<IType, Integer> removalStored = new HashMap<>();
        Map<IType, List<DeleteEdit>> typeDeletes = new HashMap<>();

        // go through each ITD and create a delete edit for it
        for (IMember itd : itdsForUnit) {
            IType parentAspectType = (IType) itd.getParent();
            int numRemovals;
            if (removalStored.containsKey(parentAspectType)) {
                numRemovals = removalStored.get(parentAspectType);
                removalStored.put(parentAspectType, ++numRemovals);
            } else {
                removalStored.put(parentAspectType, 1);
            }
            List<DeleteEdit> deletes;
            if (typeDeletes.containsKey(parentAspectType)) {
                deletes = typeDeletes.get(parentAspectType);
            } else {
                deletes = new LinkedList<>();
                typeDeletes.put(parentAspectType, deletes);
            }

            DeleteEdit edit = new DeleteEdit(itd.getSourceRange().getOffset(), itd.getSourceRange().getLength()+1);
            deletes.add(edit);
        }

        if (deleteTypes(ast, typeDeletes, removalStored)) {
            allChanges.put(source, new DeleteResourceChange(source.getResource().getFullPath(), false));
        } else {
            applyAspectEdits(source, typeDeletes);
        }
    }

    /**
     * Adds the specified new parents to the type.
     * Need to determine if the new paretns are extends or implements
     *
     * FIXADE will not handle generic types
     * @param targetType
     * @param astUnit
     * @param newParents
     * @param holder
     * @throws JavaModelException
     */
    @SuppressWarnings("unchecked")
    private void rewriteDeclareParents(IType targetType, CompilationUnit astUnit, Set<String> newParents,
            ICompilationUnit unit) throws JavaModelException {

        // find the Type declaration in the ast
        TypeDeclaration typeDecl = findType(astUnit, targetType.getElementName());
        if (typeDecl == null) {
            createJavaModelException("Couldn't find type " + targetType.getElementName() + " in " +
            unit.getElementName());
        }

        // convert all parents to simple names
        List<String> simpleParents = new ArrayList<>(newParents.size());
        for (String qual : newParents) {
            simpleParents.add(convertToSimple(qual));
        }

        // now remove any possible duplicates
        Type superclassType = typeDecl.getSuperclassType();
      if (superclassType != null && superclassType.isSimpleType()) {
            simpleParents.remove(((SimpleType) superclassType).getName().getFullyQualifiedName());
        }
        for (Type iface : (Iterable<Type>) typeDecl.superInterfaceTypes()) {
            if (iface.isSimpleType()) {
                simpleParents.remove(((SimpleType) iface).getName().getFullyQualifiedName());
            }
        }

        // Find the super class if exists
        // make assumption that there is at most one super class defined.
        // if this weren't the case, then there would be a compile error
        // and it would not be possible to invoke refactoring
        String newSuper = null;
        for (String parent : newParents) {
            if (isClass(parent, targetType)) {
            	newSuper = convertToSimple(parent);
                simpleParents.remove(newSuper);
            }
        }

        // do the rewrite.  Only need to add simple names since imports are already taken care of
        // in the holder
        ASTRewrite rewriter = ASTRewrite.create(astUnit.getAST());
        AST ast = typeDecl.getAST();
        if (newSuper != null) {
            Type newSuperType = createTypeAST(newSuper, ast);
            if (superclassType == null) {
                rewriter.set(typeDecl, TypeDeclaration.SUPERCLASS_TYPE_PROPERTY, newSuperType, null);
            } else {
                rewriter.replace(superclassType, newSuperType, null);
            }
        }
        if (simpleParents.size() > 0) {
            ListRewrite listRewrite = rewriter.getListRewrite(typeDecl, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY);
            for (String simpleParent : simpleParents) {
                listRewrite.insertLast(createTypeAST(simpleParent, ast), null);
            }
        }
        // finally, add the new change
        TextEdit edit = rewriter.rewriteAST();
        if (!isEmptyEdit(edit)) {
            TextFileChange change= (TextFileChange) allChanges.get(unit);
            if (change == null) {
                change= new TextFileChange(unit.getElementName(), (IFile) unit.getResource());
                change.setTextType("java");
                change.setEdit(new MultiTextEdit());
                allChanges.put(unit, change);
            }
            change.getEdit().addChild(edit);
        }
    }


    private Type createTypeAST(String newSuper, AST ast) {
        String toParse = newSuper + " t;";
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource((toParse).toCharArray());
        parser.setKind(ASTParser.K_CLASS_BODY_DECLARATIONS);
        ASTNode astNode = parser.createAST(null);
        Type t = null;
        if (astNode instanceof TypeDeclaration) {
            Object object = ((TypeDeclaration) astNode).bodyDeclarations().get(0);
            if (object instanceof FieldDeclaration) {
                t = ((FieldDeclaration) object).getType();
                t = (Type) ASTNode.copySubtree(ast, t);
            }
        }
        if (t == null) {
            t = ast.newSimpleType(ast.newSimpleName("MISSING"));
        }
        return t;
    }

    /**
     * Converts from a possibly generic fully qualified type name to a simple fully
     * qualified type name
     * @param qualName
     * @return
     */
    String convertToSimple(String qualName) {
        char[] charArray = qualName.toCharArray();
        StringBuilder candidate = new StringBuilder(charArray.length);
        StringBuilder complete = new StringBuilder(charArray.length);
        for (char c : charArray) {
            switch (c) {
                case '.':
                    candidate.delete(0, candidate.length());
                    break;
                case '<':
                case ',':
                case '>':
                    complete.append(candidate).append(c);
                    candidate.delete(0, candidate.length());
                    break;
                default:
                    candidate.append(c);
            }
        }
        complete.append(candidate);
        return complete.toString();
    }


    /**
     * @return true iff name is the fully qualified name of a class, false if an interface, enum, etc
     * @throws JavaModelException
     */
    private boolean isClass(String name, IType type) throws JavaModelException {
        String erasedName = name;
        int genericsIndex = name.indexOf('<');
        if (genericsIndex > 0) {
            erasedName = name.substring(0, genericsIndex);
        }

        int dotIndex = erasedName.lastIndexOf('.');
        String packageName;
        String simpleName;
        if (dotIndex > 0) {
            packageName = erasedName.substring(0, dotIndex);
            simpleName = erasedName.substring(dotIndex +1);
        } else {
            packageName = "";
            simpleName = erasedName;
        }
        IType found = findType(packageName, simpleName, type);
        return found != null && found.isClass();
    }
    private IType findType(String packageName, String simpleName, IType type) throws JavaModelException {
        NameLookup lookup = getLookup(type);
        Answer answer = lookup.findType(simpleName, packageName, false, NameLookup.ACCEPT_CLASSES | NameLookup.ACCEPT_INTERFACES, true, false, false, null);
        if (answer != null) {
            return answer.type;
        }
        // might be an inner type
        int dotIndex = packageName.lastIndexOf('.');
        if (dotIndex > 0) {
            IType foundType = findType(packageName.substring(0, dotIndex), packageName.substring(dotIndex+1), type);
            if (foundType != null && foundType.getType(simpleName).exists()) {
                return foundType.getType(simpleName);
            }
        }
        return null;
    }


    private void createJavaModelException(String message)
            throws JavaModelException {
        throw new JavaModelException(new CoreException(new Status(IStatus.INFO, AspectJUIPlugin.PLUGIN_ID,
        		message)));
    }

    @SuppressWarnings("unchecked")
    private TypeDeclaration findType(CompilationUnit ast, String name) {
        for (TypeDeclaration type : (Iterable<TypeDeclaration>) ast.types()) {
            if (type.getName().getIdentifier().equals(name)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Deletes all aspect types that have no more children
     * returns true if the compilation unit should be deleted
     * @param ast
     * @param typeDeletes
     * @param removalStored
     * @return
     * @throws JavaModelException
     */
    private boolean deleteTypes(CompilationUnit ast, Map<IType, List<DeleteEdit>> typeDeletes,
            Map<IType, Integer> removalStored) throws JavaModelException {
        if (!deleteEmpty) {
            return false;
        }
        int typesDeleted = 0;
        for (Map.Entry<IType, Integer> entry : removalStored.entrySet()) {
            IType type = entry.getKey();
            int removals = entry.getValue();

            // check to see if all of the type's children
            // have been removed.  If so, delete the type
            if (type.getChildren().length == removals) {
                @SuppressWarnings("unchecked")
                List<AbstractTypeDeclaration> typeNodes = ast.types();
                for (AbstractTypeDeclaration typeNode : typeNodes) {
                    if (typeNode.getName().toString().equals(type.getElementName())) {
                        List<DeleteEdit> deletes = typeDeletes.get(type);
                        deletes.clear();
                        deletes.add(new DeleteEdit(type.getSourceRange().getOffset(), type.getSourceRange().getLength()));
                        typesDeleted++;
                    }
                }
            }
        }

        // check to see if all types have been deleted.
        return (ast.types().size() == typesDeleted);
    }



    private void applyTargetTypeEdits(IMember itd,
            ICompilationUnit source, Collection<IMember> targets) throws CoreException, JavaModelException {
        MultiTextEdit multiEdit = new MultiTextEdit();
        for (IMember target : targets) {
            TextEdit edit = null;
            if (itd instanceof IAspectJElement) {
                IAspectJElement ajElement = (IAspectJElement) itd;
                if (itd instanceof IntertypeElement) {
                    if (target instanceof IType) {
                        IType type = (IType) target;
                        // ignore ITD fields and constructors on interfaces
                        if (type.isInterface() && ajElement.getAJKind() != Kind.INTER_TYPE_METHOD) {
                            edit = null;
                        } else {
                            edit = createEditForITDTarget((IntertypeElement) itd, type);
                        }
                    }
                } else if (ajElement.getAJKind().isDeclareAnnotation()) {
                    edit = createEditForDeclareTarget((DeclareElement) itd, target);
                }
            } else if (itd instanceof IType) {
                // an ITIT
                edit = createEditForIntertypeInnerType((IType) itd, (IType) target);
            }
            if (edit != null) {
                multiEdit.addChild(edit);
            }
        }

        if (!isEmptyEdit(multiEdit)) {
            TextFileChange change= (TextFileChange) allChanges.get(source);
            if (change == null) {
                change= new TextFileChange(source.getElementName(), (IFile) source.getResource());
                change.setTextType("java");
                change.setEdit(new MultiTextEdit());
                allChanges.put(source, change);
            }
            change.getEdit().addChild(multiEdit);
        }
    }


    private String getQualifiedTypeForDeclareAnnotation(DeclareElement itd) {
        IProgramElement ipe = getModel(itd).javaElementToProgramElement(itd);
        if (ipe != null) {
            return ipe.getAnnotationType();
        }
        return null;
    }

    /**
     * Creates the text edit for pushing in an intertype inner type
     * @param itit the intertype inner type to push in
     * @param target the target to push the itit into.
     * @return
     */
    private TextEdit createEditForIntertypeInnerType(IType itit, IType target) throws JavaModelException {
        String source = itit.getSource();
        // now, we must replace the '.' and the preceding identifier in the type name
        int nameOffset = itit.getNameRange().getOffset() - itit.getSourceRange().getOffset();
        int dotOffset = source.lastIndexOf('$', nameOffset);
        int classIndex = source.lastIndexOf("class ", dotOffset);
        source = "\n\t" + source.substring(0, classIndex + "class ".length()) + source.substring(dotOffset + 1) + "\n";
        return new InsertEdit(getITDInsertLocation(target), source);
    }

    private TextEdit createEditForDeclareTarget(DeclareElement itd,
            IMember target) throws JavaModelException {
        DeclareElementInfo declareElementInfo = (DeclareElementInfo) itd.getElementInfo();
        if (declareElementInfo.isAnnotationRemover()) {
            // must use the model to access removals
            AJProjectModelFacade model = getModel(itd);
            IProgramElement ipe = model.javaElementToProgramElement(itd);
            return getAnnotationRemovalEdit(target, ipe.getRemovedAnnotationTypes());
        } else {
            return new InsertEdit(getDeclareInsertLocation(target), getTextForDeclare(itd));
        }
    }

    /**
     * Delete the specified annotations on the target
     * @param target target element to have annotations to delete
     * @param removedAnnotationTypes annotations to delete
     * @return the delete edit.  May be a MultiText edit
     * if multiple annotations are removed
     * @throws JavaModelException
     */
    private TextEdit getAnnotationRemovalEdit(IMember target,
            String[] removedAnnotationTypes) throws JavaModelException {
        if (target instanceof IAnnotatable) {
            IAnnotatable annotatable = (IAnnotatable) target;
            IAnnotation[] anns = annotatable.getAnnotations();
            List<DeleteEdit> deletes = new ArrayList<>(removedAnnotationTypes.length);
            for (String removedType : removedAnnotationTypes) {
                // there can be only one match per removed type
                // however, there can be two annotations with the
                // same simple name on the member, but at least one has
                // a matching qual name.
                // So, if there is a simple name match, keep on looking,
                // but a qual name match, end it.
                DeleteEdit edit = null;
                for (IAnnotation ann : anns) {
                    // don't know if ann is a simple or a qualified name,
                    // so check both.
                    String annName = ann.getElementName();
                    boolean qualMatch = removedType.equals(annName);
                    boolean simpleMatch = removedType.endsWith("." + annName);
                    if (simpleMatch || qualMatch) {
                        // match!
                        ISourceRange range = ann.getSourceRange();
                        edit = new DeleteEdit(range.getOffset(), range.getLength());
                        if (qualMatch) {
                            // can only be one qualified match
                            break;
                        }
                    }
                }
                if (edit != null) {
                    deletes.add(edit);
                }
            }
            if (deletes.size() == 0) {
                return null;
            } else if (deletes.size() == 1) {
                return deletes.get(0);
            } else {
                MultiTextEdit multi = new MultiTextEdit();
                for (DeleteEdit delete : deletes) {
                    multi.addChild(delete);
                }
                return multi;
            }
        } else {
            // nothing found
            return null;
        }
    }

    private String getTextForDeclare(DeclareElement itd) throws JavaModelException {
        IProgramElement ipe = getModel(itd).javaElementToProgramElement(itd);
        if (ipe != null) {
            String details = ipe.getDetails();
            int colonIndex = details.indexOf(':');
            String text = details.substring(colonIndex+1).trim();
            if (itd.getAJKind() == Kind.DECLARE_ANNOTATION_AT_TYPE) {
                // assume top level type
                return text + "\n";
            } else {
                return text + "\n\t";
            }
        } else {
            throw new RuntimeException("Could not find program element in AspectJ model for " + itd.getHandleIdentifier());
        }
    }

    private int getDeclareInsertLocation(IMember target) throws JavaModelException {
        return target.getSourceRange().getOffset();
    }

    private TextEdit createEditForITDTarget(IntertypeElement itd, IType target)
            throws JavaModelException {
        // don't add fields or constructors to interfaces
        TextEdit edit;
        if (target.isInterface()) {
            edit = new InsertEdit(getITDInsertLocation(target), getTargetTextForInterface(itd));
        } else {
            edit = new InsertEdit(getITDInsertLocation(target), getTargetTextForClass(itd));
        }
        return edit;
    }

    private String getTargetTextForClass(IntertypeElement itd) throws JavaModelException {
        String itdName = itd.getElementName();
        String[] splits = itdName.split("\\.");
        String newName = splits[splits.length-1];
        itdName = itdName.replaceAll("\\.", "\\\\\\$");

        // check for constructor
        if (itdName.endsWith("_new")) {
            // check to see if constructor
            String maybeConstructor = itdName.substring(0, itdName.length()-"_new".length());
            String[] maybeConstructorArr = maybeConstructor.split("\\\\\\$");
            if (maybeConstructorArr.length == 2 && maybeConstructorArr[0].equals(maybeConstructorArr[1])) {
                itdName = maybeConstructorArr[0] +"\\$new";
                newName = maybeConstructorArr[0];
            }
        }

        String targetSource = getTargetSource(newName, itd);
        targetSource = "\n\t" + targetSource + "\n";
        // also replace other pplaces where the itdName may exist
        targetSource = targetSource.replaceAll(itdName, newName);

        return targetSource;
    }

    private String getTargetTextForInterface(IntertypeElement itd) throws JavaModelException {
        String itdName = itd.getElementName();
        String[] splits = itdName.split("\\.");
        String newName = splits[splits.length-1];
        itdName = itdName.replaceAll("\\.", "\\\\\\$");
        String targetSource = getTargetSource(newName, itd);
        int closeParen = targetSource.indexOf(")");  // assumption here...closing paren doesn't exist in comments
        if (closeParen >= 0) {
            targetSource = targetSource.substring(0, closeParen+1) + ';';
        }
        targetSource = "\n\t" + targetSource + "\n\n";
        // also replace any other occurrences of the itdName
        targetSource = targetSource.replaceAll(itdName, newName);
        return targetSource;
    }

    /**
     * get the target source and replace the ITD name with the new name
     * @param newName
     * @param itd
     * @return
     * @throws JavaModelException
     */
    private String getTargetSource(String newName, IntertypeElement itd) throws JavaModelException {
        int itdStart = itd.getSourceRange().getOffset();
        int itdNameStart = itd.getTargetTypeSourceRange().getOffset() - itdStart;
        int itdNameEnd = itd.getNameRange().getOffset() + itd.getNameRange().getLength() - itdStart;
        String targetSource = itd.getSource();
        targetSource = targetSource.substring(0, itdNameStart) + newName + targetSource.substring(itdNameEnd);
        return targetSource;
    }

    private int getITDInsertLocation(IType type) throws JavaModelException {
        return type.getSourceRange().getOffset()+type.getSourceRange().getLength()-1;
    }


    private void applyAspectEdits(ICompilationUnit source, Map<IType, List<DeleteEdit>> typeDeletes) throws JavaModelException,
            CoreException {
        MultiTextEdit edit = new MultiTextEdit();
        for (List<DeleteEdit> deletesForOneType : typeDeletes.values()) {
            for (DeleteEdit delete : deletesForOneType) {
                edit.addChild(delete);
            }
        }

        if (!isEmptyEdit(edit)) {
            TextFileChange change= (TextFileChange) allChanges.get(source);
            if (change == null) {
                change= new TextFileChange(source.getElementName(), (IFile) source.getResource());
                change.setTextType("java");
                change.setEdit(edit);
                allChanges.put(source, change);
            } else {
                change.getEdit().addChild(edit);
            }
        }
    }




    /**
     * Finds all of the compilation units for the given array of target elements
     * @param targets an array of target elements that are the target of ITDs in a single Aspect
     * @return all of the targets grouped by their {@link ICompilationUnit}
     */
    private Map<ICompilationUnit, Set<IMember>> getUnitTypeMap(IMember[] targets) {
        Map<ICompilationUnit, Set<IMember>> unitToTypes = new HashMap<>();
      for (IMember target : targets) {
        ICompilationUnit unit = target.getCompilationUnit();
        Set<IMember> currTypes;
        if (unitToTypes.containsKey(unit)) {
          currTypes = unitToTypes.get(unit);
        }
        else {
          currTypes = new HashSet<>();
          unitToTypes.put(unit, currTypes);
        }
        currTypes.add(target);
      }
        return unitToTypes;
    }

    @Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor monitor)
            throws CoreException, OperationCanceledException {
        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }
        RefactoringStatus status= new RefactoringStatus();

        monitor.beginTask("Checking preconditions...", 1);

        if (itds.isEmpty()) {
            return RefactoringStatus.createWarningStatus("No Intertype declarations selected.  Nothing to do.");
        }

        try {
            for (IMember itd : itds) {
                status.merge(initialITDCheck(itd));
            }
        } finally {
            monitor.done();
        }
        return status;
    }


    private RefactoringStatus initialITDCheck(IMember itd) {
        RefactoringStatus status= new RefactoringStatus();

        if (itd == null) {
            status.merge(RefactoringStatus.createFatalErrorStatus("Intertype declaration has not been specified"));
            return status;
        }
        if (! (itd instanceof IAspectJElement)) {
            return status;
        }
        try {
            AJProjectModelFacade model = getModel(itd);
            if (!model.hasModel()) {
                status.merge(RefactoringStatus.createFatalErrorStatus("No crosscutting model available.  Rebuild project."));
            }
            ICompilationUnit unit = itd.getCompilationUnit();
            Object[] itdName = new Object[] { unit.getElementName()};
            if (!itd.exists()) {
                status.merge(RefactoringStatus.createFatalErrorStatus(MessageFormat.format("ITD ''{0}'' does not exist.", itd.getElementName())));
            } else if (!unit.isStructureKnown()) {
                status.merge(RefactoringStatus.createFatalErrorStatus(MessageFormat.format("Compilation unit ''{0}'' contains compile errors.", itdName)));
            } else
                try {
                    if (unit.getResource().findMaxProblemSeverity(IMarker.MARKER, true, IResource.DEPTH_ZERO) >= IMarker.SEVERITY_ERROR) {
                        status.merge(RefactoringStatus.createFatalErrorStatus(MessageFormat.format("Compilation unit ''{0}'' contains compile errors.", itdName)));
                    }
                } catch (CoreException e) {
                    status.merge(RefactoringStatus.create(e.getStatus()));
                }
            // now check target types
            IMember[] targets = getTargets(Collections.singletonList(itd));
            if (targets.length > 0) {
              for (IMember target : targets) {
                if (!target.exists()) {
                  status.merge(RefactoringStatus.createFatalErrorStatus(
                    MessageFormat.format("Target type ''{0}'' does not exist.", target.getElementName())));
                }
                else if (target.isBinary()) {
                  status.merge(RefactoringStatus.createFatalErrorStatus(
                    MessageFormat.format("Target type ''{0}'' is binary.", target.getElementName())));
                }
                else if (!unit.isStructureKnown()) {
                  status.merge(RefactoringStatus.createFatalErrorStatus(
                    MessageFormat.format("Compilation unit ''{0}'' contains compile errors.",
                      target.getCompilationUnit().getElementName())));
                }
              }
            } else {
                status.merge(RefactoringStatus.createWarningStatus(MessageFormat.format("ITD ''{0}'' has no target.  This refactoring will delete the declaration.  " +
                		"Perhaps there is an unresolved compilation error?", itd.getElementName())));
            }
        } catch (JavaModelException e) {
            status.addFatalError("JavaModelException:\n\t" + e.getMessage() + "\n\t" + e.getJavaModelStatus().getMessage());
        }
        return status;
    }


    @Override
	public Change createChange(IProgressMonitor monitor) throws CoreException,
            OperationCanceledException {
        monitor.beginTask("Creating change...", 1);
        try {
            final Collection<Change> changes= allChanges.values();
          return new CompositeChange(getName(), changes.toArray(new Change[0])) {

              @Override
      public ChangeDescriptor getDescriptor() {
                  return new RefactoringChangeDescriptor(createDescriptor());
              }

          };
        } finally {
            monitor.done();
        }
    }

    public PushInRefactoringDescriptor createDescriptor() {
        StringBuilder projectsb = new StringBuilder();
        StringBuilder descriptionsb = new StringBuilder();
        StringBuilder commentsb = new StringBuilder();
        StringBuilder argssb = new StringBuilder();
        for (IMember itd : itds) {
            projectsb.append(itd.getJavaProject().getElementName()).append("\n");
            descriptionsb.append(MessageFormat.format("Push In intertype declaration for ''{0}''\n", itd.getElementName()));
            String itdLabel = getModel(itd).getJavaElementLinkName(itd);
            commentsb.append(MessageFormat.format("Push In intertype declaration for ''{0}''\n", itdLabel));
            argssb.append(itd.getHandleIdentifier()).append("\n");
        }
        Map<String, String> arguments = new HashMap<>();
        arguments.put(ALL_ITDS, argssb.toString());
        return new PushInRefactoringDescriptor(
                projectsb.toString(),
                descriptionsb.toString(),
                commentsb.toString(),
                arguments);
    }


    @Override
	public String getName() {
        return "Push-In";
    }


    public RefactoringStatus initialize(Map<String, String> arguments) {
        String value= arguments.get(ALL_ITDS);
        if (value != null) {
            String[] values = value.split("\\n");
            List<IMember> newitds = new ArrayList<>(values.length);
            for (int i = 0; i < values.length; i++) {
                newitds.add((IMember) AspectJCore.create(value));
            }
            setITDs(newitds);
            return new RefactoringStatus();
        } else {
            return RefactoringStatus.createErrorStatus("No ITD specified.");
        }
    }

    public void setITDs(List<IMember> itds) {
        this.itds = itds;
    }

    public List<IMember> getITDs() {
        return itds;
    }


    /**
     * This method determines all of the targets of the set of ITDs passed in.
     * it does not distinguish between which type is affected by which ITD
     * later, we need to do that filtering
     * @param itds set of ITDs to putsh in from a single compilation unit
     * @return array of target elements for these ITDs.
     * @throws JavaModelException
     */
    private IMember[] getTargets(List<IMember> itds) throws JavaModelException {
        AJProjectModelFacade model = getModel(itds.get(0));
        List<IMember> targets = new ArrayList<>();

        for (IMember itd : itds) {
            List<IJavaElement> elts;
            AJRelationshipType relationship = itd instanceof IAspectJElement && ((IAspectJElement) itd).getAJKind().isDeclareAnnotation() ?
                    AJRelationshipManager.ANNOTATES :
                        // either an ITD or an ITIT (IType)
                        AJRelationshipManager.DECLARED_ON;
            elts = model.getRelationshipsForElement(itd, relationship);

            for (IJavaElement elt : elts) {
                targets.add((IMember) elt);
            }
        }
        return targets.toArray(new IMember[0]);
    }

    private boolean isEmptyEdit(TextEdit edit) {
        return edit.getClass() == MultiTextEdit.class && !edit.hasChildren();
    }

    private boolean isCUnitContainingITD(ICompilationUnit unit, IMember itd) {
        return itd != null && itd.getCompilationUnit().equals(unit);
    }
}
