package capsule;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

public abstract class CapsuleMojo extends AbstractMojo {

	public String LOG_PREFIX = "[Capsule] ";

	public static final String CAPSULE_GROUP = "co.paralleluniverse";
	public static final String DEFAULT_CAPSULE_NAME = "Capsule";
	public static final String DEFAULT_CAPSULE_CLASS = DEFAULT_CAPSULE_NAME + ".class";

	public static final String EXEC_PREFIX = "#!/bin/sh\n\nexec java -jar \"$0\" \"$@\"\n\n";
	public static final String EXEC_TRAMPOLINE_PREFIX = "#!/bin/sh\n\nexec java -Dcapsule.trampoline -jar \"$0\" \"$@\"\n\n";

	public static final String EXEC_PLUGIN_KEY = "org.codehaus.mojo:exec-maven-plugin";

	public enum Type {
		empty,
		thin,
		fat
	}

	@Parameter(defaultValue = "${project}", readonly = true)
	protected MavenProject mavenProject = null;

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

	/**
	 * OPTIONAL VARIABLES
	 */
	@Parameter(property = "capsule.appClass")
	protected String appClass = null;
	@Parameter(property = "capsule.version")
	protected String capsuleVersion = null;
	@Parameter(property = "capsule.output", defaultValue = "${project.build.directory}")
	protected File output = null;
	@Parameter(property = "capsule.customDescriptorEmpty", defaultValue = "-capsule-empty")
	protected String customDescriptorEmpty = null;
	@Parameter(property = "capsule.customDescriptorThin", defaultValue = "-capsule-thin")
	protected String customDescriptorThin = null;
	@Parameter(property = "capsule.customDescriptorFat", defaultValue = "-capsule-fat")
	protected String customDescriptorFat = null;
	@Parameter(property = "capsule.chmod", defaultValue = "false")
	protected String chmod = null;
	@Parameter(property = "capsule.trampoline", defaultValue = "false")
	protected String trampoline = null;
	@Parameter(property = "capsule.types")
	protected String types = null;
	@Parameter(property = "capsule.caplets")
	protected String caplets;
	@Parameter(property = "capsule.execPluginConfig")
	protected String execPluginConfig = null;
	@Parameter
	protected Pair<String, String>[] properties = null; // System-Properties for the app
	@Parameter
	protected Pair<String, String>[] manifest = null; // additional manifest entries
	@Parameter
	protected Mode[] modes = null; // modes for specific properties and manifest entries
	@Parameter
	protected FileSet[] fileSets = null; // assembly style filesets to add to the capsule
	@Parameter
	protected DependencySet[] dependencySets = null; // assembly style dependency sets to add to the capsule

	protected Xpp3Dom execConfig = null;

	// will be loaded when run
	protected final Map<String, File> capletFiles = new HashMap<>();

	/**
	 * DEPENDENCIES
	 */
	@Parameter(defaultValue = "${project.artifacts}") // will only contain scope of compile+runtime
	protected Collection<Artifact> artifacts = null;

	protected File resolvedCapsuleProjectFile = null;

	protected boolean buildEmpty = true, buildThin = true, buildFat = true;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		// check for exec plugin
		if (execPluginConfig != null && mavenProject.getPlugin(EXEC_PLUGIN_KEY) != null) {
			final Plugin plugin = mavenProject.getPlugin(EXEC_PLUGIN_KEY);
			if (execPluginConfig.equals("root")) {
				execConfig = (Xpp3Dom) plugin.getConfiguration();
			} else {
				final List<PluginExecution> executions = plugin.getExecutions();
				for (final PluginExecution execution : executions) {
					if (execution.getId().equals(execPluginConfig)) {
						execConfig = (Xpp3Dom) execution.getConfiguration();
						break;
					}
				}
			}
		}

		// get app class from exec config (but only if app class is not set)
		if (appClass == null && execConfig != null) {
			final Xpp3Dom mainClassElement = execConfig.getChild("mainClass");
			if (mainClassElement != null) appClass = mainClassElement.getValue();
		}

