package com.github.christokios;

import javafx.util.Pair;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;

@Mojo(name = "capsule", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyCollection = ResolutionScope.RUNTIME)
public class CapsuleMojo extends AbstractMojo {

	public static final String DEFAULT_CAPSULE_NAME = "Capsule";
	public static final String DEFAULT_CAPSULE_CLASS = DEFAULT_CAPSULE_NAME + ".class";

	public static enum Type {
		empty,
		thin,
		full
	}

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject mavenProject;

	/**
	 * * AETHER REPO LINK **
	 */
	@Component
	private RepositorySystem repoSystem;
	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
	private RepositorySystemSession repoSession;
	@Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
	private List<RemoteRepository> remoteRepos;
	@Parameter(defaultValue = "${project.build.finalName}", readonly = true)
	private String finalName;

	/**
	 * * REQUIRED VARIABLES **
	 */
	@Parameter(property = "capsule.mainClass", required = true)
	private String mainClass;

	/**
	 * * OPTIONAL VARIABLES **
	 */
	@Parameter(property = "capsule.version", defaultValue = "0.6.0-SNAPSHOT")
	private String capsuleVersion;
	@Parameter(property = "capsule.outputDir", defaultValue = "${project.build.directory}")
	private File outputDir;
	@Parameter(property = "capsule.buildExec", defaultValue = "false")
	private String buildExec;

	@Parameter
	private Properties properties; // System-Properties for the app
	@Parameter
	private Properties manifest; // additional manifest entries

	/**
	 * * DEPENDENCIES **
	 */
	@Parameter(defaultValue = "${project.artifacts}") // will only contain scope of compile+runtime
	private Collection<Artifact> artifacts;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("[Capsule] Capsule Version: " + capsuleVersion.toString());
		getLog().info("[Capsule] Output Directory: " + outputDir.toString());
		getLog().info("[Capsule] Main Class: " + mainClass);
		if (manifest != null) {
			getLog().info("[Capsule] Manifest Entries: ");
			for (final Map.Entry property : manifest.entrySet()) getLog().info("\t\t\\--" + property.getKey() + ": " + property.getValue());
		}
		if (properties != null) {
			getLog().info("[Capsule] System Properties: ");
			for (final Map.Entry property : properties.entrySet()) getLog().info("\t\t\\--" + property.getKey() + "=" + property.getValue());
		}

		getLog().info("[Capsule] Dependencies: ");
		for (final Dependency dependency : (List<Dependency>) mavenProject.getDependencies()) {
			if (dependency.getScope().equals("compile") || dependency.getScope().equals("runtime")) {
				if (dependency.getExclusions().size() == 0)
					getLog().info("\t\t\\--" + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion());
				else {
					final StringBuilder exclusionsList = new StringBuilder();
					for (final Exclusion exclusion : dependency.getExclusions()) {
						if (dependency.getExclusions().size() > 1) exclusionsList.append(",");
						exclusionsList.append(exclusion.getGroupId() + ":" + exclusion.getArtifactId());
					}
					getLog().info("\t\t\\--" + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion() + "(" + exclusionsList + ")");
				}
			}
		}

