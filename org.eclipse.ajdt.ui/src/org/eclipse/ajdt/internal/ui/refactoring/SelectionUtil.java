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
package org.eclipse.ajdt.internal.ui.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.ITextSelection;

public class SelectionUtil {

	/**
	 * Find elements completely enclosed by the given text selection, or, if
	 * no such elements are found, the smallest element that encloses the
	 * start offset of the selection.
	 * <p>
	 * Only the top-most elements enclosed by the selection are returned, elements
	 * nested inside those elements are not.
	 * <p>
	 * This method may return an empty list but it never returns null.
	 */
	public static List<IJavaElement> findSelectedElements(
			ICompilationUnit unit, ITextSelection textSel) {
		List<IJavaElement> foundElements = new ArrayList<>();
		if (textSel.getLength()>0) {
			// Skip this call for length 0, since we know we can't find anything anyway.
			findEnclosedElements(unit, textSel.getOffset(), textSel.getLength(), foundElements);
		}
		if (foundElements.isEmpty()) {
			IJavaElement el;
			try {
				el = unit.getElementAt(textSel.getOffset());
				if (el!=null) foundElements.add(el);
			} catch (JavaModelException e) {
				//Not found... skip
			}
		}
		return foundElements;
	}

	public static void findEnclosedElements(IJavaElement el, int offset,
			int length, List<IJavaElement> foundElements) {
		if (isEnclosed(el, offset, length)) {
			foundElements.add(el);
    }
		else if (el instanceof IParent) {
			try {
				IJavaElement[] children = ((IParent) el).getChildren();
				for (IJavaElement child : children) {
					findEnclosedElements(child, offset, length, foundElements);
				}
			} catch (JavaModelException ignored) {
			}
		}
	}

	public static boolean isEnclosed(IJavaElement _el, int offset, int length) {
		if (!(_el instanceof ISourceReference)) return false;
		try {
			ISourceReference el = (ISourceReference)_el;
			int end = offset + length;
			int elOffset = el.getSourceRange().getOffset();
			int elEnd = elOffset + el.getSourceRange().getLength();
			return offset <= elOffset && elEnd <= end;
		} catch (JavaModelException e) {
			return false;
		}
	}

}
