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
package org.eclipse.ajdt.internal.ui.editor.quickfix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.eclipse.jdt.ui.text.java.IQuickFixProcessor;

/**
 * Some Java quick fix proposals will not work on Java files in AspectJ projects.
 * This class gets around that problem.
 * <p>
 * For now only doing Missing Serial Version IDs, but can expand to more in the future.
 *
 * @author andrew
 * @created Dec 27, 2008
 */
public class JavaQuickFixProcessor implements IQuickAssistProcessor, IQuickFixProcessor {

  public IJavaCompletionProposal[] getCorrections(IInvocationContext context, IProblemLocation[] locations) {
    if (locations == null || locations.length == 0 || !isAJProject(context.getCompilationUnit())) {
      return null;
    }

    HashSet<Integer> handledProblems = new HashSet<>(locations.length);
    ArrayList<IJavaCompletionProposal> resultingCollections = new ArrayList<>();
    for (IProblemLocation curr : locations) {
      Integer id = curr.getProblemId();
      if (handledProblems.add(id))
        process(context, curr, resultingCollections);
    }
    return resultingCollections.toArray(new IJavaCompletionProposal[0]);
  }

  private void process(
    IInvocationContext context,
    IProblemLocation problem,
    Collection<IJavaCompletionProposal> proposals
  ) {
    int id = problem.getProblemId();
    // no proposals for none-problem locations
    if (id == 0)
      return;
    if (id == IProblem.MissingSerialVersion)
      AJSerialVersionSubProcessor.getSerialVersionProposals(context, problem, proposals);
  }

  public boolean hasCorrections(ICompilationUnit unit, int problemId) {
    return isAJProject(unit) && problemId == IProblem.MissingSerialVersion;
  }

  private boolean isAJProject(ICompilationUnit unit) {
    return AspectJPlugin.isAJProject(unit.getJavaProject().getProject());
  }

  public IJavaCompletionProposal[] getAssists(IInvocationContext context, IProblemLocation[] locations) {
    // none
    return null;
  }

  public boolean hasAssists(IInvocationContext context) {
    // no assists, only corrections
    return false;
  }

}
