package com.github.christokios;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.codehaus.plexus.util.IOUtil;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.jar.*;
import java.util.zip.ZipEntry;

@Mojo(name = "capsule", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyCollection = ResolutionScope.RUNTIME)
public class CapsuleMojo extends AbstractMojo {

	/**** AETHER REPO LINK ***/
	@Component
	protected RepositorySystem repoSystem;
	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
	protected RepositorySystemSession repoSession;
	@Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
	protected List<RemoteRepository> remoteRepos;

	/**** CUSTOMIZABLE VARIABLES ***/
	@Parameter(property = "capsule.target", defaultValue = "${project.build.directory}")
	private File target;
	@Parameter(property = "capsule.finalName", defaultValue = "${project.build.finalName}")
	private String finalName;
	@Parameter(property = "capsule.mainClass", required = true)
	private String mainClass;
	@Parameter(property = "capsule.version", defaultValue = "0.6.0-SNAPSHOT")
	private String capsuleVersion;

	/**** DEPENDENCIES ***/
	@Parameter(defaultValue = "${project.artifacts}") // will only contain scope of compile+runtime
	private Collection<Artifact> artifacts;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("[Capsule] Capsule Version: " + capsuleVersion.toString());
		getLog().info("[Capsule] Target: " + target.toString());
		getLog().info("[Capsule] Final Name: " + finalName);
		getLog().info("[Capsule] Main Class: " + mainClass);
		getLog().info("[Capsule] Dependencies: ");
		for (final Artifact artifact : artifacts) getLog().info("\t\t\\--" + artifact);

		try {
			full();
		} catch (final IOException e) {
			e.printStackTrace();
			throw new MojoFailureException(e.getMessage());
		}

	}

	public void full() throws IOException {
		final JarOutputStream jar = openJar(target, finalName, Type.full);

		final Manifest manifest = new Manifest();
		final Attributes attributes = manifest.getMainAttributes();
		attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attributes.put(Attributes.Name.MAIN_CLASS, "Capsule");
		attributes.put(new Attributes.Name("Application-Class"), mainClass);

		// write to a temp byte array to conform to API
		final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
		manifest.write(dataStream);

		// return input stream based on byte array
		final byte[] bytes = dataStream.toByteArray();
		final ByteArrayInputStream manifestInputStream = new ByteArrayInputStream(bytes);

		addToJar(JarFile.MANIFEST_NAME, manifestInputStream, jar);

		// Add main jar
		final File mainJarFile = new File(target, finalName + ".jar");
		addToJar(mainJarFile.getName(), new FileInputStream(mainJarFile), jar);

		// Add dependencies
		for (final Artifact artifact : artifacts) {
			final File file = artifact.getFile();
			addToJar(file.getName(), new FileInputStream(file), jar);
		}

		// Add Capsule.class
		final ByteArrayInputStream capsuleClass = new ByteArrayInputStream(getCapsuleClass());
		addToJar("Capsule.class", capsuleClass, jar);

		IOUtil.close(jar);
	}

	/**** UTILS ****************************************************************/

	private byte[] getCapsuleClass() throws IOException {
		final ArtifactResult artifactResult;
		try { artifactResult = this.resolve("co.paralleluniverse", "capsule", capsuleVersion); }
		catch (final ArtifactResolutionException e) { throw new IOException("Capsule not found from repos"); }

		final JarInputStream capsuleJarInputStream = new JarInputStream(new FileInputStream(artifactResult.getArtifact().getFile()));

		JarEntry entry;
		while ((entry = capsuleJarInputStream.getNextJarEntry()) != null) // look for Capsule.class
			if (entry.getName().equals("Capsule.class"))
				return IOUtil.toByteArray(capsuleJarInputStream);
		return null;
	}

	protected final ArtifactResult resolve(final String groupId, final String artifactId, final String version) throws ArtifactResolutionException {
		return repoSystem.resolveArtifact(repoSession, new ArtifactRequest(new DefaultArtifact(groupId + ":" + artifactId + ":" + version), remoteRepos, null));
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

	public static enum Type {
		empty,
		thin,
		full
	}

}
