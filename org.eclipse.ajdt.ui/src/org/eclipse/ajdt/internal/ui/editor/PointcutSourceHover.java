/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.editor;

import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.core.javaelements.PointcutUtilities;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.text.java.hover.AbstractJavaEditorTextHover;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.information.IInformationProviderExtension2;
import org.eclipse.swt.SWT;
import org.eclipse.ui.editors.text.EditorsUI;

/**
 * Hover to show the definition of pointcuts. Loosely based on
 * org.eclipse.jdt.internal.ui.text.java.hover.JavaSourceHover
 */
public class PointcutSourceHover extends AbstractJavaEditorTextHover implements
		ITextHoverExtension, IInformationProviderExtension2 {

	/*
	 * @see ITextHover#getHoverInfo(ITextViewer, IRegion)
	 */
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		if (!(getEditor() instanceof AspectJEditor)) {
			return null;
		}
		IJavaElement input = SelectionConverter
				.getInput((JavaEditor) getEditor());
		int offset = hoverRegion.getOffset();
		if (input instanceof ICompilationUnit) {
			input = AJCompilationUnitManager.mapToAJCompilationUnit((ICompilationUnit)input);
		}
		if (input instanceof AJCompilationUnit) {
			AJCompilationUnit ajcu = (AJCompilationUnit) input;
			String source = PointcutUtilities.isInPointcutContext(ajcu, offset);
			if (source != null) {
				String id = PointcutUtilities.findIdentifier(source, offset);
				if (id != null) {
					try {
						IJavaElement pc = PointcutUtilities.findPointcut(ajcu
								.getElementAt(offset), id);
						if (pc != null) {
							IResource res = pc.getUnderlyingResource();
							AJCompilationUnit cu = AJCompilationUnitManager.INSTANCE
									.getAJCompilationUnit((IFile) res);
							if (cu != null) {
								cu.requestOriginalContentMode();
								String pcs = ((ISourceReference) pc)
										.getSource();
								cu.discardOriginalContentMode();
								return pcs;
							}
							return ((ISourceReference) pc).getSource();
						}
					} catch (JavaModelException ignored) {
					}
				}
			}
		}
		return null;
	}

	/*
	 * @see org.eclipse.jface.text.ITextHoverExtension#getHoverControlCreator()
	 * @since 3.0
	 */
	public IInformationControlCreator getHoverControlCreator() {
		return parent -> {
      // need to use our own version to get AJ keyword highlighting
      return new AJSourceViewerInformationControl(parent,
          EditorsUI.getTooltipAffordanceString());
    };
	}

	/*
	 * @see IInformationProviderExtension2#getInformationPresenterControlCreator()
	 * @since 3.0
	 */
	public IInformationControlCreator getInformationPresenterControlCreator() {
		return parent -> {
      int shellStyle = SWT.RESIZE | SWT.TOOL;
      int style = SWT.V_SCROLL | SWT.H_SCROLL;
      // need to use our own version to get AJ keyword highlighting
      return new AJSourceViewerInformationControl(parent, shellStyle,
          style);
    };
	}

}
