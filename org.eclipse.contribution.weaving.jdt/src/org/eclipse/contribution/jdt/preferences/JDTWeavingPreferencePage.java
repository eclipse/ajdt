package org.eclipse.contribution.jdt.preferences;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.contribution.jdt.JDTWeavingPlugin;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.internal.browser.WebBrowserPreference;
import org.eclipse.ui.internal.browser.WorkbenchBrowserSupport;

public class JDTWeavingPreferencePage
	extends PreferencePage
	implements IWorkbenchPreferencePage {

    private final WeavingStateConfigurer configurer;

	public JDTWeavingPreferencePage() {
		super("JDT Weaving preferences");
		setDescription("Preferences for the JDT Weaving plugin.  Enable and disable the weaving service here.");
		configurer = new WeavingStateConfigurer();
	}

    @Override
    protected Control createContents(Composite parent) {
        noDefaultAndApplyButton();

        final Composite area = new Composite(parent, SWT.NONE);

        GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 1;
        area.setLayout(gridLayout);

        Label messageLabel = new Label(area, SWT.NONE);
        messageLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false,
                false));
        if (configurer.isWeaving()) {
            messageLabel.setText("JDT Weaving is currently ENABLED");
        } else {
            messageLabel.setText("JDT Weaving is currently DISABLED");
        }

        Button changeWeavingButton = new Button(area, SWT.PUSH);
        changeWeavingButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false,
                false));
        if (configurer.isWeaving()) {
            changeWeavingButton.setText("Click to DISABLE (requires restart)");
        } else {
            changeWeavingButton.setText("Click to ENABLE (requires restart)");
        }

        changeWeavingButton.addSelectionListener(new SelectionListener() {

            public void widgetSelected(SelectionEvent e) {
                changeWeavingState(area.getShell());
            }

            public void widgetDefaultSelected(SelectionEvent e) {
                changeWeavingState(area.getShell());
            }
        });


        Label reindexLabel = new Label(area, SWT.NONE);
        reindexLabel.setText("Click here if you want to reindex your workspace so\n" +
        		"that all Java-like compilation units can be located by the indexer\n " +
        		"(e.g., during Java searches)");

        Button reindexButton = new Button(area, SWT.PUSH );
        reindexButton.setText("Reindex now");
        reindexButton.addSelectionListener(new SelectionListener() {
            public void widgetSelected(SelectionEvent e) {
                new ReindexingJob().schedule();
            }
            public void widgetDefaultSelected(SelectionEvent e) {
                new ReindexingJob().schedule();
            }
        });

        // warning label if wrong version of the weaver is being used.
        Label warningLabel = new Label(area, SWT.NONE);
        warningLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false,
                false));
        warningLabel.setText(
                "In order to access the more advanced features of AJDT\n" +
        		"(such as AspectJ-aware content assist and eager parsing), it is\n" +
        		"necessary to enable the weaving service.  With the weaving service\n" +
        		"enabled, Eclipse may require more resources.  If you encounter any\n" +
        		"sluggishness or memory problems, it is recommended that you increase\n" +
        		"your Xmx and PermGen sizes to at least 512 and 128 respectively, using\n" +
        		"something like the following vmargs when launching eclipse:");

        Text vmargsText = new Text(area, SWT.READ_ONLY | SWT.SINGLE);
        vmargsText.setBackground(area.getBackground());
        vmargsText.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false,
                false));
        vmargsText.setText("\t-vmargs -Xmx512M -XX:MaxPermSize=128M");

        Link moreInfoLink = new Link(area, SWT.BORDER);
        moreInfoLink.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false,
                false));
        moreInfoLink.setText("<a href=\"http://wiki.eclipse.org/JDT_weaving_features\">More information...</a>");
        moreInfoLink.addListener (SWT.Selection, event -> openUrl(event.text));
        Label weaverVersion = new Label(area, SWT.NONE);
        weaverVersion.setText(new WeavingStateConfigurer().getWeaverVersionInfo());


        return area;
    }

    public void init(IWorkbench workbench) {
    }

    private void changeWeavingState(Shell shell) {
        new WeavingStateConfigurerUI(shell, configurer).askFromPreferences();
    }

    public static void openUrl(String location) {
        try {
            URL url = null;

            if (location != null) {
                url = new URL(location);
            }
            if (WebBrowserPreference.getBrowserChoice() == WebBrowserPreference.EXTERNAL) {
                try {
                    IWorkbenchBrowserSupport support = PlatformUI
                            .getWorkbench().getBrowserSupport();
                    support.getExternalBrowser().openURL(url);
                } catch (Exception e) {
                    JDTWeavingPlugin.logException("Could not open browser", e);
                }
            } else {
                IWebBrowser browser;
                int flags = 0;
                if (WorkbenchBrowserSupport.getInstance()
                        .isInternalWebBrowserAvailable()) {
                    flags |= IWorkbenchBrowserSupport.AS_EDITOR
                            | IWorkbenchBrowserSupport.LOCATION_BAR
                            | IWorkbenchBrowserSupport.NAVIGATION_BAR;
                } else {
                    flags |= IWorkbenchBrowserSupport.AS_EXTERNAL
                            | IWorkbenchBrowserSupport.LOCATION_BAR
                            | IWorkbenchBrowserSupport.NAVIGATION_BAR;
                }

                String id = "org.eclipse.contribution.weaving.jdt";
                browser = WorkbenchBrowserSupport.getInstance().createBrowser(
                        flags, id, null, null);
                browser.openURL(url);
            }
        } catch (PartInitException e) {
            MessageDialog.openError(Display.getDefault().getActiveShell(),
                    "Browser initialization error",
                    "Browser could not be initiated");
        } catch (MalformedURLException e) {
            MessageDialog.openInformation(Display.getDefault()
                    .getActiveShell(), "Malformed URL",
                    location);
        }
    }
}
