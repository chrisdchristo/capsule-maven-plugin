Capsule Maven Plugin
====================

[![Version](http://img.shields.io/badge/version-0.9.1-blue.svg?style=flat)](https://github.com/chrischristo/capsule-maven-plugin/releases)
[![Maven Central](http://img.shields.io/badge/maven_central-0.9.1-blue.svg?style=flat)](http://mvnrepository.com/artifact/com.github.chrischristo/capsule-maven-plugin/)
[![License](http://img.shields.io/badge/license-MIT-blue.svg?style=flat)](http://opensource.org/licenses/MIT)

A maven plugin to build a capsule(s) out of your jar file.

See more at [capsule](https://github.com/puniverse/capsule) and the [demo using the plugin](https://github.com/chrischristo/capsule-maven-plugin-demo).

A pro? [Skip to the plugin reference](https://github.com/chrischristo/capsule-maven-plugin#reference).

Requires java version 1.7+ and maven 3.1.x+

#### Building from source
Clone the project and run a maven install:

```
git clone https://github.com/chrischristo/capsule-maven-plugin.git
cd capsule-maven-plugin
mvn install
```

Alternatively you can let maven pick up the latest version from [maven central](http://mvnrepository.com/artifact/com.github.chrischristo/capsule-maven-plugin).

## Quick Start

In the simplest form, you can add the following snippet in your `pom.xml`:

```
<plugin>
	<groupId>com.github.chrischristo</groupId>
	<artifactId>capsule-maven-plugin</artifactId>
	<version>${capsule.maven.plugin.version}</version>
	<configuration>
		<appClass>hello.HelloWorld</appClass>
	</configuration>
</plugin>
```

And then run:

```
mvn package capsule:build
```

The only requirement is to have the `<appClass>` attribute in the configuration. This is the class of your app that contains the main method which will be fired on startup. You must include the package path along with the class name (`hello` is the package and `HelloWorld` is the class name above).

## Package Phase Building

It is recommended to have an execution setup to build the capsules during the package phase, thus eliminating you to run an additional maven command to build them.

```
<plugin>
	<groupId>com.github.chrischristo</groupId>
	<artifactId>capsule-maven-plugin</artifactId>
	<version>${capsule.maven.plugin.version}</version>
	<executions>
		<execution>
			<goals>
				<goal>build</goal>
			</goals>
			<configuration>
				<appClass>hello.HelloWorld</appClass>
			</configuration>
		</execution>
	</executions>
</plugin>
```

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

Add the `<chmod>true</chmod>` to your configuration (default is false).

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<chmod>true</chmod>
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
	<trampoline>true</trampoline>
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
			<key>boo</key>
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
		<entry>
			<key>JVM-Args</key>
			<value>-Xmx512m</value>
		</entry>
	</manifest>
</configuration>
```

Note you do **not** need `Main-Class`, `Application-Class`, `Application`, `Dependencies`, `Repositories` and `System-Properties` as these are generated automatically by the plugin.

## Modes

Capsule supports the concept of modes, which essentially means defining your app jar into different ways depending on certain characteristics.
You define different modes for your app by setting specific manifest and/or system properties for each mode. So for e.g you could have a test mode which will define a test database connection, and likewise a production mode which will define a production database connection.
You can then easily run your capsule in a specific mode by adding the `-Dcapsule.mode=MODE` argument at the command line. See more at [capsule modes](https://github.com/puniverse/capsule#capsule-configuration-and-modes).

The maven plugin supports a convenient way to define modes for your capsule (include the below in the `<configuration>` tag).

```
<modes>
	<mode>
		<name>production</name>
		<properties>
			<property>
				<key>dbConnectionServer</key>
				<value>aws.amazon.example</value>
			</property>
		</properties>
		<manifest>
			<entry>
				<key>JVM-Args</key>
				<value>-Xmx1024m</value>
			</entry>
		</manifest>
	</mode>
</modes>
```

A mode must have the `<name>` tag, and you may define two things for each mode, namely, `<properties>` and `<manifest>` (in exactly the same syntax as above).

If the mode is activated at runtime (`-Dcapsule.mode=production`) then the properties listed in the mode will completely override the properties set in the main configuration. Thus, only the properties listed in the mode section will be available to the app.

However, the mode's manifest entries will be appended to the existing set of entries defined in the main section (unless any match up, then the mode's entry will override).

Of course, you can define multiple modes.

## FileSets

You can also specify assembly style `<fileSets>` in the `<configuration>` tag so you can add files to the capsule.

```
<fileSets>
	<fileSet>
		<directory>config/</directory>
		<outputDirectory>config/</outputDirectory>
		<includes>
			<include>myconfig.yml</include>
		</includes>
	</fileSet>
</fileSets>
```

You specify a number `<fileSet>` which must contain the `<directory>` (the location of the folder to copy), the `<outputDirectory>` (the destination directory within the capsule jar) and finally a set of `<include>` to specify which files from the `<directory>` to copy over.

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
		<entry>
			<key>Main-Class</key>
			<value>MyCapsule</value>
		</entry>
	</manifest>
</configuration>
```

See more info on [custom capsules](https://github.com/puniverse/capsule#custom-capsules).

## Maven Exec Plugin Integration

The [maven exec plugin](http://mojo.codehaus.org/exec-maven-plugin/) is a useful tool to run your jar all from within maven (using its classpath).

```
<plugin>
	<groupId>org.codehaus.mojo</groupId>
	<artifactId>exec-maven-plugin</artifactId>
	<version>${maven.exec.plugin.version}</version>
	<configuration>
		<mainClass>hello.HelloWorld</mainClass>
	</configuration>
</plugin>
```

You can then run your normal jar by:

```
mvn package exec:java
```

Notice that the exec plugin provides a configuration where you can specify the `<mainClass>` as well as other fields such as `<systemProperties>`. The Capsule plugin provides the ability to pull this config and apply it to the built capsules, thus saving you from having to enter it twice (once at the exec plugin and second at the capsule plugin).

In the capsule plugin you can set the `<execPluginConfig>` tag to do this:

```
<plugin>
	<groupId>com.github.chrischristo</groupId>
	<artifactId>capsule-maven-plugin</artifactId>
	<version>${capsule.maven.plugin.version}</version>
	<configuration>
		<execPluginConfig>root</execPluginConfig>
	</configuration>
</plugin>
```

The value `root` will tell the capsule plugin to pull the config from the `<configuration>` element at the root of the exec plugin.

If you are using executions within the exec plugin like so:

```
<plugin>
	<groupId>org.codehaus.mojo</groupId>
	<artifactId>exec-maven-plugin</artifactId>
	<version>${maven.exec.plugin.version}</version>
	<executions>
		<execution>
			<id>default-cli</id>
			<goals>
				<goal>java</goal>
			</goals>
			<configuration>
				<mainClass>hello.HelloWorld</mainClass>
			</configuration>
		</execution>
	</executions>
</plugin>
```

Then you can specify the `<execPluginConfig>` to the ID of the execution:

```
<plugin>
	<groupId>com.github.chrischristo</groupId>
	<artifactId>capsule-maven-plugin</artifactId>
	<version>${capsule.maven.plugin.version}</version>
	<configuration>
		<execPluginConfig>default-cli</execPluginConfig>
	</configuration>
</plugin>
```

##### How the capsule plugin maps the config from the exec plugin

The capsule plugin will map values from the exec plugin:

```
mainClass -> appClass
systemProperties -> properties
arguments -> JVM-Args (manifest entry)
```

So the `<mainClass>` element in the exec's `<configuration>` will be the `<appClass>` in the capsules's `<configuration>`.

##### A complete solution

So essentially you can setup as follows:

```
<plugin>
	<groupId>org.codehaus.mojo</groupId>
	<artifactId>exec-maven-plugin</artifactId>
	<version>${maven.exec.plugin.version}</version>
	<configuration>
		<mainClass>hello.HelloWorld</mainClass>
		<systemProperties>
			<property>
				<key>propertyName1</key>
				<value>propertyValue1</value>
			</property>
		</systemProperties>
	</configuration>
</plugin>
<plugin>
	<groupId>com.github.chrischristo</groupId>
	<artifactId>capsule-maven-plugin</artifactId>
	<version>${capsule.maven.plugin.version}</version>
	<configuration>
		<execPluginConfig>root</execPluginConfig>
	</configuration>
</plugin>
```

##### Overriding the exec plugin config

Note that if you do specify the `<appClass>`, `<properties>` or `JVM-Args` (in the `<manifest>`) of the capsule plugin, then these will override the config of the exec plugin.

## Reference

* `<appClass>`: The class with the main method (with package declaration) of your app that the capsule should run. This can be optional too, if you are using the maven exec plugin and have specified a `execPluginConfig`.
* `<types> (Optional)`: The capsule types to build, allowed is `empty`, `thin` and `fat`, separated by a space. If empty or tag not present then all three are built.
* `<chmod> (Optional)`: If executable (chmod +x) versions of the capsules should be built in the form of '.x' files (Applicable for Mac/Unix style systems). See [here](https://github.com/brianm/really-executable-jars-maven-plugin) and [here](http://skife.org/java/unix/2011/06/20/really_executable_jars.html) for more info. Defaults to false.
* `<trampoline> (Optional)`: This will create trampoline style executable capsules in the form of '.tx' files. See more info [here](https://github.com/chrischristo/capsule-maven-plugin#trampoline).
* `<output> (Optional)`: Specifies the output directory. Defaults to the `${project.build.directory}`.
* `<execPluginConfig> (Optional)`: Specifies the ID of an execution within the exec-maven-plugin. The configuration from this execution will then be used to configure the capsules. If you specify 'root' then the `<configuration>` at root will be used instead of a particular execution. The exec's `<mainClass>` will map to Capsule's `<appClass>`. The exec's `<systemProperties>` will map to capsule's `<properties>`. If you specify this tag then the `<appClass>` tag does not need to present.
* `<properties> (Optional)`: The system properties to provide the app with.
* `<manifest> (Optional)`: The set of additional manifest entries, for e.g `JVM-Args`. See [capsule](https://github.com/puniverse/capsule#reference) for an exhaustive list. Note you do **not** need `Main-Class`, `Application-Class`, `Application`, `Dependencies`, `Repositories` and `System-Properties` as these are generated automatically.
* `<modes> (Optional)`: Define a set of `<mode>` with its own set of `<properties>` and `<manifest>` entries to categorise the capsule into different modes. The mode can be set at runtime. [See more here](https://github.com/chrischristo/capsule-maven-plugin#modes).
* `<fileSets> (Optional)`: Define a set of `<fileSet>` to copy over files into the capsule. [See more here](https://github.com/chrischristo/capsule-maven-plugin#filesets).

```
<!-- BUILD CAPSULES -->
<plugin>
	<groupId>com.github.chrischristo</groupId>
	<artifactId>capsule-maven-plugin</artifactId>
	<version>${capsule.maven.plugin.version}</version>
	<configuration>

		<appClass>hello.HelloWorld</appClass>

		<!-- <output>target/</output> -->
		<!-- <chmod>true</chmod> -->
		<!-- <trampoline>true</trampoline> -->
		<!-- <types>thin fat</types> -->
		<!-- <execPluginConfig>root</execPluginConfig> -->

		<properties>
			<property>
				<key>propertyName1</key>
				<value>propertyValue1</value>
			</property>
		</properties>

		<manifest>
			<entry>
				<key>JVM-Args</key>
				<value>-Xmx512m</value>
			</entry>
			<entry>
				<key>Min-Java-Version</key>
				<value>1.8.0</value>
			</entry>
		</manifest>

		<modes>
			<mode>
				<name>production</name>
				<properties>
					<property>
						<key>dbConnectionServer</key>
						<value>aws.amazon.example</value>
					</property>
				</properties>
				<manifest>
					<entry>
						<key>JVM-Args</key>
						<value>-Xmx1024m</value>
					</entry>
				</manifest>
			</mode>
		</modes>

		<fileSets>
			<fileSet>
				<directory>config/</directory>
				<outputDirectory>config/</outputDirectory>
				<includes>
					<include>myconfig.yml</include>
				</includes>
			</fileSet>
		</fileSets>

	</configuration>
	<executions>
		<execution>
			<goals>
				<goal>build</goal>
			</goals>
		</execution>
	</executions>
</plugin>
```

## License

This project is released under the [MIT license](http://opensource.org/licenses/MIT).
