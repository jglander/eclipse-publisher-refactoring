package org.eclipse.equinox.p2.tests.planner;

import org.eclipse.equinox.internal.p2.director.ProfileChangeRequest;

import java.util.ArrayList;
import java.util.Properties;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.engine.*;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.planner.IPlanner;
import org.eclipse.equinox.p2.planner.ProfileInclusionRules;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class Bug278668 extends AbstractProvisioningTest {
	private IProfile profile;
	private IMetadataRepository repo;

	protected void setUp() throws Exception {
		super.setUp();
		profile = createProfile("test278668");
		IEngine engine = createEngine();
		IPlanner planner = createPlanner();
		ArrayList target = new ArrayList();
		target.add(createEclipseIU("com.borland.tg.modelrenaming"));
		target.add(createEclipseIU("com.borland.tg.xmldesign"));
		target.add(createEclipseIU("com.tssap.selena.dom"));
		target.add(createEclipseIU("com.tssap.selena.model.providers.rc"));
		target.add(createEclipseIU("com.tssap.selena.model.providers.resources"));
		target.add(createEclipseIUSingleton("com.tssap.selena.model", DEFAULT_VERSION));
		target.add(createEclipseIU("com.borland.tg.modelrenaming", Version.create("8.1.2.v20090422-1800")));
		target.add(createEclipseIU("com.borland.tg.xmldesign", Version.create("8.2.0.v20090422-1800")));
		target.add(createEclipseIU("com.tssap.selena.dom", Version.create("8.2.0.v20090422-1800")));
		target.add(createEclipseIU("com.tssap.selena.model.providers.rc", Version.create("8.2.0.v20090422-1800")));
		target.add(createEclipseIU("com.tssap.selena.model.providers.resources", Version.create("8.1.2.v20090422-1800")));
		target.add(createEclipseIU("com.tssap.selena.model", Version.create("8.1.5.v20090422-1800")));
		target.add(createEclipseIUSingleton("com.tssap.selena.dom", Version.create("8.2.0.v20090326-1800")));
		createTestMetdataRepository((IInstallableUnit[]) target.toArray(new IInstallableUnit[target.size()]));

		ArrayList requirements = new ArrayList();
		requirements.add(MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "com.borland.tg.modelrenaming", new VersionRange("[1.0.0, 1.0.0]"), null, false, false));
		requirements.add(MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "com.borland.tg.xmldesign", new VersionRange("[1.0.0, 1.0.0]"), null, false, false));
		requirements.add(MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "com.tssap.selena.dom", new VersionRange("[1.0.0, 1.0.0]"), null, false, false));
		requirements.add(MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "com.tssap.selena.model.providers.rc", new VersionRange("[1.0.0, 1.0.0]"), null, false, false));
		requirements.add(MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "com.tssap.selena.model.providers.resources", new VersionRange("[1.0.0, 1.0.0]"), null, false, false));
		requirements.add(MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, "com.tssap.selena.model", new VersionRange("[1.0.0, 1.0.0]"), null, false, false));

		IInstallableUnit group = createIU("com.borland.tg.modeling.feature.group", Version.create("8.2.0.v20081113-0500-_87S7nELRXmpf6G0dO3emm"), null, (IRequirement[]) requirements.toArray(new IRequirement[requirements.size()]), new IProvidedCapability[] {MetadataFactory.createProvidedCapability(IInstallableUnit.NAMESPACE_IU_ID, "com.borland.tg.modeling", Version.create("8.2.0.v20081113-0500-_87S7nELRXmpf6G0dO3emm"))}, new Properties(), null, null, true);

		ProfileChangeRequest req = new ProfileChangeRequest(profile);
		req.addInstallableUnits(new IInstallableUnit[] {group});
		IProvisioningPlan plan = planner.getProvisioningPlan(req, null, null);
		assertOK("plan should be OK", plan.getStatus());

		engine.perform(plan, null);

		repo = getMetadataRepositoryManager().loadRepository(getTestData("test data bug 278668", "testData/bug278668").toURI(), null);
	}

	protected void tearDown() throws Exception {
		getMetadataRepositoryManager().removeRepository(getTestData("test data bug 278668", "testData/bug278668").toURI());
		super.tearDown();
	}

	public void testInstallFeaturePatch() {
		IQueryResult c = repo.query(QueryUtil.createIUQuery("com.borland.tg.modeling.8.2.0.hotfixexp.patch.feature.group"), new NullProgressMonitor());
		assertEquals(1, queryResultSize(c));
		IQueryResult c2 = repo.query(QueryUtil.createIUQuery("com.borland.tg.modeling.8.2.0.nl.patch.feature.group"), new NullProgressMonitor());
		assertEquals(1, queryResultSize(c2));

		ProfileChangeRequest request = new ProfileChangeRequest(profile);
		request.addInstallableUnits(new IInstallableUnit[] {(IInstallableUnit) c.iterator().next()});
		IPlanner planner = createPlanner();
		IProvisioningPlan plan = planner.getProvisioningPlan(request, null, new NullProgressMonitor());
		assertOK("Plan OK", plan.getStatus());

		ProfileChangeRequest request2 = new ProfileChangeRequest(profile);
		request2.addInstallableUnits(new IInstallableUnit[] {(IInstallableUnit) c2.iterator().next()});
		IPlanner planner2 = createPlanner();
		IProvisioningPlan plan2 = planner2.getProvisioningPlan(request2, null, new NullProgressMonitor());
		assertOK("Plan OK", plan2.getStatus());

		ProfileChangeRequest request3 = new ProfileChangeRequest(profile);
		request3.addInstallableUnits(new IInstallableUnit[] {(IInstallableUnit) c.iterator().next(), (IInstallableUnit) c2.iterator().next()});
		IPlanner planner3 = createPlanner();
		IProvisioningPlan plan3 = planner3.getProvisioningPlan(request3, null, new NullProgressMonitor());
		assertNotOK("Plan Not OK", plan3.getStatus());

		ProfileChangeRequest request4 = new ProfileChangeRequest(profile);
		request4.addInstallableUnits(new IInstallableUnit[] {(IInstallableUnit) c.iterator().next(), (IInstallableUnit) c2.iterator().next()});
		request4.setInstallableUnitInclusionRules((IInstallableUnit) c.iterator().next(), ProfileInclusionRules.createOptionalInclusionRule((IInstallableUnit) c.iterator().next()));
		request4.setInstallableUnitInclusionRules((IInstallableUnit) c2.iterator().next(), ProfileInclusionRules.createOptionalInclusionRule((IInstallableUnit) c2.iterator().next()));
		IPlanner planner4 = createPlanner();
		IProvisioningPlan plan4 = planner4.getProvisioningPlan(request4, null, new NullProgressMonitor());
		assertOK("Plan OK", plan4.getStatus());
	}
}
