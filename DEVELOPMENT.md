# Development workflow

## First import

Open this directory as a Gradle project in IntelliJ IDEA and select JDK 21 for
the Gradle JVM.

`deployPlugin` copies the shaded JAR to `run/plugins` by default. To use a
server elsewhere, set the Gradle property locally without committing the path:

```powershell
.\gradlew.bat deployPlugin -PserverDirectory=C:/path/to/server
```

The property can also be placed in `%USERPROFILE%/.gradle/gradle.properties`.

## Start and debug

Run `deployPlugin`, start the development server from IntelliJ, and attach the
debugger to that Java process. Keep personal server paths and IntelliJ run
configurations outside the repository.

## HotSwap method changes

1. Keep the server running under **Debug**.
2. Change the body of an existing Java method.
3. Use **Run > Debugging Actions > Reload Changed Classes**. IntelliJ compiles
   the changed class and asks the JVM to replace it in the running server.

Standard JVM HotSwap handles method-body changes. Restart the server after
adding or removing fields or methods, changing method signatures, modifying
inheritance, or changing startup logic such as listener registration in
`onEnable()`.

Do not use the Minecraft `/reload` command for this workflow. It reloads whole
plugin class loaders and can leave listeners, tasks, and static state behind.

## Useful Gradle commands

```powershell
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat shadowJar
.\gradlew.bat deployPlugin
.\gradlew.bat classes
```

`deployPlugin` rebuilds and installs the complete plugin. `classes` is enough
when IntelliJ only needs freshly compiled class files for debugger HotSwap.
