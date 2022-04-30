/*******************************************************************************
 * Copyright (c) 2010 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Kris De Volder - initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.refactoring.pullout;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.ajdt.core.javaelements.SourceRange;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.manipulation.ImportReferencesCollector;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaStatusContext;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public class PullOutRefactoring extends Refactoring {

    /**
     * An instance of this class pulls together all required info for
     * the rewriting of the aspect and handles the rewriting.
     */
    class AspectRewrite {

        /**
         * Collects the text of all ITDs.
         */
        private final StringBuffer itds = new StringBuffer();

        /**
         * Collects info for and handles rewriting of imports for the target aspect.
         */
        private final ImportRewrite importRewrite;

        /**
         * This field may be set while creating the "deletion" edits to pull out ITDs.
         * This will only happen if the compilation unit from which we are pulling out
         * is the same as the target aspect's compilation unit. If set to a non-null
         * value this cuChange object should be used for recording the "insertion"
         * edits.
         */
        private CompilationUnitChange cuChange = null;

        public AspectRewrite() throws JavaModelException {
            importRewrite = ImportRewrite.create(targetAspect.getCompilationUnit(), true);
        }

        public void addITD(ITDCreator itd, RefactoringStatus status) throws JavaModelException, BadLocationException {
            itd.collectImports(importRewrite, status);
            itds.append(itd.createText());
        }


        /**
         * Create an ICompiltationUnitChange with all changes to the aspect's CU, related to the insertion
         * of ITDs. Ensure these changes are added to the allChanges object.
         */
        private void rewriteAspect(IProgressMonitor submonitor, CompositeChange allChanges) {
            try {
                CompilationUnitChange edits = getCUChange();

                // Create edit to add "privileged"
                if (isMakePrivileged() && !isPrivileged()) {
                    int start = targetAspect.getSourceRange().getOffset();
                    int nameStart = targetAspect.getNameRange().getOffset() - start;
                    String aspectText = targetAspect.getSource().substring(0,nameStart);
                    // If all is well, we now have the aspectText, upto the keywords "aspect"
                    // Caveat: for some reason AJDT has replaced this keyword by the "class" keyword.
                    int aspectKeywordStart = aspectText.lastIndexOf("class");
                    Assert.isTrue(aspectKeywordStart>=0, "The aspect keyword was not found in the aspect source");
                    aspectKeywordStart += start; // Adjust because the start of our string may not be the
                                                 // start of the compilation unit.
                    edits.addEdit(new InsertEdit(aspectKeywordStart, "privileged "));
                }

                // Create edit to the imports section of compilation unit
                if (importRewrite.hasRecordedChanges()) {
                    try {
                        edits.addEdit(importRewrite.rewriteImports(submonitor));
                    } catch (Exception e) {
                        //An aspect handles this
                    }
                }

                // Add the itds to the aspect
                edits.addEdit(new InsertEdit(getInsertLocation(), itds.toString()));

                if (edits.getParent()==null) {
                    allChanges.add(edits);
                }
                else {
                    //If not null, it means we already added it, because the aspect is
                    //in the same CU as some pulled out members.
                }
            } catch (JavaModelException ignored) {
            }
        }

        /**
         * Get the CompilationUnitChange object that should be used to record the changes related to
         * inserting ITDs into the aspect. The object is created if necessary, or reused if it
         * already exists.
         */
        private CompilationUnitChange getCUChange() {
            if (cuChange==null) {
                cuChange = newCompilationUnitChange(getAspect().getCompilationUnit());
                cuChange.setEdit(new MultiTextEdit()); // root element must be set, or we can't add edits!
            }
            return cuChange;
        }

        public void setCUChange(CompilationUnitChange cuChange) {
            this.cuChange = cuChange;
        }

    }

    /**
     * Helper class to create ITD text from a member. An instance of this class is created
     * to provide a working area in which to build the ITD text.
     * <p>
     * This class was introduced to help manage the complexity of context information that
     * was getting passed along with various helper methods that break down the creation of an ITD
     * into smaller steps. This was starting to result in extremely long argument lists.
     * <p>
     * This class keeps all that information in one convenient place. It also provides a nice
     * high-level interface to manipulate the properties of the created ITD, while encapsulating
     * the messier parts of the rewriting and text manipulation code inside the class.
     *
     * @author kdvolder
     */
    static class ITDCreator {

        private static final int VISIBILITY_MODIFIERS = Modifier.PRIVATE | Modifier.PUBLIC | Modifier.PROTECTED;

        /**
         * The original member from which we create the ITD
         */
        private IMember member;

        /**
         * The AST node corresponding to the original member.
         */
        private BodyDeclaration memberNode;

        /**
         * We place the text of the original member in this document, so that
         * we can accumulate and apply edits against the document to create
         * the final ITD text.
         */
        private IDocument memberText;

        /**
         * Rather than applying edits immeditiately, we accumulate them in here, this is so that
         * we don't end up destroying position information before we have figured out
         * all the edits to apply to the original text.
         */
        private MultiTextEdit edits = new MultiTextEdit();

        /**
         * Modifiers that should be removed when we rewrite the ITD's modifiers.
         * This is a "bitfield". See {@link Modifier} for the meaning of the bits.
         */
        private int deleteMods = 0;

        /**
         * Either an empty String, or a String containing all the modifiers to add, separated
         * by spaces and with one trailing space.
         */
        private String insertMods = "";

        /**
         * The name of the declaring type, as it should be written in the aspect context (i.e. as
         * a simple name, or a fully qualified name, depending on whether it could be imported.
         * <p>
         * This field is initialized during "collectImports".
         */
        private String declaringTypeRef = null;

        /**
         * The ITD creator requires an IMember and its corresponding AST node, to be
         * able to perform its work of creating the ITD text.
         *
         * @param member
         * @param memberNode
         * @throws JavaModelException
         */
        public ITDCreator(IMember member, BodyDeclaration memberNode) throws JavaModelException {
            this.member = member;
            this.memberNode = memberNode;
            this.memberText = new Document(getAJSource(member));
        }

        /**
         * Aspect aware method for getting source code. For elements inside an .aj file, it fetches the
         * "original" source code, not the rewritten source code. For elements in regular .java file
         * it is identical to the getSource method.
         */
        private String getAJSource(IMember member) throws JavaModelException {
            ICompilationUnit cu = member.getCompilationUnit();
            if (cu instanceof AJCompilationUnit) {
                AJCompilationUnit ajcu = (AJCompilationUnit) cu;
                ajcu.requestOriginalContentMode();
                try {
                    return member.getSource();
                }
                finally {
                    ajcu.discardOriginalContentMode();
                }
            }
            return member.getSource();
        }

        @Override
        public String toString() {
            if (memberText==null)
                return "ITDCreator(DISPOSED)";
            else {
                return "ITDCreator(----\n" +
                memberText.get()+"\n" +
                "----)";
            }
        }

        /**
         * Collect imports needed for this ITD, and add them to the aspects compilation unit's
         * importRewriter.
         * <p>
         * This also applies the necessary edits to the ITD text if some imports fail because
         * of name clashes.
         * <p>
         * If some references can't be resolved a warning is added to the refactoring satus.
         */
        public void collectImports(ImportRewrite importRewrite, RefactoringStatus status) throws JavaModelException {
            Region rangeLimit = new Region(memberNode.getStartPosition(), memberNode.getLength());

			List<SimpleName> extraType = new ArrayList<>();
			List<SimpleName> extraStatic = new ArrayList<>();

            ImportReferencesCollector.collect(memberNode, member.getJavaProject(), rangeLimit, extraType, extraStatic);

            for (Name name : extraStatic) {
                IBinding binding = name.resolveBinding();
                if (binding==null) {
                    status.addWarning("Couldn't resolve binding, imports may be incorrect", PullOutRefactoring.makeContext(member, name));
                }
                else {
                    replaceNameRef(name, importRewrite.addStaticImport(binding));
                }
            }

            for (Name name : extraType) {
                ITypeBinding binding = (ITypeBinding)name.resolveBinding();
                if (binding==null) {
                    status.addWarning("Couldn't resolve binding, imports may be incorrect", PullOutRefactoring.makeContext(member, name));
                }
                else {
                    if (binding.isParameterizedType()) {
                        // Simple names should not be treated as complete generic type references
                        binding = binding.getErasure();
                    }
                    replaceNameRef(name, importRewrite.addImport(binding));
                }
            }

            if (wasIntertype()) {
                IntertypeElement ite = (IntertypeElement) member;
                AJCompilationUnit cu = (AJCompilationUnit) ite.getCompilationUnit();
                IType targetType = ite.findTargetType();
                String typeQName = targetType!=null?targetType.getFullyQualifiedName():ite.getTargetName();
                declaringTypeRef = importRewrite.addImport(typeQName);
            }
            else {
                declaringTypeRef = importRewrite.addImport(member.getDeclaringType().getFullyQualifiedName());
            }
        }

        /**
         * @return true if the original pulled out member was *already* an ITD.
         */
        private boolean wasIntertype() {
            return member instanceof IntertypeElement;
        }

        /**
         * Add an extra textedit, if needed for updating a potentially failed import rewrite
         */
        private void replaceNameRef(Name name, String replaceStr) throws MalformedTreeException, JavaModelException {
            String orgRefText = name.getFullyQualifiedName();
            if (replaceStr.equals(orgRefText))
                return;
            edits.addChild(new ReplaceEdit(
                    name.getStartPosition()-memberStart(),
                    name.getLength(),
                    replaceStr));
        }


        /**
         * Create the necessary edits to change modifiers on the ITD as described by the
         * deleteMods and insertMods fields.
         */
        private void rewriteModifiers()
        throws BadLocationException, MalformedTreeException, JavaModelException
        {
            if (deleteMods!=0) {
                List<Modifier> mods = memberNode.modifiers();
                //String replaceText = makePublic?"public ":"";
                String replaceText = "";
                for (Modifier modifier : mods) {
                    if ( (modifier.getKeyword().toFlagValue() & deleteMods) != 0) {
                        int modStart = modifier.getStartPosition() - memberStart();
                        int modEnd = modStart + modifier.getLength();
                        if (Character.isWhitespace(memberText.getChar(modEnd))) {
                            // Delete one extra white space character for a nicer look.
                            // but only if there is one! (imagine there being a weird comment there instead)
                            modEnd++;
                        }
                        edits.addChild(new ReplaceEdit(modStart, modEnd-modStart, replaceText));
                    }
                }
            }
            if (insertMods!=null && !"".equals(insertMods)) {
                int insertPos;
                if (memberNode instanceof MethodDeclaration) {
                    MethodDeclaration methodNode = (MethodDeclaration) memberNode;
                    if (methodNode.isConstructor())
                        insertPos = methodNode.getName().getStartPosition() - memberStart();
                    else
                        insertPos = methodNode.getReturnType2().getStartPosition() - memberStart();
                }
                else if (memberNode instanceof FieldDeclaration ) {
                    FieldDeclaration fieldNode = (FieldDeclaration) memberNode;
                    insertPos = fieldNode.getType().getStartPosition() - memberStart();
                }
                else {
                    insertPos = 0;
                }
                edits.addChild(new InsertEdit(insertPos, insertMods));
            }
        }

        private int memberStart() throws JavaModelException {
            return member.getSourceRange().getOffset();
        }

        public void removeModifier(int mod) {
            this.deleteMods |= mod;
        }

        /**
         * Produce text for the ITD, by applying all requested changes to the
         * original ITD text.
         * <p>
         * This is a 'once only' operation. After the text of the ITD is created
         * this object has served its purpose and should not be used anymore.
         * @throws JavaModelException
         * @throws BadLocationException
         * @throws MalformedTreeException
         */
        public String createText() throws JavaModelException, MalformedTreeException, BadLocationException {
            try {
                int memberStart = memberStart();

                // All positions, except for memberStart itself, will be computed relative to member
                // start (since our memberText document only contains the member text)

                int memberEnd = memberText.getLength();
                int nameStart = member.getNameRange().getOffset() - memberStart;

                // Add some indentation correction to the front
                edits.addChild(
                        new InsertEdit(0, CodeFormatterUtil.createIndentString(1, member.getJavaProject())));

                // Rewrite modifiers
                rewriteModifiers();

                //Rewrite stuff in and around the name... only when original is *not* an intertype element!
                if (!wasIntertype()) {
                    // Insert declaring type reference in front of name
                    IType declaringType = member.getDeclaringType();
                    Assert.isNotNull(declaringTypeRef, "The declaring type name is computed by collectImports. Forgot to call it?");
                    StringBuilder typeName = new StringBuilder(declaringTypeRef);
                    ITypeParameter[] typeParameters = declaringType.getTypeParameters();
                    if (typeParameters !=null && typeParameters.length>0) {
                        typeName.append("<");
                        for (int i = 0; i < typeParameters.length; i++) {
                            if (i>0) typeName.append(", ");
                            typeName.append(typeParameters[i].getElementName());
                        }
                        typeName.append(">");
                    }
                    typeName.append( "." );
                    edits.addChild(new InsertEdit(nameStart, typeName.toString()));

                    // For constructors, must change name to "new"
                    if (member instanceof IMethod && ((IMethod)member).isConstructor()) {
                        edits.addChild(new ReplaceEdit(nameStart, member.getNameRange().getLength(), "new"));
                    }
                }

                // Add some newlines to the end for nicer spacing
                String newline = memberText.getLineDelimiter(0);
                if (newline==null) // We tried to use the same as in the memberText but it has none
                    newline = System.getProperty("line.separator");
                edits.addChild(new InsertEdit(memberEnd,  newline+newline));

                // applying these edits should produce the ITD text
                edits.apply(memberText, TextEdit.NONE);
                return memberText.get();
            }
            finally {
                // This object has served it's purpose and should not be reused.
                dispose();
            }
        }

        /**
         * Destroy this object. Further use of the object will probably cause NPE exception.
         */
        private void dispose() {
            member = null;
            memberNode = null;
            memberText = null;
            edits = null;
            insertMods = null;
            declaringTypeRef = null;
        }

        /**
         * Was the original member protected.
         */
        public boolean wasProtected() throws JavaModelException {
            return JdtFlags.isProtected(member);
        }

        /**
         * Was the original member public.
         */
        public boolean wasPublic() throws JavaModelException {
            return JdtFlags.isPublic(member);
        }

        /**
         * Was the original member private.
         */
        public boolean wasPrivate() throws JavaModelException {
            return JdtFlags.isPrivate(member);
        }

        /**
         * Was the original member abstract.
         */
        public boolean wasAbstract() throws JavaModelException {
            return JdtFlags.isAbstract(member);
        }

        /**
         * Was the original member "package visible" (i.e. it has no visibility
         * modifiers at all.
         */
        public boolean wasPackageVisible() throws JavaModelException {
            return JdtFlags.isPackageVisible(member);
        }

        /**
         * Get the original member from which we are creating an ITD.
         */
        public IMember getMember() {
            return member;
        }

        /**
         * Get the IJavaElement name of the original member.
         */
        public String getElementName() {
            return member.getElementName();
        }

        public ASTNode getMemberNode() {
            return memberNode;
        }

        public void addModifier(int modFlag) {
            if (isVisibilityModifier(modFlag)) {
                //When adding a visiblity modifier, make sure to remove any preexisting
                //visibility modifiers first
                deleteMods |= VISIBILITY_MODIFIERS;
            }
            ModifierKeyword toAdd = ModifierKeyword.fromFlagValue(modFlag);
            String toAddStr = toAdd.toString();
            removeModifier(modFlag);
            if (!insertMods.contains(toAddStr)) {
                insertMods += toAddStr+" ";
            }
        }

        private boolean isVisibilityModifier(int modFlag) {
            return (modFlag & VISIBILITY_MODIFIERS)!=0;
        }

        /**
         * Replace the body of the method with given text. This is really only
         * supposed to be used to add method stubs for methods that where abstract
         * before.
         */
        public void setBody(String bodyText) {
            Object bodyNode = memberNode.getStructuralProperty(MethodDeclaration.BODY_PROPERTY);
            Assert.isTrue(bodyNode==null, "There already is a method body for this member: "+getMember());
            int startPos = memberText.get().lastIndexOf(';');
            edits.addChild(new ReplaceEdit(startPos, 1, bodyText));
        }

        /**
         * Is the original member a constructor (does not include the case where the original member is
         * already an ITD, even if that ITD introduces a constructor).
         */
        public boolean wasConstructorMethod() throws JavaModelException {
            return (member instanceof IMethod) && !wasIntertype() && ((IMethod)member).isConstructor();
        }

        /**
         * Determine whether this ITD's original member (which is assumed to be a constructor) has a
         * call to 'this()'
         */
        public boolean hasThisCall() throws JavaModelException {
            Assert.isNotNull(memberNode);
            Assert.isLegal(wasConstructorMethod());
            Block body = ((MethodDeclaration)memberNode).getBody();
            if (body==null) return false;
            @SuppressWarnings("unchecked") List<Statement> stms = body.statements();
            if (stms==null || stms.size()==0) return false;
            Statement firstStm = stms.get(0);
            if (!(firstStm instanceof ConstructorInvocation)) return false;
            ConstructorInvocation call = (ConstructorInvocation) firstStm;
            // The class ConstructorInvocation only represents "this(...)" calls
            return call.arguments().isEmpty();
        }
    }

    private static final String MAKE_PRIVILEGED = "makePrivileged";
    private static final String MEMBER = "member";

    protected static final String ASPECT = "aspect";

    /**
     * The members to pull out, grouped by compilation unit for efficiency sake (
     * so we can process them one CU at a time)
     */
    private Map<ICompilationUnit, Collection<IMember>> memberMap;
    private HashSet<IMember> memberSet;

    /**
     * The target aspect to where the method should be moved.
     */
    private AspectElement targetAspect;

    /**
     * Should we make the aspect privileged
     */
    private boolean makePrivileged = false;

    /**
     * Allow pulling abstract methods. This deletes abstract keyword and generates
     * method stubs for "abstract" ITDs.
     */
    private boolean generateAbstractMethodStubs = false;

    /**
     * Allow the deletion of the protected keyword from ITDs (because this keyword
     * is not allowed on ITDs by AspectJ).
     */
    private boolean allowDeleteProtected = false;

    /**
     * Allow to make ITDs public to avoid breaking references to pulled members.
     */
    private boolean allowMakePublic;

    private IJavaProject javaProject;
    private AspectRewrite aspectChanges;

    public PullOutRefactoring() {
        clearMembers(); // initializes the member map and sets
    }

    public void addMember(IMember member, RefactoringStatus status) {
        ICompilationUnit cu = member.getCompilationUnit();
        Collection<IMember> members = getMembers(cu);
        members.add(member);
        memberSet.add(member);
        if (javaProject==null)
            javaProject = member.getJavaProject();
        else if (javaProject!=member.getJavaProject())
            status.addError("Pull-out refactoring across multiple projects is not suppored", makeContext(member));
    }

    @Override
    public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        RefactoringStatus status = new RefactoringStatus();
        SubProgressMonitor submonitor = new SubProgressMonitor(pm, memberMap.keySet().size());
        submonitor.beginTask("Checking preconditions...", memberMap.keySet().size());
        try {
            aspectChanges = new AspectRewrite();
            // For a more predictable and orderly outcome, sort by the name of the CU
            ICompilationUnit[] cus = memberMap.keySet().toArray(new ICompilationUnit[0]);
            Arrays.sort(cus, CompilationUnitComparator.the);
            for (ICompilationUnit cu : cus) {
                ASTParser parser= ASTParser.newParser(AST.JLS8);
                parser.setSource(cu);
                parser.setResolveBindings(true);
                ASTNode cuNode = parser.createAST(pm);
                for (IMember member : memberMap.get(cu)) {
                    BodyDeclaration memberNode = (BodyDeclaration) findASTNode(cuNode, member);
                    ITDCreator itd = new ITDCreator(member, memberNode);
                    if (member.getDeclaringType().isInterface()) {
                        // No need to check "isAllowMakePublic" since technically it was already public.
                        itd.addModifier(Modifier.PUBLIC);
                    }
                    if (itd.wasProtected()) {
                        if (isAllowDeleteProtected()) {
                            itd.removeModifier(Modifier.PROTECTED);
                        }
                        else {
                            status.addWarning("moved member '"+member.getElementName()+"' is protected\n" +
                                    "protected ITDs are not allowed by AspectJ.\n" ,
                                    makeContext(member));
                        }
                    }
                    if (itd.wasAbstract()) {
                        if (isGenerateAbstractMethodStubs()) {
                            itd.removeModifier(Modifier.ABSTRACT);
                            itd.setBody(getAbstractMethodStubBody(member));
                        }
                        else {
                            status.addWarning("moved member '"+member.getElementName()+"' is abstract.\n" +
                                    "abstract ITDs are not allowed by AspectJ.\n" +
                                    "You can enable the 'convert abstract methods' option to avoid this error.",
                                    makeContext(member));
                            //If you choose to ignore this error and perform refactoring anyway...
                            // We make sure the abstract keyword is added to the itd, so you will get a compile error
                            // and be forced to deal with that error.
                            itd.addModifier(Modifier.ABSTRACT);
                        }
                    }
                    checkOutgoingReferences(itd, status);
                    checkIncomingReferences(itd, status);
                    checkConctructorThisCall(itd, status);
                    aspectChanges.addITD(itd, status);
                }
                submonitor.worked(1);
            }
        } catch (BadLocationException e) {
            status.merge(RefactoringStatus.createFatalErrorStatus("Internal error:"+e.getMessage()));
        }
        finally {
            submonitor.done();
        }
        return status;
    }

    /**
     * Check whether the constructor is "safe" to pull out, or whether it might change
     * the meaning of the program (no longer executing initialiser code in target class).
     * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=318936
     */
    private void checkConctructorThisCall(ITDCreator itd,
            RefactoringStatus status) throws JavaModelException {
        if (itd.wasConstructorMethod() && !itd.hasThisCall()) {
            status.addWarning("Program semantics changed: moved '"+itd.getElementName()+"' constructor has no this() call. Initializers in the target class will not be executed " +
                    "by the intertype constructor", makeContext(itd.getMember()));
        }
    }

    /**
     * Find AST node corresponding to a given IMember.
     */
    private ASTNode findASTNode(ASTNode cuNode, IMember member)
            throws JavaModelException {
        ISourceRange range = member.getSourceRange();
        NodeFinder finder = new NodeFinder(cuNode, range.getOffset(), range.getLength());
        return finder.getCoveredNode();
        // Note: why we *have* to use getCoveredNode explicitly rather than use the
        // perform methods defined on NodeFinder.
        // See BUG 316945: Normally, we have exact positions and covering/covered are the same node.
        // but in the BUG case we should use the covered node since a JDT bug makes the source range
        // be too large.
    }

    /**
     * Check whether references to moved elements become broken. Update status message
     * accordingly (but only if allowModifierConversion is set to false).
     *
     * @return true if no references become broken
     */
    private boolean checkIncomingReferences(ITDCreator movedMember, RefactoringStatus status) throws CoreException {
        if (movedMember.wasPublic())
            return true; //Always ok if member was already public
        boolean ok = true;
        IJavaSearchScope scope= SearchEngine.createJavaSearchScope(new IJavaElement[] { javaProject });
        SearchPattern pattern= SearchPattern.createPattern(movedMember.getMember(), IJavaSearchConstants.REFERENCES);
        SearchEngine engine= new SearchEngine();
        final Set<SearchMatch> references = new HashSet<>();
        engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant()}, scope, new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) throws CoreException {
                if (match.getAccuracy() == SearchMatch.A_ACCURATE && !match.isInsideDocComment())
                    references.add(match);
            }
        }, new NullProgressMonitor());

        String referredPkg = getPackageName(targetAspect); // since the element is moved it's package *will* be...
        for (SearchMatch match : references) {
            if (match.getElement() instanceof IJavaElement) {
                IJavaElement referingElement = (IJavaElement) match.getElement();
                if (!isMoved(referingElement)) {
                    if (movedMember.wasPrivate()) {
                        ok = false;
                        if (isAllowMakePublic()) {
                            movedMember.addModifier(Modifier.PUBLIC);
                        }
                        else {
                            status.addWarning("The moved private member '"+movedMember.getElementName()+"' will not be accessible" +
                                    " after refactoring.",
                                    makeContext(match));
                        }
                    }
                    else if (movedMember.wasPackageVisible() || movedMember.wasProtected()) {
                        String referringPkg = getPackageName(referingElement);
                        if (referringPkg!=null && !referringPkg.equals(referredPkg)) {
                            ok = false;
                            if (isAllowMakePublic()) {
                                movedMember.addModifier(Modifier.PUBLIC);
                            }
                            else {
                                status.addWarning("The moved member '"+movedMember.getElementName()+"' may not be accessible " +
                                        "after refactoring",
                                        makeContext(match));
                            }
                        }
                    }
                }
            }
        }
        return ok;
    }

    /**
     * Retrieve package name of a IJavaElement.
     * @return The name of the package, or null if the IJavaElement is not nested inside a IPackagFragment.
     */
    private String getPackageName(IJavaElement el) {
        IPackageFragment pkg = (IPackageFragment) el.getAncestor(IJavaElement.PACKAGE_FRAGMENT);
        if (pkg==null) return null;
        return pkg.getElementName();
    }

    @Override
    public RefactoringStatus checkInitialConditions(IProgressMonitor monitor)
            throws CoreException, OperationCanceledException {

        RefactoringStatus status= new RefactoringStatus();
        monitor.beginTask("Checking preconditions...", 1);
        try {
            if (memberMap == null || memberMap.isEmpty())
                status.merge(RefactoringStatus.createFatalErrorStatus("No pullout targets have been specified."));
            else {
                for (ICompilationUnit cu : memberMap.keySet()) {
                    for (IMember member : memberMap.get(cu)) {
                        if (!member.exists()) {
                            status.merge(RefactoringStatus.createFatalErrorStatus(
                                    MessageFormat.format("Member ''{0}'' does not exist.",
                                      member.getElementName())));
                        }
                        else if (!isInTopLevelType(member)) {
                            status.merge(RefactoringStatus.createFatalErrorStatus(
                                    MessageFormat.format("Member ''{0}'' is not directly nested in a top-level type.",
                                      member.getElementName())));
                        }
                        else if (member.isBinary()) {
                            status.merge(RefactoringStatus.createFatalErrorStatus(
                                    MessageFormat.format("Member ''{0}'' is not in source code. Binary methods can not be refactored.",
                                      member.getElementName())));
                        }
                        else if (!member.getCompilationUnit().isStructureKnown()) {
                            status.merge(RefactoringStatus.createFatalErrorStatus(
                                    MessageFormat.format("Compilation unit ''{0}'' contains compile errors.",
                                      cu.getElementName())));
                        }
                    }
                }
            }
        } finally {
            monitor.done();
        }
        return status;
    }

    private void checkOutgoingReferences(final ITDCreator itd, final RefactoringStatus status)
            throws CoreException, OperationCanceledException {
        if (willBePrivileged())
            return; //Always OK!

        // Walk the AST to find problematic references (e.g. references to private members from within the moved method)
        itd.getMemberNode().accept(new ASTVisitor() {
            /**
             * Check for various problems that may be caused by moving a reference into
             * a different context.
             */
            private void checkReference(ASTNode node, final IBinding binding, RefactoringStatus status) {
                if (isField(binding) || isMethod(binding) || isType(binding)) {
                    if (isTypeParameter(binding))
                        return; //Exclude these, or they'll look like package restricted types in code below.
                    if (isMoved(binding))
                        return; //OK: anything moved to the aspect will be accessible from the aspect
                    int mods = binding.getModifiers();
                    if (Modifier.isPrivate(mods)) {
                        status.addWarning("private member '"+binding.getName()+"' accessed and refactored aspect is not privileged",
                                makeContext(itd.getMember(), node));
                    }
                    if (JdtFlags.isProtected(binding) || JdtFlags.isPackageVisible(binding)) {
                        // FIXKDV: separate case for protected
                        // These are really two separate cases, but the cases where this matters (i.e.
                        // aspects that have a super type are rare so I'm not dealing with that
                        // right now (this is relatively harmless: will result in a spurious warning message
                        // in rare case where the pulled member is protected and is pulled from target aspect's
                        // supertype that is not in the same package as target aspect)
                        String referredPkg = getPackageName(binding.getJavaElement());
                        if (referredPkg!=null) {
                            //If it has no package, we'll just ignore it, whatever it is, it's probably not subject to
                            // package scope :-)
                            String aspectPkg = targetAspect.getPackageFragment().getElementName();
                            if (!referredPkg.equals(aspectPkg)) {
                                String keyword = JdtFlags.isProtected(binding)?"protected":"package restricted";
                                status.addWarning(keyword+" member '"+binding.getName()+"' is accessed and refactored aspect is not privileged",
                                        makeContext(itd.getMember(), node));
                            }
                        }
                    }
                }
            }

            private boolean isField(IBinding binding) {
                return (binding instanceof IVariableBinding)
                    && ((IVariableBinding)binding).isField();
            }

            private boolean isMethod(IBinding binding) {
                return (binding instanceof IMethodBinding);
            }

            private boolean isType(IBinding binding) {
                return binding instanceof ITypeBinding;
            }

            private boolean isTypeParameter(IBinding binding) {
                return binding instanceof ITypeBinding
                  && (  ((ITypeBinding)binding).isCapture()
                     || ((ITypeBinding)binding).isTypeVariable() );
            }

            @Override
            public boolean visit(SimpleName node) {
                IBinding binding = node.resolveBinding();
                checkReference(node, binding, status);
                return true;
            }

        });
    }

    private void clearMembers() {
        memberMap = new HashMap<>();
        memberSet = new HashSet<>();
        javaProject = null;
    }

    @Override
    public Change createChange(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        try {
            pm.beginTask("Creating changes...", memberMap.keySet().size());
            CompositeChange allChanges = new CompositeChange("PullOut ITDs");
            for (ICompilationUnit cu : memberMap.keySet()) {

                // Prepare an ASTRewriter for this compilation unit
                ASTParser parser= ASTParser.newParser(AST.JLS8);
                parser.setSource(cu);
                ASTNode cuNode = parser.createAST(pm);
                MultiTextEdit cuEdits = new MultiTextEdit();

                // Apply all operations to the AST rewriter
                for (IMember member : getMembers(cu)) {
                    ISourceRange range = member.getSourceRange();
                    range = grabSpaceBefore(cu, range);
                    cuEdits.addChild(new DeleteEdit(range.getOffset(), range.getLength()));
                }

                // Create CUChange object with the accumulated deletion edits.
                CompilationUnitChange cuChanges = newCompilationUnitChange(cu);
                cuChanges.setEdit(cuEdits);

                // Add changes for this compilation unit.
                allChanges.add(cuChanges);

                pm.worked(1);
            }
            aspectChanges.rewriteAspect(pm, allChanges);
            return allChanges;
        }
        finally {
            pm.done();
        }
    }

    /**
     * For cosmetic reasons (nicer indentation of resulting text after deletion of membernode
     * we force the nodes sourcerange to include any spaces in front of the node, upto the
     * beginning of the line.
     * @param cu
     * @return
     */
    private ISourceRange grabSpaceBefore(ICompilationUnit cu, ISourceRange range) {
        try {
            IBuffer sourceText = cu.getBuffer();
            int start = range.getOffset();
            int len = range.getLength();
            while (start>0 && isSpace(sourceText.getChar(start-1))) {
                start--; len++;
            }
            return new SourceRange(start, len);
        } catch (JavaModelException e) {
            //This operation is not essential, so it is fine if it silently fails.
            return range;
        }
    }

    private boolean isSpace(char c) {
        return c==' '||c=='\t';
    }

    private CompilationUnitChange newCompilationUnitChange(ICompilationUnit cu) {
        CompilationUnitChange cuChange = new CompilationUnitChange("PullOut ITDs", cu);
        if (targetAspect.getCompilationUnit()==cu) {
            //Also use this cuChange object for the aspect changes
            aspectChanges.setCUChange(cuChange);
        }
        return cuChange;
    }

    /**
     * @return The target aspect where we will create the intertype declarations.
     */
    public AspectElement getAspect() {
        return targetAspect;
    }

    public String getAspectName() {
        AspectElement theAspect = getAspect();
        if (theAspect==null) return "";
        return theAspect.getFullyQualifiedName();
    }

    /**
     * Compute the location where new ITDs will be inserted in the target aspect's source code.
     */
    private int getInsertLocation() {
        try {
            return  targetAspect.getSourceRange().getOffset()
                  + targetAspect.getSourceRange().getLength()-1;
        } catch (JavaModelException e) {
            return 0;
        }
    }

    IJavaProject getJavaProject() {
        return javaProject;
    }

    public IMember[] getMembers() {
        List<IMember> members = new ArrayList<>();
        for (ICompilationUnit cu : memberMap.keySet()) {
            members.addAll(getMembers(cu));
        }
        return members.toArray(new IMember[0]);
    }

    private Collection<IMember> getMembers(ICompilationUnit cu) {
      Collection<IMember> result = memberMap.computeIfAbsent(cu, k -> new ArrayList<>());
      return result;
    }

    @Override
    public String getName() {
        return "Pull-Out";
    }

    public boolean hasMembers() {
        return !memberSet.isEmpty();
    }

    public RefactoringStatus initialize(Map<String, String> args) {
        RefactoringStatus status = new RefactoringStatus();
        setMakePrivileged(Boolean.parseBoolean(args.get(MAKE_PRIVILEGED)));
        setMember((IMember)JavaCore.create(args.get(MEMBER)), status);
        return status;
    }

    private boolean isInTopLevelType(IMember member) {
        IJavaElement parent = member.getParent();
        Assert.isTrue(parent.getElementType()==IJavaElement.TYPE);
        return parent.getParent().getElementType()==IJavaElement.COMPILATION_UNIT;
    }

    /**
     * Is the "make privileged" option of the refactoring set.
     */
    public boolean isMakePrivileged() {
        return makePrivileged;
    }

    /**
     * Does given IBinding refer to an IJavaElement that will be moved into the Aspect?
     */
    private boolean isMoved(IBinding binding) {
        return isPulled(binding.getJavaElement());
    }

    /**
     * Will the given IJavaElement be moved into the Aspect? This test returns true, also for
     * IJavaElements that are contained inside pulled elements!
     */
    public boolean isMoved(IJavaElement javaElement) {
        //Null case is handled to easily terminate recursion
        return javaElement!=null
           && ( isPulled(javaElement) || isMoved(javaElement.getParent()) );
    }

    /**
     * Is the target aspect privileged before the refactoring?
     */
    private boolean isPrivileged() {
        if (targetAspect==null)
            return false;
        try {
            return targetAspect.isPrivileged();
        } catch (JavaModelException e) {
            return false;
        }
    }

    /**
     * Are we allowed to delete the abstract modifier and fabricate
     * dummy method bodies?
     */
    public boolean isGenerateAbstractMethodStubs() {
        return generateAbstractMethodStubs;
    }

    /**
     * Allow pulling out of abstract methods. The abstract keyword will
     * be removed and a dummy method body added.
     */
    public void setGenerateAbstractMethodStubs(boolean allow) {
        this.generateAbstractMethodStubs = allow;
    }

    /**
     * Allow ITDs to be made public, as needed.
     */
    public void setAllowMakePublic(boolean allow) {
        this.allowMakePublic = allow;
    }

    /**
     * Allow ITDs to be made public, as needed.
     */
    public void setAllowDeleteProtected(boolean allow) {
        this.allowDeleteProtected = allow;
    }

    /**
     * Are we allowed to delete any visibility modifier (on ITDs)
     * and make the ITD public?
     */
    public boolean isAllowMakePublic() {
        return allowMakePublic;
    }

    /**
     * Are we allowed to delete the protected keyword from ITDs?
     */
    public boolean isAllowDeleteProtected() {
        return allowDeleteProtected || allowMakePublic;
    }

    /**
     * Is the given IJaveElement selected to be pulled into the Aspect. Elements moved because they are
     * nested inside selected elements are *not* considered (if you want this, use isMoved instead).
     */
    private boolean isPulled(IJavaElement javaElement) {
        return memberSet.contains(javaElement);
    }

    private static RefactoringStatusContext makeContext(ICompilationUnit cu, ASTNode node) {
        return JavaStatusContext.create(cu,
                    new SourceRange(node.getStartPosition(), node.getLength()));
    }

    private static RefactoringStatusContext makeContext(IMember member) {
        try {
            return JavaStatusContext.create(member.getCompilationUnit(), member.getSourceRange());
        } catch (JavaModelException e) {
            return null; // Too bad, no context for the error message
        }
    }

    static RefactoringStatusContext makeContext(IMember member, ASTNode node) {
        return makeContext(member.getCompilationUnit(), node);
    }

    private static RefactoringStatusContext makeContext(SearchMatch match) {
        try {
            IJavaElement element = (IJavaElement) match.getElement();
            ITypeRoot typeRoot = (ITypeRoot) element.getAncestor(IJavaElement.COMPILATION_UNIT);
            if (typeRoot==null) {
                typeRoot = (ITypeRoot) element.getAncestor(IJavaElement.CLASS_FILE);
            }
            ISourceRange range = new SourceRange(match.getOffset(), match.getLength());
            return JavaStatusContext.create(typeRoot, range);
        }
        catch (Throwable e) {
            return null;
        }
    }


    public void setAspect(AspectElement target) {
        this.targetAspect = target;
    }

    /**
     * Set the target aspect by giving the name of the aspect. Note that this method
     * only works if it can figure out what project to look for the aspect, so at least
     * one member to be pulled out has to be set prior to calling this method.
     */
    public RefactoringStatus setAspect(String name) {
        IType type;

        try {
            if (name.length() == 0)
                return RefactoringStatus.createFatalErrorStatus("Select an Aspect.");

            type= getJavaProject().findType(name, new NullProgressMonitor());
            if (type == null || !type.exists())
                return RefactoringStatus.createErrorStatus(MessageFormat.format("Aspect ''{0}'' does not exist.", name));
            if (!(type instanceof AspectElement))
                return RefactoringStatus.createErrorStatus(MessageFormat.format("''{0}'' is not an Aspect.", name));
        } catch (JavaModelException exception) {
            return RefactoringStatus.createFatalErrorStatus("Could not determine type.");
        }

        if (type.isReadOnly())
            return RefactoringStatus.createFatalErrorStatus("Type is read-only.");

        if (type.isBinary())
            return RefactoringStatus.createFatalErrorStatus("Type is binary.");

        targetAspect = (AspectElement) type;

        return new RefactoringStatus();
    }

    /**
     * Set the "make privileged" option of the refactoring.
     */
    public void setMakePrivileged(boolean makePrivileged) {
        this.makePrivileged = makePrivileged;
    }

    public void setMember(IMember member, RefactoringStatus status) {
        clearMembers();
        addMember(member, status);
    }

    /**
     * Will the target aspect be privileged after refactoring
     */
    public boolean willBePrivileged() {
        return isPrivileged() || isMakePrivileged();
    }

    protected String getAbstractMethodStubBody(IMember originalMember) {
        //FIXKDV: Stupid implementation for now... maybe we can use the Eclipse Java Code templates somehow
        // or have the user specify their own abstract method stub template in the wizard.
        return " { throw new Error(\"abstract method stub\"); }";
    }
}
