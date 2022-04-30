/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Luzius Meisser - initial implementation
 *     Sian January - changed for Eclipse 3.2
 *******************************************************************************/
package org.eclipse.ajdt.internal.core.contentassist;

import java.util.ArrayList;

import org.aspectj.asm.IProgramElement.Accessibility;
import org.eclipse.ajdt.core.ReflectionUtils;
import org.eclipse.ajdt.core.codeconversion.AspectsConvertingParser;
import org.eclipse.ajdt.core.codeconversion.JavaCompatibleBuffer;
import org.eclipse.ajdt.core.model.AJWorldFacade;
import org.eclipse.ajdt.core.model.AJWorldFacade.ITDInfo;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.codeassist.InternalCompletionProposal;

/**
 * Translates code positions from fakeBuffer into realBuffer before
 * passing them on to the wrapped ICompletionRequestor.
 *
 * A description of how code completion works in AJDT can be found in bug 74419.
 *
 * @author Luzius Meisser
 */
public class ProposalRequestorWrapper extends CompletionRequestor {

    CompletionRequestor wrapped;
    ArrayList<AspectsConvertingParser.Replacement> insertionTable;

    private AJWorldFacade world;
    private final ICompilationUnit unit;

       // this is the identifier added by the AspectsConvertingParser
    // should be ignored
    private final String contextSwitchIdentifier;

    /**
     * @param wrapped
     * @param buffer
     */
    public ProposalRequestorWrapper(CompletionRequestor wrapped,
            ICompilationUnit unit,
            JavaCompatibleBuffer buffer, String contextSwitchIdentifier) {
        super();
        this.wrapped = wrapped;
        this.unit = unit;
        this.insertionTable = buffer.getInsertionTable();
        this.contextSwitchIdentifier =
            contextSwitchIdentifier == null || contextSwitchIdentifier.length() == 0 ?
                    null : contextSwitchIdentifier;
    }
    public ProposalRequestorWrapper(CompletionRequestor wrapped,
                                    ICompilationUnit unit,
                                    ArrayList<AspectsConvertingParser.Replacement> insertionTable) {
        super();
        this.wrapped = wrapped;
        this.unit = unit;
        this.insertionTable = insertionTable;
        this.contextSwitchIdentifier = null;
    }

    public void accept(CompletionProposal proposal) {

        if (!shouldAccept(proposal)) {
            return;
        }

        if (insertionTable.size() > 0) {
            translateReplaceRange(proposal);
        }
        wrapped.accept(proposal);
    }

    private void translateReplaceRange(CompletionProposal proposal) {
        int s = proposal.getReplaceStart();
        int e = proposal.getReplaceEnd();
        proposal.setReplaceRange(trans(s), trans(e));

        /* AJDT 1.7 */
        // translate all proposals contained in this one
        if (proposal instanceof InternalCompletionProposal) {
            InternalCompletionProposal internalProposal = (InternalCompletionProposal) proposal;
            if (internalProposal.getRequiredProposals() != null) {
                for (int i = 0; i < internalProposal.getRequiredProposals().length; i++) {
                    translateReplaceRange(internalProposal.getRequiredProposals()[i]);
                }
            }
        }
    }

    protected boolean shouldAccept(CompletionProposal proposal) {
        if (proposal.getKind() == CompletionProposal.FIELD_REF ||
                proposal.getKind() == CompletionProposal.METHOD_REF) {

            if (world == null) {
                world = new AJWorldFacade(unit.getJavaProject().getProject());
            }

            ITDInfo info = world.findITDInfoFromTargetType(proposal.getDeclarationSignature(), proposal.getName());
            if (info != null) {
                if (info.accessibility == Accessibility.PUBLIC) {
                    // accessible everywhere
                    return true;
                } else if (info.accessibility == Accessibility.PACKAGE) {
                    // accessible only in package of declaring aspect
                    if (unit.getParent().getElementName().equals(info.packageDeclaredIn)) {
                        int oldFlags = proposal.getFlags();
                        oldFlags |= Flags.AccDefault;
                        oldFlags &= ~Flags.AccPublic;
                        proposal.setFlags(oldFlags);
                        return true;
                    }
                } else if (info.accessibility == Accessibility.PRIVATE) {
                    // accessible only in declaring aspect's compilation unit
                    if (unit.getElementName().startsWith(info.topLevelAspectName + ".") &&
                       unit.getParent().getElementName().equals(info.packageDeclaredIn)) {

                        int oldFlags = proposal.getFlags();
                        oldFlags |= Flags.AccPrivate;
                        oldFlags &= ~Flags.AccPublic;
                        proposal.setFlags(oldFlags);
                            return true;
                    }

                }
                return false;
            } else {
                // not an ITD
                return true;
            }
        } else if (proposal.getKind() == CompletionProposal.LOCAL_VARIABLE_REF) {
            // check to see if this is the proposal that has been added by the context switch for ITDs
          return !contextSwitchIgnore(proposal);
        } else if (proposal.getKind() == CompletionProposal.TYPE_REF) {
            // check to see if this is an ITIT type that should not be see
            char[] typeName = (char[]) ReflectionUtils.getPrivateField(InternalCompletionProposal.class, "typeName", (InternalCompletionProposal) proposal);
          return typeName == null || !CharOperation.contains('$', typeName);
        }
        return true;
    }

    // ignore the proposal added by the context switch identifier
    private boolean contextSwitchIgnore(CompletionProposal proposal) {
        return contextSwitchIdentifier != null &&
                new String(proposal.getCompletion()).startsWith(contextSwitchIdentifier);
    }


    private int trans(int pos){
        return AspectsConvertingParser.translatePositionToBeforeChanges(pos, insertionTable);
    }

    public void acceptContext(CompletionContext context) {
        wrapped.acceptContext(context);
    }

    public void endReporting() {
        wrapped.endReporting();
    }

    public void beginReporting() {
        wrapped.beginReporting();
    }

    public void completionFailure(IProblem problem) {
        wrapped.completionFailure(problem);
    }

    public String[] getFavoriteReferences() {
        return wrapped.getFavoriteReferences();
    }

    public boolean isAllowingRequiredProposals(int proposalKind,
            int requiredProposalKind) {
        return wrapped.isAllowingRequiredProposals(proposalKind, requiredProposalKind);
    }

    public boolean isExtendedContextRequired() {
        return wrapped.isExtendedContextRequired();
    }

    public boolean isIgnored(int completionProposalKind) {
        return wrapped.isIgnored(completionProposalKind);
    }

    public void setAllowsRequiredProposals(int proposalKind,
            int requiredProposalKind, boolean allow) {
        wrapped.setAllowsRequiredProposals(proposalKind, requiredProposalKind, allow);
    }

    public void setFavoriteReferences(String[] favoriteImports) {
        wrapped.setFavoriteReferences(favoriteImports);
    }

    public void setIgnored(int completionProposalKind, boolean ignore) {
        wrapped.setIgnored(completionProposalKind, ignore);
    }

    public void setRequireExtendedContext(boolean require) {
        wrapped.setRequireExtendedContext(require);
    }

    public boolean equals(Object obj) {
        return wrapped.equals(obj);
    }
    public int hashCode() {
        return wrapped.hashCode();
    }
    public String toString() {
        return wrapped.toString();
    }



}
