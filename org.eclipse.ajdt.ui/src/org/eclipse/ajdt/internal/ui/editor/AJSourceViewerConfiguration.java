/*******************************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.editor;

import org.eclipse.ajdt.internal.ui.editor.contentassist.AJCompletionProcessor;
import org.eclipse.ajdt.internal.ui.editor.outline.AJOutlineInformationControl;
import org.eclipse.jdt.internal.ui.text.JavaElementProvider;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.AbstractInformationControlManager;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.formatter.IContentFormatter;
import org.eclipse.jface.text.formatter.MultiPassContentFormatter;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.information.IInformationPresenter;
import org.eclipse.jface.text.information.IInformationProvider;
import org.eclipse.jface.text.information.InformationPresenter;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;

public class AJSourceViewerConfiguration extends JavaSourceViewerConfiguration {

	AspectJTextTools ajtt;

	public AJSourceViewerConfiguration(AspectJTextTools textTools, AspectJEditor editor) {
		super(textTools.getColorManager(), textTools.getPreferenceStore(), editor, IJavaPartitions.JAVA_PARTITIONING);
		ajtt = textTools;
	}

	protected RuleBasedScanner getCodeScanner() {
		return ajtt.getCodeScanner();
	}

	/*
	 * @see SourceViewerConfiguration#getContentAssistant(ISourceViewer)
	 */
	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
		IContentAssistant assistant = super.getContentAssistant(sourceViewer);
		if ((assistant != null) && (assistant instanceof ContentAssistant)) {
			ContentAssistant cAssi = (ContentAssistant)assistant;
			AJCompletionProcessor ajProcessor= new AJCompletionProcessor(getEditor(), (ContentAssistant) assistant, IDocument.DEFAULT_CONTENT_TYPE);
			cAssi.setContentAssistProcessor(ajProcessor, IDocument.DEFAULT_CONTENT_TYPE);
			configureAJProcessor((ContentAssistant)assistant, fPreferenceStore, ajProcessor);
		}
		return assistant;
	}

	// Fix for bug 111971 - our completion processor is not configured properly by ContentAssistPreferences
	// so copied ContentAssistPreferences.configureJavaProcessor to here.
	private void configureAJProcessor(ContentAssistant assistant, IPreferenceStore store, AJCompletionProcessor jcp) {
		String triggers= store.getString(PreferenceConstants.CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVA);
		if (triggers != null)
			jcp.setCompletionProposalAutoActivationCharacters(triggers.toCharArray());

		boolean enabled= store.getBoolean(PreferenceConstants.CODEASSIST_SHOW_VISIBLE_PROPOSALS);
		jcp.restrictProposalsToVisibility(enabled);

		enabled= store.getBoolean(PreferenceConstants.CODEASSIST_CASE_SENSITIVITY);
		jcp.restrictProposalsToMatchingCases(enabled);
	}

	/**
	 * Determines whether the preference change encoded by the given event
	 * changes the behavior of one of its contained components
	 *
	 * @param event the event to be investigated
	 * @return <code>true</code> if event causes a behavioral change
	 * @since 3.0
	 */
	public boolean affectsTextPresentation(PropertyChangeEvent event) {
		return  ajtt.getAspectjCodeScanner().affectsBehavior(event) ||
		  super.affectsTextPresentation(event);
	}



	public IContentFormatter getContentFormatter(ISourceViewer sourceViewer) {
		IContentFormatter formatter = super.getContentFormatter(sourceViewer);
		if (formatter instanceof MultiPassContentFormatter)
			((MultiPassContentFormatter)formatter).setMasterStrategy(new AJFormattingStrategy());
		return formatter;
	}

	// creates an AJOutlineInformationControl instead of a
	// JavaOutlineInformationControl
	// not needed if/when eclipse bug 79489 is fixed
	/**
	 * Returns the outline presenter control creator. The creator is a factory creating outline
	 * presenter controls for the given source viewer. This implementation always returns a creator
	 * for <code>JavaOutlineInformationControl</code> instances.
	 * @param commandId the ID of the command that opens this control
	 *
	 * @return an information control creator
	 * @since 2.1
	 */
	private IInformationControlCreator getOutlinePresenterControlCreator(final String commandId) {
		return parent -> {
      int shellStyle= SWT.RESIZE;
      int treeStyle= SWT.V_SCROLL | SWT.H_SCROLL;
      return new AJOutlineInformationControl(parent, shellStyle, treeStyle, commandId);
    };
	}

	// copied from superclass so that we can call our own version of
	// getOutlinePresenterControlCreator()
	// not needed if/when eclipse bug 79489 is fixed
	/**
	 * Returns the outline presenter which will determine and shown
	 * information requested for the current cursor position.
	 *
	 * @param sourceViewer the source viewer to be configured by this configuration
	 * @param doCodeResolve a boolean which specifies whether code resolve should be used to compute the Java element
	 * @return an information presenter
	 * @since 2.1
	 */
	public IInformationPresenter getOutlinePresenter(ISourceViewer sourceViewer, boolean doCodeResolve) {
		InformationPresenter presenter;
		if (doCodeResolve)
			presenter= new InformationPresenter(getOutlinePresenterControlCreator(IJavaEditorActionDefinitionIds.OPEN_STRUCTURE));
		else
			presenter= new InformationPresenter(getOutlinePresenterControlCreator(IJavaEditorActionDefinitionIds.SHOW_OUTLINE));
		presenter.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
		presenter.setAnchor(AbstractInformationControlManager.ANCHOR_GLOBAL);
		IInformationProvider provider= new JavaElementProvider(getEditor(), doCodeResolve);
		presenter.setInformationProvider(provider, IDocument.DEFAULT_CONTENT_TYPE);
		presenter.setInformationProvider(provider, IJavaPartitions.JAVA_DOC);
		presenter.setInformationProvider(provider, IJavaPartitions.JAVA_MULTI_LINE_COMMENT);
		presenter.setInformationProvider(provider, IJavaPartitions.JAVA_SINGLE_LINE_COMMENT);
		presenter.setInformationProvider(provider, IJavaPartitions.JAVA_STRING);
		presenter.setInformationProvider(provider, IJavaPartitions.JAVA_CHARACTER);
		presenter.setSizeConstraints(50, 20, true, false);
		return presenter;
	}

	/*
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getHyperlinkDetectors(org.eclipse.jface.text.source.ISourceViewer)
	 * @since 3.1
	 */
	public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) {
		if (!fPreferenceStore.getBoolean(AbstractDecoratedTextEditorPreferenceConstants.EDITOR_HYPERLINKS_ENABLED))
			return null;

		IHyperlinkDetector[] inheritedDetectors= super.getHyperlinkDetectors(sourceViewer);

		if (getEditor() == null)
			return inheritedDetectors;

		int inheritedDetectorsLength= inheritedDetectors != null ? inheritedDetectors.length : 0;
		IHyperlinkDetector[] detectors= new IHyperlinkDetector[inheritedDetectorsLength + 1];
		detectors[0]= new PointcutElementHyperlinkDetector(getEditor());
    System.arraycopy(inheritedDetectors, 0, detectors, 1, inheritedDetectorsLength);

		return detectors;
	}

}
