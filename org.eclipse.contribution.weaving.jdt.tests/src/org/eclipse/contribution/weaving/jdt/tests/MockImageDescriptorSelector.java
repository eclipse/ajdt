package org.eclipse.contribution.weaving.jdt.tests;

import java.net.URL;

import org.eclipse.contribution.jdt.imagedescriptor.IImageDescriptorSelector;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.core.search.JavaSearchTypeNameMatch;
import org.eclipse.jdt.internal.ui.text.java.LazyJavaCompletionProposal;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

public class MockImageDescriptorSelector implements IImageDescriptorSelector {

    public ImageDescriptor getTypeImageDescriptor(boolean isInner,
            boolean isInInterfaceOrAnnotation, int flags,
            boolean useLightIcons, Object element) {
        if (    (element instanceof MockCompilationUnit) ||
                ((element instanceof IType) && (((IType)element).getParent() instanceof MockCompilationUnit)) ||
                (element instanceof JavaSearchTypeNameMatch)) {
            isSet = true; // remember that we've been here
            return getImageDescriptor();
        } else {
            return null;
        }
    }

    public static boolean isSet = false;

    private static Image IMAGE;
    private static ImageDescriptor DESCRIPTOR;


    public ImageDescriptor createCompletionProposalImageDescriptor(
            LazyJavaCompletionProposal proposal) {

        // XXX not tested for now.
        return null;
    }


    private static void createImage() {
        try {
            URL pluginInstallURL = JDTWeavingTestsPlugin.getDefault().getBundle().getEntry("/"); //$NON-NLS-1$
            String localPath = "icons/aspect.gif";
            URL url = new URL( pluginInstallURL, localPath );
            DESCRIPTOR = ImageDescriptor.createFromURL( url );
            IMAGE = DESCRIPTOR.createImage();
        } catch (Exception ignored) {
        }
    }

    private ImageDescriptor getImageDescriptor() {
        if (DESCRIPTOR == null) {
            createImage();
        }
        return DESCRIPTOR;
    }

    public static Image getImage() {
        if (IMAGE == null) {
            createImage();
        }
        return IMAGE;
    }


}
