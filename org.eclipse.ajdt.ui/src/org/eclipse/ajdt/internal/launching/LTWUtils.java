/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Helen Hawkins and Sian January - initial version
 *******************************************************************************/

package org.eclipse.ajdt.internal.launching;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.BuildConfig;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class LTWUtils {

	public final static String AOP_XML_LOCATION = "META-INF/aop-ajc.xml"; //$NON-NLS-1$

	/**
	 * Generate one aop-ajc.xml file for each source directory in the given project.
	 * The aop-ajc.xml files will list all concrete aspects included in the active
	 * build configuration.
	 * @param project
	 */
	public static void generateLTWConfigFile(IJavaProject project) {
		try {
			// Get all the source folders in the project
			IPackageFragmentRoot[] roots = project.getAllPackageFragmentRoots();
      for (IPackageFragmentRoot root : roots) {
        if (!(root instanceof JarPackageFragmentRoot) && root.getJavaProject().equals(project)) {
          List<IType> aspects = getAspects(root);
          String path;
          if (root.getElementName().trim().equals("")) { //$NON-NLS-1$
            path = AOP_XML_LOCATION;
          }
          else {
            path = root.getElementName().trim().concat("/").concat(AOP_XML_LOCATION); //$NON-NLS-1$
          }
          IFile ltwConfigFile = (IFile) project.getProject().findMember(path);

          // If the source folder does not already contain an aop-ajc.xml file:
          if (ltwConfigFile == null) {
            if (aspects.size() != 0) { // If there are aspects in the list

              // Create the META-INF folder and the aop-ajc.xml file
              IFolder metainf = (IFolder) ((Workspace) ResourcesPlugin.getWorkspace()).
                newResource(project.getPath().append("/" + root.getElementName() + "/META-INF"), //$NON-NLS-1$ //$NON-NLS-2$
                  IResource.FOLDER);
              IFile aopFile = (IFile) ((Workspace) ResourcesPlugin.getWorkspace()).
                newResource(project.getPath().append(path),
                  IResource.FILE);
              if (metainf == null || !metainf.exists()) {
                metainf.create(true, true, null);
              }
              aopFile.create(new ByteArrayInputStream(new byte[0]), true, null);
              project.getProject().refreshLocal(4, null);

              // Add the xml content to the aop-ajc.xml file
              addAspectsToLTWConfigFile(false, aspects, aopFile);
              copyToOutputFolder(aopFile, project, root.getRawClasspathEntry());
            }

            // Otherwise update the existing file
          }
          else {
            addAspectsToLTWConfigFile(true, aspects, ltwConfigFile);
            copyToOutputFolder(ltwConfigFile, project, root.getRawClasspathEntry());
          }
        }
      }
		} catch (Exception ignored) {
		}

	}


	private static void copyToOutputFolder(IFile file, IJavaProject javaProject, IClasspathEntry srcEntry) throws CoreException {
		IPath outputPath = srcEntry.getOutputLocation();
        if (outputPath == null) {
			outputPath = javaProject.getOutputLocation();
		}
        outputPath = outputPath.removeFirstSegments(1).makeRelative();
        IContainer outputFolder = getContainerForGivenPath(outputPath,javaProject.getProject());
        IContainer srcContainer = getContainerForGivenPath(srcEntry.getPath().removeFirstSegments(1),javaProject.getProject());
        if (!outputFolder.equals(srcContainer)) {
        	IResource outputFile = outputFolder.getFile(new Path(AOP_XML_LOCATION));
			if (outputFile.exists()) {
				AJLog.log("Deleting existing file " + outputFile);//$NON-NLS-1$
				outputFile.delete(IResource.FORCE, null);
			}
			AJLog.log("Copying added file " + file);//$NON-NLS-1$
			IFolder metainf = (IFolder) ((Workspace)ResourcesPlugin.getWorkspace()).
				newResource(new Path(outputFolder.getFullPath() + "/META-INF"), //$NON-NLS-1$
					IResource.FOLDER);
			if(!metainf.exists()) {
				metainf.create(true,true,null);
			}
			file.copy(outputFile.getFullPath(), IResource.FORCE, null);
			outputFile.setDerived(true);
			outputFile.refreshLocal(IResource.DEPTH_ZERO, null);
        }
	}

	private static IContainer getContainerForGivenPath(IPath path, IProject project) {
		if (path.toOSString().equals("")) { //$NON-NLS-1$
			return project;
		}
		return project.getFolder(path);
	}

	/**
	 * Create a new xml document with an 'aspectj' element that contains
	 * one 'aspects' element.
	 * @throws ParserConfigurationException
	 */
	private static Document createNewXMLDocument() throws ParserConfigurationException {
		// Create the document and add a root 'aspectj' element with one 'aspects' child element
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		DOMImplementation impl = builder.getDOMImplementation();
		Document doc = impl.createDocument(null, "aspectj", null);	 //$NON-NLS-1$
		Element root = doc.getDocumentElement();
		Element aspectsElement = doc.createElement("aspects"); //$NON-NLS-1$
		root.appendChild(aspectsElement);
		return doc;
	}

	/**
	 * Updates the given aop-ajc.xml file with the current aspects to be included.
	 * The file should exist when this method is called.
	 * @param readFileFirst - if true then file already contains xml content
	 * @param aspects - the list of aspects (IAspectElement)
	 * @param configFile - the file
	 * @throws Exception
	 */
	private static void addAspectsToLTWConfigFile(boolean readFileFirst, List<IType> aspects, IFile configFile)
		throws Exception
	{
		Document doc;
		if (readFileFirst) { // If the aop-ajc.xml file already exists load the existing document
			doc = readFile(configFile);
		} else { // Otherwise create a new document
			doc = createNewXMLDocument();
		}

		if (doc == null) {
			return;
		}

		NodeList children = doc.getDocumentElement().getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeName().equals("aspects")) { //$NON-NLS-1$
				// Delete any existing aspects
				if (child.hasChildNodes()) {
		            Node root = child.getFirstChild();
		            while  (root != null){
		                child.removeChild(root);
		                root = child.getFirstChild();
		            }
				}
				// Add all the current aspects to the document
				for (IType aspect : aspects) {
					Element grandChild = doc.createElement("aspect"); //$NON-NLS-1$
					grandChild.setAttribute("name", getFullyQualifiedName(aspect)); //$NON-NLS-1$
					child.appendChild(grandChild);
				}
			}
		}

		// Write out the document
		File file = new File(getFileName(configFile));
		XMLPrintHandler.writeFile(doc, file);
		configFile.refreshLocal(1,null);
	}

	/**
	 * Creates an XML document from the given file
	 * @param configFile
	 * @return
	 * @throws Exception if there was an error reading the file or creating the Document
	 */
	private static Document readFile(IFile configFile) throws Exception {
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		builder.setErrorHandler(new AOPXMLErrorHandler());
		return builder.parse(configFile.getContents());
	}

	/**
	 * Get the OS specific full path for the given file
	 * @param configFile
	 * @return
	 */
	private static String getFileName(IFile configFile) {
		return ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString()
			+ configFile.getFullPath().toOSString();
	}

	/**
	 * Get the fully qualified name for the given aspect (e.g. package.Class
	 * or package.EnclosingClass.InnerClass)
	 * @param aspect
	 * @return
	 */
	private static String getFullyQualifiedName(IType aspect) {
		StringBuilder sb = new StringBuilder();
		IJavaElement parent = aspect.getCompilationUnit().getParent();
		if (parent instanceof IPackageFragment
				&& !parent.getElementName().equals("")) { //$NON-NLS-1$
			sb.append(parent.getElementName());
			sb.append("."); //$NON-NLS-1$
		}
		sb.append(getFullTypeName(aspect));
		return sb.toString();
	}

	/**
	 * Get the full type name for the given type, including any enclosing classes.
	 * @param element
	 * @return
	 */
	private static String getFullTypeName(IType element) {
	    if (element == null) {
	        return "";
	    }
		if (element.getParent() instanceof IType) {
			return getFullTypeName((IType)element.getParent()) + "." + element.getElementName(); //$NON-NLS-1$
		} else {
			return element.getElementName();
		}
	}

	/**
	 * Get a list of all the aspects found in the given source
	 * directory, which are included in the current build.
	 * @param root
	 * @return List of AspectElements
	 * @throws CoreException
	 */
	public static List<IType> getAspects(final IPackageFragmentRoot root) throws CoreException {
		final List<IType> aspects = new ArrayList<>();
		final Set<IFile> includedFiles = BuildConfig.getIncludedSourceFiles(root.getJavaProject().getProject());
		root.getResource().accept(resource -> {
			if (includedFiles.contains(resource)) {
				AJCompilationUnit ajcu = AJCompilationUnitManager.INSTANCE.getAJCompilationUnit((IFile) resource);
				if (ajcu != null) {
					try {
						IType[] types = ajcu.getAllAspects();
						aspects.addAll(Arrays.asList(types));
					}
					catch (JavaModelException ignored) { }
				}
				else {
					ICompilationUnit cu = JavaCore.createCompilationUnitFrom((IFile) resource);
					if (cu != null) {
						Set<IType> types = AJProjectModelFactory.getInstance().getModelForJavaElement(cu).aspectsForFile(cu);
						aspects.addAll(types);
					}
				}
			}
			return resource.getType() == IResource.FOLDER || resource.getType() == IResource.PROJECT;
		});
		return aspects;
	}

	/**
	 * Error handler class for AOP XML parsing exceptions
	 */
	private static class AOPXMLErrorHandler extends DefaultHandler {

		/**
		 * Throws a more detailed exception when an error occurs parsing a file.
		 * @param exception - the Exception input
		 * @throws SAXException - more detained Exception
		 * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
		 */
		public void error(SAXParseException exception) throws SAXException {
			throw new AOPXMLException(
				"A problem occurred parsing aop-ajc.xml file " //$NON-NLS-1$
					+ exception.getSystemId().substring(
						exception.getSystemId().indexOf("file:///") + 8) //$NON-NLS-1$
					+ "[" //$NON-NLS-1$
					+ exception.getLineNumber()
					+ "," //$NON-NLS-1$
					+ exception.getColumnNumber()
					+ "]"  //$NON-NLS-1$
					+ System.getProperty("line.separator") //$NON-NLS-1$
					+ exception.getMessage().substring(
						exception.getMessage().indexOf(':') + 2));
		}


		/**
		 * Throws a more detailed exception when a warning occurs while
		 * parsing a file
		 * @param exception - inout
		 * @throws SAXException - more detailed output
		 * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
		 */
		public void warning(SAXParseException exception) throws SAXException {
			error(exception);
		}


		/**
		 * Throws a more detailed exception when a fatal error occurs while
		 * parsing a file
		 * @param exception - input
		 * @throws SAXException - more detailed output
		 * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
		 */
		public void fatalError(SAXParseException exception) throws SAXException	{
			error(exception);
		}

	}

	/**
	 * Exception used in error handler to hold details of an XML parsing error
	 */
	private static class AOPXMLException extends SAXException{

		private static final long serialVersionUID = 4296332843488816647L;

		/**
		 * Constructor
		 * @param input - input String
		 */
		AOPXMLException(String input){
			super(input);
		}

	}

}
