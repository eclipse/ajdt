/*******************************************************************************
 * Copyright (c) 2010 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial version
 *******************************************************************************/
package org.eclipse.ajdt.internal.core.refactoring;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.aspectj.asm.IProgramElement.Kind;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.core.search.JavaSearchScope;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextEditChangeGroup;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

/**
 * @author Andrew Eisenberg
 * @created Apr 22, 2010
 * fields that have ITDs that are getters or setters are
 * renamed with this participant
 */
public class ITDAccessorRenameParticipant extends RenameParticipant {

    private IField field;

    private AJCompilationUnit aspectDeclaringGetter;
    private AJCompilationUnit aspectDeclaringSetter;

    private boolean useIsForBooleanGetter = false;

    /**
     * Although I don't like referring to Roo from
     * within AJDT, we need a way to disable the renaming of
     * the Aspect that declares the ITDs since Roo will
     * automatically regenerate these files anyway.
     */
    private boolean disableITDUpdatingForRoo;

    @Override
    public RefactoringStatus checkConditions(IProgressMonitor pm,
            CheckConditionsContext context) throws OperationCanceledException {
        return new RefactoringStatus();
    }

    @Override
    public String getName() {
        return "Rename occurrences of intertype getters and setters";
    }

    @Override
    protected boolean initialize(Object element) {
        if (element instanceof IField) {
            field = (IField) element;
            if (AspectJPlugin.isAJProject(field.getJavaProject().getProject())) {
                disableITDUpdatingForRoo = shouldDisableITDUpdatingForRoo();
                return true;
            }
        }
        return false;
    }

    @Override
    public Change createChange(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        boolean shouldRenameGetter = shouldRename(true);
        boolean shouldRenameSetter = shouldRename(false);
        boolean shouldRenamePrivateField = shouldRenamePrivateField();


        if (! shouldRenameGetter && ! shouldRenameSetter && ! shouldRenamePrivateField) {
            return null;
        }


        IType declaring = field.getDeclaringType();
        AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(declaring);
        if (model.hasModel()) {
            List<IJavaElement> itds = model.getRelationshipsForElement(declaring, AJRelationshipManager.ASPECT_DECLARATIONS);
            IntertypeElement getter = null, setter = null;
            for (IJavaElement elt : itds) {
                if (shouldRenameGetter && isGetter(elt)) {
                    getter = (IntertypeElement) elt;
                    aspectDeclaringGetter = (AJCompilationUnit) getter.getCompilationUnit();
                } else if (shouldRenameSetter && isSetter(elt)) {
                    setter = (IntertypeElement) elt;
                    aspectDeclaringSetter = (AJCompilationUnit) setter.getCompilationUnit();
                }
            }
            if (getter == null && setter == null) {
                return null;
            }

            // do a search for the getter
            List<SearchMatch> getterReferences;
            if (getter != null) {
                getterReferences = findReferences(getter);
            } else {
                getterReferences = Collections.emptyList();
            }
            // do a search for the setter
            List<SearchMatch> setterReferences;
            if (setter != null) {
                setterReferences = findReferences(setter);
            } else {
                setterReferences = Collections.emptyList();
            }

            // now look for private field references in Aspects
            List<SearchMatch> privateFieldReferences;
            if (shouldRenamePrivateField) {
                privateFieldReferences = findPrivateAspectReferences();
            } else {
                privateFieldReferences = Collections.emptyList();
            }

            CompositeChange change = new CompositeChange(getName());
            createDeclarationChange(getter, change, getOldGetterName(useIsForBooleanGetter), getNewGetterName(useIsForBooleanGetter));
            createDeclarationChange(setter, change, getOldSetterName(), getNewSetterName());
            createMatchedChanges(privateFieldReferences, change, field.getElementName(), getArguments().getNewName());
            createMatchedChanges(getterReferences, change, getOldGetterName(useIsForBooleanGetter), getNewGetterName(useIsForBooleanGetter));
            createMatchedChanges(setterReferences, change, getOldSetterName(), getNewSetterName());
            if (change.getChildren().length > 0) {
                return change;
            }

        }
        return null;
    }

