/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.p2.tests.planner;

import org.eclipse.equinox.internal.p2.director.ProfileChangeRequest;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.engine.IProvisioningPlan;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.planner.IPlanner;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class MissingOptional extends AbstractProvisioningTest {
	private IInstallableUnit a1;
	private IInstallableUnit b1;
	private IInstallableUnit d;
	private IProfile profile;
	private IPlanner planner;

	protected void setUp() throws Exception {
		super.setUp();
		a1 = createIU("A", Version.create("1.0.0"), true);
		b1 = createIU("B", Version.create("1.0.0"), true);

		IRequirement[] req = new IRequirement[3];
		req[0] = MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "A", VersionRange.emptyRange, null, false, false, true);
		req[1] = MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "B", VersionRange.emptyRange, null, true, false, true);
		req[2] = MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "C", VersionRange.emptyRange, null, true, false, true);
		d = createIU("D", req);

		createTestMetdataRepository(new IInstallableUnit[] {a1, b1, d});

		profile = createProfile("TestProfile." + getName());
		planner = createPlanner();
	}

	public void testInstallation() {
		//Ensure that D's installation does not fail because of C's absence
		ProfileChangeRequest req = new ProfileChangeRequest(profile);
		req.addInstallableUnits(new IInstallableUnit[] {d});
		IProvisioningPlan plan = planner.getProvisioningPlan(req, null, null);
		assertEquals(IStatus.OK, plan.getStatus().getSeverity());
		assertInstallOperand(plan, a1);
		assertInstallOperand(plan, b1); //THIS May not be in
		assertInstallOperand(plan, d);
	}
}
