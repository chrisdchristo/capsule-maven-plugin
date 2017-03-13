package com.github.chrisdchristo.capsule;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;

/**
 * Mojo to generate a Capsule jar
 */
@org.apache.maven.plugins.annotations.Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyCollection = ResolutionScope.TEST, requiresDependencyResolution
		= ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class CapsuleMojo extends Mojo {

	public final String pluginKey() {
		return "com.github.chrisdchristo:capsule-maven-plugin";
	}

	public final String logPrefix() {
		return "[CapsuleMavenPlugin] ";
	}

	private static final String DEFAULT_CAPSULE_VERSION = "1.0.3";
	private static final String DEFAULT_CAPSULE_MAVEN_VERSION = "1.0.3";

	private static final String CAPSULE_GROUP = "co.paralleluniverse";
	private static final String DEFAULT_CAPSULE_NAME = "Capsule";
	private static final String DEFAULT_CAPSULE_CLASS = DEFAULT_CAPSULE_NAME + ".class";
	private static final String DEFAULT_CAPSULE_MAVEN_NAME = "MavenCapsule";
	private static final String DEFAULT_CAPSULE_MAVEN_CLASS = "MavenCapsule.class";

	private static final String EXEC_PREFIX = "#!/bin/sh\n\nexec java -jar \"$0\" \"$@\"\n\n";
	private static final String EXEC_TRAMPOLINE_PREFIX = "#!/bin/sh\n\nexec java -Dcapsule.trampoline -jar \"$0\" \"$@\"\n\n";

	private static final String EXEC_PLUGIN_KEY = "org.codehaus.mojo:exec-maven-plugin";

	/**
	 * OPTIONAL VARIABLES
	 */
	@Parameter(property = "capsule.outputDir", defaultValue = "${project.build.directory}")
	private File outputDir = null;
	@Parameter(property = "capsule.version")
	private String capsuleVersion = DEFAULT_CAPSULE_VERSION;
	@Parameter(property = "capsule.maven.version")
	private String capsuleMavenVersion = DEFAULT_CAPSULE_MAVEN_VERSION;
	@Parameter(property = "capsule.appClass")
	private String appClass = null;
	@Parameter(property = "capsule.caplets")
	private String caplets;
	@Parameter(property = "capsule.type")
	private Type type = null;
	@Parameter(property = "capsule.chmod")
	private boolean chmod = false;
	@Parameter(property = "capsule.trampoline")
	private boolean trampoline = false;
	@Parameter(property = "capsule.setManifestRepos")
	private boolean setManifestRepos = false;

	@Parameter(property = "capsule.includeApp")
	private boolean includeApp = true;
	@Parameter(property = "capsule.includeAppDep")
	private boolean includeAppDep = false;
	@Parameter(property = "capsule.includePluginDep")
	private boolean includePluginDep = false;
	@Parameter(property = "capsule.includeTransitiveDep")
	private boolean includeTransitiveDep = false;
	@Parameter(property = "capsule.includeCompileDep")
	private boolean includeCompileDep = false;
	@Parameter(property = "capsule.includeRuntimeDep")
	private boolean includeRuntimeDep = false;
	@Parameter(property = "capsule.includeProvidedDep")
	private boolean includeProvidedDep = false;
	@Parameter(property = "capsule.includeSystemDep")
	private boolean includeSystemDep = false;
	@Parameter(property = "capsule.includeTestDep")
	private boolean includeTestDep = false;
	@Parameter(property = "capsule.includeOptionalDep")
	private boolean includeOptionalDep = false;

	@Parameter(property = "capsule.resolveApp")
	private boolean resolveApp = false;
	@Parameter(property = "capsule.resolveAppDep")
	private boolean resolveAppDep = false;
	@Parameter(property = "capsule.resolvePluginDep")
	private boolean resolvePluginDep = false;
	@Parameter(property = "capsule.resolveTransitiveDep")
	private boolean resolveTransitiveDep = false;
	@Parameter(property = "capsule.resolveCompileDep")
	private boolean resolveCompileDep = false;
	@Parameter(property = "capsule.resolveRuntimeDep")
	private boolean resolveRuntimeDep = false;
	@Parameter(property = "capsule.resolveProvidedDep")
	private boolean resolveProvidedDep = false;
	@Parameter(property = "capsule.resolveSystemDep")
	private boolean resolveSystemDep = false;
	@Parameter(property = "capsule.resolveTestDep")
	private boolean resolveTestDep = false;
	@Parameter(property = "capsule.resolveOptionalDep")
	private boolean resolveOptionalDep = false;

	@Parameter(property = "capsule.execPluginConfig")
	private String execPluginConfig = null;
	@Parameter(property = "capsule.fileName")
	private String fileName = null;
	@Parameter(property = "capsule.fileDesc")
	private String fileDesc = "-capsule";
	@Parameter
	private Pair<String, String>[] properties = null; // System-Properties for the app
	@Parameter
	private Pair<String, String>[] manifest = null; // additional manifest entries
	@Parameter
	private Mode[] modes = null; // modes for specific properties and manifest entries
	@Parameter
	private FileSet[] fileSets = null; // assembly style filesets to add to the capsule
	@Parameter
	private DependencySet[] dependencySets = null; // assembly style dependency sets to add to the capsule

	// will be loaded when run
	private Map<String, File> capletFiles = new HashMap<>();
	private Xpp3Dom execConfig = null;
	private File resolvedCapsuleProjectFile = null;
	private File resolvedCapsuleMavenProjectFile = null;
	private String outputName;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		// check for type (this overrides custom behaviour)
		if (type == Type.empty) {
			includeApp = false;
			includeAppDep = false;
			includePluginDep = false;
			includeTransitiveDep = false;
			includeCompileDep = false;
			includeRuntimeDep = false;
			includeProvidedDep = false;
			includeSystemDep = false;
			includeTestDep = false;
			includeOptionalDep = false;
			resolveApp = true;
			resolveAppDep = true;
			resolvePluginDep = true;
			resolveTransitiveDep = true;
			resolveCompileDep = true;
			resolveRuntimeDep = true;
			resolveProvidedDep = false;
			resolveSystemDep = false;
			resolveTestDep = false;
			resolveOptionalDep = false;
		} else if (type == Type.thin) {
			includeApp = true;
			includeAppDep = false;
			includePluginDep = false;
			includeTransitiveDep = false;
			includeCompileDep = false;
			includeRuntimeDep = false;
			includeProvidedDep = false;
			includeSystemDep = false;
			includeTestDep = false;
			includeOptionalDep = false;
			resolveApp = false;
			resolveAppDep = true;
			resolvePluginDep = true;
			resolveTransitiveDep = true;
			resolveCompileDep = true;
			resolveRuntimeDep = true;
			resolveProvidedDep = false;
			resolveSystemDep = false;
			resolveTestDep = false;
			resolveOptionalDep = false;
		} else if (type == Type.fat) {
			includeApp = true;
			includeAppDep = true;
			includePluginDep = true;
			includeTransitiveDep = true;
			includeCompileDep = true;
			includeRuntimeDep = true;
			includeProvidedDep = false;
			includeSystemDep = false;
			includeTestDep = false;
			includeOptionalDep = false;
			resolveApp = false;
			resolveAppDep = false;
			resolvePluginDep = false;
			resolveTransitiveDep = false;
			resolveCompileDep = false;
			resolveRuntimeDep = false;
			resolveProvidedDep = false;
			resolveSystemDep = false;
			resolveTestDep = false;
			resolveOptionalDep = false;
		}

		// check for exec plugin
		if (execPluginConfig != null && project.getPlugin(EXEC_PLUGIN_KEY) != null) {
			final Plugin plugin = project.getPlugin(EXEC_PLUGIN_KEY);
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
			throw new MojoFailureException(logPrefix() + " appClass not set (or could not be obtained from the exec plugin mainClass)");

		// resolve outputDir name (the file name of the capsule jar)
		this.outputName = this.fileName != null ? this.fileName : this.finalName;
		if (this.fileDesc != null) outputName += this.fileDesc;

		// check for caplets existence
		if (this.caplets == null) this.caplets = "";
		if (!caplets.isEmpty()) {
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
				} catch (final IOException e) {
					e.printStackTrace();
				}

				if (!capletFiles.containsKey(caplet))
					if (!caplet.contains(":")) // not from repo
						warn("Could not find caplet " + caplet + " class, skipping.");

				if (capletString.length() > 0) capletString.append(" ");
				capletString.append(caplet);
			}
			caplets = capletString.toString();
		}

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

		// double check outputDir is not in some undesired locations
		final List<String> illegalOutputPaths = Arrays.asList(
				this.buildDir.getPath() + File.separatorChar + "classes",
				this.buildDir.getPath() + File.separatorChar + "classes/"
		);
		if (illegalOutputPaths.contains(this.outputDir.getPath())) {
			this.outputDir = this.buildDir;
			debug("Output was an illegal path, resorting to default build directory.");
		}

		// build path if doesn't exist
		if (!outputDir.exists()) {
			boolean success = outputDir.mkdirs();
			if (!success) throw new MojoFailureException("Failed to build outputDir path");
		}

		info("[Capsule Version]: " + capsuleVersion);
		info("[Output Directory]: " + outputDir.toString());
		info("[Build Info]: " + buildInfoString());

		try {
			build();
		} catch (final IOException e) {
			e.printStackTrace();
			throw new MojoFailureException(e.getMessage());
		}
	}

	/**
	 * Build the capsule jar based on the parameters
	 */
	public void build() throws IOException {
		final File jarFile = new File(this.outputDir, this.outputName + ".jar");

		if (jarFile.exists()) {
			info("EXISTS - " + jarFile.getName() + " (WILL OVERWRITE)");
			final boolean deleteResult = jarFile.delete();
			if (!deleteResult) {
				warn("FAILED TO DELETE - " + jarFile.getName());
			}
		}

		final JarOutputStream jarStream = new JarOutputStream(new FileOutputStream(jarFile));
		info("[Capsule Jar File]: " + jarFile.getName());

		// add manifest entries
		addManifest(jarStream);

		// add Capsule.class
		addCapsuleClass(jarStream);

		// add caplets - i.e custom capsule classes (if exists)
		addCapletClasses(jarStream);

		// add CapsuleMaven classes (if we need to do any resolving on launch)
		addMavenCapletClasses(jarStream);

		// add the app jar
		addApp(jarStream);

		// add the dependencies as embedded jars
		addDependencies(jarStream);

		// add some files and folders to the capsule from filesets and dependencysets
		addFileSets(jarStream);
		addDependencySets(jarStream);

		IOUtil.close(jarStream);

		// build the chmod version of the capsule
		addChmodCopy(jarFile);

		// build the trampoline version of the capsule
		addTrampolineCopy(jarFile);

		// attach the capsule as a maven artifact
		info("[Maven Artifact]: Attached capsule artifact to maven (" + jarFile.getName() + ").");
		helper.attachArtifact(project, jarFile, "capsule");
	}

	// BUILD PROCESS

	private void addManifest(final JarOutputStream jar) throws IOException {
		final Manifest manifestBuild = new Manifest();
		final Attributes mainAttributes = manifestBuild.getMainAttributes();
		mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		mainAttributes.put(Attributes.Name.MAIN_CLASS, DEFAULT_CAPSULE_NAME);
		mainAttributes.put(new Attributes.Name("Application-Class"), this.appClass);
		mainAttributes.put(new Attributes.Name("Application-Name"), this.outputName);
		mainAttributes.put(new Attributes.Name("Premain-Class"), DEFAULT_CAPSULE_NAME);
		mainAttributes.put(new Attributes.Name("Build-Info"), buildInfoString());
		final String artifactsString = artifactString();
		if (!artifactsString.isEmpty())
			mainAttributes.put(new Attributes.Name("Embedded-Artifacts"), artifactsString);
		final String dependencyString = dependencyString();
		if (!dependencyString.isEmpty())
			mainAttributes.put(new Attributes.Name("Dependencies"), dependencyString);

		final String repoString = repoString().trim();
		if (!repoString.isEmpty() && setManifestRepos)
			mainAttributes.put(new Attributes.Name("Repositories"), repoString);

		// add MavenCapsule caplet (if needed) & others specified by user
		if (resolveApp || resolveCompileDep || resolveRuntimeDep || resolveProvidedDep || resolveSystemDep || resolveTestDep)
			mainAttributes.put(new Attributes.Name("Caplets"), (DEFAULT_CAPSULE_MAVEN_NAME + " " + this.caplets).trim());
		else if (this.caplets != null && !this.caplets.isEmpty())
			mainAttributes.put(new Attributes.Name("Caplets"), this.caplets.trim());

		// add properties
		final String propertiesString = systemPropertiesString();
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
					mainAttributes.put(new Attributes.Name("Args"), argsList.toString());
				}
			}
		}

		// custom user defined manifest entries (will override any before)
		if (this.manifest != null)
			for (final Pair<String, String> entry : this.manifest)
				mainAttributes.put(new Attributes.Name(entry.key), entry.value);

		// mode sections
		if (this.modes != null) {
			for (final Mode mode : this.modes) {
				if (mode.name == null) warn("Mode defined without name, ignoring.");
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
						if (modePropertiesList.length() > 0)
							modeAttributes.put(new Attributes.Name("System-Properties"), modePropertiesList.toString());
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

		addToJar(JarFile.MANIFEST_NAME, manifestInputStream, jar);
	}

	private void addCapsuleClass(final JarOutputStream jar) throws IOException {
		final JarInputStream capsuleJarInputStream = new JarInputStream(new FileInputStream(resolveCapsule()));

		JarEntry entry;
		while ((entry = capsuleJarInputStream.getNextJarEntry()) != null) // look for Capsule.class
			if (entry.getName().equals(DEFAULT_CAPSULE_CLASS))
				addToJar(DEFAULT_CAPSULE_CLASS, new ByteArrayInputStream(IOUtil.toByteArray(capsuleJarInputStream)), jar);
	}

	private void addCapletClasses(final JarOutputStream jar) throws IOException {
		if (caplets != null && !caplets.isEmpty()) {
			for (final Map.Entry<String, File> caplet : this.capletFiles.entrySet()) {
				final String path = caplet.getValue().getPath();
				addToJar(path.substring(path.indexOf("classes") + 8), new FileInputStream(caplet.getValue()), jar);
				info("\t[Caplet] Embedded Caplet class " + caplet.getKey() + " from " + caplet.getValue());
			}
		}
	}

	private void addMavenCapletClasses(final JarOutputStream jar) throws IOException {
		if (resolveApp || resolveCompileDep || resolveRuntimeDep || resolveProvidedDep || resolveSystemDep || resolveTestDep) {

			// get capsule maven classes
			final JarInputStream capsuleJarInputStream = new JarInputStream(new FileInputStream(resolveCapsuleMaven()));

			JarEntry entry;
			while ((entry = capsuleJarInputStream.getNextJarEntry()) != null) {
				if (entry.getName().contains("capsule") || entry.getName().equals(DEFAULT_CAPSULE_MAVEN_CLASS)) {
					addToJar(entry.getName(), new ByteArrayInputStream(IOUtil.toByteArray(capsuleJarInputStream)), jar);
				}
			}
			info("\t[Maven Caplet] Embedded Maven Caplet classes v" + capsuleMavenVersion + " (so capsule can resolve at launch)");
		}
	}

	private void addApp(final JarOutputStream jar) throws IOException {
		if (includeApp) {
			try {
				final File mainJarFile = new File(this.buildDir, this.finalName + ".jar");
				addToJar(mainJarFile.getName(), new FileInputStream(mainJarFile), jar);
				info("\t[App] App jar embedded (" + mainJarFile.getName() + ")");
			} catch (final FileNotFoundException e) { // if project jar wasn't built (perhaps the mvn package wasn't run, and only the mvn compile was run)
				// add compiled project classes instead
				warn("\t[App] Couldn't add main jar file to fat capsule, adding the project classes directly instead.");

				final File classesDir = new File(this.buildDir, "classes");
				Files.walkFileTree(classesDir.toPath(), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(final Path path, final BasicFileAttributes attrs) throws IOException {
						if (!attrs.isDirectory() && !path.endsWith(".DS_Store") && !path.endsWith("MANIFEST.MF")) {
							addToJar(path.toString().substring(path.toString().indexOf("classes") + 8), new FileInputStream(path.toFile()), jar);
							debug("\t\t[App] Adding Compile Project Class to Capsule: [" + path.toFile().getPath() + "]");
						}
						return FileVisitResult.CONTINUE;
					}
				});
				info("\t[App] App class files embedded.");
			}
		} else if (resolveApp) {
			info("\t[App] App jar NOT embedded and marked to be resolved at launch.");
		} else {
			warn("\t[App] App jar NOT embedded and NOT marked to be resolved at launch.");
		}
	}

	private void addDependencies(final JarOutputStream jar) throws IOException {

		// go through dependencies
		final Set<Artifact> artifacts = includeTransitiveDep ? includedDependencyArtifacts() : includedDirectDependencyArtifacts();

		for (final Artifact artifact : artifacts) {

			final String scope = artifact.getScope() == null || artifact.getScope().isEmpty() ? "compile" : artifact.getScope();

			boolean optionalMatch = true;
			if (artifact.isOptional()) optionalMatch = includeOptionalDep;

			// check artifact has a file
			if (artifact.getFile() == null)
				warn("\t[Dependency] " + coords(artifact) + "(" + artifact.getScope() + ") file not found, thus will not be added to capsule jar.");

			// ignore capsule jar
			if (artifact.getGroupId().equalsIgnoreCase(CAPSULE_GROUP) && artifact.getArtifactId().equalsIgnoreCase(DEFAULT_CAPSULE_NAME))
				continue;

			// check against requested scopes
			if (
					(includeCompileDep && scope.equals("compile") && optionalMatch) ||
							(includeRuntimeDep && scope.equals("runtime") && optionalMatch) ||
							(includeProvidedDep && scope.equals("provided") && optionalMatch) ||
							(includeSystemDep && scope.equals("system") && optionalMatch) ||
							(includeTestDep && scope.equals("test") && optionalMatch)
					) {
				addToJar(artifact.getFile().getName(), new FileInputStream(artifact.getFile()), jar);
				info("\t[Embedded-Dependency] " + coords(artifact) + "(" + scope + ")");
			} else
				debug("\t[Dependency] " + coords(artifact) + "(" + artifact.getScope() + ") skipped, as it does not match any required scope");
		}
	}

	private void addFileSets(final JarOutputStream jar) throws IOException {
		if (fileSets == null) return;

		for (final FileSet fileSet : fileSets) {
			if (fileSet.directory != null && !fileSet.directory.isEmpty()) {
				final File fileSetDir = new File(fileSet.directory);
				final File directory;
				if (fileSetDir.isAbsolute()) {
					directory = fileSetDir;
				} else {
					directory = new File(baseDir.getPath() + File.separatorChar + fileSet.directory);
				}

				// warn & skip if not directory
				if (!directory.isDirectory()) {
					warn("[FileSet] Attempted to include file from non-directory [" + directory.getAbsolutePath() + "], skipping...");
					continue;
				}

				final String outputDirectory = addDirectoryToJar(jar, fileSet.outputDirectory);

				final Set<File> matchedEntries = new HashSet<>();

				// get files entries based on direct files under dir (i.e ignore un sub dirs)
				final Set<File> entries = new HashSet<>();
				for (final File file : directory.listFiles()) {
					if (!file.isDirectory())
						entries.add(file);
				}

				for (final File entry : entries) {
					System.out.println(entry.toString());
				}

				for (final String include : fileSet.includes) {

					if (include.contains("*")) { // wildcard

						// quick hack to find number of wildcards
						final int starCount = include.length() - include.replace("*", "").length();

						// max one wildcard allowed
						if (starCount > 1) {
							warn("\t[FileSet]: More than one asterisk (*) found in include, skipping... | " + include);
							continue;
						}

						// if start
						if (include.startsWith("*")) {
							final String toMatch = include.substring(1);
							for (final File entry : entries) {
								if (entry.getName().endsWith(toMatch)) {
									matchedEntries.add(entry);
								}
							}
						}

						// if end
						else if (include.endsWith("*")) {
							final String toMatch = include.substring(0, include.length() - 1);
							for (final File entry : entries) {
								if (entry.getName().startsWith(toMatch)) {
									matchedEntries.add(entry);
								}
							}
						}

						// if middle (check start and end match)
						else {
							final String[] split = include.split("\\*");
							for (final File entry : entries) {
								if (entry.getName().startsWith(split[0]) && entry.getName().endsWith(split[1])) {
									matchedEntries.add(entry);
								}
							}
						}

					} else { // match exact (no wildcard)
						matchedEntries.add(new File(directory, include));
					}


					// add all entries matched

					if (!matchedEntries.isEmpty()) {
						for (final File entry : matchedEntries) {
							addToJar(outputDirectory + entry.getName(), new FileInputStream(entry), jar);
							info("\t[FileSet]: Embedded " + outputDirectory + entry.getName() + " from " + directory);
						}
					} else {
						warn("\t[FileSet]: No matches found in " + directory);
					}

				}
			}
		}
	}

	private void addDependencySets(final JarOutputStream jar) throws IOException {
		if (dependencySets == null) return;

		for (final DependencySet dependencySet : dependencySets) {

			final Artifact artifact = toArtifact(resolve(dependencySet.toString()));


			if (artifact == null || artifact.getFile() == null) {
				warn("\t[DependencySet]: Resolution Fail | " + dependencySet.toString());
				continue;
			}

			final JarFile jarFile = new JarFile(artifact.getFile());

			final Set<JarEntry> entries = set(jarFile.entries());

			final String outputDirectory = addDirectoryToJar(jar, dependencySet.outputDirectory);

			// if includes is set add only specified
			if (dependencySet.includes != null && dependencySet.includes.length > 0) {
				final Set<ZipEntry> matchedEntries = new HashSet<>();

				for (final String include : dependencySet.includes) {

					if (include.contains("*")) { // wildcard

						// quick hack to find number of wildcards
						final int starCount = include.length() - include.replace("*", "").length();

						// max one wildcard allowed
						if (starCount > 1) {
							warn("\t[DependencySet]: More than one asterisk (*) found in include, skipping... | " + include);
							continue;
						}

						// if start
						if (include.startsWith("*")) {
							final String toMatch = include.substring(1);
							for (final ZipEntry entry : entries) {
								if (entry.getName().endsWith(toMatch)) {
									matchedEntries.add(entry);
								}
							}
						}

						// if end
						else if (include.endsWith("*")) {
							final String toMatch = include.substring(0, include.length() - 1);
							for (final ZipEntry entry : entries) {
								if (entry.getName().startsWith(toMatch)) {
									matchedEntries.add(entry);
								}
							}
						}

						// if middle (check start and end match)
						else {
							final String[] split = include.split("\\*");
							for (final ZipEntry entry : entries) {
								if (entry.getName().startsWith(split[0]) && entry.getName().endsWith(split[1])) {
									matchedEntries.add(entry);
								}
							}
						}

					} else { // match exact (no wildcard)
						matchedEntries.add(jarFile.getEntry(include));
					}

				}

				// add all entries matched

				if (!matchedEntries.isEmpty()) {
					for (final ZipEntry entry : matchedEntries) {
						addToJar(outputDirectory + entry.getName(), jarFile.getInputStream(entry), jar);
						info("\t[DependencySet]: Embedded from " + coords(artifact) + " > " + outputDirectory + entry.getName());
					}
				} else {
					warn("\t[DependencySet]: No matches found in " + artifact.getFile());
				}

				// else add whole file
			} else {
				if (!dependencySet.unpack) {
					info("\t[DependencySet]: Adding " + artifact.getFile().getName() + " to " + outputDirectory);
					addToJar(outputDirectory + artifact.getFile().getName(), new FileInputStream(artifact.getFile()), jar);
				} else {
					if (artifact.getType() != null && artifact.getType().equals("jar")) {
						info("\t[DependencySet]: Adding (unpacked) " + artifact.getFile().getName() + " to " + outputDirectory);
						for (final ZipEntry entry : entries) {
							debug("\t\t[DependencySet]: Adding (unpacked) " + outputDirectory + entry.getName());
							addToJar(outputDirectory + entry.getName(), jarFile.getInputStream(entry), jar);
						}
					} else {
						warn("\t[DependencySet]: Cannot unpack " + artifact.getFile().getName() + " as it is not in jar format.");
					}
				}
			}

		}
	}

	private void addChmodCopy(final File jar) throws IOException {
		if (this.chmod) {
			final File file = createExecCopyProcess(jar, EXEC_PREFIX, ".x");
			info("[Capsule CHMOD]: " + file.getName());
		}
	}

	private void addTrampolineCopy(final File jar) throws IOException {
		if (this.trampoline) {
			final File file = createExecCopyProcess(jar, EXEC_TRAMPOLINE_PREFIX, ".tx");
			info("[Capsule Trampoline]: " + file.getName());
		}
	}

	// STRINGS

	private String buildInfoString() {
		final StringBuilder builder = new StringBuilder();
		if (includeApp) builder.append("includeApp ");
		if (includeAppDep) builder.append("includeAppDep ");
		if (includePluginDep) builder.append("includePluginDep ");
		if (includeCompileDep) builder.append("includeCompileDep ");
		if (includeRuntimeDep) builder.append("includeRuntimeDep ");
		if (includeProvidedDep) builder.append("includeProvidedDep ");
		if (includeSystemDep) builder.append("includeSystemDep ");
		if (includeTestDep) builder.append("includeTestDep ");
		if (includeTransitiveDep) builder.append("includeTransitiveDep ");

		if (resolveApp) builder.append("resolveApp ");
		if (resolveAppDep) builder.append("resolveAppDep ");
		if (resolvePluginDep) builder.append("resolvePluginDep ");
		if (resolveCompileDep) builder.append("resolveCompileDep ");
		if (resolveRuntimeDep) builder.append("resolveRuntimeDep ");
		if (resolveProvidedDep) builder.append("resolveProvidedDep ");
		if (resolveSystemDep) builder.append("resolveSystemDep ");
		if (resolveTestDep) builder.append("resolveTestDep ");
		if (resolveTransitiveDep) builder.append("resolveTransitiveDep ");

		return builder.toString().trim();
	}

	private String repoString() {
		final StringBuilder repoList = new StringBuilder();
		for (final RemoteRepository repository : this.remoteRepos)
			repoList.append(repository.getId()).append("(").append(repository.getUrl()).append(") ");
		return repoList.toString();
	}

	private String artifactString() throws IOException {
		final StringBuilder artifactList = new StringBuilder();

		if (includeApp) artifactList.append(coords(project.getArtifact())).append(" ");

		// go through artifacts
		final Set<Dependency> dependencies = includeTransitiveDep ? includedDependencies() : includedDirectDependencies();

		for (final Dependency dependency : dependencies) {

			final String scope = dependency.getScope() == null || dependency.getScope().isEmpty() ? "compile" : dependency.getScope();

			boolean optionalMatch = true;
			if (dependency.isOptional()) optionalMatch = includeOptionalDep;

			// ignore capsule jar
			if (dependency.getGroupId().equalsIgnoreCase(CAPSULE_GROUP) && dependency.getArtifactId().equalsIgnoreCase(DEFAULT_CAPSULE_NAME))
				continue;

			// check against requested scopes
			if (
					(includeCompileDep && scope.equals("compile") && optionalMatch) ||
							(includeRuntimeDep && scope.equals("runtime") && optionalMatch) ||
							(includeProvidedDep && scope.equals("provided") && optionalMatch) ||
							(includeSystemDep && scope.equals("system") && optionalMatch) ||
							(includeTestDep && scope.equals("test") && optionalMatch)
					)
				artifactList.append(coordsWithExclusions(dependency)).append(" ");
		}

		return artifactList.toString();
	}

	private String dependencyString() throws IOException {
		final StringBuilder dependenciesList = new StringBuilder();

		// add app to be resolved
		if (resolveApp)
			dependenciesList.append(coords(this.project.getArtifact())).append(" ");

		// go through dependencies
		final Set<Dependency> dependencies = resolveTransitiveDep ? resolvedDependencies() : resolvedDirectDependencies();

		for (final Dependency dependency : dependencies) {

			final String scope = dependency.getScope() == null || dependency.getScope().isEmpty() ? "compile" : dependency.getScope();

			boolean optionalMatch = true;
			if (dependency.isOptional()) optionalMatch = resolveOptionalDep;

			// ignore capsule jar
			if (dependency.getGroupId().equalsIgnoreCase(CAPSULE_GROUP) && dependency.getArtifactId().equalsIgnoreCase(DEFAULT_CAPSULE_NAME))
				continue;

			// check against requested scopes
			if (
					(resolveCompileDep && scope.equals("compile") && optionalMatch) ||
							(resolveRuntimeDep && scope.equals("runtime") && optionalMatch) ||
							(resolveProvidedDep && scope.equals("provided") && optionalMatch) ||
							(resolveSystemDep && scope.equals("system") && optionalMatch) ||
							(resolveTestDep && scope.equals("test") && optionalMatch)
					)
				dependenciesList.append(coordsWithExclusions(dependency)).append(" ");
		}

		return dependenciesList.toString();
	}

	private String systemPropertiesString() {
		StringBuilder propertiesList = null;
		if (this.properties != null) {
			propertiesList = new StringBuilder();
			for (final Pair property : this.properties) {
				if (property.key != null) {
					propertiesList.append(property.key);
					if (property.value != null && (property.value instanceof String && !((String) property.value).isEmpty()))
						propertiesList.append("=").append(property.value);
					propertiesList.append(" ");
				}
			}
		} else if (execConfig != null) { // else try and find properties in the exec plugin
			propertiesList = new StringBuilder();
			final Xpp3Dom propertiesElement = execConfig.getChild("systemProperties");
			if (propertiesElement != null) {
				final Xpp3Dom[] propertiesElements = propertiesElement.getChildren();
				if (propertiesElements != null && propertiesElements.length > 0) {
					for (final Xpp3Dom propertyElement : propertiesElements) {
						final Xpp3Dom key = propertyElement.getChild("key");
						final Xpp3Dom value = propertyElement.getChild("value");
						if (key != null && key.getValue() != null) {
							propertiesList.append(key.getValue()).append("=");
							if (value != null && value.getValue() != null && !value.getValue().isEmpty())
								propertiesList.append("=").append(value.getValue());
							propertiesList.append(" ");
						}
					}
				}
			}
		}
		return propertiesList == null ? null : propertiesList.toString().trim();
	}

	private File createExecCopyProcess(final File jar, final String prefix, final String extension) throws IOException {
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
			//			Runtime.getRuntime().exec("chmod +x " + x.getAbsolutePath());
			final boolean execResult = x.setExecutable(true, false);
			if (!execResult)
				warn("Failed to mark file executable - " + x.getAbsolutePath());
		} finally {
			IOUtil.close(in);
			IOUtil.close(out);
		}
		return x;
	}

	// RESOLVERS

	private File resolveCapsule() throws IOException {
		if (this.resolvedCapsuleProjectFile == null) {
			final ArtifactResult artifactResult = this.resolve(CAPSULE_GROUP, "capsule", null, capsuleVersion);
			if (artifactResult == null) throw new IOException("Capsule not found from repos");
			this.resolvedCapsuleProjectFile = artifactResult.getArtifact().getFile();
		}
		return this.resolvedCapsuleProjectFile;
	}

	private File resolveCapsuleMaven() throws IOException {
		if (this.resolvedCapsuleMavenProjectFile == null) {
			final ArtifactResult artifactResult = this.resolve(CAPSULE_GROUP, "capsule-maven", null, capsuleMavenVersion);
			if (artifactResult == null) throw new IOException("CapsuleMaven not found from repos");
			this.resolvedCapsuleMavenProjectFile = artifactResult.getArtifact().getFile();
		}
		return this.resolvedCapsuleMavenProjectFile;
	}

	private Set<Dependency> includedDependencies() {
		return cleanDependencies(appDependencies(), this.includeAppDep, pluginDependencies(), this.includePluginDep);
	}

	private Set<Dependency> includedDirectDependencies() {
		return cleanDependencies(appDirectDependencies(), this.includeAppDep, pluginDirectDependencies(), this.includePluginDep);
	}

	private Set<Artifact> includedDependencyArtifacts() {
		return cleanArtifacts(appDependencyArtifacts(), this.includeAppDep, pluginDependencyArtifacts(), this.includePluginDep);
	}

	private Set<Artifact> includedDirectDependencyArtifacts() {
		return cleanArtifacts(appDirectDependencyArtifacts(), this.includeAppDep, pluginDirectDependencyArtifacts(), this.includePluginDep);
	}

	private Set<Dependency> resolvedDependencies() {
		return cleanDependencies(appDependencies(), this.resolveAppDep, pluginDependencies(), this.resolvePluginDep);
	}

	private Set<Dependency> resolvedDirectDependencies() {
		return cleanDependencies(appDirectDependencies(), this.resolveAppDep, pluginDirectDependencies(), this.resolvePluginDep);
	}

	// HELPER OBJECTS

	public enum Type {
		empty, thin, fat;
	}

	public static class Mode {
		private String name = null;
		private Pair<String, String>[] properties = null;
		private Pair<String, String>[] manifest = null;
	}

	public static class DependencySet {
		public String groupId;
		public String artifactId;
		public String classifier;
		public String version;
		public String outputDirectory = "/";
		public String[] includes;
		public boolean unpack = false; // will unpack file of jar, zip, tar.gz, and tar.bz

		public String toString() {
			return coords(groupId, artifactId, classifier, version);
		}
	}

	public static class FileSet {
		public String directory;
		public String outputDirectory;
		public String[] includes;
	}

}
