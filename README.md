Capsule Maven Plugin
====================

Latest version 0.7.1 is deployed on [maven central](http://search.maven.org/#artifactdetails%7Ccom.github.christokios%7Ccapsule-maven-plugin%7C0.7.1%7Cmaven-plugin).

v0.8.0 coming soon.

This a maven plugin to build a capsule out of your jar file.

See more at [capsule](https://github.com/puniverse/capsule).

Also see the [demo on how to use this plugin](https://github.com/christokios/capsule-maven-plugin-demo).

## Building from source
Clone the project and run a maven install:

```
git clone https://github.com/christokios/capsule-maven-plugin.git
cd capsule-maven-plugin
mvn install
```

## Using the plugin in your project
To build a capsule for your project, add the following to the build section of your pom.xml:

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
				<!-- <types>thin fat</types> -->
				
				<properties> <!-- properties to pass your app at runtime -->
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

This will build the three types of capsules of your app. The 'fat' type, the 'thin' type and the 'empty' type.
See more at [capsule](https://github.com/puniverse/capsule) for more about the three different types of capsules.

* `<appClass>`: The class with the main method (with package declaration) of your app that the capsule should run.
* `v0.8.0+ <output> (Optional)`: Specifies the output directory. Defaults to the `${project.build.directory}`.
* `v0.8.0+ <exec> (Optional)`: If executable (chmod +x) versions of the capsules should be built (Applicable for Mac/Unix style systems). See [here](https://github.com/brianm/really-executable-jars-maven-plugin) and [here](http://skife.org/java/unix/2011/06/20/really_executable_jars.html) for more info. Defaults to false.
* `v0.8.0+ <types> (Optional)`: The capsule types to build, allowed is `empty`, `thin` and `fat`, separated by a space. If empty or tag not present then all three are built.
* `<properties> (Optional)`: The system properties to provide the app with.
* `<manifest> (Optional)`: The set of additional manifest entries, for e.g `JVM-Args`. See [capsule](https://github.com/puniverse/capsule) for an exhaustive list. Note you do **not** need `Main-Class`, `Application-Class`, `Application`, `Dependencies`, `Repositories` and `System-Properties` as these are generated automatically.

You can also specify a maven property for the capsule version (This will be the version of capsule to package within the build of the capsules):

```
<properties>
	<capsule.version>0.7.1</capsule.version>
</properties>
```

otherwise the latest version of capsule will be used.

If you want to specify your custom Capsule class, add a manifest entry pointing to it:

```
<manifest>
	<property>
		<name>Main-Class</name>
		<value>MyCapsule</value>
	</property>
</manifest>
```

## License

This project is released under the [MIT license](http://opensource.org/licenses/MIT).

