/*******************************************************************************
 * Copyright (c) 2005, 2008 IBM Corporation, SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman  - initial version
 *     Andrew Eisenberg - MockITDConvertingParser and more tests
 *******************************************************************************/
package org.eclipse.ajdt.core.tests.codeconversion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.eclipse.ajdt.core.AspectJCore;
import org.eclipse.ajdt.core.codeconversion.AspectsConvertingParser;
import org.eclipse.ajdt.core.codeconversion.ConversionOptions;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class AspectsConvertingParserTest extends AJDTCoreTestCase {

	public void testBug110751() {
		String source = "public aspect Aspect {\n" //$NON-NLS-1$
				+ "	private void Cloneable.loop(Object[] objects) throws IOException {\n" //$NON-NLS-1$
				+ "		for (Object obj : objects) {\n" //$NON-NLS-1$
				+ "			obj.toString();\n" //$NON-NLS-1$
				+ "		}\n" //$NON-NLS-1$
				+ "	}\n" //$NON-NLS-1$
				+ "};\n"; //$NON-NLS-1$
		ConversionOptions conversionOptions = ConversionOptions.STANDARD;
		AspectsConvertingParser conv = new AspectsConvertingParser(source
				.toCharArray());
		conv.convert(conversionOptions);
		String converted = new String(conv.content);
		assertTrue("Parser failed to handle enhanced for loop", //$NON-NLS-1$
      converted.contains("for (Object obj : objects)")); //$NON-NLS-1$
	}

	public void testBug118052() {
		String source = "public aspect Aspect pertypewithin(type_pattern){\n" //$NON-NLS-1$
				+ "	public static void main(String[] args) {\n" //$NON-NLS-1$
				+ "	}\n" //$NON-NLS-1$
				+ "};\n"; //$NON-NLS-1$
		ConversionOptions conversionOptions = ConversionOptions.STANDARD;
		AspectsConvertingParser conv = new AspectsConvertingParser(source
				.toCharArray());
		conv.convert(conversionOptions);
		String converted = new String(conv.content);
    assertFalse("Parser failed to handle pertypewithin", converted.contains("pertypewithin")); //$NON-NLS-1$
	}

	public void testBug134343() {
		String source = "public aspect Aspect {\n" //$NON-NLS-1$
				+ "	   declare parents : foo.inspector..* &&\n" //$NON-NLS-1$
				+ " (junit.framework.TestCase+ || *..Test*\n" //$NON-NLS-1$
    		    + " ||foo.inspector.test..*) implements Serializable;\n" //$NON-NLS-1$
				+ "};\n"; //$NON-NLS-1$
		ConversionOptions conversionOptions = ConversionOptions.STANDARD;
		AspectsConvertingParser conv = new AspectsConvertingParser(source
				.toCharArray());
		conv.convert(conversionOptions);
		String converted = new String(conv.content);
    assertFalse("Parser should not have considered Test as an import", converted.contains("Test x")); //$NON-NLS-1$
    assertFalse("Parser should not have considered TestCase as an import", converted.contains("TestCase x")); //$NON-NLS-1$
	}

	/**
	 * ensure that the RHS of an assignment is not processed as a potential ITD
	 * eg- int x = Foo.y;  should not be converted into int x = Foo$y;
	 */
	public void testRHS() {
	    String source =   "aspect Aspect { int x = Foo.y; }";
	    String expected = "class  Aspect { int x = Foo.y; }";

        ConversionOptions conversionOptions = ConversionOptions.STANDARD;
        AspectsConvertingParser conv = new AspectsConvertingParser(source
                .toCharArray());
        conv.convert(conversionOptions);
        String converted = new String(conv.content);
        assertEquals("Improperly converted", expected, converted); //$NON-NLS-1$
	}


	/**
	 * test that ':' are properly handled in switch statements
	 */
    public void testBug260914() {
        String source =   "aspect Aspect { pointcut foo() : execution(); \n void doNothing() { char i = 'o'; switch(i) { case 'o': break; default: break; } }}";
        String expected = "class  Aspect { pointcut foo()              ; \n void doNothing() { char i = 'o'; switch(i) { case 'o': break; default: break; } }}";

        ConversionOptions conversionOptions = ConversionOptions.STANDARD;
        AspectsConvertingParser conv = new AspectsConvertingParser(source
                .toCharArray());
        conv.convert(conversionOptions);
        String converted = new String(conv.content);
        assertEquals("Improperly converted", expected, converted); //$NON-NLS-1$
    }

    /**
     * test that '?' in type parameters are converted correctly
     */
    public void testBug282948() throws Exception {
        IProject bug282948 = createPredefinedProject("Bug282948");
        IFile file = bug282948.getFile("src/RR.aj");
        String source =   getContents(file);

        ConversionOptions conversionOptions = ConversionOptions.STANDARD;
        AspectsConvertingParser conv = new AspectsConvertingParser(source
                .toCharArray());
        conv.convert(conversionOptions);
        String converted = new String(conv.content);

        // now convert using a regular Java parser:
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setSource(converted.toCharArray());
        CompilationUnit result = (CompilationUnit) parser.createAST(null);
        if (result.getProblems().length > 0) {
            fail("Improperly converted.  Original:\n" + source + "\nConverted:\n" + converted); //$NON-NLS-1$
        }
    }


	/**
	 * inserts arbitrary conversion text at the proper places in the code
	 * @author andrew
	 *
	 */
  static class MockITDConvertingParser extends AspectsConvertingParser {
        public MockITDConvertingParser(char[] content) {
            super(content);
        }

        protected char[] getInterTypeDecls(char[] currentTypeName) {
            return "Here I am!".toCharArray();
        }
    }

  static class MockDeclareConvertingParser extends AspectsConvertingParser {
        public MockDeclareConvertingParser(char[] content) {
            super(content);
        }

        public char[] createImplementExtendsITDs(char[] typeName) {
            return "Here I am!".toCharArray();
        }
    }

  /**
	 * test that ITD insertions appear in the right place
	 */
	public void testITDInsertions() {
        char[] contents = "public class Foo { aspect Bar { interface X { } } } @interface Y { } ".toCharArray();
        char[] expectedContents = "public class Foo {Here I am! class  Bar {Here I am! interface X {Here I am! } } } @interface Y {Here I am! } ".toCharArray();

        AspectsConvertingParser parser = new MockITDConvertingParser(contents);

	    parser.convert(ConversionOptions.CODE_COMPLETION);
	    char[] convertedContents = parser.content;
        System.out.println(new String(convertedContents));
        System.out.println(new String(expectedContents));
	    assertEquals(new String(expectedContents), new String(convertedContents));
	}



    public void testSuperTypeInsertionsNoExistingClause() {
        char[] contents = "public class Foo { aspect Bar { interface X { } } } @interface Y { } ".toCharArray();
        char[] expectedContents = "public Here I am!{ Here I am!{ Here I am!{ } } } @Here I am!{ } ".toCharArray();

        AspectsConvertingParser parser = new MockDeclareConvertingParser(contents);
        parser.convert(ConversionOptions.CODE_COMPLETION);
        char[] convertedContents = parser.content;
        assertEquals(new String(expectedContents), new String(convertedContents));
    }
    public void testSuperTypeInsertionsExtendsExistingClause() {
        char[] contents = "public class Foo extends X { aspect Bar extends X { interface X extends Y, Z { } } } @interface Z { } ".toCharArray();
        char[] expectedContents = "public Here I am!{ Here I am!{ Here I am!{ } } } @Here I am!{ } ".toCharArray();

        AspectsConvertingParser parser = new MockDeclareConvertingParser(contents);
        parser.convert(ConversionOptions.CODE_COMPLETION);
        char[] convertedContents = parser.content;
        assertEquals(new String(expectedContents), new String(convertedContents));
    }
    public void testSuperTypeInsertionsImplementsExistingClause() {
        char[] contents = "public class Foo implements X{ aspect Bar implements X{ interface X extends X, Y{ } } } @interface Z{ } ".toCharArray();
        char[] expectedContents = "public Here I am!{ Here I am!{ Here I am!{ } } } @Here I am!{ } ".toCharArray();

        AspectsConvertingParser parser = new MockDeclareConvertingParser(contents);
        parser.convert(ConversionOptions.CODE_COMPLETION);
        char[] convertedContents = parser.content;
        assertEquals(new String(expectedContents), new String(convertedContents));
    }
    public void testSuperTypeInsertionsExtendsAndImplementsExistingClause() {
        char[] contents = "public class Foo extends X implements Y { aspect Bar extends X implements Y, B { interface X extends X, A, B { } } } @interface Z{ }".toCharArray();
        char[] expectedContents = "public Here I am!{ Here I am!{ Here I am!{ } } } @Here I am!{ }".toCharArray();

        AspectsConvertingParser parser = new MockDeclareConvertingParser(contents);
        parser.convert(ConversionOptions.CODE_COMPLETION);
        char[] convertedContents = parser.content;
        assertEquals(new String(expectedContents), new String(convertedContents));
    }

    public void testBug273914() throws Exception {
        IProject project = createPredefinedProject("Bug273914");
        IFile file = project.getFile("src/DeclaresITDs.aj");
        ICompilationUnit unit = (ICompilationUnit) AspectJCore.create(file);
        String contents = getContents(file);
        AspectsConvertingParser parser = new AspectsConvertingParser(contents.toCharArray());
        parser.setUnit(unit);
        parser.convert(ConversionOptions.CODE_COMPLETION);
        String convertedContents = new String(parser.content);
        assertTrue("Incorrect extends/implements clause for class A\n" + convertedContents, convertedContents.contains("class A {"));
        assertTrue("Incorrect extends/implements clause for interface B\n" + convertedContents, convertedContents.contains("interface B {"));
        assertTrue("Incorrect extends/implements clause for class W\n" + convertedContents, convertedContents.contains("class W extends A {"));
        assertTrue("Incorrect extends/implements clause for interface X\n" + convertedContents, convertedContents.contains("interface X {"));
        assertTrue("Incorrect extends/implements clause for class Y\n" + convertedContents, convertedContents.contains("class Y extends DeclaresITDs.W implements DeclaresITDs.X, B {"));
        assertTrue("Incorrect extends/implements clause for interface Z\n" + convertedContents, convertedContents.contains("interface Z extends DeclaresITDs.X, B {"));
        assertTrue("Incorrect extends/implements clause for class C\n" + convertedContents, convertedContents.contains("class C extends DeclaresITDs.W implements DeclaresITDs.X, DeclaresITDs.B {"));
        assertTrue("Incorrect extends/implements clause for interface D\n" + convertedContents, convertedContents.contains("interface D extends DeclaresITDs.X {"));

    }

    private String getContents(IFile javaFile) throws CoreException, IOException {
        InputStream is = javaFile.getContents();
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder buffer= new StringBuilder();
        char[] readBuffer= new char[2048];
        int n= br.read(readBuffer);
        while (n > 0) {
            buffer.append(readBuffer, 0, n);
            n= br.read(readBuffer);
        }
        return buffer.toString();
    }

}
