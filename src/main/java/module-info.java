import com.guicedee.cerial.implementations.CerialPortsBindings;
import com.guicedee.guicedinjection.interfaces.IGuiceModule;

module com.guicedee.cerial {
    requires transitive com.neuronrobotics.nrjavaserial;
    requires static lombok;
    requires com.fasterxml.jackson.annotation;
    requires java.logging;

    requires com.guicedee.client;

    exports com.guicedee.cerial;
    opens com.guicedee.cerial to com.google.guice;
    exports com.guicedee.cerial.enumerations;

    provides IGuiceModule with CerialPortsBindings;

}