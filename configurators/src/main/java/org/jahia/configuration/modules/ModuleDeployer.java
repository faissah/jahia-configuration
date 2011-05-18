package org.jahia.configuration.modules;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.jahia.configuration.logging.AbstractLogger;
import org.jahia.configuration.logging.ConsoleLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by IntelliJ IDEA.
 * User: toto
 * Date: 5/18/11
 * Time: 4:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class ModuleDeployer {
    private File output;
    private AbstractLogger logger;

    public ModuleDeployer(File output, AbstractLogger logger) {
        this.output = output;
        this.logger = logger;
    }

    public static void main(String[] args) {
        ConsoleLogger logger = new ConsoleLogger();

        if (args.length < 2) {
            logger.error("Usages : ModuleDeployer module.jar [module.jar ..] jahia-root");
            System.exit(-1);
        }

        String target = args[args.length-1];

        File output = new File(target, "WEB-INF/var/shared_modules");

        if (!output.exists()) {
            logger.error("Target does not seem to be a valid jahia root folder.");
            System.exit(-1);
        }

        ModuleDeployer deployer = new ModuleDeployer(output, logger);

        try {
            for (int i = 0; i < args.length - 1; i++) {
                String arg = args[i];
                File f = new File(arg);
                if (f.exists()) {
                    deployer.deployModule(f);
                } else {
                    logger.error("Cannot find file : "+f.getName());
                }
            }
        } catch (IOException e) {
            logger.error("", e);
        }

    }

    public void deployModule(File file) throws IOException {
        logger.info("Copy modules JAR " + file.getName() + " to shared modules folder");
        FileUtils.copyFileToDirectory(file, output);
        copyJars(file, new File(output,"../.."));
        copyDbScripts(file, new File(output,"../.."));
    }

    private void copyJars(File warFile, File targetDir) {
        try {
            JarFile war = new JarFile(warFile);
            int deployed = 0;
            if (war.getJarEntry("WEB-INF/lib") != null) {
                Enumeration<JarEntry> entries = war.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith("WEB-INF/lib/") && entry.getName().endsWith(".jar")) {
                        deployed++;
                        InputStream source = war.getInputStream(entry);
                        File libsDir = new File(targetDir, "WEB-INF/lib");
                        if (!libsDir.exists()) {
                            libsDir.mkdirs();
                        }
                        File targetFile = new File(targetDir, entry.getName());
                        FileOutputStream target = new FileOutputStream(targetFile);
                        IOUtils.copy(source, target);
                        IOUtils.closeQuietly(source);
                        target.flush();
                        IOUtils.closeQuietly(target);
                        if (entry.getTime() > 0) {
                            targetFile.setLastModified(entry.getTime());
                        }
                    }
                }
            }
            if (deployed > 0) {
                logger.info("Copied " + deployed + " JARs from " + warFile.getName() + " to jahia/WEB-INF/lib");
            }
        } catch (IOException e) {
            logger.error("Error copying JAR files for module " + warFile, e);
        }
    }

    private void copyDbScripts(File warFile, File targetDir) {
        try {
            JarFile war = new JarFile(warFile);
            if (war.getJarEntry("META-INF/db") != null) {
            	war.close();
            	ZipUnArchiver unarch = new ZipUnArchiver(warFile);
            	File tmp = new File(targetDir, String.valueOf(System.currentTimeMillis()));
            	tmp.mkdirs();
            	unarch.extract("META-INF/db", tmp);
            	FileUtils.copyDirectory(new File(tmp, "META-INF/db"), new File(targetDir, "WEB-INF/var/db/sql/schema"));
            	FileUtils.deleteDirectory(tmp);
                logger.info("Copied database scripts from " + warFile.getName() + " to jahia/WEB-INF/var/db/sql/schema");
            }
        } catch (Exception e) {
            logger.error("Error copying database scripts for module " + warFile, e);
		}
    }


}
