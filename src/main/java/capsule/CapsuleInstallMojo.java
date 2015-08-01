package capsule;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Mojo(name = "install", defaultPhase = LifecyclePhase.INSTALL, requiresDependencyCollection = ResolutionScope.RUNTIME)
public class CapsuleInstallMojo extends CapsuleMojo {

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		super.execute();

		try {
			if (buildEmpty) install(buildEmpty());
			if (buildThin) install(buildThin());
			if (buildFat) install(buildFat());
		} catch (final IOException e) {
			e.printStackTrace();
			throw new MojoFailureException(e.getMessage());
		}

	}

	protected void install(final List<File> jars) throws IOException {
		for (final File jar : jars) {
			Files.copy(jar.toPath(), Paths.get(installPath() + jar.getName()), StandardCopyOption.REPLACE_EXISTING);
			info(installPath() + jar.getName());
		}
	}

}
