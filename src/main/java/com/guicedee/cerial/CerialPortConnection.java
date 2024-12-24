package com.guicedee.cerial;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fazecast.jSerialComm.SerialPort;
import com.google.inject.Inject;
import com.guicedee.cerial.enumerations.*;
import com.guicedee.cerial.implementations.*;
import com.guicedee.client.CallScoper;
import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
import lombok.*;
import lombok.extern.java.Log;
import org.apache.commons.lang3.function.TriConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.OutputStream;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;
import static com.guicedee.cerial.enumerations.ComPortStatus.*;
import static lombok.AccessLevel.PRIVATE;

@SuppressWarnings({"UnusedReturnValue",
        "unchecked",
        "unused"})
@Getter
@ToString(of = {"comPort",
        "comPortStatus"})
@JsonIgnoreProperties(ignoreUnknown = true, value = {"inspection"})
@JsonAutoDetect(fieldVisibility = ANY, getterVisibility = NONE, setterVisibility = NONE)
@NoArgsConstructor
@Log
public class CerialPortConnection<J extends CerialPortConnection<J>> implements IJsonRepresentation<J>,
        IGuicePreDestroy<J>
{
    @Serial
    private static final long serialVersionUID = 1L;

    public static NumberFormat portNumberFormat = NumberFormat.getNumberInstance();

    static
    {
        portNumberFormat.setMinimumIntegerDigits(3);
        portNumberFormat.setMinimumFractionDigits(0);
        portNumberFormat.setMaximumFractionDigits(0);
        portNumberFormat.setMaximumIntegerDigits(3);
    }

    @JsonIgnore
    private com.fazecast.jSerialComm.SerialPort connectionPort;

    @JsonIgnore
    private final AtomicBoolean outputBufferEmpty = new AtomicBoolean(false);
    @JsonIgnore
    private final AtomicBoolean clearToSend = new AtomicBoolean(false);

    private Integer comPort;

    private BaudRate baudRate = BaudRate.$9600;
    private ComPortStatus comPortStatus = ComPortStatus.Offline;
    private ComPortType comPortType = ComPortType.Device;
    private DataBits dataBits = DataBits.$8;
    private FlowControl flowControl = FlowControl.None;
    private Parity parity = Parity.None;
    private StopBits stopBits = StopBits.$1;

    private Integer bufferSize = 4096;

    private Integer idleTimerSeconds = 5;

    @Getter(PRIVATE)
    @JsonIgnore
    private OutputStream writer = null;

    @JsonIgnore
    @Getter(PRIVATE)
    private BiConsumer<CerialPortConnection<?>, ComPortStatus> comPortStatusUpdate;
    /*@JsonIgnore
    @Getter(PRIVATE)
    private BiConsumer<byte[], CerialPortConnection<?>> comPortRead;*/
    @JsonIgnore
    @Getter(PRIVATE)
    private TriConsumer<Throwable, CerialPortConnection<?>, ComPortStatus> comPortError;
    @JsonIgnore
    private CerialIdleMonitor monitor;

    private LocalDateTime lastMessageTime;

    @Setter
    @Getter
    private char[] endOfMessage = new char[]{'\r','\n',(byte)0x03};

    @Inject
    @JsonIgnore
    @Getter(PRIVATE)
    CallScoper callScoper;

    @JsonIgnore
    private Logger logger;


    private boolean run = false;
    @JsonIgnore
    private DataSerialPortMessageListener serialPortMessageListener;
    @JsonIgnore
    private DataSerialPortDisconnectListener disconnectListener;

    public CerialPortConnection(int comPort, BaudRate baudRate, int seconds)
    {
        this.comPort = comPort;
        this.baudRate = baudRate;
        setDataBits(DataBits.$8);
        setParity(Parity.None);
        setStopBits(StopBits.$1);
        setFlowControl(FlowControl.None);
        setComPortStatus(ComPortStatus.Offline);
        setComPortType(ComPortType.Device);

        connectionPort = com.fazecast.jSerialComm.SerialPort.getCommPort(getComPortName());
        serialPortMessageListener = new DataSerialPortMessageListener(endOfMessage, connectionPort, this);
        disconnectListener = new DataSerialPortDisconnectListener(connectionPort, this);
        connectionPort.setBaudRate(baudRate.toInt());
        this.idleTimerSeconds = seconds;
        this.setMonitor(new CerialIdleMonitor(this, 2, 1, seconds));
    }

    public CerialPortConnection(int comPort, BaudRate baudRate)
    {
        this(comPort, baudRate, 60 * 10);
    }

    public CerialIdleMonitor getMonitor()
    {
        if (monitor == null)
        {
            this.setMonitor(new CerialIdleMonitor(this, 2, 1, idleTimerSeconds));
        }
        return monitor;
    }

    public J connect()
    {
        beforeConnect();
        try
        {
            connectionPort.openPort();
            if (connectionPort.isOpen())
            {
                afterConnect();
                registerShutdownHook();
                setComPortStatus(Idle);
                getLogger().info("Com Port Connected - {}", getComPortName());
            } else
            {
                setComPortStatus(Missing);
            }
        } catch (Throwable e)
        {
            getLogger().fatal("Error connecting to port", e);
            onConnectError(e, ComPortStatus.GeneralException);
        }
        return (J) this;
    }

    public J disconnect()
    {
        if (connectionPort != null && connectionPort.isOpen())
        {
            connectionPort.closePort();
            setComPortStatus(Offline);
        }
        return (J) this;
    }

    public J beforeConnect()
    {
        if (callScoper == null)
        {
            IGuiceContext.instance()
                    .inject()
                    .injectMembers(this);
        }
        configure(connectionPort);
        return (J) this;
    }

    public J afterConnect()
    {
        setComPortStatus(Silent);
        connectionPort.removeDataListener();
        connectionPort.addDataListener(serialPortMessageListener);
        connectionPort.addDataListener(disconnectListener);
        connectionPort.addDataListener(new DataSerialPortErrorSoftwareOverrunListener(connectionPort, this));
        connectionPort.addDataListener(new DataSerialPortErrorBreakInterruptListener(connectionPort, this));
        connectionPort.addDataListener(new DataSerialPortErrorFirmwareOverrunListener(connectionPort, this));
        connectionPort.addDataListener(new DataSerialPortErrorFramingListener(connectionPort, this));
        connectionPort.addDataListener(new DataSerialPortErrorParityListener(connectionPort, this));
        getMonitor().begin();
        return (J) this;
    }

    public J onConnectError(Throwable e, ComPortStatus status)
    {
        if (comPortError != null)
        {
            comPortError.accept(e, this, status);
        } else
        {
            e.printStackTrace();
            setComPortStatus(status);
        }
        disconnect();
        monitor.end();
        return (J) this;
    }

    public J onComPortStatusUpdate(BiConsumer<CerialPortConnection<?>, ComPortStatus> comPortStatusUpdate)
    {
        this.comPortStatusUpdate = comPortStatusUpdate;
        return (J) this;
    }

    public J setComPortStatus(ComPortStatus comPortStatus, boolean... update)
    {
        if (this.comPortStatus != comPortStatus && (
                (update != null && update.length == 0) ||
                        (update != null && update[0]))
        )
        {
            if (this.comPortStatusUpdate != null)
            {
                this.comPortStatusUpdate.accept(this, comPortStatus);
            }
        }
        this.comPortStatus = comPortStatus;
        return (J) this;
    }

    protected J registerShutdownHook()
    {
        IGuiceContext.getAllLoadedServices()
                .getOrDefault(IGuicePreDestroy.class, new HashSet())
                .add(this);
        return (J) this;
    }

    protected J afterShutdown()
    {
        return (J) this;
    }

    protected J beforeShutdown()
    {
        getMonitor().end();
        return (J) this;
    }

    protected J configure(SerialPort instance)
    {
        return (J) this;
    }

    private J me()
    {
        return (J) this;
    }

    private Logger getLogger()
    {
        if (logger == null)
        {
            return logger = LogManager.getLogger("COM" + comPort);
        }
        return logger;
    }

    @JsonProperty
    protected String getComPortName()
    {
        if (OSValidator.isWindows())
        {
            return "COM" + getComPort();
        } else
        {
            return "/dev/ttyUSB" + getComPort() + " " + getBaudRate().toInt();
        }
    }

    @SneakyThrows
    public void write(String message, boolean... checkForEndOfCharacter)
    {
        if (connectionPort != null && connectionPort.isOpen())
        {
            {
                if (!message.endsWith(String.valueOf('\n')))
                {
                    message += '\n';
                }
                System.out.print("[" + portNumberFormat.format(comPort) + "] TX - " + message);
                connectionPort.writeBytes(message.getBytes(StandardCharsets.UTF_8), message.length());
            }
            //getLogger().warn("TX : {}", message);
        } else
        {
            getLogger().trace("Message NOT Sent - {}", message);
        }
    }

    @Override
    public void onDestroy()
    {
        if (connectionPort.isOpen())
        {
            connectionPort.closePort();
        }
    }

    @SuppressWarnings("unused")
    private static class OSValidator
    {
        private static final String OS = System.getProperty("os.name")
                .toLowerCase();

        public static boolean isWindows()
        {
            return OS.contains("win");
        }

        public static boolean isMac()
        {
            return OS.contains("mac");
        }

        public static boolean isUnix()
        {
            return (OS.contains("nix") || OS.contains("nux") || OS.contains("aix"));
        }

        public static boolean isSolaris()
        {
            return OS.contains("sunos");
        }
    }

    public J setComPort(Integer comPort)
    {
        this.comPort = comPort;
        return (J) this;
    }

    public J setBaudRate(BaudRate baudRate)
    {
        this.baudRate = baudRate;
        return (J) this;
    }

    public J setComPortStatus(ComPortStatus comPortStatus)
    {
        if (this.comPortStatus != comPortStatus && this.comPortStatusUpdate != null)
        {
            getLogger().debug("Updating Port [" + comPort + "] to [" + comPortStatus + "]");
            this.comPortStatusUpdate.accept(this, comPortStatus);
        }
        this.comPortStatus = comPortStatus;
        return (J) this;
    }

    public J setComPortType(ComPortType comPortType)
    {
        this.comPortType = comPortType;
        return (J) this;
    }

    public J setDataBits(DataBits dataBits)
    {
        this.dataBits = dataBits;
        return (J) this;
    }

    public J setFlowControl(FlowControl flowControl)
    {
        this.flowControl = flowControl;
        return (J) this;
    }

    public J setParity(Parity parity)
    {
        this.parity = parity;
        return (J) this;
    }

    public J setStopBits(StopBits stopBits)
    {
        this.stopBits = stopBits;
        return (J) this;
    }

    public J setBufferSize(Integer bufferSize)
    {
        this.bufferSize = bufferSize;
        return (J) this;
    }

    public J setWriter(OutputStream writer)
    {
        this.writer = writer;
        return (J) this;
    }

    public J setComPortStatusUpdate(BiConsumer<CerialPortConnection<?>, ComPortStatus> comPortStatusUpdate)
    {
        this.comPortStatusUpdate = comPortStatusUpdate;
        return (J) this;
    }

    public J setComPortRead(BiConsumer<byte[], com.fazecast.jSerialComm.SerialPort> comPortRead)
    {
        if (this.serialPortMessageListener == null)
        {
            logger.warn("Port not yet ready");
            return (J) this;
        }
        this.serialPortMessageListener.setComPortRead(comPortRead);
        return (J) this;
    }

    public J setComPortError(TriConsumer<Throwable, CerialPortConnection<?>, ComPortStatus> comPortError)
    {
        this.comPortError = comPortError;
        return (J) this;
    }

    public J setMonitor(CerialIdleMonitor monitor)
    {
        this.monitor = monitor;
        return (J) this;
    }

    public J setLastMessageTime(LocalDateTime lastMessageTime)
    {
        this.lastMessageTime = lastMessageTime;
        return (J) this;
    }
}
