/*******************************************************************************
 * Copyright (c) 2008, 2009 Code 9 and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *   Code 9 - initial API and implementation
 *   IBM - ongoing development
 ******************************************************************************/
package org.eclipse.equinox.p2.publisher;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.artifact.repository.simple.SimpleArtifactDescriptor;
import org.eclipse.equinox.internal.p2.core.helpers.*;
import org.eclipse.equinox.internal.p2.core.helpers.FileUtils.IPathComputer;
import org.eclipse.equinox.internal.p2.metadata.IRequiredCapability;
import org.eclipse.equinox.internal.p2.metadata.InstallableUnit;
import org.eclipse.equinox.internal.p2.publisher.Activator;
import org.eclipse.equinox.internal.p2.publisher.QuotedTokenizer;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.p2.metadata.expression.IMatchExpression;
import org.eclipse.equinox.p2.publisher.actions.*;
import org.eclipse.equinox.p2.query.*;
import org.eclipse.equinox.p2.repository.artifact.*;
import org.eclipse.equinox.p2.repository.artifact.spi.ArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.spi.ProcessingStepDescriptor;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;

public abstract class AbstractPublisherAction implements IPublisherAction {
	public static final String CONFIG_ANY = "ANY"; //$NON-NLS-1$
	public static final String CONFIG_SEGMENT_SEPARATOR = "."; //$NON-NLS-1$

	protected IPublisherInfo info;

	/**
	 * Convert a list of tokens into an array. The list separator has to be
	 * specified.
	 */
	public static String[] getArrayFromString(String list, String separator) {
		if (list == null || list.trim().equals("")) //$NON-NLS-1$
			return new String[0];
		List<String> result = new ArrayList<String>();
		for (QuotedTokenizer tokens = new QuotedTokenizer(list, separator); tokens.hasMoreTokens();) {
			String token = tokens.nextToken().trim();
			if (!token.equals("")) //$NON-NLS-1$
				result.add(token);
		}
		return result.toArray(new String[result.size()]);
	}

	/**
	 * Returns a string array of { ws, os, arch } as parsed from the given string
	 * @param configSpec the string to parse
	 * @return the ws, os, arch form of the given string
	 */
	public static String[] parseConfigSpec(String configSpec) {
		String[] result = getArrayFromString(configSpec, CONFIG_SEGMENT_SEPARATOR);
		for (int i = 0; i < result.length; i++)
			if (result[i].equals("*")) //$NON-NLS-1$
				result[i] = CONFIG_ANY;

		if (result.length < 3) {
			String[] temp = new String[3];
			System.arraycopy(result, 0, temp, 0, result.length);
			for (int i = result.length; i < temp.length; i++) {
				temp[i] = CONFIG_ANY;
			}
			result = temp;
		}
		return result;
	}

	/**
	 * Returns the canonical form of config spec with the given ws, os and arch.
	 * Note that the result is intended to be machine readable (i.e., parseConfigSpec
	 * will parse the the result).
	 * @param ws the window system
	 * @param os the operating system
	 * @param arch the machine architecture
	 * @return the machine readable format of the given config spec
	 */
	public static String createConfigSpec(String ws, String os, String arch) {
		return ws + '.' + os + '.' + arch;
	}

	protected void addSelfCapability(InstallableUnitDescription root) {
		root.setCapabilities(new IProvidedCapability[] {createSelfCapability(root.getId(), root.getVersion())});
	}

