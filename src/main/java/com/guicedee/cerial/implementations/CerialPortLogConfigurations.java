package com.guicedee.cerial.implementations;

import com.guicedee.guicedinjection.interfaces.Log4JConfigurator;
import org.apache.logging.log4j.core.config.builder.api.ComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import static org.apache.logging.log4j.Level.DEBUG;

public class CerialPortLogConfigurations implements Log4JConfigurator
{
    @Override
    public ConfigurationBuilder<BuiltConfiguration> configure(ConfigurationBuilder<BuiltConfiguration> builder,
                                                              RootLoggerComponentBuilder rootLogger)
    {
        for (int i = 1; i < 100; i++)
        {
            String logName = "COM" + i;
            System.out.println("Creating Logger - " + logName);
            LayoutComponentBuilder layoutBuilder = builder.newLayout("PatternLayout")
                                                          .addAttribute("pattern", "%d{ABSOLUTE} %-5level: %msg%n");
            ComponentBuilder triggeringPolicy = builder.newComponent("Policies")
                                                       .addComponent(builder.newComponent("CronTriggeringPolicy")
                                                                            .addAttribute("schedule", "0 0 0 * * ?"))
                                                       .addComponent(builder.newComponent("SizeBasedTriggeringPolicy")
                                                                            .addAttribute("size", "100M"));

            var appenderBuilder = builder.newAppender(logName, "RollingFile")
                                         .addAttribute("fileName", "logs/" + logName + ".log")
                                         .addAttribute("filePattern", "logs/$${date:yyyy-MM}/" + logName + "-%d{yyyy-MM-dd-HH-mm-ss}.log.gz")
                                         .add(layoutBuilder)
                                         .addComponent(triggeringPolicy);
            builder.add(appenderBuilder);
            // create the new logger
            builder.add(builder.newLogger(logName, DEBUG)
                               .add(builder.newAppenderRef(logName))
                               .addAttribute("additivity", true));
        //    rootLogger.add(builder.newAppenderRef(logName));
        }

        return builder;
    }
}
