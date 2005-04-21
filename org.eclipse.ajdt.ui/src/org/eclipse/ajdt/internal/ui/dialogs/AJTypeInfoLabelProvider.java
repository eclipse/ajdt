/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: Sian January - initial version
 ******************************************************************************/
package org.eclipse.ajdt.internal.ui.dialogs;

import org.aspectj.asm.IProgramElement;
import org.eclipse.ajdt.internal.ui.resources.AJDTIcon;
import org.eclipse.ajdt.internal.ui.resources.AspectJImages;
import org.eclipse.jdt.internal.ui.util.TypeInfoLabelProvider;
import org.eclipse.swt.graphics.Image;

/**
 * @author Sian
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class AJTypeInfoLabelProvider extends TypeInfoLabelProvider {

	private static final Image ASPECT_ICON = ((AJDTIcon)AspectJImages.registry().getIcon(IProgramElement.Kind.ASPECT)).getImageDescriptor().createImage();

	/**
	 * @param flags
	 */
	public AJTypeInfoLabelProvider(int flags) {
		super(flags);
	}
	
	/* non java-doc
	 * @see ILabelProvider#getImage
	 */	
	public Image getImage(Object element) {
		if(element instanceof AJCUTypeInfo) {
			if (((AJCUTypeInfo)element).isAspect()) {
				return ASPECT_ICON;
			}
		}
		return super.getImage(element);
	}	

}
