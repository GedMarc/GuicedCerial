package com.guicedee.cerial;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface CerialDataReceived extends BiConsumer<byte[], CerialPortConnection>
{
}

