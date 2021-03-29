/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2019 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.utils.maven.plugin.v8;

import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.tika.io.IOUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.jahia.utils.maven.plugin.AbstractManagementMojo;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jahia utility goal to help with the migration of Jahia modules to vu8.
 *
 * @goal migrate-to-8
 * @requiresDependencyResolution runtime
 */
public class UpgradeTov8 extends AbstractManagementMojo {

    private final String actionRegex ="\\s+extends\\s+Action)[^$]*)$";
    private final String abstractFilteractionRegex ="\\s+extends\\s+AbstractFilter)[^$]*)$";
    private final String requiredMethodsSpringConf = "<!--  <property name=\"requiredMethods\" value=\"GET,POST\"/> -->";
    private static final Pattern p = Pattern.compile("name=\"name\"\\svalue=\"([a-zA-Z]+)\"");


    private List<String> actionList = new ArrayList<>();
    private List<String> actionNameList = new ArrayList<>();
    private List<String> abstractFilterList = new ArrayList<>();
    private List<File> springFiles = new ArrayList<File>();


    /**
     * @parameter property="groupID"  expression="${groupID}"
     * @required
     */
    protected String groupID;

    /**
     * @parameter property="jahiaVersion"  expression="${jahiaVersion}"
     * @required
     */
    protected String jahiaVersion;

