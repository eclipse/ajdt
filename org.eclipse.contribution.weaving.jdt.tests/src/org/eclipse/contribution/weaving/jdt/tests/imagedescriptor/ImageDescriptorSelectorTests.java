/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

package org.eclipse.contribution.weaving.jdt.tests.imagedescriptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import junit.framework.Assert;

import org.eclipse.contribution.weaving.jdt.tests.MockCompilationUnit;
import org.eclipse.contribution.weaving.jdt.tests.MockImageDescriptorSelector;
import org.eclipse.contribution.weaving.jdt.tests.WeavingTestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.search.JavaSearchTypeNameMatch;
import org.eclipse.jdt.internal.debug.ui.launcher.DebugTypeSelectionDialog;
import org.eclipse.jdt.internal.ui.dialogs.FilteredTypesSelectionDialog;
import org.eclipse.jdt.internal.ui.typehierarchy.HierarchyLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;

/**
 * @author Andrew Eisenberg
 * @created Jan 5, 2009
 *
 */
public class ImageDescriptorSelectorTests extends WeavingTestCase {
    MockCompilationUnit cu;

    public void setUp() throws Exception {
        IProject proj = createPredefinedProject("MockCUProject");
        IFile file = proj.getFile("src/nothing/nothing.mock");
        cu = (MockCompilationUnit) JavaCore.create(file);
        cu.becomeWorkingCopy(monitor);
    }

    public void tearDown() throws Exception {
        super.tearDown();
        MockImageDescriptorSelector.isSet = false;
    }

    public void testImageSelectHierarchy() {
        HierarchyLabelProvider hierarchy = new HierarchyLabelProvider(null);
        testLabelProvider(hierarchy);
    }

    public void testImageSelectDialog() throws Exception {
        FilteredTypesSelectionDialog dialog = new FilteredTypesSelectionDialog(new Shell(Display.getCurrent().getActiveShell()), false, null, null, 0, null);
        LabelProvider fTypeInfoLabelProvider = getLabelProvider(dialog);

        // here, we can't test the image directly because the extra decorators are added to it.
        // instead just test that the image selector was activated
        fTypeInfoLabelProvider.getImage(new JavaSearchTypeNameMatch((IType) cu.getChildren()[0], 0));
        Assert.assertTrue("Image descriptor selector never activated", MockImageDescriptorSelector.isSet);
    }

    public void testImageSelectDebugger() throws Exception {
        MockDebugTypeSelectionDialog dialog = new MockDebugTypeSelectionDialog(new Shell(Display.getCurrent()), new IType[] { (IType) cu.getChildren()[0] }, "");
        dialog.setInitialPattern("Mock");
        ILabelProvider provider = dialog.getLabelProvider();

        // here, we can't test the image directly because the extra decorators are added to it.
        // instead just test that the image selector was activated
        provider.getImage(cu.getChildren()[0]);
        waitForJobsToComplete();
        Assert.assertTrue("Image descriptor selector never activated", MockImageDescriptorSelector.isSet);
    }

    static class MockDebugTypeSelectionDialog extends DebugTypeSelectionDialog {

        public MockDebugTypeSelectionDialog(Shell shell, IType[] elements, String title) {
            super(shell, elements, title);
        }
        public ILabelProvider getLabelProvider() throws Exception {
            Method getItemsListLabelProviderMethod = FilteredItemsSelectionDialog.class.getDeclaredMethod("getItemsListLabelProvider");
            getItemsListLabelProviderMethod.setAccessible(true);
            Object fItemsListLabelProvider = getItemsListLabelProviderMethod.invoke(this);
            Field providerField = fItemsListLabelProvider.getClass().getDeclaredField("provider");
            providerField.setAccessible(true);
            return (ILabelProvider) providerField.get(fItemsListLabelProvider);
        }
    }

    public void testImageSelectComputeImage() {
        Image i = new JavaElementImageProvider().getImageLabel(cu, 0);
        testImage(i);
    }





    private void testLabelProvider(ILabelProvider provider) {
        Image i = provider.getImage(cu);
        testImage(i);
    }

    private void testImage(Image i) {
        byte[] createdImageData = i.getImageData().data;
        byte[] origImageData = MockImageDescriptorSelector.getImage().getImageData().data;

        Assert.assertEquals("Mock image is not the same as expected image", origImageData.length, createdImageData.length);
        for (int j = 0; j < origImageData.length; j++) {
            Assert.assertEquals("Mock image is not the same as expected image", origImageData[j], createdImageData[j]);
        }
    }


    private LabelProvider getLabelProvider(FilteredTypesSelectionDialog dialog) throws Exception {
        Field field = FilteredTypesSelectionDialog.class.getDeclaredField("fTypeInfoLabelProvider");
        field.setAccessible(true);
        return (LabelProvider) field.get(dialog);
    }
}
