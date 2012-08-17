package org.infinitest.testrunner;

import java.util.*;

import com.android.ddmlib.testrunner.*;

public class EmptyTestRunListener implements ITestRunListener {
	public void testRunStarted(String runName, int testCount) {
	}

	public void testStarted(TestIdentifier test) {
	}

	public void testFailed(TestFailure status, TestIdentifier test, String trace) {
	}

	public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
	}

	public void testRunFailed(String errorMessage) {
	}

	public void testRunStopped(long elapsedTime) {
	}

	public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
	}
}
