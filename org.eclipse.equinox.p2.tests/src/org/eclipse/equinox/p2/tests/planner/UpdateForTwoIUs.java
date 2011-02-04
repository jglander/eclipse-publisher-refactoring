package org.eclipse.equinox.p2.tests.planner;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import org.eclipse.equinox.internal.p2.director.QueryableArray;
import org.eclipse.equinox.internal.p2.metadata.query.UpdateQuery;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.p2.metadata.expression.*;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class UpdateForTwoIUs extends AbstractProvisioningTest {

	private IInstallableUnit iua;
	private Collection<IMatchExpression<IInstallableUnit>> x;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		InstallableUnitDescription iud = new MetadataFactory.InstallableUnitDescription();
		iud.setId("A");
		iud.setVersion(Version.create("1.0.0"));

		String orExpression = "providedCapabilities.exists(pc | pc.namespace == 'org.eclipse.equinox.p2.iu' && (pc.name == 'B' || pc.name == 'C'))";
		IExpression expr = ExpressionUtil.parse(orExpression);
		IMatchExpression matchExpression = ExpressionUtil.getFactory().matchExpression(expr);

		Collection<IMatchExpression<IInstallableUnit>> updateExpression = new ArrayList<IMatchExpression<IInstallableUnit>>();
		updateExpression.add(matchExpression);
		iud.setUpdateDescriptor(MetadataFactory.createUpdateDescriptor(updateExpression, IUpdateDescriptor.HIGH, (String) null, (URI) null));
		iua = MetadataFactory.createInstallableUnit(iud);

		Collection<IInstallableUnit> ius = new ArrayList<IInstallableUnit>();
		ius.add(iua);
		URI repoURI = getTempFolder().toURI();
		createMetadataRepository(repoURI, null).addInstallableUnits(ius);
		getMetadataRepositoryManager().removeRepository(repoURI);

		x = getMetadataRepositoryManager().loadRepository(repoURI, null).query(QueryUtil.ALL_UNITS, null).iterator().next().getUpdateDescriptor().getIUsBeingUpdated();
		assertEquals(matchExpression, x.iterator().next());
	}

	public void testUpdateQueryForTwoIUs() {
		//This test that A can be an update of B or C. In other words looking for an update of B or an update of C should return A.

		IQueryResult<IInstallableUnit> updates = new QueryableArray(new IInstallableUnit[] {iua}).query(new UpdateQuery(createIU("B")), null);
		assertFalse(updates.isEmpty());
		assertEquals(iua, updates.iterator().next());

		IQueryResult<IInstallableUnit> updates2 = new QueryableArray(new IInstallableUnit[] {iua}).query(new UpdateQuery(createIU("C")), null);
		assertFalse(updates2.isEmpty());
		assertEquals(iua, updates2.iterator().next());

		IQueryResult<IInstallableUnit> update3 = new QueryableArray(new IInstallableUnit[] {iua}).query(new UpdateQuery(createIU("X")), null);
		assertTrue(update3.isEmpty());
	}

}
