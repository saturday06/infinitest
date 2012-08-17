package org.infinitest.testrunner;

import java.io.*;
import java.lang.reflect.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;

import javax.xml.parsers.*;
import javax.xml.xpath.*;

import junit.framework.*;

import org.xml.sax.*;

import com.android.ddmlib.*;
import com.android.ddmlib.testrunner.*;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.io.*;

public class AndroidUtil {
	public static final String EXE_SUFFIX;

	static {
		AndroidDebugBridge.init(false);
		if (System.getProperty("os.name", "").indexOf("Windows") >= 0) {
			EXE_SUFFIX = ".exe";
		} else {
			EXE_SUFFIX = "";
		}
	}

	public static AndroidDebugBridge getADB() throws InterruptedException, IOException {
		AndroidDebugBridge adb = AndroidDebugBridge.getBridge();
		if (adb == null) {
			String adbPath = AndroidUtil.getADBPath();
			adb = AndroidDebugBridge.createBridge(adbPath, false);
		}
		for (int i = 0; i < 50; ++i) {
			Thread.sleep(100);
			if (adb.isConnected()) {
				return adb;
			}
		}
		throw new IOException("can't connect to adb");
	}

	public static String getADBPath() throws IOException {
		Properties p = getAndroidProperties(getProjectDirectory());
		String k = "sdk.dir";
		String sdkDirString = p.getProperty(k);
		if (sdkDirString == null) {
			throw new IOException("property " + k + " not found");
		}
		File sdkDir = new File(sdkDirString);
		if (!sdkDir.exists()) {
			throw new IOException("sdkDir \"" + sdkDir + "\" not found");
		}
		File adbFile = new File(sdkDir, "platform-tools" + File.separator + "adb" + EXE_SUFFIX);
		if (!adbFile.exists()) {
			throw new IOException("adbFile \"" + adbFile + "\" not found");
		}
		return adbFile.getAbsoluteFile().getCanonicalPath();
	}

	public static Properties getAndroidProperties(File dir) throws IOException {
		Properties p = new Properties();
		for (String path : new String[] { "project.properties", "ant.properties", "local.properties" }) {
			if (!new File(path).exists()) {
				continue;
			}
			InputStream is = null;
			try {
				is = new FileInputStream(path);
				p.load(is);
			} finally {
				Closeables.closeQuietly(is);
			}
		}
		return p;
	}

	private static void validateAPKFile(File apkFile) throws IOException, InterruptedException {
		for (int i = 0; i < 30; ++i) {
			if (apkFile.length() > 0) {
				break;
			}
			log("!\"" + apkFile + "\".exists() retry");
			Thread.sleep(1000);
		}
		if (!apkFile.exists()) {
			throw new FileNotFoundException(apkFile.getCanonicalPath());
		}
	}

	public static File getAPKFile(File dir) throws IOException, InterruptedException {
		File f = new File(new File(dir, "bin"), getAPKBaseName(dir) + ".apk").getAbsoluteFile().getCanonicalFile();
		validateAPKFile(f);
		return f;
	}

	public static IDevice getDevice() throws IOException, InterruptedException {
		AndroidDebugBridge adb = getADB();

		boolean connected = false;
		do {
			IDevice[] devices = adb.getDevices();
			String serialNumber = "";
			try {
				serialNumber = getDeviceSerialNumber();
			} catch (IOException e) {
				if (devices.length == 0) {
					throw new IOException("device not found");
				} else if (devices.length == 1) {
					return devices[0];
				} else {
					throw new IOException("more than one device found");
				}
			}

			for (IDevice d : devices) {
				if (matchDeviceSerialNumber(serialNumber, d.getSerialNumber())) {
					return d;
				}
			}

			if (connected) {
				throw new IOException("device " + serialNumber + " not found");
			}

			new ProcessBuilder().command(getADBPath(), "connect", serialNumber).start().waitFor();
			connected = true;
		} while (true);
	}

	public static String getDeviceSerialNumber() throws IOException {
		String s = System.getenv("INFINITEST_ANDROID_SN");
		if (s != null) {
			return s;
		}

		Properties p = getAndroidProperties(getProjectDirectory());
		String k = "infinitest.android.adb.device.serialnumber";
		s = p.getProperty(k);
		if (s == null) {
			throw new IOException("property " + k + " not found");
		}
		return s;
	}

	public static String getManifestPackage(File dir) throws IOException {
		return queryXPath(new File(dir, "AndroidManifest.xml"), "/manifest/@package");
	}

	public static String getAPKBaseName(File dir) throws IOException {
		return queryXPath(new File(dir, ".project"), "/projectDescription/name");
	}

