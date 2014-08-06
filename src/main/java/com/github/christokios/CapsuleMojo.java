package com.github.christokios;

import org.apache.maven.artifact.Artifact;
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

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject mavenProject;

	/**** AETHER REPO LINK ***/
	@Component
	private RepositorySystem repoSystem;
	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
	private RepositorySystemSession repoSession;
	@Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
	private List<RemoteRepository> remoteRepos;

	/**** REQUIRED VARIABLES ***/
	@Parameter(property = "capsule.mainClass", required = true)
	private String mainClass;

	/**** OPTIONAL VARIABLES ***/
	@Parameter(property = "capsule.target", defaultValue = "${project.build.directory}")
	private File target;
	@Parameter(property = "capsule.finalName", defaultValue = "${project.build.finalName}")
	private String finalName;
	@Parameter(property = "capsule.extractCapsule", defaultValue = "false")
	private String extractCapsule;
	@Parameter(property = "capsule.minJavaVersion", defaultValue = "1.8.0")
	private String minJavaVersion;
	@Parameter(property = "capsule.version", defaultValue = "0.6.0-SNAPSHOT")
	private String capsuleVersion;
	@Parameter(property = "capsule.jvmArgs", defaultValue = "")
	private String jvmArgs;
	@Parameter
	private Properties properties; // System-Properties for the app

	/**** DEPENDENCIES ***/
	@Parameter(defaultValue = "${project.artifacts}") // will only contain scope of compile+runtime
	private Collection<Artifact> artifacts;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("[Capsule] Capsule Version: " + capsuleVersion.toString());
		getLog().info("[Capsule] Target: " + target.toString());
		getLog().info("[Capsule] Final Name: " + finalName);
		getLog().info("[Capsule] Main Class: " + mainClass);
		getLog().info("[Capsule] Extract Capsule: " + extractCapsule);
		getLog().info("[Capsule] Min Java Version: " + minJavaVersion);
		getLog().info("[Capsule] JVM Args: " + jvmArgs);
		if (properties != null) {
			getLog().info("[Capsule] System Properties: ");
			for (final Map.Entry property : properties.entrySet()) getLog().info("\t\t\\--" + property.getKey() + "=" + property.getValue());
		}
		getLog().info("[Capsule] Dependencies: ");
		for (final Artifact artifact : artifacts) getLog().info("\t\t\\--" + artifact);

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
		final JarOutputStream jar = openJar(target, finalName, Type.empty);

		// add manifest (plus Application)
		final Map<String, String> additionalAttributes = new HashMap();
		additionalAttributes.put("Application", mavenProject.getGroupId() + ":" + mavenProject.getArtifactId() + ":" + mavenProject.getVersion());
		deployManifestToJar(jar, additionalAttributes);

		// add Capsule classes
		final Map<String, byte[]> otherCapsuleClasses = getAllCapsuleClasses();
		for (final Map.Entry<String, byte[]> entry : otherCapsuleClasses.entrySet())
			addToJar(entry.getKey(), new ByteArrayInputStream(entry.getValue()), jar);

		IOUtil.close(jar);
	}

	/**
	 * Build the thin version of the capsule (i.e no dependencies). The dependencies will be resolved at runtime.
	 */
	public final void buildThin() throws IOException {
		final JarOutputStream jar = openJar(target, finalName, Type.thin);

		// add manifest (with Dependencies list)
		final Map<String, String> additionalAttributes = new HashMap();
		final StringBuilder dependenciesList = new StringBuilder();
		for (final Artifact artifact : artifacts)
			dependenciesList.append(artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() + " ");
		additionalAttributes.put("Dependencies", dependenciesList.toString());
		additionalAttributes.put("Extract-Capsule", extractCapsule);
		deployManifestToJar(jar, additionalAttributes);

		// add compiled project classes
		final File classesDir = new File(target, "classes");
		Files.walkFileTree(classesDir.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(final Path path, final BasicFileAttributes attrs) throws IOException {
				if (!attrs.isDirectory())
					addToJar(path.toString().substring(path.toString().indexOf("classes/") + 8), new FileInputStream(path.toFile()), jar);
				return FileVisitResult.CONTINUE;
			}
		});

		// add Capsule classes
		final Map<String, byte[]> capsuleClasses = getAllCapsuleClasses();
		for (final Map.Entry<String, byte[]> entry : capsuleClasses.entrySet())
			addToJar(entry.getKey(), new ByteArrayInputStream(entry.getValue()), jar);

		IOUtil.close(jar);
	}

	/**
	 * Build the full version of the capsule which includes the dependencies embedded.
	 */
	public final void buildFull() throws IOException {
		final JarOutputStream jar = openJar(target, finalName, Type.full);

		// add manifest
		deployManifestToJar(jar, null);

		// add main jar
		final File mainJarFile = new File(target, finalName + ".jar");
		addToJar(mainJarFile.getName(), new FileInputStream(mainJarFile), jar);

		// add dependencies
		for (final Artifact artifact : artifacts)
			addToJar(artifact.getFile().getName(), new FileInputStream(artifact.getFile()), jar);

		// add Capsule.class
		addToJar("Capsule.class", new ByteArrayInputStream(getCapsuleClass()), jar);

		IOUtil.close(jar);
	}

	/**** UTILS ****************************************************************/

	private JarOutputStream deployManifestToJar(final JarOutputStream jar, final Map<String, String> additionalAttributes) throws IOException {
		final Manifest manifest = new Manifest();
		final Attributes attributes = manifest.getMainAttributes();
		attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attributes.put(Attributes.Name.MAIN_CLASS, "Capsule");
		attributes.put(new Attributes.Name("Application-Class"), mainClass);
		attributes.put(new Attributes.Name("Min-Java-Version"), minJavaVersion);

		if (properties != null) {
			final StringBuilder propertiesList = new StringBuilder();
			for (final Map.Entry<Object, Object> property : properties.entrySet())
				propertiesList.append(property.getKey() + "=" + property.getValue() + " ");
			attributes.put(new Attributes.Name("System-Properties"), propertiesList.toString());
		}

		if (jvmArgs != null && !jvmArgs.isEmpty())
			attributes.put(new Attributes.Name("JVM-Args"), jvmArgs);

		// additional attributes
		if (additionalAttributes != null)
			for (final Map.Entry<String, String> entry : additionalAttributes.entrySet())
				attributes.put(new Attributes.Name(entry.getKey()), entry.getValue());

		// write to jar
		final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
		manifest.write(dataStream);
		final byte[] bytes = dataStream.toByteArray();
		final ByteArrayInputStream manifestInputStream = new ByteArrayInputStream(bytes);
		return addToJar(JarFile.MANIFEST_NAME, manifestInputStream, jar);
	}

	private byte[] getCapsuleClass() throws IOException {
		final JarInputStream capsuleJarInputStream = new JarInputStream(new FileInputStream(resolveCapsule()));

		JarEntry entry;
		while ((entry = capsuleJarInputStream.getNextJarEntry()) != null) // look for Capsule.class
			if (entry.getName().equals("Capsule.class"))
				return IOUtil.toByteArray(capsuleJarInputStream);
		return null;
	}

	private Map<String, byte[]> getAllCapsuleClasses() throws IOException {
		final JarInputStream capsuleJarInputStream = new JarInputStream(new FileInputStream(resolveCapsule()));

		final Map<String, byte[]> otherClasses = new HashMap();
		JarEntry entry;
		while ((entry = capsuleJarInputStream.getNextJarEntry()) != null) // look for Capsule.class
			if (entry.getName().contains("capsule/") || entry.getName().equals("Capsule.class"))
				otherClasses.put(entry.getName(), IOUtil.toByteArray(capsuleJarInputStream));
		return otherClasses;
	}

	private JarOutputStream addToJar(final String name, final InputStream input, final JarOutputStream jar) throws IOException {
		jar.putNextEntry(new ZipEntry(name));
		IOUtil.copy(input, jar);
		jar.closeEntry();
		return jar;
	}

	private JarOutputStream openJar(final File target, final String projectName, final Type type) throws IOException {
		final File file = new File(target, projectName + "-capsule-" + type.toString() + ".jar");
		getLog().info("[Capsule] Created: " + file.getName());
		return new JarOutputStream(new FileOutputStream(file));
	}

	private File resolveCapsule() throws IOException {
		final ArtifactResult artifactResult;
		try { artifactResult = this.resolve("co.paralleluniverse", "capsule", capsuleVersion); }
		catch (final ArtifactResolutionException e) { throw new IOException("Capsule not found from repos"); }
		return artifactResult.getArtifact().getFile();
	}

	private final ArtifactResult resolve(final String groupId, final String artifactId, final String version) throws ArtifactResolutionException {
		return repoSystem.resolveArtifact(repoSession, new ArtifactRequest(new DefaultArtifact(groupId + ":" + artifactId + ":" + version), remoteRepos, null));
	}

	public static enum Type {
		empty,
		thin,
		full
	}

}
