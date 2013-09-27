/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cap4j.plugins.tomcat;

import cap4j.cli.Script;
import cap4j.core.Dependency;
import cap4j.core.GlobalContext;
import cap4j.core.VarFun;
import cap4j.plugins.Plugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.scm.CommandLineResult;
import cap4j.session.DynamicVariable;
import cap4j.session.SystemEnvironment;
import cap4j.session.Variables;
import cap4j.task.InstallationTask;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;
import com.google.common.base.Preconditions;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;

import static cap4j.session.Variables.concat;
import static cap4j.session.Variables.condition;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TomcatPlugin extends Plugin {
    public final DynamicVariable<String>
        version = Variables.newVar("7.0.42"),
        versionName = concat("apache-tomcat-", version),
        distrFilename = concat("apache-tomcat-", version, ".tar.gz"),
        homePath = Variables.newVar("/var/lib/tomcat").setDesc("Tomcat root dir"),
        homeParentPath = Variables.dynamic(new VarFun<String>() {
            public String apply() {
                return StringUtils.substringBeforeLast($.var(homePath), "/");
            }
        }),
        homeVersionPath = concat(homeParentPath, "/", versionName).setDesc("i.e. /var/lib/tomcat-7.0.42"),
        currentVersionPath = concat(homeParentPath, "/", versionName),

    webappsUnix = Variables.strVar("/var/lib/tomcat6/webapps").defaultTo("/var/lib/tomcat6/webapps"),
        webappsWin = Variables.dynamicNotSet("webappsWin", ""),
        webapps,
        warName = Variables.strVar("i.e. ROOT.war"),
        warPath,

    tomcatBasePort = Variables.newVar("8005"),
        tomcatAjpPort = Variables.newVar("8009"),
        tomcatHttpPort = Variables.newVar("8080"),
        tomcatHttpsPort = Variables.newVar("8443"),
        keystrokePassword = Variables.dynamic(""),
        catalinaHome = Variables.newVar("/usr/share/tomcat6"),
        catalinaExecutable = Variables.newVar("/usr/sbin/tomcat6"),

    myDirPath,
        buildPath,

    distrWwwAddress = Variables.dynamic(new VarFun<String>() {
        public String apply() {
            return MessageFormat.format("http://apache-mirror.rbc.ru/pub/apache/tomcat/tomcat-7/v{0}/bin/apache-tomcat-{0}.tar.gz", $.var(version));
        }
    });

    public TomcatPlugin(GlobalContext global) {
        super(global);

        myDirPath = Variables.joinPath(cap.sharedPath, "tomcat");
        buildPath = Variables.joinPath(myDirPath, "build");
        webapps = condition(cap.isUnix, webappsUnix, webappsWin);
        warPath = Variables.joinPath("warPath", webapps, warName);
    }

    public void initPlugin() {
        global.tasks.restartApp.addBeforeTask(new Task() {
            @Override
            protected TaskResult run(TaskRunner runner) {
                sys.sudo().rm($.var(warCacheDirs));
                sys.sudo().run($.newCommandLine()
                    .a("service", "tomcat6", "stop")
                    .semicolon()
                    .sudo()
                    .a("service", "tomcat6", "start")
                    .timeoutMin(2)
                );

                return TaskResult.OK;
            }
        });
    }

    public final InstallationTask setup = new InstallationTask() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            sys.rm($.var(buildPath));
            sys.mkdirs($.var(buildPath));

            if (!sys.exists(sys.joinPath($.var(myDirPath), $.var(distrFilename)))) {
                sys.run(new Script(sys)
                    .cd($.var(myDirPath))
                    .add(sys.line().timeoutMin(60).addRaw("wget %s", $.var(distrWwwAddress))));
            }

            final String homeParentPath = StringUtils.substringBeforeLast($.var(homePath), "/");

            final CommandLineResult r = sys.run(new Script(sys)
                .cd($.var(buildPath))
                .add(sys.line().timeoutMin(1).addRaw("tar xvfz ../%s", $.var(distrFilename)))
                .add(sys.line().sudo().addRaw("rm -r %s", $.var(homePath)))
                .add(sys.line().sudo().addRaw("rm -r %s", $.var(homeVersionPath)))
                .add(sys.line().sudo().addRaw("mv %s %s", $(buildPath) + "/" + $(versionName), homeParentPath))
                .add(sys.line().sudo().addRaw("ln -s %s %s", $.var(currentVersionPath), $.var(homePath)))
                .add(sys.line().sudo().addRaw("chmod -R g+r,o+r %s", $.var(homePath)))
                .add(sys.line().sudo().addRaw("chmod u+x,g+x,o+x %s/bin/*", $.var(homePath)))

                .add(sys.line().sudo().addRaw("rm /usr/bin/tomcatStart"))
                .add(sys.line().sudo().addRaw("ln -s %s/bin/startup.sh /usr/bin/tomcatStart", $.var(homePath)))
                .add(sys.line().sudo().addRaw("rm /usr/bin/tomcatStop"))
                .add(sys.line().sudo().addRaw("ln -s %s/bin/shutdown.sh /usr/bin/tomcatStop", $.var(homePath)))
                .add(sys.line().sudo().addRaw("rm /usr/bin/tomcatVersion"))
                .add(sys.line().sudo().addRaw("ln -s %s/bin/version.sh /usr/bin/tomcatVersion", $.var(homePath))),

                SystemEnvironment.passwordCallback($.var(cap.sshPassword))
            );

            System.out.println("verifying version...");
            final String versionText = sys.run(sys.line().setVar("JAVA_HOME", $.var(global.getPlugin(JavaPlugin.class).homePath)).addRaw("tomcatVersion")).text.trim();
            final String installedVersion = StringUtils.substringBetween(
                versionText,
                "Server version: Apache Tomcat/", "\r");

            Preconditions.checkArgument($.var(version).equals(installedVersion),
                "versions don't match: %s (installed) vs %s (actual)", installedVersion, $.var(version));

            System.out.printf("successfully installed Tomcat %s%n", $.var(version));

            return new TaskResult(r);
        }

        @Override
        public Dependency asInstalledDependency() {
            return Dependency.NONE;
        }
    };



    public final DynamicVariable<String[]> warCacheDirs = Variables.dynamic(new VarFun<String[]>() {
        public String[] apply() {
            final String name = FilenameUtils.getBaseName($.var(warName));
            return new String[]{
                $.sys.joinPath($.var(webapps), name)
            };
        }
    });


    @Override
    public InstallationTask getSetup() {
        return setup;
    }
}