	public static File getTestedProjectDirectory() throws IOException {
		Properties p = getAndroidProperties(getProjectDirectory());
		String k = "tested.project.dir";
		String testedProjectDirString = p.getProperty(k);
		if (testedProjectDirString == null) {
			throw new IOException("property " + k + " not found");
		}
		File testedProjectDir = new File(testedProjectDirString);
		if (!testedProjectDir.exists()) {
			throw new IOException("!\"" + testedProjectDir + "\".exists()");
		}
		if (!testedProjectDir.isDirectory()) {
			throw new IOException("!\"" + testedProjectDir + "\".isDirectory()");
		}
		return testedProjectDir.getCanonicalFile();
	}

	public static void installAPK(File dir) throws IOException, InterruptedException {
		long startMillis = System.currentTimeMillis();
		log("installAPK start");
		IDevice d = AndroidUtil.getDevice();
		File apkFile = AndroidUtil.getAPKFile(dir);
		log("installAPK apkFile=" + apkFile);
		long lastModified = apkFile.lastModified();
		Map<File, Long> m = loadAPKTimestamp();
		if (m.containsKey(apkFile) && m.get(apkFile).equals(lastModified)) {
			log("installAPK skipped millis=" + (System.currentTimeMillis() - startMillis));
			return;
		}
		m.put(apkFile, lastModified);
		saveAPKTimestamp(m);

		for (int i = 0; i < 5; ++i) {
			try {
				String error = d.installPackage(apkFile.getCanonicalPath(), true);
				if (error == null) {
					log("installAPK end millis=" + (System.currentTimeMillis() - startMillis));
					return;
				}
				log("installPackage: " + error);
			} catch (InstallException e) {
				log(e);
			}
			Thread.sleep(1000);
		}

		log("installAPK aborted millis=" + (System.currentTimeMillis() - startMillis));
		throw new IOException("install \"" + apkFile + "\" failed");
	}

	public static Throwable parseStackTrace(String s) {
		log("---------------------- stack trace ----------------------");
		log(s);
		log("-------------------- stack trace end --------------------");
		LinkedList<String> st = Lists.newLinkedList(Splitter.onPattern("\\r\\n|\\r|\\n").split(s));
		String firstLine = st.pop();
		log("firstLine=" + firstLine);
		LinkedList<String> classNameAndMessage = Lists.newLinkedList(Splitter.on(": ").limit(2).split(firstLine));
		String className = classNameAndMessage.pop();
		String message = classNameAndMessage.isEmpty() ? "" : classNameAndMessage.pop();
		log("className=" + className + " message=" + message);
		List<StackTraceElement> ses = new LinkedList<StackTraceElement>();
		Pattern p = Pattern.compile("^at (.+)\\.([^\\.]+)\\((.+)\\)$");
		Pattern p2 = Pattern.compile("(.+):([0-9]+)$");
		for (String ss : st) {
			log("------------------------------");
			log("line=" + ss);
			Matcher m = p.matcher(ss);
			if (!m.find()) {
				log("*** no match ***");
				continue;
			}
			String declaringClass = m.group(1);
			String methodName = m.group(2);
			String fileName = m.group(3);
			Integer lineNumber = 0;
			Matcher m2 = p2.matcher(fileName);
			log("declaringClass=" + declaringClass + " methodName=" + methodName);
			if (m2.find()) {
				fileName = m2.group(1);
				lineNumber = Integer.parseInt(m2.group(2));
				log("fileName=" + fileName + " lineNumber=" + lineNumber);
			} else {
				log("fileName=" + fileName);
			}
			ses.add(new StackTraceElement(declaringClass, methodName, fileName, lineNumber));
		}
		return newThrowable(className, message, ses.toArray(new StackTraceElement[0]));
	}

	private static String queryXPath(File file, String xpath) throws IOException {
		try {
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilderFactory.setNamespaceAware(true);
			return XPathFactory.newInstance().newXPath().compile(xpath).evaluate(documentBuilderFactory.newDocumentBuilder().parse(file.getCanonicalFile()));
		} catch (XPathExpressionException e) {
			throw new IOException(e);
		} catch (SAXException e) {
			throw new IOException(e);
		} catch (IOException e) {
			throw e;
		} catch (ParserConfigurationException e) {
			throw new IOException(e);
		}
	}