    private List<SearchMatch> findPrivateAspectReferences() {
        List<SearchMatch> maybeMatches = findReferences(field);
        // now remove all matches that are not inside of aspects
        // We could be checking for privileged aspects here, but
        // I think it is better to be more general.
        for (Iterator<SearchMatch> matchIter = maybeMatches.iterator(); matchIter.hasNext();) {
            SearchMatch maybeMatch = matchIter.next();
            if (maybeMatch.getElement() instanceof IMember) {
                IMember member = (IMember) maybeMatch.getElement();
                if (! (member.getAncestor(IJavaElement.TYPE) instanceof AspectElement)) {
                    matchIter.remove();
                }
            }
        }
        return maybeMatches;
    }

    private void createMatchedChanges(List<SearchMatch> references,
            CompositeChange finalChange, String oldName, String newName) {
        for (SearchMatch searchMatch : references) {
            Object elt = searchMatch.getElement();
            if (elt instanceof IMember) {
                addChange(finalChange, (IMember) elt, searchMatch.getOffset(), oldName.length(), newName);
            }
        }
    }

    private void createDeclarationChange(IntertypeElement accessor,
            CompositeChange finalChange, String oldName, String newName) throws JavaModelException {
        if (accessor == null) {
            return;
        }
        ISourceRange region = accessor.getNameRange();
        addChange(finalChange, accessor, region.getOffset()+region.getLength()-oldName.length(), oldName.length(), newName);
    }

    private void addChange(CompositeChange finalChange, IMember enclosingElement,
            int offset, int length, String newName) {
        ICompilationUnit unit = enclosingElement.getCompilationUnit();
        if (disableITDUpdatingForRoo && unit != null && (unit.equals(aspectDeclaringGetter) || unit.equals(aspectDeclaringSetter))) {
            return;
        }

        CompilationUnitChange existingChange = findOrCreateChange(
                enclosingElement, finalChange);
        TextEditChangeGroup[] groups = existingChange.getTextEditChangeGroups();
        TextEdit occurrenceEdit = new ReplaceEdit(offset, length, newName);

        boolean isOverlapping = false;
        for (TextEditChangeGroup group : groups) {
            if (group.getTextEdits()[0].covers(occurrenceEdit)) {
                isOverlapping = true;
                break;
            }
        }
        if (isOverlapping) {
            // don't step on someone else's feet
            return;
        }
        existingChange.addEdit(occurrenceEdit);
        existingChange.addChangeGroup(new TextEditChangeGroup(existingChange,
                new TextEditGroup("Update ITD accessor occurrence", occurrenceEdit)));

    }

    private CompilationUnitChange findOrCreateChange(IMember accessor,
            CompositeChange finalChange) {
        TextChange textChange = getTextChange(accessor.getCompilationUnit());
        CompilationUnitChange existingChange = null;
        if (textChange instanceof CompilationUnitChange) {
            // check to see if change exists from some other part of the refactoring
            existingChange = (CompilationUnitChange) textChange;
        } else {

            // check to see if we have already touched this file
            Change[] children = finalChange.getChildren();
            for (Change change : children) {
                if (change instanceof CompilationUnitChange) {
                    if (((CompilationUnitChange) change).getCompilationUnit().equals(accessor.getCompilationUnit())) {
                        existingChange = (CompilationUnitChange) change;
                        break;
                    }
                }
            }
        }


        if (existingChange == null) {
            // nope...must create a new change
            existingChange = new CompilationUnitChange("ITD accessor renamings for " + accessor.getCompilationUnit().getElementName(), accessor.getCompilationUnit());
            existingChange.setEdit(new MultiTextEdit());
            finalChange.add(existingChange);
        }
        return existingChange;
    }

    private List<SearchMatch> findReferences(IMember accessor) {
        SearchPattern pattern = SearchPattern.createPattern(accessor, IJavaSearchConstants.REFERENCES);
        SearchEngine engine = new SearchEngine();
        JavaSearchScope scope = new JavaSearchScope(false);
        try {
            scope.add(accessor.getJavaProject());
            CollectingSearchRequestor requestor = new CollectingSearchRequestor();
            engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                    scope, requestor, new NullProgressMonitor());
            return requestor.getResults();
        }
        catch (CoreException ignored) {
        }

