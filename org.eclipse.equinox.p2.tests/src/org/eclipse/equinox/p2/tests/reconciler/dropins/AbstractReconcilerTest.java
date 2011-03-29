/*******************************************************************************
 *  Copyright (c) 2008, 2010 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.reconciler.dropins;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.frameworkadmin.BundleInfo;
import org.eclipse.equinox.internal.p2.core.helpers.*;
import org.eclipse.equinox.internal.p2.engine.SimpleProfileRegistry;
import org.eclipse.equinox.internal.p2.engine.SurrogateProfileHandler;
import org.eclipse.equinox.internal.p2.jarprocessor.StreamProcessor;
import org.eclipse.equinox.internal.p2.update.*;
import org.eclipse.equinox.internal.p2.updatesite.Activator;
import org.eclipse.equinox.internal.simpleconfigurator.manipulator.SimpleConfiguratorManipulatorImpl;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;
import org.eclipse.equinox.p2.tests.TestActivator;
import org.eclipse.equinox.simpleconfigurator.manipulator.SimpleConfiguratorManipulator;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.Bundle;

public class AbstractReconcilerTest extends AbstractProvisioningTest {
	public static final String VERIFIER_BUNDLE_ID = "org.eclipse.equinox.p2.tests.verifier";
	protected static File output;
	protected static Set toRemove = new HashSet();
	private static boolean initialized = false;
	private static Properties archiveAndRepositoryProperties = null;

	private String propertyToPlatformArchive;

	static {
		loadPlatformZipPropertiesFromFile();
	}

	/*
			 * Constructor for the class.
			 */
	public AbstractReconcilerTest(String name) {
		super(name);
	}

	public AbstractReconcilerTest(String name, String propertyToPlatformArchive) {
		super(name);
		this.propertyToPlatformArchive = propertyToPlatformArchive;
	}

	/*
	 * Set up the platform binary download and get it ready to run the tests.
	 * This method is not intended to be called by clients, it will be called
	 * automatically when the clients use a ReconcilerTestSuite. If the executable isn't
	 * found on the file-system after the call, then we fail.
	 */
	public void initialize() throws Exception {
		initialized = false;
		File file = getPlatformZip();
		output = getUniqueFolder();
		toRemove.add(output);
		// for now we will exec to un-tar archives to keep the executable bits
		if (file.getName().toLowerCase().endsWith(".zip")) {
			try {
				FileUtils.unzipFile(file, output);
			} catch (IOException e) {
				fail("0.99", e);
			}
		} else {
			untar("1.0", file);
		}
		File exe = new File(output, "eclipse/eclipse.exe");
		if (!exe.exists()) {
			exe = new File(output, "eclipse/eclipse");
			if (!exe.exists())
				fail("Executable file: " + exe.getAbsolutePath() + "(or .exe) not found after extracting: " + file.getAbsolutePath() + " to: " + output);
		}
		initialized = true;
	}

	public void assertInitialized() {
		assertTrue("Test suite not initialized, check log for previous errors.", initialized);
	}

	/*
	 * Run the given command.
	 */
	protected static int run(String message, String[] commandArray) {
		try {
			Process process = Runtime.getRuntime().exec(commandArray, null, output);
			StreamProcessor.start(process.getErrorStream(), StreamProcessor.STDERR, true);
			StreamProcessor.start(process.getInputStream(), StreamProcessor.STDOUT, true);
			process.waitFor();
			return process.exitValue();
		} catch (IOException e) {
			fail(message, e);
		} catch (InterruptedException e) {
			fail(message, e);
		}
		return -1;
	}

	protected static int run(String message, String[] commandArray, File outputFile) {
		PrintStream out = System.out;
		PrintStream err = System.err;
		PrintStream fileStream = null;
		try {
			outputFile.getParentFile().mkdirs();
			fileStream = new PrintStream(new FileOutputStream(outputFile));
			System.setErr(fileStream);
			System.setOut(fileStream);
			return run(message, commandArray);
		} catch (FileNotFoundException e) {
			return -1;
		} finally {
			System.setOut(out);
			System.setErr(err);
			if (fileStream != null)
				fileStream.close();
		}
	}

	/*
	 * Untar the given file in the output directory.
	 */
	private void untar(String message, File file) {
		String name = file.getName();
		File gzFile = new File(output, name);
		output.mkdirs();
		run(message, new String[] {"cp", file.getAbsolutePath(), gzFile.getAbsolutePath()});
		run(message, new String[] {"tar", "-zpxf", gzFile.getAbsolutePath()});
		gzFile.delete();
	}

	/*
	 * Return a file object with a unique name in a temporary location.
	 */
	public static File getUniqueFolder() {
		String tempDir = System.getProperty("java.io.tmpdir");
		return new File(tempDir, getUniqueString());
	}

	/*
	 * Helper method to return the install location. Return null if it is unavailable.
	 */
	public static File getInstallLocation() {
		Location installLocation = (Location) ServiceHelper.getService(TestActivator.getContext(), Location.class.getName(), Location.INSTALL_FILTER);
		if (installLocation == null || !installLocation.isSet())
			return null;
		URL url = installLocation.getURL();
		if (url == null)
			return null;
		return URLUtil.toFile(url);
	}

	private String getValueFor(String property) {
		if (property == null)
			return null;
		String result = TestActivator.getContext().getProperty(property);
		if (result == null && archiveAndRepositoryProperties == null)
			return null;
		if (result == null)
			archiveAndRepositoryProperties.getProperty(property);
		if (result == null)
			result = archiveAndRepositoryProperties.getProperty(property + '.' + Platform.getOS());
		return result;
	}

	/*
	 * Return a file handle pointing to the platform binary zip. Method never returns null because
	 * it will fail an assert before that.
	 */
	private File getPlatformZip() {
		String property = null;
		File file = null;
		if (propertyToPlatformArchive != null) {
			property = getValueFor(propertyToPlatformArchive);
			String message = "Need to set the " + "\"" + propertyToPlatformArchive + "\" system property with a valid path to the platform binary drop or copy the archive to be a sibling of the install folder.";
			if (property == null) {
				fail(message);
			}
			file = new File(property);
			assertNotNull(message, file);
			assertTrue(message, file.exists());
			assertTrue("File is zero length: " + file.getAbsolutePath(), file.length() > 0);
			return file;
		}

		property = getValueFor("org.eclipse.equinox.p2.reconciler.tests.platform.archive");
		if (property == null) {
			// the releng test framework copies the zip so let's look for it...
			// it will be a sibling of the eclipse/ folder that we are running
			File installLocation = getInstallLocation();
			if (installLocation != null) {
				// parent will be "eclipse" and the parent's parent will be "eclipse-testing"
				File parent = installLocation.getParentFile();
				if (parent != null) {
					parent = parent.getParentFile();
					if (parent != null) {
						File[] children = parent.listFiles(new FileFilter() {
							public boolean accept(File pathname) {
								String name = pathname.getName();
								return name.startsWith("eclipse-platform-");
							}
						});
						if (children != null && children.length == 1)
							file = children[0];
					}
				}
			}
		} else {
			file = new File(property);
		}
		String message = "Need to set the \"org.eclipse.equinox.p2.reconciler.tests.platform.archive\" system property with a valid path to the platform binary drop or copy the archive to be a sibling of the install folder.";
		assertNotNull(message, file);
		assertTrue(message, file.exists());
		assertTrue("File is zero length: " + file.getAbsolutePath(), file.length() > 0);
		return file;
	}

	/*
	 * Add the given bundle to the given folder (do a copy).
	 * The folder can be one of dropins, plugins or features.
	 * If the file handle points to a directory, then do a deep copy.
	 */
	public void add(String message, String target, File file) {
		if (!(target.startsWith("dropins") || target.startsWith("plugins") || target.startsWith("features")))
			fail("Destination folder for resource copying should be either dropins, plugins or features.");
		File destinationParent = new File(output, "eclipse/" + target);
		destinationParent.mkdirs();
		copy(message, file, new File(destinationParent, file.getName()));
	}

	/*
	 * Create a link file in the links folder. Point it to the given extension location.
	 */
	public void createLinkFile(String message, String filename, String extensionLocation) {
		File file = new File(output, "eclipse/links/" + filename + ".link");
		file.getParentFile().mkdirs();
		Properties properties = new Properties();
		properties.put("path", extensionLocation);
		OutputStream stream = null;
		try {
			stream = new BufferedOutputStream(new FileOutputStream(file));
			properties.store(stream, null);
		} catch (IOException e) {
			fail(message, e);
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}

	/*
	 * Delete the link file with the given name from the links folder.
	 */
	public void removeLinkFile(String message, String filename) {
		File file = new File(output, "eclipse/links/" + filename + ".link");
		file.delete();
	}

	public void add(String message, String target, File[] files) {
		assertNotNull(files);
		for (int i = 0; i < files.length; i++)
			add(message, target, files[i]);
	}

	/*
	 * Remove the given filename from the given folder.
	 */
	public boolean remove(String message, String target, String filename) {
		if (!(target.startsWith("dropins") || target.startsWith("plugins") || target.startsWith("features")))
			fail("Target folder for resource deletion should be either dropins, plugins or features.");
		File folder = new File(output, "eclipse/" + target);
		File targetFile = new File(folder, filename);
		if (!targetFile.exists())
			return false;
		return delete(targetFile);
	}

	/*
	 * Remove the files with the given names from the target folder.
	 */
	public void remove(String message, String target, String[] names) {
		assertNotNull(names);
		for (int i = 0; i < names.length; i++)
			remove(message, target, names[i]);
	}

	/*
	 * Return a boolean value indicating whether or not a bundle with the given id
	 * is listed in the bundles.info file. Ignore the version number and return true
	 * if there are any matches in the file.
	 */
	public boolean isInBundlesInfo(String bundleId) throws IOException {
		return isInBundlesInfo(bundleId, null);
	}

	protected File getBundlesInfo() {
		return new File(output, "eclipse/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info");
	}

	public boolean isInBundlesInfo(String bundleId, String version) throws IOException {
		return isInBundlesInfo(bundleId, version, null);
	}

	public boolean isInBundlesInfo(String bundleId, String version, String location) throws IOException {
		return isInBundlesInfo(getBundlesInfo(), bundleId, version, location);
	}

	public boolean isInBundlesInfo(File bundlesInfo, String bundleId, String version) throws IOException {
		return isInBundlesInfo(bundlesInfo, bundleId, version, null);
	}

	public BundleInfo[] loadBundlesInfo(File location) throws IOException {
		SimpleConfiguratorManipulator manipulator = new SimpleConfiguratorManipulatorImpl();
		InputStream input = null;
		try {
			input = new BufferedInputStream(new FileInputStream(location));
			return manipulator.loadConfiguration(input, new File(output, "eclipse").toURI());
		} finally {
			try {
				if (input != null)
					input.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}

	public void saveBundlesInfo(BundleInfo[] bundles, File location) throws IOException {
		SimpleConfiguratorManipulator manipulator = new SimpleConfiguratorManipulatorImpl();
		manipulator.saveConfiguration(bundles, location, null);
	}

	/*
	 * Return a boolean value indicating whether or not a bundle with the given id
	 * is listed in the bundles.info file. If the version is non-null, check to ensure the
	 * version is the expected one. If the location is non-null then do a String#contains check.
	 */
	public boolean isInBundlesInfo(File bundlesInfo, String bundleId, String version, String location) throws IOException {
		BundleInfo[] infos = loadBundlesInfo(bundlesInfo);
		for (int i = 0; infos != null && i < infos.length; i++) {
			BundleInfo info = infos[i];
			if (!bundleId.equals(info.getSymbolicName()))
				continue;
			if (version != null && !version.equals(info.getVersion()))
				continue;
			if (location == null)
				return true;
			return info.getLocation().toString().contains(location);
		}
		return false;
	}

	/*
	 * Run the reconciler to discover changes in the drop-ins folder and update the system state.
	 */
	public void reconcile(String message) {
		runEclipse(message, new String[] {"-application", "org.eclipse.equinox.p2.reconciler.application"});
	}

	/*
	 * If a bundle with the given id and version exists in the bundles.info file then
	 * throw an AssertionFailedException.
	 */
	public void assertDoesNotExistInBundlesInfo(String message, String bundleId, String version) {
		try {
			assertTrue(message, !isInBundlesInfo(bundleId, version));
		} catch (IOException e) {
			fail(message, e);
		}
	}

	/*
	 * If a bundle with the given id in the bundles.info file then throw an AssertionFailedException.
	 */
	public void assertDoesNotExistInBundlesInfo(String message, String bundleId) {
		assertDoesNotExistInBundlesInfo(message, bundleId, null);
	}

	/*
	 * If a bundle with the given id and version does not exist in the bundles.info file then
	 * throw an AssertionFailedException.
	 */
	public void assertExistsInBundlesInfo(String message, String bundleId, String version) {
		assertExistsInBundlesInfo(message, bundleId, version, null);
	}

	public void assertExistsInBundlesInfo(String message, String bundleId, String version, String location) {
		try {
			assertTrue(message, isInBundlesInfo(bundleId, version, location));
		} catch (IOException e) {
			fail(message, e);
		}
	}

	/*
	 * If a bundle with the given id does not exist in the bundles.info file then throw an AssertionFailedException.
	 */
	public void assertExistsInBundlesInfo(String message, String bundleId) {
		assertExistsInBundlesInfo(message, bundleId, null);
	}

	/*
	 * Clean up the temporary data used to run the tests.
	 * This method is not intended to be called by clients, it will be called
	 * automatically when the clients use a ReconcilerTestSuite.
	 */
	public void cleanup() throws Exception {
		// rm -rf eclipse sub-dir
		for (Iterator iter = toRemove.iterator(); iter.hasNext();) {
			File next = (File) iter.next();
			delete(next);
		}
		output = null;
		toRemove.clear();
	}

	/*
	 * Read and return the configuration object. Will not return null.
	 */
	public Configuration getConfiguration() {
		File configLocation = new File(output, "eclipse/configuration/org.eclipse.update/platform.xml");
		File installLocation = new File(output, "eclipse");
		return loadConfiguration(configLocation, installLocation);
	}

	public Configuration loadConfiguration(File configLocation, File installLocation) {
		try {
			return Configuration.load(configLocation, installLocation.toURL());
		} catch (ProvisionException e) {
			fail("Error while reading configuration from " + configLocation);
		} catch (MalformedURLException e) {
			fail("Unable to convert install location to URL " + installLocation);
		}
		assertTrue("Unable to read configuration from " + configLocation, false);
		// avoid compiler error
		return null;
	}

	/*
	 * Save the given configuration to disk.
	 */
	public void save(String message, Configuration configuration) {
		File configLocation = new File(output, "eclipse/configuration/org.eclipse.update/platform.xml");
		File installLocation = new File(output, "eclipse");
		try {
			configuration.save(configLocation, installLocation.toURL());
		} catch (ProvisionException e) {
			fail(message, e);
		} catch (MalformedURLException e) {
			fail(message, e);
		}
	}

	/*
	 * Iterate over the sites in the given configuration and remove the one which
	 * has a url matching the given location.
	 */
	public boolean removeSite(Configuration configuration, String location) {
		IPath path = new Path(location);
		List sites = configuration.getSites();
		for (Iterator iter = sites.iterator(); iter.hasNext();) {
			Site tempSite = (Site) iter.next();
			String siteURL = tempSite.getUrl();
			if (path.equals(new Path(siteURL)))
				return configuration.removeSite(tempSite);
		}
		return false;
	}

	/*
	 * Create and return a new feature object with the given parameters.
	 */
	public Feature createFeature(Site site, String id, String version, String url) {
		Feature result = new Feature(site);
		result.setId(id);
		result.setVersion(version);
		result.setUrl(url);
		return result;
	}

	/*
	 * Create and return a new site object with the given parameters.
	 */
	public Site createSite(String policy, boolean enabled, boolean updateable, String uri, String[] plugins) {
		Site result = new Site();
		result.setPolicy(policy);
		result.setEnabled(enabled);
		result.setUpdateable(updateable);
		result.setUrl(uri);
		if (plugins != null)
			for (int i = 0; i < plugins.length; i++)
				result.addPlugin(plugins[i]);
		return result;
	}

	/*
	 * Copy the bundle with the given id to the specified location. (location
	 * is parent directory)
	 */
	public void copyBundle(String bundlename, File source, File destination) throws IOException {
		if (destination == null)
			destination = output;
		destination = new File(destination, "eclipse/plugins");
		if (source == null) {
			Bundle bundle = TestActivator.getBundle(bundlename);
			if (bundle == null) {
				throw new IOException("Could not find: " + bundlename);
			}
			String location = bundle.getLocation();
			if (location.startsWith("reference:"))
				location = location.substring("reference:".length());
			source = new File(FileLocator.toFileURL(new URL(location)).getFile());
		}
		destination = new File(destination, source.getName());
		if (destination.exists())
			return;
		FileUtils.copy(source, destination, new File(""), false);
	}

	/*
	 * Assert that a feature with the given id exists in the configuration. If 
	 * a version is specified then match the version, otherwise any version will
	 * do.
	 */
	public void assertFeatureExists(String message, Configuration configuration, String id, String version) {
		List sites = configuration.getSites();
		assertNotNull(message, sites);
		boolean found = false;
		for (Iterator iter = sites.iterator(); iter.hasNext();) {
			Site site = (Site) iter.next();
			Feature[] features = site.getFeatures();
			for (int i = 0; features != null && i < features.length; i++) {
				if (id.equals(features[i].getId())) {
					if (version == null)
						found = true;
					else if (version.equals(features[i].getVersion()))
						found = true;
				}
			}
		}
		assertTrue(message, found);
	}

	/*
	 * Return a boolean value indicating whether or not the IU with the given ID and version
	 * is installed. We do this by loading the profile registry and seeing if it is there.
	 */
	public boolean isInstalled(String id, String version) {
		File location = new File(output, "eclipse/p2/org.eclipse.equinox.p2.engine/profileRegistry");
		SimpleProfileRegistry registry = new SimpleProfileRegistry(getAgent(), location, new SurrogateProfileHandler(getAgent()), false);
		IProfile[] profiles = registry.getProfiles();
		assertEquals("1.0 Should only be one profile in registry.", 1, profiles.length);
		IQueryResult queryResult = profiles[0].query(QueryUtil.createIUQuery(id, Version.create(version)), null);
		return !queryResult.isEmpty();
	}

	public IInstallableUnit getRemoteIU(String id, String version) {
		File location = new File(output, "eclipse/p2/org.eclipse.equinox.p2.engine/profileRegistry");
		SimpleProfileRegistry registry = new SimpleProfileRegistry(getAgent(), location, new SurrogateProfileHandler(getAgent()), false);
		IProfile[] profiles = registry.getProfiles();
		assertEquals("1.0 Should only be one profile in registry.", 1, profiles.length);
		IQueryResult queryResult = profiles[0].query(QueryUtil.createIUQuery(id, Version.create(version)), null);
		assertEquals("1.1 Should not have more than one IU wth the same ID and version.", 1, queryResultSize(queryResult));
		return (IInstallableUnit) queryResult.iterator().next();
	}

	protected int runEclipse(String message, String[] args) {
		File root = new File(Activator.getBundleContext().getProperty("java.home"));
		root = new File(root, "bin");
		File exe = new File(root, "javaw.exe");
		if (!exe.exists())
			exe = new File(root, "java");
		assertTrue("Java executable not found in: " + exe.getAbsolutePath(), exe.exists());
		List<String> command = new ArrayList<String>();
		Collections.addAll(command, new String[] {(new File(output, "eclipse/eclipse")).getAbsolutePath(), "--launcher.suppressErrors", "-nosplash", "-vm", exe.getAbsolutePath()});
		Collections.addAll(command, args);
		Collections.addAll(command, new String[] {"-vmArgs", "-Dosgi.checkConfiguration=true"});
		// command-line if you want to run and allow a remote debugger to connect
		//Collections.addAll(command, new String[] {"-Xdebug", "-Xnoagent", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8787"});
		return run(message, command.toArray(new String[command.size()]));
	}

	public int runInitialize(String message) {
		return runEclipse(message, new String[] {"-initialize"});
	}

	public int runDirectorToUpdate(String message, String sourceRepo, String iuToInstall, String iuToUninstall) {
		return runEclipse(message, new String[] {"-application", "org.eclipse.equinox.p2.director", "-repository", sourceRepo, "-installIU", iuToInstall, "-uninstallIU", iuToUninstall});
	}

	public int runDirectorToRevert(String message, String sourceRepo, String timestampToRevertTo) {
		return runEclipse(message, new String[] {"-application", "org.eclipse.equinox.p2.director", "-repository", sourceRepo, "-revert", timestampToRevertTo});
	}

	public int runDirectorToInstall(String message, File installFolder, String sourceRepo, String iuToInstall) {
		String[] command = new String[] {//
		"-application", "org.eclipse.equinox.p2.director", //
				"-repository", sourceRepo, "-installIU", iuToInstall, //
				"-destination", installFolder.getAbsolutePath(), //
				"-bundlepool", installFolder.getAbsolutePath(), //
				"-roaming", "-profile", "PlatformProfile", "-profileProperties", "org.eclipse.update.install.features=true", // 
				"-p2.os", Platform.getOS(), "-p2.ws", Platform.getWS(), "-p2.arch", Platform.getOSArch()};
		return runEclipse(message, command);
	}

	public int runVerifierBundle(File destination) {
		if (destination == null)
			destination = output;
		String message = "Running the verifier bundle at: " + destination;
		return runEclipse(message, new String[] {"-application", "org.eclipse.equinox.p2.tests.verifier.application", "-consoleLog"});
	}

	public int installAndRunVerifierBundle(File destination) {
		if (destination == null)
			destination = output;
		try {
			copyBundle(VERIFIER_BUNDLE_ID, null, destination);
		} catch (IOException e) {
			fail("Could not find the verifier bundle");
		}
		int returnCode = runVerifierBundle(destination);
		deleteVerifierBundle(destination);
		return returnCode;
	}

	public int installAndRunVerifierBundle35(File destination) {
		if (destination == null)
			destination = output;
		try {
			copyBundle(VERIFIER_BUNDLE_ID, getTestData(VERIFIER_BUNDLE_ID + "3.5", "testData/VerifierBundle35/org.eclipse.equinox.p2.tests.verifier_1.0.0.jar"), destination);
		} catch (IOException e) {
			fail("Could not find the verifier bundle");
		}
		int returnCode = runVerifierBundle(destination);
		deleteVerifierBundle(destination);
		return returnCode;
	}

	private void deleteVerifierBundle(File destination) {
		if (destination == null)
			destination = output;
		destination = new File(destination, "eclipse/plugins");
		File[] verifierBundle = destination.listFiles(new FilenameFilter() {

			public boolean accept(File dir, String name) {
				if (name.startsWith(VERIFIER_BUNDLE_ID))
					return true;
				return false;
			}
		});
		if (verifierBundle != null && verifierBundle.length > 0)
			verifierBundle[0].delete();
	}

	private static void loadPlatformZipPropertiesFromFile() {
		File installLocation = getInstallLocation();
		if (installLocation != null) {
			// parent will be "eclipse" and the parent's parent will be "eclipse-testing"
			File parent = installLocation.getParentFile();
			if (parent != null) {
				parent = parent.getParentFile();
				if (parent != null) {
					File propertiesFile = new File(parent, "equinoxp2tests.properties");
					if (!propertiesFile.exists())
						return;
					archiveAndRepositoryProperties = new Properties();
					try {
						InputStream is = null;
						try {
							is = new BufferedInputStream(new FileInputStream(propertiesFile));
							archiveAndRepositoryProperties.load(is);
						} finally {
							is.close();
						}
					} catch (IOException e) {
						return;
					}
				}
			}
		}
	}
}