/*
 * S3SampleApp.java The purpose of this project is to store an object in AWS S3 with client-side
 * Ionic protection. This code is an example of what clients would use programmatically to
 * incorporate the Ionic platform into their S3 use cases.
 *
 * (c) 2017-2018 Ionic Security Inc. By using this code, I agree to the LICENSE included, as well as
 * the Terms & Conditions (https://dev.ionic.com/use) and the Privacy Policy
 * (https://www.ionic.com/privacy-notice/). Derived in part from AWS Sample S3 Project,
 * S3Sample.java.
 */

package com.ionic.cloudstorage.samples.bigid;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import com.ionic.sdk.agent.Agent;
import com.ionic.sdk.agent.data.MetadataMap;
import com.ionic.sdk.agent.key.KeyAttributesMap;
import com.ionic.sdk.agent.request.createkey.CreateKeysRequest;
import com.ionic.sdk.device.profile.persistor.DeviceProfiles;
import com.ionic.cloudstorage.awss3.IonicEncryptionMaterialsProvider;
import com.ionic.cloudstorage.awss3.IonicS3EncryptionClient;
import com.ionic.cloudstorage.awss3.IonicS3EncryptionClientBuilder;
import com.ionic.cloudstorage.awss3.Version;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult;
import com.amazonaws.services.simplesystemsmanagement.model.ParameterNotFoundException;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.ObjectMapper;


public class LambdaSample implements RequestHandler<Src2DestRequest, Src2DestResponse> {

    private static final String customKeyStorePath = "/tmp/CustomTruststore";

