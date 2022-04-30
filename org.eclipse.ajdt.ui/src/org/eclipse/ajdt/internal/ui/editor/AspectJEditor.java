/**********************************************************************
 Copyright (c) 2002, 2005 IBM Corporation and others.
 All rights reserved. This program and the accompanying materials
 are made available under the terms of the Eclipse Public License v1.0
 which accompanies this distribution, and is available at
 http://www.eclipse.org/legal/epl-v10.html
 Contributors:
 Adrian Colyer, Andy Clement, Tracy Gardner - initial version
 Helen Hawkins - updated for new ajde interface (bug 148190) - no longer
 required to set the current project
 ...
 **********************************************************************/
package org.eclipse.ajdt.internal.ui.editor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.CoreUtils;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.internal.ui.editor.actions.AJOrganizeImportsAction;
import org.eclipse.ajdt.internal.ui.editor.quickfix.JavaCorrectionAssistant;
import org.eclipse.ajdt.internal.ui.help.AspectJUIHelp;
import org.eclipse.ajdt.internal.ui.help.IAJHelpContextIds;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.internal.ui.xref.XRefUtils;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.contribution.xref.internal.ui.utils.XRefUIUtils;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.help.IContextProvider;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaOutlinePage;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;
import org.eclipse.jdt.ui.IPackagesViewPart;
import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.IWorkingCopyManagerExtension;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.information.IInformationPresenter;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationAccess;
import org.eclipse.jface.text.source.IAnnotationAccessExtension;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.texteditor.DocumentProviderRegistry;
import org.eclipse.ui.texteditor.IDocumentProvider;

/**
 * AspectJ Editor extends internal JDT editor in order to use our TextTools.
 * Better would be a clean interface in JDT to achieve the same effect. We also
 * override getAdapter( ) to plug-in the AspectJ-aware outline view.
 */
public class AspectJEditor extends CompilationUnitEditor {


    public static final String ASPECTJ_EDITOR_ID = "org.eclipse.ajdt.internal.ui.editor.CompilationUnitEditor"; //$NON-NLS-1$

    private AnnotationAccessWrapper annotationAccessWrapper;

    private static final Map<IEditorInput, AspectJEditor> activeEditorList = new HashMap<>();

    private AspectJEditorTitleImageUpdater aspectJEditorErrorTickUpdater;

    private AJCompilationUnitDocumentProvider provider;

    /**
     * Constructor for AspectJEditor
     */
    public AspectJEditor() {

        super();
        setRulerContextMenuId("#AJCompilationUnitRulerContext"); //$NON-NLS-1$
        // Bug 78182
        aspectJEditorErrorTickUpdater= new AspectJEditorTitleImageUpdater(this);
        if (AspectJUIPlugin.usingXref) {
            XRefUIUtils.addWorkingCopyManagerForEditor(this, JavaUI.getWorkingCopyManager());
        }
    }

    private AJSourceViewerConfiguration fAJSourceViewerConfiguration;

    private boolean isEditingAjFile = false;

    private CompilationUnitAnnotationModelWrapper.GlobalAnnotationModelListener fGlobalAnnotationModelListener;

    private IAnnotationModel annotationModel;

    private class AJTextOperationTarget implements ITextOperationTarget {
        private final ITextOperationTarget parent;

        private JavaCorrectionAssistant fCorrectionAssistant;

        private IInformationPresenter fOutlinePresenter;

        public AJTextOperationTarget(ITextOperationTarget parent) {
            this.parent = parent;
        }

        /*
         * (non-Javadoc)
         *
         * @see org.eclipse.jface.text.ITextOperationTarget#canDoOperation(int)
         */
        public boolean canDoOperation(int operation) {
            return parent.canDoOperation(operation);
        }

