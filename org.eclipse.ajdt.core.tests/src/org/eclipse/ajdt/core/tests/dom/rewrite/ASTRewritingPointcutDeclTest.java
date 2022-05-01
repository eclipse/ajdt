/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.core.tests.dom.rewrite;

import java.util.List;
import java.util.Map;

import org.aspectj.org.eclipse.jdt.core.dom.AST;
import org.aspectj.org.eclipse.jdt.core.dom.ASTNode;
import org.aspectj.org.eclipse.jdt.core.dom.ASTParser;
import org.aspectj.org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.AdviceDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.AjTypeDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.AndPointcut;
import org.aspectj.org.eclipse.jdt.core.dom.AspectDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.CflowPointcut;
import org.aspectj.org.eclipse.jdt.core.dom.CompilationUnit;
import org.aspectj.org.eclipse.jdt.core.dom.ImportDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.MethodDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.Name;
import org.aspectj.org.eclipse.jdt.core.dom.NotPointcut;
import org.aspectj.org.eclipse.jdt.core.dom.OrPointcut;
import org.aspectj.org.eclipse.jdt.core.dom.PerCflow;
import org.aspectj.org.eclipse.jdt.core.dom.PerObject;
import org.aspectj.org.eclipse.jdt.core.dom.PointcutDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.PrimitiveType;
import org.aspectj.org.eclipse.jdt.core.dom.ReferencePointcut;
import org.aspectj.org.eclipse.jdt.core.dom.SimpleName;
import org.aspectj.org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.Type;
import org.aspectj.org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.ajdt.core.dom.rewrite.AjASTRewrite;
import org.eclipse.ajdt.core.dom.rewrite.AjListRewrite;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

public class ASTRewritingPointcutDeclTest extends AJDTCoreTestCase {

	public void testAddImportStatement() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		Document doc = new Document("import java.util.List;\nclass X {}\n"); //$NON-NLS-1$
		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		AST ast = cu.getAST();
		ImportDeclaration id = ast.newImportDeclaration();
		id.setName(ast.newName(new String[] { "java", "util", "Set" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		AjASTRewrite rewriter = AjASTRewrite.create(ast); // the class I'm
															// testing
		AjListRewrite lrw = rewriter.getListRewrite(cu, // another class I'm
														// testing
				CompilationUnit.IMPORTS_PROPERTY);
		lrw.insertLast(id, null);
		TextEdit edits = rewriter.rewriteAST(doc, compilerOptions);
		try {
			edits.apply(doc);
		} catch (BadLocationException e) {
			fail("got a BadLocationException: " + e.getMessage()); //$NON-NLS-1$
		}
		if (!"import java.util.List;\nimport java.util.Set;\nclass X {}\n" //$NON-NLS-1$
		.equals(doc.get())) {
			fail("expecting:\nimport java.util.List;\nimport java.util.Set;\nclass X {}\n" //$NON-NLS-1$
           + "=====got:\n" + doc.get()); //$NON-NLS-1$
		}
	}

	public void testPointcutDesignatorRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		Document doc = new Document("aspect A {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    public pointcut c():a();\n" + "}\n"); //$NON-NLS-1$ //$NON-NLS-2$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		AspectDeclaration type = (AspectDeclaration) findAbstractTypeDeclaration(
				astRoot, "A"); //$NON-NLS-1$
		{ // rename pointcutDesignator name
			PointcutDeclaration pointcutDecl = findPointcutDeclaration(type,
					"c"); //$NON-NLS-1$
			ReferencePointcut referencePointcut = (ReferencePointcut) pointcutDecl
					.getDesignator();
			SimpleName name = referencePointcut.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
		}
		String expected = "aspect A {\n" + "    public pointcut a();\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    public pointcut c():b();\n" + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
		check(rewrite, doc, expected, compilerOptions);
	}

