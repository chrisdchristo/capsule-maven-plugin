package com.github.chrisdchristo.capsule;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultMavenProjectHelper;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.IOUtil;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

public abstract class SuperMojo extends AbstractMojo {

	/**
	 * AETHER REPO LINK
	 */
	@Component
	protected RepositorySystem repoSystem = null;
	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
	protected RepositorySystemSession repoSession = null;
	@Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
	protected List<RemoteRepository> remoteRepos = null;
	@Parameter(defaultValue = "${project.build.finalName}", readonly = true)
	protected String finalName = null;
	@Parameter(defaultValue = "${project.build.directory}")
	protected File buildDir = null;
	@Parameter(defaultValue = "${project.basedir}")
	protected File baseDir = null;


	/**
	 * Project
	 */
	@Parameter(defaultValue = "${project}", readonly = true)
	protected MavenProject project = null;

	protected final MavenProjectHelper helper = new DefaultMavenProjectHelper();

	abstract String pluginKey();
	abstract String logPrefix();

	// DEPENDENCIES

	protected Set<Dependency> appDependencies() { return set(project.getTestDependencies()); }
	protected Set<Dependency> appDirectDependencies() { return set(project.getDependencies()); }
	protected Set<Artifact> appDependencyArtifacts() { return project.getArtifacts(); }
	protected Set<Artifact> appDirectDependencyArtifacts() { return project.getDependencyArtifacts(); }

	protected Set<Dependency> pluginDependencies() { return getDependenciesOf(set(plugin().getDependencies()), true); }
	protected Set<Dependency> pluginDirectDependencies() { return set(plugin().getDependencies()); }
	protected Set<Artifact> pluginDependencyArtifacts() { return getDependencyArtifactsOf(set(plugin().getDependencies()), true); }
	protected Set<Artifact> pluginDirectDependencyArtifacts() { return toArtifacts(set(plugin().getDependencies())); }


	// RESOLVERS

	private Plugin plugin() {
		return project.getPlugin(pluginKey());
	}

	private ArtifactResult resolve(final Dependency dependency) {
		return resolve(dependency.getGroupId(), dependency.getArtifactId(), dependency.getClassifier(), dependency.getVersion());
	}

	protected ArtifactResult resolve(final String groupId, final String artifactId, final String classifier, final String version) {
		final String coords = coords(groupId, artifactId, classifier, version);
		try {
			return repoSystem.resolveArtifact(repoSession, new ArtifactRequest(new DefaultArtifact(coords), remoteRepos, null));
		} catch (final ArtifactResolutionException e) {
			warn("\t\t[Resolve] Failed to resolve: [" + coords + "]");
			return null;
		}
	}

	private Set<ArtifactResult> resolveDependencies(final Dependency dependency) {
		try {
			final CollectRequest collectRequest = new CollectRequest(new org.eclipse.aether.graph.Dependency(resolve(dependency).getArtifact(), ""), remoteRepos);
			return set(repoSystem.resolveDependencies(repoSession, new DependencyRequest(collectRequest, null)).getArtifactResults());
		} catch (final DependencyResolutionException e) {
			warn("\t\t[Resolve] Failed to resolve: [" + coords(dependency) + "]");
			return new HashSet<>();
		}
	}

	private Artifact toArtifact(final ArtifactResult ar) {
		if (ar == null) return null;
		final Artifact artifact = new org.apache.maven.artifact.DefaultArtifact(
				ar.getArtifact().getGroupId(),
				ar.getArtifact().getArtifactId(),
				ar.getArtifact().getVersion(),
				null,
				"jar",
				ar.getArtifact().getClassifier(),
				null);
		if (ar.getRequest().getDependencyNode() != null && ar.getRequest().getDependencyNode().getDependency() != null) {
			artifact.setScope(ar.getRequest().getDependencyNode().getDependency().getScope());
			artifact.setOptional(ar.getRequest().getDependencyNode().getDependency().isOptional());
		}
		if (artifact.getScope() == null || artifact.getScope().isEmpty()) artifact.setScope("compile");
		artifact.setFile(ar.getArtifact().getFile());
		return artifact;
	}