        /*
         * (non-Javadoc)
         *
         * @see org.eclipse.jface.text.ITextOperationTarget#doOperation(int)
         */
        public void doOperation(int operation) {
            if (operation == ISourceViewer.QUICK_ASSIST) {
                // use our own correction assistant
                if (fCorrectionAssistant == null) {
                    fCorrectionAssistant = new JavaCorrectionAssistant(
                            AspectJEditor.this);
                    fCorrectionAssistant.install(getSourceViewer());
                }
                String msg = fCorrectionAssistant.showPossibleQuickAssists();
                setStatusLineErrorMessage(msg);
            } else if (operation == JavaSourceViewer.SHOW_OUTLINE) {
                // use our own outline presenter
                // not needed if/when eclipse bug 79489 is fixed
                if (fOutlinePresenter == null) {
                    fOutlinePresenter = fAJSourceViewerConfiguration.getOutlinePresenter(getSourceViewer(),false);
                    fOutlinePresenter.install(getSourceViewer());
                }
                fOutlinePresenter.showInformation();
            } else {
                parent.doOperation(operation);
            }
        }

    }


    public Object getAdapter(Class key) {
        if (key.equals(ITextOperationTarget.class)) {
            // use our own wrapper around the one returned by the superclass
            return new AJTextOperationTarget((ITextOperationTarget) super
                    .getAdapter(key));
        }
        if (key.equals(IAnnotationAccess.class)) {
            Object o = super.getAdapter(key);
            if (o instanceof IAnnotationAccessExtension) {
                if (annotationAccessWrapper == null) {
                    annotationAccessWrapper = new AnnotationAccessWrapper();
                }
                annotationAccessWrapper
                        .setWrapped((IAnnotationAccessExtension) o);
                return annotationAccessWrapper;
            }
            return o;
        }
        if (key.equals(IContextProvider.class)) {
            return AspectJUIHelp.getHelpContextProvider(this, IAJHelpContextIds.ASPECTJ_EDITOR);
        }

        return super.getAdapter(key);
    }

    //Wrapper for IAnnotationAccessExtension. Purpose: this wrapper returns
    //an increased layer for overrideIndicator markers. This does not affect
    //the layer it gets displayed on, but it fixes the context menu so we
    //are still able to choose "open super implementation" when right-clicking
    //the vertical ruler (Matt/Luzius)
    static class AnnotationAccessWrapper implements IAnnotationAccessExtension {

        private IAnnotationAccessExtension wrapped;

        public void setWrapped(IAnnotationAccessExtension w) {
            wrapped = w;
        }

        public String getTypeLabel(Annotation annotation) {
            return wrapped.getTypeLabel(annotation);
        }

        public int getLayer(Annotation annotation) {
            int x = wrapped.getLayer(annotation);
            if ("org.eclipse.jdt.ui.overrideIndicator".equals(annotation //$NON-NLS-1$
                    .getType())) {
                x += 2;
            }
            return x;
        }

        public void paint(Annotation annotation, GC gc, Canvas canvas,
                Rectangle bounds) {
            wrapped.paint(annotation, gc, canvas, bounds);
        }

        public boolean isPaintable(Annotation annotation) {
            return wrapped.isPaintable(annotation);
        }

        public boolean isSubtype(Object annotationType,
                Object potentialSupertype) {
            return wrapped.isSubtype(annotationType, potentialSupertype);
        }

        public Object[] getSupertypes(Object annotationType) {
            return wrapped.getSupertypes(annotationType);
        }
    }

    /*
     * @see JavaEditor#setOutlinePageInput(JavaOutlinePage, IEditorInput)
     */
    protected void setOutlinePageInput(JavaOutlinePage page, IEditorInput input) {
        if (page != null) {
            IWorkingCopyManager manager= JavaUI.getWorkingCopyManager();
            page.setInput(manager.getWorkingCopy(input));
        }
    }

