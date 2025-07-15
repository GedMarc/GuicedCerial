package com.guicedee.cerial;

import com.guicedee.cerial.enumerations.ComPortStatus;
import com.guicedee.client.IGuiceContext;
import io.vertx.core.Vertx;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.guicedee.cerial.enumerations.ComPortStatus.Silent;
import static com.guicedee.cerial.enumerations.ComPortStatus.exceptionOperations;

/**
 * Monitors a serial port connection for idle time and updates its status accordingly.
 * <p>
 * This class uses a Vert.x periodic timer to check if a serial port connection has been
 * idle for a specified period of time. If the connection has been idle for longer than
 * the specified time, its status is updated to {@link ComPortStatus#Idle}.
 * <p>
 * The monitor can be started with the {@link #begin()} method and stopped with the
 * {@link #end()} method.
 */
@Getter
@Setter
@Log
public class CerialIdleMonitor
{
    /** The serial port connection being monitored. */
    private final CerialPortConnection connection;

    /** The initial delay in seconds before the monitor starts checking for idle time. */
    private int initialDelay;

    /** The period in seconds between checks for idle time. */
    private int period;

    /** The number of seconds after which a connection is considered idle. */
    private int seconds;

    /** The previous status of the connection. */
    private ComPortStatus previousStatus;

    /** The ID of the Vert.x timer used for monitoring. */
    private long timerId;

    /**
     * Creates a new idle monitor for the specified connection with default settings.
     * <p>
     * The default settings are:
     * <ul>
     *   <li>Initial delay: 2 seconds</li>
     *   <li>Period: 10 seconds</li>
     *   <li>Idle time: 120 seconds (2 minutes)</li>
     * </ul>
     *
     * @param connection the serial port connection to monitor
     */
    public CerialIdleMonitor(CerialPortConnection connection)
    {
        this.connection = connection;
        previousStatus = connection.getComPortStatus();
        initialDelay = 2;
        period = 10;
        //10 minutes
        seconds = (int) TimeUnit.SECONDS.toSeconds(120);
    }

    /**
     * Creates a new idle monitor for the specified connection with custom settings.
     *
     * @param connection   the serial port connection to monitor
     * @param initialDelay the initial delay in seconds before the monitor starts checking for idle time
     * @param period       the period in seconds between checks for idle time
     * @param seconds      the number of seconds after which a connection is considered idle
     */
    public CerialIdleMonitor(CerialPortConnection connection, int initialDelay, int period, int seconds)
    {
        this(connection);
        this.initialDelay = initialDelay;
        this.period = period;
        this.seconds = (int) TimeUnit.SECONDS.toSeconds(seconds);
    }

    /**
     * Starts the idle monitor.
     * <p>
     * This method sets up a periodic timer using Vert.x to check if the connection
     * has been idle for longer than the specified time. If it has, the connection's
     * status is updated to {@link ComPortStatus#Idle}.
     */
    public void begin()
    {
        var vertx = IGuiceContext.get(Vertx.class);
        timerId = vertx.setPeriodic(TimeUnit.SECONDS.toMillis(period), (handler) -> {
            if (!exceptionOperations.contains(connection.getComPortStatus()) &&
                    (connection.getComPortStatus() != Silent &&
                            ComPortStatus.onlineServerStatus.contains(connection.getComPortStatus())) &&
                    (connection.getLastMessageTime() == null || connection.getLastMessageTime()
                            .isBefore(LocalDateTime.now()
                                    .minusSeconds(seconds))

                    ))
            {
                connection.setComPortStatus(ComPortStatus.Idle);
            }
        });
    }

    /**
     * Gets the name of this idle monitor.
     * <p>
     * The name is constructed from the string "Idle Monitor " followed by the name
     * of the monitored connection's COM port.
     *
     * @return the name of this idle monitor
     */
    private String getIdleMonitorName()
    {
        return "Idle Monitor " + getConnection().getComPortName();
    }

    /**
     * Stops the idle monitor.
     * <p>
     * This method cancels the Vert.x timer used for monitoring.
     */
    public void end()
    {
        var vertx = IGuiceContext.get(Vertx.class);
        vertx.cancelTimer(timerId);
    }

}
