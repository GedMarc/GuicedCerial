import com.guicedee.cerial.implementations.CerialPortsBindings;
import com.guicedee.guicedinjection.interfaces.IGuiceModule;

module com.guicedee.cerial {
    requires transitive com.neuronrobotics.nrjavaserial;
    requires transitive org.apache.commons.lang3;
    requires static lombok;
    requires com.fasterxml.jackson.annotation;
    requires java.logging;

    requires transitive com.guicedee.client;
    requires transitive com.guicedee.jsonrepresentation;


    exports com.guicedee.cerial;
    opens com.guicedee.cerial to com.google.guice,com.fasterxml.jackson.databind;

    exports com.guicedee.cerial.enumerations;

    provides IGuiceModule with CerialPortsBindings;

}