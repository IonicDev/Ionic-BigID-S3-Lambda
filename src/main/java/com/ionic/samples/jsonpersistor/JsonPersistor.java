/*
 * (c) 2019 Ionic Security Inc. By using this code, I agree to the LICENSE included, as well as
 * the Terms & Conditions (https://dev.ionic.com/use) and the Privacy Policy
 * (https://www.ionic.com/privacy-notice/).
 */

package com.ionic.samples.jsonpersistor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.ionic.sdk.error.IonicException;
import com.ionic.sdk.crypto.CryptoUtils;
import com.ionic.sdk.cipher.CipherAbstract;
import com.ionic.sdk.device.profile.DeviceProfile;
import com.ionic.sdk.device.profile.persistor.DeviceProfilePersistorBase;
import com.ionic.sdk.error.AgentErrorModuleConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonPersistor extends DeviceProfilePersistorBase {
    private String activeProfile;
    private List<DeviceProfile> profileList = new ArrayList<DeviceProfile>();

    public JsonPersistor(String filePath, CipherAbstract cipher) {
        super(filePath, cipher);
    }

    public JsonPersistor(CipherAbstract cipher) {
        super(null, cipher);
    }


    public JsonPersistor(String json) throws IonicException {
        super(null, null);
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonBlob = mapper.readTree(json);
            this.activeProfile = jsonBlob.get("activeDeviceId").asText();
            JsonNode profiles = jsonBlob.get("profiles");
            for (int i = 0; i < profiles.size(); i++) {
                JsonNode node = profiles.get(i);
                String profileName = node.get("name").asText();
                long creationTimestamp = node.get("creationTimestamp").asLong();
                String deviceId = node.get("deviceId").asText();
                String server = node.get("server").asText();
                byte[] aesCdIdcKey =  CryptoUtils.hexToBin(node.get("aesCdIdcKey").asText());
                byte[] aesCdEiKey = CryptoUtils.hexToBin(node.get("aesCdEiKey").asText());
                DeviceProfile profile = new DeviceProfile(profileName, creationTimestamp, deviceId, server, aesCdIdcKey, aesCdEiKey);
                this.profileList.add(profile);
            }
        } catch (IOException | NullPointerException e) {
            throw new IonicException(AgentErrorModuleConstants.ISAGENT_LOAD_PROFILES_FAILED);
        }
    }

    @Override
    public List<DeviceProfile> loadAllProfiles(String[] activeProfile) {
        activeProfile[0] = this.activeProfile;
        return profileList;
    }

    @Override
    public void saveAllProfiles(List<DeviceProfile> profiles, String activeProfile) {
        this.activeProfile = activeProfile;
        this.profileList = profiles;
    }

}