    /**
     * Override of doSave to comment-out call to getStatusLineManager - always
     * returns null (why?) in our environment. Also ask the contentOutlinePage
     * to update - not strictly required at the moment, but will be in the
     * future.
     */
    public void doSave(IProgressMonitor progressMonitor) {

        IDocumentProvider p = getDocumentProvider();
        if (p == null)
            return;

        if (p.isDeleted(getEditorInput())) {

            if (isSaveAsAllowed()) {

                /*
                 * 1GEUSSR: ITPUI:ALL - User should never loose changes made in
                 * the editors. Changed Behavior to make sure that if called
                 * inside a regular save (because of deletion of input element)
                 * there is a way to report back to the caller.
                 */
                performSaveAs(progressMonitor);

            } else {

                /*
                 * 1GF5YOX: ITPJUI:ALL - Save of delete file claims it's still
                 * there Missing resources.
                 */
                Shell shell = getSite().getShell();
                MessageDialog
                        .openError(
                                shell,
                                "CompilationUnitEditor.error.saving.title1", "CompilationUnitEditor.error.saving.message1"); //$NON-NLS-1$ //$NON-NLS-2$
            }

        } else {

            IWorkingCopyManager manager = JavaUI
                    .getWorkingCopyManager();
            ICompilationUnit unit = manager.getWorkingCopy(getEditorInput());

            if (unit != null) {
                synchronized (unit) {
                    performSave(false, progressMonitor);
                }
            } else {
                performSave(false, progressMonitor);
            }
        }

    }

    /**
     * Override to replace some of the java editor actions
     * @see org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor#createActions()
     */
    // Fix for bug 88474
    protected void createActions() {
        super.createActions();
        IAction organizeImports = new AJOrganizeImportsAction(this);
        organizeImports
                .setActionDefinitionId(IJavaEditorActionDefinitionIds.ORGANIZE_IMPORTS);
        setAction("OrganizeImports", organizeImports); //$NON-NLS-1$
    }

    //override this function to prevent others from setting the
    // SourceViewConfiguration
    //to a non-AspectJ one once it was set to AJSourceViewConfiguration
    protected void setSourceViewerConfiguration(
            SourceViewerConfiguration configuration) {
        Assert.isNotNull(configuration);
        SourceViewerConfiguration myConf = this.getSourceViewerConfiguration();
        if ((myConf != null) && (myConf instanceof AJSourceViewerConfiguration)
                && !(configuration instanceof AJSourceViewerConfiguration))
            return;
        super.setSourceViewerConfiguration(configuration);
    }

    public void createPartControl(Composite parent) {
        super.createPartControl(parent);
        if(annotationModel != null) {
            if(annotationModel instanceof CompilationUnitAnnotationModelWrapper) {
                ((CompilationUnitAnnotationModelWrapper)annotationModel).setDelegate(getSourceViewer().getAnnotationModel());
            }
            if(fGlobalAnnotationModelListener == null) {
                fGlobalAnnotationModelListener = new CompilationUnitAnnotationModelWrapper.GlobalAnnotationModelListener();
                fGlobalAnnotationModelListener.addListener(JavaPlugin.getDefault().getProblemMarkerManager());
            }
            annotationModel.addAnnotationModelListener(fGlobalAnnotationModelListener);
            IDocument document = getDocumentProvider().getDocument(getEditorInput());
            ISourceViewer sourceViewer= getSourceViewer();
            sourceViewer.setDocument(document, annotationModel);
            IAnnotationModel model = getDocumentProvider().getAnnotationModel(getEditorInput());
            if(model != null) { // this is null in a linked source folder due to an eclipse bug..
                model.connect(document);
            }
        }
    }