	private Artifact toArtifact(final Dependency dependency) {
		if (dependency == null) return null;
		final Artifact artifact = toArtifact(resolve(dependency));
		artifact.setScope(dependency.getScope());
		if (artifact.getScope() == null || artifact.getScope().isEmpty()) artifact.setScope("compile");
		return artifact;
	}

	private Dependency toDependency(final Artifact artifact) {
		if (artifact == null) return null;
		final Dependency dependency = new Dependency();
		dependency.setGroupId(artifact.getGroupId());
		dependency.setArtifactId(artifact.getArtifactId());
		dependency.setVersion(artifact.getVersion());
		dependency.setScope(artifact.getScope());
		dependency.setClassifier(artifact.getClassifier());
		dependency.setOptional(artifact.isOptional());
		if (dependency.getScope() == null || dependency.getScope().isEmpty()) dependency.setScope("compile");
		return dependency;
	}

	private Dependency toDependency(final ArtifactResult ar) {
		return toDependency(toArtifact(ar));
	}

	private Set<Artifact> getDependencyArtifactsOf(final Dependency dependency, final boolean includeRoot) {
		final Set<Artifact> artifacts = new HashSet<>();
		if (includeRoot) artifacts.add(toArtifact(dependency));
		for (final ArtifactResult ar : resolveDependencies(dependency)) {
			final Artifact artifact = toArtifact(ar);

			// if null set to default compile
			if (artifact.getScope() == null || artifact.getScope().isEmpty()) artifact.setScope("compile");

			// skip any deps that aren't compile or runtime
			if (!artifact.getScope().equals("compile") && !artifact.getScope().equals("runtime")) continue;

			// set direct-scope on transitive deps
			if (dependency.getScope().equals("provided")) artifact.setScope("provided");
			if (dependency.getScope().equals("system")) artifact.setScope("system");
			if (dependency.getScope().equals("test")) artifact.setScope("test");

			artifacts.add(toArtifact(ar));
		}
		return cleanArtifacts(artifacts);
	}

	private Set<Artifact> getDependencyArtifactsOf(final Set<Dependency> dependencies, final boolean includeRoot) {
		final Set<Artifact> artifacts = new HashSet<>();
		for (final Dependency dependency : dependencies) {
			artifacts.addAll(getDependencyArtifactsOf(dependency, includeRoot));
		}
		return cleanArtifacts(artifacts);
	}

	private Set<Dependency> getDependenciesOf(final Dependency dependency, final boolean includeRoot) {
		final Set<Dependency> dependencies = new HashSet<>();
		if (includeRoot) dependencies.add(dependency);
		for (final ArtifactResult ar : resolveDependencies(dependency)) {
			dependencies.add(toDependency(ar));
		}
		return cleanDependencies(dependencies);
	}

	private Set<Dependency> getDependenciesOf(final Set<Dependency> dependencies, final boolean includeRoot) {
		final Set<Dependency> dependenciesAll = new HashSet<>();
		for (final Dependency dependency : dependencies) {
			dependenciesAll.addAll(getDependenciesOf(dependency, includeRoot));
		}
		return cleanDependencies(dependenciesAll);
	}

	private Set<Artifact> toArtifacts(final Set<Dependency> dependencies) {
		final Set<Artifact> artifacts = new HashSet<>();
		for (final Dependency dependency : dependencies) {
			artifacts.add(toArtifact(dependency));
		}
		return cleanArtifacts(artifacts);
	}

	// HELPERS

	public static class Pair<K, V> {
		public K key;
		public V value;
		public Pair() {}
		public Pair(final K key, final V value) {
			this.key = key;
			this.value = value;
		}
	}

	private <T> Set<T> set(final List<T> list) {
		return new HashSet<>(list);
	}


	// COORDS

	private String coords(final String groupId, final String artifactId, final String classifier, final String version) {
		final StringBuilder coords = new StringBuilder();
		coords.append(groupId).append(":").append(artifactId);
		if (classifier != null && !classifier.isEmpty())
			coords.append(":").append(classifier);
		if (version != null && !version.isEmpty())
			coords.append(":").append(version);
		return coords.toString();
	}

	protected String coords(final Artifact artifact) {
		return coords(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getVersion());
	}

	protected String coords(final Dependency dependency) {
		return coords(dependency.getGroupId(), dependency.getArtifactId(), dependency.getClassifier(), dependency.getVersion());
	}

