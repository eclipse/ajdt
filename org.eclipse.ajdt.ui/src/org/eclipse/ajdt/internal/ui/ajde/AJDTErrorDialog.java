/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 		IBM Corporation - initial API and implementation
 * 		Sebastian Davids <sdavids@gmx.de> - Fix for bug 19346 - Dialog font should
 * 			be activated and used by other components.
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.ajde;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IconAndMessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

// heavily adapted from org.eclipse.jface.dialogs.ErrorDialog
/**
 * A dialog to display one or more errors to the user, as contained in an
 * <code>IStatus</code> object. If an error contains additional detailed
 * information then a Details button is automatically supplied, which shows or
 * hides an error details viewer when pressed by the user.
 *
 * @see org.eclipse.core.runtime.IStatus
 */
public class AJDTErrorDialog extends IconAndMessageDialog {

    /**
     * Reserve room for this many list items.
     */
    private static final int LIST_ITEM_COUNT = 9;

    /**
     * The Details button.
     */
    private Button detailsButton;

    /**
     * The title of the dialog.
     */
    private final String title;

    /**
     * The SWT text control that displays the error details.
     */
    private Text list;

    /**
     * Indicates whether the error details viewer is currently created.
     */
    private boolean listCreated = false;

    /**
     * The current clipboard. To be disposed when closing the dialog.
     */
    private Clipboard clipboard;

	private final String longMessage;

    /**
     * Message label is the label the message is shown on.
     */
    protected Link messageLabel;

    /**
     * Creates an error dialog. Note that the dialog will have no visual
     * representation (no widgets) until it is told to open.
     * <p>
     * Normally one should use <code>openError</code> to create and open one
     * of these. This constructor is useful only if the error object being
     * displayed contains child items <it>and </it> you need to specify a mask
     * which will be used to filter the displaying of these children.
     * </p>
     *
     * @param parentShell
     *            the shell under which to create this dialog
     * @param dialogTitle
     *            the title to use for this dialog, or <code>null</code> to
     *            indicate that the default title should be used
     * @param message
     *            the message to show in this dialog, or <code>null</code> to
     *            indicate that the error's message should be shown as the
     *            primary message
     * @param status
     *            the error to show to the user
     * @param displayMask
     *            the mask to use to filter the displaying of child items, as
     *            per <code>IStatus.matches</code>
     * @see org.eclipse.core.runtime.IStatus#matches(int)
     */
    public AJDTErrorDialog(Shell parentShell, String dialogTitle, String shortMessage,
            String longMessage) {
        super(parentShell);
        this.title = dialogTitle == null ? JFaceResources
                .getString("Problem_Occurred") : //$NON-NLS-1$
                dialogTitle;
        this.message = shortMessage;
        this.longMessage = longMessage;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    /*
     * (non-Javadoc) Method declared on Dialog. Handles the pressing of the Ok
     * or Details button in this dialog. If the Ok button was pressed then close
     * this dialog. If the Details button was pressed then toggle the displaying
     * of the error details area. Note that the Details button will only be
     * visible if the error being displayed specifies child details.
     */
    protected void buttonPressed(int id) {
        if (id == IDialogConstants.DETAILS_ID) {
            // was the details button pressed?
            toggleDetailsArea();
        } else {
            super.buttonPressed(id);
        }
    }

    /*
     * (non-Javadoc) Method declared in Window.
     */
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(title);
    }

