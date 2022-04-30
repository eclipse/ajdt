/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ajdt.internal.launching;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

// Copied from the same class in org.eclipse.pde.core.  Changes marked // AspectJ Change
public class XMLPrintHandler {
	//	used to print XML file
	public static final String XML_COMMENT_END_TAG = "-->"; //$NON-NLS-1$
	public static final String XML_COMMENT_BEGIN_TAG = "<!--"; //$NON-NLS-1$
	public static final String XML_HEAD = "<?xml version=\"1.0\" encoding=\""; //$NON-NLS-1$
	public static final String XML_HEAD_END_TAG = "?>"; //$NON-NLS-1$
	public static final String XML_DBL_QUOTES = "\""; //$NON-NLS-1$
	public static final String XML_SPACE = " "; //$NON-NLS-1$
	public static final String XML_BEGIN_TAG = "<"; //$NON-NLS-1$
	public static final String XML_END_TAG = ">"; //$NON-NLS-1$
	public static final String XML_EQUAL = "="; //$NON-NLS-1$
	public static final String XML_SLASH = "/"; //$NON-NLS-1$

	public static void printBeginElement(Writer xmlWriter, String elementString, String indent, boolean terminate) throws IOException{
		StringBuilder temp = new StringBuilder(indent);
		temp.append(XML_BEGIN_TAG);
		temp.append(elementString);
		if (terminate)
			temp.append(XML_SLASH);
		temp.append(XML_END_TAG);
		temp.append("\n"); //$NON-NLS-1$
		xmlWriter.write(temp.toString());

	}

	public static void printEndElement(Writer xmlWriter, String elementString, String indent) throws IOException{
    String temp = indent + XML_BEGIN_TAG +
                  XML_SLASH + elementString + XML_END_TAG + "\n"; //$NON-NLS-1$
    xmlWriter.write(temp);

	}

	public static void printText(Writer xmlWriter, String text) throws IOException{
		xmlWriter.write(encode(text).toString());
	}

	public static void printComment(Writer xmlWriter, String comment)throws IOException {
    xmlWriter.write(XML_COMMENT_BEGIN_TAG + encode(comment) + XML_COMMENT_END_TAG + "\n" //$NON-NLS-1$
    );
	}

	public static void printHead(Writer xmlWriter, String encoding) throws IOException {
    xmlWriter.write(XML_HEAD + encoding + XML_DBL_QUOTES + XML_HEAD_END_TAG + "\n" //$NON-NLS-1$
    );
	}

	public static String wrapAttributeForPrint(String attribute, String value) {
    String temp = XML_SPACE + attribute + XML_EQUAL + XML_DBL_QUOTES +
                  encode(value) + XML_DBL_QUOTES;
		return temp;

	}

	public static void printNode(Writer xmlWriter, Node node,String encoding, String indent) throws IOException {
		if (node == null) {
			return;
		}

		switch (node.getNodeType()) {
		case Node.DOCUMENT_NODE: {
			printHead(xmlWriter,encoding);
			printNode(xmlWriter, ((Document) node).getDocumentElement(),encoding, indent);
			break;
		}
		case Node.ELEMENT_NODE: {
			//get the attribute list for this node.
			StringBuilder tempElementString = new StringBuilder(node.getNodeName());
			NamedNodeMap attributeList = node.getAttributes();
			if ( attributeList != null ) {
				for(int i= 0; i <attributeList.getLength();i++){
					Node attribute = attributeList.item(i);
					tempElementString.append(wrapAttributeForPrint(attribute.getNodeName(),attribute.getNodeValue()));
				}
			}

			// do this recursively for the child nodes.
			NodeList childNodes = node.getChildNodes();
			int length = childNodes.getLength();
			printBeginElement(xmlWriter,tempElementString.toString(), indent, length == 0);

			for (int i = 0; i < length; i++)
				printNode(xmlWriter, childNodes.item(i),encoding, indent + "\t"); //$NON-NLS-1$

			if (length > 0)
				printEndElement(xmlWriter,node.getNodeName(), indent);
			break;
		}

		case Node.TEXT_NODE: {
			// AspectJ Change - don't print text nodes - avoids formatting problems
//			xmlWriter.write(encode(node.getNodeValue()).toString());
			break;
		}
		// AspectJ Change begin - print out comment nodes
		case Node.COMMENT_NODE: {
      xmlWriter.write(XML_COMMENT_BEGIN_TAG + encode(node.getNodeValue()) + XML_COMMENT_END_TAG + "\n" //$NON-NLS-1$
      );
			break;
		}
		// AspectJ Change end
		default: {
			throw new UnsupportedOperationException("Unsupported XML Node Type."); //$NON-NLS-1$
		}
	}

	}

	public static StringBuffer encode(String value) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '&' :
					buf.append("&amp;"); //$NON-NLS-1$
					break;
				case '<' :
					buf.append("&lt;"); //$NON-NLS-1$
					break;
				case '>' :
					buf.append("&gt;"); //$NON-NLS-1$
					break;
				case '\'' :
					buf.append("&apos;"); //$NON-NLS-1$
					break;
				case '\"' :
					buf.append("&quot;"); //$NON-NLS-1$
					break;
				default :
					buf.append(c);
					break;
			}
		}
		return buf;
	}

	public static void writeFile(Document doc, File file) throws IOException {
    try (OutputStream out = Files.newOutputStream(file.toPath()); Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
      //$NON-NLS-1$
      XMLPrintHandler.printNode(writer, doc, "UTF-8", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }
	}

}