      return Collections.emptyList();
    }

    private boolean isSetter(IJavaElement elt) throws JavaModelException {
        if (elt instanceof IntertypeElement &&
                elt.getElementName().endsWith("." + getOldSetterName())) {
            IntertypeElement itd = (IntertypeElement) elt;
            String[] parameterTypes = itd.getParameterTypes();
          return itd.getAJKind() == Kind.INTER_TYPE_METHOD &&
                 parameterTypes != null &&
                 parameterTypes.length == 1 &&
                 parameterTypes[0].equals(field.getTypeSignature()) &&
                 itd.getReturnType().equals(Signature.SIG_VOID);
        }
        return false;
    }

    private boolean isGetter(IJavaElement elt) throws JavaModelException {
        boolean nameMatch = false;
        boolean isFound = false;
        if (elt instanceof IntertypeElement &&
                elt.getElementName().endsWith("." + getOldGetterName(false))) {
            nameMatch = true;
        } else if (elt instanceof IntertypeElement &&
                elt.getElementName().endsWith("." + getOldGetterName(true))) {
            nameMatch = true;
        }
        if (nameMatch) {
            useIsForBooleanGetter = isFound;
            IntertypeElement itd = (IntertypeElement) elt;
            String[] parameterTypes = itd.getParameterTypes();
          return itd.getAJKind() == Kind.INTER_TYPE_METHOD &&
                 (parameterTypes == null ||
                  parameterTypes.length == 0) &&
                 itd.getReturnType().equals(field.getTypeSignature());
        }
        return false;
    }

    private String getNewSetterName() {
        return accessorName("set", getArguments().getNewName());
    }
    private String getNewGetterName(boolean useIsForBoolean) {
        return accessorName("get", getArguments().getNewName());
    }
    private String getOldSetterName() {
        return accessorName("set", field.getElementName());
    }
    private String getOldGetterName(boolean useIsForBoolean) {
        return accessorName(useIsForBoolean ? "is" : "get", field.getElementName());
    }

    private String accessorName(String prefix, String name) {
        return prefix + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Privileged aspects can see private fields.  But, they
     * are not renamed in the RenameFieldProcessor, so must check
     * here to see if we need to do extra logic to rename in an
     * aspect
     * @throws JavaModelException
     */
    private boolean shouldRenamePrivateField() throws JavaModelException {
        return Flags.isPrivate(field.getFlags());
    }

    private Field fRenameGetterField;

    private Field fRenameSetterField;

    private boolean shouldRename(boolean getter) {
        try {
            RefactoringProcessor processor = getProcessor();
            if (! (processor.getClass().getCanonicalName().equals("org.eclipse.jdt.internal.corext.refactoring.rename.RenameFieldProcessor"))) {
                return false;
            }
            Field thisField;
            if (getter) {
                if (fRenameGetterField == null) {
                    fRenameGetterField = processor.getClass().getDeclaredField("fRenameGetter");
                    fRenameGetterField.setAccessible(true);
                }
                thisField = fRenameGetterField;
            } else {
                if (fRenameSetterField == null) {
                    fRenameSetterField = processor.getClass().getDeclaredField("fRenameSetter");
                    fRenameSetterField.setAccessible(true);
                }
                thisField = fRenameSetterField;
            }
            return thisField.getBoolean(processor);
        } catch (Exception ignored) {
        }
        return false;
    }


    /**
     * if this is a roo project and roo is installed in the system.
     */
    private boolean shouldDisableITDUpdatingForRoo() {
        try {
            return field.getJavaProject().getProject().hasNature("com.springsource.sts.roo.core.nature") &&
                (
                        Platform.getBundle("com.springsource.sts.roo.core") != null ||
                        Platform.getBundle("rg.springframework.ide.eclipse.roo.core") != null
                        );
        } catch (CoreException e) {
            return false;
        }
    }
}