		try {
			buildEmpty();
			buildThin();
			buildFull();
		} catch (final IOException e) {
			e.printStackTrace();
			throw new MojoFailureException(e.getMessage());
		}
	}

	/**
	 * Build the empty version of the capsule, i.e the the app and its dependencies will be downloaded at runtime.
	 */
	public final void buildEmpty() throws IOException {
		final Pair<File, JarOutputStream> jar = openJar(Type.empty);
		final JarOutputStream jarStream = jar.getValue();

		// add manifest (plus Application)
		final Map<String, String> additionalAttributes = new HashMap();
		additionalAttributes.put("Application", mavenProject.getGroupId() + ":" + mavenProject.getArtifactId() + ":" + mavenProject.getVersion());
		deployManifestToJar(jarStream, additionalAttributes, Type.empty);

		// add Capsule classes
		final Map<String, byte[]> otherCapsuleClasses = getAllCapsuleClasses();
		for (final Map.Entry<String, byte[]> entry : otherCapsuleClasses.entrySet())
			addToJar(entry.getKey(), new ByteArrayInputStream(entry.getValue()), jarStream);

		IOUtil.close(jarStream);
		this.createExecCopy(jar.getKey());
	}

	/**
	 * Build the thin version of the capsule (i.e no dependencies). The dependencies will be resolved at runtime.
	 */
	public final void buildThin() throws IOException {
		final Pair<File, JarOutputStream> jar = openJar(Type.thin);
		final JarOutputStream jarStream = jar.getValue();

		// add manifest (with Dependencies list)
		final Map<String, String> additionalAttributes = new HashMap();
		final StringBuilder dependenciesList = new StringBuilder();
		for (final Dependency dependency : (List<Dependency>) mavenProject.getDependencies()) {
			if (dependency.getScope().equals("compile") || dependency.getScope().equals("runtime")) {
				dependenciesList.append(dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion());
				if (dependency.getExclusions().size() > 0) {
					final StringBuilder exclusionsList = new StringBuilder();
					for (final Exclusion exclusion : dependency.getExclusions()) {
						if (dependency.getExclusions().size() > 1) exclusionsList.append(",");
						exclusionsList.append(exclusion.getGroupId() + ":" + exclusion.getArtifactId());
					}
					dependenciesList.append("(" + exclusionsList.toString() + ")");
				}
				dependenciesList.append(" ");
			}
		}
		additionalAttributes.put("Dependencies", dependenciesList.toString());
		deployManifestToJar(jarStream, additionalAttributes, Type.thin);

		// add compiled project classes
		final File classesDir = new File(outputDir, "classes");
		Files.walkFileTree(classesDir.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(final Path path, final BasicFileAttributes attrs) throws IOException {
				if (!attrs.isDirectory())
					addToJar(path.toString().substring(path.toString().indexOf("classes") + 8), new FileInputStream(path.toFile()), jarStream);
				return FileVisitResult.CONTINUE;
			}
		});

		// add Capsule classes
		final Map<String, byte[]> capsuleClasses = getAllCapsuleClasses();
		for (final Map.Entry<String, byte[]> entry : capsuleClasses.entrySet())
			addToJar(entry.getKey(), new ByteArrayInputStream(entry.getValue()), jarStream);

		IOUtil.close(jarStream);
		this.createExecCopy(jar.getKey());
	}

	/**
	 * Build the full version of the capsule which includes the dependencies embedded.
	 */
	public final void buildFull() throws IOException {
		final Pair<File, JarOutputStream> jar = openJar(Type.full);
		final JarOutputStream jarStream = jar.getValue();

		// add manifest
		deployManifestToJar(jarStream, null, Type.full);

		// add main jar
		final File mainJarFile = new File(outputDir, finalName + ".jar");
		addToJar(mainJarFile.getName(), new FileInputStream(mainJarFile), jarStream);

		// add dependencies
		for (final Artifact artifact : artifacts)
			addToJar(artifact.getFile().getName(), new FileInputStream(artifact.getFile()), jarStream);

		// add Capsule.class
		this.addToJar(DEFAULT_CAPSULE_CLASS, new ByteArrayInputStream(getCapsuleClass()), jarStream);

		IOUtil.close(jarStream);
		this.createExecCopy(jar.getKey());
	}

	/**
	 * * UTILS ***************************************************************
	 */

	private JarOutputStream deployManifestToJar(final JarOutputStream jar, final Map<String, String> additionalAttributes, final Type type) throws IOException {
		final Manifest manifestBuild = new Manifest();
		final Attributes attributes = manifestBuild.getMainAttributes();
		attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attributes.put(Attributes.Name.MAIN_CLASS, DEFAULT_CAPSULE_NAME);
		attributes.put(new Attributes.Name("Application-Class"), this.mainClass);
		attributes.put(new Attributes.Name("Application-Name"), this.finalName + "-capsule-" + type);

		if (this.properties != null) {
			final StringBuilder propertiesList = new StringBuilder();
			for (final Map.Entry<Object, Object> property : this.properties.entrySet())
				propertiesList.append(property.getKey() + "=" + property.getValue() + " ");
			attributes.put(new Attributes.Name("System-Properties"), propertiesList.toString());
		}

		// custom user defined manifest entries
		if (this.manifest != null)
			for (final Map.Entry<Object, Object> property : this.manifest.entrySet())
				attributes.put(new Attributes.Name(property.getKey().toString()), property.getValue());

		// additional attributes
		if (additionalAttributes != null)
			for (final Map.Entry<String, String> entry : additionalAttributes.entrySet())
				attributes.put(new Attributes.Name(entry.getKey()), entry.getValue());

		// write to jar
		final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
		manifestBuild.write(dataStream);
		final byte[] bytes = dataStream.toByteArray();
		final ByteArrayInputStream manifestInputStream = new ByteArrayInputStream(bytes);
		return addToJar(JarFile.MANIFEST_NAME, manifestInputStream, jar);
	}

	private byte[] getCapsuleClass() throws IOException {
		final JarInputStream capsuleJarInputStream = new JarInputStream(new FileInputStream(resolveCapsule()));

		JarEntry entry;
		while ((entry = capsuleJarInputStream.getNextJarEntry()) != null) // look for Capsule.class
			if (entry.getName().equals(DEFAULT_CAPSULE_CLASS))
				return IOUtil.toByteArray(capsuleJarInputStream);
		return null;
	}

	private Map<String, byte[]> getAllCapsuleClasses() throws IOException {
		final JarInputStream capsuleJarInputStream = new JarInputStream(new FileInputStream(resolveCapsule()));

		final Map<String, byte[]> otherClasses = new HashMap();
		JarEntry entry;
		while ((entry = capsuleJarInputStream.getNextJarEntry()) != null) // look for Capsule.class
			if (entry.getName().contains("capsule/") || entry.getName().equals(DEFAULT_CAPSULE_CLASS))
				otherClasses.put(entry.getName(), IOUtil.toByteArray(capsuleJarInputStream));

		return otherClasses;
	}

	private JarOutputStream addToJar(final String name, final InputStream input, final JarOutputStream jar) throws IOException {
		jar.putNextEntry(new ZipEntry(name));
		IOUtil.copy(input, jar);
		jar.closeEntry();
		return jar;
	}

	private Pair<File, JarOutputStream> openJar(final Type type) throws IOException {
		final File file = new File(outputDir, finalName + "-capsule-" + type.toString() + ".jar");
		getLog().info("[Capsule] Created: " + file.getName());
		return new Pair(file, new JarOutputStream(new FileOutputStream(file)));
	}

	private File resolveCapsule() throws IOException {
		final ArtifactResult artifactResult;
		try { artifactResult = this.resolve("co.paralleluniverse", "capsule", capsuleVersion); } catch (final ArtifactResolutionException e) {
			throw new IOException("Capsule not found from repos");
		}
		return artifactResult.getArtifact().getFile();
	}

	private ArtifactResult resolve(final String groupId, final String artifactId, final String version) throws ArtifactResolutionException {
		return repoSystem.resolveArtifact(repoSession, new ArtifactRequest(new DefaultArtifact(groupId + ":" + artifactId + ":" + version), remoteRepos,
			null));
	}

	private void createExecCopy(final File jar) throws IOException {
		if (this.buildExec.equals("true") || this.buildExec.equals("1")) {
			final File exec = new File(jar.getPath().replace(".jar", ".x"));
			FileOutputStream out = null;
			FileInputStream in = null;
			try {
				out = new FileOutputStream(exec);
				in = new FileInputStream(jar);
				out.write(("#!/bin/sh\n\nexec java " + " -jar \"$0\" \"$@\"\n\n").getBytes("ASCII"));
				Files.copy(jar.toPath(), out);
				out.flush();
				Runtime.getRuntime().exec("chmod +x " + exec.getAbsolutePath());
			} finally {
				IOUtil.close(in);
				IOUtil.close(out);
				getLog().info("[Capsule] Created: " + exec.getName());
			}
		}
	}

}
