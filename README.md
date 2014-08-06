Capsule Maven Plugin
====================

This a maven plugin to build a capsule out of your jar file.

See more at [capsule](https://github.com/puniverse/capsule).

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
<!-- BUILD CAPSULE -->
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
				<mainClass>hello.HelloWorld</mainClass>

				<!-- OPTIONAL -->
				<!--<target>otherOutputDirectory/</target>-->
				<!--<finalName>otherFinalName</finalName>-->
				<!--<minJavaVersion>1.8.0</minJavaVersion>-->
				<!--<extractCapsule>true</extractCapsule>-->
				<!--<jvmArgs>-Xmx512m</jvmArgs>-->
				<properties>
					<property>
						<name>propertyName1</name>
						<value>propertyValue1</value>
					</property>
				</properties>

			</configuration>
		</execution>
	</executions>
</plugin>
```

This will build the three types of capsules of your app. The 'full' type, the 'thin' type and the 'empty' type.
See more at [capsule](https://github.com/puniverse/capsule) for more about the three different types of capsules.

```
<mainClass>: The main class file name (with package declaration) of your app that the capsule should run.

<target>: Specifies the output directory. Defaults to the ${project.build.directory}.
<finalName>: Specifies the finalName of the project. Defaults to the ${project.build.finalName}.
<minJavaVersion>: The minimum java version required.
<extractCapsule>: Whether the jar should be extracted. This is only for the the 'thin' capsule type.
<jvmArgs>: The jvm args that will be applied when the app is run.
<properties>: The system properties to provide the app with.
```

You can also specify a maven property for the capsule version:

```
<properties>
	<capsule.version>0.5.0</capsule.version>
</properties>
```

## License

This project is released under the [MIT license](http://opensource.org/licenses/MIT).