	/**
	 * Returns the LDAP filter form that matches the given config spec.  Returns
	 * an empty String if the spec does not identify an ws, os or arch.
	 * @param configSpec a config spec to filter
	 * @return the LDAP filter for the given spec.  <code>null</code> if the given spec does not 
	 * parse into a filter.
	 */
	protected IMatchExpression<IInstallableUnit> createFilterSpec(String configSpec) {
		String[] config = parseConfigSpec(configSpec);
		if (config[0] != null || config[1] != null || config[2] != null) {
			String filterWs = config[0] != null && config[0] != CONFIG_ANY ? "(osgi.ws=" + config[0] + ")" : ""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String filterOs = config[1] != null && config[1] != CONFIG_ANY ? "(osgi.os=" + config[1] + ")" : ""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String filterArch = config[2] != null && config[2] != CONFIG_ANY ? "(osgi.arch=" + config[2] + ")" : ""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			if (filterWs.length() == 0 && filterOs.length() == 0 && filterArch.length() == 0)
				return null;
			return InstallableUnit.parseFilter("(& " + filterWs + filterOs + filterArch + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return null;
	}

	protected boolean filterMatches(IMatchExpression<IInstallableUnit> filter, String configSpec) {
		if (filter == null)
			return true;

		String[] config = parseConfigSpec(configSpec);
		return filter.isMatch(InstallableUnit.contextIU(config[0], config[1], config[2]));
	}

	/**
	 * Returns the normalized string form of the given config spec.  This is useful for putting
	 * in IU ids etc. Note that the result is not intended to be machine readable (i.e., parseConfigSpec
	 * may not work on the result).
	 * @param configSpec the config spec to format
	 * @return the readable format of the given config spec
	 */
	protected String createIdString(String configSpec) {
		String[] config = parseConfigSpec(configSpec);
		return config[0] + '.' + config[1] + '.' + config[2];
	}

	protected String createCUIdString(String id, String type, String flavor, String configSpec) {
		return flavor + id + "." + type + "." + createIdString(configSpec); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Creates and returns a collection of RequiredCapabilities for the IUs represented
	 * by the given collection.  The collection may include a mixture of IInstallableUnits
	 * or VersionedNames.
	 * @param children descriptions of the IUs on which requirements are to be made
	 * @return a collection of RequiredCapabilities representing the given IUs
	 */
	protected Collection<IRequirement> createIURequirements(Collection<? extends IVersionedId> children) {
		ArrayList<IRequirement> result = new ArrayList<IRequirement>(children.size());
		for (IVersionedId next : children) {
			if (next instanceof IInstallableUnit) {
				IInstallableUnit iu = (IInstallableUnit) next;
				VersionRange range = new VersionRange(iu.getVersion(), true, iu.getVersion(), true);
				result.add(MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, iu.getId(), range, iu.getFilter() == null ? null : iu.getFilter(), false, false));
			} else {
				Version version = next.getVersion();
				VersionRange range = (version == null || Version.emptyVersion.equals(version)) ? VersionRange.emptyRange : new VersionRange(version, true, version, true);
				IMatchExpression<IInstallableUnit> filter = getFilterAdvice(next);
				result.add(MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, next.getId(), range, filter, false, false));
			}
		}
		return result;
	}

	private IMatchExpression<IInstallableUnit> getFilterAdvice(IVersionedId name) {
		if (info == null)
			return null;
		Collection<IFilterAdvice> filterAdvice = info.getAdvice(CONFIG_ANY, true, name.getId(), name.getVersion(), IFilterAdvice.class);
		for (IFilterAdvice advice : filterAdvice) {
			IMatchExpression<IInstallableUnit> result = advice.getFilter(name.getId(), name.getVersion(), false);
			if (result != null)
				return result;
		}
		return null;
	}

	protected InstallableUnitDescription createIUShell(String id, Version version) {
		InstallableUnitDescription root = new MetadataFactory.InstallableUnitDescription();
		root.setId(id);
		root.setVersion(version);
		return root;
	}

	protected IArtifactDescriptor createPack200ArtifactDescriptor(IArtifactKey key, File pathOnDisk, String installSize) {
		//TODO this size calculation is bogus
		ArtifactDescriptor result = new ArtifactDescriptor(key);
		if (pathOnDisk != null) {
			result.setProperty(IArtifactDescriptor.ARTIFACT_SIZE, installSize);
			// TODO - this is wrong but I'm testing a work-around for bug 205842
			result.setProperty(IArtifactDescriptor.DOWNLOAD_SIZE, Long.toString(pathOnDisk.length()));
		}
		IProcessingStepDescriptor[] steps = new IProcessingStepDescriptor[] {new ProcessingStepDescriptor("org.eclipse.equinox.p2.processing.Pack200Unpacker", null, true)}; //$NON-NLS-1$
		result.setProcessingSteps(steps);
		result.setProperty(IArtifactDescriptor.FORMAT, IArtifactDescriptor.FORMAT_PACKED);
		return result;
	}

	protected InstallableUnitDescription createParentIU(Collection<? extends IVersionedId> children, String id, Version version) {
		InstallableUnitDescription root = createIUShell(id, version);
		root.addRequirements(createIURequirements(children));
		addSelfCapability(root);
		return root;
	}

	//This is to hide FileUtils from other actions
	protected IPathComputer createParentPrefixComputer(int segmentsToKeep) {
		return FileUtils.createParentPrefixComputer(segmentsToKeep);
	}

	//This is to hide FileUtils from other actions
	protected IPathComputer createRootPrefixComputer(final File root) {
		return FileUtils.createRootPathComputer(root);
	}

	protected IProvidedCapability createSelfCapability(String installableUnitId, Version installableUnitVersion) {
		return MetadataFactory.createProvidedCapability(PublisherHelper.IU_NAMESPACE, installableUnitId, installableUnitVersion);
	}

	protected static InstallableUnitDescription[] processAdditionalInstallableUnitsAdvice(IInstallableUnit iu, IPublisherInfo publisherInfo) {
		Collection<IAdditionalInstallableUnitAdvice> advice = publisherInfo.getAdvice(null, false, iu.getId(), iu.getVersion(), IAdditionalInstallableUnitAdvice.class);
		if (advice.isEmpty())
			return null;

		List<InstallableUnitDescription> ius = new ArrayList<InstallableUnitDescription>();
		for (IAdditionalInstallableUnitAdvice entry : advice) {
			InstallableUnitDescription[] others = entry.getAdditionalInstallableUnitDescriptions(iu);
			if (others != null)
				ius.addAll(Arrays.asList(others));
		}
		return ius.toArray(new InstallableUnitDescription[ius.size()]);
	}

	/**
	 * Add all of the advised artifact properties for the given IU and artifact descriptor.
	 * @param iu the IU
	 * @param descriptor the descriptor to decorate
	 * @param info the publisher info supplying the advice
	 */
	protected static void processArtifactPropertiesAdvice(IInstallableUnit iu, IArtifactDescriptor descriptor, IPublisherInfo info) {
		if (!(descriptor instanceof SimpleArtifactDescriptor))
			return;

		Collection<IPropertyAdvice> advice = info.getAdvice(null, false, iu.getId(), iu.getVersion(), IPropertyAdvice.class);
		for (IPropertyAdvice entry : advice) {
			Map<String, String> props = entry.getArtifactProperties(iu, descriptor);
			if (props == null)
				continue;
			for (Entry<String, String> pe : props.entrySet()) {
				((SimpleArtifactDescriptor) descriptor).setRepositoryProperty(pe.getKey(), pe.getValue());
			}
		}
	}

	/**
	 * Add all of the advised IU properties for the given IU.
	 * @param iu the IU to decorate
	 * @param info the publisher info supplying the advice
	 */
	protected static void processInstallableUnitPropertiesAdvice(InstallableUnitDescription iu, IPublisherInfo info) {
		Collection<IPropertyAdvice> advice = info.getAdvice(null, false, iu.getId(), iu.getVersion(), IPropertyAdvice.class);
		for (IPropertyAdvice entry : advice) {
			Map<String, String> props = entry.getInstallableUnitProperties(iu);
			if (props == null)
				continue;
			for (Entry<String, String> pe : props.entrySet()) {
				iu.setProperty(pe.getKey(), pe.getValue());
			}
		}
	}

	/**
	 * Add any update descriptor advice to the given IU
	 * @param iu the IU to decorate
	 * @param info the publisher info supplying the advice
	 */
	protected static void processUpdateDescriptorAdvice(InstallableUnitDescription iu, IPublisherInfo info) {
		Collection<IUpdateDescriptorAdvice> advice = info.getAdvice(null, false, iu.getId(), iu.getVersion(), IUpdateDescriptorAdvice.class);

		if (advice.isEmpty())
			return;

		for (IUpdateDescriptorAdvice entry : advice) {
			//process the IU Descriptor
			IUpdateDescriptor updateDescriptor = entry.getUpdateDescriptor(iu);
			if (updateDescriptor != null) {
				iu.setUpdateDescriptor(updateDescriptor);
			}
		}
	}

	/**
	 * Add all of the advised provided and required capabilities for the given installable unit.
	 * @param iu the IU to decorate
	 * @param info the publisher info supplying the advice
	 */
	protected static void processCapabilityAdvice(InstallableUnitDescription iu, IPublisherInfo info) {
		Collection<ICapabilityAdvice> advice = info.getAdvice(null, false, iu.getId(), iu.getVersion(), ICapabilityAdvice.class);
		if (advice.isEmpty())
			return;

		for (ICapabilityAdvice entry : advice) {
			//process required capabilities
			IRequirement[] requiredAdvice = entry.getRequiredCapabilities(iu);
			if (requiredAdvice != null) {
				List<IRequirement> current = iu.getRequirements();
				Set<IRequirement> resultRequiredCapabilities = new HashSet<IRequirement>(current);

				// remove current required capabilities that match (same name and namespace) advice.
				for (int j = 0; j < current.size(); j++) {
					IRequirement curr = current.get(j);
					IRequiredCapability currentRequiredCapability = null;
					if (curr instanceof IRequiredCapability)
						currentRequiredCapability = (IRequiredCapability) curr;
					else
						continue;
					for (int k = 0; k < requiredAdvice.length; k++) {
						IRequiredCapability requiredCapability = null;
						if (requiredAdvice[k] instanceof IRequiredCapability)
							requiredCapability = (IRequiredCapability) requiredAdvice[k];
						else
							continue;
						if (requiredCapability.getNamespace().equals(currentRequiredCapability.getNamespace()) && requiredCapability.getName().equals(currentRequiredCapability.getName())) {
							resultRequiredCapabilities.remove(currentRequiredCapability);
							break;
						}
					}
				}
				// add all advice
				resultRequiredCapabilities.addAll(Arrays.asList(requiredAdvice));
				iu.setRequirements(resultRequiredCapabilities.toArray(new IRequirement[resultRequiredCapabilities.size()]));
			}

			//process meta required capabilities
			IRequirement[] metaRequiredAdvice = entry.getMetaRequiredCapabilities(iu);
			if (metaRequiredAdvice != null) {
				Collection<IRequirement> current = iu.getMetaRequirements();
				Set<IRequirement> resultMetaRequiredCapabilities = new HashSet<IRequirement>(current);

				// remove current meta-required capabilities that match (same name and namespace) advice.
				for (IRequirement curr : current) {
					IRequiredCapability currentMetaRequiredCapability = null;
					if (curr instanceof IRequiredCapability)
						currentMetaRequiredCapability = (IRequiredCapability) curr;
					else
						continue;
					for (int k = 0; k < metaRequiredAdvice.length; k++) {
						IRequiredCapability metaRequiredCapability = null;
						if (metaRequiredAdvice[k] instanceof IRequiredCapability)
							metaRequiredCapability = (IRequiredCapability) metaRequiredAdvice[k];
						else
							continue;
						if (metaRequiredCapability.getNamespace().equals(currentMetaRequiredCapability.getNamespace()) && metaRequiredCapability.getName().equals(currentMetaRequiredCapability.getName())) {
							resultMetaRequiredCapabilities.remove(currentMetaRequiredCapability);
							break;
						}
					}
				}

				// add all advice
				resultMetaRequiredCapabilities.addAll(Arrays.asList(metaRequiredAdvice));
				iu.setMetaRequirements(resultMetaRequiredCapabilities.toArray(new IRequirement[resultMetaRequiredCapabilities.size()]));
			}

			//process provided capabilities
			IProvidedCapability[] providedAdvice = entry.getProvidedCapabilities(iu);
			if (providedAdvice != null) {
				Collection<IProvidedCapability> current = iu.getProvidedCapabilities();
				Set<IProvidedCapability> resultProvidedCapabilities = new HashSet<IProvidedCapability>(current);
				for (IProvidedCapability currentProvidedCapability : current) {
					for (int k = 0; k < providedAdvice.length; k++) {
						IProvidedCapability providedCapability = providedAdvice[k];
						if (providedCapability.getNamespace().equals(currentProvidedCapability.getNamespace()) && providedCapability.getName().equals(currentProvidedCapability.getName())) {
							resultProvidedCapabilities.remove(currentProvidedCapability);
							break;
						}
					}
				}
				resultProvidedCapabilities.addAll(Arrays.asList(providedAdvice));
				iu.setCapabilities(resultProvidedCapabilities.toArray(new IProvidedCapability[resultProvidedCapabilities.size()]));
			}
		}
	}

	/**
	 * Adds all applicable touchpoint advice to the given installable unit.
	 * @param iu The installable unit to add touchpoint advice to
	 * @param currentInstructions The set of touchpoint instructions assembled for this IU so far
	 * @param info The publisher info
	 */
	protected static void processTouchpointAdvice(InstallableUnitDescription iu, Map<String, ? extends Object> currentInstructions, IPublisherInfo info) {
		processTouchpointAdvice(iu, currentInstructions, info, null);
	}

	protected static void processTouchpointAdvice(InstallableUnitDescription iu, Map<String, ? extends Object> currentInstructions, IPublisherInfo info, String configSpec) {
		Collection<ITouchpointAdvice> advice = info.getAdvice(configSpec, false, iu.getId(), iu.getVersion(), ITouchpointAdvice.class);
		if (currentInstructions == null) {
			if (advice == null || advice.isEmpty())
				return;
			currentInstructions = CollectionUtils.emptyMap();
		}

		ITouchpointData result = MetadataFactory.createTouchpointData(currentInstructions);
		if (advice != null) {
			for (ITouchpointAdvice entry : advice) {
				result = entry.getTouchpointData(result);
			}
		}
		iu.addTouchpointData(result);
	}

	/**
	 * Publishes the artifact by zipping the <code>files</code> using <code>root</code>
	 * as a base for relative paths. Then copying the zip into the repository.
	 * @param descriptor used to identify the zip.
	 * @param inclusion the file to be published. files can be <code>null</code> but no action is taken.
	 * @param publisherInfo the publisher info.
	 */
	protected void publishArtifact(IArtifactDescriptor descriptor, File inclusion, IPublisherInfo publisherInfo) {
		// no files to publish so this is done.
		if (inclusion == null)
			return;
		// if the destination already contains the descriptor, there is nothing to do.
		IArtifactRepository destination = publisherInfo.getArtifactRepository();
		if (destination == null || destination.contains(descriptor))
			return;

		// if all we are doing is indexing things then add the descriptor and get on with it
		if ((publisherInfo.getArtifactOptions() & IPublisherInfo.A_PUBLISH) == 0) {
			destination.addDescriptor(descriptor);
			return;
		}

		// if the file is already in the same location the repo will put it, just add the descriptor and exit
		if (destination instanceof IFileArtifactRepository) {
			File descriptorFile = ((IFileArtifactRepository) destination).getArtifactFile(descriptor);
			if (inclusion.equals(descriptorFile)) {
				destination.addDescriptor(descriptor);
				return;
			}
		}

		try {
			OutputStream output = destination.getOutputStream(descriptor);
			if (output == null)
				return;
			output = new BufferedOutputStream(output);
			FileUtils.copyStream(new BufferedInputStream(new FileInputStream(inclusion)), true, output, true);
		} catch (ProvisionException e) {
			LogHelper.log(e.getStatus());
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, "Error publishing artifacts", e)); //$NON-NLS-1$
		}
	}

	/**
	 * Publishes the artifact by zipping the <code>files</code> using <code>root</code>
	 * as a base for relative paths. Then copying the zip into the repository.
	 * @param descriptor used to identify the zip.
	 * @param inclusions and folders to be included in the zip. files can be null.
	 * @param exclusions and folders to be excluded in the zip. files can be null.
	 * @param publisherInfo the publisher info.
	 * @param prefixComputer
	 */
	protected void publishArtifact(IArtifactDescriptor descriptor, File[] inclusions, File[] exclusions, IPublisherInfo publisherInfo, IPathComputer prefixComputer) {
		// no files to publish so this is done.
		if (inclusions == null || inclusions.length < 1)
			return;
		// if the destination already contains the descriptor, there is nothing to do.
		IArtifactRepository destination = publisherInfo.getArtifactRepository();
		if (destination == null || destination.contains(descriptor))
			return;
		// if all we are doing is indexing things then add the descriptor and get on with it
		if ((publisherInfo.getArtifactOptions() & IPublisherInfo.A_PUBLISH) == 0) {
			destination.addDescriptor(descriptor);
			return;
		}

		// TODO need to implement the overwrite story in the repos
		//		boolean overwrite = (info.getArtifactOptions() & IPublisherInfo.A_OVERWRITE) > 0;
		// if there is just one file and the mode is as-is, just copy the file into the repo
		// otherwise, zip up the files and copy the zip into the repo
		File tempFile = null;
		try {
			OutputStream output = destination.getOutputStream(descriptor);
			if (output == null)
				return;
			output = new BufferedOutputStream(output);
			tempFile = File.createTempFile("p2.generator", ""); //$NON-NLS-1$ //$NON-NLS-2$
			FileUtils.zip(inclusions, exclusions, tempFile, prefixComputer);
			if (output != null)
				FileUtils.copyStream(new BufferedInputStream(new FileInputStream(tempFile)), true, output, true);
		} catch (ProvisionException e) {
			LogHelper.log(e.getStatus());
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.ID, "Error publishing artifacts", e)); //$NON-NLS-1$
			e.printStackTrace();
		} finally {
			if (tempFile != null)
				tempFile.delete();
		}
	}

	/**
	 * Loop over the known metadata repositories looking for the given IU.
	 * Return the first IU found.
	 * @param iuId  the id of the IU to look for
	 * @return the first matching IU or <code>null</code> if none.
	 */
	protected IInstallableUnit queryForIU(IPublisherResult publisherResult, String iuId, Version version) {
		IQuery<IInstallableUnit> query = QueryUtil.createIUQuery(iuId, version);
		if (version == null || Version.emptyVersion.equals(version))
			query = QueryUtil.createLatestQuery(query);

		IQueryResult<IInstallableUnit> collector = Collector.emptyCollector();
		NullProgressMonitor progress = new NullProgressMonitor();
		if (publisherResult != null)
			collector = publisherResult.query(query, progress);
		if (collector.isEmpty() && info.getMetadataRepository() != null)
			collector = info.getMetadataRepository().query(query, progress);
		if (collector.isEmpty() && info.getContextMetadataRepository() != null)
			collector = info.getContextMetadataRepository().query(query, progress);
		if (!collector.isEmpty())
			return collector.iterator().next();
		return null;
	}

	/**
	 * Loop over the known metadata repositories looking for the given IU within a particular range
	 * @param publisherResult
	 * @param iuId the id of the IU to look for
	 * @param versionRange the version range to consider
	 * @return The the IUs with the matching ids in the given range
	 */
	protected IQueryResult<IInstallableUnit> queryForIUs(IPublisherResult publisherResult, String iuId, VersionRange versionRange) {
		IQuery<IInstallableUnit> query = null;
		IQueryResult<IInstallableUnit> queryResult = Collector.emptyCollector();
		query = QueryUtil.createIUQuery(iuId, versionRange);
		NullProgressMonitor progress = new NullProgressMonitor();
		if (publisherResult != null)
			queryResult = publisherResult.query(query, progress);
		if (queryResult.isEmpty() && info.getMetadataRepository() != null)
			queryResult = info.getMetadataRepository().query(query, progress);
		if (queryResult.isEmpty() && info.getContextMetadataRepository() != null)
			queryResult = info.getContextMetadataRepository().query(query, progress);
		return queryResult;
	}

	public abstract IStatus perform(IPublisherInfo publisherInfo, IPublisherResult results, IProgressMonitor monitor);

	public void setPublisherInfo(IPublisherInfo info) {
		this.info = info;
	}
}
