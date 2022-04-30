/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *******************************************************************************/
package org.eclipse.ajdt.core.codeconversion;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.aspectj.org.eclipse.jdt.internal.compiler.CompilationResult;
import org.aspectj.org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.aspectj.org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.aspectj.org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.aspectj.org.eclipse.jdt.internal.compiler.parser.Parser;
import org.aspectj.org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;

/**
 *
 */
public class CodeChecker {

	/**
	 * Determines whether the given file contains any AspectJ-specific syntax,
	 * such as an aspect, an inner aspect inside a class, or just a pointcut
	 * inside a class.
	 *
	 * @param file
	 * @return
	 */
	public static boolean containsAspectJConstructs(IFile file) {
		IPath path = file.getRawLocation();
		if (path == null) {
			return false;
		}
		File f = path.toFile();
		if (!f.exists()) {
			return false;
		}

		try {
			// read contents of file into char array
			FileReader fr = new FileReader(f);
			int size = (int) f.length();
			char[] source = new char[size];
			fr.read(source, 0, size);
			fr.close();

			/*
			 * bug 95370: we previously used a simple scanner here, looking for
			 * aspect or pointcut tokens. But there might be identifiers with
			 * the same name, which is allowed. Therefore we need to do a parse
			 * instead, so that the context is taken into account, allowing us
			 * to determine only when "aspect" or "pointcut" are used as
			 * keywords.
			 */

			// create a compilation unit with the source code for input to the
			// parser
			CompilerOptions options = new CompilerOptions();
			ProblemReporter probrep = new ProblemReporter(null, options, null);
			ICompilationUnit sourceUnit = new CompilationUnit(source, "", //$NON-NLS-1$
					options.defaultEncoding);
			CompilationResult result = new CompilationResult(sourceUnit, 0, 0,
					options.maxProblemsPerUnit);

			// drive our parser extension which records aspect and pointcut
			// declarations
			AspectDetectingParser parser = new AspectDetectingParser(probrep,
					false);
			parser.parse(sourceUnit, result);
			return parser.containsAspectJSyntax();
		}
    catch (IOException ignored) {
    }

    return false;
	}

}

/**
 * Extends the AspectJ parser, and records whether any AspectJ-specific syntax
 * (aspects or pointcut declarations) is encountered.
 */
class AspectDetectingParser extends Parser {

	private boolean foundAspectJSyntax = false;

	public AspectDetectingParser(ProblemReporter problemReporter,
			boolean optimizeStringLiterals) {
		super(problemReporter, optimizeStringLiterals);
		diet = true;
	}

	protected void consumeAspectDeclaration() {
		foundAspectJSyntax = true;
		super.consumeAspectDeclaration();
	}

	protected void consumePointcutDeclaration() {
		foundAspectJSyntax = true;
		super.consumePointcutDeclaration();
	}

	public boolean containsAspectJSyntax() {
		return foundAspectJSyntax;
	}

}
