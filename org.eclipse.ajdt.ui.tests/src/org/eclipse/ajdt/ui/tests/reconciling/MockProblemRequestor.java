package org.eclipse.ajdt.ui.tests.reconciling;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem;

public class MockProblemRequestor implements IProblemRequestor {

  List<IProblem> problems = new LinkedList<>();

  public void acceptProblem(IProblem problem) {
    problems.add(problem);
  }

  public String problemString() {
    StringBuilder sb = new StringBuilder();
    sb.append("["); //$NON-NLS-1$
    for (Object problem : problems) {
      DefaultProblem prob = (DefaultProblem) problem;
      sb.append("\n\t").append(prob.toString()); //$NON-NLS-1$
    }
    sb.append("\n]"); //$NON-NLS-1$
    return sb.toString();
  }

  public void beginReporting() {
  }

  public void endReporting() {
  }

  public boolean isActive() {
    return true;
  }

  /**
   * we can't get around having an unused import
   * we should be able to remove this soon
   */
  public static Map<String, CategorizedProblem[]> filterProblems(Map<String, CategorizedProblem[]> problems) {
    problems.values().removeIf(problems1 ->
      problems1[0].toString().equals("Pb(388) The import java.util.List is never used") //$NON-NLS-1$
    );
    return problems;
  }

  public static Map<String, CategorizedProblem[]> filterAllWarningProblems(Map<String, CategorizedProblem[]> problems) {
    problems.values().removeIf(problems1 ->
      problems1[0].isWarning()
    );
    return problems;
  }

  public static List<CategorizedProblem> filterProblems(List<CategorizedProblem>  problems) {
    problems.removeIf(problem ->
      problem.toString().equals("Pb(388) The import java.util.List is never used") //$NON-NLS-1$
    );
    return problems;
  }

  public static String printProblems(Map<String, CategorizedProblem[]> problems) {
    StringBuilder sb = new StringBuilder();
    sb.append("["); //$NON-NLS-1$
    for (Map.Entry<String, CategorizedProblem[]> entry : problems.entrySet()) {
      sb.append("\n\t").append(entry.getKey()).append(" --> "); //$NON-NLS-1$
      CategorizedProblem[] probs = entry.getValue();
      for (CategorizedProblem prob : probs)
        sb.append("\n\t\t").append(prob.toString());
    }
    sb.append("\n]\n"); //$NON-NLS-1$
    return sb.toString();
  }

  public static int countProblems(Map<String, CategorizedProblem[]> problems) {
    int count = 0;
    for (CategorizedProblem[] probsArray : problems.values())
      count += probsArray.length;
    return count;
  }
}
