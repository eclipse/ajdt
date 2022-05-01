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
package org.eclipse.ajdt.core.dom.rewrite;

import java.util.List;
import java.util.Map;

import org.aspectj.org.eclipse.jdt.core.dom.ASTVisitor;
import org.aspectj.org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer;
import org.aspectj.org.eclipse.jdt.internal.core.dom.rewrite.LineInformation;
import org.aspectj.org.eclipse.jdt.internal.core.dom.rewrite.NodeInfoStore;
import org.aspectj.org.eclipse.jdt.internal.core.dom.rewrite.RewriteEventStore;
import org.eclipse.ajdt.core.dom.rewrite.AjASTRewriteAnalyzer.IASTRewriteAnalyzerFactory;
import org.eclipse.text.edits.TextEdit;

/**
 * Factory, dynamically loaded by the TypeDeclaration class in the shadows/dom tree.
 * This is a factory for type declaration that returns the Aj subclass of typedeclaration.
 * @author AndyClement
 * @deprecated This class has not been updated for AspectJ 1.7 and may be removed in future versions of AJDT
 */
@Deprecated
public class AjASTRewriteAnalyzerFactory implements IASTRewriteAnalyzerFactory {
//	public ASTVisitor getASTRewriteAnalyzer(IDocument document, TextEdit rootEdit, RewriteEventStore eventStore,
//			NodeInfoStore nodeInfos, Map options, TargetSourceRangeComputer extendedSourceRangeComputer) {
//		return new AjASTRewriteAnalyzer(document,rootEdit,eventStore,nodeInfos,options,extendedSourceRangeComputer);
//	}

	public ASTVisitor getASTRewriteAnalyzer(char[] content2,
			LineInformation lineInfo2, String lineDelim, TextEdit result,
			RewriteEventStore eventStore2, NodeInfoStore nodeStore,
			List<?> comments, Map<String, String> options, TargetSourceRangeComputer xsrComputer) {
		return new AjASTRewriteAnalyzer(content2,lineInfo2,lineDelim,result,eventStore2,nodeStore,comments,options,xsrComputer);
	}
}
