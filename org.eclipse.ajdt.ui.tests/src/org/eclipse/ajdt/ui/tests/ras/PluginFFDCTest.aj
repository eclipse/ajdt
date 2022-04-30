package org.eclipse.ajdt.ui.tests.ras;

import org.eclipse.ajdt.core.ras.PluginFFDC;
import org.eclipse.ajdt.ui.tests.AspectJTestPlugin;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;

/**
 * Test the FFDC aspect and its usage in AJDT
 */
public class PluginFFDCTest extends UITestCase {

	public void testFFDC () {
		LogListener listener = new LogListener(getPlugin());
		String message = "testFFDC"; //$NON-NLS-1$

		try {
			throw new Exception(message);
		}
		catch (Exception ignored) {
		}

		assertMessage(listener,message);
	}

	public void testRogueFFDC () {
		LogListener listener = new LogListener(getPlugin());
		String message = "testRogueFFDC"; //$NON-NLS-1$

		try {
			throw new Exception(message);
		}
		catch (Exception ignored) {
		}

		assertMessage(listener,message);
	}

// TODO: AspectJPlugin.getResourceString() has been removed - need
// to find another way of testing the FFDC aspect
//	public void testCoreFFDC () {
//		LogListener listener = new LogListener(AspectJPlugin.getDefault());
//		String key = "bogus.bogus";
//
//		String result = AspectJPlugin.getResourceString(key);
//
//		assertEquals("Resource should not be found",key,result);
//		assertMessage(listener,key);
//	}
//
//	public void testUIFFDC () {
//		LogListener listener = new LogListener(AspectJUIPlugin.getDefault());
//		String key = "bogus.bogus";
//
//		String result = AspectJUIPlugin.getResourceString(key);
//
//		assertEquals("Resource should not be found",key,result);
//		assertMessage(listener,key);
//	}

	public static class LogListener implements ILogListener {

		private IStatus status;

		public LogListener (Plugin plugin) {
			plugin.getLog().addLogListener(this);
		}

		public boolean hasMessage (String message) {
			return (status != null && status.getMessage().contains(message));
		}

		public void logging(IStatus status, String plugin) {
			this.status = status;
//			System.err.println(status);
		}

	}

	private static aspect TestFFDCAspect extends PluginFFDC {

		protected pointcut ffdcScope () :
			within(PluginFFDCTest) &&
			cflow(execution(void testFFDC()));


	    protected String getPluginId () {
	    	return AspectJTestPlugin.getPluginId();
	    }

	    protected void log (IStatus status) {
	    	AspectJTestPlugin.getDefault().getLog().log(status);
	    }

	}

	private static aspect RogueFFDCAspect extends PluginFFDC {

		protected pointcut ffdcScope () :
			within(PluginFFDCTest) &&
			cflow(execution(void testRogueFFDC()));


	    protected String getPluginId () {
	    	return null;
	    }

	    protected void log (IStatus status) {
	    	AspectJTestPlugin.getDefault().getLog().log(status);
	    }

	}

	public void assertMessage (LogListener listener, String expected) {
		if (!listener.hasMessage(expected)) {
			fail("The log did not contain the following message\n" + expected); //$NON-NLS-1$
		}
	}

	private static Plugin getPlugin () {
    	return AspectJTestPlugin.getDefault();
	}
}
