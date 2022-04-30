/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Luzius Meisser  - initial version
 *******************************************************************************/

package org.eclipse.ajdt.ui.tests.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ajdt.internal.ui.editor.AspectJBreakpointRulerAction;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IVerticalRulerInfo;
import org.eclipse.ui.texteditor.AbstractMarkerAnnotationModel;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Tests setting breakpoints.
 * TODO: only bug like 71718 that cause RuntimeExceptions are detected.
 * -> add check if really set.
 *
 * @author Luzius Meisser
 */
public class AspectJBreakpointRulerActionTest extends UITestCase {

	IProject project;

	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		project = createPredefinedProject("Simple AJ Project"); //$NON-NLS-1$

	}

	public void testSetBreakpointA(){
		IResource res = project.findMember("src/p1/Main.java"); //$NON-NLS-1$
		if (res == null)
			fail("Required file not found."); //$NON-NLS-1$
		BreakpointSetTest((IFile)res);
	}

	public void testSetBreakpointB(){
		IResource res = project.findMember("src/p2/Aspect.aj"); //$NON-NLS-1$
		if (res == null)
			fail("Required file not found."); //$NON-NLS-1$
		BreakpointSetTest((IFile)res);
	}

	public void testSetBreakpointC(){
		IResource res = project.findMember("src/AspectInDefaultPackage.aj"); //$NON-NLS-1$
		if (res == null)
			fail("Required file not found."); //$NON-NLS-1$
		BreakpointSetTest((IFile)res);
	}

	public void BreakpointSetTest(IFile sourcefile){

		ITextEditor editorPart = (ITextEditor)openFileInDefaultEditor(sourcefile, false);

		//wait for annotation model to be created
		waitForJobsToComplete();

		//toggling breakpoint on line 17 should add it
		setBreakpoint(17, true, sourcefile, editorPart);

		waitForJobsToComplete();

		//toggling breakpoint on line 17 should remove it again
		setBreakpoint(17, true, sourcefile, editorPart);

		waitForJobsToComplete();

		//toggling breakpoint on line 100 should not be possible
		setBreakpoint(100, false, sourcefile, editorPart);

		editorPart.close(false);
	}

	private void setBreakpoint(int line, boolean hasEffect, IFile file, ITextEditor editor){
		CompositeRuler rulerInfo= (CompositeRuler) editor.getAdapter(IVerticalRulerInfo.class);
		clickLine(line, rulerInfo);
		waitForJobsToComplete();
		int numOfMarkers = getNumOfMarkers(file, editor);
		(new AspectJBreakpointRulerAction(rulerInfo, editor, editor)).run();
		waitForJobsToComplete();
		waitForJobsToComplete();

		int newNumOfMarkers = getNumOfMarkers(file, editor);
		//hasBreakpointOnCurrentLine(file, editor);
		if ((numOfMarkers == newNumOfMarkers) == hasEffect)
			fail(hasEffect?"Could not toggle breakpoint." + " in file " + file.getName() + " at line " + line:"Could set breakpoint in illegal position." + " in file " + file.getName() + " at line " + line); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	}

	private void clickLine(int desiredline, CompositeRuler rulerInfo){
		int line;
		int inputline = desiredline;
		int maxIter = 1000; // bail out limit, to avoid infinite loops
		do{
			line = rulerInfo.toDocumentLineNumber(inputline);
			if (line==-1) {
				rulerInfo.setLocationOfLastMouseButtonActivity(0, inputline);
				return;
			}
			inputline++;
			maxIter--;
		} while ((maxIter > 0) && (line != desiredline));

		rulerInfo.setLocationOfLastMouseButtonActivity(0, inputline);
	}

	protected AbstractMarkerAnnotationModel getAnnotationModel(ITextEditor editor) {
		IDocumentProvider provider = editor.getDocumentProvider();
		IAnnotationModel model = provider.getAnnotationModel(editor
				.getEditorInput());
		if (model instanceof AbstractMarkerAnnotationModel) {
			return (AbstractMarkerAnnotationModel) model;
		}
		return null;
	}

	protected boolean hasBreakpointOnCurrentLine(IResource resource, ITextEditor editor){
		return !getMarkers(resource, editor).isEmpty();
	}

	protected int getNumOfMarkers(IResource resource, ITextEditor editor) {

			try {

				IMarker[] markers;
				if (resource instanceof IFile)
					markers = resource.findMarkers(
							IBreakpoint.BREAKPOINT_MARKER, true,
							IResource.DEPTH_INFINITE);
				else {
					IWorkspaceRoot root = ResourcesPlugin.getWorkspace()
							.getRoot();
					markers = root.findMarkers(IBreakpoint.BREAKPOINT_MARKER,
							true, IResource.DEPTH_INFINITE);
				}

				if (markers != null) {
					return markers.length;
				}
			} catch (CoreException x) {
				JDIDebugUIPlugin.log(x.getStatus());
			}
		return 0;
	}

	protected List getMarkers(IResource resource, ITextEditor editor) {

		List breakpoints = new ArrayList();

		IDocumentProvider provider = editor.getDocumentProvider();
		IDocument document = provider.getDocument(editor.getEditorInput());

		AbstractMarkerAnnotationModel model = getAnnotationModel(editor);

		if (model != null) {
			try {

				IMarker[] markers;
				if (resource instanceof IFile)
					markers = resource.findMarkers(
							IBreakpoint.BREAKPOINT_MARKER, true,
							IResource.DEPTH_INFINITE);
				else {
					IWorkspaceRoot root = ResourcesPlugin.getWorkspace()
							.getRoot();
					markers = root.findMarkers(IBreakpoint.BREAKPOINT_MARKER,
							true, IResource.DEPTH_INFINITE);
				}

				if (markers != null) {
					IBreakpointManager breakpointManager = DebugPlugin
							.getDefault().getBreakpointManager();
          for (IMarker marker : markers) {
            IBreakpoint breakpoint = breakpointManager
              .getBreakpoint(marker);
            if (breakpoint != null
                && breakpointManager.isRegistered(breakpoint)
                && includesRulerLine(model
                .getMarkerPosition(marker),
              document, editor.getAdapter(IVerticalRulerInfo.class)))
              breakpoints.add(marker);
          }
				}
			} catch (CoreException x) {
				JDIDebugUIPlugin.log(x.getStatus());
			}
		}
		return breakpoints;
	}

	protected boolean includesRulerLine(Position position, IDocument document, IVerticalRulerInfo rulerInfo) {

		if (position != null) {
			try {
				int markerLine = document.getLineOfOffset(position.getOffset());
				int line = rulerInfo.getLineOfLastMouseButtonActivity();
				if (line == markerLine) {
					return true;
				}
			} catch (BadLocationException ignored) {
			}
		}

		return false;
	}

}
