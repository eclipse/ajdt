/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Luzius Meisser - initial implementation
 *******************************************************************************/

package org.eclipse.ajdt.internal.ui.lazystart;

import java.util.ArrayList;

import org.aspectj.asm.IProgramElement;
import org.eclipse.ajdt.core.AJProperties;
import org.eclipse.ajdt.core.AopXmlPreferences;
import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.ajdt.core.BuildConfig;
import org.eclipse.ajdt.core.javaelements.AJCodeElement;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AdviceElement;
import org.eclipse.ajdt.core.javaelements.DeclareElement;
import org.eclipse.ajdt.core.javaelements.IAspectJElement;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.internal.ui.resources.AJDTIcon;
import org.eclipse.ajdt.internal.ui.resources.AspectJImages;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDescriptorRegistry;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.viewsupport.TreeHierarchyLayoutProblemsDecorator;
import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.decorators.DecoratorManager;

/*
 * Loading classes in this lazystart package does not immediately cause the
 * plugin to active (as specified in MANIFEST.MF). This is done to avoid early
 * activation of AJDT plugins. Once AJDT classes outside this package are
 * referred to, the plugins are then activated.
 */
/**
 * @see ILabelDecorator
 */
public class ImageDecorator implements ILabelDecorator {

	private final ArrayList listeners;
	private ImageDescriptorRegistry fRegistry;
	private boolean preventRecursion = false;
	private final TreeHierarchyLayoutProblemsDecorator problemsDecorator;
	private final DecoratorManager decman;

	/**
	 *
	 */
	public ImageDecorator() {
		listeners = new ArrayList(2);
		problemsDecorator = new TreeHierarchyLayoutProblemsDecorator();
		decman = WorkbenchPlugin.getDefault().getDecoratorManager();
	}

	public void addListener(ILabelProviderListener listener)  {
		listeners.add(listener);
	}

	public void dispose()  {
	}

	public boolean isLabelProperty(Object element, String property)  {
		return false;
	}

	public void removeListener(ILabelProviderListener listener)  {
		listeners.remove(listener);
	}

	/**
	 * @see ILabelDecorator#decorateImage
	 */
	public Image decorateImage(Image image, Object element)  {
		if (preventRecursion)
			return null;

		// only run the decorator if the bundle is already active
		if (!Utils.isBundleActive()) {
			return null;
		}

		Image img = null;
		if (element instanceof ICompilationUnit){
			ICompilationUnit comp = (ICompilationUnit)element;
			IFile file = null;
			try {
				file = (IFile) comp.getCorrespondingResource();
			} catch (JavaModelException ignored) {
			}
			if(file != null) {
				if(comp instanceof AJCompilationUnit) {
					if (BuildConfig.isIncluded(file)) {
						Rectangle rect = image.getBounds();
						img = getImageLabel(getJavaImageDescriptor(AspectJImages.ASPECTJ_FILE.getImageDescriptor(), rect, 0));
					} else {
						Rectangle rect = image.getBounds();
						img = getImageLabel(getJavaImageDescriptor(AspectJImages.EXCLUDED_ASPECTJ_FILE.getImageDescriptor(), rect, 0));
					}
				}
			}
		} else if (element instanceof IFile) {
			IFile file= (IFile) element;
			if (file.getFileExtension() != null) {
				if (file.getFileExtension().equals(AJProperties.EXTENSION)) {
					img = getImageLabel(AspectJImages.BC_FILE.getImageDescriptor());
				} else if (file.getFileExtension().equals("jar") || file.getFileExtension().equals("zip")) { //$NON-NLS-1$ //$NON-NLS-2$
					// TODO: decorate out-jars?
				} else if (file.getFileExtension().equals("xml")) {
				    // maybe this is an aop.xml that is part of the build config
				    if (new AopXmlPreferences(file.getProject()).isAopXml(file)) {
				        img = getImageLabel(AspectJImages.AOP_XML.getImageDescriptor());
				    }
				}
			}
		} else if (element instanceof JarPackageFragmentRoot) {
		    JarPackageFragmentRoot root = (JarPackageFragmentRoot) element;
			try {
				IClasspathEntry entry = root.getRawClasspathEntry();
				if (entry != null) {
				    IResource resource = root.getResource();
				    String name = resource == null ? root.getElementName() : resource.getName();
					if (AspectJCorePreferences.isOnAspectpathWithRestrictions(entry, name)) {
						img = getImageLabel(AspectJImages.JAR_ON_ASPECTPATH.getImageDescriptor());
					} else if (AspectJCorePreferences.isOnInpathWithRestrictions(entry, name)) {
						img = getImageLabel(AspectJImages.JAR_ON_INPATH.getImageDescriptor());
					}
				}
			} catch (JavaModelException ignored) {
			}
		} else if (element instanceof AJCodeElement) {
			img = getImageLabel(AspectJImages.AJ_CODE.getImageDescriptor());
		} else if (element instanceof IAspectJElement) {
			try {
				IAspectJElement ajElem = (IAspectJElement)element;
				if(ajElem.getJavaProject().getProject().exists()) {
					IProgramElement.Accessibility acceb = ajElem.getAJAccessibility();
					AJDTIcon icon;
					if (acceb == null){
						if (ajElem instanceof AdviceElement) {
						    boolean hasTest = AJProjectModelFactory.getInstance().getModelForJavaElement(ajElem)
	                        .hasRuntimeTest(ajElem);
							icon = AspectJImages.instance().getAdviceIcon(ajElem.getAJExtraInformation(), hasTest);
						} else if (ajElem instanceof IntertypeElement) {
							icon = AspectJImages.instance().getStructureIcon(ajElem.getAJKind(), ajElem.getAJAccessibility());
						} else if (ajElem instanceof DeclareElement) {
							icon = AspectJImages.instance().getStructureIcon(ajElem.getAJKind(), ajElem.getAJAccessibility());
						} else {
							icon = AspectJImages.instance().getIcon(ajElem.getAJKind());
						}
					} else {
						icon = AspectJImages.instance().getStructureIcon(ajElem.getAJKind(), ajElem.getAJAccessibility());
					}
					if (icon != null){
						img = getImageLabel(getJavaImageDescriptor(icon.getImageDescriptor(), image.getBounds(), computeJavaAdornmentFlags(ajElem)));
					}
				}
			} catch (JavaModelException ignored) {
			}
		}

		if (img != null){
			preventRecursion = true;

			//the Java ProblemsDecorator is not registered in the official
			//decorator list of eclipse, so we need it to call ourself.
			//problem: if jdt includes more decorators, we won't know it.
			img = problemsDecorator.decorateImage(img, element);

			//apply standard decorators (eg cvs)
			img = decman.decorateImage(img, element);
			preventRecursion = false;
			return img;
		}
		return null;
	}

