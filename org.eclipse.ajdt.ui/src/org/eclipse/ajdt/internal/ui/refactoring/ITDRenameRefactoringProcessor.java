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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aspectj.asm.IProgramElement.Kind;
import org.eclipse.ajdt.core.ReflectionUtils;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor;
import org.eclipse.jdt.core.search.FieldDeclarationMatch;
import org.eclipse.jdt.core.search.FieldReferenceMatch;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.MethodDeclarationMatch;
import org.eclipse.jdt.core.search.MethodReferenceMatch;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CuCollectingSearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.base.ReferencesInBinaryContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.rename.JavaRenameProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameModifications;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.JavaConventionsUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.refactoring.RefactoringSaveHelper;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameArguments;
import org.eclipse.ltk.internal.core.refactoring.Messages;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * @author Andrew Eisenberg
 * @created May 21, 2010
 *
 */
public class ITDRenameRefactoringProcessor extends JavaRenameProcessor {

    public static final String REFACTORING_ID = "org.eclipse.ajdt.ui.renameITD";


    // The target of the refactoring
    private IntertypeElement itd;

    // we want to rename overriders of ITD methods
    private Set<IMember> elementsToRename;

    // either itd field or method
    private Kind itdKind;

    // the mock element declared in the target type
    private IMember mockElement;

    // The ITD qualifier (may be simple type name or fully qualifed,
    // depending on what is in the text)
    private String qualifier;

    private final TextChangeManager changeManager;

    // If true, then references will be renamed as well
    private boolean updateReferences;

    // group of compilations units that contain references
    private SearchResultGroup[] references;

    public ITDRenameRefactoringProcessor(IntertypeElement itd, RefactoringStatus status) {
        this.itd = itd;
        changeManager = new TextChangeManager(true);
        updateReferences = true;
        try {
            itdKind = itd.getAJKind();
        } catch (JavaModelException e) {
            status.merge(RefactoringStatus.createFatalErrorStatus("Problem accessing the AspectJ model", createErrorContext()));
        }
    }

    public ITDRenameRefactoringProcessor(JavaRefactoringArguments arguments,
            RefactoringStatus status) {
        RefactoringStatus initializeStatus= initialize(arguments);
        status.merge(initializeStatus);
        changeManager = new TextChangeManager(true);
        try {
            itdKind = itd.getAJKind();
        } catch (JavaModelException e) {
            status.merge(RefactoringStatus.createFatalErrorStatus("Problem accessing the AspectJ model", createErrorContext()));
        }
    }

