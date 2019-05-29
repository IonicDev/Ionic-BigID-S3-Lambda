# BigID S3 Demo

Looks up existing BigID scan results (the scan must be set up and run separately) on an S3 source bucket and copies all objects in the source bucket to a destination bucket, encrypting objects with PII.

Can be run either locally or as an AWS Lambda function.

This example requires a BigId set up with a data source and scan configured for an S3 bucket.

## Set up AWS Parameter Store parameters

Set the following parameters as SecureStrings in the AWS Systems Manager Parameter Store in the same region as your buckets.

- set `big-id-url` with the url of your BigId server.
- set `big-id-user` with your BigId username
- set `big-id-password` with your BigId password
- set `big-id-sep` with the contents of the of the plaintext ionic persistor you wish to use.

 Optional if bigid is using a self signed cert :

- set `big-id-cert` with the contents of the .pem used by the BigId server.

## To run locally

- configure BigID and scan the source S3 bucket
- mvn package
- java -jar target/Ionic-BigID-S3-Lambda*.jar my-bigid-source-name my-s3-source-bucket my-s3-destination-bucket

## To run with Lambda

*Make sure that all S3 buckets, the Lambda function, IAM Role and System Manager Parameter Store values are set up in the same region.*

- configure BigID and scan the source S3 bucket
- mvn package
- In AWS IAM create a Role that includes the following AWS managed policies: `AmazonS3FullAccess`, `CloudWatchLogsFullAccess` as well as a custom policy enabling `getParameter` under Systems Manager.
- In AWS Lambda dashboard create a new Lambda function (Java 8) and select the Role you created in the previous step in the `Execution role` panel.
- In the `Function code` panel upload target/Ionic-BigID-S3-Lambda*.jar as the function package and set the handler to *com.ionic.cloudstorage.samples.bigid.LambdaSample::handleRequest*
- adjust the timeout under the `Basic settings` panel to account for the volume of files in the s3 bucket

### To test Lambda

- again in the Lambda console, create a new test with the following JSON request replacing the values with your BigId data source, source S3 bucket and destination S3 bucket:

    ```json
    {
        "srcName": "my-bigid-data-source-name",
        "srcBucket": "my-s3-source-bucket",
        "destBucket": "my-s3-destination-bucket"
    }
    ```

- run the test
