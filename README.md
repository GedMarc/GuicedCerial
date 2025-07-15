# GuicedCerial

A Serial/USB Port connector integrated with Google Guice for dependency injection.

## Overview

GuicedCerial is a module that provides serial port communication capabilities for GuicedEE applications. It integrates with the GuicedInjection framework to provide dependency injection and lifecycle management for serial port connections.

The module uses the jSerialComm library for low-level serial port communication and provides a higher-level API for configuring, connecting to, and communicating with serial ports. It supports various serial port parameters such as baud rate, data bits, parity, stop bits, and flow control, and provides mechanisms for handling connection status changes and data events.

## Features

- Easy integration with Google Guice for dependency injection
- Support for various serial port parameters (baud rate, data bits, parity, stop bits, flow control)
- Event-based communication model
- Automatic lifecycle management
- Idle connection monitoring
- Comprehensive error handling
- Logging capabilities

## Getting Started

### Maven Dependency

```xml
<dependency>
    <groupId>com.guicedee</groupId>
    <artifactId>guiced-cerial</artifactId>
    <version>${guicedee.version}</version>
</dependency>
```

### Basic Usage

```java
// Create a connection to COM1 at 9600 baud
CerialPortConnection connection = new CerialPortConnection(1, BaudRate.$9600);

// Configure the connection
connection.setDataBits(DataBits.$8)
          .setParity(Parity.None)
          .setStopBits(StopBits.$1)
          .setFlowControl(FlowControl.None);

// Set up data handling
connection.setComPortRead((data, port) -> {
    String message = new String(data).trim();
    System.out.println("Received: " + message);
});

// Set up status handling
connection.onComPortStatusUpdate((conn, status) -> {
    System.out.println("Connection status changed to: " + status);
});

// Connect to the port
connection.connect();

// Send data
connection.write("Hello, world!");

// Disconnect when done
connection.disconnect();
```

### Using Dependency Injection

```java
@Inject
@Named("1") // Inject COM1
private CerialPortConnection com1;

public void initialize() {
    com1.setDataBits(DataBits.$8)
        .setParity(Parity.None)
        .setStopBits(StopBits.$1)
        .setFlowControl(FlowControl.None)
        .connect();
}
```

## Documentation

For more detailed documentation, see the [GuicedCerial Package Structure Guidelines](../guiced-cerial-rules.md).

## License

This project is licensed under the terms of the [LICENSE](LICENSE) file included in the repository.