	public void testNotPointcutRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		// add node, change ASTVisitor and ASTMatcher
		// update ASTConverter and ASTRewriteAnalyzer
		Document doc = new Document("aspect A {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    public pointcut c():!a();\n" + "}\n"); //$NON-NLS-1$ //$NON-NLS-2$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		AspectDeclaration type = (AspectDeclaration) findAbstractTypeDeclaration(
				astRoot, "A"); //$NON-NLS-1$
		{ // rename pointcutDesignator name
			PointcutDeclaration pointcutDecl = findPointcutDeclaration(type,
					"c"); //$NON-NLS-1$
			NotPointcut notPointcut = (NotPointcut) pointcutDecl
					.getDesignator();
			ReferencePointcut referencePointcut = (ReferencePointcut) notPointcut
					.getBody();
			SimpleName name = referencePointcut.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
		}
		String expected = "aspect A {\n" + "    public pointcut a();\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    public pointcut c():!b();\n" + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
		check(rewrite, doc, expected, compilerOptions);
	}

	public void testCflowPointcutRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		// add node, change ASTVisitor and ASTMatcher
		// update ASTConverter and ASTRewriteAnalyzer
		Document doc = new Document("aspect A {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    public pointcut c(): cflow(a());\n" + "}\n"); //$NON-NLS-1$ //$NON-NLS-2$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		AspectDeclaration type = (AspectDeclaration) findAbstractTypeDeclaration(
				astRoot, "A"); //$NON-NLS-1$
		{ // rename cflow pointcutDesignator name
			PointcutDeclaration pointcutDecl = findPointcutDeclaration(type,
					"c"); //$NON-NLS-1$
			CflowPointcut cflowPointcut = (CflowPointcut) pointcutDecl
					.getDesignator();
			ReferencePointcut referencePointcut = (ReferencePointcut) cflowPointcut
					.getBody();
			SimpleName name = referencePointcut.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
		}
		String expected = "aspect A {\n" + "    public pointcut a();\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    public pointcut c(): cflow(b());\n" + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
		check(rewrite, doc, expected, compilerOptions);
	}

	public void testCflowbelowPointcutRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		// add node, change ASTVisitor and ASTMatcher
		// update ASTConverter and ASTRewriteAnalyzer
		Document doc = new Document("aspect A {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    public pointcut c(): cflowbelow(a());\n" + "}\n"); //$NON-NLS-1$ //$NON-NLS-2$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		AspectDeclaration type = (AspectDeclaration) findAbstractTypeDeclaration(
				astRoot, "A"); //$NON-NLS-1$
		{ // rename cflowbelow pointcutDesignator name
			PointcutDeclaration pointcutDecl = findPointcutDeclaration(type,
					"c"); //$NON-NLS-1$
			CflowPointcut cflowbelowPointcut = (CflowPointcut) pointcutDecl
					.getDesignator();
			ReferencePointcut referencePointcut = (ReferencePointcut) cflowbelowPointcut
					.getBody();
			SimpleName name = referencePointcut.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
		}
		String expected = "aspect A {\n" + "    public pointcut a();\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    public pointcut c(): cflowbelow(b());\n" + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
		check(rewrite, doc, expected, compilerOptions);
	}

	public void testPerthisPointcutRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		// add node, change ASTVisitor and ASTMatcher
		// update ASTConverter and ASTRewriteAnalyzer
		Document doc = new Document("aspect A perthis(a()) {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" + "}\n"); //$NON-NLS-1$ //$NON-NLS-2$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		AspectDeclaration type = (AspectDeclaration) findAbstractTypeDeclaration(
				astRoot, "A"); //$NON-NLS-1$
		{ // rename perthis pointcut expression
			PerObject perObject = (PerObject) type.getPerClause();
			ReferencePointcut referencePointcut = (ReferencePointcut) perObject
					.getBody();
			SimpleName name = referencePointcut.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
		}
		String expected = "aspect A perthis(b()) {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
		check(rewrite, doc, expected, compilerOptions);
	}

	public void testPercflowPointcutRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		// add node, change ASTVisitor and ASTMatcher
		// update ASTConverter and ASTRewriteAnalyzer
		Document doc = new Document("aspect A percflow(a()) {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" + "}\n"); //$NON-NLS-1$ //$NON-NLS-2$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		AspectDeclaration type = (AspectDeclaration) findAbstractTypeDeclaration(
				astRoot, "A"); //$NON-NLS-1$
		{ // rename percflow pointcut expression
			PerCflow perCflow = (PerCflow) type.getPerClause();
			ReferencePointcut referencePointcut = (ReferencePointcut) perCflow
					.getBody();
			SimpleName name = referencePointcut.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
		}
		String expected = "aspect A percflow(b()) {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
		check(rewrite, doc, expected, compilerOptions);
	}

	public void testBeforePointcutRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		// add node, change ASTVisitor and ASTMatcher
		// update ASTConverter and ASTRewriteAnalyzer
		Document doc = new Document("aspect A {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" + "    before(): a() {}\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "}\n"); //$NON-NLS-1$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		AspectDeclaration type = (AspectDeclaration) findAbstractTypeDeclaration(
				astRoot, "A"); //$NON-NLS-1$
		{ // rename before advice pointcut expression
			AdviceDeclaration adviceDeclaration = (AdviceDeclaration) type
					.getAdvice().get(0);
			ReferencePointcut referencePointcut = (ReferencePointcut) adviceDeclaration
					.getPointcut();
			SimpleName name = referencePointcut.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
		}
		String expected = "aspect A {\n" + "    public pointcut a();\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    public pointcut b();\n" + "    before(): b() {}\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "}\n"; //$NON-NLS-1$
		check(rewrite, doc, expected, compilerOptions);
	}

	public void testAfterPointcutRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		// add node, change ASTVisitor and ASTMatcher
		// update ASTConverter and ASTRewriteAnalyzer
		Document doc = new Document("aspect A {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" + "    after(): a() {}\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "}\n"); //$NON-NLS-1$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		AspectDeclaration type = (AspectDeclaration) findAbstractTypeDeclaration(
				astRoot, "A"); //$NON-NLS-1$
		{ // rename after advice pointcut expression
			AdviceDeclaration adviceDeclaration = (AdviceDeclaration) type
					.getAdvice().get(0);
			ReferencePointcut referencePointcut = (ReferencePointcut) adviceDeclaration
					.getPointcut();
			SimpleName name = referencePointcut.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
		}
		String expected = "aspect A {\n" + "    public pointcut a();\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    public pointcut b();\n" + "    after(): b() {}\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "}\n"; //$NON-NLS-1$
		check(rewrite, doc, expected, compilerOptions);
	}

	public void testAroundPointcutRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		// add node, change ASTVisitor and ASTMatcher
		// update ASTConverter and ASTRewriteAnalyzer
		Document doc = new Document("aspect A {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    void around(): a() {}\n" + "}\n"); //$NON-NLS-1$ //$NON-NLS-2$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		AspectDeclaration type = (AspectDeclaration) findAbstractTypeDeclaration(
				astRoot, "A"); //$NON-NLS-1$
		{ // rename around advice pointcut expression
			AdviceDeclaration adviceDeclaration = (AdviceDeclaration) type
					.getAdvice().get(0);
			ReferencePointcut referencePointcut = (ReferencePointcut) adviceDeclaration
					.getPointcut();
			SimpleName name = referencePointcut.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
		}
		String expected = "aspect A {\n" + "    public pointcut a();\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    void around(): b() {}\n" + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
		check(rewrite, doc, expected, compilerOptions);
	}

	public void testAndPointcutRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		// add node, change ASTVisitor and ASTMatcher
		// update ASTConverter and ASTRewriteAnalyzer
		Document doc = new Document("aspect A {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    public pointcut c(): a() && b();\n" + "}\n"); //$NON-NLS-1$ //$NON-NLS-2$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		AspectDeclaration type = (AspectDeclaration) findAbstractTypeDeclaration(
				astRoot, "A"); //$NON-NLS-1$
		{ // rename the left one in the &&
			PointcutDeclaration pointcutDecl = findPointcutDeclaration(type,
					"c"); //$NON-NLS-1$
			AndPointcut andPointcut = (AndPointcut) pointcutDecl
					.getDesignator();
			ReferencePointcut referencePointcut = (ReferencePointcut) andPointcut
					.getLeft();
			SimpleName name = referencePointcut.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
		}
		String expected = "aspect A {\n" + "    public pointcut a();\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    public pointcut c(): b() && b();\n" + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
		check(rewrite, doc, expected, compilerOptions);
	}

	public void testOrPointcutRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		// add node, change ASTVisitor and ASTMatcher
		// update ASTConverter and ASTRewriteAnalyzer
		Document doc = new Document("aspect A {\n" //$NON-NLS-1$
				+ "    public pointcut a();\n" //$NON-NLS-1$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    public pointcut c(): a() || b();\n" + "}\n"); //$NON-NLS-1$ //$NON-NLS-2$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		AspectDeclaration type = (AspectDeclaration) findAbstractTypeDeclaration(
				astRoot, "A"); //$NON-NLS-1$
		{ // rename the left one in the ||
			PointcutDeclaration pointcutDecl = findPointcutDeclaration(type,
					"c"); //$NON-NLS-1$
			OrPointcut orPointcut = (OrPointcut) pointcutDecl.getDesignator();
			ReferencePointcut referencePointcut = (ReferencePointcut) orPointcut
					.getLeft();
			SimpleName name = referencePointcut.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
		}
		String expected = "aspect A {\n" + "    public pointcut a();\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    public pointcut b();\n" //$NON-NLS-1$
				+ "    public pointcut c(): b() || b();\n" + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
		check(rewrite, doc, expected, compilerOptions);
	}

	public void testMethodRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		Document doc = new Document("package test1;\n" //$NON-NLS-1$
				+ "abstract class E {\n" + "    public void a();\n" + "}\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		TypeDeclaration type = (TypeDeclaration) findAbstractTypeDeclaration(
				astRoot, "E"); //$NON-NLS-1$

		{ // rename method name
			MethodDeclaration methodDecl = findMethodDeclaration(type, "a"); //$NON-NLS-1$
			SimpleName name = methodDecl.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
		}
		String expected = "package test1;\n" + "abstract class E {\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    public void b();\n" + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
		check(rewrite, doc, expected, compilerOptions);
	}

	public void testPointcutRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		Document doc = new Document("package test1;\n" //$NON-NLS-1$
				+ "abstract class E {\n" + "    public pointcut a();\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "}\n"); //$NON-NLS-1$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returntype - in 3
															// it has
															// "returnType2"

		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		TypeDeclaration type = (TypeDeclaration) findAbstractTypeDeclaration(
				astRoot, "E"); //$NON-NLS-1$
		if (type instanceof AjTypeDeclaration) {
			// rename pointcut name
			PointcutDeclaration pointcutDecl = findPointcutDeclaration(
					(AjTypeDeclaration) type, "a"); //$NON-NLS-1$
			SimpleName name = pointcutDecl.getName();
			SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
			rewrite.replace(name, newName, null);
			String expected = "package test1;\n" + "abstract class E {\n" //$NON-NLS-1$ //$NON-NLS-2$
					+ "    public pointcut b();\n" + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
			check(rewrite, doc, expected, compilerOptions);
		} else {
			fail("should have found an AjTypeDeclaration"); //$NON-NLS-1$
		}
	}

	public void testPointcutWithBodyRename() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		Document doc = new Document("package test1;\n" //$NON-NLS-1$
				+ "abstract class E {\n" + "    public pointcut temp();\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    public pointcut a(): temp();\n" + "}\n"); //$NON-NLS-1$ //$NON-NLS-2$
		ASTParser parser = ASTParser.newParser(AST.JLS2); // ajh02: need to
															// use 2 for
															// returnType - in 3
															// it has
															// "returnType2"
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		TypeDeclaration type = (TypeDeclaration) findAbstractTypeDeclaration(
				astRoot, "E"); //$NON-NLS-1$
		if (type instanceof AjTypeDeclaration) {
			{ // rename pointcut name
				PointcutDeclaration pointcutDecl = findPointcutDeclaration(
						(AjTypeDeclaration) type, "a"); //$NON-NLS-1$
				SimpleName name = pointcutDecl.getName();
				SimpleName newName = ast.newSimpleName("b"); //$NON-NLS-1$
				rewrite.replace(name, newName, null);
			}
			String expected = "package test1;\n" + "abstract class E {\n" //$NON-NLS-1$ //$NON-NLS-2$
					+ "    public pointcut temp();\n" //$NON-NLS-1$
					+ "    public pointcut b(): temp();\n" + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
			check(rewrite, doc, expected, compilerOptions);
		} else {
			fail("should have found an AjTypeDeclaration"); //$NON-NLS-1$
		}
	}

	/**
	 * This test is failing under AspectJ 1.7.  Since it doesn't look like
	 * andything uses the {@link AjASTRewrite} I see no reason to maintain this test.
	 * @throws Exception
	 */
	public void _testMethodDeclChanges() throws Exception {
		IProject project = createPredefinedProject("AST"); //$NON-NLS-1$
		Map<String, String> compilerOptions = JavaCore.create(project).getOptions(true);

		Document doc = new Document(
				"package test1;\n" //$NON-NLS-1$
						+ "public abstract class E {\n" //$NON-NLS-1$
						+ "    public E(int p1, int p2, int p3) {}\n" //$NON-NLS-1$
						+ "    public void gee(int p1, int p2, int p3) throws IllegalArgumentException {}\n" //$NON-NLS-1$
						+ "    public void hee(int p1, int p2, int p3) throws IllegalArgumentException {}\n" //$NON-NLS-1$
						+ "    public void iee(int p1, int p2, int p3) throws IllegalArgumentException, IllegalAccessException {}\n" //$NON-NLS-1$
						+ "    public void jee(int p1, int p2, int p3) throws IllegalArgumentException, IllegalAccessException {}\n" //$NON-NLS-1$
						+ "    public abstract void kee(int p1, int p2, int p3) throws IllegalArgumentException, IllegalAccessException, SecurityException;\n" //$NON-NLS-1$
						+ "    public abstract void lee(int p1, int p2, int p3) throws IllegalArgumentException, IllegalAccessException, SecurityException;\n" //$NON-NLS-1$
						+ "}\n"); //$NON-NLS-1$
		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setSource(doc.get().toCharArray());
		parser.setCompilerOptions(compilerOptions);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

		AjASTRewrite rewrite = AjASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		TypeDeclaration type = (TypeDeclaration) findAbstractTypeDeclaration(
				astRoot, "E"); //$NON-NLS-1$

		{ // convert constructor to method: insert return type
			MethodDeclaration methodDecl = findMethodDeclaration(type, "E"); //$NON-NLS-1$

			Type newReturnType = astRoot.getAST().newPrimitiveType(
					PrimitiveType.FLOAT);

			// from constructor to method
			rewrite.set(methodDecl, MethodDeclaration.RETURN_TYPE2_PROPERTY,
					newReturnType, null);
			rewrite.set(methodDecl, MethodDeclaration.CONSTRUCTOR_PROPERTY,
					Boolean.FALSE, null);
		}
		{ // change return type
			MethodDeclaration methodDecl = findMethodDeclaration(type, "gee"); //$NON-NLS-1$
      assertNotNull("Has no return type: gee", methodDecl.getReturnType2()); //$NON-NLS-1$

			Type returnType = methodDecl.getReturnType2();
			Type newReturnType = astRoot.getAST().newPrimitiveType(
					PrimitiveType.FLOAT);
			rewrite.replace(returnType, newReturnType, null);
		}
		{ // remove return type
			MethodDeclaration methodDecl = findMethodDeclaration(type, "hee"); //$NON-NLS-1$
      assertNotNull("Has no return type: hee", methodDecl.getReturnType2()); //$NON-NLS-1$

			// from method to constructor
			rewrite.set(methodDecl, MethodDeclaration.CONSTRUCTOR_PROPERTY,
					Boolean.TRUE, null);
		}
		{ // rename method name
			MethodDeclaration methodDecl = findMethodDeclaration(type, "iee"); //$NON-NLS-1$

			SimpleName name = methodDecl.getName();
			SimpleName newName = ast.newSimpleName("xii"); //$NON-NLS-1$

			rewrite.replace(name, newName, null);
		}
		{ // rename first param & last throw statement
			MethodDeclaration methodDecl = findMethodDeclaration(type, "jee"); //$NON-NLS-1$
			List parameters = methodDecl.parameters();
      assertEquals("must be 3 parameters", 3, parameters.size()); //$NON-NLS-1$
			SingleVariableDeclaration newParam = createNewParam(ast, "m"); //$NON-NLS-1$
			rewrite.replace((ASTNode) parameters.get(0), newParam, null);

			List thrownExceptions = methodDecl.thrownExceptions();
      assertEquals("must be 2 thrown exceptions", 2, thrownExceptions.size()); //$NON-NLS-1$
			Name newThrownException = ast.newSimpleName("ArrayStoreException"); //$NON-NLS-1$
			rewrite.replace((ASTNode) thrownExceptions.get(1),
					newThrownException, null);
		}
		{ // rename first and second param & rename first and last
			// exception
			MethodDeclaration methodDecl = findMethodDeclaration(type, "kee"); //$NON-NLS-1$
			List parameters = methodDecl.parameters();
      assertEquals("must be 3 parameters", 3, parameters.size()); //$NON-NLS-1$
			SingleVariableDeclaration newParam1 = createNewParam(ast, "m1"); //$NON-NLS-1$
			SingleVariableDeclaration newParam2 = createNewParam(ast, "m2"); //$NON-NLS-1$
			rewrite.replace((ASTNode) parameters.get(0), newParam1, null);
			rewrite.replace((ASTNode) parameters.get(1), newParam2, null);

			List thrownExceptions = methodDecl.thrownExceptions();
      assertEquals("must be 3 thrown exceptions", 3, thrownExceptions.size()); //$NON-NLS-1$
			Name newThrownException1 = ast.newSimpleName("ArrayStoreException"); //$NON-NLS-1$
			Name newThrownException2 = ast
					.newSimpleName("InterruptedException"); //$NON-NLS-1$
			rewrite.replace((ASTNode) thrownExceptions.get(0),
					newThrownException1, null);
			rewrite.replace((ASTNode) thrownExceptions.get(2),
					newThrownException2, null);
		}
		{ // rename all params & rename second exception
			MethodDeclaration methodDecl = findMethodDeclaration(type, "lee"); //$NON-NLS-1$
			List parameters = methodDecl.parameters();
      assertEquals("must be 3 parameters", 3, parameters.size()); //$NON-NLS-1$
			SingleVariableDeclaration newParam1 = createNewParam(ast, "m1"); //$NON-NLS-1$
			SingleVariableDeclaration newParam2 = createNewParam(ast, "m2"); //$NON-NLS-1$
			SingleVariableDeclaration newParam3 = createNewParam(ast, "m3"); //$NON-NLS-1$
			rewrite.replace((ASTNode) parameters.get(0), newParam1, null);
			rewrite.replace((ASTNode) parameters.get(1), newParam2, null);
			rewrite.replace((ASTNode) parameters.get(2), newParam3, null);

			List thrownExceptions = methodDecl.thrownExceptions();
      assertEquals("must be 3 thrown exceptions", 3, thrownExceptions.size()); //$NON-NLS-1$
			Name newThrownException = ast.newSimpleName("ArrayStoreException"); //$NON-NLS-1$
			rewrite.replace((ASTNode) thrownExceptions.get(1),
					newThrownException, null);
		}

		TextEdit edits = rewrite.rewriteAST(doc, compilerOptions);
		try {
			edits.apply(doc);
		} catch (BadLocationException e) {
			fail("got a BadLocationException: " + e.getMessage()); //$NON-NLS-1$
		}

		String expected = "package test1;\n" //$NON-NLS-1$
				+ "public abstract class E {\n" //$NON-NLS-1$
				+ "    public float E(int p1, int p2, int p3) {}\n" //$NON-NLS-1$
				+ "    public float gee(int p1, int p2, int p3) throws IllegalArgumentException {}\n" //$NON-NLS-1$
				+ "    public hee(int p1, int p2, int p3) throws IllegalArgumentException {}\n" //$NON-NLS-1$
				+ "    public void xii(int p1, int p2, int p3) throws IllegalArgumentException, IllegalAccessException {}\n" //$NON-NLS-1$
				+ "    public void jee(float m, int p2, int p3) throws IllegalArgumentException, ArrayStoreException {}\n" //$NON-NLS-1$
				+ "    public abstract void kee(float m1, float m2, int p3) throws ArrayStoreException, IllegalAccessException, InterruptedException;\n" //$NON-NLS-1$
				+ "    public abstract void lee(float m1, float m2, float m3) throws IllegalArgumentException, ArrayStoreException, SecurityException;\n" //$NON-NLS-1$
				+ "}\n"; //$NON-NLS-1$

		if (!doc.get().equals(expected)) {
			fail("expecting: " + expected + "=====got:\n" //$NON-NLS-1$ //$NON-NLS-2$
           + doc.get());
		}
	}

	public void check(AjASTRewrite rewrite, Document doc, String expected, Map<String, String> optionsMap) {
		TextEdit edits = rewrite.rewriteAST(doc, optionsMap);
		try {
			edits.apply(doc);
		} catch (BadLocationException e) {
			fail("got a BadLocationException: " + e.getMessage()); //$NON-NLS-1$
		}
		if (!doc.get().equals(expected)) {
			fail("expecting:\n" + expected + "=====got:\n" //$NON-NLS-1$ //$NON-NLS-2$
           + doc.get());
		}
	}

	// ------------------------------------------------------------------------
	// ajh02: methods copied across from ASTRewritingTest.java in
	// org.eclipse.jdt.core.tests.model
	// ------------------------------------------------------------------------
	public static AbstractTypeDeclaration findAbstractTypeDeclaration(
			CompilationUnit astRoot, String simpleTypeName) {
		List types = astRoot.types();
    for (Object type : types) {
      AbstractTypeDeclaration elem = (AbstractTypeDeclaration) type;
      if (simpleTypeName.equals(elem.getName().getIdentifier())) {
        return elem;
      }
    }
		return null;
	}

	public static MethodDeclaration findMethodDeclaration(
			TypeDeclaration typeDecl, String methodName) {
		MethodDeclaration[] methods = typeDecl.getMethods();
    for (MethodDeclaration method : methods) {
      if (methodName.equals(method.getName().getIdentifier())) {
        return method;
      }
    }
		return null;
	}

	public static PointcutDeclaration findPointcutDeclaration(
			AjTypeDeclaration typeDecl, String pointcutName) {
		PointcutDeclaration[] pointcuts = typeDecl.getPointcuts();
    for (PointcutDeclaration pointcut : pointcuts) {
      if (pointcutName.equals(pointcut.getName().getIdentifier())) {
        return pointcut;
      }
    }
		return null;
	}

	public static SingleVariableDeclaration createNewParam(AST ast, String name) {
		SingleVariableDeclaration newParam = ast.newSingleVariableDeclaration();
		newParam.setType(ast.newPrimitiveType(PrimitiveType.FLOAT));
		newParam.setName(ast.newSimpleName(name));
		return newParam;
	}

	protected String evaluateRewrite(ICompilationUnit cu, AjASTRewrite rewrite)
			throws Exception {
		Document document = new Document(cu.getSource());
		TextEdit res = rewrite.rewriteAST(document, cu.getJavaProject().getOptions(true));

		res.apply(document);
		return document.get();
	}

}
