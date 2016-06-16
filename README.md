Capsule Maven Plugin
====================

[![Version](http://img.shields.io/badge/version-1.2.0-blue.svg?style=flat)](https://github.com/chrischristo/capsule-maven-plugin/releases)
[![Maven Central](http://img.shields.io/badge/maven_central-1.2.0-blue.svg?style=flat)](http://mvnrepository.com/artifact/com.github.chrischristo/capsule-maven-plugin/)
[![License](http://img.shields.io/badge/license-MIT-blue.svg?style=flat)](http://opensource.org/licenses/MIT)

A maven plugin to build a [capsule](https://github.com/puniverse/capsule) out of your app.

- [Capsule | simple java deployment](https://medium.com/@chrischristo/capsule-simple-java-delpoyment-7a70be622375)
- [Capsule & AWS | Java on the cloud](https://medium.com/@chrischristo/capsule-aws-java-on-the-cloud-4abe2d4d6c89)

See more at [capsule](https://github.com/puniverse/capsule) and the [demo using the plugin](https://github.com/chrischristo/capsule-maven-plugin-demo).

A pro? [Skip to the plugin reference](https://github.com/chrischristo/capsule-maven-plugin#reference).

Requires java version 1.7+ and maven 3.1.x+

Supports [Capsule v1.0.2](https://github.com/puniverse/capsule/releases/tag/v1.0.2) & [CapsuleMaven v1.0.3](https://github.com/puniverse/capsule-maven/releases/tag/v1.0.3) and below (It may also support new versions of Capsule, but use at your own risk).

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

Please note that the `package` command must have been executed before the `capsule:build` command can be run.

The only requirement is to have the `<appClass>` attribute in the configuration. This is the class of your app that contains the main method which will be fired on startup. You must include the package path along with the class name (`hello` is the package and `HelloWorld` is the class name above).

It is recommended to have specified the `capsule.version` property in your pom so that the capsule plugin knows which version of capsule to use.
If none is specified, the default version of Capsule will be used as specified at the top of the readme (which may not be the latest).

You can also set the `capsule.maven.version` property to tell the plugin which version of CapsuleMaven to use.


## Building Automatically

It is recommended to have an execution setup to build the capsules, thus eliminating you to run an additional maven command to build them.

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

By default the `build` goal runs during the package phase.

So now if you were to run simply `mvn package` then the build goal will execute which will build the capsules into your build directory.

Or alternatively you could use the `maven-exec-plugin` to run your app (as you develop), and then only build the capsule(s) when you want to deploy to a server. This plugin integrates nicely with the `maven-exec-plugin`, [see here](https://github.com/chrischristo/capsule-maven-plugin#maven-exec-plugin-integration).

## Capsule Contents

Essentially Capsule can be packaged with as much or as little as you want.

You have the option to include all, none or some of the dependencies. This can be done based on their ```scope```, their ```optional``` flag and if they are direct (root) or indirect (transitive) dependencies.

Generally in the past, to simplify things, we defined three common types:

- `fat`: This capsule jar will contain your app's jar as well as **some** (or usually, **all**) its dependencies. When the fat-jar is run, if all dependencies are included, then Capsule will simply setup the app and run it. If there are any missing dependencies then Capsule will resolve any missing dependencies at runtime (in the cache) before running it.
- `thin`: This capsule jar will contain your app's classes but **no** dependencies. Capsule will resolve these dependencies at runtime (in the cache).
- `empty`: This capsule will not include your app, or any of its dependencies. It will only contain the name of your app declared in the jar's manifest, along with capsule's classes. Capsule will read the manifest entry `Application` and resolve the app and its dependencies in Capsule's own cache (default `~/.capsule`).

Since users have shown a desire for a wide range of different setups, its encouraged you build the capsule with your own specific requirements without being bogged down on the three specific types listed above.


By default, the plugin will build a capsule that includes the app, and all its dependencies (including transitive) which is essentially a fat capsule.

```
target/my-app-1.0-cap.jar
```

The plugin is flexible in what it can embed inside the capsule jar, as you have the following options:

```
<includeApp>true</includeApp>
<includeCompileDep>true</includeCompileDep>
<includeRuntimeDep>true</includeRuntimeDep>
<includeProvidedDep>false</includeProvidedDep>
<includeSystemDep>false</includeSystemDep>
<includeTestDep>false</includeTestDep>
<includeTransitiveDep>true</includeTransitiveDep>
<includeOptionalDep>false</includeOptionalDep>
```

These ```includeXYZ``` flags essentially tell the plugin what to include in the jar. Of course if there are any of these that you exclude from the capsule jar, and in turn they are needed for the launch, then runtime resolution will be needed (see next section).

The above settings are the defaults.

So if a ```thin``` capsule is desired, it can be done like so:

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
				<includeApp>true</includeApp>
				<includeCompileDep>false</includeCompileDep>
				<includeRuntimeDep>false</includeRuntimeDep>
				<includeProvidedDep>false</includeProvidedDep>
				<includeSystemDep>false</includeSystemDep>
				<includeTestDep>false</includeTestDep>
				<includeTransitiveDep>false</includeTransitiveDep>
				<resolveCompileDep>true</resolveCompileDep>
				<resolveRuntimeDep>true</resolveRuntimeDep>
				<resolveProvidedDep>true</resolveProvidedDep>
				<resolveSystemDep>true</resolveSystemDep>
				<resolveTestDep>false</resolveTestDep>
				<resolveTransitiveDep>true</resolveTransitiveDep>
			</configuration>
		</execution>
	</executions>
</plugin>
```

And likewise if an ```empty``` capsule is desired, it can be done like so:

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
				<includeApp>false</includeApp>
				<includeCompileDep>false</includeCompileDep>
				<includeRuntimeDep>false</includeRuntimeDep>
				<includeProvidedDep>false</includeProvidedDep>
				<includeSystemDep>false</includeSystemDep>
				<includeTestDep>false</includeTestDep>
				<includeTransitiveDep>false</includeTransitiveDep>
				<resolveApp>true</resolveApp>
				<resolveCompileDep>true</resolveCompileDep>
				<resolveRuntimeDep>true</resolveRuntimeDep>
				<resolveProvidedDep>true</resolveProvidedDep>
				<resolveSystemDep>true</resolveSystemDep>
				<resolveTestDep>false</resolveTestDep>
				<resolveTransitiveDep>true</resolveTransitiveDep>
			</configuration>
		</execution>
	</executions>
</plugin>
```


## Runtime Resolution

To perform the resolution at runtime, the capsule will include the necessary code to do this (namely the ```MavenCaplet```). This adds slightly to the overall file size of the generated capsule jar. This additional code is obviously mandatory if any dependencies (or the app itself) needs to be resolved at runtime.

To build the capsule without this additional code, make sure none of the ```resolve``` flags are set to true (by default all set to false).

Namely these are, ```<resolveAppDep>```, ```<resolveCompileDep>```, ```<resolveRuntimeDep>```, ```<resolveProvidedDep>```, ```<resolveSystemDep>```, ```<resolveTestDep>```.

So if resolution is needed at runtime for any of these options, then it needs to be set in the ```<configuration>``` tag like so:

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<resolveAppDep>true<resolveAppDep>
	<resolveCompileDep>true<resolveCompileDep>
</configuration>
```

## Excluding Dependencies

Certain scenarios desire the case where only certain dependencies are included in the built capsule (and the others resolved at runtime).

You can exclude certain dependencies by scoping them out, or in other words setting the scope of the dependency to something that you will not be including in the built capsule.

So you could set your dependency to scope ```runtime``` like so:

```
<dependency>
	<groupId>com.google.guava</groupId>
	<artifactId>guava</artifactId>
	<version>17.0</version>
	<scope>runtime</scope>
</dependency>
```

And then mark the necessary flags:

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<includeRuntimeDep>false<includeRuntimeDep>
	<resolveRuntimeDep>true<resolveRuntimeDep>
</configuration>
```

So the above will not include the dependencies marked with ```runtime``` scope, however it will resolve them at launch.

### Including Optional Dependencies

By default, the plugin will not embed dependencies marked `<optional>true</optional>`.

So, for example an optional dependency is declared like so:

```
<dependency>
	<groupId>com.google.guava</groupId>
	<artifactId>guava</artifactId>
	<version>17.0</version>
	<optional>true</optional>
</dependency>
```

To include optional dependencies in the capsule, you simply need to turn on a flag:

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<includeOptionalDep>true</includeOptionalDep>
</configuration>
```

### Excluding Transitive Dependencies

By default, the plugin will embed the dependencies and their transitive dependencies (i.e dependencies of dependencies), as they will also be required to run the app.

However if transitive dependencies are not desired then this can be turned off. Simply set the configuration property `includeTransitiveDep` to false:

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<includeTransitiveDep>false</includeTransitiveDep>
</configuration>
```

(if you do do this however, you will probably want to set the ```resolveTransitiveDep``` flag to true.)

### Understanding Dependency Scope

In maven, you can essentially define the following five scopes for your dependencies; ```compile```, ```runtime```, ```provided```, ```system``` and ```test```.

You set the scope on each of the project's direct dependencies. Although transitive dependencies will have defined scope also, this only applies specifically to their own project.

The scope of transitive dependencies in relation to the main project will be directly affected by the scope its parent dependency. We call this the 'direct-scope'.

Also, note that transitive dependencies with scope other than ```compile``` or ```runtime``` are not applicable to the main project and thus are excluded always.

So for each direct dependency with scope:

* ```compile```
	- ```compile``` transitive dependencies have ```compile``` direct-scope.
  - ```runtime``` transitive dependencies have ```runtime``` direct-scope.
* ```runtime```
	- ```compile``` transitive dependencies have ```compile``` direct-scope.
	- ```runtime``` transitive dependencies have ```runtime``` direct-scope.
* ```provided```
	- ```compile``` & ```runtime``` transitive dependencies have ```provided``` direct-scope.
* ```system```
  - ```compile``` & ```runtime``` transitive dependencies have ```system``` direct-scope.
* ```test```
	- ```compile``` & ```runtime``` transitive dependencies have ```test``` direct-scope.

So, all the ```includeXYZ``` and ```resolveXYZ``` follow the above rules.


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
target/my-app-1.0-cap.jar
target/my-app-1.0-cap.x
```

So normally you would run the capsule like so:

```
java -jar target/my-app-1.0-cap.jar
```

However with the really executable builds, you can alternatively run the capsule nice and cleanly:

```
./target/my-app-1.0-cap.x
```

or

```
sh target/my-app-1.0-cap.x
```

##### Trampoline

When a capsule is launched, two processes are involved: first, a JVM process runs the capsule launcher, which then starts up a second, child process that runs the actual application. The two processes are linked so that killing or suspending one, will do the same for the other. While this model works well enough in most scenarios, sometimes it is desirable to directly launch the process running the application, rather than indirectly. This is supported by "capsule trampoline". [See more here at capsule](http://www.capsule.io/user-guide/#the-capsule-execution-process).

Essentially the concept defines that that when you execute the built Capsule jar, it will simply just output (in text) the full command needed to run the app (this will be a long command with all jvm and classpath args defined). The idea is then to just copy/paste the command and execute it raw.

If you would like to build 'trampoline' executable capsules you can add the `<trampoline>true</trampoline>` flag to the plugin's configuration:

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<trampoline>true</trampoline>
</configuration>
```

This will build `.tx` files like so:

```
target/my-app-1.0-cap.jar
target/my-app-1.0-cap.tx
```

Which you can run:

```
./target/my-app-1.0-cap.tx
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

Capsule supports a number of manifest entries to configure your app to your heart's content. See the full reference [here](http://www.capsule.io/reference/#manifest-attributes).

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

Note you do **not** need `Main-Class`, `Application-Class`, `Application`, `Dependencies` and `System-Properties` as these are generated automatically by the plugin.

## Custom File Name

The output capsule jar's name is as per the `<finalName>` tag with the appending of the ```customDescriptor```. By default the ```customDescriptor``` is ```-cap```.

```
<finalName><customDescriptor>.jar
```

So for example if you'd like to have your output capsule jar like 'my-amazing-app-capsule.jar' then you would do the following:

```
<build>
	<finalName>my-amazing-app</finalName>
	<plugins>
		<plugin>
      <groupId>com.github.chrischristo</groupId>
      <artifactId>capsule-maven-plugin</artifactId>
      <version>${capsule.maven.plugin.version}</version>
      <configuration>
	      <appClass>hello.HelloWorld</appClass>
	      <customDescriptor>-capsule</customDescriptor>
	    </configuration>
		</plugin>
	</plugins>
</build>
```

## Modes

Capsule supports the concept of modes, which essentially means defining your app jar into different ways depending on certain characteristics.
You define different modes for your app by setting specific manifest and/or system properties for each mode. So for e.g you could have a test mode which will define a test database connection, and likewise a production mode which will define a production database connection.
You can then easily run your capsule in a specific mode by adding the `-Dcapsule.mode=MODE` argument at the command line. See more at [capsule modes](http://www.capsule.io/user-guide/#modes-platform--and-version-specific-configuration).

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

## FileSets and DependencySets

If you'd like to copy over specific files from some local folder or from files embedded in some dependency then you can use the
 assembly style `<fileSets>` and `<dependencySets>` in the `<configuration>` tag.

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

```
<dependencySets>
	<dependencySet>
		<groupId>com.google.guava</groupId>
		<artifactId>guava</artifactId>
		<outputDirectory>config/</outputDirectory>
		<includes>
			<include>META-INF/MANIFEST.MF</include>
		</includes>
	</dependencySet>
</dependencySets>
```

So from above we copy over the myconfig.yml file that we have in our config folder and place it within the config directory in the capsule jar (the plugin will create this folder in the capsule jar).
And likewise with the dependency set defined, we pull the manifest file from within Google Guava's jar.

You specify a number of `<fileSet>` which must contain the `<directory>` (the location of the folder to copy), the `<outputDirectory>` (the destination directory within the capsule jar) and finally a set of `<include>` to specify which files from the `<directory>` to copy over.

You specify a number of `<dependencySet>` which must contain the GAV of a project dependency (the version is optional), the `<outputDirectory>` (the destination directory within the capsule jar) and finally a set of `<include>` to specify which files from the dependency to copy over.

You could also copy over the whole dependency directly if you leave out the ```includes``` tag:

```
<dependencySets>
	<dependencySet>
		<groupId>com.google.guava</groupId>
		<artifactId>guava</artifactId>
		<outputDirectory>config/</outputDirectory>
	</dependencySet>
</dependencySets>
```

You could also copy over the whole dependency in an **unpacked** form if you mark the ```<unpack>true</unpack>``` flag.

```
<dependencySets>
	<dependencySet>
		<groupId>com.google.guava</groupId>
		<artifactId>guava</artifactId>
		<outputDirectory>config/</outputDirectory>
		<unpack>true</unpack>
	</dependencySet>
</dependencySets>
```

## Custom Capsule Version

Ths plugin can support older or newer versions of capsule (at your own risk). You can specify a maven property for the capsule version (this will be the version of capsule to package within the build of the capsules).

```
<properties>
	<capsule.version>0.6.0</capsule.version>
</properties>
```
Otherwise, the default version of capsule will be used automatically. This is recommended.

## Caplets

Capsule supports defining your own Capsule class by extending the `Capsule.class`. If you want to specify your custom Capsule class, add a manifest entry pointing to it:

```
<configuration>
	<appClass>hello.HelloWorld</appClass>
	<caplets>MyCapsule</caplets>
</configuration>
```

If you have more than one, just add a space in between each one for e.g `<caplets>MyCapsule MyCapsule2</caplets>`.

If you want to use a caplet that's not a local class (i.e from a dependency) then you must specify the full coordinates of it like so:

`<caplets>co.paralleluniverse:capsule-daemon:0.1.0</caplets>`

And you can mix local and non-local caplets too:

`<caplets>MyCapsule co.paralleluniverse:capsule-daemon:0.1.0</caplets>`

See more info on [caplets](http://www.capsule.io/caplets/).

## Maven Exec Plugin Integration

The [maven exec plugin](http://www.mojohaus.org/exec-maven-plugin/) is a useful tool to run your jar all from within maven (using its classpath).

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
* `<chmod> (Optional)`: If executable (chmod +x) versions of the capsules should be built in the form of '.x' files (Applicable for Mac/Unix style systems). See [here](https://github.com/brianm/really-executable-jars-maven-plugin) and [here](http://skife.org/java/unix/2011/06/20/really_executable_jars.html) for more info. Defaults to false.
* `<trampoline> (Optional)`: This will create trampoline style executable capsules in the form of '.tx' files. See more info [here](https://github.com/chrischristo/capsule-maven-plugin#trampoline).
* `<outputDir> (Optional)`: Specifies the output directory. Defaults to the `${project.build.directory}`.
* `<execPluginConfig> (Optional)`: Specifies the ID of an execution within the exec-maven-plugin. The configuration from this execution will then be used to configure the capsules. If you specify 'root' then the `<configuration>` at root will be used instead of a particular execution. The exec's `<mainClass>` will map to Capsule's `<appClass>`. The exec's `<systemProperties>` will map to capsule's `<properties>`. If you specify this tag then the `<appClass>` tag does not need to present.
* `<properties> (Optional)`: The system properties to provide the app with.
* `<includeApp> (Optional)`: Specify whether the app itself should be embedded. Default is true.
* `<includeCompileDep> (Optional)`: Specify whether compile scope dependencies should be embedded. Default is true.
* `<includeRuntimeDep> (Optional)`: Specify whether runtime scope dependencies should be embedded. Default is true.
* `<includeProvidedDep> (Optional)`: Specify whether provided scope dependencies should be embedded. Default is false.
* `<includeSystemDep> (Optional)`: Specify whether system scope dependencies should be embedded. Default is false.
* `<includeTestDep> (Optional)`: Specify whether test scope dependencies should be embedded. Default is false.
* `<includeTransitiveDep> (Optional)`: Specify whether transitive dependencies should also be embedded. Default is true.
* `<includeOptionalDep> (Optional)`: Specify whether optional dependencies should also be embedded. The default is false.
* `<resolveApp> (Optional)`: Specifies whether the app should be resolved at launch. The default is false.
* `<resolveCompileDep> (Optional)`: Specifies whether the compile scoped dependencies should be resolved at launch. The default is false.
* `<resolveRuntimeDep> (Optional)`: Specifies whether the runtime scoped dependencies should be resolved at launch. The default is false.
* `<resolveProvidedDep> (Optional)`: Specifies whether the system scoped dependencies should be resolved at launch. The default is false.
* `<resolveSystemDep> (Optional)`: Specifies whether the system scoped dependencies should be resolved at launch. The default is false.
* `<resolveTestDep> (Optional)`: Specifies whether the test scoped dependencies should be resolved at launch. The default is false.
* `<resolveTransitiveDep> (Optional)`: Specifies whether the transitive dependencies should be resolved at launch. The default is false.
* `<manifest> (Optional)`: The set of additional manifest entries, for e.g `JVM-Args`. See [capsule](http://www.capsule.io/reference/) for an exhaustive list. Note you do **not** need `Main-Class`, `Application-Class`, `Application`, `Dependencies` and `System-Properties` as these are generated automatically.
* `<modes> (Optional)`: Define a set of `<mode>` with its own set of `<properties>` and `<manifest>` entries to categorise the capsule into different modes. The mode can be set at runtime. [See more here](https://github.com/chrischristo/capsule-maven-plugin#modes).
* `<fileSets> (Optional)`: Define a set of `<fileSet>` to copy over files into the capsule. [See more here](https://github.com/chrischristo/capsule-maven-plugin#filesets-and-dependencysets).
* `<dependencySets> (Optional)`: Define a set of `<dependencySet>` to copy over files contained within remote dependencies into the capsule. [See more here](https://github.com/chrischristo/capsule-maven-plugin#filesets-and-dependencysets).
* `<caplets> (Optional)`: Define a list of caplets (custom Capsule classes). [See more here](https://github.com/chrischristo/capsule-maven-plugin#caplets).
* `<customDescriptor> (Optional)`: The custom text for the descriptor part of the name of the output jar. This combined with the `<finalName>` tag creates the output name of the jar.

```
<!-- BUILD CAPSULES -->
<plugin>
	<groupId>com.github.chrischristo</groupId>
	<artifactId>capsule-maven-plugin</artifactId>
	<version>${capsule.maven.plugin.version}</version>
	<configuration>

		<appClass>hello.HelloWorld</appClass>

		<outputDir>target</outputDir>
		<caplets>MyCapsule MyCapsule2</caplets>

		<chmod>true</chmod>
		<trampoline>true</trampoline>

		<includeApp>true</includeApp>
		<includeCompileDep>true</includeCompileDep>
		<includeRuntimeDep>true</includeRuntimeDep>
		<includeProvidedDep>false</includeProvidedDep>
		<includeSystemDep>false</includeSystemDep>
		<includeTestDep>false</includeTestDep>
		<includeTransitiveDep>true</includeTransitiveDep>
		<includeOptionalDep>true</includeOptionalDep>

		<resolveApp>false</resolveApp>
		<resolveCompileDep>false</resolveCompileDep>
		<resolveRuntimeDep>false</resolveRuntimeDep>
		<resolveProvidedDep>false</resolveProvidedDep>
		<resolveSystemDep>false</resolveSystemDep>
		<resolveTestDep>false</resolveTestDep>
		<resolveTransitiveDep>false</resolveTransitiveDep>

		<execPluginConfig>root</execPluginConfig>
		<customDescriptor>-cap</customDescriptor>


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

		<dependencySets>
			<dependencySet>
			  <groupId>com.google.guava</groupId>
			  <artifactId>guava</artifactId>
			  <version>optional</version>
			  <outputDirectory>config/</outputDirectory>
			  <!--<unpack>true</unpack>-->
			  <includes>
			    <include>META-INF/MANIFEST.MF</include>
			  </includes>
			</dependencySet>
		</dependencySets>

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
