package ch.usi.dag.disl;

import ch.usi.dag.disl.util.WriteInfo;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.net.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClassLoaderFromJar {

    Set<String> pathToJars = new HashSet<>();

    public ClassLoaderFromJar(String pathToJar) {
        pathToJars.add(pathToJar);
    }

    public ClassLoaderFromJar(List<String> pathTiJars) {
        this.pathToJars.addAll(pathTiJars);
    }

    public void addPathToJar(String pathToJar) {
        pathToJars.add(pathToJar);
    }

    public void addPathsToJars(List<String> pathToJars) {
        this.pathToJars.addAll(pathToJars);
    }

    public URLClassLoader getUrlClassLoader() {
        List<URL> urls = new ArrayList<>();
        for (String path: pathToJars) {
            try {
                urls.add(new URI("jar:file:" + path + "!/").toURL());
            } catch (Exception e) {
                WriteInfo.getInstance().writeLine("Exception while creating URL for path: " + path);
            }
        }
        return new URLClassLoader(urls.toArray(URL[]::new));
    }

    public ClassHierarchyResolver getResolver() {
        return ClassHierarchyResolver.defaultResolver().orElse(
                ClassHierarchyResolver.ofClassLoading(getUrlClassLoader())
        );
    }

    public ClassFile.ClassHierarchyResolverOption getResolverOption() {
        return ClassFile.ClassHierarchyResolverOption.of(getResolver());
    }

    // TODO this is just a test with hard-coded paths, remove later
    public static ClassFile.ClassHierarchyResolverOption getTestResOption() {
        List<String> paths = List.of(
                "/home/ubuntu/p3-demo/libs/akka-actor_2.12-2.5.9.jar",
                "/home/ubuntu/p3-demo/libs/scala-2.12.4.jar",
                "/home/ubuntu/p3-demo/renaissance/renaissance-gpl-0.16.0.jar",
                "/home/ubuntu/p3-demo/renaissancelog4j-core-2.25.1.jar"
        );
        ClassLoaderFromJar classLoaderFromJar = new ClassLoaderFromJar(paths);
        return classLoaderFromJar.getResolverOption();
    }
}