    @Override
    public void doExecute() throws MojoExecutionException, MojoFailureException {


        ScmManager scmManager = null;
        File pomXmlFile = new File(baseDir, "pom.xml");
        String scmURL = null;
        ScmRepository scmRepository = null;
        try(Reader reader = new FileReader(pomXmlFile)) {

            Model model = new MavenXpp3Reader().read(reader);
            final Scm scm = model.getScm();
            scmURL = scm != null ? scm.getConnection() : null;
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        File springFolder = new File(baseDir, "src/main/resources/META-INF/spring");
        File javaFolder = new File(baseDir, "src/main/java");


        try {
            getLog().info("Performing Maven project modifications...");
            parsePom(springFolder);
            if (javaFolder.exists()){
                parseJava(javaFolder);
            }
            if (springFolder.exists()){
                parseSpring(springFolder);
            }

            if (actionList.size()>=1){
                addCSRFGuardConfig();
            }
        } catch (DocumentException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (SAXException e) {
            // ${TODO} Auto-generated catch block
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            // ${TODO} Auto-generated catch block
            e.printStackTrace();
        }
    }


    private void addCSRFGuardConfig() throws IOException {
        File csrfConfig = new File(baseDir,
                "src/main/resources/META-INF/configurations/org.jahia.modules.jahiacsrfguard-"+project.getArtifactId()+
                "-module.cfg");
        csrfConfig.getParentFile().mkdirs();
        csrfConfig.createNewFile();
        PrintWriter pw =  new PrintWriter(new FileWriter( csrfConfig ));
        pw.println("whitelist = *."+String.join(".do, *.", actionNameList)+".do");
        pw.close();
    }




    private void parseJava(File javaFolder) throws FileNotFoundException {

            List<File> Javafiles = ListJavaFiles(javaFolder);
            for (File Javafile : Javafiles) {
                String className = Javafile.getName().substring(0,Javafile.getName().indexOf(".java"));
                Scanner myReader = new Scanner(Javafile);
                while (myReader.hasNextLine()) {
                    String data = myReader.nextLine();

                    if (data.matches("^(.*?(\\s+class\\s+"+className+actionRegex)){
                        actionList.add(Javafile.getPath());
                        getLog().info("Action dectected:"+ className);
                        break;
                    }else if (data.matches("^(.*?(\\s+class\\s+"+className+abstractFilteractionRegex)){
                        abstractFilterList.add(Javafile.getPath());
                        getLog().info("Filter dectected:"+ className);
                        break;
                    }
                }
                myReader.close();
            }
    }


    private void parseSpring(File springFolder) throws IOException, ParserConfigurationException, SAXException {
        springFiles = Arrays.asList(springFolder.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith("xml");
            }
        }));
        for (File springFile : springFiles) {
            // Load the input XML document, parse it and return an instance of the
            // Document class.
            String actionRegex= getClassRegex(actionList);
           // document.getElementsByTagName("bean").
            File temp = File.createTempFile("temp-file-name", ".tmp");
            try(BufferedReader br = new BufferedReader(new FileReader( springFile ))) {
                try (PrintWriter pw = new PrintWriter(new FileWriter(temp))) {

                    String line;
                    int lineCount = 0;
                    while ((line = br.readLine()) != null) {
                        pw.println(line);
                        if (line.matches(actionRegex)) {
                            getLog().info("adding requiredMethods to:" + line);
                            pw.println(requiredMethodsSpringConf);
                            while ((line = br.readLine()) != null) {
                                pw.println(line);
                                Matcher m1 = p.matcher(line);
                                if (m1.find()) {
                                    actionNameList.add(m1.group(1));
                                    break;
                                }
                            }
                        }
                        lineCount++;
                    }
                    br.close();
                    pw.close();
                    temp.renameTo(springFile);
                }
            }
        }

    }


    private void parsePom(File springFolder) throws DocumentException, IOException {
        SAXReader reader = new SAXReader();
        File pom = new File(baseDir, "pom.xml");
        Document pomDocument = reader.read(pom);

        Document bundleModuleDocument = reader.read(getClass().getClassLoader().getResourceAsStream("bundleModule.xml"));

        Element root = pomDocument.getRootElement();


        // Add Spring 3.2 support

            if (springFolder.exists() && springFiles.size()>=1){
                Element properties = root.element("properties");
                if (properties == null) {
                    properties = root.addElement("properties");
                }

                Element requireCapability  = properties.element("require-capability");
                if (requireCapability == null) {
                    requireCapability = properties.addElement("require-capability");
                }
                if (!requireCapability.getText().contains("osgi.extender;filter:=\"(osgi.extender=org.jahia.bundles.blueprint.extender.config)\"")){
                    requireCapability.setText("osgi.extender;filter:=\"(osgi.extender=org.jahia.bundles.blueprint.extender.config)\"");
                }
            }


        //  Change groupID
        Element groupIDEl = root.element("groupId");
        if (groupIDEl!=null && groupIDEl.getText().equals("org.jahia.modules")){
            groupIDEl.setText(groupID);
        }

        //  Change change parent Jahia version
        Element parentEl = root.element("parent");
        if (parentEl!=null){

            Element parentGroupEl = parentEl.element("groupId");
            Element parentIdEl = parentEl.element("artifactId");
            Element parentVersionEl = parentEl.element("version");

            if (parentGroupEl!=null && parentGroupEl.getText().equals("org.jahia.modules")
                && parentIdEl!=null && parentIdEl.getText().equals("jahia-modules")
                    && parentVersionEl!=null && parentVersionEl.getText().startsWith("7.")){
                parentVersionEl.setText(jahiaVersion);
            }

        }

        // Explicitly authorize GET on Jahia Actions
        if (springFolder.exists()) {
            File[] SpringFiles = springFolder.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith("xml");
                }
            });
            for (File SpringFile : SpringFiles) {

            }
        }
        // Export pom
        XMLWriter writer = new XMLWriter(new FileOutputStream(pom), OutputFormat.createPrettyPrint());
        writer.write(pomDocument);
        writer.close();
    }

    private String getClassRegex(List<String> set) {
        List<String> classList = new ArrayList<>();
        for (String path: set) {
            String subpath = path.substring(path.indexOf("/java/") + 6 , path.length()-5);
            classList.add(subpath.replace("/","."));
        }
        return "^(.*?("+String.join("|", classList)+")[^$]*)$";
    }

    private List<File> ListJavaFiles(File rootFolder) {
        List<File> files = new ArrayList<File>();
        for (File f : rootFolder.listFiles()) {
            if (f.getName().endsWith(".java")){
                files.add(f);
            }
            if (f.isDirectory()) {
                files.addAll(ListJavaFiles(f));
            }
        }
        return files;
    }
}