	public static List<TestEvent> runTests(String className) throws IOException, InterruptedException {
		try {
			File dir = getProjectDirectory();
			IDevice d = AndroidUtil.getDevice();
			RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(AndroidUtil.getManifestPackage(dir), "android.test.InstrumentationTestRunner", d);
			final List<TestEvent> events = new LinkedList<TestEvent>();
			runner.setClassName(className);
			runner.run(new EmptyTestRunListener() {
				@Override
				public void testFailed(TestFailure status, TestIdentifier test, String trace) {
					TestEvent e = TestEvent.methodFailed(test.getClassName(), test.getTestName(), AndroidUtil.parseStackTrace(trace));
					events.add(e);
				}
			});
			return events;
		} catch (TimeoutException e) {
			throw new IOException(e);
		} catch (AdbCommandRejectedException e) {
			throw new IOException(e);
		} catch (ShellCommandUnresponsiveException e) {
			throw new IOException(e);
		}
	}

	public static File getApplicationDirectory() throws IOException {
		File d = new File(System.getProperty("user.home"), ".infinitest-android");
		if (!d.exists() && !d.mkdirs()) {
			throw new IOException("!\"" + d + "\".mkdirs()");
		}
		return d;
	}

	public static File getLogFile() throws IOException {
		File f = new File(getApplicationDirectory(), "log.txt");
		if (!f.exists() && !f.createNewFile()) {
			throw new IOException("!\"" + f + "\".createNewFile()");
		}
		return f;
	}

	public static void log(String message) {
		String f = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")).format(new Date());
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(getLogFile(), true);
			fos.write(("[" + f + "] " + message + "\r\n").getBytes("UTF-8"));
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			Closeables.closeQuietly(fos);
		}
	}

	private static class FileLongMap extends HashMap<File, Long> {
		private static final long serialVersionUID = -7949847935105133823L;

		public FileLongMap(Map<File, Long> map) {
			super(map);
		}
	}

	public static Map<File, Long> loadAPKTimestamp() {
		FileInputStream fis = null;
		ObjectInputStream ois = null;
		try {
			fis = new FileInputStream(getAPKTimestampFile());
			ois = new ObjectInputStream(fis);
			Object o = ois.readObject();
			if (o instanceof FileLongMap) {
				return (FileLongMap) o;
			}
		} catch (FileNotFoundException e) {
			log(e + "\r\n" + e.getMessage());
		} catch (IOException e) {
			log(e + "\r\n" + e.getMessage());
		} catch (ClassNotFoundException e) {
			log(e + "\r\n" + e.getMessage());
		} finally {
			Closeables.closeQuietly(ois);
			Closeables.closeQuietly(fis);
		}
		return new HashMap<File, Long>();
	}

	private static File getAPKTimestampFile() throws IOException {
		return new File(getApplicationDirectory(), "timestamp.serialized");
	}

	public static void saveAPKTimestamp(Map<File, Long> m) {
		FileOutputStream fos = null;
		ObjectOutputStream oos = null;
		try {
			fos = new FileOutputStream(getAPKTimestampFile());
			oos = new ObjectOutputStream(fos);
			oos.writeObject(new FileLongMap(m));
		} catch (IOException e) {
			log(e + "\r\n" + e.getMessage());
		} finally {
			Closeables.closeQuietly(oos);
			Closeables.closeQuietly(fos);
		}
	}

	public static void log(Throwable e) {
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		log(e + "\r\n" + sw);
	}

	public static boolean matchDeviceSerialNumber(String configSerialNumber, String deviceSerialNumber) {
		if (configSerialNumber.equals(deviceSerialNumber)) {
			return true;
		}
		if (configSerialNumber.indexOf(":") >= 0) {
			return false;
		}
		return configSerialNumber.equals(deviceSerialNumber.split(":")[0]);
	}

	public static Throwable newThrowable(String className, String message, StackTraceElement[] stackTraceElements) {
		Throwable t = new AssertionFailedError(className + (message.isEmpty() ? "" : ": " + message));
		try {
			Class<?> c = Class.forName(className);
			Class<? extends Throwable> c2 = c.asSubclass(Throwable.class);
			if (message.isEmpty()) {
				t = c2.newInstance();
			} else {
				Constructor<? extends Throwable> ctor = c2.getConstructor(String.class);
				t = ctor.newInstance(message);
			}
		} catch (ClassCastException e) {
		} catch (ClassNotFoundException e) {
		} catch (IllegalAccessException e) {
		} catch (IllegalArgumentException e) {
		} catch (InstantiationException e) {
		} catch (InvocationTargetException e) {
		} catch (NoSuchMethodException e) {
		} catch (SecurityException e) {
		}
		t.setStackTrace(stackTraceElements);
		return t;
	}

	public static File getProjectDirectory() throws IOException {
		return new File(".").getAbsoluteFile().getCanonicalFile();
	}

	public static Class<? extends NativeRunner> getNativeRunner() {
		return AndroidJUnit3Runner.class;
	}
}
