package com.guicedee.cerial;

import java.util.function.BiConsumer;

/**
 * A functional interface for handling data received from a serial port.
 * <p>
 * This interface extends {@link BiConsumer} to provide a callback mechanism for
 * processing data received from a serial port. The first parameter is the byte array
 * containing the received data, and the second parameter is the {@link CerialPortConnection}
 * that received the data.
 * <p>
 * Example usage:
 * <pre>
 * CerialDataReceived dataHandler = (data, connection) -> {
 *     String message = new String(data).trim();
 *     System.out.println("Received: " + message);
 * };
 * </pre>
 *
 * @see CerialPortConnection#setComPortRead(BiConsumer)
 */
@FunctionalInterface
public interface CerialDataReceived extends BiConsumer<byte[], CerialPortConnection>
{
}
