Capsule Maven Plugin
====================

[![Version](http://img.shields.io/badge/version-0.8.0-blue.svg?style=flat)](https://github.com/christokios/capsule-maven-plugin/releases)
[![Maven Central](http://img.shields.io/badge/maven_central-0.8.0-blue.svg?style=flat)](http://mvnrepository.com/artifact/com.github.christokios/capsule-maven-plugin/)
[![License](http://img.shields.io/badge/license-MIT-blue.svg?style=flat)](http://opensource.org/licenses/MIT)

A maven plugin to build a capsule(s) out of your jar file.

See more at [capsule](https://github.com/puniverse/capsule) and the [demo using the plugin](https://github.com/christokios/capsule-maven-plugin-demo).

A pro? [Skip to the plugin reference](https://github.com/christokios/capsule-maven-plugin#reference).

Required java version 1.7+

#### Building from source
Clone the project and run a `maven clean install`:

```
git clone https://github.com/christokios/capsule-maven-plugin.git
cd capsule-maven-plugin
mvn install
```

Alternatively you can let maven pick up the latest version from [maven central](http://mvnrepository.com/artifact/com.github.christokios/capsule-maven-plugin).

## Quick Start

In the simplest form, you can add the following snippet in your `pom.xml`:

```
<plugin>
	<groupId>com.github.christokios</groupId>
	<artifactId>capsule-maven-plugin</artifactId>
	<version>${capsule.maven.plugin.version}</version>
	<executions>
		<execution>
			<phase>package</phase>
			<goals>
				<goal>capsule</goal>
			</goals>
			<configuration>
				<appClass>hello.HelloWorld</appClass>
			</configuration>
		</execution>
	</executions>
</plugin>
```

The only requirement is to have the `<appClass>` attribute in the configuration. This is the class of your app that contains the main method which will be fired on startup. You must include the package path along with the class name (`hello` is the package and `HelloWorld` is the class name above).

## Capsule Types

Capsule essentially defines three types of capsules:

- `fat`: This capsule jar will contain your app's jar as well as **all** its dependencies. When the fat-jar is run, capsule will simply setup the app and run it, without needing to resolve any dependencies.
- `thin`: This capsule jar will contain your app's classes but **no** dependencies. Capsule will resolve these dependencies at runtime (in the cache).
- `empty`: This capsule will not include your app, or any of its dependencies. It will only contain the name of your app declared in the jar's manifest, along with capsule's classes. Capsule will read the manifest entry `Application` and resolve the app and its dependencies in Capsule's own cache (default `~/.capsule`).

By default, the plugin will build all three types in the form:

```
target/my-app-1.0-capsule-fat.jar
target/my-app-1.0-capsule-thin.jar
target/my-app-1.0-capsule-empty.jar
```

Which type of capsule you prefer is a matter of personal taste.

If you only want a specific capsule type to be built, you can add the `<types>` tag to the plugin configuration. You can specify one or more capsule types separated by a space:

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<types>thin fat</types>
</configuration>
```

## Really Executable Capsules (Mac/Linux only)

It is possible to `chmod+x` a jar so it can be run without needing to prefix the command with `java -jar`. You can see more info about this concept [here](https://github.com/brianm/really-executable-jars-maven-plugin) and [here](http://skife.org/java/unix/2011/06/20/really_executable_jars.html).

The plugin can build really executable jars for you automatically!

Add the `<exec>true</exec>` to your configuration (default is false).

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<exec>true</exec>
</configuration>
```

The plugin will then output the really executables with the extension `.x`.

```
target/my-app-1.0-capsule-fat.jar
target/my-app-1.0-capsule-thin.jar
target/my-app-1.0-capsule-empty.jar
target/my-app-1.0-capsule-fat.x
target/my-app-1.0-capsule-thin.x
target/my-app-1.0-capsule-empty.x
```

So normally you would run the fat capsule like so:

```
java -jar target/my-app-1.0-capsule-fat.jar
```

However with the really executable builds, you can alternatively run the capsule nice and cleanly:

```
./target/my-app-1.0-capsule-fat.x
```

##### Trampoline

When a capsule is launched, two processes are involved: first, a JVM process runs the capsule launcher, which then starts up a second, child process that runs the actual application. The two processes are linked so that killing or suspending one, will do the same for the other. While this model works well enough in most scenarios, sometimes it is desirable to directly launch the process running the application, rather than indirectly. This is supported by "capsule trampoline". [See more here at capsule](https://github.com/puniverse/capsule#the-capsule-execution-process).

If you would like to build 'trampoline' executable capsules you can add the `<trampoline>true</trampoline>` flag to the plugin's configuration:

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<trampolinetrue</trampoline>
</configuration>
```

This will build `.tx` files like so:

```
target/my-app-1.0-capsule-fat.jar
target/my-app-1.0-capsule-thin.jar
target/my-app-1.0-capsule-empty.jar
target/my-app-1.0-capsule-fat.tx
target/my-app-1.0-capsule-thin.tx
target/my-app-1.0-capsule-empty.tx
```

Which you can run:

```
./target/my-app-1.0-capsule-fat.tx
```

This will output the command which you then have to copy and paste and run it yourself manually, thus ensuring you have only one process for your app.

## Providing your app System Properties

Capsule also supports providing your app with system properties. This can be done at runtime but its also convenient to define some properties at build time too.

Simply add the `<properties>` tag in the plugin's configuration and declare any properties.

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<properties>
		<property>
			<name>boo</name>
			<value>ya</value>
		</property>
	</properties>
</configuration>
```

Then, anywhere in your app's code you call upon this system property:

```
package hello;
public class HelloWorld {
	public static void main(String[] args) {
		System.out.println(System.getProperty("boo")); // outputs 'ya'
	}
}
```

## Additional Manifest Entries

Capsule supports a number of manifest entries to configure your app to your heart's content. See the full reference [here](https://github.com/puniverse/capsule#manifest-attributes).

So for e.g if you would like to set the `JVM-Args`:

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<manifest>
		<property>
			<name>JVM-Args</name>
			<value>-Xmx512m</value>
		</property>
	</manifest>
</configuration>
```

Note you do **not** need `Main-Class`, `Application-Class`, `Application`, `Dependencies`, `Repositories` and `System-Properties` as these are generated automatically by the plugin.


## Custom Capsule Version

Ths plugin can support older versions of capsule (at your own risk). You can specify a maven property for the capsule version (This will be the version of capsule to package within the build of the capsules).

```
<properties>
	<capsule.version>0.6.0</capsule.version>
</properties>
```
Otherwise, the latest version of capsule will be used automatically. This is recommended.

## Custom Capsule Class

Capsule supports defining your own Capsule class by extending the `Capsule.class`. If you want to specify your custom Capsule class, add a manifest entry pointing to it:

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<manifest>
		<property>
			<name>Main-Class</name>
			<value>MyCapsule</value>
		</property>
	</manifest>
</configuration>
```

See more info on [custom capsules](https://github.com/puniverse/capsule#custom-capsules).

## Reference

* `<appClass>`: The class with the main method (with package declaration) of your app that the capsule should run.
* `<types> (Optional)`: The capsule types to build, allowed is `empty`, `thin` and `fat`, separated by a space. If empty or tag not present then all three are built.
* `<exec> (Optional)`: If executable (chmod +x) versions of the capsules should be built in the form of '.x' files (Applicable for Mac/Unix style systems). See [here](https://github.com/brianm/really-executable-jars-maven-plugin) and [here](http://skife.org/java/unix/2011/06/20/really_executable_jars.html) for more info. Defaults to false.
* `<trampoline> (Optional)`: This will create trampoline style executable capsules in the form of '.tx' files. See more info [here](https://github.com/christokios/capsule-maven-plugin#trampoline).
* `<output> (Optional)`: Specifies the output directory. Defaults to the `${project.build.directory}`.
* `<properties> (Optional)`: The system properties to provide the app with.
* `<manifest> (Optional)`: The set of additional manifest entries, for e.g `JVM-Args`. See [capsule](https://github.com/puniverse/capsule) for an exhaustive list. Note you do **not** need `Main-Class`, `Application-Class`, `Application`, `Dependencies`, `Repositories` and `System-Properties` as these are generated automatically.

```
<!-- BUILD CAPSULES -->
<plugin>
	<groupId>com.github.christokios</groupId>
	<artifactId>capsule-maven-plugin</artifactId>
	<version>${capsule.maven.plugin.version}</version>
	<executions>
		<execution>
			<phase>package</phase>
			<goals>
				<goal>capsule</goal>
			</goals>
			<configuration>

				<!-- REQUIRED -->
				<appClass>hello.HelloWorld</appClass>

				<!-- OPTIONAL (All below) -->

				<!-- <output>target/</output> -->
				<!-- <exec>true</exec> -->
				<!-- <trampoline>true</trampoline> -->
				<!-- <types>thin fat</types> -->

				<properties>
					<property>
						<name>propertyName1</name>
						<value>propertyValue1</value>
					</property>
				</properties>

				<manifest>
					<property>
						<name>JVM-Args</name>
						<value>-Xmx512m</value>
					</property>
					<property>
						<name>Min-Java-Version</name>
						<value>1.8.0</value>
					</property>
				</manifest>

			</configuration>
		</execution>
	</executions>
</plugin>
```

## License

This project is released under the [MIT license](http://opensource.org/licenses/MIT).
