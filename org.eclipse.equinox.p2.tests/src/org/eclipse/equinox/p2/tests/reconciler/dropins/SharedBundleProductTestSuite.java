/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.reconciler.dropins;

import junit.framework.Test;

public class SharedBundleProductTestSuite extends ReconcilerTestSuite {

	public SharedBundleProductTestSuite() {
		super();
	}

	public SharedBundleProductTestSuite(String propertyToPlatformArchive) {
		super(propertyToPlatformArchive);
	}

	public Test getInitializationTest() {
		return new AbstractSharedBundleProductTest("initialize", getPlatformArchive());
	}

	public Test getCleanUpTest() {
		return new AbstractSharedBundleProductTest("cleanup");
	}

}
