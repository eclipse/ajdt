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

package org.eclipse.ajdt.ui.tests.editor.contentassist;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;

/**
 * @author Andrew Eisenberg
 * @created Jan 2, 2009
 *
 */
class MockCompletionRequestor extends CompletionRequestor {

    List<CompletionProposal> accepted = new LinkedList<>();

    public void accept(CompletionProposal proposal) {
    	System.out.println("Accepting proposal: "+proposal);
        accepted.add(proposal);
    }

    public void filter(String completion) {
    	CompletionProposal toRemove = null;
    	for (CompletionProposal proposals: accepted) {
    		if (new String(proposals.getCompletion()).equals(completion)) {
    			toRemove = proposals;
    		}
    	}
    	if (toRemove != null) {
    		accepted.remove(toRemove);
    	}
    }

    public List getAccepted() {
        return accepted;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Accepted completion proposals:\n");
        if (accepted.size() > 0) {
          for (CompletionProposal proposal : accepted) {
            sb.append("\t").append(proposal.toString()).append("\n");
          }
        } else {
            sb.append("\t<none>\n");
        }
        return sb.toString();
    }

	public CompletionProposal findProposal(String completionText) {
		for (CompletionProposal proposal: accepted) {
			if (new String(proposal.getCompletion()).equals(completionText)) {
				return proposal;
			}
		}
		return null;
	}

}
