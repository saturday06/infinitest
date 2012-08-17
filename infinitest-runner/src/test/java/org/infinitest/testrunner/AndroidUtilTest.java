package org.infinitest.testrunner;

import java.io.*;
import java.util.*;

import junit.framework.*;

import com.android.ddmlib.*;

public class AndroidUtilTest extends TestCase {
	public void testADBUsage() throws Exception {
		AndroidDebugBridge adb = AndroidUtil.getADB();
		assertNotNull(adb);
		assertTrue(adb.isConnected());
	}

	public void testMatchDeviceName() {
		assertTrue(AndroidUtil.matchDeviceSerialNumber("foo", "foo"));
		assertTrue(AndroidUtil.matchDeviceSerialNumber("foo:bar", "foo:bar"));
		assertTrue(AndroidUtil.matchDeviceSerialNumber("foo", "foo:bar"));
		assertFalse(AndroidUtil.matchDeviceSerialNumber("foo:bar", "foo"));
		assertFalse(AndroidUtil.matchDeviceSerialNumber("foo:baz", "foo:bar"));
		assertFalse(AndroidUtil.matchDeviceSerialNumber("foo:bar:baz", "foo:bar"));
	}

	public void testGetLogFile() throws Exception {
		File f = AndroidUtil.getLogFile();
		assertTrue(f.exists());
		assertTrue(f.canWrite());
	}

	public void testGetApplicationDirectory() throws IOException {
		File d = AndroidUtil.getApplicationDirectory();
		assertTrue(d.exists());
		assertTrue(d.isDirectory());
		assertTrue(d.canWrite());
	}

	public void testSaveAPKTimestamp() {
		File f = new File(".");
		Long d = new Date().getTime();
		Map<File, Long> s = new HashMap<File, Long>();
		s.put(f, d);

		AndroidUtil.saveAPKTimestamp(s);
		Map<File, Long> l = AndroidUtil.loadAPKTimestamp();

		assertEquals(d, l.get(f));
	}

	public void testLoadAPKTimestamp() {
		AndroidUtil.loadAPKTimestamp();
	}

	public void testLog() throws Exception {
		File f = AndroidUtil.getLogFile();
		assertTrue(f.delete());
		String d1 = "abc";
		String d2 = "aaaaabbbbbcccccdddddeeeeefffff";
		AndroidUtil.log(d1);
		Long l1 = f.length();
		assertTrue(l1 > d1.length());
		AndroidUtil.log(d2);
		Long l2 = f.length();
		assertTrue((l2 - l1) > d2.length());
		assertTrue((l2 - l1) > l1);
	}

	public void testGetADBFile() throws Exception {
		File f = new File(AndroidUtil.getADBPath());
		assertTrue(f.exists());
		assertTrue(f.canExecute());
	}

	public void testGetAPKPath() throws Exception {
		File p = AndroidUtil.getAPKFile(new File("."));
		assertTrue(p.exists());
		assertTrue(p.toString().endsWith(".apk"));
	}

	public void testGetDevice() throws IOException, InterruptedException {
		IDevice d = AndroidUtil.getDevice();
		assertEquals(AndroidUtil.getDeviceSerialNumber(), d.getSerialNumber());
	}

	public void testGetDeviceSerialNumber() throws IOException {
		String s = AndroidUtil.getDeviceSerialNumber();
		assertFalse(s.isEmpty());
	}

	public void testGetLocalProperties() throws Exception {
		Properties p = AndroidUtil.getAndroidProperties(new File("."));
		assertNotNull(p);
		assertFalse(p.isEmpty());
	}

	public void testGetManifestPackage() throws Exception {
		String mp = AndroidUtil.getManifestPackage(new File("."));
		assertFalse(mp.isEmpty());
	}

	public void testGetPackageBaseName() throws Exception {
		String n = AndroidUtil.getAPKBaseName(new File("."));
		assertFalse(n.isEmpty());
		assertNotSame(AndroidUtil.getAPKBaseName(new File(".")), AndroidUtil.getAPKBaseName(AndroidUtil.getTestedProjectDirectory()));
	}

	public void testGetTestedAPKPath() throws Exception {
		File p = AndroidUtil.getAPKFile(AndroidUtil.getTestedProjectDirectory());
		assertTrue(p.exists());
		assertTrue(p.toString().endsWith(".apk"));
	}

	public void testGetTestedPackageBaseName() throws Exception {
		File tpd = AndroidUtil.getTestedProjectDirectory();
		String n = AndroidUtil.getAPKBaseName(tpd);
		assertFalse(n.isEmpty());
		assertNotSame(AndroidUtil.getAPKBaseName(tpd), AndroidUtil.getAPKBaseName(tpd));
	}

	public void testGetTestedProjectDir() throws IOException {
		File tpd = AndroidUtil.getTestedProjectDirectory();
		assertTrue(tpd.exists());
		assertTrue(tpd.isDirectory());
	}

	public void testInstallAPK() throws Exception, IOException {
		AndroidUtil.installAPK(new File("."));
	}