    private RefactoringStatus initialize(JavaRefactoringArguments extended) {
        final String handle= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
        if (handle != null) {
            final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(extended.getProject(), handle, false);
            if (element == null || !element.exists() || ! (element instanceof IntertypeElement)) {
                return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getProcessorName(), IJavaRefactorings.RENAME_FIELD);
            } else {
                itd= (IntertypeElement) element;
            }
        } else {
            return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT));
        }
        final String name= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME);
        if (name != null && !"".equals(name)) {  //$NON-NLS-1$
            setNewElementName(name);
        } else {
            return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME));
        }
        final String references= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_REFERENCES);
        if (references != null) {
            updateReferences= Boolean.parseBoolean(references);
        } else {
            return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_REFERENCES));
        }
        return new RefactoringStatus();
    }

    public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        checkCanceled(pm);
        mockElement = itd.createMockDeclaration();

        if (mockElement == null) {
            return RefactoringStatus.createFatalErrorStatus("AspectJ model not available for this ITD, do a full project build and try again.",
                    createErrorContext());
        }
        qualifier = itd.getElementName().substring(0,
                itd.getElementName().length()
                        - mockElement.getElementName().length());
        if (qualifier == null || qualifier.length() == 0) {
            return RefactoringStatus.createFatalErrorStatus("Invalid ITD qualifier", createErrorContext());
        }

        RefactoringStatus result = Checks.checkAvailability(itd);
        if (result.hasFatalError()) {
            return result;
        }
        result.merge(Checks.checkIfCuBroken(itd));
        checkCanceled(pm);
        return result;
    }

    private RefactoringStatusContext createErrorContext() {
        return JavaStatusContext.create(itd);
    }

    protected RefactoringStatus doCheckFinalConditions(IProgressMonitor pm,
            CheckConditionsContext context) throws CoreException,
            OperationCanceledException {
        try{
            pm.beginTask("", 18); //$NON-NLS-1$
            pm.setTaskName(RefactoringCoreMessages.RenameFieldRefactoring_checking);
            RefactoringStatus result= new RefactoringStatus();

            // cannot rename if current AJCU is broken
            result.merge(Checks.checkIfCuBroken(itd));
            if (result.hasFatalError()) {
                return result;
            }
            checkCanceled(pm);

            // ensure new name is OK
            result.merge(checkNewElementName(getNewElementName()));
            pm.worked(1);

            // field specific checks
            if (itdKind == Kind.INTER_TYPE_FIELD) {
                result.merge(checkEnclosingHierarchy((IField) mockElement));
                result.merge(checkNestedHierarchy(mockElement.getDeclaringType()));
            }
            checkCanceled(pm);
            pm.worked(1);
            pm.worked(1);


            if (updateReferences){
                pm.setTaskName(RefactoringCoreMessages.RenameFieldRefactoring_searching);
                // find all occurrences of renamed element.  This will include the declaration
                // original declaration as well as declarations and references of ripple
                // methods if ITD Method decl.
                references= getOccurrences(new SubProgressMonitor(pm, 3), result);
                pm.setTaskName(RefactoringCoreMessages.RenameFieldRefactoring_checking);
            } else {
                // currently, no way to disable updateReferences, so this will never be hit
                references= new SearchResultGroup[0];
                pm.worked(3);
            }
            checkCanceled(pm);

            if (updateReferences) {
                // warn if any compilation units are broken
                result.merge(analyzeAffectedCompilationUnits());
                // check renaming of ripple methods
                result.merge(checkRelatedElements());
            } else {
                Checks.checkCompileErrorsInAffectedFile(result, itd.getResource());
            }
            checkCanceled(pm);

            // create all changes
            result.merge(createChanges(new SubProgressMonitor(pm, 10)));
            if (result.hasFatalError())
                return result;

            return result;
        } finally{
            pm.done();
        }
    }



    private RefactoringStatus createChanges(IProgressMonitor pm) throws CoreException {
        pm.beginTask(RefactoringCoreMessages.RenameFieldRefactoring_checking, 10);
        RefactoringStatus result= new RefactoringStatus();
        changeManager.clear();

        addOccurrenceUpdates(new SubProgressMonitor(pm, 1));
        // can't do this since AspectJ does not do reconcling
//            result.merge(analyzeRenameChanges(new SubProgressMonitor(pm, 2)));
        if (result.hasFatalError())
            return result;

        pm.done();
        return result;
    }



    private void addDeclarationUpdate(IMember member) throws CoreException {
        ISourceRange nameRange= member.getNameRange();
        TextEdit textEdit= new ReplaceEdit(nameRange.getOffset(), nameRange.getLength(), extractRawITDName(getNewElementName()));
        ICompilationUnit cu= member.getCompilationUnit();
        String groupName= itdKind == Kind.INTER_TYPE_FIELD ?
                RefactoringCoreMessages.RenameFieldRefactoring_Update_field_declaration :
                RefactoringCoreMessages.RenameMethodRefactoring_update_declaration;

        addTextEdit(changeManager.get(cu), groupName, textEdit);
    }

    private void addTextEdit(TextChange change, String groupName, TextEdit textEdit) {
        TextChangeCompatibility.addTextEdit(change, groupName, textEdit);
    }


    private SearchResultGroup[] getOccurrences(IProgressMonitor pm, RefactoringStatus status) throws CoreException{

        String binaryRefsDescription= Messages.format(RefactoringCoreMessages.ReferencesInBinaryContext_ref_in_binaries_description , BasicElementLabels.getJavaElementName(getCurrentElementName()));
        ReferencesInBinaryContext binaryRefs= new ReferencesInBinaryContext(binaryRefsDescription);

        // must include ripple methods if this is an ITD method
        initializeElementsToRename(new SubProgressMonitor(pm, 1), binaryRefs);
        pm.setTaskName(RefactoringCoreMessages.RenameMethodRefactoring_taskName_searchingForReferences);

        SearchResultGroup[] result= RefactoringSearchEngine.search(createSearchPattern(), createRefactoringScope(),
                new CuCollectingSearchRequestor(binaryRefs), pm, status);
        binaryRefs.addErrorIfNecessary(status);

        return result;
    }

    /**
     * Checks the ripple methods to make sure they can validly be renamed.
     */
    private RefactoringStatus checkRelatedElements() throws CoreException {
        RefactoringStatus result= new RefactoringStatus();
        if (itdKind == Kind.INTER_TYPE_FIELD) {
            return result;
        }
        for (IMember member : elementsToRename) {
            if (! (member instanceof IMethod)) {
                result.merge(RefactoringStatus.createErrorStatus("Related element is not a method.", JavaStatusContext.create(member)));
            }
            IMethod method = (IMethod) member;

            result.merge(Checks.checkIfConstructorName(method, getNewElementName(), method.getDeclaringType().getElementName()));

            String[] msgData= new String[]{BasicElementLabels.getJavaElementName(method.getElementName()), BasicElementLabels.getJavaElementName(method.getDeclaringType().getFullyQualifiedName('.'))};
            if (! method.exists()){
                result.addFatalError(Messages.format(RefactoringCoreMessages.RenameMethodRefactoring_not_in_model, msgData));
                continue;
            }
            if (method.isBinary())
                result.addFatalError(Messages.format(RefactoringCoreMessages.RenameMethodRefactoring_no_binary, msgData));
            if (method.isReadOnly())
                result.addFatalError(Messages.format(RefactoringCoreMessages.RenameMethodRefactoring_no_read_only, msgData));
            if (JdtFlags.isNative(method))
                result.addError(Messages.format(RefactoringCoreMessages.RenameMethodRefactoring_no_native_1, msgData));
        }
        return result;
    }


    private IJavaSearchScope createRefactoringScope() throws CoreException{
        return RefactoringScopeFactory.create(itd, true, false);
    }

    private SearchPattern createSearchPattern() {
        HashSet<IMember> members= new HashSet<>(elementsToRename);
        IMember[] ms= members.toArray(new IMethod[0]);
        return RefactoringSearchEngine.createOrPattern(ms, IJavaSearchConstants.ALL_OCCURRENCES);
    }

    private void addOccurrenceUpdates(IProgressMonitor pm) throws CoreException {
        pm.beginTask("", references.length); //$NON-NLS-1$
        String editName= itdKind == Kind.INTER_TYPE_FIELD ?
                RefactoringCoreMessages.RenameFieldRefactoring_Update_field_reference :
                RefactoringCoreMessages.RenameMethodRefactoring_update_occurrence;
      for (SearchResultGroup reference : references) {

        ICompilationUnit cu = reference.getCompilationUnit();
        if (cu == null) {
          continue;
        }
        SearchMatch[] matches = reference.getSearchResults();
        for (SearchMatch match : matches) {
          if (match instanceof MethodReferenceMatch) {
            addTextEdit(changeManager.get(cu), editName, createTextChange(match));
          }
          else if (match instanceof MethodDeclarationMatch) {
            addDeclarationUpdate((IMember) match.getElement());
          }
          else if (match instanceof FieldReferenceMatch) {
            addTextEdit(changeManager.get(cu), editName, createTextChange(match));
          }
          else if (match instanceof FieldDeclarationMatch) {
            addDeclarationUpdate((IMember) match.getElement());
          }
          pm.worked(1);
        }
      }
    }

    private TextEdit createTextChange(SearchMatch match) {
        String rawITDName = extractRawITDName(itd.getElementName());
        return new ReplaceEdit(match.getOffset(), rawITDName.length(), extractRawITDName(getNewElementName()));
    }

    /*
     * (non java-doc)
     * Analyzes all compilation units in which type is referenced
     */
    private RefactoringStatus analyzeAffectedCompilationUnits() throws CoreException{
        RefactoringStatus result= new RefactoringStatus();
        references= Checks.excludeCompilationUnits(references, result);
        if (result.hasFatalError())
            return result;

        result.merge(Checks.checkCompileErrorsInAffectedFiles(references));
        return result;
    }


    private RefactoringStatus checkNestedHierarchy(IType type) throws CoreException {
        IType[] nestedTypes= type.getTypes();
        if (nestedTypes == null)
            return null;
        RefactoringStatus result= new RefactoringStatus();
      for (IType nestedType : nestedTypes) {
        IField otherField = nestedType.getField(getNewElementName());
        if (otherField.exists()) {
          String msg = Messages.format(
            RefactoringCoreMessages.RenameFieldRefactoring_hiding,
            new String[] { BasicElementLabels.getJavaElementName(itd.getElementName()), BasicElementLabels.getJavaElementName(getNewElementName()), BasicElementLabels.getJavaElementName(nestedType.getFullyQualifiedName('.')) });
          result.addWarning(msg, JavaStatusContext.create(otherField));
        }
        result.merge(checkNestedHierarchy(nestedType));
      }
        return result;
    }


    private RefactoringStatus checkEnclosingHierarchy(IField field) {
        IType current= field.getDeclaringType();
        if (Checks.isTopLevel(current))
            return null;
        RefactoringStatus result= new RefactoringStatus();
        while (current != null){
            IField otherField= current.getField(getNewElementName());
            if (otherField.exists()){
                String msg= Messages.format(RefactoringCoreMessages.RenameFieldRefactoring_hiding2,
                    new String[]{ BasicElementLabels.getJavaElementName(getNewElementName()), BasicElementLabels.getJavaElementName(current.getFullyQualifiedName('.')), BasicElementLabels.getJavaElementName(otherField.getElementName())});
                result.addWarning(msg, JavaStatusContext.create(otherField));
            }
            current= current.getDeclaringType();
        }
        return result;
    }


    /**
     * first check to make sure that the qualifier has not changed, then perform
     * checks for either a method or a field
     */
    public RefactoringStatus checkNewElementName(String newName)
            throws CoreException {
        RefactoringStatus status = new RefactoringStatus();
        status.merge(checkITDQualifier(newName));
        if (status.getSeverity() != RefactoringStatus.OK) {
            return status;
        }

        if (Checks.isAlreadyNamed(itd, newName)) {
            status.addFatalError(
                    RefactoringCoreMessages.RenameMethodRefactoring_same_name,
                    createErrorContext());
        }

        String rawName = extractRawITDName(newName);
        status.merge(Checks.checkName(rawName, JavaConventionsUtil.validateMethodName(rawName, itd)));
        if (status.isOK() && !Checks.startsWithLowerCase(rawName))
            status= RefactoringStatus.createWarningStatus(RefactoringCoreMessages.Checks_method_names_lowercase);


        if (itdKind == Kind.INTER_TYPE_FIELD) {
            if (mockElement.getDeclaringType().getField(rawName).exists())
                status.addError(RefactoringCoreMessages.RenameFieldRefactoring_field_already_defined,
                        JavaStatusContext.create(mockElement.getDeclaringType().getField(rawName)));
        }
        return status;
    }

    private String extractRawITDName(String newName) {
        String[] split = newName.split("\\.");
        return split.length > 1 ? split[split.length-1] : newName;
    }

    private RefactoringStatus checkITDQualifier(String newName) {
        if (! newName.startsWith(qualifier)) {
            return RefactoringStatus.createFatalErrorStatus("ITD qualifier may not be changed during rename.",
                    createErrorContext());
        }
        return new RefactoringStatus();
    }

    protected RenameModifications computeRenameModifications()
            throws CoreException {
        RenameModifications result= new RenameModifications();
        RenameArguments args= new RenameArguments(getNewElementName(), getUpdateReferences());
        for (IMember element : elementsToRename) {
            if (element instanceof IMethod) {
                result.rename((IMethod) element, args);
            } else if (element instanceof IField) {
                // shouldn't happen since ITDs do not implement IField
                result.rename((IField) element, args);
            }
        }
        return result;
    }

    private void initializeElementsToRename(IProgressMonitor pm, ReferencesInBinaryContext binaryRefs) throws CoreException {
        if (elementsToRename == null && itdKind == Kind.INTER_TYPE_METHOD) {
            IMethod[] rippleMethods= RippleMethodFinder2.getRelatedMethods(itd, binaryRefs, pm, null);
            elementsToRename = new HashSet<>(Arrays.asList(rippleMethods));
            elementsToRename.add(itd);
        } else {
            elementsToRename = Collections.singleton(itd);
        }
    }


    protected String[] getAffectedProjectNatures() throws CoreException {
        return JavaProcessors.computeAffectedNatures(itd);
    }

    protected IFile[] getChangedFiles() throws CoreException {
        return ResourceUtil.getFiles(changeManager.getAllCompilationUnits());
    }

    public int getSaveMode() {
        return RefactoringSaveHelper.SAVE_REFACTORING;
    }

    private void checkCanceled(IProgressMonitor pm) {
        if (pm.isCanceled()) {
            throw new OperationCanceledException();
        }
    }

    public Change createChange(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        try {
            final TextChange[] changes= changeManager.getAllChanges();
            final List<TextChange> list= new ArrayList<>(changes.length);
            list.addAll(Arrays.asList(changes));
            return new DynamicValidationRefactoringChange(createDescriptor(), "Rename Intertype Declaration", list.toArray(new Change[0]));
        } finally {
            pm.done();
        }
    }

    private RenameJavaElementDescriptor createDescriptor() {
        String project= null;
        IJavaProject javaProject= itd.getJavaProject();
        if (javaProject != null)
            project= javaProject.getElementName();
        int flags= RefactoringDescriptor.STRUCTURAL_CHANGE;
        try {
            if (!Flags.isPrivate(itd.getFlags()))
                flags|= RefactoringDescriptor.MULTI_CHANGE;
        } catch (JavaModelException ignored) {
        }
        final String description= Messages.format("Rename intertype declaration ''{0}''", BasicElementLabels.getJavaElementName(itd.getElementName()));
        final String header= Messages.format("Rename intertype declaration ''{0}'' to ''{1}''", new String[] { JavaElementLabels.getTextLabel(itd, JavaElementLabels.ALL_FULLY_QUALIFIED), BasicElementLabels.getJavaElementName(getNewElementName())});
        final String comment= new JDTRefactoringDescriptorComment(project, this, header).asString();
        // must start with an invalid refactoring ID since the constructor does a legality check.`
        final RenameJavaElementDescriptor descriptor= RefactoringSignatureDescriptorFactory.createRenameJavaElementDescriptor(IJavaRefactorings.RENAME_METHOD);
        ReflectionUtils.setPrivateField(RefactoringDescriptor.class, "fRefactoringId", descriptor, REFACTORING_ID);
        descriptor.setProject(project);
        descriptor.setDescription(description);
        descriptor.setComment(comment);
        descriptor.setFlags(flags);
        descriptor.setJavaElement(itd);
        descriptor.setNewName(getNewElementName());
        descriptor.setUpdateReferences(updateReferences);
        return descriptor;
    }

    public Object[] getElements() {
        return new Object[] { itd };
    }

    public String getIdentifier() {
        return "org.eclipse.ajdt.ui.refactoring.rename.itd";
    }

    public String getProcessorName() {
        return "Rename an Intertype Declaration";
    }

    public boolean isApplicable() throws CoreException {
        return itdKind == Kind.INTER_TYPE_FIELD || itdKind == Kind.INTER_TYPE_METHOD;
    }

    public String getCurrentElementName() {
        return itd.getElementName();
    }

    public Object getNewElement() throws CoreException {
        return new Object[] {
                IntertypeElement.create(itd.getJemDelimeter(), itd.getParent(), getNewElementName(), itd.getParameterTypes())
        };
    }

    public final void setUpdateReferences(boolean update) {
        updateReferences= update;
    }

    public boolean getUpdateReferences() {
        return updateReferences;
    }


}