    /*
     * (non-Javadoc) Method declared on Dialog.
     */
    protected void createButtonsForButtonBar(Composite parent) {
        // create OK and Details buttons
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL,
                true);
        if (shouldShowDetailsButton()) {
            detailsButton = createButton(parent, IDialogConstants.DETAILS_ID,
                    IDialogConstants.SHOW_DETAILS_LABEL, false);
        }
    }

    /**
     * Create the area the message will be shown in.
     * @param composite The composite to parent from.
     * @return Control
     */
    protected Control createMessageArea(Composite composite) {
        // create composite
        // create image
        Image image = getImage();
        if (image != null) {
            imageLabel = new Label(composite, SWT.NULL);
            image.setBackground(imageLabel.getBackground());
            imageLabel.setImage(image);
            imageLabel.setLayoutData(new GridData(
                    GridData.HORIZONTAL_ALIGN_CENTER
                            | GridData.VERTICAL_ALIGN_BEGINNING));
        }
        // create message
        if (message != null) {
            messageLabel = new Link(composite, getMessageLabelStyle());
            messageLabel.setText(message);
            messageLabel.addListener (SWT.Selection, event -> {
            try {
              URL url = new URL(event.text);
              PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(url);
            } catch (MalformedURLException | PartInitException ignored) {
            }
});
            GridData data = new GridData(GridData.GRAB_HORIZONTAL
                    | GridData.HORIZONTAL_ALIGN_FILL
                    | GridData.VERTICAL_ALIGN_BEGINNING);
            data.widthHint = convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH);
            messageLabel.setLayoutData(data);
        }
        return composite;
    }

    /**
     * This implementation of the <code>Dialog</code> framework method creates
     * and lays out a composite and calls <code>createMessageArea</code> and
     * <code>createCustomArea</code> to populate it. Subclasses should
     * override <code>createCustomArea</code> to add contents below the
     * message.
     */
    protected Control createDialogArea(Composite parent) {
        createMessageArea(parent);
        // create a composite with standard margins and spacing
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
        layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
        layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
        layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
        layout.numColumns = 2;
        composite.setLayout(layout);
        GridData childData = new GridData(GridData.FILL_BOTH);
        childData.horizontalSpan = 2;
        composite.setLayoutData(childData);
        composite.setFont(parent.getFont());
        return composite;
    }

    /*
     * @see IconAndMessageDialog#createDialogAndButtonArea(Composite)
     */
    protected void createDialogAndButtonArea(Composite parent) {
        super.createDialogAndButtonArea(parent);
        if (this.dialogArea instanceof Composite) {
            //Create a label if there are no children to force a smaller layout
            Composite dialogComposite = (Composite) dialogArea;
            if (dialogComposite.getChildren().length == 0)
                new Label(dialogComposite, SWT.NULL);
        }
    }

    /*
     *  (non-Javadoc)
     * @see org.eclipse.jface.dialogs.IconAndMessageDialog#getImage()
     */
    protected Image getImage() {
       return getErrorImage();
    }

    /**
     * Create this dialog's drop-down list component.
     *
     * @param parent
     *            the parent composite
     * @return the drop-down list component
     */
    protected Text createDropDownList(Composite parent) {
        // create the list
        list = new Text(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL
                | SWT.MULTI);
        list.setEditable(false);
        // fill the list
        populateList(list);
        GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL
                | GridData.GRAB_HORIZONTAL | GridData.VERTICAL_ALIGN_FILL
                | GridData.GRAB_VERTICAL);
        data.heightHint = list.getLineHeight() * LIST_ITEM_COUNT;
        data.horizontalSpan = 2;
        list.setLayoutData(data);
        list.setFont(parent.getFont());
        Menu copyMenu = new Menu(list);
        MenuItem copyItem = new MenuItem(copyMenu, SWT.NONE);
        copyItem.addSelectionListener(new SelectionListener() {
            /*
             * @see SelectionListener.widgetSelected (SelectionEvent)
             */
            public void widgetSelected(SelectionEvent e) {
                copyToClipboard();
            }

            /*
             * @see SelectionListener.widgetDefaultSelected(SelectionEvent)
             */
            public void widgetDefaultSelected(SelectionEvent e) {
                copyToClipboard();
            }
        });
        copyItem.setText(JFaceResources.getString("copy")); //$NON-NLS-1$
        list.setMenu(copyMenu);
        listCreated = true;
        return list;
    }

    /*
     * (non-Javadoc) Method declared on Window.
     */
    /**
     * Extends <code>Window.open()</code>. Opens an error dialog to display
     * the error. If you specified a mask to filter the displaying of these
     * children, the error dialog will only be displayed if there is at least
     * one child status matching the mask.
     */
    public int open() {
        if (!AspectJPlugin.getDefault().isHeadless()) {
            return super.open();
        } else {
            AspectJPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, AspectJPlugin.PLUGIN_ID, longMessage, new Exception()));
        }
        setReturnCode(OK);
        return OK;
    }

    /**
     * Opens an error dialog to display the given error. Use this method if the
     * error object being displayed does not contain child items, or if you wish
     * to display all such items without filtering.
     *
     * @param parent
     *            the parent shell of the dialog, or <code>null</code> if none
     * @param dialogTitle
     *            the title to use for this dialog, or <code>null</code> to
     *            indicate that the default title should be used
     * @param message
     *            the message to show in this dialog, or <code>null</code> to
     *            indicate that the error's message should be shown as the
     *            primary message
     * @param longMessage
     *            a more descriptive message, for the details area
     * @return the code of the button that was pressed that resulted in this
     *         dialog closing. This will be <code>Dialog.OK</code> if the OK
     *         button was pressed, or <code>Dialog.CANCEL</code> if this
     *         dialog's close window decoration or the ESC key was used.
     */
    public static int openError(Shell parent, String dialogTitle,
            String message, String longMessage) {
        AJDTErrorDialog dialog = new AJDTErrorDialog(parent, dialogTitle, message, longMessage);
        return dialog.open();
    }

    /**
     * Toggles the unfolding of the details area. This is triggered by the user
     * pressing the details button.
     */
    private void toggleDetailsArea() {
        Point windowSize = getShell().getSize();
        Point oldSize = getShell().computeSize(SWT.DEFAULT, SWT.DEFAULT);
        if (listCreated) {
            list.dispose();
            listCreated = false;
            detailsButton.setText(IDialogConstants.SHOW_DETAILS_LABEL);
        } else {
            list = createDropDownList((Composite) getContents());
            detailsButton.setText(IDialogConstants.HIDE_DETAILS_LABEL);
        }
        Point newSize = getShell().computeSize(SWT.DEFAULT, SWT.DEFAULT);
        getShell()
                .setSize(
                        new Point(windowSize.x, windowSize.y
                                + (newSize.y - oldSize.y)));
    }

    private void populateList(Text list) {
    	list.setText(longMessage);
    }

    /**
     * Copy the contents of the statuses to the clipboard.
     */
    private void copyToClipboard() {
        if (clipboard != null)
            clipboard.dispose();
        clipboard = new Clipboard(list.getDisplay());
        clipboard.setContents(new Object[] { longMessage },
                new Transfer[] { TextTransfer.getInstance() });
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.jface.window.Window#close()
     */
    public boolean close() {
        if (clipboard != null)
            clipboard.dispose();
        return super.close();
    }

    /**
     * Show the details portion of the dialog if it is not already visible.
     * This method will only work when it is invoked after the control of the dialog
     * has been set. In other words, after the <code>createContents</code> method
     * has been invoked and has returned the control for the content area of the dialog.
     * Invoking the method before the content area has been set or after the dialog has been
     * disposed will have no effect.
     * @since 3.1
     */
    protected final void showDetailsArea() {
        if (!listCreated) {
            Control control = getContents();
            if (control != null && ! control.isDisposed())
                toggleDetailsArea();
        }
    }

    /**
     * Return whether the Details button should be included.
     * This method is invoked once when the dialog is built.
     * By default, the Details button is only included if
     * the status used when creating the dialog was a multi-status
     * or if the status contains an exception.
     * Subclasses may override.
     * @return whether the Details button should be included
     * @since 3.1
     */
    protected boolean shouldShowDetailsButton() {
        return true;
    }

}
