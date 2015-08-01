package capsule;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.IOException;

@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyCollection = ResolutionScope.RUNTIME)
public class CapsuleBuildMojo extends CapsuleMojo {

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		super.execute();

		try {
			if (buildEmpty) buildEmpty();
			if (buildThin) buildThin();
			if (buildFat) buildFat();
		} catch (final IOException e) {
			e.printStackTrace();
			throw new MojoFailureException(e.getMessage());
		}

	}

}
