package org.eclipse.ajdt.internal.ui.tracing;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.ajdt.internal.ui.help.IAJHelpContextIds;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.IConsoleDocumentPartitioner;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.TextConsole;
import org.eclipse.ui.part.IPageBookViewPage;

public class AJDTEventTraceConsole extends TextConsole {

    public final static String CONSOLE_TYPE = "ajdtEventTraceConsole"; //$NON-NLS-1$

    static final String BUILD_CLASSPATH = ":BC: ";
    static final String BUILDER = ":B:  ";
    static final String COMPILER = ":C:  ";
    final static Token COMPILER_TOKEN = new Token(COMPILER);
    final static Token BUILDER_TOKEN = new Token(BUILDER);
    final static Token BUILD_CLASSPATH_TOKEN = new Token(BUILD_CLASSPATH);


    final static RuleBasedPartitionScanner scanner = new RuleBasedPartitionScanner();
    static {
        scanner.setPredicateRules(new IPredicateRule[] {
                new SingleLineRule(COMPILER, "", COMPILER_TOKEN),
                new SingleLineRule(BUILDER, "", BUILDER_TOKEN),
                new SingleLineRule(BUILD_CLASSPATH, "", BUILD_CLASSPATH_TOKEN)
        });
    }
    /**
     * Provides a partitioner for this console type
     */
    class AJDTEventTraceConsolePartitioner extends FastPartitioner implements IConsoleDocumentPartitioner {

        Set styles = new HashSet();

        public AJDTEventTraceConsolePartitioner() {
            super(scanner, new String[] {COMPILER, BUILDER, BUILD_CLASSPATH});
            getDocument().setDocumentPartitioner(this);
        }

        public boolean isReadOnly(int offset) {
            return true;
        }

        public StyleRange[] getStyleRanges(int offset, int length) {
            ITypedRegion[] regions = computePartitioning(offset, length);
            StyleRange[] styles = new StyleRange[regions.length];
            for (int i = 0; i < regions.length; i++) {
                if (COMPILER.equals(regions[i].getType())) {
                    styles[i] = new StyleRange(offset, length,
                            Display.getDefault().getSystemColor(SWT.COLOR_DARK_GREEN),
                            null);
                } else if (BUILDER.equals(regions[i].getType())) {
                    styles[i] = new StyleRange(offset, length,
                            Display.getDefault().getSystemColor(SWT.COLOR_DARK_BLUE),
                            null);
                } else if (BUILD_CLASSPATH.equals(regions[i].getType())) {
                    styles[i] = new StyleRange(offset, length,
                            Display.getDefault().getSystemColor(SWT.COLOR_DARK_RED),
                            null);
                } else {
                    styles[i] = new StyleRange(offset, length,
                            null, null);
                }
            }
            return styles;
        }

        void addStyle(StyleRange style) {
            styles.add(style);
        }

        void clear() {
            styles.clear();
        }

    }

    public void clearConsole() {
        super.clearConsole();
        ((AJDTEventTraceConsolePartitioner) getPartitioner()).clear();
    }

    private final AJDTEventTraceConsolePartitioner partitioner = new AJDTEventTraceConsolePartitioner();
    private final IPropertyChangeListener propertyListener = event -> {
        String property = event.getProperty();
        if (property.equals(IDebugUIConstants.PREF_CONSOLE_FONT)) {
            setFont(JFaceResources.getFont(IDebugUIConstants.PREF_CONSOLE_FONT));
        }
    };


    public AJDTEventTraceConsole() {
        super("AJDT Event Trace Console", CONSOLE_TYPE, null, true);
        Font font = JFaceResources.getFont(IDebugUIConstants.PREF_CONSOLE_FONT);
        setFont(font);
        partitioner.connect(getDocument());
    }

    /**
     * @see org.eclipse.ui.console.AbstractConsole#init()
     */
    protected void init() {
        JFaceResources.getFontRegistry().addListener(propertyListener);
    }

    /**
     * @see org.eclipse.ui.console.TextConsole#dispose()
     */
    protected void dispose() {
        JFaceResources.getFontRegistry().removeListener(propertyListener);
        super.dispose();
    }

    /**
     * @see org.eclipse.ui.console.TextConsole#getPartitioner()
     */
    protected IConsoleDocumentPartitioner getPartitioner() {
        return partitioner;
    }

    /**
     * @see org.eclipse.ui.console.TextConsole#createPage(org.eclipse.ui.console.IConsoleView)
     */
    public IPageBookViewPage createPage(IConsoleView view) {
        return new AJDTEventTraceConsolePage(this, view);
    }



    /**
     * @see org.eclipse.ui.console.AbstractConsole#getHelpContextId()
     */
    public String getHelpContextId() {
        return IAJHelpContextIds.EVENT_TRACE_VIEW;
    }
}