	private Image getImageLabel(ImageDescriptor descriptor){
		if (descriptor == null)
			return null;
		return getRegistry().get(descriptor);
	}

	private ImageDescriptorRegistry getRegistry() {
		if (fRegistry == null) {
			fRegistry= JavaPlugin.getImageDescriptorRegistry();
		}
		return fRegistry;
	}

	public static ImageDescriptor getJavaImageDescriptor(ImageDescriptor descriptor, Rectangle rect, int adorflags) {
		int flags = (rect.width == 16)?JavaElementImageProvider.SMALL_ICONS:0;
		Point size= useSmallSize(flags) ? JavaElementImageProvider.SMALL_SIZE : JavaElementImageProvider.BIG_SIZE;
		return new JavaElementImageDescriptor(descriptor, adorflags, size);
	}

	private static boolean useSmallSize(int flags) {
		return (flags & JavaElementImageProvider.SMALL_ICONS) != 0;
	}

	/**
	 * @see ILabelDecorator#decorateText
	 */
	public String decorateText(String text, Object element)  {
		// 152922: make sure the ajdt.ui bundle is activated if
		// there is an open AspectJ project present
		if (element instanceof IJavaProject) {
			IJavaProject jp = (IJavaProject)element;
			if (Utils.isAJProject(jp.getProject())) {
				// causes bundle activation
				AspectJUIPlugin.getDefault();
			}
		}
		// otherwise only run the decorator if the bundle is already active
		if (!Utils.isBundleActive()) {
			return null;
		}

		// bug 158937
		if (element instanceof DeclareElement) {
			if (text.endsWith("()")) { //$NON-NLS-1$
				return text.substring(0,text.length()-2);
			}
		} else if (element instanceof IntertypeElement) {
			IntertypeElement itd = (IntertypeElement)element;
			try {
				if (itd.getAJKind() == IProgramElement.Kind.INTER_TYPE_FIELD) {
					if (text.endsWith("()")) { //$NON-NLS-1$
						return text.substring(0,text.length()-2);
					}
				}
			} catch (JavaModelException ignored) {
			}
		}
		return null;
	}

	private int computeJavaAdornmentFlags(IJavaElement element) {
		int flags= 0;
		if (element instanceof IMember) {
			try {
				IMember member= (IMember) element;

				if (element.getElementType() == IJavaElement.METHOD && ((IMethod)element).isConstructor())
					flags |= JavaElementImageDescriptor.CONSTRUCTOR;

				int modifiers= member.getFlags();
				if (Flags.isAbstract(modifiers) && confirmAbstract(member))
					flags |= JavaElementImageDescriptor.ABSTRACT;
				if (Flags.isFinal(modifiers) || isInterfaceField(member))
					flags |= JavaElementImageDescriptor.FINAL;
				if (Flags.isSynchronized(modifiers) && confirmSynchronized(member))
					flags |= JavaElementImageDescriptor.SYNCHRONIZED;
				if (Flags.isStatic(modifiers) || isInterfaceField(member))
					flags |= JavaElementImageDescriptor.STATIC;

				if (Flags.isDeprecated(modifiers))
					flags |= JavaElementImageDescriptor.DEPRECATED;

				if (member.getElementType() == IJavaElement.TYPE) {
					if (JavaModelUtil.hasMainMethod((IType) member)) {
						flags |= JavaElementImageDescriptor.RUNNABLE;
					}
				}
			} catch (JavaModelException e) {
				// do nothing. Can't compute runnable adornment or get flags
				// can be ignored
			}
		}
		return flags;
	}

	private static boolean confirmAbstract(IMember element) throws JavaModelException {
		// never show the abstract symbol on interfaces or members in interfaces
		if (element.getElementType() == IJavaElement.TYPE) {
			return ((IType) element).isClass();
		}
		return element.getDeclaringType().isClass();
	}

	private static boolean isInterfaceField(IMember element) throws JavaModelException {
		// always show the final && static symbol on interface fields
		if (element.getElementType() == IJavaElement.FIELD) {
			return element.getDeclaringType().isInterface();
		}
		return false;
	}

	private static boolean confirmSynchronized(IJavaElement member) {
		// Synchronized types are allowed but meaningless.
		return member.getElementType() != IJavaElement.TYPE;
	}
}