		// fail if no app class
		if (appClass == null)
			throw new MojoFailureException(LOG_PREFIX + " appClass not set (or could not be obtained from the exec plugin mainClass)");

		// check for caplets existence
		if (caplets != null && !caplets.isEmpty()) {
			final StringBuilder capletString = new StringBuilder();
			final File classesDir = new File(this.buildDir, "classes");
			for (final String caplet : this.caplets.split(" ")) {
				try {
					Files.walkFileTree(classesDir.toPath(), new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(final Path path, final BasicFileAttributes attrs) {
							if (!attrs.isDirectory() && path.toString().contains(caplet)) {
								capletFiles.put(caplet, path.toFile());
								return FileVisitResult.TERMINATE;
							}
							return FileVisitResult.CONTINUE;
						}
					});
				} catch (final IOException e) { e.printStackTrace(); }

				if (!capletFiles.containsKey(caplet)) {
					warn("Could not find caplet " + caplet + " class, skipping.");
				} else {
					if (capletString.length() > 0) capletString.append(" ");
					capletString.append(caplet);
				}
			}
			caplets = capletString.toString();
		}

		// check build types
		if (types != null && (types.contains(Type.empty.name()) || types.contains(Type.thin.name()) || types.contains(Type.fat.name()))) {
			buildEmpty = false;
			buildThin = false;
			buildFat = false;
			if (types.contains(Type.empty.name())) buildEmpty = true;
			if (types.contains(Type.thin.name())) buildThin = true;
			if (types.contains(Type.fat.name())) buildFat = true;
		}

		// print types
		final StringBuilder typesString = new StringBuilder();
		if (buildEmpty) typesString.append('[').append(Type.empty.name()).append(']');
		if (buildThin) typesString.append('[').append(Type.thin.name()).append(']');
		if (buildFat) typesString.append('[').append(Type.fat.name()).append(']');
		debug("Types: " + typesString.toString());

		// if no capsule ver specified, find the latest one
		if (capsuleVersion == null) {
			final DefaultArtifact artifact = new DefaultArtifact(CAPSULE_GROUP, "capsule", null, null, "[0,)");
			final VersionRangeRequest request = new VersionRangeRequest().setRepositories(remoteRepos).setArtifact(artifact);
			try {
				final VersionRangeResult result = repoSystem.resolveVersionRange(repoSession, request);
				// get the latest version that is not a snapshot
				for (int i = result.getVersions().size() - 1; i >= 0; i--) {
					final String currentVersion = result.getVersions().get(i).toString();
					if (!currentVersion.contains("SNAPSHOT")) {
						capsuleVersion = result.getVersions().get(i).toString();
						break;
					}
				}
			} catch (VersionRangeResolutionException e) {
				throw new MojoFailureException(e.getMessage());
			}
		}

		// double check output is not in some undesired locations
		final List<String> illegalOutputPaths = Arrays.asList(
			this.buildDir.getPath() + File.separatorChar + "classes",
			this.buildDir.getPath() + File.separatorChar + "classes/"
		);
		if (illegalOutputPaths.contains(this.output.getPath())) {
			this.output = this.buildDir;
			debug("Output was an illegal path, resorting to default build directory.");
		}

		// build path if doesn't exist
		if (!output.exists()) {
			boolean success = output.mkdirs();
			if (!success) throw new MojoFailureException("Failed to build output path");
		}

		info("Using Capsule Version: " + capsuleVersion);
		debug("Output Directory: " + output.toString());



	}

	/**
	 * Build the empty version of the capsule, i.e the the app and its dependencies will be downloaded at runtime.
	 */
	public final List<File> buildEmpty() throws IOException {
		final File jarFile = new File(this.output, getOutputName(Type.empty) + ".jar");

		if (!jarFile.exists()) {
			final JarOutputStream jarStream = new JarOutputStream(new FileOutputStream(jarFile));
			info(jarFile.getName());

			// add manifest (plus Application+Repositories)
			final Map<String, String> additionalAttributes = new HashMap<>();
			additionalAttributes.put("Application", mavenProject.getGroupId() + ":" + mavenProject.getArtifactId() + ":" + mavenProject.getVersion());
			additionalAttributes.put("Repositories", getRepoString());
			addManifest(jarStream, additionalAttributes, Type.empty);

			// add Capsule classes
			final Map<String, byte[]> otherCapsuleClasses = getAllCapsuleClasses();
			for (final Map.Entry<String, byte[]> entry : otherCapsuleClasses.entrySet())
				addToJar(entry.getKey(), new ByteArrayInputStream(entry.getValue()), jarStream);

			// add custom capsule class (if exists)
			addCapletClasses(jarStream);

			// add some files and folders to the capsule
			addFileSets(jarStream);
			addDependencySets(jarStream);

			IOUtil.close(jarStream);
		} else {
			debug("EXISTS - " + jarFile.getName());
		}

		final List<File> jars = createExecCopy(jarFile);

		jars.add(jarFile);
		return jars;
	}

	/**
	 * Build the thin version of the capsule (i.e no dependencies). The dependencies will be resolved at runtime.
	 */
	public final List<File> buildThin() throws IOException {
		final File jarFile = new File(this.output, getOutputName(Type.thin) + ".jar");

		if (!jarFile.exists()) {
			final JarOutputStream jarStream = new JarOutputStream(new FileOutputStream(jarFile));
			info(jarFile.getName());

			// add manifest (with Dependencies+Repositories list)
			final Map<String, String> additionalAttributes = new HashMap<>();
			additionalAttributes.put("Dependencies", getDependencyString());
			additionalAttributes.put("Repositories", getRepoString());
			addManifest(jarStream, additionalAttributes, Type.thin);

			// add compiled project classes
			addCompiledProjectClasses(jarStream);

			// add Capsule classes
			final Map<String, byte[]> capsuleClasses = getAllCapsuleClasses();
			for (final Map.Entry<String, byte[]> entry : capsuleClasses.entrySet())
				addToJar(entry.getKey(), new ByteArrayInputStream(entry.getValue()), jarStream);

			// add custom capsule class (if exists)
			addCapletClasses(jarStream);

			// add some files and folders to the capsule
			addFileSets(jarStream);
			addDependencySets(jarStream);

			IOUtil.close(jarStream);
		} else {
			debug("EXISTS - " + jarFile.getName());
		}

		final List<File> jars = createExecCopy(jarFile);

		jars.add(jarFile);
		return jars;
	}

	/**
	 * Build the fat version of the capsule which includes the dependencies embedded.
	 */
	public final List<File> buildFat() throws IOException {
		final File jarFile = new File(this.output, getOutputName(Type.fat) + ".jar");

		if (!jarFile.exists()) {
			final JarOutputStream jarStream = new JarOutputStream(new FileOutputStream(jarFile));
			info(jarFile.getName());

			// add manifest
			addManifest(jarStream, null, Type.fat);

			// add main jar
			try {
				final File mainJarFile = new File(this.buildDir, this.finalName + ".jar");
				addToJar(mainJarFile.getName(), new FileInputStream(mainJarFile), jarStream);
			} catch (final FileNotFoundException e) { // if project jar wasn't built (perhaps the mvn package wasn't run, and only the mvn compile was run)
				// add compiled project classes instead
				warn("Couldn't add main jar file to fat capsule, adding the project classes directly instead.");
				this.addCompiledProjectClasses(jarStream);
			}

			// add dependencies
			for (final Artifact artifact : artifacts) {
				if (artifact.getFile() == null) {
					warn("Dependency[" + artifact + "] file not found, thus will not be added to fat jar.");
				} else {
					addToJar(artifact.getFile().getName(), new FileInputStream(artifact.getFile()), jarStream);
				}
			}

			// add Capsule.class
			addToJar(DEFAULT_CAPSULE_CLASS, new ByteArrayInputStream(getCapsuleClass()), jarStream);

			// add custom capsule class (if exists)
			addCapletClasses(jarStream);

			// add some files and folders to the capsule
			addFileSets(jarStream);
			addDependencySets(jarStream);

			IOUtil.close(jarStream);
		} else {
			debug("EXISTS - " + jarFile.getName());
		}

		final List<File> jars = createExecCopy(jarFile);

		jars.add(jarFile);
		return jars;
	}

	/**
	 * UTILS
	 */

	protected JarOutputStream addManifest(final JarOutputStream jar, final Map<String, String> additionalAttributes, final Type type) throws IOException {
		final Manifest manifestBuild = new Manifest();
		final Attributes mainAttributes = manifestBuild.getMainAttributes();
		mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		mainAttributes.put(Attributes.Name.MAIN_CLASS, DEFAULT_CAPSULE_NAME);
		mainAttributes.put(new Attributes.Name("Application-Class"), this.appClass);
		mainAttributes.put(new Attributes.Name("Application-Name"), this.getOutputName(type));

		// add properties
		final String propertiesString = getSystemPropertiesString();
		if (propertiesString != null) mainAttributes.put(new Attributes.Name("System-Properties"), propertiesString);

		// get arguments from exec plugin (if exist)
		if (execConfig != null) {
			final Xpp3Dom argsElement = execConfig.getChild("arguments");
			if (argsElement != null) {
				final Xpp3Dom[] argsElements = argsElement.getChildren();
				if (argsElements != null && argsElements.length > 0) {
					final StringBuilder argsList = new StringBuilder();
					for (final Xpp3Dom arg : argsElements) {
						if (arg != null && arg.getValue() != null)
							argsList.append(arg.getValue().replace(" ", "")).append(" ");
					}
					mainAttributes.put(new Attributes.Name("JVM-Args"), argsList.toString());
				}
			}
		}

		// additional attributes
		if (additionalAttributes != null)
			for (final Map.Entry<String, String> entry : additionalAttributes.entrySet())
				mainAttributes.put(new Attributes.Name(entry.getKey()), entry.getValue());

		// caplets
		if (this.caplets != null && !this.caplets.isEmpty())
			mainAttributes.put(new Attributes.Name("Caplets"), this.caplets);

		// custom user defined manifest entries (will override any before)
		if (this.manifest != null)
			for (final Pair<String, String> entry : this.manifest)
				mainAttributes.put(new Attributes.Name(entry.key), entry.value);

		// mode sections
		if (this.modes != null) {
			for (final Mode mode : this.modes) {
				if (mode.name == null) getLog().warn(LOG_PREFIX + "Mode defined without name, ignoring.");
				else {
					final Attributes modeAttributes = new Attributes();
					// add manifest entries to the mode section (these entries will override the manifests' main entries if mode is selected at runtime)
					if (mode.manifest != null) {
						for (final Pair<String, String> entry : mode.manifest)
							modeAttributes.put(new Attributes.Name(entry.key), entry.value);
					}
					// add properties to the mode, this set will override all properties of the previous set.
					if (mode.properties != null) {
						final StringBuilder modePropertiesList = new StringBuilder();
						for (final Pair property : mode.properties)
							if (property.key != null && property.value != null) {
								modePropertiesList.append(property.key).append("=").append(property.value).append(" ");
							}
						if (modePropertiesList.length() > 0) modeAttributes.put(new Attributes.Name("System-Properties"), modePropertiesList.toString());
					}
					// finally add the mode's properties and manifest entries to its own section.
					if (!modeAttributes.isEmpty()) manifestBuild.getEntries().put(mode.name, modeAttributes);
				}
			}
		}

		// write to jar
		final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
		manifestBuild.write(dataStream);
		final byte[] bytes = dataStream.toByteArray();
		final ByteArrayInputStream manifestInputStream = new ByteArrayInputStream(bytes);

		printManifest(manifestBuild);

		return addToJar(JarFile.MANIFEST_NAME, manifestInputStream, jar);
	}

	protected void addCompiledProjectClasses(final JarOutputStream jarStream) throws IOException {
		final File classesDir = new File(this.buildDir, "classes");
		Files.walkFileTree(classesDir.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(final Path path, final BasicFileAttributes attrs) throws IOException {
				if (!attrs.isDirectory() && !path.endsWith(".DS_Store") && !path.endsWith("MANIFEST.MF")) {
					addToJar(path.toString().substring(path.toString().indexOf("classes") + 8), new FileInputStream(path.toFile()), jarStream);
					getLog().debug("Adding Compile Project Class to Capsule: [" + path.toFile().getPath() + "]");
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	protected void addCapletClasses(final JarOutputStream jarStream) throws IOException {
		if (caplets != null && !caplets.isEmpty()) {
			for (final Map.Entry<String, File> caplet : this.capletFiles.entrySet()) {
				final String path = caplet.getValue().getPath();
				addToJar(path.substring(path.indexOf("classes") + 8), new FileInputStream(caplet.getValue()), jarStream);
			}
		}
	}

	protected byte[] getCapsuleClass() throws IOException {
		final JarInputStream capsuleJarInputStream = new JarInputStream(new FileInputStream(resolveCapsule()));

		JarEntry entry;
		while ((entry = capsuleJarInputStream.getNextJarEntry()) != null) // look for Capsule.class
			if (entry.getName().equals(DEFAULT_CAPSULE_CLASS))
				return IOUtil.toByteArray(capsuleJarInputStream);
		return null;
	}

	protected Map<String, byte[]> getAllCapsuleClasses() throws IOException {
		final JarInputStream capsuleJarInputStream = new JarInputStream(new FileInputStream(resolveCapsule()));

		final Map<String, byte[]> otherClasses = new HashMap<>();
		JarEntry entry;
		while ((entry = capsuleJarInputStream.getNextJarEntry()) != null) // look for Capsule.class
			if (entry.getName().contains("capsule") || entry.getName().equals(DEFAULT_CAPSULE_CLASS))
				otherClasses.put(entry.getName(), IOUtil.toByteArray(capsuleJarInputStream));

		return otherClasses;
	}

	protected void addFileSets(final JarOutputStream jar) throws IOException {
		if (fileSets == null) return;

		for (final FileSet fileSet : fileSets) {
			if (fileSet.directory != null && !fileSet.directory.isEmpty()) {
				final File directory = new File(fileSet.directory);

				// warn & skip if not directory
				if (!directory.isDirectory()) {
					warn("Attempted to include file from non-directory [" + directory.getAbsolutePath() + "], skipping...");
					continue;
				}

				final String outputDirectory = addDirectoryToJar(jar, fileSet.outputDirectory);

				for (final String include : fileSet.includes) {
					final FileInputStream fin = new FileInputStream(new File(directory, include));
					addToJar(outputDirectory + include, fin, jar);
				}
			}
		}
	}

	protected void addDependencySets(final JarOutputStream jar) throws IOException {
		if (dependencySets == null) return;

		for (final DependencySet dependencySet : dependencySets) {
			for (final Artifact artifact : artifacts) {
				if (dependencySet.groupId.equals(artifact.getGroupId()) && dependencySet.artifactId.equals(artifact.getArtifactId())) {
					if (dependencySet.version == null || dependencySet.version.equals(artifact.getVersion())) {
						if (artifact.getFile() == null) {
							warn("Could not resolve dependency: " + dependencySet.groupId + ":" + dependencySet.artifactId + ":" + dependencySet.version);
							continue;
						}

						final JarFile jarFile = new JarFile(artifact.getFile());

						final String outputDirectory = addDirectoryToJar(jar, dependencySet.outputDirectory);

						for (final String include : dependencySet.includes) {
							final ZipEntry entry = jarFile.getEntry(include);
							if (entry != null) {
								info("Adding " + include + " from " + artifact.getFile());
								addToJar(outputDirectory + include, jarFile.getInputStream(entry), jar);
							} else {
								warn(include + " not found in " + artifact.getFile());
							}
						}
					}
				}
			}
		}
	}

	protected String addDirectoryToJar(final JarOutputStream jar, final String outputDirectory) throws IOException {
		final String formattedOutputDirectory = formatDirectoryPath(outputDirectory);
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
			jar.putNextEntry(new ZipEntry(name));
			IOUtil.copy(input, jar);
			jar.closeEntry();
		} catch (final ZipException ignore) {} // ignore duplicate entries and other errors
		IOUtil.close(input);
		return jar;
	}

	protected Pair<File, JarOutputStream> openJar(final Type type) throws IOException {
		final File file = new File(this.output, getOutputName(type) + ".jar");
		return new Pair<>(file, new JarOutputStream(new FileOutputStream(file)));
	}

	protected File resolveCapsule() throws IOException {
		if (this.resolvedCapsuleProjectFile == null) {
			final ArtifactResult artifactResult;
			try {
				artifactResult = this.resolve(CAPSULE_GROUP, "capsule", capsuleVersion);
			} catch (final ArtifactResolutionException e) {
				throw new IOException("Capsule not found from repos");
			}
			this.resolvedCapsuleProjectFile = artifactResult.getArtifact().getFile();
		}
		return this.resolvedCapsuleProjectFile;
	}

	protected ArtifactResult resolve(final String groupId, final String artifactId, final String version) throws ArtifactResolutionException {
		String coords = groupId + ":" + artifactId;
		if (version != null && !version.isEmpty()) coords += ":" + version;
		return repoSystem.resolveArtifact(repoSession, new ArtifactRequest(new DefaultArtifact(coords), remoteRepos, null));
	}

	protected String getDependencyCoordsWithExclusions(final Dependency dependency) {
		final StringBuilder coords = new StringBuilder(dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion());
		if (dependency.getExclusions().size() > 0) {
			final StringBuilder exclusionsList = new StringBuilder();
			for (int i = 0; i < dependency.getExclusions().size(); i++) {
				final Exclusion exclusion = dependency.getExclusions().get(i);
				if (i > 0) exclusionsList.append(",");
				exclusionsList.append(exclusion.getGroupId()).append(":").append(exclusion.getArtifactId());
			}
			coords.append("(").append(exclusionsList.toString()).append(")");
		}
		return coords.toString();
	}

	protected String getRepoString() {
		final StringBuilder repoList = new StringBuilder();
		for (final RemoteRepository repository : this.remoteRepos)
			repoList.append(repository.getId()).append("(").append(repository.getUrl()).append(") ");
		return repoList.toString();
	}

	protected String getDependencyString() {
		final StringBuilder dependenciesList = new StringBuilder();
		for (final Dependency dependency : (List<Dependency>) mavenProject.getDependencies())
			if (dependency.getScope().equals("compile") || dependency.getScope().equals("runtime"))
				dependenciesList.append(getDependencyCoordsWithExclusions(dependency)).append(" ");
		return dependenciesList.toString();
	}

	protected String getSystemPropertiesString() {
		StringBuilder propertiesList = null;
		if (this.properties != null) {
			propertiesList = new StringBuilder();
			for (final Pair property : this.properties)
				if (property.key != null && property.value != null)
					propertiesList.append(property.key).append("=").append(property.value).append(" ");
		} else if (execConfig != null) { // else try and find properties in the exec plugin
			propertiesList = new StringBuilder();
			final Xpp3Dom propertiesElement = execConfig.getChild("systemProperties");
			if (propertiesElement != null) {
				final Xpp3Dom[] propertiesElements = propertiesElement.getChildren();
				if (propertiesElements != null && propertiesElements.length > 0) {
					for (final Xpp3Dom propertyElement : propertiesElements) {
						final Xpp3Dom key = propertyElement.getChild("key");
						final Xpp3Dom value = propertyElement.getChild("value");
						if (key != null && key.getValue() != null && value != null && value.getValue() != null)
							propertiesList.append(key.getValue()).append("=").append(value.getValue()).append(" ");
					}
				}
			}
		}
		return propertiesList == null ? null : propertiesList.toString();
	}

	protected List<File> createExecCopy(final File jar) throws IOException {
		final List<File> jars = new ArrayList<File>();
		if (this.chmod.equals("true") || this.chmod.equals("1"))
			jars.add(createExecCopyProcess(jar, EXEC_PREFIX, ".x"));
		if (this.trampoline.equals("true") || this.trampoline.equals("1"))
			jars.add(createExecCopyProcess(jar, EXEC_TRAMPOLINE_PREFIX, ".tx"));

		return jars;
	}

	protected File createExecCopyProcess(final File jar, final String prefix, final String extension) throws IOException {
		final File x = new File(jar.getPath().replace(".jar", extension));
		if (x.exists()) {
			debug("EXISTS - " + x.getName());
			return x;
		}

		FileOutputStream out = null;
		FileInputStream in = null;
		try {
			out = new FileOutputStream(x);
			in = new FileInputStream(jar);
			out.write((prefix).getBytes("ASCII"));
			Files.copy(jar.toPath(), out);
			out.flush();
			Runtime.getRuntime().exec("chmod +x " + x.getAbsolutePath());
		} finally {
			IOUtil.close(in);
			IOUtil.close(out);
			info(x.getName());
			return x;
		}
	}

	protected String getOutputName(final Type type) {
		String outputName = this.finalName;
		if (type == Type.empty) outputName += this.customDescriptorEmpty;
		else if (type == Type.thin) outputName += this.customDescriptorThin;
		else if (type == Type.fat) outputName += this.customDescriptorFat;
		return outputName;
	}

	public static class Pair<K, V> {
		public K key;
		public V value;
		public Pair() {}
		public Pair(final K key, final V value) {
			this.key = key;
			this.value = value;
		}
	}

	public static class Mode {
		private String name = null;
		private Pair<String, String>[] properties = null;
		private Pair<String, String>[] manifest = null;
	}

	public static class DependencySet {
		public String groupId;
		public String artifactId;
		public String version;
		public String outputDirectory;
		public String[] includes;
	}

	public static class FileSet {
		public String directory;
		public String outputDirectory;
		public String[] includes;
	}

	protected String formatDirectoryPath(final String directoryPath) {
		if (directoryPath != null && !directoryPath.isEmpty()) {
			if (!directoryPath.endsWith("/")) {
				return directoryPath + File.separatorChar;
			} else {
				return directoryPath;
			}
		} else {
			return "";
		}
	}

	protected void printManifest(final Manifest manifest) {
		debug("Manifest:");
		for (final Map.Entry<Object, Object> attr : manifest.getMainAttributes().entrySet()) {
			debug("\t" + attr.getKey().toString() + ": " + attr.getValue().toString());
		}
		for (final Map.Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
			debug("Name:" + entry.getKey());
			for (final Map.Entry<Object, Object> attr : entry.getValue().entrySet()) {
				debug("\t" + attr.getKey().toString() + ": " + attr.getValue().toString());
			}
		}
	}

	protected String installPath() {
		return repoSession.getLocalRepository().getBasedir().toString() + File.separatorChar + mavenProject.getGroupId().replace('.', '/') + File
			.separatorChar + mavenProject.getName() + File.separatorChar + mavenProject.getVersion() + File.separatorChar;
	}

	protected void debug(final String message) { getLog().debug(LOG_PREFIX + message); }
	protected void info(final String message) { getLog().info(LOG_PREFIX + message); }
	protected void warn(final String message) { getLog().warn(LOG_PREFIX + message); }
}