    public void doSetInput(IEditorInput input) throws CoreException {
        IEditorInput oldInput = super.getEditorInput();
        if (oldInput instanceof IFileEditorInput) {
            JavaUI.getWorkingCopyManager().disconnect(oldInput);
        }

        super.doSetInput(input);

        IPreferenceStore store = getPreferenceStore();
        AspectJTextTools textTools = new AspectJTextTools(store);
        fAJSourceViewerConfiguration = new AJSourceViewerConfiguration(
                textTools, this);
        setSourceViewerConfiguration(fAJSourceViewerConfiguration);

        if (input instanceof IFileEditorInput) {
            IFileEditorInput fInput = (IFileEditorInput) input;
            ICompilationUnit unit;
            // in case it is a .aj file, we need to register it in the
            // WorkingCopyManager
            if (CoreUtils.ASPECTJ_SOURCE_ONLY_FILTER.accept(fInput
                    .getFile().getName())) {
                JavaUI.getWorkingCopyManager().connect(input);
                unit = AJCompilationUnitManager.INSTANCE
                    .getAJCompilationUnitFromCache(fInput.getFile());
                if (unit != null) {
                    isEditingAjFile = true;
                    JavaModelManager.getJavaModelManager().discardPerWorkingCopyInfo((CompilationUnit)unit);
                    unit.becomeWorkingCopy(null);
                    ((IWorkingCopyManagerExtension) JavaUI
                            .getWorkingCopyManager()).setWorkingCopy(input, unit);

                }
            } else if (CoreUtils.ASPECTJ_SOURCE_FILTER.accept(fInput
                    .getFile().getName())) { // It's a .java file
                unit = JavaCore.createCompilationUnitFrom(fInput.getFile());
                annotationModel = new CompilationUnitAnnotationModelWrapper(unit);

                // bug 265902 Ensure that is there is no weaving, Java compilation units are
                // not reconciled.  This way, they can have AJ syntax, but no errors
                if (!AspectJPlugin.USING_CU_PROVIDER) {
                    if(unit instanceof CompilationUnit) {
                        JavaModelManager.getJavaModelManager().discardPerWorkingCopyInfo((CompilationUnit)unit);
                    }
                }

                unit.becomeWorkingCopy(null);
                ((IWorkingCopyManagerExtension) JavaUI
                    .getWorkingCopyManager()).setWorkingCopy(input, unit);

            }

            AJLog.log("Editor opened on " + fInput.getFile().getName()); //$NON-NLS-1$
            // Ensure any advice markers are created since they are not
            // persisted.
            synchronized(activeEditorList) {
                activeEditorList.put(this.getEditorInput(), this);
            }
            IDocument document = getDocumentProvider().getDocument(fInput);

            textTools.setupJavaDocumentPartitioner(document,
                    IJavaPartitions.JAVA_PARTITIONING);

//           Part of the fix for 89793 - editor icon is not always correct
            resetTitleImage();

            /*
             * This is where the hook for the prompt dialog should go (asking
             * the user if they want to open the Cross References view). If the
             * user has already been prompted then this call will just hendle
             * the opening (or not) of the Cross Reference view.
             *
             * NB It is very important that this task be scheduled for running
             * in the UI Thread, as otherwise it will fail in the case of the
             * AspectJEditor being restored when the workbench is started.
             *
             * -spyoung
             */
            Job job = new UIJob("AutoOpenXRefView") { //$NON-NLS-1$
                public IStatus runInUIThread(IProgressMonitor monitor) {
                    XRefUtils.autoOpenXRefView();
                    return Status.OK_STATUS;
                }
            };
            job.schedule();
        }

    }

    public void dispose() {
        AJLog.log("Disposing editor for:" + getTitle()); //$NON-NLS-1$
        IEditorInput input = getEditorInput();
        if (input instanceof IFileEditorInput) {
            IFileEditorInput fInput = (IFileEditorInput) input;
            // Fix for bug 79633 - editor buffer is not refreshed
            JavaUI.getWorkingCopyManager().disconnect(input);

            AJLog.log("Editor closed - " + fInput.getFile().getName()); //$NON-NLS-1$
            synchronized(activeEditorList) {
                activeEditorList.remove(input);
            }

            try {
                ICompilationUnit unit = AJCompilationUnitManager.INSTANCE.getAJCompilationUnitFromCache(fInput.getFile());
                if (unit == null) {
                    unit = JavaCore.createCompilationUnitFrom(fInput.getFile());
                }
                if (unit != null) {
                    unit.discardWorkingCopy();
                }
            } catch (JavaModelException ignored) {
            }

        }
        if (aspectJEditorErrorTickUpdater != null) {
            aspectJEditorErrorTickUpdater.dispose();
            aspectJEditorErrorTickUpdater = null;
        }
        if (AspectJUIPlugin.usingXref) {
            XRefUIUtils.removeWorkingCopyManagerForEditor(this);
        }
        super.dispose();
    }

