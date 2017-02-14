/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.maven;

import org.apache.maven.Maven;
import org.apache.maven.plugin.AbstractMojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class LagomAbstractMojo extends AbstractMojo {

    private static final Logger log = LoggerFactory.getLogger(LagomAbstractMojo.class);
    private static final String MAVEN_CORE_POM_PROPERTIES = "META-INF/maven/org.apache.maven/maven-core/pom.properties";
    private static final Pattern VERSION_PARSER = Pattern.compile("(\\d+)\\.(\\d+).*");

    static {
        InputStream is = Maven.class.getClassLoader().getResourceAsStream(MAVEN_CORE_POM_PROPERTIES);
        if (is != null) {
            try {
                Properties props = new Properties();
                props.load(is);
                String version = props.getProperty("version");
                if (version != null) {
                    Matcher matcher = VERSION_PARSER.matcher(version);
                    if (matcher.matches()) {
                        int major = Integer.parseInt(matcher.group(1));
                        int minor = Integer.parseInt(matcher.group(2));
                        if (major < 3 || (major == 3 && minor < 2)) {
                            String message = "Lagom requires at least Maven 3.2, you are using Maven " + version + ". Please upgrade to a more recent version of Maven.";
                            log.error(message);
                            throw new RuntimeException(message);
                        }
                    } else {
                        log.warn("Could not parse version " + version + " in " + MAVEN_CORE_POM_PROPERTIES + " to check and enforce maven version");
                    }
                } else {
                    log.warn("Could not find version property in " + MAVEN_CORE_POM_PROPERTIES + " to check and enforce maven version");
                }
            } catch (IOException e) {
                log.warn("Could not load " + MAVEN_CORE_POM_PROPERTIES + " to check and enforce maven version", e);
            }
        } else {
            log.warn("Could not find " + MAVEN_CORE_POM_PROPERTIES + " to check and enforce maven version");
        }
    }

}
