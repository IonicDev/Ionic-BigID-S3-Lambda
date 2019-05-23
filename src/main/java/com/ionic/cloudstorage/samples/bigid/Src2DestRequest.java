package com.ionic.cloudstorage.samples.bigid;

public class Src2DestRequest {
    String srcName; // BigID data source name
    String srcBucket; // S3 source bucket
    String destBucket; // S3 destination bucket

    public String getSrcName() {
        return srcName;
    }

    public void setSrcName(String srcName) {
        this.srcName = srcName;
    }

    public String getSrcBucket() {
        return srcBucket;
    }

    public void setSrcBucket(String srcBucket) {
        this.srcBucket = srcBucket;
    }

    public String getDestBucket() {
        return destBucket;
    }

    public void setDestBucket(String destBucket) {
        this.destBucket = destBucket;
    }
}