	protected String coordsWithExclusions(final Dependency dependency) {
		final StringBuilder coords = new StringBuilder(coords(dependency));
		if (dependency.getExclusions().size() > 0) {
			final StringBuilder exclusionsList = new StringBuilder();
			int i = 0;
			for (final Exclusion exclusion : dependency.getExclusions()) {
				if (i > 0) exclusionsList.append(",");
				exclusionsList.append(exclusion.getGroupId()).append(":").append(exclusion.getArtifactId());
				i++;
			}
			coords.append("(").append(exclusionsList.toString()).append(")");
		}
		return coords.toString();
	}


	// CLEANERS

	// clean any duplicates
	private Set<Dependency> cleanDependencies(final Set<Dependency> dependencies) {
		final Set<Dependency> dependenciesClean = new HashSet<>();
		for (final Dependency dependencyA : dependencies) {
			boolean found = false;
			for (final Dependency dependencyB : dependenciesClean) {
				if (coords(dependencyA).equals(coords(dependencyB))) {
					found = true;
					break;
				}
			}
			if (!found) dependenciesClean.add(dependencyA);
		}
		return dependenciesClean;
	}

	// clean any duplicates
	private Set<Artifact> cleanArtifacts(final Set<Artifact> artifacts) {
		final Set<Artifact> artifactsClean = new HashSet<>();
		for (final Artifact artifactA : artifacts) {
			boolean found = false;
			for (final Artifact artifactB : artifactsClean) {
				if (coords(artifactA).equals(coords(artifactB))){
					found = true;
					break;
				}
			}
			if (!found) artifactsClean.add(artifactA);
		}
		return artifactsClean;
	}

	protected Set<Dependency> cleanDependencies(final Set<Dependency> setA, final boolean includeA, final Set<Dependency> setB, final boolean includeB) {
		final Set<Dependency> set = new HashSet<>();
		if (includeA) set.addAll(setA);
		if (includeB) set.addAll(setB);
		return cleanDependencies(set);
	}

	protected Set<Artifact> cleanArtifacts(final Set<Artifact> setA, final boolean includeA, final Set<Artifact> setB, final boolean includeB) {
		final Set<Artifact> set = new HashSet<>();
		if (includeA) set.addAll(setA);
		if (includeB) set.addAll(setB);
		return cleanArtifacts(set);
	}


	// JAR & FILE HELPERS

	protected String addDirectoryToJar(final JarOutputStream jar, final String outputDirectory) throws IOException {

		// format the output directory
		String formattedOutputDirectory = "";
		if (outputDirectory != null && !outputDirectory.isEmpty()) {
			if (!outputDirectory.endsWith("/")) {
				formattedOutputDirectory = outputDirectory + File.separatorChar;
			} else {
				formattedOutputDirectory = outputDirectory;
			}
		}

		if (!formattedOutputDirectory.isEmpty()) {
			try {
				jar.putNextEntry(new ZipEntry(formattedOutputDirectory));
				jar.closeEntry();
			} catch (final ZipException ignore) {} // ignore duplicate entries and other errors
		}
		return formattedOutputDirectory;
	}

	protected JarOutputStream addToJar(final String name, final InputStream input, final JarOutputStream jar) throws IOException {
		try {
			debug("\t[Added to Jar]: " + name);
			jar.putNextEntry(new ZipEntry(name));
			IOUtil.copy(input, jar);
			jar.closeEntry();
		} catch (final ZipException ignore) {} // ignore duplicate entries and other errors
		IOUtil.close(input);
		return jar;
	}


	// LOG

	protected void debug(final String message) { getLog().debug(logPrefix() + message); }
	protected void info(final String message) { getLog().info(logPrefix() + message); }
	protected void warn(final String message) { getLog().warn(logPrefix() + message); }
	protected void printManifest(final Manifest manifest) {
		info("\t[Manifest]:");
		for (final Map.Entry<Object, Object> attr : manifest.getMainAttributes().entrySet()) {
			info("\t\t" + attr.getKey().toString() + ": " + attr.getValue().toString());
		}
		for (final Map.Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
			info("");
			info("\t\tName:" + entry.getKey());
			for (final Map.Entry<Object, Object> attr : entry.getValue().entrySet()) {
				info("\t\t" + attr.getKey().toString() + ": " + attr.getValue().toString());
			}
		}
	}

}