    public static void src2Dest(String srcName, String srcBucket, String destBucket, PrintStream outStream, PrintStream errStream) {
        try {
            AWSSimpleSystemsManagement systemsManagementClient = AWSSimpleSystemsManagementClientBuilder.defaultClient();

            String bigidUrl;
            String bigidPassword;
            String bigidUser;
            String bigidSep;

            // Acquire parameters from AWS Parameter store
            GetParameterRequest request = new GetParameterRequest();
            request.setWithDecryption(true);
            // Acquire BigId server url
            request.setName("big-id-url");
            GetParameterResult result = systemsManagementClient.getParameter(request);
            bigidUrl = result.getParameter().getValue();
            // Acquire BigId password
            request.setName("big-id-password");
            result = systemsManagementClient.getParameter(request);
            bigidPassword = result.getParameter().getValue();
            // Acquire BigId user
            request.setName("big-id-user");
            result = systemsManagementClient.getParameter(request);
            bigidUser = result.getParameter().getValue();
            // Acquire sep contents
            request.setName("big-id-sep");
            result = systemsManagementClient.getParameter(request);
            bigidSep = result.getParameter().getValue();
            // If the bigid service is using a self-signed certificate aquire the certificate
            // from the parameters store and add it to the sll store.
            try {
                request.setName("big-id-cert");
                result = systemsManagementClient.getParameter(request);
                String bigidCert = result.getParameter().getValue();
                outStream.println("Loading self-signed certificate");
                // Load the default key store
                String keyStorePath = System.getProperty("java.home") + "/lib/security/cacerts";
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                try (FileInputStream fis = new FileInputStream(keyStorePath)) {
                    keyStore.load(fis, "changeit".toCharArray()); // default keyStore password
                }

                // Load BigId crt from string
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                Certificate caCert = certificateFactory.generateCertificate(new ByteArrayInputStream(bigidCert.getBytes()));

                // Add cert to key store
                keyStore.setCertificateEntry("ca-cert", caCert);

                // Save the new key store
                try (FileOutputStream fos = new FileOutputStream(customKeyStorePath)) {
                    keyStore.store(fos, "TempFileEasyPass".toCharArray());
                }

                // Use the new key store
                System.setProperty("javax.net.ssl.trustStore", customKeyStorePath);
            } catch (ParameterNotFoundException e) {
                // big-id-cert getParameter does not exist. Continue assuming the bigid service is not using a self-signed cert.
            } catch (Exception e) {
                throw e;
            }

            JsonNode piiObjectList;

            try (CloseableHttpClient client = HttpClients.createDefault();) {

                HttpPost httpPost = new HttpPost(bigidUrl + "/api/v1/sessions");

                ObjectNode node = JsonNodeFactory.instance.objectNode();
                node.put("username", bigidUser);
                node.put("password", bigidPassword);
                StringEntity params = new StringEntity(node.toString());
                httpPost.addHeader("content-type", "application/json; charset=utf-8");
                httpPost.setEntity(params);

                String authTokenString;
                ObjectMapper mapper = new ObjectMapper();
                try (CloseableHttpResponse response = client.execute(httpPost)) {
                    HttpEntity entity = response.getEntity();
                    JsonNode jsonBlob = mapper.readTree(entity.getContent());
                    JsonNode authToken = jsonBlob.get("auth_token");
                    authTokenString = authToken.asText();
                }

                HttpGet httpGet = new HttpGet(bigidUrl + "/api/v1/piiRecords/objects?filter=" + URLEncoder.encode("system IN (" + srcName + ")", "UTF-8"));
                httpGet.addHeader("content-type", "application/json");
                httpGet.addHeader("Authorization", authTokenString);

                try (CloseableHttpResponse response = client.execute(httpGet)) {
                    HttpEntity entity = response.getEntity();
                    JsonNode jsonBlob = mapper.readTree(entity.getContent());
                    piiObjectList = jsonBlob.get("objectsList");
                    outStream.println(piiObjectList.size() + " targets identified.");
                }
            }

            // Set up Agent and provide to builder for IonicS3EncryptionClient
            Agent agent = new Agent(new DeviceProfiles(bigidSep));
            agent.setMetadata(getMetadataMap());

            IonicS3EncryptionClient ionicS3 = IonicS3EncryptionClientBuilder.standard().withIonicAgent(agent).buildIonic();
            outStream.println("Encryption client built");

            AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();

            Set<String> copiedSet = new HashSet<String>();

            piiObjectList.forEach((JsonNode node) -> {

                String split[] = node.get("fullObjectName").asText().split("/", 2);
                if (!split[0].equals(srcBucket)) {
                    return;
                }
                S3Object plainObject = s3.getObject(srcBucket, split[1]);

                KeyAttributesMap kam = new KeyAttributesMap();

                kam.put("ionic-filename", Arrays.asList(node.get("objectName").asText()));

                ArrayList<String> bigidAttributes = new ArrayList<String>();
                node.get("attribute").forEach((JsonNode entry) -> {
                    bigidAttributes.add(entry.asText());
                });
                kam.put("bigid-attributes", bigidAttributes);

                PutObjectRequest req = new PutObjectRequest(destBucket, plainObject.getKey(),
                    plainObject.getObjectContent(), plainObject.getObjectMetadata());
                outStream.println("Encrypting " + node.get("objectName").asText());
                ionicS3.putObject(req, new CreateKeysRequest.Key("", 1, kam));
                copiedSet.add(split[1]);
            });

            List<S3ObjectSummary> srcObjectSummaries = s3.listObjects(srcBucket).getObjectSummaries();
            outStream.println("\n" + (srcObjectSummaries.size() - piiObjectList.size()) + " objects without PII to be copied unencrypted.");
            srcObjectSummaries.forEach((S3ObjectSummary objectSummary) -> {
                String key = objectSummary.getKey();
                if (copiedSet.contains(key)) {
                    return;
                }
                outStream.println("Copying unencrypted " + key);
                s3.copyObject(srcBucket, key, destBucket, key);
            });

        } catch (Exception e) {
            errStream.println(e.getLocalizedMessage());
        } finally {
            // Clean up temp files
            try {
                Files.deleteIfExists(Paths.get(customKeyStorePath));
            } catch (Exception e) {
                errStream.println(e.getLocalizedMessage());
            }
        }
    }

    public Src2DestResponse handleRequest(Src2DestRequest request, Context context) {
        ByteArrayOutputStream baOutStream = new ByteArrayOutputStream(), baErrStream  = new ByteArrayOutputStream();
        PrintStream outStream = new PrintStream(baOutStream), errStream = new PrintStream(baErrStream);
        src2Dest(request.getSrcName(), request.getSrcBucket(), request.getDestBucket(), outStream, errStream);
        return new Src2DestResponse(baOutStream.toString(), baErrStream.toString());
    }

    // Main entry point for testing jar in a non lambda context
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java -jar target/LambdaSample.jar <source-name> <source-bucket> <destination-bucket>");
            return;
        }
        src2Dest(args[0], args[1], args[2], System.out, System.err);
    }

    public static MetadataMap getMetadataMap() {
        MetadataMap mApplicationMetadata = new MetadataMap();
        mApplicationMetadata.set("ionic-application-name", "Ionic-BigID-Example");
        mApplicationMetadata.set("ionic-application-version", Version.getFullVersion());
        mApplicationMetadata.set("ionic-client-type", "IDTS for S3 Java");
        mApplicationMetadata.set("ionic-client-version", Version.getFullVersion());

        return mApplicationMetadata;
    }
}
