/**********************************************************************
Copyright (c) 2002 IBM Corporation and others.
All rights reserved. This program and the accompanying materials
are made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html
Contributors:
Adrian Colyer - initial version
...
**********************************************************************/
package org.eclipse.ajdt.internal.ui;

import org.eclipse.ajdt.internal.ui.actions.ClearEventTraceAction;
import org.eclipse.ajdt.internal.utils.AJDTEventTrace;
import org.eclipse.ajdt.internal.utils.AJDTEventTrace.Event;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;

/**
 * This view displays the AJDT Event Trace output
 */
public class AJDTEventTraceView extends ViewPart 
				implements AJDTEventTrace.EventListener {

	Text text;
	private ClearEventTraceAction clearEventTraceAction;
	
	/**
	 * Constructor for AJDTEventTraceView.
	 */
	public AJDTEventTraceView() {
		super();
	}

	public void dispose( ) {
		AJDTEventTrace.removeListener( this );
	}

	/**
	 * @see IWorkbenchPart#createPartControl(Composite)
	 */
	public void createPartControl(Composite parent) {
		text = new Text( parent, SWT.MULTI | SWT.READ_ONLY | SWT.VERTICAL | SWT.HORIZONTAL );		
		AJDTEventTrace.addListener( this );

		makeActions();
		contributeToActionBars();
	}

	/**
	 * @see IWorkbenchPart#setFocus()
	 */
	public void setFocus() {
		text.setFocus();
	}

	/**
	 * @see EventListener#ajdtEvent(Event)
	 */
	public void ajdtEvent(Event e) {
		text.append( e.toString() );
		text.append( "\n" ); //$NON-NLS-1$
	}

	// Implementing feature 99129
	
	private void makeActions() {
		clearEventTraceAction = new ClearEventTraceAction(text);
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(clearEventTraceAction);
	}
}
