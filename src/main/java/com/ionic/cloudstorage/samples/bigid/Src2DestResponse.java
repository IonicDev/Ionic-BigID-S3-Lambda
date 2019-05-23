package com.ionic.cloudstorage.samples.bigid;

public class Src2DestResponse {
    String[] outLines;
    String[] errLines;

    public String[] getOutLines() {
        return outLines;
    }

    public void setOutLines(String[] outLines) {
        this.outLines = outLines;
    }

    public String[] getErrLines() {
        return errLines;
    }

    public void setErrLines(String[] errLines) {
        this.errLines = errLines;
    }

    public Src2DestResponse(String outStr, String errStr) {
        this.outLines = outStr.split("\\n");
        this.errLines = errStr.split("\\n");
    }
}
