/******************************************************************************* 
* Copyright (c) 2009 EclipseSource and others. All rights reserved. This
* program and the accompanying materials are made available under the terms of
* the Eclipse Public License v1.0 which accompanies this distribution, and is
* available at http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*   EclipseSource - initial API and implementation
******************************************************************************/
package org.eclipse.equinox.p2.tests.publisher.actions;

import java.util.List;
import java.util.Map;
import junit.framework.TestCase;
import org.eclipse.equinox.frameworkadmin.BundleInfo;
import org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile;
import org.eclipse.equinox.p2.metadata.IVersionedId;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.tests.TestData;

/**
 * Tests the product file parser found in the publisher.
 */
public class ProductFileTest extends TestCase {

	String productFileLocation = null;
	ProductFile productFile = null;

	String configFile = "/org.eclipse.equinox.p2.tests/testData/ProductActionTest/productWithConfig/config.ini";
	private String uidProductFileLocation;
	private ProductFile uidProductFile;

	protected void setUp() throws Exception {
		productFileLocation = TestData.getFile("ProductActionTest/productWithConfig", "sample.product").toString();
		productFile = new ProductFile(productFileLocation);
		uidProductFileLocation = TestData.getFile("ProductActionTest/productWithConfig", "uidproduct.product").toString();
		uidProductFile = new ProductFile(uidProductFileLocation);
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getLauncherName()}.
	 */
	public void testGetLauncherName() {
		assertEquals("1.0", "sample", productFile.getLauncherName());
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getLocation()}.
	 */
	public void testGetLocation() {
		assertEquals("1.0", productFileLocation, productFile.getLocation().toString());
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getProperties()}.
	 */
	public void testGetConfigurationProperties() {
		Map<String, String> properties = productFile.getConfigurationProperties();
		assertEquals("1.0", 4, properties.size());
		assertEquals("1.1", "bar", properties.get("foo"));
		assertEquals("1.2", "", properties.get("foo1"));
		assertEquals("1.3", "test.product", properties.get("eclipse.product"));
		assertEquals("1.4", "test.app", properties.get("eclipse.application"));
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getBundles(boolean)}.
	 */
	public void testGetBundles() {
		List bundles = productFile.getBundles(false);
		assertEquals("1.0", 1, bundles.size());
		assertEquals("1.1", "org.eclipse.core.runtime", ((IVersionedId) bundles.get(0)).getId());
		assertEquals("1.2", Version.createOSGi(1, 0, 4), ((IVersionedId) bundles.get(0)).getVersion());
		bundles = productFile.getBundles(true);
		assertEquals("1.3", 2, bundles.size());
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getBundleInfos()}.
	 */
	public void testGetBundleInfos() {
		List bundleInfos = productFile.getBundleInfos();
		BundleInfo info = (BundleInfo) bundleInfos.iterator().next();
		assertEquals("1.0", 1, bundleInfos.size());
		assertEquals("1.1", "org.eclipse.core.runtime", info.getSymbolicName());
		assertEquals("1.2", 2, info.getStartLevel());
		assertEquals("1.3", true, info.isMarkedAsStarted());
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getFragments()}.
	 */
	public void testGetFragments() {
		List fragments = productFile.getFragments();
		assertEquals("1.0", 1, fragments.size());
		assertEquals("1.1", "org.eclipse.swt.win32.win32.x86", ((IVersionedId) fragments.get(0)).getId());
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getFeatures()}.
	 */
	public void testGetFeatures() {
		List features = productFile.getFeatures();
		assertEquals("1.0", 1, features.size());
		assertEquals("1.1", "org.eclipse.rcp", ((IVersionedId) features.get(0)).getId());
		assertEquals("1.2", Version.create("3.5.0.v20081110-9C9tEvNEla71LZ2jFz-RFB-t"), ((IVersionedId) features.get(0)).getVersion());
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getIcons(java.lang.String)}.
	 */
	//	public void testGetIcons() {
	//		String[] icons = productFile.getIcons("win32");
	//		String absolutePath = new File(productFile.getLocation().getParentFile(), "test/icon.bmp").getAbsolutePath();
	//		assertEquals("1.0", 1, icons.length);
	//		assertEquals("1.1", absolutePath, icons[0]);
	//	}
	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getConfigIniPath()}.
	 */
	public void testGetConfigIniPath() {
		String configIni = productFile.getConfigIniPath("win32");
		assertEquals("1.0", "config.ini", configIni);
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getId()}.
	 */
	public void testGetId() {
		String id = productFile.getId();
		assertEquals("1.0", "test.product", id);
	}

	public void testGetUID() {
		String id = uidProductFile.getId();
		assertEquals("1.0", "UID.test.product", id);
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getSplashLocation()}.
	 */
	public void testGetSplashLocation() {
		String splashLocation = productFile.getSplashLocation();
		assertEquals("1.0", "org.eclipse.equinox.p2.tests", splashLocation);
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getProductName()}.
	 */
	public void testGetProductName() {
		String productName = productFile.getProductName();
		assertEquals("1.0", "aaTestProduct", productName);
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getApplication()}.
	 */
	public void testGetApplication() {
		String application = productFile.getApplication();
		assertEquals("1.0", "test.app", application);
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#useFeatures()}.
	 */
	public void testUseFeatures() {
		boolean useFeatures = productFile.useFeatures();
		assertTrue("1.0", !useFeatures);
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getVersion()}.
	 */
	public void testGetVersion() {
		String version = productFile.getVersion();
		assertEquals("1.0", Version.create("1"), Version.create(version));
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getVMArguments(java.lang.String)}.
	 */
	public void testGetVMArguments() {
		String vmArguments = productFile.getVMArguments("");
		assertEquals("1.0", "vmArg -Dfoo=\"b a r\"", vmArguments);
		vmArguments = productFile.getVMArguments(null);
		assertEquals("1.1", "vmArg -Dfoo=\"b a r\"", vmArguments);
	}

	/**
	 * Test method for {@link org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile#getProgramArguments(java.lang.String)}.
	 */
	public void testGetProgramArguments() {
		String programArguments = productFile.getProgramArguments("");
		assertEquals("1.0", "programArg -name \"My Name\"", programArguments);
		programArguments = productFile.getProgramArguments(null);
		assertEquals("1.1", "programArg -name \"My Name\"", programArguments);
	}

	public void testGetLicenseURL() throws Exception {
		String productWithLicense = TestData.getFile("ProductActionTest", "productWithLicense.product").toString();
		ProductFile product = new ProductFile(productWithLicense);
		assertEquals("1.0", "http://www.example.com", product.getLicenseURL());
	}

	public void testGetLicenseText() throws Exception {
		String productWithLicense = TestData.getFile("ProductActionTest", "productWithLicense.product").toString();
		ProductFile product = new ProductFile(productWithLicense);
		assertEquals("1.0", "This is the liCenSE.", product.getLicenseText().trim());
	}

}