	public void testInstallAPKReinstall() throws Exception {
		File f = new File(".");
		File apkFile = AndroidUtil.getAPKFile(f);
		AndroidUtil.installAPK(f);

		long install1Start = System.currentTimeMillis();
		AndroidUtil.installAPK(f);
		long install1Elapsed = System.currentTimeMillis() - install1Start;

		apkFile.setLastModified(apkFile.lastModified() + 1);

		long install2Start = System.currentTimeMillis();
		AndroidUtil.installAPK(f);
		long install2Elapsed = System.currentTimeMillis() - install2Start;

		long install3Start = System.currentTimeMillis();
		AndroidUtil.installAPK(f);
		long install3Elapsed = System.currentTimeMillis() - install3Start;

		apkFile.setLastModified(apkFile.lastModified() + 1);

		long install4Start = System.currentTimeMillis();
		AndroidUtil.installAPK(f);
		long install4Elapsed = System.currentTimeMillis() - install4Start;

		assertTrue(install1Elapsed + " * 2 < " + install2Elapsed, (install1Elapsed * 2) < install2Elapsed);
		assertTrue(install3Elapsed + " * 2 < " + install2Elapsed, (install3Elapsed * 2) < install2Elapsed);

		assertTrue(install1Elapsed + " * 2 < " + install4Elapsed, (install1Elapsed * 2) < install4Elapsed);
		assertTrue(install3Elapsed + " * 2 < " + install4Elapsed, (install3Elapsed * 2) < install4Elapsed);
	}

	public void testInstallTestedAPK() throws Exception, IOException {
		AndroidUtil.installAPK(AndroidUtil.getTestedProjectDirectory());
	}

	public void testParseStackTrace() {
		String m = "junit.framework.AssertionFailedError";
		String s = m + "\n" + "at com.example.test.SimpleTestCase.testBarFailure(SimpleTestCase.java:11)\n" + "at java.lang.reflect.Method.invokeNative(Native Method)\n" + "at android.test.AndroidTestRunner.runTest(AndroidTestRunner.java:169)\n" + "at android.test.AndroidTestRunner.runTest(AndroidTestRunner.java:154)\n" + "at android.test.InstrumentationTestRunner.onStart(InstrumentationTestRunner.java:529)\n" + "at android.app.Instrumentation$InstrumentationThread.run(Instrumentation.java:1448)\n";

		Throwable t = AndroidUtil.parseStackTrace(s);
		assertEquals(m, t.getMessage());
		StackTraceElement[] stes = t.getStackTrace();
		assertEquals(new StackTraceElement("com.example.test.SimpleTestCase", "testBarFailure", "SimpleTestCase.java", 11), stes[0]);
		assertEquals(new StackTraceElement("java.lang.reflect.Method", "invokeNative", "Native Method", 0), stes[1]);
		assertEquals(new StackTraceElement("android.test.AndroidTestRunner", "runTest", "AndroidTestRunner.java", 169), stes[2]);
		assertEquals(new StackTraceElement("android.test.AndroidTestRunner", "runTest", "AndroidTestRunner.java", 154), stes[3]);
		assertEquals(new StackTraceElement("android.test.InstrumentationTestRunner", "onStart", "InstrumentationTestRunner.java", 529), stes[4]);
		assertEquals(new StackTraceElement("android.app.Instrumentation$InstrumentationThread", "run", "Instrumentation.java", 1448), stes[5]);
	}

	public void testRunTests() throws Exception {
		File dir = new File(".");
		AndroidUtil.installAPK(dir);
		AndroidUtil.installAPK(AndroidUtil.getTestedProjectDirectory());
		List<TestEvent> events1 = AndroidUtil.runTests("com.example.test.SimpleTestCase");
		assertEquals(2, events1.size());
		events1.get(0).getTestName().equals("com.example.test.SimpleTestCase");

		List<TestEvent> events2 = AndroidUtil.runTests("com.example.test.ComplexTestCase");
		assertEquals(2, events2.size());
		events2.get(1).getTestName().equals("com.example.test.ComplexTestCase");
	}

	public void testNewThrowable() {
		String m1 = "message1";
		StackTraceElement[] s1 = new StackTraceElement[0];
		String n1 = "junit.framework.AssertionFailedError";
		Throwable t1 = AndroidUtil.newThrowable(n1, m1, s1);
		assertEquals(n1, t1.getClass().getName());
		// assertEquals("message1", t1.getMessage());

		String m2 = "message2";
		StackTraceElement[] s2 = new StackTraceElement[0];
		String n2 = ";) no class";
		Throwable t2 = AndroidUtil.newThrowable(n2, m2, s2);
		assertEquals(AssertionFailedError.class.getName(), t2.getClass().getName());
		// assertEquals("message2", t2.getMessage());

		assertFalse(s1 == s2);
	}

	public void testGetProjectDirectory() throws Exception {
		File d = AndroidUtil.getProjectDirectory();
		assertTrue(d.isDirectory());
		assertEquals(new File(".").getAbsoluteFile().getCanonicalFile(), d.getAbsoluteFile().getCanonicalFile());
	}
}
