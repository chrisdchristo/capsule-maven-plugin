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
to build a capsule for your project, add the following to the build section of your pom.xml

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
				<!--<target></target>-->
				<!--<finalName></finalName>-->
				<mainClass>hello.HelloWorld</mainClass>
			</configuration>
		</execution>
	</executions>
</plugin>
```

This will build a capsule version of your jar, with the -capsule appended.

```
<target></target>: Specifies the output directory. Defaults to the ${project.build.directory}.
<finalName></finalName>: Specifies the finalName of the project. Defaults to the ${project.build.finalName}.
<mainClass></mainClass>: The main class file name (with package declaration) of your app that the capsule should run.
```

You can also specify a maven property for the capsule version:

```
<properties>
	<capsule.version>0.5.0</capsule.version>
</properties>
```

## License

This demo project is released under the [MIT license](http://opensource.org/licenses/MIT).

