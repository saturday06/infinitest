package org.infinitest.testrunner;

import java.io.*;

public class AndroidJUnit3Runner implements NativeRunner {
	public TestResults runTest(String testClass) {
		try {
			File d = AndroidUtil.getProjectDirectory();
			File m = new File(d, "AndroidManifest.xml");
			if (!m.exists()) {
				AndroidUtil.log("!\"" + m + "\".exists()");
				AndroidUtil.log(JUnit4Runner.class.getName());
				return new JUnit4Runner().runTest(testClass);
			}
			AndroidUtil.log(AndroidJUnit3Runner.class.getName());
			AndroidUtil.installAPK(d);
			AndroidUtil.installAPK(AndroidUtil.getTestedProjectDirectory());
			return new TestResults(AndroidUtil.runTests(testClass));
		} catch (InterruptedException e) {
			return new TestResults(TestEvent.methodFailed(AndroidJUnit3Runner.class.getSimpleName(), "runTests()", e));
		} catch (IOException e) {
			AndroidUtil.log(e);
			return new TestResults(TestEvent.methodFailed(AndroidJUnit3Runner.class.getSimpleName(), "runTests()", e));
		}
	}
}
