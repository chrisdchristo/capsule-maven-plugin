package enix.capsule;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.DefaultMavenProjectHelper;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
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

@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyCollection = ResolutionScope.TEST, requiresDependencyResolution
	= ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class CapsuleMojo extends AbstractMojo {

	private String LOG_PREFIX = "[CapsuleMavenPlugin] ";

	private static final String DEFAULT_CAPSULE_VERSION = "1.0.2";
	private static final String DEFAULT_CAPSULE_MAVEN_VERSION = "1.0.3";

	private static final String CAPSULE_GROUP = "co.paralleluniverse";
	private static final String DEFAULT_CAPSULE_NAME = "Capsule";
	private static final String DEFAULT_CAPSULE_CLASS = DEFAULT_CAPSULE_NAME + ".class";
	private static final String DEFAULT_CAPSULE_MAVEN_NAME = "MavenCapsule";
	private static final String DEFAULT_CAPSULE_MAVEN_CLASS = "MavenCapsule.class";

	private static final String EXEC_PREFIX = "#!/bin/sh\n\nexec java -jar \"$0\" \"$@\"\n\n";
	private static final String EXEC_TRAMPOLINE_PREFIX = "#!/bin/sh\n\nexec java -Dcapsule.trampoline -jar \"$0\" \"$@\"\n\n";

	private static final String EXEC_PLUGIN_KEY = "org.codehaus.mojo:exec-maven-plugin";

	private final MavenProjectHelper helper = new DefaultMavenProjectHelper();

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project = null;

	/**
	 * AETHER REPO LINK
	 */
	@Component
	private RepositorySystem repoSystem = null;
	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
	private RepositorySystemSession repoSession = null;
	@Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
	private List<RemoteRepository> remoteRepos = null;
	@Parameter(defaultValue = "${project.build.finalName}", readonly = true)
	private String finalName = null;
	@Parameter(defaultValue = "${project.build.directory}")
	private File buildDir = null;
	@Parameter(defaultValue = "${project.basedir}")
	private File baseDir = null;

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
	@Parameter(property = "capsule.chmod", defaultValue = "false")
	private String chmod = null;
	@Parameter(property = "capsule.trampoline", defaultValue = "false")
	private String trampoline = null;

	@Parameter(property = "capsule.includeApp", defaultValue = "true")
	private boolean includeApp = false;
	@Parameter(property = "capsule.includeTransitiveDep", defaultValue = "true")
	private boolean includeTransitiveDep = false;
	@Parameter(property = "capsule.includeCompileDep", defaultValue = "true")
	private boolean includeCompileDep = false;
	@Parameter(property = "capsule.includeRuntimeDep", defaultValue = "true")
	private boolean includeRuntimeDep = false;
	@Parameter(property = "capsule.includeProvidedDep", defaultValue = "false")
	private boolean includeProvidedDep = false;
	@Parameter(property = "capsule.includeSystemDep", defaultValue = "false")
	private boolean includeSystemDep = false;
	@Parameter(property = "capsule.includeTestDep", defaultValue = "false")
	private boolean includeTestDep = false;
	@Parameter(property = "capsule.includeOptionalDep", defaultValue = "false")
	private boolean includeOptionalDep = false;

	@Parameter(property = "capsule.resolveApp", defaultValue = "false")
	private boolean resolveApp = false;
	@Parameter(property = "capsule.resolveTransitiveDep", defaultValue = "false")
	private boolean resolveTransitiveDep = false;
	@Parameter(property = "capsule.resolveCompileDep", defaultValue = "false")
	private boolean resolveCompileDep = false;
	@Parameter(property = "capsule.resolveRuntimeDep", defaultValue = "false")
	private boolean resolveRuntimeDep = false;
	@Parameter(property = "capsule.resolveProvidedDep", defaultValue = "false")
	private boolean resolveProvidedDep = false;
	@Parameter(property = "capsule.resolveSystemDep", defaultValue = "false")
	private boolean resolveSystemDep = false;
	@Parameter(property = "capsule.resolveTestDep", defaultValue = "false")
	private boolean resolveTestDep = false;
	@Parameter(property = "capsule.resolveOptionalDep", defaultValue = "false")
	private boolean resolveOptionalDep = false;

	@Parameter(property = "capsule.execPluginConfig")
	private String execPluginConfig = null;
	@Parameter(property = "capsule.customDescriptor", defaultValue = "-capsule")
	private String customDescriptor = null;
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
			includeTransitiveDep = false;
			includeCompileDep = false;
			includeRuntimeDep = false;
			includeProvidedDep = false;
			includeSystemDep = false;
			includeTestDep = false;
			includeOptionalDep = false;
			resolveApp = true;
			resolveTransitiveDep = true;
			resolveCompileDep = true;
			resolveRuntimeDep = true;
			resolveProvidedDep = false;
			resolveSystemDep = false;
			resolveTestDep = false;
			resolveOptionalDep = false;
		} else if (type == Type.thin) {
			includeApp = true;
			includeTransitiveDep = false;
			includeCompileDep = false;
			includeRuntimeDep = false;
			includeProvidedDep = false;
			includeSystemDep = false;
			includeTestDep = false;
			includeOptionalDep = false;
			resolveApp = false;
			resolveTransitiveDep = true;
			resolveCompileDep = true;
			resolveRuntimeDep = true;
			resolveProvidedDep = false;
			resolveSystemDep = false;
			resolveTestDep = false;
			resolveOptionalDep = false;
		} else if (type == Type.fat) {
			includeApp = true;
			includeTransitiveDep = true;
			includeCompileDep = true;
			includeRuntimeDep = true;
			includeProvidedDep = false;
			includeSystemDep = false;
			includeTestDep = false;
			includeOptionalDep = false;
			resolveApp = false;
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
			throw new MojoFailureException(LOG_PREFIX + " appClass not set (or could not be obtained from the exec plugin mainClass)");

		// resolve outputDir name (the file name of the capsule jar)
		this.outputName = this.finalName;
		if (this.customDescriptor != null) outputName += this.customDescriptor;

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
				} catch (final IOException e) { e.printStackTrace(); }

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

		if (!jarFile.exists()) {
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
		} else {
			info("EXISTS - " + jarFile.getName() + " (WILL NOT OVERWRITE)");
		}

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
		if (!repoString.isEmpty())
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
		final Set<Artifact> artifacts = includeTransitiveDep ? dependencyArtifacts() : directDependencyArtifacts();

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
				final File directory = new File(baseDir.getPath() + File.separatorChar + fileSet.directory);

				// warn & skip if not directory
				if (!directory.isDirectory()) {
					warn("[FileSet] Attempted to include file from non-directory [" + directory.getAbsolutePath() + "], skipping...");
					continue;
				}

				final String outputDirectory = addDirectoryToJar(jar, fileSet.outputDirectory);

				for (final String include : fileSet.includes) {
					final FileInputStream fin = new FileInputStream(new File(directory, include));
					addToJar(outputDirectory + include, fin, jar);
					info("\t[FileSet]: Embedded " + outputDirectory + include + " from " + directory);
				}
			}
		}
	}

	private void addDependencySets(final JarOutputStream jar) throws IOException {
		if (dependencySets == null) return;

		for (final DependencySet dependencySet : dependencySets) {
			for (final Object artifactObject : project.getDependencyArtifacts()) {
				final Artifact artifact = (Artifact) artifactObject;
				if (dependencySet.groupId.equals(artifact.getGroupId()) && dependencySet.artifactId.equals(artifact.getArtifactId())) {
					if (dependencySet.version == null || dependencySet.version.equals(artifact.getVersion())) {

						if (artifact.getFile() == null) {
							warn("Could not resolve dependency: " + dependencySet.groupId + ":" + dependencySet.artifactId + ":" + dependencySet.version);
							continue;
						}

						final JarFile jarFile = new JarFile(artifact.getFile());

						final String outputDirectory = addDirectoryToJar(jar, dependencySet.outputDirectory);

						// if includes is set add only specified
						if (dependencySet.includes != null && dependencySet.includes.length > 0) {
							for (final String include : dependencySet.includes) {
								final ZipEntry entry = jarFile.getEntry(include);
								if (entry != null) {
									addToJar(outputDirectory + include, jarFile.getInputStream(entry), jar);
									info("\t[DependencySet]: Embedded " + outputDirectory + include + " from " + artifact.getFile());
								} else {
									warn(include + " not found in " + artifact.getFile());
								}
							}

							// else add whole file
						} else {
							if (!dependencySet.unpack) {
								info("\t[DependencySet]: Adding " + artifact.getFile().getName() + " to " + outputDirectory);
								addToJar(outputDirectory + artifact.getFile().getName(), new FileInputStream(artifact.getFile()), jar);
							} else {
								if (artifact.getType() != null && artifact.getType().equals("jar")) {
									info("\t[DependencySet]: Adding (unpacked) " + artifact.getFile().getName() + " to " + outputDirectory);
									final Enumeration<JarEntry> entries = jarFile.entries();
									while (entries.hasMoreElements()) {
										final JarEntry entry = entries.nextElement();
										debug("\t\t[DependencySet]: Adding (unpacked) " + outputDirectory + entry.getName());
										addToJar(outputDirectory + entry.getName(), jarFile.getInputStream(entry), jar);
									}
								} else {
									warn("\t[DependencySet]: Cannot unpack " + artifact.getFile().getName() + " as it is not in jar format.");
								}
							}
						}
					} else {
						warn("\t[DependencySet]: Artifact version mismatch: " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion());
					}
				}
			}
		}
	}

	private void addChmodCopy(final File jar) throws IOException {
		if (this.chmod.equals("true") || this.chmod.equals("1")) {
			final File file = createExecCopyProcess(jar, EXEC_PREFIX, ".x");
			info("[Capsule CHMOD]: " + file.getName());
		}
	}

	private void addTrampolineCopy(final File jar) throws IOException {
		if (this.trampoline.equals("true") || this.trampoline.equals("1")) {
			final File file = createExecCopyProcess(jar, EXEC_TRAMPOLINE_PREFIX, ".tx");
			info("[Capsule Trampoline]: " + file.getName());
		}
	}

	// STRINGS

	private String buildInfoString() {
		final StringBuilder builder = new StringBuilder();
		if (includeApp) builder.append("includeApp ");
		if (includeCompileDep) builder.append("includeCompileDep ");
		if (includeRuntimeDep) builder.append("includeRuntimeDep ");
		if (includeProvidedDep) builder.append("includeProvidedDep ");
		if (includeSystemDep) builder.append("includeSystemDep ");
		if (includeTestDep) builder.append("includeTestDep ");
		if (includeTransitiveDep) builder.append("includeTransitiveDep ");

		if (resolveApp) builder.append("resolveApp ");
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
		final List<Dependency> dependencies = includeTransitiveDep ? dependencies() : directDependencies();

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
		final List<Dependency> dependencies = resolveTransitiveDep ? dependencies() : directDependencies();

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

	private String coords(final String groupId, final String artifactId, final String classifier, final String version) {
		final StringBuilder coords = new StringBuilder();
		coords.append(groupId).append(":").append(artifactId);
		if (classifier != null && !classifier.isEmpty())
			coords.append(":").append(classifier);
		coords.append(":").append(version);
		return coords.toString();
	}

	private String coords(final Artifact artifact) {
		return coords(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getVersion());
	}

	private String coords(final Dependency dependency) {
		return coords(dependency.getGroupId(), dependency.getArtifactId(), dependency.getClassifier(), dependency.getVersion());
	}

	private String coordsWithExclusions(final Dependency dependency) {
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

	// JAR & FILE HELPERS

	private String addDirectoryToJar(final JarOutputStream jar, final String outputDirectory) throws IOException {

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

	private JarOutputStream addToJar(final String name, final InputStream input, final JarOutputStream jar) throws IOException {
		try {
			debug("\t[Added to Jar]: " + name);
			jar.putNextEntry(new ZipEntry(name));
			IOUtil.copy(input, jar);
			jar.closeEntry();
		} catch (final ZipException ignore) {} // ignore duplicate entries and other errors
		IOUtil.close(input);
		return jar;
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
			final ArtifactResult artifactResult;
			try {
				artifactResult = this.resolve(CAPSULE_GROUP, "capsule", null, capsuleVersion);
			} catch (final ArtifactResolutionException e) {
				throw new IOException("Capsule not found from repos");
			}
			this.resolvedCapsuleProjectFile = artifactResult.getArtifact().getFile();
		}
		return this.resolvedCapsuleProjectFile;
	}

	private File resolveCapsuleMaven() throws IOException {
		if (this.resolvedCapsuleMavenProjectFile == null) {
			final ArtifactResult artifactResult;
			try {
				artifactResult = this.resolve(CAPSULE_GROUP, "capsule-maven", null, capsuleMavenVersion);
			} catch (final ArtifactResolutionException e) {
				throw new IOException("CapsuleMaven not found from repos");
			}
			this.resolvedCapsuleMavenProjectFile = artifactResult.getArtifact().getFile();
		}
		return this.resolvedCapsuleMavenProjectFile;
	}

	private ArtifactResult resolve(final String groupId, final String artifactId, final String classifier, final String version) throws
		ArtifactResolutionException {
		String coords = groupId + ":" + artifactId;
		if (classifier != null && !classifier.isEmpty()) coords += ":" + classifier;
		if (version != null && !version.isEmpty()) coords += ":" + version;
		return repoSystem.resolveArtifact(repoSession, new ArtifactRequest(new DefaultArtifact(coords), remoteRepos, null));
	}

	private List<Dependency> directDependencies() { return project.getDependencies(); }
	private Set<Artifact> directDependencyArtifacts() { return project.getDependencyArtifacts(); }
	private List<Dependency> dependencies() { return project.getTestDependencies(); }
	private Set<Artifact> dependencyArtifacts() { return project.getArtifacts(); }

	// HELPER OBJECTS

	public static class Pair<K, V> {
		public K key;
		public V value;
		public Pair() {}
		public Pair(final K key, final V value) {
			this.key = key;
			this.value = value;
		}
	}

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
		public String version;
		public String outputDirectory = "/";
		public String[] includes;
		public boolean unpack = false; // will unpack file of jar, zip, tar.gz, and tar.bz
	}

	public static class FileSet {
		public String directory;
		public String outputDirectory;
		public String[] includes;
	}

	// LOG

	private void debug(final String message) { getLog().debug(LOG_PREFIX + message); }
	private void info(final String message) { getLog().info(LOG_PREFIX + message); }
	private void warn(final String message) { getLog().warn(LOG_PREFIX + message); }
	private void printManifest(final Manifest manifest) {
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
