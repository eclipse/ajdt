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

    List problems = new LinkedList();

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
    public static HashMap filterProblems(HashMap problems) {
      //$NON-NLS-1$
      problems.values().removeIf(o -> ((CategorizedProblem[]) o)[0].toString()
        .equals("Pb(388) The import java.util.List is never used"));
        return problems;
    }
    public static HashMap filterAllWarningProblems(HashMap problems) {
      problems.values().removeIf(o -> ((CategorizedProblem[]) o)[0].isWarning());
        return problems;
    }



    public static List filterProblems(List problems) {
      //$NON-NLS-1$
      problems.removeIf(o -> o.toString()
        .equals("Pb(388) The import java.util.List is never used"));
        return problems;
    }

    public static String printProblems(HashMap problems) {
        StringBuilder sb = new StringBuilder();
        sb.append("["); //$NON-NLS-1$
      for (Object o : problems.entrySet()) {
        Map.Entry entry = (Map.Entry) o;
        sb.append("\n\t").append(entry.getKey().toString()).append(" --> "); //$NON-NLS-1$
        CategorizedProblem[] probs = (CategorizedProblem[]) entry.getValue();
        for (CategorizedProblem prob : probs) {
          sb.append("\n\t\t").append(prob.toString());
        }
      }
        sb.append("\n]\n"); //$NON-NLS-1$
        return sb.toString();
    }

    public static int countProblems(HashMap problems) {
        int count = 0;
      for (Object o : problems.values()) {
        CategorizedProblem[] probsArray = (CategorizedProblem[]) o;
        count += probsArray.length;
      }
        return count;
    }
}
