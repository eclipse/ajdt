package org.eclipse.ajdt.internal.ui.tracing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.internal.ui.preferences.AspectJPreferences;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.internal.ui.tracing.EventTrace.EventListener;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.jdt.internal.debug.ui.IJDIPreferencesConstants;
import org.eclipse.jdt.internal.debug.ui.IJavaDebugHelpContextIds;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jdt.internal.debug.ui.JavaDebugImages;
import org.eclipse.jdt.internal.debug.ui.console.ConsoleMessages;
import org.eclipse.jdt.internal.debug.ui.console.JavaStackTraceConsoleViewer;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.TextConsole;
import org.eclipse.ui.console.TextConsolePage;
import org.eclipse.ui.console.TextConsoleViewer;
import org.eclipse.ui.part.IPageSite;

public class AJDTEventTraceConsolePage extends TextConsolePage implements EventListener {

    public static class AutoFormatSettingAction extends Action {
        private final AJDTEventTraceConsolePage fPage;
        private final IPreferenceStore fPreferenceStore;

        public AutoFormatSettingAction(AJDTEventTraceConsolePage page) {
            super(ConsoleMessages.AutoFormatSettingAction_0, SWT.TOGGLE);
            fPage = page;

            setToolTipText(ConsoleMessages.AutoFormatSettingAction_1);
            setImageDescriptor(JavaDebugImages.getImageDescriptor(JavaDebugImages.IMG_ELCL_AUTO_FORMAT));
            setHoverImageDescriptor(JavaDebugImages.getImageDescriptor(JavaDebugImages.IMG_ELCL_AUTO_FORMAT));
            PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaDebugHelpContextIds.CONSOLE_AUTOFORMAT_STACKTRACES_ACTION);

            fPreferenceStore = JDIDebugUIPlugin.getDefault().getPreferenceStore();
            boolean checked = fPreferenceStore.getBoolean(IJDIPreferencesConstants.PREF_AUTO_FORMAT_JSTCONSOLE);
            setChecked(checked);
        }

        public void run() {
            boolean checked = isChecked();
            JavaStackTraceConsoleViewer viewer = (JavaStackTraceConsoleViewer) fPage.getViewer();
            viewer.setAutoFormat(checked);
            fPreferenceStore.setValue(IJDIPreferencesConstants.PREF_AUTO_FORMAT_JSTCONSOLE, checked);
        }
    }


    private FilterTraceAction filterAction;
    private PrintCrossCuttingModelAction printModelAction;

    public AJDTEventTraceConsolePage(TextConsole console, IConsoleView view) {
        super(console, view);
        EventTrace.addListener( this );
    }

    protected void createActions() {
        super.createActions();

        IActionBars actionBars= getSite().getActionBars();

        String dlogTitle = UIMessages.eventTrace_filter_dialog_title;
        String dlogMessage = UIMessages.eventTrace_filter_dialog_message;
        List<String> populatingList = Arrays.asList(DebugTracing.categoryNames);

        List<String> checkedList = AspectJPreferences.getEventTraceCheckedList();

        List<String> defaultList = new ArrayList<>();
        defaultList.add(DebugTracing.categoryNames[0]);
        defaultList.add(DebugTracing.categoryNames[3]);

        if (checkedList == null){
            checkedList = new ArrayList<>(defaultList);
        }
        DebugTracing.setDebugCategories(checkedList);

        filterAction = new FilterTraceAction(getSite().getShell(),
                populatingList, checkedList, defaultList, dlogTitle,
                dlogMessage, UIMessages.eventTrace_filter_action_tooltip);

        filterAction.fillActionBars(actionBars);

        printModelAction = new PrintCrossCuttingModelAction();
        printModelAction.fillActionBars(actionBars);
    }

    protected TextConsoleViewer createViewer(Composite parent) {
        TextConsoleViewer viewer = new TextConsoleViewer(parent, (AJDTEventTraceConsole) getConsole());
        viewer.setEditable(false);
        return viewer;
    }

    public void init(IPageSite pageSite) throws PartInitException {
        super.init(pageSite);

        DebugTracing.setDebug(true);
        ajdtEvent(DebugTracing.startupInfo(),AJLog.DEFAULT, new Date());
    }

    public void ajdtEvent(String msg, final int category, Date time) {
        /*
         * This code no longer dependent on either java.util.DateFormat, nor its ICU4J
         * version, while avoiding the deprecated methods in java.util.Date, hence the
         * slightly convoluted manner of extracting the time from the given date.
         *
         * -spyoung
         */
        Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(time);

        final String txt = calendar.get(Calendar.HOUR_OF_DAY) + ":"  //$NON-NLS-1$
            + calendar.get(Calendar.MINUTE) + ":" + calendar.get(Calendar.SECOND) + " " + msg + "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        AspectJUIPlugin.getDefault().getDisplay().asyncExec(() -> appendEventText(txt, category));
    }

    public void dispose() {
        super.dispose();
        DebugTracing.setDebug(false);
        AspectJPreferences.setEventTraceList(
                filterAction.getCheckedList());
    }

    private void appendEventText(String msg, int category) {
        StyledText text = getViewer().getTextWidget();

        String prefix;
        switch (category) {
        case AJLog.BUILDER:
            prefix = AJDTEventTraceConsole.BUILDER;
            break;
        case AJLog.BUILDER_CLASSPATH:
            prefix = AJDTEventTraceConsole.BUILD_CLASSPATH;
            break;
        case AJLog.COMPILER:
        case AJLog.COMPILER_PROGRESS:
        case AJLog.COMPILER_MESSAGES:
            prefix = AJDTEventTraceConsole.COMPILER;
            break;

        default:
            prefix = "";
            break;
        }

        text.append( prefix + msg );
        text.setTopIndex(text.getLineCount() - 1);
    }
}
