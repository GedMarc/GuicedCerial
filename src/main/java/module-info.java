import com.guicedee.cerial.implementations.CerialPortsBindings;
import com.guicedee.guicedinjection.interfaces.IGuiceModule;
import com.guicedee.guicedinjection.interfaces.Log4JConfigurator;

module com.guicedee.cerial {
    //requires transitive com.neuronrobotics.nrjavaserial;
    requires transitive org.apache.commons.lang3;
    requires static lombok;
    requires com.fasterxml.jackson.annotation;
    requires java.logging;

    requires org.apache.logging.log4j;

    requires transitive com.guicedee.client;
    requires transitive com.guicedee.jsonrepresentation;
    requires org.apache.logging.log4j.core;
    requires org.apache.commons.io;
    requires transitive com.fazecast.jSerialComm;


    exports com.guicedee.cerial;
    opens com.guicedee.cerial to com.google.guice,com.fasterxml.jackson.databind;

    exports com.guicedee.cerial.enumerations;

    provides IGuiceModule with CerialPortsBindings;

}