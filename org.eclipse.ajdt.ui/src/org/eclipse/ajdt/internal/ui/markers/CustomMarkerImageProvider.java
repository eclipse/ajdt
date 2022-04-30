/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Sian January - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.markers;

import java.net.MalformedURLException;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.internal.ui.resources.AspectJImages;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.texteditor.IAnnotationImageProvider;
import org.eclipse.ui.texteditor.SimpleMarkerAnnotation;

public class CustomMarkerImageProvider implements IAnnotationImageProvider {

	public static final String IMAGE_LOCATION_ATTRIBUTE = "Image_Location"; //$NON-NLS-1$

	public static final ImageDescriptor[] sampleImageDescriptors = new ImageDescriptor[] {
		AspectJImages.ARROW_SAMPLE.getImageDescriptor(),
		AspectJImages.BULB_SAMPLE.getImageDescriptor(),
		AspectJImages.CIRCLE_SAMPLE.getImageDescriptor(),
		AspectJImages.CLOCK_SAMPLE.getImageDescriptor(),
		AspectJImages.COG_SAMPLE.getImageDescriptor(),
		AspectJImages.CROSS_SAMPLE.getImageDescriptor(),
		AspectJImages.DEBUG_SAMPLE.getImageDescriptor(),
		AspectJImages.DOCUMENT_SAMPLE.getImageDescriptor(),
		AspectJImages.EXCLAMATION_SAMPLE.getImageDescriptor(),
		AspectJImages.KEY_SAMPLE.getImageDescriptor(),
		AspectJImages.PLUS_SAMPLE.getImageDescriptor(),
		AspectJImages.READWRITE_SAMPLE.getImageDescriptor(),
		AspectJImages.TICK_SAMPLE.getImageDescriptor(),
		AspectJImages.TRACE_SAMPLE.getImageDescriptor(),
		AspectJImages.JUNIT_SAMPLE.getImageDescriptor(),
		AspectJImages.PROGRESS_SAMPLE.getImageDescriptor()
	};

	public static final String[] sampleImageLocations = new String[] {
		"SAMPLE_0", //$NON-NLS-1$
		"SAMPLE_1", //$NON-NLS-1$
		"SAMPLE_2", //$NON-NLS-1$
		"SAMPLE_3", //$NON-NLS-1$
		"SAMPLE_4", //$NON-NLS-1$
		"SAMPLE_5", //$NON-NLS-1$
		"SAMPLE_6", //$NON-NLS-1$
		"SAMPLE_7", //$NON-NLS-1$
		"SAMPLE_8", //$NON-NLS-1$
		"SAMPLE_9", //$NON-NLS-1$
		"SAMPLE_10", //$NON-NLS-1$
		"SAMPLE_11", //$NON-NLS-1$
		"SAMPLE_12", //$NON-NLS-1$
		"SAMPLE_13", //$NON-NLS-1$
		"SAMPLE_14", //$NON-NLS-1$
		"SAMPLE_15" //$NON-NLS-1$
	};

	public static String[] sampleImageNames = new String[] {
			UIMessages.CustomMarkerImageProvider_Arrow,
			UIMessages.CustomMarkerImageProvider_Bulb,
			UIMessages.CustomMarkerImageProvider_Circle,
			UIMessages.CustomMarkerImageProvider_Clock,
			UIMessages.CustomMarkerImageProvider_Cog,
			UIMessages.CustomMarkerImageProvider_Cross,
			UIMessages.CustomMarkerImageProvider_Debug,
			UIMessages.CustomMarkerImageProvider_Document,
			UIMessages.CustomMarkerImageProvider_Exclamation,
			UIMessages.CustomMarkerImageProvider_Key,
			UIMessages.CustomMarkerImageProvider_Plus,
			UIMessages.CustomMarkerImageProvider_ReadWrite,
			UIMessages.CustomMarkerImageProvider_Tick,
			UIMessages.CustomMarkerImageProvider_Pencil,
			UIMessages.CustomMarkerImageProvider_JUnit,
			UIMessages.CustomMarkerImageProvider_Progress
		};

	public Image getManagedImage(Annotation annotation) {
		if(annotation instanceof SimpleMarkerAnnotation) {
			String imageLocation = ((SimpleMarkerAnnotation)annotation).getMarker().getAttribute(IMAGE_LOCATION_ATTRIBUTE, null);
			if(imageLocation != null) {
				if(imageLocation.startsWith("SAMPLE")) { //$NON-NLS-1$
					String[] split = imageLocation.split("_"); //$NON-NLS-1$
					int index = Integer.parseInt(split[1]);
					if (index < sampleImageDescriptors.length) {
						return AspectJImages.instance().getRegistry().get(sampleImageDescriptors[index]);
					}
				} else {
					return getImage(imageLocation);
				}
			}
		}
		return null;
	}

	public String getImageDescriptorId(Annotation annotation) {
		if(annotation instanceof SimpleMarkerAnnotation) {
			return ((SimpleMarkerAnnotation)annotation).getMarker().getAttribute(IMAGE_LOCATION_ATTRIBUTE, null);
		} else {
			return null;
		}
	}

	public ImageDescriptor getImageDescriptor(String imageDescritporId) {
		if(imageDescritporId.startsWith("SAMPLE")) { //$NON-NLS-1$
			String[] split = imageDescritporId.split("_"); //$NON-NLS-1$
			int index = Integer.parseInt(split[1]);
			return sampleImageDescriptors[index];
		}
		return null;
	}

	public static Image getImage(String imageLocation) {
		IFile file = AspectJPlugin.getWorkspace().getRoot().getFile(new Path(imageLocation));
		ImageDescriptor id;
		try {
			id = ImageDescriptor.createFromURL(file.getLocationURI().toURL());
			return AspectJImages.instance().getRegistry().get(id);
		} catch (MalformedURLException ignored) {
		}
		return null;
	}

}