    /**
     * @see org.eclipse.ui.IWorkbenchPart#setFocus()
     */
    public void setFocus() {
        // AMC -added this method to catch user switching between multiple open
        // files
        // in the editor - used to trigger a structured selection event, but now
        // triggers a TextSelection event. TextSelection event does not give AJP
        // enouhg info to determine project, so have to do from here instead.
        IEditorInput input = getEditorInput();
        super.setFocus();

        // Sian: Added the code below to fix bug 77479 - link with editor does not work for .aj files
        if(isEditingAjFile) {
            IViewPart view = getEditorSite().getPage().findView(JavaUI.ID_PACKAGES);
            if(view != null && view instanceof IPackagesViewPart) {  // can be ErrorViewPart
                IPackagesViewPart packageExplorer = (IPackagesViewPart)view;
                if(packageExplorer.isLinkingEnabled()) {
                    IFileEditorInput fInput = (IFileEditorInput) input;
                    AJCompilationUnit ajc = AJCompilationUnitManager.INSTANCE.getAJCompilationUnit(fInput.getFile());
                    if (ajc != null) {
                        packageExplorer.selectAndReveal(ajc);
                    }
                }
            }
        }
    }



    protected ITypeRoot getInputJavaElement() {
        return JavaUI.getWorkingCopyManager().getWorkingCopy(getEditorInput());
    }

    /**
     * @return Returns the activeEditorList.
     */
    public static Collection<AspectJEditor> getActiveEditorList() {
        synchronized(activeEditorList) {
            return activeEditorList.values();
        }
    }

    public static boolean isInActiveEditor(IEditorInput input) {
        return activeEditorList.containsKey(input);
    }

    public IDocumentProvider getDocumentProvider() {
        return provider == null ? super.getDocumentProvider() : provider;
    }

    protected void setDocumentProvider(IEditorInput input) {
        IDocumentProvider provider = DocumentProviderRegistry.getDefault().getDocumentProvider(input);
        if (provider instanceof AJCompilationUnitDocumentProvider) {
            this.provider = (AJCompilationUnitDocumentProvider) provider;
        } else {
            super.setDocumentProvider(input);
        }
    }

    protected void disposeDocumentProvider() {
        super.disposeDocumentProvider();
        provider = null;
    }


    public synchronized void updatedTitleImage(Image image) {
        // only let us update the image (fix for 105299)
    }

    public synchronized void customUpdatedTitleImage(Image image) {
        // only let us update the image (fix for 105299)
        super.updatedTitleImage(image);
    }

    /**
     * Update the title image
     */
    // Part of the fix for 89793 - editor icon is not always correct
    public  void resetTitleImage() {
        refreshJob.setElement(getInputJavaElement());
        refreshJob.schedule();
    }

    private final UpdateTitleImageJob refreshJob = new UpdateTitleImageJob();

    private class UpdateTitleImageJob extends UIJob {
        private IJavaElement elem;

        UpdateTitleImageJob() {
            super(UIMessages.editor_title_refresh_job);
            setSystem(true);
        }

        public void setElement(IJavaElement element) {
            elem = element;
        }

        public IStatus runInUIThread(IProgressMonitor monitor) {
            if (elem != null && aspectJEditorErrorTickUpdater != null) {
                aspectJEditorErrorTickUpdater.updateEditorImage(elem);
            }
            return Status.OK_STATUS;
        }
    }
}
