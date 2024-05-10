package com.guicedee.cerial.enumerations;

public enum FlowControl
{
     None(0),
     RTSCTS_IN(1),
     RTSCTS_OUT(2),
     XONXOFF_IN(4),
     XONXOFF_OUT(8);

    private final int flowControl;

    FlowControl(int flowControl)
    {
        this.flowControl = flowControl;
    }

    public int toInt()
    {
        return flowControl;
    }
}
