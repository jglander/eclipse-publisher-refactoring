/*******************************************************************************
 *  Copyright (c) 2008, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.updatesite;

import junit.framework.*;

/**
 * Performs all automated director tests.
 */
public class AllTests extends TestCase {

	public static Test suite() {
		TestSuite suite = new TestSuite(AllTests.class.getName());
		suite.addTestSuite(CategoryXMLActionTest.class);
		suite.addTestSuite(CategoryIUXMLActionTest.class);
		suite.addTestSuite(DoesNotCacheStaleData.class);
		suite.addTestSuite(SiteXMLActionTest.class);
		suite.addTestSuite(UpdateSiteTest.class);
		suite.addTestSuite(LocalUpdatesiteTest.class);
		return suite;
	}

}
