module guiced.cerial.test {
    requires com.guicedee.cerial;
    requires com.guicedee.guicedinjection;

    requires org.junit.platform.commons;
    requires com.neuronrobotics.nrjavaserial;
    requires org.junit.jupiter.api;

    //requires org.slf4j.simple;

    opens com.guicedee.cerial.test to org.junit.platform.commons;

}